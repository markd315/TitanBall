package gameserver.effects.effects;

import gameserver.GameEngine;
import gameserver.effects.EffectId;
import gameserver.engine.StatEngine;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.Entity;
import gameserver.entity.Titan;

public class DeadEffect extends Effect {

    public DeadEffect(int durationMillis, Entity e){
        super(EffectId.DEAD, e, durationMillis);
    }

    @Override
    public void onActivate(GameEngine context) {
        if(on instanceof Titan) {
            Titan t = (Titan) on;
            if(t.possession == 1){
                context.lastPossessed = null;
                t.possession = 0;
                context.ball.X = t.X + 35 - context.ball.centerDist;
                context.ball.Y = t.Y + 35 - context.ball.centerDist;
            }
            context.stats.grant(context.clientFromTitan((on)), StatEngine.StatEnum.DEATHS);
            context.stats.grantKillAssists(context, (Titan) on, context.effectPool);
        }
        on.setHealth(-99999);
        context.effectPool.cullAllOn(context, on);
    }

    @Override
    public void onCease(GameEngine context) {
        if(on instanceof Titan) {
            if (on.team == TeamAffiliation.HOME) {
                on.X = 133;
                on.Y = 584;
                while (on.collidesSolid(context, context.allSolids)) {
                    on.Y += 35;
                }
            }
            if (on.team == TeamAffiliation.AWAY) {
                on.X = 1923;
                on.Y = 584;
                while (on.collidesSolid(context, context.allSolids)) {
                    on.Y += 35;
                }
            }
            on.health = on.maxHealth;
        }
    }

    @Override
    public void onTick(GameEngine context) {
        on.setHealth(-99999);
        on.X = 9999999;
        on.Y = 9999999;
    }

    public DeadEffect(){}
}
