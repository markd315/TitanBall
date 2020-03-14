package authserver.users;

import authserver.models.ClassStat;
import authserver.models.User;
import authserver.users.classes.ClassServiceImpl;
import authserver.users.identities.CustomUserDetailsService;
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
    public void postgameStats1v1(String email, StatEngine stats, String className, int wasVictorious, double newRating) throws Exception {
        User user = userService.findUserByEmail(email);
        ClassStat classStat = classService.findStatsTrackerByRole(className);
        JSONObject toAdd = stats.statsOf(email);
        if(wasVictorious == 1){
            user.setWins_1v1(user.getWins_1v1() + 1);
            classStat.setWins(classStat.getWins() + 1); //TODO add class stats for this stuff!
        }else if(wasVictorious == -1){
            user.setLosses_1v1(user.getLosses_1v1() + 1);
            classStat.setLosses(classStat.getLosses() + 1);
        }else{ //tie
            user.setTies_1v1(user.getTies_1v1() + 1);
            classStat.setTies(classStat.getTies() + 1);
        }
        user.setRating_1v1(newRating);
        if(toAdd.has(StatEngine.StatEnum.GOALS.toString())){
            user.setGoals_1v1((int) (user.getGoals_1v1() + (double) toAdd.get(StatEngine.StatEnum.GOALS.toString())));
            classStat.setGoals((int) (classStat.getGoals() + (double) toAdd.get(StatEngine.StatEnum.GOALS.toString())));
        }
        if(toAdd.has(StatEngine.StatEnum.SIDEGOALS.toString())){
            user.setSidegoals_1v1((int) (user.getSidegoals_1v1() +(double)  toAdd.get(StatEngine.StatEnum.SIDEGOALS.toString())));
            classStat.setSidegoals((int) (classStat.getSidegoals() +(double)  toAdd.get(StatEngine.StatEnum.SIDEGOALS.toString())));
        }
        if(toAdd.has(StatEngine.StatEnum.POINTS.toString())){
            user.setPoints_1v1(user.getPoints_1v1() + (double) toAdd.get(StatEngine.StatEnum.POINTS.toString()));
            classStat.setPoints(classStat.getPoints() + (double) toAdd.get(StatEngine.StatEnum.POINTS.toString()));
        }
        if(toAdd.has(StatEngine.StatEnum.STEALS.toString())){
            user.setSteals_1v1((int) (user.getSteals_1v1() + (double)  toAdd.get(StatEngine.StatEnum.STEALS.toString())));
            classStat.setSteals((int) (classStat.getSteals() + (double)  toAdd.get(StatEngine.StatEnum.STEALS.toString())));
        }
        if(toAdd.has(StatEngine.StatEnum.BLOCKS.toString())){
            user.setBlocks_1v1((int) (user.getBlocks_1v1() + (double) toAdd.get(StatEngine.StatEnum.BLOCKS.toString())));
            classStat.setBlocks((int) (classStat.getBlocks() + (double) toAdd.get(StatEngine.StatEnum.BLOCKS.toString())));
        }
        if(toAdd.has(StatEngine.StatEnum.PASSES.toString())){
            user.setPasses_1v1((int) (user.getPasses_1v1() +(double)  toAdd.get(StatEngine.StatEnum.PASSES.toString())));
            classStat.setPasses((int) (classStat.getPasses() +(double)  toAdd.get(StatEngine.StatEnum.PASSES.toString())));
        }
        if(toAdd.has(StatEngine.StatEnum.KILLS.toString())){
            user.setKills_1v1((int) (user.getKills_1v1() + (double) toAdd.get(StatEngine.StatEnum.KILLS.toString())));
            classStat.setKills((int) (classStat.getKills() + (double) toAdd.get(StatEngine.StatEnum.KILLS.toString())));
        }
        if(toAdd.has(StatEngine.StatEnum.DEATHS.toString())){
            user.setDeaths_1v1((int) (user.getDeaths_1v1() +(double) toAdd.get(StatEngine.StatEnum.DEATHS.toString())));
            classStat.setDeaths((int) (classStat.getDeaths() +(double) toAdd.get(StatEngine.StatEnum.DEATHS.toString())));
        }
        if(toAdd.has(StatEngine.StatEnum.TURNOVERS.toString())){
            user.setTurnovers_1v1((int) (user.getTurnovers_1v1() +(double)  toAdd.get(StatEngine.StatEnum.TURNOVERS.toString())));
            classStat.setTurnovers((int) (classStat.getTurnovers() +(double)  toAdd.get(StatEngine.StatEnum.TURNOVERS.toString())));
        }
        if(toAdd.has(StatEngine.StatEnum.KILLASSISTS.toString())){
            user.setKillassists_1v1((int) (user.getKillassists_1v1() +(double)  toAdd.get(StatEngine.StatEnum.KILLASSISTS.toString())));
            classStat.setKillassists((int) (classStat.getKillassists() +(double)  toAdd.get(StatEngine.StatEnum.KILLASSISTS.toString())));
        }
        if(toAdd.has(StatEngine.StatEnum.GOALASSISTS.toString())){
            user.setGoalassists_1v1((int) (user.getGoalassists_1v1() +(double)  toAdd.get(StatEngine.StatEnum.GOALASSISTS.toString())));
            classStat.setGoalassists((int) (classStat.getGoalassists() +(double)  toAdd.get(StatEngine.StatEnum.GOALASSISTS.toString())));
        }
        if(toAdd.has(StatEngine.StatEnum.REBOUND.toString())){
            user.setRebounds_1v1((int) (user.getRebounds_1v1() +(double)  toAdd.get(StatEngine.StatEnum.REBOUND.toString())));
            classStat.setRebounds((int) (classStat.getRebounds() +(double)  toAdd.get(StatEngine.StatEnum.REBOUND.toString())));
        }
        userService.saveUser(user);
        classService.saveClass(classStat);
    }
}
