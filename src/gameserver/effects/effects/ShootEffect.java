package gameserver.effects.effects;

import gameserver.engine.GameEngine;
import gameserver.effects.EffectId;
import gameserver.entity.Entity;
import gameserver.entity.Titan;

import com.fasterxml.jackson.annotation.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ShootEffect extends Effect  {
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
    }

    @Override
    public void onCease(GameEngine context) {
        ceased = true;
    }

    @Override
    public void onTick(GameEngine context) {
    }

    public ShootEffect(){}
}
