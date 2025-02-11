package gameserver.effects.effects;

import gameserver.engine.GameEngine;
import gameserver.effects.EffectId;
import gameserver.entity.Entity;

public class DefenseEffect extends Effect {
    double armorRatio;

    public DefenseEffect(int durationMillis, Entity e){
        super(EffectId.DEFENSE, e, durationMillis);
        armorRatio = 1.5;
    }

    public DefenseEffect(int durationMillis, Entity e, double armorRatio){
        super(EffectId.DEFENSE, e, durationMillis);
        this.armorRatio = armorRatio;
    }

    @Override
    public void onActivate(GameEngine context) {
        on.armorRatio*=armorRatio;
    }

    @Override
    public void onCease(GameEngine context) {
        on.armorRatio/=armorRatio;
    }

    @Override
    public void onTick(GameEngine context) {
    }

    public DefenseEffect(){}
}
