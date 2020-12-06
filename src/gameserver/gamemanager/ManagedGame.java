package gameserver.gamemanager;

import authserver.SpringContextBridge;
import authserver.users.identities.UserService;
import com.esotericsoftware.kryonet.Connection;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rits.cloning.Cloner;
import gameserver.Const;
import gameserver.effects.EffectId;
import gameserver.effects.EffectPool;
import gameserver.engine.GameEngine;
import gameserver.engine.GameOptions;
import gameserver.entity.Entity;
import gameserver.entity.Titan;
import networking.CandidateGame;
import networking.ClientPacket;
import networking.PlayerConnection;
import networking.PlayerDivider;
import org.joda.time.Instant;
import util.Util;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ManagedGame {
    public static final ServerMode SERVER_MODE = ServerMode.TRUETHREE;
    public ServerMode serverMode = SERVER_MODE;
    protected GameOptions options;
    protected Const c = new Const("res/game.cfg");

    public GameEngine state;
    public String gameId;
    List<PlayerConnection> clients = new ArrayList<>();
    public List<List<Integer>> availableSlots;
    int claimIndex = 0;

    public ManagedGame() {
    }


    public void delegatePacket(Connection connection, ClientPacket request) {
        if (state == null || state.phase != GamePhase.INGAME) {
            addOrReplaceNewClient(connection, clients, request.token);
        }
        if (state != null) {
            state.kickoff();
            PlayerDivider pd = dividerFromConn(connection);
            if(pd == null){//client rejoining under new connection ID
                String email = Util.jwtExtractEmail(request.token);
                for(PlayerDivider p : state.clients){
                    if(p.getEmail().equals(email)){
                        pd = p;
                        pd.setId(connection);
                        pd.setEmail(email);
                    }
                }
                for(PlayerConnection pc : clients){
                    if(pc.getEmail().equals(email)){
                        pc.setClient(connection);
                    }
                }
            }
            state.processClientPacket(pd, request);
        }
    }

    private PlayerDivider dividerFromConn(Connection connection) {
        for(PlayerDivider pc : state.clients){
            //System.out.println(pc.id);
            if(connection.getID() == pc.id){
                return pc;
            }
        }
        return null;
    }

    boolean lobbyFull(List<PlayerConnection> pd){
        List<String> uniqueEmails = new ArrayList<>();
        for(PlayerConnection p : pd){
            if(!uniqueEmails.contains(p.getEmail())){
                uniqueEmails.add(p.getEmail());
            }
        }
        return (uniqueEmails.size() == availableSlots.size());
    }

    void addOrReplaceNewClient(Connection c, List<PlayerConnection> queue, String token){
        boolean connFound = connectionQueued(queue, c);
        String email = Util.jwtExtractEmail(token);
        boolean emailFound = accountQueued(queue, email);
        System.out.println(email + " c found" + connFound + " e found " + emailFound);
        if(!connFound){
            if(emailFound){ //rejoin unstarted game
                for(PlayerConnection p : queue){
                    if(p.getEmail().equals(email)){
                        p.setClient(c);
                    }
                }
            }else{
                for(PlayerConnection p : queue){
                    System.out.println(p.toString());
                }
                System.out.println("adding NEW client");
                System.out.println(c.getRemoteAddressTCP());
                //We should be sorting the connections when the game actually starts, so doesn't matter
                queue.add(new PlayerConnection(nextUnclaimedSlot(), c, email));
            }
        }
        if(lobbyFull(queue)){
            startGame(queue);
        }
    }

    private void startGame(List<PlayerConnection> gameIncludedClients){
        if(state.away.score + state.home.score > 0){
            return; //Don't reset the game in this case lol
        }
        System.out.println("starting full");
        List<PlayerDivider> players = playersFromConnections(gameIncludedClients);
        state = new GameEngine(gameId, players, options, this); //Start the game
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
        ScheduledExecutorService exec = Executors.newScheduledThreadPool(gameIncludedClients.size());

        System.out.println("reassigning client list on startgame");
        for(PlayerConnection p : clients){
            //System.out.println(p.toString());
        }
        clients = gameIncludedClients;
        for(PlayerConnection p : clients){
            //System.out.println(p.toString());
        }
        Runnable updateClients = () -> {
            //System.out.println("updating clients now");
            clients.parallelStream().forEach(client -> {
                PlayerDivider pd = dividerFromConn(client.getClient());
                //Optimizing clone away by only hacking in the needed var fails because of occasional concurrency issue
                Cloner cloner= new Cloner();
                GameEngine update = cloner.deepClone(state);
                update.underControl = state.titanSelected(pd);
                update.now = Instant.now();
                client.getClient().sendTCP(anticheat(update));
            });
        };
        exec.scheduleAtFixedRate(updateClients, 1, 50, TimeUnit.MILLISECONDS);
    }

    private GameEngine anticheat(GameEngine update) {
        Titan underControl = update.underControl;
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


    private static List<PlayerDivider> playersFromConnections(List<PlayerConnection> clients) {
        List<PlayerDivider> ret = new ArrayList<>();
        for(PlayerConnection pc : clients){
            ret.add(new PlayerDivider(pc)); //GameEngine class doesn't know the connections, just IDs
        }
        return ret;
    }

    UserService userService = null;

    List<Integer> nextUnclaimedSlot(){
        claimIndex++;
        return availableSlots.get(claimIndex -1);
    }

    private boolean connectionQueued(List<PlayerConnection> queue, Connection query){
        boolean connFound = false;
        for(PlayerConnection p : queue){
            if (p.getClient().getID() == query.getID()){
                connFound = true;
            }
        }
        return connFound;
    }

    private boolean accountQueued(List<PlayerConnection> queue, String email) {
        boolean emailFound = false;
        for(PlayerConnection p : queue){
            if (p.getEmail().equals(email)){
                emailFound = true;
            }
        }
        return emailFound;
    }

    private List<PlayerConnection> monteCarloBalance(List<PlayerConnection> players) {
        Map<String, Double> tempRating= new HashMap<>();
        for(PlayerConnection pl : players){
            //System.out.println(pl.email +  " " + userService.findUserByEmail(pl.email).getRating());
            tempRating.put(pl.email, userService.findUserByEmail(pl.email).getRating());
        }
        final int MAX_MM = 5;
        CandidateGame candidateGame= new CandidateGame();
        for(int i=0; i<MAX_MM; i++){
            //The final possibleSelection is still wrong, maybe trash this last list constructor
            List<PlayerConnection> testOrder = new ArrayList<>(players);
            Collections.shuffle(testOrder);
            List<PlayerConnection> home = testOrder.subList(0, testOrder.size() / 2);
            List<PlayerConnection> away = testOrder.subList(testOrder.size() / 2, testOrder.size());
            candidateGame.suggestTeams(home, away, tempRating);
        }
        return candidateGame.bestMonteCarloBalance(availableSlots);
    }

    private void instantiateSpringContext() {
        userService = SpringContextBridge.services().getUserService();
    }


    public ManagedGame(String id, GameOptions op){
        this.gameId = id;
        this.options = op;
        ObjectMapper mapper = new ObjectMapper();
        try {
            System.out.println("tenant options");
            System.out.println(mapper.writeValueAsString(this.options));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        if(op.playerIndex == 1 && false) { //player switching working, but disabled for now
            this.availableSlots = new ArrayList<>();
            ArrayList<Integer> c1 = new ArrayList<>();
            c1.add(4);
            c1.add(1);
            c1.add(3);
            c1.add(5);
            c1.add(6);
            ArrayList<Integer> c2 = new ArrayList<>();
            c2.add(8);
            c2.add(2);
            c2.add(7);
            c2.add(9);
            c2.add(10);
            this.availableSlots.add(c1);
            this.availableSlots.add(c2);
        }
        if(op.playerIndex == 1) {
            this.availableSlots = new ArrayList<>();
            ArrayList<Integer> c1 = new ArrayList<>();
            c1.add(3);
            c1.add(1);
            ArrayList<Integer> c2 = new ArrayList<>();
            c2.add(4);
            c2.add(2);
            this.availableSlots.add(c1);
            this.availableSlots.add(c2);
        }
        else if(op.playerIndex == 0) {
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
        }
        else if(op.playerIndex == 5) {
            this.availableSlots = new ArrayList<>();
            List<Integer> c1 = new ArrayList<>();
            List<Integer> c2 = new ArrayList<>();
            List<Integer> c3 = new ArrayList<>();
            List<Integer> c4 = new ArrayList<>();
            List<Integer> c5 = new ArrayList<>();
            List<Integer> c6 = new ArrayList<>();
            List<Integer> c7 = new ArrayList<>();
            List<Integer> c8 = new ArrayList<>();
            List<Integer> c9 = new ArrayList<>();
            List<Integer> c10 = new ArrayList<>();
            c1.add(1);
            c2.add(2);
            c3.add(3);
            c4.add(4);
            c5.add(5);
            c6.add(6);
            c7.add(7);
            c8.add(8);
            c9.add(9);
            c10.add(10);
            this.availableSlots.add(c1);
            this.availableSlots.add(c2);
            this.availableSlots.add(c3);
            this.availableSlots.add(c4);
            this.availableSlots.add(c5);
            this.availableSlots.add(c6);
            this.availableSlots.add(c7);
            this.availableSlots.add(c8);
            this.availableSlots.add(c9);
            this.availableSlots.add(c10);
        }
        else if(op.playerIndex == 4){
            this.availableSlots = new ArrayList<>();
            List<Integer> c3 = new ArrayList<>();
            List<Integer> c4 = new ArrayList<>();
            List<Integer> c5 = new ArrayList<>();
            List<Integer> c6 = new ArrayList<>();
            List<Integer> c7 = new ArrayList<>();
            List<Integer> c8 = new ArrayList<>();
            List<Integer> c9 = new ArrayList<>();
            List<Integer> c10 = new ArrayList<>();
            c3.add(3);
            c3.add(1);
            c4.add(4);
            c5.add(5);
            c6.add(6);

            c7.add(7);
            c7.add(2);//def covers goalie
            c8.add(8);
            c9.add(9);
            c10.add(10);
            this.availableSlots.add(c3);
            this.availableSlots.add(c4);
            this.availableSlots.add(c5);
            this.availableSlots.add(c6);
            this.availableSlots.add(c7);
            this.availableSlots.add(c8);
            this.availableSlots.add(c9);
            this.availableSlots.add(c10);
        }
        else if(op.playerIndex == 3 && false){ //pswitch disabled for now
            this.availableSlots = new ArrayList<>();
            List<Integer> c3 = new ArrayList<>();
            List<Integer> c4 = new ArrayList<>();
            List<Integer> c5 = new ArrayList<>();
            List<Integer> c6 = new ArrayList<>();
            List<Integer> c7 = new ArrayList<>();
            List<Integer> c8 = new ArrayList<>();
            c3.add(3);
            c3.add(1);
            c4.add(4);
            c5.add(5);
            c5.add(6);

            c6.add(7);
            c6.add(2);//def covers goalie
            c7.add(8);
            c8.add(9);
            c8.add(10);
            this.availableSlots.add(c3);
            this.availableSlots.add(c4);
            this.availableSlots.add(c5);
            this.availableSlots.add(c6);
            this.availableSlots.add(c7);
            this.availableSlots.add(c8);
        }
        else if(op.playerIndex == 2 && false){ //disabled for now
            this.availableSlots = new ArrayList<>();
            List<Integer> c3 = new ArrayList<>();
            List<Integer> c4 = new ArrayList<>();
            List<Integer> c5 = new ArrayList<>();
            List<Integer> c6 = new ArrayList<>();
            c3.add(3);
            c3.add(1);
            c4.add(4);
            c4.add(5);
            c4.add(6);

            c5.add(7);
            c5.add(2);//def covers goalie
            c6.add(8);
            c6.add(9);
            c6.add(10);
            this.availableSlots.add(c3);
            this.availableSlots.add(c4);
            this.availableSlots.add(c5);
            this.availableSlots.add(c6);
        }
        else if(op.playerIndex == 2){
            this.availableSlots = new ArrayList<>();
            List<Integer> c3 = new ArrayList<>();
            List<Integer> c4 = new ArrayList<>();
            List<Integer> c5 = new ArrayList<>();
            List<Integer> c6 = new ArrayList<>();
            c3.add(3);
            c3.add(1);
            c4.add(4);

            c5.add(5);
            c5.add(2);
            c6.add(6);
            this.availableSlots.add(c3);
            this.availableSlots.add(c4);
            this.availableSlots.add(c5);
            this.availableSlots.add(c6);
        }
        else if(op.playerIndex == 3){
            this.availableSlots = new ArrayList<>();
            List<Integer> c3 = new ArrayList<>();
            List<Integer> c4 = new ArrayList<>();
            List<Integer> c5 = new ArrayList<>();
            List<Integer> c6 = new ArrayList<>();
            List<Integer> c7 = new ArrayList<>();
            List<Integer> c8 = new ArrayList<>();
            c3.add(3);//goalies are removed anyway if disabled for "true" 3v3
            c3.add(1);
            c4.add(4);
            c5.add(5);

            c6.add(6);
            c6.add(2);
            c7.add(7);
            c8.add(8);
            this.availableSlots.add(c3);
            this.availableSlots.add(c4);
            this.availableSlots.add(c5);
            this.availableSlots.add(c6);
            this.availableSlots.add(c7);
            this.availableSlots.add(c8);
        }
        /*
        else if(GameTenant.serverMode == ServerMode.ONEVTWO){ //disabled for now
            this.availableSlots = new ArrayList<>();
            List<Integer> c3 = new ArrayList<>();
            List<Integer> c4 = new ArrayList<>();
            List<Integer> c5 = new ArrayList<>();
            c3.add(3); //goalies are removed anyway if disabled for "true" 2v1
            c3.add(1);
            c4.add(4);

            c5.add(5);
            c5.add(2);
            c5.add(6);
            this.availableSlots.add(c3);
            this.availableSlots.add(c4);
            this.availableSlots.add(c5);
        }*/
    }

    public boolean gameContainsEmail(Collection<String> gameFor) {
        for(String searchFor : gameFor){
            for(PlayerConnection matches : this.clients){
                if(matches.email.equals(searchFor)){
                    return true;
                }
            }
        }
        return false;
    }
}
