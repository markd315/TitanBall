package gameserver.engine;

import gameserver.Const;
import gameserver.effects.cooldowns.CooldownW;
import gameserver.entity.Entity;
import gameserver.entity.Titan;
import gameserver.entity.minions.*;
import gameserver.targeting.SelectorOffset;
import gameserver.targeting.Targeting;
import gameserver.targeting.core.Selector;
import util.Util;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Executes deployable objects, traps, portals, cages, and field obstacles.
 */
public class TitanAbilitiesDeployable {

    public static void wall(AbilityStrategy strat) {
        Titan caster = strat.caster;
        GameEngine context = strat.context;
        Const c = strat.c;

        int range = (int) (c.getI("titan.wall.range") * caster.rangeFactor);
        strat.shape = new CollisionMath.Bounds(0, 0, 12, 120);
        strat.sel = new Selector(strat.shape, SelectorOffset.MOUSE_CENTER, range);
        strat.sel.select(Collections.EMPTY_SET, strat.x, strat.y, caster);
        strat.corners = strat.sel.getLatestColliderBounds();
        if (strat.corners.width() > 0 && AbilityPositionHelper.inBoundsNotRedzone(strat.corners, context)) {
            AbilityHelper.goOnCooldown(context, caster, "titan.wall.cdms", 'W');
            Wall w = new Wall(context, (int) strat.corners.minX(), (int) strat.corners.minY());
            w.team = caster.team;
            context.entityPool.add(w);
        }
    }

    public static void scatter(AbilityStrategy strat, int rangeIn, int scatterDist, int cdms) {
        Titan caster = strat.caster;
        GameEngine context = strat.context;
        Const c = strat.c;

        int range = (int) (rangeIn * caster.rangeFactor);
        strat.shape = new CollisionMath.Bounds(0, 0, range, range);
        strat.sel = new Selector(strat.shape, SelectorOffset.CAST_CENTER, c.FAR_RANGE);
        strat.appliedTo = new Targeting(strat.sel, AbilityHelper.champions, AbilityHelper.unlimited, context)
                .process(strat.x, strat.y, caster, (int) context.ball.X, (int) context.ball.Y);
        if (strat.appliedTo.isEmpty()) {
            return;
        }
        context.effectPool.addUniqueEffect(
                new CooldownW((int) (caster.cooldownFactor * cdms), caster), context);
        int limit = 0;
        while (limit < scatterDist) {
            for (Entity e : strat.appliedTo) {
                double tx = caster.X;
                double ty = caster.Y;
                double ang = Util.degreesFromCoords(tx - e.X, ty - e.Y);
                ang += 180; // Kick them away, not towards
                double dx = Math.cos(Math.toRadians((ang)));
                double dy = Math.sin(Math.toRadians((ang)));
                if (!e.collidesSolid(context, context.allSolids, 0, (int) dy)) {
                    e.translateBounded(context, 0, dy);
                }
                if (!e.collidesSolid(context, context.allSolids, (int) dx, 0)) {
                    e.translateBounded(context, dx, 0);
                }
            }
            limit++;
        }
    }

    public static void spawnBallPortal(AbilityStrategy strat) {
        Titan caster = strat.caster;
        GameEngine context = strat.context;
        Const c = strat.c;

        int range = (int) (c.getI("titan.bportal.range") * caster.rangeFactor);
        strat.shape = new CollisionMath.Bounds(0, 0, 50, 50);
        strat.sel = new Selector(strat.shape, SelectorOffset.MOUSE_CENTER, range);
        strat.sel.select(Collections.EMPTY_SET, strat.x, strat.y, caster);
        strat.corners = strat.sel.getLatestColliderBounds();
        if (strat.corners.getWidth() > 0 && AbilityPositionHelper.inBoundsNotRedzone(strat.corners, context)) {
            AbilityHelper.goOnCooldown(context, caster, "titan.bportal.cdms", 'W');
            context.entityPool.add(new BallPortal(caster.team, caster, context.entityPool,
                    (int) strat.corners.getX(),
                    (int) strat.corners.getY(), context));
        }
    }

    public static void spawnPortal(AbilityStrategy strat) {
        Titan caster = strat.caster;
        GameEngine context = strat.context;
        Const c = strat.c;

        int range = (int) (c.getI("titan.portal.range") * caster.rangeFactor);
        strat.shape = new CollisionMath.Bounds(0, 0, 50, 50);
        strat.sel = new Selector(strat.shape, SelectorOffset.MOUSE_CENTER, range);
        strat.sel.select(Collections.EMPTY_SET, strat.x, strat.y, caster);
        strat.corners = strat.sel.getLatestColliderBounds();
        if (strat.corners.getWidth() > 0 && AbilityPositionHelper.inBoundsNotRedzone(strat.corners, context)) {
            AbilityHelper.goOnCooldown(context, caster, "titan.portal.cdms", 'Q');
            System.out.println("-1 hit");
            context.entityPool.add(new Portal(caster.team, caster,
                    context.entityPool, (int) strat.corners.getX(), (int) strat.corners.getY(), context));
        }
    }

    public static void spawnTrap(AbilityStrategy strat) {
        Titan caster = strat.caster;
        GameEngine context = strat.context;
        Const c = strat.c;

        int range = (int) (c.getI("titan.trap.range") * caster.rangeFactor);
        strat.shape = new CollisionMath.Bounds(0, 0, 100, 100);
        strat.sel = new Selector(strat.shape, SelectorOffset.MOUSE_CENTER, range);
        strat.sel.select(Collections.EMPTY_SET, strat.x, strat.y, caster);
        strat.corners = strat.sel.getLatestColliderBounds();
        if (strat.corners.getWidth() > 0 && AbilityPositionHelper.inBoundsNotRedzone(strat.corners, context)) {
            AbilityHelper.goOnCooldown(context, caster, "titan.trap.cdms", 'Q');
            context.entityPool.add(new Trap(caster, context, (int) strat.corners.getX(), (int) strat.corners.getY()));
        }
    }

    public static void spawnCage(AbilityStrategy strat) {
        Titan caster = strat.caster;
        GameEngine context = strat.context;
        Const c = strat.c;

        int range = (int) (c.getI("titan.cage.range") * caster.rangeFactor);
        strat.shape = new CollisionMath.Bounds(0, 0, 70, 70);
        strat.sel = new Selector(strat.shape, SelectorOffset.MOUSE_CENTER, range);
        strat.sel.select(Collections.EMPTY_SET, strat.x, strat.y, caster);
        strat.corners = strat.sel.getLatestColliderBounds();
        if (strat.corners.getWidth() > 0 && AbilityPositionHelper.inBoundsNotRedzone(strat.corners, context)) {
            AbilityHelper.goOnCooldown(context, caster, "titan.cage.cdms", 'Q');
            context.entityPool.add(new Cage(caster.team, caster,
                    (int) strat.corners.getX(), (int) strat.corners.getY(), context));
        }
    }

    public static void releaseCages(AbilityStrategy strat) {
        Titan caster = strat.caster;
        GameEngine context = strat.context;

        AbilityHelper.goOnCooldown(context, caster, "titan.wolf.cdms", 'W');
        ArrayList<Cage> cages = new ArrayList<Cage>();
        for (Entity e : context.entityPool) {
            if (e instanceof Cage && ((Cage) e).getCreatedById().equals(caster.id)) {
                cages.add((Cage) e);
            }
        }
        for (Cage c : cages) {
            c.open(context, cages.size());
        }
    }

    public static void spiderWeb(AbilityStrategy strat) {
        Titan caster = strat.caster;
        GameEngine context = strat.context;
        Const c = strat.c;

        int range = (int) (c.getI("titan.spider.web.range") * caster.rangeFactor);
        strat.shape = new CollisionMath.Bounds(0, 0, 110, 110);
        strat.sel = new Selector(strat.shape, SelectorOffset.MOUSE_CENTER, range);
        strat.sel.select(Collections.EMPTY_SET, strat.x, strat.y, caster);
        strat.corners = strat.sel.getLatestColliderBounds();
        if (strat.corners.getWidth() > 0 && AbilityPositionHelper.inBoundsNotRedzone(strat.corners, context)) {
            AbilityHelper.goOnCooldown(context, caster, "titan.spider.web.cdms", 'Q');
            context.entityPool.add(new Web(caster, context, (int) strat.corners.getX(), (int) strat.corners.getY()));
        }
    }
}
