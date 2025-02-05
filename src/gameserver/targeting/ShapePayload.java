package gameserver.targeting;

import client.graphical.ScreenConst;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.Titan;
import javafx.scene.paint.Color;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import org.joda.time.Instant;

import java.io.Serializable;

public class ShapePayload  implements Serializable {
    private static final int COLLIDER_DISP_MS = 400;
    int x, y, w, h;
    double rot;
    ShapePayload.ShapeSelector type;
    private double[] xp;
    private double[] yp;
    protected double[] color = new double[4];
    public Instant dispUntil;
    public boolean disp;

    public ShapePayload(){ }
    public ShapePayload(Shape s){
        this(s, 0.0);
    }


    public ShapePayload(Shape s, double rot) {
        if (s instanceof Ellipse) {
            this.type = ShapeSelector.ELLIPSE;
            Ellipse ellipse = (Ellipse) s;
            this.x = (int) (ellipse.getCenterX() - ellipse.getRadiusX());
            this.y = (int) (ellipse.getCenterY() - ellipse.getRadiusY());
            this.h = (int) (ellipse.getRadiusY() * 2);
            this.w = (int) (ellipse.getRadiusX() * 2);
        } else if (s instanceof Polygon) {
            this.type = ShapeSelector.TRI;
            Polygon poly = (Polygon) s;
            this.xp = poly.getPoints().stream().filter(i -> poly.getPoints().indexOf(i) % 2 == 0).mapToDouble(i -> i).toArray();
            this.yp = poly.getPoints().stream().filter(i -> poly.getPoints().indexOf(i) % 2 != 0).mapToDouble(i -> i).toArray();
        } else if (s instanceof Rectangle) {
            this.type = ShapeSelector.RECT;
            Rectangle rect = (Rectangle) s;
            this.x = (int) rect.getX();
            this.y = (int) rect.getY();
            this.h = (int) rect.getHeight();
            this.w = (int) rect.getWidth();
        }

        this.rot = rot;
        trigger();
    }

    public Shape from() {
    if (this.type == ShapeSelector.ELLIPSE) {
        return new Ellipse(this.x + this.w / 2, this.y + this.h / 2, this.w / 2, this.h / 2);
    }
    if (this.type == ShapeSelector.TRI) {
        Polygon polygon = new Polygon();
        for (int i = 0; i < this.xp.length; i++) {
            polygon.getPoints().addAll(this.xp[i], this.yp[i]);
        }
        return polygon;
    }
    return new Rectangle(this.x, this.y, this.w, this.h);
}

    public Shape fromWithCamera(int camX, int camY, ScreenConst sconst) {
        if(this.type == ShapeSelector.ELLIPSE){
            double xt = sconst.adjX(x - camX);
            double wt = sconst.adjX(w);
            double yt = sconst.adjY(y - camY);
            double ht = sconst.adjY(h);
            return new Ellipse(xt, yt, wt, ht);
        }
        if(this.type == ShapeSelector.TRI){
            Polygon polygon = new Polygon();
            for (int i = 0; i < this.xp.length; i++) {
                polygon.getPoints().addAll(this.xp[i], this.yp[i]);
            }
            return polygon;
        }
        int xt = sconst.adjX(x - camX);
        int wt = sconst.adjX(w);
        int yt = sconst.adjY(y - camY);
        int ht = sconst.adjY(h);
        return new Rectangle(xt, yt, wt, ht);
    }

    public void setColor(Titan caster) {
        if(caster.team == TeamAffiliation.HOME){
            setColor(Color.BLUE);
            return;
        }
        if(caster.team == TeamAffiliation.AWAY){
            setColor(Color.WHITE);
            return;
        }
        setColor(Color.GRAY);
    }

    private void setColor(Color color){
        this.color = new double[4];
        this.color[0] = color.getRed()/256.0;
        this.color[1] = color.getGreen()/256.0;
        this.color[2] = color.getBlue()/256.0;
        this.color[3] = color.getOpacity()/256.0;
    }

    public enum ShapeSelector{
        RECT, TRI, ELLIPSE
    }

    public javafx.scene.paint.Paint getColor() {
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
