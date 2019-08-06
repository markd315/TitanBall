package gameserver.entity.minions;

import gameserver.GameEngine;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.Box;
import gameserver.entity.Collidable;
import gameserver.entity.Entity;

public class Trap extends gameserver.entity.Entity implements Collidable {

    public Trap(TeamAffiliation team, int x, int y) {
        super(team);
        this.setX(x);
        this.setY(y);
        this.width = 50;
        this.height = 50;
        this.health = 50;
        this.maxHealth = 50;
        this.solid = false;
    }

    @Override
    public void triggerCollide(GameEngine context, Box box) {
        if (box instanceof Entity) {
            Entity entity = (Entity) box;
            if (entity.team != this.team) {
                entity.damage(context, 2.5);
            }
        }
    }

    public Trap(){
        super(TeamAffiliation.UNAFFILIATED);
    }
}
