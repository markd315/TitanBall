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

    public static boolean boundsIntersect(Bounds b1, Bounds b2) {
        return b1.intersects(b2);
    }
}
