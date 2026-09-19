package authserver.models;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "playergamestat")
public class PlayerGameStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false)
    private String gameId;

    @Column(name = "recorded_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date recordedAt = new Date();

    @Column(name = "match_duration_seconds", nullable = false)
    private double matchDurationSeconds;

    @Column(name = "team", nullable = false)
    private String team;

    @Column(name = "won", nullable = false)
    private int won;

    @Column(name = "outfieldclass", nullable = false)
    private String outfieldclass;

    @Column(name = "preset")
    private String preset;

    @Column(name = "points_for", nullable = false)
    private double pointsFor;

    @Column(name = "points_against", nullable = false)
    private double pointsAgainst;

    @Column(name = "point_diff", nullable = false)
    private double pointDiff;

    @Column(name = "cg_for", nullable = false)
    private int cgFor;

    @Column(name = "cg_against", nullable = false)
    private int cgAgainst;

    @Column(name = "sidegoals_for", nullable = false)
    private int sidegoalsFor;

    @Column(name = "sidegoals_against", nullable = false)
    private int sidegoalsAgainst;

    @Column(name = "goals", nullable = false)
    private int goals;

    @Column(name = "sidegoals", nullable = false)
    private int sidegoals;

    @Column(name = "points", nullable = false)
    private double points;

    @Column(name = "cg_assists", nullable = false)
    private int cgAssists;

    @Column(name = "sg_assists", nullable = false)
    private int sgAssists;

    @Column(name = "passes", nullable = false)
    private int passes;

    @Column(name = "rebounds", nullable = false)
    private int rebounds;

    @Column(name = "turnovers", nullable = false)
    private int turnovers;

    @Column(name = "steals", nullable = false)
    private int steals;

    @Column(name = "blocks", nullable = false)
    private int blocks;

    @Column(name = "kills", nullable = false)
    private int kills;

    @Column(name = "deaths", nullable = false)
    private int deaths;

    @Column(name = "killassists", nullable = false)
    private int killassists;

    public PlayerGameStat() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }

    public Date getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Date recordedAt) { this.recordedAt = recordedAt; }

    public double getMatchDurationSeconds() { return matchDurationSeconds; }
    public void setMatchDurationSeconds(double matchDurationSeconds) { this.matchDurationSeconds = matchDurationSeconds; }

    public String getTeam() { return team; }
    public void setTeam(String team) { this.team = team; }

    public int getWon() { return won; }
    public void setWon(int won) { this.won = won; }

    public String getOutfieldclass() { return outfieldclass; }
    public void setOutfieldclass(String outfieldclass) { this.outfieldclass = outfieldclass; }

    public String getPreset() { return preset; }
    public void setPreset(String preset) { this.preset = preset; }

    public double getPointsFor() { return pointsFor; }
    public void setPointsFor(double pointsFor) { this.pointsFor = pointsFor; }

    public double getPointsAgainst() { return pointsAgainst; }
    public void setPointsAgainst(double pointsAgainst) { this.pointsAgainst = pointsAgainst; }

    public double getPointDiff() { return pointDiff; }
    public void setPointDiff(double pointDiff) { this.pointDiff = pointDiff; }

    public int getCgFor() { return cgFor; }
    public void setCgFor(int cgFor) { this.cgFor = cgFor; }

    public int getCgAgainst() { return cgAgainst; }
    public void setCgAgainst(int cgAgainst) { this.cgAgainst = cgAgainst; }

    public int getSidegoalsFor() { return sidegoalsFor; }
    public void setSidegoalsFor(int sidegoalsFor) { this.sidegoalsFor = sidegoalsFor; }

    public int getSidegoalsAgainst() { return sidegoalsAgainst; }
    public void setSidegoalsAgainst(int sidegoalsAgainst) { this.sidegoalsAgainst = sidegoalsAgainst; }

    public int getGoals() { return goals; }
    public void setGoals(int goals) { this.goals = goals; }

    public int getSidegoals() { return sidegoals; }
    public void setSidegoals(int sidegoals) { this.sidegoals = sidegoals; }

    public double getPoints() { return points; }
    public void setPoints(double points) { this.points = points; }

    public int getCgAssists() { return cgAssists; }
    public void setCgAssists(int cgAssists) { this.cgAssists = cgAssists; }

    public int getSgAssists() { return sgAssists; }
    public void setSgAssists(int sgAssists) { this.sgAssists = sgAssists; }

    public int getPasses() { return passes; }
    public void setPasses(int passes) { this.passes = passes; }

    public int getRebounds() { return rebounds; }
    public void setRebounds(int rebounds) { this.rebounds = rebounds; }

    public int getTurnovers() { return turnovers; }
    public void setTurnovers(int turnovers) { this.turnovers = turnovers; }

    public int getSteals() { return steals; }
    public void setSteals(int steals) { this.steals = steals; }

    public int getBlocks() { return blocks; }
    public void setBlocks(int blocks) { this.blocks = blocks; }

    public int getKills() { return kills; }
    public void setKills(int kills) { this.kills = kills; }

    public int getDeaths() { return deaths; }
    public void setDeaths(int deaths) { this.deaths = deaths; }

    public int getKillassists() { return killassists; }
    public void setKillassists(int killassists) { this.killassists = killassists; }
}
