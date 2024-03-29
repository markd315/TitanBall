package gameserver.effects.cooldowns;


import gameserver.engine.GameEngine;
import gameserver.effects.EffectId;
import gameserver.effects.effects.Effect;
import gameserver.entity.Entity;

import java.io.Serializable;

public class CooldownCurve extends Effect implements Serializable {

    public CooldownCurve(int durationMillis, Entity e){
        super(EffectId.COOLDOWN_CURVE, e, durationMillis);
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

    public CooldownCurve(){}
}
