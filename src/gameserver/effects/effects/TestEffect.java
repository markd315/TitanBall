package gameserver.effects.effects;

import gameserver.GameEngine;
import gameserver.effects.EffectId;
import gameserver.entity.Entity;

public class TestEffect extends Effect {

    public TestEffect(int durationMillis, Entity e){
        super(EffectId.FAST, e, durationMillis);
    }

    @Override
    public void onActivate(GameEngine context) {
        System.out.println("activated");
    }

    @Override
    public void onCease(GameEngine context) {
        System.out.println("ceased");
    }

    @Override
    public void onTick(GameEngine context) {
        System.out.println("ticked");
    }

    public TestEffect(){}
}
