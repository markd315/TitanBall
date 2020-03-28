package gameserver.entity.minions;


import gameserver.engine.GameEngine;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.Box;
import gameserver.entity.Collidable;

public class Wall extends gameserver.entity.Entity implements Collidable {

    public Wall(GameEngine context, int x, int y) {
        this.team = TeamAffiliation.UNAFFILIATED;
        this.setX(x);
        this.setY(y);
        this.width = 12;
        this.height = 120;
        this.health = 4;
        this.maxHealth = 4;
        this.solid = true;
        while(this.collidesSolid(context, context.allSolids)){
            this.setY((int)this.Y +1);
        }
    }

    @Override
    public void triggerCollide(GameEngine context, Box entity) {
        return; //impossible
    }

    public Wall(){
        super(TeamAffiliation.UNAFFILIATED);
    }
}

