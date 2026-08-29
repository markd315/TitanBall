package authserver.models;

import javax.persistence.*;

@Entity
@Table(name = "classstat")
public class ClassStat{

    public ClassStat(String className){
        this.role = className;
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

    public ClassStat(){
        super();
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Integer id;

    @Column(name = "role")
    protected String role;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    @Column(name = "wins")
    protected Integer wins;

    @Column(name = "losses")
    protected Integer losses;

    @Column(name = "ties")
    protected Integer ties;

    @Column(name = "goals")
    protected int goals;

    @Column(name = "sidegoals")
    protected int sidegoals;

    @Column(name = "points")
    protected double points;

    @Column(name = "steals")
    protected int steals;

    @Column(name = "blocks")
    protected int blocks;

    @Column(name = "passes")
    protected int passes;

    @Column(name = "kills")
    protected int kills;

    @Column(name = "deaths")
    protected int deaths;

    @Column(name = "turnovers")
    protected int turnovers;

    @Column(name = "killassists")
    protected int killassists;

    @Column(name = "goalassists")
    protected int goalassists;

    @Column(name = "rebounds")
    protected int rebounds;

    @Column(name = "saves")
    protected int saves;

    @Column(name = "lasthits")
    protected int lasthits;

    @Column(name = "miniondamage")
    protected double miniondamage;

    @Column(name = "upgradesgold")
    protected int upgradesgold;

    @Column(name = "consumablesgold")
    protected int consumablesgold;

    @Column(name = "sidegoalsaves")
    protected int sidegoalsaves;

    @Column(name = "centergoalsaves")
    protected int centergoalsaves;

    @Column(name = "sidegoalsconceded")
    protected int sidegoalsconceded;

    @Column(name = "goalsconceded")
    protected int goalsconceded;

    @Column(name = "manaspent")
    protected int manaspent;

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

    public Integer getTies() {
        return ties;
    }

    public void setTies(Integer ties) {
        this.ties = ties;
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

    public int getKillassists() {
        return killassists;
    }

    public void setKillassists(int killassists) {
        this.killassists = killassists;
    }

    public int getGoalassists() {
        return goalassists;
    }

    public void setGoalassists(int goalassists) {
        this.goalassists = goalassists;
    }

    public int getRebounds() {
        return rebounds;
    }

    public void setRebounds(int rebounds) {
        this.rebounds = rebounds;
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

    /*
    CREATE TABLE mysql.classstat
(
  id integer(20) not null auto_increment,
  role varchar(32),
  points double,
  wins integer,
  losses integer,
  ties integer,
  sidegoals integer,
  steals integer,
  blocks integer,
  passes integer,
  kills integer,
  deaths integer,
  turnovers integer,
  killassists integer,
  goalassists integer,
  rebounds integer,
  goals integer,
  CONSTRAINT id_pk PRIMARY KEY (id)
  );

  INSERT INTO mysql.classstat (role, wins, losses, ties, goals, points, sidegoals, blocks, steals, passes, kills, deaths, turnovers, killassists, goalassists, rebounds)
VALUES ('GOALIE', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

INSERT INTO mysql.classstat (role, wins, losses, ties, goals, points, sidegoals, blocks, steals, passes, kills, deaths, turnovers, killassists, goalassists, rebounds)
VALUES ('WARRIOR', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

INSERT INTO mysql.classstat (role, wins, losses, ties, goals, points, sidegoals, blocks, steals, passes, kills, deaths, turnovers, killassists, goalassists, rebounds)
VALUES ('RANGER', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

INSERT INTO mysql.classstat (role, wins, losses, ties, goals, points, sidegoals, blocks, steals, passes, kills, deaths, turnovers, killassists, goalassists, rebounds)
VALUES ('DASHER', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

INSERT INTO mysql.classstat (role, wins, losses, ties, goals, points, sidegoals, blocks, steals, passes, kills, deaths, turnovers, killassists, goalassists, rebounds)
VALUES ('MARKSMAN', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

INSERT INTO mysql.classstat (role, wins, losses, ties, goals, points, sidegoals, blocks, steals, passes, kills, deaths, turnovers, killassists, goalassists, rebounds)
VALUES ('STEALTH', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

INSERT INTO mysql.classstat (role, wins, losses, ties, goals, points, sidegoals, blocks, steals, passes, kills, deaths, turnovers, killassists, goalassists, rebounds)
VALUES ('SUPPORT', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

INSERT INTO mysql.classstat (role, wins, losses, ties, goals, points, sidegoals, blocks, steals, passes, kills, deaths, turnovers, killassists, goalassists, rebounds)
VALUES ('ARTISAN', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

INSERT INTO mysql.classstat (role, wins, losses, ties, goals, points, sidegoals, blocks, steals, passes, kills, deaths, turnovers, killassists, goalassists, rebounds)
VALUES ('GOLEM', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

INSERT INTO mysql.classstat (role, wins, losses, ties, goals, points, sidegoals, blocks, steals, passes, kills, deaths, turnovers, killassists, goalassists, rebounds)
VALUES ('MAGE', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

INSERT INTO mysql.classstat (role, wins, losses, ties, goals, points, sidegoals, blocks, steals, passes, kills, deaths, turnovers, killassists, goalassists, rebounds)
VALUES ('BUILDER', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

INSERT INTO mysql.classstat (role, wins, losses, ties, goals, points, sidegoals, blocks, steals, passes, kills, deaths, turnovers, killassists, goalassists, rebounds)
VALUES ('GRENADIER', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

INSERT INTO mysql.classstat (role, wins, losses, ties, goals, points, sidegoals, blocks, steals, passes, kills, deaths, turnovers, killassists, goalassists, rebounds)
VALUES ('HOUNDMASTER', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

INSERT INTO mysql.classstat (role, wins, losses, ties, goals, points, sidegoals, blocks, steals, passes, kills, deaths, turnovers, killassists, goalassists, rebounds)
VALUES ('CAPTAIN', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

INSERT INTO mysql.classstat (role, wins, losses, ties, goals, points, sidegoals, blocks, steals, passes, kills, deaths, turnovers, killassists, goalassists, rebounds)
VALUES ('SPIDER', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
     */
}
