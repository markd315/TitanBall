package gameserver.engine;


import client.graphical.GoalSprite;
import client.graphical.ScreenConst;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rits.cloning.Cloner;
import gameserver.tenancy.GameTenant;
import gameserver.TutorialOverrides;
import gameserver.effects.EffectId;
import gameserver.entity.Box;
import gameserver.entity.Entity;
import gameserver.entity.Titan;
import gameserver.entity.TitanType;
import gameserver.models.Game;
import networking.ClientPacket;
import networking.KeyDifferences;
import networking.PlayerDivider;
import util.Util;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GameEngine extends Game {
    private static final double SHOT_FREEZE_RATIO = .4;
    protected boolean GOALIE_DISABLED = false;
    protected static final double BALL_X = 1040;
    protected static final double BALL_Y = 605;
    protected Ability ability = new Ability();

    public GameEngine(String id, List<PlayerDivider> clients, GameOptions options) {
        this.clients = clients;
        this.options = options;
        ObjectMapper mapper = new ObjectMapper();
        try {
            System.out.println(mapper.writeValueAsString(this.options));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        cullUnmappedTitans();
        if (options.goalieIndex == 0) {
            cullGoalies();
        } else {
            if (!(this instanceof TutorialOverrides)) {
                players[0].setVarsBasedOnType();
                players[1].setVarsBasedOnType();
            }
        }
        this.gameId = id;
        this.lastControlPacket = new ClientPacket[clients.size()];
    }

    public GameEngine(String gameId, List<PlayerDivider> players, GameOptions options, GameTenant gameTenant) {
        this(gameId, players, options);
    }

    protected void cullGoalies() {
        if (!GOALIE_DISABLED) {
            GOALIE_DISABLED = true;
            players[0].possession = 0;
            players[1].possession = 0;
            players[0].Y = 999999;
            players[0].X = 999999;
            players[1].X = 999999;
            players[1].Y = 999999;
            for (PlayerDivider p : clients) {
                List<Integer> rm = new ArrayList<>();
                for (Integer poss : p.possibleSelection) {
                    if (poss == 1 || poss == 2) {
                        rm.add(poss);
                    }
                }
                p.possibleSelection.removeAll(rm);
                p.selection = p.possibleSelection.get(0);
            }
        }

    }

    protected void cullUnmappedTitans() {
        List<Titan> rm = new ArrayList<>();
        for (int i = 0; i < players.length; i++) {
            boolean found = false;
            for (PlayerDivider p : clients) {
                if (p.possibleSelection.contains(i + 1)) {
                    found = true;
                }
            }
            if (!found) {
                Titan t = players[i];
                rm.add(t);
                System.out.println("unmapped titan " + t.team + t.getType() + t.id);
            }
        }
        List<Titan> temp = new LinkedList<>(Arrays.asList(players));
        temp.removeAll(rm);
        players = new Titan[temp.size()];
        players = temp.toArray(players);
    }

    public GameEngine() {
        super();
    }

    public void lock() {
        while (!locked.compareAndSet(false, true)) {
            //some other thread won, starting.
        }
    }

    public void unlock() {
        locked.set(false);
    }

    protected void doHealthModification() {
        for (Entity e : allSolids) {
            double factor = (1000.0 / Game.GAMETICK_MS);
            if(!(e instanceof Titan)){
                factor*=15.0;
            }
            GoalHoop[] pains = getPainHoopsFromTeam(e.team);
            for (GoalHoop pain : pains) {
                int d = (int) Point2D.distance(e.X + 35, e.Y + 35, pain.x + (pain.w / 2), pain.y + (pain.h / 2));
                double delta = (-10051.0 / 146431200000.0) * Math.pow(d, 3) +
                        Math.pow(d, 2) * 556391.0 / 4881040000.0 -
                        (276389.0 / 4306800.0) * d
                        + 13.82;//-(10051 x^3)/146431200000 + (556391 x^2)/4881040000 - (276389 x)/4306800 + 843475/61013≈-6.86397×10^-8 x^3 + 0.00011399 x^2 - 0.064175 x + 13.8245
                //https://www.wolframalpha.com/input/?i=model+cubic&assumption=%7B%22F%22%2C+%22CubicFitCalculator%22%2C+%22data2%22%7D+-%3E%22%7B%7B1000%2C+-5%7D%2C+%7B400%2C2%7D%2C+%7B200%2C+5%7D%2C+%7B30%2C+12%7D%7D%22
                if (delta > 0) {
                    if (delta > 20) {
                        delta = 20;
                    }
                    e.damage(this, delta / factor);
                } else {
                    if (delta < -3) {
                        delta = -3;
                    }
                    if (e.team != TeamAffiliation.UNAFFILIATED && !e.teamPoss(this) && hoopDmg) {
                        e.heal(-delta / factor);
                    }
                }
            }
        }
    }

    protected GoalHoop[] getPainHoopsFromTeam(TeamAffiliation team) {
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

    public void detectGoals() throws Exception {
        //If ball is in the air
        for (int n = players.length - 1; n >= 0; n--) {
            if (players[n].actionState == Titan.TitanState.LOB
                    && players[n].actionFrame > 10 &&
                    players[n].actionFrame < 30) {
                return;
            }
        }
        Rectangle ballBounds = new Rectangle((int) this.ball.X, (int) this.ball.Y, 30, 30);
        for (GoalHoop goal : this.lowGoals) {
            GoalSprite tempGoal = new GoalSprite(goal, 0, 0, new ScreenConst(1920, 1080)); //Just for using the g2d intersect method
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
            GoalSprite tempGoal = new GoalSprite(hoop, 0, 0, new ScreenConst(1920, 1080)); //Just for using the g2d intersect method
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
                //reset enemy team ghost points
                enemy.score = Math.floor(enemy.score);
                us.hasBall = true;
                enemy.hasBall = false;
                ballVisible = false;
                inGame = false;
                goalVisible = true;
                serverDelayReset();
            }
        }
    }


    //TODO not production ready, could have bugs if the goalie is blocked from moving!
    protected void goalieMinorHoopBounce() {
        if (titanInPossession().isPresent() && titanInPossession().get().getType() == TitanType.GOALIE) {
            for (GoalHoop goal : this.lowGoals) {
                GoalSprite tempGoal = new GoalSprite(goal, 0, 0, new ScreenConst(1920, 1080)); //Just for using the g2d intersect method
                Rectangle2D ballBounds = ball.asRect();
                while (tempGoal.intersects(ballBounds)) {
                    ballBounds = ball.asRect();
                    int wRad = (int) ((tempGoal.width - 1) / 2);
                    int hRad = (int) ((tempGoal.height - 1) / 2);
                    double ang = Util.degreesFromCoords(tempGoal.x + wRad - ball.X - 15, tempGoal.y + hRad - ball.Y - 15);
                    ang += 180; //Run away, not towards
                    double dx = Math.cos(Math.toRadians((ang)));
                    double dy = Math.sin(Math.toRadians((ang)));
                    titanInPossession().get().X += dx;
                    titanInPossession().get().Y += dy;
                }
            }
        }
    }

    protected void minorHoopBounce() {
        for (GoalHoop goal : this.lowGoals) {
            GoalSprite tempGoal = new GoalSprite(goal, 0, 0, new ScreenConst(1920, 1080)); //Just for using the g2d intersect method
            Rectangle2D ballBounds = ball.asRect();
            while (tempGoal.intersects(ballBounds)) {
                ballBounds = ball.asRect();
                int wRad = (int) ((tempGoal.width - 1) / 2);
                int hRad = (int) ((tempGoal.height - 1) / 2);
                double ang = Util.degreesFromCoords(tempGoal.x + wRad - ball.X - 15, tempGoal.y + hRad - ball.Y - 15);
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
        ball.X = BALL_X;
        ball.Y = BALL_Y;
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
            if (client != null) {
                client.setSelection(client.getPossibleSelection().get(0));
            }
        }
        for(ClientPacket packet : lastControlPacket){
            packet = new ClientPacket();
        }
        lastPossessed = null;
        players[0].setX(HOME_HI_X);
        players[1].setX(AWAY_HI_X);
        players[0].setY(HOME_HI_Y);
        players[1].setY(AWAY_HI_Y);
        if (GOALIE_DISABLED) {
            players[0].setX(9999999);
            players[1].setX(9999999);
            players[0].setY(9999999);
            players[1].setY(9999999);
        }
        int nonGoaliePerTeam = (players.length - 2) / 2;
        int i = 2;
        int teamIndex = 0;
        for (; i < 2 + nonGoaliePerTeam; i++) {
            players[i].isBoosting = false;
            players[i].team = TeamAffiliation.HOME; //unmap sometimes breaks this
            //System.out.println("setting" + players[i].team + players[i].getType() + teamIndex);
            setPosition(players[i], teamIndex, nonGoaliePerTeam);
            if (away.score > home.score) {
                players[i].X += 50;//possession bonus for losing
            }
            teamIndex++;
        }
        teamIndex = 0;
        for (; i < players.length; i++) {
            players[i].team = TeamAffiliation.AWAY; //unmap sometimes breaks this
            setPosition(players[i], teamIndex, nonGoaliePerTeam);
            System.out.println("setting" + players[i].team + players[i].getType() + teamIndex);
            if (away.score > home.score) {
                players[i].X += 50;//possession bonus for losing
            }
            players[i].X = FIELD_LENGTH - players[i].X; //reflect across X mid
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
        if (slots >= 4) {
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
    }

    public void serverDelayReset() {
        this.phase = 9;
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
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
        ball.X = BALL_X;
        ball.Y = BALL_Y;
        this.phase = 8;
    }

    public void intersectAll() {
        for (int n = players.length - 1; n >= 0; n--) {
            if (players[n].actionState == Titan.TitanState.SHOOT
                    && players[n].actionFrame < 2) {
                return;
            }
            if (players[n].actionState == Titan.TitanState.CURVE_LEFT
                    && players[n].actionFrame < 2) {
                return;
            }
            if (players[n].actionState == Titan.TitanState.CURVE_RIGHT
                    && players[n].actionFrame < 2) {
                return;
            }
            if (players[n].actionState == Titan.TitanState.LOB
                    && players[n].actionFrame >= 9
                    && players[n].actionFrame <= 30) {
                return;
            }
        }
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
        if (from != null) {
            Titan t = titanFromPacket(from);
            if (t == null) {
                System.out.println("got passed a bad titan index! Possibly from another game?");
                return;
            }
            int btn = 0;
            if (request.shotBtn) {
                btn = 1;
            }
            if (request.passBtn) {
                btn = 3;
            }
            if (request.posX != -1 && request.posY != -1 && btn != 0) {
                this.serverMouseRoutine(t, request.posX, request.posY, btn, request.camX, request.camY);
            }
            this.processProgramming(t, request);
            this.processKeys(request, from);
            //System.out.println(from.toString() + " packet");
            for (PlayerDivider client : clients) {
                if (client.id == from.id) {
                    from.ready = true;
                    int classSelIndex = client.possibleSelection.get(0) - 1;
                    players[classSelIndex].setType(request.classSelection);
                    if (request.masteries != null) {
                        request.masteries.applyMasteries(players[classSelIndex]);
                    }
                }
            }
            kickoff();
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

    public void kickoff() {
        if (!began) {
            began = true;
            /*for (PlayerDivider client : clients) {
                if (!client.ready) {
                    unlock();
                    return;
                }
            }*/
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
        if (controlsHeld.BOOST == 1 && (this.phase == 8 || phase == 101)) {
            t.isBoosting = true;
        }//Hold to boost
        if (controlsHeld.BOOST == -1 && (this.phase == 8 || phase == 101)) {
            t.isBoosting = false;
        }//Hold to boost
        if (controlsHeld.BOOST_LOCK == 1 && (this.phase == 8 || phase == 101)) {
            t.isBoosting = !t.isBoosting;
        }//Lock on press
        if (t.fuel < 1.0) {
            t.isBoosting = false;//out
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
            Cloner c = new Cloner();
            lastControlPacket[clientIndex] = c.deepClone(controls);
            boost(controlsHeld, t);
            if (controlsHeld.SWITCH == 1 && this.phase == 8 && t.actionState == Titan.TitanState.IDLE) {
                from.incSel(this);
                t.runLeft = 0;
                t.runRight = 0;
                t.runDown = 0;
                t.runUp = 0;
                t.runningFrame = 0;
                t.diagonalRunDir = 0;
            }
            if ((controlsHeld.STEAL == 1 && this.phase == 8 && t.actionState == Titan.TitanState.IDLE)) {
                if (!effectPool.isStunned(t) && !effectPool.hasEffect(t, EffectId.COOLDOWN_Q)) {
                    try {
                        boolean stolen = ability.castQ(this, t);
                        if (t.actionState == Titan.TitanState.IDLE && !stolen) {//Curve may be set by ability
                            t.actionState = Titan.TitanState.STEAL;
                            t.actionFrame = 0;
                        }
                    } catch (Exception e) {
                    }

                }
            }
            if (controlsHeld.CAM == 1 && this.phase == 5) {
                this.phase = 6;
            }
            if (controlsHeld.CAM == 1 && this.phase == 2) {
                this.phase = 3;
            }
            if (controlsHeld.CAM == 1 && this.phase == 50) {
                this.phase = 2;
            }
            if (controlsHeld.CAM == 1 && this.phase == 0) {
                this.phase = 50;
            }
            if (controlsHeld.CAM == 1 && this.phase == 12) {
                this.phase = 0;
            }
            if (controlsHeld.E == 1 && this.phase == 8){
                if((t.actionState == Titan.TitanState.IDLE) ) {
                    if (!effectPool.isStunned(t)) {
                        try {
                            boolean caststun = ability.castE(this, t);
                            if (caststun) {//Curve may be set by ability
                                t.actionState = Titan.TitanState.A1;
                                t.actionFrame = 0;
                            }
                        } catch (Exception e) {
                        }
                    }
                }
            }
            if (controlsHeld.R == 1 && this.phase == 8){
                if((t.actionState == Titan.TitanState.IDLE)) {
                    if (!effectPool.isStunned(t)) {
                        try {
                            boolean caststun = ability.castR(this, t);
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
            if (controlsHeld.R == -1 || controlsHeld.E == -1 || controlsHeld.STEAL == -1 && this.phase == 8 && t.actionState == Titan.TitanState.IDLE) {
                //this.colliders = new ArrayList<>();
            }
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
                int toX = t.marchingOrderX;
                int toY = t.marchingOrderY;
                if (t.marchingOrderX == -1 && t.marchingOrderY == -1) {
                    //move to ball
                    toX = (int) (ball.X + ball.centerDist);
                    toY = (int) (ball.Y + ball.centerDist);
                }
                if (t.X + 35 > toX + t.actualSpeed()) { //speed included to avoid jittery finish
                    t.runLeft = 1;
                    t.runRight = 0;
                } else if (t.X + 35 < toX - t.actualSpeed()) {
                    t.runRight = 1;
                    t.runLeft = 0;
                } else { //Calm down once we arrive at click destination
                    t.runRight = 0;
                    t.runLeft = 0;
                }
                if (t.Y + 35 > toY + t.actualSpeed()) {
                    t.runUp = 1;
                    t.runDown = 0;
                } else if (t.Y + 35 < toY - t.actualSpeed()) {
                    t.runDown = 1;
                    t.runUp = 0;
                } else {
                    t.runUp = 0;
                    t.runDown = 0;
                }
            } else {
                if (controlsHeld.RIGHT == 1 && this.phase == 8) {
                    if (t.actionState == Titan.TitanState.IDLE ||
                            t.actionFrame >= (int) (t.kickingFrames*SHOT_FREEZE_RATIO)) {
                        t.runLeft = 0;
                        t.runRight = 1;
                    } else {
                        t.moveMemR = true;
                        t.moveMemL = false;
                    }
                }
                if (controlsHeld.LEFT == 1 && this.phase == 8) {
                    if (t.actionState == Titan.TitanState.IDLE ||
                            t.actionFrame >= (int) (t.kickingFrames*SHOT_FREEZE_RATIO)) {
                        t.runLeft = 1;
                        t.runRight = 0;
                    } else {
                        t.moveMemR = false;
                        t.moveMemL = true;
                    }
                }
                if (controlsHeld.UP == 1 && this.phase == 8) {
                    if (t.actionState == Titan.TitanState.IDLE ||
                            t.actionFrame >= (int) (t.kickingFrames*SHOT_FREEZE_RATIO)) {
                        t.runUp = 1;
                        t.runDown = 0;
                    } else {
                        t.moveMemU = true;
                        t.moveMemD = false;
                    }
                }
                if (controlsHeld.DOWN == 1 && this.phase == 8) {
                    if (t.actionState == Titan.TitanState.IDLE ||
                            t.actionFrame >= (int) (t.kickingFrames*SHOT_FREEZE_RATIO)) {
                        t.runUp = 0;
                        t.runDown = 1;
                    } else {
                        t.moveMemU = false;
                        t.moveMemD = true;
                    }
                }
                //Done with helds

                //The releases below here
                if (controlsHeld.RIGHT == -1 && this.phase == 8) {
                    t.runRight = 0;
                    t.runningFrame = 0;
                    t.diagonalRunDir = 0;
                    t.moveMemR = false;
                }
                if (controlsHeld.LEFT == -1 && this.phase == 8) {
                    t.runLeft = 0;
                    t.runningFrame = 0;
                    t.diagonalRunDir = 0;
                    t.moveMemL = false;
                }
                if (controlsHeld.UP == -1 && this.phase == 8) {
                    t.runUp = 0;
                    t.runningFrame = 0;
                    t.dirToBall = 0;
                    t.moveMemU = false;
                }
                if (controlsHeld.DOWN == -1 && this.phase == 8) {
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

    public void gameTick() throws Exception {
        //System.out.println("tock " + began + ended);
        lock();
        if (began && !ended) {
            try {
                framesSinceStart++;
                gameDurationRuleChanges();
                List<Entity> tempSolids = new ArrayList<>();
                tempSolids.addAll(Arrays.asList(players));
                trimEntities(entityPool);
                tempSolids.addAll(entityPool);
                allSolids = tempSolids.toArray(new Entity[tempSolids.size()]);
                updateBallIfPossessed();
                effectPool.tickAll(this);
                doHealthModification();
                updateSelectedDirection();
                cullOldColliders();
            } catch (Exception e) {
                e.printStackTrace();
            }
            for (Titan t : players) {
                if (t.isBoosting) {
                    t.fuel -= .5;
                    if (t.fuel < 0) {
                        t.fuel = 0;
                    }
                } else {
                    if (t.fuel > 25.0) {
                        t.fuel += .18;//regen bonus
                    } else {
                        t.fuel += .07;
                    }
                    if (t.fuel > 100.0) {
                        t.fuel = 100.0;
                    }
                }
                if (t.runRight == 1) runRightCtrl(t);
                if (t.runLeft == 1) runLeftCtrl(t);
                if (t.runUp == 1) runUpCtrl(t);
                if (t.runDown == 1) runDownCtrl(t);
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
        if (ball.X < GameEngine.MIN_X) ball.X = GameEngine.MIN_X;
        if (ball.X > GameEngine.MAX_X) ball.X = GameEngine.MAX_X;
        if (ball.Y < GameEngine.MIN_Y) ball.Y = GameEngine.MIN_Y;
        if (ball.Y > GameEngine.MAX_Y) ball.Y = GameEngine.MAX_Y;
        resurrectAll();
        unlock();
    }

    private void resurrectAll() {
        for(Titan t : players){
            if(t.resurrecting){
                t.resurrect(this);
            }
        }
    }

    private void gameDurationRuleChanges() throws Exception {
        final long FPS = 1000 / GAMETICK_MS;
        if ((options.goalieIndex == 1) && framesSinceStart / FPS > GOALIE_DISABLE_TIME) {
            cullGoalies();
        }
        if (framesSinceStart / FPS > PAIN_DISABLE_TIME) {
            hoopDmg = false;
        }
        if (framesSinceStart / FPS > this.options.suddenDeathIndex * 60) {
            suddenDeath = true;
            checkWinCondition();
        }
        if (framesSinceStart / FPS > this.options.tieIndex * 60) {
            tieAble = true;
            checkWinCondition();
        }
    }

    protected void trimEntities(List<Entity> entityPool) {
        List<Entity> temp = entityPool;
        entityPool = new ArrayList<>();
        for (int i = 0; i < temp.size(); i++) {
            if (temp.get(i).getHealth() > 0.0) {
                entityPool.add(temp.get(i));
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
        if (t.possession == 1) {
            int valuePlayerX = (int) t.X;
            int valuePlayerY = (int) t.Y;
            if (GOALIE_DISABLED || (numSel != 1 && numSel != 2)) {
                ball.X = (valuePlayerX + 35 - ball.centerDist);
                ball.Y = (valuePlayerY + 35 - ball.centerDist);
            }
            if (!GOALIE_DISABLED) {
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
        Rectangle r1 = new Rectangle(valuePlayerX + SPRITE_X_EMPTY / 2, valuePlayerY + SPRITE_Y_EMPTY / 2, 70 - SPRITE_X_EMPTY, 70 - SPRITE_Y_EMPTY);
        r1 = goalieHitboxOverride(numSel, r1);
        Rectangle r2 = new Rectangle((int) ball.X, (int) ball.Y, 30, 30);
        if ((r1.intersects(r2))) {
            Titan t = players[numSel - 1];
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

    protected Rectangle goalieHitboxOverride(int numSel, Rectangle rect) {
        if (numSel > 2 || GOALIE_DISABLED) {
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
        intersectAll(); //Update state of variables doubleclick fails
        if (t.possession == 1 && t.actionState == Titan.TitanState.IDLE
                && !effectPool.isStunned(t)) {
            if (phase == 8 && btn == 1) {
                t.actionState = Titan.TitanState.SHOOT;
            } else if (phase == 8 && btn == 3) {
                t.actionState = Titan.TitanState.LOB;
            } else if (phase == 8 && btn == 4) {
                t.actionState = Titan.TitanState.CURVE_LEFT;
            } else if (phase == 8 && btn == 5) {
                t.actionState = Titan.TitanState.CURVE_RIGHT;
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
                    Rectangle ballTangle = new Rectangle((int) ball.X, (int) ball.Y, ball.width, ball.height);
                    Rectangle playertangle = new Rectangle((int) players[pIndex].X + SPRITE_X_EMPTY / 2, (int) players[pIndex].Y + SPRITE_Y_EMPTY / 2,
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
        if (!GOALIE_DISABLED && !anyClientSelected(1) && !effectPool.isRooted(players[0])) {
            goalieTactics(players[0], TeamAffiliation.HOME);
        }
        if (!GOALIE_DISABLED && !anyClientSelected(2) && !effectPool.isRooted(players[1])) {
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
        if (GOALIE_DISABLED) {
            goalie.X = 99999;
            goalie.Y = 99999;
            return;
        }
        if (effectPool.isRooted(goalie)) {
            return;
        }
        int YMAX = GOALIE_Y_MAX, XMAX = GOALIE_XH_MAX, YMIN = GOALIE_Y_MIN, XMIN = GOALIE_XH_MIN;
        if (team == TeamAffiliation.AWAY) {
            XMAX = GOALIE_XA_MAX;
            XMIN = GOALIE_XA_MIN;
        }
        Rectangle ballBounds = new Rectangle((int) this.ball.X, (int) this.ball.Y, 30, 30);
        for (GoalHoop goal : this.lowGoals) {
            GoalSprite tempGoal = new GoalSprite(goal, 0, 0, new ScreenConst(1920, 1080)); //Just for using the g2d intersect method
            if (goalie.possession == 1 &&
                    tempGoal.intersects(ballBounds) && goal.team.equals(TeamAffiliation.HOME)) {
                if (!goalie.collidesSolid(this, allSolids, 0, (int) +goalie.speed)) {
                    goalie.setX((int) (goalie.getX() + goalie.speed));
                    if (goalie.getX() > XMAX) goalie.setX(XMAX);
                }
            }
            if (goalie.possession == 1 &&
                    tempGoal.intersects(ballBounds) && goal.team.equals(TeamAffiliation.AWAY)) {
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
        int maxX = 2070;
        if (t.inactiveDir == 1 && !effectPool.isRooted(t) && !effectPool.hasEffect(t, EffectId.DEAD)) {
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
        if (t.inactiveDir == 2 && !effectPool.isRooted(t) && !effectPool.hasEffect(t, EffectId.DEAD)) {
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
        if (!effectPool.isRooted(t) && !effectPool.hasEffect(t, EffectId.DEAD)) {
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
        if (phase == 8 || phase == 101) {
            if (!GOALIE_DISABLED && t.id.equals(players[0].id) && !effectPool.isRooted(players[0])) {
                players[0].setY((int) players[0].getY() - 4);
                if (players[0].getY() < GOALIE_Y_MIN) players[0].setY(GOALIE_Y_MIN);
            }
            if (!GOALIE_DISABLED && t.id.equals(players[1].id) && !effectPool.isRooted(players[1])) {
                players[1].setY((int) players[1].getY() - 4);
                if (players[1].getY() < GOALIE_Y_MIN) players[1].setY(GOALIE_Y_MIN);
            }
            int p = GOALIE_DISABLED ? 0 : 2;
            for (; p < players.length; p++) {
                if (t.id.equals(players[p].id) && !effectPool.isRooted(t)
                    /*&& t.actionState == Titan.TitanState.IDLE*/) {
                    if (!t.collidesSolid(this, allSolids, (int) -t.speed, 0)) {
                        if (t.X <= ball.X) t.dirToBall = 1;
                        if (t.X > ball.X) t.dirToBall = 2;
                        if (t.diagonalRunDir == 1) t.dirToBall = 1;
                        if (t.diagonalRunDir == 2) t.dirToBall = 2;
                        t.translateBounded(this, 0.0, -t.actualSpeed());
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
        if (phase == 8 || phase == 101) {
            if (!GOALIE_DISABLED && t.id.equals(players[0].id) && !effectPool.isRooted(players[0])) {
                players[0].setY((int) players[0].getY() + 4);
                if (players[0].getY() > GOALIE_Y_MAX) players[0].setY(GOALIE_Y_MAX);
            }
            if (!GOALIE_DISABLED && t.id.equals(players[1].id) && !effectPool.isRooted(players[1])) {
                players[1].setY((int) players[1].getY() + 4);
                if (players[1].getY() > GOALIE_Y_MAX) players[1].setY(GOALIE_Y_MAX);
            }
            int p = GOALIE_DISABLED ? 0 : 2;
            for (; p < players.length; p++) {
                if (t.id.equals(players[p].id) && !effectPool.isRooted(t)
                    /*&& t.actionState == Titan.TitanState.IDLE*/) {
                    if (!t.collidesSolid(this, allSolids, (int) t.speed, 0)) {
                        if (t.X <= ball.X) t.dirToBall = 1;
                        if (t.X > ball.X) t.dirToBall = 2;
                        if (t.diagonalRunDir == 1) t.dirToBall = 1;
                        if (t.diagonalRunDir == 2) t.dirToBall = 2;
                        t.translateBounded(this, 0.0, t.actualSpeed());
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
        if (phase == 8 || phase == 101) {
            if (!GOALIE_DISABLED && t.id.equals(players[0].id) && !effectPool.isRooted(players[0])) {
                players[0].setX((int) players[0].getX() + 4);
                if (players[0].getX() > GOALIE_XH_MAX) players[0].setX(GOALIE_XH_MAX);
            }
            if (!GOALIE_DISABLED && t.id.equals(players[1].id) && !effectPool.isRooted(players[1])) {
                players[1].setX((int) players[1].getX() + 4);
                if (players[1].getX() > GOALIE_XA_MAX) players[1].setX(GOALIE_XA_MAX);
            }
            int p = GOALIE_DISABLED ? 0 : 2;
            for (; p < players.length; p++) {
                if (t.id.equals(players[p].id) && !effectPool.isRooted(t)
                        /*&& t.actionState == Titan.TitanState.IDLE*/) {
                    if (!t.collidesSolid(this, allSolids, 0, (int) t.speed)) {
                        t.diagonalRunDir = 1;
                        t.translateBounded(this, t.actualSpeed(), 0.0);
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
        if (phase == 8 || phase == 101) {
            if (!GOALIE_DISABLED && t.id.equals(players[0].id) && !effectPool.isRooted(players[0])) {
                players[0].setX((int) players[0].getX() - 3);
                if (players[0].getX() < GOALIE_XH_MIN) players[0].setX(GOALIE_XH_MIN);
            }
            if (!GOALIE_DISABLED && t.id.equals(players[1].id) && !effectPool.isRooted(players[1])) {
                players[1].setX((int) players[1].getX() - 3);
                if (players[1].getX() < GOALIE_XA_MIN) players[1].setX(GOALIE_XA_MIN);
            }
            int p = GOALIE_DISABLED ? 0 : 2;
            for (; p < players.length; p++) {
                if (t.id.equals(players[p].id) && !effectPool.isRooted(t)
                    /*&& t.actionState == Titan.TitanState.IDLE*/) {
                    t.diagonalRunDir = 2;
                    if (!t.collidesSolid(this, allSolids, 0, (int) -t.speed)) {
                        t.translateBounded(this, -t.actualSpeed(), 0.0);
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

    // Effect of the kicked ball
    public void shootingBall(Titan t) throws Exception {
        //System.out.println("pow " + xKickPow + " " + yKickPow)
        if (t.actionFrame == 0 &&
                t.getType() != null &&
                !t.getType().equals(TitanType.GOALIE)) {
            t.pushMove();
            centerBall(t);
        }
        t.actionFrame += 1;
        t.kickingFrames = 28;
        if(t.actionFrame == (int) (t.kickingFrames*SHOT_FREEZE_RATIO)){
            t.popMove();
        }
        if (t.actionFrame < t.kickingFrames) {
            t.possession = 0;
            setBallFromTip();
            for (int i = 0; i < 8; i++) {
                ball.X += 5.89 * xKickPow * t.throwPower;
                ball.Y -= 5.89 * yKickPow * t.throwPower;
                intersectAll();
                detectGoals();
                bounceWalls();
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
        if (t.actionFrame == 0) {
            t.pushMove();
            centerBall(t);
        }
        t.actionFrame += 1;
        //System.out.println(t.actionState.toString() + t.actionFrame);
        t.kickingFrames = 40;
        if(t.actionFrame == (int) (t.kickingFrames*SHOT_FREEZE_RATIO)){
            t.popMove();
        }
        if (t.actionFrame < t.kickingFrames) {
            t.possession = 0;
            setBallFromTip();
            //Only check for lob intersections while near start or end
            if (t.actionFrame < 9 || t.actionFrame > 30) {
                for (int i = 0; i < 8; i++) {
                    ball.X += 2.71 * xKickPow * t.throwPower;
                    ball.Y -= 2.71 * yKickPow * t.throwPower;
                    intersectAll();
                    detectGoals();
                    bounceWalls();
                }
            } else {
                for (int i = 0; i < 8; i++) {
                    ball.X += 2.71 * xKickPow * t.throwPower;
                    ball.Y -= 2.71 * yKickPow * t.throwPower;
                    bounceWalls();
                }
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

    protected void centerBall(Titan t) {
        ball.X = t.getX() + 35 - ball.centerDist;
        ball.Y = t.getY() + 35 - ball.centerDist;
    }

    public void bounceWalls() {
        Box[] temp = new Box[entityPool.size()];
        entityPool.toArray(temp);
        if (ball.collidesSolid(this, temp, 0, 0)) {
            //System.out.println("wall");
            xKickPow = -xKickPow;
            yKickPow = -yKickPow;
        }
        if (ball.X > GameEngine.MAX_X) xKickPow = -xKickPow;
        if (ball.X < GameEngine.MIN_X) xKickPow = -xKickPow;
        if (ball.Y < GameEngine.MIN_Y) yKickPow = -yKickPow;
        if (ball.Y > GameEngine.MAX_Y) yKickPow = -yKickPow;
    }

    protected void curve(Titan t, int sign) throws Exception {
        //System.out.println("pow " + xKickPow + " " + yKickPow);
        if (t.actionFrame == 0) {
            centerBall(t);
            t.pushMove();
        }
        t.actionFrame += 1;
        //System.out.println(t.actionState.toString() + t.actionFrame);
        t.kickingFrames = 17;
        if(t.actionFrame == (int) (t.kickingFrames*SHOT_FREEZE_RATIO)){
            t.popMove();
        }
        if (t.actionFrame < t.kickingFrames) {
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
                ball.X += 2.95 * tempXPow * t.throwPower;
                ball.Y -= 2.95 * tempYPow * t.throwPower;
                intersectAll();
                detectGoals();
                bounceWalls();
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

    void checkWinCondition() throws Exception {
        int SOFT_WIN = this.options.playToIndex;
        int WIN_BY = this.options.winByIndex;
        int HARD_WIN = this.options.hardWinIndex;
        if (!suddenDeath) {
            if ((home.score >= SOFT_WIN && home.score - away.score >= WIN_BY) ||
                    home.score >= HARD_WIN) {
                triggerWin(home);
            }
            if ((away.score >= SOFT_WIN && away.score - home.score >= WIN_BY) ||
                    away.score >= HARD_WIN) {
                triggerWin(away);
            }
        } else {
            if (tieAble) {
                triggerTie(away);
                triggerTie(home);
            } else {
                if (extremeSuddenDeath) {
                    if (home.score > away.score) {
                        triggerWin(home);
                    }
                    if (away.score > home.score) {
                        triggerWin(away);
                    }
                } else {
                    if ((int) home.score > (int) away.score) {
                        triggerWin(home);
                    }
                    if ((int) away.score > (int) home.score) {
                        triggerWin(away);
                    }
                }

            }

        }
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
