package gameserver;


import client.ClientPacket;
import client.GoalSprite;
import gameserver.effects.EffectPool;
import gameserver.engine.*;
import gameserver.entity.Box;
import gameserver.entity.Entity;
import gameserver.entity.Titan;
import gameserver.entity.TitanType;
import gameserver.targeting.ShapePayload;
import networking.KeyDifferences;
import networking.PlayerDivider;
import util.Util;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class Game {
    public String gameId;
    public static final double SOFT_WIN = 3.0, WIN_BY = 2.0, HARD_WIN = 8.0;
    private static final long GAMETICK_MS = 25;
    public List<PlayerDivider> clients;
    public ClientPacket[] lastControlPacket = null;
    public static final int SPRITE_X_EMPTY = 50;
    public static final int SPRITE_Y_EMPTY = 18;
    private AtomicBoolean locked = new AtomicBoolean(false);
    public Set<ShapePayload> colliders;
    public Entity[] allSolids;
    private int curveFactor;
    public Titan underControl = null; //Only set by the gameserver right before pushing an update
    public boolean ended = false;
    public StatEngine stats = new StatEngine();

    public Game(String id, List<PlayerDivider> clients) {
        this.clients = clients;
        //cullUnmappedTitans();
        this.gameId = id;
        this.lastControlPacket = new ClientPacket[clients.size()];
    }

    private void cullUnmappedTitans() {
        List<Titan> rm = new ArrayList<>();
        for (int i = 0; i < players.length; i++) {
            boolean found = false;
            for (PlayerDivider p : clients) {
                if (p.possibleSelection.contains(i + 1)) {
                    found = true;
                }
            }
            if (!found)
                rm.add(players[i]);
        }
        List<Titan> temp = new LinkedList<>(Arrays.asList(players));
        temp.removeAll(rm);
        players = (Titan[]) temp.toArray();
    }

    public Game() {
    } //For kryo deser

    public void lock() {
        while (!locked.compareAndSet(false, true)) {
            //some other thread won, waiting.
        }
    }

    public void unlock() {
        locked.set(false);
    }

    private void doHealthModification() throws Exception {
        for (Entity e : allSolids) {
            GoalHoop[] pains = getPainHoopsFromTeam(e.team);
            for (GoalHoop pain : pains) {
                int d = (int) Point2D.distance(e.X + 35, e.Y + 35, pain.x + (pain.w / 2), pain.y + (pain.h / 2));
                double delta = (-67.0 / 432000000.0) * Math.pow(d, 3) +
                        Math.pow(d, 2) * 1261.0 / 4320000.0 -
                        (3817.0 / 21600.0) * d
                        + 35;// -1.6×10^-8 x^3 + 0.000068 x^2 - 0.104 x + 52 //fits {{1500, -5}, {1000, 0}, {500, 15}, {250, 30}}
                //https://www.wolframalpha.com/input/?i=model+cubic&assumption=%7B%22F%22,+%22CubicFitCalculator%22,+%22data2%22%7D+-%3E%22%7B%7B1000,+-5%7D,+%7B400,1%7D,+%7B200,+10%7D,+%7B100,+20%7D%7D%22
                if (delta > 0) {
                    if (delta > 20) {
                        delta = 20;
                    }
                    e.damage(this, delta / 40.0);
                } else {
                    if (delta < -5) {
                        delta = -5;
                    }
                    if (e.team != TeamAffiliation.UNAFFILIATED && !e.teamPoss(this)) {
                        e.heal(delta / -12.0);
                    }
                }
            }
        }
    }

    private GoalHoop[] getPainHoopsFromTeam(TeamAffiliation team) {
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

    public Box ball = new Box(0, 0, 15, 15);
    Titan hGol = new Titan(0, 0, TeamAffiliation.HOME, TitanType.GUARDIAN);
    Titan awGol = new Titan(1200, 400, TeamAffiliation.AWAY, TitanType.GUARDIAN);
    public Titan[] players = {hGol,
            awGol,
            new Titan(0, 0, TeamAffiliation.HOME, TitanType.WARRIOR),
            new Titan(0, 0, TeamAffiliation.HOME, TitanType.SLASHER),
            new Titan(0, 0, TeamAffiliation.HOME, TitanType.MAGE),
            new Titan(0, 0, TeamAffiliation.HOME, TitanType.SUPPORT),

            new Titan(0, 0, TeamAffiliation.AWAY, TitanType.RANGER),
            new Titan(0, 0, TeamAffiliation.AWAY, TitanType.ARTISAN),
            new Titan(0, 0, TeamAffiliation.AWAY, TitanType.MARKSMAN),
            new Titan(0, 0, TeamAffiliation.AWAY, TitanType.STEALTH) //bugfix where not displayed
    };
    public GoalHoop[] lowGoals = {new GoalHoop(189, 353, 23, 97, TeamAffiliation.HOME),
            new GoalHoop(189, 789, 23, 97, TeamAffiliation.HOME),
            new GoalHoop(1901, 353, 23, 97, TeamAffiliation.AWAY),
            new GoalHoop(1901, 789, 23, 97, TeamAffiliation.AWAY)};

    public final int HOME_HI_X = 133;
    public final int HOME_HI_Y = 584;
    public final int AWAY_HI_X = 1923;
    public final int AWAY_HI_Y = 584;

    public GoalHoop homeHiGoal = new GoalHoop(HOME_HI_X, HOME_HI_Y, 56, 70, TeamAffiliation.HOME);
    public GoalHoop awayHiGoal = new GoalHoop(AWAY_HI_X, AWAY_HI_Y, 56, 70, TeamAffiliation.AWAY);
    public GoalHoop[] hiGoals = {homeHiGoal, awayHiGoal};
    public Team away = new Team(TeamAffiliation.AWAY, 0.0, awayHiGoal, lowGoals[2], lowGoals[3]);
    public Team home = new Team(TeamAffiliation.HOME, 0.0, homeHiGoal, lowGoals[0], lowGoals[1],
            players);

    public void detectGoals() throws Exception {
        Rectangle ballBounds = new Rectangle((int) this.ball.X, (int) this.ball.Y, 15, 15);
        for (GoalHoop goal : this.lowGoals) {
            GoalSprite tempGoal = new GoalSprite(goal, 0, 0); //Just for using the g2d intersect method
            if (tempGoal.intersects(ballBounds) && goal.checkReady()) {
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
                checkWinCondition();//somewhat intentional to check condition before ghost removal
                enemy.score = Math.floor(enemy.score); //Reset any of the other teams ghostpoints
            }
        }
        for (GoalHoop hoop : this.hiGoals) {
            Team us, enemy;
            if (hoop.team == TeamAffiliation.HOME) {
                us = this.away;
                enemy = this.home;
            } else { //(goal.team == TeamAffiliation.AWAY)
                us = this.home;
                enemy = this.away;
            }
            GoalSprite tempGoal = new GoalSprite(hoop, 0, 0); //Just for using the g2d intersect method
            if (tempGoal.intersects(ballBounds) && hoop.checkReady()) {
                hoop.trigger();
                //Cash in all ghost/combo points for a full point
                long iPart = (long) us.score;
                double fPart = us.score - iPart;
                us.score = Math.floor(us.score);
                us.score += fPart * 4 + 1;
                stats.grant(getPossessorOrThrower(), StatEngine.StatEnum.GOALS);
                stats.grant(getPossessorOrThrower(), StatEngine.StatEnum.POINTS, fPart * 4 + 1);
                checkWinCondition();
                serverGoalScored();
                //reset enemy team ghost points
                enemy.score = Math.floor(enemy.score);
                us.hasBall = true;
                enemy.hasBall = false;
                ballVisible = false;
                inGame = false;
                goalVisible = true;
                phase = 9;
                /*
                for(ChaosballClient client : allClients){
                    client.audienceCelebrateSound.rewindStart();
                }*/
            }
        }
    }

    private PlayerDivider getPossessorOrThrower() {
        if (Titan.titanInPossession().isPresent()) {
            return clientFromTitan(Titan.titanInPossession().get());
        }
        if(lastPossessed != null){
            return clientFromTitan(Titan.byId(lastPossessed));
        }
        return null;
    }

    public void initializeServer() throws InterruptedException {
        startTime = System.currentTimeMillis();
        startGameTime = startTime / 60000;
        endGameTime = (startGameTime + 5);
        timeSpent = (int) (endGameTime - startGameTime);
        home.hasBall = false;
        away.hasBall = false;
        goalVisible = false;
        ballVisible = true;
        for (Titan t : players) {
            t.actionState = Titan.TitanState.IDLE;
            t.actionFrame = 0;
        }
        home.score = 0;
        away.score = 0;
        ball.X = 1050;
        ball.Y = 630;
        for (Titan p : players) {
            p.runningFrame = 0;
            p.dirToBall = 0;
            p.diagonalRunDir = 0;
        }
        resetPosSel();
        phase = 8;
    }

    public void resetPosSel() {
        for (PlayerDivider client : clients) {
            client.setSelection(client.getPossibleSelection().get(0));
        }
        lastPossessed = null;
        players[0].setX(HOME_HI_X);
        players[1].setX(AWAY_HI_X);
        players[2].X = DEFENDER_HOME;
        players[3].X = MID_HOME;
        players[4].X = FW_HOME;
        players[5].X = FW_HOME;
        players[6].X = FIELD_LENGTH - DEFENDER_HOME;
        players[7].X = FIELD_LENGTH - MID_HOME;
        players[8].X = FIELD_LENGTH - FW_HOME;
        players[9].X = FIELD_LENGTH - FW_HOME;

        players[0].setY(HOME_HI_Y);
        players[1].Y = AWAY_HI_Y;
        players[2].Y = MID_WING_HOME;
        players[3].Y = MID_WING_HOME;
        players[4].Y = TOP_WING_HOME;
        players[5].Y = BOT_WING_HOME;

        players[6].Y = MID_WING_HOME;
        players[7].Y = MID_WING_HOME;
        players[8].Y = TOP_WING_HOME;
        players[9].Y = BOT_WING_HOME;
        /*if (home.hasBall == true) { //TODO this is kinda pointless eventually
            players[5].X = 1000;
            players[6].X = 1000;
            players[5].Y = 555;
            players[6].Y = 624;
        }
        if (away.hasBall == true) { //TODO this is kinda pointless eventually
            players[5].X = 1000;
            players[6].X = 1000;
            players[5].Y = 555;
            players[6].Y = 624;
        }*/
    }

    public void serverGoalScored() {
        goalVisible = false;
        ballVisible = true;
        for (Titan t : players) {
            t.actionState = Titan.TitanState.IDLE;
            t.runDown = 0;
            t.runUp = 0;
            t.runLeft = 0;
            t.runRight = 0;
        }
        for (Titan p : players) {
            effectPool.cullAllOn(this, p);
            p.facing = 0;
            p.possession = 0;
            p.runningFrame = 0;
            p.actionFrame = 0;
            p.dirToBall = 0;
            p.diagonalRunDir = 0;
        }
        resetPosSel();
        ball.X = 1050;
        ball.Y = 630;
    }

    public void intersectAll() {
        for (int n = players.length - 1; n >= 0; n--) {
            intersectBall(n + 1, (int) players[n].X, (int) players[n].Y);
        }
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
        lock();
        //System.out.println("xclick " + request.posX);
        if (from != null) {
            if (request.posX != -1 && request.posY != -1 && request.btn != 0) {
                //System.out.println("BTNCLICK" + request.btn);
                Titan t = titanFromPacket(from);
                this.serverMouseRoutine(t, request.posX, request.posY, request.btn, request.camX, request.camY);
            }
            this.processKeys(request, from);
            //moving select code to here allows class switch during game
            if (!began) {
                System.out.println(from.toString() + " ready");
                for (PlayerDivider client : clients) {
                    if (client.id == from.id) {
                        from.ready = true;
                        int classSelIndex = client.possibleSelection.get(0) - 1;
                        players[classSelIndex].setType(request.classSelecton);
                    }
                }
                for (PlayerDivider client : clients) {
                    if (!client.ready) {
                        unlock();
                        return;
                    }
                }
                began = true;
                ScheduledExecutorService exec = Executors.newScheduledThreadPool(1);
                TerminableExecutor terminableExecutor = new TerminableExecutor(this, exec);
                exec.scheduleAtFixedRate(terminableExecutor, 0, GAMETICK_MS, TimeUnit.MILLISECONDS);

                System.out.println("gametick kickoff should only run once");
            }
            intersectAll();
            try {
                detectGoals();
            } catch (Exception e) {
                unlock();
                e.printStackTrace();
            }
        }
        unlock();
    }

    private Titan titanFromPacket(PlayerDivider conn) {
        for (PlayerDivider p : clients) {
            if (p.id == conn.id) {
                return players[p.selection - 1];
            }
        }
        return null;
    }

    private void processKeys(ClientPacket controls, PlayerDivider from) {
        if (from != null) {
            int playerSelected = from.selection;
            Titan t = players[playerSelected - 1];
            int clientIndex = clientIndex(from);
            KeyDifferences controlsHeld = new KeyDifferences(controls, lastControlPacket[clientIndex]);
            lastControlPacket[clientIndex] = controls;
            if (controlsHeld.Z == 1 && this.phase == 8 && t.actionState == Titan.TitanState.IDLE) {
                from.incSel(this);
                t.runLeft = 0;
                t.runRight = 0;
                t.runDown = 0;
                t.runUp = 0;
                t.runningFrame = 0;
                t.diagonalRunDir = 0;
            }
            if (controlsHeld.Q == 1 && this.phase == 8
                    && t.actionState == Titan.TitanState.IDLE) {//Later repurpose button to be a steal
                if (!effectPool.isStunned(t)) {
                    try {
                        boolean caststun = Ability.castQ(this, t);
                        if (t.actionState == Titan.TitanState.IDLE && caststun) {//Curve may be set by ability
                            t.actionState = Titan.TitanState.STEAL;
                            t.actionFrame = 0;
                        }
                    } catch (Exception e) {
                    }

                }
            }
            if (controlsHeld.SPACE == 1 && this.phase == 5) {
                this.phase = 6;
            }
            if (controlsHeld.SPACE == 1 && this.phase == 2) {
                this.phase = 3;
            }
            if (controlsHeld.SPACE == 1 && this.phase == 50) {
                this.phase = 2;
            }
            if (controlsHeld.SPACE == 1 && this.phase == 0) {
                this.phase = 50;
            }
            if (controlsHeld.SPACE == 1 && this.phase == 9) {
                serverGoalScored();
                this.phase = 8;
            }
            if (controlsHeld.SPACE == 1 && this.phase == 12) {
                this.phase = 0;
            }
            if (controlsHeld.E == 1 && this.phase == 8 && playerSelected != 1 && playerSelected != 2
                    && t.actionState == Titan.TitanState.IDLE) {
                if (!effectPool.isStunned(t)) {
                    try {
                        boolean caststun = Ability.castE(this, t);
                        if (t.actionState == Titan.TitanState.IDLE && caststun) {//Curve may be set by ability
                            t.actionState = Titan.TitanState.A1;
                            t.actionFrame = 0;
                        }
                    } catch (Exception e) {
                    }
                }
            }
            if (controlsHeld.R == 1 && this.phase == 8 && playerSelected != 1 && playerSelected != 2
                    && t.actionState == Titan.TitanState.IDLE) {
                if (!effectPool.isStunned(t)) {
                    try {
                        boolean caststun = Ability.castR(this, t);
                        if (t.actionState == Titan.TitanState.IDLE && caststun) {//Curve may be set by ability
                            t.actionState = Titan.TitanState.A2;
                            t.actionFrame = 0;
                        }
                    } catch (Exception e) {
                    }
                }
            }
            if (controlsHeld.D == 1 && this.phase == 8 && t.actionState == Titan.TitanState.IDLE) {
                t.runLeft = 0;
                t.runRight = 1;
            }
            if (controlsHeld.A == 1 && this.phase == 8 && t.actionState == Titan.TitanState.IDLE) {
                t.runRight = 0;
                t.runLeft = 1;
            }
            if (controlsHeld.W == 1 && this.phase == 8 && t.actionState == Titan.TitanState.IDLE) {
                t.runDown = 0;
                t.runUp = 1;
            }
            if (controlsHeld.S == 1 && this.phase == 8 && t.actionState == Titan.TitanState.IDLE) {
                t.runUp = 0;
                t.runDown = 1;
            }
            //Done with helds

            //The releases below here
            if (controlsHeld.D == -1 && this.phase == 8) {
                t.runRight = 0;
                t.runningFrame = 0;
                t.diagonalRunDir = 0;
            }
            if (controlsHeld.A == -1 && this.phase == 8) {
                t.runLeft = 0;
                t.runningFrame = 0;
                t.diagonalRunDir = 0;
            }
            if (controlsHeld.W == -1 && this.phase == 8) {
                t.runUp = 0;
                t.runningFrame = 0;
                t.dirToBall = 0;
            }
            if (controlsHeld.S == -1 && this.phase == 8) {
                t.runDown = 0;
                t.runningFrame = 0;
                t.dirToBall = 0;
            }
            if (controlsHeld.R == -1 || controlsHeld.E == -1 || controlsHeld.Q == -1 && this.phase == 8 && t.actionState == Titan.TitanState.IDLE) {
                this.colliders = new HashSet<>();
            }
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

    public PlayerDivider clientFromTitan(Entity t) {
        for (PlayerDivider pd : clients) {
            List<Integer> sels = pd.possibleSelection;
            for (Integer i : sels) {
                if (players[i - 1].id.equals(t.id)) {
                    return pd;
                }
            }
        }
        return null;
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

    private void updateSelectedDirection() {
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

    public void gameTick() throws Exception {
        //System.out.println("tock " + began + ended);
        lock();
        if (began && !ended) {
            try {
                List<Entity> tempSolids = new ArrayList<>();
                tempSolids.addAll(Arrays.asList(players));
                trimEntities(entityPool);
                tempSolids.addAll(entityPool);
                allSolids = tempSolids.toArray(new Entity[tempSolids.size()]);
                updateBallIfPossessed();
                effectPool.tickAll(this);
                doHealthModification();
                updateSelectedDirection();
            } catch (Exception e) {
                e.printStackTrace();
            }
            startTime = System.currentTimeMillis();
            startGameTime = startTime / 60000;
            timeSpent = (int) (endGameTime - startGameTime);
            if (timeSpent < 1) {
            }
            for (Titan t : players) {
                if (t.runRight == 1) runRightCtrl(t);
                if (t.runLeft == 1) runLeftCtrl(t);
                if (t.runUp == 1) runUpCtrl(t);
                if (t.runDown == 1) runDownCtrl(t);
                if (t.actionState == Titan.TitanState.SHOOT) shootingBall(t);
                else if (t.actionState == Titan.TitanState.PASS) passingBall(t);
                else if (t.actionState == Titan.TitanState.CURVE_LEFT) curve(t, 1);
                else if (t.actionState == Titan.TitanState.CURVE_RIGHT) curve(t, -1);
                if (t.actionState == Titan.TitanState.A1) attack1(t);
                if (t.actionState == Titan.TitanState.A2) attack2(t);
                if (t.actionState == Titan.TitanState.STEAL) steal(t);
            }
            yourPlayerTactics();
        }
        if (ballVisible == true) {
            intersectAll();
            detectGoals();
        }
        if (ball.X < Game.MIN_X) ball.X = Game.MIN_X;
        if (ball.X > Game.MAX_X) ball.X = Game.MAX_X;
        if (ball.Y < Game.MIN_Y) ball.Y = Game.MIN_Y;
        if (ball.Y > Game.MAX_Y) ball.Y = Game.MAX_Y;
        unlock();
    }

    private void trimEntities(List<Entity> entityPool) {
        List<Entity> temp = entityPool;
        entityPool = new ArrayList<>();
        for (int i = 0; i < temp.size(); i++) {
            if (temp.get(i).getHealth() > 0.0) {
                entityPool.add(temp.get(i));
            }
        }
    }

    private void updateBallIfPossessed() {
        int i = 1;
        for (Titan t : players) {
            updateBallIfPossessed(t, i);
            i++;
        }
    }

    private void updateBallIfPossessed(Titan t, int numSel) {
        if (t.possession == 1) {
            int valuePlayerX = (int) t.X;
            int valuePlayerY = (int) t.Y;
            if (numSel != 1 && numSel != 2) {
                ball.X = (valuePlayerX + 35 - 7);
                ball.Y = (valuePlayerY + 35 - 7);
            }
            if (numSel == 1) {//guardian exceptions
                ball.X = (valuePlayerX + 57);
                ball.Y = (valuePlayerY + 20);
            }
            if (numSel == 2) {
                ball.X = (valuePlayerX - 1);
                ball.Y = (valuePlayerY + 20);
            }
        }
    }


    private void setBallFromTip() {
        Optional<Titan> tip = Titan.titanInPossession();
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
        Rectangle r1 = new Rectangle(valuePlayerX + SPRITE_X_EMPTY / 2, valuePlayerY + SPRITE_Y_EMPTY / 2, 70 - SPRITE_X_EMPTY, 70 - SPRITE_Y_EMPTY);
        r1 = goalieHitboxOverride(numSel, r1);
        Rectangle r2 = new Rectangle((int) ball.X, (int) ball.Y, 15, 15);
        if ((r1.intersects(r2))) {
            Titan t = players[numSel - 1];
            if (t.id.equals(players[numSel - 1].id) && !t.id.equals(lastPossessed)) {
                Optional<Titan> tip = Titan.titanInPossession();
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
        // }
    }

    private void changePossessionStats(Titan lost, Titan gained) {
        if(lost != null){
            TeamAffiliation oldTeam = lost.team;
            if(gained.team == oldTeam){
                stats.grant(this, lost, StatEngine.StatEnum.PASSES);
            }
            else{ //Enemy taking possession
                stats.grant(this, lost, StatEngine.StatEnum.TURNOVERS);
                stats.grant(this, gained, StatEngine.StatEnum.BLOCKS);
            }
        }
    }

    private Rectangle goalieHitboxOverride(int numSel, Rectangle rect) {
        if (numSel > 2) {
            return rect;
        }
        if (numSel == 1) {
            return new Rectangle((int) players[numSel - 1].X - 10,
                    (int) players[numSel - 1].Y,
                    90,
                    70 - SPRITE_Y_EMPTY);

        }
        if (numSel == 2) {
            return new Rectangle((int) players[numSel - 1].X - 10,
                    (int) players[numSel - 1].Y,
                    90,
                    70 - SPRITE_Y_EMPTY);
        }
        return rect;
    }

    private Titan getAnyBallMover() {
        for (Titan t : players) {
            if (t.actionState == Titan.TitanState.PASS ||
                    t.actionState == Titan.TitanState.SHOOT ||
                    t.actionState == Titan.TitanState.CURVE_LEFT ||
                    t.actionState == Titan.TitanState.CURVE_RIGHT) {
                return t;
            }
        }
        return null;
    }

    public void serverMouseRoutine(Titan t, int clickX, int clickY, int btn, int camX, int camY) {
        intersectAll(); //Update state of variables doubleclick fails
        if (t.possession == 1 && t.actionState == Titan.TitanState.IDLE) {
            if (phase == 8 && btn == 1) {
                t.actionState = Titan.TitanState.SHOOT;
            }
            if (phase == 8 && btn == 3) {
                t.actionState = Titan.TitanState.PASS;
            }
            if (phase == 8 && btn == 4) {
                t.actionState = Titan.TitanState.CURVE_LEFT;
            }
            if (phase == 8 && btn == 5) {
                t.actionState = Titan.TitanState.CURVE_RIGHT;
            }
            int xClick = (int) ((clickX - ball.X) + camX - 7); //mid sprite, plus account for locations
            int yClick = (int) (-1 * ((clickY - ball.Y) + camY - 7)); //same, plus flip Y axis for coordinate plane
            //System.out.println("angle params: " + " (" + (xClick) + ", " + (yClick) + ")");
            double angle = Util.degreesFromCoords(xClick, yClick);
            //System.out.println("ang: " + angle);
            xKickPow = Math.cos(Math.toRadians(angle)) / 4.0;
            yKickPow = Math.sin(Math.toRadians(angle)) / 4.0;
        }
    }

    private void aiTactics(int pIndex, int minX, int maxX, int minY, int maxY) {
        Optional<Titan> tip = Titan.titanInPossession();
        //PlayerDivider client = clientFromIndex(pIndex);
        Titan aiFor = players[pIndex]; //no sub1
        if (!anyClientSelected(pIndex + 1)) {
            if (aiFor.X + 35 > (ball.X + 7) && aiFor.X > minX) {
                aiFor.inactiveDir = 2;
                runLeftAI(aiFor);
            }
            if (aiFor.X + 35 < (ball.X + 7) && aiFor.X < maxX) {
                aiFor.inactiveDir = 1;
                runRightAI(aiFor);
            }
            if (tip.isPresent() && tip.get().team != aiFor.team) {
                if (aiFor.Y + 35 > (ball.Y + 7) && aiFor.Y > minY) {
                    runUpAI(aiFor);
                }
                if (aiFor.Y + 35 < (ball.Y + 7) && aiFor.Y < maxY) {
                    runDownAI(aiFor);
                }
            }// Bot intersection control with ball and passing ball in case of automatic control
            if (!tip.isPresent()) {
                Rectangle ballTangle = new Rectangle((int) ball.X, (int) ball.Y, 15, 15);
                Rectangle playertangle = new Rectangle((int) players[pIndex].X + SPRITE_X_EMPTY / 2, (int) players[pIndex].Y + SPRITE_Y_EMPTY / 2,
                        players[pIndex].width - SPRITE_X_EMPTY, players[pIndex].height - SPRITE_Y_EMPTY);
                if (ballTangle.intersects(playertangle)) {
                    clientFromIndex(pIndex + 1).selection = pIndex + 1;
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

    private PlayerDivider clientFromIndex(int pIndex) {
        for (PlayerDivider p : clients) {
            if (p.possibleSelection.contains(pIndex)) {
                return p;
            }
        }
        return null;
    }

    public void yourPlayerTactics() {
        // Guardian
        if (!anyClientSelected(1) && !effectPool.isRooted(players[0])) {
            goalieTactics(players[0], TeamAffiliation.HOME);
        }
        if (!anyClientSelected(2) && !effectPool.isRooted(players[1])) {
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

    private void goalieTactics(Titan goalie, TeamAffiliation team) {
        if (effectPool.isRooted(goalie) || goalie.possession == 1) {
            return;
        }
        int YMAX = GOALIE_Y_MAX, XMAX = GOALIE_XH_MAX, YMIN = GOALIE_Y_MIN, XMIN = GOALIE_XH_MIN;
        if (team == TeamAffiliation.AWAY) {
            XMAX = GOALIE_XA_MAX;
            XMIN = GOALIE_XA_MIN;
        }
        if (goalie.getY() + 35 < (ball.Y + 15)) {
            if (!goalie.collidesSolid(this, allSolids, (int) goalie.speed, 0)) {
                goalie.setY((int) (goalie.getY() + goalie.speed));
                if (goalie.getY() > YMAX) goalie.setY(YMAX);
            }
        }
        if (goalie.getY() + 35 > (ball.Y + 15)) {
            if (!goalie.collidesSolid(this, allSolids, (int) -goalie.speed, 0)) {
                goalie.setY((int) (goalie.getY() - goalie.speed));
                if (goalie.getY() < YMIN) goalie.setY(YMIN);
            }
        }
        if (goalie.getX() + 35 > ball.X + 7) {
            if (!goalie.collidesSolid(this, allSolids, 0, (int) -goalie.speed)) {
                goalie.setX((int) (goalie.getX() - goalie.speed));
                if (goalie.getX() < XMIN) goalie.setX(XMIN);
            }
        }
        if (goalie.getX() + 35 < ball.X + 7) {
            if (!goalie.collidesSolid(this, allSolids, 0, (int) goalie.speed)) {
                goalie.setX((int) (goalie.getX() + goalie.speed));
                if (goalie.getX() > XMAX) goalie.setX(XMAX);
            }
        }
    }

    // SELECTION METHODS PLAYER COMPANIONS OF PLAY
    public void runRightAI(Titan t) {
        int maxX = 2070;
        if (t.inactiveDir == 1 && !effectPool.isRooted(t)) {
            t.diagonalRunDir = 1;
            if (!t.collidesSolid(this, allSolids, 0, (int) t.speed)) {
                t.X += t.getSpeed();
                if (t.X > maxX) t.X = maxX;
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
        int minX = -10;
        if (t.inactiveDir == 2 && !effectPool.isRooted(t)) {
            if (!t.collidesSolid(this, allSolids, 0, (int) -t.speed)) {
                t.diagonalRunDir = 2;
                t.X -= t.getSpeed();
                if (t.X < minX) t.X = minX;
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
        if (!effectPool.isRooted(t)) {
            if (!t.collidesSolid(this, allSolids, (int) -t.speed, 0)) {
                t.Y -= t.getSpeed();
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
                t.Y += t.getSpeed();
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
        if (phase == 8) {
            this.colliders = new HashSet<>();
            if (t.id.equals(players[0].id) && !effectPool.isRooted(players[0])) {
                players[0].setY((int) players[0].getY() - 4);
                if (players[0].getY() < GOALIE_Y_MIN) players[0].setY(GOALIE_Y_MIN);
            }
            if (t.id.equals(players[1].id) && !effectPool.isRooted(players[1])) {
                players[1].setY((int) players[1].getY() - 4);
                if (players[1].getY() < GOALIE_Y_MIN) players[1].setY(GOALIE_Y_MIN);
            }
            for (int p = 2; p < players.length; p++) {
                if (t.id.equals(players[p].id) && !effectPool.isRooted(t) &&
                        t.actionState == Titan.TitanState.IDLE) {
                    if (!t.collidesSolid(this, allSolids, (int) -t.speed, 0)) {
                        if (t.X <= ball.X) t.dirToBall = 1;
                        if (t.X > ball.X) t.dirToBall = 2;
                        if (t.diagonalRunDir == 1) t.dirToBall = 1;
                        if (t.diagonalRunDir == 2) t.dirToBall = 2;
                        t.Y -= t.speed;
                        if (t.Y < 170) t.Y = 170;
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
    }

    public void runDownCtrl(Titan t) {
        if (phase == 8) {
            this.colliders = new HashSet<>();
            if (t.id.equals(players[0].id) && !effectPool.isRooted(players[0])) {
                players[0].setY((int) players[0].getY() + 4);
                if (players[0].getY() > GOALIE_Y_MAX) players[0].setY(GOALIE_Y_MAX);
            }
            if (t.id.equals(players[1].id) && !effectPool.isRooted(players[1])) {
                players[1].setY((int) players[1].getY() + 4);
                if (players[1].getY() > GOALIE_Y_MAX) players[1].setY(GOALIE_Y_MAX);
            }
            for (int p = 2; p < players.length; p++) {
                if (t.id.equals(players[p].id) && !effectPool.isRooted(t)
                        && t.actionState == Titan.TitanState.IDLE) {
                    if (!t.collidesSolid(this, allSolids, (int) t.speed, 0)) {
                        if (t.X <= ball.X) t.dirToBall = 1;
                        if (t.X > ball.X) t.dirToBall = 2;
                        if (t.diagonalRunDir == 1) t.dirToBall = 1;
                        if (t.diagonalRunDir == 2) t.dirToBall = 2;
                        t.Y += t.speed;
                        if (t.Y > 950) t.Y = 950;
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
    }

    public void runRightCtrl(Titan t) {
        if (phase == 8) {
            this.colliders = new HashSet<>();
            if (t.id.equals(players[0].id) && !effectPool.isRooted(players[0])) {
                players[0].setX((int) players[0].getX() + 4);
                if (players[0].getX() > GOALIE_XH_MAX) players[0].setX(GOALIE_XH_MAX);
            }
            if (t.id.equals(players[1].id) && !effectPool.isRooted(players[1])) {
                players[1].setX((int) players[1].getX() + 4);
                if (players[1].getX() > GOALIE_XA_MAX) players[1].setX(GOALIE_XA_MAX);
            }
            for (int p = 2; p < players.length; p++) {
                if (t.id.equals(players[p].id) && !effectPool.isRooted(t)
                        && t.actionState == Titan.TitanState.IDLE) {
                    if (!t.collidesSolid(this, allSolids, 0, (int) t.speed)) {
                        t.diagonalRunDir = 1;
                        t.X += t.speed;
                        if (t.X > 2030) t.X = 2030;
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
    }

    public void runLeftCtrl(Titan t) {
        if (phase == 8) {
            this.colliders = new HashSet<>();
            if (t.id.equals(players[0].id) && !effectPool.isRooted(players[0])) {
                players[0].setX((int) players[0].getX() - 3);
                if (players[0].getX() < GOALIE_XH_MIN) players[0].setX(GOALIE_XH_MIN);
            }
            if (t.id.equals(players[1].id) && !effectPool.isRooted(players[1])) {
                players[1].setX((int) players[1].getX() - 3);
                if (players[1].getX() < GOALIE_XA_MIN) players[1].setX(GOALIE_XA_MIN);
            }
            for (int p = 2; p < players.length; p++) {
                if (t.id.equals(players[p].id) && !effectPool.isRooted(t)
                        && t.actionState == Titan.TitanState.IDLE) {
                    t.diagonalRunDir = 2;
                    if (!t.collidesSolid(this, allSolids, 0, (int) -t.speed)) {
                        t.X -= t.speed;
                        if (t.X < -10) t.X = -10;
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
    }

    private UUID lastPossessed;

    // Effect of the kicked ball
    public void shootingBall(Titan t) throws Exception {
        //System.out.println("pow " + xKickPow + " " + yKickPow);
        if (lastPossessed != null || Titan.titanInPossession().isPresent()) {
            if (lastPossessed == null) {
                lastPossessed = Titan.titanInPossession().get().id;
            }
            Titan tip;
            try {
                tip = titanFromId(lastPossessed);
            } catch (Exception e) {
                return;
            }
            if (tip == null || effectPool.isStunned(tip)) {
                return;
            }
            if (t.actionFrame == 0 && !t.getType().equals(TitanType.GUARDIAN)) {
                centerBall(t);
            }
            t.actionFrame += 1;
            //System.out.println(t.actionState.toString() + t.actionFrame);
            //System.out.println("tip " + tip.team + tip.getType());
            t.kickingFrames = 33;
            if (t.actionFrame < t.kickingFrames) {
                t.runRight = 0;
                t.runLeft = 0;
                t.runDown = 0;
                t.runUp = 0;
                t.possession = 0;
                setBallFromTip();
                for (int i = 0; i < 8; i++) {
                    ball.X += 5.0 * xKickPow * tip.throwPower;
                    ball.Y -= 5.0 * yKickPow * tip.throwPower;
                    intersectAll();
                    detectGoals();
                    bounceWalls();
                }
            }
            if (t.actionFrame == t.kickingFrames) {
                t.actionFrame = 0;
                lastPossessed = null;
                // It prevents the effect at the end of the shootingState from leaving the ball beyond the margins with no more play to go back
                if (ball.X < Game.MIN_X) ball.X = Game.MIN_X;
                if (Game.MAX_X > Game.MAX_X) ball.X = Game.MAX_X;
                if (ball.Y < Game.MIN_Y) ball.Y = Game.MIN_Y;
                if (ball.Y > Game.MAX_Y) ball.Y = Game.MAX_Y;
                t.actionState = Titan.TitanState.IDLE;
            }
        }
    }

    private Titan titanFromId(UUID lastPossessed) throws Exception {
        for (Titan t : players) {
            if (t.id.equals(lastPossessed)) {
                return t;
            }
        }
        throw new Exception("No titan with id!");
    }

    private void centerBall(Titan t) {
        ball.X = t.getX() + 35 - 7;
        ball.Y = t.getY() + 35 - 7;
    }

    public void bounceWalls() {
        Box[] temp = new Box[entityPool.size()];
        entityPool.toArray(temp);
        if (ball.collidesSolid(this, temp, 0, 0)) {
            //System.out.println("wall");
            xKickPow = -xKickPow;
            yKickPow = -yKickPow;
        }
        if (ball.X > Game.MAX_X) xKickPow = -xKickPow;
        if (ball.X < Game.MIN_X) xKickPow = -xKickPow;
        if (ball.Y < Game.MIN_Y) yKickPow = -yKickPow;
        if (ball.Y > Game.MAX_Y) yKickPow = -yKickPow;
    }

    public void passingBall(Titan t) throws Exception {
        //System.out.println("pow " + xKickPow + " " + yKickPow);
        if (lastPossessed != null || Titan.titanInPossession().isPresent()) {
            if (lastPossessed == null) {
                lastPossessed = Titan.titanInPossession().get().id;
            }
            Titan tip;
            try {
                tip = titanFromId(lastPossessed);
            } catch (Exception e) {
                return;
            }
            if (tip == null || effectPool.isStunned(tip)) {
                return;
            }
            if (t.actionFrame == 0) {
                centerBall(t);
            }
            t.actionFrame += 1;
            //System.out.println(t.actionState.toString() + t.actionFrame);
            t.kickingFrames = 17;
            if (t.actionFrame < t.kickingFrames) {
                t.runRight = 0;
                t.runLeft = 0;
                t.runDown = 0;
                t.runUp = 0;
                t.possession = 0;
                setBallFromTip();
                for (int i = 0; i < 8; i++) {
                    ball.X += 5.82 * xKickPow * tip.throwPower;
                    ball.Y -= 5.82 * yKickPow * tip.throwPower;
                    intersectAll();
                    detectGoals();
                    bounceWalls();
                }
            }
            if (t.actionFrame == t.kickingFrames) {
                t.actionFrame = 0;
                lastPossessed = null;
                // It prevents the effect at the end of the shootingState from leaving the ball beyond the margins with no more play to go back
                if (ball.X < Game.MIN_X) ball.X = Game.MIN_X;
                if (Game.MAX_X > Game.MAX_X) ball.X = Game.MAX_X;
                if (ball.Y < Game.MIN_Y) ball.Y = Game.MIN_Y;
                if (ball.Y > Game.MAX_Y) ball.Y = Game.MAX_Y;
                t.actionState = Titan.TitanState.IDLE;
            }
        }
    }

    private void curve(Titan t, int sign) throws Exception {
        //System.out.println("pow " + xKickPow + " " + yKickPow);
        if (lastPossessed != null || Titan.titanInPossession().isPresent()) {
            if (lastPossessed == null) {
                lastPossessed = Titan.titanInPossession().get().id;
            }
            Titan tip;
            try {
                tip = titanFromId(lastPossessed);
            } catch (Exception e) {
                return;
            }
            if (tip == null || effectPool.isStunned(tip)) {
                return;
            }
            if (t.actionFrame == 0) {
                centerBall(t);
            }
            t.actionFrame += 1;
            //System.out.println(t.actionState.toString() + t.actionFrame);
            t.kickingFrames = 17;
            if (t.actionFrame < t.kickingFrames) {
                t.runRight = 0;
                t.runLeft = 0;
                t.runDown = 0;
                t.runUp = 0;
                t.possession = 0;
                setBallFromTip();
                curveFactor = sign * 18 - (sign * 2 * t.actionFrame);
                double angle = Util.degreesFromCoords(xKickPow, yKickPow);
                if (sign == 1) {
                    angle -= 7;
                } else if (sign == -1) {
                    angle += 7;
                }
                angle += curveFactor * 6.8;
                double tempXPow = Math.cos(Math.toRadians(angle));
                double tempYPow = Math.sin(Math.toRadians(angle));
                for (int i = 0; i < 8; i++) {
                    ball.X += 2.95 * tempXPow * tip.throwPower;
                    ball.Y -= 2.95 * tempYPow * tip.throwPower;
                    intersectAll();
                    detectGoals();
                    bounceWalls();
                }
            }
            if (t.actionFrame == t.kickingFrames) {
                t.actionFrame = 0;
                lastPossessed = null;
                // It prevents the effect at the end of the shootingState from leaving the ball beyond the margins with no more play to go back
                if (ball.X < Game.MIN_X) ball.X = Game.MIN_X;
                if (Game.MAX_X > Game.MAX_X) ball.X = Game.MAX_X;
                if (ball.Y < Game.MIN_Y) ball.Y = Game.MIN_Y;
                if (ball.Y > Game.MAX_Y) ball.Y = Game.MAX_Y;
                t.actionState = Titan.TitanState.IDLE;
            }
        }
    }

    public void attack1(Titan t) {
        if (t.actionFrame < t.eCastFrames) {
            t.actionFrame++;
        }
        if (t.actionFrame == t.eCastFrames) {
            t.actionFrame = 0;
            t.actionState = Titan.TitanState.IDLE;
        }
    }

    public void attack2(Titan t) {
        if (t.actionFrame < t.rCastFrames) {
            t.actionFrame++;
        }
        if (t.actionFrame == t.rCastFrames) {
            t.actionFrame = 0;
            t.actionState = Titan.TitanState.IDLE;
        }
    }

    public void steal(Titan t) {
        if (t.actionFrame < t.sCastFrames) {
            t.actionFrame++;
        }
        if (t.actionFrame == t.sCastFrames) {
            t.actionFrame = 0;
            t.actionState = Titan.TitanState.IDLE;
        }
    }

    public static final int MAX_X = 2070;
    public static final int MAX_Y = 1010;
    public static final int MIN_X = 50;
    public static final int MIN_Y = 230;
    public static final int GOALIE_Y_MAX = 890;
    public static final int GOALIE_Y_MIN = 290;
    public static final int GOALIE_XH_MAX = 179;
    public static final int GOALIE_XH_MIN = 43;
    public static final int GOALIE_XA_MAX = 2002;
    public static final int GOALIE_XA_MIN = 1866;
    final int FIELD_LENGTH = 2070;
    final int TOP_WING_ST = 0;
    final int TOP_WING_END = 500;
    final int BOT_WING_ST = 700;
    final int BOT_WING_END = 9999;

    final int DEFENDER_CREEP = 650;
    final int DEFENDER_RETREAT = 0;
    final int MID_CREEP = 1550;
    final int MID_RETREAT = 550;
    final int FW_CREEP = 1850;
    final int FW_RETREAT = 1250;

    final int TOP_WING_HOME = 300;
    final int MID_WING_HOME = 600;
    final int T_CIRCLE_WING_HOME = 450;
    final int B_CIRCLE_WING_HOME = 750;
    final int BOT_WING_HOME = 900;

    final int DEFENDER_HOME = 325;
    final int MID_HOME = 850;
    final int FW_HOME = 900;

    public EffectPool effectPool = new EffectPool();
    public List<Entity> entityPool = new ArrayList<>();
    public boolean ballVisible, inGame, goalVisible;

    public int phase;
    public long startTime;
    public long startGameTime;
    public long endGameTime;
    public int timeSpent;
    public boolean began = false;
    public double xKickPow, yKickPow;

    public Titan titanSelected(PlayerDivider p) {
        Titan t = players[p.selection - 1];
        //System.out.println( "controlling " + (t.team.toString() + t.getType()
        //+ " " + t.runUp + t.runDown + t.runLeft + t.runRight));
        return t;
    }

    public boolean anyPoss() {
        for (Titan t : players) {
            if (t.possession == 1) {
                return true;
            }
        }
        return false;
    }

    public boolean anyBallMoveState() {
        for (Titan t : players) {
            if (t.actionState == Titan.TitanState.SHOOT ||
                    t.actionState == Titan.TitanState.CURVE_RIGHT || t.actionState ==
                    Titan.TitanState.CURVE_LEFT || t.actionState == Titan.TitanState.PASS) {
                return true;
            }
        }
        return false;
    }

    void checkWinCondition() throws Exception {
        //System.out.println("checking win cond");
        if ((home.score >= SOFT_WIN && home.score - away.score >= WIN_BY) ||
                home.score >= HARD_WIN) {
            triggerWin(home);
        }
        if ((away.score >= SOFT_WIN && away.score - home.score >= WIN_BY) ||
                away.score >= HARD_WIN) {
            triggerWin(away);
        }
    }

    void triggerWin(Team winner) throws Exception {
        //System.out.println("wonnered");
        for (PlayerDivider p : clients) {
            int winDex = p.selection;
            if (winner.which.equals(players[winDex - 1].team)) {
                p.wasVictorious = 1;
            } else {
                p.wasVictorious = -1;
            }
        }
        this.ended = true;
    }

    public double homeWinBy() {
        return home.score - away.score;
    }

    public void setClients(List<PlayerDivider> players) {
        this.clients = players;
    }

    private class TerminableExecutor implements Runnable {
        Game context;
        ScheduledExecutorService exec;

        TerminableExecutor(Game gm, ScheduledExecutorService exec) {
            this.context = gm;
            this.exec = exec;
        }

        public void run() {
            if (context.ended) {
                System.out.println("suspending game thread");
                exec.shutdown();
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
