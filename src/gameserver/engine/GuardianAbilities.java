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
    public List<Integer> activeBarrageRegions = new ArrayList<>();
    public List<String> activeBarrageTypes = new ArrayList<>();
    public List<Integer> pendingBarrageRegions = new ArrayList<>();
    public List<String> pendingBarrageTypes = new ArrayList<>();
    public Set<Integer> lastBarrageRegions = new HashSet<>();
    public Map<UUID, String> barrageEntityTypes = new HashMap<>();
    public int barrageCycleTick = 0;
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
    public transient Set<UUID> titansOnParapetLastTick = new HashSet<>();
    public transient Set<UUID> enemiesInVinesLastTick = new HashSet<>();

    public GuardianAbilities() {
        // No-arg constructor for Jackson deserialization
    }

    public GuardianAbilities(TeamAffiliation team) {
        this.team = team;
    }

    public void purchaseOrUse(GameEngine context, Titan goalie, String nodeKey) {
        if (nodeKey == null) return;
        Set<String> purchased = (team == TeamAffiliation.HOME) ? context.homeGoaliePurchasedUpgrades : context.awayGoaliePurchasedUpgrades;

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
        } else if (nodeKey.endsWith(".barrage") || nodeKey.endsWith(".icebarrage") || nodeKey.endsWith(".firebarrage")) {
            clearBarrageConfig(context);
            lastBarrageRegions.clear();
            activeBarrageRegions.clear();
            activeBarrageTypes.clear();
            pendingBarrageRegions.clear();
            pendingBarrageTypes.clear();
            rollNextBarrage(purchased);
            activeBarrageRegions.addAll(pendingBarrageRegions);
            activeBarrageTypes.addAll(pendingBarrageTypes);
            pendingBarrageRegions.clear();
            pendingBarrageTypes.clear();
            for (int i = 0; i < activeBarrageRegions.size(); i++) {
                spawnBarrageRegion(context, activeBarrageRegions.get(i), activeBarrageTypes.get(i));
            }
            barrageCycleTick = 0;
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
        } else if (nodeKey.endsWith(".incendiarymines") || nodeKey.endsWith(".forwardoutpost")) {
            context.entityPool.removeIf(e -> e instanceof Fire && e.team == team && (e.width == 80 || e.width == 150));
            spawnForwardMines(context, goalie);
        } else if (nodeKey.endsWith(".ballportal")) {
            spawnBallPortal(context, goalie);
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

        // 3x3 Nine Regions Dynamic moving barrage logic
        // Duty Cycle:
        // 0.0s – 3.0s (ticks 0..119): effect starts/runs (active hazards on board, no telegraph)
        // 3.0s – 5.0s (ticks 120..199): effect continues, NEXT BARRAGE IS TELEGRAPHED IN A NEW AREA (#b8f9ff at 50% opacity)
        // 5.0s -> 0.0s (tick 200): next cycle begins, prior effect stops.
        // Elemental barrages purchased without base barrage (via mana pollinate) also run the
        // cycle, but rollNextBarrage caps them at 50% spawn probability per cycle.
        boolean hasAnyBarrage = purchased.contains("fortress.t4.barrage")
            || purchased.contains("fortress.t5.icebarrage")
            || purchased.contains("fortress.t5.firebarrage");
        if (hasAnyBarrage) {
            barrageCycleTick++;
            if (activeBarrageRegions.isEmpty() && pendingBarrageRegions.isEmpty()) {
                rollNextBarrage(purchased);
                activeBarrageRegions.addAll(pendingBarrageRegions);
                activeBarrageTypes.addAll(pendingBarrageTypes);
                pendingBarrageRegions.clear();
                pendingBarrageTypes.clear();
                for (int i = 0; i < activeBarrageRegions.size(); i++) {
                    spawnBarrageRegion(context, activeBarrageRegions.get(i), activeBarrageTypes.get(i));
                }
                barrageCycleTick = 0;
            }
            if (barrageCycleTick == 120) {
                // At 3.0s: effect continues, telegraph next barrage in a new area for 2.0s
                rollNextBarrage(purchased);
            } else if (barrageCycleTick >= 200) {
                // At 5.0s: prior effect stops, telegraphed barrage becomes active
                barrageCycleTick = 0;
                clearBarrageConfig(context);
                lastBarrageRegions.clear();
                lastBarrageRegions.addAll(activeBarrageRegions);
                activeBarrageRegions.clear();
                activeBarrageRegions.addAll(pendingBarrageRegions);
                activeBarrageTypes.clear();
                activeBarrageTypes.addAll(pendingBarrageTypes);
                pendingBarrageRegions.clear();
                pendingBarrageTypes.clear();
                for (int i = 0; i < activeBarrageRegions.size(); i++) {
                    spawnBarrageRegion(context, activeBarrageRegions.get(i), activeBarrageTypes.get(i));
                }
            }
        } else {
            if (!activeBarrageIds.isEmpty() || !activeBarrageRegions.isEmpty() || !pendingBarrageRegions.isEmpty()) {
                clearBarrageConfig(context);
                activeBarrageRegions.clear();
                activeBarrageTypes.clear();
                pendingBarrageRegions.clear();
                pendingBarrageTypes.clear();
                lastBarrageRegions.clear();
                barrageCycleTick = 0;
            }
        }

        // Synchronize barrage state directly to Game context for client serialization
        if (team == TeamAffiliation.HOME) {
            context.homeActiveBarrageRegions = new ArrayList<>(activeBarrageRegions);
            context.homeActiveBarrageTypes = new ArrayList<>(activeBarrageTypes);
            context.homePendingBarrageRegions = new ArrayList<>(pendingBarrageRegions);
            context.homePendingBarrageTypes = new ArrayList<>(pendingBarrageTypes);
        } else {
            context.awayActiveBarrageRegions = new ArrayList<>(activeBarrageRegions);
            context.awayActiveBarrageTypes = new ArrayList<>(activeBarrageTypes);
            context.awayPendingBarrageRegions = new ArrayList<>(pendingBarrageRegions);
            context.awayPendingBarrageTypes = new ArrayList<>(pendingBarrageTypes);
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
                String bType = barrageEntityTypes.getOrDefault(e.id, "BASE");
                double baseDmg = context.c.getD("guardian.barrage.dmg");
                double dmg = "FIRE".equals(bType) ? baseDmg : (baseDmg * 0.5);

                // Apply damage & effects to enemy Titans and Minions overlapping the zone
                for (Titan t : context.players) {
                    if (t.team != team && t.health > 0.0 && e.asBounds().intersects(t.asBounds())) {
                        t.damage(context, dmg);
                        applyBarrageRegionEffects(context, t, bType);
                    }
                }
                for (Entity mn : context.entityPool) {
                    if (mn instanceof LaneMinion && mn.team != team && mn.getHealth() > 0.0 && e.asBounds().intersects(mn.asBounds())) {
                        mn.damage(context, dmg);
                        applyBarrageRegionEffects(context, mn, bType);
                    }
                }
            }

            // Forward Mines & Incendiary Mines
            if (e instanceof Fire && e.team == team && (e.width == 80 || e.width == 150)) {
                boolean isIncendiary = (e.width == 150) || purchased.contains("siege.t5.incendiarymines") || purchased.contains("siege.t5.forwardoutpost");
                double dmg = context.c.getD("guardian.barrage.dmg") * 0.75;
                for (Titan t : context.players) {
                    if (t.team != team && t.health > 0.0 && e.asBounds().intersects(t.asBounds())) {
                        t.damage(context, dmg);
                        if (isIncendiary) {
                            context.effectPool.addUniqueEffect(new RatioEffect(1200, t, EffectId.BURN, 1.0), context);
                        }
                    }
                }
                for (Entity mn : context.entityPool) {
                    if (mn instanceof LaneMinion && mn.team != team && mn.getHealth() > 0.0 && e.asBounds().intersects(mn.asBounds())) {
                        mn.damage(context, dmg);
                        if (isIncendiary) {
                            context.effectPool.addUniqueEffect(new RatioEffect(1200, mn, EffectId.BURN, 1.0), context);
                        }
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

            // Mana Vines (Slow/Burn hazard at base entrance + 30 mana on pass-through)
            if (e instanceof Trap && e.team == team && e.width == 80) {
                if (enemiesInVinesLastTick == null) {
                    enemiesInVinesLastTick = new HashSet<>();
                }
                Set<UUID> currentVinesOverlaps = new HashSet<>();
                for (Titan t : context.players) {
                    if (t.team != team && t.health > 0.0 && e.asBounds().intersects(t.asBounds())) {
                        currentVinesOverlaps.add(t.id);
                        if (!enemiesInVinesLastTick.contains(t.id)) {
                            if (team == TeamAffiliation.HOME) {
                                context.homeGoalieMana = Math.min(getMaxMana(context), context.homeGoalieMana + 30.0);
                            } else {
                                context.awayGoalieMana = Math.min(getMaxMana(context), context.awayGoalieMana + 30.0);
                            }
                        }
                        context.effectPool.addUniqueEffect(new RatioEffect(2000, t, EffectId.SLOW, 1.40), context);
                        context.effectPool.addUniqueEffect(new RatioEffect(2000, t, EffectId.BURN, 1.0), context);
                    }
                }
                for (Entity mn : context.entityPool) {
                    if (mn instanceof LaneMinion && mn.team != team && mn.getHealth() > 0.0 && e.asBounds().intersects(mn.asBounds())) {
                        currentVinesOverlaps.add(mn.id);
                        if (!enemiesInVinesLastTick.contains(mn.id)) {
                            if (team == TeamAffiliation.HOME) {
                                context.homeGoalieMana = Math.min(getMaxMana(context), context.homeGoalieMana + 30.0);
                            } else {
                                context.awayGoalieMana = Math.min(getMaxMana(context), context.awayGoalieMana + 30.0);
                            }
                        }
                        context.effectPool.addUniqueEffect(new RatioEffect(2000, mn, EffectId.SLOW, 1.40), context);
                        context.effectPool.addUniqueEffect(new RatioEffect(2000, mn, EffectId.BURN, 1.0), context);
                    }
                }
                enemiesInVinesLastTick.clear();
                enemiesInVinesLastTick.addAll(currentVinesOverlaps);
            }

            // Parapet elevated platform
            if (e instanceof Parapet) {
                if (titansOnParapetLastTick == null) {
                    titansOnParapetLastTick = new HashSet<>();
                }
                Set<UUID> currentOverlaps = new HashSet<>();
                for (Titan t : context.players) {
                    if (t.health > 0.0 && e.asBounds().intersects(t.asBounds())) {
                        currentOverlaps.add(t.id);
                        if (!titansOnParapetLastTick.contains(t.id)) {
                            context.effectPool.addUniqueEffect(new EmptyEffect(1000, t, EffectId.ROOT), context);
                        }
                    }
                }
                titansOnParapetLastTick.clear();
                titansOnParapetLastTick.addAll(currentOverlaps);
            }
        }



        // Ice Portal aura check (continuously SLOWs all enemy heroes by 10%)
        if (purchased.contains("cultivation.t6.iceportal")) {
            for (Titan t : context.players) {
                if (t.team != team && t.health > 0.0 && t.getType() != TitanType.GOALIE) {
                    context.effectPool.addUniqueEffect(new RatioEffect(1000, t, EffectId.SLOW, 1.111), context);
                }
            }
        }
        applyRosterStatBoosts(context);
    }

    private void applyBarrageRegionEffects(GameEngine context, Entity target, String bType) {
        if ("ICE".equals(bType)) {
            context.effectPool.addUniqueEffect(new RatioEffect(
                context.c.getI("guardian.barrage.slow.dur"), 
                target, 
                EffectId.SLOW, 
                context.c.getD("guardian.barrage.slow.ratio")
            ), context);
        } else if ("FIRE".equals(bType)) {
            context.effectPool.addUniqueEffect(new RatioEffect(
                context.c.getI("guardian.barrage.burn.dur"), 
                target, 
                EffectId.BURN, 
                1.0
            ), context);
        }
    }

    public static class RegionBounds {
        public int x, y, width, height;
        public RegionBounds(int x, int y, int width, int height) {
            this.x = x; this.y = y; this.width = width; this.height = height;
        }
    }

    public RegionBounds getBarrageRegionBounds(int regionIndex) {
        int row = regionIndex / 3; // 0=top, 1=mid, 2=bot
        int col = regionIndex % 3; // 0, 1, 2

        // Y bounds: 232, 484, 736 (height = 252 per row, covering 232 to 988)
        int y = 232 + row * 252;
        int height = 252;

        int x, width;
        if (team == TeamAffiliation.HOME) {
            // HOME defensive third: X from 36 to 680 (width 644)
            if (col == 0) {
                x = 36; width = 214;
            } else if (col == 1) {
                x = 250; width = 215;
            } else {
                x = 465; width = 215;
            }
        } else {
            // AWAY defensive third: X from 1368 to 2012 (width 644)
            if (col == 0) {
                x = 1368; width = 215;
            } else if (col == 1) {
                x = 1583; width = 215;
            } else {
                x = 1798; width = 214;
            }
        }
        return new RegionBounds(x, y, width, height);
    }

    public void rollNextBarrage(Set<String> purchased) {
        /*
        Purchased	Spawn chance per cycle
        barrage + firebarrage + icebarrage	100% (1 fire + 1 ice)
        barrage + firebarrage only	100% (1 or 2 fire regions)
        barrage + icebarrage only	100% (1 or 2 ice regions)
        barrage only	80% (1 BASE region)
        firebarrage only (mana pollinate bypass)	50% → then 90%/10%
        icebarrage only (mana pollinate bypass)	50% → then 90%/10%
        */
        pendingBarrageRegions.clear();
        pendingBarrageTypes.clear();

        boolean hasBarrage = purchased.contains("fortress.t4.barrage");
        boolean hasFire    = purchased.contains("fortress.t5.firebarrage");
        boolean hasIce     = purchased.contains("fortress.t5.icebarrage");

        if (!hasBarrage && !hasFire && !hasIce) {
            return;
        }

        Random rand = new Random();
        List<Integer> candidateRegions = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            if (!activeBarrageRegions.contains(i) && !lastBarrageRegions.contains(i)) {
                candidateRegions.add(i);
            }
        }
        if (candidateRegions.size() < 2) {
            candidateRegions.clear();
            for (int i = 0; i < 9; i++) {
                if (!activeBarrageRegions.contains(i)) {
                    candidateRegions.add(i);
                }
            }
        }
        Collections.shuffle(candidateRegions, rand);

        if (hasFire && hasIce) {
            pendingBarrageRegions.add(candidateRegions.get(0));
            pendingBarrageTypes.add("FIRE");
            pendingBarrageRegions.add(candidateRegions.get(1));
            pendingBarrageTypes.add("ICE");
        } else if (hasFire) {
            if (hasBarrage || rand.nextDouble() < 0.50) {
                boolean twoRegions = rand.nextDouble() < 0.10;
                pendingBarrageRegions.add(candidateRegions.get(0));
                pendingBarrageTypes.add("FIRE");
                if (twoRegions && candidateRegions.size() > 1) {
                    pendingBarrageRegions.add(candidateRegions.get(1));
                    pendingBarrageTypes.add("FIRE");
                }
            }
        } else if (hasIce) {
            if (hasBarrage || rand.nextDouble() < 0.50) {
                boolean twoRegions = rand.nextDouble() < 0.10;
                pendingBarrageRegions.add(candidateRegions.get(0));
                pendingBarrageTypes.add("ICE");
                if (twoRegions && candidateRegions.size() > 1) {
                    pendingBarrageRegions.add(candidateRegions.get(1));
                    pendingBarrageTypes.add("ICE");
                }
            }
        } else {
            if (rand.nextDouble() < 0.80) {
                pendingBarrageRegions.add(candidateRegions.get(0));
                pendingBarrageTypes.add("BASE");
            }
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
        Random rand = new Random();
        int sx, sy;
        if (team == TeamAffiliation.HOME) {
            sx = 150 + rand.nextInt(400); // 150 to 550
        } else {
            sx = 1418 + rand.nextInt(282); // 1418 to 1700
        }
        sy = 100 + rand.nextInt(900); // 100 to 1000
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

        int topHoopCY = (int) (context.c.getI("goal.low.y") + context.c.getI("goal.low.height") / 2.0);
        int botHoopCY = (int) (context.c.getI("goal.low2.y") + context.c.getI("goal.low.height") / 2.0);
        int midHoopCY = (int) (context.c.getI("goal.hi.y") + context.c.getI("goal.hi.height") / 2.0);

        // Top lane and bottom lane walls centered on friendly hoop Y values
        int hY = topHoopCY - wHeight / 2;
        int aY = botHoopCY - wHeight / 2;
        int sx = (team == TeamAffiliation.HOME) ? context.c.GOALIE_XH_MAX + 150 : context.c.GOALIE_XA_MIN - 150 - wWidth;

        Wall w1 = new Wall(context, sx, hY);
        w1.team = team; w1.width = wWidth; w1.height = wHeight; w1.health = hp; w1.maxHealth = hp;
        context.entityPool.add(w1);

        Wall w2 = new Wall(context, sx, aY);
        w2.team = team; w2.width = wWidth; w2.height = wHeight; w2.health = hp; w2.maxHealth = hp;
        context.entityPool.add(w2);

        // Hemmed In extra goal wall behind center goal centered on middle hoop Y
        if (hemmedIn) {
            int gx = (team == TeamAffiliation.HOME) ? context.c.GOALIE_XH_MIN - 50 : context.c.GOALIE_XA_MAX + 50;
            Wall w3 = new Wall(context, gx, midHoopCY - wHeight / 2);
            w3.team = team; w3.width = wWidth; w3.height = wHeight; w3.health = hp; w3.maxHealth = hp;
            context.entityPool.add(w3);
        }
    }

    private void spawnBarrageRegion(GameEngine context, int regionIndex, String type) {
        Titan goalie = (team == TeamAffiliation.HOME) ? context.players[0] : context.players[1];
        RegionBounds b = getBarrageRegionBounds(regionIndex);
        
        Fire f = new Fire(goalie, b.x, b.y);
        f.team = team;
        f.width = b.width;
        f.height = b.height;
        f.health = 99999;
        f.maxHealth = 99999;
        context.entityPool.add(f);
        activeBarrageIds.add(f.id);
        barrageEntityTypes.put(f.id, type);
    }

    private void clearBarrageConfig(GameEngine context) {
        context.entityPool.removeIf(e -> activeBarrageIds.contains(e.id));
        activeBarrageIds.clear();
        barrageEntityTypes.clear();
    }

    private void applyHemmedIn(GameEngine context, Titan goalie) {
        int wWidth = 18;
        int wHeight = 180;
        int hp = 999999;
        int topHoopCY = (int) (context.c.getI("goal.low.y") + context.c.getI("goal.low.height") / 2.0);
        int botHoopCY = (int) (context.c.getI("goal.low2.y") + context.c.getI("goal.low.height") / 2.0);
        int midHoopCY = (int) (context.c.getI("goal.hi.y") + context.c.getI("goal.hi.height") / 2.0);

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
                    if (e.Y < 600) {
                        e.Y = topHoopCY - wHeight / 2;
                    } else {
                        e.Y = botHoopCY - wHeight / 2;
                    }
                }
            }
        }
        int gx = (team == TeamAffiliation.HOME) ? context.c.GOALIE_XH_MIN - 50 : context.c.GOALIE_XA_MAX + 50;
        Wall w3 = new Wall(context, gx, midHoopCY - wHeight / 2);
        w3.team = team;
        w3.width = wWidth;
        w3.height = wHeight;
        w3.health = hp;
        w3.maxHealth = hp;
        context.entityPool.add(w3);
    }

    private void spawnForwardMines(GameEngine context, Titan goalie) {
        Set<String> purchased = (team == TeamAffiliation.HOME) ? context.homeGoaliePurchasedUpgrades : context.awayGoaliePurchasedUpgrades;
        int midHoopCY = (int) (context.c.getI("goal.hi.y") + context.c.getI("goal.hi.height") / 2.0);
        int baseMx = (team == TeamAffiliation.HOME) ? 1600 : 400;

        boolean isIncendiary = purchased.contains("siege.t5.incendiarymines") || purchased.contains("siege.t5.forwardoutpost");
        int mSize = isIncendiary ? 150 : 80;
        int mx = isIncendiary ? (baseMx - (150 - 80) / 2) : baseMx;
        int my = midHoopCY - mSize / 2;

        Fire mines = new Fire(goalie, mx, my);
        mines.team = team;
        mines.width = mSize;
        mines.height = mSize;
        mines.health = 99999;
        mines.maxHealth = 99999;
        context.entityPool.add(mines);
    }

    // Saved implementation for "Quantum portals" (no-cooldown instant teleportation)
    /*
    private void spawnQuantumPortals(GameEngine context, Titan goalie) {
        int px = (team == TeamAffiliation.HOME) ? context.c.GOALIE_XA_MIN - 350 - 50 : context.c.GOALIE_XH_MAX + 350;
        int py1 = 354 - 25;
        int py2 = 790 - 25;
        BallPortal p1 = new BallPortal(team, goalie, context.entityPool, px, py1, context) {
            @Override
            public void triggerCollide(GameEngine ctx, Box entity) {
                if (ctx.ball.id.equals(entity.id) && !ctx.anyPoss() && !ctx.contactExemptBall()) {
                    entity.setX(px);
                    entity.setY(py2);
                }
            }
        };
        p1.team = team; p1.health = 99999; p1.maxHealth = 99999;
        context.entityPool.add(p1);
        BallPortal p2 = new BallPortal(team, goalie, context.entityPool, px, py2, context) {
            @Override
            public void triggerCollide(GameEngine ctx, Box entity) {
                if (ctx.ball.id.equals(entity.id) && !ctx.anyPoss() && !ctx.contactExemptBall()) {
                    entity.setX(px);
                    entity.setY(py1);
                }
            }
        };
        p2.team = team; p2.health = 99999; p2.maxHealth = 99999;
        context.entityPool.add(p2);
    }
    */

    private void spawnBallPortal(GameEngine context, Titan goalie) {
        int topHoopCY = (int) (context.c.getI("goal.low.y") + context.c.getI("goal.low.height") / 2.0);
        int botHoopCY = (int) (context.c.getI("goal.low2.y") + context.c.getI("goal.low.height") / 2.0);
        int px = (team == TeamAffiliation.HOME) ? context.c.GOALIE_XA_MIN - 350 - 50 : context.c.GOALIE_XH_MAX + 350;
        int py1 = topHoopCY - 25;
        int py2 = botHoopCY - 25;
        
        BallPortal p1 = new BallPortal(team, goalie, context.entityPool, px, py1, context);
        p1.team = team; p1.health = 99999; p1.maxHealth = 99999;
        context.entityPool.add(p1);
        
        BallPortal p2 = new BallPortal(team, goalie, context.entityPool, px, py2, context);
        p2.team = team; p2.health = 99999; p2.maxHealth = 99999;
        context.entityPool.add(p2);
    }

    private void spawnRushLane(GameEngine context, Titan goalie) {
        int topHoopCY = (int) (context.c.getI("goal.low.y") + context.c.getI("goal.low.height") / 2.0);
        int botHoopCY = (int) (context.c.getI("goal.low2.y") + context.c.getI("goal.low.height") / 2.0);
        int midHoopCY = (int) (context.c.getI("goal.hi.y") + context.c.getI("goal.hi.height") / 2.0);
        int lane = randIndex();
        int targetCY = (lane == 0 ? topHoopCY : (lane == 1 ? midHoopCY : botHoopCY));
        int rHeight = 40;
        int ly = targetCY - rHeight / 2;
        Fire rl = new Fire(goalie, 36, ly);
        rl.team = team;
        rl.width = 1976;
        rl.height = rHeight;
        rl.health = 99999;
        rl.maxHealth = 99999;
        context.entityPool.add(rl);
    }

    private void spawnParapet(GameEngine context, Titan goalie) {
        int topHoopCY = (int) (context.c.getI("goal.low.y") + context.c.getI("goal.low.height") / 2.0);
        int px = (team == TeamAffiliation.HOME) ? 350 - 50 : 1690 - 50;
        int py = topHoopCY - 50;
        Parapet platform = new Parapet(team, goalie, px, py);
        context.entityPool.add(platform);
    }

    private void spawnSecondBall(GameEngine context) {
        SecondBall second = new SecondBall(1040, 609);
        context.entityPool.add(second);
    }

    private void spawnHeroPortals(GameEngine context, Titan goalie) {
        int topHoopCY = (int) (context.c.getI("goal.low.y") + context.c.getI("goal.low.height") / 2.0);
        int botHoopCY = (int) (context.c.getI("goal.low2.y") + context.c.getI("goal.low.height") / 2.0);
        int offset = context.c.getI("guardian.heroportals.xoffset");
        int x1 = (team == TeamAffiliation.HOME) ? 1024 + offset : 1024 - offset;

        Portal p1 = new Portal(team, goalie, context.entityPool, x1, topHoopCY - 25, context);
        p1.team = team; p1.health = 99999; p1.maxHealth = 99999;
        context.entityPool.add(p1);

        Portal p2 = new Portal(team, goalie, context.entityPool, x1, botHoopCY - 25, context);
        p2.team = team; p2.health = 99999; p2.maxHealth = 99999;
        context.entityPool.add(p2);
    }

    private void spawnManaVines(GameEngine context, Titan goalie) {
        int midHoopCY = (int) (context.c.getI("goal.hi.y") + context.c.getI("goal.hi.height") / 2.0);
        int vx = (team == TeamAffiliation.HOME) ? context.c.GOALIE_XH_MAX + 100 : context.c.GOALIE_XA_MIN - 100 - 80;
        int vHeight = 200;
        int vy = midHoopCY - vHeight / 2;
        Trap vines = new Trap(goalie, context, vx, vy);
        vines.team = team;
        vines.width = 80;
        vines.height = vHeight;
        vines.health = 99999;
        vines.maxHealth = 99999;
        context.entityPool.add(vines);
    }

    private void spawnForwardMedics(GameEngine context, Titan goalie) {
        int midHoopCY = (int) (context.c.getI("goal.hi.y") + context.c.getI("goal.hi.height") / 2.0);
        int mx = (team == TeamAffiliation.HOME) ? 1600 : 400;
        int mHeight = 160;
        int my = midHoopCY - mHeight / 2;
        Fire medics = new Fire(goalie, mx, my);
        medics.team = team;
        medics.width = 160;
        medics.height = mHeight;
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
            context.effectPool.addUniqueEffect(new EmptyEffect(10000, target, EffectId.FLARE), context);
            applyRosterStatBoosts(context);
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
        double y0 = getSpawnY(context, 0);
        double y1 = getSpawnY(context, 1);
        double y2 = getSpawnY(context, 2);
        double d0 = Math.abs(goalie.Y - y0);
        double d1 = Math.abs(goalie.Y - y1);
        double d2 = Math.abs(goalie.Y - y2);
        
        int lane = 0;
        if (d1 < d0 && d1 < d2) {
            lane = 1;
        } else if (d2 < d0 && d2 < d1) {
            lane = 2;
        }

        double sx = getSpawnX(context, lane, team);
        double sy = getSpawnY(context, lane);

        for (int i = 0; i < 2; i++) {
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


    private void applyRosterStatBoosts(GameEngine context) {
        Set<String> purchased = (team == TeamAffiliation.HOME) ? context.homeGoaliePurchasedUpgrades : context.awayGoaliePurchasedUpgrades;

        int speedCount = 0;
        int throwCount = 0;
        int rangeCount = 0;
        int cdCount = 0;
        int durCount = 0;
        int hpCount = 0;
        int painCount = 0;
        int stealCount = 0;
        int damageCount = 0;

        if (purchased.contains("empowerment.t3.grit")) { hpCount++; painCount++; }
        if (purchased.contains("empowerment.t3.marksmanship")) { throwCount++; rangeCount++; }
        if (purchased.contains("empowerment.t3.footwork")) { speedCount++; }
        if (purchased.contains("empowerment.t3.discipline")) { cdCount++; durCount++; }
        
        if (purchased.contains("empowerment.t6.apexform")) {
            speedCount += 1; throwCount += 1; rangeCount += 1; cdCount += 1;
            durCount += 1; hpCount += 1; painCount += 1; stealCount += 1; damageCount += 1;
        }

        // Focused Training logic (+2 in highest existing mastery category)
        int gritVal = purchased.contains("empowerment.t3.grit") ? 1 : 0;
        int marksVal = purchased.contains("empowerment.t3.marksmanship") ? 1 : 0;
        int footVal = purchased.contains("empowerment.t3.footwork") ? 1 : 0;
        int discVal = purchased.contains("empowerment.t3.discipline") ? 1 : 0;

        int highest = 2; // 0 = Grit, 1 = Marksmanship, 2 = Footwork, 3 = Discipline. Default to footwork/speed.
        int maxVal = footVal;
        if (gritVal > maxVal) { maxVal = gritVal; highest = 0; }
        if (marksVal > maxVal) { maxVal = marksVal; highest = 1; }
        if (discVal > maxVal) { maxVal = discVal; highest = 3; }

        int focusTimes = countPurchases(purchased, "empowerment.t5.focusedtraining");
        if (focusTimes > 0) {
            int bonus = focusTimes * 2;
            if (highest == 0) {
                hpCount += bonus;
                painCount += bonus;
            } else if (highest == 1) {
                throwCount += bonus;
                rangeCount += bonus;
            } else if (highest == 2) {
                speedCount += bonus;
            } else {
                cdCount += bonus;
                durCount += bonus;
            }
        }

        for (Titan t : context.players) {
            if (t.team == team && t.getType() != null && t.getType() != TitanType.GOALIE) {
                // Apply enhancements
                t.speed = t.baseSpeed * Math.pow(context.c.getD("masteries.speed.mult"), speedCount);

                boolean hasSharpshooter = context.effectPool.hasEffect(t, EffectId.FLARE);
                boolean hasShoot = context.effectPool.hasEffect(t, EffectId.SHOOT);
                double sharpMult = hasSharpshooter ? 1.20 : 1.0;
                double shootMult = hasShoot ? 1.50 : 1.0;

                t.throwPower = t.baseThrowPower * Math.pow(context.c.getD("masteries.throw.mult"), throwCount) * sharpMult * shootMult;
                t.rangeFactor = t.baseRangeFactor * Math.pow(context.c.getD("masteries.range.mult"), rangeCount) * sharpMult;
                
                double cf = t.baseCooldownFactor / Math.pow(context.c.getD("masteries.cooldowns.mult"), cdCount);
                if (purchased.contains("cultivation.t4.manafrenzy")) {
                    double currentMana = (team == TeamAffiliation.HOME) ? context.homeGoalieMana : context.awayGoalieMana;
                    double frenzyCdr = currentMana / 30.0;
                    double frenzyMult = Math.max(0.0, 1.0 - (frenzyCdr / 100.0));
                    cf *= frenzyMult;
                }
                t.cooldownFactor = cf;

                t.durationsFactor = t.baseDurationsFactor * Math.pow(context.c.getD("masteries.effectDuration.mult"), durCount);
                t.maxHealth = t.baseMaxHealth * Math.pow(context.c.getD("masteries.health.mult"), hpCount);
                t.painReduction = t.basePainReduction * Math.pow(context.c.getD("masteries.painReduction.mult"), painCount);
                
                double heistMult = purchased.contains("empowerment.t5.heistcamp") ? 1.2 : 1.0;
                double stealBonus = 0.0;
                if (purchased.contains("empowerment.t4.forecheck") && t.X >= 680 && t.X <= 1368) {
                    stealBonus = context.c.getD("guardian.forecheck.bonus");
                }
                t.stealRad = (int) ((t.baseStealRad * heistMult * Math.pow(context.c.getD("masteries.stealRadius.mult"), stealCount)) + stealBonus);
                t.damageFactor = t.baseDamageFactor * Math.pow(context.c.getD("masteries.damage.mult"), damageCount);
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
        return purchased.contains("cultivation.t3.highermanacap") ? 1000.0 : 250.0;
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
        int cd = context.c.getI("guardian.wallportals.cooldown");
        int count = 15;
        
        if (team == TeamAffiliation.HOME) {
            // HOME goalie purchased upgrade (opponent is AWAY)
            // Top-to-Bottom Pairing (15 sets = 30 portals total)
            double startX = 1100.0;
            double endX = 2012.0;
            double topY = 36.0;
            double botY = 1182.0;
            for (int i = 0; i < count; i++) {
                int xVal = (int) (startX + i * (endX - startX) / (count - 1.0));
                
                BallPortal pTop = new BallPortal(team, goalie, context.entityPool, xVal, (int)topY, context);
                pTop.team = team; pTop.health = 99999; pTop.maxHealth = 99999;
                pTop.width = 40; pTop.height = 40;
                pTop.destinationX = xVal;
                pTop.destinationY = (int)botY;
                context.entityPool.add(pTop);

                BallPortal pBot = new BallPortal(team, goalie, context.entityPool, xVal, (int)botY, context);
                pBot.team = team; pBot.health = 99999; pBot.maxHealth = 99999;
                pBot.width = 40; pBot.height = 40;
                pBot.destinationX = xVal;
                pBot.destinationY = (int)topY;
                context.entityPool.add(pBot);
            }

            // Backwall-to-1/3-Line Pairing (15 sets = 30 portals total)
            double backX = 2012.0;
            double thirdX = 1368.0;
            double startY = 36.0;
            double endY = 1182.0;
            for (int i = 0; i < count; i++) {
                int yVal = (int) (startY + i * (endY - startY) / (count - 1.0));

                BallPortal pBack = new BallPortal(team, goalie, context.entityPool, (int)backX, yVal, context);
                pBack.team = team; pBack.health = 99999; pBack.maxHealth = 99999;
                pBack.width = 40; pBack.height = 40;
                pBack.destinationX = (int)thirdX;
                pBack.destinationY = yVal;
                context.entityPool.add(pBack);

                BallPortal pThird = new BallPortal(team, goalie, context.entityPool, (int)thirdX, yVal, context);
                pThird.team = team; pThird.health = 99999; pThird.maxHealth = 99999;
                pThird.width = 40; pThird.height = 40;
                pThird.destinationX = (int)backX;
                pThird.destinationY = yVal;
                context.entityPool.add(pThird);
            }
        } else {
            // AWAY goalie purchased upgrade (opponent is HOME)
            // Top-to-Bottom Pairing (15 sets = 30 portals total)
            double startX = 36.0;
            double endX = 948.0;
            double topY = 36.0;
            double botY = 1182.0;
            for (int i = 0; i < count; i++) {
                int xVal = (int) (startX + i * (endX - startX) / (count - 1.0));

                BallPortal pTop = new BallPortal(team, goalie, context.entityPool, xVal, (int)topY, context);
                pTop.team = team; pTop.health = 99999; pTop.maxHealth = 99999;
                pTop.width = 40; pTop.height = 40;
                pTop.destinationX = xVal;
                pTop.destinationY = (int)botY;
                context.entityPool.add(pTop);

                BallPortal pBot = new BallPortal(team, goalie, context.entityPool, xVal, (int)botY, context);
                pBot.team = team; pBot.health = 99999; pBot.maxHealth = 99999;
                pBot.width = 40; pBot.height = 40;
                pBot.destinationX = xVal;
                pBot.destinationY = (int)topY;
                context.entityPool.add(pBot);
            }

            // Backwall-to-1/3-Line Pairing (15 sets = 30 portals total)
            double backX = 36.0;
            double thirdX = 680.0;
            double startY = 36.0;
            double endY = 1182.0;
            for (int i = 0; i < count; i++) {
                int yVal = (int) (startY + i * (endY - startY) / (count - 1.0));

                BallPortal pBack = new BallPortal(team, goalie, context.entityPool, (int)backX, yVal, context);
                pBack.team = team; pBack.health = 99999; pBack.maxHealth = 99999;
                pBack.width = 40; pBack.height = 40;
                pBack.destinationX = (int)thirdX;
                pBack.destinationY = yVal;
                context.entityPool.add(pBack);

                BallPortal pThird = new BallPortal(team, goalie, context.entityPool, (int)thirdX, yVal, context);
                pThird.team = team; pThird.health = 99999; pThird.maxHealth = 99999;
                pThird.width = 40; pThird.height = 40;
                pThird.destinationX = (int)backX;
                pThird.destinationY = yVal;
                context.entityPool.add(pThird);
            }
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
        if (purchased.contains("fortress.t4.barrage") && !activeBarrageRegions.isEmpty()) {
            clearBarrageConfig(context);
            for (int i = 0; i < activeBarrageRegions.size(); i++) {
                spawnBarrageRegion(context, activeBarrageRegions.get(i), activeBarrageTypes.get(i));
            }
        }

        if (purchased.contains("siege.t3.rushlane")) {
            spawnRushLane(context, goalie);
        }
        if (purchased.contains("siege.t3.forwardmines") || purchased.contains("siege.t5.incendiarymines") || purchased.contains("siege.t5.forwardoutpost")) {
            spawnForwardMines(context, goalie);
        }
        if (purchased.contains("siege.t3.ballportal")) {
            spawnBallPortal(context, goalie);
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
