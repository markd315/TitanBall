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
import io.socket.socketio.server.SocketIoNamespace;
import io.socket.socketio.server.SocketIoServer;
import io.socket.socketio.server.SocketIoSocket;
import networking.ClientPacket;
import networking.PlayerDivider;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class ServerApplication {
    public static final boolean PAYWALL = false;
    static Map<String, ManagedGame> states = null; //game UUID onto game

    static Matchmaker matchmaker;

    static PersistenceManager persistenceManager;

    static JwtTokenProvider tp = new JwtTokenProvider();

    static Properties prop;
    static String appSecret;

    static {
        try {
            System.out.println("Loading servlet properties");
            prop = new Properties();
            prop.load(new FileInputStream(new File("application.properties")));
            appSecret = prop.getProperty("app.jwtSecret");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void main(String[] args) throws IOException {
        System.out.println("servlet starting");
        states = new HashMap<>();

        final ServerWrapper serverWrapper = new ServerWrapper("127.0.0.1", 54555, null); // null means "allow all" as stated in https://github.com/socketio/engine.io-server-java/blob/f8cd8fc96f5ee1a027d9b8d9748523e2f9a14d2a/engine.io-server/src/main/java/io/socket/engineio/server/EngineIoServerOptions.java#L26
        try {
            System.out.println("Servlet wrapper created");
            serverWrapper.startServer();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("server listening 54555 for game changes");
        SocketIoServer serverSocket = serverWrapper.getSocketIoServer();
        SocketIoNamespace ns = serverSocket.namespace("/");
        ns.on("connection", args12 -> {
            SocketIoSocket socket = (SocketIoSocket) args12[0];
            System.out.println("Client " + socket.getId() + " (" + socket.getInitialHeaders().get("remote_addr") + ") has connected.");

            socket.on("controlsHeld", args1 -> {
                Object object = args1[0];
                if (object instanceof ClientPacket) {
                    String token = ((ClientPacket) object).token;
                    if (token == null) {
                        //System.out.println("token null");
                        return;
                    }
                    delegatePacket(socket, (ClientPacket) object);
                }
                System.out.println("[Client " + socket.getId() + "] " + object);
                //TODO move this somewhere else in the code, we should only be doing it periodically.
                socket.send("gameState", "test message", 1);
            });

        });
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

    public static void delegatePacket(SocketIoSocket connection, ClientPacket packet) {
        instantiateSpringContext();
        checkGameExpiry();
        //System.out.println("delegating from game " + packet.gameID);
        //System.out.println("game map size: " + states.size());
        if (states.containsKey(packet.gameID)) {
            ManagedGame state = states.get(packet.gameID);
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
            ManagedGame val = states.get(id);
            if (val.state != null && val.state.ended) {
                System.out.println("ENDING GAME");
                System.out.println(val.options.toStringSrv());
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
