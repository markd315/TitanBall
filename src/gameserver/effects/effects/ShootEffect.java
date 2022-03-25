package gameserver.effects.effects;

import gameserver.engine.GameEngine;
import gameserver.effects.EffectId;
import gameserver.entity.Entity;
import gameserver.entity.Titan;

import java.io.Serializable;

public class ShootEffect extends Effect implements Serializable {
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
    public void onActivate(GameEngine context) {
        if(on instanceof Titan) {
            Titan t = (Titan) on;
            t.throwPower *= shotRatio;
        }
    }

    @Override
    public void onCease(GameEngine context) {
        if(!ceased){
            if(on instanceof Titan) {
                Titan t = (Titan) on;
                t.throwPower /= shotRatio;
            }
            ceased = true;
        }
    }

    @Override
    public void onTick(GameEngine context) {
    }

    public ShootEffect(){}
}
