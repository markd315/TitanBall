package gameserver.effects.effects;


import gameserver.GameEngine;
import gameserver.effects.EffectId;
import gameserver.entity.Entity;

public class PossessionEffect extends Effect {
    public PossessionEffect(int durationMillis, Entity e){
        super(EffectId.POSSESSION, e, durationMillis);
    }

    @Override
    public void onActivate(GameEngine context) {
        on.setHealReduce(on.getHealReduce() * 1000);
        on.setSpeed(on.getSpeed() / 3.2);
    }

    @Override
    public void onCease(GameEngine context) {
        on.setHealReduce(on.getHealReduce() / 1000);
        on.setSpeed(on.getSpeed() * 3.2);
    }

    @Override
    public void onTick(GameEngine context) {
    }

    public PossessionEffect(){}
}

