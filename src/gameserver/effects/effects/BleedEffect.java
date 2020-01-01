package gameserver.effects.effects;

import gameserver.engine.GameEngine;
import gameserver.effects.EffectId;
import gameserver.entity.Entity;

public class BleedEffect extends Effect {

    private double initialDamage, recurringDamage;

    public BleedEffect(int durationMillis, Entity e){
        this(durationMillis, e, 30, .2);
    }

    public BleedEffect(int durationMillis, Entity e, double initialDamage, double recurringDamage){
        super(EffectId.FLARE, e, durationMillis);
        this.initialDamage = initialDamage;
        this.recurringDamage = recurringDamage;
    }

    @Override
    public void onActivate(GameEngine context) {
        on.damage(context, initialDamage);
    }

    @Override
    public void onCease(GameEngine context) {
    }

    @Override
    public void onTick(GameEngine context) {
        on.damage(context, recurringDamage);
    }

    public BleedEffect(){}
}
