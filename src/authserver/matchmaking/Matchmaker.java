package authserver.matchmaking;

import gameserver.gamemanager.ServerApplication;
import gameserver.engine.GameOptions;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class Matchmaker {

    private void spawnGame(Collection<String> gameFor, GameOptions op) {
        UUID gameId = UUID.randomUUID();
        ServerApplication.addNewGame(gameId.toString(), op, gameFor);
        for (String email : gameFor) {
            System.out.println("gamemap " + email + gameId.toString());
            gameMap.put(email, gameId.toString());
        }
    }

    private Map<String, String> waitingPool = new HashMap<>();//user emails -> tournament code
    private Map<String, String> gameMap = new HashMap<>();//user emails -> game id

    private Map<String, String> teamMemberWaitingPool = new HashMap<>();//user emails -> teamN
    private Map<String, String> teamWaitingPool = new HashMap<>();//teamN -> tournament code
    public Map<String, String> teamGameMap = new HashMap<>();//user emails -> game id

    private int desperation = 0; //TODO increase to eventually sacrifice match quality

    public String findGame(Authentication login) {
        String email = login.getName();
        if (gameMap.containsKey(email)) {
            return gameMap.get(email);
        }
        if (waitingPool.containsKey(email)) {
            return "WAITING";
        }
        return "NOT QUEUED";
    }

    public Map<String, String> getGameMap(){
        return gameMap;
    }

    private void makeMatches() {
        Set<String> gameFor = new HashSet<>();
        GameOptions op = null;
        for (String val : waitingPool.values()) {
            int count = 0;
            //detect if max people queued for same tournament code (or open matchmaking)
            for (String user : waitingPool.keySet()) {
                String cmpVal = waitingPool.get(user);
                if (val.equals(cmpVal) && !gameFor.contains(user)) {
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
            }catch(Exception ex1){
                System.out.println("catch");
            }
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
        for (String s : gameFor) {
            waitingPool.remove(s);
            System.out.println("WAITING POOL SIZE: " + waitingPool.size());
        }
    }

    private void makeTeamMatches() {
        System.out.println("mtm 1");
        List<String> gameFor = new ArrayList<>();
        for (String val : teamWaitingPool.values()) {
            int count = 0;
            //detect if max people queued for same tournament code (or open matchmaking)
            for (String teamName : teamWaitingPool.keySet()) {
                System.out.println("mtm 2");
                String cmpVal = teamWaitingPool.get(teamName);
                if (val.equals(cmpVal) && !gameFor.contains(teamName)) {
                    //need to prevent double counting and making too many games in outer loop
                    count++;
                    System.out.println("mtm 3");
                }
            }
            int teams;
            GameOptions op = new GameOptions(val);
            teams = 2;
            if (teams == 0) {
                teams = 1;
                System.out.println("mtm bad");
            }
            if (count >= teams) {
                System.out.println("mtm yes");
                int gameMembers = 0;
                for (String email : teamWaitingPool.keySet()) {
                    if (teamWaitingPool.get(email).equals(val)) {
                        gameFor.add(email);
                        gameMembers++;
                        System.out.println("mtm 4");
                        if (gameMembers == teams) {
                            break;
                        }
                    }
                }
                System.out.println("mtm 5");
                //TODO we need to order the players correctly
                //TODO we also need to add postgame stuff
                //orderPlayersForTeams(gameFor, );
                spawnGame(gameFor, op);
            }
        }
        //only to avoid comod exception
        for (String s : gameFor) {
            teamWaitingPool.remove(s);
            System.out.println("WAITING POOL SIZE: " + teamWaitingPool.size());
        }
    }

    public void registerIntent(Authentication login, String tournamentCode, String teamname) {
        if (teamname != null) {
            registerIntentTeam(login, tournamentCode, teamname);
        }
        String email = login.getName();
        boolean contains = false;
        for (String e : waitingPool.keySet()) {
            if (e.equals(email)) { //check by email in case some other attr changed
                contains = true;
            }
        }
        if (!contains) {
            waitingPool.put(email, tournamentCode);
            makeMatches();
        }
    }

    public void registerIntentTeam(Authentication login, String tournamentCode, String teamname) {
        System.out.println("rit 1");
        String email = login.getName();
        boolean contains = false;
        int teamQueue = 0;
        //email -> teamN (after full -> tournament code)
        for (String e : teamMemberWaitingPool.keySet()) {
            if (e.equals(email)) { //check by email in case some other attr changed
                contains = true;
                System.out.println("rit 2");
            }
        }
        if (!contains) {
            System.out.println("rit 3");
            waitingPool.put(email, teamname);
        }
        System.out.println("rit 4");
        for (String e : teamMemberWaitingPool.values()) {
            if (e.equals(teamname)) { //check by email in case some other attr changed
                teamQueue += 1;
                System.out.println("rit 5");
            }
        }
        GameOptions op = new GameOptions(tournamentCode);
        if (teamQueue > (op.playerIndex - 1)) {
            System.out.println("rit 6");
            teamWaitingPool.put(teamname, tournamentCode);
            makeTeamMatches();
        }
        System.out.println("rit 7");
        waitingPool.put(email, tournamentCode);
    }

    public void removeIntent(Authentication login) {
        String email = login.getName();
        System.out.println("DEREGISTERING " + email);
        String rm = null;
        for (String e : waitingPool.keySet()) {
            if (e.equals(email)) {
                rm = e;
            }
        }
        if (rm != null) {
            waitingPool.remove(rm);
        }
    }

    public void endGame(String id) {
        List<String> rm = new ArrayList<>();
        for (String email : gameMap.keySet()) {
            if (gameMap.get(email)
                    .equals(id)) {
                rm.add(email);
            }
        }
        for (String email : rm) {
            System.out.println("ENDING AND FREEING " + email);
            gameMap.remove(email);
        }
    }
}
