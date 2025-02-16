package gameserver.gamemanager;

import authserver.SpringContextBridge;
import authserver.jwt.JwtTokenProvider;
import authserver.matchmaking.Match;
import authserver.matchmaking.Matchmaker;
import authserver.matchmaking.Rating;
import authserver.models.User;
import authserver.users.PersistenceManager;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import gameserver.engine.GameEngine;
import gameserver.engine.GameOptions;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.Titan;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
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
    private static final int PORT = 54555;
    static Map<String, ManagedGame> states = new HashMap<>(); //game UUID onto game

    static Matchmaker matchmaker;

    static PersistenceManager persistenceManager;

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

    public static void startServer() {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        Channel serverChannel = null;
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class)
             .option(ChannelOption.SO_BACKLOG, 100)
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 public void initChannel(SocketChannel ch) {
                     ch.pipeline().addLast(
                         new HttpServerCodec(),
                         new HttpObjectAggregator(65536),
                         new WebSocketServerProtocolHandler("/ws"),
                         new SimpleChannelInboundHandler<TextWebSocketFrame>() {
                             @Override
                             protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
                                 String message = frame.text();
                                 Object object = KryoRegistry.deserializeWithKryo(message);
                                 if (object instanceof ClientPacket clientPacket) {
                                     if (clientPacket.token == null) {
                                         return;
                                     }
                                     delegatePacket(ctx.channel(), clientPacket);
                                 }
                             }

                             @Override
                             public void handlerAdded(ChannelHandlerContext ctx) {
                                 System.out.println("Client connected: " + ctx.channel().id());
                             }

                             @Override
                             public void handlerRemoved(ChannelHandlerContext ctx) {
                                 System.out.println("Client disconnected: " + ctx.channel().id());
                             }
                         }
                     );
                 }
             });

            serverChannel = b.bind(PORT).syncUninterruptibly().channel();
            System.out.println("WebSocket server listening on port " + PORT);

            // Ensure the server stays open and processes events
            serverChannel.closeFuture().addListener(future -> {
                // This listener will be triggered when the server channel is closed
                System.out.println("Server closed.");
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            });

            // Main thread will continue running here, allowing other setup operations
            System.out.println("Main thread is running, WebSocket server is active.");

            // Keep the server running indefinitely
            // This can be done using a "dummy" blocking call, or a different mechanism for a proper shutdown signal.
            // For example, you can use a `CountDownLatch` or `Thread.sleep()`, etc.
            Thread.sleep(Long.MAX_VALUE);  // Keeps the main thread running indefinitely, ensuring the server stays open

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // Graceful shutdown hook to ensure cleanup when the process is terminated
            Channel finalServerChannel = serverChannel;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (finalServerChannel != null && finalServerChannel.isOpen()) {
                    finalServerChannel.close();
                }
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            }));
        }
    }

    public static void main(String[] args) throws IOException {
        Kryo kryo = new Kryo();
        KryoRegistry.register(kryo);
        new Thread(ServerApplication::startServer).start();
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

    public static void delegatePacket(Channel connection, ClientPacket packet) {
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
                        System.out.println("passing connection " + connection.id() + " to game " + mg.gameId);
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
