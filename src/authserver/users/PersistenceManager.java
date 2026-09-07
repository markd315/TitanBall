package authserver.users;

import authserver.models.BuildOrderStat;
import authserver.models.ClassStat;
import authserver.models.MasteriesStat;
import authserver.models.UpgradeClassStat;
import authserver.models.User;
import authserver.users.builds.BuildOrderStatServiceImpl;
import authserver.users.classes.ClassServiceImpl;
import authserver.users.masteries.MasteriesStatServiceImpl;
import authserver.users.upgrades.UpgradeClassStatServiceImpl;
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

    @Autowired
    public MasteriesStatServiceImpl masteriesService;

    @Autowired
    public UpgradeClassStatServiceImpl upgradeClassStatService;

    @Autowired
    public BuildOrderStatServiceImpl buildOrderStatService;

    public void postgameStats(String email, StatEngine stats, String className, int wasVictorious, double newRating) throws Exception {
        User user = userService.findUserByEmail(email);
        if (user == null) {
            user = userService.findUserByUsername(email);
        }
        if (user == null) {
            System.err.println("[PersistenceManager] User not found for email/username: " + email);
            return;
        }
        ClassStat classStat = classService.findStatsTrackerByRole(className);
        ObjectNode toAdd = stats.statsOf(email);
        if(wasVictorious == 1){
            user.setWins(user.getWins() + 1);
            if (classStat != null) classStat.setWins(classStat.getWins() + 1);
        }else if(wasVictorious == -1){
            user.setLosses(user.getLosses() + 1);
            if (classStat != null) classStat.setLosses(classStat.getLosses() + 1);
        }else{ //tie
            user.setTies(user.getTies() + 1);
            if (classStat != null) classStat.setTies(classStat.getTies() + 1);
        }
        user.setRating(newRating);
        if(toAdd.has(StatEngine.StatEnum.GOALS.toString())){
            user.setGoals((user.getGoals() + toAdd.get(StatEngine.StatEnum.GOALS.toString()).asInt()));
            if (classStat != null) classStat.setGoals((classStat.getGoals() +  toAdd.get(StatEngine.StatEnum.GOALS.toString()).asInt()));
        }
        if(toAdd.has(StatEngine.StatEnum.SIDEGOALS.toString())){
            user.setSidegoals((user.getSidegoals() + toAdd.get(StatEngine.StatEnum.SIDEGOALS.toString()).asInt()));
            if (classStat != null) classStat.setSidegoals((classStat.getSidegoals() + toAdd.get(StatEngine.StatEnum.SIDEGOALS.toString()).asInt()));
        }
        if(toAdd.has(StatEngine.StatEnum.POINTS.toString())){
            user.setPoints(user.getPoints() +  toAdd.get(StatEngine.StatEnum.POINTS.toString()).asDouble());
            if (classStat != null) classStat.setPoints(classStat.getPoints() +  toAdd.get(StatEngine.StatEnum.POINTS.toString()).asDouble());
        }
        if(toAdd.has(StatEngine.StatEnum.STEALS.toString())){
            user.setSteals((user.getSteals() +   toAdd.get(StatEngine.StatEnum.STEALS.toString()).asInt()));
            if (classStat != null) classStat.setSteals((classStat.getSteals() + toAdd.get(StatEngine.StatEnum.STEALS.toString()).asInt()));
        }
        if(toAdd.has(StatEngine.StatEnum.BLOCKS.toString())){
            user.setBlocks((user.getBlocks() +  toAdd.get(StatEngine.StatEnum.BLOCKS.toString()).asInt()));
            if (classStat != null) classStat.setBlocks((classStat.getBlocks() +  toAdd.get(StatEngine.StatEnum.BLOCKS.toString()).asInt()));
        }
        if(toAdd.has(StatEngine.StatEnum.PASSES.toString())){
            user.setPasses((user.getPasses() +  toAdd.get(StatEngine.StatEnum.PASSES.toString()).asInt()));
            if (classStat != null) classStat.setPasses((classStat.getPasses() +  toAdd.get(StatEngine.StatEnum.PASSES.toString()).asInt()));
        }
        if(toAdd.has(StatEngine.StatEnum.KILLS.toString())){
            user.setKills((user.getKills() +  toAdd.get(StatEngine.StatEnum.KILLS.toString()).asInt()));
            if (classStat != null) classStat.setKills((classStat.getKills() +  toAdd.get(StatEngine.StatEnum.KILLS.toString()).asInt()));
        }
        if(toAdd.has(StatEngine.StatEnum.DEATHS.toString())){
            user.setDeaths((user.getDeaths() + toAdd.get(StatEngine.StatEnum.DEATHS.toString()).asInt()));
            if (classStat != null) classStat.setDeaths((classStat.getDeaths() + toAdd.get(StatEngine.StatEnum.DEATHS.toString()).asInt()));
        }
        if(toAdd.has(StatEngine.StatEnum.TURNOVERS.toString())){
            user.setTurnovers((user.getTurnovers() +  toAdd.get(StatEngine.StatEnum.TURNOVERS.toString()).asInt()));
            if (classStat != null) classStat.setTurnovers((classStat.getTurnovers() +  toAdd.get(StatEngine.StatEnum.TURNOVERS.toString()).asInt()));
        }
        if(toAdd.has(StatEngine.StatEnum.KILLASSISTS.toString())){
            user.setKillassists((user.getKillassists() +  toAdd.get(StatEngine.StatEnum.KILLASSISTS.toString()).asInt()));
            if (classStat != null) classStat.setKillassists((classStat.getKillassists() +  toAdd.get(StatEngine.StatEnum.KILLASSISTS.toString()).asInt()));
        }
        if(toAdd.has(StatEngine.StatEnum.GOALASSISTS.toString())){
            user.setGoalassists(user.getGoalassists() +  toAdd.get(StatEngine.StatEnum.GOALASSISTS.toString()).asInt());
            if (classStat != null) classStat.setGoalassists(classStat.getGoalassists() +  toAdd.get(StatEngine.StatEnum.GOALASSISTS.toString()).asInt());
        }
        if(toAdd.has(StatEngine.StatEnum.REBOUND.toString())){
            user.setRebounds(user.getRebounds() +  toAdd.get(StatEngine.StatEnum.REBOUND.toString()).asInt());
            if (classStat != null) classStat.setRebounds(classStat.getRebounds() + toAdd.get(StatEngine.StatEnum.REBOUND.toString()).asInt());
        }
        if (classStat != null) {
            if (toAdd.has(StatEngine.StatEnum.SAVES.toString())) {
                classStat.setSaves(classStat.getSaves() + toAdd.get(StatEngine.StatEnum.SAVES.toString()).asInt());
            }
            if (toAdd.has(StatEngine.StatEnum.LASTHITS.toString())) {
                classStat.setLasthits(classStat.getLasthits() + toAdd.get(StatEngine.StatEnum.LASTHITS.toString()).asInt());
            }
            if (toAdd.has(StatEngine.StatEnum.MINIONDAMAGE.toString())) {
                classStat.setMiniondamage(classStat.getMiniondamage() + toAdd.get(StatEngine.StatEnum.MINIONDAMAGE.toString()).asDouble());
            }
            if (toAdd.has(StatEngine.StatEnum.UPGRADESGOLD.toString())) {
                classStat.setUpgradesgold(classStat.getUpgradesgold() + toAdd.get(StatEngine.StatEnum.UPGRADESGOLD.toString()).asInt());
            }
            if (toAdd.has(StatEngine.StatEnum.CONSUMABLESGOLD.toString())) {
                classStat.setConsumablesgold(classStat.getConsumablesgold() + toAdd.get(StatEngine.StatEnum.CONSUMABLESGOLD.toString()).asInt());
            }
            if (toAdd.has(StatEngine.StatEnum.SIDEGOAL_SAVES.toString())) {
                classStat.setSidegoalsaves(classStat.getSidegoalsaves() + toAdd.get(StatEngine.StatEnum.SIDEGOAL_SAVES.toString()).asInt());
            }
            if (toAdd.has(StatEngine.StatEnum.CENTERGOAL_SAVES.toString())) {
                classStat.setCentergoalsaves(classStat.getCentergoalsaves() + toAdd.get(StatEngine.StatEnum.CENTERGOAL_SAVES.toString()).asInt());
            }
            if (toAdd.has(StatEngine.StatEnum.SIDEGOALS_CONCEDED.toString())) {
                classStat.setSidegoalsconceded(classStat.getSidegoalsconceded() + toAdd.get(StatEngine.StatEnum.SIDEGOALS_CONCEDED.toString()).asInt());
            }
            if (toAdd.has(StatEngine.StatEnum.GOALS_CONCEDED.toString())) {
                classStat.setGoalsconceded(classStat.getGoalsconceded() + toAdd.get(StatEngine.StatEnum.GOALS_CONCEDED.toString()).asInt());
            }
            if (toAdd.has(StatEngine.StatEnum.MANASPENT.toString())) {
                classStat.setManaspent(classStat.getManaspent() + toAdd.get(StatEngine.StatEnum.MANASPENT.toString()).asInt());
            }
        }
        userService.saveUser(user);
        if (classStat != null) classService.saveClass(classStat);
    }
    public void postgameStats1v1(String email, StatEngine stats, String className, int wasVictorious, double newRating) throws Exception {
        User user = userService.findUserByEmail(email);
        if (user == null) {
            user = userService.findUserByUsername(email);
        }
        if (user == null) {
            System.err.println("[PersistenceManager 1v1] User not found for email/username: " + email);
            return;
        }
        ClassStat classStat = classService.findStatsTrackerByRole(className);
        JsonNode toAdd = stats.statsOf(email);
        if(wasVictorious == 1){
            user.setWins_1v1(user.getWins_1v1() + 1);
            if (classStat != null) classStat.setWins(classStat.getWins() + 1);
        }else if(wasVictorious == -1){
            user.setLosses_1v1(user.getLosses_1v1() + 1);
            if (classStat != null) classStat.setLosses(classStat.getLosses() + 1);
        }else{ //tie
            user.setTies_1v1(user.getTies_1v1() + 1);
            if (classStat != null) classStat.setTies(classStat.getTies() + 1);
        }
        user.setRating_1v1(newRating);
        if(toAdd.has(StatEngine.StatEnum.GOALS.toString())){
            user.setGoals_1v1(user.getGoals_1v1() +  toAdd.get(StatEngine.StatEnum.GOALS.toString()).asInt());
            if (classStat != null) classStat.setGoals(classStat.getGoals() +  toAdd.get(StatEngine.StatEnum.GOALS.toString()).asInt());
        }
        if(toAdd.has(StatEngine.StatEnum.SIDEGOALS.toString())){
            user.setSidegoals_1v1((user.getSidegoals_1v1() +  toAdd.get(StatEngine.StatEnum.SIDEGOALS.toString()).asInt()));
            if (classStat != null) classStat.setSidegoals((classStat.getSidegoals() +  toAdd.get(StatEngine.StatEnum.SIDEGOALS.toString()).asInt()));
        }
        if(toAdd.has(StatEngine.StatEnum.POINTS.toString())){
            user.setPoints_1v1(user.getPoints_1v1() +  toAdd.get(StatEngine.StatEnum.POINTS.toString()).asDouble());
            if (classStat != null) classStat.setPoints(classStat.getPoints() +  toAdd.get(StatEngine.StatEnum.POINTS.toString()).asDouble());
        }
        if(toAdd.has(StatEngine.StatEnum.STEALS.toString())){
            user.setSteals_1v1((user.getSteals_1v1() +   toAdd.get(StatEngine.StatEnum.STEALS.toString()).asInt()));
            if (classStat != null) classStat.setSteals((classStat.getSteals() +   toAdd.get(StatEngine.StatEnum.STEALS.toString()).asInt()));
        }
        if(toAdd.has(StatEngine.StatEnum.BLOCKS.toString())){
            user.setBlocks_1v1((user.getBlocks_1v1() +  toAdd.get(StatEngine.StatEnum.BLOCKS.toString()).asInt()));
            if (classStat != null) classStat.setBlocks((classStat.getBlocks() +  toAdd.get(StatEngine.StatEnum.BLOCKS.toString()).asInt()));
        }
        if(toAdd.has(StatEngine.StatEnum.PASSES.toString())){
            user.setPasses_1v1((user.getPasses_1v1() +  toAdd.get(StatEngine.StatEnum.PASSES.toString()).asInt()));
            if (classStat != null) classStat.setPasses((classStat.getPasses() +  toAdd.get(StatEngine.StatEnum.PASSES.toString()).asInt()));
        }
        if(toAdd.has(StatEngine.StatEnum.KILLS.toString())){
            user.setKills_1v1((user.getKills_1v1() +  toAdd.get(StatEngine.StatEnum.KILLS.toString()).asInt()));
            if (classStat != null) classStat.setKills((classStat.getKills() +  toAdd.get(StatEngine.StatEnum.KILLS.toString()).asInt()));
        }
        if(toAdd.has(StatEngine.StatEnum.DEATHS.toString())){
            user.setDeaths_1v1((user.getDeaths_1v1() + toAdd.get(StatEngine.StatEnum.DEATHS.toString()).asInt()));
            if (classStat != null) classStat.setDeaths((classStat.getDeaths() + toAdd.get(StatEngine.StatEnum.DEATHS.toString()).asInt()));
        }
        if(toAdd.has(StatEngine.StatEnum.TURNOVERS.toString())){
            user.setTurnovers_1v1((user.getTurnovers_1v1() +  toAdd.get(StatEngine.StatEnum.TURNOVERS.toString()).asInt()));
            if (classStat != null) classStat.setTurnovers((classStat.getTurnovers() +  toAdd.get(StatEngine.StatEnum.TURNOVERS.toString()).asInt()));
        }
        if(toAdd.has(StatEngine.StatEnum.KILLASSISTS.toString())){
            user.setKillassists_1v1((user.getKillassists_1v1() +  toAdd.get(StatEngine.StatEnum.KILLASSISTS.toString()).asInt()));
            if (classStat != null) classStat.setKillassists((classStat.getKillassists() +  toAdd.get(StatEngine.StatEnum.KILLASSISTS.toString()).asInt()));
        }
        if(toAdd.has(StatEngine.StatEnum.GOALASSISTS.toString())){
            user.setGoalassists_1v1((user.getGoalassists_1v1() +  toAdd.get(StatEngine.StatEnum.GOALASSISTS.toString()).asInt()));
            if (classStat != null) classStat.setGoalassists((classStat.getGoalassists() +  toAdd.get(StatEngine.StatEnum.GOALASSISTS.toString()).asInt()));
        }
        if(toAdd.has(StatEngine.StatEnum.REBOUND.toString())){
            user.setRebounds_1v1((user.getRebounds_1v1() +  toAdd.get(StatEngine.StatEnum.REBOUND.toString()).asInt()));
            if (classStat != null) classStat.setRebounds((classStat.getRebounds() +  toAdd.get(StatEngine.StatEnum.REBOUND.toString()).asInt()));
        }
        if (classStat != null) {
            if (toAdd.has(StatEngine.StatEnum.SAVES.toString())) {
                classStat.setSaves(classStat.getSaves() + toAdd.get(StatEngine.StatEnum.SAVES.toString()).asInt());
            }
            if (toAdd.has(StatEngine.StatEnum.LASTHITS.toString())) {
                classStat.setLasthits(classStat.getLasthits() + toAdd.get(StatEngine.StatEnum.LASTHITS.toString()).asInt());
            }
            if (toAdd.has(StatEngine.StatEnum.MINIONDAMAGE.toString())) {
                classStat.setMiniondamage(classStat.getMiniondamage() + toAdd.get(StatEngine.StatEnum.MINIONDAMAGE.toString()).asDouble());
            }
            if (toAdd.has(StatEngine.StatEnum.UPGRADESGOLD.toString())) {
                classStat.setUpgradesgold(classStat.getUpgradesgold() + toAdd.get(StatEngine.StatEnum.UPGRADESGOLD.toString()).asInt());
            }
            if (toAdd.has(StatEngine.StatEnum.CONSUMABLESGOLD.toString())) {
                classStat.setConsumablesgold(classStat.getConsumablesgold() + toAdd.get(StatEngine.StatEnum.CONSUMABLESGOLD.toString()).asInt());
            }
            if (toAdd.has(StatEngine.StatEnum.SIDEGOAL_SAVES.toString())) {
                classStat.setSidegoalsaves(classStat.getSidegoalsaves() + toAdd.get(StatEngine.StatEnum.SIDEGOAL_SAVES.toString()).asInt());
            }
            if (toAdd.has(StatEngine.StatEnum.CENTERGOAL_SAVES.toString())) {
                classStat.setCentergoalsaves(classStat.getCentergoalsaves() + toAdd.get(StatEngine.StatEnum.CENTERGOAL_SAVES.toString()).asInt());
            }
            if (toAdd.has(StatEngine.StatEnum.SIDEGOALS_CONCEDED.toString())) {
                classStat.setSidegoalsconceded(classStat.getSidegoalsconceded() + toAdd.get(StatEngine.StatEnum.SIDEGOALS_CONCEDED.toString()).asInt());
            }
            if (toAdd.has(StatEngine.StatEnum.GOALS_CONCEDED.toString())) {
                classStat.setGoalsconceded(classStat.getGoalsconceded() + toAdd.get(StatEngine.StatEnum.GOALS_CONCEDED.toString()).asInt());
            }
            if (toAdd.has(StatEngine.StatEnum.MANASPENT.toString())) {
                classStat.setManaspent(classStat.getManaspent() + toAdd.get(StatEngine.StatEnum.MANASPENT.toString()).asInt());
            }
        }
        userService.saveUser(user);
        if (classStat != null) classService.saveClass(classStat);
    }

    public synchronized void recordClassStats(StatEngine stats, String trackerKey, String className, int wasVictorious) {
        if (classService == null) return;
        ClassStat classStat = classService.findStatsTrackerByRole(className);
        if (classStat == null) {
            classStat = new ClassStat(className);
        }
        com.fasterxml.jackson.databind.JsonNode toAdd = stats.statsOf(trackerKey);
        if (wasVictorious == 1) {
            classStat.setWins(classStat.getWins() + 1);
        } else if (wasVictorious == -1) {
            classStat.setLosses(classStat.getLosses() + 1);
        } else {
            classStat.setTies(classStat.getTies() + 1);
        }
        if (toAdd.has(StatEngine.StatEnum.GOALS.toString())) {
            classStat.setGoals(classStat.getGoals() + toAdd.get(StatEngine.StatEnum.GOALS.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.SIDEGOALS.toString())) {
            classStat.setSidegoals(classStat.getSidegoals() + toAdd.get(StatEngine.StatEnum.SIDEGOALS.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.POINTS.toString())) {
            classStat.setPoints(classStat.getPoints() + toAdd.get(StatEngine.StatEnum.POINTS.toString()).asDouble());
        }
        if (toAdd.has(StatEngine.StatEnum.STEALS.toString())) {
            classStat.setSteals(classStat.getSteals() + toAdd.get(StatEngine.StatEnum.STEALS.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.BLOCKS.toString())) {
            classStat.setBlocks(classStat.getBlocks() + toAdd.get(StatEngine.StatEnum.BLOCKS.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.PASSES.toString())) {
            classStat.setPasses(classStat.getPasses() + toAdd.get(StatEngine.StatEnum.PASSES.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.KILLS.toString())) {
            classStat.setKills(classStat.getKills() + toAdd.get(StatEngine.StatEnum.KILLS.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.DEATHS.toString())) {
            classStat.setDeaths(classStat.getDeaths() + toAdd.get(StatEngine.StatEnum.DEATHS.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.TURNOVERS.toString())) {
            classStat.setTurnovers(classStat.getTurnovers() + toAdd.get(StatEngine.StatEnum.TURNOVERS.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.KILLASSISTS.toString())) {
            classStat.setKillassists(classStat.getKillassists() + toAdd.get(StatEngine.StatEnum.KILLASSISTS.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.GOALASSISTS.toString())) {
            classStat.setGoalassists(classStat.getGoalassists() + toAdd.get(StatEngine.StatEnum.GOALASSISTS.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.REBOUND.toString())) {
            classStat.setRebounds(classStat.getRebounds() + toAdd.get(StatEngine.StatEnum.REBOUND.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.SAVES.toString())) {
            classStat.setSaves(classStat.getSaves() + toAdd.get(StatEngine.StatEnum.SAVES.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.LASTHITS.toString())) {
            classStat.setLasthits(classStat.getLasthits() + toAdd.get(StatEngine.StatEnum.LASTHITS.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.MINIONDAMAGE.toString())) {
            classStat.setMiniondamage(classStat.getMiniondamage() + toAdd.get(StatEngine.StatEnum.MINIONDAMAGE.toString()).asDouble());
        }
        if (toAdd.has(StatEngine.StatEnum.UPGRADESGOLD.toString())) {
            classStat.setUpgradesgold(classStat.getUpgradesgold() + toAdd.get(StatEngine.StatEnum.UPGRADESGOLD.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.CONSUMABLESGOLD.toString())) {
            classStat.setConsumablesgold(classStat.getConsumablesgold() + toAdd.get(StatEngine.StatEnum.CONSUMABLESGOLD.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.SIDEGOAL_SAVES.toString())) {
            classStat.setSidegoalsaves(classStat.getSidegoalsaves() + toAdd.get(StatEngine.StatEnum.SIDEGOAL_SAVES.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.CENTERGOAL_SAVES.toString())) {
            classStat.setCentergoalsaves(classStat.getCentergoalsaves() + toAdd.get(StatEngine.StatEnum.CENTERGOAL_SAVES.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.SIDEGOALS_CONCEDED.toString())) {
            classStat.setSidegoalsconceded(classStat.getSidegoalsconceded() + toAdd.get(StatEngine.StatEnum.SIDEGOALS_CONCEDED.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.GOALS_CONCEDED.toString())) {
            classStat.setGoalsconceded(classStat.getGoalsconceded() + toAdd.get(StatEngine.StatEnum.GOALS_CONCEDED.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.MANASPENT.toString())) {
            classStat.setManaspent(classStat.getManaspent() + toAdd.get(StatEngine.StatEnum.MANASPENT.toString()).asInt());
        }
        classService.saveClass(classStat);
    }

    public synchronized void recordMasteryStats(StatEngine stats, String trackerKey, String masteryPair, int wasVictorious) {
        if (masteriesService == null) return;
        MasteriesStat masteriesStat = masteriesService.findStatsTrackerByRole(masteryPair);
        if (masteriesStat == null) {
            masteriesStat = new MasteriesStat(masteryPair);
        }
        com.fasterxml.jackson.databind.JsonNode toAdd = stats.statsOf(trackerKey);
        if (wasVictorious == 1) {
            masteriesStat.setWins(masteriesStat.getWins() + 1);
        } else if (wasVictorious == -1) {
            masteriesStat.setLosses(masteriesStat.getLosses() + 1);
        } else {
            masteriesStat.setTies(masteriesStat.getTies() + 1);
        }
        if (toAdd.has(StatEngine.StatEnum.GOALS.toString())) {
            masteriesStat.setGoals(masteriesStat.getGoals() + toAdd.get(StatEngine.StatEnum.GOALS.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.SIDEGOALS.toString())) {
            masteriesStat.setSidegoals(masteriesStat.getSidegoals() + toAdd.get(StatEngine.StatEnum.SIDEGOALS.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.POINTS.toString())) {
            masteriesStat.setPoints(masteriesStat.getPoints() + toAdd.get(StatEngine.StatEnum.POINTS.toString()).asDouble());
        }
        if (toAdd.has(StatEngine.StatEnum.STEALS.toString())) {
            masteriesStat.setSteals(masteriesStat.getSteals() + toAdd.get(StatEngine.StatEnum.STEALS.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.BLOCKS.toString())) {
            masteriesStat.setBlocks(masteriesStat.getBlocks() + toAdd.get(StatEngine.StatEnum.BLOCKS.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.PASSES.toString())) {
            masteriesStat.setPasses(masteriesStat.getPasses() + toAdd.get(StatEngine.StatEnum.PASSES.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.KILLS.toString())) {
            masteriesStat.setKills(masteriesStat.getKills() + toAdd.get(StatEngine.StatEnum.KILLS.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.DEATHS.toString())) {
            masteriesStat.setDeaths(masteriesStat.getDeaths() + toAdd.get(StatEngine.StatEnum.DEATHS.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.TURNOVERS.toString())) {
            masteriesStat.setTurnovers(masteriesStat.getTurnovers() + toAdd.get(StatEngine.StatEnum.TURNOVERS.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.KILLASSISTS.toString())) {
            masteriesStat.setKillassists(masteriesStat.getKillassists() + toAdd.get(StatEngine.StatEnum.KILLASSISTS.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.GOALASSISTS.toString())) {
            masteriesStat.setGoalassists(masteriesStat.getGoalassists() + toAdd.get(StatEngine.StatEnum.GOALASSISTS.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.REBOUND.toString())) {
            masteriesStat.setRebounds(masteriesStat.getRebounds() + toAdd.get(StatEngine.StatEnum.REBOUND.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.SAVES.toString())) {
            masteriesStat.setSaves(masteriesStat.getSaves() + toAdd.get(StatEngine.StatEnum.SAVES.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.LASTHITS.toString())) {
            masteriesStat.setLasthits(masteriesStat.getLasthits() + toAdd.get(StatEngine.StatEnum.LASTHITS.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.MINIONDAMAGE.toString())) {
            masteriesStat.setMiniondamage(masteriesStat.getMiniondamage() + toAdd.get(StatEngine.StatEnum.MINIONDAMAGE.toString()).asDouble());
        }
        if (toAdd.has(StatEngine.StatEnum.UPGRADESGOLD.toString())) {
            masteriesStat.setUpgradesgold(masteriesStat.getUpgradesgold() + toAdd.get(StatEngine.StatEnum.UPGRADESGOLD.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.CONSUMABLESGOLD.toString())) {
            masteriesStat.setConsumablesgold(masteriesStat.getConsumablesgold() + toAdd.get(StatEngine.StatEnum.CONSUMABLESGOLD.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.SIDEGOAL_SAVES.toString())) {
            masteriesStat.setSidegoalsaves(masteriesStat.getSidegoalsaves() + toAdd.get(StatEngine.StatEnum.SIDEGOAL_SAVES.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.CENTERGOAL_SAVES.toString())) {
            masteriesStat.setCentergoalsaves(masteriesStat.getCentergoalsaves() + toAdd.get(StatEngine.StatEnum.CENTERGOAL_SAVES.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.SIDEGOALS_CONCEDED.toString())) {
            masteriesStat.setSidegoalsconceded(masteriesStat.getSidegoalsconceded() + toAdd.get(StatEngine.StatEnum.SIDEGOALS_CONCEDED.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.GOALS_CONCEDED.toString())) {
            masteriesStat.setGoalsconceded(masteriesStat.getGoalsconceded() + toAdd.get(StatEngine.StatEnum.GOALS_CONCEDED.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.MANASPENT.toString())) {
            masteriesStat.setManaspent(masteriesStat.getManaspent() + toAdd.get(StatEngine.StatEnum.MANASPENT.toString()).asInt());
        }
        masteriesService.saveMasteriesStat(masteriesStat);
    }

    public synchronized void recordUpgradeStats(StatEngine stats, String trackerKey, String upgradeKey, int wasVictorious) {
        if (upgradeClassStatService == null) return;
        UpgradeClassStat upgradeStat = upgradeClassStatService.findStatsTrackerByUpgrade(upgradeKey);
        if (upgradeStat == null) {
            upgradeStat = new UpgradeClassStat(upgradeKey);
        }
        com.fasterxml.jackson.databind.JsonNode toAdd = stats.statsOf(trackerKey);
        if (wasVictorious == 1) {
            upgradeStat.setWins(upgradeStat.getWins() + 1);
        } else if (wasVictorious == -1) {
            upgradeStat.setLosses(upgradeStat.getLosses() + 1);
        } else {
            upgradeStat.setTies(upgradeStat.getTies() + 1);
        }
        if (toAdd.has(StatEngine.StatEnum.GOALS.toString())) {
            upgradeStat.setGoals(upgradeStat.getGoals() + toAdd.get(StatEngine.StatEnum.GOALS.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.SIDEGOALS.toString())) {
            upgradeStat.setSidegoals(upgradeStat.getSidegoals() + toAdd.get(StatEngine.StatEnum.SIDEGOALS.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.POINTS.toString())) {
            upgradeStat.setPoints(upgradeStat.getPoints() + toAdd.get(StatEngine.StatEnum.POINTS.toString()).asDouble());
        }
        if (toAdd.has(StatEngine.StatEnum.STEALS.toString())) {
            upgradeStat.setSteals(upgradeStat.getSteals() + toAdd.get(StatEngine.StatEnum.STEALS.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.BLOCKS.toString())) {
            upgradeStat.setBlocks(upgradeStat.getBlocks() + toAdd.get(StatEngine.StatEnum.BLOCKS.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.PASSES.toString())) {
            upgradeStat.setPasses(upgradeStat.getPasses() + toAdd.get(StatEngine.StatEnum.PASSES.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.KILLS.toString())) {
            upgradeStat.setKills(upgradeStat.getKills() + toAdd.get(StatEngine.StatEnum.KILLS.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.DEATHS.toString())) {
            upgradeStat.setDeaths(upgradeStat.getDeaths() + toAdd.get(StatEngine.StatEnum.DEATHS.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.TURNOVERS.toString())) {
            upgradeStat.setTurnovers(upgradeStat.getTurnovers() + toAdd.get(StatEngine.StatEnum.TURNOVERS.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.KILLASSISTS.toString())) {
            upgradeStat.setKillassists(upgradeStat.getKillassists() + toAdd.get(StatEngine.StatEnum.KILLASSISTS.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.GOALASSISTS.toString())) {
            upgradeStat.setGoalassists(upgradeStat.getGoalassists() + toAdd.get(StatEngine.StatEnum.GOALASSISTS.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.REBOUND.toString())) {
            upgradeStat.setRebounds(upgradeStat.getRebounds() + toAdd.get(StatEngine.StatEnum.REBOUND.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.SAVES.toString())) {
            upgradeStat.setSaves(upgradeStat.getSaves() + toAdd.get(StatEngine.StatEnum.SAVES.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.LASTHITS.toString())) {
            upgradeStat.setLasthits(upgradeStat.getLasthits() + toAdd.get(StatEngine.StatEnum.LASTHITS.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.MINIONDAMAGE.toString())) {
            upgradeStat.setMiniondamage(upgradeStat.getMiniondamage() + toAdd.get(StatEngine.StatEnum.MINIONDAMAGE.toString()).asDouble());
        }
        if (toAdd.has(StatEngine.StatEnum.UPGRADESGOLD.toString())) {
            upgradeStat.setUpgradesgold(upgradeStat.getUpgradesgold() + toAdd.get(StatEngine.StatEnum.UPGRADESGOLD.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.CONSUMABLESGOLD.toString())) {
            upgradeStat.setConsumablesgold(upgradeStat.getConsumablesgold() + toAdd.get(StatEngine.StatEnum.CONSUMABLESGOLD.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.SIDEGOAL_SAVES.toString())) {
            upgradeStat.setSidegoalsaves(upgradeStat.getSidegoalsaves() + toAdd.get(StatEngine.StatEnum.SIDEGOAL_SAVES.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.CENTERGOAL_SAVES.toString())) {
            upgradeStat.setCentergoalsaves(upgradeStat.getCentergoalsaves() + toAdd.get(StatEngine.StatEnum.CENTERGOAL_SAVES.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.SIDEGOALS_CONCEDED.toString())) {
            upgradeStat.setSidegoalsconceded(upgradeStat.getSidegoalsconceded() + toAdd.get(StatEngine.StatEnum.SIDEGOALS_CONCEDED.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.GOALS_CONCEDED.toString())) {
            upgradeStat.setGoalsconceded(upgradeStat.getGoalsconceded() + toAdd.get(StatEngine.StatEnum.GOALS_CONCEDED.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.MANASPENT.toString())) {
            upgradeStat.setManaspent(upgradeStat.getManaspent() + toAdd.get(StatEngine.StatEnum.MANASPENT.toString()).asInt());
        }
        upgradeClassStatService.saveUpgradeClassStat(upgradeStat);
    }

    public synchronized void recordBuildOrderStats(StatEngine stats, String trackerKey, String buildName, int wasVictorious) {
        if (buildOrderStatService == null || buildName == null) return;
        BuildOrderStat stat = buildOrderStatService.findStatsTrackerByBuildName(buildName);
        if (stat == null) {
            stat = new BuildOrderStat(buildName);
        }
        com.fasterxml.jackson.databind.JsonNode toAdd = stats.statsOf(trackerKey);
        if (wasVictorious == 1) {
            stat.setWins(stat.getWins() + 1);
        } else if (wasVictorious == -1) {
            stat.setLosses(stat.getLosses() + 1);
        } else {
            stat.setTies(stat.getTies() + 1);
        }
        if (toAdd.has(StatEngine.StatEnum.GOALS.toString())) {
            stat.setGoals(stat.getGoals() + toAdd.get(StatEngine.StatEnum.GOALS.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.SIDEGOALS.toString())) {
            stat.setSidegoals(stat.getSidegoals() + toAdd.get(StatEngine.StatEnum.SIDEGOALS.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.POINTS.toString())) {
            stat.setPoints(stat.getPoints() + toAdd.get(StatEngine.StatEnum.POINTS.toString()).asDouble());
        }
        if (toAdd.has(StatEngine.StatEnum.STEALS.toString())) {
            stat.setSteals(stat.getSteals() + toAdd.get(StatEngine.StatEnum.STEALS.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.BLOCKS.toString())) {
            stat.setBlocks(stat.getBlocks() + toAdd.get(StatEngine.StatEnum.BLOCKS.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.PASSES.toString())) {
            stat.setPasses(stat.getPasses() + toAdd.get(StatEngine.StatEnum.PASSES.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.KILLS.toString())) {
            stat.setKills(stat.getKills() + toAdd.get(StatEngine.StatEnum.KILLS.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.DEATHS.toString())) {
            stat.setDeaths(stat.getDeaths() + toAdd.get(StatEngine.StatEnum.DEATHS.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.TURNOVERS.toString())) {
            stat.setTurnovers(stat.getTurnovers() + toAdd.get(StatEngine.StatEnum.TURNOVERS.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.KILLASSISTS.toString())) {
            stat.setKillassists(stat.getKillassists() + toAdd.get(StatEngine.StatEnum.KILLASSISTS.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.GOALASSISTS.toString())) {
            stat.setGoalassists(stat.getGoalassists() + toAdd.get(StatEngine.StatEnum.GOALASSISTS.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.REBOUND.toString())) {
            stat.setRebounds(stat.getRebounds() + toAdd.get(StatEngine.StatEnum.REBOUND.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.SAVES.toString())) {
            stat.setSaves(stat.getSaves() + toAdd.get(StatEngine.StatEnum.SAVES.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.LASTHITS.toString())) {
            stat.setLasthits(stat.getLasthits() + toAdd.get(StatEngine.StatEnum.LASTHITS.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.MINIONDAMAGE.toString())) {
            stat.setMiniondamage(stat.getMiniondamage() + toAdd.get(StatEngine.StatEnum.MINIONDAMAGE.toString()).asDouble());
        }
        if (toAdd.has(StatEngine.StatEnum.UPGRADESGOLD.toString())) {
            stat.setUpgradesgold(stat.getUpgradesgold() + toAdd.get(StatEngine.StatEnum.UPGRADESGOLD.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.CONSUMABLESGOLD.toString())) {
            stat.setConsumablesgold(stat.getConsumablesgold() + toAdd.get(StatEngine.StatEnum.CONSUMABLESGOLD.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.SIDEGOAL_SAVES.toString())) {
            stat.setSidegoalsaves(stat.getSidegoalsaves() + toAdd.get(StatEngine.StatEnum.SIDEGOAL_SAVES.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.CENTERGOAL_SAVES.toString())) {
            stat.setCentergoalsaves(stat.getCentergoalsaves() + toAdd.get(StatEngine.StatEnum.CENTERGOAL_SAVES.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.SIDEGOALS_CONCEDED.toString())) {
            stat.setSidegoalsconceded(stat.getSidegoalsconceded() + toAdd.get(StatEngine.StatEnum.SIDEGOALS_CONCEDED.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.GOALS_CONCEDED.toString())) {
            stat.setGoalsconceded(stat.getGoalsconceded() + toAdd.get(StatEngine.StatEnum.GOALS_CONCEDED.toString()).asInt());
        }
        if (toAdd.has(StatEngine.StatEnum.MANASPENT.toString())) {
            stat.setManaspent(stat.getManaspent() + toAdd.get(StatEngine.StatEnum.MANASPENT.toString()).asInt());
        }
        buildOrderStatService.saveBuildOrderStat(stat);
    }
}
