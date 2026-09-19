package authserver.users;

import authserver.models.BuildOrderStat;
import authserver.models.ClassStat;
import authserver.models.MasteriesStat;
import authserver.models.PlayerGameStat;
import authserver.models.UpgradeClassStat;
import authserver.models.User;
import authserver.models.UserClassStat;
import authserver.users.builds.BuildOrderStatServiceImpl;
import authserver.users.classes.ClassServiceImpl;
import authserver.users.classes.UserClassStatServiceImpl;
import authserver.users.masteries.MasteriesStatServiceImpl;
import authserver.users.playerstats.PlayerGameStatService;
import authserver.users.upgrades.UpgradeClassStatServiceImpl;
import authserver.users.identities.CustomUserDetailsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gameserver.engine.GameEngine;
import gameserver.engine.StatEngine;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.Titan;
import gameserver.entity.TitanType;
import networking.PlayerDivider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class PersistenceManager {

    @Autowired
    public CustomUserDetailsService userService;

    @Autowired
    public ClassServiceImpl classService;

    @Autowired
    public UserClassStatServiceImpl userClassStatService;

    @Autowired
    public MasteriesStatServiceImpl masteriesService;

    @Autowired
    public UpgradeClassStatServiceImpl upgradeClassStatService;

    @Autowired
    public BuildOrderStatServiceImpl buildOrderStatService;

    @Autowired
    public PlayerGameStatService playerGameStatService;

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
        UserClassStat userClassStat = null;
        if (userClassStatService != null) {
            userClassStat = userClassStatService.findByUserAndRole(email, className);
            if (userClassStat == null) {
                userClassStat = new UserClassStat(email, className);
                if (user.getUsername() != null) {
                    userClassStat.setUsername(user.getUsername());
                }
            }
        }
        if(wasVictorious == 1){
            user.setWins(user.getWins() + 1);
            if (classStat != null) classStat.setWins(classStat.getWins() + 1);
            if (userClassStat != null) userClassStat.setWins(userClassStat.getWins() + 1);
        }else if(wasVictorious == -1){
            user.setLosses(user.getLosses() + 1);
            if (classStat != null) classStat.setLosses(classStat.getLosses() + 1);
            if (userClassStat != null) userClassStat.setLosses(userClassStat.getLosses() + 1);
        }else{ //tie
            user.setTies(user.getTies() + 1);
            if (classStat != null) classStat.setTies(classStat.getTies() + 1);
            if (userClassStat != null) userClassStat.setTies(userClassStat.getTies() + 1);
        }
        if (newRating > 0.0) {
            user.setRating(newRating);
        }
        if ("GOALIE".equalsIgnoreCase(className)) {
            user.setGoalie_matches(user.getGoalie_matches() + 1);
        }
        if(toAdd.has(StatEngine.StatEnum.GOALS.toString())){
            user.setGoals((user.getGoals() + toAdd.get(StatEngine.StatEnum.GOALS.toString()).asInt()));
            if (classStat != null) classStat.setGoals((classStat.getGoals() +  toAdd.get(StatEngine.StatEnum.GOALS.toString()).asInt()));
            if (userClassStat != null) userClassStat.setGoals(userClassStat.getGoals() + toAdd.get(StatEngine.StatEnum.GOALS.toString()).asInt());
        }
        if(toAdd.has(StatEngine.StatEnum.SIDEGOALS.toString())){
            user.setSidegoals((user.getSidegoals() + toAdd.get(StatEngine.StatEnum.SIDEGOALS.toString()).asInt()));
            if (classStat != null) classStat.setSidegoals((classStat.getSidegoals() + toAdd.get(StatEngine.StatEnum.SIDEGOALS.toString()).asInt()));
            if (userClassStat != null) userClassStat.setSidegoals(userClassStat.getSidegoals() + toAdd.get(StatEngine.StatEnum.SIDEGOALS.toString()).asInt());
        }
        if(toAdd.has(StatEngine.StatEnum.POINTS.toString())){
            user.setPoints(user.getPoints() +  toAdd.get(StatEngine.StatEnum.POINTS.toString()).asDouble());
            if (classStat != null) classStat.setPoints(classStat.getPoints() +  toAdd.get(StatEngine.StatEnum.POINTS.toString()).asDouble());
            if (userClassStat != null) userClassStat.setPoints(userClassStat.getPoints() + toAdd.get(StatEngine.StatEnum.POINTS.toString()).asDouble());
        }
        if(toAdd.has(StatEngine.StatEnum.STEALS.toString())){
            user.setSteals((user.getSteals() +   toAdd.get(StatEngine.StatEnum.STEALS.toString()).asInt()));
            if (classStat != null) classStat.setSteals((classStat.getSteals() + toAdd.get(StatEngine.StatEnum.STEALS.toString()).asInt()));
            if (userClassStat != null) userClassStat.setSteals(userClassStat.getSteals() + toAdd.get(StatEngine.StatEnum.STEALS.toString()).asInt());
        }
        if(toAdd.has(StatEngine.StatEnum.BLOCKS.toString())){
            user.setBlocks((user.getBlocks() +  toAdd.get(StatEngine.StatEnum.BLOCKS.toString()).asInt()));
            if (classStat != null) classStat.setBlocks((classStat.getBlocks() +  toAdd.get(StatEngine.StatEnum.BLOCKS.toString()).asInt()));
            if (userClassStat != null) userClassStat.setBlocks(userClassStat.getBlocks() + toAdd.get(StatEngine.StatEnum.BLOCKS.toString()).asInt());
        }
        if(toAdd.has(StatEngine.StatEnum.PASSES.toString())){
            user.setPasses((user.getPasses() +  toAdd.get(StatEngine.StatEnum.PASSES.toString()).asInt()));
            if (classStat != null) classStat.setPasses((classStat.getPasses() +  toAdd.get(StatEngine.StatEnum.PASSES.toString()).asInt()));
            if (userClassStat != null) userClassStat.setPasses(userClassStat.getPasses() + toAdd.get(StatEngine.StatEnum.PASSES.toString()).asInt());
        }
        if(toAdd.has(StatEngine.StatEnum.KILLS.toString())){
            user.setKills((user.getKills() +  toAdd.get(StatEngine.StatEnum.KILLS.toString()).asInt()));
            if (classStat != null) classStat.setKills((classStat.getKills() +  toAdd.get(StatEngine.StatEnum.KILLS.toString()).asInt()));
            if (userClassStat != null) userClassStat.setKills(userClassStat.getKills() + toAdd.get(StatEngine.StatEnum.KILLS.toString()).asInt());
        }
        if(toAdd.has(StatEngine.StatEnum.DEATHS.toString())){
            user.setDeaths((user.getDeaths() + toAdd.get(StatEngine.StatEnum.DEATHS.toString()).asInt()));
            if (classStat != null) classStat.setDeaths((classStat.getDeaths() + toAdd.get(StatEngine.StatEnum.DEATHS.toString()).asInt()));
            if (userClassStat != null) userClassStat.setDeaths(userClassStat.getDeaths() + toAdd.get(StatEngine.StatEnum.DEATHS.toString()).asInt());
        }
        if(toAdd.has(StatEngine.StatEnum.TURNOVERS.toString())){
            user.setTurnovers((user.getTurnovers() +  toAdd.get(StatEngine.StatEnum.TURNOVERS.toString()).asInt()));
            if (classStat != null) classStat.setTurnovers((classStat.getTurnovers() +  toAdd.get(StatEngine.StatEnum.TURNOVERS.toString()).asInt()));
            if (userClassStat != null) userClassStat.setTurnovers(userClassStat.getTurnovers() + toAdd.get(StatEngine.StatEnum.TURNOVERS.toString()).asInt());
        }
        if(toAdd.has(StatEngine.StatEnum.KILLASSISTS.toString())){
            user.setKillassists((user.getKillassists() +  toAdd.get(StatEngine.StatEnum.KILLASSISTS.toString()).asInt()));
            if (classStat != null) classStat.setKillassists((classStat.getKillassists() +  toAdd.get(StatEngine.StatEnum.KILLASSISTS.toString()).asInt()));
            if (userClassStat != null) userClassStat.setKillassists(userClassStat.getKillassists() + toAdd.get(StatEngine.StatEnum.KILLASSISTS.toString()).asInt());
        }
        if(toAdd.has(StatEngine.StatEnum.GOALASSISTS.toString())){
            user.setGoalassists(user.getGoalassists() +  toAdd.get(StatEngine.StatEnum.GOALASSISTS.toString()).asInt());
            if (classStat != null) classStat.setGoalassists(classStat.getGoalassists() +  toAdd.get(StatEngine.StatEnum.GOALASSISTS.toString()).asInt());
            if (userClassStat != null) userClassStat.setGoalassists(userClassStat.getGoalassists() + toAdd.get(StatEngine.StatEnum.GOALASSISTS.toString()).asInt());
        }
        if(toAdd.has(StatEngine.StatEnum.SIDEGOALASSISTS.toString())){
            user.setSidegoalassists(user.getSidegoalassists() +  toAdd.get(StatEngine.StatEnum.SIDEGOALASSISTS.toString()).asInt());
            if (classStat != null) classStat.setSidegoalassists(classStat.getSidegoalassists() +  toAdd.get(StatEngine.StatEnum.SIDEGOALASSISTS.toString()).asInt());
            if (userClassStat != null) userClassStat.setSidegoalassists(userClassStat.getSidegoalassists() + toAdd.get(StatEngine.StatEnum.SIDEGOALASSISTS.toString()).asInt());
        }
        if(toAdd.has(StatEngine.StatEnum.REBOUND.toString())){
            user.setRebounds(user.getRebounds() +  toAdd.get(StatEngine.StatEnum.REBOUND.toString()).asInt());
            if (classStat != null) classStat.setRebounds(classStat.getRebounds() + toAdd.get(StatEngine.StatEnum.REBOUND.toString()).asInt());
            if (userClassStat != null) userClassStat.setRebounds(userClassStat.getRebounds() + toAdd.get(StatEngine.StatEnum.REBOUND.toString()).asInt());
        }
        if(toAdd.has(StatEngine.StatEnum.SAVES.toString())){
            user.setSaves(user.getSaves() + toAdd.get(StatEngine.StatEnum.SAVES.toString()).asInt());
            if (userClassStat != null) userClassStat.setSaves(userClassStat.getSaves() + toAdd.get(StatEngine.StatEnum.SAVES.toString()).asInt());
        }
        if(toAdd.has(StatEngine.StatEnum.SIDEGOAL_SAVES.toString())){
            user.setSidegoalsaves(user.getSidegoalsaves() + toAdd.get(StatEngine.StatEnum.SIDEGOAL_SAVES.toString()).asInt());
            if (userClassStat != null) userClassStat.setSidegoalsaves(userClassStat.getSidegoalsaves() + toAdd.get(StatEngine.StatEnum.SIDEGOAL_SAVES.toString()).asInt());
        }
        if(toAdd.has(StatEngine.StatEnum.CENTERGOAL_SAVES.toString())){
            user.setCentergoalsaves(user.getCentergoalsaves() + toAdd.get(StatEngine.StatEnum.CENTERGOAL_SAVES.toString()).asInt());
            if (userClassStat != null) userClassStat.setCentergoalsaves(userClassStat.getCentergoalsaves() + toAdd.get(StatEngine.StatEnum.CENTERGOAL_SAVES.toString()).asInt());
        }
        if(toAdd.has(StatEngine.StatEnum.SIDEGOALS_CONCEDED.toString())){
            user.setSidegoalsconceded(user.getSidegoalsconceded() + toAdd.get(StatEngine.StatEnum.SIDEGOALS_CONCEDED.toString()).asInt());
            if (userClassStat != null) userClassStat.setSidegoalsconceded(userClassStat.getSidegoalsconceded() + toAdd.get(StatEngine.StatEnum.SIDEGOALS_CONCEDED.toString()).asInt());
        }
        if(toAdd.has(StatEngine.StatEnum.GOALS_CONCEDED.toString())){
            user.setGoalsconceded(user.getGoalsconceded() + toAdd.get(StatEngine.StatEnum.GOALS_CONCEDED.toString()).asInt());
            if (userClassStat != null) userClassStat.setGoalsconceded(userClassStat.getGoalsconceded() + toAdd.get(StatEngine.StatEnum.GOALS_CONCEDED.toString()).asInt());
        }
        if(toAdd.has(StatEngine.StatEnum.UPGRADESGOLD.toString())){
            user.setUpgradesgold(user.getUpgradesgold() + toAdd.get(StatEngine.StatEnum.UPGRADESGOLD.toString()).asInt());
            if (userClassStat != null) userClassStat.setUpgradesgold(userClassStat.getUpgradesgold() + toAdd.get(StatEngine.StatEnum.UPGRADESGOLD.toString()).asInt());
        }
        if(toAdd.has(StatEngine.StatEnum.CONSUMABLESGOLD.toString())){
            user.setConsumablesgold(user.getConsumablesgold() + toAdd.get(StatEngine.StatEnum.CONSUMABLESGOLD.toString()).asInt());
            if (userClassStat != null) userClassStat.setConsumablesgold(userClassStat.getConsumablesgold() + toAdd.get(StatEngine.StatEnum.CONSUMABLESGOLD.toString()).asInt());
        }
        if(toAdd.has(StatEngine.StatEnum.MANASPENT.toString())){
            user.setManaspent(user.getManaspent() + toAdd.get(StatEngine.StatEnum.MANASPENT.toString()).asInt());
            if (userClassStat != null) userClassStat.setManaspent(userClassStat.getManaspent() + toAdd.get(StatEngine.StatEnum.MANASPENT.toString()).asInt());
        }
        // Goalie action stats: record directly into UserClassStat for GOALIE
        if(toAdd.has(StatEngine.StatEnum.BLOCKS_G.toString())){
            int val = toAdd.get(StatEngine.StatEnum.BLOCKS_G.toString()).asInt();
            user.setBlocks_g(user.getBlocks_g() + val);
            if (userClassStat != null) userClassStat.setBlocks(userClassStat.getBlocks() + val);
        }
        if(toAdd.has(StatEngine.StatEnum.PASSES_G.toString())){
            int val = toAdd.get(StatEngine.StatEnum.PASSES_G.toString()).asInt();
            user.setPasses_g(user.getPasses_g() + val);
            if (userClassStat != null) userClassStat.setPasses(userClassStat.getPasses() + val);
        }
        if(toAdd.has(StatEngine.StatEnum.TURNOVERS_G.toString())){
            int val = toAdd.get(StatEngine.StatEnum.TURNOVERS_G.toString()).asInt();
            user.setTurnovers_g(user.getTurnovers_g() + val);
            if (userClassStat != null) userClassStat.setTurnovers(userClassStat.getTurnovers() + val);
        }
        if(toAdd.has(StatEngine.StatEnum.REBOUND_G.toString())){
            int val = toAdd.get(StatEngine.StatEnum.REBOUND_G.toString()).asInt();
            user.setRebounds_g(user.getRebounds_g() + val);
            if (userClassStat != null) userClassStat.setRebounds(userClassStat.getRebounds() + val);
        }
        if(toAdd.has(StatEngine.StatEnum.STEALS_G.toString())){
            int val = toAdd.get(StatEngine.StatEnum.STEALS_G.toString()).asInt();
            user.setSteals_g(user.getSteals_g() + val);
            if (userClassStat != null) userClassStat.setSteals(userClassStat.getSteals() + val);
        }
        if(toAdd.has(StatEngine.StatEnum.KILLS_G.toString())){
            int val = toAdd.get(StatEngine.StatEnum.KILLS_G.toString()).asInt();
            user.setKills_g(user.getKills_g() + val);
            if (userClassStat != null) userClassStat.setKills(userClassStat.getKills() + val);
        }
        if(toAdd.has(StatEngine.StatEnum.DEATHS_G.toString())){
            int val = toAdd.get(StatEngine.StatEnum.DEATHS_G.toString()).asInt();
            user.setDeaths_g(user.getDeaths_g() + val);
            if (userClassStat != null) userClassStat.setDeaths(userClassStat.getDeaths() + val);
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
        if (userClassStat != null && userClassStatService != null) userClassStatService.save(userClassStat);
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
        UserClassStat userClassStat = null;
        if (userClassStatService != null) {
            userClassStat = userClassStatService.findByUserAndRole(email, className);
            if (userClassStat == null) {
                userClassStat = new UserClassStat(email, className);
                if (user.getUsername() != null) {
                    userClassStat.setUsername(user.getUsername());
                }
            }
        }
        if(wasVictorious == 1){
            user.setWins_1v1(user.getWins_1v1() + 1);
            if (classStat != null) classStat.setWins(classStat.getWins() + 1);
            if (userClassStat != null) userClassStat.setWins_1v1(userClassStat.getWins_1v1() + 1);
        }else if(wasVictorious == -1){
            user.setLosses_1v1(user.getLosses_1v1() + 1);
            if (classStat != null) classStat.setLosses(classStat.getLosses() + 1);
            if (userClassStat != null) userClassStat.setLosses_1v1(userClassStat.getLosses_1v1() + 1);
        }else{ //tie
            user.setTies_1v1(user.getTies_1v1() + 1);
            if (classStat != null) classStat.setTies(classStat.getTies() + 1);
            if (userClassStat != null) userClassStat.setTies_1v1(userClassStat.getTies_1v1() + 1);
        }
        if (newRating > 0.0) {
            user.setRating_1v1(newRating);
        }
        if(toAdd.has(StatEngine.StatEnum.GOALS.toString())){
            user.setGoals_1v1(user.getGoals_1v1() +  toAdd.get(StatEngine.StatEnum.GOALS.toString()).asInt());
            if (classStat != null) classStat.setGoals(classStat.getGoals() +  toAdd.get(StatEngine.StatEnum.GOALS.toString()).asInt());
            if (userClassStat != null) userClassStat.setGoals_1v1(userClassStat.getGoals_1v1() + toAdd.get(StatEngine.StatEnum.GOALS.toString()).asInt());
        }
        if(toAdd.has(StatEngine.StatEnum.SIDEGOALS.toString())){
            user.setSidegoals_1v1((user.getSidegoals_1v1() +  toAdd.get(StatEngine.StatEnum.SIDEGOALS.toString()).asInt()));
            if (classStat != null) classStat.setSidegoals((classStat.getSidegoals() +  toAdd.get(StatEngine.StatEnum.SIDEGOALS.toString()).asInt()));
            if (userClassStat != null) userClassStat.setSidegoals_1v1(userClassStat.getSidegoals_1v1() + toAdd.get(StatEngine.StatEnum.SIDEGOALS.toString()).asInt());
        }
        if(toAdd.has(StatEngine.StatEnum.POINTS.toString())){
            user.setPoints_1v1(user.getPoints_1v1() +  toAdd.get(StatEngine.StatEnum.POINTS.toString()).asDouble());
            if (classStat != null) classStat.setPoints(classStat.getPoints() +  toAdd.get(StatEngine.StatEnum.POINTS.toString()).asDouble());
            if (userClassStat != null) userClassStat.setPoints_1v1(userClassStat.getPoints_1v1() + toAdd.get(StatEngine.StatEnum.POINTS.toString()).asDouble());
        }
        if(toAdd.has(StatEngine.StatEnum.STEALS.toString())){
            user.setSteals_1v1((user.getSteals_1v1() +   toAdd.get(StatEngine.StatEnum.STEALS.toString()).asInt()));
            if (classStat != null) classStat.setSteals((classStat.getSteals() +   toAdd.get(StatEngine.StatEnum.STEALS.toString()).asInt()));
            if (userClassStat != null) userClassStat.setSteals_1v1(userClassStat.getSteals_1v1() + toAdd.get(StatEngine.StatEnum.STEALS.toString()).asInt());
        }
        if(toAdd.has(StatEngine.StatEnum.BLOCKS.toString())){
            user.setBlocks_1v1((user.getBlocks_1v1() +  toAdd.get(StatEngine.StatEnum.BLOCKS.toString()).asInt()));
            if (classStat != null) classStat.setBlocks((classStat.getBlocks() +  toAdd.get(StatEngine.StatEnum.BLOCKS.toString()).asInt()));
            if (userClassStat != null) userClassStat.setBlocks_1v1(userClassStat.getBlocks_1v1() + toAdd.get(StatEngine.StatEnum.BLOCKS.toString()).asInt());
        }
        if(toAdd.has(StatEngine.StatEnum.PASSES.toString())){
            user.setPasses_1v1((user.getPasses_1v1() +  toAdd.get(StatEngine.StatEnum.PASSES.toString()).asInt()));
            if (classStat != null) classStat.setPasses((classStat.getPasses() +  toAdd.get(StatEngine.StatEnum.PASSES.toString()).asInt()));
            if (userClassStat != null) userClassStat.setPasses_1v1(userClassStat.getPasses_1v1() + toAdd.get(StatEngine.StatEnum.PASSES.toString()).asInt());
        }
        if(toAdd.has(StatEngine.StatEnum.KILLS.toString())){
            user.setKills_1v1((user.getKills_1v1() +  toAdd.get(StatEngine.StatEnum.KILLS.toString()).asInt()));
            if (classStat != null) classStat.setKills((classStat.getKills() +  toAdd.get(StatEngine.StatEnum.KILLS.toString()).asInt()));
            if (userClassStat != null) userClassStat.setKills_1v1(userClassStat.getKills_1v1() + toAdd.get(StatEngine.StatEnum.KILLS.toString()).asInt());
        }
        if(toAdd.has(StatEngine.StatEnum.DEATHS.toString())){
            user.setDeaths_1v1((user.getDeaths_1v1() + toAdd.get(StatEngine.StatEnum.DEATHS.toString()).asInt()));
            if (classStat != null) classStat.setDeaths((classStat.getDeaths() + toAdd.get(StatEngine.StatEnum.DEATHS.toString()).asInt()));
            if (userClassStat != null) userClassStat.setDeaths_1v1(userClassStat.getDeaths_1v1() + toAdd.get(StatEngine.StatEnum.DEATHS.toString()).asInt());
        }
        if(toAdd.has(StatEngine.StatEnum.TURNOVERS.toString())){
            user.setTurnovers_1v1((user.getTurnovers_1v1() +  toAdd.get(StatEngine.StatEnum.TURNOVERS.toString()).asInt()));
            if (classStat != null) classStat.setTurnovers((classStat.getTurnovers() +  toAdd.get(StatEngine.StatEnum.TURNOVERS.toString()).asInt()));
            if (userClassStat != null) userClassStat.setTurnovers_1v1(userClassStat.getTurnovers_1v1() + toAdd.get(StatEngine.StatEnum.TURNOVERS.toString()).asInt());
        }
        if(toAdd.has(StatEngine.StatEnum.KILLASSISTS.toString())){
            user.setKillassists_1v1((user.getKillassists_1v1() +  toAdd.get(StatEngine.StatEnum.KILLASSISTS.toString()).asInt()));
            if (classStat != null) classStat.setKillassists((classStat.getKillassists() +  toAdd.get(StatEngine.StatEnum.KILLASSISTS.toString()).asInt()));
            if (userClassStat != null) userClassStat.setKillassists_1v1(userClassStat.getKillassists_1v1() + toAdd.get(StatEngine.StatEnum.KILLASSISTS.toString()).asInt());
        }
        if(toAdd.has(StatEngine.StatEnum.GOALASSISTS.toString())){
            user.setGoalassists_1v1((user.getGoalassists_1v1() +  toAdd.get(StatEngine.StatEnum.GOALASSISTS.toString()).asInt()));
            if (classStat != null) classStat.setGoalassists((classStat.getGoalassists() +  toAdd.get(StatEngine.StatEnum.GOALASSISTS.toString()).asInt()));
            if (userClassStat != null) userClassStat.setGoalassists_1v1(userClassStat.getGoalassists_1v1() + toAdd.get(StatEngine.StatEnum.GOALASSISTS.toString()).asInt());
        }
        if(toAdd.has(StatEngine.StatEnum.SIDEGOALASSISTS.toString())){
            user.setSidegoalassists_1v1((user.getSidegoalassists_1v1() +  toAdd.get(StatEngine.StatEnum.SIDEGOALASSISTS.toString()).asInt()));
            if (classStat != null) classStat.setSidegoalassists((classStat.getSidegoalassists() +  toAdd.get(StatEngine.StatEnum.SIDEGOALASSISTS.toString()).asInt()));
            if (userClassStat != null) userClassStat.setSidegoalassists_1v1(userClassStat.getSidegoalassists_1v1() + toAdd.get(StatEngine.StatEnum.SIDEGOALASSISTS.toString()).asInt());
        }
        if(toAdd.has(StatEngine.StatEnum.REBOUND.toString())){
            user.setRebounds_1v1((user.getRebounds_1v1() +  toAdd.get(StatEngine.StatEnum.REBOUND.toString()).asInt()));
            if (classStat != null) classStat.setRebounds((classStat.getRebounds() +  toAdd.get(StatEngine.StatEnum.REBOUND.toString()).asInt()));
            if (userClassStat != null) userClassStat.setRebounds_1v1(userClassStat.getRebounds_1v1() + toAdd.get(StatEngine.StatEnum.REBOUND.toString()).asInt());
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
        if (userClassStat != null && userClassStatService != null) userClassStatService.save(userClassStat);
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
        if (toAdd.has(StatEngine.StatEnum.SIDEGOALASSISTS.toString())) {
            classStat.setSidegoalassists(classStat.getSidegoalassists() + toAdd.get(StatEngine.StatEnum.SIDEGOALASSISTS.toString()).asInt());
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
        if (toAdd.has(StatEngine.StatEnum.SIDEGOALASSISTS.toString())) {
            masteriesStat.setSidegoalassists(masteriesStat.getSidegoalassists() + toAdd.get(StatEngine.StatEnum.SIDEGOALASSISTS.toString()).asInt());
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
        if (toAdd.has(StatEngine.StatEnum.SIDEGOALASSISTS.toString())) {
            upgradeStat.setSidegoalassists(upgradeStat.getSidegoalassists() + toAdd.get(StatEngine.StatEnum.SIDEGOALASSISTS.toString()).asInt());
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
        if (toAdd.has(StatEngine.StatEnum.SIDEGOALASSISTS.toString())) {
            stat.setSidegoalassists(stat.getSidegoalassists() + toAdd.get(StatEngine.StatEnum.SIDEGOALASSISTS.toString()).asInt());
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

    public void recordPlayerGameStats(GameEngine endedGame) {
        if (playerGameStatService == null || endedGame == null) return;
        try {
            double duration = (endedGame.GAMETICK_MS > 0) ? (endedGame.framesSinceStart / (1000.0 / endedGame.GAMETICK_MS)) : 0.0;
            double homeScore = (endedGame.home != null) ? endedGame.home.score : 0.0;
            double awayScore = (endedGame.away != null) ? endedGame.away.score : 0.0;
            String gameId = (endedGame.gameId != null && !endedGame.gameId.isEmpty()) ? endedGame.gameId : UUID.randomUUID().toString();

            // 1. Calculate team-level CG and SG for home and away
            int homeCg = 0, awayCg = 0, homeSg = 0, awaySg = 0;
            if (endedGame.clients != null && endedGame.stats != null) {
                for (PlayerDivider player : endedGame.clients) {
                    Titan t = endedGame.titanSelected(player);
                    if (t != null && t.team != null && player.email != null) {
                        JsonNode node = endedGame.stats.statsOf(player.email);
                        int g = node.has(StatEngine.StatEnum.GOALS.toString()) ? node.get(StatEngine.StatEnum.GOALS.toString()).asInt() : 0;
                        int sg = node.has(StatEngine.StatEnum.SIDEGOALS.toString()) ? node.get(StatEngine.StatEnum.SIDEGOALS.toString()).asInt() : 0;
                        if (t.team == TeamAffiliation.HOME) {
                            homeCg += g;
                            homeSg += sg;
                        } else if (t.team == TeamAffiliation.AWAY) {
                            awayCg += g;
                            awaySg += sg;
                        }
                    }
                }
            }

            // 2. Iterate outfielders only (ignore goalies)
            List<PlayerGameStat> toSave = new ArrayList<>();
            if (endedGame.clients != null && endedGame.stats != null) {
                for (PlayerDivider player : endedGame.clients) {
                    Titan t = endedGame.titanSelected(player);
                    if (t == null || t.getType() == null || t.getType() == TitanType.GOALIE) {
                        continue;
                    }
                    boolean isHome = (t.team == TeamAffiliation.HOME);
                    double pointsFor = isHome ? homeScore : awayScore;
                    double pointsAgainst = isHome ? awayScore : homeScore;
                    double pointDiff = pointsFor - pointsAgainst;
                    int cgFor = isHome ? homeCg : awayCg;
                    int cgAgainst = isHome ? awayCg : homeCg;
                    int sgFor = isHome ? homeSg : awaySg;
                    int sgAgainst = isHome ? awaySg : homeSg;
                    int won = (player.wasVictorious == 1) ? 1 : 0;

                    PlayerGameStat pgs = new PlayerGameStat();
                    pgs.setGameId(gameId);
                    pgs.setMatchDurationSeconds(duration);
                    pgs.setTeam(isHome ? "HOME" : "AWAY");
                    pgs.setWon(won);
                    pgs.setOutfieldclass(t.getType().toString());
                    pgs.setPreset(t.presetName);
                    pgs.setPointsFor(pointsFor);
                    pgs.setPointsAgainst(pointsAgainst);
                    pgs.setPointDiff(pointDiff);
                    pgs.setCgFor(cgFor);
                    pgs.setCgAgainst(cgAgainst);
                    pgs.setSidegoalsFor(sgFor);
                    pgs.setSidegoalsAgainst(sgAgainst);

                    if (player.email != null) {
                        JsonNode s = endedGame.stats.statsOf(player.email);
                        if (s.has(StatEngine.StatEnum.GOALS.toString())) pgs.setGoals(s.get(StatEngine.StatEnum.GOALS.toString()).asInt());
                        if (s.has(StatEngine.StatEnum.SIDEGOALS.toString())) pgs.setSidegoals(s.get(StatEngine.StatEnum.SIDEGOALS.toString()).asInt());
                        if (s.has(StatEngine.StatEnum.POINTS.toString())) pgs.setPoints(s.get(StatEngine.StatEnum.POINTS.toString()).asDouble());
                        if (s.has(StatEngine.StatEnum.GOALASSISTS.toString())) pgs.setCgAssists(s.get(StatEngine.StatEnum.GOALASSISTS.toString()).asInt());
                        if (s.has(StatEngine.StatEnum.SIDEGOALASSISTS.toString())) pgs.setSgAssists(s.get(StatEngine.StatEnum.SIDEGOALASSISTS.toString()).asInt());
                        if (s.has(StatEngine.StatEnum.PASSES.toString())) pgs.setPasses(s.get(StatEngine.StatEnum.PASSES.toString()).asInt());
                        if (s.has(StatEngine.StatEnum.REBOUND.toString())) pgs.setRebounds(s.get(StatEngine.StatEnum.REBOUND.toString()).asInt());
                        if (s.has(StatEngine.StatEnum.TURNOVERS.toString())) pgs.setTurnovers(s.get(StatEngine.StatEnum.TURNOVERS.toString()).asInt());
                        if (s.has(StatEngine.StatEnum.STEALS.toString())) pgs.setSteals(s.get(StatEngine.StatEnum.STEALS.toString()).asInt());
                        if (s.has(StatEngine.StatEnum.BLOCKS.toString())) pgs.setBlocks(s.get(StatEngine.StatEnum.BLOCKS.toString()).asInt());
                        if (s.has(StatEngine.StatEnum.KILLS.toString())) pgs.setKills(s.get(StatEngine.StatEnum.KILLS.toString()).asInt());
                        if (s.has(StatEngine.StatEnum.DEATHS.toString())) pgs.setDeaths(s.get(StatEngine.StatEnum.DEATHS.toString()).asInt());
                        if (s.has(StatEngine.StatEnum.KILLASSISTS.toString())) pgs.setKillassists(s.get(StatEngine.StatEnum.KILLASSISTS.toString()).asInt());
                    }

                    toSave.add(pgs);
                }
            }
            if (!toSave.isEmpty()) {
                playerGameStatService.saveAll(toSave);
            }
        } catch (Exception e) {
            System.err.println("[PersistenceManager] Error recording player game stats: " + e.getMessage());
        }
    }
}
