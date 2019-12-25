package authserver.matchmaking;

import gameserver.ServerApplication;
import gameserver.engine.GameOptions;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

@Component
public class Matchmaker {
    static{//Boot server for matchmaker
        String[] args = new String[0];
        try {
            ServerApplication.main(args);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private Map<String, String> waitingPool = new HashMap<>();//user emails
    private Map<String, String> gameMap = new HashMap<>();//user emails -> game id
    private int desperation = 0; //TODO increase to eventually sacrifice match quality

    public String findGame(Authentication login){
        String email = login.getName();
        if(gameMap.containsKey(email)){
            return gameMap.get(email);
        }
        if(waitingPool.containsKey(email)) {
            return "WAITING";
        }
        return "NOT QUEUED";
    }

    private void makeMatches() {
        Set<String> gameFor = new HashSet<>();
        GameOptions op = null;
        for(String val : waitingPool.values()){
            int count = 0;
            //detect if max people queued for same tournament code (or open matchmaking)
            for(String user : waitingPool.keySet()){
                String cmpVal = waitingPool.get(user);
                if(val.equals(cmpVal) && !gameFor.contains(user)){
                    //need to prevent double counting and making too many games in outer loop
                    count++;
                }
            }
            int players = 4;
            try{
                op = new GameOptions(val);
                players = op.playerIndex * 2;
                if(players == 0){
                    players = 1;
                }
            }catch(Exception ex1){}
            if(count >= players){
                int gameMembers = 0;
                for(String email : waitingPool.keySet()){
                    if(waitingPool.get(email).equals(val)) {
                        gameFor.add(email);
                        gameMembers++;
                        if(gameMembers == players){
                            break;
                        }
                    }
                }
                spawnGame(gameFor, op);
            }
        }
        //only to avoid comod exception
        for(String s : gameFor){
            waitingPool.remove(s);
        }
    }

    private void spawnGame(Set<String> gameFor, GameOptions op){
        UUID gameId = UUID.randomUUID();
        for(String email : gameFor) {
            gameMap.put(email, gameId.toString());
        }
        ServerApplication.addNewGame(gameId.toString(), op);
    }

    public void registerIntent(Authentication login, String tournamentCode) throws IOException {
        String email = login.getName();
        boolean contains = false;
        for(String e : waitingPool.keySet()){
            if(e.equals(email)){ //check by email in case some other attr changed
                contains = true;
            }
        }
        if(!contains){
            waitingPool.put(email, tournamentCode);
            makeMatches();
        }
    }

    public void removeIntent(Authentication login){
        String email = login.getName();
        String rm = null;
        for(String e : waitingPool.keySet()){
            if(e.equals(email)){
                rm = e;
            }
        }
        if(rm != null){
            waitingPool.remove(rm);
        }
    }

    public void endGame(String id){
        gameMap.values().removeIf(id::equals);
    }
}
