package gameserver.entity.minions;

import gameserver.engine.GameEngine;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.*;
import org.joda.time.Instant;
import util.Util;

import java.awt.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class Portal extends gameserver.entity.Entity implements Collidable {

    private static final int COOLDOWN_MS = 10000;
    private static final int MAX_RANGE = 600;
    private UUID createdById;
    public RangeCircle rangeCircle;
    private Instant createdAt;//only used serverside so clock skew is irrelevant
    private Instant cdUntil;

    public Portal(TeamAffiliation team, Titan pl, List<Entity> pool, int x, int y, GameEngine context) {
        super(team);
        this.setX(x);
        this.setY(y);
        this.width = 50;
        this.height = 50;
        this.health = 20;
        this.maxHealth = 20;
        this.solid = false;
        this.createdById = pl.id;
        this.createdAt = Instant.now();//only used serverside so clock skew is irrelevant
        removeOldestPortal(pool, pl);
        RetObj secondCanStayIfRange = oldestP(pool, pl);
        if(! isPlaceableRangeCheck(context, pl.id)){
            if(secondCanStayIfRange.curr > 0){
                pool.remove(secondCanStayIfRange.bp);
            }
        }else{
            if(secondCanStayIfRange.bp != null){
                secondCanStayIfRange.bp.rangeCircle = null;
            }
        }
        this.rangeCircle = new RangeCircle(Color.RED, MAX_RANGE);
    }

    public boolean isCooldown(Instant now) {
        return cdUntil != null && now.isBefore(cdUntil);
    }

    private void removeOldestPortal(List<Entity> pool, Titan player) {
        RetObj oldestP = oldestP(pool, player);
        if (oldestP.bp != null && oldestP.curr > 1) {
            pool.remove(oldestP.bp);
        }
    }

    private RetObj oldestP(List<Entity> pool, Titan player){
        Portal oldestP = null;
        int currentBallPortals = 0;
        for (Entity e : pool) {
            if (e instanceof Portal && e.getHealth() > 0) {
                Portal p = (Portal) e;
                if (player.id.equals(p.createdById)) {
                    currentBallPortals += 1;
                    if (oldestP == null || p.createdAt.isBefore(oldestP.createdAt)) {
                        oldestP = p;
                    }
                }
            }
        }
        return new RetObj(oldestP, currentBallPortals);
    }

    private boolean isPlaceableRangeCheck(GameEngine context, UUID creator){
        Optional<Portal> friendly = findFriendlyPortal(context, creator);
        if(!friendly.isPresent()){
            return true;
        }
        double dist = Util.dist(friendly.get().X, friendly.get().Y,
                this.X, this.Y);
        return dist < MAX_RANGE;
    }

    private Optional<Portal> findFriendlyPortal(GameEngine context, UUID creator) {
        for (Entity e : context.entityPool) {
            if (e instanceof Portal) {
                Portal p = (Portal) e;
                if (creator.equals(p.createdById) && p.health > 0.0 && !p.id.equals(this.id)) {
                    return Optional.of(p);
                }
            }
        }
        return Optional.empty();
    }

    private void triggerCd(Instant now) {
        this.cdUntil = now.plus(COOLDOWN_MS);
    }

    @Override
    public void triggerCollide(GameEngine context, Box entity) {
        if (!this.isCooldown(context.now)) {
            Optional<Portal> p = findFriendlyPortal(context, this.createdById);
            if (p.isPresent() && !p.get().isCooldown(context.now)
                    && entity instanceof Titan && ((Titan) entity).team == p.get().team) {
                this.triggerCd(context.now);
                p.get().triggerCd(context.now);
                int x = (int)p.get().getX() + 25 - 35;
                int y = (int)p.get().getY() + 25 - 35;
                entity.setX(x);
                entity.setY(y);
                while (entity.collidesSolid(context, context.allSolids)) {
                    entity.Y += 3;
                }
            }
        }
    }

    public double cooldownPercentOver(Instant now){
        if(this.isCooldown(now)){
            double cdActivated = cdUntil.getMillis() - COOLDOWN_MS;
            double nowNormalized = now.getMillis() - cdActivated;
            double percent = nowNormalized*100.0 / ((double)COOLDOWN_MS);
            System.out.println(percent);
            return percent;
        }
        else{
            return 0;
        }
    }

    public Portal() {
        super(TeamAffiliation.UNAFFILIATED);
    }

    public UUID getCreatedById() {
        return createdById;
    }

    private class RetObj{
        private RetObj(Portal bp, int curr){
            this.bp = bp;
            this.curr = curr;
        }
        Portal bp;
        int curr;
    }
}
