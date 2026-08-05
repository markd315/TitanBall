package gameserver.effects.effects;


import gameserver.engine.GameEngine;
import gameserver.effects.EffectId;
import gameserver.entity.Entity;

import com.fasterxml.jackson.annotation.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EmptyEffect extends Effect  {

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
