package gameserver.targeting;

import gameserver.engine.GameEngine;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.Entity;
import gameserver.entity.TitanType;
import gameserver.targeting.core.Filter;
import gameserver.targeting.core.Limiter;
import gameserver.targeting.core.Selector;

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

    public Targeting(Selector sel, Filter fil, Limiter lim, GameEngine context){
    //initialize and call children with entity array
        entities = new HashSet<>();
        entities.addAll(context.entityPool);
        //entities.addAll(context.allSolids);
        entities.addAll(Arrays.asList(context.players));
        selector = sel;
        filter = fil;
        limiter = lim;
    }

    public Targeting(Selector sel, GameEngine context){
        this(sel, defaultFilter, defaultLimiter, context);
    }

    public Targeting(Selector sel, Filter fil, GameEngine context){
        this(sel, fil, defaultLimiter, context);
    }

    public Targeting(Selector sel, Limiter lim, GameEngine context){
        this(sel, defaultFilter, lim, context);
    }

    public Set<Entity> process(int mX, int mY, Entity casting, int ballX, int ballY){
        //System.out.println("processing");
        for(Entity e : entities){
            //System.out.println("pool " + e.team + e.health);
        }
        Set<Entity> ret = selector.select(entities, mX, mY, casting);
        for(Entity e : ret){
            //System.out.println("sel " + e.team + e.health);
        }
        ret = filter.process(ret, casting);
        for(Entity e : ret){
            //System.out.println("fil " + e.team + e.health);
        }
        ret = limiter.process(ret, casting, mX, mY, ballX, ballY);
        for(Entity e : ret){
            //System.out.println("lim " + e.team + e.health);
        }
        return ret;
    }

    //3 stage process: Include entities in selection based on collision implementation (required)
    //Filter entities by type, team, distance from target (default behavior is opposing team only)
    //Sort and limit the entries by parameter (default behavior is skip this)
}
