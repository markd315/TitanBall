package gameserver.engine;

import gameserver.Const;
import gameserver.effects.EffectId;
import gameserver.effects.effects.Effect;
import gameserver.effects.effects.EmptyEffect;
import gameserver.effects.effects.RatioEffect;
import gameserver.entity.Entity;
import gameserver.entity.Titan;
import gameserver.entity.TitanType;
import gameserver.entity.minions.LaneMinion;
import networking.PlayerDivider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Handles all AI tactics, decision-making trees, autonomous movement,
 * and offensive/defensive strategy for bot Titans.
 */
public class TitanAiEngine {

    protected final GameEngine context;

    public TitanAiEngine(GameEngine context) {
        this.context = context;
    }

    public void aiTactics(int pIndex, int minX, int maxX, int minY, int maxY) {
        Optional<Titan> tip = context.titanInPossession();
        if (pIndex < context.players.length) {
            Titan aiFor = context.players[pIndex];
            if (!context.anyClientSelected(pIndex + 1)) {
                if (aiFor.X + 35 > (context.ball.X + context.ball.centerDist) && aiFor.X > minX) {
                    aiFor.inactiveDir = 2;
                    runLeftAI(aiFor);
                }
                if (aiFor.X + 35 < (context.ball.X + context.ball.centerDist) && aiFor.X < maxX) {
                    aiFor.inactiveDir = 1;
                    runRightAI(aiFor);
                }
                if (tip.isPresent() && tip.get().team != aiFor.team) {
                    if (aiFor.Y + 35 > (context.ball.Y + context.ball.centerDist) && aiFor.Y > minY) {
                        runUpAI(aiFor);
                    }
                    if (aiFor.Y + 35 < (context.ball.Y + context.ball.centerDist) && aiFor.Y < maxY) {
                        runDownAI(aiFor);
                    }
                }
                if (!tip.isPresent()) {
                    CollisionMath.Bounds ballTangle = new CollisionMath.Bounds(
                            (int) context.ball.X, (int) context.ball.Y, context.ball.width, context.ball.height);
                    CollisionMath.Bounds playertangle = new CollisionMath.Bounds(
                            (int) context.players[pIndex].X + context.SPRITE_X_EMPTY / 2.0,
                            (int) context.players[pIndex].Y + context.SPRITE_Y_EMPTY / 2.0,
                            context.players[pIndex].width - context.SPRITE_X_EMPTY,
                            context.players[pIndex].height - context.SPRITE_Y_EMPTY);
                    if (ballTangle.intersects(playertangle)) {
                        context.players[pIndex].possession = 1;
                        context.players[pIndex].inactiveDir = 0;
                        context.players[pIndex].runningFrame = 0;
                        context.players[pIndex].runningFrameCounter = 0;
                        context.players[pIndex].actionState = Titan.TitanState.IDLE;
                        context.players[pIndex].actionFrame = 0;
                    }
                }
            }
        }
    }

    public void yourPlayerTactics() {
        runCoopVsAiEngine();
    }

    public void runCoopVsAiEngine() {
        long nowMs = System.currentTimeMillis();
        for (int i = 0; i < context.players.length; i++) {
            Titan t = context.players[i];
            if (t == null || context.effectPool.hasEffect(t, EffectId.DEAD)) continue;
            if (!context.anyClientSelected(i + 1)) {
                tickAiTitan(t, nowMs);
            }
        }
    }

    public void resetAiReactionAfterPossession(Titan t) {
        if (t == null) return;
        boolean isGoalie = (t.getType() == TitanType.GOALIE);
        int minDelay = (context.options != null) ? context.options.getAiReactionTimeMinMs(isGoalie) : (isGoalie ? 480 : 1200);
        int maxDelay = (context.options != null) ? context.options.getAiReactionTimeMaxMs(isGoalie) : (isGoalie ? 680 : 1700);
        t.aiLastDecisionTimeMs = System.currentTimeMillis();
        t.aiReactionDelayMs = minDelay + (long)(Math.random() * (maxDelay - minDelay + 1));
        t.aiTargetAction = 0;
        t.aiStealTargetStartMs = 0;
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
            double boostThreshold = context.isGoalie(ai) ? 0.0 : 100.0;
            ai.isBoosting = (dist > boostThreshold);
        } else {
            ai.isBoosting = false;
        }
    }

    public void tickAiTitan(Titan ai, long nowMs) {
        if (context.effectPool.isRooted(ai) || context.effectPool.isStunned(ai)) return;
        if (context.c != null && !context.c.AI_OMNISCIENCE_ENABLED && context.effectPool.hasEffect(ai, EffectId.BLIND)) {
            ai.isBoosting = false;
        }

        // 1. STEAL PRIORITY: Governed by 40% anticipation reaction timer (experiential timing feel)
        Optional<Titan> possessorOpt = context.titanInPossession();
        if (possessorOpt.isPresent() && possessorOpt.get().team != ai.team && ai.possession == 0 && context.ballVisible) {
            Titan tip = possessorOpt.get();
            if (context.isTitanVisibleTo(ai, tip) && context.isWithinStealRange(ai, tip)) {
                if (ai.aiStealTargetStartMs == 0) {
                    ai.aiStealTargetStartMs = nowMs;
                    int stealMin = (context.options != null) ? context.options.getAiReactionTimeMinMs(true) : 480;
                    int stealMax = (context.options != null) ? context.options.getAiReactionTimeMaxMs(true) : 680;
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
        int minDelay = (context.options != null) ? context.options.getAiReactionTimeMinMs(isGoalie) : (isGoalie ? 480 : 1200);
        int maxDelay = (context.options != null) ? context.options.getAiReactionTimeMaxMs(isGoalie) : (isGoalie ? 680 : 1700);

        // 2. Movement target re-evaluation strictly governed by difficulty reaction delay timer.
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
            double arrivalDist = context.isGoalie(ai) ? 0.0 : 30.0;
            if (ai.fuel <= 0 || distToTarget <= arrivalDist) {
                if (ai.aiTargetAction != 10) { // 10 = TRANSITION_BOOST
                    ai.isBoosting = false;
                }
            } else if (context.isGoalie(ai) && ai.possession == 0 && ai.fuel > 0) {
                ai.isBoosting = true;
            }

            if (context.isGoalie(ai) && ai.possession == 1) {
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
            if (ai.aiStuckHorizontalTicks >= 3 && ai.possession == 1 && !context.isGoalie(ai)) {
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

    public void evaluateAiDecision(Titan ai) {
        if (ai.getType() == TitanType.GOALIE) {
            evaluateGoalieDecision(ai);
            updateAiBoostDecision(ai);
            return;
        }

        if (context.c != null && !context.c.AI_OMNISCIENCE_ENABLED && context.effectPool != null && context.effectPool.hasEffect(ai, EffectId.BLIND)) {
            ai.isBoosting = false;
            ai.aiTargetAction = 0;
            return;
        }

        // Abilities check: Evaluated on reaction delay timer
        tryUseAbilities(ai);
        if (ai.actionState != Titan.TitanState.IDLE) {
            return;
        }

        TeamAffiliation myTeam = ai.team;
        TeamAffiliation enemyTeam = (myTeam == TeamAffiliation.HOME) ? TeamAffiliation.AWAY : TeamAffiliation.HOME;

        Optional<Titan> possessorOpt = context.titanInPossession();
        boolean looseBall = !possessorOpt.isPresent();
        Titan possessor = possessorOpt.orElse(null);

        double ballCenterX = context.ball.X + context.ball.width / 2.0;
        double ballCenterY = context.ball.Y + context.ball.height / 2.0;

        // Rule 3: Neutral state / Loose ball or thrown ball
        if (looseBall) {
            boolean ballInMotion = context.anyBallMoveState();
            double targetBallX;
            double targetBallY;
            if (ballInMotion) {
                targetBallX = Math.max(context.c.MIN_X, Math.min(context.c.MAX_X, context.ball.X + context.xKickPow * 15));
                targetBallY = Math.max(context.c.MIN_Y, Math.min(context.c.MAX_Y, context.ball.Y + context.yKickPow * 15));
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

    public void tryUseAbilities(Titan ai) {
        if (ai.actionState != Titan.TitanState.IDLE || context.effectPool.isStunned(ai)) return;
        if (context.c != null && !context.c.AI_OMNISCIENCE_ENABLED && context.effectPool != null && context.effectPool.hasEffect(ai, EffectId.BLIND)) return;

        // Do not use abilities if in steal range of enemy ball carrier - steal has absolute priority
        Optional<Titan> possessorOpt = context.titanInPossession();
        if (possessorOpt.isPresent() && possessorOpt.get().team != ai.team && ai.possession == 0 && context.ballVisible) {
            if (context.isWithinStealRange(ai, possessorOpt.get())) {
                return;
            }
        }

        // Try Q
        if (!context.effectPool.hasEffect(ai, EffectId.COOLDOWN_Q)) {
            if (hasTargetForAbility(ai, true)) {
                try {
                    boolean castSuccess = context.ability.castQ(context, ai);
                    if (castSuccess) {
                        ai.actionState = Titan.TitanState.A1;
                        ai.actionFrame = 0;
                        ai.pushMove();
                        context.effectPool.addUniqueEffect(new EmptyEffect(ai.eCastFrames * context.GAMETICK_MS, ai, EffectId.CAST_LAG), context);
                        return;
                    }
                } catch (Exception ignored) {}
            }
        }

        // Try W
        if (ai.actionState == Titan.TitanState.IDLE && !context.effectPool.hasEffect(ai, EffectId.COOLDOWN_W)) {
            if (hasTargetForAbility(ai, false)) {
                try {
                    boolean castSuccess = context.ability.castW(context, ai);
                    if (castSuccess) {
                        ai.actionState = Titan.TitanState.A2;
                        ai.actionFrame = 0;
                        ai.pushMove();
                        context.effectPool.addUniqueEffect(new EmptyEffect(ai.rCastFrames * context.GAMETICK_MS, ai, EffectId.CAST_LAG), context);
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    public boolean hasTargetForAbility(Titan ai, boolean isQ) {
        TitanType type = ai.getType();
        if (type == null) return false;
        if (context.c != null && !context.c.AI_OMNISCIENCE_ENABLED && context.effectPool != null && context.effectPool.hasEffect(ai, EffectId.BLIND)) return false;

        TeamAffiliation enemyTeam = (ai.team == TeamAffiliation.HOME) ? TeamAffiliation.AWAY : TeamAffiliation.HOME;
        Titan nearestEnemy = findNearestEnemy(ai, enemyTeam);
        double enemyDist = (nearestEnemy != null) ? Math.hypot(nearestEnemy.X - ai.X, nearestEnemy.Y - ai.Y) : Double.MAX_VALUE;

        Const c = context.c;
        double rf = ai.rangeFactor;

        if (isQ) {
            switch (type) {
                case WARRIOR: return enemyDist <= (c.getI("titan.slash.range") / 2.0) * rf;
                case RANGER: return enemyDist <= c.getI("titan.arrow.range") * rf;
                case MAGE: return enemyDist <= c.getI("titan.portal.range") * rf;
                case BUILDER: return enemyDist <= c.getI("titan.trap.range") * rf;
                case MARKSMAN: return enemyDist <= c.getI("titan.slow.range") * rf;
                case ARTISAN: return ai.possession == 0 && Math.hypot(context.ball.X - ai.X, context.ball.Y - ai.Y) <= (c.getI("titan.suck.range") / 2.0) * rf;
                case SUPPORT: return enemyDist <= (c.getI("titan.stun.range") / 2.0) * rf;
                case GOLEM: return enemyDist <= 200.0 || ai.getHealth() < ai.maxHealth * 0.7;
                case STEALTH: return enemyDist <= 300.0;
                case DASHER: return ai.possession == 1;
                case HOUNDMASTER: return enemyDist <= c.getI("titan.cage.range") * rf;
                case GRENADIER: return enemyDist <= (c.getI("titan.flashbang.range") / 2.0) * rf;
                case CAPTAIN: return ai.ammo > 0 && enemyDist <= c.getI("titan.captain.shot.range") * rf;
                case SPIDER: return enemyDist <= c.getI("titan.spider.web.range") * rf;
                case GOALIE: return ai.possession == 0 && (context.activeLobThrower != null || Math.hypot(context.ball.X - ai.X, context.ball.Y - ai.Y) <= 300.0);
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
                    for (Titan ally : context.players) {
                        if (ally != null && ally.team == ai.team && !context.effectPool.hasEffect(ally, EffectId.DEAD)) {
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
                    for (Entity e : context.entityPool) {
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
                    GoalHoop centerHoop = (ai.team == TeamAffiliation.HOME) ? context.homeHiGoal : context.awayHiGoal;
                    double hoopRadius = (centerHoop != null) ? Math.max(centerHoop.w, centerHoop.h) / 2.0 : (context.c.getI("goal.hi.width") / 2.0);
                    double aiCenterX = ai.X + ai.width / 2.0;
                    double aiCenterY = ai.Y + ai.height / 2.0;
                    double dist = Math.hypot(ai.aiTargetX - aiCenterX, ai.aiTargetY - aiCenterY);
                    double maxSlideDist = context.c.getI("titan.goalie.slide.dist") * ai.rangeFactor;
                    return dist > hoopRadius && dist <= maxSlideDist;
                }
                default: return false;
            }
        }
    }

    public void tickAiGoalieMinionFarming(Titan ai) {
        if (context.effectPool.hasEffect(ai, EffectId.COOLDOWN_GOALIE)) {
            return;
        }
        if (context.c != null && !context.c.AI_OMNISCIENCE_ENABLED && context.effectPool != null && context.effectPool.hasEffect(ai, EffectId.BLIND)) {
            return;
        }

        // The goalie clicks to lasthit and push whenever his team has the ball
        Optional<Titan> possessorOpt = context.titanInPossession();
        if (!possessorOpt.isPresent() || possessorOpt.get().team != ai.team) {
            return;
        }

        double gx = ai.X + ai.width / 2.0;
        double gy = ai.Y + ai.height / 2.0;
        double rangeX = context.c.getI("titan.goalie.rangex") * ai.rangeFactor;
        double rangeY = context.c.getI("titan.goalie.rangey") * ai.rangeFactor;

        double baseDmg = (context.c != null && context.c.hasKey("goalie.click.damage")) ? context.c.getD("goalie.click.damage") : 5.0;
        GuardianAbilities ga = (ai.team == TeamAffiliation.HOME) ? context.homeGoalieAbilities : context.awayGoalieAbilities;
        Set<String> purchased = (ai.team == TeamAffiliation.HOME) ? context.homeGoaliePurchasedUpgrades : context.awayGoaliePurchasedUpgrades;

        Entity bestTarget = null;
        int bestTier = 999;
        double bestScore = Double.MAX_VALUE;

        for (Entity e : context.entityPool) {
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
                        tier = 1;
                        score = m.getHealth();
                    } else if (m.getHealth() <= effectiveDmg * 2.0) {
                        tier = 2;
                        score = m.getHealth();
                    } else {
                        tier = 3;
                        score = Math.abs(m.X - gx);
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
            context.handleGoalieAttackClick("", bestTarget.X, bestTarget.Y, ai.team, ai);
        }
    }

    public void evaluateGoalieDecision(Titan ai) {
        context.tickAiGoalieBuildOrder(ai);
        tryUseAbilities(ai);

        GoalHoop myGoal = (ai.team == TeamAffiliation.HOME) ? context.homeHiGoal : context.awayHiGoal;
        double hoopCX = myGoal.x + myGoal.w / 2.0;
        double hoopCY = myGoal.y + myGoal.h / 2.0;
        double rx = myGoal.w / 2.0;
        double ry = myGoal.h / 2.0;

        int YMAX = context.c.GOALIE_Y_MAX;
        int YMIN = context.c.GOALIE_Y_MIN;
        int XMAX = (ai.team == TeamAffiliation.AWAY ? context.c.GOALIE_XA_MAX : context.c.GOALIE_XH_MAX);
        int XMIN = (ai.team == TeamAffiliation.AWAY ? context.c.GOALIE_XA_MIN : context.c.GOALIE_XH_MIN);

        double minCenterX = XMIN + ai.width / 2.0;
        double maxCenterX = XMAX + ai.width / 2.0;
        double minCenterY = YMIN + ai.height / 2.0;
        double maxCenterY = YMAX + ai.height / 2.0;

        if (context.c != null && !context.c.AI_OMNISCIENCE_ENABLED && context.effectPool != null && context.effectPool.hasEffect(ai, EffectId.BLIND)) {
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

        double ballCX = context.ball.X + context.ball.width / 2.0;
        double ballCY = context.ball.Y + context.ball.height / 2.0;

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

    public void evaluateDefense(Titan ai, Titan possessor, TeamAffiliation myTeam, TeamAffiliation enemyTeam) {
        boolean carrierVisible = context.isTitanVisibleTo(ai, possessor);
        double aiCX = ai.X + ai.width / 2.0;
        double aiCY = ai.Y + ai.height / 2.0;

        double pCX, pCY;
        if (!carrierVisible) {
            if (context.ballVisible) {
                pCX = context.ball.X + context.ball.width / 2.0;
                pCY = context.ball.Y + context.ball.height / 2.0;
            } else {
                GoalHoop defendingGoal = (myTeam == TeamAffiliation.HOME) ? context.homeHiGoal : context.awayHiGoal;
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

        Titan primaryOnBallDefender = null;
        double minDistanceToBallCarrier = Double.MAX_VALUE;
        for (Titan t : context.players) {
            if (t != null && t.team == ai.team && t.getType() != TitanType.GOALIE && !context.effectPool.hasEffect(t, EffectId.DEAD)) {
                double d = Math.hypot(t.X - possessor.X, t.Y - possessor.Y);
                if (d < minDistanceToBallCarrier) {
                    minDistanceToBallCarrier = d;
                    primaryOnBallDefender = t;
                }
            }
        }

        if (ai == primaryOnBallDefender || primaryOnBallDefender == null) {
            ai.aiDefenseMode = chooseSteal ? 1 : 0;
            if (chooseSteal) {
                ai.aiTargetX = pCX;
                ai.aiTargetY = pCY;
                ai.aiTargetAction = 0;
            } else {
                GoalHoop defendingGoal = (myTeam == TeamAffiliation.HOME) ? context.homeHiGoal : context.awayHiGoal;
                double hoopCX = defendingGoal.x + defendingGoal.w / 2.0;
                double hoopCY = defendingGoal.y + defendingGoal.h / 2.0;

                double vx = hoopCX - pCX;
                double vy = hoopCY - pCY;
                double vLen = Math.hypot(vx, vy);
                if (vLen > 0.001) {
                    double ux = vx / vLen;
                    double uy = vy / vLen;
                    double blockDist = Math.min(dist * 0.5, 120.0);
                    ai.aiTargetX = Math.max(context.c.MIN_X, Math.min(context.c.MAX_X, pCX + ux * blockDist));
                    ai.aiTargetY = Math.max(context.c.MIN_Y, Math.min(context.c.MAX_Y, pCY + uy * blockDist));
                } else {
                    ai.aiTargetX = pCX;
                    ai.aiTargetY = pCY;
                }
                ai.aiTargetAction = 0;
            }
        } else {
            ai.aiDefenseMode = 0;

            List<Titan> otherTeammates = new ArrayList<>();
            for (Titan t : context.players) {
                if (t != null && t.team == ai.team && t != primaryOnBallDefender && t.getType() != TitanType.GOALIE && !context.effectPool.hasEffect(t, EffectId.DEAD)) {
                    otherTeammates.add(t);
                }
            }
            List<Titan> otherEnemies = new ArrayList<>();
            for (Titan t : context.players) {
                if (t != null && t.team == enemyTeam && t != possessor && t.getType() != TitanType.GOALIE && !context.effectPool.hasEffect(t, EffectId.DEAD)) {
                    if (context.c != null && !context.c.AI_OMNISCIENCE_ENABLED && !context.isTitanVisibleTo(ai, t)) continue;
                    otherEnemies.add(t);
                }
            }

            Titan targetEnemy = otherEnemies.isEmpty() ? null : otherEnemies.get(Math.max(0, otherTeammates.indexOf(ai)) % otherEnemies.size());
            if (targetEnemy != null) {
                double eCX = targetEnemy.X + targetEnemy.width / 2.0, eCY = targetEnemy.Y + targetEnemy.height / 2.0;
                double laneX = pCX - eCX, laneY = pCY - eCY, laneDist = Math.hypot(laneX, laneY);
                double frontDist = Math.min(80.0, laneDist * 0.45);
                ai.aiTargetX = Math.max(context.c.MIN_X, Math.min(context.c.MAX_X, laneDist > 0.001 ? eCX + (laneX / laneDist) * frontDist : eCX));
                ai.aiTargetY = Math.max(context.c.MIN_Y, Math.min(context.c.MAX_Y, laneDist > 0.001 ? eCY + (laneY / laneDist) * frontDist : eCY));
            } else {
                GoalHoop defendingGoal = (myTeam == TeamAffiliation.HOME) ? context.homeHiGoal : context.awayHiGoal;
                ai.aiTargetX = Math.max(context.c.MIN_X, Math.min(context.c.MAX_X, (pCX + defendingGoal.x + defendingGoal.w / 2.0) / 2.0));
                ai.aiTargetY = Math.max(context.c.MIN_Y, Math.min(context.c.MAX_Y, (pCY + defendingGoal.y + defendingGoal.h / 2.0) / 2.0));
            }
            ai.aiTargetAction = 0;
        }
    }

    public void evaluateOffBallOffense(Titan ai, Titan possessor, TeamAffiliation enemyTeam) {
        boolean isTransition = (ai.team == TeamAffiliation.HOME)
                ? possessor.X < context.FIELD_LENGTH / 2.0
                : possessor.X > context.FIELD_LENGTH / 2.0;

        if (isTransition) {
            if (ai.fuel > 25.0) {
                ai.isBoosting = true;
                ai.aiTargetAction = 10;
            } else {
                ai.isBoosting = false;
            }
        }

        double carrierCX = possessor.X + possessor.width / 2.0;
        double carrierCY = possessor.Y + possessor.height / 2.0;

        double forwardDir = (ai.team == TeamAffiliation.HOME) ? 1.0 : -1.0;
        double verticalOffset = (ai.Y < carrierCY) ? -220.0 : 220.0;

        ai.aiTargetX = Math.max(context.c.MIN_X, Math.min(context.c.MAX_X, carrierCX + forwardDir * 200.0));
        ai.aiTargetY = Math.max(context.c.MIN_Y, Math.min(context.c.MAX_Y, carrierCY + verticalOffset));
    }

    public double getTitanChaseSpeed(Titan t) {
        if (t == null) return 1.0;
        if (context.effectPool != null && (context.effectPool.isRooted(t) || context.effectPool.isStunned(t))) {
            return 0.001;
        }
        double base = t.speed;
        boolean hasDilators = (context.homeGoaliePurchasedUpgrades != null && context.homeGoaliePurchasedUpgrades.contains("fortress.t5.dilators")) ||
                              (context.awayGoaliePurchasedUpgrades != null && context.awayGoaliePurchasedUpgrades.contains("fortress.t5.dilators"));
        if (hasDilators && context.c != null) {
            base *= context.c.getD("guardian.dilators.speedmult");
        }
        if (context.effectPool != null) {
            for (Effect eff : context.effectPool.getEffects()) {
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
        int topCY = (int) (context.c.getI("goal.low.y") + context.c.getI("goal.low.height") / 2.0);
        int midCY = (int) (context.c.getI("goal.hi.y") + context.c.getI("goal.hi.height") / 2.0);
        int botCY = (int) (context.c.getI("goal.low2.y") + context.c.getI("goal.low.height") / 2.0);
        double d0 = Math.abs(t.Y - topCY);
        double d1 = Math.abs(t.Y - midCY);
        double d2 = Math.abs(t.Y - botCY);
        if (d1 < d0 && d1 < d2) L = 1;
        else if (d2 < d0 && d2 < d1) L = 2;
        return Math.max(0.1, context.getLaneMinionSpeed(L, t.team, base, 0.0));
    }

    public boolean isTeammateCloserToLooseBall(Titan ai, double targetBallX, double targetBallY) {
        double aiCX = ai.X + ai.width / 2.0;
        double aiCY = ai.Y + ai.height / 2.0;
        double aiDist = Math.hypot(targetBallX - aiCX, targetBallY - aiCY);
        double aiSpeed = getTitanChaseSpeed(ai);
        double aiTimeToBall = aiDist / aiSpeed;

        for (Titan t : context.players) {
            if (t == null || t.id.equals(ai.id) || t.team != ai.team) continue;
            if (context.effectPool != null && (context.effectPool.hasEffect(t, EffectId.DEAD)
                    || context.effectPool.isRooted(t) || context.effectPool.isStunned(t))) {
                continue;
            }

            // Goalies stay in their crease and only contest balls inside their box
            if (context.isGoalie(t)) {
                boolean inCrease = (targetBallY >= context.c.GOALIE_Y_MIN && targetBallY <= context.c.GOALIE_Y_MAX
                        && (t.team == TeamAffiliation.HOME
                                ? (targetBallX >= context.c.GOALIE_XH_MIN && targetBallX <= context.c.GOALIE_XH_MAX)
                                : (targetBallX >= context.c.GOALIE_XA_MIN && targetBallX <= context.c.GOALIE_XA_MAX)));
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

        double targetX = Math.max(context.c.MIN_X + 25, Math.min(context.c.MAX_X - 25, ballX + forwardOffset));
        double targetY = Math.max(context.c.MIN_Y + 25, Math.min(context.c.MAX_Y - 25, ballY + preferredVerticalOffset));

        // If preferred flank pass path is blocked by an enemy, try alternate flank
        if (isPassPathBlocked(ballX, ballY, targetX, targetY, enemyTeam)) {
            double altTargetY = Math.max(context.c.MIN_Y + 25, Math.min(context.c.MAX_Y - 25, ballY + alternateVerticalOffset));
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
        for (Titan t : context.players) {
            if (t != null && t.team == enemyTeam && !context.effectPool.hasEffect(t, EffectId.DEAD)) {
                if (context.c != null && !context.c.AI_OMNISCIENCE_ENABLED && !context.isTitanVisibleTo(ai, t)) continue;
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
        for (Titan t : context.players) {
            if (t != null && !t.id.equals(ai.id) && t.team == ai.team && t.getType() != TitanType.GOALIE && !context.effectPool.hasEffect(t, EffectId.DEAD)) {
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
                ? new GoalHoop[]{ context.homeHiGoal, context.lowGoals[0], context.lowGoals[1] }
                : new GoalHoop[]{ context.awayHiGoal, context.lowGoals[2], context.lowGoals[3] };
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
                ? new GoalHoop[]{ context.homeHiGoal, context.lowGoals[0], context.lowGoals[1] }
                : new GoalHoop[]{ context.awayHiGoal, context.lowGoals[2], context.lowGoals[3] };

        double simulatedBallCX = t.X + dx + 35.0;
        double simulatedBallCY = t.Y + dy + 35.0;
        double ballRadius = (context.ball != null) ? context.ball.width / 2.0 : 15.0;
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
        double distToTop = currentCY - context.c.MIN_Y, distToBottom = context.c.MAX_Y - currentCY;
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
            double[] vert = calculateEvadeTarget(ai, impedingEnemy, currentCX, currentCY, 0.0, 140.0);
            if (!isNearFriendlyGoal(ai.team, vert[0], vert[1], 65.0)) {
                return vert;
            }
            double[] fwd = calculateForwardEvadeTarget(ai, impedingEnemy, currentCX, currentCY);
            if (!isNearFriendlyGoal(ai.team, fwd[0], fwd[1], 65.0)) {
                return fwd;
            }
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
                Math.max(context.c.MIN_X, Math.min(context.c.MAX_X, cx + fwdDir * fwdDist)),
                Math.max(context.c.MIN_Y + 30.0, Math.min(context.c.MAX_Y - 30.0, cy + vertDir * vertDist))
        };
    }

    public void evaluateOnBallOffense(Titan ai, TeamAffiliation enemyTeam) {
        double currentCX = ai.X + ai.width / 2.0;
        double currentCY = ai.Y + ai.height / 2.0;

        boolean inAttackingThird = (ai.team == TeamAffiliation.HOME)
                ? currentCX > (context.FIELD_LENGTH * 2.0 / 3.0)
                : currentCX < (context.FIELD_LENGTH / 3.0);

        Titan impedingEnemy = getHorizontalImpedingEnemy(ai, enemyTeam);

        // Transition Offense: Pass-and-chase priorities in backcourt/midfield
        if (!inAttackingThird) {
            if (impedingEnemy != null) {
                if (ai.aiStuckHorizontalTicks < 2) {
                    double[] fwdTarget = calculateForwardEvadeTarget(ai, impedingEnemy, currentCX, currentCY);
                    ai.aiTargetX = fwdTarget[0];
                    ai.aiTargetY = fwdTarget[1];
                    ai.aiTargetAction = 0; // RUN
                    ai.isBoosting = false;
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
                ai.aiTargetAction = 0; // RUN
                ai.isBoosting = false;
                return;
            }

            Titan fasterTeammate = findFasterUpfieldTeammate(ai, enemyTeam);
            if (fasterTeammate != null) {
                ai.aiTargetX = fasterTeammate.X + fasterTeammate.width / 2.0;
                ai.aiTargetY = fasterTeammate.Y + fasterTeammate.height / 2.0;
                ai.aiTargetAction = 1; // PASS
                return;
            }

            double[] laneTarget = findBestOpenLanePassTarget(ai, enemyTeam);
            if (laneTarget != null) {
                ai.aiTargetX = laneTarget[0];
                ai.aiTargetY = laneTarget[1];
                ai.aiTargetAction = 1; // PASS ahead into lane
                return;
            }

            double forwardDir = (ai.team == TeamAffiliation.HOME) ? 1.0 : -1.0;
            if (ai.aiStuckHorizontalTicks >= 2) {
                int vertDir = (ai.aiEvadeVerticalDir != 0) ? ai.aiEvadeVerticalDir : ((currentCY < (context.c.MIN_Y + context.c.MAX_Y) / 2.0) ? 1 : -1);
                ai.aiEvadeVerticalDir = vertDir;
                double forwardX = currentCX + forwardDir * 120.0;
                ai.aiTargetX = Math.max(context.c.MIN_X, Math.min(context.c.MAX_X, forwardX));
                ai.aiTargetY = Math.max(context.c.MIN_Y + 30.0, Math.min(context.c.MAX_Y - 30.0, currentCY + vertDir * 160.0));
            } else {
                double forwardX = currentCX + forwardDir * 250.0;
                ai.aiTargetX = Math.max(context.c.MIN_X, Math.min(context.c.MAX_X, forwardX));
                ai.aiTargetY = currentCY;
            }
            ai.aiTargetAction = 0; // RUN
            ai.isBoosting = false;
            return;
        }

        // On ball offense in attacking third
        if (impedingEnemy != null) {
            GoalHoop openGoal = findUnblockedGoal(ai, enemyTeam);
            if (openGoal != null) {
                ai.aiTargetX = openGoal.x + openGoal.w / 2.0;
                ai.aiTargetY = openGoal.y + openGoal.h / 2.0;
                ai.aiTargetAction = 1;
                return;
            }

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
                double retreatX = currentCX + (currentCX - (nearestDefender.X + nearestDefender.width / 2.0));
                double defCY = nearestDefender.Y + nearestDefender.height / 2.0;
                double vertStep = (currentCY >= defCY) ? 90.0 : -90.0;
                double retreatY = currentCY + vertStep;
                if (ai.possession == 1 && isNearFriendlyGoal(ai.team, retreatX, retreatY, 65.0)) {
                    retreatX = currentCX;
                    if (isNearFriendlyGoal(ai.team, retreatX, retreatY, 65.0)) {
                        double altY = currentCY - vertStep;
                        if (!isNearFriendlyGoal(ai.team, retreatX, altY, 65.0)) {
                            retreatY = altY;
                        } else {
                            retreatY = currentCY;
                        }
                    }
                }
                ai.aiTargetX = Math.max(context.c.MIN_X, Math.min(context.c.MAX_X, retreatX));
                ai.aiTargetY = Math.max(context.c.MIN_Y, Math.min(context.c.MAX_Y, retreatY));
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
            double forwardDir = (ai.team == TeamAffiliation.HOME) ? 1.0 : -1.0;
            if (ai.aiStuckHorizontalTicks >= 2) {
                int vertDir = (ai.aiEvadeVerticalDir != 0) ? ai.aiEvadeVerticalDir : ((currentCY < (context.c.MIN_Y + context.c.MAX_Y) / 2.0) ? 1 : -1);
                ai.aiEvadeVerticalDir = vertDir;
                ai.aiTargetX = Math.max(context.c.MIN_X, Math.min(context.c.MAX_X, currentCX + forwardDir * 80.0));
                ai.aiTargetY = Math.max(context.c.MIN_Y + 30.0, Math.min(context.c.MAX_Y - 30.0, currentCY + vertDir * 140.0));
            } else {
                double stepX = currentCX + forwardDir * 150.0;
                ai.aiTargetX = Math.max(context.c.MIN_X, Math.min(context.c.MAX_X, stepX));
                ai.aiTargetY = currentCY;
            }
        } else {
            double probeY = (Math.random() < 0.5) ? Math.max(context.c.MIN_Y, currentCY - 160.0) : Math.min(context.c.MAX_Y, currentCY + 160.0);
            ai.aiTargetX = currentCX;
            ai.aiTargetY = probeY;
        }
        ai.aiTargetAction = 0;
    }

    public boolean isPassPathBlocked(double x1, double y1, double x2, double y2, TeamAffiliation enemyTeam) {
        for (Titan enemy : context.players) {
            if (enemy != null && enemy.team == enemyTeam && !context.effectPool.hasEffect(enemy, EffectId.DEAD)) {
                if (context.c != null && !context.c.AI_OMNISCIENCE_ENABLED && context.effectPool != null
                        && context.effectPool.hasEffect(enemy, EffectId.STEALTHED)
                        && !context.effectPool.hasEffect(enemy, EffectId.FLARE)) {
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

    public Titan findFasterUpfieldTeammate(Titan ai, TeamAffiliation enemyTeam) {
        Titan best = null;
        double bestDist = -1.0;
        double aiCX = ai.X + ai.width / 2.0;
        double aiCY = ai.Y + ai.height / 2.0;
        double aiSpeed = ai.actualSpeed(context);

        for (Titan t : context.players) {
            if (t != null && !t.id.equals(ai.id) && t.team == ai.team && !context.effectPool.hasEffect(t, EffectId.DEAD)) {
                double tCX = t.X + t.width / 2.0;
                double tCY = t.Y + t.height / 2.0;
                double forwardX = (ai.team == TeamAffiliation.HOME) ? (tCX - aiCX) : (aiCX - tCX);

                if (forwardX > 40.0 && (t.actualSpeed(context) > aiSpeed || t.speed > ai.speed)) {
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

    public boolean isDestinationCloserToFriendlyThanEnemy(Titan ai, double targetX, double targetY) {
        double minFriendlyDist = Double.MAX_VALUE;
        double minEnemyDist = Double.MAX_VALUE;

        for (Titan t : context.players) {
            if (t != null && !context.effectPool.hasEffect(t, EffectId.DEAD)) {
                if (t.team != ai.team && context.c != null && !context.c.AI_OMNISCIENCE_ENABLED && !context.isTitanVisibleTo(ai, t)) {
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

    public double[] findBestOpenLanePassTarget(Titan ai, TeamAffiliation enemyTeam) {
        double aiCX = ai.X + ai.width / 2.0;
        double aiCY = ai.Y + ai.height / 2.0;
        double forwardDir = (ai.team == TeamAffiliation.HOME) ? 1.0 : -1.0;
        double targetX = Math.max(context.c.MIN_X + 50.0, Math.min(context.c.MAX_X - 50.0, aiCX + forwardDir * 260.0));

        double[] laneYs = context.laneCenterYs();
        int bestLane = -1;
        int maxAdvantage = -999;
        for (int L = 0; L < 3; L++) {
            double laneY = laneYs[L];
            if (!isPassPathBlocked(aiCX, aiCY, targetX, laneY, enemyTeam)) {
                if (isDestinationCloserToFriendlyThanEnemy(ai, targetX, laneY)) {
                    int advantage = context.getLaneAdvantage(L, ai.team);
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

    public Titan findBestPassTarget(Titan ai) {
        Titan best = null;
        double bestDist = -1.0;
        double aiCX = ai.X + ai.width / 2.0;
        double aiCY = ai.Y + ai.height / 2.0;
        TeamAffiliation enemyTeam = (ai.team == TeamAffiliation.HOME) ? TeamAffiliation.AWAY : TeamAffiliation.HOME;
        for (Titan t : context.players) {
            if (t != null && !t.id.equals(ai.id) && t.team == ai.team && t.getType() != TitanType.GOALIE && !context.effectPool.hasEffect(t, EffectId.DEAD)) {
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

    public void executeAiShot(Titan ai, double targetX, double targetY) {
        if (ai.possession != 1 || ai.actionState != Titan.TitanState.IDLE) return;
        context.serverMouseRoutine(ai, (int) targetX, (int) targetY, 1, 0, 0);
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

        for (Titan enemy : context.players) {
            if (enemy != null && enemy.team == enemyTeam && !context.effectPool.hasEffect(enemy, EffectId.DEAD)) {
                if (context.c != null && !context.c.AI_OMNISCIENCE_ENABLED && context.effectPool != null
                        && context.effectPool.hasEffect(enemy, EffectId.STEALTHED)
                        && !context.effectPool.hasEffect(enemy, EffectId.FLARE)) {
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
        if (caller == null || context.effectPool.hasEffect(caller, EffectId.DEAD)) return false;
        Optional<Titan> possessorOpt = context.titanInPossession();
        if (!possessorOpt.isPresent()) return false;
        Titan possessor = possessorOpt.get();
        if (possessor.id.equals(caller.id)) return false;
        if (possessor.team != caller.team) return false;

        int possessorIndex = -1;
        for (int i = 0; i < context.players.length; i++) {
            if (context.players[i] != null && context.players[i].id.equals(possessor.id)) {
                possessorIndex = i;
                break;
            }
        }
        if (possessorIndex == -1) return false;
        if (context.anyClientSelected(possessorIndex + 1)) return false;

        double callerCX = caller.X + caller.width / 2.0;
        double callerCY = caller.Y + caller.height / 2.0;
        boolean lob = isEnemyBetween(possessor, caller);

        caller.queuedBtn = 0;
        executeAiPassTo(possessor, callerCX, callerCY, lob);
        return true;
    }

    public void executeAiPassTo(Titan ai, double targetX, double targetY, boolean lob) {
        if (ai.possession != 1 || ai.actionState != Titan.TitanState.IDLE) return;
        ai.queuedBtn = lob ? 3 : 1;
        context.serverMouseRoutine(ai, (int) targetX, (int) targetY, ai.queuedBtn, 0, 0);
        ai.queuedBtn = 0;
        ai.aiLastDecisionTimeMs = System.currentTimeMillis();
        boolean isGoalie = (ai.getType() == TitanType.GOALIE);
        int minDelay = (context.options != null) ? context.options.getAiReactionTimeMinMs(isGoalie) : (isGoalie ? 480 : 1200);
        int maxDelay = (context.options != null) ? context.options.getAiReactionTimeMaxMs(isGoalie) : (isGoalie ? 680 : 1700);
        ai.aiReactionDelayMs = minDelay + (long)(Math.random() * (maxDelay - minDelay + 1));
    }

    public void executeAiSteal(Titan ai) {
        if (ai.possession == 1) return;
        if (!context.effectPool.isStunned(ai) && !context.effectPool.hasEffect(ai, EffectId.COOLDOWN_STEAL)) {
            try {
                boolean stolen = context.ability.castSteal(context, ai);
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

    public GoalHoop findNearestFriendlySideHoop(TeamAffiliation team, double x, double y) {
        GoalHoop[] low = (team == TeamAffiliation.HOME) ? new GoalHoop[]{context.lowGoals[0], context.lowGoals[1]} : new GoalHoop[]{context.lowGoals[2], context.lowGoals[3]};
        double d0 = Math.hypot(low[0].x + low[0].w / 2.0 - x, low[0].y + low[0].h / 2.0 - y);
        double d1 = Math.hypot(low[1].x + low[1].w / 2.0 - x, low[1].y + low[1].h / 2.0 - y);
        return d0 <= d1 ? low[0] : low[1];
    }

    public Titan findAssignedDefensiveTarget(Titan ai, Titan possessor) {
        List<Titan> enemies = new ArrayList<>();
        for (Titan t : context.players) {
            if (t != null && t.team != ai.team && !context.effectPool.hasEffect(t, EffectId.DEAD) && t.getType() != TitanType.GOALIE) {
                if (context.c != null && !context.c.AI_OMNISCIENCE_ENABLED && !context.isTitanVisibleTo(ai, t)) continue;
                enemies.add(t);
            }
        }
        if (enemies.isEmpty()) return possessor;
        int aiIndex = 0;
        for (Titan t : context.players) {
            if (t.id.equals(ai.id)) break;
            if (t.team == ai.team && t.getType() != TitanType.GOALIE) aiIndex++;
        }
        return enemies.get(aiIndex % enemies.size());
    }

    public Titan findNearestEnemy(Titan ai, TeamAffiliation enemyTeam) {
        if (ai != null && context.c != null && !context.c.AI_OMNISCIENCE_ENABLED && context.effectPool != null && context.effectPool.hasEffect(ai, EffectId.BLIND)) {
            return null;
        }
        Titan nearest = null;
        double minD = Double.MAX_VALUE;
        for (Titan t : context.players) {
            if (t != null && t.team == enemyTeam && !context.effectPool.hasEffect(t, EffectId.DEAD)) {
                if (ai != null && context.c != null && !context.c.AI_OMNISCIENCE_ENABLED && !context.isTitanVisibleTo(ai, t)) {
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

    public GoalHoop findUnblockedGoal(Titan ai, TeamAffiliation enemyTeam) {
        GoalHoop[] enemyGoals = (enemyTeam == TeamAffiliation.AWAY)
                ? new GoalHoop[]{context.awayHiGoal, context.lowGoals[2], context.lowGoals[3]}
                : new GoalHoop[]{context.homeHiGoal, context.lowGoals[0], context.lowGoals[1]};
        for (GoalHoop g : enemyGoals) {
            double gx = g.x + g.w / 2.0;
            double gy = g.y + g.h / 2.0;
            boolean blocked = false;
            for (Titan enemy : context.players) {
                if (enemy != null && enemy.team == enemyTeam && !context.effectPool.hasEffect(enemy, EffectId.DEAD)) {
                    if (ai != null && context.c != null && !context.c.AI_OMNISCIENCE_ENABLED && !context.isTitanVisibleTo(ai, enemy)) {
                        continue;
                    }
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

    public static double distToSegment(double px, double py, double x1, double y1, double x2, double y2) {
        double l2 = (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1);
        if (l2 == 0) return Math.hypot(px - x1, py - y1);
        double t = Math.max(0, Math.min(1, ((px - x1) * (x2 - x1) + (py - y1) * (y2 - y1)) / l2));
        double projX = x1 + t * (x2 - x1);
        double projY = y1 + t * (y2 - y1);
        return Math.hypot(px - projX, py - projY);
    }

    public void goalieTactics(Titan goalie, TeamAffiliation team) {
        if (context.c.GOALIE_DISABLED) {
            goalie.X = 999000;
            goalie.Y = 999000;
            return;
        }
        if (context.effectPool.isRooted(goalie)) {
            return;
        }
        Set<String> purchased = (team == TeamAffiliation.HOME) ? context.homeGoaliePurchasedUpgrades : context.awayGoaliePurchasedUpgrades;
        boolean pullGoalie = purchased != null && purchased.contains("siege.t3.pullgoalie");
        int YMAX = pullGoalie ? context.c.E_MAX_Y : context.c.GOALIE_Y_MAX;
        int YMIN = pullGoalie ? context.c.E_MIN_Y : context.c.GOALIE_Y_MIN;
        int XMAX = pullGoalie ? context.c.E_MAX_X : (team == TeamAffiliation.AWAY ? context.c.GOALIE_XA_MAX : context.c.GOALIE_XH_MAX);
        int XMIN = pullGoalie ? context.c.E_MIN_X : (team == TeamAffiliation.AWAY ? context.c.GOALIE_XA_MIN : context.c.GOALIE_XH_MIN);
        for (GoalHoop goal : context.lowGoals) {
            if (goalie.possession == 1 &&
                    context.ballIntersectsEllipse(goal) && goal.team.equals(TeamAffiliation.HOME)) {
                if (!goalie.collidesSolid(context, context.allSolids, 0, (int) +goalie.speed)) {
                    goalie.setX((int) (goalie.getX() + goalie.speed));
                    if (goalie.getX() > XMAX) goalie.setX(XMAX);
                }
            }
            if (goalie.possession == 1 &&
                    context.ballIntersectsEllipse(goal) && goal.team.equals(TeamAffiliation.AWAY)) {
                if (!goalie.collidesSolid(context, context.allSolids, 0, (int) -goalie.speed)) {
                    goalie.setX((int) (goalie.getX() - goalie.speed));
                    if (goalie.getX() < XMIN) goalie.setX(XMIN);
                }
            }
        }
        if (goalie.possession == 1) {
            return;
        }
        if (goalie.getY() + 35 < (context.ball.Y + context.ball.centerDist)) {
            if (!goalie.collidesSolid(context, context.allSolids, (int) goalie.speed, 0)) {
                goalie.setY((int) (goalie.getY() + goalie.speed));
                if (goalie.getY() > YMAX) goalie.setY(YMAX);
            }
        }
        if (goalie.getY() + 35 > (context.ball.Y + context.ball.centerDist)) {
            if (!goalie.collidesSolid(context, context.allSolids, (int) -goalie.speed, 0)) {
                goalie.setY((int) (goalie.getY() - goalie.speed));
                if (goalie.getY() < YMIN) goalie.setY(YMIN);
            }
        }
        if (goalie.getX() + 35 > context.ball.X + context.ball.centerDist) {
            if (!goalie.collidesSolid(context, context.allSolids, 0, (int) -goalie.speed)) {
                goalie.setX((int) (goalie.getX() - goalie.speed));
                if (goalie.getX() < XMIN) goalie.setX(XMIN);
            }
        }
        if (goalie.getX() + 35 < context.ball.X + context.ball.centerDist) {
            if (!goalie.collidesSolid(context, context.allSolids, 0, (int) goalie.speed)) {
                goalie.setX((int) (goalie.getX() + goalie.speed));
                if (goalie.getX() > XMAX) goalie.setX(XMAX);
            }
        }
    }

    public void runRightAI(Titan t) {
        if (t.inactiveDir == 1 && !context.effectPool.isRooted(t) && !context.effectPool.hasEffect(t, EffectId.DEAD)) {
            t.diagonalRunDir = 1;
            if (!t.collidesSolid(context, context.allSolids, 0, (int) t.speed)) {
                if (t.X > context.c.MAX_X) t.X = context.c.MAX_X;
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
        if (t.inactiveDir == 2 && !context.effectPool.isRooted(t) && !context.effectPool.hasEffect(t, EffectId.DEAD)) {
            if (!t.collidesSolid(context, context.allSolids, 0, (int) -t.speed)) {
                t.diagonalRunDir = 2;
                if (t.X < context.c.MIN_X) t.X = context.c.MIN_X;
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
        if (!context.effectPool.isRooted(t) && !context.effectPool.hasEffect(t, EffectId.DEAD)) {
            if (!t.collidesSolid(context, context.allSolids, (int) -t.speed, 0)) {
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
        if (!context.effectPool.isRooted(t)) {
            if (!t.collidesSolid(context, context.allSolids, (int) t.speed, 0)) {
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
