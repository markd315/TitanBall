package gameserver.effects.effects;

import gameserver.Game;
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
    public void onActivate(Game context) {
        on.armorRatio*=armorRatio;
    }

    @Override
    public void onCease(Game context) {
        on.armorRatio/=armorRatio;
    }

    @Override
    public void onTick(Game context) {
    }

    public DefenseEffect(){}
}
