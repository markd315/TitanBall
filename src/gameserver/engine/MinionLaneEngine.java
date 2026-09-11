package gameserver.engine;

import gameserver.effects.EffectId;
import gameserver.effects.effects.EmptyEffect;
import gameserver.entity.Entity;
import gameserver.entity.Titan;
import gameserver.entity.TitanType;
import gameserver.entity.minions.Dragon;
import gameserver.entity.minions.LaneMinion;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Handles all MOBA lane operations: minion wave spawning, minion combat and movement,
 * lane advantages, goalie economy (gold/mana trickle), dragon boss lifecycle, and accumulator hoops.
 */
public class MinionLaneEngine {

    protected final GameEngine context;

    private int minionWaveCount = 0;
    private int framesSinceDragonDead = 0;

    private final List<LaneMinion> hmL0 = new ArrayList<>(64);
    private final List<LaneMinion> hmL1 = new ArrayList<>(64);
    private final List<LaneMinion> hmL2 = new ArrayList<>(64);
    private final List<LaneMinion> amL0 = new ArrayList<>(64);
    private final List<LaneMinion> amL1 = new ArrayList<>(64);
    private final List<LaneMinion> amL2 = new ArrayList<>(64);
    private final List<List<LaneMinion>> homeMinionsReused = List.of(hmL0, hmL1, hmL2);
    private final List<List<LaneMinion>> awayMinionsReused = List.of(amL0, amL1, amL2);

    public MinionLaneEngine(GameEngine context) {
        this.context = context;
    }

    public double[] laneCenterYs() {
        int goalLowH = context.c.getI("goal.low.height");
        int goalHiH = context.c.getI("goal.hi.height");
        double topCenter = context.c.getI("goal.low.y") + goalLowH / 2.0;
        double midCenter = context.c.getI("goal.hi.y") + goalHiH / 2.0;
        double botCenter = context.c.getI("goal.low2.y") + goalLowH / 2.0;
        return new double[]{ topCenter, midCenter, botCenter };
    }

    public int getLaneFromY(double y) {
        double[] ys = laneCenterYs();
        double TOP_MID_DIV = (ys[0] + ys[1]) / 2.0;
        double MID_BOT_DIV = (ys[1] + ys[2]) / 2.0;
        if (y <= TOP_MID_DIV) return 0;
        else if (y <= MID_BOT_DIV) return 1;
        else return 2;
    }

    public void spawnMinion(int L, TeamAffiliation team) {
        int goalLowW = context.c.getI("goal.low.width");
        int goalLowH = context.c.getI("goal.low.height");
        int goalHiW = context.c.getI("goal.hi.width");
        int goalHiH = context.c.getI("goal.hi.height");

        int[] laneW = { goalLowW, goalHiW, goalLowW };
        int[] laneH = { goalLowH, goalHiH, goalLowH };

        int[] goalYs = { context.c.getI("goal.low.y"), context.c.getI("goal.hi.y"), context.c.getI("goal.low2.y") };

        int[] HOME_X = { context.c.getI("goal.home.low.x"), context.c.getI("goal.home.hi.x"), context.c.getI("goal.home.low.x") };
        int[] AWAY_X = { context.c.getI("goal.away.low.x"), context.c.getI("goal.away.hi.x"), context.c.getI("goal.away.low.x") };

        int spawnX = (team == TeamAffiliation.HOME) ? (HOME_X[L] + laneW[L] / 2) : (AWAY_X[L] + laneW[L] / 2);
        int spawnY = goalYs[L] + laneH[L] / 2;

        final int CAP = 200;
        int teamCount = 0;
        for (Entity e : context.entityPool) {
            if (e instanceof LaneMinion m && m.team == team) {
                teamCount++;
            }
        }
        if (teamCount >= CAP) return;

        double[] centers = laneCenterYs();
        double TOP_MID_DIV = (centers[0] + centers[1]) / 2.0;
        double MID_BOT_DIV = (centers[1] + centers[2]) / 2.0;

        boolean isHome = (team == TeamAffiliation.HOME);
        boolean overcharged = isHome ? (context.homeGoalieAbilities.overchargedWavesQueued > 0) : (context.awayGoalieAbilities.overchargedWavesQueued > 0);
        Set<String> upgrades = isHome ? context.homeGoaliePurchasedUpgrades : context.awayGoaliePurchasedUpgrades;

        LaneMinion m = new LaneMinion(spawnX, spawnY, team, L);
        m.health = context.c.getD("minion.base.health");
        m.maxHealth = m.health;

        if (overcharged) {
            m.health = context.c.getD("minion.base.health") * 2.0;
            m.maxHealth = m.health;
            m.damageMultiplier = 1.5;
        }

        if (upgrades.contains("empowerment.t6.bannerofcommand")) {
            int numHeroes = 0;
            for (Titan t : context.players) {
                if (t.health > 0.0 && t.getType() != TitanType.GOALIE && t.team == team) {
                    int tLane = 0;
                    if (t.Y >= TOP_MID_DIV && t.Y < MID_BOT_DIV) tLane = 1;
                    else if (t.Y >= MID_BOT_DIV) tLane = 2;
                    if (tLane == L) numHeroes++;
                }
            }
            m.damageMultiplier *= (1.0 + 0.15 * numHeroes);
        }

        if (upgrades.contains("siege.t5.phalanx")) {
            m.armorRatio = 1.0;
        }

        GuardianAbilities ga = (team == TeamAffiliation.HOME) ? context.homeGoalieAbilities : context.awayGoalieAbilities;
        if (upgrades.contains("siege.t3.rushlane") && ga.airSupportLane == L) {
            m.damageMultiplier *= 1.40;
        }

        context.entityPool.add(m);
        teamCount++;

        if (upgrades.contains("siege.t3.vanguards")) {
            boolean hasBall = false;
            for (Titan t : context.players) {
                if (t.team == team && t.possession == 1) {
                    hasBall = true;
                    break;
                }
            }
            boolean pastAttackingThird = false;
            for (Entity e : context.entityPool) {
                if (e instanceof LaneMinion && e.team == team && e.getHealth() > 0.0) {
                    if (isHome && e.X >= 1368) {
                        pastAttackingThird = true;
                        break;
                    } else if (!isHome && e.X <= 680) {
                        pastAttackingThird = true;
                        break;
                    }
                }
            }
            if (hasBall && pastAttackingThird && teamCount < CAP) {
                LaneMinion extra = new LaneMinion(spawnX, spawnY, team, L);
                extra.damageMultiplier = m.damageMultiplier;
                extra.armorRatio = m.armorRatio;
                extra.health = m.health;
                extra.maxHealth = m.maxHealth;
                context.entityPool.add(extra);
            }
        }
    }

    public void spawnMinionWave() {
        minionWaveCount++;
        for (int L = 0; L < 3; L++) {
            final int lane = L;
            spawnMinion(lane, TeamAffiliation.HOME);
            if (minionWaveCount == 2) {
                CompletableFuture.runAsync(() -> {
                    try {
                        if (lane != 1) {
                            Thread.sleep(2500);
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    context.lock();
                    try {
                        spawnMinion(lane, TeamAffiliation.AWAY);
                    } finally {
                        context.unlock();
                    }
                });
            } else {
                spawnMinion(lane, TeamAffiliation.AWAY);
            }
        }

        boolean homeOvercharged = context.homeGoalieAbilities.overchargedWavesQueued > 0;
        boolean awayOvercharged = context.awayGoalieAbilities.overchargedWavesQueued > 0;
        if (homeOvercharged) context.homeGoalieAbilities.overchargedWavesQueued--;
        if (awayOvercharged) context.awayGoalieAbilities.overchargedWavesQueued--;

        int goalHiW = context.c.getI("goal.hi.width");
        int HOME_SPAWN_X_MID = context.c.getI("goal.home.hi.x") + goalHiW / 2;
        int AWAY_SPAWN_X_MID = context.c.getI("goal.away.hi.x") + goalHiW / 2;
        int SPAWN_Y_MID = context.c.getI("goal.hi.y") + context.c.getI("goal.hi.height") / 2;

        final int HOME_CAP = 200;
        final int AWAY_CAP = 200;
        int homeCount = 0;
        int awayCount = 0;
        for (Entity e : context.entityPool) {
            if (e instanceof LaneMinion m) {
                if (m.team == TeamAffiliation.HOME) homeCount++;
                else awayCount++;
            }
        }

        // uninhibitedportal: constantly spawns 2 extra minions in the middle lane (lane index 1)
        if (context.homeGoaliePurchasedUpgrades.contains("cultivation.t6.uninhibitedportal") && homeCount < HOME_CAP - 1) {
            for (int i = 0; i < 2; i++) {
                LaneMinion extra = new LaneMinion(HOME_SPAWN_X_MID, SPAWN_Y_MID, TeamAffiliation.HOME, 1);
                extra.health = context.c.getD("minion.base.health");
                extra.maxHealth = extra.health;
                context.entityPool.add(extra);
            }
        }
        if (context.awayGoaliePurchasedUpgrades.contains("cultivation.t6.uninhibitedportal") && awayCount < AWAY_CAP - 1) {
            for (int i = 0; i < 2; i++) {
                LaneMinion extra = new LaneMinion(AWAY_SPAWN_X_MID, SPAWN_Y_MID, TeamAffiliation.AWAY, 1);
                extra.health = context.c.getD("minion.base.health");
                extra.maxHealth = extra.health;
                context.entityPool.add(extra);
            }
        }
    }

    public void tickLaneMinions() {
        for (int L = 0; L < 3; L++) {
            long now = context.nowEpochMs;
            context.getHomeLaneBonusesList().get(L).removeIf(b -> now >= b.expiryMs);
            context.getAwayLaneBonusesList().get(L).removeIf(b -> now >= b.expiryMs);

            int hSum = 0;
            long maxHExpiry = 0;
            for (GameEngine.LaneBonus b : context.getHomeLaneBonusesList().get(L)) {
                hSum += b.amount;
                if (b.expiryMs > maxHExpiry) maxHExpiry = b.expiryMs;
            }
            context.homeLaneBonusValue[L] = hSum;
            context.homeLaneBonusEndTime[L] = maxHExpiry;

            int aSum = 0;
            long maxAExpiry = 0;
            for (GameEngine.LaneBonus b : context.getAwayLaneBonusesList().get(L)) {
                aSum += b.amount;
                if (b.expiryMs > maxAExpiry) maxAExpiry = b.expiryMs;
            }
            context.awayLaneBonusValue[L] = aSum;
            context.awayLaneBonusEndTime[L] = maxAExpiry;
        }

        double[] centers = laneCenterYs();
        final double TOP_CENTER = centers[0];
        final double MID_CENTER = centers[1];
        final double BOT_CENTER = centers[2];

        final double TOP_MID_DIV = (TOP_CENTER + MID_CENTER) / 2.0;
        final double MID_BOT_DIV = (MID_CENTER + BOT_CENTER) / 2.0;

        boolean hasDilators = context.homeGoaliePurchasedUpgrades.contains("fortress.t5.dilators") ||
                context.awayGoaliePurchasedUpgrades.contains("fortress.t5.dilators");
        double minionSpeed = hasDilators ? 1.25 * context.c.getD("guardian.dilators.speedmult") : 1.25;
        double minionDmg = 0.05;
        double titanDmg = 0.05;
        double fightRange = 45.0;

        hmL0.clear(); hmL1.clear(); hmL2.clear();
        amL0.clear(); amL1.clear(); amL2.clear();

        for (Entity e : context.entityPool) {
            if (e instanceof LaneMinion && e.getHealth() > 0.0) {
                LaneMinion m = (LaneMinion) e;
                if (m.laneIndex == 0) {
                    if (m.team == TeamAffiliation.HOME) hmL0.add(m); else amL0.add(m);
                } else if (m.laneIndex == 1) {
                    if (m.team == TeamAffiliation.HOME) hmL1.add(m); else amL1.add(m);
                } else if (m.laneIndex == 2) {
                    if (m.team == TeamAffiliation.HOME) hmL2.add(m); else amL2.add(m);
                }
            }
        }

        for (int L = 0; L < 3; L++) {
            List<LaneMinion> homeInLane = homeMinionsReused.get(L);
            List<LaneMinion> awayInLane = awayMinionsReused.get(L);

            homeInLane.sort((a, b) -> Double.compare(b.X, a.X));
            awayInLane.sort((a, b) -> Double.compare(a.X, b.X));

            double laneCenterY = (L == 0 ? TOP_CENTER : L == 1 ? MID_CENTER : BOT_CENTER);
            double curHomeSpeed = getLaneMinionSpeed(L, TeamAffiliation.HOME, minionSpeed, homeInLane.size(), awayInLane.size(), 1.0);
            double curAwaySpeed = getLaneMinionSpeed(L, TeamAffiliation.AWAY, minionSpeed, homeInLane.size(), awayInLane.size(), -1.0);

            // HOME MINIONS
            for (LaneMinion h : homeInLane) {
                LaneMinion target = findNearestEnemyMinion(h.X, awayInLane, fightRange + 120.0);
                if (target != null) {
                    double dmg = minionDmg * h.damageMultiplier;
                    if (context.awayGoaliePurchasedUpgrades.contains("siege.t5.phalanx")) {
                        int adjCount = 0;
                        for (LaneMinion f : awayInLane) {
                            if (f != target && Math.abs(f.X - target.X) <= 100.0) {
                                adjCount++;
                            }
                        }
                        dmg *= Math.max(0.1, 1.0 - 0.1 * adjCount);
                    }
                    target.health -= dmg;
                    continue;
                }
                if (!awayInLane.isEmpty()) {
                    double frontEnemyX = awayInLane.get(0).X;
                    h.X = Math.min(h.X + curHomeSpeed, frontEnemyX - fightRange);
                } else {
                    h.X += curHomeSpeed;
                    Titan t = findNearestTitanInLane(h.X, h.Y, TeamAffiliation.AWAY,
                            TOP_CENTER, MID_CENTER, BOT_CENTER, TOP_MID_DIV, MID_BOT_DIV);
                    if (t != null) t.damage(context, titanDmg * h.damageMultiplier);
                }
                if (h.X >= 1780) {
                    h.health = 0;
                    context.getHomeLaneBonusesList().get(L).add(new GameEngine.LaneBonus(
                            context.nowEpochMs + context.c.getI("guardian.crashbonus.lifetime"),
                            context.c.getI("guardian.crashbonus.amount")));
                }
            }

            // AWAY MINIONS
            for (LaneMinion a : awayInLane) {
                LaneMinion target = findNearestEnemyMinion(a.X, homeInLane, fightRange + 120.0);
                if (target != null) {
                    double dmg = minionDmg * a.damageMultiplier;
                    if (context.homeGoaliePurchasedUpgrades.contains("siege.t5.phalanx")) {
                        int adjCount = 0;
                        for (LaneMinion f : homeInLane) {
                            if (f != target && Math.abs(f.X - target.X) <= 100.0) {
                                adjCount++;
                            }
                        }
                        dmg *= Math.max(0.1, 1.0 - 0.1 * adjCount);
                    }
                    target.health -= dmg;
                    continue;
                }
                if (!homeInLane.isEmpty()) {
                    double frontEnemyX = homeInLane.get(0).X;
                    a.X = Math.max(a.X - curAwaySpeed, frontEnemyX + fightRange);
                } else {
                    a.X -= curAwaySpeed;
                    Titan t = findNearestTitanInLane(a.X, a.Y, TeamAffiliation.HOME,
                            TOP_CENTER, MID_CENTER, BOT_CENTER, TOP_MID_DIV, MID_BOT_DIV);
                    if (t != null) t.damage(context, titanDmg * a.damageMultiplier);
                }
                if (a.X <= 300) {
                    a.health = 0;
                    context.getAwayLaneBonusesList().get(L).add(new GameEngine.LaneBonus(
                            context.nowEpochMs + context.c.getI("guardian.crashbonus.lifetime"),
                            context.c.getI("guardian.crashbonus.amount")));
                }
            }

            separateMinionsVertically(homeInLane, 45.0, laneCenterY);
            separateMinionsVertically(awayInLane, 45.0, laneCenterY);
        }
    }

    public LaneMinion findNearestEnemyMinion(double x, List<LaneMinion> enemies, double maxRange) {
        LaneMinion nearest = null;
        double minDist = maxRange;
        for (LaneMinion e : enemies) {
            if (e.health <= 0.0) continue;
            double dist = Math.abs(e.X - x);
            if (dist <= minDist) {
                minDist = dist;
                nearest = e;
            }
        }
        return nearest;
    }

    public void separateMinionsVertically(List<LaneMinion> list, double minSpacing, double laneCenterY) {
        int size = list.size();
        if (size == 0) return;
        if (size == 1) {
            list.get(0).Y = laneCenterY;
            return;
        }

        final double COL_SPACING_X = 50.0;
        final double GROUP_X_THRESHOLD = 65.0;
        final int MAX_GROUP_SIZE = 5;

        int groupStart = 0;
        for (int i = 1; i <= size; i++) {
            if (i == size || Math.abs(list.get(i).X - list.get(i - 1).X) > GROUP_X_THRESHOLD) {
                int groupLen = i - groupStart;
                if (groupLen == 1) {
                    list.get(groupStart).Y = laneCenterY;
                } else {
                    double frontX = list.get(groupStart).X;
                    int centerSlot = MAX_GROUP_SIZE / 2;
                    for (int j = 0; j < groupLen; j++) {
                        LaneMinion m = list.get(groupStart + j);
                        int col = j / MAX_GROUP_SIZE;
                        int slot = j % MAX_GROUP_SIZE;
                        m.Y = laneCenterY + (slot - centerSlot) * minSpacing;
                        if (m.team == TeamAffiliation.HOME) {
                            m.X = frontX - col * COL_SPACING_X;
                        } else {
                            m.X = frontX + col * COL_SPACING_X;
                        }
                    }
                }
                groupStart = i;
            }
        }
    }

    public Titan findNearestTitanInLane(
            double x, double y, TeamAffiliation team,
            double TOP_CENTER, double MID_CENTER, double BOT_CENTER,
            double TOP_MID_DIV, double MID_BOT_DIV) {

        int laneIndex;
        if (y <= TOP_MID_DIV) laneIndex = 0;
        else if (y <= MID_BOT_DIV) laneIndex = 1;
        else laneIndex = 2;

        double laneMinY, laneMaxY;
        if (laneIndex == 0) {
            laneMinY = 0;
            laneMaxY = TOP_MID_DIV;
        } else if (laneIndex == 1) {
            laneMinY = TOP_MID_DIV;
            laneMaxY = MID_BOT_DIV;
        } else {
            laneMinY = MID_BOT_DIV;
            laneMaxY = 2000;
        }

        Titan nearest = null;
        double minDist = 150.0;

        for (Titan t : context.players) {
            if (t.team != team || t.health <= 0.0) continue;
            double ty = t.Y + 35;
            if (ty < laneMinY || ty > laneMaxY) continue;

            double dist = Math.abs((t.X + 35) - x);
            if (dist < minDist) {
                minDist = dist;
                nearest = t;
            }
        }
        return nearest;
    }

    public void handleGoalieAttackClick(String email, double clickX, double clickY,
                                         TeamAffiliation goalieTeam, Titan goalie) {
        if (context.effectPool.hasEffect(goalie, EffectId.COOLDOWN_GOALIE)) {
            return;
        }

        double gx = goalie.X + goalie.width / 2.0;
        double gy = goalie.Y + goalie.height / 2.0;
        double rangeX = context.c.getI("titan.goalie.rangex") * goalie.rangeFactor;
        double rangeY = context.c.getI("titan.goalie.rangey") * goalie.rangeFactor;

        Entity target = null;
        boolean isDragon = false;
        double minDist = 45.0;

        for (Entity e : context.entityPool) {
            if (e instanceof LaneMinion) {
                LaneMinion m = (LaneMinion) e;
                if (m.getHealth() <= 0.0) continue;

                double dx = m.X - gx;
                double dy = m.Y - gy;
                if ((dx * dx) / (rangeX * rangeX) + (dy * dy) / (rangeY * rangeY) > 1.0) continue;

                double dist = util.Util.dist(m.X, m.Y, clickX, clickY);
                if (dist < minDist) {
                    minDist = dist;
                    target = m;
                    isDragon = false;
                }
            } else if (e instanceof Dragon) {
                Dragon d = (Dragon) e;
                if (d.getHealth() <= 0.0) continue;

                double dx = (d.X + d.width / 2.0) - gx;
                double dy = (d.Y + d.height / 2.0) - gy;
                if ((dx * dx) / (rangeX * rangeX) + (dy * dy) / (rangeY * rangeY) > 1.0) continue;

                double dist = util.Util.dist(d.X + d.width / 2.0, d.Y + d.height / 2.0, clickX, clickY);
                if (dist < minDist + 30.0) {
                    minDist = dist;
                    target = d;
                    isDragon = true;
                }
            }
        }

        if (target != null) {
            context.effectPool.addUniqueEffect(
                    new EmptyEffect(500, goalie, EffectId.COOLDOWN_GOALIE),
                    context
            );

            double dmg = context.c.getD("goalie.click.damage");
            GuardianAbilities ga = (goalieTeam == TeamAffiliation.HOME) ? context.homeGoalieAbilities : context.awayGoalieAbilities;
            Set<String> goaliePurchased = (goalieTeam == TeamAffiliation.HOME) ? context.homeGoaliePurchasedUpgrades : context.awayGoaliePurchasedUpgrades;

            int targetLane = isDragon ? 2 : ((LaneMinion) target).laneIndex;
            if (goaliePurchased.contains("siege.t3.rushlane") && ga.airSupportLane == targetLane) {
                dmg *= 1.40;
            }

            if (isDragon) {
                Dragon d = (Dragon) target;
                context.stats.grant(context, goalie, StatEngine.StatEnum.MINIONDAMAGE, dmg);
                d.damage(context, dmg, goalieTeam);
                if (d.getHealth() <= 0.0) {
                    context.stats.grant(context, goalie, StatEngine.StatEnum.LASTHITS);
                }
            } else {
                LaneMinion m = (LaneMinion) target;
                if (m.team != goalieTeam) {
                    context.stats.grant(context, goalie, StatEngine.StatEnum.MINIONDAMAGE, dmg);
                } else {
                    dmg *= 0.5;
                }
                m.health -= dmg;
                if (m.health <= 0.0) {
                    m.health = 0.0;
                    if (m.team != goalieTeam) {
                        context.stats.grant(context, goalie, StatEngine.StatEnum.LASTHITS);
                        if (goalieTeam == TeamAffiliation.HOME) {
                            context.homeGoalieCurrency += 5.0;
                        } else {
                            context.awayGoalieCurrency += 5.0;
                        }
                    }
                }
            }
        }
    }

    public void tickGoalieGold() {
        double ticksPerSec = 1000.0 / Math.max(1, context.GAMETICK_MS);
        double goldTrickleRate = 5.0 / (3.0 * ticksPerSec);
        context.homeGoalieCurrency += goldTrickleRate;
        context.awayGoalieCurrency += goldTrickleRate;
    }

    public void tickGoalieMana() {
        if (context.homeGoaliePurchasedUpgrades.contains("cultivation.t1.manawell")) {
            double rate = 0.025;
            if (context.homeGoaliePurchasedUpgrades.contains("cultivation.t3.manacompounding")) {
                rate *= (1.0 + (context.homeGoalieMana / 500.0) * 0.5);
            }
            if (context.homeGoaliePurchasedUpgrades.contains("cultivation.t3.tollcollector")) {
                int favored = countFavoredLanes(TeamAffiliation.HOME);
                rate *= (1.0 + 0.20 * favored);
            }
            rate *= context.homeGoalieAbilities.manaRateMultiplier;
            context.homeGoalieMana = Math.min(context.homeGoalieAbilities.getMaxMana(context), context.homeGoalieMana + rate);
        }
        if (context.awayGoaliePurchasedUpgrades.contains("cultivation.t1.manawell")) {
            double rate = 0.025;
            if (context.awayGoaliePurchasedUpgrades.contains("cultivation.t3.manacompounding")) {
                rate *= (1.0 + (context.awayGoalieMana / 500.0) * 0.5);
            }
            if (context.awayGoaliePurchasedUpgrades.contains("cultivation.t3.tollcollector")) {
                int favored = countFavoredLanes(TeamAffiliation.AWAY);
                rate *= (1.0 + 0.20 * favored);
            }
            rate *= context.awayGoalieAbilities.manaRateMultiplier;
            context.awayGoalieMana = Math.min(context.awayGoalieAbilities.getMaxMana(context), context.awayGoalieMana + rate);
        }
    }

    public int countFavoredLanes(TeamAffiliation team) {
        int favored = 0;
        for (int L = 0; L < 3; L++) {
            int homeCount = 0;
            int awayCount = 0;
            for (Entity entity : context.entityPool) {
                if (entity instanceof LaneMinion && entity.getHealth() > 0.0 && ((LaneMinion) entity).laneIndex == L) {
                    if (entity.team == TeamAffiliation.HOME) homeCount++;
                    else awayCount++;
                }
            }

            int homeBonus = (context.homeLaneBonusEndTime[L] > context.nowEpochMs) ? 3 : 0;
            int awayBonus = (context.awayLaneBonusEndTime[L] > context.nowEpochMs) ? 3 : 0;
            int net = (team == TeamAffiliation.HOME)
                    ? (homeCount + homeBonus - (awayCount + awayBonus))
                    : (awayCount + awayBonus - (homeCount + homeBonus));
            if (net > 0) favored++;
        }
        return favored;
    }

    public void checkDragonSpawning() {
        if (context.dragonSpawned) {
            context.dragonPreIndicatorActive = false;
            return;
        }
        boolean hasBreath = context.homeGoaliePurchasedUpgrades.contains("empowerment.t6.dragonsbreath") ||
                context.awayGoaliePurchasedUpgrades.contains("empowerment.t6.dragonsbreath");
        if (!hasBreath) {
            framesSinceDragonDead = 0;
            context.dragonPreIndicatorActive = false;
            return;
        }

        framesSinceDragonDead++;
        double ticksPerSec = 1000.0 / Math.max(1, context.GAMETICK_MS);
        int delayFrames = (int) (10.0 * ticksPerSec);
        int preIndicatorStart = delayFrames - (int) (2.0 * ticksPerSec);
        context.dragonPreIndicatorActive = (framesSinceDragonDead >= preIndicatorStart);
        if (framesSinceDragonDead >= delayFrames) {
            int botHoopCY = (int) (context.c.getI("goal.low2.y") + context.c.getI("goal.low.height") / 2.0);
            Dragon d = new Dragon(1024 - 60, botHoopCY - 60);
            context.entityPool.add(d);
            context.dragonSpawned = true;
            context.dragonPreIndicatorActive = false;
            framesSinceDragonDead = 0;
        }
    }

    public double getLaneMinionSpeed(int L, TeamAffiliation team, double baseSpeed) {
        return getLaneMinionSpeed(L, team, baseSpeed, 0.0);
    }

    public double getLaneMinionSpeed(int L, TeamAffiliation team, double baseSpeed, double dirX) {
        int homeCount = 0;
        int awayCount = 0;
        for (Entity entity : context.entityPool) {
            if (entity instanceof LaneMinion && entity.getHealth() > 0.0 && ((LaneMinion) entity).laneIndex == L) {
                if (entity.team == TeamAffiliation.HOME) homeCount++;
                else awayCount++;
            }
        }
        return getLaneMinionSpeed(L, team, baseSpeed, homeCount, awayCount, dirX);
    }

    public double getLaneMinionSpeed(int L, TeamAffiliation team, double baseSpeed, int homeCount, int awayCount) {
        double defaultDir = (team == TeamAffiliation.HOME) ? 1.0 : -1.0;
        return getLaneMinionSpeed(L, team, baseSpeed, homeCount, awayCount, defaultDir);
    }

    public double getLaneMinionSpeed(int L, TeamAffiliation team, double baseSpeed, int homeCount, int awayCount, double dirX) {
        int homeBonus = context.homeLaneBonusValue[L];
        int awayBonus = context.awayLaneBonusValue[L];

        int netMinionsHome = homeCount - awayCount;
        if (netMinionsHome > 10) netMinionsHome = 10;
        if (netMinionsHome < -10) netMinionsHome = -10;

        int netBonusHome = homeBonus - awayBonus;
        int P_home = netMinionsHome + netBonusHome;
        if (P_home > 20) P_home = 20;
        if (P_home < -20) P_home = -20;

        int P_team = (team == TeamAffiliation.HOME) ? P_home : -P_home;

        // 1. Quartered Unilateral Boost (-5% to +5%)
        double unilateralBoost = P_team * 0.0025;
        if (unilateralBoost > 0) {
            boolean maxPressure = context.homeGoaliePurchasedUpgrades.contains("siege.t6.maximumpressure") ||
                    context.awayGoaliePurchasedUpgrades.contains("siege.t6.maximumpressure");
            if (maxPressure && P_team > 5) {
                unilateralBoost *= 2.0;
            }
        } else if (unilateralBoost < 0) {
            boolean impenetrable = (team == TeamAffiliation.HOME)
                    ? context.homeGoaliePurchasedUpgrades.contains("fortress.t6.impenetrable")
                    : context.awayGoaliePurchasedUpgrades.contains("fortress.t6.impenetrable");
            if (impenetrable && P_team < -3) {
                unilateralBoost = -3 * 0.0025;
            }
        }

        // 2. Directional Hill Effect (-20% to +20%)
        double dirSign = (dirX > 0) ? 1.0 : ((dirX < 0) ? -1.0 : 0.0);
        double hillEffect = dirSign * P_home * 0.01;

        if (hillEffect < 0) {
            long insuranceUntil = (team == TeamAffiliation.HOME)
                    ? context.homeGoalieAbilities.fastBreakUntilMs
                    : context.awayGoalieAbilities.fastBreakUntilMs;
            if (context.nowEpochMs < insuranceUntil) {
                hillEffect = 0.0;
            }
        }

        double speed = baseSpeed * (1.0 + unilateralBoost + hillEffect);
        return Math.max(0.2, speed);
    }

    public void checkTurnovers() {
        TeamAffiliation currentPossessionTeam = TeamAffiliation.UNAFFILIATED;
        for (Titan t : context.players) {
            if (t.possession == 1) {
                currentPossessionTeam = t.team;
                break;
            }
        }
        if (currentPossessionTeam != context.lastPossessionTeam && currentPossessionTeam != TeamAffiliation.UNAFFILIATED) {
            if (context.lastPossessionTeam == TeamAffiliation.HOME) {
                if (context.homeGoaliePurchasedUpgrades.contains("fortress.t3.fastbreakinsurance")) {
                    context.homeGoalieAbilities.fastBreakUntilMs = context.nowEpochMs + 5000;
                }
            } else if (context.lastPossessionTeam == TeamAffiliation.AWAY) {
                if (context.awayGoaliePurchasedUpgrades.contains("fortress.t3.fastbreakinsurance")) {
                    context.awayGoalieAbilities.fastBreakUntilMs = context.nowEpochMs + 5000;
                }
            }
            context.lastPossessionTeam = currentPossessionTeam;
        }
    }

    public int getLaneAdvantage(int L, TeamAffiliation team) {
        int homeCount = 0;
        int awayCount = 0;
        for (Entity entity : context.entityPool) {
            if (entity instanceof LaneMinion && entity.getHealth() > 0.0 && ((LaneMinion) entity).laneIndex == L) {
                if (entity.team == TeamAffiliation.HOME) homeCount++;
                else awayCount++;
            }
        }
        int homeBonus = context.homeLaneBonusValue[L];
        int awayBonus = context.awayLaneBonusValue[L];

        int netMinions = (team == TeamAffiliation.HOME)
                ? (homeCount - awayCount)
                : (awayCount - homeCount);

        if (netMinions > 10) netMinions = 10;
        if (netMinions < -10) netMinions = -10;

        int netBonus = (team == TeamAffiliation.HOME)
                ? (homeBonus - awayBonus)
                : (awayBonus - homeBonus);

        int P = netMinions + netBonus;

        if (P > 20) P = 20;
        if (P < -20) P = -20;
        return P;
    }
}

