package authserver.matchmaking;

import gameserver.gamemanager.ServerApplication;
import gameserver.engine.GameOptions;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.IOException;
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
    public Map<String, String> playerClasses = new java.util.concurrent.ConcurrentHashMap<>();
    public Map<String, String> playerPreferredLanes = new java.util.concurrent.ConcurrentHashMap<>();
    public Map<String, Set<String>> partnerPool = new java.util.concurrent.ConcurrentHashMap<>();

    private Map<String, String> teamMemberWaitingPool = new HashMap<>();//user emails -> teamN
    private Map<String, String> teamWaitingPool = new HashMap<>();//teamN -> tournament code

    public synchronized String findGame(Authentication login) {
        String email = (login.getPrincipal() instanceof authserver.models.User)
                ? ((authserver.models.User) login.getPrincipal()).getEmail()
                : login.getName();
        if (gameMap.containsKey(email)) {
            return gameMap.get(email);
        }
        if (waitingPool.containsKey(email)) {
            return "WAITING";
        }
        return "NOT QUEUED";
    }

    public boolean areMutualPartners(String email1, String email2) {
        if (email1 == null || email2 == null || email1.equalsIgnoreCase(email2)) return false;
        Set<String> s1 = partnerPool.get(email1);
        Set<String> s2 = partnerPool.get(email2);
        if (s1 == null || s2 == null) return false;
        
        return wantsPartner(s1, email2) && wantsPartner(s2, email1);
    }

    private boolean wantsPartner(Set<String> partnerSet, String targetEmail) {
        if (partnerSet == null || targetEmail == null) return false;
        String targetName = targetEmail.split("@")[0];
        for (String p : partnerSet) {
            if (p.equalsIgnoreCase(targetEmail) || p.equalsIgnoreCase(targetName)) {
                return true;
            }
        }
        return false;
    }

    private void makeMatches() {
        Set<String> gameFor = new HashSet<>();
        Set<String> uniqueCodes = new HashSet<>(waitingPool.values());

        for (String val : uniqueCodes) {
            int players = 8;
            GameOptions op = null;
            try {
                op = new GameOptions(val);
                int[] vals = GameOptions.getPlayersVal();
                if (op.playerIndex >= 0 && op.playerIndex < vals.length) {
                    int teamSize = vals[op.playerIndex];
                    if (teamSize == 0) {
                        players = 1; // Single-player
                    } else {
                        players = teamSize * 2;
                    }
                }
            } catch (Exception ex1) {
                System.out.println("catch");
            }

            List<String> pool = new ArrayList<>();
            for (Map.Entry<String, String> entry : waitingPool.entrySet()) {
                if (entry.getValue().equals(val) && !gameFor.contains(entry.getKey())) {
                    pool.add(entry.getKey());
                }
            }

            while (pool.size() >= players) {
                List<String> selectedPlayers = new ArrayList<>();
                // Form match prioritizing grouping mutual partners
                for (String candidate : pool) {
                    if (selectedPlayers.contains(candidate)) continue;
                    
                    // Collect candidate and any unselected mutual partners in the pool
                    List<String> cluster = new ArrayList<>();
                    cluster.add(candidate);
                    for (String other : pool) {
                        if (!other.equals(candidate) && !selectedPlayers.contains(other) && areMutualPartners(candidate, other)) {
                            cluster.add(other);
                        }
                    }

                    if (selectedPlayers.size() + cluster.size() <= players) {
                        selectedPlayers.addAll(cluster);
                    } else if (selectedPlayers.size() < players) {
                        // Take candidate individually if full cluster doesn't fit
                        selectedPlayers.add(candidate);
                    }

                    if (selectedPlayers.size() == players) {
                        break;
                    }
                }

                if (selectedPlayers.size() == players) {
                    gameFor.addAll(selectedPlayers);
                    pool.removeAll(selectedPlayers);
                    spawnGame(selectedPlayers, op);
                } else {
                    break;
                }
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

    private String normalizeTournamentCode(String code) {
        if (code == null || code.isEmpty() || code.equals("3v3") || code.equals("/3v3")) {
            return "/0/0/1/5/2/9999/10/12"; // 3v3 is index 0
        }
        if (code.equals("1v1") || code.equals("/1v1")) {
            return "/4/1/1/5/2/9999/10/12"; // 1v1 is index 4
        }
        return code;
    }

    public synchronized void registerIntent(Authentication login, String tournamentCode, String teamname, String classSelection, String preferredLane, String partners) {
        tournamentCode = normalizeTournamentCode(tournamentCode);
        if (teamname != null) {
            registerIntentTeam(login, tournamentCode, teamname);
        }
        String email = (login.getPrincipal() instanceof authserver.models.User)
                ? ((authserver.models.User) login.getPrincipal()).getEmail()
                : login.getName();
        
        if (classSelection != null && !classSelection.isEmpty()) {
            playerClasses.put(email, classSelection);
        } else {
            playerClasses.put(email, "WARRIOR");
        }

        if ("GOALIE".equalsIgnoreCase(classSelection)) {
            playerPreferredLanes.put(email, "GOALIE");
        } else if (preferredLane != null && !preferredLane.trim().isEmpty()) {
            playerPreferredLanes.put(email, preferredLane.trim().toUpperCase());
        } else {
            playerPreferredLanes.remove(email);
        }

        if (partners != null && !partners.trim().isEmpty()) {
            Set<String> partnersSet = new HashSet<>();
            for (String p : partners.split(",")) {
                if (!p.trim().isEmpty()) {
                    partnersSet.add(p.trim());
                }
            }
            partnerPool.put(email, partnersSet);
        } else {
            partnerPool.remove(email);
        }

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

    public synchronized void registerIntent(Authentication login, String tournamentCode, String teamname, String classSelection, String partners) {
        registerIntent(login, tournamentCode, teamname, classSelection, null, partners);
    }

    public synchronized void registerIntent(Authentication login, String tournamentCode, String teamname, String classSelection) {
        registerIntent(login, tournamentCode, teamname, classSelection, null, null);
    }

    public synchronized void registerIntent(Authentication login, String tournamentCode, String teamname) {
        registerIntent(login, tournamentCode, teamname, "WARRIOR", null, null);
    }

    public synchronized void registerIntentTeam(Authentication login, String tournamentCode, String teamname) {
        tournamentCode = normalizeTournamentCode(tournamentCode);
        System.out.println("rit 1");
        String email = (login.getPrincipal() instanceof authserver.models.User)
                ? ((authserver.models.User) login.getPrincipal()).getEmail()
                : login.getName();
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
        int teamSize = 1;
        try {
            int[] vals = GameOptions.getPlayersVal();
            if (op.playerIndex >= 0 && op.playerIndex < vals.length) {
                teamSize = Math.max(1, vals[op.playerIndex]);
            }
        } catch (Exception e) {}
        if (teamQueue >= teamSize) {
            System.out.println("rit 6");
            teamWaitingPool.put(teamname, tournamentCode);
            makeTeamMatches();
        }
        System.out.println("rit 7");
        waitingPool.put(email, tournamentCode);
    }

    public synchronized void removeIntent(Authentication login) {
        String email = (login.getPrincipal() instanceof authserver.models.User)
                ? ((authserver.models.User) login.getPrincipal()).getEmail()
                : login.getName();
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
        playerClasses.remove(email);
        partnerPool.remove(email);
    }

    public synchronized void clearWaitingPools() { //for graceful shutdown
        waitingPool.clear();
        teamMemberWaitingPool.clear();
        teamWaitingPool.clear();
        partnerPool.clear();
    }

    public synchronized void endGame(String id) {
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
            playerClasses.remove(email);
            partnerPool.remove(email);
        }
    }

    public synchronized void registerTutorialGame(String email, String gameId) {
        gameMap.put(email, gameId);
    }
}
