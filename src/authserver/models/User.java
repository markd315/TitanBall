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

    @Column(name = "rebounds")
    private int rebounds;

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
}
