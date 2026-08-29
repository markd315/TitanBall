package gameserver.targeting.core;

import gameserver.entity.Entity;
import gameserver.entity.Titan;
import gameserver.targeting.SortBy;

import java.awt.geom.Point2D;
import com.fasterxml.jackson.annotation.*;
import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Limiter {
    private final SortBy method;
    private final int limit;

    public Limiter(SortBy method, int limit) {
        this.method = method;
        this.limit = limit;
    }

    public Set<Entity> process(Set<Entity> in, Entity casting, int mX, int mY, int ballX, int ballY) {
        List<Entity> proc = new ArrayList<>(in);
        Comparator<Entity> cmp;
        switch (method) {
            case LOWEST_HP:
            case HIGHEST_HP:
                cmp = (o1, o2) -> Double.compare(o1.health, o2.health);
                break;
            case NEAREST_BALL:
            case FURTHEST_BALL:
                cmp = (o1, o2) -> Double.compare(
                        Point2D.distance(o1.X + o1.width / 2.0, o1.Y + o1.height / 2.0, ballX, ballY),
                        Point2D.distance(o2.X + o2.width / 2.0, o2.Y + o2.height / 2.0, ballX, ballY));
                break;
            case NEAREST_MOUSE:
            case FURTHEST_MOUSE:
            case NEAREST:
            case FURTHEST:
            default:
                double tx = (method == SortBy.NEAREST_MOUSE || method == SortBy.FURTHEST_MOUSE || casting == null) ? mX : casting.X + casting.width / 2.0;
                double ty = (method == SortBy.NEAREST_MOUSE || method == SortBy.FURTHEST_MOUSE || casting == null) ? mY : casting.Y + casting.height / 2.0;
                cmp = (o1, o2) -> {
                    if ((o1 instanceof Titan) != (o2 instanceof Titan)) return (o1 instanceof Titan) ? -1 : 1;
                    return Double.compare(
                            Point2D.distance(o1.X + (o1.width > 0 ? o1.width / 2.0 : 35.0), o1.Y + (o1.height > 0 ? o1.height / 2.0 : 35.0), tx, ty),
                            Point2D.distance(o2.X + (o2.width > 0 ? o2.width / 2.0 : 35.0), o2.Y + (o2.height > 0 ? o2.height / 2.0 : 35.0), tx, ty));
                };
                break;
        }
        Collections.sort(proc, (method.toString().contains("FURTHEST") || method.toString().contains("HIGHEST")) ? Collections.reverseOrder(cmp) : cmp);
        return new LinkedHashSet<>(proc.subList(0, Math.min(this.limit, proc.size())));
    }
}
