package authserver.users;

import authserver.models.ClassStat;
import authserver.models.User;
import gameserver.engine.StatEngine;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PersistenceManager {

    @Autowired
    public CustomUserDetailsService userService;

    @Autowired
    public ClassServiceImpl classService;

    public void postgameStats(String email, StatEngine stats, String className, int wasVictorious, double newRating) throws Exception {
        User user = userService.findUserByEmail(email);
        ClassStat classStat = classService.findStatsTrackerByRole(className);
        JSONObject toAdd = stats.statsOf(email);
        if(wasVictorious == 1){
            user.setWins(user.getWins() + 1);
            classStat.setWins(classStat.getWins() + 1);
        }else if(wasVictorious == -1){
            user.setLosses(user.getLosses() + 1);
            classStat.setLosses(classStat.getLosses() + 1);
        }else{ //tie
            user.setTies(user.getTies() + 1);
            classStat.setTies(classStat.getTies() + 1);
        }
        user.setRating(newRating);
        if(toAdd.has(StatEngine.StatEnum.GOALS.toString())){
            user.setGoals((int) (user.getGoals() + (double) toAdd.get(StatEngine.StatEnum.GOALS.toString())));
            classStat.setGoals((int) (classStat.getGoals() + (double) toAdd.get(StatEngine.StatEnum.GOALS.toString())));
        }
        if(toAdd.has(StatEngine.StatEnum.SIDEGOALS.toString())){
            user.setSidegoals((int) (user.getSidegoals() +(double)  toAdd.get(StatEngine.StatEnum.SIDEGOALS.toString())));
            classStat.setSidegoals((int) (classStat.getSidegoals() +(double)  toAdd.get(StatEngine.StatEnum.SIDEGOALS.toString())));
        }
        if(toAdd.has(StatEngine.StatEnum.POINTS.toString())){
            user.setPoints(user.getPoints() + (double) toAdd.get(StatEngine.StatEnum.POINTS.toString()));
            classStat.setPoints(classStat.getPoints() + (double) toAdd.get(StatEngine.StatEnum.POINTS.toString()));
        }
        if(toAdd.has(StatEngine.StatEnum.STEALS.toString())){
            user.setSteals((int) (user.getSteals() + (double)  toAdd.get(StatEngine.StatEnum.STEALS.toString())));
            classStat.setSteals((int) (classStat.getSteals() + (double)  toAdd.get(StatEngine.StatEnum.STEALS.toString())));
        }
        if(toAdd.has(StatEngine.StatEnum.BLOCKS.toString())){
            user.setBlocks((int) (user.getBlocks() + (double) toAdd.get(StatEngine.StatEnum.BLOCKS.toString())));
            classStat.setBlocks((int) (classStat.getBlocks() + (double) toAdd.get(StatEngine.StatEnum.BLOCKS.toString())));
        }
        if(toAdd.has(StatEngine.StatEnum.PASSES.toString())){
            user.setPasses((int) (user.getPasses() +(double)  toAdd.get(StatEngine.StatEnum.PASSES.toString())));
            classStat.setPasses((int) (classStat.getPasses() +(double)  toAdd.get(StatEngine.StatEnum.PASSES.toString())));
        }
        if(toAdd.has(StatEngine.StatEnum.KILLS.toString())){
            user.setKills((int) (user.getKills() + (double) toAdd.get(StatEngine.StatEnum.KILLS.toString())));
            classStat.setKills((int) (classStat.getKills() + (double) toAdd.get(StatEngine.StatEnum.KILLS.toString())));
        }
        if(toAdd.has(StatEngine.StatEnum.DEATHS.toString())){
            user.setDeaths((int) (user.getDeaths() +(double) toAdd.get(StatEngine.StatEnum.DEATHS.toString())));
            classStat.setDeaths((int) (classStat.getDeaths() +(double) toAdd.get(StatEngine.StatEnum.DEATHS.toString())));
        }
        if(toAdd.has(StatEngine.StatEnum.TURNOVERS.toString())){
            user.setTurnovers((int) (user.getTurnovers() +(double)  toAdd.get(StatEngine.StatEnum.TURNOVERS.toString())));
            classStat.setTurnovers((int) (classStat.getTurnovers() +(double)  toAdd.get(StatEngine.StatEnum.TURNOVERS.toString())));
        }
        if(toAdd.has(StatEngine.StatEnum.KILLASSISTS.toString())){
            user.setKillassists((int) (user.getKillassists() +(double)  toAdd.get(StatEngine.StatEnum.KILLASSISTS.toString())));
            classStat.setKillassists((int) (classStat.getKillassists() +(double)  toAdd.get(StatEngine.StatEnum.KILLASSISTS.toString())));
        }
        if(toAdd.has(StatEngine.StatEnum.GOALASSISTS.toString())){
            user.setGoalassists((int) (user.getGoalassists() +(double)  toAdd.get(StatEngine.StatEnum.GOALASSISTS.toString())));
            classStat.setGoalassists((int) (classStat.getGoalassists() +(double)  toAdd.get(StatEngine.StatEnum.GOALASSISTS.toString())));
        }
        if(toAdd.has(StatEngine.StatEnum.REBOUND.toString())){
            user.setRebounds((int) (user.getRebounds() +(double)  toAdd.get(StatEngine.StatEnum.REBOUND.toString())));
            classStat.setRebounds((int) (classStat.getRebounds() +(double)  toAdd.get(StatEngine.StatEnum.REBOUND.toString())));
        }
        userService.saveUser(user);
        classService.saveClass(classStat);
    }
}
