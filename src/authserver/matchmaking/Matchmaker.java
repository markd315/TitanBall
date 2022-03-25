package authserver.matchmaking;

import authserver.models.User;
import authserver.users.PersistenceManager;
import gameserver.engine.GameEngine;
import gameserver.engine.GameOptions;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.Titan;
import gameserver.gamemanager.ManagedGame;
import gameserver.gamemanager.ServerApplication;
import networking.PlayerDivider;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class Matchmaker {

    private void spawnGame(Collection<String> gameFor, GameOptions op) {
        UUID gameId = UUID.randomUUID();
        addNewGame(gameId.toString(), op, gameFor);
        for (String email : gameFor) {
            System.out.println("gamemap " + email + gameId);
            gameMap.put(email, gameId.toString());
        }
    }

    private Map<String, String> waitingPool = new HashMap<>();//user emails -> tournament code
    private Map<String, String> teamMemberWaitingPool = new HashMap<>();//user emails -> teamN
    private Map<String, String> teamWaitingPool = new HashMap<>();//teamN -> tournament code

    public Map<String, String> teamGameMap = new HashMap<>();//user emails -> game id

    private static Map<String, String> gameMap = new HashMap<>();//user emails -> game id
    static Map<String, ManagedGame> states = new HashMap<>(); //game id -> actual game object

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

    public Map<String, String> getGameMap() {
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
                for (String email : waitingPool.keySet()) {
                    if (waitingPool.get(email).equals(val)) {
                        gameFor.add(email);
                        gameMembers++;
                        if (gameMembers == players) {
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
        }
        System.out.println("WAITING POOL SIZE: " + waitingPool.size());
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

    public static void endGame(String id) {
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

    public static void addNewGame(String id, GameOptions op, Collection<String> gameFor) {
        System.out.println("adding new game, id " + id);
        ManagedGame newgame = new ManagedGame(id, op);
        states.put(id, newgame);
        for(String email : gameFor){
            newgame.addOrReplaceNewClient(email);
        }
        System.out.println("game map size: " + states.size());
    }


    public static void checkGameExpiry() {
        Set<String> rm = new HashSet<>();
        PersistenceManager pm = ServerApplication.getPersistenceManager();
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
                                pm.postgameStats(player.email, val.state.stats, className, player.wasVictorious, player.newRating);
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
                                pm.postgameStats1v1(player.email, val.state.stats, className, player.wasVictorious, player.newRating);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                rm.add(id);
                endGame(id);
            }
        }
        for (String id : rm) {
            states.remove(id);
        }
    }

    private static void injectRatingsToPlayers(GameEngine state) {
        List<Rating> home = new ArrayList<>(), away = new ArrayList<>();
        PersistenceManager pm = ServerApplication.getPersistenceManager();
        for (PlayerDivider pl : state.clients) {
            User persistence = pm.userService.findUserByEmail(pl.email);
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
        PersistenceManager pm = ServerApplication.getPersistenceManager();
        for (PlayerDivider pl : state.clients) {
            User persistence = pm.userService.findUserByEmail(pl.email);
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

    public ManagedGame getManagedGame(String uuid){
        return states.get(uuid);
    }
}
