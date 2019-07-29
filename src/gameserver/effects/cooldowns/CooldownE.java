package gameserver.effects.cooldowns;

import gameserver.Game;
import gameserver.effects.EffectId;
import gameserver.effects.effects.Effect;
import gameserver.entity.Entity;

public class CooldownE extends Effect {

    public CooldownE(int durationMillis, Entity e){
        super(EffectId.COOLDOWN_E, e, durationMillis);
    }

    @Override
    public void onActivate(Game context) {

    }

    @Override
    public void onCease(Game context) {
    }

    @Override
    public void onTick(Game context) {
    }

    public CooldownE(){}
}
