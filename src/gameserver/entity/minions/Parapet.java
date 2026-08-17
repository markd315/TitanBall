package gameserver.entity.minions;

import gameserver.engine.TeamAffiliation;
import gameserver.entity.Entity;
import gameserver.entity.Titan;

import java.io.Serializable;

public class Parapet extends Entity implements Serializable {
    public Parapet(TeamAffiliation team, Titan goalie, int x, int y) {
        super(team);
        this.setX(x);
        this.setY(y);
        this.width = 100;
        this.height = 100;
        this.health = 99999;
        this.maxHealth = 99999;
        this.solid = false;
    }

    public Parapet() {
        super(TeamAffiliation.UNAFFILIATED);
    }
}
