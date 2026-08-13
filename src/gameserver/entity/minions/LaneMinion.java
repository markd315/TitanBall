package gameserver.entity.minions;

import gameserver.engine.GameEngine;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.Entity;

import java.io.Serializable;

public class LaneMinion extends Entity implements Tickable, Serializable {
    public int laneIndex;
    public double damageMultiplier = 1.0;

    public LaneMinion() {
        super();
    }

    public LaneMinion(double x, double y, TeamAffiliation team, int laneIndex) {
        super(team);
        this.X = x;
        this.Y = y;
        this.width = 20;
        this.height = 20;
        this.laneIndex = laneIndex;
        this.health = 45.0;
        this.maxHealth = 45.0;
        this.solid = false;
    }

    @Override
    public void tick(GameEngine context) {
        // Handled in GameEngine.tickLaneMinions()
    }
}
