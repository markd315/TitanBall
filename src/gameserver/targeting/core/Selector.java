package gameserver.targeting.core;


import com.esotericsoftware.kryo.Kryo;
import gameserver.entity.Entity;
import gameserver.entity.Titan;
import gameserver.targeting.SelectorOffset;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;
import util.Util;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

public class Selector  implements Serializable {
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
    public Set<Entity> select(Set<Entity> input, int mX, int mY, Entity casting){
        Set<Entity> ret = new HashSet<>();

        double centerX = casting.X + casting.width/ 2;
        double centerY = casting.Y + casting.height/ 2;
        double attemptedRange = new Point2D(mX, mY).distance(new Point2D(centerX, centerY));
        if(attemptedRange > offsetRange && offset != SelectorOffset.CAST_CENTER){
            System.out.println("Aimed too far, handle later");
            return ret;
        }
        double shapeAngle = getMouseAngleRadians(mX, mY, casting);
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
                shapeAngle = 0.0;
                break;
        }
        Shape shape = new Kryo().copy(sizeDef);

        Bounds bounds = shape.getBoundsInLocal();
        double shapeCenterX = bounds.getMinX() + bounds.getWidth() / 2;
        double shapeCenterY = bounds.getMinY() + bounds.getHeight() / 2;

        Affine transform = new Affine();
        transform.append(new Translate(centerX - shapeCenterX, centerY - shapeCenterY));
        transform.append(new Rotate(Math.toDegrees(shapeAngle), centerX, centerY));
        shape.getTransforms().add(transform);

        // Set the latest collider
        latestCollider = shape;

        //System.out.println("rect " + shape + shape.getBounds().toString());
        for(Entity e : input){
            //System.out.println("" + e.team + e.health);
            if(collide(e, shape)){
                //System.out.println("adding^");
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
        Rectangle r = new Rectangle((int)entity.X, (int)entity.Y, entity.width, entity.height);

        if(entity instanceof Titan){
            r = new Rectangle((int)entity.X + 15, (int)entity.Y + 5, entity.width - 30, entity.height - 10);
        }
        Shape intersection = Shape.intersect(r, s);
        return intersection.getBoundsInLocal().getWidth() > 0 && intersection.getBoundsInLocal().getHeight() > 0;
    }

    public Rectangle getLatestColliderBounds() {
        if (latestCollider == null) {
            return new Rectangle(99999, 9999, 0, 0);
        }
        Bounds bounds = latestCollider.getBoundsInLocal();
        return new Rectangle(bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight());
    }

    public Ellipse getLatestColliderCircle() {
        if (!(latestCollider instanceof Ellipse)) {
            return new Ellipse(99999, 9999, 0, 0);
        }
        return (Ellipse) latestCollider;
    }
}
