package gameserver.gamemanager;

import authserver.SpringContextBridge;
import authserver.models.User;
import authserver.users.identities.UserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gameserver.Const;
import gameserver.effects.EffectId;
import gameserver.effects.EffectPool;
import gameserver.engine.GameEngine;
import gameserver.engine.GameOptions;
import gameserver.entity.Entity;
import gameserver.entity.Titan;
import networking.CandidateGame;
import networking.ClientPacket;
import networking.PlayerDivider;
import util.Util;

import java.util.*;

public class ManagedGame {
    public static final ServerMode SERVER_MODE = ServerMode.TRUETHREE;
    public GameOptions options;
    protected Const c = new Const("res/game.cfg");

    public GameEngine state;
    public String gameId;
    public List<PlayerDivider> gameStartQueue = new ArrayList<>();
    public List<PlayerDivider> clients;
    public List<List<Integer>> availableSlots;
    int claimIndex = 0;

    private UserService userService;

    public void delegatePacket(PlayerDivider pl, ClientPacket request) {
        String email = Util.jwtExtractEmail(request.token);
        addOrReplaceNewClient(email);
        if(state != null){
            System.out.println("delegating from player " + pl);
            state.processClientPacket(pl, request);
        }
        else{
            System.out.println("server still not ready to start!");
        }
    }

    public PlayerDivider playerFromToken(String token){
        String email = Util.jwtExtractEmail(token);
        PlayerDivider pd = null;
        System.out.println("clients" + clients);
        for(PlayerDivider p : clients){
            System.out.println("need a email match for user " + p.getEmail());
            if(p.getEmail().equals(email)){
                System.out.println("matched an email (phew)");
                pd = p;
                pd.setEmail(email);
            }
        }
        return pd;
    }

    List<Integer> nextUnclaimedSlot(){
        claimIndex++;
        return availableSlots.get(claimIndex -1);
    }

    public void addOrReplaceNewClient(String email){
        if (!accountQueued(gameStartQueue, email)) {
            System.out.println("adding NEW client");
            gameStartQueue.add(new PlayerDivider(nextUnclaimedSlot(), email));
            if(lobbyFull(gameStartQueue)){
                this.clients = gameStartQueue;
                System.out.println("starting game!");
                startGame();
            }
        }
    }

    private void startGame(){
        instantiateSpringContext();
        System.out.println("starting full with " + clients.size() + " users and selections ");
        this.clients = this.monteCarloBalance(this.clients);
        System.out.println("starting full with " + clients.size() + " users and selections ");
        for(PlayerDivider cl : clients){
            for(Integer i : cl.getPossibleSelection()){
                System.out.print(" " + i);
            }
            System.out.println();
        }
        System.out.println("updating state with gameoptions " + options.toStringSrv());
        state  = new GameEngine(gameId, clients, new GameOptions(options.toStringSrv())); //Start the game
        waitFive();
        state.initializeServer();
        state.clients = clients;
    }

    private void waitFive() {
        try {
            for(int i=0; i<5; i++){
                Thread.sleep(950);
            }
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public GameEngine anticheat(GameEngine update) {
        Titan underControl = update.underControl;
        EffectPool fx = update.effectPool;
        if(fx.hasEffect(underControl, EffectId.BLIND)){
            for(Titan player : update.players){
                if(!player.id.equals(underControl)){
                    censor(player);
                }
            }
            for(Entity ent : update.entityPool){
                if(!ent.id.equals(underControl)){
                    censor(ent);
                }
            }
            update.ball.X = 9999;
            update.ball.Y = 9999;
        }
        for(Titan player : update.players){
            if(fx.hasEffect(player, EffectId.STEALTHED)
            && !fx.hasEffect(player, EffectId.FLARE)){
                if(player.team != underControl.team){
                    censor(player);
                }
            }
        }
        for(Entity entity : update.entityPool){
            if(fx.hasEffect(entity, EffectId.STEALTHED)
                    && !fx.hasEffect(entity, EffectId.FLARE)){
                if(entity.team != underControl.team){
                    censor(entity);
                }
            }
        }
        return update;
    }

    private void censor(Entity player) {
        player.X = 99999;
        player.Y = 99999;
    }

    boolean lobbyFull(List<PlayerDivider> pd){
        List<String> uniqueEmails = new ArrayList<>();
        for(PlayerDivider p : pd){
            if(!uniqueEmails.contains(p.getEmail())){
                uniqueEmails.add(p.getEmail());
            }
        }
        return (uniqueEmails.size() == availableSlots.size());
    }

    private boolean accountQueued(List<PlayerDivider> queue, String email) {
        boolean emailFound = false;
        for(PlayerDivider p : queue){
            if (p.getEmail().equals(email)){
                emailFound = true;
            }
        }
        return emailFound;
    }

    private List<PlayerDivider> monteCarloBalance(List<PlayerDivider> players) {
        Map<PlayerDivider, Double> tempRating = new HashMap<>();
        instantiateSpringContext();
        System.out.println("players size" + players.size());
        for (PlayerDivider pl : players) {
            User user = userService.findUserByEmail(pl.email);
            if(user == null){
                user = userService.findUserByUsername(pl.email);
            }
            System.out.println(pl.email + " " + user.getRating());
            //Currently pl says email, but may actually be a USERNAME, we need to fix that
            pl.email = user.getEmail();
            tempRating.put(pl, user.getRating());
        }
        CandidateGame candidateGame = new CandidateGame();
        return candidateGame.bestMonteCarloBalance(availableSlots, tempRating);
    }

    private void instantiateSpringContext() {
        userService = SpringContextBridge.services().getUserService();
    }


    public ManagedGame(String id, GameOptions op){
        this.gameId = id;
        this.options = op;
        ObjectMapper mapper = new ObjectMapper();
        try {
            System.out.println("tenant options");
            System.out.println(mapper.writeValueAsString(this.options));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        if(op.playerIndex == 1) { //player switching working, but disabled for now
            this.availableSlots = new ArrayList<>();
            ArrayList<Integer> c1 = new ArrayList<>();
            c1.add(4);
            c1.add(1);
            c1.add(3);
            c1.add(5);
            c1.add(6);
            ArrayList<Integer> c2 = new ArrayList<>();
            c2.add(8);
            c2.add(2); //second goalie
            c2.add(7);
            c2.add(9);
            c2.add(10);
            this.availableSlots.add(c1);
            this.availableSlots.add(c2);
        }
        if(op.playerIndex == 1) {
            this.availableSlots = new ArrayList<>();
            ArrayList<Integer> c1 = new ArrayList<>();
            c1.add(3);
            c1.add(1);
            ArrayList<Integer> c2 = new ArrayList<>();
            c2.add(4);
            c2.add(2);
            this.availableSlots.add(c1);
            this.availableSlots.add(c2);
        }
        else if(op.playerIndex == 0) {
            this.availableSlots = new ArrayList<>();
            List<Integer> c1 = new ArrayList<>();
            c1.add(3);
            c1.add(4);
            c1.add(5);
            c1.add(6);
            c1.add(7);
            c1.add(1);

            c1.add(8);
            c1.add(9);
            c1.add(10);
            c1.add(11);
            c1.add(12);
            c1.add(2);
            this.availableSlots.add(c1);
        }
        else if(op.playerIndex == 5) {
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
        else if(op.playerIndex == 4){
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
        else if(op.playerIndex == 3 && false){ //pswitch disabled for now
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
        else if(op.playerIndex == 2 && false){ //disabled for now
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
        else if(op.playerIndex == 2){
            this.availableSlots = new ArrayList<>();
            List<Integer> c3 = new ArrayList<>();
            List<Integer> c4 = new ArrayList<>();
            List<Integer> c5 = new ArrayList<>();
            List<Integer> c6 = new ArrayList<>();
            c3.add(3);
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
        else if(op.playerIndex == 3){
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
        /*
        else if(GameTenant.serverMode == ServerMode.ONEVTWO){ //disabled for now
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
        }*/
    }
}
