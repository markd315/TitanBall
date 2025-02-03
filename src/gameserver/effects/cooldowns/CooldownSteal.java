package gameserver.effects.cooldowns;

import gameserver.effects.EffectId;
import gameserver.effects.effects.Effect;
import gameserver.engine.GameEngine;
import gameserver.entity.Entity;

import java.io.Serializable;

public class CooldownSteal extends Effect implements Serializable {

    public CooldownSteal(int durationMillis, Entity e){
        super(EffectId.COOLDOWN_STEAL, e, durationMillis);
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

    public CooldownSteal(){}
}
