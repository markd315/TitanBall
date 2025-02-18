package gameserver.targeting.core;

import gameserver.engine.TeamAffiliation;
import gameserver.entity.Entity;
import gameserver.entity.Titan;
import gameserver.entity.TitanType;
import gameserver.targeting.DistanceFilter;

import java.awt.geom.Point2D;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Filter  implements Serializable {
    TeamAffiliation team;
    TitanType type;
    boolean allowSelf;
    List<DistanceFilter> distanceFilters;

    public Filter() {
    }

    public Filter(TeamAffiliation any, TitanType any1, boolean allowSelf, List<DistanceFilter> distanceFilters) {
        this.team = any;
        this.type = any1;
        this.allowSelf = allowSelf;
        this.distanceFilters = distanceFilters;
    }
    public Filter(TeamAffiliation any, TitanType any1, boolean allowSelf) {
        this.team = any;
        if(this.team == null){
            this.team = TeamAffiliation.ANY;
        }
        this.type = any1;
        if(this.team == null){
            this.type = TitanType.ANY;
        }
        this.allowSelf = allowSelf;
        this.distanceFilters = Collections.emptyList();
    }

    private boolean satisfiesDistanceFilter(Entity e, Entity caster){
        double actualDist = Point2D.distance(e.X, e.Y, caster.X, caster.Y);
        for(DistanceFilter fp : this.distanceFilters){
            if(fp.isLessThanNotGreaterThan() && fp.isStrict()){
                if(actualDist < fp.getDist()){
                    return false;
                }
            }
            if(fp.isLessThanNotGreaterThan() && !fp.isStrict()){
                if(actualDist <= fp.getDist()){
                    return false;
                }
            }
            if(!fp.isLessThanNotGreaterThan() && fp.isStrict()){
                if(actualDist > fp.getDist()){
                    return false;
                }
            }
            if(!fp.isLessThanNotGreaterThan() && !fp.isStrict()){
                if(actualDist >= fp.getDist()){
                    return false;
                }
            }
        }
        return true;
    }

    private boolean satisfiesTeam(TeamAffiliation in, Entity caster){
        System.out.println(in);
        System.out.println(caster.team);
        System.out.println(this.team);
        if(this.team == TeamAffiliation.ANY){
            return true;
        }
        if(this.team == TeamAffiliation.SAME && caster.team == in){
            return true;
        }
        if(this.team == TeamAffiliation.ENEMIES){
            if(caster.team == TeamAffiliation.HOME){
                return !in.equals(TeamAffiliation.HOME);
            }
            if(caster.team == TeamAffiliation.AWAY){
                return !in.equals(TeamAffiliation.AWAY);
            }
        }
        if(this.team == TeamAffiliation.OPPONENT){
            //System.out.println(caster.team + "" + in);
            if(caster.team == TeamAffiliation.HOME && in == TeamAffiliation.AWAY){
                return true;
            }
            if(caster.team == TeamAffiliation.AWAY && in == TeamAffiliation.HOME){
                return true;
            }
        }
        return this.team == in;
    }

    private boolean satisfiesType(Entity entity){
        if(this.type == TitanType.ANY_ENTITY){
            return true;
        }
        if(entity instanceof Titan) {
            TitanType in = ((Titan) entity).getType();
            if (this.type == TitanType.ANY) {
                return true;
            }
            if (this.type == TitanType.NOT_GUARDIAN && in != TitanType.GOALIE) {
                return true;
            }
            return this.type == in;
        }
        return false;
    }

    public Set<Entity> process(Set<Entity> in, Entity caster){
        Set<Entity> out = new HashSet<>();
        for(Entity e : in){
            if(satisfiesTeam(e.team, caster) && satisfiesType(e) && satisfiesDistanceFilter(e, caster)
                    && (allowSelf || e != caster)){
                out.add(e);
            }
        }
        return out;
    }
}
