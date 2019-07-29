package gameserver.effects.effects;

import gameserver.Game;
import gameserver.effects.EffectId;
import gameserver.entity.Entity;

public class TestEffect extends Effect {

    public TestEffect(int durationMillis, Entity e){
        super(EffectId.FAST, e, durationMillis);
    }

    @Override
    public void onActivate(Game context) {
        System.out.println("activated");
    }

    @Override
    public void onCease(Game context) {
        System.out.println("ceased");
    }

    @Override
    public void onTick(Game context) {
        System.out.println("ticked");
    }

    public TestEffect(){}
}
