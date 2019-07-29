package gameserver.effects.cooldowns;


import gameserver.Game;
import gameserver.effects.EffectId;
import gameserver.effects.effects.Effect;
import gameserver.entity.Entity;

public class CooldownR extends Effect {

    public CooldownR(int durationMillis, Entity e){
        super(EffectId.COOLDOWN_R, e, durationMillis);
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

    public CooldownR(){}
}
