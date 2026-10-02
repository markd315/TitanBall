package authserver.models;

/**
 * Common contract for aggregate statistical performance trackers
 * (ClassStat, MasteriesStat, UpgradeClassStat, BuildOrderStat).
 */
public interface AggregateStatTracker {
    Integer getWins();
    void setWins(Integer wins);
    Integer getLosses();
    void setLosses(Integer losses);
    Integer getTies();
    void setTies(Integer ties);
    Integer getGoals();
    void setGoals(Integer goals);
    Integer getSidegoals();
    void setSidegoals(Integer sidegoals);
    Double getPoints();
    void setPoints(Double points);
    Integer getSteals();
    void setSteals(Integer steals);
    Integer getBlocks();
    void setBlocks(Integer blocks);
    Integer getPasses();
    void setPasses(Integer passes);
    Integer getKills();
    void setKills(Integer kills);
    Integer getDeaths();
    void setDeaths(Integer deaths);
    Integer getTurnovers();
    void setTurnovers(Integer turnovers);
    Integer getKillassists();
    void setKillassists(Integer killassists);
    Integer getGoalassists();
    void setGoalassists(Integer goalassists);
    Integer getSidegoalassists();
    void setSidegoalassists(Integer sidegoalassists);
    Integer getRebounds();
    void setRebounds(Integer rebounds);
    int getSaves();
    void setSaves(int saves);
    int getLasthits();
    void setLasthits(int lasthits);
    double getMiniondamage();
    void setMiniondamage(double miniondamage);
    int getUpgradesgold();
    void setUpgradesgold(int upgradesgold);
    int getConsumablesgold();
    void setConsumablesgold(int consumablesgold);
    int getSidegoalsaves();
    void setSidegoalsaves(int sidegoalsaves);
    int getCentergoalsaves();
    void setCentergoalsaves(int centergoalsaves);
    int getSidegoalsconceded();
    void setSidegoalsconceded(int sidegoalsconceded);
    int getGoalsconceded();
    void setGoalsconceded(int goalsconceded);
    int getManaspent();
    void setManaspent(int manaspent);
}
