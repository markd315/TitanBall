package gameserver.targeting;

import gameserver.Game;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.Entity;
import gameserver.entity.TitanType;

import java.util.*;

public class Targeting {

    public Set<Entity> entities;
    private Selector selector;
    private Filter filter;
    private Limiter limiter;
    private static Filter defaultFilter = new Filter(TeamAffiliation.ANY,
            TitanType.ANY,
            true,
            Collections.emptyList()
            );
    private static Limiter defaultLimiter = new Limiter(SortBy.NEAREST, 1);

    public Targeting(Selector sel, Filter fil, Limiter lim, Game context){
    //initialize and call children with entity array
        entities = new HashSet<>();
        List<Entity> entityList = Arrays.asList(context.allSolids);
        for(Entity e : entityList){
            entities.add(e);
        }
        selector = sel;
        filter = fil;
        limiter = lim;
    }

    public Targeting(Selector sel, Game context){
        this(sel, defaultFilter, defaultLimiter, context);
    }

    public Targeting(Selector sel, Filter fil, Game context){
        this(sel, fil, defaultLimiter, context);
    }

    public Targeting(Selector sel, Limiter lim, Game context){
        this(sel, defaultFilter, lim, context);
    }

    public Set<Entity> process(int mX, int mY, Entity casting, int ballX, int ballY){
        //System.out.println("processing");
        Set<Entity> ret = selector.select(entities, mX, mY, casting);
        ret = filter.process(ret, casting);
        ret = limiter.process(ret, casting, mX, mY, ballX, ballY);
        for(Entity e : ret){
            //System.out.println("pro " + e.team + e.health);
        }
        return ret;
    }

    //3 stage process: Include entities in selection based on collision implementation (required)
    //Filter entities by type, team, distance from target (default behavior is opposing team only)
    //Sort and limit the entries by parameter (default behavior is skip this)
}
