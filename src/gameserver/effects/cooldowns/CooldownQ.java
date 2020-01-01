package gameserver.effects.cooldowns;

import gameserver.engine.GameEngine;
import gameserver.effects.EffectId;
import gameserver.effects.effects.Effect;
import gameserver.entity.Entity;

public class CooldownQ extends Effect {

    public CooldownQ(int durationMillis, Entity e){
        super(EffectId.COOLDOWN_Q, e, durationMillis);
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

    public CooldownQ(){}
}
