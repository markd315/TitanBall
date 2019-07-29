package authserver.models.responses;

import authserver.models.User;

public class UserResponse {
    protected Double points;
    protected Integer kills, blocks, steals, deaths, turnovers, passes, sidegoals, goals;
    protected Integer wins, losses;
    protected Double rating;
    protected String username;
    protected String email;
    protected String role;

    public UserResponse(User user){
        this.role = user.getRole();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.wins = user.getWins();
        this.losses = user.getLosses();
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
}

