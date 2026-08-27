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
            } else {
                Titan t = players[i];
                //System.out.println("unmapped titan " + t.team + t.getType() + t.id);
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
                        e.heal(-delta / factor);
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

    public boolean ballIntersectsEllipse(GoalHoop goal) {
        gameserver.engine.CollisionMath.EllipseData g = goal.ellipseData();
        gameserver.engine.CollisionMath.EllipseData b = ball.ellipseData();
        return gameserver.engine.CollisionMath.ellipseBoundsIntersect(b, g);
    }


    public void detectGoals() {
        if (this.phase == GamePhase.SCORE_FREEZE || !ballVisible) {
            return;
        }
        //If ball is in the air
        if(contactExemptBall()){
            return;
        }
        for (GoalHoop goal : this.lowGoals) {
            if (ballIntersectsEllipse(goal) && goal.checkReady()) {
                Team enemy, us;
                if (goal.team == TeamAffiliation.HOME) {
                    us = this.away;
                    enemy = this.home;
                } else { //(goal.team == TeamAffiliation.AWAY)
                    us = this.home;
                    enemy = this.away;
                }
                goal.trigger();
                stats.grant(getPossessorOrThrower(), StatEngine.StatEnum.SIDEGOALS);
                stats.grant(getPossessorOrThrower(), StatEngine.StatEnum.POINTS, .25);
                if (us.score % 1.0 == .75) {
                    goal.freeze();
                }
                us.score += .25;
                checkWinCondition(false);//somewhat intentional to check condition before ghost removal
                boolean saveProgress = (enemy == this.home)
                    ? homeGoaliePurchasedUpgrades.contains("siege.t5.saveprogress")
                    : awayGoaliePurchasedUpgrades.contains("siege.t5.saveprogress");
                if (!saveProgress) {
                    enemy.score = Math.floor(enemy.score); //Reset any of the other teams ghostpoints.
                }
            }
        }

        for (GoalHoop goal : this.hiGoals) {
            if (ballIntersectsEllipse(goal) && goal.checkReady()) {
                Team us, enemy;
                if (goal.team == TeamAffiliation.HOME) {
                    us = this.away;
                    enemy = this.home;
                } else { //(goal.team == TeamAffiliation.AWAY)
                    us = this.home;
                    enemy = this.away;
                }
                goal.trigger();
                //Cash in all ghost/combo points for a full point
                long iPart = (long) us.score;
                double fPart = us.score - iPart;
                us.score = Math.floor(us.score);
                us.score += fPart * 4 + 1;
                stats.grant(getPossessorOrThrower(), StatEngine.StatEnum.GOALS);
                stats.grant(getPossessorOrThrower(), StatEngine.StatEnum.POINTS, fPart * 4 + 1);
                checkWinCondition(false);
                //reset enemy team ghost points
                boolean saveProgressHi = (enemy == this.home)
                    ? homeGoaliePurchasedUpgrades.contains("siege.t5.saveprogress")
                    : awayGoaliePurchasedUpgrades.contains("siege.t5.saveprogress");
                if (!saveProgressHi) {
                    enemy.score = Math.floor(enemy.score);
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
        for (Titan t : players) {
            t.setVarsBasedOnType();
            t.actionState = Titan.TitanState.IDLE;
            t.actionFrame = 0;
        }
        home.score = 0;
        away.score = 0;
        lastScoredTeam = TeamAffiliation.UNAFFILIATED;
        ball.X = c.BALL_X;
        ball.Y = c.BALL_Y;
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
        if (slots <= 2) {
            if (slotIndex == 0) {
                t.X = MID_HOME;
                t.Y = MID_WING_HOME;
            }
            if (slotIndex == 1) {
                t.X = DEFENDER_HOME;
                t.Y = MID_WING_HOME;
            }
        }
        if (slots == 3) {
            if (slotIndex == 0) {
                t.X = MID_HOME;
                t.Y = MID_WING_HOME;
            }
            if (slotIndex == 1) {
                t.X = FW_HOME;
                t.Y = TOP_WING_HOME;
            }
            if (slotIndex == 2) {
                t.X = FW_HOME;
                t.Y = BOT_WING_HOME;
            }
        }
        if (slots == 4) {
            if (slotIndex == 0) {
                t.X = MID_HOME;
                t.Y = MID_WING_HOME;
            }
            if (slotIndex == 1) {
                t.X = DEFENDER_HOME;
                t.Y = MID_WING_HOME;
            }
            if (slotIndex == 2) {
                t.X = FW_HOME;
                t.Y = TOP_WING_HOME;
            }
            if (slotIndex >= 3) {
                t.X = FW_HOME;
                t.Y = BOT_WING_HOME;
            }
        }
        if (slots == 5) {
            if (slotIndex == 0) {
                t.X = MID_HOME;
                t.Y = MID_WING_HOME;
            }
            if (slotIndex == 1) {
                t.X = DEFENDER_HOME;
                t.Y = TOP_WING_HOME;
            }
            if (slotIndex == 2) {
                t.X = DEFENDER_HOME;
                t.Y = BOT_WING_HOME;
            }
            if (slotIndex == 3) {
                t.X = FW_HOME;
                t.Y = TOP_WING_HOME;
            }
            if (slotIndex >= 4) {
                t.X = FW_HOME;
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
        ball.X = c.BALL_X;
        ball.Y = c.BALL_Y;
        resetPosSel();
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
                int btn = getBtn(request, t);
                if (request.posX != -1 && request.posY != -1 && btn != 0) {
                    this.serverMouseRoutine(t, request.posX, request.posY, btn, request.camX, request.camY);
                }
                this.processProgramming(t, request);
                this.processKeys(request, from);
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
        if (hasCost) {
            purchased.add(nodeKey);
        }
        
        if (isHome) {
            homeGoalieAbilities.purchaseOrUse(this, t, nodeKey);
        } else {
            awayGoalieAbilities.purchaseOrUse(this, t, nodeKey);
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
                        }
                    } catch (Exception e) {
                    }
                }
            }
            if (controlsHeld.CAM == 1 && this.phase == GamePhase.DRAW_CLASS_SCREEN) {
                this.phase = GamePhase.SET_MASTERIES;
            }
            if (controlsHeld.E == 1 && this.phase == GamePhase.INGAME){
                if((t.actionState == Titan.TitanState.IDLE) ) {
                    if (!effectPool.isStunned(t)) {
                        try {
                            boolean caststun = ability.castQ(this, t);
                            if (caststun) {//Curve may be set by ability
                                t.actionState = Titan.TitanState.A1;
                                t.actionFrame = 0;
                            }
                        } catch (Exception e) {
                        }
                    }
                }
            }
            if (controlsHeld.R == 1 && this.phase == GamePhase.INGAME){
                if((t.actionState == Titan.TitanState.IDLE)) {
                    if (!effectPool.isStunned(t)) {
                        try {
                            boolean caststun = ability.castW(this, t);
                            if (caststun) {//Curve may be set by ability
                                t.actionState = Titan.TitanState.A2;
                                t.actionFrame = 0;
                            }
                        } catch (Exception e) {
                        }
                    }
                }
            }
            moveKeys(controlsHeld, t);
            lastControlPacket[clientIndex] = controls;
        }
    }   

    public void moveKeys(KeyDifferences controlsHeld, Titan t) {
        if (!effectPool.hasEffect(t, EffectId.DEAD)) {
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
                if (controlsHeld.RIGHT == 1 && this.phase == GamePhase.INGAME) {
                    if (t.actionState == Titan.TitanState.IDLE ||
                            t.actionFrame >= (int) (t.kickingFrames*c.SHOT_FREEZE_RATIO)) {
                        t.runLeft = 0;
                        t.runRight = 1;
                    } else {
                        t.moveMemR = true;
                        t.moveMemL = false;
                    }
                }
                if (controlsHeld.LEFT == 1 && this.phase == GamePhase.INGAME) {
                    if (t.actionState == Titan.TitanState.IDLE ||
                            t.actionFrame >= (int) (t.kickingFrames*c.SHOT_FREEZE_RATIO)) {
                        t.runLeft = 1;
                        t.runRight = 0;
                    } else {
                        t.moveMemR = false;
                        t.moveMemL = true;
                    }
                }
                if (controlsHeld.UP == 1 && this.phase == GamePhase.INGAME) {
                    if (t.actionState == Titan.TitanState.IDLE ||
                            t.actionFrame >= (int) (t.kickingFrames*c.SHOT_FREEZE_RATIO)) {
                        t.runUp = 1;
                        t.runDown = 0;
                    } else {
                        t.moveMemU = true;
                        t.moveMemD = false;
                    }
                }
                if (controlsHeld.DOWN == 1 && this.phase == GamePhase.INGAME) {
                    if (t.actionState == Titan.TitanState.IDLE ||
                            t.actionFrame >= (int) (t.kickingFrames*c.SHOT_FREEZE_RATIO)) {
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
        for (int i = 0; i < clients.size(); i++) {
            if (clients.get(i).id == from.id) {
                return i;
            }
        }
        return -1;
    }

    public int clientIndex(Titan t) {
        PlayerDivider from = clientFromTitan(t);
        for (int i = 0; i < clients.size(); i++) {
            if (clients.get(i).id == from.id) {
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

                double maxFuel = (t.team == TeamAffiliation.HOME) 
                    ? homeGoalieAbilities.getMaxFuel(this) 
                    : awayGoalieAbilities.getMaxFuel(this);

                if (t.isBoosting) {
                    t.fuel -= .75;
                    if (t.fuel < 0) {
                        t.fuel = 0;
                    }
                } else {
                    if (t.fuel > c.getD("globals.boost.regen.cutoff")) {
                        t.fuel += c.getD("globals.boost.regen.fast");//regen bonus
                    } else {
                        t.fuel += c.getD("globals.boost.regen.slow");
                    }
                    if (t.fuel > maxFuel) {
                        t.fuel = maxFuel;
                    }
                }
                if (t.runRight == 1) runRightCtrl(t);
                if (t.runLeft == 1) runLeftCtrl(t);
                if (t.runUp == 1) runUpCtrl(t);
                if (t.runDown == 1) runDownCtrl(t);
                programmedCtrl(t);
                unhideBallIfHidden(t);
                if (t.actionState == Titan.TitanState.SHOOT) shootingBall(t);
                else if (t.actionState == Titan.TitanState.LOB) lobbingBall(t);
                else if (t.actionState == Titan.TitanState.CURVE_LEFT) curve(t, 1);
                else if (t.actionState == Titan.TitanState.CURVE_RIGHT) curve(t, -1);
                if (t.actionState == Titan.TitanState.A1) attack1(t);
                if (t.actionState == Titan.TitanState.A2) attack2(t);
                if (t.actionState == Titan.TitanState.STEAL) steal(t);
            }
            yourPlayerTactics();
        }
        if (ballVisible) {
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
            if (!effectPool.isRooted(t) &&
                    (t.actionState == Titan.TitanState.IDLE ||
                    t.actionFrame >= (int) (t.kickingFrames*c.SHOT_FREEZE_RATIO))) {
                double ang = Util.degreesFromCoords(t.marchingOrderX - (t.X + t.height/2),
                        t.marchingOrderY - (t.Y + t.height/2 ));
                double dx = t.actualSpeed(this) * Math.cos(Math.toRadians((ang)));
                double dy = t.actualSpeed(this) * Math.sin(Math.toRadians((ang)));
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
            TeamAffiliation team = tip.get().team;
            if (team == TeamAffiliation.HOME) {
                home.hasBall = true;
                away.hasBall = false;
            }
            if (team == TeamAffiliation.AWAY) {
                away.hasBall = true;
                home.hasBall = false;
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
            }
        } else {//Picking up loose ball
            stats.grant(this, gained, StatEngine.StatEnum.REBOUND);
        }
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

    public void serverMouseRoutine(Titan t, int clickX, int clickY, int btn, int camX, int camY) {
        int priorPossession = t.possession;
        intersectAll(); //Update state of variables doubleclick fails
        // Only allow a throw if the titan already held the ball before intersectAll() ran.
        // This prevents a held-down shot button from auto-firing the instant the ball is caught.
        if (t.possession == 1 && priorPossession == 1 && t.actionState == Titan.TitanState.IDLE
                && !effectPool.isStunned(t)) {
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
            //System.out.println("angle params: " + " (" + (xClick) + ", " + (yClick) + ")");
            double angle = Util.degreesFromCoords(xClick, yClick);
            //System.out.println("ang: " + angle);
            xKickPow = Math.cos(Math.toRadians(angle)) / 4.0;
            yKickPow = Math.sin(Math.toRadians(angle)) / 4.0;
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
        // Guardian
        if (!c.GOALIE_DISABLED && !anyClientSelected(1) && !effectPool.isRooted(players[0])) {
            goalieTactics(players[0], TeamAffiliation.HOME);
        }
        if (!c.GOALIE_DISABLED && !anyClientSelected(2) && !effectPool.isRooted(players[1])) {
            goalieTactics(players[1], TeamAffiliation.AWAY);
        }
        // Defender
        aiTactics(2, DEFENDER_RETREAT, DEFENDER_CREEP, TOP_WING_ST, BOT_WING_END);
        // Midfielder
        aiTactics(3, MID_RETREAT, MID_CREEP, TOP_WING_ST, BOT_WING_END);
        //Attacker
        aiTactics(4, FW_RETREAT, FW_CREEP, TOP_WING_ST, TOP_WING_END);
        aiTactics(5, FW_RETREAT, FW_CREEP, BOT_WING_ST, BOT_WING_END);

        // Defender
        //Must reverse creep and retreat for this to work
        /*
        aiTactics(6, FIELD_LENGTH - DEFENDER_CREEP,
                FIELD_LENGTH - DEFENDER_RETREAT, TOP_WING_ST, BOT_WING_END);
        // Midfielder
        aiTactics(7, FIELD_LENGTH - MID_CREEP,
                FIELD_LENGTH - MID_RETREAT, TOP_WING_ST, BOT_WING_END);
        //Attacker
        aiTactics(8, FIELD_LENGTH - FW_CREEP,
                FIELD_LENGTH - FW_RETREAT, TOP_WING_ST, TOP_WING_END);
        aiTactics(9, FIELD_LENGTH - FW_CREEP,
                FIELD_LENGTH - FW_RETREAT, BOT_WING_ST, BOT_WING_END);
                */
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
            if (!effectPool.isRooted(t)) {
                if (!t.collidesSolid(this, allSolids, (int) -t.speed, 0)) {
                    if (t.X <= ball.X) t.dirToBall = 1;
                    if (t.X > ball.X) t.dirToBall = 2;
                    if (t.diagonalRunDir == 1) t.dirToBall = 1;
                    if (t.diagonalRunDir == 2) t.dirToBall = 2;
                    if (!t.programmed) {
                        t.translateBounded(this, 0.0, -t.actualSpeed(this));
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
            if (!effectPool.isRooted(t)) {
                if (!t.collidesSolid(this, allSolids, (int) t.speed, 0)) {
                    if (t.X <= ball.X) t.dirToBall = 1;
                    if (t.X > ball.X) t.dirToBall = 2;
                    if (t.diagonalRunDir == 1) t.dirToBall = 1;
                    if (t.diagonalRunDir == 2) t.dirToBall = 2;
                    if (!t.programmed) {
                        t.translateBounded(this, 0.0, t.actualSpeed(this));
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
            if (!effectPool.isRooted(t)) {
                if (!t.collidesSolid(this, allSolids, 0, (int) t.speed)) {
                    if (!t.programmed) {
                        t.translateBounded(this, t.actualSpeed(this), 0.0);
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
            if (!effectPool.isRooted(t)) {
                t.diagonalRunDir = 2;
                if (!t.collidesSolid(this, allSolids, 0, (int) -t.speed)) {
                    if (!t.programmed) {
                        t.translateBounded(this, -t.actualSpeed(this), 0.0);
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
        if(t.actionFrame == (int) (t.kickingFrames*c.SHOT_FREEZE_RATIO)){
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
            for (int i = 0; i < 800; i++) {
                if (this.phase == GamePhase.SCORE_FREEZE) {
                    break;
                }
                if (vel[0] == 0 && vel[1] == 0) {
                    break;
                }
                ball.X += vel[0];
                ball.Y += vel[1];
                intersectAll();
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
        if(t.actionFrame == (int) (t.kickingFrames*c.SHOT_FREEZE_RATIO)){
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
            for (int i = 0; i < 800; i++) {
                if (this.phase == GamePhase.SCORE_FREEZE) {
                    break;
                }
                if (vel[0] == 0 && vel[1] == 0) {
                    break;
                }
                ball.X += vel[0];
                ball.Y += vel[1];
                intersectAll();
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
        if(t.actionFrame == (int) (t.kickingFrames*c.SHOT_FREEZE_RATIO)){
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
            for (int i = 0; i < 800; i++) {
                if (this.phase == GamePhase.SCORE_FREEZE || !ballVisible) {
                    break;
                }
                if (vel[0] == 0 && vel[1] == 0) {
                    break;
                }
                ball.X += vel[0];
                ball.Y += vel[1];
                intersectAll();
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
        if (t.actionFrame == 0) {
            t.pushMove();
        }
        if (t.actionFrame < t.eCastFrames) {
            t.actionFrame++;
        }
        if (t.actionFrame == t.eCastFrames) {
            t.actionFrame = 0;
            t.actionState = Titan.TitanState.IDLE;
            t.popMove();
        }
    }

    public void attack2(Titan t) {
        if (t.actionFrame == 0) {
            t.pushMove();
        }
        if (t.actionFrame < t.rCastFrames) {
            t.actionFrame++;
        }
        if (t.actionFrame == t.rCastFrames) {
            t.actionFrame = 0;
            t.actionState = Titan.TitanState.IDLE;
            t.popMove();
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

    void triggerWin(Team winner) {
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

        double curHomeSpeed = getLaneMinionSpeed(L, TeamAffiliation.HOME, minionSpeed, homeInLane.size(), awayInLane.size());
        double curAwaySpeed = getLaneMinionSpeed(L, TeamAffiliation.AWAY, minionSpeed, homeInLane.size(), awayInLane.size());

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
                d.damage(this, dmg, goalieTeam);
            } else {
                LaneMinion m = (LaneMinion) target;
                if (m.team == goalieTeam) {
                    dmg *= 0.5;
                }
                m.health -= dmg;
                if (m.health <= 0.0) {
                    m.health = 0.0;
                    if (m.team != goalieTeam) {
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
        int homeCount = 0;
        int awayCount = 0;
        for (Entity entity : entityPool) {
            if (entity instanceof LaneMinion && entity.getHealth() > 0.0 && ((LaneMinion) entity).laneIndex == L) {
                if (entity.team == TeamAffiliation.HOME) homeCount++;
                else awayCount++;
            }
        }
        return getLaneMinionSpeed(L, team, baseSpeed, homeCount, awayCount);
    }

    public double getLaneMinionSpeed(int L, TeamAffiliation team, double baseSpeed, int homeCount, int awayCount) {
        int homeBonus = homeLaneBonusValue[L];
        int awayBonus = awayLaneBonusValue[L];
        
        int netMinions = (team == TeamAffiliation.HOME)
            ? (homeCount - awayCount)
            : (awayCount - homeCount);

        // Soft-cap base minion count difference at +10 / -10
        if (netMinions > 10) netMinions = 10;
        if (netMinions < -10) netMinions = -10;

        int netBonus = (team == TeamAffiliation.HOME)
            ? (homeBonus - awayBonus)
            : (awayBonus - homeBonus);

        int P = netMinions + netBonus;

        // Clamp total lane advantage at hard-cap (+20 / -20)
        if (P > 20) P = 20;
        if (P < -20) P = -20;

        if (P > 0) {
            boolean maxPressure = homeGoaliePurchasedUpgrades.contains("siege.t6.maximumpressure") ||
                                  awayGoaliePurchasedUpgrades.contains("siege.t6.maximumpressure");
            double val = P * 0.01;
            if (maxPressure && P > 5) {
                val *= 2.0;
            }
            return baseSpeed * (1.0 + val);
        } else if (P < 0) {
            boolean impenetrable = (team == TeamAffiliation.HOME)
                ? homeGoaliePurchasedUpgrades.contains("fortress.t6.impenetrable")
                : awayGoaliePurchasedUpgrades.contains("fortress.t6.impenetrable");
            if (impenetrable && P < -3) {
                P = -3;
            }
            long insuranceUntil = (team == TeamAffiliation.HOME)
                ? homeGoalieAbilities.fastBreakUntilMs
                : awayGoalieAbilities.fastBreakUntilMs;
            
            double val = 0.0; //if insurance
            if (nowEpochMs >= insuranceUntil) { //if NO insurance
                val = P * 0.01;
            }
            double speed = baseSpeed * (1.0 + val);
            return Math.max(0.2, speed);
        }
        return baseSpeed;
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
