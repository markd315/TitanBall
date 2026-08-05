package gameserver.targeting.core;

import gameserver.entity.Entity;
import gameserver.entity.Titan;
import gameserver.targeting.SelectorOffset;
import gameserver.engine.CollisionMath;
import util.Util;

import com.fasterxml.jackson.annotation.*;
import java.util.HashSet;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Selector  {
    // Region-based selection of entities
    public CollisionMath.Bounds sizeDef, latestCollider;
    SelectorOffset offset;
    int offsetRange; // Applies to mouse-center and cast-to-mouse

    public Selector(CollisionMath.Bounds shape, SelectorOffset offset, int offsetRange) {
        this.sizeDef = shape;
        this.offset = offset;
        this.offsetRange = offsetRange;
    }

    public Set<Entity> select(Set<Entity> input, int mX, int mY, Entity casting) {
        Set<Entity> ret = new HashSet<>();
        // Removed transformation/rotation logic for simplicity to get rid of JavaFX.
        
        latestCollider = new CollisionMath.Bounds(mX, mY, sizeDef.width(), sizeDef.height());
        for (Entity e : input) {
            if (collide(e, latestCollider)) {
                ret.add(e);
            }
        }
        return ret;
    }

    private boolean collide(Entity entity, CollisionMath.Bounds shapeBounds) {
        CollisionMath.Bounds r = new CollisionMath.Bounds((int) entity.X, (int) entity.Y, entity.width, entity.height);

        if (entity instanceof Titan) {
            r = new CollisionMath.Bounds((int) entity.X + 15, (int) entity.Y + 5, entity.width - 30, entity.height - 10);
        }

        return r.intersects(shapeBounds);
    }

    public CollisionMath.Bounds getLatestColliderBounds() {
        if (latestCollider == null) {
            return new CollisionMath.Bounds(99999, 9999, 0, 0);
        }
        return latestCollider;
    }
}
