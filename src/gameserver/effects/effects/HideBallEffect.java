package gameserver.effects.effects;


import gameserver.engine.GameEngine;
import gameserver.effects.EffectId;
import gameserver.entity.Entity;
import gameserver.entity.Titan;

import com.fasterxml.jackson.annotation.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HideBallEffect extends Effect  {
    public HideBallEffect(int durationMillis, Entity e){
        super(EffectId.HIDE_BALL, e, durationMillis);
    }

    @Override
    public void onActivate(GameEngine context) {
        if (on instanceof Titan t && t.possession == 1 && t.actionState == Titan.TitanState.IDLE) {
            context.ballVisible = false;
        }
    }

    @Override
    public void onCease(GameEngine context) {
        if (!ceased) {
            context.ballVisible = true;
            context.lastPossessed = null;
            ceased = true;
        }
    }

    @Override
    public void onTick(GameEngine context) {
        if (on instanceof Titan t && t.possession == 1 && t.actionState == Titan.TitanState.IDLE) {
            context.ballVisible = false;
        } else {
            context.ballVisible = true;
        }
    }

    public HideBallEffect(){}
}

