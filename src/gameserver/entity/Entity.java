package gameserver.entity;


import gameserver.engine.GameEngine;
import gameserver.effects.EffectId;
import gameserver.effects.effects.DeadEffect;
import gameserver.engine.TeamAffiliation;
import gameserver.models.Game;

public class Entity extends Box {
    public double health, maxHealth;
    public TeamAffiliation team;
    public double speed = 5;
    public double armorRatio = 1.0;
    public double healReduce = 1.0;

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
        health /= this.armorRatio;
        this.health -= health;
        if (this.health < 0.0)
            this.die(context);
    }

    private void die(GameEngine context) {
        context.effectPool.addUniqueEffect(new DeadEffect(4000, this, context), context);
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
        for(Titan t : context.players){
            if(t.possession == 1 && t.team.equals(this.team)){
                return true;
            }
        }
        return false;
    }

    public void translateBounded(GameEngine context, double dx, double dy) {
        this.X+=dx;
        this.Y+=dy;
        if(this instanceof Titan && context.effectPool.hasEffect((Titan) this, EffectId.DEAD)){
            this.X = 99999;
            this.Y = 99999;
            return;
        }
        if(this.X > Game.E_MAX_X){
            this.X = Game.E_MAX_X;
        }
        if(this.X < Game.E_MIN_X){
            this.X = Game.E_MIN_X;
        }
        if(this.Y > Game.E_MAX_Y){
            this.Y = Game.E_MAX_Y;
        }
        if(this.Y < Game.E_MIN_Y){
            this.Y = Game.E_MIN_Y;
        }
    }
}
