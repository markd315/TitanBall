package gameserver.engine;

import gameserver.effects.EffectId;
import gameserver.effects.effects.EmptyEffect;
import gameserver.effects.effects.HealEffect;
import gameserver.effects.effects.RatioEffect;
import gameserver.effects.effects.Effect;
import gameserver.effects.effects.CallbackEffect;
import gameserver.entity.Entity;
import gameserver.entity.Titan;
import gameserver.entity.TitanType;
import gameserver.entity.Box;
import gameserver.entity.minions.*;
import gameserver.Const;
import org.joda.time.Instant;

import java.io.Serializable;
import java.util.*;

public class GuardianAbilities implements Serializable {
    public static final long serialVersionUID = 1L;

    public TeamAffiliation team;
    public Map<UUID, Long> entityExpiries = new HashMap<>();
    public List<UUID> activeBarrageIds = new ArrayList<>();
    public Set<UUID> tollPaidMinions = new HashSet<>();

    // Active timers/state
    public long lowGravityUntilMs = 0;
    public long fastBreakUntilMs = 0;
    public Titan tetherCaster = null;
    public Titan tetherTarget = null;
    public long tetherUntilMs = 0;
    public int overchargedWavesQueued = 0;
    public long riskAdjustedReturnUntilMs = 0;
    public boolean riskAdjustedReturnPending = false;
    public double manaRateMultiplier = 1.0;
    
    // Flag to prevent double field dilatation
    private boolean fieldDilated = false;

    public GuardianAbilities() {
        // No-arg constructor for Jackson deserialization
    }

    public GuardianAbilities(TeamAffiliation team) {
        this.team = team;
    }

    public void purchaseOrUse(GameEngine context, Titan goalie, String nodeKey) {
        if (nodeKey == null) return;

        // Fortress Tree
        if (nodeKey.endsWith(".reinforce")) {
            int ry = 300 + (int)(Math.random() * 500);
            spawnWallAt(context, goalie, 15, 10000, ry);
        } else if (nodeKey.endsWith(".healingburst")) {
            applyHealingBurst(context, goalie);
        } else if (nodeKey.endsWith(".snaretrap")) {
            spawnSnareTrap(context, goalie);
        } else if (nodeKey.endsWith(".biggermodels")) {
            scaleAlliedTitans(context);
        } else if (nodeKey.endsWith(".bastionprotocol")) {
            spawnBastionWalls(context, goalie);
        } else if (nodeKey.endsWith(".barrage")) {
            spawnBarrageConfig(context, 1);
        } else if (nodeKey.endsWith(".hemmedin")) {
            applyHemmedIn(context, goalie);
        } else if (nodeKey.endsWith(".emergencybarrier")) {
            spawnWall(context, goalie, 8, context.c.getI("guardian.emergencybarrier.lifetime"));
        } else if (nodeKey.endsWith(".noflyzoneperm")) {
            // Permanent passive - no entity spawned
        } else if (nodeKey.endsWith(".noflyzonetmp")) {
            if (team == TeamAffiliation.HOME) {
                context.homeNoFlyZoneActiveUntil = context.nowEpochMs + 10000;
            } else {
                context.awayNoFlyZoneActiveUntil = context.nowEpochMs + 10000;
            }
        } else if (nodeKey.endsWith(".deepfreeze")) {
            // Permanent passive - no entity spawned
        } else if (nodeKey.endsWith(".dilators")) {
            // Dilators is passive, speed scale is checked globally
        }

        // Siege Tree
        else if (nodeKey.endsWith(".overchargeminion")) {
            overchargedWavesQueued++;
        } else if (nodeKey.endsWith(".lowgravity")) {
            lowGravityUntilMs = context.nowEpochMs + 15000;
        } else if (nodeKey.endsWith(".energyrush")) {
            applyEnergyRush(context);
        } else if (nodeKey.endsWith(".forwardmines")) {
            spawnForwardMines(context, goalie);
        } else if (nodeKey.endsWith(".ballportal_rough")) {
            spawnBallPortalRough(context, goalie);
        } else if (nodeKey.endsWith(".rushlane")) {
            spawnRushLane(context, goalie);
        } else if (nodeKey.endsWith(".parapet")) {
            spawnParapet(context, goalie);
        } else if (nodeKey.endsWith(".callsiegeminion")) {
            spawnSiegeMinion(context, goalie);
        } else if (nodeKey.endsWith(".anchor")) {
            applyAnchor(context, goalie);
        } else if (nodeKey.endsWith(".shockgrenade")) {
            applyShockGrenade(context);
        } else if (nodeKey.endsWith(".wallsdown")) {
            disableEnemyWalls(context);
        } else if (nodeKey.endsWith(".forwardmedics")) {
            spawnForwardMedics(context, goalie);
        } else if (nodeKey.endsWith(".multiball")) {
            spawnSecondBall(context);
        }

        // Empowerment Tree
        else if (nodeKey.endsWith(".sharpshooter")) {
            applySharpshooter(context);
        } else if (nodeKey.endsWith(".heroportals")) {
            spawnHeroPortals(context, goalie);
        } else if (nodeKey.endsWith(".energysurge")) {
            refillFuel(context);
        } else if (nodeKey.endsWith(".secondwind")) {
            resetCooldowns(context);
        }

        // Cultivation Tree
        else if (nodeKey.endsWith(".manainfusion")) {
            if (team == TeamAffiliation.HOME) {
                context.homeGoalieMana = Math.min(getMaxMana(context), context.homeGoalieMana + 100.0);
            } else {
                context.awayGoalieMana = Math.min(getMaxMana(context), context.awayGoalieMana + 100.0);
            }
            manaRateMultiplier *= 1.1;
        } else if (nodeKey.endsWith(".manacompounding") || nodeKey.endsWith(".highermanacap") || nodeKey.endsWith(".tollcollector")) {
            // Handled passively via mana tick calculations
        } else if (nodeKey.endsWith(".manavines")) {
            spawnManaVines(context, goalie);
        } else if (nodeKey.endsWith(".manasurge")) {
            manaSurge(context);
        } else if (nodeKey.endsWith(".manasummon")) {
            manaSummon(context, goalie);
        } else if (nodeKey.endsWith(".wallportals")) {
            spawnWallPortals(context, goalie);
        } else if (nodeKey.endsWith(".riskadjustedreturn")) {
            triggerRiskAdjustedReturn(context);
        } else if (nodeKey.endsWith(".tripledown")) {
            triggerTripleDown(context);
        }

        // Re-apply roster masteries on any passive upgrade purchase (covers grit, marking, apex, etc.)
        applyRosterStatBoosts(context);
    }

    public void tick(GameEngine context) {
        // 1. Process culling of temporary entities & handle health decay for temporary walls
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, Long> entry : entityExpiries.entrySet()) {
            long expiry = entry.getValue();
            long remaining = expiry - context.nowEpochMs;
            
            // Find entity
            for (Entity e : context.entityPool) {
                if (e.id.equals(entry.getKey()) && e.health > 0.0) {
                    if (remaining <= 0) {
                        e.health = 0.0;
                    } else if (e instanceof Wall) {
                        // Decay health linearly
                        long lifetime = 10000; // default for reinforce
                        if (e.maxHealth == 8.0) { // emergency barrier has 8 hp
                            lifetime = context.c.getI("guardian.emergencybarrier.lifetime");
                        }
                        double fraction = (double) remaining / lifetime;
                        e.health = Math.max(0.01, e.maxHealth * fraction);
                    }
                }
            }
            
            if (remaining <= 0) {
                toRemove.add(entry.getKey());
            }
        }
        for (UUID id : toRemove) {
            entityExpiries.remove(id);
        }

        // 2. Process Anchor tether logic
        if (tetherUntilMs > 0 && context.nowEpochMs < tetherUntilMs) {
            if (tetherCaster != null && tetherTarget != null) {
                double dist = util.Util.dist(tetherCaster.X, tetherCaster.Y, tetherTarget.X, tetherTarget.Y);
                if (dist > 220) {
                    // Pull them closer to the midpoint
                    double midX = (tetherCaster.X + tetherTarget.X) / 2.0;
                    double midY = (tetherCaster.Y + tetherTarget.Y) / 2.0;
                    double ratio = 220.0 / dist;
                    tetherCaster.X = midX - (midX - tetherCaster.X) * ratio;
                    tetherCaster.Y = midY - (midY - tetherCaster.Y) * ratio;
                    tetherTarget.X = midX - (midX - tetherTarget.X) * ratio;
                    tetherTarget.Y = midY - (midY - tetherTarget.Y) * ratio;
                }
            }
        } else {
            tetherUntilMs = 0;
            tetherCaster = null;
            tetherTarget = null;
        }

        // 3. Process Risk Adjusted Return timer
        if (riskAdjustedReturnPending && context.nowEpochMs >= riskAdjustedReturnUntilMs) {
            riskAdjustedReturnPending = false;
            if (team == TeamAffiliation.HOME) {
                context.home.score += 1.5;
            } else {
                context.away.score += 1.5;
            }
        }

        // Toll collector check (5 mana whenever a FRIENDLY minion crosses the midline)
        Set<String> purchased = (team == TeamAffiliation.HOME) ? context.homeGoaliePurchasedUpgrades : context.awayGoaliePurchasedUpgrades;
        if (purchased.contains("cultivation.t3.tollcollector")) {
            for (Entity e : context.entityPool) {
                if (e instanceof LaneMinion && e.team == team && e.getHealth() > 0.0) {
                    boolean crossed = (team == TeamAffiliation.HOME) ? (e.X >= 1024.0) : (e.X <= 1024.0);
                    if (crossed && !tollPaidMinions.contains(e.id)) {
                        tollPaidMinions.add(e.id);
                        if (team == TeamAffiliation.HOME) {
                            context.homeGoalieMana = Math.min(getMaxMana(context), context.homeGoalieMana + 5.0);
                        } else {
                            context.awayGoalieMana = Math.min(getMaxMana(context), context.awayGoalieMana + 5.0);
                        }
                    }
                }
            }
        }

        // Deep Freeze: slow ratio of 1.50 (33% slow) to enemy units in defensive third
        boolean deepFreeze = purchased.contains("fortress.t6.deepfreeze");
        if (team == TeamAffiliation.HOME) {
            context.homeDeepFreezeActive = deepFreeze;
        } else {
            context.awayDeepFreezeActive = deepFreeze;
        }
        if (deepFreeze) {
            double minX = (team == TeamAffiliation.HOME) ? 36.0 : 1368.0;
            double maxX = (team == TeamAffiliation.HOME) ? 680.0 : 2012.0;
            for (Titan t : context.players) {
                if (t.team != team && t.health > 0.0 && t.X >= minX && t.X <= maxX) {
                    context.effectPool.addUniqueEffect(new RatioEffect(1000, t, EffectId.SLOW, 1.50), context);
                }
            }
            for (Entity mn : context.entityPool) {
                if (mn instanceof LaneMinion && mn.team != team && mn.getHealth() > 0.0 && mn.X >= minX && mn.X <= maxX) {
                    context.effectPool.addUniqueEffect(new RatioEffect(1000, mn, EffectId.SLOW, 1.50), context);
                }
            }
        }

        // No-Fly Zone active check
        boolean noFly = purchased.contains("fortress.t5.noflyzoneperm") || 
                        context.nowEpochMs < (team == TeamAffiliation.HOME ? context.homeNoFlyZoneActiveUntil : context.awayNoFlyZoneActiveUntil);
        if (team == TeamAffiliation.HOME) {
            context.homeNoFlyZoneActive = noFly;
        } else {
            context.awayNoFlyZoneActive = noFly;
        }

        // Low Gravity active check
        boolean lowGrav = context.nowEpochMs < lowGravityUntilMs;
        if (team == TeamAffiliation.HOME) {
            context.homeLowGravityActive = lowGrav;
        } else {
            context.awayLowGravityActive = lowGrav;
        }

        // Dynamic moving barrage configuration alternation
        if (purchased.contains("fortress.t4.barrage")) {
            int cycleFrame = context.framesSinceStart % 300;
            if (cycleFrame == 0) {
                clearBarrageConfig(context);
                spawnBarrageConfig(context, 1);
            } else if (cycleFrame == 60) {
                clearBarrageConfig(context);
            } else if (cycleFrame == 100) {
                clearBarrageConfig(context);
                spawnBarrageConfig(context, 2);
            } else if (cycleFrame == 160) {
                clearBarrageConfig(context);
            } else if (cycleFrame == 200) {
                clearBarrageConfig(context);
                spawnBarrageConfig(context, 3);
            } else if (cycleFrame == 260) {
                clearBarrageConfig(context);
            }
        }

        // 4. Continuous tick checks (zones, auras, drones, etc.)
        tickZonesAndHazards(context);
    }

    private void tickZonesAndHazards(GameEngine context) {
        Set<String> purchased = (team == TeamAffiliation.HOME) ? context.homeGoaliePurchasedUpgrades : context.awayGoaliePurchasedUpgrades;

        // Repair Drone (passive heal to all friendly minions)
        if (purchased.contains("fortress.t5.repairdrone")) {
            for (Entity e : context.entityPool) {
                if (e instanceof LaneMinion && e.team == team && e.getHealth() > 0.0) {
                    e.heal(0.04); // Passive minion heal
                }
            }
        }

        // Retrieve zone behaviors: No-Fly Zone, Barrages, Medics, Rush Lane, etc.
        for (Entity e : context.entityPool) {
            if (e.getHealth() <= 0.0) continue;

            // Barrage hazards spawned by us
            if (e instanceof Fire && e.team == team && activeBarrageIds.contains(e.id)) {
                // Apply damage & effects to enemy Titans and Minions overlapping the zone
                for (Titan t : context.players) {
                    if (t.team != team && t.health > 0.0 && e.asBounds().intersects(t.asBounds())) {
                        t.damage(context, context.c.getD("guardian.barrage.dmg"));
                        applyBarrageEffects(context, t, purchased);
                    }
                }
                for (Entity mn : context.entityPool) {
                    if (mn instanceof LaneMinion && mn.team != team && mn.getHealth() > 0.0 && e.asBounds().intersects(mn.asBounds())) {
                        mn.damage(context, context.c.getD("guardian.barrage.dmg"));
                        applyBarrageEffects(context, mn, purchased);
                    }
                }
            }

            // Forward Mines
            if (e instanceof Fire && e.team == team && e.width == 150 && !purchased.contains("siege.t5.forwardoutpost")) {
                for (Titan t : context.players) {
                    if (t.team != team && t.health > 0.0 && e.asBounds().intersects(t.asBounds())) {
                        t.damage(context, context.c.getD("guardian.barrage.dmg") * 0.75);
                        context.effectPool.addUniqueEffect(new RatioEffect(1200, t, EffectId.BURN, 1.0), context);
                    }
                }
                for (Entity mn : context.entityPool) {
                    if (mn instanceof LaneMinion && mn.team != team && mn.getHealth() > 0.0 && e.asBounds().intersects(mn.asBounds())) {
                        mn.damage(context, context.c.getD("guardian.barrage.dmg") * 0.75);
                        context.effectPool.addUniqueEffect(new RatioEffect(1200, mn, EffectId.BURN, 1.0), context);
                    }
                }
            }

            // Forward Outpost (solid defensive building)
            if (e instanceof Wall && e.team == team && e.width == 24) {
                // Radial chip damage to nearby enemies
                double range = 180.0;
                for (Titan t : context.players) {
                    if (t.team != team && t.health > 0.0 && util.Util.dist(e.X + 12, e.Y + 60, t.X + 35, t.Y + 35) <= range) {
                        t.damage(context, context.c.getD("guardian.outpost.dmg"));
                    }
                }
                for (Entity mn : context.entityPool) {
                    if (mn instanceof LaneMinion && mn.team != team && mn.getHealth() > 0.0 && util.Util.dist(e.X + 12, e.Y + 60, mn.X + 10, mn.Y + 10) <= range) {
                        mn.damage(context, context.c.getD("guardian.outpost.dmg"));
                    }
                }
            }

            // Forward Medics (continuous healing zone)
            if (e instanceof Fire && e.team == team && e.width == 160) {
                for (Titan t : context.players) {
                    if (t.team == team && t.health > 0.0 && e.asBounds().intersects(t.asBounds())) {
                        context.effectPool.addUniqueEffect(new HealEffect(1000, t, 1.5, 0.1), context);
                    }
                }
                for (Entity mn : context.entityPool) {
                    if (mn instanceof LaneMinion && mn.team == team && mn.getHealth() > 0.0 && e.asBounds().intersects(mn.asBounds())) {
                        mn.heal(0.12);
                    }
                }
            }

            // Rush Lane (kinetic force zone pushing units along Y lane)
            if (e instanceof Fire && e.team == team && e.height == 40) {
                double shoveSpeed = context.c.getD("guardian.shove.speed");
                if (shoveSpeed <= 0.0) shoveSpeed = 2.5;
                double dx = (team == TeamAffiliation.HOME) ? shoveSpeed : -shoveSpeed;
                
                for (Titan t : context.players) {
                    if (e.asBounds().intersects(t.asBounds())) {
                        t.translateBounded(context, dx, 0.0);
                    }
                }
                for (Entity mn : context.entityPool) {
                    if (mn instanceof LaneMinion && mn.getHealth() > 0.0 && e.asBounds().intersects(mn.asBounds())) {
                        mn.translateBounded(context, dx, 0.0);
                    }
                }
            }

            // Mana Vines (Slow/Burn hazard at base entrance)
            if (e instanceof Trap && e.team == team && e.width == 80) {
                for (Titan t : context.players) {
                    if (t.team != team && t.health > 0.0 && e.asBounds().intersects(t.asBounds())) {
                        context.effectPool.addUniqueEffect(new RatioEffect(2000, t, EffectId.SLOW, 1.40), context);
                        context.effectPool.addUniqueEffect(new RatioEffect(2000, t, EffectId.BURN, 1.0), context);
                    }
                }
                for (Entity mn : context.entityPool) {
                    if (mn instanceof LaneMinion && mn.team != team && mn.getHealth() > 0.0 && e.asBounds().intersects(mn.asBounds())) {
                        context.effectPool.addUniqueEffect(new RatioEffect(2000, mn, EffectId.SLOW, 1.40), context);
                        context.effectPool.addUniqueEffect(new RatioEffect(2000, mn, EffectId.BURN, 1.0), context);
                    }
                }
            }

            // Parapet elevated platform
            if (e instanceof Portal && e.width == 100) {
                for (Titan t : context.players) {
                    if (t.health > 0.0 && e.asBounds().intersects(t.asBounds())) {
                        // Apply brief root upon entering Parapet
                        if (!context.effectPool.hasEffect(t, EffectId.ROOT)) {
                            context.effectPool.addUniqueEffect(new EmptyEffect(1000, t, EffectId.ROOT), context);
                        }
                    }
                }
            }
        }

        // No-Fly Zone aura check (blocks LOB and boosts in defensive third)
        if (purchased.contains("fortress.t5.noflyzoneperm") || purchased.contains("fortress.t5.noflyzonetmp")) {
            for (Titan t : context.players) {
                if (t.team != team && t.health > 0.0) {
                    boolean inOwnEnd = (team == TeamAffiliation.HOME) ? (t.X <= 680) : (t.X >= 1368);
                    if (inOwnEnd) {
                        t.isBoosting = false;
                        if (t.actionState == Titan.TitanState.LOB) {
                            t.actionState = Titan.TitanState.SHOOT; // Turn lob into a regular shot
                        }
                    }
                }
            }
        }

        // Ice Portal aura check (continuously SLOWs all enemy minions)
        if (purchased.contains("cultivation.t6.iceportal")) {
            for (Entity mn : context.entityPool) {
                if (mn instanceof LaneMinion && mn.team != team && mn.getHealth() > 0.0) {
                    context.effectPool.addUniqueEffect(new RatioEffect(1000, mn, EffectId.SLOW, 1.25), context);
                }
            }
        }
    }

    private void applyBarrageEffects(GameEngine context, Entity target, Set<String> purchased) {
        if (purchased.contains("fortress.t5.icebarrage")) {
            context.effectPool.addUniqueEffect(new RatioEffect(
                context.c.getI("guardian.barrage.slow.dur"), 
                target, 
                EffectId.SLOW, 
                context.c.getD("guardian.barrage.slow.ratio")
            ), context);
        }
        if (purchased.contains("fortress.t5.firebarrage")) {
            context.effectPool.addUniqueEffect(new RatioEffect(
                context.c.getI("guardian.barrage.burn.dur"), 
                target, 
                EffectId.BURN, 
                1.0
            ), context);
        }
    }

    // Ability Spawner Helpers

    private void spawnWall(GameEngine context, Titan goalie, int hp, int lifetimeMs) {
        int sx = (int) goalie.X;
        int sy = (int) goalie.Y + 35 - 60;
        if (team == TeamAffiliation.HOME) {
            sx = context.c.GOALIE_XH_MAX + 50;
        } else {
            sx = context.c.GOALIE_XA_MIN - 50 - 12;
        }
        Wall w = new Wall(context, sx, sy);
        w.team = team;
        w.health = hp;
        w.maxHealth = hp;
        context.entityPool.add(w);
        entityExpiries.put(w.id, context.nowEpochMs + lifetimeMs);
    }

    private void spawnSnareTrap(GameEngine context, Titan goalie) {
        int sx = (int) goalie.X;
        int sy = (int) goalie.Y + 35 - 50;
        if (team == TeamAffiliation.HOME) {
            sx = context.c.GOALIE_XH_MAX + 120;
        } else {
            sx = context.c.GOALIE_XA_MIN - 120 - 100;
        }
        Trap t = new Trap(goalie, context, sx, sy);
        t.team = team;
        t.health = context.c.getD("guardian.snaretrap.permhealth");
        t.maxHealth = t.health;
        context.entityPool.add(t);
    }

    private void spawnBastionWalls(GameEngine context, Titan goalie) {
        Set<String> purchased = (team == TeamAffiliation.HOME) ? context.homeGoaliePurchasedUpgrades : context.awayGoaliePurchasedUpgrades;
        boolean hemmedIn = purchased.contains("fortress.t6.hemmedin");
        int wWidth = hemmedIn ? 18 : 12;
        int wHeight = hemmedIn ? 180 : 120;
        int hp = 999999;

        // Top lane and bottom lane walls
        int hY = 354 - wHeight / 2;
        int aY = 790 - wHeight / 2;
        int sx = (team == TeamAffiliation.HOME) ? context.c.GOALIE_XH_MAX + 150 : context.c.GOALIE_XA_MIN - 150 - wWidth;

        Wall w1 = new Wall(context, sx, hY);
        w1.team = team; w1.width = wWidth; w1.height = wHeight; w1.health = hp; w1.maxHealth = hp;
        context.entityPool.add(w1);

        Wall w2 = new Wall(context, sx, aY);
        w2.team = team; w2.width = wWidth; w2.height = wHeight; w2.health = hp; w2.maxHealth = hp;
        context.entityPool.add(w2);

        // Hemmed In extra goal wall
        if (hemmedIn) {
            int gx = (team == TeamAffiliation.HOME) ? context.c.GOALIE_XH_MIN - 50 : context.c.GOALIE_XA_MAX + 50;
            Wall w3 = new Wall(context, gx, 583 - wHeight / 2);
            w3.team = team; w3.width = wWidth; w3.height = wHeight; w3.health = hp; w3.maxHealth = hp;
            context.entityPool.add(w3);
        }
    }

    private void spawnBarrageConfig(GameEngine context, int config) {
        Titan goalie = (team == TeamAffiliation.HOME) ? context.players[0] : context.players[1];
        int bx = (team == TeamAffiliation.HOME) ? 250 : 1670;
        int by = 0;
        if (config == 1) {
            by = 150;
        } else if (config == 2) {
            by = 470;
        } else {
            by = 790;
        }
        
        Fire f = new Fire(goalie, bx, by);
        f.team = team;
        f.width = 150;
        f.height = 280;
        f.health = 99999;
        f.maxHealth = 99999;
        context.entityPool.add(f);
        activeBarrageIds.add(f.id);
    }

    private void clearBarrageConfig(GameEngine context) {
        context.entityPool.removeIf(e -> activeBarrageIds.contains(e.id));
        activeBarrageIds.clear();
    }

    private void applyHemmedIn(GameEngine context, Titan goalie) {
        int wWidth = 18;
        int wHeight = 180;
        int hp = 999999;
        for (Entity e : context.entityPool) {
            if (e instanceof Wall && e.team == team) {
                boolean isBastion = false;
                if (team == TeamAffiliation.HOME) {
                    if (Math.abs(e.X - (context.c.GOALIE_XH_MAX + 150)) <= 20) isBastion = true;
                } else {
                    if (Math.abs(e.X - (context.c.GOALIE_XA_MIN - 150 - 12)) <= 20 || Math.abs(e.X - (context.c.GOALIE_XA_MIN - 150 - 18)) <= 20) isBastion = true;
                }
                if (isBastion) {
                    e.width = wWidth;
                    e.height = wHeight;
                    if (e.Y < 500) {
                        e.Y = 354 - wHeight / 2;
                    } else {
                        e.Y = 790 - wHeight / 2;
                    }
                }
            }
        }
        int gx = (team == TeamAffiliation.HOME) ? context.c.GOALIE_XH_MIN - 50 : context.c.GOALIE_XA_MAX + 50;
        Wall w3 = new Wall(context, gx, 583 - wHeight / 2);
        w3.team = team;
        w3.width = wWidth;
        w3.height = wHeight;
        w3.health = hp;
        w3.maxHealth = hp;
        context.entityPool.add(w3);
    }

    private void spawnForwardMines(GameEngine context, Titan goalie) {
        Set<String> purchased = (team == TeamAffiliation.HOME) ? context.homeGoaliePurchasedUpgrades : context.awayGoaliePurchasedUpgrades;
        int mx = (team == TeamAffiliation.HOME) ? 1600 : 400;
        int my = 583 - 75;

        if (purchased.contains("siege.t5.forwardoutpost")) {
            // Spawn Outpost standing structure instead of mines
            Wall outpost = new Wall(context, mx, my);
            outpost.team = team;
            outpost.width = 24;
            outpost.height = 120;
            outpost.health = context.c.getD("guardian.outpost.health");
            outpost.maxHealth = outpost.health;
            context.entityPool.add(outpost);
        } else {
            Fire mines = new Fire(goalie, mx, my);
            mines.team = team;
            mines.width = 150;
            mines.height = 150;
            mines.health = 99999;
            mines.maxHealth = 99999;
            context.entityPool.add(mines);
        }
    }

    private void spawnBallPortalRough(GameEngine context, Titan goalie) {
        int px = (team == TeamAffiliation.HOME) ? context.c.GOALIE_XH_MAX + 350 : context.c.GOALIE_XA_MIN - 350 - 50;
        int py1 = 354 - 25;
        int py2 = 790 - 25;
        
        BallPortal p1 = new BallPortal(team, goalie, context.entityPool, px, py1, context) {
            @Override
            public void triggerCollide(GameEngine ctx, Box entity) {
                if (!this.isCooldown(new Instant(ctx.nowEpochMs)) && ctx.ball.id.equals(entity.id) && !ctx.anyPoss() && !ctx.contactExemptBall()) {
                    entity.setX(px);
                    entity.setY(py2);
                    ctx.effectPool.addUniqueEffect(new EmptyEffect(2000, this, EffectId.COOLDOWN_GOALIE), ctx);
                    for (Entity other : ctx.entityPool) {
                        if (other instanceof BallPortal && other.team == team && other.Y == py2) {
                            ctx.effectPool.addUniqueEffect(new EmptyEffect(2000, other, EffectId.COOLDOWN_GOALIE), ctx);
                        }
                    }
                }
            }
        };
        p1.team = team; p1.health = 99999; p1.maxHealth = 99999;
        context.entityPool.add(p1);

        BallPortal p2 = new BallPortal(team, goalie, context.entityPool, px, py2, context) {
            @Override
            public void triggerCollide(GameEngine ctx, Box entity) {
                if (!this.isCooldown(new Instant(ctx.nowEpochMs)) && ctx.ball.id.equals(entity.id) && !ctx.anyPoss() && !ctx.contactExemptBall()) {
                    entity.setX(px);
                    entity.setY(py1);
                    ctx.effectPool.addUniqueEffect(new EmptyEffect(2000, this, EffectId.COOLDOWN_GOALIE), ctx);
                    for (Entity other : ctx.entityPool) {
                        if (other instanceof BallPortal && other.team == team && other.Y == py1) {
                            ctx.effectPool.addUniqueEffect(new EmptyEffect(2000, other, EffectId.COOLDOWN_GOALIE), ctx);
                        }
                    }
                }
            }
        };
        p2.team = team; p2.health = 99999; p2.maxHealth = 99999;
        context.entityPool.add(p2);
    }

    private void spawnRushLane(GameEngine context, Titan goalie) {
        int lane = randIndex();
        int ly = (lane == 0 ? 354 : lane == 1 ? 583 : 790) - 20;
        Fire rl = new Fire(goalie, 36, ly);
        rl.team = team;
        rl.width = 1976;
        rl.height = 40;
        rl.health = 99999;
        rl.maxHealth = 99999;
        context.entityPool.add(rl);
    }

    private void spawnParapet(GameEngine context, Titan goalie) {
        int px = 1024 - 50;
        int py = 354 - 50;
        Portal platform = new Portal(team, goalie, context.entityPool, px, py, context);
        platform.team = team;
        platform.width = 100;
        platform.height = 100;
        platform.health = 99999;
        platform.maxHealth = 99999;
        context.entityPool.add(platform);
    }

    private void spawnSecondBall(GameEngine context) {
        SecondBall second = new SecondBall(1040, 609);
        context.entityPool.add(second);
    }

    private void spawnHeroPortals(GameEngine context, Titan goalie) {
        int offset = context.c.getI("guardian.heroportals.xoffset");
        int x1 = (team == TeamAffiliation.HOME) ? 1024 + offset : 1024 - offset;

        Portal p1 = new Portal(team, goalie, context.entityPool, x1, 354 - 25, context);
        p1.team = team; p1.health = 99999; p1.maxHealth = 99999;
        context.entityPool.add(p1);

        Portal p2 = new Portal(team, goalie, context.entityPool, x1, 790 - 25, context);
        p2.team = team; p2.health = 99999; p2.maxHealth = 99999;
        context.entityPool.add(p2);
    }

    private void spawnManaVines(GameEngine context, Titan goalie) {
        int vx = (team == TeamAffiliation.HOME) ? context.c.GOALIE_XH_MAX + 100 : context.c.GOALIE_XA_MIN - 100 - 80;
        int vy = 583 - 100;
        Trap vines = new Trap(goalie, context, vx, vy);
        vines.team = team;
        vines.width = 80;
        vines.height = 200;
        vines.health = 99999;
        vines.maxHealth = 99999;
        context.entityPool.add(vines);
    }

    private void spawnForwardMedics(GameEngine context, Titan goalie) {
        int mx = (team == TeamAffiliation.HOME) ? 1600 : 400;
        int my = 583 - 80;
        Fire medics = new Fire(goalie, mx, my);
        medics.team = team;
        medics.width = 160;
        medics.height = 160;
        medics.health = 99999;
        medics.maxHealth = 99999;
        context.entityPool.add(medics);
    }

    // Active Buffs / Resource Actions

    private void applyHealingBurst(GameEngine context, Titan goalie) {
        Set<String> purchased = (team == TeamAffiliation.HOME) ? context.homeGoaliePurchasedUpgrades : context.awayGoaliePurchasedUpgrades;
        double mult = purchased.contains("fortress.t3.homehealamp") ? context.c.getD("guardian.homehealamp.mult") : 1.0;
        double amt = context.c.getD("guardian.healingburst.amt") * mult;
        int dur = context.c.getI("guardian.healingburst.dur");

        for (Titan t : context.players) {
            if (t.team == team && t.health > 0.0) {
                boolean inOwnEnd = (team == TeamAffiliation.HOME) ? (t.X <= 680) : (t.X >= 1368);
                if (inOwnEnd) {
                    context.effectPool.addUniqueEffect(new HealEffect(dur, t, amt, amt * 0.05), context);
                }
            }
        }
    }

    private void applyEnergyRush(GameEngine context) {
        List<Titan> pool = new ArrayList<>();
        for (Titan t : context.players) {
            if (t.team == team && t.health > 0.0 && t.getType() != TitanType.GOALIE) {
                pool.add(t);
            }
        }
        if (!pool.isEmpty()) {
            Titan randomHero = pool.get(new Random().nextInt(pool.size()));
            context.effectPool.addUniqueEffect(new RatioEffect(3000, randomHero, EffectId.FAST, 1.35), context);
        }
    }

    private void spawnSiegeMinion(GameEngine context, Titan goalie) {
        int lane = randIndex();
        double sx = getSpawnX(context, lane, team);
        double sy = getSpawnY(context, lane);
        LaneMinion sm = new LaneMinion(sx, sy, team, lane);
        sm.health = context.c.getD("minion.heavy.health");
        sm.maxHealth = sm.health;
        context.entityPool.add(sm);
    }

    private void applyAnchor(GameEngine context, Titan goalie) {
        List<Titan> allies = new ArrayList<>();
        List<Titan> enemies = new ArrayList<>();
        for (Titan t : context.players) {
            if (t.health > 0.0 && t.getType() != TitanType.GOALIE) {
                if (t.team == team) allies.add(t); else enemies.add(t);
            }
        }
        if (!allies.isEmpty() && !enemies.isEmpty()) {
            tetherCaster = allies.get(new Random().nextInt(allies.size()));
            tetherTarget = enemies.get(new Random().nextInt(enemies.size()));
            tetherUntilMs = context.nowEpochMs + 8000;
        }
    }

    private void applyShockGrenade(GameEngine context) {
        List<Titan> enemies = new ArrayList<>();
        for (Titan t : context.players) {
            if (t.team != team && t.health > 0.0 && t.getType() != TitanType.GOALIE) {
                enemies.add(t);
            }
        }
        if (!enemies.isEmpty()) {
            Titan enemy = enemies.get(new Random().nextInt(enemies.size()));
            context.effectPool.addUniqueEffect(new EmptyEffect(250, enemy, EffectId.STUN), context);
        }
    }

    private void disableEnemyWalls(GameEngine context) {
        // Find enemy goalie abilities instance and set walls down
        GuardianAbilities enemyGa = (team == TeamAffiliation.HOME) ? context.awayGoalieAbilities : context.homeGoalieAbilities;
        if (enemyGa != null) {
            // Make their walls solid=false for 1000ms
            for (Entity e : context.entityPool) {
                if (e instanceof Wall && e.team == enemyGa.team) {
                    e.solid = false;
                }
            }
            // Schedule resetting them
            context.effectPool.addUniqueEffect(new CallbackEffect(1000, goalieOfTeam(context, enemyGa.team), EffectId.COOLDOWN_STEAL, () -> {
                for (Entity wall : context.entityPool) {
                    if (wall instanceof Wall && wall.team == enemyGa.team && wall.maxHealth > 100.0) {
                        wall.solid = true;
                    }
                }
            }), context);
        }
    }

    private Titan goalieOfTeam(GameEngine context, TeamAffiliation t) {
        return (t == TeamAffiliation.HOME) ? context.players[0] : context.players[1];
    }

    private void applySharpshooter(GameEngine context) {
        List<Titan> pool = new ArrayList<>();
        for (Titan t : context.players) {
            if (t.team == team && t.health > 0.0 && t.getType() != TitanType.GOALIE) {
                pool.add(t);
            }
        }
        if (!pool.isEmpty()) {
            Titan target = pool.get(new Random().nextInt(pool.size()));
            double oldPower = target.throwPower;
            double oldRange = target.rangeFactor;
            target.throwPower *= 1.20;
            target.rangeFactor *= 1.20;
            // Restore after 10s
            context.effectPool.addUniqueEffect(new CallbackEffect(10000, target, EffectId.COOLDOWN_Q, () -> {
                target.throwPower = oldPower;
                target.rangeFactor = oldRange;
            }), context);
        }
    }

    private void refillFuel(GameEngine context) {
        for (Titan t : context.players) {
            if (t.team == team && t.health > 0.0) {
                t.fuel = getMaxFuel(context);
            }
        }
    }

    private void resetCooldowns(GameEngine context) {
        List<Effect> rm = new ArrayList<>();
        for (Effect eff : context.effectPool.getEffects()) {
            if (eff.on.team == team && (eff.getEffect() == EffectId.COOLDOWN_Q || eff.getEffect() == EffectId.COOLDOWN_W)) {
                rm.add(eff);
            }
        }
        for (Effect eff : rm) {
            eff.cull(context);
            context.effectPool.getEffects().remove(eff);
        }
    }

    private void manaSurge(GameEngine context) {
        resetCooldowns(context);
        refillFuel(context);
        for (Titan t : context.players) {
            if (t.team == team && t.health > 0.0) {
                t.health = t.maxHealth;
            }
        }
    }

    private void manaSummon(GameEngine context, Titan goalie) {
        for (int i = 0; i < 2; i++) {
            int lane = randIndex();
            double sx = getSpawnX(context, lane, team);
            double sy = getSpawnY(context, lane);
            LaneMinion sm = new LaneMinion(sx, sy, team, lane);
            sm.health = context.c.getD("minion.heavy.health");
            sm.maxHealth = sm.health;
            context.entityPool.add(sm);
        }
    }

    private void triggerRiskAdjustedReturn(GameEngine context) {
        if (team == TeamAffiliation.HOME) {
            context.away.score += 1.0;
        } else {
            context.home.score += 1.0;
        }
        riskAdjustedReturnUntilMs = context.nowEpochMs + 150000;
        riskAdjustedReturnPending = true;
    }

    private void triggerTripleDown(GameEngine context) {
        if (team == TeamAffiliation.HOME) {
            context.away.score += 0.75;
            context.home.score += 1.0;
        } else {
            context.home.score += 0.75;
            context.away.score += 1.0;
        }
    }

    private void widenFieldBounds(GameEngine context) {
        if (fieldDilated) return;
        fieldDilated = true;

        Const c = context.c;
        double factor = 1.30;
        
        // Center X is 1024, Center Y is 609
        int width = c.MAX_X - c.MIN_X;
        c.MIN_X = (int) (1024 - (width * factor) / 2);
        c.MAX_X = (int) (1024 + (width * factor) / 2);

        int height = c.MAX_Y - c.MIN_Y;
        c.MIN_Y = (int) (609 - (height * factor) / 2);
        c.MAX_Y = (int) (609 + (height * factor) / 2);

        int eWidth = c.E_MAX_X - c.E_MIN_X;
        c.E_MIN_X = (int) (1024 - (eWidth * factor) / 2);
        c.E_MAX_X = (int) (1024 + (eWidth * factor) / 2);

        int eHeight = c.E_MAX_Y - c.E_MIN_Y;
        c.E_MIN_Y = (int) (609 - (eHeight * factor) / 2);
        c.E_MAX_Y = (int) (609 + (eHeight * factor) / 2);
    }

    private void applyRosterStatBoosts(GameEngine context) {
        Set<String> purchased = (team == TeamAffiliation.HOME) ? context.homeGoaliePurchasedUpgrades : context.awayGoaliePurchasedUpgrades;

        int speedCount = 0;
        int throwCount = 0;
        int rangeCount = 0;
        int cdCount = 0;
        int durCount = 0;
        int hpCount = 0;
        int painCount = 0;

        if (purchased.contains("empowerment.t3.grit")) { hpCount++; painCount++; }
        if (purchased.contains("empowerment.t3.marksmanship")) { throwCount++; rangeCount++; }
        if (purchased.contains("empowerment.t3.footwork")) { speedCount++; }
        if (purchased.contains("empowerment.t3.discipline")) { cdCount++; durCount++; }
        if (purchased.contains("empowerment.t6.apexform")) {
            speedCount += 2; throwCount += 2; rangeCount += 2; cdCount += 2;
            durCount += 2; hpCount += 2; painCount += 2;
        }

        // Focused Training logic (+1 speed as choice)
        int focusTimes = countPurchases(purchased, "empowerment.t5.focusedtraining");
        speedCount += focusTimes;

        for (Titan t : context.players) {
            if (t.team == team && t.getType() != null && t.getType() != TitanType.GOALIE) {
                // Apply enhancements
                t.speed = t.baseSpeed * Math.pow(context.c.getD("masteries.speed.mult"), speedCount);
                t.throwPower = t.baseThrowPower * Math.pow(context.c.getD("masteries.throw.mult"), throwCount);
                t.rangeFactor = t.baseRangeFactor * Math.pow(context.c.getD("masteries.range.mult"), rangeCount);
                t.cooldownFactor = t.baseCooldownFactor / Math.pow(context.c.getD("masteries.cooldowns.mult"), cdCount);
                t.durationsFactor = t.baseDurationsFactor * Math.pow(context.c.getD("masteries.effectDuration.mult"), durCount);
                t.maxHealth = t.baseMaxHealth * Math.pow(context.c.getD("masteries.health.mult"), hpCount);
                t.painReduction = t.basePainReduction * Math.pow(context.c.getD("masteries.painReduction.mult"), painCount);
            }
        }
    }

    private void scaleAlliedTitans(GameEngine context) {
        for (Titan t : context.players) {
            if (t.team == team && t.getType() != TitanType.GOALIE) {
                t.width = (int) (70 * 1.25);
                t.height = (int) (70 * 1.25);
            }
        }
    }

    // Dynamic Helpers

    public double getMaxMana(GameEngine context) {
        Set<String> purchased = (team == TeamAffiliation.HOME) ? context.homeGoaliePurchasedUpgrades : context.awayGoaliePurchasedUpgrades;
        return purchased.contains("cultivation.t3.highermanacap") ? 1000.0 : 500.0;
    }

    public double getMaxFuel(GameEngine context) {
        Set<String> purchased = (team == TeamAffiliation.HOME) ? context.homeGoaliePurchasedUpgrades : context.awayGoaliePurchasedUpgrades;
        return purchased.contains("empowerment.t4.fuelreserves") ? 150.0 : 100.0;
    }

    private int countPurchases(Set<String> purchased, String prefix) {
        int count = 0;
        for (String key : purchased) {
            if (key.startsWith(prefix)) count++;
        }
        return count;
    }

    private int randIndex() {
        return new Random().nextInt(3);
    }

    private double getSpawnX(GameEngine context, int lane, TeamAffiliation team) {
        int goalW = (lane == 1) ? context.c.getI("goal.hi.width") : context.c.getI("goal.low.width");
        int goalX = (team == TeamAffiliation.HOME)
            ? ((lane == 1) ? context.c.getI("goal.home.hi.x") : context.c.getI("goal.home.low.x"))
            : ((lane == 1) ? context.c.getI("goal.away.hi.x") : context.c.getI("goal.away.low.x"));
        return goalX + goalW / 2.0;
    }

    private double getSpawnY(GameEngine context, int lane) {
        int goalH = (lane == 1) ? context.c.getI("goal.hi.height") : context.c.getI("goal.low.height");
        int goalY = (lane == 0) ? context.c.getI("goal.low.y") : (lane == 1 ? context.c.getI("goal.hi.y") : context.c.getI("goal.low2.y"));
        return goalY + goalH / 2.0;
    }

    private void spawnWallAt(GameEngine context, Titan goalie, int hp, int lifetimeMs, int y) {
        int sx = (team == TeamAffiliation.HOME) ? context.c.GOALIE_XH_MAX + 50 : context.c.GOALIE_XA_MIN - 50 - 12;
        Wall w = new Wall(context, sx, y);
        w.team = team;
        w.health = hp;
        w.maxHealth = hp;
        context.entityPool.add(w);
        entityExpiries.put(w.id, context.nowEpochMs + lifetimeMs);
    }



    private void spawnWallPortals(GameEngine context, Titan goalie) {
        double wallX = (team == TeamAffiliation.HOME) ? context.c.MIN_X : context.c.MAX_X;
        double midX = 1024.0;
        int count = context.c.getI("guardian.wallportals.count");
        int cd = context.c.getI("guardian.wallportals.cooldown");
        
        for (int i = 0; i < count; i++) {
            double y = context.c.MIN_Y + 50.0 + i * (context.c.MAX_Y - context.c.MIN_Y - 100.0) / (count - 1.0);
            final double targetY = y;
            
            BallPortal pWall = new BallPortal(team, goalie, context.entityPool, (int)wallX, (int)y, context) {
                @Override
                public void triggerCollide(GameEngine ctx, Box entity) {
                    if (!this.isCooldown(new Instant(ctx.nowEpochMs)) && ctx.ball.id.equals(entity.id) && !ctx.anyPoss() && !ctx.contactExemptBall()) {
                        entity.setX(midX);
                        entity.setY(targetY);
                        ctx.effectPool.addUniqueEffect(new EmptyEffect(cd, this, EffectId.COOLDOWN_GOALIE), ctx);
                        for (Entity other : ctx.entityPool) {
                            if (other instanceof BallPortal && other.team == team && other.X == (int)midX && Math.abs(other.Y - targetY) < 5.0) {
                                ctx.effectPool.addUniqueEffect(new EmptyEffect(cd, other, EffectId.COOLDOWN_GOALIE), ctx);
                            }
                        }
                    }
                }
            };
            pWall.team = team; pWall.health = 99999; pWall.maxHealth = 99999;
            pWall.width = 40; pWall.height = 40;
            context.entityPool.add(pWall);

            BallPortal pMid = new BallPortal(team, goalie, context.entityPool, (int)midX, (int)y, context) {
                @Override
                public void triggerCollide(GameEngine ctx, Box entity) {
                    if (!this.isCooldown(new Instant(ctx.nowEpochMs)) && ctx.ball.id.equals(entity.id) && !ctx.anyPoss() && !ctx.contactExemptBall()) {
                        entity.setX(wallX);
                        entity.setY(targetY);
                        ctx.effectPool.addUniqueEffect(new EmptyEffect(cd, this, EffectId.COOLDOWN_GOALIE), ctx);
                        for (Entity other : ctx.entityPool) {
                            if (other instanceof BallPortal && other.team == team && other.X == (int)wallX && Math.abs(other.Y - targetY) < 5.0) {
                                ctx.effectPool.addUniqueEffect(new EmptyEffect(cd, other, EffectId.COOLDOWN_GOALIE), ctx);
                            }
                        }
                    }
                }
            };
            pMid.team = team; pMid.health = 99999; pMid.maxHealth = 99999;
            pMid.width = 40; pMid.height = 40;
            context.entityPool.add(pMid);
        }
    }

    public void recreatePermanentEntities(GameEngine context) {
        Titan goalie = (team == TeamAffiliation.HOME) ? context.players[0] : context.players[1];
        Set<String> purchased = (team == TeamAffiliation.HOME) ? context.homeGoaliePurchasedUpgrades : context.awayGoaliePurchasedUpgrades;

        if (purchased.contains("fortress.t3.snaretrap")) {
            spawnSnareTrap(context, goalie);
        }
        if (purchased.contains("fortress.t4.bastionprotocol")) {
            spawnBastionWalls(context, goalie);
        }

        if (purchased.contains("siege.t3.rushlane")) {
            spawnRushLane(context, goalie);
        }
        if (purchased.contains("siege.t3.forwardmines")) {
            spawnForwardMines(context, goalie);
        }
        if (purchased.contains("siege.t3.ballportal_rough")) {
            spawnBallPortalRough(context, goalie);
        }
        if (purchased.contains("siege.t4.parapet")) {
            spawnParapet(context, goalie);
        }
        if (purchased.contains("siege.t6.forwardmedics")) {
            spawnForwardMedics(context, goalie);
        }
        if (purchased.contains("empowerment.t4.heroportals")) {
            spawnHeroPortals(context, goalie);
        }
        if (purchased.contains("cultivation.t4.manavines")) {
            spawnManaVines(context, goalie);
        }
        if (purchased.contains("cultivation.t6.wallportals")) {
            spawnWallPortals(context, goalie);
        }
    }
}
