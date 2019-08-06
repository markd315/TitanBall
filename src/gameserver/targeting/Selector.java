package gameserver.targeting;


import com.esotericsoftware.kryo.Kryo;
import gameserver.entity.Entity;
import util.Util;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.util.HashSet;
import java.util.Set;

public class Selector {
    //Region-based selection of entities
    public Shape sizeDef, latestCollider;
    //sizeDef does not have updated cast info, and is a prototype
    SelectorOffset offset;
    int offsetRange; //Applies to mouse-center and cast-to-mouse

    public Selector(Shape shape, SelectorOffset offset, int offsetRange) {
        this.sizeDef = shape;
        this.offset = offset;
        this.offsetRange = offsetRange;
    }

    //TODO assumes mX and mY are camera-adjusted
    public Set<Entity> select(Set<Entity> set, int mX, int mY, Entity casting){
        Set<Entity> ret = new HashSet<>();

        double attemptedRange = Point2D.distance(mX, mY, casting.X + casting.width/2, casting.Y + casting.height/2);
        if(attemptedRange > offsetRange && offset != SelectorOffset.CAST_CENTER){
            System.out.println("Aimed too far, handle later");
            return ret;
        }
        double mouseAngle, shapeAngle = getMouseAngleRadians(mX, mY, casting);
        int centerX, centerY;
        int xLoc = (int)casting.X + (casting.width /2);
        int yLoc = (int)casting.Y + (casting.height /2);
        switch(offset){
            case CAST_TO_MOUSE:
                centerX = (mX + (int)casting.X)/2;
                centerY = (mY + (int)casting.Y)/2;
                break;
            case MOUSE_CENTER:
                centerX = mX;
                centerY = mY;
                shapeAngle = 0.0;
                break;
            case CAST_CENTER:
            default:
                centerX = xLoc;
                centerY = yLoc;
                shapeAngle = 0.0;
                break;

        }
        Shape shape = new Kryo().copy(sizeDef);
        AffineTransform rot = new AffineTransform();
        rot.translate(centerX - shape.getBounds().width / 2, centerY - shape.getBounds().height / 2);
        rot.rotate(shapeAngle); //Defaults to mouse angle unless it was changed
        shape = rot.createTransformedShape(shape);
        latestCollider = shape;

        //System.out.println("rect " + shape + shape.getBounds().toString());
        for(Entity e : set){
            if(collide(e, shape) && !casting.id.equals(e.id)){
                ret.add(e);
            }
        }
        return ret;
    }

    private double getMouseAngleRadians(int mX, int mY, Entity casting) {
        int xLoc = (int)casting.X + (casting.width /2);
        int yLoc = (int)casting.Y + (casting.height /2);
        int xClick = ((mX - xLoc));
        int yClick = (-1 * ((mY - yLoc)));
        double theta = Util.degreesFromCoords(xClick, yClick);
        return Math.toRadians(theta);
    }

    private boolean collide(Entity entity, Shape s) {
        Rectangle r = new Rectangle((int)entity.X + 15, (int)entity.Y + 5, entity.width - 30, entity.height - 10);
        //System.out.println("" + entity.X + " " + entity.Y + " " + entity.width + " " + entity.height);
        //System.out.println("" + s.getBounds().getX()+ " " + s.getBounds().getY()+ " " + s.getBounds().height+ " " + s.getBounds().width);
        return s.intersects(r) || s.contains(r);
    }

    public Rectangle getLatestColliderBounds() {
        if(latestCollider == null){
            return new Rectangle(99999,9999,0,0);
        }
        return latestCollider.getBounds();
    }

    public Ellipse2D.Double getLatestColliderCircle() {
        if(latestCollider == null){
            return new Ellipse2D.Double(99999,9999,0,0);
        }
        Rectangle r = latestCollider.getBounds();
        return new Ellipse2D.Double(r.x, r.y, r.width, r.height);
    }
}
