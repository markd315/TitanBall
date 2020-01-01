package gameserver.effects.effects;

import gameserver.engine.GameEngine;
import gameserver.effects.EffectId;
import gameserver.entity.Entity;

public class FastEffect extends Effect {

    private double ratio;

    public FastEffect(int durationMillis, Entity e){
        this(durationMillis, e, 1.5);
    }

    public FastEffect(int durationMillis, Entity e, double ratio){
        super(EffectId.FAST, e, durationMillis);
        this.ratio = ratio;
    }

    @Override
    public void onActivate(GameEngine context) {
        on.setSpeed(on.getSpeed() * ratio);
    }

    @Override
    public void onCease(GameEngine context) {
        on.setSpeed(on.getSpeed() / ratio);
    }

    @Override
    public void onTick(GameEngine context) {
    }

    public FastEffect(){}
}
