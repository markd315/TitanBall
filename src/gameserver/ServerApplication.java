package gameserver;

import authserver.SpringContextBridge;
import authserver.matchmaking.Match;
import authserver.matchmaking.Matchmaker;
import authserver.matchmaking.Rating;
import authserver.models.User;
import authserver.users.UserService;
import client.ClientPacket;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import gameserver.engine.TeamAffiliation;
import networking.GameTenant;
import networking.KryoRegistry;
import networking.PlayerDivider;

import java.io.IOException;
import java.util.*;

public class ServerApplication {
    static Map<String, GameTenant> states = null; //UUID onto game

    static Matchmaker matchmaker;

    static UserService userService;

    public static void main(String[] args) throws IOException {
        Server server = new Server(16384*8, 2048*8);
        Kryo kryo = server.getKryo();
        KryoRegistry.register(kryo);
        server.start();
        //gameserver.setHardy(true);
        server.bind(54555, 54556);

        states = new HashMap<>();

        server.addListener(new Listener() {
            public void received (Connection connection, Object object) {
                if(connection.getID() > 0) {
                    if(object instanceof ClientPacket){
                        if(((ClientPacket) object).token == null){
                            return; //TODO stricter assertions
                        }
                        delegatePacket(connection, (ClientPacket) object);
                    }
                }
            }
        } );
        System.out.println("server listening 54555 for game changes");
    }

    public static void addNewGame(String id){
        states.put(id, new GameTenant(id));
    }

    public static void delegatePacket(Connection connection, ClientPacket packet){
        instantiateSpringContext();
        checkGameExpiry();
        if(states.containsKey(packet.gameID)){
            GameTenant state = states.get(packet.gameID);
            try {
                state.delegatePacket(connection, packet);
            }
            catch (IllegalArgumentException ex){
                //need a new game created, this should only be triggered if the same user tries to join a new game
            }
        }
    }

    private static void instantiateSpringContext() {
        userService = SpringContextBridge.services().getUserService();
        matchmaker = SpringContextBridge.services().getMatchmaker();
    }

    private static void checkGameExpiry() {
        Set<String> rm = new HashSet<>();
        for(String id : states.keySet()){
            GameTenant val = states.get(id);
            if(val != null && val.state!= null && val.state.ended){
                injectRatingsToPlayers(val.state);
                for(PlayerDivider player: val.state.clients) {
                    try {
                        userService.postgameStats(player.email, val.state.stats, player.wasVictorious, player.newRating);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                rm.add(id);
                matchmaker.endGame(id);
            }
        }
        for(String id : rm) {
            states.remove(id);
        }
    }

    private static void injectRatingsToPlayers(GameEngine state) {
        List<Rating> home = new ArrayList<>(), away = new ArrayList<>();
        for(PlayerDivider pl : state.clients){
            User persistence = userService.findUserByEmail(pl.email);
            //System.out.println("got user " + persistence.getEmail() + persistence.getRating());
            Rating<User> oldRating = new Rating<>(persistence, persistence.getLosses()+persistence.getWins());
            if(state.players[pl.getSelection() - 1].team == TeamAffiliation.HOME){
                oldRating.setRating(persistence.getRating());
                home.add(oldRating);
            }
            else{
                oldRating.setRating(persistence.getRating());
                away.add(oldRating);
            }
        }
        Rating<String> homeRating = new Rating<>(home, "home", 0);
        Rating<String> awayRating = new Rating<>(away, "away", 0);
        Match<User> match = new Match(homeRating, awayRating, state.home.score - state.away.score);
        //System.out.println(match.winMargin + "");
        match.injectAverage(home, away);
        for(PlayerDivider pl : state.clients){
            updatePlayerRating(pl, home);
            updatePlayerRating(pl, away);
        }
    }

    private static void updatePlayerRating(PlayerDivider pl, List<Rating> team) {
        for(Rating<User> r : team){
            if(r.getID().getEmail().equals(pl.email)){
                //System.out.println(r.rating + " new");
                pl.newRating = r.rating;
            }
        }
    }
}
