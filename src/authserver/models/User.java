package authserver.models;

import gameserver.ServerApplication;
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

    @Column(name = "rating")
    protected Double rating;

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
        this.enabled = false;
        Calendar calendar = Calendar.getInstance();
        java.util.Date now = calendar.getTime();
        java.sql.Timestamp currentTimestamp = new java.sql.Timestamp(now.getTime());
        this.subExp = currentTimestamp;
        this.activation = Util.randomKey();
        //TODO send email activation
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
}
