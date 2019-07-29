package gameserver.effects.effects;

import gameserver.Game;
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
    public void onActivate(Game context) {

    }

    @Override
    public void onCease(Game context) {
        on.damage(context, eventualDamage);
    }

    @Override
    public void onTick(Game context) {
    }

    public BombEffect(){}
}
