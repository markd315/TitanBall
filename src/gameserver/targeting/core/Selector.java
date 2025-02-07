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

public class Selector implements Serializable {
    // Region-based selection of entities
    public Shape sizeDef, latestCollider;
    SelectorOffset offset;
    int offsetRange; // Applies to mouse-center and cast-to-mouse

    public Selector(Shape shape, SelectorOffset offset, int offsetRange) {
        this.sizeDef = shape;
        this.offset = offset;
        this.offsetRange = offsetRange;
    }

    public Set<Entity> select(Set<Entity> input, int mX, int mY, Entity casting) {
        Set<Entity> ret = new HashSet<>();

        double centerX = casting.X + casting.width / 2;
        double centerY = casting.Y + casting.height / 2;
        double attemptedRange = new Point2D(mX, mY).distance(new Point2D(centerX, centerY));

        System.out.println("Selecting entities...");
        System.out.println("Mouse: (" + mX + ", " + mY + ") | Caster: (" + centerX + ", " + centerY + ")");
        System.out.println("Attempted range: " + attemptedRange + ", Max range: " + offsetRange);

        if (attemptedRange > offsetRange && offset != SelectorOffset.CAST_CENTER) {
            System.out.println("Aimed too far, ignoring selection.");
            return ret;
        }

        double shapeAngle = getMouseAngleRadians(mX, mY, casting);
        switch (offset) {
            case CAST_TO_MOUSE:
                centerX = (mX + (int) casting.X) / 2;
                centerY = (mY + (int) casting.Y) / 2;
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

        System.out.println("casting " + sizeDef);
        Shape shape = Shape.union(sizeDef, new Rectangle(0, 0)); // Creates a copy
        System.out.println("Shape original bounds: " + shape.getBoundsInLocal());

                // Get shape bounds before transformation
        Bounds originalBounds = shape.getBoundsInLocal();
        double shapeCenterX = originalBounds.getMinX() + originalBounds.getWidth() / 2;
        double shapeCenterY = originalBounds.getMinY() + originalBounds.getHeight() / 2;

        // Apply transform to move and rotate the shape
        Affine transform = new Affine();
        transform.append(new Translate(centerX - shapeCenterX, centerY - shapeCenterY));
        transform.append(new Rotate(Math.toDegrees(shapeAngle), centerX, centerY));

        shape.getTransforms().add(transform);

        // Force shape transformation to persist
        latestCollider = Shape.union(shape, shape);  // Forces recalculation

        // Debug logging
        Bounds transformedBounds = latestCollider.localToScene(latestCollider.getBoundsInLocal());
        System.out.println("Transformed shape: " + latestCollider);
        System.out.println("Transformed shape bounds: " + transformedBounds);

        latestCollider = shape;

        // Entity collision check
        for (Entity e : input) {
            System.out.println("Checking collision with Entity at (" + e.X + ", " + e.Y + ")...");
            if (collide(e, transformedBounds)) {
                System.out.println("Entity selected!");
                ret.add(e);
            } else {
                System.out.println("No collision.");
            }
        }
        return ret;
    }

    private double getMouseAngleRadians(int mX, int mY, Entity casting) {
        int xLoc = (int) casting.X + (casting.width / 2);
        int yLoc = (int) casting.Y + (casting.height / 2);
        int xClick = (mX - xLoc);
        int yClick = (-1 * (mY - yLoc));
        double theta = Util.degreesFromCoords(xClick, yClick);
        return Math.toRadians(theta);
    }

    private boolean collide(Entity entity, Bounds shapeBounds) {
        Rectangle r = new Rectangle((int) entity.X, (int) entity.Y, entity.width, entity.height);

        if (entity instanceof Titan) {
            r = new Rectangle((int) entity.X + 15, (int) entity.Y + 5, entity.width - 30, entity.height - 10);
        }

        Bounds entityBounds = r.getBoundsInLocal();
        boolean collides = entityBounds.intersects(shapeBounds);

        System.out.println("Collision check: Entity Bounds: " + entityBounds +
                " | Selector Bounds: " + shapeBounds +
                " | Result: " + collides);

        return collides;
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
