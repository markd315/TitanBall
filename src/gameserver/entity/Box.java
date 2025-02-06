package gameserver.entity;

import gameserver.engine.GameEngine;
import javafx.geometry.Bounds;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;

import java.io.Serializable;
import java.util.Optional;
import java.util.UUID;

public class Box extends Coordinates  implements Serializable {
    public int width, height;
    public boolean solid;
    public UUID id;
    public int centerDist;

    public Box(int x, int y, int w, int h) {
        super(x, y);
        this.width=w;
        this.height=h;
        this.centerDist=(w+h)/4;//assumes symmetrical dimensions
        id = UUID.randomUUID();
        solid = false;
    }

    public Box(){id = UUID.randomUUID();}

    public boolean collidesSolid(GameEngine context, Entity[] solids) {
        return collidesSolid(context, solids, 0, 0);
    }

    public Optional<Box> collidesSolidWhich(GameEngine context, Entity[] solids) {
        return collidesSolidWhich(context, solids, 0, 0);
    }

    public boolean exists(Shape intersection){
        return intersection.getBoundsInLocal().getWidth() > 0&&
            intersection.getBoundsInLocal().getHeight() > 0;
    }


    public boolean collidesAny(GameEngine context, Box[] boxes, int yd, int xd){
        Shape cmp = new Rectangle((int)this.X + xd, (int)this.Y + yd, this.width, this.height);
        if (this instanceof Titan) {
            cmp = new Rectangle((int)(this.X + xd + context.SPRITE_X_EMPTY/2), (int)(this.Y + yd + context.SPRITE_Y_EMPTY/2),
                    this.width - context.SPRITE_X_EMPTY, this.height - context.SPRITE_Y_EMPTY);
        }
        boolean ret = false;
        for (Box collCheck : boxes) {
            if (collCheck.id != this.id &&
                    (!(collCheck instanceof Entity) || (((Entity) collCheck).health > 0))) {
                if (collCheck instanceof Titan) {
                    //Titans don't take up their full hitboxes. Mostly.
                    //It's twice as much because we only adjust the "collCheck" collider
                    Rectangle inter = new Rectangle((int)collCheck.X + context.SPRITE_X_EMPTY/2,
                            (int)collCheck.Y + context.SPRITE_Y_EMPTY/2,
                            collCheck.width - context.SPRITE_X_EMPTY,
                            collCheck.height - context.SPRITE_Y_EMPTY
                    );
                    if (exists(Shape.intersect(inter, cmp))) {
                        return true;
                    }
                } else if (exists(Shape.intersect(
                        new Rectangle((int)collCheck.X, (int)collCheck.Y, collCheck.width, collCheck.height),
                        cmp
                ))) {
                    return true;
                }

            }
        }
        return ret;
    }

    public boolean collidesSolid(GameEngine context, Box[] solids, double yd, double xd) {
        Optional<Box> tmp = collidesSolidWhich(context, solids, yd, xd);
        return tmp.isPresent();
    }

    public Optional<Box> collidesSolidWhich(GameEngine context, Box[] solids, double yd, double xd) {
        Shape cmp = new Rectangle(this.X + xd, this.Y + yd, this.width, this.height);
        if (this instanceof Titan) {
            cmp = new Ellipse(this.X + xd + context.SPRITE_X_EMPTY/2, this.Y + yd + context.SPRITE_Y_EMPTY/2,
                    this.width - context.SPRITE_X_EMPTY, this.height - context.SPRITE_Y_EMPTY);
        }
        Optional<Box> ret = Optional.empty();
        for (Box collCheck : solids) {
            if (collCheck != null && collCheck.id != this.id &&
                    (!(collCheck instanceof Entity) || (((Entity) collCheck).health > 0))) {
                if (collCheck instanceof Titan) {
                    //Titans don't take up their full sprite boxes. Mostly.
                    //It's twice as much because we only adjust the "collCheck" collider

                    Rectangle inter = new Rectangle(
                            (int)collCheck.X + context.SPRITE_X_EMPTY/2,
                            (int)collCheck.Y + context.SPRITE_Y_EMPTY/2,
                            collCheck.width - context.SPRITE_X_EMPTY,
                            collCheck.height - context.SPRITE_Y_EMPTY);
                    if (exists(Shape.intersect(inter, cmp))) {
                        if (performIntersection(context, collCheck)){
                            return Optional.of(collCheck);
                        }
                    }
                } else if (cmp.intersects(new Rectangle((int)collCheck.X, (int)collCheck.Y, collCheck.width, collCheck.height).getBoundsInLocal())) {
                    if(performIntersection(context, collCheck)){
                        return Optional.of(collCheck);
                    }
                }
            }
        }
        return ret;
    }

    private boolean performIntersection(GameEngine context, Box collCheck){
        if (collCheck instanceof Collidable) {
            Collidable c = (Collidable) collCheck;
            c.triggerCollide(context, this);
        }
        if(collCheck.solid) {
            return true;
        }
        return false;
    }

    public boolean intersectCircle(double x2, double y2, double r2)
    {
        double r1 = (this.width + this.height) / 4.0;
        double centerX = (- this.width/2.0) + this.X;
        double centerY = (- this.height/2.0) + this.Y;
        double distSq = (centerX - x2) * (centerX - x2) +
                (centerY - y2) * (centerY - y2);
        double radSumSq = (r1 + r2) * (r1 + r2);
        if (distSq >= radSumSq)
            return false;
        else
            return true;
    }

    public Bounds asBounds() {
        return new Rectangle((int)this.X, (int)this.Y, this.width, this.height).getBoundsInLocal();
    }

    public boolean ballNearestEdgeisX(Box ball) {
        double x1 = Math.abs(ball.X - this.X);
        double x2 = Math.abs((ball.X + ball.width ) - (this.X + this.width));
        double y1 = Math.abs(ball.Y - this.Y);
        double y2 = Math.abs((ball.Y + ball.height ) - (this.Y + this.height));
        return (x1 < y1 && x1 < y2) || (x2 < y1 && x2 < y2);
    }
}