package gameserver.entity.minions;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import gameserver.effects.EffectId;
import gameserver.effects.effects.EmptyEffect;
import gameserver.engine.GameEngine;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.Entity;
import gameserver.entity.Titan;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Bomb extends Entity implements Tickable, Serializable {

    @JsonIgnore
    public Titan caster;
    public long spawnEpochMs;
    public int fuseMs = 3000;
    public boolean exploded = false;
    @JsonIgnore
    public int ticksRemaining = 120; // 3000ms / 25ms tick
    @JsonIgnore
    public int explosionTicks = 16;  // ~400ms visual explosion duration

    public Bomb(Titan caster, int x, int y, GameEngine context) {
        super(caster.team);
        this.caster = caster;
        this.setX(x);
        this.setY(y);
        this.width = 50;
        this.height = 50;
        this.health = 9999;
        this.maxHealth = 9999;
        this.solid = false;
        this.spawnEpochMs = context.nowEpochMs;
        this.ticksRemaining = 3000 / context.c.GAMETICK_MS;
    }

    public Bomb() {
        super(TeamAffiliation.UNAFFILIATED);
    }

    @Override
    public void tick(GameEngine context) {
        if (!exploded) {
            ticksRemaining--;
            if (ticksRemaining <= 0) {
                exploded = true;
                double centerOrigX = this.X + 25.0;
                double centerOrigY = this.Y + 25.0;
                // Explode with 2x frame scale: 100 width, 140 height, centered
                this.width = 100;
                this.height = 140;
                this.setX((int) (centerOrigX - 50.0));
                this.setY((int) (centerOrigY - 70.0));

                double dmg = 50.0 * (caster != null ? caster.damageFactor : 1.0);

                // Hit enemies in the 100x140 area
                gameserver.engine.CollisionMath.Bounds bounds = this.asBounds();
                if (context.players != null) {
                    for (Titan t : context.players) {
                        if (t != null && t.team != this.team && t.asBounds().intersects(bounds) && t.getHealth() > 0) {
                            t.damage(context, dmg);
                            if (caster != null) {
                                context.effectPool.addStackingEffect(caster, new EmptyEffect(5000, t, EffectId.ATTACKED));
                            }
                        }
                    }
                }
                if (context.entityPool != null) {
                    for (Entity e : context.entityPool) {
                        if (e != null && e.team != this.team && e.asBounds().intersects(bounds) && e.getHealth() > 0) {
                            e.damage(context, dmg);
                            if (caster != null) {
                                context.effectPool.addStackingEffect(caster, new EmptyEffect(5000, e, EffectId.ATTACKED));
                            }
                        }
                    }
                }
            }
        } else {
            explosionTicks--;
            if (explosionTicks <= 0) {
                this.health = 0; // Cleared on next sweep
            }
        }
    }
}
