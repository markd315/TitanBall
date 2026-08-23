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
        
        int teamSize = 1;
        try {
            int[] vals = GameOptions.getPlayersVal();
            if (op.playerIndex >= 0 && op.playerIndex < vals.length) {
                teamSize = Math.max(1, vals[op.playerIndex]);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

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
            if (teamSize <= 1 || op.goaliesDisabled()) {
                int index = 0;
                for (String email : gameFor) {
                    if (index == 0) {
                        preAssignedSlots.put(email, 3);
                    } else if (index == 1) {
                        preAssignedSlots.put(email, 4);
                    }
                    index++;
                }
            } else {
                List<String> teamHome = new ArrayList<>();
                List<String> teamAway = new ArrayList<>();
                
                partitionTeams(new ArrayList<>(gameFor), teamHome, teamAway, teamSize);
                
                authserver.matchmaking.Matchmaker mm = null;
                try {
                    mm = authserver.SpringContextBridge.services().getMatchmaker();
                } catch (Exception ignored) {}

                assignTeamSlots(teamHome, 1, 3, mm);
                assignTeamSlots(teamAway, 2, 3 + teamSize, mm);
            }
        }
    }

    private void assignTeamSlots(List<String> team, int goalieSlot, int fieldSlotStart, authserver.matchmaking.Matchmaker mm) {
        String goalie = null;
        if (mm != null) {
            for (String email : team) {
                if ("GOALIE".equalsIgnoreCase(mm.playerClasses.getOrDefault(email, "WARRIOR"))) {
                    goalie = email;
                    break;
                }
            }
        }
        if (goalie == null && !team.isEmpty()) {
            goalie = team.get(0);
        }
        if (goalie != null) {
            preAssignedSlots.put(goalie, goalieSlot);
        }
        int fieldIdx = 0;
        for (String email : team) {
            if (!email.equals(goalie)) {
                preAssignedSlots.put(email, fieldSlotStart + fieldIdx++);
            }
        }
    }

    private void partitionTeams(List<String> selectedPlayers, List<String> teamHome, List<String> teamAway, int teamSize) {
        int n = selectedPlayers.size();
        if (n <= 1) {
            teamHome.addAll(selectedPlayers);
            return;
        }

        authserver.matchmaking.Matchmaker mm = null;
        try {
            mm = authserver.SpringContextBridge.services().getMatchmaker();
        } catch (Exception ignored) {}

        List<List<Integer>> combinations = new ArrayList<>();
        generateCombinations(combinations, new ArrayList<>(), 1, n - 1, teamSize - 1);

        List<Integer> bestHome = null;
        int bestBrokenMutual = Integer.MAX_VALUE;
        int bestClassMismatch = Integer.MAX_VALUE;
        double bestEloDiff = Double.MAX_VALUE;

        for (List<Integer> comb : combinations) {
            List<Integer> homeIdx = new ArrayList<>();
            homeIdx.add(0);
            homeIdx.addAll(comb);

            List<Integer> awayIdx = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if (!homeIdx.contains(i)) awayIdx.add(i);
            }

            // Priority 1: Fewest broken mutual partner pairs
            int brokenMutual = 0;
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    if (areMutualPartners(selectedPlayers.get(i), selectedPlayers.get(j), mm)) {
                        boolean same = (homeIdx.contains(i) && homeIdx.contains(j)) || (awayIdx.contains(i) && awayIdx.contains(j));
                        if (!same) brokenMutual++;
                    }
                }
            }

            // Priority 2: Fewest class preference mismatches
            int classMismatch = getTeamClassMismatch(selectedPlayers, homeIdx, mm) + getTeamClassMismatch(selectedPlayers, awayIdx, mm);

            // Priority 3: Smallest team average ELO difference
            double homeElo = 0, awayElo = 0;
            for (int idx : homeIdx) homeElo += getPlayerElo(selectedPlayers.get(idx));
            for (int idx : awayIdx) awayElo += getPlayerElo(selectedPlayers.get(idx));
            double eloDiff = Math.abs((homeElo / homeIdx.size()) - (awayElo / awayIdx.size()));

            boolean isBetter = bestHome == null
                    || brokenMutual < bestBrokenMutual
                    || (brokenMutual == bestBrokenMutual && classMismatch < bestClassMismatch)
                    || (brokenMutual == bestBrokenMutual && classMismatch == bestClassMismatch && eloDiff < bestEloDiff);

            if (isBetter) {
                bestHome = homeIdx;
                bestBrokenMutual = brokenMutual;
                bestClassMismatch = classMismatch;
                bestEloDiff = eloDiff;
            }
        }

        for (int i = 0; i < n; i++) {
            if (bestHome != null && bestHome.contains(i)) {
                teamHome.add(selectedPlayers.get(i));
            } else {
                teamAway.add(selectedPlayers.get(i));
            }
        }
    }

    private void generateCombinations(List<List<Integer>> result, List<Integer> current, int start, int end, int k) {
        if (current.size() == k) {
            result.add(new ArrayList<>(current));
            return;
        }
        for (int i = start; i <= end; i++) {
            current.add(i);
            generateCombinations(result, current, i + 1, end, k);
            current.remove(current.size() - 1);
        }
    }

    private int getTeamClassMismatch(List<String> selectedPlayers, List<Integer> teamIndices, authserver.matchmaking.Matchmaker mm) {
        if (mm == null) return 0;
        int goalieChoices = 0;
        for (int idx : teamIndices) {
            String p = selectedPlayers.get(idx);
            if ("GOALIE".equalsIgnoreCase(mm.playerClasses.getOrDefault(p, "WARRIOR"))) {
                goalieChoices++;
            }
        }
        return goalieChoices > 0 ? (goalieChoices - 1) : 1;
    }

    private double getPlayerElo(String email) {
        try {
            authserver.users.PersistenceManager pm = authserver.SpringContextBridge.services().getPersistenceManager();
            if (pm != null && pm.userService != null) {
                authserver.models.User u = pm.userService.findUserByEmail(email);
                if (u != null) {
                    Double r = (options != null && (options.playerIndex == 4 || "/1/1/1/5/2/9999/10/12".equals(options.toStringSrv())))
                            ? u.getRating_1v1()
                            : u.getRating();
                    if (r != null) return r;
                }
            }
        } catch (Exception ignored) {}
        return 1000.0;
    }

    private boolean areMutualPartners(String email1, String email2, authserver.matchmaking.Matchmaker mm) {
        return mm != null && mm.areMutualPartners(email1, email2);
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
        if (state != null) {
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
        
        state.initializeServer();
        state.secondsToStart = c.getD("server.startDelay") / 1000.0;
        state.kickoff();

        exec = Executors.newScheduledThreadPool(gameIncludedClients.size());
        clients = gameIncludedClients;

        Runnable updateClients = () -> {
            stateRef.set(state);
            Game snapshot = stateRef.get();
            if (snapshot == null) return;

            // One deep-clone under lock to produce a stable, mutable base snapshot.
            Game baseClone = (Game) deepClone(snapshot);
            if (baseClone == null) return;

            // Determine once per broadcast cycle whether any censoring is needed.
            // If no player is BLIND or STEALTHED we can share a single serialised
            // base JSON and only patch the per-client fields, saving N-1 full
            // Jackson reflection passes per tick.
            boolean anyCensoringNeeded = censoringRequired(baseClone);

            // Serialize the base JSON string once (when censoring isn't needed).
            final String baseJson;
            if (!anyCensoringNeeded) {
                String tmp = null;
                try {
                    tmp = mapper.writeValueAsString(baseClone);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                baseJson = tmp;
            } else {
                baseJson = null;
            }

            final Game finalBaseClone = baseClone;
            final long nowMs = System.currentTimeMillis();

            clients.parallelStream().forEach(client -> {
                try {
                    PlayerDivider pd = dividerFromConn(client);
                    if (!client.isConnected()) return;

                    if (!anyCensoringNeeded && baseJson != null) {
                        // Fast path: patch underControl and nowEpochMs via JsonNode —
                        // much cheaper than a full convertValue deep-clone.
                        Titan controlled = state.titanSelected(pd);
                        com.fasterxml.jackson.databind.node.ObjectNode node =
                                (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(baseJson);
                        node.put("nowEpochMs", nowMs);
                        if (controlled != null) {
                            node.set("underControl", mapper.valueToTree(controlled));
                        } else {
                            node.putNull("underControl");
                        }
                        client.sendJson(mapper.writeValueAsString(node));
                    } else {
                        // Slow path: full per-client clone + anticheat censoring.
                        Game update = mapper.convertValue(finalBaseClone, Game.class);
                        if (update == null) return;
                        update.underControl = state.titanSelected(pd);
                        update.nowEpochMs = nowMs;
                        client.sendJson(mapper.writeValueAsString(anticheat(update)));
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
        };
        exec.scheduleWithFixedDelay(updateClients, 1, c.getI("server.clients.updateinterval.ms"), TimeUnit.MILLISECONDS);
    }

    /**
     * Returns true if any active anticheat censoring (BLIND or STEALTHED effect) is
     * present in the game state, meaning per-client game snapshots must differ.
     * When false, all clients receive the same base JSON (with only underControl /
     * nowEpochMs patched), saving N-1 full Jackson deep-clone passes per tick.
     */
    private boolean censoringRequired(Game game) {
        if (game == null || game.effectPool == null) return false;
        List<gameserver.effects.effects.Effect> effects = game.effectPool.getEffects();
        if (effects == null) return false;
        for (gameserver.effects.effects.Effect eff : effects) {
            EffectId id = eff.getEffect();
            if (id == EffectId.BLIND || id == EffectId.STEALTHED) {
                return true;
            }
        }
        return false;
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
            if (update.effectPool != null) {
                if (update.effectPool.getOn() != null) {
                    for (Entity ent : update.effectPool.getOn()) {
                        if (ent != null && !ent.id.equals(underControl.id)) censor(ent);
                    }
                }
                if (update.effectPool.getEffects() != null) {
                    for (gameserver.effects.effects.Effect eff : update.effectPool.getEffects()) {
                        if (eff != null && eff.on != null && !eff.on.id.equals(underControl.id)) censor(eff.on);
                    }
                }
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
        if (update.effectPool != null) {
            if (update.effectPool.getOn() != null) {
                for (Entity ent : update.effectPool.getOn()) {
                    if (ent != null && fx.hasEffect(ent, EffectId.STEALTHED) && !fx.hasEffect(ent, EffectId.FLARE)) {
                        if (ent.team != underControl.team) censor(ent);
                    }
                }
            }
            if (update.effectPool.getEffects() != null) {
                for (gameserver.effects.effects.Effect eff : update.effectPool.getEffects()) {
                    if (eff != null && eff.on != null && fx.hasEffect(eff.on, EffectId.STEALTHED) && !fx.hasEffect(eff.on, EffectId.FLARE)) {
                        if (eff.on.team != underControl.team) censor(eff.on);
                    }
                }
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