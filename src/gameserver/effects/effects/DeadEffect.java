package gameserver.effects.effects;

import gameserver.effects.EffectId;
import gameserver.engine.GameEngine;
import gameserver.engine.StatEngine;
import gameserver.entity.Entity;
import gameserver.entity.Titan;
import gameserver.models.Game;

public class DeadEffect extends Effect {

    public DeadEffect(int durationMillis, Entity e, Game context){
        super(EffectId.DEAD, e, durationMillis);
        if(e instanceof Titan) {
            Titan t = (Titan) e;
            if(t.possession == 1){
                t.possession = 0;
                context.lastPossessed = null;
                //context.ball.X = t.X + 35 - context.ball.centerDist;
                //context.ball.Y = t.Y + 35 - context.ball.centerDist;
            }
        }
    }

    @Override
    public void onActivate(GameEngine context) {
        if(on instanceof Titan) {
            Titan t = (Titan) on;
            if(t.possession == 1){
                t.possession = 0;
                context.lastPossessed = null;
                //context.ball.X = t.X + 35 - context.ball.centerDist;
                //context.ball.Y = t.Y + 35 - context.ball.centerDist;
            }
            t.runUp = 0;
            t.runDown = 0;
            t.runLeft = 0;
            t.runRight = 0;
            t.actionState = Titan.TitanState.DEAD;
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
