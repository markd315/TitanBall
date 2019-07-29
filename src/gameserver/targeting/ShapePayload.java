package gameserver.targeting;

import java.awt.*;
import java.awt.geom.Ellipse2D;

public class ShapePayload {
    int x, y, w, h;
    double rot;
    ShapeSelector type;
    private int[] xp, yp;

    public ShapePayload(){}
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

    public Shape fromWithCamera(int camX, int camY) {
        if(this.type == ShapeSelector.ELLIPSE){
            return new Ellipse2D.Double(this.x - camX, this.y - camY, this.w, this.h);
        }
        if(this.type == ShapeSelector.TRI){
            for(int i=0; i<xp.length; i++){
                this.xp[i] -= camX;
                this.yp[i] -= camY;
            }
            return new Polygon(this.xp, this.yp, 3);
        }
        return new Rectangle(this.x - camX, this.y - camY, this.w, this.h);
    }

    public enum ShapeSelector{
        RECT, TRI, ELLIPSE
    }
}
