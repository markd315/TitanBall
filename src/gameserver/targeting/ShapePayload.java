package gameserver.targeting;

import client.graphical.ScreenConst;
import com.fasterxml.jackson.annotation.JsonProperty;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.Titan;
import org.joda.time.Instant;

import java.awt.*;
import java.awt.geom.Ellipse2D;

public class ShapePayload {
    private static final int COLLIDER_DISP_MS = 400;
    @JsonProperty
    int x, y, w, h;
    @JsonProperty
    double rot;
    @JsonProperty
    ShapeSelector type;
    @JsonProperty
    private int[] xp, yp;
    @JsonProperty
    protected float[] color = new float[4];
    @JsonProperty
    public Instant dispUntil;
    @JsonProperty
    public boolean disp;

    public ShapePayload(){ }
    public ShapePayload(Shape s){
        this(s, 0.0);
    }

    public ShapePayload(Shape s, double rot){
        if(s instanceof Ellipse2D.Double){
            this.type = ShapeSelector.ELLIPSE;
        }
        else if(s instanceof Polygon){
            this.type = ShapeSelector.TRI;
            Polygon poly = (Polygon) s;
            this.xp = poly.xpoints;
            this.yp = poly.ypoints;
        }
        else if(s instanceof Rectangle){
            this.type = ShapeSelector.RECT;
        }
        this.x = (int) s.getBounds().getX();
        this.y = (int) s.getBounds().getY();
        this.h = s.getBounds().height;
        this.w = s.getBounds().width;
        this.rot = rot;
        trigger();
    }

    public Shape from(){
        if(this.type == ShapeSelector.ELLIPSE){
            return new Ellipse2D.Double(this.x, this.y, this.w, this.h);
        }
        if(this.type == ShapeSelector.TRI){
            return new Polygon(this.xp, this.yp, 3);
        }
        return new Rectangle(this.x, this.y, this.w, this.h);

    }

    public Shape fromWithCamera(int camX, int camY, ScreenConst sconst) {
        if(this.type == ShapeSelector.ELLIPSE){
            double xt = sconst.adjX(x - camX);
            double wt = sconst.adjX(w);
            double yt = sconst.adjY(y - camY);
            double ht = sconst.adjY(h);
            return new Ellipse2D.Double(xt, yt, wt, ht);
        }
        if(this.type == ShapeSelector.TRI){
            for(int i=0; i<xp.length; i++){
                this.xp[i] = (int) sconst.adjX(this.xp[i] - camX);
                this.yp[i] = sconst.adjY(this.xp[i] - camY);
            }
            return new Polygon(this.xp, this.yp, 3);
        }
        int xt = (int) sconst.adjX(x - camX);
        int wt = (int) sconst.adjX(w);
        int yt = sconst.adjY(y - camY);
        int ht = sconst.adjY(h);
        return new Rectangle(xt, yt, wt, ht);
    }

    public void setColor(Titan caster) {
        if(caster.team == TeamAffiliation.HOME){
            setColor(Color.blue);
            return;
        }
        if(caster.team == TeamAffiliation.AWAY){
            setColor(Color.white);
            return;
        }
        setColor(Color.gray);
    }

    private void setColor(Color color){
        this.color = new float[4];
        this.color[0] = color.getRed()/256.0f;
        this.color[1] = color.getGreen()/256.0f;
        this.color[2] = color.getBlue()/256.0f;
        this.color[3] = color.getAlpha()/256.0f;
    }

    public enum ShapeSelector{
        RECT, TRI, ELLIPSE
    }

    public Color getColor() {
        return new Color(color[0], color[1], color[2], color[3]);
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
