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
                List<String> playerList = new ArrayList<>(gameFor);
                List<String> teamHome = new ArrayList<>();
                List<String> teamAway = new ArrayList<>();
                
                partitionTeams(playerList, teamHome, teamAway, teamSize);
                
                authserver.matchmaking.Matchmaker mm = authserver.SpringContextBridge.services().getMatchmaker();
                
                // Assign slots for Home Team
                String homeGoalie = null;
                for (String email : teamHome) {
                    String chosenClass = mm.playerClasses.getOrDefault(email, "WARRIOR");
                    if ("GOALIE".equalsIgnoreCase(chosenClass)) {
                        homeGoalie = email;
                        break;
                    }
                }
                if (homeGoalie == null && !teamHome.isEmpty()) {
                    homeGoalie = teamHome.get(0); // fallback
                }
                if (homeGoalie != null) {
                    preAssignedSlots.put(homeGoalie, 1);
                }
                
                int homeFieldIndex = 0;
                for (String email : teamHome) {
                    if (email.equals(homeGoalie)) continue;
                    int slot = 3 + homeFieldIndex;
                    preAssignedSlots.put(email, slot);
                    homeFieldIndex++;
                }
                
                // Assign slots for Away Team
                String awayGoalie = null;
                for (String email : teamAway) {
                    String chosenClass = mm.playerClasses.getOrDefault(email, "WARRIOR");
                    if ("GOALIE".equalsIgnoreCase(chosenClass)) {
                        awayGoalie = email;
                        break;
                    }
                }
                if (awayGoalie == null && !teamAway.isEmpty()) {
                    awayGoalie = teamAway.get(0); // fallback
                }
                if (awayGoalie != null) {
                    preAssignedSlots.put(awayGoalie, 2);
                }
                
                int awayFieldIndex = 0;
                for (String email : teamAway) {
                    if (email.equals(awayGoalie)) continue;
                    int slot = 3 + teamSize + awayFieldIndex;
                    preAssignedSlots.put(email, slot);
                    awayFieldIndex++;
                }
            }
        }
    }

    private void partitionTeams(List<String> selectedPlayers, List<String> teamHome, List<String> teamAway, int teamSize) {
        authserver.matchmaking.Matchmaker mm = null;
        try {
            mm = authserver.SpringContextBridge.services().getMatchmaker();
        } catch (Exception e) {
            // Spring context might not be initialized
        }
        
        if (mm == null) {
            int mid = selectedPlayers.size() / 2;
            for (int i = 0; i < selectedPlayers.size(); i++) {
                if (i < mid) {
                    teamHome.add(selectedPlayers.get(i));
                } else {
                    teamAway.add(selectedPlayers.get(i));
                }
            }
            return;
        }

        final authserver.matchmaking.Matchmaker finalMm = mm;

        // Find all connected components of partner groups based on mutual partner requests
        List<List<String>> mutualComponents = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        
        for (String player : selectedPlayers) {
            if (!visited.contains(player)) {
                List<String> component = new ArrayList<>();
                Queue<String> queue = new LinkedList<>();
                queue.add(player);
                visited.add(player);
                
                while (!queue.isEmpty()) {
                    String curr = queue.poll();
                    component.add(curr);
                    for (String other : selectedPlayers) {
                        if (!visited.contains(other) && areMutualPartners(curr, other)) {
                            visited.add(other);
                            queue.add(other);
                        }
                    }
                }
                mutualComponents.add(component);
            }
        }
        
        // Identify components that have exactly one goalie choice
        List<List<String>> mustKeepTogether = new ArrayList<>();
        for (List<String> comp : mutualComponents) {
            if (comp.size() >= 2) {
                int goalieCount = 0;
                for (String email : comp) {
                    String chosenClass = finalMm.playerClasses.getOrDefault(email, "WARRIOR");
                    if ("GOALIE".equalsIgnoreCase(chosenClass)) {
                        goalieCount++;
                    }
                }
                if (goalieCount == 1) {
                    mustKeepTogether.add(comp);
                }
            }
        }
        
        // Exhaustive search over all possible team partitions to find the one with the minimum penalty.
        // A partition splits selectedPlayers into teamHome and teamAway (each of size teamSize).
        // To avoid symmetry, we fix selectedPlayers.get(0) to always be in teamHome.
        int n = selectedPlayers.size();
        List<Integer> bestHomeIndices = null;
        long bestPenalty = Long.MAX_VALUE;
        
        List<List<Integer>> combinations = new ArrayList<>();
        generateCombinations(combinations, new ArrayList<>(), 1, n - 1, teamSize - 1);
        
        for (List<Integer> comb : combinations) {
            List<Integer> homeIdx = new ArrayList<>();
            homeIdx.add(0);
            homeIdx.addAll(comb);
            
            List<Integer> awayIdx = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if (!homeIdx.contains(i)) {
                    awayIdx.add(i);
                }
            }
            
            long penalty = calculatePartitionPenalty(selectedPlayers, homeIdx, awayIdx, mustKeepTogether, finalMm);
            if (penalty < bestPenalty) {
                bestPenalty = penalty;
                bestHomeIndices = homeIdx;
            }
        }
        
        if (bestHomeIndices != null) {
            for (int i = 0; i < n; i++) {
                if (bestHomeIndices.contains(i)) {
                    teamHome.add(selectedPlayers.get(i));
                } else {
                    teamAway.add(selectedPlayers.get(i));
                }
            }
        } else {
            int mid = n / 2;
            for (int i = 0; i < n; i++) {
                if (i < mid) {
                    teamHome.add(selectedPlayers.get(i));
                } else {
                    teamAway.add(selectedPlayers.get(i));
                }
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

    private long calculatePartitionPenalty(List<String> selectedPlayers, List<Integer> homeIdx, List<Integer> awayIdx,
                                           List<List<String>> mustKeepTogether, authserver.matchmaking.Matchmaker mm) {
        long penalty = 0;
        
        // 1. Must-Keep-Together components split penalty: 100,000 per split component
        for (List<String> comp : mustKeepTogether) {
            int inHome = 0;
            int inAway = 0;
            for (String email : comp) {
                int idx = selectedPlayers.indexOf(email);
                if (homeIdx.contains(idx)) {
                    inHome++;
                } else {
                    inAway++;
                }
            }
            if (inHome > 0 && inAway > 0) {
                penalty += 100000;
            }
        }
        
        // 2. Class reassignment penalty: 1,000 per reassigned player
        penalty += getTeamReassignmentCost(selectedPlayers, homeIdx, mm) * 1000;
        penalty += getTeamReassignmentCost(selectedPlayers, awayIdx, mm) * 1000;
        
        // 3. Other partner splits penalty: 1 per split partner pair (one-way or mutual)
        for (int i = 0; i < selectedPlayers.size(); i++) {
            for (int j = i + 1; j < selectedPlayers.size(); j++) {
                if (arePartners(selectedPlayers.get(i), selectedPlayers.get(j))) {
                    boolean sameTeam = (homeIdx.contains(i) && homeIdx.contains(j)) || (awayIdx.contains(i) && awayIdx.contains(j));
                    if (!sameTeam) {
                        penalty += 1;
                    }
                }
            }
        }
        
        return penalty;
    }

    private int getTeamReassignmentCost(List<String> selectedPlayers, List<Integer> teamIndices, authserver.matchmaking.Matchmaker mm) {
        int goalieChoices = 0;
        for (int idx : teamIndices) {
            String p = selectedPlayers.get(idx);
            if ("GOALIE".equalsIgnoreCase(mm.playerClasses.getOrDefault(p, "WARRIOR"))) {
                goalieChoices++;
            }
        }
        if (goalieChoices > 0) {
            return goalieChoices - 1;
        } else {
            return 1;
        }
    }

    private boolean areMutualPartners(String email1, String email2) {
        try {
            authserver.matchmaking.Matchmaker mm = authserver.SpringContextBridge.services().getMatchmaker();
            Set<String> s1 = mm.partnerPool.get(email1);
            Set<String> s2 = mm.partnerPool.get(email2);
            if (s1 == null || s2 == null) return false;
            
            String name1 = email1.split("@")[0];
            String name2 = email2.split("@")[0];
            
            boolean aWantsB = s1.contains(name2) || s1.contains(email2);
            boolean bWantsA = s2.contains(name1) || s2.contains(email1);
            
            return aWantsB && bWantsA;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean arePartners(String email1, String email2) {
        try {
            authserver.matchmaking.Matchmaker mm = authserver.SpringContextBridge.services().getMatchmaker();
            Set<String> s1 = mm.partnerPool.get(email1);
            Set<String> s2 = mm.partnerPool.get(email2);
            
            String name1 = email1.split("@")[0];
            String name2 = email2.split("@")[0];
            
            boolean aWantsB = (s1 != null && (s1.contains(name2) || s1.contains(email2)));
            boolean bWantsA = (s2 != null && (s2.contains(name1) || s2.contains(email1)));
            
            return aWantsB || bWantsA;
        } catch (Exception e) {
            return false;
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
            
            Game baseClone = (Game) deepClone(snapshot);
            if (baseClone == null) return;
            
            final Game finalBaseClone = baseClone;
            
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