package gameserver.effects.effects;

import gameserver.engine.GameEngine;
import gameserver.effects.EffectId;
import gameserver.entity.Entity;
import gameserver.entity.Titan;

import com.fasterxml.jackson.annotation.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FlareEffect extends Effect  {

    private double initialDamage, recurringDamage;
    @JsonIgnore
    private Titan caster;
    @JsonProperty("casterId")
    public String casterId;

    public FlareEffect(int durationMillis, Entity e){
        this(durationMillis, e, 30, .2, null);
    }

    public FlareEffect(int durationMillis, Entity e, double initialDamage, double recurringDamage){
        this(durationMillis, e, initialDamage, recurringDamage, null);
    }

    public FlareEffect(int durationMillis, Entity e, double initialDamage, double recurringDamage, Titan caster){
        super(EffectId.FLARE, e, durationMillis);
        this.initialDamage = initialDamage;
        this.recurringDamage = recurringDamage;
        this.caster = caster;
        if (caster != null && caster.id != null) {
            this.casterId = caster.id.toString();
        }
    }

    public Titan resolveCaster(GameEngine context) {
        if (this.caster != null) return this.caster;
        if (this.casterId != null && context != null) {
            return context.titanByID(this.casterId).orElse(null);
        }
        return null;
    }

    @Override
    public void onActivate(GameEngine context) {
        on.damage(context, initialDamage, resolveCaster(context));
    }

    @Override
    public void onCease(GameEngine context) {
    }

    @Override
    public void onTick(GameEngine context) {
        on.damage(context, recurringDamage, resolveCaster(context));
    }

    public FlareEffect(){}
}
