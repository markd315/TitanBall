package gameserver.engine;

import gameserver.entity.Titan;

/**
 * Handles spatial positioning, boundary validation, redzone exclusion,
 * and safe unoccupied collision resolution for abilities.
 */
public class AbilityPositionHelper {

    public static boolean inBoundsNotRedzone(CollisionMath.Bounds corners, GameEngine context) {
        CollisionMath.Bounds goalH = new CollisionMath.Bounds(context.c.GOALIE_XH_MIN + 50,
                (context.c.GOALIE_Y_MIN + 24),
                context.c.GOALIE_XH_MAX - context.c.GOALIE_XH_MIN,
                context.c.GOALIE_Y_MAX - (context.c.GOALIE_Y_MIN) + 10);
        CollisionMath.Bounds goalA = new CollisionMath.Bounds(context.c.GOALIE_XA_MIN - 4,
                (context.c.GOALIE_Y_MIN + 24),
                context.c.GOALIE_XA_MAX - context.c.GOALIE_XA_MIN + 29,
                context.c.GOALIE_Y_MAX - (context.c.GOALIE_Y_MIN) + 10);
        if (goalA.intersects(corners) ||
                goalH.intersects(corners) ||
                goalA.contains(new CollisionMath.Point2D(corners.minX(), corners.minY())) ||
                goalA.contains(new CollisionMath.Point2D(corners.minX() + corners.width(), corners.minY() + corners.height())) ||
                goalH.contains(new CollisionMath.Point2D(corners.minX(), corners.minY())) ||
                goalH.contains(new CollisionMath.Point2D(corners.minX() + corners.width(), corners.minY() + corners.height()))) {
            return false; // redzone
        }
        return inBounds(corners, context);
    }

    public static boolean inBounds(CollisionMath.Bounds corners, GameEngine context) {
        CollisionMath.Bounds bounds = new CollisionMath.Bounds(context.c.MIN_X, context.c.MIN_Y,
                context.c.MAX_X - context.c.MIN_X,
                context.c.MAX_Y - context.c.MIN_Y);
        return corners.intersects(bounds) ||
                bounds.contains(new CollisionMath.Point2D(corners.minX(), corners.minY())) ||
                bounds.contains(new CollisionMath.Point2D(corners.minX(), corners.minY()));
    }

    public static boolean isPositionOccupied(double testX, double testY, Titan caster, GameEngine context) {
        if (testX < context.c.MIN_X || testX > context.c.MAX_X - 70 ||
                testY < context.c.MIN_Y || testY > context.c.MAX_Y - 70) {
            return true;
        }
        double prevX = caster.X;
        double prevY = caster.Y;
        caster.setX((int) testX);
        caster.setY((int) testY);

        boolean collides = false;
        if (caster.collidesSolid(context, context.allSolids)) {
            collides = true;
        } else if (context.players != null) {
            for (Titan other : context.players) {
                if (other != null && other.health > 0 && !other.id.equals(caster.id)) {
                    int otherW = other.width > 0 ? other.width : 70;
                    int otherH = other.height > 0 ? other.height : 70;
                    if (testX + 70 > other.X && testX < other.X + otherW &&
                            testY + 70 > other.Y && testY < other.Y + otherH) {
                        collides = true;
                        break;
                    }
                }
            }
        }
        caster.setX((int) prevX);
        caster.setY((int) prevY);
        return collides;
    }

    public static double[] findClosestUnoccupiedPosition(double initialX, double initialY, Titan caster, GameEngine context, double prefAngle) {
        if (!isPositionOccupied(initialX, initialY, caster, context)) {
            return new double[]{initialX, initialY};
        }

        for (int dist = 5; dist <= 300; dist += 5) {
            for (int i = 0; i < 16; i++) {
                int sign = (i % 2 == 0) ? 1 : -1;
                double angleOffset = sign * (i / 2) * (Math.PI / 8.0);
                double angle = prefAngle + angleOffset;

                double testX = initialX + Math.cos(angle) * dist;
                double testY = initialY + Math.sin(angle) * dist;

                testX = Math.max(context.c.MIN_X, Math.min(context.c.MAX_X - 70, testX));
                testY = Math.max(context.c.MIN_Y, Math.min(context.c.MAX_Y - 70, testY));

                if (!isPositionOccupied(testX, testY, caster, context)) {
                    return new double[]{testX, testY};
                }
            }
        }
        return new double[]{initialX, initialY};
    }
}
