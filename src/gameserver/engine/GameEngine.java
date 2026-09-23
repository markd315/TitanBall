package gameserver.engine;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gameserver.TutorialOverrides;
import gameserver.effects.EffectId;
import gameserver.effects.cooldowns.CooldownCurve;
import gameserver.effects.effects.Effect;
import gameserver.effects.effects.RatioEffect;
import gameserver.entity.Box;
import gameserver.entity.Entity;
import gameserver.entity.Titan;
import gameserver.entity.TitanType;
import gameserver.entity.minions.Tickable;
import gameserver.entity.minions.LaneMinion;
import gameserver.entity.minions.Parapet;
import gameserver.effects.effects.EmptyEffect;
import gameserver.gamemanager.GamePhase;
import gameserver.gamemanager.ManagedGame;
import gameserver.models.Game;

import networking.ClientPacket;
import networking.KeyDifferences;
import networking.PlayerDivider;
import org.joda.time.Instant;
import util.Util;

import java.util.List;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
public class GameEngine extends Game {
    private static boolean logs = false;
    protected Ability ability = new Ability();
    public GuardianAbilities homeGoalieAbilities = new GuardianAbilities(TeamAffiliation.HOME);
    public GuardianAbilities awayGoalieAbilities = new GuardianAbilities(TeamAffiliation.AWAY);
    public TeamAffiliation lastPossessionTeam = TeamAffiliation.UNAFFILIATED;
    public TeamAffiliation lastScoredTeam = TeamAffiliation.UNAFFILIATED;
    // Tracks the titan currently executing a lob so that the uncatchable window
    // (frames 3–8) can be scoped to that thrower alone, not all players globally.
    @com.fasterxml.jackson.annotation.JsonIgnore
    public transient Titan activeLobThrower = null;

    // Tracks the active sidegoal scorers until a center goal is cashed in (combo goal)
    // or the ghost points are rounded away by the enemy team.
    @com.fasterxml.jackson.annotation.JsonIgnore
    public transient final List<PlayerDivider> activeHomeSidegoalScorers = new ArrayList<>();
    @com.fasterxml.jackson.annotation.JsonIgnore
    public transient final List<PlayerDivider> activeAwaySidegoalScorers = new ArrayList<>();
    @com.fasterxml.jackson.annotation.JsonIgnore
    public transient java.util.function.Consumer<GameEngine> onGameEnded = null;
    @com.fasterxml.jackson.annotation.JsonIgnore
    public transient int customTickIntervalMs = -1;
    @com.fasterxml.jackson.annotation.JsonIgnore
    public transient boolean[] staticObstacleGrid = null;
    @com.fasterxml.jackson.annotation.JsonIgnore
    public transient int cachedSolidEntityCount = -1;

    // ── Performance: ObjectMapper is heavyweight and thread-safe; share one instance
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ── Performance: per-lane minion bucket lists reused every tick to avoid per-tick allocation
    @com.fasterxml.jackson.annotation.JsonIgnore
    private transient final ArrayList<LaneMinion> hmL0 = new ArrayList<>(), hmL1 = new ArrayList<>(), hmL2 = new ArrayList<>();
    @com.fasterxml.jackson.annotation.JsonIgnore
    private transient final ArrayList<LaneMinion> amL0 = new ArrayList<>(), amL1 = new ArrayList<>(), amL2 = new ArrayList<>();
    @com.fasterxml.jackson.annotation.JsonIgnore
    private transient final List<List<LaneMinion>> homeMinionsReused = List.of(hmL0, hmL1, hmL2);
    @com.fasterxml.jackson.annotation.JsonIgnore
    private transient final List<List<LaneMinion>> awayMinionsReused = List.of(amL0, amL1, amL2);

    public static class LaneBonus implements java.io.Serializable {
        public long expiryMs;
        public int amount;
        public LaneBonus() {}
        public LaneBonus(long expiryMs, int amount) {
            this.expiryMs = expiryMs;
            this.amount = amount;
        }
    }
    
    @com.fasterxml.jackson.annotation.JsonIgnore
    public transient List<List<LaneBonus>> homeLaneBonusesList = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    @com.fasterxml.jackson.annotation.JsonIgnore
    public transient List<List<LaneBonus>> awayLaneBonusesList = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

    public List<List<LaneBonus>> getHomeLaneBonusesList() {
        if (homeLaneBonusesList == null) {
            homeLaneBonusesList = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }
        return homeLaneBonusesList;
    }

    public List<List<LaneBonus>> getAwayLaneBonusesList() {
        if (awayLaneBonusesList == null) {
            awayLaneBonusesList = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }
        return awayLaneBonusesList;
    }
    public GameEngine(String id, List<PlayerDivider> clients, GameOptions options) {
        this.clients = clients;
        this.options = options;
        authserver.matchmaking.Matchmaker mm = null;
        try {
            mm = authserver.SpringContextBridge.services().getMatchmaker();
        } catch (Exception e) {
            // Spring context might not be active, e.g., in unit tests
        }
        if (mm != null) {
            for (PlayerDivider p : clients) {
                if (p.possibleSelection != null && !p.possibleSelection.isEmpty()) {
                    int slotIndex = p.possibleSelection.get(0) - 1;
                    if (slotIndex >= 0 && slotIndex < players.length) {
                        String chosenStr = mm.playerClasses.get(p.email);
                        if (chosenStr != null) {
                            try {
                                TitanType chosenType = TitanType.valueOf(chosenStr.toUpperCase());
                                if (slotIndex == 0 || slotIndex == 1) {
                                    players[slotIndex].setType(TitanType.GOALIE);
                                } else {
                                    if (chosenType != TitanType.GOALIE) {
                                        players[slotIndex].setType(chosenType);
                                    } else {
                                        players[slotIndex].setType(TitanType.WARRIOR);
                                    }
                                }
                            } catch (Exception ex) {
                                // ignore invalid enum values
                            }
                        }
                    }
                }
            }
        }
        try {
            System.out.println(MAPPER.writeValueAsString(this.options));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        if (options.goaliesDisabled()) {
            cullGoalies();
        }
        cullUnmappedTitans();
        for (PlayerDivider p : clients) {
            // Remove any titan indices that no longer exist
            p.possibleSelection.removeIf(sel -> sel > players.length || sel < 1);

            // Ensure selection is valid
            if (p.possibleSelection.isEmpty()) {
                // fallback: always at least select titan #1
                p.possibleSelection.add(1);
            }

            // Reset selection to a valid value
            p.selection = p.possibleSelection.get(0);
        }
        System.out.println("goalieoptions " + options.goalieIndex);
        if (!options.goaliesDisabled()) {
            if (!(this instanceof TutorialOverrides)) {
                players[0].setVarsBasedOnType();
                players[1].setVarsBasedOnType();
            }
        }
        this.gameId = id;
        this.lastControlPacket = new ClientPacket[clients.size()];
    }
    public GameEngine(String gameId, List<PlayerDivider> players, GameOptions options, ManagedGame managedGame) {
        this(gameId, players, options);
    }

    protected void cullGoalies() {
        if (!c.GOALIE_DISABLED) {
            c.GOALIE_DISABLED = true;
            players[0].possession = 0;
            players[1].possession = 0;
            players[0].Y = 999000;
            players[0].X = 999000;
            players[1].X = 999000;
            players[1].Y = 999000;
            for (PlayerDivider p : clients) {
                p.possibleSelection.removeIf(poss -> poss == 1 || poss == 2);
                if (!p.possibleSelection.isEmpty()) {
                    p.selection = p.possibleSelection.get(0);
                }
            }
        }
    }

    protected void cullUnmappedTitans() {
        List<Titan> keepTitans = new ArrayList<>();
        Map<Integer, Integer> oldToNewIndex = new HashMap<>();
        int newIndex = 1;
        
        if (options != null && (options.isCoopVsAi() || options.isHybrid())) {
            int teamSize = 2;
            int[] vals = GameOptions.getPlayersVal();
            if (options.playerIndex >= 0 && options.playerIndex < vals.length) {
                teamSize = vals[options.playerIndex];
            }
            int numFieldNeeded = options.goaliesDisabled() ? teamSize : teamSize - 1;

            for (int i = 0; i < players.length; i++) {
                boolean found = false;
                if (!options.goaliesDisabled() && (i == 0 || i == 1)) { // Goalies
                    found = true;
                } else if (i >= 2 && i < 2 + numFieldNeeded) { // Home field
                    found = true;
                } else if (i >= 10 && i < 10 + numFieldNeeded) { // Away field
                    found = true;
                }
                if (found) {
                    keepTitans.add(players[i]);
                    oldToNewIndex.put(i + 1, newIndex);
                    newIndex++;
                }
            }
        } else {
            for (int i = 0; i < players.length; i++) {
                boolean found = false;
                // Always keep goalie slots 1 and 2 (indices 0 and 1) to preserve engine slot mapping
                if (i == 0 || i == 1) {
                    found = true;
                } else {
                    for (PlayerDivider p : clients) {
                        if (p.possibleSelection.contains(i + 1)) {
                            found = true;
                            break;
                        }
                    }
                }
                if (found) {
                    keepTitans.add(players[i]);
                    oldToNewIndex.put(i + 1, newIndex);
                    newIndex++;
                }
            }
        }
        
        players = keepTitans.toArray(new Titan[0]);
        
        // Remap the clients' possible selections to align with the new contiguous array indices
        for (PlayerDivider p : clients) {
            List<Integer> remapped = new ArrayList<>();
            for (Integer sel : p.possibleSelection) {
                if (oldToNewIndex.containsKey(sel)) {
                    remapped.add(oldToNewIndex.get(sel));
                }
            }
            p.possibleSelection = remapped;
            if (!p.possibleSelection.isEmpty()) {
                p.selection = p.possibleSelection.get(0);
            }
        }
    }

    public GameEngine() {
        super();
    }

    protected void doHealthModification() {
        for (Entity e : allSolids) {
            double factor = (1000.0 / GAMETICK_MS);
            if(!(e instanceof Titan)){
                factor*=c.PAIN_FACTOR; //so that buildings bonusdrain slower
                e.damage(this,c.FLAT_PAIN/41.0); //But will still always drain 41 ticks/s
                //drain .3/s or 18 hp/minute
            }
            GoalHoop[] pains = getPainHoopsFromTeam(e.team);
            for (GoalHoop pain : pains) {
                double delta = Util.calculatePain(e, pain);
                if (e.painReduction > 0) {
                    delta /= e.painReduction;
                }
                if (delta > 0) {
                    if (delta > c.MAX_PAIN) {
                        delta = c.MAX_PAIN;
                    }
                    e.damage(this, delta / factor);
                } else {
                    if (delta < -c.MAX_HEAL) {
                        delta = -c.MAX_HEAL;
                        if(!(e instanceof Titan)){
                            delta = 0; //buildings cannot heal
                        }
                    }
                    if (!e.teamPoss(this) && hoopDmg) {
                        Set<String> purchased = (e.team == TeamAffiliation.HOME) ? homeGoaliePurchasedUpgrades : awayGoaliePurchasedUpgrades;
                        double ampMult = (purchased != null && purchased.contains("fortress.t3.homehealamp")) ? c.getD("guardian.homehealamp.mult") : 1.0;
                        e.heal((-delta * ampMult) / factor);
                    }
                }
            }
        }
    }

    public GoalHoop[] getPainHoopsFromTeam(TeamAffiliation team) {
        GoalHoop[] ret = new GoalHoop[1];
        if (team == TeamAffiliation.UNAFFILIATED) {
            ret = new GoalHoop[2];
            ret[0] = homeHiGoal;
            ret[1] = awayHiGoal;
            return ret;
        }
        if (team == TeamAffiliation.AWAY) {
            ret[0] = homeHiGoal;
            return ret;
        }
        if (team == TeamAffiliation.HOME) {
            ret[0] = awayHiGoal;
            return ret;
        }
        return new GoalHoop[0];
    }

    public enum ShotType {
        NONE, CENTERGOAL, SIDEGOAL
    }
    public ShotType currentShotType = ShotType.NONE;
    public double[] currentStepVel = new double[]{0, 0};
    public UUID lastHomePossessor = null;
    public UUID lastAwayPossessor = null;
    public UUID lastHomePasser = null;
    public UUID lastAwayPasser = null;

    public UUID currentToucher = null;
    public UUID previousToucher = null;

    public void registerBallTouch(Titan t) {
        if (t == null) return;
        if (currentToucher == null) {
            currentToucher = t.id;
        } else if (!currentToucher.equals(t.id)) {
            previousToucher = currentToucher;
            currentToucher = t.id;
        }
    }

    public ShotType predictShotTypeFromRay(double bx, double by, double dx, double dy, TeamAffiliation throwerTeam) {
        double len = Math.hypot(dx, dy);
        if (len == 0) return ShotType.NONE;
        double uX = dx / len;
        double uY = dy / len;

        // 1. Center Goal (hiGoals) takes absolute priority
        if (hiGoals != null) {
            for (GoalHoop goal : hiGoals) {
                if (goal != null && goal.team != throwerTeam) {
                    if (rayIntersectsHoop(bx, by, uX, uY, goal)) {
                        return ShotType.CENTERGOAL;
                    }
                }
            }
        }

        // 2. Side Goals (lowGoals) checked only if no Center Goal is targeted
        if (lowGoals != null) {
            for (GoalHoop goal : lowGoals) {
                if (goal != null && goal.team != throwerTeam) {
                    if (rayIntersectsHoop(bx, by, uX, uY, goal)) {
                        return ShotType.SIDEGOAL;
                    }
                }
            }
        }

        return ShotType.NONE;
    }

    private boolean rayIntersectsHoop(double bx, double by, double uX, double uY, GoalHoop goal) {
        double gx = goal.x + goal.w / 2.0;
        double gy = goal.y + goal.h / 2.0;
        double rx = goal.w / 2.0 + 35.0; // hoop radius + tolerance
        double ry = goal.h / 2.0 + 35.0;

        double vX = gx - bx;
        double vY = gy - by;
        double t = vX * uX + vY * uY;
        if (t <= 0) return false;

        double px = bx + t * uX;
        double py = by + t * uY;

        double normX = (px - gx) / rx;
        double normY = (py - gy) / ry;

        return (normX * normX + normY * normY) <= 1.0;
    }

    public ShotType predictShotTrajectory(double startX, double startY, double[] vel, TeamAffiliation throwerTeam) {
        if (vel == null || (vel[0] == 0 && vel[1] == 0)) return ShotType.NONE;
        double bx = (ball != null) ? (ball.X + ball.width / 2.0) : startX;
        double by = (ball != null) ? (ball.Y + ball.height / 2.0) : startY;
        return predictShotTypeFromRay(bx, by, vel[0], vel[1], throwerTeam);
    }

    public boolean isGoalie(Titan t) {
        if (t == null) return false;
        return t.getType() == TitanType.GOALIE || t == players[0] || t == players[1];
    }

    public boolean ballIntersectsEllipse(GoalHoop goal) {
        gameserver.engine.CollisionMath.EllipseData g = goal.ellipseData();
        gameserver.engine.CollisionMath.EllipseData b = ball.ellipseData();
        return gameserver.engine.CollisionMath.ellipseBoundsIntersect(b, g);
    }


    public void detectGoals() {
        if (this.phase == GamePhase.SCORE_FREEZE || !ballVisible) {
            return;
        }
        if (contactExemptBall()) {
            return;
        }
        for (GoalHoop goal : this.lowGoals) {
            if (ballIntersectsEllipse(goal) && goal.checkReady()) {
                Optional<Titan> possessor = titanInPossession();
                if (possessor.isPresent() && isGoalie(possessor.get()) && possessor.get().team == goal.team) {
                    continue;
                }
                Titan mover = getAnyBallMover();
                if (mover != null && isGoalie(mover) && mover.team == goal.team) {
                    continue;
                }
                if (lastPossessed != null) {
                    Titan lp = titanByID(lastPossessed.toString()).orElse(null);
                    if (lp != null && isGoalie(lp) && lp.team == goal.team) {
                        continue;
                    }
                }
                if (activeLobThrower != null && isGoalie(activeLobThrower) && activeLobThrower.team == goal.team) {
                    continue;
                }
                Team enemy, us;
                List<PlayerDivider> ourSideScorers;
                List<PlayerDivider> enemySideScorers;
                if (goal.team == TeamAffiliation.HOME) {
                    us = this.away;
                    enemy = this.home;
                    ourSideScorers = this.activeAwaySidegoalScorers;
                    enemySideScorers = this.activeHomeSidegoalScorers;
                } else { //(goal.team == TeamAffiliation.AWAY)
                    us = this.home;
                    enemy = this.away;
                    ourSideScorers = this.activeHomeSidegoalScorers;
                    enemySideScorers = this.activeAwaySidegoalScorers;
                }
                goal.trigger(c != null ? c.HOOP_SIDEGOAL_CD_MS : 1800);
                Titan defGoalie = (goal.team == TeamAffiliation.HOME) ? players[0] : players[1];
                stats.grant(this, defGoalie, StatEngine.StatEnum.SIDEGOALS_CONCEDED);
                UUID attackerId = (us.which == TeamAffiliation.HOME) ? lastHomePossessor : lastAwayPossessor;
                UUID scorerId = (currentToucher != null) ? currentToucher : attackerId;
                Titan scorerTitan = (scorerId != null) ? titanByID(scorerId.toString()).orElse(null) : null;
                PlayerDivider scorer = (scorerTitan != null) ? clientFromTitan(scorerTitan) : getPossessorOrThrower();
                if (scorer != null) {
                    stats.grant(scorer, StatEngine.StatEnum.SIDEGOALS);
                    ourSideScorers.add(scorer);
                }

                // If the last person to touch the ball prior to the scorer was friendly, credit assist
                if (scorerId != null && previousToucher != null && !previousToucher.equals(scorerId)) {
                    Titan assister = titanByID(previousToucher.toString()).orElse(null);
                    if (assister != null && assister.team == us.which) {
                        stats.grant(this, assister, StatEngine.StatEnum.SIDEGOALASSISTS);
                    }
                }
                previousToucher = null;
                currentToucher = null;
                lastHomePasser = null;
                lastAwayPasser = null;
                if (us.score % 1.0 == .75) {
                    goal.freeze();
                }
                us.score += .25;
                // If 4 sidegoals have accumulated without a center goal, each sidegoal scorer gets 0.25 pts (1 full team point)
                if (ourSideScorers.size() >= 4) {
                    for (int sIdx = 0; sIdx < 4 && !ourSideScorers.isEmpty(); sIdx++) {
                        PlayerDivider s = ourSideScorers.remove(0);
                        if (s != null) {
                            stats.grant(s, StatEngine.StatEnum.POINTS, 0.25);
                        }
                    }
                }
                checkWinCondition(false);//somewhat intentional to check condition before ghost removal
                boolean saveProgress = (enemy == this.home)
                    ? homeGoaliePurchasedUpgrades.contains("siege.t5.saveprogress")
                    : awayGoaliePurchasedUpgrades.contains("siege.t5.saveprogress");
                if (!saveProgress) {
                    enemy.score = Math.floor(enemy.score); //Reset any of the other teams ghostpoints.
                    enemySideScorers.clear(); // Sidegoals rounded away: 0 points credit
                }
            }
        }

        for (GoalHoop goal : this.hiGoals) {
            if (ballIntersectsEllipse(goal) && goal.checkReady()) {
                Optional<Titan> possessor = titanInPossession();
                if (possessor.isPresent() && isGoalie(possessor.get()) && possessor.get().team == goal.team) {
                    continue;
                }
                Titan mover = getAnyBallMover();
                if (mover != null && isGoalie(mover) && mover.team == goal.team) {
                    continue;
                }
                if (lastPossessed != null) {
                    Titan lp = titanByID(lastPossessed.toString()).orElse(null);
                    if (lp != null && isGoalie(lp) && lp.team == goal.team) {
                        continue;
                    }
                }
                if (activeLobThrower != null && isGoalie(activeLobThrower) && activeLobThrower.team == goal.team) {
                    continue;
                }
                Team us, enemy;
                List<PlayerDivider> ourSideScorers;
                List<PlayerDivider> enemySideScorers;
                if (goal.team == TeamAffiliation.HOME) {
                    us = this.away;
                    enemy = this.home;
                    ourSideScorers = this.activeAwaySidegoalScorers;
                    enemySideScorers = this.activeHomeSidegoalScorers;
                } else { //(goal.team == TeamAffiliation.AWAY)
                    us = this.home;
                    enemy = this.away;
                    ourSideScorers = this.activeHomeSidegoalScorers;
                    enemySideScorers = this.activeAwaySidegoalScorers;
                }
                goal.trigger();
                Titan defGoalieHi = (goal.team == TeamAffiliation.HOME) ? players[0] : players[1];
                stats.grant(this, defGoalieHi, StatEngine.StatEnum.GOALS_CONCEDED);
                //Cash in all ghost/combo points for a full point
                long iPart = (long) us.score;
                double fPart = us.score - iPart;
                int nSidegoals = (int) Math.round(fPart * 4.0);
                us.score = Math.floor(us.score);
                us.score += fPart * 4 + 1;
                UUID attackerIdHi = (us.which == TeamAffiliation.HOME) ? lastHomePossessor : lastAwayPossessor;
                UUID scorerIdHi = (currentToucher != null) ? currentToucher : attackerIdHi;
                Titan scorerTitanHi = (scorerIdHi != null) ? titanByID(scorerIdHi.toString()).orElse(null) : null;
                PlayerDivider scorerHi = (scorerTitanHi != null) ? clientFromTitan(scorerTitanHi) : getPossessorOrThrower();
                if (scorerHi != null) {
                    stats.grant(scorerHi, StatEngine.StatEnum.GOALS);
                    // Combo goal credit: 1 + (0.5 * n) points credit goes to center goal scorer
                    double centerPoints = 1.0 + (0.5 * nSidegoals);
                    stats.grant(scorerHi, StatEngine.StatEnum.POINTS, centerPoints);
                }

                // If the last person to touch the ball prior to the scorer was friendly, credit assist
                if (scorerIdHi != null && previousToucher != null && !previousToucher.equals(scorerIdHi)) {
                    Titan assisterHi = titanByID(previousToucher.toString()).orElse(null);
                    if (assisterHi != null && assisterHi.team == us.which) {
                        stats.grant(this, assisterHi, StatEngine.StatEnum.GOALASSISTS);
                    }
                }
                previousToucher = null;
                currentToucher = null;
                lastHomePasser = null;
                lastAwayPasser = null;
                // 0.5 points credit goes to each active sidegoal scorer as an assist in the combo goal
                for (int sIdx = 0; sIdx < nSidegoals && !ourSideScorers.isEmpty(); sIdx++) {
                    PlayerDivider sideScorer = ourSideScorers.remove(0);
                    if (sideScorer != null) {
                        stats.grant(sideScorer, StatEngine.StatEnum.POINTS, 0.5);
                    }
                }
                ourSideScorers.clear();
                checkWinCondition(false);
                //reset enemy team ghost points
                boolean saveProgressHi = (enemy == this.home)
                    ? homeGoaliePurchasedUpgrades.contains("siege.t5.saveprogress")
                    : awayGoaliePurchasedUpgrades.contains("siege.t5.saveprogress");
                if (!saveProgressHi) {
                    enemy.score = Math.floor(enemy.score);
                    enemySideScorers.clear();
                }
                us.hasBall = true;
                enemy.hasBall = false;
                lastScoredTeam = us.which;
                ballVisible = false;
                inGame = false;
                goalVisible = true;
                System.out.println(us.score + " " + enemy.score);
                serverDelayReset();
            }
        }
    }

    public void minorHoopBounce() {
        if (contactExemptBall()) {
            return;
        }
        gameserver.engine.CollisionMath.Bounds ballBounds = ball.asBounds();
        for (GoalHoop goal : this.lowGoals) {
            gameserver.engine.CollisionMath.EllipseData ell = goal.ellipseData();
            gameserver.engine.CollisionMath.Bounds ellBounds = new gameserver.engine.CollisionMath.Bounds(ell.centerX() - ell.radiusX(), ell.centerY() - ell.radiusY(), ell.radiusX() * 2, ell.radiusY() * 2);
            boolean bounced = false;
            double dx = 0;
            double dy = 0;
            while (ellBounds.intersects(ballBounds)) {
                bounced = true;
                ballBounds = ball.asBounds();
                double ang = Util.degreesFromCoords(
                        ell.centerX() - ball.X - ball.centerDist,
                        ell.centerY() - ball.Y - ball.centerDist
                );
                ang += 180; //Kick it away, not towards
                dx = Math.cos(Math.toRadians((ang)));
                dy = Math.sin(Math.toRadians((ang)));
                ball.X += dx;
                ball.Y += dy;
            }
            if (bounced) {
                int extraKick = (c != null) ? c.HOOP_BOUNCE_EXTRA_KICK : 30;
                ball.X += dx * extraKick;
                ball.Y += dy * extraKick;
                if (c != null) {
                    ball.X = Math.max(c.MIN_X, Math.min(c.MAX_X, ball.X));
                    ball.Y = Math.max(c.MIN_Y, Math.min(c.MAX_Y, ball.Y));
                }
            }
        }
    }

    protected PlayerDivider getPossessorOrThrower() {
        if (this.titanInPossession().isPresent()) {
            return clientFromTitan(this.titanInPossession().get());
        }
        if (lastPossessed != null) {
            return clientFromTitan(this.titanByID(lastPossessed.toString()).get());
        }
        return null;
    }

    public void initializeServer() {
        System.out.println("initializing");
        home.hasBall = false;
        away.hasBall = false;
        goalVisible = false;
        ballVisible = true;
        this.currentToucher = null;
        this.previousToucher = null;
        List<TitanType> playableTypes;
        if (c != null && c.AI_INCLUDED_TITANS != null && c.AI_INCLUDED_TITANS.length > 0) {
            playableTypes = Arrays.asList(c.AI_INCLUDED_TITANS);
        } else {
            playableTypes = Arrays.stream(TitanType.values())
                .filter(t -> t != TitanType.GOALIE && t != TitanType.ANY && t != TitanType.NOT_GUARDIAN && t != TitanType.ANY_ENTITY)
                .collect(Collectors.toList());
        }
        java.util.Random rng = new java.util.Random();
        for (int i = 0; i < players.length; i++) {
            Titan t = players[i];
            if (t.getType() != TitanType.GOALIE && !anyClientSelected(i + 1)) {
                TitanType randomType = playableTypes.get(rng.nextInt(playableTypes.size()));
                t.setType(randomType);
            }
            t.setVarsBasedOnType();
            t.actionState = Titan.TitanState.IDLE;
            t.actionFrame = 0;
        }
        for (int i = 0; i < 2; i++) {
            Titan goalie = players[i];
            if (!anyClientSelected(i + 1)) {
                if (c != null && !c.HEADLESS_BUILDORDERS_ENABLED) {
                    goalie.aiGoalieBuildName = "randomizer";
                } else {
                    GoalieBuildOrderManager.BuildPreset preset = GOALIE_NAMED_BUILD_PRESETS.get(rng.nextInt(GOALIE_NAMED_BUILD_PRESETS.size()));
                    setAiGoalieBuildOrder(goalie, preset.name, preset.order);
                }
            }
        }
        home.score = 0;
        away.score = 0;
        lastScoredTeam = TeamAffiliation.UNAFFILIATED;
        setRandomBallSpawnPosition();
        for (Titan p : players) {
            p.runningFrame = 0;
            p.dirToBall = 0;
            p.diagonalRunDir = 0;
        }
        resetPosSel();
        if (gameId != null && gameId.startsWith("tutorial-")) {
            phase = GamePhase.TUTORIAL;
        } else {
            phase = GamePhase.COUNTDOWN;
        }
    }

    public void resetPosSel() {
        for (PlayerDivider client : clients) {
            if (client != null) {
                client.setSelection(client.getPossibleSelection().get(0));
            }
        }
        lastPossessed = null;
        if (c.GOALIE_DISABLED) {
            players[0].setX(999000);
            players[1].setX(999000);
            players[0].setY(999000);
            players[1].setY(999000);
        }else{
            players[0].setX(HOME_HI_X);
            players[1].setX(AWAY_HI_X);
            players[0].setY(HOME_HI_Y);
            players[1].setY(AWAY_HI_Y);
        }
        int nonGoaliePerTeam = (players.length - 2) / 2;
        int i = 2;
        int teamIndex = 0;
        for (; i < 2 + nonGoaliePerTeam; i++) {
            players[i].isBoosting = false;
            players[i].runLeft = 0; players[i].runRight = 0; players[i].runDown = 0; players[i].runUp = 0;
            players[i].programmed = false;
            players[i].team = TeamAffiliation.HOME; //unmap sometimes breaks this
            //System.out.println("setting" + players[i].team + players[i].getType() + teamIndex);
            setPosition(players[i], teamIndex, nonGoaliePerTeam);
            if (lastScoredTeam == TeamAffiliation.AWAY) {
                players[i].X += 35;//possession bonus for conceding (closer to ball)
            } else if (lastScoredTeam == TeamAffiliation.HOME) {
                players[i].X -= 35;//possession penalty for scoring (further from ball)
            }
            teamIndex++;
        }
        teamIndex = 0;
        for (; i < players.length; i++) {
            players[i].team = TeamAffiliation.AWAY; //unmap sometimes breaks this
            setPosition(players[i], teamIndex, nonGoaliePerTeam);
            //System.out.println("setting" + players[i].team + players[i].getType() + teamIndex);
            players[i].X = 2040 - players[i].X; //reflect across center to match ball center exactly
            if (lastScoredTeam == TeamAffiliation.HOME) {
                players[i].X -= 35;//possession bonus for conceding (closer to ball)
            } else if (lastScoredTeam == TeamAffiliation.AWAY) {
                players[i].X += 35;//possession penalty for scoring (further from ball)
            }
            teamIndex++;
        }
    }

    protected void setPosition(Titan t, int slotIndex, int slots) {
        if (slots <= 1) {
            if (slotIndex == 0) {
                t.X = MID_HOME;
                t.Y = MID_WING_HOME;
            }
        }
        if (slots == 2) {
            if (slotIndex == 0) {
                t.X = MID_HOME;
                t.Y = TOP_WING_HOME;
            }
            if (slotIndex == 1) {
                t.X = MID_HOME;
                t.Y = BOT_WING_HOME;
            }
        }
        if (slots == 3) {
            if (slotIndex == 0) {
                t.X = MID_HOME;
                t.Y = TOP_WING_HOME;
            }
            if (slotIndex == 1) {
                t.X = MID_HOME;
                t.Y = MID_WING_HOME;
            }
            if (slotIndex == 2) {
                t.X = MID_HOME;
                t.Y = BOT_WING_HOME;
            }
        }
        if (slots == 4) {
            if (slotIndex == 0) {
                t.X = MID_HOME;
                t.Y = TOP_WING_HOME;
            }
            if (slotIndex == 1) {
                t.X = MID_HOME;
                t.Y = MID_WING_HOME;
            }
            if (slotIndex == 2) {
                t.X = MID_HOME;
                t.Y = BOT_WING_HOME;
            }
            if (slotIndex >= 3) {
                t.X = DEFENDER_HOME;
                t.Y = MID_WING_HOME;
            }
        }
        if (slots >= 5) {
            if (slotIndex == 0) {
                t.X = MID_HOME;
                t.Y = TOP_WING_HOME;
            }
            if (slotIndex == 1) {
                t.X = MID_HOME;
                t.Y = MID_WING_HOME;
            }
            if (slotIndex == 2) {
                t.X = MID_HOME;
                t.Y = BOT_WING_HOME;
            }
            if (slotIndex == 3) {
                t.X = DEFENDER_HOME;
                t.Y = TOP_WING_HOME;
            }
            if (slotIndex >= 4) {
                t.X = DEFENDER_HOME;
                t.Y = BOT_WING_HOME;
            }
        }
    }

    public void serverDelayReset() {
        if (this.ended) {
            return;
        }
        this.phase = GamePhase.SCORE_FREEZE;
        this.lastPossessed = null;
        this.lastHomePasser = null;
        this.lastAwayPasser = null;
        this.currentToucher = null;
        this.previousToucher = null;
        for (GoalHoop goal : lowGoals) {
            goal.onCooldown = false;
            goal.frozen = false;
        }
        for (GoalHoop goal : hiGoals) {
            goal.onCooldown = false;
            goal.frozen = false;
        }
        for (Titan p : players) {
            p.actionState = Titan.TitanState.IDLE;
            p.actionFrame = 0;
            p.possession = 0;
            p.runLeft = 0; p.runRight = 0; p.runDown = 0; p.runUp = 0;
            p.moveMemD = false;
            p.moveMemU = false;
            p.moveMemL = false;
            p.moveMemR = false;
            p.marchingOrderX = (int) p.X;
            p.marchingOrderY = (int) p.Y;
            p.programmed = false;
        }
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(c.getI("server.goalDelay"));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            lock();
            try {
                if (!this.ended) {
                    finishGoalReset();
                }
            } finally {
                unlock();
            }
        });
    }

    private void finishGoalReset() {
        if (this.ended) {
            return;
        }
        this.phase = GamePhase.INGAME;
        goalVisible = false;
        ballVisible = true;
        this.lastHomePasser = null;
        this.lastAwayPasser = null;
        this.currentToucher = null;
        this.previousToucher = null;
        // Remove transient entities (non-Titans, non-LaneMinions, non-permanent structures) once,
        // not once-per-player as before — the predicate never depended on `p`.
        entityPool.removeIf(e -> {
            if (e instanceof Titan || e instanceof LaneMinion) {
                return false;
            }
            if (e.maxHealth >= 99999.0) {
                return false;
            }
            return true;
        });
        for (Titan p : players) {
            p.actionState = Titan.TitanState.IDLE;
            p.health = 3.0 * p.maxHealth / 4;
            p.programmed = false;
            p.marchingOrderX = (int) p.X;
            p.marchingOrderY = (int) p.Y;
            effectPool.cullAllOn(this, p);
            p.facing = 0;
            p.possession = 0;
            p.runningFrame = 0;
            p.actionFrame = 0;
            p.dirToBall = 0;
            p.diagonalRunDir = 0;
            p.runLeft = 0; p.runRight = 0; p.runDown = 0; p.runUp = 0;
            p.X = c.FAR_RANGE;
            p.Y = c.FAR_RANGE; // this should get reset anyway.. right?
        }
        if(anyPoss()){
            Titan tip = this.titanInPossession().get();
            tip.possession = 0;
        }
        setRandomBallSpawnPosition();
        resetPosSel();
    }

    public void setRandomBallSpawnPosition() {
        ball.X = c.BALL_X;
        double TOP_CENTER = c.getI("goal.low.y") + c.getI("goal.low.height") / 2.0;
        double MID_CENTER = c.getI("goal.hi.y") + c.getI("goal.hi.height") / 2.0;
        double BOT_CENTER = c.getI("goal.low2.y") + c.getI("goal.low.height") / 2.0;

        double roll = Math.random();
        if (roll < 0.30) {
            ball.Y = TOP_CENTER;
        } else if (roll < 0.60) {
            ball.Y = BOT_CENTER;
        } else {
            ball.Y = MID_CENTER;
        }
    }

    public void intersectAll() {
        boolean exempt = contactExemptBall();
        for (int n = players.length - 1; n >= 0; n--) {
            Titan p = players[n];
            if (!exempt || (p.getType() == TitanType.GOALIE && effectPool.hasEffect(p, EffectId.BLOCK))) {
                intersectBall(n + 1, (int) p.X, (int) p.Y);
            }
        }
        Entity[] solids = (allSolids != null) ? allSolids : entityPool.toArray(new Entity[0]);
        ball.collidesSolid(this, solids);
    }

    public boolean contactExemptBall() {
        // LOB uncatchable window (BLUE region, frames 3–8): scoped strictly to the active
        // lob thrower. This prevents a stuck LOB state on any player from globally
        // disabling ball collision during an unrelated throw.
        if (activeLobThrower != null
                && activeLobThrower.actionFrame >= 3
                && activeLobThrower.actionFrame <= 8) {
            return true;
        }
        Titan mover = getAnyBallMover();
        if (mover != null && mover.actionState == Titan.TitanState.LOB
                && mover.actionFrame >= 3 && mover.actionFrame <= 8) {
            return true;
        }
        return false;
    }

    public boolean anyClientSelected(int n) {
        for (PlayerDivider p : clients) {
            if (!p.isAi && p.selection == n) {
                return true;
            }
        }
        return false;
    }

    public void processClientPacket(PlayerDivider from, ClientPacket request) {
        if(this.phase != GamePhase.INGAME){
            return; //dontcare
        }
        boolean known = false;
        for (PlayerDivider p : clients) {
            if (p.id == from.id) {
                known = true;
                break;
            }
        }
        if (!known) {
            System.out.println("Packet from unknown client id=" + from.id);
            return;
        }
        lock();
        try {
            if (from != null) {
                if(logs){
                    System.out.println(from + " packet");
                    try {
                        System.out.println(MAPPER.writeValueAsString(request));
                    } catch (JsonProcessingException e) {
                        e.printStackTrace();
                    }
                }
                Titan t = titanFromPacket(from);
                if (t == null) {
                    System.out.println("got passed a bad titan index! Possibly from another game?");
                    return;
                }
                this.processKeys(request, from);
                this.processProgramming(t, request);
                int btn = getBtn(request, t);
                if (t.possession == 1 && request.posX != -1 && request.posY != -1 && btn != 0) {
                    this.serverMouseRoutine(t, request.posX, request.posY, btn, request.camX, request.camY);
                }
                for (PlayerDivider client : clients) {
                    if (client.id == from.id) {
                        from.ready = true;
                        int classSelIndex = client.possibleSelection.get(0) - 1;
                        Titan classTitan = players[classSelIndex];
                        if (request.classSelection != null) {
                            if (request.classSelection == TitanType.GOALIE) {
                                if (classSelIndex == 0 || classSelIndex == 1) {
                                    classTitan.setType(TitanType.GOALIE);
                                } else {
                                    //System.out.println("[DIAG] Blocking non-goalie slot from setting type to GOALIE");
                                }
                            } else {
                                if (classSelIndex == 0 || classSelIndex == 1) {
                                    //System.out.println("[DIAG] Blocking goalie slot from changing class");
                                } else {
                                    classTitan.setType(request.classSelection);
                                }
                            }
                        }
                        if (request.masteries != null) {
                            request.masteries.applyMasteries(classTitan);
                        }
                    }
                }
                kickoff();
            }
        } finally {
            unlock();
        }
    }

    private static int getBtn(ClientPacket request, Titan t) {
        int btn = 0;
        if (request.lobBtn) {
            btn = 3;
        } else if (request.shotBtn) {
            // Artisan spin-shot: direction is a parameter, not a trigger, so read raw from request
            if (t.getType() != null && t.getType().equals(TitanType.ARTISAN)) {
                if (request.artisanShot == ClientPacket.ARTISAN_SHOT.LEFT) {
                    btn = 4;
                } else if (request.artisanShot == ClientPacket.ARTISAN_SHOT.RIGHT) {
                    btn = 5;
                } else {
                    btn = 1;
                }
            } else {
                btn = 1;
            }
        }
        return btn;
    }

    public synchronized void kickoff() {
        if (!began) {
            began = true;
            int interval = customTickIntervalMs > 0 ? customTickIntervalMs : GAMETICK_MS;
            ScheduledExecutorService exec = Executors.newScheduledThreadPool(1);
            TerminableExecutor terminableExecutor = new TerminableExecutor(this, exec);
            exec.scheduleAtFixedRate(terminableExecutor, 0, interval, TimeUnit.MILLISECONDS);

            System.out.println("gametick kickoff should only run once (interval: " + interval + "ms)");
        }
    }

    protected Titan titanFromPacket(PlayerDivider conn) {
        try {
            for (PlayerDivider p : clients) {
                if (p.id == conn.id) {
                    return players[p.selection - 1];
                }
            }
        } catch (Exception ex1) {
        }
        return null;
    }

    protected void boost(KeyDifferences controlsHeld, Titan t) {
        if (controlsHeld.BOOST == 1 && (this.phase == GamePhase.INGAME || phase == GamePhase.TUTORIAL)) {
            t.isBoosting = true;
        }
        if (controlsHeld.BOOST == -1 && (this.phase == GamePhase.INGAME || phase == GamePhase.TUTORIAL)) {
            t.isBoosting = false;
        }
        if (controlsHeld.BOOST_LOCK == 1 && (this.phase == GamePhase.INGAME || phase == GamePhase.TUTORIAL)) {
            t.isBoosting = !t.isBoosting;
        }
        if (t.fuel < 1.0) {
            t.isBoosting = false;
        }
    }

    static final Map<String, String> TREE_SHORT_NAME = Map.of(
        "GOALIE_TREE_SIEGE", "siege",
        "GOALIE_TREE_FORTRESS", "fortress",
        "GOALIE_TREE_EMPOWERMENT", "empowerment",
        "GOALIE_TREE_CULTIVATION", "cultivation"
    );

    private static final Map<String, int[]> REQUIRED_TECHS = Map.of(
        "siege",       new int[]{1, 0, 2, 1, 1},
        "fortress",    new int[]{1, 0, 2, 1, 1},
        "empowerment", new int[]{1, 0, 2, 1, 1},
        "cultivation", new int[]{1, 0, 2, 1, 1}
    );

    boolean tierPrereqMet(String shortName, String tier, Set<String> purchased) {
        int n = Integer.parseInt(tier.substring(1)); // "t3" -> 3
        if (n <= 1) return true; // t1 has no prereq

        String prevTier = "t" + (n - 1);
        if (!tierPrereqMet(shortName, prevTier, purchased)) return false;

        int required = REQUIRED_TECHS.get(shortName)[n - 2]; // index 0 = t1->t2 gate
        return countInTier(purchased, shortName, prevTier) >= required;
    }

    public void handleGoalieTreePurchase(Titan t, String treeKey, String nodeKey) {
        if (treeKey == null || nodeKey == null) return;
        String shortName = TREE_SHORT_NAME.get(treeKey);
        if (shortName == null || !nodeKey.startsWith(shortName + ".")) return;
        String rest = nodeKey.substring(shortName.length() + 1); // "t3.snaretrap"
        int dot = rest.indexOf('.');
        if (dot < 0) return;
        String tier = rest.substring(0, dot);
        boolean isHome = (t.team == TeamAffiliation.HOME);
        Set<String> purchased = isHome ? homeGoaliePurchasedUpgrades : awayGoaliePurchasedUpgrades;
        boolean hasCost = costs.hasKey(nodeKey + ".cost") || costs.hasKey(nodeKey + ".cost.mana");
        boolean hasUse  = costs.hasKey(nodeKey + ".use")  || costs.hasKey(nodeKey + ".use.mana");
        if (!hasCost && !hasUse) return; // unknown node - reject
        if (hasCost && purchased.contains(nodeKey)) return; // one-time, already owned

        // Check if this purchase is allowed via Mana Pollinate
        boolean pollinated = purchased.contains("cultivation.t5.manapollinate");
        boolean isPollinatedPurchase = false;
        if (pollinated && tier.equals("t5") && !shortName.equals("cultivation") && hasCost) {
            boolean alreadyPurchasedOtherT5 = false;
            for (String key : purchased) {
                if (key.contains(".t5.") && !key.startsWith("cultivation.")) {
                    alreadyPurchasedOtherT5 = true;
                    break;
                }
            }
            if (!alreadyPurchasedOtherT5) {
                isPollinatedPurchase = true;
            }
        }

        // prereq, derived from purchased set each call - chains back to t1
        if (!isPollinatedPurchase) {
            if (!tierPrereqMet(shortName, tier, purchased)) return;
        }

        boolean isMana = costs.hasKey(nodeKey + ".cost.mana") || costs.hasKey(nodeKey + ".use.mana");
        if (isPollinatedPurchase) {
            isMana = true;
        }

        String costKey = hasCost
            ? (isMana ? (costs.hasKey(nodeKey + ".cost.mana") ? nodeKey + ".cost.mana" : nodeKey + ".cost") : (costs.hasKey(nodeKey + ".cost") ? nodeKey + ".cost" : nodeKey + ".cost.mana"))
            : (isMana ? (costs.hasKey(nodeKey + ".use.mana") ? nodeKey + ".use.mana" : nodeKey + ".use") : (costs.hasKey(nodeKey + ".use") ? nodeKey + ".use" : nodeKey + ".use.mana"));
        double cost = costs.getD(costKey);
        boolean checkBalance = costs.getB("features.CHECK_BALANCE");
        double balance = isMana
            ? (isHome ? homeGoalieMana : awayGoalieMana)
            : (isHome ? homeGoalieCurrency : awayGoalieCurrency);
        if (checkBalance && balance < cost) return;
        double newBalance = balance - cost;
        if (isMana) {
            if (isHome) homeGoalieMana = newBalance; else awayGoalieMana = newBalance;
        } else {
            if (isHome) homeGoalieCurrency = newBalance; else awayGoalieCurrency = newBalance;
        }
        if (isMana) {
            stats.grant(this, t, StatEngine.StatEnum.MANASPENT, cost);
        } else {
            if (hasCost) {
                if (!"cultivation".equals(shortName)) {
                    stats.grant(this, t, StatEngine.StatEnum.UPGRADESGOLD, cost);
                }
            } else if (hasUse) {
                stats.grant(this, t, StatEngine.StatEnum.CONSUMABLESGOLD, cost);
            }
        }
        if (hasCost) {
            purchased.add(nodeKey);
        }
        if (isHome) {
            homeGoalieAllPurchasedUpgrades.add(nodeKey);
        } else {
            awayGoalieAllPurchasedUpgrades.add(nodeKey);
        }
        
        if (isHome) {
            homeGoalieAbilities.purchaseOrUse(this, t, nodeKey);
        } else {
            awayGoalieAbilities.purchaseOrUse(this, t, nodeKey);
        }
        double matchFps = 1000.0 / (GAMETICK_MS > 0 ? GAMETICK_MS : 25);
        int totalSec = (int)(framesSinceStart / matchFps);
        String gameTimeStr = String.format("%d:%02d", totalSec / 60, totalSec % 60);
        recentPurchases.add(new RecentPurchase(isHome ? "HOME" : "AWAY", nodeKey, System.currentTimeMillis(), gameTimeStr));
        if (recentPurchases.size() > 20) {
            recentPurchases.remove(0);
        }
        for(String s : purchased)
            System.out.println(s);
    }

    private int countInTier(Set<String> purchased, String shortName, String tier) {
        String prefix = shortName + "." + tier + ".";
        int count = 0;
        for (String key : purchased) if (key.startsWith(prefix)) count++;
        return count;
    }

    public static final List<GoalieBuildOrderManager.BuildPreset> GOALIE_NAMED_BUILD_PRESETS = GoalieBuildOrderManager.GOALIE_NAMED_BUILD_PRESETS;
    public static final List<List<String>> GOALIE_BUILD_PRESETS = GoalieBuildOrderManager.GOALIE_BUILD_PRESETS;
    @JsonIgnore
    public transient GoalieBuildOrderManager goalieBuildOrderManager = new GoalieBuildOrderManager();

    public static String getTreeKeyForNode(String nodeKey) {
        return GoalieBuildOrderManager.getTreeKeyForNode(nodeKey);
    }

    public boolean isManaBuildNode(List<String> order, String nodeKey) {
        return GoalieBuildOrderManager.isManaBuildNode(this, order, nodeKey);
    }

    public void setAiGoalieBuildOrder(Titan ai, String buildName, List<String> order) {
        GoalieBuildOrderManager.setAiGoalieBuildOrder(this, ai, buildName, order);
    }

    public void setAiGoalieBuildOrder(Titan ai, List<String> order) {
        GoalieBuildOrderManager.setAiGoalieBuildOrder(this, ai, order);
    }

    public void tickAiGoalieBuildOrder(Titan ai) {
        GoalieBuildOrderManager.tickAiGoalieBuildOrder(this, ai);
    }


    protected void processKeys(ClientPacket controls, PlayerDivider from) {
        if (from != null) {
            Titan t = players[from.selection - 1];
            int clientIndex = clientIndex(from);
            if (lastControlPacket == null || lastControlPacket.length == 0) {
                lastControlPacket = new ClientPacket[1];
                lastControlPacket[0] = new ClientPacket();
            }
            KeyDifferences controlsHeld = new KeyDifferences(controls, lastControlPacket[clientIndex]);
            boost(controlsHeld, t);
            if (t.getType() == TitanType.GOALIE && this.phase == GamePhase.INGAME) {
                if (controls.goalieClickX != null && controls.goalieClickY != null) {
                    double clickX = controls.goalieClickX + controls.camX;
                    double clickY = controls.goalieClickY + controls.camY;
                    handleGoalieAttackClick(from.getEmail(), clickX, clickY, t.team, t);
                } else if (controls.shotBtn && t.possession == 0) {
                    double clickX = controls.posX + controls.camX;
                    double clickY = controls.posY + controls.camY;
                    handleGoalieAttackClick(from.getEmail(), clickX, clickY, t.team, t);
                }
            }
            // Goalie skill-tree purchase. buyGoalieNode is one-shot from the
            // client (set on the packet only for the tick of the click, null
            // otherwise), so no KeyDifferences edge-detection needed here -
            // a null check is sufficient and avoids double-buying if this
            // packet is ever reprocessed.
            if (t.getType() == TitanType.GOALIE && controls.buyGoalieNode != null && this.phase == GamePhase.INGAME) {
                System.out.println("Made purchase attempt");
                handleGoalieTreePurchase(t, controls.buyGoalieTree, controls.buyGoalieNode);
            }
            if (controlsHeld.SWITCH == 1 && this.phase == GamePhase.INGAME && t.actionState == Titan.TitanState.IDLE) {
                from.incSel(this);
                t.runLeft = 0;
                t.runRight = 0;
                t.runDown = 0;
                t.runUp = 0;
                t.runningFrame = 0;
                t.diagonalRunDir = 0;
            }
            if ((controlsHeld.STEAL == 1 && this.phase == GamePhase.INGAME && t.actionState == Titan.TitanState.IDLE)) {
                if (!effectPool.isStunned(t) && !effectPool.hasEffect(t, EffectId.COOLDOWN_STEAL)) {
                    try {
                        boolean stolen = ability.castSteal(this, t);
                        if (t.actionState == Titan.TitanState.IDLE && !stolen) {//Curve may be set by ability
                            t.actionState = Titan.TitanState.STEAL;
                            t.actionFrame = 0;
                            t.pushMove();
                        }
                    } catch (Exception e) {
                    }
                }
            }
            if (controlsHeld.CAM == 1 && this.phase == GamePhase.DRAW_CLASS_SCREEN) {
                this.phase = GamePhase.SET_MASTERIES;
            }
            if ((controlsHeld.E == 1 || controlsHeld.callForBall == 1) && this.phase == GamePhase.INGAME){
                boolean handledCallForBall = false;
                if (t.possession == 0 && !effectPool.hasEffect(t, EffectId.DEAD)) {
                    handledCallForBall = handleCallForBall(t);
                }
                if (!handledCallForBall && controlsHeld.E == 1) {
                    System.out.println("[CAST_DEBUG] E key detected for titan " + t.getType() + ", actionState=" + t.actionState + ", isStunned=" + effectPool.isStunned(t));
                    if((t.actionState == Titan.TitanState.IDLE) ) {
                        if (!effectPool.isStunned(t)) {
                            try {
                                boolean caststun = ability.castQ(this, t);
                                System.out.println("[CAST_DEBUG] castQ returned: " + caststun + " for titan " + t.getType());
                                if (caststun) {//Curve may be set by ability
                                    t.actionState = Titan.TitanState.A1;
                                    t.actionFrame = 0;
                                    t.pushMove();
                                    effectPool.addUniqueEffect(
                                        new EmptyEffect(t.eCastFrames * GAMETICK_MS, t, EffectId.CAST_LAG), this);
                                    System.out.println("[CAST_DEBUG] Set state to A1 for titan " + t.getType() + ", eCastFrames=" + t.eCastFrames + ", CAST_LAG added");
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
            if (controlsHeld.R == 1 && this.phase == GamePhase.INGAME){
                System.out.println("[CAST_DEBUG] R key detected for titan " + t.getType() + ", actionState=" + t.actionState + ", isStunned=" + effectPool.isStunned(t));
                if((t.actionState == Titan.TitanState.IDLE)) {
                    if (!effectPool.isStunned(t)) {
                        try {
                            boolean caststun = ability.castW(this, t);
                            System.out.println("[CAST_DEBUG] castW returned: " + caststun + " for titan " + t.getType());
                            if (caststun) {//Curve may be set by ability
                                t.actionState = Titan.TitanState.A2;
                                t.actionFrame = 0;
                                t.pushMove();
                                effectPool.addUniqueEffect(
                                    new EmptyEffect(t.rCastFrames * GAMETICK_MS, t, EffectId.CAST_LAG), this);
                                System.out.println("[CAST_DEBUG] Set state to A2 for titan " + t.getType() + ", rCastFrames=" + t.rCastFrames + ", CAST_LAG added");
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            moveKeys(controlsHeld, t);
            lastControlPacket[clientIndex] = controls;
        }
    }   

    public boolean isActionMovementUnlocked(Titan t) {
        if (t.actionState == Titan.TitanState.IDLE) {
            return true;
        }
        if (t.actionState == Titan.TitanState.SHOOT) {
            return t.actionFrame >= c.SHOT_CASTLAG_FRAMES;
        }
        if (t.actionState == Titan.TitanState.LOB) {
            return t.actionFrame >= c.LOB_CASTLAG_FRAMES;
        }
        return false;
    }

    public void moveKeys(KeyDifferences controlsHeld, Titan t) {
        if (!effectPool.hasEffect(t, EffectId.DEAD)) {
            // Always keep keyHeld* in sync with the live key state,
            // regardless of cast lag – popMove() uses these to resume movement.
            if (controlsHeld.RIGHT == 1)  t.keyHeldR = true;
            if (controlsHeld.RIGHT == -1) t.keyHeldR = false;
            if (controlsHeld.LEFT  == 1)  t.keyHeldL = true;
            if (controlsHeld.LEFT  == -1) t.keyHeldL = false;
            if (controlsHeld.UP    == 1)  t.keyHeldU = true;
            if (controlsHeld.UP    == -1) t.keyHeldU = false;
            if (controlsHeld.DOWN  == 1)  t.keyHeldD = true;
            if (controlsHeld.DOWN  == -1) t.keyHeldD = false;

            if (controlsHeld.RIGHT == 1 || controlsHeld.UP == 1 || controlsHeld.LEFT == 1 || controlsHeld.DOWN == 1) {
                t.programmed = false;
            }
            if (controlsHeld.RIGHT == 1) {
                t.runLeft = 0;
                t.moveMemL = false;
            }
            if (controlsHeld.LEFT == 1) {
                t.runRight = 0;
                t.moveMemR = false;
            }
            if (controlsHeld.UP == 1) {
                t.runDown = 0;
                t.moveMemD = false;
            }
            if (controlsHeld.DOWN == 1) {
                t.runUp = 0;
                t.moveMemU = false;
            }
            if (t.programmed) {
                t.runDown = 0;
                t.runLeft = 0;
                t.runRight = 0;
                t.runUp = 0;
            } else {
                boolean canMoveNow = !effectPool.isRooted(t) && isActionMovementUnlocked(t);
                if (controlsHeld.RIGHT == 1 && this.phase == GamePhase.INGAME) {
                    if (canMoveNow) {
                        t.runLeft = 0;
                        t.runRight = 1;
                    } else {
                        t.moveMemR = true;
                        t.moveMemL = false;
                    }
                }
                if (controlsHeld.LEFT == 1 && this.phase == GamePhase.INGAME) {
                    if (canMoveNow) {
                        t.runLeft = 1;
                        t.runRight = 0;
                    } else {
                        t.moveMemR = false;
                        t.moveMemL = true;
                    }
                }
                if (controlsHeld.UP == 1 && this.phase == GamePhase.INGAME) {
                    if (canMoveNow) {
                        t.runUp = 1;
                        t.runDown = 0;
                    } else {
                        t.moveMemU = true;
                        t.moveMemD = false;
                    }
                }
                if (controlsHeld.DOWN == 1 && this.phase == GamePhase.INGAME) {
                    if (canMoveNow) {
                        t.runUp = 0;
                        t.runDown = 1;
                    } else {
                        t.moveMemU = false;
                        t.moveMemD = true;
                    }
                }
                //Done with helds

                //The releases below here
                if (controlsHeld.RIGHT == -1 && this.phase == GamePhase.INGAME) {
                    t.runRight = 0;
                    t.runningFrame = 0;
                    t.diagonalRunDir = 0;
                    t.moveMemR = false;
                }
                if (controlsHeld.LEFT == -1 && this.phase == GamePhase.INGAME) {
                    t.runLeft = 0;
                    t.runningFrame = 0;
                    t.diagonalRunDir = 0;
                    t.moveMemL = false;
                }
                if (controlsHeld.UP == -1 && this.phase == GamePhase.INGAME) {
                    t.runUp = 0;
                    t.runningFrame = 0;
                    t.dirToBall = 0;
                    t.moveMemU = false;
                }
                if (controlsHeld.DOWN == -1 && this.phase == GamePhase.INGAME) {
                    t.runDown = 0;
                    t.runningFrame = 0;
                    t.dirToBall = 0;
                    t.moveMemD = false;
                }
            }
        }
    }

    protected void processProgramming(Titan t, ClientPacket request) {
        if (request.MV_BALL) {
            t.programmed = true;
            t.marchingOrderX = -1;
            t.marchingOrderY = -1;
            t.invalidatePath();
        }
        if (request.MV_CLICK) {
            t.programmed = true;
            t.marchingOrderX = request.posX + request.camX;
            t.marchingOrderY = request.posY + request.camY;
            t.invalidatePath();
        }
    }

    public int clientIndex(PlayerDivider from) {
        if (from == null || clients == null) {
            return -1;
        }
        for (int i = 0; i < clients.size(); i++) {
            if (clients.get(i) != null && clients.get(i).id == from.id) {
                return i;
            }
        }
        return -1;
    }

    public int clientIndex(Titan t) {
        if (t == null) {
            return -1;
        }
        PlayerDivider from = clientFromTitan(t);
        if (from == null || clients == null) {
            return -1;
        }
        for (int i = 0; i < clients.size(); i++) {
            if (clients.get(i) != null && clients.get(i).id == from.id) {
                return i;
            }
        }
        return -1;
    }

    protected void updateSelectedDirection() {
        for (Titan t : players) {
            if (t.runRight == 1 && t.runUp == 1) {
                t.facing = 45;
            }
            if (t.runUp == 1 && t.runRight == 0 && t.runLeft == 0) {
                if (t.facing > 90 && t.facing < 270) {
                    t.facing = 91;
                } else {
                    t.facing = 89;//shouldn't affect any other uses but allows remembering LR dir
                }
            }
            if (t.runLeft == 1 && t.runUp == 1) {
                t.facing = 135;
            }
            if (t.runLeft == 1 && t.runDown == 0 && t.runUp == 0) {
                t.facing = 180;
            }
            if (t.runLeft == 1 && t.runDown == 1) {
                t.facing = 225;
            }
            if (t.runDown == 1 && t.runRight == 0 && t.runLeft == 0) {
                if (t.facing > 270 || t.facing < 90) {
                    t.facing = 271;
                } else {
                    t.facing = 269;//shouldn't affect any other uses but allows remembering LR dir
                }
            }
            if (t.runRight == 1 && t.runDown == 1) {
                t.facing = 315;
            }
            if (t.runRight == 1 && t.runDown == 0 && t.runUp == 0) {
                t.facing = 0;
            }
        }
    }
    public int getLaneAdvantage(int L, TeamAffiliation team) {
        int homeCount = 0;
        int awayCount = 0;
        for (Entity entity : entityPool) {
            if (entity instanceof LaneMinion && entity.getHealth() > 0.0 && ((LaneMinion) entity).laneIndex == L) {
                if (entity.team == TeamAffiliation.HOME) homeCount++;
                else awayCount++;
            }
        }
        int homeBonus = homeLaneBonusValue[L];
        int awayBonus = awayLaneBonusValue[L];
        
        int netMinions = (team == TeamAffiliation.HOME)
            ? (homeCount - awayCount)
            : (awayCount - homeCount);

        if (netMinions > 10) netMinions = 10;
        if (netMinions < -10) netMinions = -10;

        int netBonus = (team == TeamAffiliation.HOME)
            ? (homeBonus - awayBonus)
            : (awayBonus - homeBonus);

        int P = netMinions + netBonus;

        if (P > 20) P = 20;
        if (P < -20) P = -20;
        return P;
    }

    public void updateAccumulatorHoops() {
        int baseHiW = c.getI("goal.hi.width");
        int baseHiH = c.getI("goal.hi.height");
        int baseLowW = c.getI("goal.low.width");
        int baseLowH = c.getI("goal.low.height");

        double homeHiCX = HOME_HI_X + baseHiW / 2.0;
        double homeHiCY = HOME_HI_Y + baseHiH / 2.0;
        double awayHiCX = AWAY_HI_X + baseHiW / 2.0;
        double awayHiCY = AWAY_HI_Y + baseHiH / 2.0;

        double homeLow1CX = c.getI("goal.home.low.x") + baseLowW / 2.0;
        double homeLow1CY = c.getI("goal.low.y") + baseLowH / 2.0;
        double homeLow2CX = c.getI("goal.home.low.x") + baseLowW / 2.0;
        double homeLow2CY = c.getI("goal.low2.y") + baseLowH / 2.0;

        double awayLow1CX = c.getI("goal.away.low.x") + baseLowW / 2.0;
        double awayLow1CY = c.getI("goal.low.y") + baseLowH / 2.0;
        double awayLow2CX = c.getI("goal.away.low.x") + baseLowW / 2.0;
        double awayLow2CY = c.getI("goal.low2.y") + baseLowH / 2.0;

        homeHiGoal.w = baseHiW; homeHiGoal.h = baseHiH; homeHiGoal.x = HOME_HI_X; homeHiGoal.y = HOME_HI_Y;
        awayHiGoal.w = baseHiW; awayHiGoal.h = baseHiH; awayHiGoal.x = AWAY_HI_X; awayHiGoal.y = AWAY_HI_Y;
        
        lowGoals[0].w = baseLowW; lowGoals[0].h = baseLowH; lowGoals[0].x = c.getI("goal.home.low.x"); lowGoals[0].y = c.getI("goal.low.y");
        lowGoals[1].w = baseLowW; lowGoals[1].h = baseLowH; lowGoals[1].x = c.getI("goal.home.low.x"); lowGoals[1].y = c.getI("goal.low2.y");
        lowGoals[2].w = baseLowW; lowGoals[2].h = baseLowH; lowGoals[2].x = c.getI("goal.away.low.x"); lowGoals[2].y = c.getI("goal.low.y");
        lowGoals[3].w = baseLowW; lowGoals[3].h = baseLowH; lowGoals[3].x = c.getI("goal.away.low.x"); lowGoals[3].y = c.getI("goal.low2.y");

        if (homeGoaliePurchasedUpgrades.contains("siege.t4.accumulators")) {
            int x0 = getLaneAdvantage(0, TeamAffiliation.HOME);
            if (x0 > 10) {
                double scale = 1.0 + (5.0 * (x0 - 10.0)) / 100.0;
                lowGoals[2].w = (int) (baseLowW * scale);
                lowGoals[2].h = (int) (baseLowH * scale);
                lowGoals[2].x = (int) (awayLow1CX - lowGoals[2].w / 2.0);
                lowGoals[2].y = (int) (awayLow1CY - lowGoals[2].h / 2.0);
            }
            int x1 = getLaneAdvantage(1, TeamAffiliation.HOME);
            if (x1 > 10) {
                double scale = 1.0 + (5.0 * (x1 - 10.0)) / 100.0;
                awayHiGoal.w = (int) (baseHiW * scale);
                awayHiGoal.h = (int) (baseHiH * scale);
                awayHiGoal.x = (int) (awayHiCX - awayHiGoal.w / 2.0);
                awayHiGoal.y = (int) (awayHiCY - awayHiGoal.h / 2.0);
            }
            int x2 = getLaneAdvantage(2, TeamAffiliation.HOME);
            if (x2 > 10) {
                double scale = 1.0 + (5.0 * (x2 - 10.0)) / 100.0;
                lowGoals[3].w = (int) (baseLowW * scale);
                lowGoals[3].h = (int) (baseLowH * scale);
                lowGoals[3].x = (int) (awayLow2CX - lowGoals[3].w / 2.0);
                lowGoals[3].y = (int) (awayLow2CY - lowGoals[3].h / 2.0);
            }
        }

        if (awayGoaliePurchasedUpgrades.contains("siege.t4.accumulators")) {
            int x0 = getLaneAdvantage(0, TeamAffiliation.AWAY);
            if (x0 > 10) {
                double scale = 1.0 + (5.0 * (x0 - 10.0)) / 100.0;
                lowGoals[0].w = (int) (baseLowW * scale);
                lowGoals[0].h = (int) (baseLowH * scale);
                lowGoals[0].x = (int) (homeLow1CX - lowGoals[0].w / 2.0);
                lowGoals[0].y = (int) (homeLow1CY - lowGoals[0].h / 2.0);
            }
            int x1 = getLaneAdvantage(1, TeamAffiliation.AWAY);
            if (x1 > 10) {
                double scale = 1.0 + (5.0 * (x1 - 10.0)) / 100.0;
                homeHiGoal.w = (int) (baseHiW * scale);
                homeHiGoal.h = (int) (baseHiH * scale);
                homeHiGoal.x = (int) (homeHiCX - homeHiGoal.w / 2.0);
                homeHiGoal.y = (int) (homeHiCY - homeHiGoal.h / 2.0);
            }
            int x2 = getLaneAdvantage(2, TeamAffiliation.AWAY);
            if (x2 > 10) {
                double scale = 1.0 + (5.0 * (x2 - 10.0)) / 100.0;
                lowGoals[1].w = (int) (baseLowW * scale);
                lowGoals[1].h = (int) (baseLowH * scale);
                lowGoals[1].x = (int) (homeLow2CX - lowGoals[1].w / 2.0);
                lowGoals[1].y = (int) (homeLow2CY - lowGoals[1].h / 2.0);
            }
        }
    }


    public void gameTick() throws Exception {
        //System.out.println("tock " + began + ended);
        lock();
        this.nowEpochMs = System.currentTimeMillis();
        if (this.phase == GamePhase.SCORE_FREEZE) {
            unlock();
            return;
        }
        if (this.phase == GamePhase.COUNTDOWN) {
            secondsToStart -= (GAMETICK_MS / 1000.0);
            if (secondsToStart <= 0) {
                secondsToStart = 0;
                this.phase = GamePhase.INGAME;
            }
            unlock();
            return;
        }
        if (began && !ended) {
            try {
                framesSinceStart++;
                updateAccumulatorHoops();
                homeGoalieAbilities.tick(this);
                awayGoalieAbilities.tick(this);
                tickGoalieMana();
                tickGoalieGold();
                checkDragonSpawning();
                checkTurnovers();
                boolean over = gameDurationRuleChanges();
                
                double ticksPerSec = 1000.0 / Math.max(1, GAMETICK_MS);
                int waveInterval = (int) (3.0 * ticksPerSec);
                if (framesSinceStart % waveInterval == 1) {
                    spawnMinionWave();
                }
                tickLaneMinions();

                // Build allSolids directly without an ArrayList intermediate.
                // tickEntities removes dead entries from entityPool first, then we
                // copy players + surviving entities into a single array.
                tickEntities(entityPool);
                Entity[] newSolids = new Entity[players.length + entityPool.size()];
                System.arraycopy(players, 0, newSolids, 0, players.length);
                for (int si = 0; si < entityPool.size(); si++) {
                    newSolids[players.length + si] = entityPool.get(si);
                }
                allSolids = newSolids;
                refreshStaticGridIfNeeded();
                updateBallIfPossessed();
                effectPool.tickAll(this);
                doHealthModification();
                for (GoalHoop goal : lowGoals) {
                    goal.checkReady();
                }
                for (GoalHoop goal : hiGoals) {
                    goal.checkReady();
                }
                updateSelectedDirection();
                cullOldColliders();
            } catch (Exception e) {
                e.printStackTrace();
            }
            for (Titan t : players) {
                if(t.isBoosting && t.possession == 1 && t.getType() != TitanType.DASHER){
                    t.isBoosting = false;
                }

                double maxFuel = 100.0;
                t.maxFuel = maxFuel;

                double goalieDrainMult = (t.team == TeamAffiliation.HOME) 
                    ? homeGoalieAbilities.getBoostDrainMultiplier(this) 
                    : awayGoalieAbilities.getBoostDrainMultiplier(this);
                double drainRate = 0.75 * t.boostDrainFactor * goalieDrainMult;

                if (t.isBoosting) {
                    t.fuel -= drainRate;
                    if (t.fuel < 0) {
                        t.fuel = 0;
                    }
                } else {
                    double fastRegen = c.getD("globals.boost.regen.fast") * t.boostRegenFactor;
                    double slowRegen = c.getD("globals.boost.regen.slow") * t.boostRegenFactor;
                    double cutoff = maxFuel * 0.25;
                    if (t.fuel > cutoff) {
                        t.fuel += fastRegen;//regen bonus
                    } else {
                        t.fuel += slowRegen;
                    }
                    if (t.fuel > maxFuel) {
                        t.fuel = maxFuel;
                    }
                }
                boolean canRun = !effectPool.isRooted(t) && isActionMovementUnlocked(t);
                if (canRun) {
                    if (t.runRight == 1) runRightCtrl(t);
                    if (t.runLeft == 1) runLeftCtrl(t);
                    if (t.runUp == 1) runUpCtrl(t);
                    if (t.runDown == 1) runDownCtrl(t);
                    programmedCtrl(t);
                }
                unhideBallIfHidden(t);
                if (t.actionState == Titan.TitanState.SHOOT) shootingBall(t);
                else if (t.actionState == Titan.TitanState.LOB) lobbingBall(t);
                else if (t.actionState == Titan.TitanState.CURVE_LEFT) curve(t, 1);
                else if (t.actionState == Titan.TitanState.CURVE_RIGHT) curve(t, -1);
                if (t.actionState == Titan.TitanState.A1) attack1(t);
                if (t.actionState == Titan.TitanState.A2) attack2(t);
                if (t.actionState == Titan.TitanState.STEAL) steal(t);
                checkAndExecuteQueuedShot(t);
            }
            yourPlayerTactics();
        }
        if (ballVisible) {
            updateBallIfPossessed();
            intersectAll();
            detectGoals();
        }
        if (ball.X < c.MIN_X) ball.X = c.MIN_X;
        if (ball.X > c.MAX_X) ball.X = c.MAX_X;
        if (ball.Y < c.MIN_Y) ball.Y = c.MIN_Y;
        if (ball.Y > c.MAX_Y) ball.Y = c.MAX_Y;
        resurrectAll();
        unlock();
    }

    public boolean[] getOrCreateStaticObstacleGrid() {
        if (staticObstacleGrid == null) {
            staticObstacleGrid = TitanPathfinder.buildStaticGrid(this);
            cachedSolidEntityCount = countSolidStaticEntities();
        }
        return staticObstacleGrid;
    }

    public void refreshStaticGridIfNeeded() {
        int currentCount = countSolidStaticEntities();
        if (staticObstacleGrid == null || cachedSolidEntityCount != currentCount) {
            staticObstacleGrid = TitanPathfinder.buildStaticGrid(this);
            cachedSolidEntityCount = currentCount;
            if (entityPool != null) {
                for (Entity e : entityPool) {
                    if (e != null && e.solid && !(e instanceof Titan) && e.health > 0) {
                        depenetrateTitansFrom(e);
                    }
                }
            }
            // Invalidate all active titan paths when a new solid obstacle appears or disappears
            for (Titan t : players) {
                if (t != null && t.programmed) {
                    t.invalidatePath();
                }
            }
        }
    }

    public void depenetrateTitansFrom(Entity obstacle) {
        if (obstacle == null || !obstacle.solid || players == null) return;
        double obsMinX = obstacle.X;
        double obsMaxX = obstacle.X + obstacle.width;
        double obsMinY = obstacle.Y;
        double obsMaxY = obstacle.Y + obstacle.height;

        for (Titan t : players) {
            if (t == null || t.health <= 0) continue;
            double tMinX, tMinY, tW, tH;
            if (t.getType() == TitanType.GOALIE) {
                double xOffset = (t.width - GOALIE_SOLID_W) / 2.0;
                tMinX = t.X + xOffset;
                tMinY = t.Y;
                tW = GOALIE_SOLID_W;
                tH = GOALIE_SOLID_H;
            } else {
                tMinX = t.X + SPRITE_X_EMPTY / 2.0;
                tMinY = t.Y + SPRITE_Y_EMPTY / 2.0;
                tW = t.width - SPRITE_X_EMPTY;
                tH = t.height - SPRITE_Y_EMPTY;
            }
            double tMaxX = tMinX + tW;
            double tMaxY = tMinY + tH;

            if (tMinX < obsMaxX && tMaxX > obsMinX && tMinY < obsMaxY && tMaxY > obsMinY) {
                double overlapLeft = tMaxX - obsMinX;
                double overlapRight = obsMaxX - tMinX;
                double overlapTop = tMaxY - obsMinY;
                double overlapBottom = obsMaxY - tMinY;

                double minOverlap = Math.min(Math.min(overlapLeft, overlapRight), Math.min(overlapTop, overlapBottom));
                double pushX = 0;
                double pushY = 0;

                if (minOverlap == overlapLeft) {
                    pushX = -(overlapLeft + 2.0);
                } else if (minOverlap == overlapRight) {
                    pushX = (overlapRight + 2.0);
                } else if (minOverlap == overlapTop) {
                    pushY = -(overlapTop + 2.0);
                } else {
                    pushY = (overlapBottom + 2.0);
                }

                double newX = t.X + pushX;
                double newY = t.Y + pushY;

                newX = Math.max(c.MIN_X, Math.min(c.MAX_X - t.width, newX));
                newY = Math.max(c.MIN_Y, Math.min(c.MAX_Y - t.height, newY));

                t.setX((int) Math.round(newX));
                t.setY((int) Math.round(newY));

                if (t.collidesSolid(this, allSolids)) {
                    double obsCX = obstacle.X + obstacle.width / 2.0;
                    double obsCY = obstacle.Y + obstacle.height / 2.0;
                    double titanCX = t.X + (t.width > 0 ? t.width : 70) / 2.0;
                    double titanCY = t.Y + (t.height > 0 ? t.height : 70) / 2.0;
                    double prefAngle = Math.atan2(titanCY - obsCY, titanCX - obsCX);
                    double[] safePos = AbilityPositionHelper.findClosestUnoccupiedPosition(t.X, t.Y, t, this, prefAngle);
                    t.setX((int) Math.round(safePos[0]));
                    t.setY((int) Math.round(safePos[1]));
                }

                if (t.programmed) {
                    t.invalidatePath();
                }
            }
        }
    }

    private int countSolidStaticEntities() {
        int count = 0;
        if (allSolids != null) {
            for (Entity e : allSolids) {
                if (e != null && !(e instanceof Titan) && (e.solid || e instanceof Parapet) && e.health > 0) {
                    count++;
                }
            }
        } else if (entityPool != null) {
            for (Entity e : entityPool) {
                if (e != null && !(e instanceof Titan) && (e.solid || e instanceof Parapet) && e.health > 0) {
                    count++;
                }
            }
        }
        return count;
    }

    public void programmedCtrl(Titan t) {
        TitanPathfinder.executeProgrammedMovement(this, t);
    }

    private void unhideBallIfHidden(Titan t) {
        if(t != null && effectPool != null && effectPool.hasEffect(t, EffectId.HIDE_BALL) &&
                (t.actionState == Titan.TitanState.SHOOT ||
                        t.actionState == Titan.TitanState.LOB ||
                        t.actionState == Titan.TitanState.CURVE_LEFT ||
                        t.actionState == Titan.TitanState.CURVE_RIGHT)){
            ballVisible = true;
            lastPossessed = t.id;
            effectPool.cullEffectOn(this, t, EffectId.HIDE_BALL);
        }
    }

    private void resurrectAll() {
        for(Titan t : players){
            if(t.resurrecting){
                t.resurrect(this);
            }
        }
    }

    private boolean gameDurationRuleChanges() {
        final long FPS = 1000 / GAMETICK_MS;
        if (options.goaliesDisabled() && framesSinceStart / FPS > GOALIE_DISABLE_TIME) {
            cullGoalies();
        }
        if (framesSinceStart / FPS > PAIN_DISABLE_TIME) {
            hoopDmg = false;
        }
        double tieTimeMinutes = (this.options != null && this.options.tieIndex > 0)
                ? this.options.tieIndex
                : c.getD("tie.time");
        if (framesSinceStart / FPS > tieTimeMinutes * 60) {
            tieAble = true;
            return checkWinCondition(false);
        }
        if (framesSinceStart / FPS > c.getD("suddendeath.extreme.time") * 60) {
            suddenDeath = true;
            extremeSuddenDeath = true;
            return checkWinCondition(false);
        }
        double suddenDeathTimeMinutes = (this.options != null && this.options.suddenDeathIndex > 0)
                ? this.options.suddenDeathIndex
                : 10.0;
        if (framesSinceStart / FPS > suddenDeathTimeMinutes * 60) {
            suddenDeath = true;
            return checkWinCondition(false);
        }
        return false;
    }

    protected void tickEntities(List<Entity> entityList) {
        entityList.removeIf(e -> e.getHealth() <= 0.0);
        for (Entity e : entityList) {
            if (e instanceof Tickable) {
                ((Tickable) e).tick(this);
            }
        }
        for (Titan t : players) {
            if (t.getType() == TitanType.CAPTAIN && t.ammo == 0 && !effectPool.hasEffect(t, EffectId.COOLDOWN_Q)) {
                t.ammo = 8;
            }
        }
    }

    public void updateBallIfPossessed() {
        int i = 1;
        for (Titan t : players) {
            updateBallIfPossessed(t, i);
            i++;
        }
    }

    public void updateBallIfPossessed(Titan t, int numSel) {
        if (t.possession == 1 && !effectPool.hasEffect(t, EffectId.DEAD)) {
            int valuePlayerX = (int) t.X;
            int valuePlayerY = (int) t.Y;
            boolean goalie = isGoalie(t) || numSel == 1 || numSel == 2;
            if (c.GOALIE_DISABLED || !goalie) {
                ball.X = (int) Math.round(valuePlayerX + t.width / 2.0 - ball.centerDist);
                ball.Y = (int) Math.round(valuePlayerY + t.height / 2.0 - ball.centerDist);
            }
            if (!c.GOALIE_DISABLED && goalie) {
                boolean isHome = (t.team == TeamAffiliation.HOME || t == players[0] || (numSel == 1 && t.team != TeamAffiliation.AWAY));
                if (isHome) { // guardian exceptions: HOME
                    ball.X = (valuePlayerX + 57);
                    ball.Y = (valuePlayerY + 20);
                    // Don't own-goal this shit.
                    int safety = 0;
                    while (ownGoal() && safety++ < 300) {
                        ball.X += 1;
                        ball.Y -= 1;
                    }
                    if (ownGoal()) {
                        for (GoalHoop g : lowGoals) {
                            if (ballIntersectsEllipse(g)) ball.X = Math.max(ball.X, (int) (g.x + g.w) + 2);
                        }
                        for (GoalHoop g : hiGoals) {
                            if (ballIntersectsEllipse(g)) ball.X = Math.max(ball.X, (int) (g.x + g.w) + 2);
                        }
                    }
                } else { // guardian exceptions: AWAY
                    ball.X = (valuePlayerX - 1);
                    ball.Y = (valuePlayerY + 20);
                    int safety = 0;
                    while (ownGoal() && safety++ < 300) {
                        ball.X -= 1;
                        ball.Y -= 1;
                    }
                    if (ownGoal()) {
                        for (GoalHoop g : lowGoals) {
                            if (ballIntersectsEllipse(g)) ball.X = Math.min(ball.X, (int) (g.x - ball.width) - 2);
                        }
                        for (GoalHoop g : hiGoals) {
                            if (ballIntersectsEllipse(g)) ball.X = Math.min(ball.X, (int) (g.x - ball.width) - 2);
                        }
                    }
                }
            }

        }
    }

    private boolean ownGoal() {
        for(GoalHoop lowgoal : lowGoals){
            if(ballIntersectsEllipse(lowgoal)){
                return true;
            }
        }
        for(GoalHoop higoal : hiGoals){
            if(ballIntersectsEllipse(higoal)){
                return true;
            }
        }
        return false;
    }


    protected void setBallFromTip() {
        Optional<Titan> tip = this.titanInPossession();
        if (tip.isPresent()) {
            Titan tipTitan = tip.get();
            TeamAffiliation team = tipTitan.team;
            if (team == TeamAffiliation.HOME) {
                home.hasBall = true;
                away.hasBall = false;
                lastHomePossessor = tipTitan.id;
            }
            if (team == TeamAffiliation.AWAY) {
                away.hasBall = true;
                home.hasBall = false;
                lastAwayPossessor = tipTitan.id;
            }
        } else {
            home.hasBall = false;
            away.hasBall = false;
        }
    }

    public void intersectBall(int numSel, int valuePlayerX, int valuePlayerY) {
        Titan t = players[numSel - 1];
        if (t.getType() == TitanType.GOALIE && t.actionState == Titan.TitanState.A2) {
            CollisionMath.Bounds r1 = t.asBounds();
            CollisionMath.Bounds r2 = ball.asBounds();
            if (r1.intersects(r2)) {
                bounceOffTitan(t, null);
            }
            return;
        }
        CollisionMath.Bounds r1 = new CollisionMath.Bounds(
                valuePlayerX + SPRITE_X_EMPTY / 2.0,
                valuePlayerY + SPRITE_Y_EMPTY / 2.0,
                t.width - SPRITE_X_EMPTY,
                t.height - SPRITE_Y_EMPTY
        );
        r1 = goalieHitboxOverride(numSel, r1);
        CollisionMath.Bounds r2 = ball.asBounds();
        if ((r1.intersects(r2))) {
            if (t.id.equals(players[numSel - 1].id) && !t.id.equals(lastPossessed)) {
                Optional<Titan> tip = this.titanInPossession();
                if (!tip.isPresent()) {
                    Titan release = getAnyBallMover();
                    if (release != null) {
                        release.actionState = Titan.TitanState.IDLE;
                        release.actionFrame = 0;
                    }
                    activeLobThrower = null;
                    changePossessionStats(release, t);
                    if (t.team == TeamAffiliation.HOME) {
                        lastHomePossessor = t.id;
                    } else if (t.team == TeamAffiliation.AWAY) {
                        lastAwayPossessor = t.id;
                    }
                    home.hasBall = true;
                    away.hasBall = false;
                    players[numSel - 1].possession = 1;
                    players[numSel - 1].queuedBtn = 0;
                    lastPossessed = players[numSel - 1].id;
                    registerBallTouch(players[numSel - 1]);
                    resetAiReactionAfterPossession(players[numSel - 1]);
                    updateBallIfPossessed(t, numSel);
                }
            }
        }
    }

    protected void changePossessionStats(Titan lost, Titan gained) {
        if (lost != null) {
            TeamAffiliation oldTeam = lost.team;
            if (gained.team == oldTeam) {
                stats.grant(this, lost, StatEngine.StatEnum.PASSES);
                if (oldTeam == TeamAffiliation.HOME) {
                    lastHomePasser = lost.id;
                } else if (oldTeam == TeamAffiliation.AWAY) {
                    lastAwayPasser = lost.id;
                }
            } else { //Enemy taking possession
                stats.grant(this, lost, StatEngine.StatEnum.TURNOVERS);
                stats.grant(this, gained, StatEngine.StatEnum.BLOCKS);
                lastHomePasser = null;
                lastAwayPasser = null;
                if (gained.getType() == TitanType.GOALIE) {
                    ShotType st = currentShotType;
                    if (st == ShotType.NONE && currentStepVel != null) {
                        st = predictShotTrajectory(ball.X, ball.Y, currentStepVel, lost.team);
                    }
                    if (st == ShotType.CENTERGOAL) {
                        stats.grant(this, gained, StatEngine.StatEnum.CENTERGOAL_SAVES);
                        stats.grant(this, gained, StatEngine.StatEnum.SAVES);
                    } else if (st == ShotType.SIDEGOAL) {
                        stats.grant(this, gained, StatEngine.StatEnum.SIDEGOAL_SAVES);
                        stats.grant(this, gained, StatEngine.StatEnum.SAVES);
                    }
                }
            }
        } else {//Picking up loose ball
            if (gained.team == TeamAffiliation.HOME) {
                lastHomePasser = null;
            } else if (gained.team == TeamAffiliation.AWAY) {
                lastAwayPasser = null;
            }
            stats.grant(this, gained, StatEngine.StatEnum.REBOUND);
            if (gained.getType() == TitanType.GOALIE) {
                Titan shooter = (this.titanInPossession().isPresent()) ? this.titanInPossession().get() : (lastPossessed != null ? this.titanByID(lastPossessed.toString()).orElse(null) : null);
                ShotType st = currentShotType;
                if (st == ShotType.NONE && shooter != null && shooter.team != gained.team && currentStepVel != null) {
                    st = predictShotTrajectory(ball.X, ball.Y, currentStepVel, shooter.team);
                }
                if (st == ShotType.CENTERGOAL) {
                    stats.grant(this, gained, StatEngine.StatEnum.CENTERGOAL_SAVES);
                    stats.grant(this, gained, StatEngine.StatEnum.SAVES);
                } else if (st == ShotType.SIDEGOAL) {
                    stats.grant(this, gained, StatEngine.StatEnum.SIDEGOAL_SAVES);
                    stats.grant(this, gained, StatEngine.StatEnum.SAVES);
                }
            }
        }
        currentShotType = ShotType.NONE;
    }

    protected CollisionMath.Bounds goalieHitboxOverride(int numSel, CollisionMath.Bounds rect) {
        if (numSel < 1 || numSel > players.length || c.GOALIE_DISABLED) {
            return rect;
        }
        Titan t = players[numSel - 1];
        if (t.getType() == TitanType.GOALIE || numSel == 1 || numSel == 2) {
            double w = c.GOALIE_INTERCEPT_W;
            double h = c.GOALIE_INTERCEPT_H;
            if (effectPool.hasEffect(t, EffectId.BLOCK)) {
                w *= 1.5;
                h *= 1.5;
            }
            double xOffset = (t.width - w) / 2.0;
            double yOffset = (t.height - h) / 2.0;
            return new CollisionMath.Bounds(
                    (int) t.X + xOffset,
                    (int) t.Y + yOffset,
                    w,
                    h
            );
        }
        return rect;
    }

    protected Titan getAnyBallMover() {
        for (Titan t : players) {
            if (t.actionState == Titan.TitanState.LOB ||
                    t.actionState == Titan.TitanState.SHOOT ||
                    t.actionState == Titan.TitanState.CURVE_LEFT ||
                    t.actionState == Titan.TitanState.CURVE_RIGHT) {
                return t;
            }
        }
        return null;
    }

    public void checkAndExecuteQueuedShot(Titan t) {
        if (t.queuedBtn != 0) {
            if (t.possession == 1 && t.actionState == Titan.TitanState.IDLE && !effectPool.isStunned(t)) {
                int btn = t.queuedBtn;
                int cx = t.queuedClickX;
                int cy = t.queuedClickY;
                int camX = t.queuedCamX;
                int camY = t.queuedCamY;
                t.queuedBtn = 0;
                serverMouseRoutine(t, cx, cy, btn, camX, camY);
            } else if (t.possession != 1) {
                t.queuedBtn = 0;
            }
        }
    }

    public void serverMouseRoutine(Titan t, int clickX, int clickY, int btn, int camX, int camY) {
        int priorPossession = t.possession;
        intersectAll(); //Update state of variables doubleclick fails
        // Only allow a throw if the titan already held the ball before intersectAll() ran.
        // This prevents a held-down shot button from auto-firing the instant the ball is caught.
        if (t.possession == 1 && priorPossession == 1) {
            if (t.actionState == Titan.TitanState.IDLE && !effectPool.isStunned(t)) {
                t.queuedBtn = 0;
                if (phase == GamePhase.INGAME && btn == 1) {
                    t.actionState = Titan.TitanState.SHOOT;
                } else if (phase == GamePhase.INGAME && btn == 3) {
                    t.actionState = Titan.TitanState.LOB;
                } else if (phase == GamePhase.INGAME && btn == 4) {
                    if(!effectPool.hasEffect(t, EffectId.COOLDOWN_CURVE)){
                        t.actionState = Titan.TitanState.CURVE_LEFT;
                        effectPool.addUniqueEffect(new CooldownCurve((int) (t.cooldownFactor * 5000), t), this);
                    }
                } else if (phase == GamePhase.INGAME && btn == 5) {
                    if(!effectPool.hasEffect(t, EffectId.COOLDOWN_CURVE)){
                        t.actionState = Titan.TitanState.CURVE_RIGHT;
                        effectPool.addUniqueEffect(new CooldownCurve((int) (t.cooldownFactor * 5000), t), this);
                    }
                }
                int xClick = (int) ((clickX - ball.X) + camX - ball.centerDist); //mid sprite, plus account for locations
                int yClick = (int) (-1 * ((clickY - ball.Y) + camY - ball.centerDist)); //same, plus flip Y axis for coordinate plane
                double angle = Util.degreesFromCoords(xClick, yClick);
                xKickPow = Math.cos(Math.toRadians(angle)) / 4.0;
                yKickPow = Math.sin(Math.toRadians(angle)) / 4.0;
                currentShotType = predictShotTypeFromRay(ball.X + ball.width / 2.0, ball.Y + ball.height / 2.0, xKickPow, -yKickPow, t.team);
            } else {
                // Titan holds ball but is currently in cast lag / animation / stunned.
                // Queue the shot/lob to fire immediately when cast lag finishes.
                t.queuedBtn = btn;
                t.queuedClickX = clickX;
                t.queuedClickY = clickY;
                t.queuedCamX = camX;
                t.queuedCamY = camY;
            }
        }
    }

    protected void aiTactics(int pIndex, int minX, int maxX, int minY, int maxY) {
        Optional<Titan> tip = this.titanInPossession();
        //PlayerDivider client = clientFromIndex(pIndex);
        if (pIndex < players.length) {
            Titan aiFor = players[pIndex]; //no sub1
            if (!anyClientSelected(pIndex + 1)) {
                if (aiFor.X + 35 > (ball.X + ball.centerDist) && aiFor.X > minX) {
                    aiFor.inactiveDir = 2;
                    runLeftAI(aiFor);
                }
                if (aiFor.X + 35 < (ball.X + ball.centerDist) && aiFor.X < maxX) {
                    aiFor.inactiveDir = 1;
                    runRightAI(aiFor);
                }
                if (tip.isPresent() && tip.get().team != aiFor.team) {
                    if (aiFor.Y + 35 > (ball.Y + ball.centerDist) && aiFor.Y > minY) {
                        runUpAI(aiFor);
                    }
                    if (aiFor.Y + 35 < (ball.Y + ball.centerDist) && aiFor.Y < maxY) {
                        runDownAI(aiFor);
                    }
                }// Bot intersection control with ball and passing ball in case of automatic control
                if (!tip.isPresent()) {
                    CollisionMath.Bounds ballTangle = new CollisionMath.Bounds((int) ball.X, (int) ball.Y, ball.width, ball.height);
                    CollisionMath.Bounds playertangle = new CollisionMath.Bounds((int) players[pIndex].X + SPRITE_X_EMPTY / 2.0, (int) players[pIndex].Y + SPRITE_Y_EMPTY / 2.0,
                            players[pIndex].width - SPRITE_X_EMPTY, players[pIndex].height - SPRITE_Y_EMPTY);
                    if (ballTangle.intersects(playertangle)) {
                        Titan aiT = players[pIndex];
                        aiT.possession = 1;
                        lastPossessed = aiT.id;
                        if (aiT.team == TeamAffiliation.HOME) {
                            lastHomePossessor = aiT.id;
                            home.hasBall = true;
                            away.hasBall = false;
                        } else if (aiT.team == TeamAffiliation.AWAY) {
                            lastAwayPossessor = aiT.id;
                            away.hasBall = true;
                            home.hasBall = false;
                        }
                        registerBallTouch(aiT);
                        aiT.inactiveDir = 0;
                        aiT.runningFrame = 0;
                        aiT.runningFrameCounter = 0;
                        aiT.actionState = Titan.TitanState.IDLE;
                        aiT.actionFrame = 0;
                    }
                }
            }
        }
    }

    protected PlayerDivider clientFromIndex(int pIndex) {
        for (PlayerDivider p : clients) {
            if (p.possibleSelection.contains(pIndex)) {
                return p;
            }
        }
        return null;
    }

    public void yourPlayerTactics() {
        runCoopVsAiEngine();
    }

    protected void runCoopVsAiEngine() {
        long nowMs = (long) framesSinceStart * GAMETICK_MS;
        for (int i = 0; i < players.length; i++) {
            Titan t = players[i];
            if (t == null || effectPool.hasEffect(t, EffectId.DEAD)) continue;
            if (!anyClientSelected(i + 1)) {
                tickAiTitan(t, nowMs);
            }
        }
    }

    public boolean isTitanVisibleTo(Titan observer, Titan target) {
        if (observer == null || target == null) return false;
        if (observer.id != null && observer.id.equals(target.id)) return true;
        if (c != null && c.AI_OMNISCIENCE_ENABLED) return true;
        if (effectPool != null && effectPool.hasEffect(observer, EffectId.BLIND)) return false;
        if (observer.team != target.team && effectPool != null
                && effectPool.hasEffect(target, EffectId.STEALTHED)
                && !effectPool.hasEffect(target, EffectId.FLARE)) {
            return false;
        }
        return true;
    }

    public void resetAiReactionAfterPossession(Titan t) {
        if (t == null) return;
        boolean isGoalie = (t.getType() == TitanType.GOALIE);
        double goalieRatio = (c != null) ? c.AI_GOALIE_REACTION_RATIO : 0.70;
        int minDelay = (options != null) ? options.getAiReactionTimeMinMs(isGoalie, goalieRatio) : (isGoalie ? (int) Math.round(1200 * goalieRatio) : 1200);
        int maxDelay = (options != null) ? options.getAiReactionTimeMaxMs(isGoalie, goalieRatio) : (isGoalie ? (int) Math.round(1700 * goalieRatio) : 1700);
        t.aiLastDecisionTimeMs = (long) framesSinceStart * GAMETICK_MS;
        t.aiReactionDelayMs = minDelay + (long)(Math.random() * (maxDelay - minDelay + 1));
        t.aiTargetAction = 0;
        t.aiStealTargetStartMs = 0;

        // Catch-and-shoot one-timer in attacking third
        if (!isGoalie) {
            double currentCX = t.X + t.width / 2.0;
            boolean inAttackingThird = (t.team == TeamAffiliation.HOME)
                    ? currentCX > (FIELD_LENGTH * 2.0 / 3.0)
                    : currentCX < (FIELD_LENGTH / 3.0);
            if (inAttackingThird) {
                TeamAffiliation enemyTeam = (t.team == TeamAffiliation.HOME) ? TeamAffiliation.AWAY : TeamAffiliation.HOME;
                GoalHoop openGoal = findUnblockedGoal(t, enemyTeam);
                if (openGoal != null) {
                    t.aiReactionDelayMs = 0; // Immediate one-timer execution
                }
            }
        }
    }

    public void updateAiBoostDecision(Titan ai) {
        if (ai.fuel <= 0) {
            ai.isBoosting = false;
            return;
        }
        if (ai.possession == 1 && ai.getType() != TitanType.DASHER) {
            ai.isBoosting = false;
            return;
        }
        if (ai.aiTargetAction == 10) { // 10 = TRANSITION_BOOST
            return;
        }
        if (ai.aiTargetX >= 0 && ai.aiTargetY >= 0) {
            double currentCenterX = ai.X + ai.width / 2.0;
            double currentCenterY = ai.Y + ai.height / 2.0;
            double dist = Math.hypot(ai.aiTargetX - currentCenterX, ai.aiTargetY - currentCenterY);
            double boostThreshold = isGoalie(ai) ? 3.0 : 100.0;
            ai.isBoosting = (dist > boostThreshold);
        } else {
            ai.isBoosting = false;
        }
    }

    protected void tickAiTitan(Titan ai, long nowMs) {
        if (effectPool.isRooted(ai) || effectPool.isStunned(ai)) return;
        if (c != null && !c.AI_OMNISCIENCE_ENABLED && effectPool.hasEffect(ai, EffectId.BLIND)) {
            ai.isBoosting = false;
        }

        // 1. STEAL PRIORITY: Governed by 70% anticipation reaction timer (experiential timing feel)
        Optional<Titan> possessorOpt = titanInPossession();
        if (possessorOpt.isPresent() && possessorOpt.get().team != ai.team && ai.possession == 0 && ballVisible) {
            Titan tip = possessorOpt.get();
            if (isTitanVisibleTo(ai, tip) && isWithinStealRange(ai, tip)) {
                if (ai.aiStealTargetStartMs == 0) {
                    ai.aiStealTargetStartMs = nowMs;
                    double goalieRatio = (c != null) ? c.AI_GOALIE_REACTION_RATIO : 0.70;
                    int stealMin = (options != null) ? options.getAiReactionTimeMinMs(true, goalieRatio) : (int) Math.round(1200 * goalieRatio);
                    int stealMax = (options != null) ? options.getAiReactionTimeMaxMs(true, goalieRatio) : (int) Math.round(1700 * goalieRatio);
                    ai.aiStealReactionDelayMs = stealMin + (long)(Math.random() * (stealMax - stealMin + 1));
                }
                if (nowMs - ai.aiStealTargetStartMs >= ai.aiStealReactionDelayMs) {
                    executeAiSteal(ai);
                    ai.aiStealTargetStartMs = 0;
                    if (ai.possession == 1 || ai.actionState == Titan.TitanState.STEAL) {
                        return;
                    }
                }
            } else {
                ai.aiStealTargetStartMs = 0;
            }
        } else {
            ai.aiStealTargetStartMs = 0;
        }

        boolean isGoalie = (ai.getType() == TitanType.GOALIE);
        if (isGoalie) {
            tickAiGoalieMinionFarming(ai);
        }
        double goalieRatio = (c != null) ? c.AI_GOALIE_REACTION_RATIO : 0.70;
        int minDelay = (options != null) ? options.getAiReactionTimeMinMs(isGoalie, goalieRatio) : (isGoalie ? (int) Math.round(1200 * goalieRatio) : 1200);
        int maxDelay = (options != null) ? options.getAiReactionTimeMaxMs(isGoalie, goalieRatio) : (isGoalie ? (int) Math.round(1700 * goalieRatio) : 1700);

        // 2. Abilities check (Independent of movement reaction timer - does not reset decision timer)
        tryUseAbilities(ai);
        if (ai.actionState != Titan.TitanState.IDLE) {
            return;
        }

        // 3. Movement target re-evaluation strictly governed by difficulty reaction delay timer.
        if (ai.aiReactionDelayMs == 0 || (nowMs - ai.aiLastDecisionTimeMs >= ai.aiReactionDelayMs)) {
            evaluateAiDecision(ai);
            ai.aiLastDecisionTimeMs = nowMs;
            ai.aiReactionDelayMs = minDelay + (long)(Math.random() * (maxDelay - minDelay + 1));
        }

        // 3. Apply movement to target destination
        if (ai.aiTargetX >= 0 && ai.aiTargetY >= 0) {
            double currentCenterX = ai.X + ai.width / 2.0;
            double currentCenterY = ai.Y + ai.height / 2.0;
            double dx = ai.aiTargetX - currentCenterX;
            double dy = ai.aiTargetY - currentCenterY;
            double distToTarget = Math.hypot(dx, dy);

            // Safety shutoff: If fuel is depleted or within close proximity of target, stop boosting
            double arrivalDist = isGoalie(ai) ? 5.0 : 30.0;
            if (ai.fuel <= 0 || distToTarget <= arrivalDist) {
                if (ai.aiTargetAction != 10) { // 10 = TRANSITION_BOOST
                    ai.isBoosting = false;
                }
            } else if (isGoalie(ai) && ai.possession == 0 && ai.fuel > 0) {
                ai.isBoosting = true;
            }

            if (isGoalie(ai) && ai.possession == 1) {
                // Goalie holding ball should hold ground in crease and not march downfield towards pass target
                ai.programmed = false;
                ai.invalidatePath();
            } else {
                ai.programmed = true;
                ai.marchingOrderX = (int) ai.aiTargetX;
                ai.marchingOrderY = (int) ai.aiTargetY;
            }

            // Track horizontal progress when attempting forward horizontal movement
            double forwardDir = (ai.team == TeamAffiliation.HOME) ? 1.0 : -1.0;
            if (dx * forwardDir > 15.0) {
                if (ai.aiPrevX >= 0 && Math.abs(ai.X - ai.aiPrevX) < 0.25) {
                    ai.aiStuckHorizontalTicks++;
                } else {
                    ai.aiStuckHorizontalTicks = Math.max(0, ai.aiStuckHorizontalTicks - 1);
                }
                ai.aiPrevX = ai.X;
            } else {
                ai.aiStuckHorizontalTicks = 0;
                ai.aiPrevX = ai.X;
            }

            // Pathfind around enemies instead of purely through: mix in diagonal clicks if no horizontal progress happens
            // Priorities when stuck: 2) Find backpass option, 3) Run backwards diagonally to create space
            // NOTE: Goalies must NEVER evade backwards into their own net!
            if (ai.aiStuckHorizontalTicks >= 3 && ai.possession == 1 && !isGoalie(ai)) {
                TeamAffiliation enemyTeam = (ai.team == TeamAffiliation.HOME) ? TeamAffiliation.AWAY : TeamAffiliation.HOME;
                Titan passTarget = findBackwardsOrVerticalPassTarget(ai, enemyTeam);
                if (passTarget != null) {
                    ai.aiTargetX = passTarget.X + passTarget.width / 2.0;
                    ai.aiTargetY = passTarget.Y + passTarget.height / 2.0;
                    ai.aiTargetAction = 1;
                    executeAiShot(ai, ai.aiTargetX, ai.aiTargetY);
                    ai.aiTargetAction = 0;
                } else {
                    Titan impeding = getHorizontalImpedingEnemy(ai, enemyTeam);
                    double[] backTarget = calculateBackwardDiagonalEvadeTarget(ai, impeding, currentCenterX, currentCenterY);
                    ai.marchingOrderX = (int) backTarget[0];
                    ai.marchingOrderY = (int) backTarget[1];
                    ai.aiTargetX = backTarget[0];
                    ai.aiTargetY = backTarget[1];
                }
            }
        }

        // 4. Execute queued actions
        if ((ai.aiTargetAction == 1 || ai.aiTargetAction == 3) && ai.possession == 1) { // SHOOT / PASS / LOB
            executeAiShot(ai, ai.aiTargetX, ai.aiTargetY, ai.aiTargetAction);
            ai.aiTargetAction = 0;
        } else if (ai.aiTargetAction == 2 && ai.possession == 0) { // STEAL
            executeAiSteal(ai);
            ai.aiTargetAction = 0;
        }
    }

    public void evaluateAiDecision(Titan ai) {
        if (ai.getType() == TitanType.GOALIE) {
            evaluateGoalieDecision(ai);
            updateAiBoostDecision(ai);
            return;
        }

        if (c != null && !c.AI_OMNISCIENCE_ENABLED && effectPool != null && effectPool.hasEffect(ai, EffectId.BLIND)) {
            ai.isBoosting = false;
            ai.aiTargetAction = 0;
            return;
        }


        TeamAffiliation myTeam = ai.team;
        TeamAffiliation enemyTeam = (myTeam == TeamAffiliation.HOME) ? TeamAffiliation.AWAY : TeamAffiliation.HOME;

        Optional<Titan> possessorOpt = titanInPossession();
        boolean looseBall = !possessorOpt.isPresent();
        Titan possessor = possessorOpt.orElse(null);

        double ballCenterX = ball.X + ball.width / 2.0;
        double ballCenterY = ball.Y + ball.height / 2.0;

        // Rule 3: Neutral state / Loose ball or thrown ball
        if (looseBall) {
            boolean ballInMotion = anyBallMoveState();
            double targetBallX;
            double targetBallY;
            if (ballInMotion) {
                targetBallX = Math.max(c.MIN_X, Math.min(c.MAX_X, ball.X + xKickPow * 15));
                targetBallY = Math.max(c.MIN_Y, Math.min(c.MAX_Y, ball.Y + yKickPow * 15));
            } else {
                targetBallX = ballCenterX;
                targetBallY = ballCenterY;
            }

            if (isTeammateCloserToLooseBall(ai, targetBallX, targetBallY)) {
                evaluateLooseBallAttackingPosture(ai, targetBallX, targetBallY);
                return;
            }

            ai.aiTargetX = targetBallX;
            ai.aiTargetY = targetBallY;
            ai.aiTargetAction = 0;
            updateAiBoostDecision(ai);
            return;
        }

        // If AI has the ball
        if (possessor != null && possessor.id.equals(ai.id)) {
            evaluateOnBallOffense(ai, enemyTeam);
            updateAiBoostDecision(ai);
            return;
        }

        // If teammate has the ball
        if (possessor != null && possessor.team == myTeam) {
            evaluateOffBallOffense(ai, possessor, enemyTeam);
            updateAiBoostDecision(ai);
            return;
        }

        // If enemy has the ball
        if (possessor != null && possessor.team == enemyTeam) {
            evaluateDefense(ai, possessor, myTeam, enemyTeam);
            updateAiBoostDecision(ai);
            return;
        }
    }

    protected void tryUseAbilities(Titan ai) {
        if (ai.actionState != Titan.TitanState.IDLE || effectPool.isStunned(ai)) return;
        if (c != null && !c.AI_OMNISCIENCE_ENABLED && effectPool != null && effectPool.hasEffect(ai, EffectId.BLIND)) return;

        // Do not use abilities if in steal range of enemy ball carrier - steal has absolute priority
        Optional<Titan> possessorOpt = titanInPossession();
        if (possessorOpt.isPresent() && possessorOpt.get().team != ai.team && ai.possession == 0 && ballVisible) {
            if (isWithinStealRange(ai, possessorOpt.get())) {
                return;
            }
        }

        // Try Q
        if (!effectPool.hasEffect(ai, EffectId.COOLDOWN_Q)) {
            if (hasTargetForAbility(ai, true)) {
                try {
                    boolean castSuccess = ability.castQ(this, ai);
                    if (castSuccess) {
                        ai.actionState = Titan.TitanState.A1;
                        ai.actionFrame = 0;
                        ai.pushMove();
                        effectPool.addUniqueEffect(new EmptyEffect(ai.eCastFrames * GAMETICK_MS, ai, EffectId.CAST_LAG), this);
                        return;
                    }
                } catch (Exception ignored) {}
            }
        }

        // Try W
        if (ai.actionState == Titan.TitanState.IDLE && !effectPool.hasEffect(ai, EffectId.COOLDOWN_W)) {
            if (hasTargetForAbility(ai, false)) {
                try {
                    boolean castSuccess = ability.castW(this, ai);
                    if (castSuccess) {
                        ai.actionState = Titan.TitanState.A2;
                        ai.actionFrame = 0;
                        ai.pushMove();
                        effectPool.addUniqueEffect(new EmptyEffect(ai.rCastFrames * GAMETICK_MS, ai, EffectId.CAST_LAG), this);
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    protected boolean hasTargetForAbility(Titan ai, boolean isQ) {
        TitanType type = ai.getType();
        if (type == null) return false;
        if (c != null && !c.AI_OMNISCIENCE_ENABLED && effectPool != null && effectPool.hasEffect(ai, EffectId.BLIND)) return false;

        TeamAffiliation enemyTeam = (ai.team == TeamAffiliation.HOME) ? TeamAffiliation.AWAY : TeamAffiliation.HOME;
        Titan nearestEnemy = findNearestEnemy(ai, enemyTeam);
        double enemyDist = (nearestEnemy != null) ? Math.hypot(nearestEnemy.X - ai.X, nearestEnemy.Y - ai.Y) : Double.MAX_VALUE;

        double rf = ai.rangeFactor;

        if (isQ) {
            switch (type) {
                case WARRIOR: return enemyDist <= (c.getI("titan.slash.range") / 2.0) * rf;
                case RANGER: return enemyDist <= c.getI("titan.arrow.range") * rf;
                case MAGE: return enemyDist <= c.getI("titan.portal.range") * rf;
                case BUILDER: return enemyDist <= c.getI("titan.trap.range") * rf;
                case MARKSMAN: return enemyDist <= c.getI("titan.slow.range") * rf;
                case ARTISAN: return ai.possession == 0 && Math.hypot(ball.X - ai.X, ball.Y - ai.Y) <= (c.getI("titan.suck.range") / 2.0) * rf;
                case SUPPORT: return enemyDist <= (c.getI("titan.stun.range") / 2.0) * rf;
                case GOLEM: return enemyDist <= 200.0 || ai.getHealth() < ai.maxHealth * 0.7;
                case STEALTH: return enemyDist <= 300.0;
                case DASHER: return ai.possession == 1;
                case HOUNDMASTER: return enemyDist <= c.getI("titan.cage.range") * rf;
                case GRENADIER: return enemyDist <= (c.getI("titan.flashbang.range") / 2.0) * rf;
                case CAPTAIN: return ai.ammo > 0 && enemyDist <= c.getI("titan.captain.shot.range") * rf;
                case SPIDER: return enemyDist <= c.getI("titan.spider.web.range") * rf;
                case GOALIE: return ai.possession == 0 && (activeLobThrower != null || Math.hypot(ball.X - ai.X, ball.Y - ai.Y) <= 300.0);
                default: return false;
            }
        } else {
            switch (type) {
                case WARRIOR: {
                    double maxDist = c.getI("titan.flash.warrior.dist") * rf;
                    return enemyDist > 80.0 && enemyDist <= maxDist;
                }
                case RANGER: return enemyDist <= (c.getI("titan.kick.range") / 2.0) * rf;
                case MAGE: return enemyDist <= c.getI("titan.ignite.range") * rf;
                case BUILDER: return enemyDist <= c.getI("titan.wall.range") * rf;
                case MARKSMAN: return enemyDist <= 400.0;
                case ARTISAN: return true;
                case SUPPORT: {
                    double healRange = c.getI("titan.heal.range") * rf;
                    for (Titan ally : players) {
                        if (ally != null && ally.team == ai.team && !effectPool.hasEffect(ally, EffectId.DEAD)) {
                            if (ally.getHealth() < ally.maxHealth && Math.hypot(ally.X - ai.X, ally.Y - ai.Y) <= healRange) {
                                return true;
                            }
                        }
                    }
                    return false;
                }
                case GOLEM: return enemyDist <= (c.getI("titan.scatter.range") / 2.0) * rf;
                case STEALTH: {
                    double maxDist = c.getI("titan.flash.stealth.dist") * rf;
                    return enemyDist > 80.0 && enemyDist <= maxDist;
                }
                case DASHER: return enemyDist <= c.getI("titan.ignite.range") * rf;
                case HOUNDMASTER: {
                    for (Entity e : entityPool) {
                        if (e instanceof gameserver.entity.minions.Cage && ((gameserver.entity.minions.Cage) e).getCreatedById().equals(ai.id)) {
                            return true;
                        }
                    }
                    return false;
                }
                case GRENADIER: return enemyDist <= c.getI("titan.molotov.range") * rf;
                case CAPTAIN: return enemyDist <= c.getI("titan.captain.slide.range") * rf;
                case SPIDER: return enemyDist <= c.getI("titan.spider.cocoon.range") * rf;
                case GOALIE: {
                    if (ai.possession != 0) return false;
                    if (ai.aiTargetX < 0 || ai.aiTargetY < 0) return false;
                    GoalHoop centerHoop = (ai.team == TeamAffiliation.HOME) ? homeHiGoal : awayHiGoal;
                    double hoopRadius = (centerHoop != null) ? Math.max(centerHoop.w, centerHoop.h) / 2.0 : (c.getI("goal.hi.width") / 2.0);
                    double aiCenterX = ai.X + ai.width / 2.0;
                    double aiCenterY = ai.Y + ai.height / 2.0;
                    double dist = Math.hypot(ai.aiTargetX - aiCenterX, ai.aiTargetY - aiCenterY);
                    double maxSlideDist = c.getI("titan.goalie.slide.dist") * ai.rangeFactor;
                    return dist > hoopRadius && dist <= maxSlideDist;
                }
                default: return false;
            }
        }
    }

    public void tickAiGoalieMinionFarming(Titan ai) {
        if (effectPool.hasEffect(ai, EffectId.COOLDOWN_GOALIE)) {
            return;
        }
        if (c != null && !c.AI_OMNISCIENCE_ENABLED && effectPool != null && effectPool.hasEffect(ai, EffectId.BLIND)) {
            return;
        }

        // The goalie clicks to lasthit and push whenever his team has the ball
        Optional<Titan> possessorOpt = titanInPossession();
        if (!possessorOpt.isPresent() || possessorOpt.get().team != ai.team) {
            return;
        }

        double gx = ai.X + ai.width / 2.0;
        double gy = ai.Y + ai.height / 2.0;
        double rangeX = c.getI("titan.goalie.rangex") * ai.rangeFactor;
        double rangeY = c.getI("titan.goalie.rangey") * ai.rangeFactor;

        double baseDmg = (c != null && c.hasKey("goalie.click.damage")) ? c.getD("goalie.click.damage") : 5.0;
        GuardianAbilities ga = (ai.team == TeamAffiliation.HOME) ? homeGoalieAbilities : awayGoalieAbilities;
        Set<String> purchased = (ai.team == TeamAffiliation.HOME) ? homeGoaliePurchasedUpgrades : awayGoaliePurchasedUpgrades;

        Entity bestTarget = null;
        int bestTier = 999;
        double bestScore = Double.MAX_VALUE;

        for (Entity e : entityPool) {
            if (e.getHealth() <= 0.0) continue;

            if (e instanceof gameserver.entity.minions.Dragon d) {
                double dx = (d.X + d.width / 2.0) - gx;
                double dy = (d.Y + d.height / 2.0) - gy;
                if ((dx * dx) / (rangeX * rangeX) + (dy * dy) / (rangeY * rangeY) <= 1.0) {
                    if (d.getHealth() <= baseDmg) {
                        bestTarget = d;
                        bestTier = 0;
                        break;
                    }
                }
                continue;
            }

            if (e instanceof LaneMinion m && m.team != ai.team) {
                double dx = m.X - gx;
                double dy = m.Y - gy;
                if ((dx * dx) / (rangeX * rangeX) + (dy * dy) / (rangeY * rangeY) <= 1.0) {
                    double effectiveDmg = baseDmg;
                    if (purchased != null && purchased.contains("siege.t3.rushlane") && ga != null && ga.airSupportLane == m.laneIndex) {
                        effectiveDmg *= 1.40;
                    }

                    int tier;
                    double score;
                    if (m.getHealth() <= effectiveDmg) {
                        // Tier 1: Guaranteed Last Hit!
                        tier = 1;
                        score = m.getHealth(); // lowest health first
                    } else if (m.getHealth() <= effectiveDmg * 2.0) {
                        // Tier 2: Setup Kill
                        tier = 2;
                        score = m.getHealth();
                    } else {
                        // Tier 3: Wave Push
                        tier = 3;
                        score = Math.abs(m.X - gx); // frontmost enemy minion in lane
                    }

                    if (tier < bestTier || (tier == bestTier && score < bestScore)) {
                        bestTier = tier;
                        bestScore = score;
                        bestTarget = m;
                    }
                }
            }
        }

        if (bestTarget != null) {
            handleGoalieAttackClick("", bestTarget.X, bestTarget.Y, ai.team, ai);
        }
    }

    public void evaluateGoalieDecision(Titan ai) {
        tickAiGoalieBuildOrder(ai);

        GoalHoop myGoal = (ai.team == TeamAffiliation.HOME) ? homeHiGoal : awayHiGoal;
        double hoopCX = myGoal.x + myGoal.w / 2.0;
        double hoopCY = myGoal.y + myGoal.h / 2.0;
        double rx = myGoal.w / 2.0;
        double ry = myGoal.h / 2.0;

        int YMAX = c.GOALIE_Y_MAX;
        int YMIN = c.GOALIE_Y_MIN;
        int XMAX = (ai.team == TeamAffiliation.AWAY ? c.GOALIE_XA_MAX : c.GOALIE_XH_MAX);
        int XMIN = (ai.team == TeamAffiliation.AWAY ? c.GOALIE_XA_MIN : c.GOALIE_XH_MIN);

        double minCenterX = XMIN + ai.width / 2.0;
        double maxCenterX = XMAX + ai.width / 2.0;
        double minCenterY = YMIN + ai.height / 2.0;
        double maxCenterY = YMAX + ai.height / 2.0;

        if (c != null && !c.AI_OMNISCIENCE_ENABLED && effectPool != null && effectPool.hasEffect(ai, EffectId.BLIND)) {
            double holdX = hoopCX + (ai.team == TeamAffiliation.HOME ? rx : -rx);
            ai.aiTargetX = Math.max(minCenterX, Math.min(maxCenterX, holdX));
            ai.aiTargetY = Math.max(minCenterY, Math.min(maxCenterY, hoopCY));
            ai.aiTargetAction = 0;
            return;
        }

        if (ai.possession == 1) {
            Titan openTeammate = findBestPassTarget(ai);
            if (openTeammate != null) {
                ai.aiTargetX = openTeammate.X + openTeammate.width / 2.0;
                ai.aiTargetY = openTeammate.Y + openTeammate.height / 2.0;
                ai.aiTargetAction = 1;
            } else {
                double clearX = (ai.team == TeamAffiliation.HOME) ? hoopCX + 400 : hoopCX - 400;
                double clearY = hoopCY;
                if (isDestinationCloserToFriendlyThanEnemy(ai, clearX, clearY)) {
                    ai.aiTargetX = clearX;
                    ai.aiTargetY = clearY;
                    ai.aiTargetAction = 1;
                } else {
                    double holdX = hoopCX + (ai.team == TeamAffiliation.HOME ? rx : -rx);
                    ai.aiTargetX = Math.max(minCenterX, Math.min(maxCenterX, holdX));
                    ai.aiTargetY = Math.max(minCenterY, Math.min(maxCenterY, hoopCY));
                    ai.aiTargetAction = 0;
                }
            }
            return;
        }

        double ballCX = ball.X + ball.width / 2.0;
        double ballCY = ball.Y + ball.height / 2.0;

        // The center goal hoop is a planar circle shootable/dunkable from any 360-degree angle.
        // The goalie stands straddling the edge of the hoop nearest the ball at all times.
        double vx = ballCX - hoopCX;
        double vy = ballCY - hoopCY;
        double len = Math.hypot(vx, vy);

        double ux, uy;
        if (len < 0.001) {
            ux = (ai.team == TeamAffiliation.HOME ? 1.0 : -1.0);
            uy = 0.0;
        } else {
            ux = vx / len;
            uy = vy / len;
        }

        double invDenom = Math.hypot(ux / rx, uy / ry);
        double k = (invDenom > 0.0001) ? (1.0 / invDenom) : rx;

        double edgeX = hoopCX + k * ux;
        double edgeY = hoopCY + k * uy;

        ai.aiTargetX = Math.max(minCenterX, Math.min(maxCenterX, edgeX));
        ai.aiTargetY = Math.max(minCenterY, Math.min(maxCenterY, edgeY));
        ai.aiTargetAction = 0;
    }

    protected void evaluateDefense(Titan ai, Titan possessor, TeamAffiliation myTeam, TeamAffiliation enemyTeam) {
        boolean carrierVisible = isTitanVisibleTo(ai, possessor);
        double aiCX = ai.X + ai.width / 2.0;
        double aiCY = ai.Y + ai.height / 2.0;

        double pCX, pCY;
        if (!carrierVisible) {
            if (ballVisible) {
                pCX = ball.X + ball.width / 2.0;
                pCY = ball.Y + ball.height / 2.0;
            } else {
                // Completely invisible (carrier stealthed + ball hidden): fall back to guarding hoop
                GoalHoop defendingGoal = (myTeam == TeamAffiliation.HOME) ? homeHiGoal : awayHiGoal;
                ai.aiTargetX = defendingGoal.x + defendingGoal.w / 2.0;
                ai.aiTargetY = defendingGoal.y + defendingGoal.h / 2.0;
                ai.aiTargetAction = 0;
                ai.aiDefenseMode = 0;
                return;
            }
        } else {
            pCX = possessor.X + possessor.width / 2.0;
            pCY = possessor.Y + possessor.height / 2.0;
        }
        double dist = Math.hypot(pCX - aiCX, pCY - aiCY);

        double shortRange = Math.max(25.0, (ai.stealRad * ai.rangeFactor) * 0.5);
        double maxRange = 100.0;

        double stealProb;
        if (!carrierVisible) {
            stealProb = 0.0;
        } else if (dist <= shortRange) {
            stealProb = 1.0;
        } else if (dist >= maxRange) {
            stealProb = 0.0;
        } else {
            stealProb = (maxRange - dist) / (maxRange - shortRange);
        }

        boolean chooseSteal = Math.random() < stealProb;

        // Determine primary on-ball defender: closest living non-goalie teammate to the ball carrier
        Titan primaryOnBallDefender = null;
        double minDistanceToBallCarrier = Double.MAX_VALUE;
        for (Titan t : players) {
            if (t != null && t.team == ai.team && t.getType() != TitanType.GOALIE && !effectPool.hasEffect(t, EffectId.DEAD)) {
                double d = Math.hypot(t.X - possessor.X, t.Y - possessor.Y);
                if (d < minDistanceToBallCarrier) {
                    minDistanceToBallCarrier = d;
                    primaryOnBallDefender = t;
                }
            }
        }

        if (ai == primaryOnBallDefender || primaryOnBallDefender == null) {
            // Primary defender: on-ball defense (choose between STEAL and BLOCK)
            ai.aiDefenseMode = chooseSteal ? 1 : 0;
            if (chooseSteal) {
                ai.aiTargetX = pCX;
                ai.aiTargetY = pCY;
                ai.aiTargetAction = 0;
            } else {
                // BLOCK on-ball defense: position between carrier and our defending goal hoop
                GoalHoop defendingGoal = (myTeam == TeamAffiliation.HOME) ? homeHiGoal : awayHiGoal;
                double hoopCX = defendingGoal.x + defendingGoal.w / 2.0;
                double hoopCY = defendingGoal.y + defendingGoal.h / 2.0;

                double vx = hoopCX - pCX;
                double vy = hoopCY - pCY;
                double vLen = Math.hypot(vx, vy);
                if (vLen > 0.001) {
                    double ux = vx / vLen;
                    double uy = vy / vLen;
                    double blockDist = Math.min(dist * 0.5, 120.0);
                    ai.aiTargetX = Math.max(c.MIN_X, Math.min(c.MAX_X, pCX + ux * blockDist));
                    ai.aiTargetY = Math.max(c.MIN_Y, Math.min(c.MAX_Y, pCY + uy * blockDist));
                } else {
                    ai.aiTargetX = pCX;
                    ai.aiTargetY = pCY;
                }
                ai.aiTargetAction = 0;
            }
        } else {
            // Secondary defender: front the pass to the other enemy field titan (deny passing lane)
            ai.aiDefenseMode = 0;

            List<Titan> otherTeammates = new ArrayList<>();
            for (Titan t : players) {
                if (t != null && t.team == ai.team && t != primaryOnBallDefender && t.getType() != TitanType.GOALIE && !effectPool.hasEffect(t, EffectId.DEAD)) {
                    otherTeammates.add(t);
                }
            }
            List<Titan> otherEnemies = new ArrayList<>();
            for (Titan t : players) {
                if (t != null && t.team == enemyTeam && t != possessor && t.getType() != TitanType.GOALIE && !effectPool.hasEffect(t, EffectId.DEAD)) {
                    if (c != null && !c.AI_OMNISCIENCE_ENABLED && !isTitanVisibleTo(ai, t)) continue;
                    otherEnemies.add(t);
                }
            }

            Titan targetEnemy = otherEnemies.isEmpty() ? null : otherEnemies.get(Math.max(0, otherTeammates.indexOf(ai)) % otherEnemies.size());
            if (targetEnemy != null) {
                double eCX = targetEnemy.X + targetEnemy.width / 2.0, eCY = targetEnemy.Y + targetEnemy.height / 2.0;
                double laneX = pCX - eCX, laneY = pCY - eCY, laneDist = Math.hypot(laneX, laneY);
                double frontDist = Math.min(80.0, laneDist * 0.45);
                ai.aiTargetX = Math.max(c.MIN_X, Math.min(c.MAX_X, laneDist > 0.001 ? eCX + (laneX / laneDist) * frontDist : eCX));
                ai.aiTargetY = Math.max(c.MIN_Y, Math.min(c.MAX_Y, laneDist > 0.001 ? eCY + (laneY / laneDist) * frontDist : eCY));
            } else {
                GoalHoop defendingGoal = (myTeam == TeamAffiliation.HOME) ? homeHiGoal : awayHiGoal;
                ai.aiTargetX = Math.max(c.MIN_X, Math.min(c.MAX_X, (pCX + defendingGoal.x + defendingGoal.w / 2.0) / 2.0));
                ai.aiTargetY = Math.max(c.MIN_Y, Math.min(c.MAX_Y, (pCY + defendingGoal.y + defendingGoal.h / 2.0) / 2.0));
            }
            ai.aiTargetAction = 0;
        }
    }

    protected void evaluateOffBallOffense(Titan ai, Titan possessor, TeamAffiliation enemyTeam) {
        boolean isTransition = (ai.team == TeamAffiliation.HOME)
                ? possessor.X < FIELD_LENGTH / 2.0
                : possessor.X > FIELD_LENGTH / 2.0;

        // Gather all active off-ball outfield teammates
        List<Titan> offBallTeammates = new ArrayList<>();
        for (Titan t : players) {
            if (t != null && t.team == ai.team && !t.id.equals(possessor.id)
                    && t.getType() != TitanType.GOALIE && !effectPool.hasEffect(t, EffectId.DEAD)) {
                offBallTeammates.add(t);
            }
        }
        offBallTeammates.sort(Comparator.comparing(t -> t.id != null ? t.id.toString() : ""));

        int slotIndex = Math.max(0, offBallTeammates.indexOf(ai));
        int totalOffBall = Math.max(1, offBallTeammates.size());

        double carrierCX = possessor.X + possessor.width / 2.0;
        double carrierCY = possessor.Y + possessor.height / 2.0;
        double forwardDir = (ai.team == TeamAffiliation.HOME) ? 1.0 : -1.0;

        if (isTransition) {
            if (ai.fuel > 25.0) {
                ai.isBoosting = true;
                ai.aiTargetAction = 10;
            } else {
                ai.isBoosting = false;
            }
            // Stagger transition lanes so off-ball teammates don't stack on top of each other
            double verticalOffset;
            double leadDist = 200.0;
            if (slotIndex == 0) {
                verticalOffset = -220.0;
            } else if (slotIndex == 1) {
                verticalOffset = 220.0;
            } else if (slotIndex == 2) {
                verticalOffset = 0.0;
                leadDist = 320.0;
            } else {
                verticalOffset = (slotIndex % 2 == 1) ? 260.0 : -260.0;
                leadDist = -120.0;
            }
            ai.aiTargetX = Math.max(c.MIN_X + 25.0, Math.min(c.MAX_X - 25.0, carrierCX + forwardDir * leadDist));
            ai.aiTargetY = Math.max(c.MIN_Y + 25.0, Math.min(c.MAX_Y - 25.0, carrierCY + verticalOffset));
            return;
        }

        GoalHoop centerHoop = (ai.team == TeamAffiliation.HOME) ? awayHiGoal : homeHiGoal;
        double hoopCX = (centerHoop != null) ? centerHoop.x + centerHoop.w / 2.0 : (ai.team == TeamAffiliation.HOME ? 1821.0 : 291.0);
        double hoopCY = (centerHoop != null) ? centerHoop.y + centerHoop.h / 2.0 : 625.0;
        double centerY = (c.MIN_Y + c.MAX_Y) / 2.0;

        // Dynamic slot positions across perimeter horseshoe
        // Spots:
        // 0: High Slot Trailer
        // 1: Top Wing
        // 2: Deep Top Behind Hoop
        // 3: Deep Center Behind Hoop
        // 4: Deep Bottom Behind Hoop
        // 5: Bottom Wing
        // 6: Low Slot Trailer
        // 7: Point Safety
        double wingX = (ai.team == TeamAffiliation.HOME)
                ? Math.min(hoopCX - 80.0, Math.max(carrierCX + 100.0, hoopCX - 280.0))
                : Math.max(hoopCX + 80.0, Math.min(carrierCX - 100.0, hoopCX + 280.0));
        double behindHoopX = hoopCX + forwardDir * 130.0;
        double trailerX = carrierCX - forwardDir * 200.0;

        double[] spotX = new double[8];
        double[] spotY = new double[8];

        spotX[0] = trailerX;
        spotY[0] = centerY - 180.0;

        spotX[1] = wingX;
        spotY[1] = c.MIN_Y + 110.0;

        spotX[2] = behindHoopX;
        spotY[2] = hoopCY - 160.0;

        spotX[3] = behindHoopX;
        spotY[3] = hoopCY;

        spotX[4] = behindHoopX;
        spotY[4] = hoopCY + 160.0;

        spotX[5] = wingX;
        spotY[5] = c.MAX_Y - 110.0;

        spotX[6] = trailerX;
        spotY[6] = centerY + 180.0;

        spotX[7] = carrierCX - forwardDir * 350.0;
        spotY[7] = centerY;

        // Identify if the ball carrier is occupying or crowding a designated slot
        int carrierOccupiedSpot = -1;
        double minCarrierDist = Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            double dist = Math.hypot(spotX[i] - carrierCX, spotY[i] - carrierCY);
            if (dist < 220.0 && dist < minCarrierDist) {
                minCarrierDist = dist;
                carrierOccupiedSpot = i;
            }
        }

        // When the ball is not in its designated spot, dynamically shift/rotate off-ball positions
        int[] activeOrder;
        switch (carrierOccupiedSpot) {
            case 1: // Carrier at Top Wing -> off-ball rotates to Deep Top Behind Hoop (2)
                activeOrder = new int[] { 2, 5, 3, 4, 0, 6, 7 };
                break;
            case 5: // Carrier at Bottom Wing -> off-ball rotates to Deep Bottom Behind Hoop (4)
                activeOrder = new int[] { 4, 1, 3, 2, 6, 0, 7 };
                break;
            case 3: // Carrier at Deep Center Behind Hoop -> off-ball rotates to wings and flank behind-hoop (2, 4)
                activeOrder = new int[] { 1, 5, 2, 4, 0, 6, 7 };
                break;
            case 2: // Carrier at Deep Top Behind Hoop -> off-ball rotates to Top Wing & Center Behind
                activeOrder = new int[] { 1, 5, 3, 4, 0, 6, 7 };
                break;
            case 4: // Carrier at Deep Bottom Behind Hoop -> off-ball rotates to Bottom Wing & Center Behind
                activeOrder = new int[] { 1, 5, 3, 2, 0, 6, 7 };
                break;
            case 0: // Carrier at High Slot Trailer
                activeOrder = new int[] { 1, 5, 3, 2, 4, 6, 7 };
                break;
            case 6: // Carrier at Low Slot Trailer
                activeOrder = new int[] { 1, 5, 3, 2, 4, 0, 7 };
                break;
            case 7: // Carrier at Point Safety
                activeOrder = new int[] { 1, 5, 3, 2, 4, 0, 6 };
                break;
            default: // Ball in open space / center initiation
                activeOrder = new int[] { 1, 5, 3, 2, 4, 0, 6, 7 };
                break;
        }

        double targetX, targetY;
        if (slotIndex < activeOrder.length) {
            int assignedSpot = activeOrder[slotIndex];
            targetX = spotX[assignedSpot];
            targetY = spotY[assignedSpot];
        } else {
            // Roster sizes beyond 8 off-ball outfielders (e.g. 9v9+ safeties)
            int overflowIndex = slotIndex - activeOrder.length;
            targetX = carrierCX - forwardDir * (350.0 + overflowIndex * 60.0);
            targetY = centerY + (overflowIndex % 2 == 0 ? 1 : -1) * (overflowIndex * 40.0);
        }

        // Anti-clustering separation guarantee: ensure off-ball target is never within 200px of carrier
        double distToCarrier = Math.hypot(targetX - carrierCX, targetY - carrierCY);
        if (distToCarrier < 200.0 && distToCarrier > 0.001) {
            double pushX = (targetX - carrierCX) / distToCarrier;
            double pushY = (targetY - carrierCY) / distToCarrier;
            targetX = carrierCX + pushX * 220.0;
            targetY = carrierCY + pushY * 220.0;
        }

        // Clamp to valid playing pitch
        targetX = Math.max(c.MIN_X + 25.0, Math.min(c.MAX_X - 25.0, targetX));
        targetY = Math.max(c.MIN_Y + 25.0, Math.min(c.MAX_Y - 25.0, targetY));

        // Passing lane micro-adjustment: If an enemy blocks the direct pass line, shift vertically to open the window
        if (isPassPathBlocked(carrierCX, carrierCY, targetX, targetY, enemyTeam)) {
            double altY1 = Math.min(c.MAX_Y - 30.0, targetY + 80.0);
            double altY2 = Math.max(c.MIN_Y + 30.0, targetY - 80.0);
            if (!isPassPathBlocked(carrierCX, carrierCY, targetX, altY1, enemyTeam)) {
                targetY = altY1;
            } else if (!isPassPathBlocked(carrierCX, carrierCY, targetX, altY2, enemyTeam)) {
                targetY = altY2;
            }
        }

        ai.aiTargetX = targetX;
        ai.aiTargetY = targetY;
        ai.aiTargetAction = 0;
        ai.isBoosting = false;
    }

    public double getTitanChaseSpeed(Titan t) {
        if (t == null) return 1.0;
        if (effectPool != null && (effectPool.isRooted(t) || effectPool.isStunned(t))) {
            return 0.001;
        }
        double base = t.speed;
        boolean hasDilators = (homeGoaliePurchasedUpgrades != null && homeGoaliePurchasedUpgrades.contains("fortress.t5.dilators")) ||
                              (awayGoaliePurchasedUpgrades != null && awayGoaliePurchasedUpgrades.contains("fortress.t5.dilators"));
        if (hasDilators && c != null) {
            base *= c.getD("guardian.dilators.speedmult");
        }
        if (effectPool != null) {
            for (Effect eff : effectPool.getEffects()) {
                if (eff != null && eff.on != null && eff.on.id.equals(t.id)) {
                    if (eff.effect.equals(EffectId.SLOW)) {
                        base /= ((RatioEffect) eff).getRatio();
                    } else if (eff.effect.equals(EffectId.FAST)) {
                        base *= ((RatioEffect) eff).getRatio();
                    }
                }
            }
        }
        if (t.fuel > 5.0) {
            base *= t.boostFactor;
        }
        int L = 0;
        int topCY = (int) (c.getI("goal.low.y") + c.getI("goal.low.height") / 2.0);
        int midCY = (int) (c.getI("goal.hi.y") + c.getI("goal.hi.height") / 2.0);
        int botCY = (int) (c.getI("goal.low2.y") + c.getI("goal.low.height") / 2.0);
        double d0 = Math.abs(t.Y - topCY);
        double d1 = Math.abs(t.Y - midCY);
        double d2 = Math.abs(t.Y - botCY);
        if (d1 < d0 && d1 < d2) L = 1;
        else if (d2 < d0 && d2 < d1) L = 2;
        return Math.max(0.1, getLaneMinionSpeed(L, t.team, base, 0.0));
    }

    public boolean isTeammateCloserToLooseBall(Titan ai, double targetBallX, double targetBallY) {
        double aiCX = ai.X + ai.width / 2.0;
        double aiCY = ai.Y + ai.height / 2.0;
        double aiDist = Math.hypot(targetBallX - aiCX, targetBallY - aiCY);
        double aiSpeed = getTitanChaseSpeed(ai);
        double aiTimeToBall = aiDist / aiSpeed;

        for (Titan t : players) {
            if (t == null || t.id.equals(ai.id) || t.team != ai.team) continue;
            if (effectPool != null && (effectPool.hasEffect(t, EffectId.DEAD)
                    || effectPool.isRooted(t) || effectPool.isStunned(t))) {
                continue;
            }

            // Goalies stay in their crease and only contest balls inside their box
            if (isGoalie(t)) {
                boolean inCrease = (targetBallY >= c.GOALIE_Y_MIN && targetBallY <= c.GOALIE_Y_MAX
                        && (t.team == TeamAffiliation.HOME
                                ? (targetBallX >= c.GOALIE_XH_MIN && targetBallX <= c.GOALIE_XH_MAX)
                                : (targetBallX >= c.GOALIE_XA_MIN && targetBallX <= c.GOALIE_XA_MAX)));
                if (!inCrease) continue;
            }

            double tCX = t.X + t.width / 2.0;
            double tCY = t.Y + t.height / 2.0;
            double tDist = Math.hypot(targetBallX - tCX, targetBallY - tCY);
            double tSpeed = getTitanChaseSpeed(t);
            double tTimeToBall = tDist / tSpeed;

            if (tTimeToBall < aiTimeToBall - 0.001) {
                return true;
            } else if (Math.abs(tTimeToBall - aiTimeToBall) <= 0.001) {
                if (tDist < aiDist - 0.001) {
                    return true;
                } else if (Math.abs(tDist - aiDist) <= 0.001) {
                    if (t.id.compareTo(ai.id) < 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void evaluateLooseBallAttackingPosture(Titan ai, double ballX, double ballY) {
        double forwardDir = (ai.team == TeamAffiliation.HOME) ? 1.0 : -1.0;
        TeamAffiliation enemyTeam = (ai.team == TeamAffiliation.HOME) ? TeamAffiliation.AWAY : TeamAffiliation.HOME;

        // One pass length away (~297 px = sqrt(210^2 + 210^2), standard throw power distance is 316.0)
        double forwardOffset = forwardDir * 210.0;
        double preferredVerticalOffset = (ai.Y < ballY) ? -210.0 : 210.0;
        double alternateVerticalOffset = -preferredVerticalOffset;

        double targetX = Math.max(c.MIN_X + 25, Math.min(c.MAX_X - 25, ballX + forwardOffset));
        double targetY = Math.max(c.MIN_Y + 25, Math.min(c.MAX_Y - 25, ballY + preferredVerticalOffset));

        // If preferred flank pass path is blocked by an enemy, try alternate flank
        if (isPassPathBlocked(ballX, ballY, targetX, targetY, enemyTeam)) {
            double altTargetY = Math.max(c.MIN_Y + 25, Math.min(c.MAX_Y - 25, ballY + alternateVerticalOffset));
            if (!isPassPathBlocked(ballX, ballY, targetX, altTargetY, enemyTeam)) {
                targetY = altTargetY;
            }
        }

        ai.aiTargetX = targetX;
        ai.aiTargetY = targetY;
        ai.aiTargetAction = 0;
        updateAiBoostDecision(ai);
    }

    public Titan getHorizontalImpedingEnemy(Titan ai, TeamAffiliation enemyTeam) {
        double fwdDir = (ai.team == TeamAffiliation.HOME) ? 1.0 : -1.0;
        double aiCX = ai.X + ai.width / 2.0, aiCY = ai.Y + ai.height / 2.0;
        Titan best = null;
        double closestDist = Double.MAX_VALUE;
        for (Titan t : players) {
            if (t != null && t.team == enemyTeam && !effectPool.hasEffect(t, EffectId.DEAD)) {
                if (c != null && !c.AI_OMNISCIENCE_ENABLED && !isTitanVisibleTo(ai, t)) continue;
                double dx = (t.X + t.width / 2.0) - aiCX, dy = (t.Y + t.height / 2.0) - aiCY;
                double fwdDist = dx * fwdDir;
                if (fwdDist > -25.0 && fwdDist < 170.0 && Math.abs(dy) < 75.0) {
                    double dist = Math.hypot(dx, dy);
                    if (dist < closestDist) { closestDist = dist; best = t; }
                }
            }
        }
        return best;
    }

    public Titan findBackwardsOrVerticalPassTarget(Titan ai, TeamAffiliation enemyTeam) {
        double fwdDir = (ai.team == TeamAffiliation.HOME) ? 1.0 : -1.0;
        double aiCX = ai.X + ai.width / 2.0, aiCY = ai.Y + ai.height / 2.0;
        Titan best = null;
        double bestScore = -1.0;
        for (Titan t : players) {
            if (t != null && !t.id.equals(ai.id) && t.team == ai.team && t.getType() != TitanType.GOALIE && !effectPool.hasEffect(t, EffectId.DEAD)) {
                double dx = (t.X + t.width / 2.0) - aiCX, dy = Math.abs((t.Y + t.height / 2.0) - aiCY);
                double fwdDist = dx * fwdDir, dist = Math.hypot(dx, dy);
                if (dist < 80.0 || dist > 520.0 || (fwdDist >= -20.0 && (dy < 80.0 || fwdDist > 60.0))) continue;
                if (isPassPathBlocked(aiCX, aiCY, t.X + t.width / 2.0, t.Y + t.height / 2.0, enemyTeam)) continue;
                Titan enemy = findNearestEnemy(t, enemyTeam);
                double enemyDist = enemy != null ? Math.hypot(enemy.X - t.X, enemy.Y - t.Y) : 999.0;
                if (enemyDist < 60.0) continue;
                double score = enemyDist + (dy >= 80.0 ? 50.0 : 30.0);
                if (score > bestScore) { bestScore = score; best = t; }
            }
        }
        return best;
    }

    public boolean isNearFriendlyGoal(TeamAffiliation team, double x, double y, double buffer) {
        GoalHoop[] friendlyGoals = (team == TeamAffiliation.HOME)
                ? new GoalHoop[]{ homeHiGoal, lowGoals[0], lowGoals[1] }
                : new GoalHoop[]{ awayHiGoal, lowGoals[2], lowGoals[3] };
        for (GoalHoop g : friendlyGoals) {
            if (g == null) continue;
            double gCX = g.x + g.w / 2.0;
            double gCY = g.y + g.h / 2.0;
            double rx = g.w / 2.0 + buffer;
            double ry = g.h / 2.0 + buffer;
            double dx = (x - gCX) / rx;
            double dy = (y - gCY) / ry;
            if (dx * dx + dy * dy <= 1.0) {
                return true;
            }
        }
        return false;
    }

    public boolean wouldTitanEnterFriendlyGoal(Titan t, double dx, double dy) {
        if (t == null || t.getType() == TitanType.GOALIE) return false;
        GoalHoop[] friendlyGoals = (t.team == TeamAffiliation.HOME)
                ? new GoalHoop[]{ homeHiGoal, lowGoals[0], lowGoals[1] }
                : new GoalHoop[]{ awayHiGoal, lowGoals[2], lowGoals[3] };

        double simulatedBallCX = t.X + dx + 35.0;
        double simulatedBallCY = t.Y + dy + 35.0;
        double ballRadius = (ball != null) ? ball.width / 2.0 : 15.0;
        double safety = 25.0;

        for (GoalHoop g : friendlyGoals) {
            if (g == null) continue;
            double gCX = g.x + g.w / 2.0;
            double gCY = g.y + g.h / 2.0;
            double rx = g.w / 2.0 + ballRadius + safety;
            double ry = g.h / 2.0 + ballRadius + safety;
            double diffX = (simulatedBallCX - gCX) / rx;
            double diffY = (simulatedBallCY - gCY) / ry;
            if (diffX * diffX + diffY * diffY <= 1.0) {
                return true;
            }
        }
        return false;
    }

    public int getEvasionVerticalDir(Titan ai, double eCY, double currentCY) {
        if (ai.aiEvadeVerticalDir != 0 && Math.random() < 0.7) return ai.aiEvadeVerticalDir;
        double distToTop = currentCY - c.MIN_Y, distToBottom = c.MAX_Y - currentCY;
        int dir = (eCY > currentCY + 5.0) ? ((distToTop > 100.0) ? -1 : 1) :
                  (eCY < currentCY - 5.0) ? ((distToBottom > 100.0) ? 1 : -1) :
                  ((distToTop > distToBottom) ? -1 : 1);
        return ai.aiEvadeVerticalDir = dir;
    }

    public double[] calculateForwardEvadeTarget(Titan ai, Titan impedingEnemy, double currentCX, double currentCY) {
        return calculateEvadeTarget(ai, impedingEnemy, currentCX, currentCY, 80.0, 180.0);
    }

    public double[] calculateBackwardDiagonalEvadeTarget(Titan ai, Titan impedingEnemy, double currentCX, double currentCY) {
        if (ai != null && ai.possession == 1) {
            double[] bwd = calculateEvadeTarget(ai, impedingEnemy, currentCX, currentCY, -130.0, 120.0);
            if (!isNearFriendlyGoal(ai.team, bwd[0], bwd[1], 65.0)) {
                return bwd;
            }
            // Backward is dangerous near own goal/sidegoal!
            // Priority: Try vertical evasion without moving backward
            double[] vert = calculateEvadeTarget(ai, impedingEnemy, currentCX, currentCY, 0.0, 140.0);
            if (!isNearFriendlyGoal(ai.team, vert[0], vert[1], 65.0)) {
                return vert;
            }
            // If vertical at current X is also near a goal, try forward diagonal evasion away from goal
            double[] fwd = calculateForwardEvadeTarget(ai, impedingEnemy, currentCX, currentCY);
            if (!isNearFriendlyGoal(ai.team, fwd[0], fwd[1], 65.0)) {
                return fwd;
            }
            // If completely cornered against friendly goal, hold position outside goal rather than backing in
            return new double[]{ currentCX, currentCY };
        }
        return calculateEvadeTarget(ai, impedingEnemy, currentCX, currentCY, -130.0, 120.0);
    }

    public double[] calculateVerticalEvasionTarget(Titan ai, Titan impedingEnemy, double currentCX, double currentCY) {
        return calculateForwardEvadeTarget(ai, impedingEnemy, currentCX, currentCY);
    }

    private double[] calculateEvadeTarget(Titan ai, Titan impedingEnemy, double cx, double cy, double fwdDist, double vertDist) {
        double eCY = (impedingEnemy != null) ? (impedingEnemy.Y + impedingEnemy.height / 2.0) : cy;
        int vertDir = getEvasionVerticalDir(ai, eCY, cy);
        double fwdDir = (ai.team == TeamAffiliation.HOME) ? 1.0 : -1.0;
        return new double[]{
            Math.max(c.MIN_X, Math.min(c.MAX_X, cx + fwdDir * fwdDist)),
            Math.max(c.MIN_Y + 30.0, Math.min(c.MAX_Y - 30.0, cy + vertDir * vertDist))
        };
    }

    public void evaluateOnBallOffense(Titan ai, TeamAffiliation enemyTeam) {
        double currentCX = ai.X + ai.width / 2.0;
        double currentCY = ai.Y + ai.height / 2.0;

        boolean inAttackingThird = (ai.team == TeamAffiliation.HOME)
                ? currentCX > (FIELD_LENGTH * 2.0 / 3.0)
                : currentCX < (FIELD_LENGTH / 3.0);

        Titan impedingEnemy = getHorizontalImpedingEnemy(ai, enemyTeam);

        // Transition Offense: Pass-and-chase priorities in backcourt/midfield
        if (!inAttackingThird) {
            // If an opponent is an immediate steal threat on the X axis, evasive action must be taken:
            // Priorities: 1) Run forward, 2) Find a backpass option, 3) Run backwards diagonally to create space.
            if (impedingEnemy != null) {
                // Priority 1: Run forward (via forward diagonal/vertical evasion around enemy) if not stuck
                if (ai.aiStuckHorizontalTicks < 2) {
                    double[] fwdTarget = calculateForwardEvadeTarget(ai, impedingEnemy, currentCX, currentCY);
                    ai.aiTargetX = fwdTarget[0];
                    ai.aiTargetY = fwdTarget[1];
                    ai.aiTargetAction = 0; // RUN
                    ai.isBoosting = false;
                    return;
                }

                // Priority 2: Find a backpass option
                Titan outletPass = findBackwardsOrVerticalPassTarget(ai, enemyTeam);
                if (outletPass != null) {
                    ai.aiTargetX = outletPass.X + outletPass.width / 2.0;
                    ai.aiTargetY = outletPass.Y + outletPass.height / 2.0;
                    ai.aiTargetAction = 1; // PASS
                    return;
                }

                // Priority 3: Run backwards diagonally to create space
                double[] backTarget = calculateBackwardDiagonalEvadeTarget(ai, impedingEnemy, currentCX, currentCY);
                ai.aiTargetX = backTarget[0];
                ai.aiTargetY = backTarget[1];
                ai.aiTargetAction = 0; // RUN
                ai.isBoosting = false;
                return;
            }

            // Priority 1: Pass to a faster, upfield teammate with clear passing line
            Titan fasterTeammate = findFasterUpfieldTeammate(ai, enemyTeam);
            if (fasterTeammate != null) {
                ai.aiTargetX = fasterTeammate.X + fasterTeammate.width / 2.0;
                ai.aiTargetY = fasterTeammate.Y + fasterTeammate.height / 2.0;
                ai.aiTargetAction = 1; // PASS
                return;
            }

            // Priority 2: Pass upfield into fastest reachable lane with speedboost unblocked by enemy (pass-and-chase)
            double[] laneTarget = findBestOpenLanePassTarget(ai, enemyTeam);
            if (laneTarget != null) {
                ai.aiTargetX = laneTarget[0];
                ai.aiTargetY = laneTarget[1];
                ai.aiTargetAction = 1; // PASS ahead into lane
                return;
            }

            // Priority 3: Run upfield yourself (cannot boost while holding ball)
            double forwardDir = (ai.team == TeamAffiliation.HOME) ? 1.0 : -1.0;
            if (ai.aiStuckHorizontalTicks >= 2) {
                int vertDir = (ai.aiEvadeVerticalDir != 0) ? ai.aiEvadeVerticalDir : ((currentCY < (c.MIN_Y + c.MAX_Y) / 2.0) ? 1 : -1);
                ai.aiEvadeVerticalDir = vertDir;
                double forwardX = currentCX + forwardDir * 120.0;
                ai.aiTargetX = Math.max(c.MIN_X, Math.min(c.MAX_X, forwardX));
                ai.aiTargetY = Math.max(c.MIN_Y + 30.0, Math.min(c.MAX_Y - 30.0, currentCY + vertDir * 160.0));
            } else {
                double forwardX = currentCX + forwardDir * 250.0;
                ai.aiTargetX = Math.max(c.MIN_X, Math.min(c.MAX_X, forwardX));
                ai.aiTargetY = currentCY;
            }
            ai.aiTargetAction = 0; // RUN
            ai.isBoosting = false;
            return;
        }

        // On ball offense in attacking third: 6-Tier Strategic Decision Hierarchy
        GoalHoop enemyCenterGoal = (enemyTeam == TeamAffiliation.AWAY) ? awayHiGoal : homeHiGoal;
        double cgX = enemyCenterGoal.x + enemyCenterGoal.w / 2.0;
        double cgY = enemyCenterGoal.y + enemyCenterGoal.h / 2.0;
        double distToCenterGoal = Math.hypot(cgX - currentCX, cgY - currentCY);
        double maxGroundShotDist = 316.0 * ai.throwPower;

        // Tier 1: Check center goal, drill it if open
        if (enemyCenterGoal.checkReady() && distToCenterGoal <= maxGroundShotDist && !isShotPathBlocked(currentCX, currentCY, cgX, cgY, enemyTeam)) {
            ai.aiTargetX = cgX;
            ai.aiTargetY = cgY;
            ai.aiTargetAction = 1; // Flat line-drive ground shot
            return;
        }

        // Tier 2: Check lob, and goalie on block cooldown, shoot it if open
        Titan defendingGoalie = getDefendingGoalie(enemyTeam);
        boolean goalieCannotBlock = (defendingGoalie == null
                || effectPool.hasEffect(defendingGoalie, EffectId.DEAD)
                || effectPool.isStunned(defendingGoalie)
                || (!effectPool.hasEffect(defendingGoalie, EffectId.BLOCK) && effectPool.hasEffect(defendingGoalie, EffectId.COOLDOWN_Q)));

        if (goalieCannotBlock && enemyCenterGoal.checkReady()) {
            double gravityMult = 1.0;
            long lowGrav = (ai.team == TeamAffiliation.HOME)
                    ? homeGoalieAbilities.lowGravityUntilMs
                    : awayGoalieAbilities.lowGravityUntilMs;
            if (nowEpochMs < lowGrav) {
                gravityMult = 1.5;
            }
            double noFlyMult = 1.0;
            if (ai.team == TeamAffiliation.HOME) {
                if (awayNoFlyZoneActive && ai.X >= 1368.0 && ai.X <= 2012.0) {
                    noFlyMult = 0.5;
                }
            } else if (ai.team == TeamAffiliation.AWAY) {
                if (homeNoFlyZoneActive && ai.X >= 36.0 && ai.X <= 680.0) {
                    noFlyMult = 0.5;
                }
            }
            double dLob = 230.0 * ai.throwPower * gravityMult * noFlyMult;
            double hoopRadius = Math.max(enemyCenterGoal.w, enemyCenterGoal.h) / 2.0;

            // Lob reaches and descends into hoop (past uncatchable window frames 3-8, landing in hoop)
            if (distToCenterGoal >= 0.70 * dLob && distToCenterGoal <= dLob + hoopRadius) {
                if (!isLandingZoneBlocked(cgX, cgY, enemyTeam)) {
                    ai.aiTargetX = cgX;
                    ai.aiTargetY = cgY;
                    ai.aiTargetAction = 3; // Overhead lob shot
                    return;
                }
            }
        }

        // Tier 3: Check unblocked one-timers into the center goal, where shooter has a clear path and pass has a clean path
        Titan bestOneTimerTarget = null;
        double bestOneTimerGoalDist = Double.MAX_VALUE;
        for (Titan t : players) {
            if (t != null && !t.id.equals(ai.id) && t.team == ai.team && !isGoalie(t)
                    && !effectPool.hasEffect(t, EffectId.DEAD) && !effectPool.isStunned(t)) {
                double tCX = t.X + t.width / 2.0;
                double tCY = t.Y + t.height / 2.0;
                double distToTeammate = Math.hypot(tCX - currentCX, tCY - currentCY);

                if (distToTeammate >= 60.0 && distToTeammate <= maxGroundShotDist) {
                    if (!isPassPathBlocked(currentCX, currentCY, tCX, tCY, enemyTeam)) {
                        double distTeammateToGoal = Math.hypot(cgX - tCX, cgY - tCY);
                        double maxTeammateShotDist = 316.0 * t.throwPower;
                        if (distTeammateToGoal <= maxTeammateShotDist && !isShotPathBlocked(tCX, tCY, cgX, cgY, enemyTeam)) {
                            if (distTeammateToGoal < bestOneTimerGoalDist) {
                                bestOneTimerGoalDist = distTeammateToGoal;
                                bestOneTimerTarget = t;
                            }
                        }
                    }
                }
            }
        }
        if (bestOneTimerTarget != null) {
            ai.aiTargetX = bestOneTimerTarget.X + bestOneTimerTarget.width / 2.0;
            ai.aiTargetY = bestOneTimerTarget.Y + bestOneTimerTarget.height / 2.0;
            ai.aiTargetAction = 1; // PASS to one-timer shooter
            return;
        }

        // Tier 4: Checkdown into the sidegoal
        GoalHoop[] enemySideGoals = (enemyTeam == TeamAffiliation.AWAY)
                ? new GoalHoop[]{lowGoals[2], lowGoals[3]}
                : new GoalHoop[]{lowGoals[0], lowGoals[1]};
        GoalHoop bestSideGoal = null;
        double bestSideGoalDist = Double.MAX_VALUE;
        for (GoalHoop sg : enemySideGoals) {
            if (sg == null || !sg.checkReady()) continue;
            double sgX = sg.x + sg.w / 2.0;
            double sgY = sg.y + sg.h / 2.0;
            double distToSg = Math.hypot(sgX - currentCX, sgY - currentCY);
            if (distToSg <= maxGroundShotDist && !isShotPathBlocked(currentCX, currentCY, sgX, sgY, enemyTeam)) {
                if (distToSg < bestSideGoalDist) {
                    bestSideGoalDist = distToSg;
                    bestSideGoal = sg;
                }
            }
        }
        if (bestSideGoal != null) {
            ai.aiTargetX = bestSideGoal.x + bestSideGoal.w / 2.0;
            ai.aiTargetY = bestSideGoal.y + bestSideGoal.h / 2.0;
            ai.aiTargetAction = 1; // Sidegoal shot
            return;
        }

        // Tier 5: If no goals in range, find any safe pass
        Titan bestSafePassTarget = null;
        double bestPassScore = -99999.0;
        for (Titan t : players) {
            if (t != null && !t.id.equals(ai.id) && t.team == ai.team && !isGoalie(t)
                    && !effectPool.hasEffect(t, EffectId.DEAD) && !effectPool.isStunned(t)) {
                double tCX = t.X + t.width / 2.0;
                double tCY = t.Y + t.height / 2.0;
                double distToTeammate = Math.hypot(tCX - currentCX, tCY - currentCY);
                if (distToTeammate >= 60.0 && distToTeammate <= maxGroundShotDist) {
                    if (!isPassPathBlocked(currentCX, currentCY, tCX, tCY, enemyTeam)) {
                        boolean teammateSafe = true;
                        for (Titan enemy : players) {
                            if (enemy != null && enemy.team == enemyTeam && !effectPool.hasEffect(enemy, EffectId.DEAD)) {
                                if (Math.hypot(enemy.X + enemy.width / 2.0 - tCX, enemy.Y + enemy.height / 2.0 - tCY) < 50.0) {
                                    teammateSafe = false;
                                    break;
                                }
                            }
                        }
                        if (teammateSafe) {
                            double forwardProgress = (ai.team == TeamAffiliation.HOME) ? (tCX - currentCX) : (currentCX - tCX);
                            double score = forwardProgress + 500.0 - distToTeammate * 0.2;
                            if (score > bestPassScore) {
                                bestPassScore = score;
                                bestSafePassTarget = t;
                            }
                        }
                    }
                }
            }
        }
        if (bestSafePassTarget != null) {
            ai.aiTargetX = bestSafePassTarget.X + bestSafePassTarget.width / 2.0;
            ai.aiTargetY = bestSafePassTarget.Y + bestSafePassTarget.height / 2.0;
            ai.aiTargetAction = 1; // Safe pass
            return;
        }

        // Tier 6: If no safe passes and already in attacking third, shoot to any safe location (50%) or move laterally (50%)
        if (Math.random() < 0.5) {
            // Shoot to safe location in attacking third
            double targetX = (ai.team == TeamAffiliation.HOME)
                    ? Math.min(c.MAX_X - 50.0, currentCX + 150.0)
                    : Math.max(c.MIN_X + 50.0, currentCX - 150.0);
            double targetY = (currentCY < (c.MIN_Y + c.MAX_Y) / 2.0)
                    ? c.MIN_Y + 50.0
                    : c.MAX_Y - 50.0;
            ai.aiTargetX = targetX;
            ai.aiTargetY = targetY;
            ai.aiTargetAction = 1;
        } else {
            // Move laterally to create space/angle; reassess on natural reaction cycle
            int vertDir = (ai.aiEvadeVerticalDir != 0) ? ai.aiEvadeVerticalDir : ((currentCY < (c.MIN_Y + c.MAX_Y) / 2.0) ? 1 : -1);
            ai.aiEvadeVerticalDir = vertDir;
            double targetY = Math.max(c.MIN_Y + 40.0, Math.min(c.MAX_Y - 40.0, currentCY + vertDir * 140.0));
            ai.aiTargetX = currentCX;
            ai.aiTargetY = targetY;
            ai.aiTargetAction = 0;
        }
        return;
    }

    protected boolean isPassPathBlocked(double x1, double y1, double x2, double y2, TeamAffiliation enemyTeam) {
        for (Titan enemy : players) {
            if (enemy != null && enemy.team == enemyTeam && !effectPool.hasEffect(enemy, EffectId.DEAD)) {
                if (c != null && !c.AI_OMNISCIENCE_ENABLED && effectPool != null
                        && effectPool.hasEffect(enemy, EffectId.STEALTHED)
                        && !effectPool.hasEffect(enemy, EffectId.FLARE)) {
                    continue;
                }
                double eCX = enemy.X + enemy.width / 2.0;
                double eCY = enemy.Y + enemy.height / 2.0;
                if (distToSegment(eCX, eCY, x1, y1, x2, y2) < 45.0) {
                    return true;
                }
            }
        }
        return false;
    }

    protected Titan findFasterUpfieldTeammate(Titan ai, TeamAffiliation enemyTeam) {
        Titan best = null;
        double bestDist = -1.0;
        double aiCX = ai.X + ai.width / 2.0;
        double aiCY = ai.Y + ai.height / 2.0;
        double aiSpeed = ai.actualSpeed(this);

        for (Titan t : players) {
            if (t != null && !t.id.equals(ai.id) && t.team == ai.team && !effectPool.hasEffect(t, EffectId.DEAD)) {
                double tCX = t.X + t.width / 2.0;
                double tCY = t.Y + t.height / 2.0;
                double forwardX = (ai.team == TeamAffiliation.HOME) ? (tCX - aiCX) : (aiCX - tCX);

                // Teammate must be upfield by at least 40px and faster than AI
                if (forwardX > 40.0 && (t.actualSpeed(this) > aiSpeed || t.speed > ai.speed)) {
                    if (!isPassPathBlocked(aiCX, aiCY, tCX, tCY, enemyTeam)) {
                        if (forwardX > bestDist) {
                            bestDist = forwardX;
                            best = t;
                        }
                    }
                }
            }
        }
        return best;
    }

    protected boolean isDestinationCloserToFriendlyThanEnemy(Titan ai, double targetX, double targetY) {
        double minFriendlyDist = Double.MAX_VALUE;
        double minEnemyDist = Double.MAX_VALUE;

        for (Titan t : players) {
            if (t != null && !effectPool.hasEffect(t, EffectId.DEAD)) {
                if (t.team != ai.team && c != null && !c.AI_OMNISCIENCE_ENABLED && !isTitanVisibleTo(ai, t)) {
                    continue;
                }
                double cx = t.X + t.width / 2.0;
                double cy = t.Y + t.height / 2.0;
                double d = Math.hypot(cx - targetX, cy - targetY);
                if (t.team == ai.team) {
                    if (d < minFriendlyDist) {
                        minFriendlyDist = d;
                    }
                } else {
                    if (d < minEnemyDist) {
                        minEnemyDist = d;
                    }
                }
            }
        }
        return minFriendlyDist < minEnemyDist;
    }

    protected double[] findBestOpenLanePassTarget(Titan ai, TeamAffiliation enemyTeam) {
        double aiCX = ai.X + ai.width / 2.0;
        double aiCY = ai.Y + ai.height / 2.0;
        double forwardDir = (ai.team == TeamAffiliation.HOME) ? 1.0 : -1.0;
        double targetX = Math.max(c.MIN_X + 50.0, Math.min(c.MAX_X - 50.0, aiCX + forwardDir * 260.0));

        double[] laneYs = laneCenterYs();
        int bestLane = -1;
        int maxAdvantage = -999;
        for (int L = 0; L < 3; L++) {
            double laneY = laneYs[L];
            if (!isPassPathBlocked(aiCX, aiCY, targetX, laneY, enemyTeam)) {
                if (isDestinationCloserToFriendlyThanEnemy(ai, targetX, laneY)) {
                    int advantage = getLaneAdvantage(L, ai.team);
                    if (advantage > maxAdvantage) {
                        maxAdvantage = advantage;
                        bestLane = L;
                    }
                }
            }
        }
        if (bestLane != -1) {
            return new double[]{targetX, laneYs[bestLane]};
        }
        return null;
    }

    protected Titan findBestPassTarget(Titan ai) {
        Titan best = null;
        double bestDist = -1.0;
        double aiCX = ai.X + ai.width / 2.0;
        double aiCY = ai.Y + ai.height / 2.0;
        boolean inAttackingThird = (ai.team == TeamAffiliation.HOME)
                ? aiCX > (FIELD_LENGTH * 2.0 / 3.0)
                : aiCX < (FIELD_LENGTH / 3.0);
        TeamAffiliation enemyTeam = (ai.team == TeamAffiliation.HOME) ? TeamAffiliation.AWAY : TeamAffiliation.HOME;
        for (Titan t : players) {
            if (t != null && !t.id.equals(ai.id) && t.team == ai.team && t.getType() != TitanType.GOALIE && !effectPool.hasEffect(t, EffectId.DEAD)) {
                double tCX = t.X + t.width / 2.0;
                double tCY = t.Y + t.height / 2.0;
                double forwardX = (ai.team == TeamAffiliation.HOME) ? (tCX - aiCX) : (aiCX - tCX);
                boolean validTarget = false;
                double targetScore = 0.0;
                if (!inAttackingThird) {
                    if (forwardX > 50.0) {
                        validTarget = true;
                        targetScore = forwardX;
                    }
                } else {
                    double dist = Math.hypot(tCX - aiCX, tCY - aiCY);
                    // In attacking third, allow lateral kicks (Y diff >= 100) or upfield, as long as within passing range and not way behind
                    if (dist >= 80.0 && dist <= 550.0 && (forwardX > -50.0 || Math.abs(tCY - aiCY) >= 100.0)) {
                        validTarget = true;
                        targetScore = 1000.0 - dist;
                    }
                }
                if (validTarget) {
                    if (!isPassPathBlocked(aiCX, aiCY, tCX, tCY, enemyTeam)) {
                        if (targetScore > bestDist) {
                            bestDist = targetScore;
                            best = t;
                        }
                    }
                }
            }
        }
        return best;
    }

    protected void executeAiShot(Titan ai, double targetX, double targetY) {
        executeAiShot(ai, targetX, targetY, 1);
    }

    protected void executeAiShot(Titan ai, double targetX, double targetY, int btn) {
        if (ai.possession != 1 || ai.actionState != Titan.TitanState.IDLE) return;
        serverMouseRoutine(ai, (int) targetX, (int) targetY, btn, 0, 0);
    }

    public boolean isEnemyBetween(Titan from, Titan to) {
        if (from == null || to == null) return false;
        double ax = from.X + from.width / 2.0;
        double ay = from.Y + from.height / 2.0;
        double cx = to.X + to.width / 2.0;
        double cy = to.Y + to.height / 2.0;
        double dx = cx - ax;
        double dy = cy - ay;
        double l2 = dx * dx + dy * dy;
        if (l2 < 1.0) return false;

        TeamAffiliation enemyTeam = (from.team == TeamAffiliation.HOME) ? TeamAffiliation.AWAY : TeamAffiliation.HOME;

        for (Titan enemy : players) {
            if (enemy != null && enemy.team == enemyTeam && !effectPool.hasEffect(enemy, EffectId.DEAD)) {
                if (c != null && !c.AI_OMNISCIENCE_ENABLED && effectPool != null
                        && effectPool.hasEffect(enemy, EffectId.STEALTHED)
                        && !effectPool.hasEffect(enemy, EffectId.FLARE)) {
                    continue;
                }
                double ex = enemy.X + enemy.width / 2.0;
                double ey = enemy.Y + enemy.height / 2.0;
                double t = ((ex - ax) * dx + (ey - ay) * dy) / l2;
                if (t > 0.05 && t < 0.95) {
                    double projX = ax + t * dx;
                    double projY = ay + t * dy;
                    if (Math.hypot(ex - projX, ey - projY) < 65.0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean handleCallForBall(Titan caller) {
        if (caller == null || effectPool.hasEffect(caller, EffectId.DEAD)) return false;
        Optional<Titan> possessorOpt = titanInPossession();
        if (!possessorOpt.isPresent()) return false;
        Titan possessor = possessorOpt.get();
        if (possessor.id.equals(caller.id)) return false;
        if (possessor.team != caller.team) return false;

        // Check if possessor is an AI titan (no human client controlling it)
        int possessorIndex = -1;
        for (int i = 0; i < players.length; i++) {
            if (players[i] != null && players[i].id.equals(possessor.id)) {
                possessorIndex = i;
                break;
            }
        }
        if (possessorIndex == -1) return false;
        if (anyClientSelected(possessorIndex + 1)) return false;

        double callerCX = caller.X + caller.width / 2.0;
        double callerCY = caller.Y + caller.height / 2.0;
        boolean lob = isEnemyBetween(possessor, caller);

        caller.queuedBtn = 0;
        executeAiPassTo(possessor, callerCX, callerCY, lob);
        return true;
    }

    public void executeAiPassTo(Titan ai, double targetX, double targetY, boolean lob) {
        if (ai.possession != 1) return;
        int btn = lob ? 3 : 1;
        serverMouseRoutine(ai, (int) targetX, (int) targetY, btn, 0, 0);
        ai.aiTargetAction = 0;
        ai.aiLastDecisionTimeMs = (long) framesSinceStart * GAMETICK_MS;
        boolean isGoalie = (ai.getType() == TitanType.GOALIE);
        double goalieRatio = (c != null) ? c.AI_GOALIE_REACTION_RATIO : 0.70;
        int minDelay = (options != null) ? options.getAiReactionTimeMinMs(isGoalie, goalieRatio) : (isGoalie ? (int) Math.round(1200 * goalieRatio) : 1200);
        int maxDelay = (options != null) ? options.getAiReactionTimeMaxMs(isGoalie, goalieRatio) : (isGoalie ? (int) Math.round(1700 * goalieRatio) : 1700);
        ai.aiReactionDelayMs = minDelay + (long)(Math.random() * (maxDelay - minDelay + 1));
    }

    public CollisionMath.Bounds getTitanSolidBounds(Titan t) {
        if (t.getType() == TitanType.GOALIE) {
            double xOffset = (t.width - this.GOALIE_SOLID_W) / 2.0;
            return new CollisionMath.Bounds(
                    t.X + xOffset,
                    t.Y,
                    this.GOALIE_SOLID_W,
                    this.GOALIE_SOLID_H
            );
        } else {
            return new CollisionMath.Bounds(
                    t.X + this.SPRITE_X_EMPTY / 2.0,
                    t.Y + this.SPRITE_Y_EMPTY / 2.0,
                    t.width - this.SPRITE_X_EMPTY,
                    t.height - this.SPRITE_Y_EMPTY
            );
        }
    }

    public static double getBoundsDistance(CollisionMath.Bounds b1, CollisionMath.Bounds b2) {
        double dx = Math.max(0, Math.max(b1.minX() - (b2.minX() + b2.width()), b2.minX() - (b1.minX() + b1.width())));
        double dy = Math.max(0, Math.max(b1.minY() - (b2.minY() + b2.height()), b2.minY() - (b1.minY() + b1.height())));
        return Math.hypot(dx, dy);
    }

    public boolean isWithinStealRange(Titan caster, Titan target) {
        if (caster == null || target == null) return false;
        double effectiveStealRad = caster.stealRad * caster.rangeFactor;

        // Solid core bounds distance (titans are solid and cannot pass through one another)
        CollisionMath.Bounds b1 = getTitanSolidBounds(caster);
        CollisionMath.Bounds b2 = getTitanSolidBounds(target);
        double boxDist = getBoundsDistance(b1, b2);

        // Center-to-ball distance
        double cCtrX = caster.X + caster.width / 2.0;
        double cCtrY = caster.Y + caster.height / 2.0;
        double ballCtrX = ball.X + ball.width / 2.0;
        double ballCtrY = ball.Y + ball.height / 2.0;
        double ballRadius = ball.width / 2.0;
        double distToBall = Math.hypot(cCtrX - ballCtrX, cCtrY - ballCtrY);

        // Both bounding box proximity AND reach to ball center must be satisfied.
        // When vertically stacked, 52px collision height prevents non-guardians from reaching the ball.
        return boxDist <= effectiveStealRad && distToBall <= (effectiveStealRad + ballRadius);
    }

    protected void executeAiSteal(Titan ai) {
        if (ai.possession == 1) return;
        if (!effectPool.isStunned(ai) && !effectPool.hasEffect(ai, EffectId.COOLDOWN_STEAL)) {
            try {
                boolean stolen = ability.castSteal(this, ai);
                if (!stolen) {
                    ai.actionState = Titan.TitanState.STEAL;
                    ai.actionFrame = 0;
                    ai.pushMove();
                }
            } catch (Exception e) {
                System.out.println("[AI_STEAL_ERR] Exception during AI steal: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    protected GoalHoop findNearestFriendlySideHoop(TeamAffiliation team, double x, double y) {
        GoalHoop[] low = (team == TeamAffiliation.HOME) ? new GoalHoop[]{lowGoals[0], lowGoals[1]} : new GoalHoop[]{lowGoals[2], lowGoals[3]};
        double d0 = Math.hypot(low[0].x + low[0].w / 2.0 - x, low[0].y + low[0].h / 2.0 - y);
        double d1 = Math.hypot(low[1].x + low[1].w / 2.0 - x, low[1].y + low[1].h / 2.0 - y);
        return d0 <= d1 ? low[0] : low[1];
    }

    protected Titan findAssignedDefensiveTarget(Titan ai, Titan possessor) {
        List<Titan> enemies = new ArrayList<>();
        for (Titan t : players) {
            if (t != null && t.team != ai.team && !effectPool.hasEffect(t, EffectId.DEAD) && t.getType() != TitanType.GOALIE) {
                if (c != null && !c.AI_OMNISCIENCE_ENABLED && !isTitanVisibleTo(ai, t)) continue;
                enemies.add(t);
            }
        }
        if (enemies.isEmpty()) return possessor;
        int aiIndex = 0;
        for (Titan t : players) {
            if (t.id.equals(ai.id)) break;
            if (t.team == ai.team && t.getType() != TitanType.GOALIE) aiIndex++;
        }
        return enemies.get(aiIndex % enemies.size());
    }

    public Titan findNearestEnemy(Titan ai, TeamAffiliation enemyTeam) {
        if (ai != null && c != null && !c.AI_OMNISCIENCE_ENABLED && effectPool != null && effectPool.hasEffect(ai, EffectId.BLIND)) {
            return null;
        }
        Titan nearest = null;
        double minD = Double.MAX_VALUE;
        for (Titan t : players) {
            if (t != null && t.team == enemyTeam && !effectPool.hasEffect(t, EffectId.DEAD)) {
                if (ai != null && c != null && !c.AI_OMNISCIENCE_ENABLED && !isTitanVisibleTo(ai, t)) {
                    continue;
                }
                double d = Math.hypot(t.X - ai.X, t.Y - ai.Y);
                if (d < minD) {
                    minD = d;
                    nearest = t;
                }
            }
        }
        return nearest;
    }

    public boolean isShotPathBlocked(double x1, double y1, double x2, double y2, TeamAffiliation enemyTeam) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double l2 = dx * dx + dy * dy;
        if (l2 < 1.0) return false;

        for (Titan enemy : players) {
            if (enemy != null && enemy.team == enemyTeam && !effectPool.hasEffect(enemy, EffectId.DEAD)) {
                if (c != null && !c.AI_OMNISCIENCE_ENABLED && effectPool != null
                        && effectPool.hasEffect(enemy, EffectId.STEALTHED)
                        && !effectPool.hasEffect(enemy, EffectId.FLARE)) {
                    continue;
                }
                double ex = enemy.X + enemy.width / 2.0;
                double ey = enemy.Y + enemy.height / 2.0;
                double t = ((ex - x1) * dx + (ey - y1) * dy) / l2;
                if (t > 0.05 && t < 1.02) {
                    double projX = x1 + t * dx;
                    double projY = y1 + t * dy;
                    if (Math.hypot(ex - projX, ey - projY) < 40.0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public Titan getDefendingGoalie(TeamAffiliation enemyTeam) {
        if (players == null) return null;
        for (Titan t : players) {
            if (t != null && t.team == enemyTeam && isGoalie(t)) {
                return t;
            }
        }
        return null;
    }

    public boolean isLandingZoneBlocked(double gx, double gy, TeamAffiliation enemyTeam) {
        for (Titan enemy : players) {
            if (enemy != null && enemy.team == enemyTeam && !effectPool.hasEffect(enemy, EffectId.DEAD) && !isGoalie(enemy)) {
                if (c != null && !c.AI_OMNISCIENCE_ENABLED && effectPool != null
                        && effectPool.hasEffect(enemy, EffectId.STEALTHED)
                        && !effectPool.hasEffect(enemy, EffectId.FLARE)) {
                    continue;
                }
                double ex = enemy.X + enemy.width / 2.0;
                double ey = enemy.Y + enemy.height / 2.0;
                if (Math.hypot(ex - gx, ey - gy) < 40.0) {
                    return true;
                }
            }
        }
        return false;
    }

    protected GoalHoop findUnblockedGoal(Titan ai, TeamAffiliation enemyTeam) {
        if (ai == null) return null;
        double aiCX = ai.X + ai.width / 2.0;
        double aiCY = ai.Y + ai.height / 2.0;
        double maxDist = 316.0 * ai.throwPower;
        GoalHoop[] enemyGoals = (enemyTeam == TeamAffiliation.AWAY)
                ? new GoalHoop[]{awayHiGoal, lowGoals[2], lowGoals[3]}
                : new GoalHoop[]{homeHiGoal, lowGoals[0], lowGoals[1]};
        for (GoalHoop g : enemyGoals) {
            if (g == null || !g.checkReady()) continue;
            double gx = g.x + g.w / 2.0;
            double gy = g.y + g.h / 2.0;
            if (Math.hypot(gx - aiCX, gy - aiCY) > maxDist) continue;
            if (!isShotPathBlocked(aiCX, aiCY, gx, gy, enemyTeam)) {
                return g;
            }
        }
        return null;
    }

    private static double distToSegment(double px, double py, double x1, double y1, double x2, double y2) {
        double l2 = (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1);
        if (l2 == 0) return Math.hypot(px - x1, py - y1);
        double t = Math.max(0, Math.min(1, ((px - x1) * (x2 - x1) + (py - y1) * (y2 - y1)) / l2));
        double projX = x1 + t * (x2 - x1);
        double projY = y1 + t * (y2 - y1);
        return Math.hypot(px - projX, py - projY);
    }

    protected void goalieTactics(Titan goalie, TeamAffiliation team) {
        if (c.GOALIE_DISABLED) {
            goalie.X = 999000;
            goalie.Y = 999000;
            return;
        }
        if (effectPool.isRooted(goalie)) {
            return;
        }
        Set<String> purchased = (team == TeamAffiliation.HOME) ? homeGoaliePurchasedUpgrades : awayGoaliePurchasedUpgrades;
        boolean pullGoalie = purchased != null && purchased.contains("siege.t3.pullgoalie");
        int YMAX = pullGoalie ? c.E_MAX_Y : c.GOALIE_Y_MAX;
        int YMIN = pullGoalie ? c.E_MIN_Y : c.GOALIE_Y_MIN;
        int XMAX = pullGoalie ? c.E_MAX_X : (team == TeamAffiliation.AWAY ? c.GOALIE_XA_MAX : c.GOALIE_XH_MAX);
        int XMIN = pullGoalie ? c.E_MIN_X : (team == TeamAffiliation.AWAY ? c.GOALIE_XA_MIN : c.GOALIE_XH_MIN);
        for (GoalHoop goal : this.lowGoals) {
            if (goalie.possession == 1 &&
                    ballIntersectsEllipse(goal) && goal.team.equals(TeamAffiliation.HOME)) {
                if (!goalie.collidesSolid(this, allSolids, 0, (int) +goalie.speed)) {
                    goalie.setX((int) (goalie.getX() + goalie.speed));
                    if (goalie.getX() > XMAX) goalie.setX(XMAX);
                }
            }
            if (goalie.possession == 1 &&
                    ballIntersectsEllipse(goal) && goal.team.equals(TeamAffiliation.AWAY)) {
                if (!goalie.collidesSolid(this, allSolids, 0, (int) -goalie.speed)) {
                    goalie.setX((int) (goalie.getX() - goalie.speed));
                    if (goalie.getX() < XMIN) goalie.setX(XMIN);
                }
            }
        }
        if (goalie.possession == 1) {
            return;
        }
        if (goalie.getY() + 35 < (ball.Y + ball.centerDist)) {
            if (!goalie.collidesSolid(this, allSolids, (int) goalie.speed, 0)) {
                goalie.setY((int) (goalie.getY() + goalie.speed));
                if (goalie.getY() > YMAX) goalie.setY(YMAX);
            }
        }
        if (goalie.getY() + 35 > (ball.Y + ball.centerDist)) {
            if (!goalie.collidesSolid(this, allSolids, (int) -goalie.speed, 0)) {
                goalie.setY((int) (goalie.getY() - goalie.speed));
                if (goalie.getY() < YMIN) goalie.setY(YMIN);
            }
        }
        if (goalie.getX() + 35 > ball.X + ball.centerDist) {
            if (!goalie.collidesSolid(this, allSolids, 0, (int) -goalie.speed)) {
                goalie.setX((int) (goalie.getX() - goalie.speed));
                if (goalie.getX() < XMIN) goalie.setX(XMIN);
            }
        }
        if (goalie.getX() + 35 < ball.X + ball.centerDist) {
            if (!goalie.collidesSolid(this, allSolids, 0, (int) goalie.speed)) {
                goalie.setX((int) (goalie.getX() + goalie.speed));
                if (goalie.getX() > XMAX) goalie.setX(XMAX);
            }
        }
    }

    public void runRightAI(Titan t) {
        if (t.inactiveDir == 1 && !effectPool.isRooted(t) && !effectPool.hasEffect(t, EffectId.DEAD)) {
            t.diagonalRunDir = 1;
            if (!t.collidesSolid(this, allSolids, 0, (int) t.speed)) {
                if (t.X > c.MAX_X) t.X = c.MAX_X;
                t.runningFrameCounter += 1;
                if (t.runningFrameCounter == 5) t.runningFrame = 1;
                if (t.runningFrameCounter == 10) {
                    t.runningFrame = 2;
                    t.runningFrameCounter = 0;
                }
            }
        }
    }

    public void runLeftAI(Titan t) {
        if (t.inactiveDir == 2 && !effectPool.isRooted(t) && !effectPool.hasEffect(t, EffectId.DEAD)) {
            if (!t.collidesSolid(this, allSolids, 0, (int) -t.speed)) {
                t.diagonalRunDir = 2;
                if (t.X < c.MIN_X) t.X = c.MIN_X;
                t.runningFrameCounter += 1;
                if (t.runningFrameCounter == 5) t.runningFrame = 1;
                if (t.runningFrameCounter == 10) {
                    t.runningFrame = 2;
                    t.runningFrameCounter = 0;
                }
            }
        }
    }

    public void runUpAI(Titan t) {
        if (!effectPool.isRooted(t) && !effectPool.hasEffect(t, EffectId.DEAD)) {
            if (!t.collidesSolid(this, allSolids, (int) -t.speed, 0)) {
                t.runningFrameCounter += 1;
                if (t.runningFrameCounter == 5) t.runningFrame = 1;
                if (t.runningFrameCounter == 10) {
                    t.runningFrame = 2;
                    t.runningFrameCounter = 0;

                }
            }
        }
    }

    public void runDownAI(Titan t) {
        if (!effectPool.isRooted(t)) {
            if (!t.collidesSolid(this, allSolids, (int) t.speed, 0)) {
                t.runningFrameCounter += 1;
                if (t.runningFrameCounter == 5) t.runningFrame = 1;
                if (t.runningFrameCounter == 10) {
                    t.runningFrame = 2;
                    t.runningFrameCounter = 0;
                }
            }
        }
    }

    // Movement methods with player selection
    public void runUpCtrl(Titan t) {
        if (phase == GamePhase.INGAME || phase == GamePhase.TUTORIAL) {
            boolean canRun = isActionMovementUnlocked(t);
            if (!canRun) return;
            if (!effectPool.isRooted(t)) {
                if (!t.collidesSolid(this, allSolids, (int) -t.speed, 0)) {
                    if (t.X <= ball.X) t.dirToBall = 1;
                    if (t.X > ball.X) t.dirToBall = 2;
                    if (t.diagonalRunDir == 1) t.dirToBall = 1;
                    if (t.diagonalRunDir == 2) t.dirToBall = 2;
                    if (!t.programmed) {
                        t.translateBounded(this, 0.0, -t.actualSpeed(this, 0.0));
                    }
                    t.runningFrameCounter += 1;
                    if (t.runningFrameCounter == 5) t.runningFrame = 1;
                    if (t.runningFrameCounter == 10) {
                        t.runningFrame = 2;
                        t.runningFrameCounter = 0;
                    }
                }
            }
        }
    }

    public void runDownCtrl(Titan t) {
        if (phase == GamePhase.INGAME || phase == GamePhase.TUTORIAL) {
            boolean canRun = isActionMovementUnlocked(t);
            if (!canRun) return;
            if (!effectPool.isRooted(t)) {
                if (!t.collidesSolid(this, allSolids, (int) t.speed, 0)) {
                    if (t.X <= ball.X) t.dirToBall = 1;
                    if (t.X > ball.X) t.dirToBall = 2;
                    if (t.diagonalRunDir == 1) t.dirToBall = 1;
                    if (t.diagonalRunDir == 2) t.dirToBall = 2;
                    if (!t.programmed) {
                        t.translateBounded(this, 0.0, t.actualSpeed(this, 0.0));
                    }
                    t.runningFrameCounter += 1;
                    if (t.runningFrameCounter == 5) t.runningFrame = 1;
                    if (t.runningFrameCounter == 10) {
                        t.runningFrame = 2;
                        t.runningFrameCounter = 0;
                    }
                }
            }
        }
    }

    public void runRightCtrl(Titan t) {
        if (phase == GamePhase.INGAME || phase == GamePhase.TUTORIAL) {
            boolean canRun = isActionMovementUnlocked(t);
            if (!canRun) return;
            if (!effectPool.isRooted(t)) {
                if (!t.collidesSolid(this, allSolids, 0, (int) t.speed)) {
                    if (!t.programmed) {
                        t.translateBounded(this, t.actualSpeed(this, 1.0), 0.0);
                    }
                    t.diagonalRunDir = 1;
                    t.runningFrameCounter += 1;
                    if (t.runningFrameCounter == 5) t.runningFrame = 1;
                    if (t.runningFrameCounter == 10) {
                        t.runningFrame = 2;
                        t.runningFrameCounter = 0;
                    }
                }
            }
        }
    }

    public void runLeftCtrl(Titan t) {
        if (phase == GamePhase.INGAME || phase == GamePhase.TUTORIAL) {
            boolean canRun = isActionMovementUnlocked(t);
            if (!canRun) return;
            if (!effectPool.isRooted(t)) {
                t.diagonalRunDir = 2;
                if (!t.collidesSolid(this, allSolids, 0, (int) -t.speed)) {
                    if (!t.programmed) {
                        t.translateBounded(this, -t.actualSpeed(this, -1.0), 0.0);
                    }
                    t.runningFrameCounter += 1;
                    if (t.runningFrameCounter == 5) t.runningFrame = 1;
                    if (t.runningFrameCounter == 10) {
                        t.runningFrame = 2;
                        t.runningFrameCounter = 0;
                    }
                }
            }
        }
    }

    // Effect of the kicked ball
    public void shootingBall(Titan t) throws Exception {
        //System.out.println("pow " + xKickPow + " " + yKickPow)
        if (t.actionFrame == 0) {
            ballVisible = true;
            if (!isGoalie(t)) {
                t.pushMove();
                centerBall(t);
            }
        }
        t.actionFrame += 1;
        t.kickingFrames = 20;
        if(t.actionFrame == c.SHOT_CASTLAG_FRAMES){
            t.popMove();
        }
        if (t.actionFrame < t.kickingFrames) {
            t.possession = 0;
            ballVisible = true;
            setBallFromTip();
            double D = 316.0 * t.throwPower;
            double v_tick = (D * (20 - t.actionFrame)) / 190.0;
            double stepFactor = (v_tick * 4.0) / 800.0;
            // Snapshot once — entityPool doesn't change during ball travel (no entity removal inside the loop)
            Entity[] wallSnap = entityPool.toArray(new Entity[0]);
            double speedMult = (homeGoaliePurchasedUpgrades.contains("fortress.t5.dilators") || awayGoaliePurchasedUpgrades.contains("fortress.t5.dilators")) ? c.getD("guardian.dilators.speedmult") : 1.0;
            double dxPerStep = stepFactor * xKickPow * speedMult;
            double dyPerStep = -stepFactor * yKickPow * speedMult;
            double[] vel = new double[]{ dxPerStep, dyPerStep };
            currentShotType = predictShotTrajectory(ball.X, ball.Y, vel, t.team);
            for (int i = 0; i < 800; i++) {
                currentStepVel = vel;
                if (this.phase == GamePhase.SCORE_FREEZE) {
                    break;
                }
                if (vel[0] == 0 && vel[1] == 0) {
                    break;
                }
                ball.X += vel[0];
                ball.Y += vel[1];
                intersectAll();
                if (titanInPossession().isPresent()) {
                    break;
                }
                detectGoals();
                bounceWalls(wallSnap, vel);
            }
        }
        if (t.actionFrame == t.kickingFrames) {
            t.actionFrame = 0;
            lastPossessed = null;
            // It prevents the effect at the end of the shootingState from leaving the ball beyond the margins with no more play to go back
            bounceWalls();
            t.actionState = Titan.TitanState.IDLE;
            detectGoals();
            minorHoopBounce();
        }
    }

    public void lobbingBall(Titan t) throws Exception {
        //System.out.println("pow " + xKickPow + " " + yKickPow);
        activeLobThrower = t;
        if (t.actionFrame == 0) {
            ballVisible = true;
            if (!isGoalie(t)) {
                t.pushMove();
                centerBall(t);
            }
        }
        t.actionFrame += 1;
        //System.out.println(t.actionState.toString() + t.actionFrame);
        t.kickingFrames = 20;
        if(t.actionFrame == c.LOB_CASTLAG_FRAMES){
            t.popMove();
        }
        if (t.actionFrame < t.kickingFrames) {
            t.possession = 0;
            ballVisible = true;
            setBallFromTip();
            
            double gravityMult = 1.0;
            long lowGrav = (t.team == TeamAffiliation.HOME) 
                ? homeGoalieAbilities.lowGravityUntilMs 
                : awayGoalieAbilities.lowGravityUntilMs;
            if (nowEpochMs < lowGrav) {
                gravityMult = 1.5;
            }
            double noFlyMult = 1.0;
            if (t.team == TeamAffiliation.HOME) {
                if (awayNoFlyZoneActive && t.X >= 1368.0 && t.X <= 2012.0) {
                    noFlyMult = 0.5;
                }
            } else if (t.team == TeamAffiliation.AWAY) {
                if (homeNoFlyZoneActive && t.X >= 36.0 && t.X <= 680.0) {
                    noFlyMult = 0.5;
                }
            }
            double D = 230.0 * t.throwPower * gravityMult * noFlyMult;
            double v_tick = (D * (20 - t.actionFrame)) / 190.0;
            double stepFactor = (v_tick * 4.0) / 800.0;

            // Snapshot once — entityPool doesn't change during ball travel (no entity removal inside the loop)
            Entity[] wallSnap = entityPool.toArray(new Entity[0]);
            double speedMult = (homeGoaliePurchasedUpgrades.contains("fortress.t5.dilators") || awayGoaliePurchasedUpgrades.contains("fortress.t5.dilators")) ? c.getD("guardian.dilators.speedmult") : 1.0;
            double dxPerStep = stepFactor * xKickPow * speedMult;
            double dyPerStep = -stepFactor * yKickPow * speedMult;
            double[] vel = new double[]{ dxPerStep, dyPerStep };
            currentShotType = predictShotTrajectory(ball.X, ball.Y, vel, t.team);
            for (int i = 0; i < 800; i++) {
                currentStepVel = vel;
                if (this.phase == GamePhase.SCORE_FREEZE) {
                    break;
                }
                if (vel[0] == 0 && vel[1] == 0) {
                    break;
                }
                ball.X += vel[0];
                ball.Y += vel[1];
                intersectAll();
                if (titanInPossession().isPresent()) {
                    break;
                }
                detectGoals();
                bounceWalls(wallSnap, vel);
            }
        }
        if (t.actionFrame == t.kickingFrames) {
            t.actionFrame = 0;
            activeLobThrower = null;
            lastPossessed = null;
            // It prevents the effect at the end of the shootingState from leaving the ball beyond the margins with no more play to go back
            bounceWalls();
            t.actionState = Titan.TitanState.IDLE;
            detectGoals();
            minorHoopBounce();
        }
    }

    protected void centerBall(Titan t) {
        ball.X = (int) Math.round(t.getX() + t.width / 2.0 - ball.centerDist);
        ball.Y = (int) Math.round(t.getY() + t.height / 2.0 - ball.centerDist);
    }

    public void bounceOffTitan(Titan t, double[] vel) {
        registerBallTouch(t);
        double curDx = (vel != null) ? vel[0] : (xKickPow != 0 ? xKickPow : 0);
        double curDy = (vel != null) ? vel[1] : (-yKickPow);
        CollisionMath.Bounds bBounds = ball.asBounds();
        CollisionMath.Bounds oBounds = t.asBounds();
        CollisionMath.CollisionSide side = CollisionMath.getCollisionSide(bBounds, oBounds, curDx, curDy);

        if (side == CollisionMath.CollisionSide.LEFT) {
            ball.X = oBounds.minX() - ball.width;
            xKickPow = -Math.max(5.0, Math.abs(xKickPow));
            if (vel != null) vel[0] = -Math.max(0.1, Math.abs(vel[0]));
        } else if (side == CollisionMath.CollisionSide.RIGHT) {
            ball.X = oBounds.minX() + oBounds.width();
            xKickPow = Math.max(5.0, Math.abs(xKickPow));
            if (vel != null) vel[0] = Math.max(0.1, Math.abs(vel[0]));
        } else if (side == CollisionMath.CollisionSide.TOP) {
            ball.Y = oBounds.minY() - ball.height;
            yKickPow = Math.max(5.0, Math.abs(yKickPow));
            if (vel != null) vel[1] = -Math.max(0.1, Math.abs(vel[1]));
        } else if (side == CollisionMath.CollisionSide.BOTTOM) {
            ball.Y = oBounds.minY() + oBounds.height();
            yKickPow = -Math.max(5.0, Math.abs(yKickPow));
            if (vel != null) vel[1] = Math.max(0.1, Math.abs(vel[1]));
        } else {
            double ballCX = ball.X + ball.width / 2.0;
            double titanCX = t.X + t.width / 2.0;
            double ballCY = ball.Y + ball.height / 2.0;
            double titanCY = t.Y + t.height / 2.0;
            if (Math.abs(ballCX - titanCX) > Math.abs(ballCY - titanCY)) {
                if (ballCX < titanCX) {
                    ball.X = oBounds.minX() - ball.width;
                    xKickPow = -Math.max(5.0, Math.abs(xKickPow));
                    if (vel != null) vel[0] = -Math.max(0.1, Math.abs(vel[0]));
                } else {
                    ball.X = oBounds.minX() + oBounds.width();
                    xKickPow = Math.max(5.0, Math.abs(xKickPow));
                    if (vel != null) vel[0] = Math.max(0.1, Math.abs(vel[0]));
                }
            } else {
                if (ballCY < titanCY) {
                    ball.Y = oBounds.minY() - ball.height;
                    yKickPow = Math.max(5.0, Math.abs(yKickPow));
                    if (vel != null) vel[1] = -Math.max(0.1, Math.abs(vel[1]));
                } else {
                    ball.Y = oBounds.minY() + oBounds.height();
                    yKickPow = -Math.max(5.0, Math.abs(yKickPow));
                    if (vel != null) vel[1] = Math.max(0.1, Math.abs(vel[1]));
                }
            }
        }
    }

    /** Public entry-point used for one-off bounces (post-shot, etc.). Takes its own snapshot. */
    public void bounceWalls() {
        bounceWalls(entityPool.toArray(new Entity[0]), null);
    }

    public void bounceWalls(Entity[] wallEntities) {
        bounceWalls(wallEntities, null);
    }

    /**
     * Core wall-bounce logic. Accepts a pre-computed entity snapshot and optional step velocity array [dx, dy]
     * so callers inside tight loops (the 800-step ball-travel loop) can snapshot once and update velocity dynamically
     * upon bounce.
     */
    public void bounceWalls(Entity[] wallEntities, double[] vel) {
        // Check solid sliding goalies (behave as walls during slide cast lag)
        for (Titan t : players) {
            if (t != null && t.getType() == TitanType.GOALIE && t.actionState == Titan.TitanState.A2) {
                if (t.asBounds().intersects(ball.asBounds())) {
                    bounceOffTitan(t, vel);
                    return;
                }
            }
        }

        boolean homeDead = homeGoaliePurchasedUpgrades.contains("fortress.t4.deadwalls");
        boolean awayDead = awayGoaliePurchasedUpgrades.contains("fortress.t4.deadwalls");

        for (Entity ent : wallEntities) {
            if (ent instanceof gameserver.entity.minions.Web && ent.asBounds().intersects(ball.asBounds()) && !contactExemptBall()) {
                xKickPow = 0;
                yKickPow = 0;
                if (vel != null) {
                    vel[0] = 0;
                    vel[1] = 0;
                }
                return;
            }
        }

        // 1. Check solid obstacle entities (Builder walls, Bastion walls, etc.)
        Optional<Box> coll = ball.collidesSolidWhich(this, wallEntities);
        if (coll.isPresent() && !contactExemptBall()) {
            Box obstacle = coll.get();
            double curDx = (vel != null) ? vel[0] : (xKickPow != 0 ? xKickPow : 0);
            double curDy = (vel != null) ? vel[1] : (-yKickPow);
            CollisionMath.Bounds bBounds = ball.asBounds();
            CollisionMath.Bounds oBounds = obstacle.asBounds();
            CollisionMath.CollisionSide side = CollisionMath.getCollisionSide(bBounds, oBounds, curDx, curDy);

            boolean isDeadWall = (obstacle instanceof Entity ent) &&
                    ((ent.team == TeamAffiliation.HOME && homeDead) || (ent.team == TeamAffiliation.AWAY && awayDead));

            if (isDeadWall) {
                xKickPow = 0;
                yKickPow = 0;
                if (vel != null) {
                    vel[0] = 0;
                    vel[1] = 0;
                }
                if (side == CollisionMath.CollisionSide.LEFT) {
                    ball.X = oBounds.minX() - ball.width;
                } else if (side == CollisionMath.CollisionSide.RIGHT) {
                    ball.X = oBounds.minX() + oBounds.width();
                } else if (side == CollisionMath.CollisionSide.TOP) {
                    ball.Y = oBounds.minY() - ball.height;
                } else if (side == CollisionMath.CollisionSide.BOTTOM) {
                    ball.Y = oBounds.minY() + oBounds.height();
                }
                return;
            }

            if (side == CollisionMath.CollisionSide.LEFT) {
                ball.X = oBounds.minX() - ball.width;
                xKickPow = -Math.abs(xKickPow);
                if (vel != null) vel[0] = -Math.abs(vel[0]);
            } else if (side == CollisionMath.CollisionSide.RIGHT) {
                ball.X = oBounds.minX() + oBounds.width();
                xKickPow = Math.abs(xKickPow);
                if (vel != null) vel[0] = Math.abs(vel[0]);
            } else if (side == CollisionMath.CollisionSide.TOP) {
                ball.Y = oBounds.minY() - ball.height;
                yKickPow = Math.abs(yKickPow);
                if (vel != null) vel[1] = -Math.abs(vel[1]);
            } else if (side == CollisionMath.CollisionSide.BOTTOM) {
                ball.Y = oBounds.minY() + oBounds.height();
                yKickPow = -Math.abs(yKickPow);
                if (vel != null) vel[1] = Math.abs(vel[1]);
            } else {
                if (obstacle.ballNearestEdgeisX(ball, curDx, curDy)) {
                    xKickPow = -xKickPow;
                    if (vel != null) vel[0] = -vel[0];
                } else {
                    yKickPow = -yKickPow;
                    if (vel != null) vel[1] = -vel[1];
                }
            }
        }

        // 2. Check field boundaries
        if (ball.X > c.MAX_X) {
            if (awayDead) {
                xKickPow = 0;
                yKickPow = 0;
                ball.X = c.MAX_X;
                if (vel != null) { vel[0] = 0; vel[1] = 0; }
                return;
            } else {
                ball.X = c.MAX_X;
                xKickPow = -Math.abs(xKickPow);
                if (vel != null) vel[0] = -Math.abs(vel[0]);
            }
        }
        if (ball.X < c.MIN_X) {
            if (homeDead) {
                xKickPow = 0;
                yKickPow = 0;
                ball.X = c.MIN_X;
                if (vel != null) { vel[0] = 0; vel[1] = 0; }
                return;
            } else {
                ball.X = c.MIN_X;
                xKickPow = Math.abs(xKickPow);
                if (vel != null) vel[0] = Math.abs(vel[0]);
            }
        }
        if (ball.Y < c.MIN_Y) {
            if ((ball.X <= 1024 && homeDead) || (ball.X > 1024 && awayDead)) {
                yKickPow = 0;
                xKickPow = 0;
                ball.Y = c.MIN_Y;
                if (vel != null) { vel[0] = 0; vel[1] = 0; }
                return;
            } else {
                ball.Y = c.MIN_Y;
                yKickPow = -Math.abs(yKickPow);
                if (vel != null) vel[1] = Math.abs(vel[1]);
            }
        }
        if (ball.Y > c.MAX_Y) {
            if ((ball.X <= 1024 && homeDead) || (ball.X > 1024 && awayDead)) {
                yKickPow = 0;
                xKickPow = 0;
                ball.Y = c.MAX_Y;
                if (vel != null) { vel[0] = 0; vel[1] = 0; }
                return;
            } else {
                ball.Y = c.MAX_Y;
                yKickPow = Math.abs(yKickPow);
                if (vel != null) vel[1] = -Math.abs(vel[1]);
            }
        }
    }

    protected void curve(Titan t, int sign) throws Exception {
        if (t.actionFrame == 0 && !isGoalie(t)) {
            t.pushMove();
            centerBall(t);
        }
        t.actionFrame += 1;
        t.kickingFrames = 20;
        if(t.actionFrame == c.SHOT_CASTLAG_FRAMES){
            t.popMove();
        }
        if (t.actionFrame < t.kickingFrames) {
            t.possession = 0;
            setBallFromTip();

            // Unit vectors in world coordinates (+X right, +Y down)
            double uParX = 4.0 * xKickPow;
            double uParY = -4.0 * yKickPow;
            double uPerpX = sign * (-4.0 * yKickPow);
            double uPerpY = sign * (-4.0 * xKickPow);

            // Control points matching client-side quadratic Bezier curve
            // P0 = (0, 0)
            // P1 = Q_CURVE_A * throwPower * [cos(delta)*uPar + sin(delta)*uPerp]
            // P2 = Q_CURVE_B * throwPower * uPar
            double qCurveA = 310.0 * t.throwPower;
            double qCurveB = 316.0 * t.throwPower;
            double delta = 0.97; // radians (~55.58 deg)

            double p1x = qCurveA * (Math.cos(delta) * uParX + Math.sin(delta) * uPerpX);
            double p1y = qCurveA * (Math.cos(delta) * uParY + Math.sin(delta) * uPerpY);

            double p2x = qCurveB * uParX;
            double p2y = qCurveB * uParY;

            // Frame displacement along the Bezier curve B(u) = 2u(1-u)P1 + u^2 P2
            // Weighting w_k = (20 - k) / 190.0
            int k = t.actionFrame;
            double u1 = (double) ((k - 1) * (40 - k)) / 380.0;
            double u2 = (double) (k * (39 - k)) / 380.0;
            double du = u2 - u1;
            double uBar = u1 + u2;

            double dxTick = 2.0 * du * (1.0 - uBar) * p1x + du * uBar * p2x;
            double dyTick = 2.0 * du * (1.0 - uBar) * p1y + du * uBar * p2y;

            // Snapshot once — entityPool doesn't change during ball travel (no entity removal inside the loop)
            Entity[] wallSnap = entityPool.toArray(new Entity[0]);
            double speedMult = (homeGoaliePurchasedUpgrades.contains("fortress.t5.dilators") || awayGoaliePurchasedUpgrades.contains("fortress.t5.dilators")) ? c.getD("guardian.dilators.speedmult") : 1.0;
            double dxPerStep = (dxTick / 800.0) * speedMult;
            double dyPerStep = (dyTick / 800.0) * speedMult;
            double[] vel = new double[]{ dxPerStep, dyPerStep };
            currentShotType = predictShotTrajectory(ball.X, ball.Y, vel, t.team);
            for (int i = 0; i < 800; i++) {
                currentStepVel = vel;
                if (this.phase == GamePhase.SCORE_FREEZE || !ballVisible) {
                    break;
                }
                if (vel[0] == 0 && vel[1] == 0) {
                    break;
                }
                ball.X += vel[0];
                ball.Y += vel[1];
                intersectAll();
                if (titanInPossession().isPresent()) {
                    break;
                }
                detectGoals();
                bounceWalls(wallSnap, vel);
            }
        }
        if (t.actionFrame == t.kickingFrames) {
            t.actionFrame = 0;
            lastPossessed = null;
            // It prevents the effect at the end of the shootingState from leaving the ball beyond the margins with no more play to go back
            bounceWalls();
            t.actionState = Titan.TitanState.IDLE;
            detectGoals();
            minorHoopBounce();
        }
    }

    public Optional<Titan> titanInPossession() {
        for (Titan t : players) {
            if (t.possession == 1) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }

    public void attack1(Titan t) {
        if (t.actionFrame < t.eCastFrames) {
            t.actionFrame++;
        }
        if (t.actionFrame == t.eCastFrames) {
            t.actionFrame = 0;
            t.actionState = Titan.TitanState.IDLE;
            t.popMove();
            checkAndExecuteQueuedShot(t);
        }
    }

    public void attack2(Titan t) {
        if (t.actionFrame < t.rCastFrames) {
            t.actionFrame++;
        }
        if (t.actionFrame == t.rCastFrames) {
            t.actionFrame = 0;
            t.actionState = Titan.TitanState.IDLE;
            t.popMove();
            checkAndExecuteQueuedShot(t);
        }
    }

    public void steal(Titan t) {
        if (t.actionFrame < t.sCastFrames) {
            t.actionFrame++;
        }
        if (t.actionFrame == t.sCastFrames) {
            t.actionFrame = 0;
            t.actionState = Titan.TitanState.IDLE;
            t.popMove();
            checkAndExecuteQueuedShot(t);
        }
    }

    public Titan titanSelected(PlayerDivider p) {
        if(p == null)
            return null;
        Titan t = players[p.selection - 1];
        //System.out.println( "controlling " + (t.team.toString() + t.getType()
        //+ " " + t.runUp + t.runDown + t.runLeft + t.runRight));
        return t;
    }

    public Optional<Titan> titanByID(String id) {
        for (Titan t : players) {
            if (t.id.toString().equals(id)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }

    public boolean checkWinCondition(boolean forceContinueGame) {
        int SOFT_WIN = this.options.playToIndex;
        int WIN_BY = this.options.winByIndex;
        int HARD_WIN = this.options.hardWinIndex;
        boolean over = false;
        double homeDifferential = home.score - away.score;
        if (!suddenDeath) {
            if ((home.score >= SOFT_WIN && home.score - away.score >= WIN_BY) ||
                    home.score >= HARD_WIN) {
                over = true;
            }
            if ((away.score >= SOFT_WIN && away.score - home.score >= WIN_BY) ||
                    away.score >= HARD_WIN) {
                over = true;
            }
        } else {
            if (tieAble) {
                over = true;
            } else {
                if (extremeSuddenDeath) {
                   if (home.score != away.score) {
                        over = true;
                    }
                } else {
                    if ((int) home.score != (int) away.score) {
                        over = true;
                    }
                }
            }
        }
        if (over && !forceContinueGame) {
            if (homeDifferential > 0) {
                triggerWin(home);
            } else if (homeDifferential < 0) {
                triggerWin(away);
            } else{
                triggerTie(home);
                triggerTie(away);
            }
        }
        return over;
    }

    private void resolveEndGameSidegoals() {
        // Condition 1: Game end. Any remaining uncashed sidegoals still active when game ends
        // are credited as 0.25 points to each sidegoal scorer.
        for (PlayerDivider s : activeHomeSidegoalScorers) {
            if (s != null) {
                stats.grant(s, StatEngine.StatEnum.POINTS, 0.25);
            }
        }
        activeHomeSidegoalScorers.clear();

        for (PlayerDivider s : activeAwaySidegoalScorers) {
            if (s != null) {
                stats.grant(s, StatEngine.StatEnum.POINTS, 0.25);
            }
        }
        activeAwaySidegoalScorers.clear();
    }

    void triggerWin(Team winner) {
        resolveEndGameSidegoals();
        for (PlayerDivider p : clients) {
            int winDex = p.selection;
            if (winner.which.equals(players[winDex - 1].team)) {
                p.wasVictorious = 1;
            } else {
                p.wasVictorious = -1;
            }
        }
        this.ended = true;
        if (onGameEnded != null) {
            try {
                onGameEnded.accept(this);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        gameserver.gamemanager.ServerApplication.triggerGameExpiry();
    }

    void triggerTie(Team winner) {
        resolveEndGameSidegoals();
        for (PlayerDivider p : clients) {
            int winDex = p.selection;
            if (winner.which.equals(players[winDex - 1].team)) {
                p.wasVictorious = 0;
            } else {
                p.wasVictorious = 0;
            }
        }
        this.ended = true;
        if (onGameEnded != null) {
            try {
                onGameEnded.accept(this);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        gameserver.gamemanager.ServerApplication.triggerGameExpiry();
    }

    private java.util.Map<String, Long> goalieLastAttackTime = new java.util.HashMap<>();
    private int minionWaveCount = 0;

    double[] laneCenterYs() {
        int goalLowH = c.getI("goal.low.height");
        int goalHiH  = c.getI("goal.hi.height");
        double topCenter = c.getI("goal.low.y")  + goalLowH / 2.0;
        double midCenter = c.getI("goal.hi.y")   + goalHiH  / 2.0;
        double botCenter = c.getI("goal.low2.y") + goalLowH / 2.0;
        return new double[]{ topCenter, midCenter, botCenter };
    }

    public void spawnMinion(int L, TeamAffiliation team) {
        int goalLowW = c.getI("goal.low.width");
        int goalLowH = c.getI("goal.low.height");
        int goalHiW  = c.getI("goal.hi.width");
        int goalHiH  = c.getI("goal.hi.height");

        int[] laneW = { goalLowW, goalHiW, goalLowW };
        int[] laneH = { goalLowH, goalHiH, goalLowH };

        int[] goalYs = { c.getI("goal.low.y"), c.getI("goal.hi.y"), c.getI("goal.low2.y") };

        int[] HOME_X = { c.getI("goal.home.low.x"), c.getI("goal.home.hi.x"), c.getI("goal.home.low.x") };
        int[] AWAY_X = { c.getI("goal.away.low.x"), c.getI("goal.away.hi.x"), c.getI("goal.away.low.x") };

        int spawnX = (team == TeamAffiliation.HOME) ? (HOME_X[L] + laneW[L] / 2) : (AWAY_X[L] + laneW[L] / 2);
        int spawnY = goalYs[L] + laneH[L] / 2;

        final int CAP = 200;
        int teamCount = 0;
        for (Entity e : entityPool) {
            if (e instanceof LaneMinion m && m.team == team) {
                teamCount++;
            }
        }
        if (teamCount >= CAP) return;

        double[] centers = laneCenterYs();
        double TOP_CENTER = centers[0];
        double MID_CENTER = centers[1];
        double BOT_CENTER = centers[2];
        double TOP_MID_DIV = (TOP_CENTER + MID_CENTER) / 2.0;
        double MID_BOT_DIV = (MID_CENTER + BOT_CENTER) / 2.0;

        boolean isHome = (team == TeamAffiliation.HOME);
        boolean overcharged = isHome ? (homeGoalieAbilities.overchargedWavesQueued > 0) : (awayGoalieAbilities.overchargedWavesQueued > 0);
        java.util.Set<String> upgrades = isHome ? homeGoaliePurchasedUpgrades : awayGoaliePurchasedUpgrades;

        LaneMinion m = new LaneMinion(spawnX, spawnY, team, L);
        m.health = c.getD("minion.base.health");
        m.maxHealth = m.health;

        if (overcharged) {
            m.health = c.getD("minion.base.health") * 2.0;
            m.maxHealth = m.health;
            m.damageMultiplier = 1.5;
        }

        if (upgrades.contains("empowerment.t6.bannerofcommand")) {
            int numHeroes = 0;
            for (Titan t : players) {
                if (t.health > 0.0 && t.getType() != TitanType.GOALIE && t.team == team) {
                    int tLane = 0;
                    if (t.Y >= TOP_MID_DIV && t.Y < MID_BOT_DIV) tLane = 1;
                    else if (t.Y >= MID_BOT_DIV) tLane = 2;
                    if (tLane == L) numHeroes++;
                }
            }
            m.damageMultiplier *= (1.0 + 0.30 * numHeroes);
        }

        if (upgrades.contains("siege.t5.phalanx")) {
            m.armorRatio = 1.0;
        }

        GuardianAbilities ga = (team == TeamAffiliation.HOME) ? homeGoalieAbilities : awayGoalieAbilities;
        if (upgrades.contains("siege.t3.rushlane") && ga.airSupportLane == L) {
            m.damageMultiplier *= 1.40;
        }

        entityPool.add(m);
        teamCount++;

        if (upgrades.contains("siege.t3.vanguards")) {
            boolean hasBall = false;
            for (Titan t : players) {
                if (t.team == team && t.possession == 1) {
                    hasBall = true;
                    break;
                }
            }
            boolean pastAttackingThird = false;
            for (Entity e : entityPool) {
                if (e instanceof LaneMinion && e.team == team && e.getHealth() > 0.0) {
                    if (isHome && e.X >= 1368) {
                        pastAttackingThird = true;
                        break;
                    } else if (!isHome && e.X <= 680) {
                        pastAttackingThird = true;
                        break;
                    }
                }
            }
            if (hasBall && pastAttackingThird && teamCount < CAP) {
                LaneMinion extra = new LaneMinion(spawnX, spawnY, team, L);
                extra.damageMultiplier = m.damageMultiplier;
                extra.armorRatio = m.armorRatio;
                extra.health = m.health;
                extra.maxHealth = m.maxHealth;
                entityPool.add(extra);
            }
        }
    }

    public void spawnMinionWave() {
        minionWaveCount++;
        for (int L = 0; L < 3; L++) {
            final int lane = L;
            spawnMinion(lane, TeamAffiliation.HOME);
            if (minionWaveCount == 2) {
                CompletableFuture.runAsync(() -> {
                    try {
                        if (lane != 1) {
                            Thread.sleep(2500);
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    lock();
                    try {
                        spawnMinion(lane, TeamAffiliation.AWAY);
                    } finally {
                        unlock();
                    }
                });
            } else {
                spawnMinion(lane, TeamAffiliation.AWAY);
            }
        }

        boolean homeOvercharged = homeGoalieAbilities.overchargedWavesQueued > 0;
        boolean awayOvercharged = awayGoalieAbilities.overchargedWavesQueued > 0;
        if (homeOvercharged) homeGoalieAbilities.overchargedWavesQueued--;
        if (awayOvercharged) awayGoalieAbilities.overchargedWavesQueued--;

        int goalHiW  = c.getI("goal.hi.width");
        int goalHiH  = c.getI("goal.hi.height");
        int HOME_SPAWN_X_MID = c.getI("goal.home.hi.x") + goalHiW / 2;
        int AWAY_SPAWN_X_MID = c.getI("goal.away.hi.x") + goalHiW / 2;
        int SPAWN_Y_MID = c.getI("goal.hi.y") + goalHiH / 2;

        final int HOME_CAP = 200;
        final int AWAY_CAP = 200;
        int homeCount = 0;
        int awayCount = 0;
        for (Entity e : entityPool) {
            if (e instanceof LaneMinion m) {
                if (m.team == TeamAffiliation.HOME) homeCount++;
                else awayCount++;
            }
        }

        // uninhibitedportal: constantly spawns 2 extra minions in the middle lane (lane index 1)
        if (homeGoaliePurchasedUpgrades.contains("cultivation.t6.uninhibitedportal") && homeCount < HOME_CAP - 1) {
            for (int i = 0; i < 2; i++) {
                LaneMinion extra = new LaneMinion(HOME_SPAWN_X_MID, SPAWN_Y_MID, TeamAffiliation.HOME, 1);
                extra.health = c.getD("minion.base.health");
                extra.maxHealth = extra.health;
                entityPool.add(extra);
            }
        }
        if (awayGoaliePurchasedUpgrades.contains("cultivation.t6.uninhibitedportal") && awayCount < AWAY_CAP - 1) {
            for (int i = 0; i < 2; i++) {
                LaneMinion extra = new LaneMinion(AWAY_SPAWN_X_MID, SPAWN_Y_MID, TeamAffiliation.AWAY, 1);
                extra.health = c.getD("minion.base.health");
                extra.maxHealth = extra.health;
                entityPool.add(extra);
            }
        }
    }

protected void tickLaneMinions() {
    for (int L = 0; L < 3; L++) {
        long now = nowEpochMs;
        getHomeLaneBonusesList().get(L).removeIf(b -> now >= b.expiryMs);
        getAwayLaneBonusesList().get(L).removeIf(b -> now >= b.expiryMs);
        
        int hSum = 0;
        long maxHExpiry = 0;
        for (LaneBonus b : getHomeLaneBonusesList().get(L)) {
            hSum += b.amount;
            if (b.expiryMs > maxHExpiry) maxHExpiry = b.expiryMs;
        }
        homeLaneBonusValue[L] = hSum;
        homeLaneBonusEndTime[L] = maxHExpiry;
        
        int aSum = 0;
        long maxAExpiry = 0;
        for (LaneBonus b : getAwayLaneBonusesList().get(L)) {
            aSum += b.amount;
            if (b.expiryMs > maxAExpiry) maxAExpiry = b.expiryMs;
        }
        awayLaneBonusValue[L] = aSum;
        awayLaneBonusEndTime[L] = maxAExpiry;
    }

    // Lane centers, corrected for top-left-justified goal coords (corner + half dimension)
    double[] centers = laneCenterYs();
    final double TOP_CENTER = centers[0];
    final double MID_CENTER = centers[1];
    final double BOT_CENTER = centers[2];

    // Lane boundaries (midpoints)
    final double TOP_MID_DIV = (TOP_CENTER + MID_CENTER) / 2.0;
    final double MID_BOT_DIV = (MID_CENTER + BOT_CENTER) / 2.0;

    // Minion speed & damage
    boolean hasDilators = homeGoaliePurchasedUpgrades.contains("fortress.t5.dilators") ||
                          awayGoaliePurchasedUpgrades.contains("fortress.t5.dilators");
    double minionSpeed = hasDilators ? 1.25 * c.getD("guardian.dilators.speedmult") : 1.25;
    double minionDmg   = 0.05;
    double titanDmg    = 0.05;
    double fightRange  = 45.0;

    // Collect minions by lane into reused bucket lists (cleared each tick)
    hmL0.clear(); hmL1.clear(); hmL2.clear();
    amL0.clear(); amL1.clear(); amL2.clear();

    for (Entity e : entityPool) {
        if (e instanceof LaneMinion && e.getHealth() > 0.0) {
            LaneMinion m = (LaneMinion) e;
            if (m.laneIndex == 0) {
                if (m.team == TeamAffiliation.HOME) hmL0.add(m); else amL0.add(m);
            } else if (m.laneIndex == 1) {
                if (m.team == TeamAffiliation.HOME) hmL1.add(m); else amL1.add(m);
            } else if (m.laneIndex == 2) {
                if (m.team == TeamAffiliation.HOME) hmL2.add(m); else amL2.add(m);
            }
        }
    }

    // Process each lane independently
    for (int L = 0; L < 3; L++) {

        List<LaneMinion> homeInLane = homeMinionsReused.get(L);
        List<LaneMinion> awayInLane = awayMinionsReused.get(L);

        // Sort so front minions are at the front
        homeInLane.sort((a, b) -> Double.compare(b.X, a.X)); // home moves +X, front = largest X
        awayInLane.sort((a, b) -> Double.compare(a.X, b.X)); // away moves -X, front = smallest X

        double laneCenterY =
            (L == 0 ? TOP_CENTER :
             L == 1 ? MID_CENTER :
                      BOT_CENTER);

        double curHomeSpeed = getLaneMinionSpeed(L, TeamAffiliation.HOME, minionSpeed, homeInLane.size(), awayInLane.size(), 1.0);
        double curAwaySpeed = getLaneMinionSpeed(L, TeamAffiliation.AWAY, minionSpeed, homeInLane.size(), awayInLane.size(), -1.0);

        // HOME MINIONS
        // Every minion independently targets its nearest live enemy - multiple attackers can
        // pile onto the same target (3v1 etc. is intended), but any minion without a target in
        // range is physically blocked from crossing past the frontmost living enemy in the lane.
        for (LaneMinion h : homeInLane) {
            LaneMinion target = findNearestEnemyMinion(h.X, awayInLane, fightRange + 120.0);
            if (target != null) {
                double dmg = minionDmg * h.damageMultiplier;
                if (awayGoaliePurchasedUpgrades.contains("siege.t5.phalanx")) {
                    int adjCount = 0;
                    for (LaneMinion f : awayInLane) {
                        if (f != target && Math.abs(f.X - target.X) <= 100.0) {
                            adjCount++;
                        }
                    }
                    dmg *= Math.max(0.1, 1.0 - 0.1 * adjCount);
                }
                target.health -= dmg;
                continue; // engaged: hold position, never push through the enemy line
            }
            if (!awayInLane.isEmpty()) {
                // Enemy wave still exists in this lane (just outside fightRange) - advance,
                // but clamp so we can never cross past the frontmost living enemy.
                double frontEnemyX = awayInLane.get(0).X;
                h.X = Math.min(h.X + curHomeSpeed, frontEnemyX - fightRange);
            } else {
                // Lane is fully clear of enemy minions - free to push toward the goal/titans.
                h.X += curHomeSpeed;
                Titan t = findNearestTitanInLane(h.X, h.Y, TeamAffiliation.AWAY,
                                                 TOP_CENTER, MID_CENTER, BOT_CENTER,
                                                 TOP_MID_DIV, MID_BOT_DIV);
                if (t != null) t.damage(this, titanDmg * h.damageMultiplier);
            }
            if (h.X >= 1780) {
                h.health = 0;
                getHomeLaneBonusesList().get(L).add(new LaneBonus(nowEpochMs + c.getI("guardian.crashbonus.lifetime"), c.getI("guardian.crashbonus.amount")));
            }
        }

        // AWAY MINIONS - mirror of home logic
        for (LaneMinion a : awayInLane) {
            LaneMinion target = findNearestEnemyMinion(a.X, homeInLane, fightRange + 120.0);
            if (target != null) {
                double dmg = minionDmg * a.damageMultiplier;
                if (homeGoaliePurchasedUpgrades.contains("siege.t5.phalanx")) {
                    int adjCount = 0;
                    for (LaneMinion f : homeInLane) {
                        if (f != target && Math.abs(f.X - target.X) <= 100.0) {
                            adjCount++;
                        }
                    }
                    dmg *= Math.max(0.1, 1.0 - 0.1 * adjCount);
                }
                target.health -= dmg;
                continue;
            }
            if (!homeInLane.isEmpty()) {
                double frontEnemyX = homeInLane.get(0).X;
                a.X = Math.max(a.X - curAwaySpeed, frontEnemyX + fightRange);
            } else {
                a.X -= curAwaySpeed;
                Titan t = findNearestTitanInLane(a.X, a.Y, TeamAffiliation.HOME,
                                                 TOP_CENTER, MID_CENTER, BOT_CENTER,
                                                 TOP_MID_DIV, MID_BOT_DIV);
                if (t != null) t.damage(this, titanDmg * a.damageMultiplier);
            }
            if (a.X <= 300) {
                a.health = 0;
                getAwayLaneBonusesList().get(L).add(new LaneBonus(nowEpochMs + c.getI("guardian.crashbonus.lifetime"), c.getI("guardian.crashbonus.amount")));
            }
        }

        // Apply vertical spacing — only if X values are within range
        separateMinionsVertically(homeInLane, 45.0, laneCenterY);
        separateMinionsVertically(awayInLane, 45.0, laneCenterY);
    }
}
    private LaneMinion findNearestEnemyMinion(double x, List<LaneMinion> enemies, double maxRange) {
        LaneMinion nearest = null;
        double minDist = maxRange;

        for (LaneMinion e : enemies) {
            if (e.health <= 0.0) continue;
            double dist = Math.abs(e.X - x);
            if (dist <= minDist) {
                minDist = dist;
                nearest = e;
            }
        }
        return nearest;
    }

    private void separateMinionsVertically(List<LaneMinion> list, double minSpacing, double laneCenterY) {
        int size = list.size();
        if (size == 0) return;
        if (size == 1) {
            list.get(0).Y = laneCenterY;
            return;
        }

        final double COL_SPACING_X = 50.0;
        final double GROUP_X_THRESHOLD = 65.0;
        final int MAX_GROUP_SIZE = 5;

        int groupStart = 0;
        for (int i = 1; i <= size; i++) {
            if (i == size || Math.abs(list.get(i).X - list.get(i - 1).X) > GROUP_X_THRESHOLD) {
                int groupLen = i - groupStart;
                if (groupLen == 1) {
                    list.get(groupStart).Y = laneCenterY;
                } else {
                    double frontX = list.get(groupStart).X;
                    int centerSlot = MAX_GROUP_SIZE / 2;
                    for (int j = 0; j < groupLen; j++) {
                        LaneMinion m = list.get(groupStart + j);
                        int col = j / MAX_GROUP_SIZE;
                        int slot = j % MAX_GROUP_SIZE;
                        m.Y = laneCenterY + (slot - centerSlot) * minSpacing;
                        if (m.team == TeamAffiliation.HOME) {
                            m.X = frontX - col * COL_SPACING_X;
                        } else {
                            m.X = frontX + col * COL_SPACING_X;
                        }
                    }
                }
                groupStart = i;
            }
        }
    }

    public int getLaneFromY(double y) {
        double TOP_CENTER = c.getI("goal.low.y") + c.getI("goal.low.height") / 2.0;
        double BOT_CENTER = c.getI("goal.low2.y") + c.getI("goal.low.height") / 2.0;
        double MID_CENTER = c.getI("goal.hi.y") + c.getI("goal.hi.height") / 2.0;
        double TOP_MID_DIV = (TOP_CENTER + MID_CENTER) / 2.0;
        double MID_BOT_DIV = (MID_CENTER + BOT_CENTER) / 2.0;
        if (y <= TOP_MID_DIV) return 0;
        else if (y <= MID_BOT_DIV) return 1;
        else return 2;
    }

    private Titan findNearestTitanInLane(
            double x, double y, TeamAffiliation team,
            double TOP_CENTER, double MID_CENTER, double BOT_CENTER,
            double TOP_MID_DIV, double MID_BOT_DIV) {

        // Determine lane from Y
        int laneIndex;
        if (y <= TOP_MID_DIV) laneIndex = 0;
        else if (y <= MID_BOT_DIV) laneIndex = 1;
        else laneIndex = 2;

        double laneMinY, laneMaxY;

        if (laneIndex == 0) {
            laneMinY = 0;
            laneMaxY = TOP_MID_DIV;
        } else if (laneIndex == 1) {
            laneMinY = TOP_MID_DIV;
            laneMaxY = MID_BOT_DIV;
        } else {
            laneMinY = MID_BOT_DIV;
            laneMaxY = 2000;
        }

        Titan nearest = null;
        double minDist = 150.0;

        for (Titan t : players) {
            if (t.team != team || t.health <= 0.0) continue;

            double ty = t.Y + 35;

            if (ty < laneMinY || ty > laneMaxY) continue;

            double dist = Math.abs((t.X + 35) - x);
            if (dist < minDist) {
                minDist = dist;
                nearest = t;
            }
        }

        return nearest;
    }

        
    void handleGoalieAttackClick(String email, double clickX, double clickY,
                                         TeamAffiliation goalieTeam, Titan goalie) {

        // Cooldown check
        if (effectPool.hasEffect(goalie, EffectId.COOLDOWN_GOALIE)) {
            return;
        }

        // Goalie elliptical range check
        double gx = goalie.X + goalie.width / 2.0;
        double gy = goalie.Y + goalie.height / 2.0;
        double rangeX = c.getI("titan.goalie.rangex") * goalie.rangeFactor;
        double rangeY = c.getI("titan.goalie.rangey") * goalie.rangeFactor;

        Entity target = null;
        boolean isDragon = false;
        double minDist = 45.0;

        for (Entity e : entityPool) {
            if (e instanceof LaneMinion) {
                LaneMinion m = (LaneMinion) e;
                if (m.getHealth() <= 0.0) continue;

                double dx = m.X - gx;
                double dy = m.Y - gy;
                if ((dx * dx) / (rangeX * rangeX) + (dy * dy) / (rangeY * rangeY) > 1.0) continue;

                double dist = util.Util.dist(m.X, m.Y, clickX, clickY);
                if (dist < minDist) {
                    minDist = dist;
                    target = m;
                    isDragon = false;
                }
            } else if (e instanceof gameserver.entity.minions.Dragon) {
                gameserver.entity.minions.Dragon d = (gameserver.entity.minions.Dragon) e;
                if (d.getHealth() <= 0.0) continue;

                double dx = (d.X + d.width / 2.0) - gx;
                double dy = (d.Y + d.height / 2.0) - gy;
                if ((dx * dx) / (rangeX * rangeX) + (dy * dy) / (rangeY * rangeY) > 1.0) continue;

                double dist = util.Util.dist(d.X + d.width / 2.0, d.Y + d.height / 2.0, clickX, clickY);
                if (dist < minDist + 30.0) {
                    minDist = dist;
                    target = d;
                    isDragon = true;
                }
            }
        }

        if (target != null) {
            effectPool.addUniqueEffect(
                new EmptyEffect(500, goalie, EffectId.COOLDOWN_GOALIE),
                this
            );

            double dmg = c.getD("goalie.click.damage");
            GuardianAbilities ga = (goalieTeam == TeamAffiliation.HOME) ? homeGoalieAbilities : awayGoalieAbilities;
            Set<String> goaliePurchased = (goalieTeam == TeamAffiliation.HOME) ? homeGoaliePurchasedUpgrades : awayGoaliePurchasedUpgrades;

            int targetLane = isDragon ? 2 : ((LaneMinion) target).laneIndex;
            if (goaliePurchased.contains("siege.t3.rushlane") && ga.airSupportLane == targetLane) {
                dmg *= 1.40;
            }

            if (isDragon) {
                gameserver.entity.minions.Dragon d = (gameserver.entity.minions.Dragon) target;
                stats.grant(this, goalie, StatEngine.StatEnum.MINIONDAMAGE, dmg);
                d.damage(this, dmg, goalieTeam);
                if (d.getHealth() <= 0.0) {
                    stats.grant(this, goalie, StatEngine.StatEnum.LASTHITS);
                }
            } else {
                LaneMinion m = (LaneMinion) target;
                if (m.team != goalieTeam) {
                    stats.grant(this, goalie, StatEngine.StatEnum.MINIONDAMAGE, dmg);
                } else {
                    dmg *= 0.5;
                }
                m.health -= dmg;
                if (m.health <= 0.0) {
                    m.health = 0.0;
                    if (m.team != goalieTeam) {
                        stats.grant(this, goalie, StatEngine.StatEnum.LASTHITS);
                        if (goalieTeam == TeamAffiliation.HOME) {
                            homeGoalieCurrency += 5.0;
                        } else {
                            awayGoalieCurrency += 5.0;
                        }
                    }
                }
            }
        }
    }

    private void tickGoalieGold() {
        double ticksPerSec = 1000.0 / Math.max(1, GAMETICK_MS);
        double goldTrickleRate = 5.0 / (3.0 * ticksPerSec);
        homeGoalieCurrency += goldTrickleRate;
        awayGoalieCurrency += goldTrickleRate;
    }

    private void tickGoalieMana() {
        if (homeGoaliePurchasedUpgrades.contains("cultivation.t1.manawell")) {
            double rate = 0.025;
            if (homeGoaliePurchasedUpgrades.contains("cultivation.t3.manacompounding")) {
                rate *= (1.0 + (homeGoalieMana / 500.0) * 0.5);
            }
            if (homeGoaliePurchasedUpgrades.contains("cultivation.t3.tollcollector")) {
                int favored = countFavoredLanes(TeamAffiliation.HOME);
                rate *= (1.0 + 0.20 * favored);
            }
            rate *= homeGoalieAbilities.manaRateMultiplier;
            homeGoalieMana = Math.min(homeGoalieAbilities.getMaxMana(this), homeGoalieMana + rate);
        }
        if (awayGoaliePurchasedUpgrades.contains("cultivation.t1.manawell")) {
            double rate = 0.025;
            if (awayGoaliePurchasedUpgrades.contains("cultivation.t3.manacompounding")) {
                rate *= (1.0 + (awayGoalieMana / 500.0) * 0.5);
            }
            if (awayGoaliePurchasedUpgrades.contains("cultivation.t3.tollcollector")) {
                int favored = countFavoredLanes(TeamAffiliation.AWAY);
                rate *= (1.0 + 0.20 * favored);
            }
            rate *= awayGoalieAbilities.manaRateMultiplier;
            awayGoalieMana = Math.min(awayGoalieAbilities.getMaxMana(this), awayGoalieMana + rate);
        }
    }

    private int countFavoredLanes(TeamAffiliation team) {
        int favored = 0;
        for (int L = 0; L < 3; L++) {
            int homeCount = 0;
            int awayCount = 0;
            for (Entity entity : entityPool) {
                if (entity instanceof LaneMinion && entity.getHealth() > 0.0 && ((LaneMinion) entity).laneIndex == L) {
                    if (entity.team == TeamAffiliation.HOME) homeCount++;
                    else awayCount++;
                }
            }
            double[] YS = laneCenterYs();
            double TOP_MID_DIV = (YS[0] + YS[1]) / 2.0;
            double MID_BOT_DIV = (YS[1] + YS[2]) / 2.0;

            int homeBonus = (homeLaneBonusEndTime[L] > nowEpochMs) ? 3 : 0;
            int awayBonus = (awayLaneBonusEndTime[L] > nowEpochMs) ? 3 : 0;
            int net = (team == TeamAffiliation.HOME)
                ? (homeCount + homeBonus - (awayCount + awayBonus))
                : (awayCount + awayBonus - (homeCount + homeBonus));
            if (net > 0) favored++;
        }
        return favored;
    }

    private transient int framesSinceDragonDead = 0;

    private void checkDragonSpawning() {
        if (dragonSpawned) {
            dragonPreIndicatorActive = false;
            return;
        }
        boolean hasBreath = homeGoaliePurchasedUpgrades.contains("empowerment.t6.dragonsbreath") ||
                            awayGoaliePurchasedUpgrades.contains("empowerment.t6.dragonsbreath");
        if (!hasBreath) {
            framesSinceDragonDead = 0;
            dragonPreIndicatorActive = false;
            return;
        }

        framesSinceDragonDead++;
        double ticksPerSec = 1000.0 / Math.max(1, GAMETICK_MS);
        int delayFrames = (int) (10.0 * ticksPerSec);
        int preIndicatorStart = delayFrames - (int) (2.0 * ticksPerSec);
        dragonPreIndicatorActive = (framesSinceDragonDead >= preIndicatorStart);
        if (framesSinceDragonDead >= delayFrames) {
            int botHoopCY = (int) (c.getI("goal.low2.y") + c.getI("goal.low.height") / 2.0);
            gameserver.entity.minions.Dragon d = new gameserver.entity.minions.Dragon(1024 - 60, botHoopCY - 60);
            entityPool.add(d);
            dragonSpawned = true;
            dragonPreIndicatorActive = false;
            framesSinceDragonDead = 0;
        }
    }

    // Removed applyUninhibitedPortalForce

    public double getLaneMinionSpeed(int L, TeamAffiliation team, double baseSpeed) {
        return getLaneMinionSpeed(L, team, baseSpeed, 0.0);
    }

    public double getLaneMinionSpeed(int L, TeamAffiliation team, double baseSpeed, double dirX) {
        int homeCount = 0;
        int awayCount = 0;
        for (Entity entity : entityPool) {
            if (entity instanceof LaneMinion && entity.getHealth() > 0.0 && ((LaneMinion) entity).laneIndex == L) {
                if (entity.team == TeamAffiliation.HOME) homeCount++;
                else awayCount++;
            }
        }
        return getLaneMinionSpeed(L, team, baseSpeed, homeCount, awayCount, dirX);
    }

    public double getLaneMinionSpeed(int L, TeamAffiliation team, double baseSpeed, int homeCount, int awayCount) {
        double defaultDir = (team == TeamAffiliation.HOME) ? 1.0 : -1.0;
        return getLaneMinionSpeed(L, team, baseSpeed, homeCount, awayCount, defaultDir);
    }

    public double getLaneMinionSpeed(int L, TeamAffiliation team, double baseSpeed, int homeCount, int awayCount, double dirX) {
        int homeBonus = homeLaneBonusValue[L];
        int awayBonus = awayLaneBonusValue[L];

        int softCap = (c != null) ? (int) c.LANE_ADVANTAGE_SOFTCAP : 15;
        int hardCap = (c != null) ? (int) c.LANE_ADVANTAGE_HARDCAP : 30;
        double unilateralFactor = (c != null) ? c.LANE_ADVANTAGE_UNILATERAL_FACTOR : 0.0050;
        double directionalFactor = (c != null) ? c.LANE_ADVANTAGE_DIRECTIONAL_FACTOR : 0.01;
        double maxPressureMultiplier = (c != null) ? c.MAXIMUM_PRESSURE_MULTIPLIER : 2.0;

        int netMinionsHome = homeCount - awayCount;
        if (netMinionsHome > softCap) netMinionsHome = softCap;
        if (netMinionsHome < -softCap) netMinionsHome = -softCap;

        int netBonusHome = homeBonus - awayBonus;
        int P_home = netMinionsHome + netBonusHome;
        if (P_home > hardCap) P_home = hardCap;
        if (P_home < -hardCap) P_home = -hardCap;

        int P_team = (team == TeamAffiliation.HOME) ? P_home : -P_home;

        // 1. Unilateral Boost
        double unilateralBoost = P_team * unilateralFactor;
        if (unilateralBoost > 0) {
            boolean maxPressure = (team == TeamAffiliation.HOME)
                    ? homeGoaliePurchasedUpgrades.contains("siege.t6.maximumpressure")
                    : awayGoaliePurchasedUpgrades.contains("siege.t6.maximumpressure");
            if (maxPressure && P_team > 5) {
                unilateralBoost *= maxPressureMultiplier;
            }
        } else if (unilateralBoost < 0) {
            boolean impenetrable = (team == TeamAffiliation.HOME)
                    ? homeGoaliePurchasedUpgrades.contains("fortress.t6.impenetrable")
                    : awayGoaliePurchasedUpgrades.contains("fortress.t6.impenetrable");
            if (impenetrable && P_team < -3) {
                unilateralBoost = -3 * unilateralFactor;
            }
        }

        // 2. Directional Hill Effect
        double dirSign = (dirX > 0) ? 1.0 : ((dirX < 0) ? -1.0 : 0.0);
        double hillEffect = dirSign * P_home * directionalFactor;

        if (hillEffect < 0) {
            long insuranceUntil = (team == TeamAffiliation.HOME)
                    ? homeGoalieAbilities.fastBreakUntilMs
                    : awayGoalieAbilities.fastBreakUntilMs;
            if (nowEpochMs < insuranceUntil) {
                hillEffect = 0.0;
            }
        }

        double speed = baseSpeed * (1.0 + unilateralBoost + hillEffect);
        return Math.max(0.2, speed);
    }

    private void checkTurnovers() {
        TeamAffiliation currentPossessionTeam = TeamAffiliation.UNAFFILIATED;
        for (Titan t : players) {
            if (t.possession == 1) {
                currentPossessionTeam = t.team;
                break;
            }
        }
        if (currentPossessionTeam != lastPossessionTeam && currentPossessionTeam != TeamAffiliation.UNAFFILIATED) {
            if (lastPossessionTeam == TeamAffiliation.HOME) {
                if (homeGoaliePurchasedUpgrades.contains("fortress.t3.fastbreakinsurance")) {
                    homeGoalieAbilities.fastBreakUntilMs = nowEpochMs + 5000;
                }
            } else if (lastPossessionTeam == TeamAffiliation.AWAY) {
                if (awayGoaliePurchasedUpgrades.contains("fortress.t3.fastbreakinsurance")) {
                    awayGoalieAbilities.fastBreakUntilMs = nowEpochMs + 5000;
                }
            }
            lastPossessionTeam = currentPossessionTeam;
        }
    }

    public double homeWinBy() {
        return home.score - away.score;
    }

    public void setClients(List<PlayerDivider> players) {
        this.clients = players;
    }

    protected class TerminableExecutor implements Runnable {
        GameEngine context;
        ScheduledExecutorService exec;

        TerminableExecutor(GameEngine gm, ScheduledExecutorService exec) {
            this.context = gm;
            this.exec = exec;
        }

        @Override
        public void run() {
            if (context.ended) {
                System.out.println("suspending game thread");
                exec.shutdown();
                gameserver.gamemanager.ServerApplication.triggerGameExpiry();
            } else {
                try {
                    context.gameTick();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
