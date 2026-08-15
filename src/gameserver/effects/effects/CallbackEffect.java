package gameserver.effects.effects;

import gameserver.effects.EffectId;
import gameserver.engine.GameEngine;
import gameserver.entity.Entity;

public class CallbackEffect extends Effect {
    private transient Runnable callback;

    public CallbackEffect() {
        super(EffectId.COOLDOWN_Q, null, 0);
    }

    public CallbackEffect(int durationMillis, Entity on, EffectId effect, Runnable callback) {
        super(effect, on, durationMillis);
        this.callback = callback;
    }

    @Override
    public void onActivate(GameEngine context) {}

    @Override
    public void onCease(GameEngine context) {
        if (callback != null) {
            callback.run();
        }
    }

    @Override
    public void onTick(GameEngine context) {}
}
