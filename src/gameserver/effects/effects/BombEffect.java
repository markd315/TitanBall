package gameserver.effects.effects;

import gameserver.GameEngine;
import gameserver.effects.EffectId;
import gameserver.entity.Entity;

public class BombEffect extends Effect {

    private double eventualDamage;

    public BombEffect(int durationMillis, Entity e){
        this(durationMillis, e, 50);
    }

    public BombEffect(int durationMillis, Entity e, double eventualDamage){
        super(EffectId.BOMB, e, durationMillis);
        this.eventualDamage = eventualDamage;
    }

    @Override
    public void onActivate(GameEngine context) {

    }

    @Override
    public void onCease(GameEngine context) {
        on.damage(context, eventualDamage);
    }

    @Override
    public void onTick(GameEngine context) {
    }

    public BombEffect(){}
}
