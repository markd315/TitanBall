package gameserver.engine;

import gameserver.effects.EffectId;
import gameserver.entity.Box;
import gameserver.entity.Entity;
import gameserver.entity.Titan;
import gameserver.entity.TitanType;
import gameserver.gamemanager.GamePhase;
import networking.PlayerDivider;
import util.Util;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles all ball kinematics, shooting, lobbing, curving, wall/titan bounce physics,
 * intersection detection, and goal detection/scoring.
 */
public class BallPhysicsEngine {

    protected final GameEngine context;

    public BallPhysicsEngine(GameEngine context) {
        this.context = context;
    }

    public GameEngine.ShotType predictShotTypeFromRay(double bx, double by, double dx, double dy, TeamAffiliation throwerTeam) {
        double len = Math.hypot(dx, dy);
        if (len == 0) return GameEngine.ShotType.NONE;
        double uX = dx / len;
        double uY = dy / len;

        // 1. Center Goal (hiGoals) takes absolute priority
        if (context.hiGoals != null) {
            for (GoalHoop goal : context.hiGoals) {
                if (goal != null && goal.team != throwerTeam) {
                    if (rayIntersectsHoop(bx, by, uX, uY, goal)) {
                        return GameEngine.ShotType.CENTERGOAL;
                    }
                }
            }
        }

        // 2. Side Goals (lowGoals) checked only if no Center Goal is targeted
        if (context.lowGoals != null) {
            for (GoalHoop goal : context.lowGoals) {
                if (goal != null && goal.team != throwerTeam) {
                    if (rayIntersectsHoop(bx, by, uX, uY, goal)) {
                        return GameEngine.ShotType.SIDEGOAL;
                    }
                }
            }
        }

        return GameEngine.ShotType.NONE;
    }

    public boolean rayIntersectsHoop(double bx, double by, double uX, double uY, GoalHoop goal) {
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

    public GameEngine.ShotType predictShotTrajectory(double startX, double startY, double[] vel, TeamAffiliation throwerTeam) {
        if (vel == null || (vel[0] == 0 && vel[1] == 0)) return GameEngine.ShotType.NONE;
        double bx = (context.ball != null) ? (context.ball.X + context.ball.width / 2.0) : startX;
        double by = (context.ball != null) ? (context.ball.Y + context.ball.height / 2.0) : startY;
        return predictShotTypeFromRay(bx, by, vel[0], vel[1], throwerTeam);
    }

    public boolean ballIntersectsEllipse(GoalHoop goal) {
        CollisionMath.EllipseData g = goal.ellipseData();
        CollisionMath.EllipseData b = context.ball.ellipseData();
        return CollisionMath.ellipseBoundsIntersect(b, g);
    }

    public boolean isGoalie(Titan t) {
        if (t == null) return false;
        return t.getType() == TitanType.GOALIE || t == context.players[0] || t == context.players[1];
    }

    public void detectGoals() {
        if (context.phase == GamePhase.SCORE_FREEZE || !context.ballVisible) {
            return;
        }
        if (contactExemptBall()) {
            return;
        }

        for (GoalHoop goal : context.lowGoals) {
            if (ballIntersectsEllipse(goal) && goal.checkReady()) {
                Optional<Titan> possessor = context.titanInPossession();
                if (possessor.isPresent() && isGoalie(possessor.get()) && possessor.get().team == goal.team) {
                    continue;
                }
                Titan mover = getAnyBallMover();
                if (mover != null && isGoalie(mover) && mover.team == goal.team) {
                    continue;
                }
                if (context.lastPossessed != null) {
                    Titan lp = context.titanByID(context.lastPossessed.toString()).orElse(null);
                    if (lp != null && isGoalie(lp) && lp.team == goal.team) {
                        continue;
                    }
                }
                if (context.activeLobThrower != null && isGoalie(context.activeLobThrower) && context.activeLobThrower.team == goal.team) {
                    continue;
                }
                Team enemy, us;
                List<PlayerDivider> ourSideScorers;
                List<PlayerDivider> enemySideScorers;
                if (goal.team == TeamAffiliation.HOME) {
                    us = context.away;
                    enemy = context.home;
                    ourSideScorers = context.activeAwaySidegoalScorers;
                    enemySideScorers = context.activeHomeSidegoalScorers;
                } else {
                    us = context.home;
                    enemy = context.away;
                    ourSideScorers = context.activeHomeSidegoalScorers;
                    enemySideScorers = context.activeAwaySidegoalScorers;
                }
                goal.trigger(context.c != null ? context.c.HOOP_SIDEGOAL_CD_MS : 1800);
                Titan defGoalie = (goal.team == TeamAffiliation.HOME) ? context.players[0] : context.players[1];
                context.stats.grant(context, defGoalie, StatEngine.StatEnum.SIDEGOALS_CONCEDED);
                UUID attackerId = (us.which == TeamAffiliation.HOME) ? context.lastHomePossessor : context.lastAwayPossessor;
                PlayerDivider scorer = (attackerId != null) ? context.clientFromTitan(context.titanByID(attackerId.toString()).orElse(null)) : context.getPossessorOrThrower();
                if (scorer != null) {
                    context.stats.grant(scorer, StatEngine.StatEnum.SIDEGOALS);
                    ourSideScorers.add(scorer);

                    UUID passId = (us.which == TeamAffiliation.HOME) ? context.lastHomePasser : context.lastAwayPasser;
                    if (passId != null && (attackerId == null || !passId.equals(attackerId))) {
                        Titan assister = context.titanByID(passId.toString()).orElse(null);
                        if (assister != null && assister.team == us.which) {
                            context.stats.grant(context, assister, StatEngine.StatEnum.SIDEGOALASSISTS);
                        }
                    }
                }
                if (us.which == TeamAffiliation.HOME) {
                    context.lastHomePasser = null;
                } else {
                    context.lastAwayPasser = null;
                }
                if (us.score % 1.0 == .75) {
                    goal.freeze();
                }
                us.score += .25;

                if (ourSideScorers.size() >= 4) {
                    for (int sIdx = 0; sIdx < 4 && !ourSideScorers.isEmpty(); sIdx++) {
                        PlayerDivider s = ourSideScorers.remove(0);
                        if (s != null) {
                            context.stats.grant(s, StatEngine.StatEnum.POINTS, 0.25);
                        }
                    }
                }
                context.checkWinCondition(false);
                boolean saveProgress = (enemy == context.home)
                        ? context.homeGoaliePurchasedUpgrades.contains("siege.t5.saveprogress")
                        : context.awayGoaliePurchasedUpgrades.contains("siege.t5.saveprogress");
                if (!saveProgress) {
                    enemy.score = Math.floor(enemy.score);
                    enemySideScorers.clear();
                }
            }
        }

        for (GoalHoop goal : context.hiGoals) {
            if (ballIntersectsEllipse(goal) && goal.checkReady()) {
                Optional<Titan> possessor = context.titanInPossession();
                if (possessor.isPresent() && isGoalie(possessor.get()) && possessor.get().team == goal.team) {
                    continue;
                }
                Titan mover = getAnyBallMover();
                if (mover != null && isGoalie(mover) && mover.team == goal.team) {
                    continue;
                }
                if (context.lastPossessed != null) {
                    Titan lp = context.titanByID(context.lastPossessed.toString()).orElse(null);
                    if (lp != null && isGoalie(lp) && lp.team == goal.team) {
                        continue;
                    }
                }
                if (context.activeLobThrower != null && isGoalie(context.activeLobThrower) && context.activeLobThrower.team == goal.team) {
                    continue;
                }
                Team us, enemy;
                List<PlayerDivider> ourSideScorers;
                List<PlayerDivider> enemySideScorers;
                if (goal.team == TeamAffiliation.HOME) {
                    us = context.away;
                    enemy = context.home;
                    ourSideScorers = context.activeAwaySidegoalScorers;
                    enemySideScorers = context.activeHomeSidegoalScorers;
                } else {
                    us = context.home;
                    enemy = context.away;
                    ourSideScorers = context.activeHomeSidegoalScorers;
                    enemySideScorers = context.activeAwaySidegoalScorers;
                }
                goal.trigger();
                Titan defGoalieHi = (goal.team == TeamAffiliation.HOME) ? context.players[0] : context.players[1];
                context.stats.grant(context, defGoalieHi, StatEngine.StatEnum.GOALS_CONCEDED);

                long iPart = (long) us.score;
                double fPart = us.score - iPart;
                int nSidegoals = (int) Math.round(fPart * 4.0);
                us.score = Math.floor(us.score);
                us.score += fPart * 4 + 1;
                UUID attackerIdHi = (us.which == TeamAffiliation.HOME) ? context.lastHomePossessor : context.lastAwayPossessor;
                PlayerDivider scorerHi = (attackerIdHi != null) ? context.clientFromTitan(context.titanByID(attackerIdHi.toString()).orElse(null)) : context.getPossessorOrThrower();
                if (scorerHi != null) {
                    context.stats.grant(scorerHi, StatEngine.StatEnum.GOALS);
                    double centerPoints = 1.0 + (0.5 * nSidegoals);
                    context.stats.grant(scorerHi, StatEngine.StatEnum.POINTS, centerPoints);

                    UUID passIdHi = (us.which == TeamAffiliation.HOME) ? context.lastHomePasser : context.lastAwayPasser;
                    if (passIdHi != null && (attackerIdHi == null || !passIdHi.equals(attackerIdHi))) {
                        Titan assisterHi = context.titanByID(passIdHi.toString()).orElse(null);
                        if (assisterHi != null && assisterHi.team == us.which) {
                            context.stats.grant(context, assisterHi, StatEngine.StatEnum.GOALASSISTS);
                        }
                    }
                }
                context.lastHomePasser = null;
                context.lastAwayPasser = null;
                for (int sIdx = 0; sIdx < nSidegoals && !ourSideScorers.isEmpty(); sIdx++) {
                    PlayerDivider sideScorer = ourSideScorers.remove(0);
                    if (sideScorer != null) {
                        context.stats.grant(sideScorer, StatEngine.StatEnum.POINTS, 0.5);
                    }
                }
                ourSideScorers.clear();
                context.checkWinCondition(false);
                boolean saveProgressHi = (enemy == context.home)
                        ? context.homeGoaliePurchasedUpgrades.contains("siege.t5.saveprogress")
                        : context.awayGoaliePurchasedUpgrades.contains("siege.t5.saveprogress");
                if (!saveProgressHi) {
                    enemy.score = Math.floor(enemy.score);
                    enemySideScorers.clear();
                }
                us.hasBall = true;
                enemy.hasBall = false;
                context.lastScoredTeam = us.which;
                context.ballVisible = false;
                context.inGame = false;
                context.goalVisible = true;
                System.out.println(us.score + " " + enemy.score);
                context.serverDelayReset();
            }
        }
    }

    public void minorHoopBounce() {
        if (contactExemptBall()) {
            return;
        }
        CollisionMath.Bounds ballBounds = context.ball.asBounds();
        for (GoalHoop goal : context.lowGoals) {
            CollisionMath.EllipseData ell = goal.ellipseData();
            CollisionMath.Bounds ellBounds = new CollisionMath.Bounds(ell.centerX() - ell.radiusX(), ell.centerY() - ell.radiusY(), ell.radiusX() * 2, ell.radiusY() * 2);
            boolean bounced = false;
            double dx = 0;
            double dy = 0;
            while (ellBounds.intersects(ballBounds)) {
                bounced = true;
                ballBounds = context.ball.asBounds();
                double ang = Util.degreesFromCoords(
                        ell.centerX() - context.ball.X - context.ball.centerDist,
                        ell.centerY() - context.ball.Y - context.ball.centerDist
                );
                ang += 180;
                dx = Math.cos(Math.toRadians((ang)));
                dy = Math.sin(Math.toRadians((ang)));
                context.ball.X += dx;
                context.ball.Y += dy;
            }
            if (bounced) {
                int extraKick = (context.c != null) ? context.c.HOOP_BOUNCE_EXTRA_KICK : 30;
                context.ball.X += dx * extraKick;
                context.ball.Y += dy * extraKick;
                if (context.c != null) {
                    context.ball.X = Math.max(context.c.MIN_X, Math.min(context.c.MAX_X, context.ball.X));
                    context.ball.Y = Math.max(context.c.MIN_Y, Math.min(context.c.MAX_Y, context.ball.Y));
                }
            }
        }
    }

    public void updateBallIfPossessed() {
        int i = 1;
        for (Titan t : context.players) {
            updateBallIfPossessed(t, i);
            i++;
        }
    }

    public void updateBallIfPossessed(Titan t, int numSel) {
        if (t.possession == 1 && !context.effectPool.hasEffect(t, EffectId.DEAD)) {
            int valuePlayerX = (int) t.X;
            int valuePlayerY = (int) t.Y;
            boolean goalie = isGoalie(t) || numSel == 1 || numSel == 2;
            if (context.c.GOALIE_DISABLED || !goalie) {
                context.ball.X = (int) Math.round(valuePlayerX + t.width / 2.0 - context.ball.centerDist);
                context.ball.Y = (int) Math.round(valuePlayerY + t.height / 2.0 - context.ball.centerDist);
            }
            if (!context.c.GOALIE_DISABLED && goalie) {
                boolean isHome = (t.team == TeamAffiliation.HOME || t == context.players[0] || (numSel == 1 && t.team != TeamAffiliation.AWAY));
                if (isHome) { // guardian exceptions: HOME
                    context.ball.X = (valuePlayerX + 57);
                    context.ball.Y = (valuePlayerY + 20);
                    int safety = 0;
                    while (ownGoal() && safety++ < 300) {
                        context.ball.X += 1;
                        context.ball.Y -= 1;
                    }
                    if (ownGoal()) {
                        for (GoalHoop g : context.lowGoals) {
                            if (ballIntersectsEllipse(g)) context.ball.X = Math.max(context.ball.X, (int) (g.x + g.w) + 2);
                        }
                        for (GoalHoop g : context.hiGoals) {
                            if (ballIntersectsEllipse(g)) context.ball.X = Math.max(context.ball.X, (int) (g.x + g.w) + 2);
                        }
                    }
                } else { // guardian exceptions: AWAY
                    context.ball.X = (valuePlayerX - 1);
                    context.ball.Y = (valuePlayerY + 20);
                    int safety = 0;
                    while (ownGoal() && safety++ < 300) {
                        context.ball.X -= 1;
                        context.ball.Y -= 1;
                    }
                    if (ownGoal()) {
                        for (GoalHoop g : context.lowGoals) {
                            if (ballIntersectsEllipse(g)) context.ball.X = Math.min(context.ball.X, (int) (g.x - context.ball.width) - 2);
                        }
                        for (GoalHoop g : context.hiGoals) {
                            if (ballIntersectsEllipse(g)) context.ball.X = Math.min(context.ball.X, (int) (g.x - context.ball.width) - 2);
                        }
                    }
                }
            }
        }
    }

    public boolean ownGoal() {
        for (GoalHoop lowgoal : context.lowGoals) {
            if (ballIntersectsEllipse(lowgoal)) {
                return true;
            }
        }
        for (GoalHoop higoal : context.hiGoals) {
            if (ballIntersectsEllipse(higoal)) {
                return true;
            }
        }
        return false;
    }

    public void setBallFromTip() {
        Optional<Titan> tip = context.titanInPossession();
        if (tip.isPresent()) {
            Titan tipTitan = tip.get();
            TeamAffiliation team = tipTitan.team;
            if (team == TeamAffiliation.HOME) {
                context.home.hasBall = true;
                context.away.hasBall = false;
                context.lastHomePossessor = tipTitan.id;
            }
            if (team == TeamAffiliation.AWAY) {
                context.away.hasBall = true;
                context.home.hasBall = false;
                context.lastAwayPossessor = tipTitan.id;
            }
        } else {
            context.home.hasBall = false;
            context.away.hasBall = false;
        }
    }

    public void intersectBall(int numSel, int valuePlayerX, int valuePlayerY) {
        Titan t = context.players[numSel - 1];
        if (t.getType() == TitanType.GOALIE && t.actionState == Titan.TitanState.A2) {
            CollisionMath.Bounds r1 = t.asBounds();
            CollisionMath.Bounds r2 = context.ball.asBounds();
            if (r1.intersects(r2)) {
                bounceOffTitan(t, null);
            }
            return;
        }
        CollisionMath.Bounds r1 = new CollisionMath.Bounds(
                valuePlayerX + context.SPRITE_X_EMPTY / 2.0,
                valuePlayerY + context.SPRITE_Y_EMPTY / 2.0,
                t.width - context.SPRITE_X_EMPTY,
                t.height - context.SPRITE_Y_EMPTY
        );
        r1 = goalieHitboxOverride(numSel, r1);
        CollisionMath.Bounds r2 = context.ball.asBounds();
        if (r1.intersects(r2)) {
            if (t.id.equals(context.players[numSel - 1].id) && !t.id.equals(context.lastPossessed)) {
                Optional<Titan> tip = context.titanInPossession();
                if (!tip.isPresent()) {
                    Titan release = getAnyBallMover();
                    if (release != null) {
                        release.actionState = Titan.TitanState.IDLE;
                        release.actionFrame = 0;
                    }
                    context.activeLobThrower = null;
                    changePossessionStats(release, t);
                    if (t.team == TeamAffiliation.HOME) {
                        context.lastHomePossessor = t.id;
                    } else if (t.team == TeamAffiliation.AWAY) {
                        context.lastAwayPossessor = t.id;
                    }
                    context.home.hasBall = true;
                    context.away.hasBall = false;
                    context.players[numSel - 1].possession = 1;
                    context.players[numSel - 1].queuedBtn = 0;
                    context.lastPossessed = context.players[numSel - 1].id;
                    context.resetAiReactionAfterPossession(context.players[numSel - 1]);
                    updateBallIfPossessed(t, numSel);
                }
            }
        }
    }

    public void changePossessionStats(Titan lost, Titan gained) {
        if (lost != null) {
            TeamAffiliation oldTeam = lost.team;
            if (gained.team == oldTeam) {
                context.stats.grant(context, lost, StatEngine.StatEnum.PASSES);
                if (oldTeam == TeamAffiliation.HOME) {
                    context.lastHomePasser = lost.id;
                } else if (oldTeam == TeamAffiliation.AWAY) {
                    context.lastAwayPasser = lost.id;
                }
            } else {
                context.stats.grant(context, lost, StatEngine.StatEnum.TURNOVERS);
                context.stats.grant(context, gained, StatEngine.StatEnum.BLOCKS);
                context.lastHomePasser = null;
                context.lastAwayPasser = null;
                if (gained.getType() == TitanType.GOALIE) {
                    GameEngine.ShotType st = context.currentShotType;
                    if (st == GameEngine.ShotType.NONE && context.currentStepVel != null) {
                        st = predictShotTrajectory(context.ball.X, context.ball.Y, context.currentStepVel, lost.team);
                    }
                    if (st == GameEngine.ShotType.CENTERGOAL) {
                        context.stats.grant(context, gained, StatEngine.StatEnum.CENTERGOAL_SAVES);
                        context.stats.grant(context, gained, StatEngine.StatEnum.SAVES);
                    } else if (st == GameEngine.ShotType.SIDEGOAL) {
                        context.stats.grant(context, gained, StatEngine.StatEnum.SIDEGOAL_SAVES);
                        context.stats.grant(context, gained, StatEngine.StatEnum.SAVES);
                    }
                }
            }
        } else {
            if (gained.team == TeamAffiliation.HOME) {
                context.lastHomePasser = null;
            } else if (gained.team == TeamAffiliation.AWAY) {
                context.lastAwayPasser = null;
            }
            context.stats.grant(context, gained, StatEngine.StatEnum.REBOUND);
            if (gained.getType() == TitanType.GOALIE) {
                Titan shooter = (context.titanInPossession().isPresent()) ? context.titanInPossession().get() : (context.lastPossessed != null ? context.titanByID(context.lastPossessed.toString()).orElse(null) : null);
                GameEngine.ShotType st = context.currentShotType;
                if (st == GameEngine.ShotType.NONE && shooter != null && shooter.team != gained.team && context.currentStepVel != null) {
                    st = predictShotTrajectory(context.ball.X, context.ball.Y, context.currentStepVel, shooter.team);
                }
                if (st == GameEngine.ShotType.CENTERGOAL) {
                    context.stats.grant(context, gained, StatEngine.StatEnum.CENTERGOAL_SAVES);
                    context.stats.grant(context, gained, StatEngine.StatEnum.SAVES);
                } else if (st == GameEngine.ShotType.SIDEGOAL) {
                    context.stats.grant(context, gained, StatEngine.StatEnum.SIDEGOAL_SAVES);
                    context.stats.grant(context, gained, StatEngine.StatEnum.SAVES);
                }
            }
        }
        context.currentShotType = GameEngine.ShotType.NONE;
    }

    public CollisionMath.Bounds goalieHitboxOverride(int numSel, CollisionMath.Bounds rect) {
        if (numSel < 1 || numSel > context.players.length || context.c.GOALIE_DISABLED) {
            return rect;
        }
        Titan t = context.players[numSel - 1];
        if (t.getType() == TitanType.GOALIE || numSel == 1 || numSel == 2) {
            double w = context.c.GOALIE_INTERCEPT_W;
            double h = context.c.GOALIE_INTERCEPT_H;
            if (context.effectPool.hasEffect(t, EffectId.BLOCK)) {
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

    public Titan getAnyBallMover() {
        for (Titan t : context.players) {
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
            if (t.possession == 1 && t.actionState == Titan.TitanState.IDLE && !context.effectPool.isStunned(t)) {
                int btn = t.queuedBtn;
                int cx = t.queuedClickX;
                int cy = t.queuedClickY;
                int camX = t.queuedCamX;
                int camY = t.queuedCamY;
                t.queuedBtn = 0;
                context.serverMouseRoutine(t, cx, cy, btn, camX, camY);
            } else if (t.possession != 1) {
                t.queuedBtn = 0;
            }
        }
    }

    public void intersectAll() {
        if (contactExemptBall()) return;
        for (int i = 0; i < context.players.length; i++) {
            intersectBall(i + 1, (int) context.players[i].X, (int) context.players[i].Y);
        }
    }

    public boolean contactExemptBall() {
        Titan mover = getAnyBallMover();
        if (mover != null && mover.actionState == Titan.TitanState.LOB && mover.actionFrame >= 3 && mover.actionFrame <= 8) {
            return true;
        }
        if (context.activeLobThrower != null) {
            Titan alt = context.activeLobThrower;
            return alt.actionState == Titan.TitanState.LOB && alt.actionFrame >= 3 && alt.actionFrame <= 8;
        }
        return false;
    }

    public void centerBall(Titan t) {
        context.ball.X = (int) Math.round(t.getX() + t.width / 2.0 - context.ball.centerDist);
        context.ball.Y = (int) Math.round(t.getY() + t.height / 2.0 - context.ball.centerDist);
    }

    public void bounceOffTitan(Titan t, double[] vel) {
        double curDx = (vel != null) ? vel[0] : (context.xKickPow != 0 ? context.xKickPow : 0);
        double curDy = (vel != null) ? vel[1] : (-context.yKickPow);
        CollisionMath.Bounds bBounds = context.ball.asBounds();
        CollisionMath.Bounds oBounds = t.asBounds();
        CollisionMath.CollisionSide side = CollisionMath.getCollisionSide(bBounds, oBounds, curDx, curDy);

        if (side == CollisionMath.CollisionSide.LEFT) {
            context.ball.X = oBounds.minX() - context.ball.width;
            context.xKickPow = -Math.max(5.0, Math.abs(context.xKickPow));
            if (vel != null) vel[0] = -Math.max(0.1, Math.abs(vel[0]));
        } else if (side == CollisionMath.CollisionSide.RIGHT) {
            context.ball.X = oBounds.minX() + oBounds.width();
            context.xKickPow = Math.max(5.0, Math.abs(context.xKickPow));
            if (vel != null) vel[0] = Math.max(0.1, Math.abs(vel[0]));
        } else if (side == CollisionMath.CollisionSide.TOP) {
            context.ball.Y = oBounds.minY() - context.ball.height;
            context.yKickPow = Math.max(5.0, Math.abs(context.yKickPow));
            if (vel != null) vel[1] = -Math.max(0.1, Math.abs(vel[1]));
        } else if (side == CollisionMath.CollisionSide.BOTTOM) {
            context.ball.Y = oBounds.minY() + oBounds.height();
            context.yKickPow = -Math.max(5.0, Math.abs(context.yKickPow));
            if (vel != null) vel[1] = Math.max(0.1, Math.abs(vel[1]));
        } else {
            double ballCX = context.ball.X + context.ball.width / 2.0;
            double titanCX = t.X + t.width / 2.0;
            double ballCY = context.ball.Y + context.ball.height / 2.0;
            double titanCY = t.Y + t.height / 2.0;
            if (Math.abs(ballCX - titanCX) > Math.abs(ballCY - titanCY)) {
                if (ballCX < titanCX) {
                    context.ball.X = oBounds.minX() - context.ball.width;
                    context.xKickPow = -Math.max(5.0, Math.abs(context.xKickPow));
                    if (vel != null) vel[0] = -Math.max(0.1, Math.abs(vel[0]));
                } else {
                    context.ball.X = oBounds.minX() + oBounds.width();
                    context.xKickPow = Math.max(5.0, Math.abs(context.xKickPow));
                    if (vel != null) vel[0] = Math.max(0.1, Math.abs(vel[0]));
                }
            } else {
                if (ballCY < titanCY) {
                    context.ball.Y = oBounds.minY() - context.ball.height;
                    context.yKickPow = Math.max(5.0, Math.abs(context.yKickPow));
                    if (vel != null) vel[1] = -Math.max(0.1, Math.abs(vel[1]));
                } else {
                    context.ball.Y = oBounds.minY() + oBounds.height();
                    context.yKickPow = -Math.max(5.0, Math.abs(context.yKickPow));
                    if (vel != null) vel[1] = Math.max(0.1, Math.abs(vel[1]));
                }
            }
        }
    }

    public void bounceWalls() {
        bounceWalls(context.entityPool.toArray(new Entity[0]), null);
    }

    public void bounceWalls(Entity[] wallEntities) {
        bounceWalls(wallEntities, null);
    }

    public void bounceWalls(Entity[] wallEntities, double[] vel) {
        for (Titan t : context.players) {
            if (t != null && t.getType() == TitanType.GOALIE && t.actionState == Titan.TitanState.A2) {
                if (t.asBounds().intersects(context.ball.asBounds())) {
                    bounceOffTitan(t, vel);
                    return;
                }
            }
        }

        boolean homeDead = context.homeGoaliePurchasedUpgrades.contains("fortress.t4.deadwalls");
        boolean awayDead = context.awayGoaliePurchasedUpgrades.contains("fortress.t4.deadwalls");

        for (Entity ent : wallEntities) {
            if (ent instanceof gameserver.entity.minions.Web && ent.asBounds().intersects(context.ball.asBounds()) && !contactExemptBall()) {
                context.xKickPow = 0;
                context.yKickPow = 0;
                if (vel != null) {
                    vel[0] = 0;
                    vel[1] = 0;
                }
                return;
            }
        }

        // 1. Check solid obstacle entities
        Optional<Box> coll = context.ball.collidesSolidWhich(context, wallEntities);
        if (coll.isPresent() && !contactExemptBall()) {
            Box obstacle = coll.get();
            double curDx = (vel != null) ? vel[0] : (context.xKickPow != 0 ? context.xKickPow : 0);
            double curDy = (vel != null) ? vel[1] : (-context.yKickPow);
            CollisionMath.Bounds bBounds = context.ball.asBounds();
            CollisionMath.Bounds oBounds = obstacle.asBounds();
            CollisionMath.CollisionSide side = CollisionMath.getCollisionSide(bBounds, oBounds, curDx, curDy);

            boolean isDeadWall = (obstacle instanceof Entity ent) &&
                    ((ent.team == TeamAffiliation.HOME && homeDead) || (ent.team == TeamAffiliation.AWAY && awayDead));

            if (isDeadWall) {
                context.xKickPow = 0;
                context.yKickPow = 0;
                if (vel != null) {
                    vel[0] = 0;
                    vel[1] = 0;
                }
                if (side == CollisionMath.CollisionSide.LEFT) {
                    context.ball.X = oBounds.minX() - context.ball.width;
                } else if (side == CollisionMath.CollisionSide.RIGHT) {
                    context.ball.X = oBounds.minX() + oBounds.width();
                } else if (side == CollisionMath.CollisionSide.TOP) {
                    context.ball.Y = oBounds.minY() - context.ball.height;
                } else if (side == CollisionMath.CollisionSide.BOTTOM) {
                    context.ball.Y = oBounds.minY() + oBounds.height();
                }
                return;
            }

            if (side == CollisionMath.CollisionSide.LEFT) {
                context.ball.X = oBounds.minX() - context.ball.width;
                context.xKickPow = -Math.abs(context.xKickPow);
                if (vel != null) vel[0] = -Math.abs(vel[0]);
            } else if (side == CollisionMath.CollisionSide.RIGHT) {
                context.ball.X = oBounds.minX() + oBounds.width();
                context.xKickPow = Math.abs(context.xKickPow);
                if (vel != null) vel[0] = Math.abs(vel[0]);
            } else if (side == CollisionMath.CollisionSide.TOP) {
                context.ball.Y = oBounds.minY() - context.ball.height;
                context.yKickPow = Math.abs(context.yKickPow);
                if (vel != null) vel[1] = -Math.abs(vel[1]);
            } else if (side == CollisionMath.CollisionSide.BOTTOM) {
                context.ball.Y = oBounds.minY() + oBounds.height();
                context.yKickPow = -Math.abs(context.yKickPow);
                if (vel != null) vel[1] = Math.abs(vel[1]);
            } else {
                if (obstacle.ballNearestEdgeisX(context.ball, curDx, curDy)) {
                    context.xKickPow = -context.xKickPow;
                    if (vel != null) vel[0] = -vel[0];
                } else {
                    context.yKickPow = -context.yKickPow;
                    if (vel != null) vel[1] = -vel[1];
                }
            }
        }

        // 2. Check field boundaries
        if (context.ball.X > context.c.MAX_X) {
            if (awayDead) {
                context.xKickPow = 0;
                context.yKickPow = 0;
                context.ball.X = context.c.MAX_X;
                if (vel != null) { vel[0] = 0; vel[1] = 0; }
                return;
            } else {
                context.ball.X = context.c.MAX_X;
                context.xKickPow = -Math.abs(context.xKickPow);
                if (vel != null) vel[0] = -Math.abs(vel[0]);
            }
        }
        if (context.ball.X < context.c.MIN_X) {
            if (homeDead) {
                context.xKickPow = 0;
                context.yKickPow = 0;
                context.ball.X = context.c.MIN_X;
                if (vel != null) { vel[0] = 0; vel[1] = 0; }
                return;
            } else {
                context.ball.X = context.c.MIN_X;
                context.xKickPow = Math.abs(context.xKickPow);
                if (vel != null) vel[0] = Math.abs(vel[0]);
            }
        }
        if (context.ball.Y < context.c.MIN_Y) {
            if ((context.ball.X <= 1024 && homeDead) || (context.ball.X > 1024 && awayDead)) {
                context.yKickPow = 0;
                context.xKickPow = 0;
                context.ball.Y = context.c.MIN_Y;
                if (vel != null) { vel[0] = 0; vel[1] = 0; }
                return;
            } else {
                context.ball.Y = context.c.MIN_Y;
                context.yKickPow = -Math.abs(context.yKickPow);
                if (vel != null) vel[1] = Math.abs(vel[1]);
            }
        }
        if (context.ball.Y > context.c.MAX_Y) {
            if ((context.ball.X <= 1024 && homeDead) || (context.ball.X > 1024 && awayDead)) {
                context.yKickPow = 0;
                context.xKickPow = 0;
                context.ball.Y = context.c.MAX_Y;
                if (vel != null) { vel[0] = 0; vel[1] = 0; }
                return;
            } else {
                context.ball.Y = context.c.MAX_Y;
                context.yKickPow = Math.abs(context.yKickPow);
                if (vel != null) vel[1] = -Math.abs(vel[1]);
            }
        }
    }

    /**
     * DRY unified 800-substep trajectory simulation loop used across shots, lobs, and curveballs.
     */
    public boolean stepBallTrajectory(double[] vel, Entity[] wallSnap, boolean checkVisibility) {
        for (int i = 0; i < 800; i++) {
            context.currentStepVel = vel;
            if (context.phase == GamePhase.SCORE_FREEZE || (checkVisibility && !context.ballVisible)) {
                break;
            }
            if (vel[0] == 0 && vel[1] == 0) {
                break;
            }
            context.ball.X += vel[0];
            context.ball.Y += vel[1];
            intersectAll();
            if (context.titanInPossession().isPresent()) {
                return true;
            }
            detectGoals();
            bounceWalls(wallSnap, vel);
        }
        return false;
    }

    public void shootingBall(Titan t) throws Exception {
        if (t.actionFrame == 0) {
            context.ballVisible = true;
            if (!isGoalie(t)) {
                t.pushMove();
                centerBall(t);
            }
        }
        t.actionFrame += 1;
        t.kickingFrames = 20;
        if (t.actionFrame == context.c.SHOT_CASTLAG_FRAMES) {
            t.popMove();
        }
        if (t.actionFrame < t.kickingFrames) {
            t.possession = 0;
            context.ballVisible = true;
            setBallFromTip();
            double D = 316.0 * t.throwPower;
            double v_tick = (D * (20 - t.actionFrame)) / 190.0;
            double stepFactor = (v_tick * 4.0) / 800.0;

            Entity[] wallSnap = context.entityPool.toArray(new Entity[0]);
            double speedMult = (context.homeGoaliePurchasedUpgrades.contains("fortress.t5.dilators") || context.awayGoaliePurchasedUpgrades.contains("fortress.t5.dilators")) ? context.c.getD("guardian.dilators.speedmult") : 1.0;
            double dxPerStep = stepFactor * context.xKickPow * speedMult;
            double dyPerStep = -stepFactor * context.yKickPow * speedMult;
            double[] vel = new double[]{ dxPerStep, dyPerStep };
            context.currentShotType = predictShotTrajectory(context.ball.X, context.ball.Y, vel, t.team);

            stepBallTrajectory(vel, wallSnap, false);
        }
        if (t.actionFrame == t.kickingFrames) {
            t.actionFrame = 0;
            context.lastPossessed = null;
            bounceWalls();
            t.actionState = Titan.TitanState.IDLE;
            detectGoals();
            minorHoopBounce();
        }
    }

    public void lobbingBall(Titan t) throws Exception {
        context.activeLobThrower = t;
        if (t.actionFrame == 0) {
            context.ballVisible = true;
            if (!isGoalie(t)) {
                t.pushMove();
                centerBall(t);
            }
        }
        t.actionFrame += 1;
        t.kickingFrames = 20;
        if (t.actionFrame == context.c.LOB_CASTLAG_FRAMES) {
            t.popMove();
        }
        if (t.actionFrame < t.kickingFrames) {
            t.possession = 0;
            context.ballVisible = true;
            setBallFromTip();

            double gravityMult = 1.0;
            long lowGrav = (t.team == TeamAffiliation.HOME)
                    ? context.homeGoalieAbilities.lowGravityUntilMs
                    : context.awayGoalieAbilities.lowGravityUntilMs;
            if (context.nowEpochMs < lowGrav) {
                gravityMult = 1.5;
            }
            double noFlyMult = 1.0;
            if (t.team == TeamAffiliation.HOME) {
                if (context.awayNoFlyZoneActive && t.X >= 1368.0 && t.X <= 2012.0) {
                    noFlyMult = 0.5;
                }
            } else if (t.team == TeamAffiliation.AWAY) {
                if (context.homeNoFlyZoneActive && t.X >= 36.0 && t.X <= 680.0) {
                    noFlyMult = 0.5;
                }
            }
            double D = 230.0 * t.throwPower * gravityMult * noFlyMult;
            double v_tick = (D * (20 - t.actionFrame)) / 190.0;
            double stepFactor = (v_tick * 4.0) / 800.0;

            Entity[] wallSnap = context.entityPool.toArray(new Entity[0]);
            double speedMult = (context.homeGoaliePurchasedUpgrades.contains("fortress.t5.dilators") || context.awayGoaliePurchasedUpgrades.contains("fortress.t5.dilators")) ? context.c.getD("guardian.dilators.speedmult") : 1.0;
            double dxPerStep = stepFactor * context.xKickPow * speedMult;
            double dyPerStep = -stepFactor * context.yKickPow * speedMult;
            double[] vel = new double[]{ dxPerStep, dyPerStep };
            context.currentShotType = predictShotTrajectory(context.ball.X, context.ball.Y, vel, t.team);

            stepBallTrajectory(vel, wallSnap, false);
        }
        if (t.actionFrame == t.kickingFrames) {
            t.actionFrame = 0;
            context.activeLobThrower = null;
            context.lastPossessed = null;
            bounceWalls();
            t.actionState = Titan.TitanState.IDLE;
            detectGoals();
            minorHoopBounce();
        }
    }

    public void curve(Titan t, int sign) throws Exception {
        if (t.actionFrame == 0 && !isGoalie(t)) {
            t.pushMove();
            centerBall(t);
        }
        t.actionFrame += 1;
        t.kickingFrames = 20;
        if (t.actionFrame == context.c.SHOT_CASTLAG_FRAMES) {
            t.popMove();
        }
        if (t.actionFrame < t.kickingFrames) {
            t.possession = 0;
            setBallFromTip();

            double uParX = 4.0 * context.xKickPow;
            double uParY = -4.0 * context.yKickPow;
            double uPerpX = sign * (-4.0 * context.yKickPow);
            double uPerpY = sign * (-4.0 * context.xKickPow);

            double qCurveA = 310.0 * t.throwPower;
            double qCurveB = 316.0 * t.throwPower;
            double delta = 0.97;

            double p1x = qCurveA * (Math.cos(delta) * uParX + Math.sin(delta) * uPerpX);
            double p1y = qCurveA * (Math.cos(delta) * uParY + Math.sin(delta) * uPerpY);
            double p2x = qCurveB * uParX;
            double p2y = qCurveB * uParY;

            int k = t.actionFrame;
            double u1 = (double) ((k - 1) * (40 - k)) / 380.0;
            double u2 = (double) (k * (39 - k)) / 380.0;
            double du = u2 - u1;
            double uBar = u1 + u2;

            double dxTick = 2.0 * du * (1.0 - uBar) * p1x + du * uBar * p2x;
            double dyTick = 2.0 * du * (1.0 - uBar) * p1y + du * uBar * p2y;

            Entity[] wallSnap = context.entityPool.toArray(new Entity[0]);
            double speedMult = (context.homeGoaliePurchasedUpgrades.contains("fortress.t5.dilators") || context.awayGoaliePurchasedUpgrades.contains("fortress.t5.dilators")) ? context.c.getD("guardian.dilators.speedmult") : 1.0;
            double dxPerStep = (dxTick / 800.0) * speedMult;
            double dyPerStep = (dyTick / 800.0) * speedMult;
            double[] vel = new double[]{ dxPerStep, dyPerStep };
            context.currentShotType = predictShotTrajectory(context.ball.X, context.ball.Y, vel, t.team);

            stepBallTrajectory(vel, wallSnap, true);
        }
        if (t.actionFrame == t.kickingFrames) {
            t.actionFrame = 0;
            context.lastPossessed = null;
            bounceWalls();
            t.actionState = Titan.TitanState.IDLE;
            detectGoals();
            minorHoopBounce();
        }
    }
}
