package gameserver.engine;

import gameserver.effects.EffectId;
import gameserver.effects.cooldowns.CooldownCurve;
import gameserver.effects.effects.EmptyEffect;
import gameserver.entity.Titan;
import gameserver.entity.TitanType;
import gameserver.gamemanager.GamePhase;
import networking.ClientPacket;
import networking.KeyDifferences;
import networking.PlayerDivider;
import util.Util;

/**
 * Handles decoding client packets, key inputs, boost mechanics,
 * player movement controls, and server mouse routines.
 */
public class PlayerInputEngine {

    protected final GameEngine context;

    public PlayerInputEngine(GameEngine context) {
        this.context = context;
    }

    public void processClientPacket(PlayerDivider from, ClientPacket request) {
        if (context.phase != GamePhase.INGAME) {
            return;
        }
        boolean known = false;
        for (PlayerDivider p : context.clients) {
            if (p.id == from.id) {
                known = true;
                break;
            }
        }
        if (!known) {
            System.out.println("Packet from unknown client id=" + from.id);
            return;
        }
        context.lock();
        try {
            if (from != null) {
                Titan t = context.titanFromPacket(from);
                if (t == null) {
                    System.out.println("got passed a bad titan index! Possibly from another game?");
                    return;
                }
                processKeys(request, from);
                processProgramming(t, request);
                int btn = getBtn(request, t);
                if (t.possession == 1 && request.posX != -1 && request.posY != -1 && btn != 0) {
                    serverMouseRoutine(t, request.posX, request.posY, btn, request.camX, request.camY);
                }
                for (PlayerDivider client : context.clients) {
                    if (client.id == from.id) {
                        from.ready = true;
                        int classSelIndex = client.possibleSelection.get(0) - 1;
                        Titan classTitan = context.players[classSelIndex];
                        if (request.classSelection != null) {
                            if (request.classSelection == TitanType.GOALIE) {
                                if (classSelIndex == 0 || classSelIndex == 1) {
                                    classTitan.setType(TitanType.GOALIE);
                                }
                            } else {
                                if (classSelIndex != 0 && classSelIndex != 1) {
                                    classTitan.setType(request.classSelection);
                                }
                            }
                        }
                        if (request.masteries != null) {
                            request.masteries.applyMasteries(classTitan);
                        }
                    }
                }
                context.kickoff();
            }
        } finally {
            context.unlock();
        }
    }

    public static int getBtn(ClientPacket request, Titan t) {
        int btn = 0;
        if (request.lobBtn) {
            btn = 3;
        } else if (request.shotBtn) {
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

    public void boost(KeyDifferences controlsHeld, Titan t) {
        if (controlsHeld.BOOST == 1 && (context.phase == GamePhase.INGAME || context.phase == GamePhase.TUTORIAL)) {
            t.isBoosting = true;
        }
        if (controlsHeld.BOOST == -1 && (context.phase == GamePhase.INGAME || context.phase == GamePhase.TUTORIAL)) {
            t.isBoosting = false;
        }
        if (controlsHeld.BOOST_LOCK == 1 && (context.phase == GamePhase.INGAME || context.phase == GamePhase.TUTORIAL)) {
            t.isBoosting = !t.isBoosting;
        }
        if (t.fuel < 1.0) {
            t.isBoosting = false;
        }
    }

    public void processKeys(ClientPacket controls, PlayerDivider from) {
        if (from != null) {
            Titan t = context.players[from.selection - 1];
            int clientIndex = context.clientIndex(from);
            if (context.lastControlPacket == null || context.lastControlPacket.length == 0) {
                context.lastControlPacket = new ClientPacket[1];
                context.lastControlPacket[0] = new ClientPacket();
            }
            KeyDifferences controlsHeld = new KeyDifferences(controls, context.lastControlPacket[clientIndex]);
            boost(controlsHeld, t);
            if (t.getType() == TitanType.GOALIE && context.phase == GamePhase.INGAME) {
                if (controls.goalieClickX != null && controls.goalieClickY != null) {
                    double clickX = controls.goalieClickX + controls.camX;
                    double clickY = controls.goalieClickY + controls.camY;
                    context.handleGoalieAttackClick(from.getEmail(), clickX, clickY, t.team, t);
                } else if (controls.shotBtn && t.possession == 0) {
                    double clickX = controls.posX + controls.camX;
                    double clickY = controls.posY + controls.camY;
                    context.handleGoalieAttackClick(from.getEmail(), clickX, clickY, t.team, t);
                }
            }
            if (t.getType() == TitanType.GOALIE && controls.buyGoalieNode != null && context.phase == GamePhase.INGAME) {
                System.out.println("Made purchase attempt");
                context.handleGoalieTreePurchase(t, controls.buyGoalieTree, controls.buyGoalieNode);
            }
            if (controlsHeld.SWITCH == 1 && context.phase == GamePhase.INGAME && t.actionState == Titan.TitanState.IDLE) {
                from.incSel(context);
                t.runLeft = 0;
                t.runRight = 0;
                t.runDown = 0;
                t.runUp = 0;
                t.runningFrame = 0;
                t.diagonalRunDir = 0;
            }
            if ((controlsHeld.STEAL == 1 && context.phase == GamePhase.INGAME && t.actionState == Titan.TitanState.IDLE)) {
                if (!context.effectPool.isStunned(t) && !context.effectPool.hasEffect(t, EffectId.COOLDOWN_STEAL)) {
                    try {
                        boolean stolen = context.ability.castSteal(context, t);
                        if (t.actionState == Titan.TitanState.IDLE && !stolen) {
                            t.actionState = Titan.TitanState.STEAL;
                            t.actionFrame = 0;
                            t.pushMove();
                        }
                    } catch (Exception ignored) {}
                }
            }
            if (controlsHeld.CAM == 1 && context.phase == GamePhase.DRAW_CLASS_SCREEN) {
                context.phase = GamePhase.SET_MASTERIES;
            }
            if ((controlsHeld.E == 1 || controlsHeld.callForBall == 1) && context.phase == GamePhase.INGAME) {
                boolean handledCallForBall = false;
                if (t.possession == 0 && !context.effectPool.hasEffect(t, EffectId.DEAD)) {
                    handledCallForBall = context.handleCallForBall(t);
                }
                if (!handledCallForBall && controlsHeld.E == 1) {
                    System.out.println("[CAST_DEBUG] E key detected for titan " + t.getType() + ", actionState=" + t.actionState + ", isStunned=" + context.effectPool.isStunned(t));
                    if (t.actionState == Titan.TitanState.IDLE) {
                        if (!context.effectPool.isStunned(t)) {
                            try {
                                boolean caststun = context.ability.castQ(context, t);
                                System.out.println("[CAST_DEBUG] castQ returned: " + caststun + " for titan " + t.getType());
                                if (caststun) {
                                    t.actionState = Titan.TitanState.A1;
                                    t.actionFrame = 0;
                                    t.pushMove();
                                    context.effectPool.addUniqueEffect(
                                            new EmptyEffect(t.eCastFrames * context.GAMETICK_MS, t, EffectId.CAST_LAG), context);
                                    System.out.println("[CAST_DEBUG] Set state to A1 for titan " + t.getType() + ", eCastFrames=" + t.eCastFrames + ", CAST_LAG added");
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
            if (controlsHeld.R == 1 && context.phase == GamePhase.INGAME) {
                System.out.println("[CAST_DEBUG] R key detected for titan " + t.getType() + ", actionState=" + t.actionState + ", isStunned=" + context.effectPool.isStunned(t));
                if (t.actionState == Titan.TitanState.IDLE) {
                    if (!context.effectPool.isStunned(t)) {
                        try {
                            boolean caststun = context.ability.castW(context, t);
                            System.out.println("[CAST_DEBUG] castW returned: " + caststun + " for titan " + t.getType());
                            if (caststun) {
                                t.actionState = Titan.TitanState.A2;
                                t.actionFrame = 0;
                                t.pushMove();
                                context.effectPool.addUniqueEffect(
                                        new EmptyEffect(t.rCastFrames * context.GAMETICK_MS, t, EffectId.CAST_LAG), context);
                                System.out.println("[CAST_DEBUG] Set state to A2 for titan " + t.getType() + ", rCastFrames=" + t.rCastFrames + ", CAST_LAG added");
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            moveKeys(controlsHeld, t);
            context.lastControlPacket[clientIndex] = controls;
        }
    }

    public boolean isActionMovementUnlocked(Titan t) {
        if (t.actionState == Titan.TitanState.IDLE) {
            return true;
        }
        if (t.actionState == Titan.TitanState.SHOOT) {
            return t.actionFrame >= context.c.SHOT_CASTLAG_FRAMES;
        }
        if (t.actionState == Titan.TitanState.LOB) {
            return t.actionFrame >= context.c.LOB_CASTLAG_FRAMES;
        }
        return false;
    }

    public void moveKeys(KeyDifferences controlsHeld, Titan t) {
        if (!context.effectPool.hasEffect(t, EffectId.DEAD)) {
            if (controlsHeld.RIGHT == 1) t.keyHeldR = true;
            if (controlsHeld.RIGHT == -1) t.keyHeldR = false;
            if (controlsHeld.LEFT == 1) t.keyHeldL = true;
            if (controlsHeld.LEFT == -1) t.keyHeldL = false;
            if (controlsHeld.UP == 1) t.keyHeldU = true;
            if (controlsHeld.UP == -1) t.keyHeldU = false;
            if (controlsHeld.DOWN == 1) t.keyHeldD = true;
            if (controlsHeld.DOWN == -1) t.keyHeldD = false;

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
                boolean canMoveNow = !context.effectPool.isRooted(t) && isActionMovementUnlocked(t);
                if (controlsHeld.RIGHT == 1 && context.phase == GamePhase.INGAME) {
                    if (canMoveNow) {
                        t.runLeft = 0;
                        t.runRight = 1;
                    } else {
                        t.moveMemR = true;
                        t.moveMemL = false;
                    }
                }
                if (controlsHeld.LEFT == 1 && context.phase == GamePhase.INGAME) {
                    if (canMoveNow) {
                        t.runLeft = 1;
                        t.runRight = 0;
                    } else {
                        t.moveMemR = false;
                        t.moveMemL = true;
                    }
                }
                if (controlsHeld.UP == 1 && context.phase == GamePhase.INGAME) {
                    if (canMoveNow) {
                        t.runUp = 1;
                        t.runDown = 0;
                    } else {
                        t.moveMemU = true;
                        t.moveMemD = false;
                    }
                }
                if (controlsHeld.DOWN == 1 && context.phase == GamePhase.INGAME) {
                    if (canMoveNow) {
                        t.runUp = 0;
                        t.runDown = 1;
                    } else {
                        t.moveMemU = false;
                        t.moveMemD = true;
                    }
                }

                if (controlsHeld.RIGHT == -1 && context.phase == GamePhase.INGAME) {
                    t.runRight = 0;
                    t.runningFrame = 0;
                    t.diagonalRunDir = 0;
                    t.moveMemR = false;
                }
                if (controlsHeld.LEFT == -1 && context.phase == GamePhase.INGAME) {
                    t.runLeft = 0;
                    t.runningFrame = 0;
                    t.diagonalRunDir = 0;
                    t.moveMemL = false;
                }
                if (controlsHeld.UP == -1 && context.phase == GamePhase.INGAME) {
                    t.runUp = 0;
                    t.runningFrame = 0;
                    t.dirToBall = 0;
                    t.moveMemU = false;
                }
                if (controlsHeld.DOWN == -1 && context.phase == GamePhase.INGAME) {
                    t.runDown = 0;
                    t.runningFrame = 0;
                    t.dirToBall = 0;
                    t.moveMemD = false;
                }
            }
        }
    }

    public void processProgramming(Titan t, ClientPacket request) {
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

    public void updateSelectedDirection() {
        for (Titan t : context.players) {
            if (t.runRight == 1 && t.runUp == 1) {
                t.facing = 45;
            }
            if (t.runUp == 1 && t.runRight == 0 && t.runLeft == 0) {
                if (t.facing > 90 && t.facing < 270) {
                    t.facing = 91;
                } else {
                    t.facing = 89;
                }
            }
            if (t.runUp == 1 && t.runLeft == 1) {
                t.facing = 135;
            }
            if (t.runLeft == 1 && t.runUp == 0 && t.runDown == 0) {
                t.facing = 180;
            }
            if (t.runLeft == 1 && t.runDown == 1) {
                t.facing = 225;
            }
            if (t.runDown == 1 && t.runRight == 0 && t.runLeft == 0) {
                if (t.facing > 90 && t.facing < 270) {
                    t.facing = 269;
                } else {
                    t.facing = 271;
                }
            }
            if (t.runDown == 1 && t.runRight == 1) {
                t.facing = 315;
            }
            if (t.runRight == 1 && t.runUp == 0 && t.runDown == 0) {
                t.facing = 0;
            }
        }
    }

    public void serverMouseRoutine(Titan t, int clickX, int clickY, int btn, int camX, int camY) {
        int priorPossession = t.possession;
        context.intersectAll();
        if (t.possession == 1 && priorPossession == 1) {
            if (t.actionState == Titan.TitanState.IDLE && !context.effectPool.isStunned(t)) {
                t.queuedBtn = 0;
                if (context.phase == GamePhase.INGAME && btn == 1) {
                    t.actionState = Titan.TitanState.SHOOT;
                } else if (context.phase == GamePhase.INGAME && btn == 3) {
                    t.actionState = Titan.TitanState.LOB;
                } else if (context.phase == GamePhase.INGAME && btn == 4) {
                    if (!context.effectPool.hasEffect(t, EffectId.COOLDOWN_CURVE)) {
                        t.actionState = Titan.TitanState.CURVE_LEFT;
                        context.effectPool.addUniqueEffect(new CooldownCurve((int) (t.cooldownFactor * 5000), t), context);
                    }
                } else if (context.phase == GamePhase.INGAME && btn == 5) {
                    if (!context.effectPool.hasEffect(t, EffectId.COOLDOWN_CURVE)) {
                        t.actionState = Titan.TitanState.CURVE_RIGHT;
                        context.effectPool.addUniqueEffect(new CooldownCurve((int) (t.cooldownFactor * 5000), t), context);
                    }
                }
                int xClick = (int) ((clickX - context.ball.X) + camX - context.ball.centerDist);
                int yClick = (int) (-1 * ((clickY - context.ball.Y) + camY - context.ball.centerDist));
                double angle = Util.degreesFromCoords(xClick, yClick);
                context.xKickPow = Math.cos(Math.toRadians(angle)) / 4.0;
                context.yKickPow = Math.sin(Math.toRadians(angle)) / 4.0;
                context.currentShotType = context.predictShotTypeFromRay(context.ball.X + context.ball.width / 2.0, context.ball.Y + context.ball.height / 2.0, context.xKickPow, -context.yKickPow, t.team);
            } else {
                t.queuedBtn = btn;
                t.queuedClickX = clickX;
                t.queuedClickY = clickY;
                t.queuedCamX = camX;
                t.queuedCamY = camY;
            }
        }
    }

    public void runUpCtrl(Titan t) {
        if (context.phase == GamePhase.INGAME || context.phase == GamePhase.TUTORIAL) {
            boolean canRun = isActionMovementUnlocked(t);
            if (!canRun) return;
            if (!context.effectPool.isRooted(t)) {
                if (!t.collidesSolid(context, context.allSolids, (int) -t.speed, 0)) {
                    if (t.X <= context.ball.X) t.dirToBall = 1;
                    if (t.X > context.ball.X) t.dirToBall = 2;
                    if (t.diagonalRunDir == 1) t.dirToBall = 1;
                    if (t.diagonalRunDir == 2) t.dirToBall = 2;
                    if (!t.programmed) {
                        t.translateBounded(context, 0.0, -t.actualSpeed(context, 0.0));
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
        if (context.phase == GamePhase.INGAME || context.phase == GamePhase.TUTORIAL) {
            boolean canRun = isActionMovementUnlocked(t);
            if (!canRun) return;
            if (!context.effectPool.isRooted(t)) {
                if (!t.collidesSolid(context, context.allSolids, (int) t.speed, 0)) {
                    if (t.X <= context.ball.X) t.dirToBall = 1;
                    if (t.X > context.ball.X) t.dirToBall = 2;
                    if (t.diagonalRunDir == 1) t.dirToBall = 1;
                    if (t.diagonalRunDir == 2) t.dirToBall = 2;
                    if (!t.programmed) {
                        t.translateBounded(context, 0.0, t.actualSpeed(context, 0.0));
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
        if (context.phase == GamePhase.INGAME || context.phase == GamePhase.TUTORIAL) {
            boolean canRun = isActionMovementUnlocked(t);
            if (!canRun) return;
            if (!context.effectPool.isRooted(t)) {
                if (!t.collidesSolid(context, context.allSolids, 0, (int) t.speed)) {
                    if (!t.programmed) {
                        t.translateBounded(context, t.actualSpeed(context, 1.0), 0.0);
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
        if (context.phase == GamePhase.INGAME || context.phase == GamePhase.TUTORIAL) {
            boolean canRun = isActionMovementUnlocked(t);
            if (!canRun) return;
            if (!context.effectPool.isRooted(t)) {
                t.diagonalRunDir = 2;
                if (!t.collidesSolid(context, context.allSolids, 0, (int) -t.speed)) {
                    if (!t.programmed) {
                        t.translateBounded(context, -t.actualSpeed(context, -1.0), 0.0);
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

    public void programmedCtrl(Titan t) {
        TitanPathfinder.executeProgrammedMovement(context, t);
    }
}
