package gameserver.engine;

import gameserver.Const;
import gameserver.effects.EffectId;
import gameserver.effects.cooldowns.CooldownQ;
import gameserver.effects.effects.EmptyEffect;
import gameserver.effects.effects.HealEffect;
import gameserver.entity.Entity;
import gameserver.entity.Titan;
import gameserver.entity.minions.Bomb;
import gameserver.entity.minions.Parapet;
import gameserver.targeting.SelectorOffset;
import gameserver.targeting.Targeting;
import gameserver.targeting.core.Selector;
import util.Util;

import java.util.Collections;

/**
 * Executes support, healing, ball control, and defense abilities.
 */
public class TitanAbilitiesSupport {

    public static void goalieBlock(AbilityStrategy strat) {
        Titan caster = strat.caster;
        GameEngine context = strat.context;
        Const c = strat.c;

        int cd = (int) (caster.cooldownFactor * c.getI("titan.goalie.block.cdms"));
        int dur = (int) (caster.durationsFactor * c.getI("titan.goalie.block.dur"));
        context.effectPool.addUniqueEffect(new CooldownQ(cd, caster), context);
        context.effectPool.addUniqueEffect(new EmptyEffect(dur, caster, EffectId.BLOCK), context);
    }

    public static void heal(AbilityStrategy strat) {
        Titan caster = strat.caster;
        GameEngine context = strat.context;
        Const c = strat.c;

        int dur = (int) (c.getI("titan.heal.dur") * caster.durationsFactor);
        int range = (int) (c.getI("titan.heal.range") * caster.rangeFactor);
        strat.shape = new CollisionMath.Bounds(0, 0, range * 2, range * 2);
        strat.sel = new Selector(strat.shape, SelectorOffset.MOUSE_CENTER, range);
        strat.appliedTo = new Targeting(strat.sel, AbilityHelper.friendlyIncSelf, AbilityHelper.mouseNear, context)
                .process(strat.x, strat.y, caster, (int) context.ball.X, (int) context.ball.Y);
        if (strat.appliedTo.isEmpty()) {
            return;
        }
        for (Entity e : strat.appliedTo) {
            AbilityHelper.goOnCooldown(context, caster, "titan.heal.cdms", 'W');
            strat.eff = new HealEffect(dur, e, c.getD("titan.heal.initd"), c.getD("titan.heal.recurd"));
            context.effectPool.addStackingEffect(strat.eff); // also unique and singleton
        }
    }

    public static void suckBall(AbilityStrategy strat) {
        Titan caster = strat.caster;
        GameEngine context = strat.context;
        Const c = strat.c;

        int range = (int) (c.getI("titan.suck.range") * caster.rangeFactor);
        strat.shape = new CollisionMath.Bounds(0, 0, range, range);
        strat.sel = new Selector(strat.shape, SelectorOffset.CAST_CENTER, c.FAR_RANGE);
        strat.sel.select(Collections.EMPTY_SET, strat.x, strat.y, caster);
        if (strat.sel.latestCollider.intersects(context.ball.asBounds()) && !context.anyPoss()) {
            AbilityHelper.goOnCooldown(context, caster, "titan.suck.cdms", 'Q');
            if (context.lastPossessed != null && context.lastPossessed.equals(caster.id)) {
                context.lastPossessed = null;
            }
            double dx = (caster.X + (caster.width > 0 ? caster.width / 2.0 : 35.0)) - (context.ball.X + (context.ball.width > 0 ? context.ball.width / 2.0 : 10.0));
            double dy = (caster.Y + (caster.height > 0 ? caster.height / 2.0 : 35.0)) - (context.ball.Y + (context.ball.height > 0 ? context.ball.height / 2.0 : 10.0));
            double dist = Math.hypot(dx, dy);
            double dirX = dist > 0 ? dx / dist : 0, dirY = dist > 0 ? dy / dist : 0;

            int limit = 0;
            int maxDist = (int) (range * 1.5); // Enough to pull from max range
            while (!context.anyPoss() && limit < maxDist) {
                context.ball.X += 3.0 * dirX;
                context.ball.Y += 3.0 * dirY;
                if (!context.contactExemptBall()) {
                    for (int n = context.players.length - 1; n >= 0; n--) {
                        Titan p = context.players[n];
                        if (p.id.equals(caster.id) || p.team != caster.team) {
                            context.intersectBall(n + 1, (int) p.X, (int) p.Y);
                        }
                    }
                    Entity[] arr = new Entity[1];
                    context.ball.collidesSolid(context, context.entityPool.toArray(arr));
                }
                try {
                    context.detectGoals();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                limit++;
            }
        }
    }

    public static boolean stealBall(AbilityStrategy strat) {
        Titan caster = strat.caster;
        GameEngine context = strat.context;
        Const c = strat.c;

        if (context.titanInPossession().isEmpty() || !context.titanInPossession().get().id.equals(caster.id)) {
            AbilityHelper.goOnCooldown(context, caster, "titan.steal.cdms", 'S');
            if (context.titanInPossession().isPresent()) {
                Titan tip = context.titanInPossession().get();
                boolean isOccupyingParapet = false;
                for (Entity e : context.entityPool) {
                    if (e instanceof Parapet p && p.isMounted(tip.id)) {
                        isOccupyingParapet = true;
                        break;
                    }
                }
                if (!isOccupyingParapet) {
                    if (context.isWithinStealRange(caster, tip) && context.ballVisible) {
                        try {
                            context.stats.grant(context, tip, StatEngine.StatEnum.TURNOVERS);
                            context.stats.grant(context, caster, StatEngine.StatEnum.STEALS);
                        } catch (Exception ignored) {}
                        tip.possession = 0;
                        strat.eff = new EmptyEffect((int) (c.STOLEN_STUN * caster.durationsFactor), tip, EffectId.STEAL);
                        context.effectPool.addStackingEffect(caster, strat.eff);

                        context.ball.X = (int) Math.round(caster.X + caster.width / 2.0 - context.ball.centerDist);
                        context.ball.Y = (int) Math.round(caster.Y + caster.height / 2.0 - context.ball.centerDist);
                        caster.actionState = Titan.TitanState.IDLE;
                        caster.actionFrame = 0;
                        caster.possession = 1;
                        context.resetAiReactionAfterPossession(caster);
                        return true;
                    }
                }
            }
            caster.pushMove();
        }
        return false;
    }

    public static void captainShoot(AbilityStrategy strat) {
        Titan caster = strat.caster;
        GameEngine context = strat.context;
        Const c = strat.c;

        if (caster.ammo <= 0) {
            return;
        }
        int range = (int) (c.getI("titan.captain.shot.range") * caster.rangeFactor);
        double dmgChamp = 5.0 * caster.damageFactor;
        double dmgMinion = 10.0 * caster.damageFactor;
        strat.shape = new CollisionMath.Bounds(0, 0, range * 2, range * 2);
        strat.sel = new Selector(strat.shape, SelectorOffset.MOUSE_CENTER, range);
        strat.appliedTo = new Targeting(strat.sel, AbilityHelper.notFriendly, AbilityHelper.mouseNear, context)
                .process(strat.x, strat.y, caster, (int) context.ball.X, (int) context.ball.Y);
        if (strat.appliedTo.isEmpty()) {
            return;
        }

        for (Entity e : strat.appliedTo) {
            context.effectPool.addStackingEffect(caster, new EmptyEffect(5000, e, EffectId.ATTACKED));
            if (e instanceof Titan) {
                e.damage(context, dmgChamp, caster);
            } else {
                e.damage(context, dmgMinion, caster);
            }
        }

        caster.ammo--;
        if (caster.ammo > 0) {
            AbilityHelper.goOnCooldown(context, caster, "titan.captain.shot.cdms", 'Q');
        } else {
            AbilityHelper.goOnCooldown(context, caster, "titan.captain.reload.cdms", 'Q');
        }
    }

    public static void captainSlideBomb(AbilityStrategy strat) {
        Titan caster = strat.caster;
        GameEngine context = strat.context;
        Const c = strat.c;

        int maxDist = (int) (c.getI("titan.captain.slide.range") * caster.rangeFactor);
        AbilityHelper.goOnCooldown(context, caster, "titan.captain.slide.cdms", 'W');

        // Drop 3s timebomb at origin
        context.entityPool.add(new Bomb(caster, (int) caster.X, (int) caster.Y, context));

        // Slide toward target direction up to maxDist
        double ang = Util.degreesFromCoords(strat.x - caster.X - 35, strat.y - caster.Y - 35);
        double dx = Math.cos(Math.toRadians(ang));
        double dy = Math.sin(Math.toRadians(ang));
        int limit = 0;
        while (limit < maxDist) {
            if (!caster.collidesSolid(context, context.allSolids, 0, (int) dy)) {
                caster.translateBounded(context, 0, dy);
            }
            if (!caster.collidesSolid(context, context.allSolids, (int) dx, 0)) {
                caster.translateBounded(context, dx, 0);
            }
            limit++;
        }
    }
}
