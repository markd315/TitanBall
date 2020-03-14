package authserver.models.responses;

import authserver.models.User;
import com.fasterxml.jackson.annotation.JsonAutoDetect;

import java.io.Serializable;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class UserResponse implements Serializable {
    protected Double points, rating;
    protected Integer kills, blocks, steals, deaths, turnovers, passes, sidegoals, goals;
    protected Integer wins, losses;
    protected int ties, ties_1v1;
    protected Double points_1v1, rating_1v1;
    protected Integer kills_1v1, blocks_1v1, steals_1v1, deaths_1v1, turnovers_1v1, passes_1v1, sidegoals_1v1, goals_1v1;
    protected Integer wins_1v1, losses_1v1;
    protected String username;
    protected String email;
    protected String role;
    protected int rank, rank1v1;

    public UserResponse(User user, int rank, int rank1v1){
        this.rank = rank;
        this.rank1v1 = rank1v1;
        this.role = user.getRole();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.wins = user.getWins();
        this.losses = user.getLosses();
        this.ties = user.getTies();
        this.rating = user.getRating();
        this.goals = user.getSidegoals();
        this.points = user.getPoints();
        this.sidegoals = user.getSidegoals();
        this.blocks = user.getBlocks();
        this.steals = user.getSteals();
        this.passes = user.getPasses();
        this.turnovers = user.getTurnovers();
        this.kills = user.getKills();
        this.deaths = user.getDeaths();
        this.wins_1v1 = user.getWins_1v1();
        this.losses_1v1 = user.getLosses_1v1();
        this.rating_1v1 = user.getRating_1v1();
        this.goals_1v1 = user.getSidegoals_1v1();
        this.points_1v1 = user.getPoints_1v1();
        this.sidegoals_1v1 = user.getSidegoals_1v1();
        this.blocks_1v1 = user.getBlocks_1v1();
        this.steals_1v1 = user.getSteals_1v1();
        this.passes_1v1 = user.getPasses_1v1();
        this.turnovers_1v1 = user.getTurnovers_1v1();
        this.kills_1v1 = user.getKills_1v1();
        this.deaths_1v1 = user.getDeaths_1v1();
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

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

    public Double getRating() {
        return rating;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

    public String getRole() {
        return role;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Double getPoints() {
        return points;
    }

    public void setPoints(Double points) {
        this.points = points;
    }

    public Integer getKills() {
        return kills;
    }

    public void setKills(Integer kills) {
        this.kills = kills;
    }

    public Integer getBlocks() {
        return blocks;
    }

    public void setBlocks(Integer blocks) {
        this.blocks = blocks;
    }

    public Integer getSteals() {
        return steals;
    }

    public void setSteals(Integer steals) {
        this.steals = steals;
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

    public Integer getPasses() {
        return passes;
    }

    public void setPasses(Integer passes) {
        this.passes = passes;
    }

    public Integer getSidegoals() {
        return sidegoals;
    }

    public void setSidegoals(Integer sidegoals) {
        this.sidegoals = sidegoals;
    }

    public Integer getGoals() {
        return goals;
    }

    public void setGoals(Integer goals) {
        this.goals = goals;
    }

    public Double getPoints_1v1() {
        return points_1v1;
    }

    public void setPoints_1v1(Double points_1v1) {
        this.points_1v1 = points_1v1;
    }

    public Double getRating_1v1() {
        return rating_1v1;
    }

    public void setRating_1v1(Double rating_1v1) {
        this.rating_1v1 = rating_1v1;
    }

    public Integer getKills_1v1() {
        return kills_1v1;
    }

    public void setKills_1v1(Integer kills_1v1) {
        this.kills_1v1 = kills_1v1;
    }

    public Integer getBlocks_1v1() {
        return blocks_1v1;
    }

    public void setBlocks_1v1(Integer blocks_1v1) {
        this.blocks_1v1 = blocks_1v1;
    }

    public Integer getSteals_1v1() {
        return steals_1v1;
    }

    public void setSteals_1v1(Integer steals_1v1) {
        this.steals_1v1 = steals_1v1;
    }

    public Integer getDeaths_1v1() {
        return deaths_1v1;
    }

    public void setDeaths_1v1(Integer deaths_1v1) {
        this.deaths_1v1 = deaths_1v1;
    }

    public Integer getTurnovers_1v1() {
        return turnovers_1v1;
    }

    public void setTurnovers_1v1(Integer turnovers_1v1) {
        this.turnovers_1v1 = turnovers_1v1;
    }

    public Integer getPasses_1v1() {
        return passes_1v1;
    }

    public void setPasses_1v1(Integer passes_1v1) {
        this.passes_1v1 = passes_1v1;
    }

    public Integer getSidegoals_1v1() {
        return sidegoals_1v1;
    }

    public void setSidegoals_1v1(Integer sidegoals_1v1) {
        this.sidegoals_1v1 = sidegoals_1v1;
    }

    public Integer getGoals_1v1() {
        return goals_1v1;
    }

    public void setGoals_1v1(Integer goals_1v1) {
        this.goals_1v1 = goals_1v1;
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

    public int getTies() {
        return ties;
    }

    public void setTies(int ties) {
        this.ties = ties;
    }

    public int getTies_1v1() {
        return ties_1v1;
    }

    public void setTies_1v1(int ties_1v1) {
        this.ties_1v1 = ties_1v1;
    }

    public int getRank() {
        return rank;
    }

    public int getRank1v1() {
        return rank1v1;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public void setRank1v1(int rank1v1) {
        this.rank1v1 = rank1v1;
    }
}

