package gameserver.effects.cooldowns;

import gameserver.engine.GameEngine;
import gameserver.effects.EffectId;
import gameserver.effects.effects.Effect;
import gameserver.entity.Entity;

import java.io.Serializable;

public class CooldownE extends Effect implements Serializable {

    public CooldownE(int durationMillis, Entity e){
        super(EffectId.COOLDOWN_E, e, durationMillis);
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

    public CooldownE(){}
}
