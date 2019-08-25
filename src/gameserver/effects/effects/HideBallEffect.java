package gameserver.effects.effects;


import gameserver.GameEngine;
import gameserver.effects.EffectId;
import gameserver.entity.Entity;

public class HideBallEffect extends Effect {
    public HideBallEffect(int durationMillis, Entity e){
        super(EffectId.HIDE_BALL, e, durationMillis);
    }

    @Override
    public void onActivate(GameEngine context) {
        context.ballVisible = false;
    }

    @Override
    public void onCease(GameEngine context) {
        context.ballVisible = true;
    }

    @Override
    public void onTick(GameEngine context) {
        context.ballVisible = false;
    }

    public HideBallEffect(){}
}

