package gameserver.entity.minions;

import gameserver.effects.EffectId;
import gameserver.effects.effects.DefenseEffect;
import gameserver.effects.effects.EmptyEffect;
import gameserver.effects.effects.HideBallEffect;
import gameserver.effects.effects.ShootEffect;
import gameserver.engine.GameEngine;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.Box;
import gameserver.entity.Collidable;
import gameserver.entity.Entity;
import gameserver.entity.Titan;

import java.io.Serializable;
import java.util.*;

public class Parapet extends Entity implements Tickable, Collidable, Serializable {
    public static final long serialVersionUID = 1L;

    public static class ParapetOccupant implements Serializable {
        public static final long serialVersionUID = 1L;
        public UUID titanId;
        public double entryX;
        public double entryY;
        public enum State { ENTERING, MOUNTED, EXITING }
        public State state;
        public long timerUntilEpochMs;
        public long mountGraceUntilEpochMs;
    }

    public Map<UUID, ParapetOccupant> occupants = new HashMap<>();
    public Map<UUID, Long> entryCooldowns = new HashMap<>();

    public Parapet(TeamAffiliation team, Titan goalie, int x, int y) {
        super(team);
        this.setX(x);
        this.setY(y);
        this.width = 100;
        this.height = 100;
        this.health = 99999;
        this.maxHealth = 99999;
        this.solid = false;
    }

    public Parapet() {
        super(TeamAffiliation.UNAFFILIATED);
    }

    public boolean isMounted(UUID titanId) {
        ParapetOccupant occ = occupants.get(titanId);
        return occ != null && occ.state == ParapetOccupant.State.MOUNTED;
    }

    @Override
    public void triggerCollide(GameEngine context, Box box) {
        if (box instanceof Titan t && t.team == this.team && t.health > 0.0) {
            startEntry(context, t);
        }
    }

    @Override
    public void tick(GameEngine context) {
        long now = context.nowEpochMs;

        // 1. Clean up expired entry cooldowns
        entryCooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);

        // 2. Process existing occupants
        List<UUID> toRemove = new ArrayList<>();
        for (ParapetOccupant occ : occupants.values()) {
            Titan t = findTitan(context, occ.titanId);
            if (t == null || t.health <= 0.0) {
                if (t != null) {
                    removeParapetBonuses(context, t);
                }
                toRemove.add(occ.titanId);
                continue;
            }

            if (occ.state == ParapetOccupant.State.ENTERING) {
                clearMovement(t);
                if (now >= occ.timerUntilEpochMs) {
                    // Entry root complete -> teleport to center and mount
                    t.X = this.X + this.width / 2.0 - t.width / 2.0;
                    t.Y = this.Y + this.height / 2.0 - t.height / 2.0;
                    clearMovement(t);
                    occ.state = ParapetOccupant.State.MOUNTED;
                    occ.mountGraceUntilEpochMs = now + 350; // Give 350ms to swallow lingering pathfinding clicks
                    ensureParapetBonuses(context, t);
                }
            } else if (occ.state == ParapetOccupant.State.MOUNTED) {
                if (now >= occ.mountGraceUntilEpochMs && hasIssuedMovement(t)) {
                    // Movement issued -> lose all bonuses immediately and root for 1s
                    occ.state = ParapetOccupant.State.EXITING;
                    occ.timerUntilEpochMs = now + 1000;
                    removeParapetBonuses(context, t);
                    context.effectPool.addUniqueEffect(new EmptyEffect(1000, t, EffectId.ROOT), context);
                    clearMovement(t);
                } else {
                    // Keep centered and maintain bonuses
                    t.X = this.X + this.width / 2.0 - t.width / 2.0;
                    t.Y = this.Y + this.height / 2.0 - t.height / 2.0;
                    if (now < occ.mountGraceUntilEpochMs) {
                        clearMovement(t);
                    }
                    ensureParapetBonuses(context, t);
                }
            } else if (occ.state == ParapetOccupant.State.EXITING) {
                clearMovement(t);
                if (now >= occ.timerUntilEpochMs) {
                    // Exit root complete -> teleport back to entry point
                    t.X = occ.entryX;
                    t.Y = occ.entryY;
                    clearMovement(t);
                    entryCooldowns.put(t.id, now + 1000);
                    toRemove.add(t.id);
                }
            }
        }

        for (UUID id : toRemove) {
            occupants.remove(id);
        }

        // 3. Check for friendly titans walking into the parapet bounds
        for (Titan t : context.players) {
            if (t != null && t.team == this.team && t.health > 0.0) {
                if (this.asBounds().intersects(t.asBounds())) {
                    startEntry(context, t);
                }
            }
        }
    }

    private void startEntry(GameEngine context, Titan t) {
        long now = context.nowEpochMs;
        if (!occupants.containsKey(t.id) && (!entryCooldowns.containsKey(t.id) || entryCooldowns.get(t.id) <= now)) {
            ParapetOccupant occ = new ParapetOccupant();
            occ.titanId = t.id;
            occ.entryX = t.X;
            occ.entryY = t.Y;
            occ.state = ParapetOccupant.State.ENTERING;
            occ.timerUntilEpochMs = now + 1000;
            occupants.put(t.id, occ);

            context.effectPool.addUniqueEffect(new EmptyEffect(1000, t, EffectId.ROOT), context);
            clearMovement(t);
        }
    }

    private boolean hasIssuedMovement(Titan t) {
        if (t.runLeft != 0 || t.runRight != 0 || t.runUp != 0 || t.runDown != 0) return true;
        if (t.moveMemL || t.moveMemR || t.moveMemU || t.moveMemD) return true;
        if (t.programmed) return true;
        if (t.marchingOrderX != -1 && t.marchingOrderY != -1) {
            double centerX = this.X + this.width / 2.0;
            double centerY = this.Y + this.height / 2.0;
            double dist = util.Util.dist(t.marchingOrderX, t.marchingOrderY, centerX, centerY);
            if (dist > 30.0) {
                return true;
            }
        }
        return false;
    }

    private void clearMovement(Titan t) {
        t.runLeft = 0;
        t.runRight = 0;
        t.runUp = 0;
        t.runDown = 0;
        t.moveMemL = false;
        t.moveMemR = false;
        t.moveMemU = false;
        t.moveMemD = false;
        t.programmed = false;
        t.marchingOrderX = -1;
        t.marchingOrderY = -1;
    }

    public gameserver.engine.CollisionMath.Bounds getEnemySolidBounds() {
        if (this.team == TeamAffiliation.HOME) {
            // Home parapet is on the away/right side facing away goal (right); the half closest to goal is right half
            return new gameserver.engine.CollisionMath.Bounds((int) this.X + 50, (int) this.Y, 50, this.height);
        } else {
            // Away parapet is on the home/left side facing home goal (left); the half closest to goal is left half
            return new gameserver.engine.CollisionMath.Bounds((int) this.X, (int) this.Y, 50, this.height);
        }
    }

    private void ensureParapetBonuses(GameEngine context, Titan t) {
        if (!context.effectPool.hasEffect(t, EffectId.DEFENSE)) {
            context.effectPool.addUniqueEffect(new DefenseEffect(10000, t, 1.20), context);
        }
        if (!context.effectPool.hasEffect(t, EffectId.SHOOT)) {
            context.effectPool.addUniqueEffect(new ShootEffect(10000, t, 1.20), context);
        }
        if (t.possession == 1 && t.actionState == Titan.TitanState.IDLE) {
            if (!context.effectPool.hasEffect(t, EffectId.HIDE_BALL)) {
                context.effectPool.addUniqueEffect(new HideBallEffect(10000, t), context);
            }
        } else {
            if (context.effectPool.hasEffect(t, EffectId.HIDE_BALL)) {
                context.effectPool.cullEffectOn(context, t, EffectId.HIDE_BALL);
                context.ballVisible = true;
            }
        }
    }

    private void removeParapetBonuses(GameEngine context, Titan t) {
        context.effectPool.cullEffectOn(context, t, EffectId.DEFENSE);
        context.effectPool.cullEffectOn(context, t, EffectId.SHOOT);
        context.effectPool.cullEffectOn(context, t, EffectId.HIDE_BALL);
        context.ballVisible = true;
    }

    private Titan findTitan(GameEngine context, UUID id) {
        if (context.players == null) return null;
        for (Titan t : context.players) {
            if (t != null && t.id.equals(id)) {
                return t;
            }
        }
        return null;
    }
}
