package gameserver.gamemanager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import gameserver.Const;
import gameserver.effects.EffectId;
import gameserver.effects.EffectPool;
import gameserver.engine.GameEngine;
import gameserver.engine.GameOptions;
import gameserver.entity.Entity;
import gameserver.entity.Titan;
import gameserver.models.Game;
import networking.ClientPacket;
import networking.WebSocketPlayerConnection;
import networking.PlayerDivider;
import util.Util;

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
    public List<Integer> availableSlots = new ArrayList<>();
    public Map<String, Integer> preAssignedSlots = new HashMap<>();
    
    private final Set<Integer> claimedSlotIndices = new HashSet<>();
    private final Map<String, Integer> claimedSlotsByEmail = new HashMap<>();
    private final Object slotLock = new Object();
    
    final AtomicReference<Game> stateRef = new AtomicReference<>(state);
    ScheduledExecutorService exec;

    private static final ObjectMapper mapper = new ObjectMapper();
    static {
        SimpleModule module = new SimpleModule();
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

    public ManagedGame() {}

    public ManagedGame(String id, GameOptions op) {
        this(id, op, new ArrayList<>());
    }

    public ManagedGame(String id, GameOptions op, Collection<String> gameFor) {
        this.gameId = id;
        this.options = op;
        
        int teamSize = Math.max(1, op.playerIndex);
        if (gameFor != null && !gameFor.isEmpty()) {
            teamSize = gameFor.size() / 2;
        }

        if (teamSize <= 1 || op.goaliesDisabled()) {
            availableSlots.add(3);
            availableSlots.add(4);
        } else {
            availableSlots.add(1); 
            availableSlots.add(2); 
            for (int i = 0; i < teamSize - 1; i++) availableSlots.add(3 + i); 
            for (int i = 0; i < teamSize - 1; i++) availableSlots.add(3 + teamSize + i); 
        }

        if (gameFor != null && !gameFor.isEmpty()) {
            int index = 0;
            for (String email : gameFor) {
                if (index < availableSlots.size()) {
                    preAssignedSlots.put(email, availableSlots.get(index));
                    index++;
                }
            }
        }
    }

    public void delegatePacket(WebSocketPlayerConnection connection, ClientPacket request) {
        if (state == null || (state.phase != GamePhase.INGAME && state.phase != GamePhase.SCORE_FREEZE && state.phase != GamePhase.TUTORIAL)) {
            addOrReplaceNewClient(connection, clients, request.token);
        }
        if (state != null) {
            if (state.ended) {
                exec.shutdown();
                return; 
            }
            state.kickoff();
            PlayerDivider pd = dividerFromConn(connection);
            
            if (pd == null) {
                String email = Util.jwtExtractEmail(request.token);
                for (PlayerDivider p : state.clients) {
                    if (p.getEmail().equals(email)) {
                        pd = p;
                        pd.id = connection.id;
                        pd.setEmail(email);
                    }
                }
                boolean foundInClients = false;
                for (WebSocketPlayerConnection pc : clients) {
                    if (pc.getEmail().equals(email)) {
                        pc.setClient(connection);
                        foundInClients = true;
                    }
                }
                if (!foundInClients && pd != null) {
                    // FIX: Replaced invalid `pd.titanId` with `pd.getPossibleSelection()` for strictly bijective 1:1 lists
                    WebSocketPlayerConnection newConn = new WebSocketPlayerConnection(pd.getPossibleSelection(), connection, email);
                    clients.add(newConn);
                }
            }
            state.processClientPacket(pd, request);
        }
    }

    private PlayerDivider dividerFromConn(WebSocketPlayerConnection connection) {
        for (PlayerDivider pc : state.clients) {
            if (connection.id == pc.id) {
                return pc;
            }
        }
        return null;
    }

    boolean lobbyFull(List<WebSocketPlayerConnection> queue) {
        Set<String> uniqueEmails = new HashSet<>();
        for (WebSocketPlayerConnection p : queue) {
            uniqueEmails.add(p.getEmail());
        }
        return uniqueEmails.size() == availableSlots.size();
    }

    void addOrReplaceNewClient(WebSocketPlayerConnection c, List<WebSocketPlayerConnection> queue, String token) {
        String email = Util.jwtExtractEmail(token);
        boolean shouldStart;
        
        synchronized (slotLock) {
            boolean connFound = queue.stream().anyMatch(p -> p.id == c.id);
            boolean emailFound = queue.stream().anyMatch(p -> p.getEmail().equals(email));
            
            if (!connFound) {
                if (emailFound) { 
                    for (WebSocketPlayerConnection p : queue) {
                        if (p.getEmail().equals(email)) {
                            p.setClient(c);
                        }
                    }
                } else {
                    int slot = preAssignedSlots.containsKey(email)
                            ? preAssignedSlots.get(email)
                            : claimedSlotsByEmail.computeIfAbsent(email, e -> nextUnclaimedSlot());
                    // FIX: Wrap `slot` in Collections.singletonList to match WebSocketPlayerConnection constructor mapping requirement
                    queue.add(new WebSocketPlayerConnection(Collections.singletonList(slot), c, email));
                }
            }
            shouldStart = lobbyFull(queue);
        }
        
        if (shouldStart) {
            startGame(queue); 
        }
    }

    private int nextUnclaimedSlot() {
        for (int i = 0; i < availableSlots.size(); i++) {
            if (!claimedSlotIndices.contains(i)) {
                claimedSlotIndices.add(i);
                return availableSlots.get(i);
            }
        }
        throw new IllegalStateException("No unclaimed slots remaining in game " + gameId);
    }

    private void startGame(List<WebSocketPlayerConnection> gameIncludedClients) {
        if (state != null && state.away.score + state.home.score > 0) {
            return;
        }

        List<PlayerDivider> players = new ArrayList<>();
        for (WebSocketPlayerConnection pc : gameIncludedClients) {
            players.add(new PlayerDivider(pc));
        }

        if (gameId != null && gameId.startsWith("tutorial-")) {
            state = new gameserver.TutorialOverrides(gameId, players, options, this);
        } else {
            state = new GameEngine(gameId, players, options, this);
        }
        
        try {
            state.initializeServer();
            state.secondsToStart = c.getD("server.startDelay");
            for (int i = 0; i < 5; i++) {
                Thread.sleep(1000);
                state.secondsToStart -= 1;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        exec = Executors.newScheduledThreadPool(gameIncludedClients.size());
        clients = gameIncludedClients;
        
        Runnable updateClients = () -> {
            stateRef.set(state);
            Game snapshot = stateRef.get();
            if (snapshot == null) return;
            
            Game baseClone = (Game) deepClone(snapshot);
            if (baseClone == null) return;
            
            final Game finalBaseClone = baseClone;
            clients.removeIf(client -> !client.isConnected());
            
            clients.parallelStream().forEach(client -> {
                try {
                    PlayerDivider pd = dividerFromConn(client);
                    Game update = mapper.convertValue(finalBaseClone, Game.class);
                    if (update == null) return;
                    
                    update.underControl = state.titanSelected(pd);
                    update.nowEpochMs = System.currentTimeMillis();
                    
                    if (client.isConnected()) {
                        client.sendJson(mapper.writeValueAsString(anticheat(update)));
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
        };
        exec.scheduleWithFixedDelay(updateClients, 1, c.getI("server.clients.updateinterval.ms"), TimeUnit.MILLISECONDS);
    }

    private Game anticheat(Game update) {
        Titan underControl = update.underControl;
        if (underControl == null) {
            return update;
        }
        EffectPool fx = update.effectPool;
        if (fx.hasEffect(underControl, EffectId.BLIND)) {
            for (Titan player : update.players) {
                if (!player.id.equals(underControl)) censor(player);
            }
            for (Entity ent : update.entityPool) {
                if (!ent.id.equals(underControl)) censor(ent);
            }
            update.ball.X = 9999;
            update.ball.Y = 9999;
        }
        for (Titan player : update.players) {
            if (fx.hasEffect(player, EffectId.STEALTHED) && !fx.hasEffect(player, EffectId.FLARE)) {
                if (player.team != underControl.team) censor(player);
            }
        }
        for (Entity entity : update.entityPool) {
            if (fx.hasEffect(entity, EffectId.STEALTHED) && !fx.hasEffect(entity, EffectId.FLARE)) {
                if (entity.team != underControl.team) censor(entity);
            }
        }
        return update;
    }

    private void censor(Entity entity) {
        entity.X = 99999;
        entity.Y = 99999;
    }

    public boolean gameContainsEmail(Collection<String> gameFor) {
        for (String searchFor : gameFor) {
            for (WebSocketPlayerConnection matches : this.clients) {
                if (matches.email.equals(searchFor)) {
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
        Game snapshot = stateRef.get();
        if (snapshot == null) return;
        
        snapshot.phase = GamePhase.ENDED;
        CompletableFuture.runAsync(() -> {
            clients.parallelStream().forEach(client -> {
                try {
                    PlayerDivider pd = dividerFromConn(client);
                    Game update = (Game) deepClone(snapshot);
                    if (update == null) return;
                    
                    update.underControl = state.titanSelected(pd);
                    update.nowEpochMs = System.currentTimeMillis();
                    
                    if (client.isConnected()) {
                        client.sendJson(mapper.writeValueAsString(update));
                        Thread.sleep(1200);
                        client.close();
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
        });
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
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (isGameEngine) {
                ((GameEngine) object).unlock();
            }
        }
    }
}