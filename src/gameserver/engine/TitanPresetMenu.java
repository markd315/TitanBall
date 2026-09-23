package gameserver.engine;

import gameserver.entity.Titan;
import gameserver.entity.TitanType;

import java.util.*;

/**
 * Menu of highly optimized and top-performing Titan builds for headless games.
 * Includes user-curated strategic archetypes as well as empirical TOPTHREE builds
 * derived from live masteriesstat balance data.
 */
public class TitanPresetMenu {

    public static class TitanPreset {
        public final String name;
        public final String displayName;
        public final TitanType defaultType;
        public final TitanType[] allowedTypes;
        public final int[] tierThreeIndices; // 3 stat indices from 0 to 10
        public final boolean isGoalie;
        public final String dataComment;

        public TitanPreset(String name, String displayName, TitanType defaultType,
                           int[] tierThreeIndices, boolean isGoalie, String dataComment) {
            this(name, displayName, defaultType, null, tierThreeIndices, isGoalie, dataComment);
        }

        public TitanPreset(String name, String displayName, TitanType defaultType,
                           TitanType[] allowedTypes, int[] tierThreeIndices, boolean isGoalie, String dataComment) {
            this.name = name;
            this.displayName = displayName;
            this.defaultType = defaultType;
            this.allowedTypes = allowedTypes;
            this.tierThreeIndices = tierThreeIndices;
            this.isGoalie = isGoalie;
            this.dataComment = dataComment;
        }

        public TitanType resolveTitanType(Random rng) {
            if (allowedTypes != null && allowedTypes.length > 0) {
                return allowedTypes[rng.nextInt(allowedTypes.length)];
            }
            return defaultType;
        }

        public Masteries createMasteries(Random rng) {
            Masteries m = new Masteries();
            m.zeroAll();
            for (int idx : tierThreeIndices) {
                m.setByIndex(idx, 3);
            }

            // Exactly 1 point allocated to a remaining stat to satisfy validate() == 0 (10 points total)
            List<Integer> remaining = new ArrayList<>();
            for (int i = 0; i <= 10; i++) {
                boolean inTierThree = false;
                for (int t3 : tierThreeIndices) {
                    if (t3 == i) {
                        inTierThree = true;
                        break;
                    }
                }
                if (!inTierThree) {
                    remaining.add(i);
                }
            }
            int filler = remaining.get(rng.nextInt(remaining.size()));
            m.setByIndex(filler, 1);
            return m;
        }

        public void applyToTitan(Titan t, Random rng) {
            if (!isGoalie) {
                TitanType chosen = resolveTitanType(rng);
                if (chosen != null) {
                    t.setType(chosen);
                    t.setVarsBasedOnType();
                }
            } else {
                if (t.getType() == null) {
                    t.setType(TitanType.GOALIE);
                    t.setVarsBasedOnType();
                }
            }
            t.presetName = this.name;
            Masteries m = createMasteries(rng);
            m.applyMasteries(t);
        }
    }

    // Stat indices in Masteries:
    // 0: health, 1: shot, 2: damage, 3: speed, 4: cooldowns, 5: effectDuration,
    // 6: stealRadius, 7: abilityRange, 8: abilityLag, 9: painReduction, 10: boost

    public static final List<TitanPreset> CURATED_OUTFIELD_PRESETS = Collections.unmodifiableList(Arrays.asList(
            // Max stealth: effect duration (5), cooldown (4), boost (10)
            new TitanPreset("MAX_STEALTH", "Max stealth", TitanType.STEALTH,
                    new int[]{5, 4, 10}, false, "Curated stealth pacing archetype"),

            // Rim runner golem: Painreduction (9), boost (10), castlag (8)
            new TitanPreset("RIM_RUNNER_GOLEM", "Rim runner golem", TitanType.GOLEM,
                    new int[]{9, 10, 8}, false, "Curated mobile tank golem archetype"),

            // Shot marksman: shoot (1), speed (3), effect duration (5)
            new TitanPreset("SHOT_MARKSMAN", "Shot marksman", TitanType.MARKSMAN,
                    new int[]{1, 3, 5}, false, "Curated ranged shooter marksman"),

            // DPS captain: abilityrange (7), cooldown reduction (4), damage (2)
            new TitanPreset("DPS_CAPTAIN", "DPS captain", TitanType.CAPTAIN,
                    new int[]{7, 4, 2}, false, "Curated rifle burst DPS archetype"),

            // DPS ranger: abilityrange (7), cooldown reduction (4), damage (2)
            new TitanPreset("DPS_RANGER", "DPS ranger", TitanType.RANGER,
                    new int[]{7, 4, 2}, false, "Curated longbow sniper DPS archetype"),

            // Speed warrior: speed (3), boost (10), castlag (8)
            new TitanPreset("SPEED_WARRIOR", "Speed warrior", TitanType.WARRIOR,
                    new int[]{3, 10, 8}, false, "Curated mobile skirmisher warrior"),

            // DPS warrior: cooldown reduction (4), damage (2), castlag (8)
            new TitanPreset("DPS_WARRIOR", "DPS warrior", TitanType.WARRIOR,
                    new int[]{4, 2, 8}, false, "Curated melee executioner warrior"),

            // Webspinner (spider): abilityrange (7), cast duration / effect duration (5), speed (3)
            new TitanPreset("WEBSPINNER", "Webspinner (spider)", TitanType.SPIDER,
                    new int[]{7, 5, 3}, false, "Curated zone control spider archetype"),

            // Burst mage: cooldown reduction (4), damage (2), effect duration (5)
            new TitanPreset("BURST_MAGE", "Burst mage", TitanType.MAGE,
                    new int[]{4, 2, 5}, false, "Curated burst assassinate mage archetype"),

            // Supportive support: Cast lag (8), steal radius (6), shot (1)
            new TitanPreset("SUPPORTIVE_SUPPORT", "Supportive support", TitanType.SUPPORT,
                    new int[]{8, 6, 1}, false, "Curated high-tempo disruptor & ball-handler support archetype"),

            // Forecheck artisan: stealrange (6), speed (3), boost (10)
            new TitanPreset("FORECHECK_ARTISAN", "Forecheck artisan", TitanType.ARTISAN,
                    new int[]{6, 3, 10}, false, "Curated dispossess & loose ball chase artisan")
    ));

    public static final List<TitanPreset> CURATED_GOALIE_PRESETS = Collections.unmodifiableList(Arrays.asList(
            // Outlet goalie: shot range (1), speed (3), steal range (6)
            new TitanPreset("OUTLET_GOALIE", "Outlet goalie", TitanType.GOALIE,
                    new int[]{1, 3, 6}, true, "Curated long distribution outlet goalie"),

            // Maxdef goalie: health (0), steal radius (6), speed (3)
            new TitanPreset("MAXDEF_GOALIE", "Maxdef goalie", TitanType.GOALIE,
                    new int[]{0, 6, 3}, true, "Curated lockdown defensive goalie")
    ));

    // Top three masteries per class by win rate in live masteriesstat balance data:
    public static final List<TitanPreset> TOPTHREE_OUTFIELD_PRESETS = Collections.unmodifiableList(Arrays.asList(
            // ARTISAN: Speed (48.49%), Ability Range (47.48%), Health (47.01%)
            new TitanPreset("TOPTHREE_ARTISAN", "Top-3 Artisan", TitanType.ARTISAN,
                    new int[]{3, 7, 0}, false,
                    "Chosen from preliminary data: Speed 48.49%, AbilityRange 47.48%, Health 47.01%"),

            // BUILDER: Speed (57.45%), Steal Radius (51.30%), Boost (51.14%)
            new TitanPreset("TOPTHREE_BUILDER", "Top-3 Builder", TitanType.BUILDER,
                    new int[]{3, 6, 10}, false,
                    "Chosen from preliminary data: Speed 57.45%, StealRadius 51.30%, Boost 51.14%"),

            // CAPTAIN: Speed (54.02%), Steal Radius (53.00%), Pain Reduction (51.69%)
            new TitanPreset("TOPTHREE_CAPTAIN", "Top-3 Captain", TitanType.CAPTAIN,
                    new int[]{3, 6, 9}, false,
                    "Chosen from preliminary data: Speed 54.02%, StealRadius 53.00%, PainReduction 51.69%"),

            // DASHER: Speed (49.60%), Ability Range (47.85%), Pain Reduction (45.84%)
            new TitanPreset("TOPTHREE_DASHER", "Top-3 Dasher", TitanType.DASHER,
                    new int[]{3, 7, 9}, false,
                    "Chosen from preliminary data: Speed 49.60%, AbilityRange 47.85%, PainReduction 45.84%"),

            // GOLEM: Speed (53.27%), Cast Lag (51.86%), Ability Range (51.10%)
            new TitanPreset("TOPTHREE_GOLEM", "Top-3 Golem", TitanType.GOLEM,
                    new int[]{3, 8, 7}, false,
                    "Chosen from preliminary data: Speed 53.27%, AbilityLag 51.86%, AbilityRange 51.10%"),

            // HOUNDMASTER: Speed (55.16%), Pain Reduction (54.79%), Steal Radius (53.76%)
            new TitanPreset("TOPTHREE_HOUNDMASTER", "Top-3 Houndmaster", TitanType.HOUNDMASTER,
                    new int[]{3, 9, 6}, false,
                    "Chosen from preliminary data: Speed 55.16%, PainReduction 54.79%, StealRadius 53.76%"),

            // MARKSMAN: Speed (54.39%), Damage (54.14%), Shooting (52.65%)
            new TitanPreset("TOPTHREE_MARKSMAN", "Top-3 Marksman", TitanType.MARKSMAN,
                    new int[]{3, 2, 1}, false,
                    "Chosen from preliminary data: Speed 54.39%, Damage 54.14%, Shot 52.65%"),

            // RANGER: Pain Reduction (56.79%), Damage (55.01%), Speed (54.23%)
            new TitanPreset("TOPTHREE_RANGER", "Top-3 Ranger", TitanType.RANGER,
                    new int[]{9, 2, 3}, false,
                    "Chosen from preliminary data: PainReduction 56.79%, Damage 55.01%, Speed 54.23%"),

            // SPIDER: Promoted from successful Webspinner theorycraft: Ability Range (54.07%), Effect Duration (52.16%), Speed (53.66%)
            new TitanPreset("TOPTHREE_SPIDER", "Top-3 Spider", TitanType.SPIDER,
                    new int[]{7, 5, 3}, false,
                    "Chosen from preliminary data and Webspinner theorycraft: AbilityRange 54.07%, EffectDuration 52.16%, Speed 53.66%"),

            // STEALTH: Speed (52.64%), Ability Range (51.85%), Health (51.35%)
            new TitanPreset("TOPTHREE_STEALTH", "Top-3 Stealth", TitanType.STEALTH,
                    new int[]{3, 7, 0}, false,
                    "Chosen from preliminary data: Speed 52.64%, AbilityRange 51.85%, Health 51.35%"),

            // SUPPORT: Cast Lag (54.74%), Boost (52.92%), Steal Radius (52.66%)
            new TitanPreset("TOPTHREE_SUPPORT", "Top-3 Support", TitanType.SUPPORT,
                    new int[]{8, 10, 6}, false,
                    "Chosen from preliminary data: AbilityLag 54.74%, Boost 52.92%, StealRadius 52.66%"),

            // WARRIOR: Steal Radius (56.64%), Speed (56.07%), Pain Reduction (55.77%)
            new TitanPreset("TOPTHREE_WARRIOR", "Top-3 Warrior", TitanType.WARRIOR,
                    new int[]{6, 3, 9}, false,
                    "Chosen from preliminary data: StealRadius 56.64%, Speed 56.07%, PainReduction 55.77%")
    ));

    public static final List<TitanPreset> TOPTHREE_GOALIE_PRESETS = Collections.unmodifiableList(Arrays.asList(
            // GOALIE: Boost (54.50%), Health (52.38%), Speed (49.99%)
            new TitanPreset("TOPTHREE_GOALIE", "Top-3 Goalie", TitanType.GOALIE,
                    new int[]{10, 0, 3}, true,
                    "Chosen from preliminary data: Boost 54.50%, Health 52.38%, Speed 49.99%")
    ));

    // Combined menus containing both curated and data-driven presets
    public static final List<TitanPreset> ALL_OUTFIELD_PRESETS;
    public static final List<TitanPreset> ALL_GOALIE_PRESETS;

    static {
        List<TitanPreset> outfield = new ArrayList<>(CURATED_OUTFIELD_PRESETS);
        outfield.addAll(TOPTHREE_OUTFIELD_PRESETS);
        ALL_OUTFIELD_PRESETS = Collections.unmodifiableList(outfield);

        List<TitanPreset> goalie = new ArrayList<>(CURATED_GOALIE_PRESETS);
        goalie.addAll(TOPTHREE_GOALIE_PRESETS);
        ALL_GOALIE_PRESETS = Collections.unmodifiableList(goalie);
    }

    public static TitanPreset getRandomOutfieldPreset(Random rng) {
        return ALL_OUTFIELD_PRESETS.get(rng.nextInt(ALL_OUTFIELD_PRESETS.size()));
    }

    public static TitanPreset getRandomGoaliePreset(Random rng) {
        return ALL_GOALIE_PRESETS.get(rng.nextInt(ALL_GOALIE_PRESETS.size()));
    }

    public static TitanPreset findByName(String name) {
        if (name == null) return null;
        for (TitanPreset p : ALL_OUTFIELD_PRESETS) {
            if (p.name.equalsIgnoreCase(name)) return p;
        }
        for (TitanPreset p : ALL_GOALIE_PRESETS) {
            if (p.name.equalsIgnoreCase(name)) return p;
        }
        return null;
    }
}
