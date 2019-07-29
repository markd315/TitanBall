package gameserver.entity.minions;

import org.joda.time.Instant;
import gameserver.Game;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.Box;
import gameserver.entity.Collidable;
import gameserver.entity.Entity;
import gameserver.entity.Titan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class BallPortal extends Entity implements Collidable {

    private UUID createdById;
    private Instant createdAt;
    private Instant cdUntil;

    public BallPortal(TeamAffiliation team, Titan pl, List<Entity> pool, int x, int y) {
        super(team);
        this.setX(x);
        this.setY(y);
        this.width = 50;
        this.height = 50;
        this.health = 40;
        this.maxHealth = 40;
        this.solid = false;
        this.createdById = pl.id;
        this.createdAt = Instant.now();
        removeOldestBallPortal(pool, pl);
    }

    public boolean isCooldown() {
        return cdUntil != null && Instant.now().isBefore(cdUntil);
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

    private Optional<BallPortal> findFriendlyBallPortal(Game context, UUID creator) {
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

    private void triggerCd() {
        this.cdUntil = Instant.now().plus(5000);
    }

    @Override
    public void triggerCollide(Game context, Box entity) {
        if (!this.isCooldown()) {
            Optional<BallPortal> p = findFriendlyBallPortal(context, this.createdById);
            if(!context.ball.id.equals(entity.id)){
                return;
            }
            if (p.isPresent() && !p.get().isCooldown()) {
                this.triggerCd();
                p.get().triggerCd();
                int x = (int)p.get().getX() + 25 - 7;
                int y = (int)p.get().getY() + 25 - 7;
                entity.setX(x);
                entity.setY(y);
            }
        }
    }

    public BallPortal() {
        super(TeamAffiliation.UNAFFILIATED);
    }
}
