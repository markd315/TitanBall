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
                cmp = (o1, o2) -> {
                    double d1 = Point2D.distance(o1.X + o1.width / 2.0, o1.Y + o1.height / 2.0, ballX, ballY);
                    double d2 = Point2D.distance(o2.X + o2.width / 2.0, o2.Y + o2.height / 2.0, ballX, ballY);
                    return Double.compare(d1, d2);
                };
                break;
            case NEAREST_MOUSE:
            case FURTHEST_MOUSE:
                cmp = (o1, o2) -> {
                    // Titans ALWAYS take priority over minions for single-target ability selection
                    boolean isT1 = (o1 instanceof Titan);
                    boolean isT2 = (o2 instanceof Titan);
                    if (isT1 && !isT2) return -1;
                    if (!isT1 && isT2) return 1;

                    double c1x = o1.X + (o1.width > 0 ? o1.width / 2.0 : 35.0);
                    double c1y = o1.Y + (o1.height > 0 ? o1.height / 2.0 : 35.0);
                    double c2x = o2.X + (o2.width > 0 ? o2.width / 2.0 : 35.0);
                    double c2y = o2.Y + (o2.height > 0 ? o2.height / 2.0 : 35.0);

                    double d1 = Point2D.distance(c1x, c1y, mX, mY);
                    double d2 = Point2D.distance(c2x, c2y, mX, mY);
                    return Double.compare(d1, d2);
                };
                break;
            case NEAREST:
            case FURTHEST:
            default:
                cmp = (o1, o2) -> {
                    boolean isT1 = (o1 instanceof Titan);
                    boolean isT2 = (o2 instanceof Titan);
                    if (isT1 && !isT2) return -1;
                    if (!isT1 && isT2) return 1;

                    double c1x = o1.X + (o1.width > 0 ? o1.width / 2.0 : 35.0);
                    double c1y = o1.Y + (o1.height > 0 ? o1.height / 2.0 : 35.0);
                    double c2x = o2.X + (o2.width > 0 ? o2.width / 2.0 : 35.0);
                    double c2y = o2.Y + (o2.height > 0 ? o2.height / 2.0 : 35.0);

                    double cx = casting != null ? casting.X + casting.width / 2.0 : mX;
                    double cy = casting != null ? casting.Y + casting.height / 2.0 : mY;

                    double d1 = Point2D.distance(c1x, c1y, cx, cy);
                    double d2 = Point2D.distance(c2x, c2y, cx, cy);
                    return Double.compare(d1, d2);
                };
                break;
        }

        if (method.toString().contains("FURTHEST") || method.toString().contains("HIGHEST")) {
            Collections.sort(proc, Collections.reverseOrder(cmp));
        } else {
            Collections.sort(proc, cmp);
        }

        Set<Entity> ret = new LinkedHashSet<>();
        int limitVal = Math.min(this.limit, proc.size());
        for (int i = 0; i < limitVal; i++) {
            ret.add(proc.get(i));
        }
        return ret;
    }
}
