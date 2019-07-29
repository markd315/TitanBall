package gameserver.entity.minions;


import gameserver.Game;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.Box;
import gameserver.entity.Collidable;

public class Wall extends gameserver.entity.Entity implements Collidable {

    public Wall(Game context, int x, int y) {
        this.team = TeamAffiliation.UNAFFILIATED;
        this.setX(x);
        this.setY(y);
        this.width = 30;
        this.height = 30;
        this.health = 20;
        this.maxHealth = 20;
        this.solid = true;
        while(this.collidesSolid(context, context.allSolids)){
            this.setY((int)this.Y +1);
        }
    }

    @Override
    public void triggerCollide(Game context, Box entity) {
        return; //impossible
    }

    public Wall(){
        super(TeamAffiliation.UNAFFILIATED);
    }
}

