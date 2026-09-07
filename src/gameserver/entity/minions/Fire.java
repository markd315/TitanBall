package gameserver.entity.minions;

import gameserver.Const;
import gameserver.effects.EffectId;
import gameserver.effects.effects.EmptyEffect;
import gameserver.effects.effects.FlareEffect;
import gameserver.engine.GameEngine;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.Box;
import gameserver.entity.Collidable;
import gameserver.entity.Entity;
import gameserver.entity.Titan;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;

public class Fire extends gameserver.entity.Entity implements Collidable, Serializable {

    private String caster;
    @JsonIgnore
    private transient Titan casterTitan;
    private static Const c = new Const("res/game.cfg");

    public Fire(Titan caster, int x, int y) {
        super(caster.team);
        this.casterTitan = caster;
        this.caster = caster.id.toString();
        this.setX(x);
        this.setY(y);
        this.width = 140;
        this.height = 140;
        this.health = c.getD("titan.molotov.hp");
        this.maxHealth = health;
        this.solid = false;
    }

    public String getCaster() {
        return caster;
    }

    public void setCaster(String caster) {
        this.caster = caster;
    }

    public Titan resolveCaster(GameEngine context) {
        if (this.casterTitan != null) return this.casterTitan;
        if (this.caster != null && context != null) {
            return context.titanByID(this.caster).orElse(null);
        }
        return null;
    }

    @Override
    public void triggerCollide(GameEngine context, Box box) {
        if (box instanceof Entity) {
            Entity entity = (Entity) box;
            if (entity.team != this.team) {
                Titan casterObj = resolveCaster(context);
                if (casterObj != null && entity instanceof Titan) {
                    context.effectPool.addStackingEffect(casterObj, new EmptyEffect(5000, entity, EffectId.ATTACKED));
                }
                context.effectPool.addUniqueEffect(casterObj, new FlareEffect(
                        c.getI("titan.molotov.dur"),
                        entity, c.getD("titan.molotov.initd"),
                        c.getD("titan.molotov.recurd"),
                        casterObj),
                        context);
            }
        }
    }

    public Fire(){
        super(TeamAffiliation.UNAFFILIATED);
    }
}
