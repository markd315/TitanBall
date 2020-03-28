package gameserver.effects.effects;


import gameserver.engine.GameEngine;
import gameserver.effects.EffectId;
import gameserver.entity.Entity;

import java.io.Serializable;

public class EmptyEffect extends Effect implements Serializable {

    public EmptyEffect(int durationMillis, Entity e, EffectId id){
        super(id, e, durationMillis);
    }

    @Override
    public void onActivate(GameEngine context) {
    }

    @Override
    public void onCease(GameEngine context) {
    }

    @Override
    public void onTick(GameEngine context) {
    }

    public EmptyEffect(){}
}
