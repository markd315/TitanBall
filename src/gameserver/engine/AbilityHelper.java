package gameserver.engine;

import gameserver.Const;
import gameserver.effects.cooldowns.CooldownQ;
import gameserver.effects.cooldowns.CooldownSteal;
import gameserver.effects.cooldowns.CooldownW;
import gameserver.entity.Titan;
import gameserver.entity.TitanType;
import gameserver.targeting.SortBy;
import gameserver.targeting.core.Filter;
import gameserver.targeting.core.Limiter;

/**
 * Shared filters, limiters, and cooldown utilities for ability strategies.
 */
public class AbilityHelper {

    public static final Filter friendly = new Filter(TeamAffiliation.SAME, TitanType.ANY, false);
    public static final Filter friendlyIncSelf = new Filter(TeamAffiliation.SAME, TitanType.ANY, true);
    public static final Filter champions = new Filter(TeamAffiliation.OPPONENT, TitanType.ANY, false);
    public static final Filter championsNoGoalie = new Filter(TeamAffiliation.OPPONENT, TitanType.ANY, false);
    public static final Filter enemiesIncMinions = new Filter(TeamAffiliation.ENEMIES, TitanType.ANY, false);
    public static final Filter all = new Filter(TeamAffiliation.ANY, TitanType.ANY, true);
    public static final Filter notFriendly = new Filter(TeamAffiliation.ENEMIES, TitanType.ANY_ENTITY, false);

    public static final Limiter nearest = new Limiter(SortBy.NEAREST, 1);
    public static final Limiter unlimited = new Limiter(SortBy.NEAREST, 999);
    public static final Limiter mouseNear = new Limiter(SortBy.NEAREST_MOUSE, 1);

    public static void goOnCooldown(GameEngine context, Titan caster, String cdKey, char qOrW) {
        if (context == null || caster == null || cdKey == null) return;
        Const c = context.c;
        if (c == null) return;

        switch (qOrW) {
            case 'Q':
                context.effectPool.addUniqueEffect(
                        new CooldownQ((int) (caster.cooldownFactor * c.getI(cdKey)), caster), context);
                break;
            case 'W':
                context.effectPool.addUniqueEffect(
                        new CooldownW((int) (caster.cooldownFactor * c.getI(cdKey)), caster), context);
                break;
            case 'S':
                context.effectPool.addUniqueEffect(
                        new CooldownSteal((int) (caster.cooldownFactor * c.getI(cdKey)), caster), context);
                break;
        }
    }
}
