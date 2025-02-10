package authserver.users;

import authserver.models.ClassStat;
import authserver.models.User;
import authserver.users.classes.ClassServiceImpl;
import authserver.users.identities.CustomUserDetailsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gameserver.engine.StatEngine;
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
        ObjectNode toAdd = stats.statsOf(email);
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
            user.setGoals((user.getGoals() + toAdd.get(StatEngine.StatEnum.GOALS.toString()).asInt()));
            classStat.setGoals((classStat.getGoals() +  toAdd.get(StatEngine.StatEnum.GOALS.toString()).asInt()));
        }
        if(toAdd.has(StatEngine.StatEnum.SIDEGOALS.toString())){
            user.setSidegoals((user.getSidegoals() + toAdd.get(StatEngine.StatEnum.SIDEGOALS.toString()).asInt()));
            classStat.setSidegoals((classStat.getSidegoals() + toAdd.get(StatEngine.StatEnum.SIDEGOALS.toString()).asInt()));
        }
        if(toAdd.has(StatEngine.StatEnum.POINTS.toString())){
            user.setPoints(user.getPoints() +  toAdd.get(StatEngine.StatEnum.POINTS.toString()).asDouble());
            classStat.setPoints(classStat.getPoints() +  toAdd.get(StatEngine.StatEnum.POINTS.toString()).asDouble());
        }
        if(toAdd.has(StatEngine.StatEnum.STEALS.toString())){
            user.setSteals((user.getSteals() +   toAdd.get(StatEngine.StatEnum.STEALS.toString()).asInt()));
            classStat.setSteals((classStat.getSteals() + toAdd.get(StatEngine.StatEnum.STEALS.toString()).asInt()));
        }
        if(toAdd.has(StatEngine.StatEnum.BLOCKS.toString())){
            user.setBlocks((user.getBlocks() +  toAdd.get(StatEngine.StatEnum.BLOCKS.toString()).asInt()));
            classStat.setBlocks((classStat.getBlocks() +  toAdd.get(StatEngine.StatEnum.BLOCKS.toString()).asInt()));
        }
        if(toAdd.has(StatEngine.StatEnum.PASSES.toString())){
            user.setPasses((user.getPasses() +  toAdd.get(StatEngine.StatEnum.PASSES.toString()).asInt()));
            classStat.setPasses((classStat.getPasses() +  toAdd.get(StatEngine.StatEnum.PASSES.toString()).asInt()));
        }
        if(toAdd.has(StatEngine.StatEnum.KILLS.toString())){
            user.setKills((user.getKills() +  toAdd.get(StatEngine.StatEnum.KILLS.toString()).asInt()));
            classStat.setKills((classStat.getKills() +  toAdd.get(StatEngine.StatEnum.KILLS.toString()).asInt()));
        }
        if(toAdd.has(StatEngine.StatEnum.DEATHS.toString())){
            user.setDeaths((user.getDeaths() + toAdd.get(StatEngine.StatEnum.DEATHS.toString()).asInt()));
            classStat.setDeaths((classStat.getDeaths() + toAdd.get(StatEngine.StatEnum.DEATHS.toString()).asInt()));
        }
        if(toAdd.has(StatEngine.StatEnum.TURNOVERS.toString())){
            user.setTurnovers((user.getTurnovers() +  toAdd.get(StatEngine.StatEnum.TURNOVERS.toString()).asInt()));
            classStat.setTurnovers((classStat.getTurnovers() +  toAdd.get(StatEngine.StatEnum.TURNOVERS.toString()).asInt()));
        }
        if(toAdd.has(StatEngine.StatEnum.KILLASSISTS.toString())){
            user.setKillassists((user.getKillassists() +  toAdd.get(StatEngine.StatEnum.KILLASSISTS.toString()).asInt()));
            classStat.setKillassists((classStat.getKillassists() +  toAdd.get(StatEngine.StatEnum.KILLASSISTS.toString()).asInt()));
        }
        if(toAdd.has(StatEngine.StatEnum.GOALASSISTS.toString())){
            user.setGoalassists(user.getGoalassists() +  toAdd.get(StatEngine.StatEnum.GOALASSISTS.toString()).asInt());
            classStat.setGoalassists(classStat.getGoalassists() +  toAdd.get(StatEngine.StatEnum.GOALASSISTS.toString()).asInt());
        }
        if(toAdd.has(StatEngine.StatEnum.REBOUND.toString())){
            user.setRebounds(user.getRebounds() +  toAdd.get(StatEngine.StatEnum.REBOUND.toString()).asInt());
            classStat.setRebounds(classStat.getRebounds() + toAdd.get(StatEngine.StatEnum.REBOUND.toString()).asInt());
        }
        userService.saveUser(user);
        classService.saveClass(classStat);
    }
    public void postgameStats1v1(String email, StatEngine stats, String className, int wasVictorious, double newRating) throws Exception {
        User user = userService.findUserByEmail(email);
        ClassStat classStat = classService.findStatsTrackerByRole(className);
        JsonNode toAdd = stats.statsOf(email);
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
            user.setGoals_1v1(user.getGoals_1v1() +  toAdd.get(StatEngine.StatEnum.GOALS.toString()).asInt());
            classStat.setGoals(classStat.getGoals() +  toAdd.get(StatEngine.StatEnum.GOALS.toString()).asInt());
        }
        if(toAdd.has(StatEngine.StatEnum.SIDEGOALS.toString())){
            user.setSidegoals_1v1((user.getSidegoals_1v1() +  toAdd.get(StatEngine.StatEnum.SIDEGOALS.toString()).asInt()));
            classStat.setSidegoals((classStat.getSidegoals() +  toAdd.get(StatEngine.StatEnum.SIDEGOALS.toString()).asInt()));
        }
        if(toAdd.has(StatEngine.StatEnum.POINTS.toString())){
            user.setPoints_1v1(user.getPoints_1v1() +  toAdd.get(StatEngine.StatEnum.POINTS.toString()).asDouble());
            classStat.setPoints(classStat.getPoints() +  toAdd.get(StatEngine.StatEnum.POINTS.toString()).asDouble());
        }
        if(toAdd.has(StatEngine.StatEnum.STEALS.toString())){
            user.setSteals_1v1((user.getSteals_1v1() +   toAdd.get(StatEngine.StatEnum.STEALS.toString()).asInt()));
            classStat.setSteals((classStat.getSteals() +   toAdd.get(StatEngine.StatEnum.STEALS.toString()).asInt()));
        }
        if(toAdd.has(StatEngine.StatEnum.BLOCKS.toString())){
            user.setBlocks_1v1((user.getBlocks_1v1() +  toAdd.get(StatEngine.StatEnum.BLOCKS.toString()).asInt()));
            classStat.setBlocks((classStat.getBlocks() +  toAdd.get(StatEngine.StatEnum.BLOCKS.toString()).asInt()));
        }
        if(toAdd.has(StatEngine.StatEnum.PASSES.toString())){
            user.setPasses_1v1((user.getPasses_1v1() +  toAdd.get(StatEngine.StatEnum.PASSES.toString()).asInt()));
            classStat.setPasses((classStat.getPasses() +  toAdd.get(StatEngine.StatEnum.PASSES.toString()).asInt()));
        }
        if(toAdd.has(StatEngine.StatEnum.KILLS.toString())){
            user.setKills_1v1((user.getKills_1v1() +  toAdd.get(StatEngine.StatEnum.KILLS.toString()).asInt()));
            classStat.setKills((classStat.getKills() +  toAdd.get(StatEngine.StatEnum.KILLS.toString()).asInt()));
        }
        if(toAdd.has(StatEngine.StatEnum.DEATHS.toString())){
            user.setDeaths_1v1((user.getDeaths_1v1() + toAdd.get(StatEngine.StatEnum.DEATHS.toString()).asInt()));
            classStat.setDeaths((classStat.getDeaths() + toAdd.get(StatEngine.StatEnum.DEATHS.toString()).asInt()));
        }
        if(toAdd.has(StatEngine.StatEnum.TURNOVERS.toString())){
            user.setTurnovers_1v1((user.getTurnovers_1v1() +  toAdd.get(StatEngine.StatEnum.TURNOVERS.toString()).asInt()));
            classStat.setTurnovers((classStat.getTurnovers() +  toAdd.get(StatEngine.StatEnum.TURNOVERS.toString()).asInt()));
        }
        if(toAdd.has(StatEngine.StatEnum.KILLASSISTS.toString())){
            user.setKillassists_1v1((user.getKillassists_1v1() +  toAdd.get(StatEngine.StatEnum.KILLASSISTS.toString()).asInt()));
            classStat.setKillassists((classStat.getKillassists() +  toAdd.get(StatEngine.StatEnum.KILLASSISTS.toString()).asInt()));
        }
        if(toAdd.has(StatEngine.StatEnum.GOALASSISTS.toString())){
            user.setGoalassists_1v1((user.getGoalassists_1v1() +  toAdd.get(StatEngine.StatEnum.GOALASSISTS.toString()).asInt()));
            classStat.setGoalassists((classStat.getGoalassists() +  toAdd.get(StatEngine.StatEnum.GOALASSISTS.toString()).asInt()));
        }
        if(toAdd.has(StatEngine.StatEnum.REBOUND.toString())){
            user.setRebounds_1v1((user.getRebounds_1v1() +  toAdd.get(StatEngine.StatEnum.REBOUND.toString()).asInt()));
            classStat.setRebounds((classStat.getRebounds() +  toAdd.get(StatEngine.StatEnum.REBOUND.toString()).asInt()));
        }
        userService.saveUser(user);
        classService.saveClass(classStat);
    }
}
