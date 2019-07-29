package gameserver.effects.effects;

import gameserver.Game;
import gameserver.effects.EffectId;
import gameserver.entity.Entity;
import gameserver.entity.Titan;

public class ShootEffect extends Effect {
    double shotRatio;

    public ShootEffect(int durationMillis, Entity e){
        super(EffectId.SHOOT, e, durationMillis);
        shotRatio = 1.5;
    }

    public ShootEffect(int durationMillis, Entity e, double shotRatio){
        super(EffectId.SHOOT, e, durationMillis);
        this.shotRatio = shotRatio;
    }

    @Override
    public void onActivate(Game context) {
        if(on instanceof Titan) {
            Titan t = (Titan) on;
            t.throwPower *= shotRatio;
        }
    }

    @Override
    public void onCease(Game context) {
        if(on instanceof Titan) {
            Titan t = (Titan) on;
            t.throwPower /= shotRatio;
        }
    }

    @Override
    public void onTick(Game context) {
    }

    public ShootEffect(){}
}
