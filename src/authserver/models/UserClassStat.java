package authserver.models;

import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(
    name = "userclassstat",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_class", columnNames = {"email", "role"})
    },
    indexes = {
        @Index(name = "idx_user_class", columnList = "email, role"),
        @Index(name = "idx_username_class", columnList = "username, role")
    }
)
public class UserClassStat implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Integer id;

    @Column(name = "email", nullable = false)
    protected String email;

    @Column(name = "username")
    protected String username;

    @Column(name = "role", nullable = false, length = 32)
    protected String role;

    // 4v4 team match stats
    @Column(name = "wins")
    protected Integer wins = 0;

    @Column(name = "losses")
    protected Integer losses = 0;

    @Column(name = "ties")
    protected Integer ties = 0;

    @Column(name = "goals")
    protected Integer goals = 0;

    @Column(name = "points")
    protected Double points = 0.0;

    @Column(name = "sidegoals")
    protected Integer sidegoals = 0;

    @Column(name = "blocks")
    protected Integer blocks = 0;

    @Column(name = "steals")
    protected Integer steals = 0;

    @Column(name = "passes")
    protected Integer passes = 0;

    @Column(name = "kills")
    protected Integer kills = 0;

    @Column(name = "deaths")
    protected Integer deaths = 0;

    @Column(name = "turnovers")
    protected Integer turnovers = 0;

    @Column(name = "killassists")
    protected Integer killassists = 0;

    @Column(name = "goalassists")
    protected Integer goalassists = 0;

    @Column(name = "sidegoalassists")
    protected Integer sidegoalassists = 0;

    @Column(name = "rebounds")
    protected Integer rebounds = 0;

    @Column(name = "saves")
    protected Integer saves = 0;

    @Column(name = "lasthits")
    protected Integer lasthits = 0;

    @Column(name = "miniondamage")
    protected Double miniondamage = 0.0;

    @Column(name = "upgradesgold")
    protected Integer upgradesgold = 0;

    @Column(name = "consumablesgold")
    protected Integer consumablesgold = 0;

    @Column(name = "sidegoalsaves")
    protected Integer sidegoalsaves = 0;

    @Column(name = "centergoalsaves")
    protected Integer centergoalsaves = 0;

    @Column(name = "sidegoalsconceded")
    protected Integer sidegoalsconceded = 0;

    @Column(name = "goalsconceded")
    protected Integer goalsconceded = 0;

    @Column(name = "manaspent")
    protected Integer manaspent = 0;

    // 1v1 scrimmage match stats
    @Column(name = "wins_1v1")
    protected Integer wins_1v1 = 0;

    @Column(name = "losses_1v1")
    protected Integer losses_1v1 = 0;

    @Column(name = "ties_1v1")
    protected Integer ties_1v1 = 0;

    @Column(name = "goals_1v1")
    protected Integer goals_1v1 = 0;

    @Column(name = "points_1v1")
    protected Double points_1v1 = 0.0;

    @Column(name = "sidegoals_1v1")
    protected Integer sidegoals_1v1 = 0;

    @Column(name = "blocks_1v1")
    protected Integer blocks_1v1 = 0;

    @Column(name = "steals_1v1")
    protected Integer steals_1v1 = 0;

    @Column(name = "passes_1v1")
    protected Integer passes_1v1 = 0;

    @Column(name = "kills_1v1")
    protected Integer kills_1v1 = 0;

    @Column(name = "deaths_1v1")
    protected Integer deaths_1v1 = 0;

    @Column(name = "turnovers_1v1")
    protected Integer turnovers_1v1 = 0;

    @Column(name = "killassists_1v1")
    protected Integer killassists_1v1 = 0;

    @Column(name = "goalassists_1v1")
    protected Integer goalassists_1v1 = 0;

    @Column(name = "sidegoalassists_1v1")
    protected Integer sidegoalassists_1v1 = 0;

    @Column(name = "rebounds_1v1")
    protected Integer rebounds_1v1 = 0;

    public UserClassStat() {
        super();
    }

    public UserClassStat(String email, String role) {
        this.email = email;
        this.role = role;
    }

    public UserClassStat(String email, String username, String role) {
        this.email = email;
        this.username = username;
        this.role = role;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Integer getWins() {
        return wins != null ? wins : 0;
    }

    public void setWins(Integer wins) {
        this.wins = wins;
    }

    public Integer getLosses() {
        return losses != null ? losses : 0;
    }

    public void setLosses(Integer losses) {
        this.losses = losses;
    }

    public Integer getTies() {
        return ties != null ? ties : 0;
    }

    public void setTies(Integer ties) {
        this.ties = ties;
    }

    public Integer getGoals() {
        return goals != null ? goals : 0;
    }

    public void setGoals(Integer goals) {
        this.goals = goals;
    }

    public Double getPoints() {
        return points != null ? points : 0.0;
    }

    public void setPoints(Double points) {
        this.points = points;
    }

    public Integer getSidegoals() {
        return sidegoals != null ? sidegoals : 0;
    }

    public void setSidegoals(Integer sidegoals) {
        this.sidegoals = sidegoals;
    }

    public Integer getBlocks() {
        return blocks != null ? blocks : 0;
    }

    public void setBlocks(Integer blocks) {
        this.blocks = blocks;
    }

    public Integer getSteals() {
        return steals != null ? steals : 0;
    }

    public void setSteals(Integer steals) {
        this.steals = steals;
    }

    public Integer getPasses() {
        return passes != null ? passes : 0;
    }

    public void setPasses(Integer passes) {
        this.passes = passes;
    }

    public Integer getKills() {
        return kills != null ? kills : 0;
    }

    public void setKills(Integer kills) {
        this.kills = kills;
    }

    public Integer getDeaths() {
        return deaths != null ? deaths : 0;
    }

    public void setDeaths(Integer deaths) {
        this.deaths = deaths;
    }

    public Integer getTurnovers() {
        return turnovers != null ? turnovers : 0;
    }

    public void setTurnovers(Integer turnovers) {
        this.turnovers = turnovers;
    }

    public Integer getKillassists() {
        return killassists != null ? killassists : 0;
    }

    public void setKillassists(Integer killassists) {
        this.killassists = killassists;
    }

    public Integer getGoalassists() {
        return goalassists != null ? goalassists : 0;
    }

    public void setGoalassists(Integer goalassists) {
        this.goalassists = goalassists;
    }

    public Integer getSidegoalassists() {
        return sidegoalassists != null ? sidegoalassists : 0;
    }

    public void setSidegoalassists(Integer sidegoalassists) {
        this.sidegoalassists = sidegoalassists;
    }

    public Integer getRebounds() {
        return rebounds != null ? rebounds : 0;
    }

    public void setRebounds(Integer rebounds) {
        this.rebounds = rebounds;
    }

    public Integer getSaves() {
        return saves != null ? saves : 0;
    }

    public void setSaves(Integer saves) {
        this.saves = saves;
    }

    public Integer getLasthits() {
        return lasthits != null ? lasthits : 0;
    }

    public void setLasthits(Integer lasthits) {
        this.lasthits = lasthits;
    }

    public Double getMiniondamage() {
        return miniondamage != null ? miniondamage : 0.0;
    }

    public void setMiniondamage(Double miniondamage) {
        this.miniondamage = miniondamage;
    }

    public Integer getUpgradesgold() {
        return upgradesgold != null ? upgradesgold : 0;
    }

    public void setUpgradesgold(Integer upgradesgold) {
        this.upgradesgold = upgradesgold;
    }

    public Integer getConsumablesgold() {
        return consumablesgold != null ? consumablesgold : 0;
    }

    public void setConsumablesgold(Integer consumablesgold) {
        this.consumablesgold = consumablesgold;
    }

    public Integer getSidegoalsaves() {
        return sidegoalsaves != null ? sidegoalsaves : 0;
    }

    public void setSidegoalsaves(Integer sidegoalsaves) {
        this.sidegoalsaves = sidegoalsaves;
    }

    public Integer getCentergoalsaves() {
        return centergoalsaves != null ? centergoalsaves : 0;
    }

    public void setCentergoalsaves(Integer centergoalsaves) {
        this.centergoalsaves = centergoalsaves;
    }

    public Integer getSidegoalsconceded() {
        return sidegoalsconceded != null ? sidegoalsconceded : 0;
    }

    public void setSidegoalsconceded(Integer sidegoalsconceded) {
        this.sidegoalsconceded = sidegoalsconceded;
    }

    public Integer getGoalsconceded() {
        return goalsconceded != null ? goalsconceded : 0;
    }

    public void setGoalsconceded(Integer goalsconceded) {
        this.goalsconceded = goalsconceded;
    }

    public Integer getManaspent() {
        return manaspent != null ? manaspent : 0;
    }

    public void setManaspent(Integer manaspent) {
        this.manaspent = manaspent;
    }

    public Integer getWins_1v1() {
        return wins_1v1 != null ? wins_1v1 : 0;
    }

    public void setWins_1v1(Integer wins_1v1) {
        this.wins_1v1 = wins_1v1;
    }

    public Integer getLosses_1v1() {
        return losses_1v1 != null ? losses_1v1 : 0;
    }

    public void setLosses_1v1(Integer losses_1v1) {
        this.losses_1v1 = losses_1v1;
    }

    public Integer getTies_1v1() {
        return ties_1v1 != null ? ties_1v1 : 0;
    }

    public void setTies_1v1(Integer ties_1v1) {
        this.ties_1v1 = ties_1v1;
    }

    public Integer getGoals_1v1() {
        return goals_1v1 != null ? goals_1v1 : 0;
    }

    public void setGoals_1v1(Integer goals_1v1) {
        this.goals_1v1 = goals_1v1;
    }

    public Double getPoints_1v1() {
        return points_1v1 != null ? points_1v1 : 0.0;
    }

    public void setPoints_1v1(Double points_1v1) {
        this.points_1v1 = points_1v1;
    }

    public Integer getSidegoals_1v1() {
        return sidegoals_1v1 != null ? sidegoals_1v1 : 0;
    }

    public void setSidegoals_1v1(Integer sidegoals_1v1) {
        this.sidegoals_1v1 = sidegoals_1v1;
    }

    public Integer getBlocks_1v1() {
        return blocks_1v1 != null ? blocks_1v1 : 0;
    }

    public void setBlocks_1v1(Integer blocks_1v1) {
        this.blocks_1v1 = blocks_1v1;
    }

    public Integer getSteals_1v1() {
        return steals_1v1 != null ? steals_1v1 : 0;
    }

    public void setSteals_1v1(Integer steals_1v1) {
        this.steals_1v1 = steals_1v1;
    }

    public Integer getPasses_1v1() {
        return passes_1v1 != null ? passes_1v1 : 0;
    }

    public void setPasses_1v1(Integer passes_1v1) {
        this.passes_1v1 = passes_1v1;
    }

    public Integer getKills_1v1() {
        return kills_1v1 != null ? kills_1v1 : 0;
    }

    public void setKills_1v1(Integer kills_1v1) {
        this.kills_1v1 = kills_1v1;
    }

    public Integer getDeaths_1v1() {
        return deaths_1v1 != null ? deaths_1v1 : 0;
    }

    public void setDeaths_1v1(Integer deaths_1v1) {
        this.deaths_1v1 = deaths_1v1;
    }

    public Integer getTurnovers_1v1() {
        return turnovers_1v1 != null ? turnovers_1v1 : 0;
    }

    public void setTurnovers_1v1(Integer turnovers_1v1) {
        this.turnovers_1v1 = turnovers_1v1;
    }

    public Integer getKillassists_1v1() {
        return killassists_1v1 != null ? killassists_1v1 : 0;
    }

    public void setKillassists_1v1(Integer killassists_1v1) {
        this.killassists_1v1 = killassists_1v1;
    }

    public Integer getGoalassists_1v1() {
        return goalassists_1v1 != null ? goalassists_1v1 : 0;
    }

    public void setGoalassists_1v1(Integer goalassists_1v1) {
        this.goalassists_1v1 = goalassists_1v1;
    }

    public Integer getSidegoalassists_1v1() {
        return sidegoalassists_1v1 != null ? sidegoalassists_1v1 : 0;
    }

    public void setSidegoalassists_1v1(Integer sidegoalassists_1v1) {
        this.sidegoalassists_1v1 = sidegoalassists_1v1;
    }

    public Integer getRebounds_1v1() {
        return rebounds_1v1 != null ? rebounds_1v1 : 0;
    }

    public void setRebounds_1v1(Integer rebounds_1v1) {
        this.rebounds_1v1 = rebounds_1v1;
    }
}
