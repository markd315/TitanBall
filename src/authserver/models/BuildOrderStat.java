package authserver.models;

import javax.persistence.*;

@Entity
@Table(name = "buildorderstat")
public class BuildOrderStat {

    public BuildOrderStat(String buildname) {
        this.buildname = buildname;
        this.wins = 0;
        this.losses = 0;
        this.ties = 0;
        this.goals = 0;
        this.points = 0.0;
        this.steals = 0;
        this.blocks = 0;
        this.sidegoals = 0;
        this.kills = 0;
        this.deaths = 0;
        this.passes = 0;
        this.turnovers = 0;
        this.killassists = 0;
        this.goalassists = 0;
        this.sidegoalassists = 0;
        this.rebounds = 0;
        this.saves = 0;
        this.lasthits = 0;
        this.miniondamage = 0.0;
        this.upgradesgold = 0;
        this.consumablesgold = 0;
        this.sidegoalsaves = 0;
        this.centergoalsaves = 0;
        this.sidegoalsconceded = 0;
        this.goalsconceded = 0;
        this.manaspent = 0;
    }

    public BuildOrderStat() {
        super();
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Integer id;

    @Column(name = "buildname")
    protected String buildname;

    public String getBuildname() {
        return buildname;
    }

    public void setBuildname(String buildname) {
        this.buildname = buildname;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    @Column(name = "wins")
    protected Integer wins = 0;

    @Column(name = "losses")
    protected Integer losses = 0;

    @Column(name = "ties")
    protected Integer ties = 0;

    @Column(name = "goals")
    protected int goals = 0;

    @Column(name = "sidegoals")
    protected int sidegoals = 0;

    @Column(name = "points")
    protected double points = 0.0;

    @Column(name = "steals")
    protected int steals = 0;

    @Column(name = "blocks")
    protected int blocks = 0;

    @Column(name = "passes")
    protected int passes = 0;

    @Column(name = "kills")
    protected int kills = 0;

    @Column(name = "deaths")
    protected int deaths = 0;

    @Column(name = "turnovers")
    protected int turnovers = 0;

    @Column(name = "killassists")
    protected int killassists = 0;

    @Column(name = "goalassists")
    protected int goalassists = 0;

    @Column(name = "sidegoalassists")
    protected int sidegoalassists = 0;

    @Column(name = "rebounds")
    protected int rebounds = 0;

    @Column(name = "saves")
    protected int saves = 0;

    @Column(name = "lasthits")
    protected int lasthits = 0;

    @Column(name = "miniondamage")
    protected double miniondamage = 0.0;

    @Column(name = "upgradesgold")
    protected int upgradesgold = 0;

    @Column(name = "consumablesgold")
    protected int consumablesgold = 0;

    @Column(name = "sidegoalsaves")
    protected int sidegoalsaves = 0;

    @Column(name = "centergoalsaves")
    protected int centergoalsaves = 0;

    @Column(name = "sidegoalsconceded")
    protected int sidegoalsconceded = 0;

    @Column(name = "goalsconceded")
    protected int goalsconceded = 0;

    @Column(name = "manaspent")
    protected int manaspent = 0;

    public Integer getWins() {
        return wins;
    }

    public void setWins(Integer wins) {
        this.wins = wins;
    }

    public Integer getLosses() {
        return losses;
    }

    public void setLosses(Integer losses) {
        this.losses = losses;
    }

    public Integer getGoals() {
        return goals;
    }

    public void setGoals(Integer goals) {
        this.goals = goals;
    }

    public Integer getSidegoals() {
        return sidegoals;
    }

    public void setSidegoals(Integer sidegoals) {
        this.sidegoals = sidegoals;
    }

    public Double getPoints() {
        return points;
    }

    public void setPoints(Double points) {
        this.points = points;
    }

    public Integer getSteals() {
        return steals;
    }

    public void setSteals(Integer steals) {
        this.steals = steals;
    }

    public Integer getBlocks() {
        return blocks;
    }

    public void setBlocks(Integer blocks) {
        this.blocks = blocks;
    }

    public Integer getPasses() {
        return passes;
    }

    public void setPasses(Integer passes) {
        this.passes = passes;
    }

    public Integer getKills() {
        return kills;
    }

    public void setKills(Integer kills) {
        this.kills = kills;
    }

    public Integer getDeaths() {
        return deaths;
    }

    public void setDeaths(Integer deaths) {
        this.deaths = deaths;
    }

    public Integer getTurnovers() {
        return turnovers;
    }

    public void setTurnovers(Integer turnovers) {
        this.turnovers = turnovers;
    }

    public Integer getKillassists() {
        return killassists;
    }

    public void setKillassists(Integer killassists) {
        this.killassists = killassists;
    }

    public Integer getGoalassists() {
        return goalassists;
    }

    public void setGoalassists(Integer goalassists) {
        this.goalassists = goalassists;
    }

    public Integer getSidegoalassists() {
        return sidegoalassists;
    }

    public void setSidegoalassists(Integer sidegoalassists) {
        this.sidegoalassists = sidegoalassists;
    }

    public Integer getRebounds() {
        return rebounds;
    }

    public void setRebounds(Integer rebounds) {
        this.rebounds = rebounds;
    }

    public Integer getTies() {
        return ties;
    }

    public void setTies(Integer ties) {
        this.ties = ties;
    }

    public int getSaves() {
        return saves;
    }

    public void setSaves(int saves) {
        this.saves = saves;
    }

    public int getLasthits() {
        return lasthits;
    }

    public void setLasthits(int lasthits) {
        this.lasthits = lasthits;
    }

    public double getMiniondamage() {
        return miniondamage;
    }

    public void setMiniondamage(double miniondamage) {
        this.miniondamage = miniondamage;
    }

    public int getUpgradesgold() {
        return upgradesgold;
    }

    public void setUpgradesgold(int upgradesgold) {
        this.upgradesgold = upgradesgold;
    }

    public int getConsumablesgold() {
        return consumablesgold;
    }

    public void setConsumablesgold(int consumablesgold) {
        this.consumablesgold = consumablesgold;
    }

    public int getSidegoalsaves() {
        return sidegoalsaves;
    }

    public void setSidegoalsaves(int sidegoalsaves) {
        this.sidegoalsaves = sidegoalsaves;
    }

    public int getCentergoalsaves() {
        return centergoalsaves;
    }

    public void setCentergoalsaves(int centergoalsaves) {
        this.centergoalsaves = centergoalsaves;
    }

    public int getSidegoalsconceded() {
        return sidegoalsconceded;
    }

    public void setSidegoalsconceded(int sidegoalsconceded) {
        this.sidegoalsconceded = sidegoalsconceded;
    }

    public int getGoalsconceded() {
        return goalsconceded;
    }

    public void setGoalsconceded(int goalsconceded) {
        this.goalsconceded = goalsconceded;
    }

    public int getManaspent() {
        return manaspent;
    }

    public void setManaspent(int manaspent) {
        this.manaspent = manaspent;
    }
}
