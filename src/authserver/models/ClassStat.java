package authserver.models;

import javax.persistence.*;

@Entity
@Table(name = "classes")
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

}
