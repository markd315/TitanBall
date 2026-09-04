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

        double ballCenterX = mover.minX() + mover.width() / 2.0;
        double ballCenterY = mover.minY() + mover.height() / 2.0;

        double obstMinX = obstacle.minX();
        double obstMaxX = obstacle.minX() + obstacle.width();
        double obstMinY = obstacle.minY();
        double obstMaxY = obstacle.minY() + obstacle.height();

        boolean isVerticalPane = obstacle.height() > obstacle.width();
        boolean isHorizontalPane = obstacle.width() > obstacle.height();

        // Previous position bounds prior to velocity step (dx, dy)
        double prevMinX = mover.minX() - dx;
        double prevMaxX = prevMinX + mover.width();
        double prevMinY = mover.minY() - dy;
        double prevMaxY = prevMinY + mover.height();

        Double tx = null;
        CollisionSide sideX = null;
        if (dx > 0 && prevMaxX <= obstMinX + 2.0) {
            tx = Math.max(0.0, (obstMinX - (prevMaxX - 2.0)) / dx);
            sideX = CollisionSide.LEFT;
        } else if (dx < 0 && prevMinX >= obstMaxX - 2.0) {
            tx = Math.max(0.0, ((prevMinX + 2.0) - obstMaxX) / (-dx));
            sideX = CollisionSide.RIGHT;
        }

        Double ty = null;
        CollisionSide sideY = null;
        if (dy > 0 && prevMaxY <= obstMinY + 2.0) {
            ty = Math.max(0.0, (obstMinY - (prevMaxY - 2.0)) / dy);
            sideY = CollisionSide.TOP;
        } else if (dy < 0 && prevMinY >= obstMaxY - 2.0) {
            ty = Math.max(0.0, ((prevMinY + 2.0) - obstMaxY) / (-dy));
            sideY = CollisionSide.BOTTOM;
        }

        if (sideX != null && sideY == null) return sideX;
        if (sideY != null && sideX == null) return sideY;
        if (sideX != null && sideY != null) {
            if (tx < ty) return sideX;
            if (ty < tx) return sideY;
            return isVerticalPane ? sideX : sideY;
        }

        // Spatial center checks when backtrack was ambiguous or already overlapping
        boolean inYRange = (ballCenterY >= obstMinY && ballCenterY <= obstMaxY);
        boolean inXRange = (ballCenterX >= obstMinX && ballCenterX <= obstMaxX);

        if (inYRange && !inXRange) {
            return (ballCenterX <= obstMinX) ? CollisionSide.LEFT : CollisionSide.RIGHT;
        }
        if (inXRange && !inYRange) {
            return (ballCenterY <= obstMinY) ? CollisionSide.TOP : CollisionSide.BOTTOM;
        }

        if (isVerticalPane && !inXRange) {
            return (ballCenterX <= obstMinX) ? CollisionSide.LEFT : CollisionSide.RIGHT;
        }
        if (isHorizontalPane && !inYRange) {
            return (ballCenterY <= obstMinY) ? CollisionSide.TOP : CollisionSide.BOTTOM;
        }

        // Corner region ratio fallback
        if (!inXRange && !inYRange) {
            double cornerX = (ballCenterX < obstMinX) ? obstMinX : obstMaxX;
            double cornerY = (ballCenterY < obstMinY) ? obstMinY : obstMaxY;
            double distCornerX = Math.abs(ballCenterX - cornerX);
            double distCornerY = Math.abs(ballCenterY - cornerY);

            if (distCornerX >= distCornerY) {
                return (ballCenterX < obstMinX) ? CollisionSide.LEFT : CollisionSide.RIGHT;
            } else {
                return (ballCenterY < obstMinY) ? CollisionSide.TOP : CollisionSide.BOTTOM;
            }
        }

        // Deep penetration fallback
        if (isVerticalPane) {
            return (ballCenterX <= (obstMinX + obstMaxX) / 2.0) ? CollisionSide.LEFT : CollisionSide.RIGHT;
        }
        if (isHorizontalPane) {
            return (ballCenterY <= (obstMinY + obstMaxY) / 2.0) ? CollisionSide.TOP : CollisionSide.BOTTOM;
        }

        double distLeft = ballCenterX - obstMinX;
        double distRight = obstMaxX - ballCenterX;
        double distTop = ballCenterY - obstMinY;
        double distBottom = obstMaxY - ballCenterY;

        double minDist = Math.min(Math.min(distLeft, distRight), Math.min(distTop, distBottom));
        if (minDist == distLeft) return CollisionSide.LEFT;
        if (minDist == distRight) return CollisionSide.RIGHT;
        if (minDist == distTop) return CollisionSide.TOP;
        return CollisionSide.BOTTOM;
    }

    public static boolean boundsIntersect(Bounds b1, Bounds b2) {
        return b1.intersects(b2);
    }
}
