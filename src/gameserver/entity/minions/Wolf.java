package gameserver.entity.minions;

import gameserver.engine.GameEngine;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.Titan;
import util.Util;

import java.util.UUID;

public class Wolf extends gameserver.entity.Entity implements Tickable {
    public int wolfPower;
    public boolean facingRight = false;
    private UUID createdById;

    private static double MOVE_SPEED = 2.3;
    private static final double BITE_DIST = 22.0;

    public Wolf(Cage source, int numCages) {
        this.team = source.team;
        this.createdById = source.getCreatedById();
        this.width = 70;
        this.height = 35;
        this.setX(source.X + source.width/2 - this.width/2);
        this.setY(source.Y + source.height/2 - this.height/2);
        this.health = 8;
        this.maxHealth = 8;
        this.solid = false;
        this.wolfPower = numCages;
        System.out.println("power " + this.wolfPower);
    }

    @Override
    public void tick(GameEngine context){
        Titan nearest = getNearestEnemy(context);
        if(nearest != null){
            if(nearest.X + 35 > this.X + this.width/2){
                this.facingRight = true;
                this.X += MOVE_SPEED;
            }
            else{
                this.facingRight = false;
                this.X -= MOVE_SPEED;
            }
            if(nearest.Y + 35 > this.Y + this.height/2){
                this.Y += MOVE_SPEED;
            }
            else{
                this.Y -= MOVE_SPEED;
            }
            if(dist(nearest) < BITE_DIST){
                bite(context, nearest);
            }
        }
    }

    private void bite(GameEngine context, Titan nearest) {
        if(this.wolfPower == 1){
            nearest.damage(context, .25);
        }
        if(this.wolfPower > 1 && this.wolfPower < 3){
            nearest.damage(context, .35);
        }
        if(this.wolfPower >= 3 && this.wolfPower < 5){
            nearest.damage(context, 1.5);
        }
        if(this.wolfPower >= 5){
            nearest.damage(context, this.wolfPower);
        }
    }

    private double dist(Titan t){
        return Util.dist(this.getX() + (this.width / 2.0),
        this.getY() + (this.height / 2.0),
        t.X + 35,
        t.Y + 35);
    }

    public Titan getNearestEnemy(GameEngine context){
        double dist = 99999.0;
        Titan ret = null;//should never return this
        for(Titan t : context.players){
            if(t.team != this.team) {
                double cmp = Util.dist(this.getX() + (this.width / 2.0),
                        this.getY() + (this.height / 2.0),
                        t.X + 35,
                        t.Y + 35);
                if (cmp < dist){
                    dist = cmp;
                    ret = t;
                }
            }
        }
        return ret;
    }

    public Wolf() {
        super(TeamAffiliation.UNAFFILIATED);
    }
}
