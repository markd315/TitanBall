package gameserver.gamemanager;

import authserver.SpringContextBridge;
import authserver.jwt.JwtTokenProvider;
import authserver.matchmaking.Match;
import authserver.matchmaking.Matchmaker;
import authserver.matchmaking.Rating;
import authserver.models.User;
import authserver.users.PersistenceManager;
import gameserver.engine.GameEngine;
import gameserver.engine.GameOptions;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.Titan;
import networking.ClientPacket;

import networking.PlayerDivider;
import util.Util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class ServerApplication {
    public static final boolean PAYWALL = false;
    static Map<String, ManagedGame> states = new HashMap<>(); //game UUID onto game

    static Matchmaker matchmaker;

    static PersistenceManager persistenceManager;

    static JwtTokenProvider tp = new JwtTokenProvider();

    static Properties prop;
    static String appSecret;

    static {
        try {
            prop = new Properties();
            prop.load(new FileInputStream(new File("application.properties")));
            appSecret = prop.getProperty("app.jwtSecret");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void addNewGame(String id, GameOptions op, Collection<String> gameFor) {
        instantiateSpringContext();
        System.out.println("adding new game, id " + id);
        cleanupCorruptStates(gameFor);
        states.put(id, new ManagedGame(id, op, gameFor));
        System.out.println("game map size: " + states.size());
    }

    public static void addNewTutorial(String id, String email) {
        instantiateSpringContext();
        System.out.println("adding new tutorial, id " + id + " for " + email);
        cleanupCorruptStates(Collections.singletonList(email));
        GameOptions op = new GameOptions("/1/1/1/5/2/9999/10/12");
        ManagedGame mg = new ManagedGame(id, op);
        mg.availableSlots = new ArrayList<>();
        // Enforce bijective mapping: availableSlots is now a flat List<Integer>
        mg.availableSlots.add(3);
        mg.availableSlots.add(1);
        states.put(id, mg);
        matchmaker.registerTutorialGame(email, id);
    }

    private static void cleanupCorruptStates(Collection<String> gameFor) {
        Set<String> rm = new HashSet<>();
        for(String id : states.keySet()){
            ManagedGame gt = states.get(id);
            boolean userFound = gt.gameContainsEmail(gameFor);
            if(userFound){
                rm.add(id);
            }
        }
        for(String id : rm){
            System.out.println("removed a corrupt state! (somehow)");
            states.remove(id);//avoid comod
        }
    }

    private static long lastExpiryCheckMs = 0;

    public static void delegatePacket(networking.WebSocketPlayerConnection connection, ClientPacket packet) {
        if (persistenceManager == null || matchmaker == null) {
            instantiateSpringContext();
        }
        long now = System.currentTimeMillis();
        if (now - lastExpiryCheckMs > 1000) {
            lastExpiryCheckMs = now;
            checkGameExpiry();
        }
        try {
            if (states.containsKey(packet.gameID)) {
                ManagedGame state = states.get(packet.gameID);
                state.delegatePacket(connection, packet);
            }
            else {
                System.out.println("found a packet for a new connection but an existing game+user");
                String email = Util.jwtExtractEmail(packet.token);
                for (ManagedGame mg : states.values()) {
                    System.out.println("checking " + mg.gameId);
                    if (mg.gameContainsEmail(Collections.singleton(email))) {
                        System.out.println("found a game for " + email + " to rejoin");
                        mg.replaceConnectionForSameUser(connection, packet.token);
                        System.out.println("passing connection " + connection.id + " to game " + mg.gameId);
                        mg.delegatePacket(connection, packet);
                    }
                }
            }
        } catch (IllegalArgumentException ex) {
            //need a new game created, this should only be triggered if the same user tries to join a new game
        }
        catch (NullPointerException ex1) {
            ex1.printStackTrace();
            System.out.println("NullPointerException receving packet with no gameid at end of game, ignoring");
        }
    }

    private static final java.util.concurrent.ScheduledExecutorService expiryScheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
    static {
        expiryScheduler.scheduleWithFixedDelay(() -> {
            try {
                checkGameExpiry();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 500, 500, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public static void triggerGameExpiry() {
        try {
            checkGameExpiry();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void instantiateSpringContext() {
        if (persistenceManager == null || matchmaker == null) {
            try {
                persistenceManager = SpringContextBridge.services().getPersistenceManager();
                matchmaker = SpringContextBridge.services().getMatchmaker();
            } catch (Exception ignored) {}
        }
    }

    private static final Map<String, Long> endedGames = new java.util.concurrent.ConcurrentHashMap<>();

    public static synchronized void checkGameExpiry() {
        instantiateSpringContext();
        long now = System.currentTimeMillis();
        List<String> currentIds = new ArrayList<>(states.keySet());
        for (String id : currentIds) {
            ManagedGame val = states.get(id);
            if (val != null && val.state != null && val.state.ended) {
                if (!endedGames.containsKey(id)) {
                    endedGames.put(id, now);
                    val.stateRef.set(val.state); // everyone gets the final packet
                    System.out.println("ENDING GAME: " + id);
                    if (val.options != null) {
                        System.out.println("options: " + val.options.toStringSrv());
                    }
                    if (val.stateRef.get() != null) {
                        System.out.println(val.stateRef.get().toString());
                    }
                    val.terminateConnections(val.stateRef);

                    boolean isTutorial = (id != null && id.startsWith("tutorial-"));
                    if (!isTutorial && persistenceManager != null && val.options != null) {
                        boolean is1v1 = (val.options.playerIndex == 4 
                                || "/1/1/1/5/2/9999/10/12".equals(val.options.toStringSrv())
                                || "/4/1/1/5/2/9999/10/12".equals(val.options.toStringSrv())
                                || val.options.allowsNoGoalie());
                        
                        if (is1v1) {
                            System.out.println("Recording 1v1 postgame stats for game: " + id);
                            inject1v1RatingsToPlayers(val.state);
                            for (PlayerDivider player : val.state.clients) {
                                try {
                                    Titan t = val.state.titanSelected(player);
                                    if (t != null && player.email != null) {
                                        String className = t.getType() != null ? t.getType().toString() : "WARRIOR";
                                        persistenceManager.postgameStats1v1(player.email, val.state.stats, className, player.wasVictorious, player.newRating);
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        } else {
                            System.out.println("Recording team postgame stats for game: " + id);
                            injectRatingsToPlayers(val.state);
                            for (PlayerDivider player : val.state.clients) {
                                try {
                                    Titan t = val.state.titanSelected(player);
                                    if (t != null && player.email != null) {
                                        String className = t.getType() != null ? t.getType().toString() : "WARRIOR";
                                        persistenceManager.postgameStats(player.email, val.state.stats, className, player.wasVictorious, player.newRating);
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                } else if (now - endedGames.get(id) > 5000) {
                    // Grace period of 5 seconds elapsed, clean up game resources
                    if (val.exec != null && !val.exec.isShutdown()) {
                        val.exec.shutdown();
                    }
                    states.remove(id);
                    endedGames.remove(id);
                    if (matchmaker != null) {
                        matchmaker.endGame(id);
                    }
                }
            }
        }
    }

    private static void injectRatingsToPlayers(GameEngine state) {
        if (state == null || state.clients == null || persistenceManager == null || persistenceManager.userService == null) return;
        List<Rating> home = new ArrayList<>(), away = new ArrayList<>();
        for (PlayerDivider pl : state.clients) {
            if (pl.email == null) continue;
            User persistence = persistenceManager.userService.findUserByEmail(pl.email);
            if (persistence == null) continue;
            int totalGames = (persistence.getLosses() != null ? persistence.getLosses() : 0) + (persistence.getWins() != null ? persistence.getWins() : 0);
            Rating<User> oldRating = new Rating<>(persistence, totalGames);
            double curRating = persistence.getRating() != null ? persistence.getRating() : 1000.0;
            Titan t = state.titanSelected(pl);
            if (t != null && t.team == TeamAffiliation.HOME) {
                oldRating.setRating(curRating);
                home.add(oldRating);
            } else if (t != null && t.team == TeamAffiliation.AWAY) {
                oldRating.setRating(curRating);
                away.add(oldRating);
            }
        }
        if (home.isEmpty() || away.isEmpty()) return;
        Rating<String> homeRating = new Rating<>(home, "home", 0);
        Rating<String> awayRating = new Rating<>(away, "away", 0);
        double diff = (state.home != null && state.away != null) ? (state.home.score - state.away.score) : 0.0;
        Match<String> match = new Match<>(homeRating, awayRating, diff);
        match.injectAverage(home, away);
        for (PlayerDivider pl : state.clients) {
            updatePlayerRating(pl, home);
            updatePlayerRating(pl, away);
        }
    }

    private static void inject1v1RatingsToPlayers(GameEngine state) {
        if (state == null || state.clients == null || persistenceManager == null || persistenceManager.userService == null) return;
        List<Rating> home = new ArrayList<>(), away = new ArrayList<>();
        for (PlayerDivider pl : state.clients) {
            if (pl.email == null) continue;
            User persistence = persistenceManager.userService.findUserByEmail(pl.email);
            if (persistence == null) continue;
            int totalGames = (persistence.getLosses_1v1() != null ? persistence.getLosses_1v1() : 0) + (persistence.getWins_1v1() != null ? persistence.getWins_1v1() : 0);
            Rating<User> oldRating = new Rating<>(persistence, totalGames);
            double curRating = persistence.getRating_1v1() != null ? persistence.getRating_1v1() : 1000.0;
            Titan t = state.titanSelected(pl);
            if (t != null && t.team == TeamAffiliation.HOME) {
                oldRating.setRating(curRating);
                home.add(oldRating);
            } else if (t != null && t.team == TeamAffiliation.AWAY) {
                oldRating.setRating(curRating);
                away.add(oldRating);
            }
        }
        if (home.isEmpty() || away.isEmpty()) return;
        Rating<String> homeRating = new Rating<>(home, "home", 0);
        Rating<String> awayRating = new Rating<>(away, "away", 0);
        double diff = (state.home != null && state.away != null) ? (state.home.score - state.away.score) : 0.0;
        Match<String> match = new Match<>(homeRating, awayRating, diff);
        match.injectAverage(home, away);
        for (PlayerDivider pl : state.clients) {
            updatePlayerRating(pl, home);
            updatePlayerRating(pl, away);
        }
    }

    private static void updatePlayerRating(PlayerDivider pl, List<Rating> team) {
        for (Rating<User> r : team) {
            if (r != null && r.getID() != null && r.getID().getEmail() != null && r.getID().getEmail().equals(pl.email)) {
                pl.newRating = r.rating;
            }
        }
    }
}