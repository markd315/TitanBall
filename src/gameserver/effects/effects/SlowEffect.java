package gameserver.effects.effects;

import gameserver.Game;
import gameserver.effects.EffectId;
import gameserver.entity.Entity;

public class SlowEffect extends Effect {

    private double ratio;

    public SlowEffect(int durationMillis, Entity e){
        this(durationMillis, e, 1.5);
    }

    public SlowEffect(int durationMillis, Entity e, double ratio){
        super(EffectId.SLOW, e, durationMillis);
        this.ratio = ratio;
    }

    @Override
    public void onActivate(Game context) {
        on.setSpeed(on.getSpeed() / ratio);
    }

    @Override
    public void onCease(Game context) {
        on.setSpeed(on.getSpeed() * ratio);
    }

    @Override
    public void onTick(Game context) {
    }

    public SlowEffect(){}
}
