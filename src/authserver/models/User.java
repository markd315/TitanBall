package authserver.models;

import gameserver.gamemanager.ServerApplication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import util.Util;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Collection;

@Entity
@Table(name = "users")
public class User implements Serializable, UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Integer id;

    @Column(name = "username")
    protected String username;

    @Column(name = "role")
    protected String role;

    @Column(name = "email")
    protected String email;

    @Column(name = "password")
    protected String password;

    @Column(name = "created")
    protected Timestamp createdAt;

    @Column(name = "activation")
    protected String activation;

    @Column(name = "subexpiration")
    protected Timestamp subExp;

    @Column(name = "enabled")
    private boolean enabled;

    public boolean activate(String trialCode){
        if(trialCode.equals(activation) && !enabled){
            renew(14);
            this.enabled = true;
            return true;
        }
        return false;
    }

    public void renew(int renewalDays){
        Calendar calendar = Calendar.getInstance();
        java.util.Date now = calendar.getTime();
        java.sql.Timestamp currentTimestamp = new java.sql.Timestamp(now.getTime());

        if(subExp.before(currentTimestamp)){ //bring up to current time before renew
            subExp = new java.sql.Timestamp(now.getTime());
        }

        long lTime = subExp.getTime();
        lTime += (long) renewalDays * 1000L * 60L * 60L * 24L;
        subExp = new java.sql.Timestamp(lTime);
    }

    public String getActivation() {
        return activation;
    }

    public Timestamp getSubExp() {
        return subExp;
    }

    public User(){
        this.role = "USER";
        this.rating = 1000.0;
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
        this.sidegoalsaves = 0;
        this.centergoalsaves = 0;
        this.sidegoalsconceded = 0;
        this.goalsconceded = 0;
        this.upgradesgold = 0;
        this.consumablesgold = 0;
        this.manaspent = 0;
        this.blocks_g = 0;
        this.passes_g = 0;
        this.turnovers_g = 0;
        this.rebounds_g = 0;
        this.steals_g = 0;
        this.kills_g = 0;
        this.deaths_g = 0;
        this.goalie_matches = 0;
        this.rating_1v1 = 1000.0;
        this.wins_1v1 = 0;
        this.losses_1v1 = 0;
        this.ties_1v1 = 0;
        this.goals_1v1 = 0;
        this.points_1v1 = 0.0;
        this.steals_1v1 = 0;
        this.blocks_1v1 = 0;
        this.sidegoals_1v1 = 0;
        this.kills_1v1 = 0;
        this.deaths_1v1 = 0;
        this.passes_1v1 = 0;
        this.turnovers_1v1 = 0;
        this.killassists_1v1 = 0;
        this.goalassists_1v1 = 0;
        this.rebounds_1v1 = 0;
        this.enabled = false;
        Calendar calendar = Calendar.getInstance();
        java.util.Date now = calendar.getTime();
        java.sql.Timestamp currentTimestamp = new java.sql.Timestamp(now.getTime());
        this.subExp = currentTimestamp;
        this.activation = Util.randomKey();
    }

    public User(String username, String pwEncoded){
        this();
        this.username = username;
        this.password = pwEncoded;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Double getRating() {
        return rating;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    @Override
    public boolean isAccountNonExpired() {
        return this.isEnabled();
    }

    @Override
    public boolean isAccountNonLocked() {
        return this.isEnabled();
    }

    @Override
    public boolean isCredentialsNonExpired(){
        if(ServerApplication.PAYWALL){
            Calendar calendar = Calendar.getInstance();
            java.util.Date now = calendar.getTime();
            java.sql.Timestamp currentTimestamp = new java.sql.Timestamp(now.getTime());

            return (subExp.after(currentTimestamp));
        }
        return true;
    }

    @Override
    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean s) {
        this.enabled = s;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return null;
    }

    @Column(name = "rating")
    protected Double rating;

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
    protected int saves = 0;

    @Column(name = "sidegoalsaves")
    protected int sidegoalsaves = 0;

    @Column(name = "centergoalsaves")
    protected int centergoalsaves = 0;

    @Column(name = "sidegoalsconceded")
    protected int sidegoalsconceded = 0;

    @Column(name = "goalsconceded")
    protected int goalsconceded = 0;

    @Column(name = "upgradesgold")
    protected int upgradesgold = 0;

    @Column(name = "consumablesgold")
    protected int consumablesgold = 0;

    @Column(name = "manaspent")
    protected int manaspent = 0;

    @Column(name = "blocks_g")
    protected int blocks_g = 0;

    @Column(name = "passes_g")
    protected int passes_g = 0;

    @Column(name = "turnovers_g")
    protected int turnovers_g = 0;

    @Column(name = "rebounds_g")
    protected int rebounds_g = 0;

    @Column(name = "steals_g")
    protected int steals_g = 0;

    @Column(name = "kills_g")
    protected int kills_g = 0;

    @Column(name = "deaths_g")
    protected int deaths_g = 0;

    @Column(name = "goalie_matches")
    protected int goalie_matches = 0;

    @Column(name = "rating_1v1")
    protected Double rating_1v1;

    @Column(name = "wins_1v1")
    protected Integer wins_1v1;

    @Column(name = "losses_1v1")
    protected Integer losses_1v1;

    @Column(name = "ties_1v1")
    protected Integer ties_1v1;

    @Column(name = "goals_1v1")
    protected int goals_1v1;

    @Column(name = "sidegoals_1v1")
    protected int sidegoals_1v1;

    @Column(name = "points_1v1")
    protected double points_1v1;

    @Column(name = "steals_1v1")
    protected int steals_1v1;

    @Column(name = "blocks_1v1")
    protected int blocks_1v1;

    @Column(name = "passes_1v1")
    protected int passes_1v1;

    @Column(name = "kills_1v1")
    protected int kills_1v1;

    @Column(name = "deaths_1v1")
    protected int deaths_1v1;

    @Column(name = "turnovers_1v1")
    protected int turnovers_1v1;

    @Column(name = "killassists_1v1")
    protected int killassists_1v1;

    @Column(name = "goalassists_1v1")
    protected int goalassists_1v1;

    @Column(name = "rebounds_1v1")
    protected int rebounds_1v1;

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

    public int getTies() {
        return this.ties;
    }

    public void setTies(int ties) {
        this.ties = ties;
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

    public Double getRating_1v1() {
        return rating_1v1;
    }

    public void setRating_1v1(Double rating_1v1) {
        this.rating_1v1 = rating_1v1;
    }

    public Integer getWins_1v1() {
        return wins_1v1;
    }

    public void setWins_1v1(Integer wins_1v1) {
        this.wins_1v1 = wins_1v1;
    }

    public Integer getLosses_1v1() {
        return losses_1v1;
    }

    public void setLosses_1v1(Integer losses_1v1) {
        this.losses_1v1 = losses_1v1;
    }

    public Integer getTies_1v1() {
        return ties_1v1;
    }

    public void setTies_1v1(Integer ties_1v1) {
        this.ties_1v1 = ties_1v1;
    }

    public int getGoals_1v1() {
        return goals_1v1;
    }

    public void setGoals_1v1(int goals_1v1) {
        this.goals_1v1 = goals_1v1;
    }

    public int getSidegoals_1v1() {
        return sidegoals_1v1;
    }

    public void setSidegoals_1v1(int sidegoals_1v1) {
        this.sidegoals_1v1 = sidegoals_1v1;
    }

    public double getPoints_1v1() {
        return points_1v1;
    }

    public void setPoints_1v1(double points_1v1) {
        this.points_1v1 = points_1v1;
    }

    public int getSteals_1v1() {
        return steals_1v1;
    }

    public void setSteals_1v1(int steals_1v1) {
        this.steals_1v1 = steals_1v1;
    }

    public int getBlocks_1v1() {
        return blocks_1v1;
    }

    public void setBlocks_1v1(int blocks_1v1) {
        this.blocks_1v1 = blocks_1v1;
    }

    public int getPasses_1v1() {
        return passes_1v1;
    }

    public void setPasses_1v1(int passes_1v1) {
        this.passes_1v1 = passes_1v1;
    }

    public int getKills_1v1() {
        return kills_1v1;
    }

    public void setKills_1v1(int kills_1v1) {
        this.kills_1v1 = kills_1v1;
    }

    public int getDeaths_1v1() {
        return deaths_1v1;
    }

    public void setDeaths_1v1(int deaths_1v1) {
        this.deaths_1v1 = deaths_1v1;
    }

    public int getTurnovers_1v1() {
        return turnovers_1v1;
    }

    public void setTurnovers_1v1(int turnovers_1v1) {
        this.turnovers_1v1 = turnovers_1v1;
    }

    public int getKillassists_1v1() {
        return killassists_1v1;
    }

    public void setKillassists_1v1(int killassists_1v1) {
        this.killassists_1v1 = killassists_1v1;
    }

    public int getGoalassists_1v1() {
        return goalassists_1v1;
    }

    public void setGoalassists_1v1(int goalassists_1v1) {
        this.goalassists_1v1 = goalassists_1v1;
    }

    public int getRebounds_1v1() {
        return rebounds_1v1;
    }

    public void setRebounds_1v1(int rebounds_1v1) {
        this.rebounds_1v1 = rebounds_1v1;
    }

    public int getSaves() {
        return saves;
    }

    public void setSaves(int saves) {
        this.saves = saves;
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

    public int getManaspent() {
        return manaspent;
    }

    public void setManaspent(int manaspent) {
        this.manaspent = manaspent;
    }

    public int getBlocks_g() {
        return blocks_g;
    }

    public void setBlocks_g(int blocks_g) {
        this.blocks_g = blocks_g;
    }

    public int getPasses_g() {
        return passes_g;
    }

    public void setPasses_g(int passes_g) {
        this.passes_g = passes_g;
    }

    public int getTurnovers_g() {
        return turnovers_g;
    }

    public void setTurnovers_g(int turnovers_g) {
        this.turnovers_g = turnovers_g;
    }

    public int getRebounds_g() {
        return rebounds_g;
    }

    public void setRebounds_g(int rebounds_g) {
        this.rebounds_g = rebounds_g;
    }

    public int getSteals_g() {
        return steals_g;
    }

    public void setSteals_g(int steals_g) {
        this.steals_g = steals_g;
    }

    public int getKills_g() {
        return kills_g;
    }

    public void setKills_g(int kills_g) {
        this.kills_g = kills_g;
    }

    public int getDeaths_g() {
        return deaths_g;
    }

    public void setDeaths_g(int deaths_g) {
        this.deaths_g = deaths_g;
    }

    public int getGoalie_matches() {
        return goalie_matches;
    }

    public void setGoalie_matches(int goalie_matches) {
        this.goalie_matches = goalie_matches;
    }
}
