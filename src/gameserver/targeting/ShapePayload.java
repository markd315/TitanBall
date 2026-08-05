package gameserver.targeting;

import gameserver.engine.TeamAffiliation;
import gameserver.entity.Titan;
import org.joda.time.Instant;

import com.fasterxml.jackson.annotation.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ShapePayload  {
    private static final int COLLIDER_DISP_MS = 400;
    public int x, y, w, h;
    public double rot;
    public ShapeSelector type;
    private double[] xp;
    private double[] yp;
    protected double[] color = new double[4];
    public Instant dispUntil;
    public boolean disp;

    public ShapePayload() {}

    public void setColor(Titan caster) {
        if(caster.team == TeamAffiliation.HOME){
            setColor(0, 0, 1.0, 1.0); // Blue
            return;
        }
        if(caster.team == TeamAffiliation.AWAY){
            setColor(1.0, 1.0, 1.0, 1.0); // White
            return;
        }
        setColor(0.5, 0.5, 0.5, 1.0); // Gray
    }

    private void setColor(double r, double g, double b, double a){
        this.color = new double[]{r, g, b, a};
    }

    public enum ShapeSelector{
        RECT, TRI, ELLIPSE
    }

    public double[] getColorArray() {
        return color;
    }

    public void trigger() {
        disp = true;
        Instant now = Instant.now();
        dispUntil = now.plus(COLLIDER_DISP_MS);
    }

    public boolean checkDisp() {
        Instant currentTimestamp = Instant.now();
        if (currentTimestamp.isAfter(dispUntil)) {
            disp = false;
        }
        return disp;
    }
}
