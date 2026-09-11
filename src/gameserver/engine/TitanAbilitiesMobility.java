package gameserver.engine;

import gameserver.Const;
import gameserver.effects.EffectId;
import gameserver.effects.cooldowns.CooldownW;
import gameserver.effects.effects.CallbackEffect;
import gameserver.effects.effects.EmptyEffect;
import gameserver.entity.Entity;
import gameserver.entity.Titan;
import gameserver.targeting.SelectorOffset;
import gameserver.targeting.Targeting;
import gameserver.targeting.core.Selector;
import util.Util;

/**
 * Executes mobility, dash, displacement, and teleport abilities.
 */
public class TitanAbilitiesMobility {

    public static void goalieSlide(AbilityStrategy strat) {
        if (strat.caster.possession == 1) {
            strat.caster.possession = 0;
        }
        parameterizedFlash(strat, strat.c.getD("titan.goalie.slide.cds"), strat.c.getI("titan.goalie.slide.dist"));
    }

    public static void parameterizedFlash(AbilityStrategy strat, double cdSeconds, int dist) {
        Titan caster = strat.caster;
        GameEngine context = strat.context;
        Const c = strat.c;

        int cd = (int) (caster.cooldownFactor * cdSeconds * 1000);
        dist *= caster.rangeFactor;
        context.effectPool.addUniqueEffect(new CooldownW(cd, caster), context);
        strat.shape = new CollisionMath.Bounds(0, 0, 2, 2);
        strat.sel = new Selector(strat.shape, SelectorOffset.MOUSE_CENTER, c.FAR_RANGE);
        new Targeting(strat.sel, AbilityHelper.champions, AbilityHelper.nearest, context)
                .process(strat.x, strat.y, caster, (int) context.ball.X, (int) context.ball.Y);
        CollisionMath.Bounds re = strat.sel.latestCollider;
        if (re.minX() > caster.X + 35) {
            caster.facing = 0;
            caster.diagonalRunDir = 2;
            caster.dirToBall = 2;
        } else if (re.minX() < caster.X + 35) {
            caster.facing = 180;
            caster.diagonalRunDir = 1;
            caster.dirToBall = 1;
        }
        int limitt = 0;
        while (limitt < dist) {
            double ang = Util.degreesFromCoords(re.minX() - caster.X - 35, re.minY() - caster.Y - 35);
            double dx = Math.cos(Math.toRadians((ang)));
            double dy = Math.sin(Math.toRadians((ang)));
            if (!caster.collidesSolid(context, context.allSolids, (int) dx, (int) dy)) { // collision
                caster.translateBounded(context, dx, dy);
            }
            limitt++;
        }
        if (caster.possession == 1) {
            context.ball.X = (int) Math.round(caster.X + caster.width / 2.0 - context.ball.centerDist);
            context.ball.Y = (int) Math.round(caster.Y + caster.height / 2.0 - context.ball.centerDist);
        }
        caster.pushMove();
    }

    public static void kickSelectedTarget(AbilityStrategy strat) {
        Titan caster = strat.caster;
        GameEngine context = strat.context;
        Const c = strat.c;

        int range = (int) (c.getI("titan.kick.range") * caster.rangeFactor);
        strat.shape = new CollisionMath.Bounds(0, 0, range * 2, range * 2);
        strat.sel = new Selector(strat.shape, SelectorOffset.MOUSE_CENTER, range);
        strat.appliedTo = new Targeting(strat.sel, AbilityHelper.champions, AbilityHelper.mouseNear, context)
                .process(strat.x, strat.y, caster, (int) context.ball.X, (int) context.ball.Y);
        if (!strat.appliedTo.isEmpty()) {
            context.effectPool.addUniqueEffect(
                    new CooldownW((int) (c.getI("titan.kick.cdms") * caster.cooldownFactor), caster), context);
        }
        for (Entity e : strat.appliedTo) {
            double tx = caster.X;
            double ty = caster.Y;
            double ang = Util.degreesFromCoords(tx - e.X, ty - e.Y);
            ang += 180; // Kick them away, not towards
            int limitt = 0;
            while (limitt < c.getI("titan.kick.range")) {
                double dx = Math.cos(Math.toRadians((ang)));
                double dy = Math.sin(Math.toRadians((ang)));
                if (!e.collidesSolid(context, context.allSolids, 0, (int) dy)) {
                    e.translateBounded(context, 0, dy);
                }
                if (!e.collidesSolid(context, context.allSolids, (int) dx, 0)) {
                    e.translateBounded(context, dx, 0);
                }
                limitt++;
            }
        }
    }

    public static void spiderCocoon(AbilityStrategy strat) {
        Titan caster = strat.caster;
        GameEngine context = strat.context;
        Const c = strat.c;

        int range = (int) (c.getI("titan.spider.cocoon.range") * caster.rangeFactor);
        strat.shape = new CollisionMath.Bounds(0, 0, range * 2, range * 2);
        strat.sel = new Selector(strat.shape, SelectorOffset.MOUSE_CENTER, range);
        strat.appliedTo = new Targeting(strat.sel, AbilityHelper.all, AbilityHelper.mouseNear, context)
                .process(strat.x, strat.y, caster, (int) context.ball.X, (int) context.ball.Y);

        Entity targetHero = null;
        for (Entity e : strat.appliedTo) {
            if (e instanceof Titan && !e.id.equals(caster.id)) {
                targetHero = e;
                break;
            }
        }
        if (targetHero == null) {
            return;
        }

        AbilityHelper.goOnCooldown(context, caster, "titan.spider.cocoon.cdms", 'W');
        caster.actionState = Titan.TitanState.A2;
        context.effectPool.addUniqueEffect(new EmptyEffect(1000, caster, EffectId.ROOT), context);
        final double origX = caster.X + 35.0;
        final double origY = caster.Y + 35.0;
        final double targetCastX = targetHero.X + 35.0;
        final double targetCastY = targetHero.Y + 35.0;

        double dx = targetCastX - origX;
        double dy = targetCastY - origY;
        double dist = Math.hypot(dx, dy);
        if (dist < 1.0) {
            dx = 1.0;
            dy = 0.0;
            dist = 1.0;
        }
        double rawDestX = targetCastX + (dx / dist) * 70.0 - 35.0;
        double rawDestY = targetCastY + (dy / dist) * 70.0 - 35.0;

        final double destX = Math.max(context.c.MIN_X, Math.min(context.c.MAX_X - 70, rawDestX));
        final double destY = Math.max(context.c.MIN_Y, Math.min(context.c.MAX_Y - 70, rawDestY));
        final double finalDx = dx;
        final double finalDy = dy;

        context.effectPool.addStackingEffect(new CallbackEffect(1000, caster, EffectId.COOLDOWN_W, () -> {
            double finalX = destX;
            double finalY = destY;

            // Find overlapping player if any to determine preferred push-out direction
            double prefAngle = Math.atan2(finalDy, finalDx);
            if (context.players != null) {
                for (Titan other : context.players) {
                    if (other != null && other.health > 0 && !other.id.equals(caster.id)) {
                        int otherW = other.width > 0 ? other.width : 70;
                        int otherH = other.height > 0 ? other.height : 70;
                        if (finalX + 70 > other.X && finalX < other.X + otherW &&
                            finalY + 70 > other.Y && finalY < other.Y + otherH) {
                            double pdx = (finalX + 35.0) - (other.X + otherW / 2.0);
                            double pdy = (finalY + 35.0) - (other.Y + otherH / 2.0);
                            if (Math.hypot(pdx, pdy) > 0.1) {
                                prefAngle = Math.atan2(pdy, pdx);
                            }
                            break;
                        }
                    }
                }
            }

            double[] safePos = AbilityPositionHelper.findClosestUnoccupiedPosition(finalX, finalY, caster, context, prefAngle);
            caster.setX((int) safePos[0]);
            caster.setY((int) safePos[1]);
            caster.actionState = Titan.TitanState.IDLE;
        }));
    }
}
