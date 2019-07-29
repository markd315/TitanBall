package gameserver;

import authserver.SpringContextBridge;
import authserver.users.UserService;
import com.esotericsoftware.kryonet.Connection;
import com.rits.cloning.Cloner;
import networking.CandidateGame;
import networking.ClientPacket;
import networking.PlayerConnection;
import networking.PlayerDivider;
import util.Util;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GameTenant {
    public static final ServerMode SERVER_MODE = ServerMode.TRUETWO;
    public static ServerMode serverMode = SERVER_MODE;
    public static int PLAYERS; //instantiated in initialization block

    public GameEngine state;
    public String gameId;
    List<PlayerConnection> clients = new ArrayList<>();
    public List<List<Integer>> availableSlots;
    int claimIndex = 0;

    public GameTenant() {
    }

    static{
        initializeMode();
    }

    public void delegatePacket(Connection connection, ClientPacket request) {
        if (state == null || state.phase < 8) {
            addOrReplaceNewClient(connection, clients, request.token);
        }
        if (state != null) {
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
                System.out.println(c.getRemoteAddressUDP());
                queue.add(new PlayerConnection(nextUnclaimedSlot(), c, email));
            }
        }
        if(lobbyFull(queue)){
            startGame(queue);
        }
    }

    private void startGame(List<PlayerConnection> gameIncludedClients){
        System.out.println("starting full");
        List<PlayerDivider> players = playersFromConnections(gameIncludedClients);
        state = new GameEngine(gameId, players); //Start the game
        try {
            state.initializeServer();
            instantiateSpringContext();
            gameIncludedClients = this.monteCarloBalance(gameIncludedClients);
            int seconds = 5;
            for(int i=0; i<5; i++){
                Thread.sleep(1000);
                seconds -=1;
            }
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
        ScheduledExecutorService exec = Executors.newScheduledThreadPool(gameIncludedClients.size());

        System.out.println("reassigning client list on startgame");
        for(PlayerConnection p : clients){
            System.out.println(p.toString());
        }
        clients = gameIncludedClients;
        for(PlayerConnection p : clients){
            System.out.println(p.toString());
        }
        Runnable updateClients = () -> {
            //System.out.println("updating clients now");
            clients.parallelStream().forEach(client -> {
                PlayerDivider pd = dividerFromConn(client.getClient());
                //Optimizing clone away by only hacking in the needed var fails because of occasional concurrency issue
                Cloner cloner= new Cloner();
                GameEngine update = cloner.deepClone(state);
                update.underControl = state.titanSelected(pd);
                client.getClient().sendUDP(update);
            });
        };

        exec.scheduleAtFixedRate(updateClients, 1, 20, TimeUnit.MILLISECONDS);
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
            System.out.println(pl.email +  " " + userService.findUserByEmail(pl.email).getRating());
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

    public static void initializeMode() {
        if (serverMode == ServerMode.ALL) {
            PLAYERS = 1;
        }
        if (serverMode == ServerMode.TEAMS) {
            PLAYERS = 2;
        }
        if (serverMode == ServerMode.SOLONOGOL) {
            PLAYERS = 8;
        }
        if (serverMode == ServerMode.SOLOS) {
            PLAYERS = 10;
        }
        if (serverMode == ServerMode.TWOS) {
            PLAYERS = 4;
        }
        if (serverMode == ServerMode.THREES) {
            PLAYERS = 6;
        }
        if (serverMode == ServerMode.TRUETWO) {
            PLAYERS = 4;
        }
        if (serverMode == ServerMode.TRUETHREE) {
            PLAYERS = 6;
        }
        if (serverMode == ServerMode.ONEVTWO) {
            PLAYERS = 3;
        }
    }

    public GameTenant(String id){
        this.gameId = id;
        if(GameTenant.serverMode == ServerMode.TEAMS) {
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
        else if(GameTenant.serverMode == ServerMode.ALL) {
            this.availableSlots = new ArrayList<>();
            List<Integer> c1 = new ArrayList<>();
            c1.add(4);
            c1.add(3);
            c1.add(5);
            c1.add(6);
            c1.add(1);

            c1.add(8);
            c1.add(7);
            c1.add(9);
            c1.add(10);
            c1.add(2);
            this.availableSlots.add(c1);
        }
        else if(GameTenant.serverMode == ServerMode.SOLOS) {
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
        else if(GameTenant.serverMode == ServerMode.SOLONOGOL){
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
        else if(GameTenant.serverMode == ServerMode.THREES){
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
        else if(GameTenant.serverMode == ServerMode.TWOS){
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
        else if(GameTenant.serverMode == ServerMode.TRUETWO){
            this.availableSlots = new ArrayList<>();
            List<Integer> c3 = new ArrayList<>();
            List<Integer> c4 = new ArrayList<>();
            List<Integer> c5 = new ArrayList<>();
            List<Integer> c6 = new ArrayList<>();
            c3.add(3); //goalies are removed anyway if disabled for "true" 2v2
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
        else if(GameTenant.serverMode == ServerMode.TRUETHREE){
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
        else if(GameTenant.serverMode == ServerMode.ONEVTWO){
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
        }
    }
}
