package gameserver.entity;

import gameserver.engine.GameEngine;


import com.fasterxml.jackson.annotation.*;
import java.util.Optional;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Box extends Coordinates   {
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

    public boolean exists(gameserver.engine.CollisionMath.Bounds intersection){
        return intersection.width() > 0 &&
            intersection.height() > 0;
    }

    public boolean collidesSolid(GameEngine context, Box[] solids, double yd, double xd) {
        Optional<Box> tmp = collidesSolidWhich(context, solids, yd, xd);
        return tmp.isPresent();
    }

    public Optional<Box> collidesSolidWhich(GameEngine context, Box[] solids, double yd, double xd) {
        gameserver.engine.CollisionMath.Bounds cmpBounds;
        if (this instanceof Titan t && t.getType() == TitanType.GOALIE) {
            double xOffset = (this.width - context.GOALIE_SOLID_W) / 2.0;
            cmpBounds = new gameserver.engine.CollisionMath.Bounds(
                    this.X + xd + xOffset,
                    this.Y + yd,
                    context.GOALIE_SOLID_W,
                    context.GOALIE_SOLID_H
            );
        } else if (this instanceof Titan) {
            cmpBounds = new gameserver.engine.CollisionMath.Bounds(
                    this.X + xd + context.SPRITE_X_EMPTY/2.0,
                    this.Y + yd + context.SPRITE_Y_EMPTY/2.0,
                    this.width - context.SPRITE_X_EMPTY,
                    this.height - context.SPRITE_Y_EMPTY
            );
        } else {
            cmpBounds = new gameserver.engine.CollisionMath.Bounds(this.X + xd, this.Y + yd, this.width, this.height);
        }
        Optional<Box> ret = Optional.empty();
        if (solids == null) {
            return ret;
        }
        for (Box collCheck : solids) {
            if (collCheck != null && collCheck.id != this.id &&
                    (!(collCheck instanceof Entity) || (((Entity) collCheck).health > 0))) {
                
                gameserver.engine.CollisionMath.Bounds checkBounds;
                if (collCheck instanceof Titan tc && tc.getType() == TitanType.GOALIE) {
                    double xOffset = (collCheck.width - context.GOALIE_SOLID_W) / 2.0;
                    checkBounds = new gameserver.engine.CollisionMath.Bounds(
                            (int)collCheck.X + xOffset,
                            (int)collCheck.Y,
                            context.GOALIE_SOLID_W,
                            context.GOALIE_SOLID_H
                    );
                } else if (collCheck instanceof Titan) {
                    checkBounds = new gameserver.engine.CollisionMath.Bounds(
                            (int)collCheck.X + context.SPRITE_X_EMPTY/2.0,
                            (int)collCheck.Y + context.SPRITE_Y_EMPTY/2.0,
                            collCheck.width - context.SPRITE_X_EMPTY,
                            collCheck.height - context.SPRITE_Y_EMPTY);
                } else if (collCheck instanceof gameserver.entity.minions.Parapet p) {
                    if (this instanceof Titan t && t.team != p.team) {
                        checkBounds = p.getEnemySolidBounds();
                    } else {
                        checkBounds = new gameserver.engine.CollisionMath.Bounds((int)collCheck.X, (int)collCheck.Y, collCheck.width, collCheck.height);
                    }
                } else {
                    checkBounds = new gameserver.engine.CollisionMath.Bounds((int)collCheck.X, (int)collCheck.Y, collCheck.width, collCheck.height);
                }

                if (cmpBounds.intersects(checkBounds)) {
                    if (performIntersection(context, collCheck)){
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
        if (collCheck instanceof gameserver.entity.minions.Parapet p) {
            if (this instanceof Titan t && t.team != p.team) {
                return true;
            }
            return false;
        }
        if(collCheck.solid) {
            return true;
        }
        return false;
    }

    public boolean intersectCircle(double x2, double y2, double r2) {
        double centerX = this.X + this.width/2.0;
        double centerY = this.Y + this.height/2.0;
        double distSq = (centerX - x2) * (centerX - x2) +
                (centerY - y2) * (centerY - y2);
        double totalRadius = r2 + (this.width / 2.0);
        return distSq <= (totalRadius * totalRadius);
    }

    public gameserver.engine.CollisionMath.Bounds asBounds() {
        return new gameserver.engine.CollisionMath.Bounds((int)this.X, (int)this.Y, this.width, this.height);
    }

    public boolean ballNearestEdgeisX(Box ball) {
        return ballNearestEdgeisX(ball, 0, 0);
    }

    public boolean ballNearestEdgeisX(Box ball, double dx, double dy) {
        gameserver.engine.CollisionMath.Bounds ballBounds = ball.asBounds();
        gameserver.engine.CollisionMath.Bounds wallBounds = this.asBounds();
        gameserver.engine.CollisionMath.CollisionSide side = gameserver.engine.CollisionMath.getCollisionSide(ballBounds, wallBounds, dx, dy);
        return side == gameserver.engine.CollisionMath.CollisionSide.LEFT || side == gameserver.engine.CollisionMath.CollisionSide.RIGHT;
    }

    public gameserver.engine.CollisionMath.EllipseData ellipseData() {
        return new gameserver.engine.CollisionMath.EllipseData(this.X + (double) this.width /2, this.Y + (double) this.height /2, (double) this.width / 2, (double) this.height / 2);
    }
}