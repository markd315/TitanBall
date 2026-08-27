package gameserver.entity.minions;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import gameserver.effects.EffectId;
import gameserver.effects.effects.RatioEffect;
import gameserver.engine.GameEngine;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.Box;
import gameserver.entity.Collidable;
import gameserver.entity.Entity;
import gameserver.entity.Titan;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Web extends Entity implements Collidable, Tickable, Serializable {

    @JsonIgnore
    public Titan caster;
    public long spawnEpochMs;
    @JsonIgnore
    public int lifetimeTicks = 800; // 20s duration

    public Web(Titan caster, GameEngine context, int x, int y) {
        super(caster.team);
        this.caster = caster;
        this.setX(x);
        this.setY(y);
        this.width = 110;
        this.height = 110;
        this.health = 12;
        this.maxHealth = 12;
        this.solid = false;
        this.spawnEpochMs = context.nowEpochMs;
    }

    public Web() {
        super(TeamAffiliation.UNAFFILIATED);
    }

    @Override
    public void triggerCollide(GameEngine context, Box box) {
        if (box instanceof Entity) {
            Entity entity = (Entity) box;
            if (entity.team != this.team) {
                // 25% slow (speed multiplier 0.75, so ratio = 1.0 / 0.75 = 1.3333)
                context.effectPool.addUniqueEffect(
                        new RatioEffect(1000, entity, EffectId.SLOW, 1.3333), context);
            }
        }
    }

    @Override
    public void tick(GameEngine context) {
        lifetimeTicks--;
        if (lifetimeTicks <= 0) {
            this.health = 0;
            return;
        }

        // Stick loose ball if it enters web bounds
        if (!context.anyPoss() && this.asBounds().intersects(context.ball.asBounds())) {
            context.xKickPow = 0;
            context.yKickPow = 0;
        }
    }
}
