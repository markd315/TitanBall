package gameserver.gamemanager;

import authserver.SpringContextBridge;
import authserver.matchmaking.Matchmaker;
import authserver.users.identities.UserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gameserver.Const;
import gameserver.effects.EffectId;
import gameserver.effects.EffectPool;
import gameserver.engine.GameEngine;
import gameserver.engine.GameOptions;
import gameserver.entity.Entity;
import gameserver.entity.Titan;
import gameserver.models.Game;
import networking.CandidateGame;
import networking.ClientPacket;
import networking.WebSocketPlayerConnection;
import networking.PlayerDivider;
import org.joda.time.Instant;
import util.Util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class ManagedGame {
    public static final ServerMode SERVER_MODE = ServerMode.TRUETHREE;
    public ServerMode serverMode = SERVER_MODE;
    protected GameOptions options;
    protected Const c = new Const("res/game.cfg");

    public GameEngine state;
    public String gameId;
    List<WebSocketPlayerConnection> clients = new ArrayList<>();
    public List<List<Integer>> availableSlots;
    public Map<String, List<Integer>> preAssignedSlots = new HashMap<>();
    int claimIndex = 0;
    final AtomicReference<Game> stateRef = new AtomicReference<>(state);
    ScheduledExecutorService exec;

    public ManagedGame() {
    }


    public void delegatePacket(WebSocketPlayerConnection connection, ClientPacket request) {
        if (state == null || (state.phase != GamePhase.INGAME && state.phase != GamePhase.SCORE_FREEZE && state.phase != GamePhase.TUTORIAL)) {
            addOrReplaceNewClient(connection, clients, request.token);
        }
        if (state != null) {
            if (state.ended) {
                System.out.println("GameManager: ENDED GAME");
                exec.shutdown(); //Stop updating clients
                return; //game end logic sends the final update
            }
            state.kickoff();
            PlayerDivider pd = dividerFromConn(connection);
            if(pd == null){//client rejoining under new connection ID
                String email = Util.jwtExtractEmail(request.token);
                for(PlayerDivider p : state.clients){
                    if(p.getEmail().equals(email)){
                        pd = p;
                        pd.id = connection.id;
                        pd.setEmail(email);
                    }
                }
                boolean foundInClients = false;
                for(WebSocketPlayerConnection pc : clients){
                    if(pc.getEmail().equals(email)){
                        pc.setClient(connection);
                        foundInClients = true;
                    }
                }
                if (!foundInClients && pd != null) {
                    WebSocketPlayerConnection newConn = new WebSocketPlayerConnection(pd.possibleSelection, connection, email);
                    clients.add(newConn);
                }
            }
            state.processClientPacket(pd, request);
        }
    }

    private PlayerDivider dividerFromConn(WebSocketPlayerConnection connection) {
        for(PlayerDivider pc : state.clients){
            //System.out.println(pc.id);
            if(connection.id == pc.id){
                return pc;
            }
        }
        return null;
    }

    boolean lobbyFull(List<WebSocketPlayerConnection> queue){
        List<String> uniqueEmails = new ArrayList<>();
        for(WebSocketPlayerConnection p : queue){
            if(!uniqueEmails.contains(p.getEmail())){
                uniqueEmails.add(p.getEmail());
            }
        }
        return (uniqueEmails.size() == availableSlots.size()); // Check if all players are connected
    }

    void addOrReplaceNewClient(WebSocketPlayerConnection c, List<WebSocketPlayerConnection> queue, String token){
        boolean connFound = connectionQueued(queue, c);
        String email = Util.jwtExtractEmail(token);
        boolean emailFound = accountQueued(queue, email);
        if(!connFound){
            if(emailFound){ //rejoin unstarted game
                for(WebSocketPlayerConnection p : queue){
                    if(p.getEmail().equals(email)){
                        p.setClient(c);
                    }
                }
            }else{
                for(WebSocketPlayerConnection p : queue){
                    System.out.println(p.toString());
                }
                System.out.println("adding NEW client");
                //We should be sorting the connections when the game actually starts, so doesn't matter
                List<Integer> slot = preAssignedSlots.containsKey(email) ? preAssignedSlots.get(email) : nextUnclaimedSlot();
                queue.add(new WebSocketPlayerConnection(slot, c, email));
            }
        }
        if(lobbyFull(queue)){
            startGame(queue);
        }
    }

    private void startGame(List<WebSocketPlayerConnection> gameIncludedClients){
        if(state != null && state.away.score + state.home.score > 0){
            return; //Don't reset the game in this case lol
        }
        System.out.println("starting full");
        List<PlayerDivider> players = playersFromConnections(gameIncludedClients);
        if (gameId != null && gameId.startsWith("tutorial-")) {
            state = new gameserver.TutorialOverrides(gameId, players, options, this);
        } else {
            state = new GameEngine(gameId, players, options, this); //Start the game
        }
        try {
            state.initializeServer();
            instantiateSpringContext();
            gameIncludedClients = this.monteCarloBalance(gameIncludedClients);
            state.secondsToStart = c.getD("server.startDelay");
            for(int i=0; i<5; i++){
                Thread.sleep(1000);
                state.secondsToStart -=1;
            }
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
        exec = Executors.newScheduledThreadPool(gameIncludedClients.size());

        System.out.println("reassigning client list on startgame");
        clients = gameIncludedClients;
        Runnable updateClients = () -> {
            stateRef.set(state); // everyone gets the latest state once and no one gets a stale one or a fresher one
            Game snapshot = stateRef.get();
            if (snapshot == null) {
                System.err.println("Warning: state is null, skipping update");
                return;
            }
            
            // Clone the snapshot once under a single lock to decouple clients from the gameTick thread
            Game baseClone = null;
            boolean isGameEngine = snapshot instanceof GameEngine;
            if (isGameEngine) {
                ((GameEngine) snapshot).lock();
            }
            try {
                baseClone = mapper.convertValue(snapshot, Game.class);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (isGameEngine) {
                    ((GameEngine) snapshot).unlock();
                }
            }
            
            if (baseClone == null) {
                return;
            }
            
            final Game finalBaseClone = baseClone;
            
            // remove if not connected
            clients.removeIf(client -> !client.isConnected());
            clients.parallelStream().forEach(client -> {
                try {
                    PlayerDivider pd = dividerFromConn(client);
                    
                    // Clone the static baseClone (no lock required since it's not live state!)
                    Game update = mapper.convertValue(finalBaseClone, Game.class);
                    if (update == null) {
                        return;
                    }
                    if (diagUpdateCount < 5) {
                        diagUpdateCount++;
                        System.out.println("[DIAG] update #" + diagUpdateCount + " pre-send types (live): "
                                + describePlayerTypes(snapshot.players));
                        System.out.println("[DIAG] update #" + diagUpdateCount + " post-clone types: "
                                + describePlayerTypes(update.players));
                    }
                    update.underControl = state.titanSelected(pd);
                    update.nowEpochMs = System.currentTimeMillis();
                    if (client.isConnected()) {
                        client.sendJson(mapper.writeValueAsString(anticheat(update)));
                    }
                }
                catch (ConcurrentModificationException ex1) {
                    System.out.println("ConcurrentModificationException in update thread, skipping");
                }
                catch (Exception ex1) {
                    ex1.printStackTrace();
                }
            });
        };
        exec.scheduleWithFixedDelay(updateClients, 1, c.getI("server.clients.updateinterval.ms"),
                TimeUnit.MILLISECONDS);
        //cleanup schedule when game ends
    }

    private Game anticheat(Game update) {
        Titan underControl = update.underControl;
        if (underControl == null) {
            return update;
        }
        EffectPool fx = update.effectPool;
        if(fx.hasEffect(underControl, EffectId.BLIND)){
            for(Titan player : update.players){
                if(!player.id.equals(underControl)){
                    censor(player);
                }
            }
            for(Entity ent : update.entityPool){
                if(!ent.id.equals(underControl)){
                    censor(ent);
                }
            }
            update.ball.X = 9999;
            update.ball.Y = 9999;
        }
        for(Titan player : update.players){
            if(fx.hasEffect(player, EffectId.STEALTHED)
            && !fx.hasEffect(player, EffectId.FLARE)){
                if(player.team != underControl.team){
                    censor(player);
                }
            }
        }
        for(Entity entity : update.entityPool){
            if(fx.hasEffect(entity, EffectId.STEALTHED)
                    && !fx.hasEffect(entity, EffectId.FLARE)){
                if(entity.team != underControl.team){
                    censor(entity);
                }
            }
        }
        return update;
    }

    private void censor(Entity player) {
        player.X = 99999;
        player.Y = 99999;
    }


    private static List<PlayerDivider> playersFromConnections(List<WebSocketPlayerConnection> clients) {
        List<PlayerDivider> ret = new ArrayList<>();
        for(WebSocketPlayerConnection pc : clients){
            ret.add(new PlayerDivider(pc)); //GameEngine class doesn't know the connections, just IDs
        }
        return ret;
    }

    UserService userService = null;

    List<Integer> nextUnclaimedSlot(){
        claimIndex++;
        return availableSlots.get(claimIndex -1);
    }

    private boolean connectionQueued(List<WebSocketPlayerConnection> queue, WebSocketPlayerConnection query){
        boolean connFound = false;
        for(WebSocketPlayerConnection p : queue){
            if (p.id == query.id){
                connFound = true;
            }
        }
        return connFound;
    }

    public boolean gameContainsEmail(Collection<String> gameFor) {
        for(String searchFor : gameFor){
            for(WebSocketPlayerConnection matches : this.clients){
                if(matches.email.equals(searchFor)){
                    return true;
                }
            }
        }
        return false;
    }

    public WebSocketPlayerConnection replaceConnectionForSameUser(WebSocketPlayerConnection connection, String token) {
        for (WebSocketPlayerConnection pc : clients) {
            if (pc.getEmail().equals(Util.jwtExtractEmail(token))) {
                pc.setClient(connection);
                return pc;
            }
        }
        return null;
    }

    public void terminateConnections(AtomicReference<Game> stateRef) {
        System.out.println("terminating connections");
        //Client evaluates its own victory condition based on the score in the final packet
        Game snapshot = stateRef.get();
        if (snapshot == null) {
            System.err.println("Warning: state is null, skipping update");
            return;
        }
        snapshot.phase = GamePhase.ENDED;
        //Don't block the main thread since we sleep in the final update
        CompletableFuture.runAsync(() -> {
            clients.parallelStream().forEach(client -> {
                try{
                    PlayerDivider pd = dividerFromConn(client);
                    Game update = (Game) deepClone(snapshot);
                    if (update == null) {
                        return;
                    }
                    update.underControl = state.titanSelected(pd);
                    update.nowEpochMs = System.currentTimeMillis();
                    if (client.isConnected()) {
                        client.sendJson(mapper.writeValueAsString(update));
                        //Wait for the client to receive the final update before closing
                        Thread.sleep(1200);
                        client.close();
                    }
                }
                catch (Exception ex1) {
                    ex1.printStackTrace();
                }
            });
        });
    }

    private boolean accountQueued(List<WebSocketPlayerConnection> queue, String email) {
        boolean emailFound = false;
        for(WebSocketPlayerConnection p : queue){
            if (p.getEmail().equals(email)){
                emailFound = true;
            }
        }
        return emailFound;
    }

    private List<WebSocketPlayerConnection> monteCarloBalance(List<WebSocketPlayerConnection> players) {
        Map<String, Double> tempRating= new HashMap<>();
        for(WebSocketPlayerConnection pl : players){
            //System.out.println(pl.email +  " " + userService.findUserByEmail(pl.email).getRating());
            tempRating.put(pl.email, userService.findUserByEmail(pl.email).getRating());
        }
        final int MAX_MM = 5;
        CandidateGame candidateGame= new CandidateGame();
        for(int i=0; i<MAX_MM; i++){
            //The final possibleSelection is still wrong, maybe trash this last list constructor
            List<WebSocketPlayerConnection> testOrder = new ArrayList<>(players);
            Collections.shuffle(testOrder);
            List<WebSocketPlayerConnection> home = testOrder.subList(0, testOrder.size() / 2);
            List<WebSocketPlayerConnection> away = testOrder.subList(testOrder.size() / 2, testOrder.size());
            candidateGame.suggestTeams(home, away, tempRating);
        }
        return candidateGame.bestMonteCarloBalance(availableSlots);
    }

    private void instantiateSpringContext() {
        userService = SpringContextBridge.services().getUserService();
    }


    public ManagedGame(String id, GameOptions op) {
        this(id, op, new ArrayList<>());
    }

    public ManagedGame(String id, GameOptions op, Collection<String> gameFor) {
        this.gameId = id;
        this.options = op;
        ObjectMapper mapper = new ObjectMapper();
        try {
            System.out.println("tenant options");
            System.out.println(mapper.writeValueAsString(this.options));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        if (op.playerIndex == 0) {
            this.availableSlots = new ArrayList<>();
            List<Integer> c1 = new ArrayList<>();
            c1.add(3);
            c1.add(4);
            c1.add(5);
            c1.add(6);
            c1.add(7);
            c1.add(1);
            c1.add(8);
            c1.add(9);
            c1.add(10);
            c1.add(11);
            c1.add(12);
            c1.add(2);
            this.availableSlots.add(c1);
        } else if (op.playerIndex == 1) {
            this.availableSlots = new ArrayList<>();
            ArrayList<Integer> c1 = new ArrayList<>();
            c1.add(3);
            c1.add(1);
            ArrayList<Integer> c2 = new ArrayList<>();
            c2.add(4);
            c2.add(2);
            this.availableSlots.add(c1);
            this.availableSlots.add(c2);
        } else if (op.playerIndex >= 2 && op.playerIndex <= 8) {
            this.availableSlots = new ArrayList<>();
            int numPlayers = op.playerIndex;
            for (int k = 0; k < numPlayers; k++) {
                List<Integer> slot = new ArrayList<>();
                slot.add(3 + k);
                if (k == 0) {
                    slot.add(1);
                }
                this.availableSlots.add(slot);
            }
            for (int k = 0; k < numPlayers; k++) {
                List<Integer> slot = new ArrayList<>();
                slot.add(3 + numPlayers + k);
                if (k == 0) {
                    slot.add(2);
                }
                this.availableSlots.add(slot);
            }
        }

        if (gameFor != null && !gameFor.isEmpty()) {
            List<String> emails = new ArrayList<>(gameFor);
            List<String> homeTeam = new ArrayList<>();
            List<String> awayTeam = new ArrayList<>();
            
            Matchmaker matchmaker = SpringContextBridge.services() != null ? SpringContextBridge.services().getMatchmaker() : null;
            
            List<String> homeCoaches = new ArrayList<>();
            List<String> awayCoaches = new ArrayList<>();
            List<String> regularPlayers = new ArrayList<>();
            
            for (String email : emails) {
                String cls = "WARRIOR";
                if (matchmaker != null && matchmaker.playerClasses != null) {
                    cls = matchmaker.playerClasses.getOrDefault(email, "WARRIOR");
                }
                if ("GOALIE".equalsIgnoreCase(cls)) {
                    if (homeCoaches.isEmpty()) {
                        homeCoaches.add(email);
                    } else {
                        awayCoaches.add(email);
                    }
                } else {
                    regularPlayers.add(email);
                }
            }
            
            int half = regularPlayers.size() / 2;
            for (int i = 0; i < regularPlayers.size(); i++) {
                if (i < half) {
                    homeTeam.add(regularPlayers.get(i));
                } else {
                    awayTeam.add(regularPlayers.get(i));
                }
            }
            
            if (!homeCoaches.isEmpty()) {
                homeTeam.add(0, homeCoaches.get(0));
            }
            if (!awayCoaches.isEmpty()) {
                awayTeam.add(0, awayCoaches.get(0));
            }
            
            int numPlayers = op.playerIndex;
            boolean homeHasGoalie = !homeCoaches.isEmpty();
            for (int k = 0; k < homeTeam.size(); k++) {
                String email = homeTeam.get(k);
                List<Integer> slot = new ArrayList<>();
                if (k == 0 && homeHasGoalie) {
                    slot.add(1);
                } else {
                    int regularIndex = homeHasGoalie ? k - 1 : k;
                    slot.add(3 + regularIndex);
                    if (k == 0 && !homeHasGoalie) {
                        slot.add(1);
                    }
                }
                preAssignedSlots.put(email, slot);
            }
            
            boolean awayHasGoalie = !awayCoaches.isEmpty();
            for (int k = 0; k < awayTeam.size(); k++) {
                String email = awayTeam.get(k);
                List<Integer> slot = new ArrayList<>();
                if (k == 0 && awayHasGoalie) {
                    slot.add(2);
                } else {
                    int regularIndex = awayHasGoalie ? k - 1 : k;
                    slot.add(3 + numPlayers + regularIndex);
                    if (k == 0 && !awayHasGoalie) {
                        slot.add(2);
                    }
                }
                preAssignedSlots.put(email, slot);
            }
        }
    }

    private static int diagUpdateCount = 0;

    private static final ObjectMapper mapper = new ObjectMapper();
    static {
        com.fasterxml.jackson.databind.module.SimpleModule module = new com.fasterxml.jackson.databind.module.SimpleModule();
        module.addSerializer(org.joda.time.Instant.class, new com.fasterxml.jackson.databind.JsonSerializer<org.joda.time.Instant>() {
            @Override
            public void serialize(org.joda.time.Instant value, com.fasterxml.jackson.core.JsonGenerator gen, com.fasterxml.jackson.databind.SerializerProvider serializers) throws java.io.IOException {
                gen.writeNumber(value.getMillis());
            }
        });
        module.addDeserializer(org.joda.time.Instant.class, new com.fasterxml.jackson.databind.JsonDeserializer<org.joda.time.Instant>() {
            @Override
            public org.joda.time.Instant deserialize(com.fasterxml.jackson.core.JsonParser p, com.fasterxml.jackson.databind.DeserializationContext ctxt) throws java.io.IOException {
                return new org.joda.time.Instant(p.getValueAsLong());
            }
        });
        mapper.registerModule(module);
    }

    private static String describePlayerTypes(Titan[] players) {
        if (players == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < players.length; i++) {
            Titan t = players[i];
            if (i > 0) {
                sb.append(", ");
            }
            if (t == null) {
                sb.append(i).append(":null");
            } else {
                sb.append(i).append(":").append(t.getType())
                        .append("(locked=").append(t.typeAndMasteriesLocked).append(")");
            }
        }
        return sb.append("]").toString();
    }

    public static Object deepClone(Object object) {
        boolean isGameEngine = object instanceof GameEngine;
        if (isGameEngine) {
            ((GameEngine) object).lock();
        }
        try {
            if (object instanceof GameEngine) {
                return mapper.convertValue(object, GameEngine.class);
            } else if (object instanceof Game) {
                return mapper.convertValue(object, Game.class);
            }
            return mapper.convertValue(object, object.getClass());
        }
        catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        finally {
            if (isGameEngine) {
                ((GameEngine) object).unlock();
            }
        }
    }
}
