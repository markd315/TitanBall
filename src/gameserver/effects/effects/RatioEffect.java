package gameserver.effects.effects;

import gameserver.effects.EffectId;
import gameserver.entity.Entity;

public class RatioEffect extends EmptyEffect{
    private double ratio;

    public RatioEffect(int durationMillis, Entity e, EffectId id, double ratio){
        super(durationMillis, e, id);
        this.ratio = ratio;
    }

    public double getRatio() {
        return ratio;
    }

    public RatioEffect(){}
}
