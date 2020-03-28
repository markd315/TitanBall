package gameserver.effects.effects;

import gameserver.engine.GameEngine;
import gameserver.effects.EffectId;
import gameserver.entity.Entity;

import java.io.Serializable;

public class BombEffect extends Effect implements Serializable {

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
        if(!ceased){
            on.damage(context, eventualDamage);
            ceased = true;
        }
    }

    @Override
    public void onTick(GameEngine context) {
    }

    public BombEffect(){}
}
