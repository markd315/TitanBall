package authserver.models;

import authserver.users.identities.UserRepository;

import javax.naming.InsufficientResourcesException;
import javax.persistence.*;
import java.io.Serializable;
import java.util.Calendar;
import java.util.Optional;

@Entity
@Table(name = "premadestats")
public class Premade implements Serializable{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Integer id;

    @Column(name = "teamname")
    protected String teamname;

    @Column(name = "topemail")
    protected String topuser;

    @Column(name = "midemail")
    protected String miduser;

    @Column(name = "botemail")
    protected String botuser;

    @Column(name = "botconfirmed")
    protected boolean botConfirmed;

    @Column(name = "midconfirmed")
    protected boolean midConfirmed;

    @Column(name = "topconfirmed")
    protected boolean topConfirmed;

    @Column(name = "botqueued")
    protected boolean botQueued;
    @Column(name = "midqueued")
    protected boolean midQueued;
    @Column(name = "topqueued")
    protected boolean topQueued;

    public Premade(PremadeDTO input, String callerQueued) {
        this();
        this.teamname = input.teamname;
        this.topuser = input.top;
        this.miduser = input.mid;
        this.botuser = input.bot;
        if(callerQueued.equals(input.top)){
            topQueued = !topQueued;
        }
        if(callerQueued.equals(input.mid)){
            midQueued = !midQueued;
        }
        if(callerQueued.equals(input.bot)){
            botQueued = !botQueued;
        }
    }

    public boolean confirmAs(String by) throws InsufficientResourcesException {
        System.out.println("confirming team as " + by);
        if(by.equals(topuser) || by.equals(miduser) || by.equals(botuser)){
            if(by.equals(topuser)){
                topConfirmed = true;
            }
            if(by.equals(miduser)){
                midConfirmed = true;
            }
            if(by.equals(botuser)){
                botConfirmed = true;
            }
            return topConfirmed && midConfirmed && botConfirmed;
        }
        throw new InsufficientResourcesException("This teamname is taken already!");
    }

    public void injectRatings(UserRepository userRepository) throws Exception {
        User tUser = userRepository.findByUsername(this.topuser);
        User mUser = userRepository.findByUsername(this.miduser);
        User bUser = userRepository.findByUsername(this.botuser);
        if(tUser == null || mUser == null || bUser == null){
            System.out.println("At least one user was null..");
            throw new Exception("At least one user was null..");
        }
        this.rating = (tUser.rating + mUser.rating + bUser.rating + 3000.0) / 6;
    }

    public Premade(){
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
        Calendar calendar = Calendar.getInstance();
        java.util.Date now = calendar.getTime();
    }

    public Premade(String username, String pwEncoded){
        this();
        this.topuser = username;
    }

    public String getTopuser() {
        return topuser;
    }

    public void setTopuser(String username) {
        this.topuser = username;
    }

    public String getMiduser() {
        return miduser;
    }

    public void setMiduser(String email) {
        this.miduser = email;
    }

    public String getBotuser() {
        return botuser;
    }

    public void setBotuser(String email) {
        this.botuser = email;
    }

    public Double getRating() {
        return rating;
    }

    public void setRating(Double rating) {
        this.rating = rating;
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

    public String getTeamname() {
        return teamname;
    }

    public boolean isBotConfirmed() {
        return botConfirmed;
    }

    public boolean isMidConfirmed() {
        return midConfirmed;
    }

    public boolean isTopConfirmed() {
        return topConfirmed;
    }

    public boolean copyConfirms(Optional<Premade> exists) throws Exception {
        if(exists.isPresent()){
            this.topConfirmed = exists.get().topConfirmed;
            this.botConfirmed = exists.get().botConfirmed;
            this.midConfirmed = exists.get().midConfirmed;
            if(this.miduser.equals(exists.get().miduser) &&
                    this.botuser.equals(exists.get().botuser) &&
                    this.topuser.equals(exists.get().topuser)){
                return true;
            }else{
                throw new Exception();
            }
        }
        else{
            return false;
        }
    }
    /*
    CREATE TABLE premadestats
(
  id integer(20) not null auto_increment,
  teamname VARCHAR(190) unique,
  topemail VARCHAR(255),
  midemail VARCHAR(255),
  botemail VARCHAR(255),
  topconfirmed tinyint(1),
  midconfirmed tinyint(1),
  botconfirmed tinyint(1),
  rating double,
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
     */
}
