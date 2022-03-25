package gameserver.effects.effects;


import gameserver.engine.GameEngine;
import gameserver.effects.EffectId;
import gameserver.entity.Entity;

import java.io.Serializable;

public class HideBallEffect extends Effect implements Serializable {
    public HideBallEffect(int durationMillis, Entity e){
        super(EffectId.HIDE_BALL, e, durationMillis);
    }

    @Override
    public void onActivate(GameEngine context) {
        context.ballVisible = false;
    }

    @Override
    public void onCease(GameEngine context) {
        if(!ceased){
            context.ballVisible = true;
            context.lastPossessed = null;
            ceased = true;
        }
    }

    @Override
    public void onTick(GameEngine context) {
        context.ballVisible = false;
    }

    public HideBallEffect(){}
}

