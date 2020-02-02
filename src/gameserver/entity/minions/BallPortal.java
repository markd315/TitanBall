package gameserver.entity.minions;

import gameserver.engine.GameEngine;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.Box;
import gameserver.entity.Collidable;
import gameserver.entity.Entity;
import gameserver.entity.Titan;
import org.joda.time.Instant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class BallPortal extends Entity implements Collidable {

    private static int COOLDOWN_MS = 8000;
    private UUID createdById;
    private Instant createdAt;//only used serverside so clock skew is irrelevant
    private Instant cdUntil;

    public BallPortal(TeamAffiliation team, Titan pl, List<Entity> pool, int x, int y) {
        super(team);
        this.setX(x);
        this.setY(y);
        this.width = 50;
        this.height = 50;
        this.health = 20;
        this.maxHealth = 20;
        this.solid = false;
        this.createdById = pl.id;
        this.createdAt = Instant.now(); //only used serverside so clock skew is irrelevant
        removeOldestBallPortal(pool, pl);
    }

    public boolean isCooldown(Instant now) {
        return cdUntil != null && now.isBefore(cdUntil);
    }

    private void removeOldestBallPortal(List<Entity> pool, Titan player) {
        BallPortal oldestP = null;
        int currentBallPortals = 0;
        for (Entity e : pool) {
            if (e instanceof BallPortal && e.getHealth() > 0) {
                BallPortal p = (BallPortal) e;
                if (player.id.equals(p.createdById)) {
                    currentBallPortals += 1;
                    if (oldestP == null || p.createdAt.isBefore(oldestP.createdAt)) {
                        oldestP = p;
                    }
                }
            }
        }
        if (oldestP != null && currentBallPortals > 1) {
            pool.remove(oldestP);
        }
    }

    private Optional<BallPortal> findFriendlyBallPortal(GameEngine context, UUID creator) {
        for (Entity e : context.entityPool) {
            if (e instanceof BallPortal) {
                BallPortal p = (BallPortal) e;
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
            Optional<BallPortal> p = findFriendlyBallPortal(context, this.createdById);
            if(!context.ball.id.equals(entity.id)){
                return;
            }
            if (p.isPresent() && !p.get().isCooldown(context.now)) {
                this.triggerCd(context.now);
                p.get().triggerCd(context.now);
                int x = (int)p.get().getX() + 25 - context.ball.centerDist;
                int y = (int)p.get().getY() + 25 - context.ball.centerDist;
                entity.setX(x);
                entity.setY(y);
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

    public BallPortal() {
        super(TeamAffiliation.UNAFFILIATED);
    }
}
