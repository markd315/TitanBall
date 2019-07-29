package client;

import org.joda.time.Instant;
import gameserver.engine.GoalHoop;
import gameserver.engine.TeamAffiliation;

import java.awt.geom.Ellipse2D;

public class GoalSprite extends Ellipse2D.Double {
    public Instant nextAvailable;
    public boolean onCooldown, frozen;
    public TeamAffiliation team;

    public GoalSprite(GoalHoop payload, int camX, int camY){
        super(payload.x - camX, payload.y - camY, payload.w, payload.h);
        this.team = payload.team;
        this.nextAvailable = payload.nextAvailable;
        this.onCooldown = payload.onCooldown;
        this.frozen = payload.frozen;
    }

    public boolean checkReady() {
        Instant currentTimestamp = Instant.now();
        if (frozen) {
            if (currentTimestamp.plus(1000).isAfter(nextAvailable)) {
                frozen = false;
            }
        }
        if (onCooldown) {
            if (currentTimestamp.isAfter(nextAvailable)) {
                onCooldown = false;
            }
        }
        return !onCooldown;
    }
}
