package gameserver.effects.effects;


import gameserver.Game;
import gameserver.effects.EffectId;
import gameserver.entity.Entity;

public class PossessionEffect extends Effect {
    public PossessionEffect(int durationMillis, Entity e){
        super(EffectId.POSSESSION, e, durationMillis);
    }

    @Override
    public void onActivate(Game context) {
        on.setHealReduce(on.getHealReduce() * 1000);
        on.setSpeed(on.getSpeed() / 3.2);
    }

    @Override
    public void onCease(Game context) {
        on.setHealReduce(on.getHealReduce() / 1000);
        on.setSpeed(on.getSpeed() * 3.2);
    }

    @Override
    public void onTick(Game context) {
    }

    public PossessionEffect(){}
}

