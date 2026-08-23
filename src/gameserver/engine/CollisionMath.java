package gameserver.engine;

import java.util.List;

public class CollisionMath   {
    public CollisionMath() {}

    public record EllipseData(double centerX, double centerY, double radiusX, double radiusY) {}
    public record Bounds(double minX, double minY, double width, double height) {
        public boolean intersects(Bounds b2) {
            return minX < b2.minX() + b2.width() &&
                   minX + width > b2.minX() &&
                   minY < b2.minY() + b2.height() &&
                   minY + height > b2.minY();
        }
        public boolean contains(Point2D p) {
            return contains(p.x(), p.y());
        }
        public boolean contains(double x, double y) {
            return x >= minX && x <= minX + width &&
                   y >= minY && y <= minY + height;
        }
        public double getWidth() { return width; }
        public double getHeight() { return height; }
        public double getX() { return minX; }
        public double getY() { return minY; }
        public double getMinX() { return minX; }
        public double getMinY() { return minY; }
        public double getMaxX() { return minX + width; }
        public double getMaxY() { return minY + height; }
    }
    public record Point2D(double x, double y) {
        public double distance(Point2D other) {
            return Math.hypot(x - other.x, y - other.y);
        }
    }
    
    public record PolygonData(List<Double> points) {}
    public record RectangleData(double x, double y, double width, double height) {}

    public static boolean ellipseBoundsIntersect(EllipseData e1, EllipseData e2) {
        Bounds b1 = new Bounds(e1.centerX() - e1.radiusX(), e1.centerY() - e1.radiusY(), e1.radiusX() * 2, e1.radiusY() * 2);
        Bounds b2 = new Bounds(e2.centerX() - e2.radiusX(), e2.centerY() - e2.radiusY(), e2.radiusX() * 2, e2.radiusY() * 2);
        return b1.intersects(b2);
    }

    public enum CollisionSide {
        LEFT, RIGHT, TOP, BOTTOM, NONE
    }

    public static CollisionSide getCollisionSide(Bounds mover, Bounds obstacle, double dx, double dy) {
        if (!mover.intersects(obstacle)) {
            return CollisionSide.NONE;
        }

        double overlapLeft = (mover.minX() + mover.width()) - obstacle.minX();
        double overlapRight = (obstacle.minX() + obstacle.width()) - mover.minX();
        double overlapTop = (mover.minY() + mover.height()) - obstacle.minY();
        double overlapBottom = (obstacle.minY() + obstacle.height()) - mover.minY();

        double candX = Double.POSITIVE_INFINITY;
        CollisionSide sideX = CollisionSide.NONE;
        if (dx > 0 && overlapLeft > 0) {
            candX = overlapLeft;
            sideX = CollisionSide.LEFT;
        } else if (dx < 0 && overlapRight > 0) {
            candX = overlapRight;
            sideX = CollisionSide.RIGHT;
        }

        double candY = Double.POSITIVE_INFINITY;
        CollisionSide sideY = CollisionSide.NONE;
        if (dy > 0 && overlapTop > 0) {
            candY = overlapTop;
            sideY = CollisionSide.TOP;
        } else if (dy < 0 && overlapBottom > 0) {
            candY = overlapBottom;
            sideY = CollisionSide.BOTTOM;
        }

        if (candX < candY && sideX != CollisionSide.NONE) {
            return sideX;
        } else if (candY < candX && sideY != CollisionSide.NONE) {
            return sideY;
        }

        // Fallback: choose smallest penetration depth overall
        double minX = Math.min(overlapLeft, overlapRight);
        double minY = Math.min(overlapTop, overlapBottom);
        if (minX < minY) {
            return (overlapLeft < overlapRight) ? CollisionSide.LEFT : CollisionSide.RIGHT;
        } else {
            return (overlapTop < overlapBottom) ? CollisionSide.TOP : CollisionSide.BOTTOM;
        }
    }

    public static boolean boundsIntersect(Bounds b1, Bounds b2) {
        return b1.intersects(b2);
    }
}
