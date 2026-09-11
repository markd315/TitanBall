package gameserver.engine;

import gameserver.Const;
import gameserver.effects.EffectId;
import gameserver.effects.cooldowns.CooldownQ;
import gameserver.effects.cooldowns.CooldownW;
import gameserver.effects.effects.*;
import gameserver.entity.Entity;
import gameserver.entity.Titan;
import gameserver.entity.minions.Fire;
import gameserver.targeting.SelectorOffset;
import gameserver.targeting.Targeting;
import gameserver.targeting.core.Selector;

import java.util.Collections;

/**
 * Executes offensive combat, damage, and crowd control abilities.
 */
public class TitanAbilitiesCombat {

    public static void ignite(AbilityStrategy strat, double cd, double dur, double initialD, double recurringD) {
        Titan caster = strat.caster;
        GameEngine context = strat.context;
        Const c = strat.c;

        cd *= caster.cooldownFactor;
        dur *= caster.durationsFactor;
        initialD *= caster.damageFactor;
        recurringD *= caster.damageFactor;
        int range = (int) (c.getI("titan.ignite.range") * caster.rangeFactor);

        strat.shape = new CollisionMath.Bounds(0, 0, range * 2, range * 2);
        strat.sel = new Selector(strat.shape, SelectorOffset.MOUSE_CENTER, range);
        strat.appliedTo = new Targeting(strat.sel, AbilityHelper.notFriendly, AbilityHelper.mouseNear, context)
                .process(strat.x, strat.y, caster, (int) context.ball.X, (int) context.ball.Y);
        if (strat.appliedTo.isEmpty()) {
            return;
        }
        for (Entity e : strat.appliedTo) {
            if (initialD + recurringD > 0.0) {
                context.effectPool.addStackingEffect(caster, new EmptyEffect(5000, e, EffectId.ATTACKED));
            }
            context.effectPool.addUniqueEffect(new CooldownW((int) (cd * 1000), caster), context);
            context.effectPool.addStackingEffect(caster, new FlareEffect((int) (dur * 1000), e, initialD, recurringD, caster));
        }
    }

    public static void circleSlash(AbilityStrategy strat, double dmg, double cdMs) {
        Titan caster = strat.caster;
        GameEngine context = strat.context;
        Const c = strat.c;

        dmg *= caster.damageFactor;
        double range = c.getI("titan.slash.range") * caster.rangeFactor;
        strat.shape = new CollisionMath.Bounds(0, 0, range, range);
        strat.sel = new Selector(strat.shape, SelectorOffset.CAST_CENTER, c.FAR_RANGE);
        strat.appliedTo = new Targeting(strat.sel, AbilityHelper.notFriendly, AbilityHelper.unlimited, context)
                .process(strat.x, strat.y, caster, (int) context.ball.X, (int) context.ball.Y);
        if (strat.appliedTo.isEmpty()) {
            return;
        }
        context.effectPool.addUniqueEffect(new CooldownQ((int) (cdMs * caster.cooldownFactor), caster), context);
        for (Entity e : strat.appliedTo) {
            context.effectPool.addStackingEffect(caster, new EmptyEffect(5000, e, EffectId.ATTACKED));
            e.damage(context, dmg, caster);
        }
        caster.pushMove();
    }

    public static void shootArrow(AbilityStrategy strat, double dmg) {
        Titan caster = strat.caster;
        GameEngine context = strat.context;
        Const c = strat.c;

        int range = (int) (c.getI("titan.arrow.range") * caster.rangeFactor);
        dmg *= caster.damageFactor;
        strat.shape = new CollisionMath.Bounds(0, 0, range * 2, range * 2);
        strat.sel = new Selector(strat.shape, SelectorOffset.MOUSE_CENTER, range);
        strat.appliedTo = new Targeting(strat.sel, AbilityHelper.notFriendly, AbilityHelper.mouseNear, context)
                .process(strat.x, strat.y, caster, (int) context.ball.X, (int) context.ball.Y);
        if (strat.appliedTo.isEmpty()) {
            return;
        }
        AbilityHelper.goOnCooldown(context, caster, "titan.arrow.cdms", 'Q');
        for (Entity e : strat.appliedTo) {
            context.effectPool.addStackingEffect(caster, new EmptyEffect(5000, e, EffectId.ATTACKED));
            e.damage(context, dmg, caster);
        }
    }

    public static void chargeShot(AbilityStrategy strat) {
        Titan caster = strat.caster;
        GameEngine context = strat.context;
        Const c = strat.c;

        int dur = (int) (c.getI("titan.shoot.dur") * caster.durationsFactor);
        AbilityHelper.goOnCooldown(context, caster, "titan.shoot.cdms", 'W');
        context.effectPool.addUniqueEffect(
                new ShootEffect(dur, caster, c.getD("titan.shoot.ratio")), context);
    }

    public static void slow(AbilityStrategy strat) {
        Titan caster = strat.caster;
        GameEngine context = strat.context;
        Const c = strat.c;

        int dur = (int) (c.getI("titan.slow.dur") * caster.durationsFactor);
        int range = (int) (c.getI("titan.slow.range") * caster.rangeFactor);
        strat.shape = new CollisionMath.Bounds(0, 0, range * 2, range * 2);
        strat.sel = new Selector(strat.shape, SelectorOffset.MOUSE_CENTER, range);
        strat.appliedTo = new Targeting(strat.sel, AbilityHelper.champions, AbilityHelper.mouseNear, context)
                .process(strat.x, strat.y, caster, (int) context.ball.X, (int) context.ball.Y);
        if (strat.appliedTo.isEmpty()) {
            return;
        }
        for (Entity e : strat.appliedTo) {
            AbilityHelper.goOnCooldown(context, caster, "titan.slow.cdms", 'Q');
            strat.eff = new RatioEffect(dur, e, EffectId.SLOW, c.getD("titan.slow.ratio"));
            context.effectPool.addUniqueEffect(strat.eff, context);
        }
    }

    public static void flashbang(AbilityStrategy strat, double durMillis) {
        Titan caster = strat.caster;
        GameEngine context = strat.context;
        Const c = strat.c;

        int range = (int) (c.getI("titan.flashbang.range") * caster.rangeFactor);
        int dur = (int) (durMillis * caster.durationsFactor);
        strat.shape = new CollisionMath.Bounds(0, 0, range, range);
        strat.sel = new Selector(strat.shape, SelectorOffset.CAST_CENTER, c.FAR_RANGE);
        strat.appliedTo = new Targeting(strat.sel, AbilityHelper.champions, AbilityHelper.nearest, context)
                .process(strat.x, strat.y, caster, (int) context.ball.X, (int) context.ball.Y);
        if (strat.appliedTo.isEmpty()) {
            return;
        }
        AbilityHelper.goOnCooldown(context, caster, "titan.flashbang.cdms", 'Q');
        for (Entity e : strat.appliedTo) {
            strat.eff = new EmptyEffect(dur, e, EffectId.BLIND);
            context.effectPool.addCasterUniqueEffect(strat.eff, caster);
        }
    }

    public static void molotov(AbilityStrategy strat) {
        Titan caster = strat.caster;
        GameEngine context = strat.context;
        Const c = strat.c;

        int range = (int) (c.getI("titan.molotov.range") * caster.rangeFactor);
        strat.shape = new CollisionMath.Bounds(0, 0, range, range);
        strat.sel = new Selector(strat.shape, SelectorOffset.MOUSE_CENTER, range);
        strat.sel.select(Collections.EMPTY_SET, strat.x, strat.y, caster);
        strat.corners = strat.sel.getLatestColliderBounds();

        if (strat.corners.getWidth() > 0 && AbilityPositionHelper.inBoundsNotRedzone(strat.corners, context)) {
            AbilityHelper.goOnCooldown(context, caster, "titan.molotov.cdms", 'W');
            context.entityPool.add(new Fire(caster, (int) strat.corners.getX(), (int) strat.corners.getY()));
        }
    }

    public static void stunByRadius(AbilityStrategy strat, double durMillis) {
        Titan caster = strat.caster;
        GameEngine context = strat.context;
        Const c = strat.c;

        int range = (int) (c.getI("titan.stun.range") * caster.rangeFactor);
        int dur = (int) (durMillis * caster.durationsFactor);
        strat.shape = new CollisionMath.Bounds(0, 0, range, range);
        strat.sel = new Selector(strat.shape, SelectorOffset.CAST_CENTER, c.FAR_RANGE);
        strat.appliedTo = new Targeting(strat.sel, AbilityHelper.champions, AbilityHelper.nearest, context)
                .process(strat.x, strat.y, caster, (int) context.ball.X, (int) context.ball.Y);
        if (strat.appliedTo.isEmpty()) {
            return;
        }
        AbilityHelper.goOnCooldown(context, caster, "titan.stun.cdms", 'Q');
        for (Entity e : strat.appliedTo) {
            strat.eff = new EmptyEffect(dur, e, EffectId.STUN);
            context.effectPool.addCasterUniqueEffect(strat.eff, caster);
        }
    }
}
