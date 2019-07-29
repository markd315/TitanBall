package gameserver.entity;

import gameserver.GameEngine;

import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.util.UUID;

public class Box extends Coordinates{
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

    public boolean collidesAny(GameEngine context, Box[] boxes, int yd, int xd){
        Shape cmp = new Rectangle((int)this.X + xd, (int)this.Y + yd, this.width, this.height);
        if (this instanceof Titan) {
            cmp = new Rectangle((int)(this.X + xd + GameEngine.SPRITE_X_EMPTY/2), (int)(this.Y + yd + GameEngine.SPRITE_Y_EMPTY/2),
                    this.width - GameEngine.SPRITE_X_EMPTY, this.height - GameEngine.SPRITE_Y_EMPTY);
        }
        boolean ret = false;
        for (Box collCheck : boxes) {
            if (collCheck.id != this.id &&
                    (!(collCheck instanceof Entity) || (((Entity) collCheck).health > 0))) {
                if (collCheck instanceof Titan) {
                    //Titans don't take up their full hitboxes. Mostly.
                    //It's twice as much because we only adjust the "collCheck" collider
                    Area inter = new Area(new Rectangle((int)collCheck.X + GameEngine.SPRITE_X_EMPTY/2, (int)collCheck.Y + GameEngine.SPRITE_Y_EMPTY/2,
                            collCheck.width - GameEngine.SPRITE_X_EMPTY, collCheck.height - GameEngine.SPRITE_Y_EMPTY));
                    Area cmpa = new Area(cmp);
                    inter.intersect(cmpa);
                    if (!inter.isEmpty()) {
                        return true;
                    }
                } else if (cmp.intersects(new Rectangle((int)collCheck.X, (int)collCheck.Y, collCheck.width, collCheck.height))) {
                    return true;
                }

            }
        }
        return ret;
    }

    public boolean collidesSolid(GameEngine context, Box[] solids, int yd, int xd) {
        Shape cmp = new Rectangle((int)this.X + xd, (int)this.Y + yd, this.width, this.height);
        if (this instanceof Titan) {
            cmp = new Ellipse2D.Double(this.X + xd + GameEngine.SPRITE_X_EMPTY/2, this.Y + yd + GameEngine.SPRITE_Y_EMPTY/2,
                    this.width - GameEngine.SPRITE_X_EMPTY, this.height - GameEngine.SPRITE_Y_EMPTY);
        }
        boolean ret = false;
        for (Box collCheck : solids) {
            if (collCheck.id != this.id &&
                    (!(collCheck instanceof Entity) || (((Entity) collCheck).health > 0))) {
                if (collCheck instanceof Titan) {
                    //Titans don't take up their full hitboxes. Mostly.
                    //It's twice as much because we only adjust the "collCheck" collider
                    Area inter = new Area(new Rectangle((int)collCheck.X + GameEngine.SPRITE_X_EMPTY/2, (int)collCheck.Y + GameEngine.SPRITE_Y_EMPTY/2,
                            collCheck.width - GameEngine.SPRITE_X_EMPTY, collCheck.height - GameEngine.SPRITE_Y_EMPTY));
                    Area cmpa = new Area(cmp);
                    inter.intersect(cmpa);
                    if (!inter.isEmpty()) {
                        return performIntersection(context, collCheck);
                    }
                } else if (cmp.intersects(new Rectangle((int)collCheck.X, (int)collCheck.Y, collCheck.width, collCheck.height))) {
                    return performIntersection(context, collCheck);
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

    public Rectangle2D asRect() {
        return new Rectangle((int)this.X, (int)this.Y, this.width, this.height);
    }
}
