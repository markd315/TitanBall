package gameserver.entity;


import gameserver.effects.EffectId;
import gameserver.effects.effects.DeadEffect;
import gameserver.engine.GameEngine;
import gameserver.engine.TeamAffiliation;

import com.fasterxml.jackson.annotation.*;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "entityClass",
    defaultImpl = Entity.class
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = Titan.class, name = "Titan"),
    @JsonSubTypes.Type(value = gameserver.entity.minions.Portal.class, name = "Portal"),
    @JsonSubTypes.Type(value = gameserver.entity.minions.BallPortal.class, name = "BallPortal"),
    @JsonSubTypes.Type(value = gameserver.entity.minions.Wolf.class, name = "Wolf"),
    @JsonSubTypes.Type(value = gameserver.entity.minions.Cage.class, name = "Cage"),
    @JsonSubTypes.Type(value = gameserver.entity.minions.Fire.class, name = "Fire"),
    @JsonSubTypes.Type(value = gameserver.entity.minions.Wall.class, name = "Wall"),
    @JsonSubTypes.Type(value = gameserver.entity.minions.Trap.class, name = "Trap"),
    @JsonSubTypes.Type(value = gameserver.entity.minions.LaneMinion.class, name = "LaneMinion"),
    @JsonSubTypes.Type(value = gameserver.entity.minions.Dragon.class, name = "Dragon"),
    @JsonSubTypes.Type(value = gameserver.entity.minions.SecondBall.class, name = "SecondBall"),
    @JsonSubTypes.Type(value = gameserver.entity.minions.Parapet.class, name = "Parapet")
})
public class Entity extends Box   {
    public double health, maxHealth;
    public TeamAffiliation team;
    public double speed = 5;
    public double armorRatio = 1.0;
    public double healReduce = 1.0;
    public double painReduction = 1.0;

    @JsonIgnore
    public transient Titan lastAttacker;
    @JsonIgnore
    public transient long lastAttackerTimeMs;

    public Entity() {
    }

    public Entity(TeamAffiliation team) {
        this();
        this.team = team;
        if (team != null && team != TeamAffiliation.HOME && team != TeamAffiliation.AWAY && team != TeamAffiliation.UNAFFILIATED) {
            throw new IllegalArgumentException("Entity team must be a literal, not a comparator");
        }
    }

    public double getHealth() {
        return health;
    }

    public void heal(double health) {
        health/= healReduce;
        this.health += health;
        if (this.health > this.maxHealth)
            this.health = this.maxHealth;
    }

    public void damage(GameEngine context, double health) {
        damage(context, health, null);
    }

    public void damage(GameEngine context, double health, Titan attacker) {
        double currentArmor = this.armorRatio;
        if (this instanceof Titan t) {
            java.util.Set<String> purchased = (t.team == TeamAffiliation.HOME) ? context.homeGoaliePurchasedUpgrades : context.awayGoaliePurchasedUpgrades;
            if (purchased != null && purchased.contains("empowerment.t5.clutchgene") && t.possession == 1) {
                currentArmor *= 1.3;
            }
        }
        health /= currentArmor;
        if (health > 0.0 && attacker != null && this instanceof Titan && attacker.team != this.team) {
            this.lastAttacker = attacker;
            this.lastAttackerTimeMs = System.currentTimeMillis();
            if (context != null && context.effectPool != null) {
                context.effectPool.addStackingEffect(attacker, new gameserver.effects.effects.EmptyEffect(5000, this, EffectId.ATTACKED));
            }
        }
        this.health -= health;
        if (this.health <= 0.0)
            this.die(context);
    }

    private void die(GameEngine context) {
        int respawnMs = context.c.getI("globals.titan.respawn.ms");
        context.effectPool.addUniqueEffect(new DeadEffect(respawnMs, this, context), context);
    }

    public void setHealth(int health) {
        this.health = health;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }
    public double getHealReduce() {
        return healReduce;
    }

    public void setHealReduce(double healReduce) {
        this.healReduce = healReduce;
    }


    public boolean teamPoss(GameEngine context) {
        if (context == null || context.players == null) {
            return false;
        }
        for (Titan t : context.players) {
            if (t != null && t.possession == 1 && t.team == this.team) {
                return true;
            }
        }
        return false;
    }

    public void translateBounded(GameEngine context, double dx, double dy) {
        this.X += dx;
        this.Y += dy;
        if (this instanceof Titan titan && context.effectPool != null && context.effectPool.hasEffect(titan, EffectId.DEAD)) {
            this.X = 99999;
            this.Y = 99999;
            return;
        }
        if (this instanceof Titan titan && titan.getType() == TitanType.GOALIE) {
            java.util.Set<String> purchased = (this.team == TeamAffiliation.HOME) ? context.homeGoaliePurchasedUpgrades : context.awayGoaliePurchasedUpgrades;
            boolean pullGoalie = purchased != null && purchased.contains("siege.t3.pullgoalie");
            if (!pullGoalie) {
                int xMin = (this.team == TeamAffiliation.HOME) ? context.c.GOALIE_XH_MIN : context.c.GOALIE_XA_MIN;
                int xMax = (this.team == TeamAffiliation.HOME) ? context.c.GOALIE_XH_MAX : context.c.GOALIE_XA_MAX;
                this.X = Math.max(xMin, Math.min(xMax, this.X));
                this.Y = Math.max(context.c.GOALIE_Y_MIN, Math.min(context.c.GOALIE_Y_MAX, this.Y));
                return;
            }
        }
        this.X = Math.max(context.c.E_MIN_X, Math.min(context.c.E_MAX_X, this.X));
        this.Y = Math.max(context.c.E_MIN_Y, Math.min(context.c.E_MAX_Y, this.Y));
    }

    public String toString()   {
        return "Entity{" +
                "id=" + id +
                ", team=" + team +
                ", health=" + health +
                ", maxHealth=" + maxHealth +
                ", X=" + X +
                ", Y=" + Y +
                ", solid=" + solid +
                ", width=" + width +
                ", height=" + height +
                ", speed=" + speed
                + "}";
    }
}
