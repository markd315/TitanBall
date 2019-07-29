package gameserver.entity;


import gameserver.Game;
import gameserver.effects.effects.DeadEffect;
import gameserver.engine.TeamAffiliation;

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

    public void damage(Game context, double health) {
        health /= this.armorRatio;
        this.health -= health;
        if (this.health < 0.0)
            this.die(context);
    }

    private void die(Game context) {
        context.effectPool.addUniqueEffect(new DeadEffect(4000, this));
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


    public boolean teamPoss(Game context) {
        for(Titan t : context.players){
            if(t.possession == 1 && t.team.equals(this.team)){
                return true;
            }
        }
        return false;
    }
}
