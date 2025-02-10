package client.graphical;

import javafx.geometry.Bounds;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.shape.Ellipse;
import org.joda.time.Instant;
import gameserver.engine.GoalHoop;
import gameserver.engine.TeamAffiliation;

import java.io.Serializable;


public class GoalSprite implements Serializable {
    public Instant nextAvailable;
    public boolean onCooldown, frozen;
    public TeamAffiliation team;
    private int cx;
    private int cy;
    private int radX;
    private int radY;

    public GoalSprite(GoalHoop payload, int camX, int camY, ScreenConst sconst){
        setCy(sconst.adjY(payload.y - camY));
        setCx((sconst.adjX(payload.x - camX)));
        setRadX(sconst.adjX(payload.w));
        setRadY(sconst.adjY(payload.h));
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
        Ellipse ellipse = new Ellipse(cx, cy, radX, radY);
        gc.strokeOval(ellipse.getCenterX(), ellipse.getCenterY(), ellipse.getRadiusX(), ellipse.getRadiusY());
    }

    public int getCx() {
        return cx;
    }

    public void setCx(int cx) {
        this.cx = cx;
    }

    public int getCy() {
        return cy;
    }

    public void setCy(int cy) {
        this.cy = cy;
    }

    public int getRadX() {
        return radX;
    }

    public void setRadX(int radX) {
        this.radX = radX;
    }

    public int getRadY() {
        return radY;
    }

    public void setRadY(int radY) {
        this.radY = radY;
    }

    public boolean intersects(Bounds ballBounds) {
        Ellipse ellipse = new Ellipse(cx, cy, radX, radY);
        return ballBounds.intersects(ellipse.getBoundsInLocal());
    }
}
