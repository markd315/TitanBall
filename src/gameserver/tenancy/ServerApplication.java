package gameserver.tenancy;

import authserver.SpringContextBridge;
import authserver.jwt.JwtTokenProvider;
import authserver.matchmaking.Match;
import authserver.matchmaking.Matchmaker;
import authserver.matchmaking.Rating;
import authserver.models.User;
import authserver.users.PersistenceManager;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import gameserver.engine.GameEngine;
import gameserver.engine.GameOptions;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.Titan;
import networking.ClientPacket;
import networking.KryoRegistry;
import networking.PlayerDivider;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class ServerApplication {
    public static final boolean PAYWALL = false;
    static Map<String, GameTenant> states = null; //game UUID onto game

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
        Server server = new Server(16384 * 8, 2048 * 8);
        Kryo kryo = server.getKryo();
        KryoRegistry.register(kryo);
        server.start();
        //gameserver.setHardy(true);
        server.bind(54555, 54556);

        states = new HashMap<>();

        server.addListener(new Listener() {
            public void received(Connection connection, Object object) {
                if (connection.getID() > 0) {
                    if (object instanceof ClientPacket) {
                        String token = ((ClientPacket) object).token;
                        if (token == null) {
                            System.out.println("token null");
                            return;
                        }
                        delegatePacket(connection, (ClientPacket) object);
                    }
                }
            }
        });
        System.out.println("server listening 54555 for game changes");
    }

    public static void addNewGame(String id, GameOptions op, Set<String> gameFor) {
        System.out.println("adding new game, id " + id);
        cleanupCorruptStates(gameFor);
        states.put(id, new GameTenant(id, op));
        System.out.println("game map size: " + states.size());
    }

    private static void cleanupCorruptStates(Set<String> gameFor) {
        Set<String> rm = new HashSet<>();
        for(String id : states.keySet()){
            GameTenant gt = states.get(id);
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
        if (states.containsKey(packet.gameID)) {
            GameTenant state = states.get(packet.gameID);
            try {
                //System.out.println("passing connection " + connection.getID() + " to game " + state.gameId);
                state.delegatePacket(connection, packet);
            } catch (IllegalArgumentException ex) {
                //need a new game created, this should only be triggered if the same user tries to join a new game
            }
        }
    }

    private static void instantiateSpringContext() {
        persistenceManager = SpringContextBridge.services().getPersistenceManager();
        matchmaker = SpringContextBridge.services().getMatchmaker();
    }

    private static void checkGameExpiry() {
        Set<String> rm = new HashSet<>();
        for (String id : states.keySet()) {
            GameTenant val = states.get(id);
            if (val.state != null && val.state.ended) {
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

    private static void updatePlayerRating(PlayerDivider pl, List<Rating> team) {
        for (Rating<User> r : team) {
            if (r.getID().getEmail().equals(pl.email)) {
                //System.out.println(r.rating + " new");
                pl.newRating = r.rating;
            }
        }
    }
}
