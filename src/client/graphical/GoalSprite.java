package client.graphical;

import gameserver.entity.Box;
import gameserver.entity.Entity;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.shape.Ellipse;
import org.joda.time.Instant;
import gameserver.engine.GoalHoop;
import gameserver.engine.TeamAffiliation;


public class GoalSprite extends Ellipse {
    public Instant nextAvailable;
    public boolean onCooldown, frozen;
    public TeamAffiliation team;

    public GoalSprite(GoalHoop payload, int camX, int camY, ScreenConst sconst){
        super(payload.x - camX, payload.y - camY, payload.w, payload.h);
        super.setCenterY(sconst.adjY(payload.y - camY));
        super.setCenterX(sconst.adjX(payload.x - camX));
        super.setRadiusX(sconst.adjX(payload.w));
        super.setRadiusY(sconst.adjY(payload.h));
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

    public void draw(GraphicsContext gc) {
        //draw an ellipse, assumes color preset
        gc.strokeOval(super.getCenterX(), super.getCenterY(), super.getRadiusX(), super.getRadiusY());
    }

}
