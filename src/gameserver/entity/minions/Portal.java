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

public class Portal extends gameserver.entity.Entity implements Collidable {

    private UUID createdById;
    private Instant createdAt;
    private Instant cdUntil;

    public Portal(TeamAffiliation team, Titan pl, List<Entity> pool, int x, int y) {
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
        removeOldestPortal(pool, pl);
    }

    public boolean isCooldown() {
        return cdUntil != null && Instant.now().isBefore(cdUntil);
    }

    private void removeOldestPortal(List<Entity> pool, Titan player) {
        Portal oldestP = null;
        int currentPortals = 0;
        for (Entity e : pool) {
            if (e instanceof Portal && e.getHealth() > 0) {
                Portal p = (Portal) e;
                if (player.id.equals(p.createdById)) {
                    currentPortals += 1;
                    if (oldestP == null || p.createdAt.isBefore(oldestP.createdAt)) {
                        oldestP = p;
                    }
                }
            }
        }
        if (oldestP != null && currentPortals > 1) {
            pool.remove(oldestP);
        }
    }

    private Optional<Portal> findFriendlyPortal(Game context, UUID creator) {
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

    private void triggerCd() {
        this.cdUntil = Instant.now().plus(5000);
    }

    @Override
    public void triggerCollide(Game context, Box entity) {
        if (!this.isCooldown()) {
            Optional<Portal> p = findFriendlyPortal(context, this.createdById);
            if (p.isPresent() && !p.get().isCooldown() && entity instanceof Titan) {
                this.triggerCd();
                p.get().triggerCd();
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

    public Portal() {
        super(TeamAffiliation.UNAFFILIATED);
    }
}
