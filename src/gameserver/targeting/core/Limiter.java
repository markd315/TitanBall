package gameserver.targeting.core;


import gameserver.entity.Entity;
import gameserver.targeting.SortBy;

import java.awt.geom.Point2D;
import java.io.Serializable;
import java.util.*;

public class Limiter  implements Serializable {
    private final SortBy method;
    private final int limit;

    public Limiter(SortBy method, int limit) {
        this.method = method;
        this.limit = limit;
    }

    public Set<Entity> process(Set<Entity> in, Entity casting, int mX, int mY, int ballX, int ballY){
        List<Entity> proc = new ArrayList<>();
        for(Entity e : in){
            proc.add(e);
        }
        Comparator cmp;
        switch(method) {
            case LOWEST_HP:
            case HIGHEST_HP:
                cmp = (Comparator<Entity>) (o1, o2) -> (int) (o1.health - o2.health);
                break;
            case NEAREST_BALL:
            case FURTHEST_BALL:
                cmp = (Comparator<Entity>) (o1, o2) -> {
                    double d1 = Point2D.distance(o1.X, o1.Y, ballX, ballY);
                    double d2 = Point2D.distance(o2.X, o2.Y, ballX, ballY);
                    return (int) (d1 - d2);
                };
                break;
            case NEAREST_MOUSE:
            case FURTHEST_MOUSE:
                cmp = (Comparator<Entity>) (o1, o2) -> {
                    double d1 = Point2D.distance(o1.X, o1.Y, mX, mY);
                    double d2 = Point2D.distance(o2.X, o2.Y, mX, mY);
                    return (int) (d1 - d2);
                };
                break;
            case NEAREST:
            case FURTHEST:
            default:
                //System.out.println("method hit");
                cmp = (Comparator<Entity>) (o1, o2) -> {
                    double d1 = Point2D.distance(o1.X, o1.Y, casting.X, casting.Y);
                    double d2 = Point2D.distance(o2.X, o2.Y, casting.X, casting.Y);
                    return (int) (d1 - d2);
                };
                break;
        }
        if(method.toString().contains("FURTHEST") || method.toString().contains("HIGHEST")){
            //System.out.println("rev");
            Collections.sort(proc,Collections.reverseOrder(cmp));
        }
        else{
            //System.out.println("sorted");
            Collections.sort(proc,cmp);
        }
        Set<Entity> ret = new HashSet<>();
        int limit = (this.limit < proc.size()) ? this.limit : proc.size();
        for(int i=0; i<limit; i++){
            ret.add(proc.get(i));
            //System.out.println(proc.get(i));
        }
        return ret;
    }
}
