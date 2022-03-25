package gameserver.entity.minions;

import gameserver.Const;
import gameserver.engine.GameEngine;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.Entity;
import gameserver.entity.Titan;

import java.io.Serializable;
import java.util.UUID;

public class Cage extends Entity  implements Serializable {
    private UUID createdById;
    public Const c;

    public Cage(TeamAffiliation team, Titan pl, int x, int y, GameEngine context) {
        super(team);
        this.setX(x);
        this.setY(y);
        this.width = 70;
        this.height = 70;
        this.c = context.c;
        this.health = c.getI("cage.hp");
        this.maxHealth = this.health;
        this.solid = true;
        this.createdById = pl.id;
        while(this.collidesSolid(context, context.allSolids)){
            this.setY((int)this.Y +1);
        }
    }

    public void open(GameEngine context, int numCages){
        context.entityPool.add(new Wolf(this, numCages));
        context.entityPool.remove(this);
    }

    public Cage() {
        super(TeamAffiliation.UNAFFILIATED);
    }

    public UUID getCreatedById() {
        return createdById;
    }
}
