package gameserver.effects.effects;

import gameserver.engine.GameEngine;
import gameserver.effects.EffectId;
import gameserver.entity.Entity;

import java.io.Serializable;

public class TestEffect extends Effect implements Serializable {

    public TestEffect(int durationMillis, Entity e){
        super(EffectId.FAST, e, durationMillis);
    }

    @Override
    public void onActivate(GameEngine context) {
        System.out.println("activated");
    }

    @Override
    public void onCease(GameEngine context) {
        if(!ceased){
            System.out.println("ceased");
            ceased = true;
        }
    }

    @Override
    public void onTick(GameEngine context) {
        System.out.println("ticked");
    }

    public TestEffect(){}
}
