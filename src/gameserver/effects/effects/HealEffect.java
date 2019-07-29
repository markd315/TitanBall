package gameserver.effects.effects;


import gameserver.Game;
import gameserver.effects.EffectId;
import gameserver.entity.Entity;

public class HealEffect extends Effect {

    private double initialDamage, recurringDamage;

    public HealEffect(int durationMillis, Entity e){
        this(durationMillis, e, 30, .2);
    }

    public HealEffect(int durationMillis, Entity e, double initialDamage, double recurringDamage){
        super(EffectId.HEAL, e, durationMillis);
        this.initialDamage = initialDamage;
        this.recurringDamage = recurringDamage;
    }

    @Override
    public void onActivate(Game context) {
        on.heal(initialDamage);
    }

    @Override
    public void onCease(Game context) {
    }

    @Override
    public void onTick(Game context) {
        on.heal(recurringDamage);
    }

    public HealEffect(){}
}
