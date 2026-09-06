package gameserver.engine;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gameserver.TutorialOverrides;
import gameserver.effects.EffectId;
import gameserver.effects.cooldowns.CooldownCurve;
import gameserver.effects.effects.Effect;
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
    private transient Titan activeLobThrower = null;

    // Tracks the active sidegoal scorers until a center goal is cashed in (combo goal)
    // or the ghost points are rounded away by the enemy team.
    @com.fasterxml.jackson.annotation.JsonIgnore
    public transient final List<PlayerDivider> activeHomeSidegoalScorers = new ArrayList<>();
    @com.fasterxml.jackson.annotation.JsonIgnore
    public transient final List<PlayerDivider> activeAwaySidegoalScorers = new ArrayList<>();

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
                delta *= e.painReduction;
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

    public boolean ballIntersectsEllipse(GoalHoop goal) {
        gameserver.engine.CollisionMath.EllipseData g = goal.ellipseData();
        gameserver.engine.CollisionMath.EllipseData b = ball.ellipseData();
        return gameserver.engine.CollisionMath.ellipseBoundsIntersect(b, g);
    }


    public void detectGoals() {
        if (this.phase == GamePhase.SCORE_FREEZE || !ballVisible) {
            return;
        }
        // NOTE: contactExemptBall() is intentionally NOT checked here.
        // The lob uncatchable window must never block goal detection — a ball
        // in the air can and should score. Only intersectAll() (player catching)
        // is gated by contactExemptBall().
        for (GoalHoop goal : this.lowGoals) {
            if (ballIntersectsEllipse(goal) && goal.checkReady()) {
                Optional<Titan> possessor = titanInPossession();
                if (possessor.isPresent() && possessor.get().getType() == TitanType.GOALIE && possessor.get().team == goal.team) {
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
                goal.trigger();
                Titan defGoalie = (goal.team == TeamAffiliation.HOME) ? players[0] : players[1];
                stats.grant(this, defGoalie, StatEngine.StatEnum.SIDEGOALS_CONCEDED);
                UUID attackerId = (us.which == TeamAffiliation.HOME) ? lastHomePossessor : lastAwayPossessor;
                PlayerDivider scorer = (attackerId != null) ? clientFromTitan(titanByID(attackerId.toString()).orElse(null)) : getPossessorOrThrower();
                if (scorer != null) {
                    stats.grant(scorer, StatEngine.StatEnum.SIDEGOALS);
                    ourSideScorers.add(scorer);
                }
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
                if (possessor.isPresent() && possessor.get().getType() == TitanType.GOALIE && possessor.get().team == goal.team) {
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
                PlayerDivider scorerHi = (attackerIdHi != null) ? clientFromTitan(titanByID(attackerIdHi.toString()).orElse(null)) : getPossessorOrThrower();
                if (scorerHi != null) {
                    stats.grant(scorerHi, StatEngine.StatEnum.GOALS);
                    // Combo goal credit: 1 + (0.5 * n) points credit goes to center goal scorer
                    double centerPoints = 1.0 + (0.5 * nSidegoals);
                    stats.grant(scorerHi, StatEngine.StatEnum.POINTS, centerPoints);
                }
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

    protected void minorHoopBounce() {
        gameserver.engine.CollisionMath.Bounds ballBounds = ball.asBounds();
        for (GoalHoop goal : this.lowGoals) {
            gameserver.engine.CollisionMath.EllipseData ell = goal.ellipseData();
            gameserver.engine.CollisionMath.Bounds ellBounds = new gameserver.engine.CollisionMath.Bounds(ell.centerX() - ell.radiusX(), ell.centerY() - ell.radiusY(), ell.radiusX() * 2, ell.radiusY() * 2);
            while (ellBounds.intersects(ballBounds)) {
                ballBounds = ball.asBounds();
                double ang = Util.degreesFromCoords(
                        ell.centerX() - ball.X - ball.centerDist,
                        ell.centerY() - ball.Y - ball.centerDist
                );
                ang += 180; //Kick it away, not towards
                double dx = Math.cos(Math.toRadians((ang)));
                double dy = Math.sin(Math.toRadians((ang)));
                ball.X += dx;
                ball.Y += dy;
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
        List<TitanType> playableTypes = Arrays.stream(TitanType.values())
            .filter(t -> t != TitanType.GOALIE && t != TitanType.ANY && t != TitanType.NOT_GUARDIAN && t != TitanType.ANY_ENTITY)
            .collect(Collectors.toList());
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
                goalie.aiGoalieBuildOrder = GOALIE_BUILD_PRESETS.get(rng.nextInt(GOALIE_BUILD_PRESETS.size()));
                goalie.aiGoalieBuildIndex = 0;
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
        if(contactExemptBall()){
            return;
        }
        for (int n = players.length - 1; n >= 0; n--) {
            intersectBall(n + 1, (int) players[n].X, (int) players[n].Y);
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
        return false;
    }

    public boolean anyClientSelected(int n) {
        for (PlayerDivider p : clients) {
            if (p.selection == n) {
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
                if (request.posX != -1 && request.posY != -1 && btn != 0) {
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
            ScheduledExecutorService exec = Executors.newScheduledThreadPool(1);
            TerminableExecutor terminableExecutor = new TerminableExecutor(this, exec);
            exec.scheduleAtFixedRate(terminableExecutor, 0, GAMETICK_MS, TimeUnit.MILLISECONDS);

            System.out.println("gametick kickoff should only run once");
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

    private static final Map<String, String> TREE_SHORT_NAME = Map.of(
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

    private boolean tierPrereqMet(String shortName, String tier, Set<String> purchased) {
        int n = Integer.parseInt(tier.substring(1)); // "t3" -> 3
        if (n <= 1) return true; // t1 has no prereq

        String prevTier = "t" + (n - 1);
        if (!tierPrereqMet(shortName, prevTier, purchased)) return false;

        int required = REQUIRED_TECHS.get(shortName)[n - 2]; // index 0 = t1->t2 gate
        return countInTier(purchased, shortName, prevTier) >= required;
    }

    private void handleGoalieTreePurchase(Titan t, String treeKey, String nodeKey) {
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
                stats.grant(this, t, StatEngine.StatEnum.UPGRADESGOLD, cost);
            } else if (hasUse) {
                stats.grant(this, t, StatEngine.StatEnum.CONSUMABLESGOLD, cost);
            }
        }
        if (hasCost) {
            purchased.add(nodeKey);
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

    public static final List<List<String>> GOALIE_BUILD_PRESETS = List.of(
        List.of("siege.t1.siegedoctrine", "siege.t3.ballportal", "siege.t3.vanguards", "siege.t4.accumulators", "siege.t5.saveprogress", "siege.t6.forwardmedics", "empowerment.t1.combinecontract", "empowerment.t3.marksmanship", "empowerment.t3.footwork", "empowerment.t4.heroportals", "empowerment.t5.clutchgene", "empowerment.t6.apexform", "siege.t5.wallsdown"),
        List.of("cultivation.t1.manawell", "cultivation.t3.manacompounding", "cultivation.t3.highermanacap", "cultivation.t2.manainfusion", "cultivation.t4.manafrenzy", "fortress.t1.homeward", "fortress.t3.biggermodels", "fortress.t3.snaretrap", "fortress.t4.barrage", "fortress.t5.icebarrage", "fortress.t6.deepfreeze", "fortress.t5.emergencybarrier"),
        List.of("cultivation.t1.manawell", "cultivation.t3.manacompounding", "cultivation.t3.highermanacap", "cultivation.t2.manainfusion", "cultivation.t4.manavines", "cultivation.t5.manapollinate", "siege.t5.phalanx", "cultivation.t6.uninhibitedportal", "cultivation.t5.manasummon", "cultivation.t5.manasummon", "cultivation.t5.manasummon")
    );

    private static String getTreeKeyForNode(String nodeKey) {
        if (nodeKey == null) return null;
        if (nodeKey.startsWith("siege.")) return "GOALIE_TREE_SIEGE";
        if (nodeKey.startsWith("fortress.")) return "GOALIE_TREE_FORTRESS";
        if (nodeKey.startsWith("empowerment.")) return "GOALIE_TREE_EMPOWERMENT";
        if (nodeKey.startsWith("cultivation.")) return "GOALIE_TREE_CULTIVATION";
        return null;
    }

    private void tickAiGoalieBuildOrder(Titan ai) {
        if (ai.aiGoalieBuildOrder == null) {
            java.util.Random r = new java.util.Random();
            ai.aiGoalieBuildOrder = GOALIE_BUILD_PRESETS.get(r.nextInt(GOALIE_BUILD_PRESETS.size()));
            ai.aiGoalieBuildIndex = 0;
        }
        if (ai.aiGoalieBuildIndex >= ai.aiGoalieBuildOrder.size()) {
            return;
        }
        String nodeKey = ai.aiGoalieBuildOrder.get(ai.aiGoalieBuildIndex);
        String treeKey = getTreeKeyForNode(nodeKey);
        if (treeKey == null) {
            ai.aiGoalieBuildIndex++;
            return;
        }
        boolean isHome = (ai.team == TeamAffiliation.HOME);
        Set<String> purchased = isHome ? homeGoaliePurchasedUpgrades : awayGoaliePurchasedUpgrades;
        boolean hasCost = costs.hasKey(nodeKey + ".cost") || costs.hasKey(nodeKey + ".cost.mana");
        if (hasCost && purchased.contains(nodeKey)) {
            ai.aiGoalieBuildIndex++;
            return;
        }
        boolean isMana = costs.hasKey(nodeKey + ".cost.mana") || costs.hasKey(nodeKey + ".use.mana");
        String costKey = hasCost
            ? (isMana ? (costs.hasKey(nodeKey + ".cost.mana") ? nodeKey + ".cost.mana" : nodeKey + ".cost") : (costs.hasKey(nodeKey + ".cost") ? nodeKey + ".cost" : nodeKey + ".cost.mana"))
            : (isMana ? (costs.hasKey(nodeKey + ".use.mana") ? nodeKey + ".use.mana" : nodeKey + ".use") : (costs.hasKey(nodeKey + ".use") ? nodeKey + ".use" : nodeKey + ".use.mana"));
        if (!costs.hasKey(costKey)) {
            ai.aiGoalieBuildIndex++;
            return;
        }
        double cost = costs.getD(costKey);
        double currentBalance = isMana
            ? (isHome ? homeGoalieMana : awayGoalieMana)
            : (isHome ? homeGoalieCurrency : awayGoalieCurrency);

        if (currentBalance < cost) {
            return; // Wait for gold or mana to accumulate before buying
        }

        double balanceBefore = currentBalance;
        handleGoalieTreePurchase(ai, treeKey, nodeKey);

        double balanceAfter = isMana
            ? (isHome ? homeGoalieMana : awayGoalieMana)
            : (isHome ? homeGoalieCurrency : awayGoalieCurrency);

        if (balanceAfter < balanceBefore || purchased.contains(nodeKey)) {
            ai.aiGoalieBuildIndex++;
        }
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
            if (controlsHeld.E == 1 && this.phase == GamePhase.INGAME){
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
        }
        if (request.MV_CLICK) {
            t.programmed = true;
            t.marchingOrderX = request.posX + request.camX;
            t.marchingOrderY = request.posY + request.camY;
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

                double maxFuel = ((t.team == TeamAffiliation.HOME) 
                    ? homeGoalieAbilities.getMaxFuel(this) 
                    : awayGoalieAbilities.getMaxFuel(this)) * t.boostMaxFactor;

                if (t.isBoosting) {
                    t.fuel -= .75;
                    if (t.fuel < 0) {
                        t.fuel = 0;
                    }
                } else {
                    double fastRegen = c.getD("globals.boost.regen.fast") * t.boostRegenFactor;
                    double slowRegen = c.getD("globals.boost.regen.slow") * t.boostRegenFactor;
                    if (t.fuel > c.getD("globals.boost.regen.cutoff")) {
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

    public void programmedCtrl(Titan t) {
        if(t.programmed){
            boolean canRun = isActionMovementUnlocked(t);
            if (!effectPool.isRooted(t) && canRun) {
                double ang = Util.degreesFromCoords(t.marchingOrderX - (t.X + t.height/2),
                        t.marchingOrderY - (t.Y + t.height/2 ));
                double cosAng = Math.cos(Math.toRadians(ang));
                double sinAng = Math.sin(Math.toRadians(ang));
                double dirX = (cosAng > 0.001) ? 1.0 : ((cosAng < -0.001) ? -1.0 : 0.0);
                double dx = t.actualSpeed(this, dirX) * cosAng;
                double dy = t.actualSpeed(this, 0.0) * sinAng;
                if(dx > 0 && dx > t.marchingOrderX - (t.X + t.width/2)){
                    dx = t.marchingOrderX - (t.X + t.width/2);
                }
                if(dy > 0 && dy > t.marchingOrderY - (t.Y + t.height/2)){
                    dy = t.marchingOrderY - (t.Y + t.height/2);
                }
                if(dx < 0 && dx > (t.X + t.width/2) - t.marchingOrderX){
                    dx = (t.X + t.width/2) - t.marchingOrderX;
                }
                if(dy < 0 && dy > (t.Y + t.height/2) - t.marchingOrderY){
                    dy = (t.Y + t.height/2) - t.marchingOrderY;
                }
                boolean atLocation = 0.1 * t.actualSpeed(this) > (Math.abs(dx) + Math.abs(dy));
                if (atLocation) {
                    t.runningFrame = 0;
                    t.runningFrameCounter = 0;
                }
                if (!atLocation && !t.collidesSolid(this, allSolids, 0, dx)) {
                    t.facing = (int) ang;
                    t.diagonalRunDir = dx > 0 ? 2 : 1;
                    t.dirToBall = t.diagonalRunDir;
                    t.translateBounded(this, dx, 0.0);
                    t.runningFrameCounter += 1;
                    if (t.runningFrameCounter == 5) t.runningFrame = 1;
                    if (t.runningFrameCounter == 10) {
                        t.runningFrame = 2;
                        t.runningFrameCounter = 0;
                    }
                }
                if (!atLocation && !t.collidesSolid(this, allSolids, dy, 0)) {
                    t.translateBounded(this, 0.0, dy);
                }
            }
        }
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
        if (framesSinceStart / FPS > c.getD("tie.time") * 60) {
            tieAble = true;
            return checkWinCondition(false);
        }
        if (framesSinceStart / FPS > c.getD("suddendeath.extreme.time") * 60) {
            suddenDeath = true;
            extremeSuddenDeath = true;
            return checkWinCondition(false);
        }
        if (framesSinceStart / FPS > this.options.suddenDeathIndex * 60) {
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

    protected void updateBallIfPossessed() {
        int i = 1;
        for (Titan t : players) {
            updateBallIfPossessed(t, i);
            i++;
        }
    }

    protected void updateBallIfPossessed(Titan t, int numSel) {
        if (t.possession == 1 && !effectPool.hasEffect(t, EffectId.DEAD)) {
            int valuePlayerX = (int) t.X;
            int valuePlayerY = (int) t.Y;
            if (c.GOALIE_DISABLED || (numSel != 1 && numSel != 2)) {
                ball.X = (valuePlayerX + 35 - ball.centerDist);
                ball.Y = (valuePlayerY + 35 - ball.centerDist);
            }
            if (!c.GOALIE_DISABLED) {
                if (numSel == 1) {//guardian exceptions
                    ball.X = (valuePlayerX + 57);
                    ball.Y = (valuePlayerY + 20);
                    //Don't own-goal this shit.
                    while(ownGoal()){
                        ball.X+=1;
                        ball.Y-=1;
                    }
                }
                if (numSel == 2) {
                    ball.X = (valuePlayerX - 1);
                    ball.Y = (valuePlayerY + 20);
                    while(ownGoal()){
                        ball.X-=1;
                        ball.Y-=1;
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
                    changePossessionStats(release, t);
                    if (t.team == TeamAffiliation.HOME) {
                        lastHomePossessor = t.id;
                    } else if (t.team == TeamAffiliation.AWAY) {
                        lastAwayPossessor = t.id;
                    }
                    home.hasBall = true;
                    away.hasBall = false;
                    players[numSel - 1].possession = 1;
                    lastPossessed = players[numSel - 1].id;
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
            } else { //Enemy taking possession
                stats.grant(this, lost, StatEngine.StatEnum.TURNOVERS);
                stats.grant(this, gained, StatEngine.StatEnum.BLOCKS);
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
            stats.grant(this, gained, StatEngine.StatEnum.REBOUND);
            stats.grant(this, gained, StatEngine.StatEnum.BLOCKS);
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
        if (numSel > 2 || c.GOALIE_DISABLED) {
            return rect;
        }
        if (numSel == 1 || numSel == 2) {
            Titan t = players[numSel - 1];
            double xOffset = (t.width - c.GOALIE_INTERCEPT_W) / 2.0;
            return new CollisionMath.Bounds(
                    (int) t.X + xOffset,
                    (int) t.Y,
                    c.GOALIE_INTERCEPT_W,
                    c.GOALIE_INTERCEPT_H
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
                        //clientFromIndex(pIndex + 1).selection = pIndex + 1;
                        players[pIndex].possession = 1;
                        players[pIndex].inactiveDir = 0;
                        players[pIndex].runningFrame = 0;
                        players[pIndex].runningFrameCounter = 0;
                        players[pIndex].actionState = Titan.TitanState.IDLE;
                        players[pIndex].actionFrame = 0;
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
        long nowMs = System.currentTimeMillis();
        for (int i = 0; i < players.length; i++) {
            Titan t = players[i];
            if (t == null || effectPool.hasEffect(t, EffectId.DEAD)) continue;
            if (!anyClientSelected(i + 1)) {
                tickAiTitan(t, nowMs);
            }
        }
    }

    protected void tickAiTitan(Titan ai, long nowMs) {
        if (effectPool.isRooted(ai) || effectPool.isStunned(ai)) return;

        // 1. STEAL PRIORITY: If enemy has the ball and AI is in steal range, RUN THAT T-KEY CODE!
        Optional<Titan> possessorOpt = titanInPossession();
        if (possessorOpt.isPresent() && possessorOpt.get().team != ai.team && ai.possession == 0 && ballVisible) {
            Titan tip = possessorOpt.get();
            if (isWithinStealRange(ai, tip)) {
                executeAiSteal(ai);
                if (ai.possession == 1 || ai.actionState == Titan.TitanState.STEAL) {
                    return;
                }
            }
        }

        // 2. Abilities check: If in combat range of an enemy, use abilities!
        tryUseAbilities(ai);
        if (ai.actionState != Titan.TitanState.IDLE) {
            return;
        }

        boolean isGoalie = (ai.getType() == TitanType.GOALIE);
        int minDelay = (options != null) ? options.getAiReactionTimeMinMs(isGoalie) : (isGoalie ? 480 : 1200);
        int maxDelay = (options != null) ? options.getAiReactionTimeMaxMs(isGoalie) : (isGoalie ? 680 : 1700);

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

            // Boost logic: If > 100px away from target destination, boost to reach it faster
            if (distToTarget > 100.0 && ai.fuel > 0) {
                ai.isBoosting = true;
            } else if (distToTarget <= 100.0 || ai.fuel <= 0) {
                if (ai.aiTargetAction != 10) { // 10 = TRANSITION_BOOST
                    ai.isBoosting = false;
                }
            }

            ai.programmed = true;
            ai.marchingOrderX = (int) ai.aiTargetX;
            ai.marchingOrderY = (int) ai.aiTargetY;

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
            if (ai.aiStuckHorizontalTicks >= 3 && ai.possession == 1) {
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
        if (ai.aiTargetAction == 1 && ai.possession == 1) { // SHOOT / PASS
            executeAiShot(ai, ai.aiTargetX, ai.aiTargetY);
            ai.aiTargetAction = 0;
        } else if (ai.aiTargetAction == 2 && ai.possession == 0) { // STEAL
            executeAiSteal(ai);
            ai.aiTargetAction = 0;
        }
    }

    protected void evaluateAiDecision(Titan ai) {
        if (ai.getType() == TitanType.GOALIE) {
            evaluateGoalieDecision(ai);
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
            if (ballInMotion) {
                ai.aiTargetX = Math.max(c.MIN_X, Math.min(c.MAX_X, ball.X + xKickPow * 15));
                ai.aiTargetY = Math.max(c.MIN_Y, Math.min(c.MAX_Y, ball.Y + yKickPow * 15));
            } else {
                ai.aiTargetX = ballCenterX;
                ai.aiTargetY = ballCenterY;
            }
            ai.aiTargetAction = 0;
            return;
        }

        // If AI has the ball
        if (possessor != null && possessor.id.equals(ai.id)) {
            evaluateOnBallOffense(ai, enemyTeam);
            return;
        }

        // If teammate has the ball
        if (possessor != null && possessor.team == myTeam) {
            evaluateOffBallOffense(ai, possessor, enemyTeam);
            return;
        }

        // If enemy has the ball
        if (possessor != null && possessor.team == enemyTeam) {
            evaluateDefense(ai, possessor, myTeam, enemyTeam);
            return;
        }
    }

    protected void tryUseAbilities(Titan ai) {
        if (ai.actionState != Titan.TitanState.IDLE || effectPool.isStunned(ai)) return;

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

        TeamAffiliation enemyTeam = (ai.team == TeamAffiliation.HOME) ? TeamAffiliation.AWAY : TeamAffiliation.HOME;
        Titan nearestEnemy = findNearestEnemy(ai, enemyTeam);
        double enemyDist = (nearestEnemy != null) ? Math.hypot(nearestEnemy.X - ai.X, nearestEnemy.Y - ai.Y) : Double.MAX_VALUE;

        if (isQ) {
            switch (type) {
                case WARRIOR: return enemyDist <= 110.0;
                case RANGER: return enemyDist <= 320.0;
                case MAGE: return enemyDist <= 400.0;
                case BUILDER: return enemyDist <= 200.0;
                case MARKSMAN: return enemyDist <= 150.0;
                case ARTISAN: return ai.possession == 0 && Math.hypot(ball.X - ai.X, ball.Y - ai.Y) <= 280.0;
                case SUPPORT: return enemyDist <= 130.0;
                case GOLEM: return enemyDist <= 200.0 || ai.getHealth() < ai.maxHealth * 0.7;
                case STEALTH: return enemyDist <= 300.0;
                case DASHER: return ai.possession == 1;
                case HOUNDMASTER: return enemyDist <= 300.0;
                case GRENADIER: return enemyDist <= 260.0;
                case CAPTAIN: return ai.ammo > 0 && enemyDist <= 300.0;
                case SPIDER: return enemyDist <= 250.0;
                default: return false;
            }
        } else {
            switch (type) {
                case WARRIOR: return enemyDist > 80.0 && enemyDist <= 250.0;
                case RANGER: return enemyDist <= 120.0;
                case MAGE: return enemyDist <= 250.0;
                case BUILDER: return enemyDist <= 300.0;
                case MARKSMAN: return enemyDist <= 400.0;
                case ARTISAN: return true;
                case SUPPORT: {
                    for (Titan ally : players) {
                        if (ally != null && ally.team == ai.team && !effectPool.hasEffect(ally, EffectId.DEAD)) {
                            if (ally.getHealth() < ally.maxHealth && Math.hypot(ally.X - ai.X, ally.Y - ai.Y) <= 250.0) {
                                return true;
                            }
                        }
                    }
                    return false;
                }
                case GOLEM: return enemyDist <= 180.0;
                case STEALTH: return enemyDist > 80.0 && enemyDist <= 250.0;
                case DASHER: return enemyDist <= 250.0;
                case HOUNDMASTER: {
                    for (Entity e : entityPool) {
                        if (e instanceof gameserver.entity.minions.Cage && ((gameserver.entity.minions.Cage) e).getCreatedById().equals(ai.id)) {
                            return true;
                        }
                    }
                    return false;
                }
                case GRENADIER: return enemyDist <= 140.0;
                case CAPTAIN: return enemyDist <= 200.0;
                case SPIDER: return enemyDist <= 200.0;
                default: return false;
            }
        }
    }

    protected void evaluateGoalieDecision(Titan ai) {
        tickAiGoalieBuildOrder(ai);

        GoalHoop myGoal = (ai.team == TeamAffiliation.HOME) ? homeHiGoal : awayHiGoal;
        double hoopCX = myGoal.x + myGoal.w / 2.0;
        double hoopCY = myGoal.y + myGoal.h / 2.0;
        double rx = myGoal.w / 2.0;
        double ry = myGoal.h / 2.0;
        double hoopLeft = myGoal.x;
        double hoopRight = myGoal.x + myGoal.w;
        double hoopTop = myGoal.y;
        double hoopBottom = myGoal.y + myGoal.h;

        int YMAX = c.GOALIE_Y_MAX;
        int YMIN = c.GOALIE_Y_MIN;
        int XMAX = (ai.team == TeamAffiliation.AWAY ? c.GOALIE_XA_MAX : c.GOALIE_XH_MAX);
        int XMIN = (ai.team == TeamAffiliation.AWAY ? c.GOALIE_XA_MIN : c.GOALIE_XH_MIN);

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
                    double holdX = (ai.team == TeamAffiliation.HOME) ? (hoopLeft + c.GOALIE_INTERCEPT_W / 2.0) : (hoopRight - c.GOALIE_INTERCEPT_W / 2.0);
                    ai.aiTargetX = Math.max(XMIN, Math.min(XMAX, holdX));
                    ai.aiTargetY = Math.max(YMIN, Math.min(YMAX, hoopCY + 10.0));
                    ai.aiTargetAction = 0;
                }
            }
            return;
        }

        Optional<Titan> possessorOpt = titanInPossession();
        boolean teamOnOffense = possessorOpt.isPresent() && possessorOpt.get().team == ai.team;

        // The goalie only farms lanes when their team is on offense
        if (teamOnOffense) {
            int[] enemyMinionsPerLane = new int[3];
            LaneMinion[] closestMinionPerLane = new LaneMinion[3];
            double[] closestDistPerLane = new double[]{Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};

            for (Entity e : entityPool) {
                if (e instanceof LaneMinion && e.getHealth() > 0.0 && e.team != ai.team) {
                    LaneMinion lm = (LaneMinion) e;
                    int lane = lm.laneIndex;
                    if (lane >= 0 && lane < 3) {
                        enemyMinionsPerLane[lane]++;
                        double d = Math.hypot(lm.X - ai.X, lm.Y - ai.Y);
                        if (d < closestDistPerLane[lane]) {
                            closestDistPerLane[lane] = d;
                            closestMinionPerLane[lane] = lm;
                        }
                    }
                }
            }

            int bestLane = -1;
            int maxMinions = 0;
            for (int l = 0; l < 3; l++) {
                if (enemyMinionsPerLane[l] > maxMinions) {
                    maxMinions = enemyMinionsPerLane[l];
                    bestLane = l;
                }
            }

            if (bestLane != -1 && maxMinions > 0) {
                double[] laneYs = laneCenterYs();
                double targetY = laneYs[bestLane];
                LaneMinion targetMinion = closestMinionPerLane[bestLane];
                double targetX = (targetMinion != null) ? targetMinion.X : (ai.team == TeamAffiliation.HOME ? XMAX : XMIN);

                ai.aiTargetX = Math.max(XMIN, Math.min(XMAX, targetX));
                ai.aiTargetY = Math.max(YMIN, Math.min(YMAX, targetY));
                ai.aiTargetAction = 0;

                // Goalie is bounded by the exact same elliptical range restrictions as a human player
                if (targetMinion != null) {
                    double gx = ai.X + ai.width / 2.0;
                    double gy = ai.Y + ai.height / 2.0;
                    double rangeX = c.getI("titan.goalie.rangex") * ai.rangeFactor;
                    double rangeY = c.getI("titan.goalie.rangey") * ai.rangeFactor;
                    double dx = targetMinion.X - gx;
                    double dy = targetMinion.Y - gy;
                    if ((dx * dx) / (rangeX * rangeX) + (dy * dy) / (rangeY * rangeY) <= 1.0) {
                        handleGoalieAttackClick("", targetMinion.X, targetMinion.Y, ai.team, ai);
                    }
                }
                return;
            }
        }

        // Defensive objective: defend the outermost part of the hoop closest to the ball, not the center of the hoop.
        // The rectangular intercept collider (90x50) must cover the hoop without leaving any part uncovered.
        double ballCX = ball.X + ball.width / 2.0;
        double ballCY = ball.Y + ball.height / 2.0;
        double vx = ballCX - hoopCX;
        double vy = ballCY - hoopCY;
        double len = Math.hypot(vx, vy);
        if (len < 0.001) { vx = (ai.team == TeamAffiliation.HOME ? 1.0 : -1.0); vy = 0; len = 1.0; }

        double ux = vx / len;
        double uy = vy / len;

        // Outermost point on the hoop's elliptical perimeter closest to the ball
        double invDenom = Math.hypot(ux / rx, uy / ry);
        double k = (invDenom > 0.0001) ? (1.0 / invDenom) : rx;
        double outerHoopX = hoopCX + k * ux;
        double outerHoopY = hoopCY + k * uy;

        int colliderW = c.GOALIE_INTERCEPT_W; // 90
        int colliderH = c.GOALIE_INTERCEPT_H; // 50

        // In Y: Center collider on outerHoopY, clamped so the collider never extends into empty space outside the hoop,
        // ensuring no part of the hoop is left uncovered.
        double minColliderCY = hoopTop + colliderH / 2.0;
        double maxColliderCY = hoopBottom - colliderH / 2.0;
        double desiredColliderCY = Math.max(minColliderCY, Math.min(maxColliderCY, outerHoopY));
        // Goalie sprite center Y is offset +10px from collider center Y (aiCenterY = ai.Y + 35, colliderCY = ai.Y + 25)
        double targetY = desiredColliderCY + 10.0;

        // In X: Cover from hoop back through outerHoopX towards the incoming ball.
        double desiredColliderCX;
        if (ai.team == TeamAffiliation.HOME) {
            // Home hoop is [256, 326]. Collider spans [256, 346] with center at 301, completely covering the hoop and extending 20px out
            desiredColliderCX = hoopLeft + colliderW / 2.0;
        } else {
            // Away hoop is [1786, 1856]. Collider spans [1766, 1856] with center at 1811, completely covering the hoop and extending 20px out
            desiredColliderCX = hoopRight - colliderW / 2.0;
        }
        double targetX = desiredColliderCX;

        ai.aiTargetX = Math.max(XMIN, Math.min(XMAX, targetX));
        ai.aiTargetY = Math.max(YMIN, Math.min(YMAX, targetY));
        ai.aiTargetAction = 0;

        if (possessorOpt.isPresent() && possessorOpt.get().team != ai.team && ballVisible) {
            Titan tip = possessorOpt.get();
            if (isWithinStealRange(ai, tip)) {
                executeAiSteal(ai);
            }
        }
    }

    protected void evaluateDefense(Titan ai, Titan possessor, TeamAffiliation myTeam, TeamAffiliation enemyTeam) {
        double aiCX = ai.X + ai.width / 2.0;
        double aiCY = ai.Y + ai.height / 2.0;
        double pCX = possessor.X + possessor.width / 2.0;
        double pCY = possessor.Y + possessor.height / 2.0;
        double dist = Math.hypot(pCX - aiCX, pCY - aiCY);

        double shortRange = Math.max(25.0, (ai.stealRad * ai.rangeFactor) * 0.5);
        double maxRange = 100.0;

        double stealProb;
        if (dist <= shortRange) {
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

                if (ballVisible && isWithinStealRange(ai, possessor)) {
                    executeAiSteal(ai);
                }
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
        // Rule 4: Transition Offense - boost without ball until reaching 25% boost
        boolean isTransition = (ai.team == TeamAffiliation.HOME)
                ? possessor.X < FIELD_LENGTH / 2.0
                : possessor.X > FIELD_LENGTH / 2.0;

        if (isTransition) {
            if (ai.fuel > 25.0) {
                ai.isBoosting = true;
                ai.aiTargetAction = 10;
            } else {
                ai.isBoosting = false;
            }
        }

        // Rule 5: Off-ball offense - stay one pass away (~280-320px) from ball carrier
        double carrierCX = possessor.X + possessor.width / 2.0;
        double carrierCY = possessor.Y + possessor.height / 2.0;

        double forwardDir = (ai.team == TeamAffiliation.HOME) ? 1.0 : -1.0;
        double verticalOffset = (ai.Y < carrierCY) ? -220.0 : 220.0;

        ai.aiTargetX = Math.max(c.MIN_X, Math.min(c.MAX_X, carrierCX + forwardDir * 200.0));
        ai.aiTargetY = Math.max(c.MIN_Y, Math.min(c.MAX_Y, carrierCY + verticalOffset));
    }

    public Titan getHorizontalImpedingEnemy(Titan ai, TeamAffiliation enemyTeam) {
        double fwdDir = (ai.team == TeamAffiliation.HOME) ? 1.0 : -1.0;
        double aiCX = ai.X + ai.width / 2.0, aiCY = ai.Y + ai.height / 2.0;
        Titan best = null;
        double closestDist = Double.MAX_VALUE;
        for (Titan t : players) {
            if (t != null && t.team == enemyTeam && !effectPool.hasEffect(t, EffectId.DEAD)) {
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

        // On ball offense in attacking third
        if (impedingEnemy != null) {
            // Check clear path to goals for a shot first
            GoalHoop openGoal = findUnblockedGoal(ai, enemyTeam);
            if (openGoal != null) {
                ai.aiTargetX = openGoal.x + openGoal.w / 2.0;
                ai.aiTargetY = openGoal.y + openGoal.h / 2.0;
                ai.aiTargetAction = 1;
                return;
            }

            // Priorities: 1) Run forward, 2) Find a backpass option, 3) Run backwards diagonally to create space
            if (ai.aiStuckHorizontalTicks < 2) {
                double[] fwdTarget = calculateForwardEvadeTarget(ai, impedingEnemy, currentCX, currentCY);
                ai.aiTargetX = fwdTarget[0];
                ai.aiTargetY = fwdTarget[1];
                ai.aiTargetAction = 0;
                return;
            }

            Titan outletPass = findBackwardsOrVerticalPassTarget(ai, enemyTeam);
            if (outletPass != null) {
                ai.aiTargetX = outletPass.X + outletPass.width / 2.0;
                ai.aiTargetY = outletPass.Y + outletPass.height / 2.0;
                ai.aiTargetAction = 1; // PASS
                return;
            }

            double[] backTarget = calculateBackwardDiagonalEvadeTarget(ai, impedingEnemy, currentCX, currentCY);
            ai.aiTargetX = backTarget[0];
            ai.aiTargetY = backTarget[1];
            ai.aiTargetAction = 0;
            return;
        }

        Titan nearestDefender = findNearestEnemy(ai, enemyTeam);
        if (nearestDefender != null) {
            double defDist = Math.hypot(nearestDefender.X - ai.X, nearestDefender.Y - ai.Y);
            if (defDist < 110.0) {
                // Back away from defender with vertical offset to prevent steal attempt and avoid horizontal trap
                double retreatX = currentCX + (currentCX - (nearestDefender.X + nearestDefender.width / 2.0));
                double defCY = nearestDefender.Y + nearestDefender.height / 2.0;
                double vertStep = (currentCY >= defCY) ? 90.0 : -90.0;
                double retreatY = currentCY + vertStep;
                ai.aiTargetX = Math.max(c.MIN_X, Math.min(c.MAX_X, retreatX));
                ai.aiTargetY = Math.max(c.MIN_Y, Math.min(c.MAX_Y, retreatY));
                ai.aiTargetAction = 0;
                return;
            }
        }

        // Check clear path to goals for a shot
        GoalHoop openGoal = findUnblockedGoal(ai, enemyTeam);
        if (openGoal != null) {
            ai.aiTargetX = openGoal.x + openGoal.w / 2.0;
            ai.aiTargetY = openGoal.y + openGoal.h / 2.0;
            ai.aiTargetAction = 1;
            return;
        }

        // Check for open pass
        Titan openPassTarget = findBestPassTarget(ai);
        if (openPassTarget != null) {
            ai.aiTargetX = openPassTarget.X + openPassTarget.width / 2.0;
            ai.aiTargetY = openPassTarget.Y + openPassTarget.height / 2.0;
            ai.aiTargetAction = 1;
            return;
        }

        boolean groundGiven = (nearestDefender == null || Math.hypot(nearestDefender.X - ai.X, nearestDefender.Y - ai.Y) > 220.0);
        if (groundGiven) {
            // Take ground
            double forwardDir = (ai.team == TeamAffiliation.HOME) ? 1.0 : -1.0;
            if (ai.aiStuckHorizontalTicks >= 2) {
                int vertDir = (ai.aiEvadeVerticalDir != 0) ? ai.aiEvadeVerticalDir : ((currentCY < (c.MIN_Y + c.MAX_Y) / 2.0) ? 1 : -1);
                ai.aiEvadeVerticalDir = vertDir;
                ai.aiTargetX = Math.max(c.MIN_X, Math.min(c.MAX_X, currentCX + forwardDir * 80.0));
                ai.aiTargetY = Math.max(c.MIN_Y + 30.0, Math.min(c.MAX_Y - 30.0, currentCY + vertDir * 140.0));
            } else {
                double stepX = currentCX + forwardDir * 150.0;
                ai.aiTargetX = Math.max(c.MIN_X, Math.min(c.MAX_X, stepX));
                ai.aiTargetY = currentCY;
            }
        } else {
            // Probe back and forth
            double probeY = (Math.random() < 0.5) ? Math.max(c.MIN_Y, currentCY - 160.0) : Math.min(c.MAX_Y, currentCY + 160.0);
            ai.aiTargetX = currentCX;
            ai.aiTargetY = probeY;
        }
        ai.aiTargetAction = 0;
    }

    protected boolean isPassPathBlocked(double x1, double y1, double x2, double y2, TeamAffiliation enemyTeam) {
        for (Titan enemy : players) {
            if (enemy != null && enemy.team == enemyTeam && !effectPool.hasEffect(enemy, EffectId.DEAD)) {
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
        TeamAffiliation enemyTeam = (ai.team == TeamAffiliation.HOME) ? TeamAffiliation.AWAY : TeamAffiliation.HOME;
        for (Titan t : players) {
            if (t != null && !t.id.equals(ai.id) && t.team == ai.team && t.getType() != TitanType.GOALIE && !effectPool.hasEffect(t, EffectId.DEAD)) {
                double tCX = t.X + t.width / 2.0;
                double tCY = t.Y + t.height / 2.0;
                double forwardX = (ai.team == TeamAffiliation.HOME) ? (tCX - aiCX) : (aiCX - tCX);
                if (forwardX > 50.0) {
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

    protected void executeAiShot(Titan ai, double targetX, double targetY) {
        if (ai.possession != 1 || ai.actionState != Titan.TitanState.IDLE) return;
        serverMouseRoutine(ai, (int) targetX, (int) targetY, 1, 0, 0);
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



    protected Titan findNearestEnemy(Titan ai, TeamAffiliation enemyTeam) {
        Titan nearest = null;
        double minD = Double.MAX_VALUE;
        for (Titan t : players) {
            if (t != null && t.team == enemyTeam && !effectPool.hasEffect(t, EffectId.DEAD)) {
                double d = Math.hypot(t.X - ai.X, t.Y - ai.Y);
                if (d < minD) {
                    minD = d;
                    nearest = t;
                }
            }
        }
        return nearest;
    }

    protected GoalHoop findUnblockedGoal(Titan ai, TeamAffiliation enemyTeam) {
        GoalHoop[] enemyGoals = (enemyTeam == TeamAffiliation.AWAY)
                ? new GoalHoop[]{awayHiGoal, lowGoals[2], lowGoals[3]}
                : new GoalHoop[]{homeHiGoal, lowGoals[0], lowGoals[1]};
        for (GoalHoop g : enemyGoals) {
            double gx = g.x + g.w / 2.0;
            double gy = g.y + g.h / 2.0;
            boolean blocked = false;
            for (Titan enemy : players) {
                if (enemy != null && enemy.team == enemyTeam && !effectPool.hasEffect(enemy, EffectId.DEAD)) {
                    double distToLine = distToSegment(enemy.X + enemy.width / 2.0, enemy.Y + enemy.height / 2.0, ai.X + ai.width / 2.0, ai.Y + ai.height / 2.0, gx, gy);
                    if (distToLine < 40.0) {
                        blocked = true;
                        break;
                    }
                }
            }
            if (!blocked) return g;
        }
        return enemyGoals[0];
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
            if (t.getType() != null && !t.getType().equals(TitanType.GOALIE)) {
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
            t.pushMove();
            centerBall(t);
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
        ball.X = t.getX() + 35 - ball.centerDist;
        ball.Y = t.getY() + 35 - ball.centerDist;
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
        if (t.actionFrame == 0 &&
                t.getType() != null &&
                !t.getType().equals(TitanType.GOALIE)) {
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
        gameserver.gamemanager.ServerApplication.triggerGameExpiry();
    }

    private java.util.Map<String, Long> goalieLastAttackTime = new java.util.HashMap<>();
    private int minionWaveCount = 0;

    private double[] laneCenterYs() {
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
            m.damageMultiplier *= (1.0 + 0.15 * numHeroes);
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

        
    private void handleGoalieAttackClick(String email, double clickX, double clickY,
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

        int netMinionsHome = homeCount - awayCount;
        if (netMinionsHome > 10) netMinionsHome = 10;
        if (netMinionsHome < -10) netMinionsHome = -10;

        int netBonusHome = homeBonus - awayBonus;
        int P_home = netMinionsHome + netBonusHome;
        if (P_home > 20) P_home = 20;
        if (P_home < -20) P_home = -20;

        int P_team = (team == TeamAffiliation.HOME) ? P_home : -P_home;

        // 1. Quartered Unilateral Boost (-5% to +5%)
        double unilateralBoost = P_team * 0.0025;
        if (unilateralBoost > 0) {
            boolean maxPressure = homeGoaliePurchasedUpgrades.contains("siege.t6.maximumpressure") ||
                                  awayGoaliePurchasedUpgrades.contains("siege.t6.maximumpressure");
            if (maxPressure && P_team > 5) {
                unilateralBoost *= 2.0;
            }
        } else if (unilateralBoost < 0) {
            boolean impenetrable = (team == TeamAffiliation.HOME)
                ? homeGoaliePurchasedUpgrades.contains("fortress.t6.impenetrable")
                : awayGoaliePurchasedUpgrades.contains("fortress.t6.impenetrable");
            if (impenetrable && P_team < -3) {
                unilateralBoost = -3 * 0.0025;
            }
        }

        // 2. Directional Hill Effect (-20% to +20%)
        double dirSign = (dirX > 0) ? 1.0 : ((dirX < 0) ? -1.0 : 0.0);
        double hillEffect = dirSign * P_home * 0.01;

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
