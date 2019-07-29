package gameserver.effects.effects;


import gameserver.Game;
import gameserver.effects.EffectId;
import gameserver.entity.Entity;

public class EmptyEffect extends Effect {

    public EmptyEffect(int durationMillis, Entity e, EffectId id){
        super(id, e, durationMillis);
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

    public EmptyEffect(){}
}
