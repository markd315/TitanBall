package gameserver.effects.effects;

import gameserver.engine.GameEngine;
import gameserver.effects.EffectId;
import gameserver.engine.StatEngine;
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
            t.actionState = Titan.TitanState.DEAD;
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
            ((Titan) on).resurrecting = true;
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
