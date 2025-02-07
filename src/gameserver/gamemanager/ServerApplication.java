package gameserver.gamemanager;

import authserver.SpringContextBridge;
import authserver.jwt.JwtTokenProvider;
import authserver.matchmaking.Match;
import authserver.matchmaking.Matchmaker;
import authserver.matchmaking.Rating;
import authserver.models.User;
import authserver.users.PersistenceManager;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.FrameworkMessage;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import gameserver.engine.GameEngine;
import gameserver.engine.GameOptions;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.Titan;
import networking.ClientPacket;
import networking.KryoRegistry;
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


    public static void main(String[] args) throws IOException {
        Server server = new Server(8 * 1024 * 1024, 1024 * 1024); //8mb and 1mb
        Kryo kryo = server.getKryo();
        KryoRegistry.register(kryo);
        server.start();
        //gameserver.setHardy(true);
        server.bind(54555);
        server.addListener(new Listener() {
            public void received(Connection connection, Object object) {
                if (connection.getID() > 0) {
                    if (object instanceof FrameworkMessage.KeepAlive) {
                        // delegate keepalives so that game will start
                        delegatePacket(connection, null);
                    }
                    if (object instanceof ClientPacket) {
                        String token = ((ClientPacket) object).token;
                        if (token == null) {
                            //System.out.println("token null");
                            return;
                        }
                        delegatePacket(connection, (ClientPacket) object);
                    }
                }
            }
        });
        System.out.println("server listening 54555 for game changes");
    }

    public static void addNewGame(String id, GameOptions op, Collection<String> gameFor) {
        System.out.println("adding new game, id " + id);
        cleanupCorruptStates(gameFor);
        states.put(id, new ManagedGame(id, op));
        System.out.println("game map size: " + states.size());
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

    public static void delegatePacket(Connection connection, ClientPacket packet) {
        instantiateSpringContext();
        checkGameExpiry();
        //System.out.println("delegating from game " + packet.gameID);
        //System.out.println("game map size: " + states.size());
        try {
            if (states.containsKey(packet.gameID)) {
                ManagedGame state = states.get(packet.gameID);
                //System.out.println("passing connection " + connection.getID() + " to game " + state.gameId);
                state.delegatePacket(connection, packet);
            }
            else {
                //Rejoin logic for when the token has the right email but the connection is new.
                //This is a hack to fix the issue of rejoining a game with a new connection
                System.out.println("found a packet for a new connection but an existing game+user");
                String email = Util.jwtExtractEmail(packet.token);
                for (ManagedGame mg : states.values()) {
                    System.out.println("checking " + mg.gameId);
                    if (mg.gameContainsEmail(Collections.singleton(email))) {
                        System.out.println("found a game for " + email + " to rejoin");
                        mg.replaceConnectionForSameUser(connection, packet.token);
                        System.out.println("passing connection " + connection.getID() + " to game " + mg.gameId);
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

    private static void instantiateSpringContext() {
        persistenceManager = SpringContextBridge.services().getPersistenceManager();
        matchmaker = SpringContextBridge.services().getMatchmaker();
    }

    private static Set<String> rm = new HashSet<>();

    private static void checkGameExpiry() {
        for (String id : states.keySet()) {
            if (rm.contains(id)) {
                continue;
            }
            ManagedGame val = states.get(id);
            if (val.state != null && val.state.ended) {
                val.exec.shutdown();
                val.stateRef.set(val.state); // everyone gets the final packet one time.
                System.out.println("ENDING GAME");
                System.out.println("options: " + val.options.toStringSrv());
                System.out.println(val.stateRef.get().toString());
                val.terminateConnections(val.stateRef);
                if(val.options.toStringSrv().equals("/3/1/1/5/2/9999/10/12")) {//default public mode only for ratings
                    injectRatingsToPlayers(val.state);
                    for (PlayerDivider player : val.state.clients) {
                        try {
                            Titan t = val.state.titanSelected(player);
                            if (t != null) {
                                String className = t.getType().toString();
                                persistenceManager.postgameStats(player.email, val.state.stats, className, player.wasVictorious, player.newRating);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                if(val.options.toStringSrv().equals("/1/1/1/5/2/9999/10/12")) {//1v1 ratings mode
                    inject1v1RatingsToPlayers(val.state);//This method is new
                    for (PlayerDivider player : val.state.clients) {
                        try {
                            Titan t = val.state.titanSelected(player);
                            if (t != null) {
                                String className = t.getType().toString();
                                //This method is new
                                persistenceManager.postgameStats1v1(player.email, val.state.stats, className, player.wasVictorious, player.newRating);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                rm.add(id);
                matchmaker.endGame(id);
            }
        }
        for (String id : rm) {
            states.remove(id);
        }
    }

    private static void injectRatingsToPlayers(GameEngine state) {
        List<Rating> home = new ArrayList<>(), away = new ArrayList<>();
        for (PlayerDivider pl : state.clients) {
            User persistence = persistenceManager.userService.findUserByEmail(pl.email);
            //System.out.println("got user " + persistence.getEmail() + persistence.getRating());
            Rating<User> oldRating = new Rating<>(persistence, persistence.getLosses() + persistence.getWins());
            if (state.players[pl.getSelection() - 1].team == TeamAffiliation.HOME) {
                oldRating.setRating(persistence.getRating());
                home.add(oldRating);
            } else {
                oldRating.setRating(persistence.getRating());
                away.add(oldRating);
            }
        }
        Rating<String> homeRating = new Rating<>(home, "home", 0);
        Rating<String> awayRating = new Rating<>(away, "away", 0);
        Match<User> match = new Match(homeRating, awayRating, state.home.score - state.away.score);
        //System.out.println(match.winMargin + "");
        match.injectAverage(home, away);
        for (PlayerDivider pl : state.clients) {
            updatePlayerRating(pl, home);
            updatePlayerRating(pl, away);
        }
    }

    private static void inject1v1RatingsToPlayers(GameEngine state) {
        List<Rating> home = new ArrayList<>(), away = new ArrayList<>();
        for (PlayerDivider pl : state.clients) {
            User persistence = persistenceManager.userService.findUserByEmail(pl.email);
            //System.out.println("got user " + persistence.getEmail() + persistence.getRating());
            Rating<User> oldRating = new Rating<>(persistence, persistence.getLosses_1v1() + persistence.getWins_1v1());
            if (state.players[pl.getSelection() - 1].team == TeamAffiliation.HOME) {
                oldRating.setRating(persistence.getRating_1v1());
                home.add(oldRating);
            } else {
                oldRating.setRating(persistence.getRating_1v1());
                away.add(oldRating);
            }
        }
        Rating<String> homeRating = new Rating<>(home, "home", 0);
        Rating<String> awayRating = new Rating<>(away, "away", 0);
        Match<User> match = new Match(homeRating, awayRating, state.home.score - state.away.score);
        //System.out.println(match.winMargin + "");
        match.injectAverage(home, away);
        for (PlayerDivider pl : state.clients) {
            updatePlayerRating(pl, home);
            updatePlayerRating(pl, away);
        }
    }

    private static void updatePlayerRating(PlayerDivider pl, List<Rating> team) {
        for (Rating<User> r : team) {
            if (r.getID().getEmail().equals(pl.email)) {
                //System.out.println(r.rating + " new");
                pl.newRating = r.rating;
            }
        }
    }
}
