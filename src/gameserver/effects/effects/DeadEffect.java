package gameserver.effects.effects;

import gameserver.Game;
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
    public void onActivate(Game context) {
        if(on instanceof Titan) {
            context.stats.grant(context.clientFromTitan((on)), StatEngine.StatEnum.DEATHS);
        }
        on.setHealth(-99999);
        context.effectPool.cullAllOn(context, on);
    }

    @Override
    public void onCease(Game context) {
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
    public void onTick(Game context) {
        if(on instanceof Titan){
            Titan t = (Titan) on;
            t.possession = 0;
        }
        on.setHealth(-99999);
        on.X = 9999999;
        on.Y = 9999999;
    }

    public DeadEffect(){}
}
