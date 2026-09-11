package gameserver.engine;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import gameserver.entity.Titan;

import java.io.File;
import java.util.*;

/**
 * Manages AI goalie build orders, presets, currency classification,
 * and concurrent Gold/Mana execution tracks.
 */
@JsonIgnoreType
public class GoalieBuildOrderManager {

    public static class BuildPreset {
        public final String name;
        public final List<String> order;
        public BuildPreset(String name, List<String> order) {
            this.name = name;
            this.order = order;
        }
    }

    public static final List<BuildPreset> GOALIE_NAMED_BUILD_PRESETS = loadNamedBuildPresets();
    public static final List<List<String>> GOALIE_BUILD_PRESETS = GOALIE_NAMED_BUILD_PRESETS.stream().map(p -> p.order).toList();

    public static List<BuildPreset> loadNamedBuildPresets() {
        List<BuildPreset> presets = new ArrayList<>();
        // Priority locations: res/builds/ (runtime & docker), client-web/builds/ (dev workspace)
        File[] searchDirs = new File[] {
            new File("res/builds"),
            new File("client-web/builds")
        };

        File foundDir = null;
        for (File dir : searchDirs) {
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles((d, name) -> name.endsWith(".txt"));
                if (files != null && files.length > 0) {
                    foundDir = dir;
                    break;
                }
            }
        }

        if (foundDir != null) {
            File[] files = foundDir.listFiles((d, name) -> name.endsWith(".txt"));
            if (files != null) {
                // Sort files deterministically by filename
                java.util.Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                for (File file : files) {
                    List<String> order = loadBuildOrderFromFile(file);
                    if (!order.isEmpty()) {
                        presets.add(new BuildPreset(file.getName(), List.copyOf(order)));
                    }
                }
            }
        }

        // Safe fallback if directory scan ever yields nothing
        if (presets.isEmpty()) {
            presets.add(new BuildPreset("default.txt", List.of("siege.t1.siegedoctrine", "siege.t3.vanguards", "siege.t4.accumulators", "fortress.t1.homeward", "fortress.t3.biggermodels", "fortress.t4.bastionprotocol")));
        }

        return List.copyOf(presets);
    }

    public static List<String> loadBuildOrderFromFile(File file) {
        List<String> order = new ArrayList<>();
        if (file == null || !file.exists()) return order;
        try (java.util.Scanner sc = new java.util.Scanner(file)) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                if (line == null) continue;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue;
                int hashIdx = line.indexOf('#');
                if (hashIdx != -1) line = line.substring(0, hashIdx).trim();
                int slashIdx = line.indexOf("//");
                if (slashIdx != -1) line = line.substring(0, slashIdx).trim();
                if (!line.isEmpty()) {
                    order.add(line);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return order;
    }


    public static String getTreeKeyForNode(String nodeKey) {
        if (nodeKey == null) return null;
        if (nodeKey.startsWith("siege.")) return "GOALIE_TREE_SIEGE";
        if (nodeKey.startsWith("fortress.")) return "GOALIE_TREE_FORTRESS";
        if (nodeKey.startsWith("empowerment.")) return "GOALIE_TREE_EMPOWERMENT";
        if (nodeKey.startsWith("cultivation.")) return "GOALIE_TREE_CULTIVATION";
        return null;
    }

    public static boolean isManaBuildNode(GameEngine engine, List<String> order, String nodeKey) {
        if (nodeKey == null) return false;
        if (engine.costs.hasKey(nodeKey + ".cost.mana") || engine.costs.hasKey(nodeKey + ".use.mana")) {
            return true;
        }
        // Foreign T5 node after manapollinate without foreign tree's T1 is intended as a pollinated mana purchase
        if (nodeKey.contains(".t5.") && !nodeKey.startsWith("cultivation.") && order != null) {
            int pollinateIdx = order.indexOf("cultivation.t5.manapollinate");
            int nodeIdx = order.indexOf(nodeKey);
            if (pollinateIdx != -1 && nodeIdx > pollinateIdx) {
                String treePrefix = nodeKey.substring(0, nodeKey.indexOf('.')) + ".t1.";
                boolean hasT1 = false;
                for (String k : order) {
                    if (k.startsWith(treePrefix)) {
                        hasT1 = true;
                        break;
                    }
                }
                if (!hasT1) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void setAiGoalieBuildOrder(GameEngine engine, Titan ai, String buildName, List<String> order) {
        ai.aiGoalieBuildName = buildName;
        setAiGoalieBuildOrder(engine, ai, order);
    }

    public static void setAiGoalieBuildOrder(GameEngine engine, Titan ai, List<String> order) {
        ai.aiGoalieBuildOrder = order;
        ai.aiGoalieBuildIndex = 0;
        ai.aiGoalieGoldOrder = new ArrayList<>();
        ai.aiGoalieManaOrder = new ArrayList<>();
        ai.aiGoalieGoldIndex = 0;
        ai.aiGoalieManaIndex = 0;
        if (order != null) {
            for (String nodeKey : order) {
                if (isManaBuildNode(engine, order, nodeKey)) {
                    ai.aiGoalieManaOrder.add(nodeKey);
                } else {
                    ai.aiGoalieGoldOrder.add(nodeKey);
                }
            }
        }
    }

    public static final List<String> ALL_GOLD_UPGRADES = List.of(
        // Fortress
        "fortress.t1.homeward",
        "fortress.t3.snaretrap", "fortress.t3.homehealamp", "fortress.t3.fastbreakinsurance", "fortress.t3.biggermodels",
        "fortress.t4.deadwalls", "fortress.t4.bastionprotocol", "fortress.t4.barrage",
        "fortress.t5.noflyzoneperm", "fortress.t5.dilators", "fortress.t5.icebarrage", "fortress.t5.firebarrage",
        "fortress.t6.deepfreeze", "fortress.t6.impenetrable", "fortress.t6.hemmedin",
        // Siege
        "siege.t1.siegedoctrine",
        "siege.t3.rushlane", "siege.t3.forwardmines", "siege.t3.ballportal", "siege.t3.vanguards", "siege.t3.pullgoalie",
        "siege.t4.accumulators", "siege.t4.parapet",
        "siege.t5.saveprogress", "siege.t5.incendiarymines", "siege.t5.forwardoutpost", "siege.t5.phalanx",
        "siege.t6.forwardmedics", "siege.t6.maximumpressure", "siege.t6.multiball",
        // Empowerment
        "empowerment.t1.combinecontract",
        "empowerment.t3.grit", "empowerment.t3.marksmanship", "empowerment.t3.footwork", "empowerment.t3.discipline",
        "empowerment.t4.forecheck", "empowerment.t4.fuelreserves", "empowerment.t4.heroportals",
        "empowerment.t5.focusedtraining", "empowerment.t5.focusedtraining2", "empowerment.t5.heistcamp", "empowerment.t5.clutchgene",
        "empowerment.t6.dragonsbreath", "empowerment.t6.apexform", "empowerment.t6.bannerofcommand",
        // Cultivation (Gold)
        "cultivation.t1.manawell",
        "cultivation.t3.manacompounding", "cultivation.t3.highermanacap", "cultivation.t3.tollcollector"
    );

    public static final List<String> ALL_MANA_UPGRADES = List.of(
        "cultivation.t4.manavines", "cultivation.t4.manafrenzy",
        "cultivation.t5.manapollinate", "cultivation.t5.riskadjustedreturn", "cultivation.t5.tripledown",
        "cultivation.t6.wallportals", "cultivation.t6.uninhibitedportal", "cultivation.t6.iceportal"
    );

    public static String getTree(String nodeKey) {
        if (nodeKey == null) return "";
        int dot = nodeKey.indexOf('.');
        return dot >= 0 ? nodeKey.substring(0, dot) : nodeKey;
    }

    public static String getTier(String nodeKey) {
        if (nodeKey == null) return "";
        int firstDot = nodeKey.indexOf('.');
        if (firstDot < 0) return "";
        String rest = nodeKey.substring(firstDot + 1);
        int secondDot = rest.indexOf('.');
        return secondDot >= 0 ? rest.substring(0, secondDot) : rest;
    }

    public static int getTierInt(String nodeKey) {
        String tier = getTier(nodeKey);
        if (tier.startsWith("t") && tier.length() > 1) {
            try {
                return Integer.parseInt(tier.substring(1));
            } catch (NumberFormatException ignored) {}
        }
        return 1;
    }

    public static String rollNextGoldUpgrade(GameEngine engine, Titan ai, Random rng) {
        boolean isHome = (ai.team == TeamAffiliation.HOME);
        Set<String> purchased = isHome ? engine.homeGoaliePurchasedUpgrades : engine.awayGoaliePurchasedUpgrades;

        Map<String, List<String>> purchasableByTree = new HashMap<>();
        for (String nodeKey : ALL_GOLD_UPGRADES) {
            if (purchased.contains(nodeKey)) continue;
            String tree = getTree(nodeKey);
            String tier = getTier(nodeKey);
            if (engine.tierPrereqMet(tree, tier, purchased)) {
                purchasableByTree.computeIfAbsent(tree, k -> new ArrayList<>()).add(nodeKey);
            }
        }

        if (purchasableByTree.isEmpty()) {
            return null;
        }

        List<String> existingTrees = new ArrayList<>();
        List<String> newTrees = new ArrayList<>();
        for (String tree : purchasableByTree.keySet()) {
            boolean hasPurchasedInTree = purchased.stream().anyMatch(k -> k.startsWith(tree + "."));
            if (hasPurchasedInTree) {
                existingTrees.add(tree);
            } else {
                newTrees.add(tree);
            }
        }

        float r = rng.nextFloat();

        // 10% chance to rotate to a new tree (or 100% if no existing trees have been specced into yet)
        if ((r >= 0.90f || existingTrees.isEmpty()) && !newTrees.isEmpty()) {
            String selectedTree = newTrees.get(rng.nextInt(newTrees.size()));
            List<String> nodes = purchasableByTree.get(selectedTree);
            return nodes.get(rng.nextInt(nodes.size()));
        }

        if (existingTrees.isEmpty()) {
            List<String> allAvailable = new ArrayList<>();
            for (List<String> list : purchasableByTree.values()) allAvailable.addAll(list);
            return allAvailable.get(rng.nextInt(allAvailable.size()));
        }

        String selectedTree = existingTrees.get(rng.nextInt(existingTrees.size()));
        List<String> availableInTree = purchasableByTree.get(selectedTree);

        int maxTier = availableInTree.stream().mapToInt(GoalieBuildOrderManager::getTierInt).max().orElse(1);
        List<String> highestTierNodes = new ArrayList<>();
        List<String> lowerTierNodes = new ArrayList<>();
        for (String node : availableInTree) {
            if (getTierInt(node) == maxTier) {
                highestTierNodes.add(node);
            } else {
                lowerTierNodes.add(node);
            }
        }

        // 20% chance to broaden to a lower tier of upgrade in the same tree
        if (r >= 0.70f && r < 0.90f && !lowerTierNodes.isEmpty()) {
            return lowerTierNodes.get(rng.nextInt(lowerTierNodes.size()));
        }

        // 70% chance (or fallback) to buy the highest tier of purchasable upgrade in the existing tree
        return highestTierNodes.get(rng.nextInt(highestTierNodes.size()));
    }

    public static String rollNextManaUpgrade(GameEngine engine, Titan ai, Random rng) {
        boolean isHome = (ai.team == TeamAffiliation.HOME);
        Set<String> purchased = isHome ? engine.homeGoaliePurchasedUpgrades : engine.awayGoaliePurchasedUpgrades;

        boolean hasCultivation = purchased.stream().anyMatch(k -> k.startsWith("cultivation."));
        if (!hasCultivation) {
            return null;
        }

        List<String> purchasableManaNodes = new ArrayList<>();
        for (String nodeKey : ALL_MANA_UPGRADES) {
            if (purchased.contains(nodeKey)) continue;
            String tree = getTree(nodeKey);
            String tier = getTier(nodeKey);
            if (engine.tierPrereqMet(tree, tier, purchased)) {
                purchasableManaNodes.add(nodeKey);
            }
        }

        // Check if Mana Pollinate is active and allows buying a foreign T5 with mana
        boolean pollinated = purchased.contains("cultivation.t5.manapollinate");
        List<String> pollinatedOptions = new ArrayList<>();
        if (pollinated) {
            boolean alreadyPurchasedOtherT5 = false;
            for (String key : purchased) {
                if (key.contains(".t5.") && !key.startsWith("cultivation.")) {
                    alreadyPurchasedOtherT5 = true;
                    break;
                }
            }
            if (!alreadyPurchasedOtherT5) {
                for (String nodeKey : ALL_GOLD_UPGRADES) {
                    if (nodeKey.contains(".t5.") && !nodeKey.startsWith("cultivation.") && !purchased.contains(nodeKey)) {
                        pollinatedOptions.add(nodeKey);
                    }
                }
            }
        }

        if (purchasableManaNodes.isEmpty() && pollinatedOptions.isEmpty()) {
            return null;
        }

        float r = rng.nextFloat();

        // 10% chance to rotate to a new tree (via pollinated foreign T5)
        if (r >= 0.90f && !pollinatedOptions.isEmpty()) {
            return pollinatedOptions.get(rng.nextInt(pollinatedOptions.size()));
        }

        if (purchasableManaNodes.isEmpty()) {
            return pollinatedOptions.get(rng.nextInt(pollinatedOptions.size()));
        }

        int maxTier = purchasableManaNodes.stream().mapToInt(GoalieBuildOrderManager::getTierInt).max().orElse(4);
        List<String> highestTier = new ArrayList<>();
        List<String> lowerTier = new ArrayList<>();
        for (String node : purchasableManaNodes) {
            if (getTierInt(node) == maxTier) {
                highestTier.add(node);
            } else {
                lowerTier.add(node);
            }
        }

        // 20% chance to broaden to a lower tier
        if (r >= 0.70f && r < 0.90f && !lowerTier.isEmpty()) {
            return lowerTier.get(rng.nextInt(lowerTier.size()));
        }

        // 70% chance (or fallback) to highest tier
        return highestTier.get(rng.nextInt(highestTier.size()));
    }

    public static void tickAiGoalieRandomizer(GameEngine engine, Titan ai) {
        boolean isHome = (ai.team == TeamAffiliation.HOME);
        Set<String> purchased = isHome ? engine.homeGoaliePurchasedUpgrades : engine.awayGoaliePurchasedUpgrades;
        Random rng = new Random();

        // ─── Gold Track ──────────────────────────────────────────
        if (ai.aiGoalieTargetGoldUpgrade == null || purchased.contains(ai.aiGoalieTargetGoldUpgrade)) {
            ai.aiGoalieTargetGoldUpgrade = rollNextGoldUpgrade(engine, ai, rng);
        }

        if (ai.aiGoalieTargetGoldUpgrade != null) {
            String nodeKey = ai.aiGoalieTargetGoldUpgrade;
            String treeKey = getTreeKeyForNode(nodeKey);
            String costKey = nodeKey + ".cost";
            if (engine.costs.hasKey(costKey)) {
                double cost = engine.costs.getD(costKey);
                double currentGold = isHome ? engine.homeGoalieCurrency : engine.awayGoalieCurrency;
                if (currentGold >= cost) {
                    engine.handleGoalieTreePurchase(ai, treeKey, nodeKey);
                    if (purchased.contains(nodeKey)) {
                        ai.aiGoalieTargetGoldUpgrade = null;
                    }
                }
            } else {
                ai.aiGoalieTargetGoldUpgrade = null;
            }
        }

        // ─── Mana Track (if Cultivation specced into) ──────────────
        boolean hasCultivation = purchased.stream().anyMatch(k -> k.startsWith("cultivation."));
        if (hasCultivation) {
            if (ai.aiGoalieTargetManaUpgrade == null || purchased.contains(ai.aiGoalieTargetManaUpgrade)) {
                ai.aiGoalieTargetManaUpgrade = rollNextManaUpgrade(engine, ai, rng);
            }

            if (ai.aiGoalieTargetManaUpgrade != null) {
                String nodeKey = ai.aiGoalieTargetManaUpgrade;
                String treeKey = getTreeKeyForNode(nodeKey);
                String costKey = engine.costs.hasKey(nodeKey + ".cost.mana") ? (nodeKey + ".cost.mana") : (nodeKey + ".cost");
                if (engine.costs.hasKey(costKey)) {
                    double cost = engine.costs.getD(costKey);
                    double currentMana = isHome ? engine.homeGoalieMana : engine.awayGoalieMana;
                    if (currentMana >= cost) {
                        engine.handleGoalieTreePurchase(ai, treeKey, nodeKey);
                        if (purchased.contains(nodeKey)) {
                            ai.aiGoalieTargetManaUpgrade = null;
                        }
                    }
                } else {
                    ai.aiGoalieTargetManaUpgrade = null;
                }
            }
        }
    }

    public static void tickAiGoalieBuildOrder(GameEngine engine, Titan ai) {
        if (engine.c != null && !engine.c.HEADLESS_BUILDORDERS_ENABLED) {
            tickAiGoalieRandomizer(engine, ai);
            return;
        }

        if (ai.aiGoalieBuildOrder == null || ai.aiGoalieGoldOrder == null || ai.aiGoalieManaOrder == null) {
            Random r = new Random();
            BuildPreset preset = GOALIE_NAMED_BUILD_PRESETS.get(r.nextInt(GOALIE_NAMED_BUILD_PRESETS.size()));
            setAiGoalieBuildOrder(engine, ai, preset.name, preset.order);
        }

        // Tick Gold Track
        tickAiGoalieTrack(engine, ai, false);

        // Tick Mana Track
        tickAiGoalieTrack(engine, ai, true);
    }

    private static void tickAiGoalieTrack(GameEngine engine, Titan ai, boolean isManaTrack) {
        List<String> order = isManaTrack ? ai.aiGoalieManaOrder : ai.aiGoalieGoldOrder;
        if (order == null) return;
        int index = isManaTrack ? ai.aiGoalieManaIndex : ai.aiGoalieGoldIndex;
        if (index >= order.size()) {
            return;
        }

        String nodeKey = order.get(index);
        String treeKey = getTreeKeyForNode(nodeKey);
        if (treeKey == null) {
            if (isManaTrack) ai.aiGoalieManaIndex++; else ai.aiGoalieGoldIndex++;
            return;
        }

        boolean isHome = (ai.team == TeamAffiliation.HOME);
        Set<String> purchased = isHome ? engine.homeGoaliePurchasedUpgrades : engine.awayGoaliePurchasedUpgrades;
        boolean hasCost = engine.costs.hasKey(nodeKey + ".cost") || engine.costs.hasKey(nodeKey + ".cost.mana");

        if (hasCost && purchased.contains(nodeKey)) {
            if (isManaTrack) ai.aiGoalieManaIndex++; else ai.aiGoalieGoldIndex++;
            return;
        }

        String shortName = GameEngine.TREE_SHORT_NAME.get(treeKey);
        String rest = (shortName != null && nodeKey.startsWith(shortName + ".")) ? nodeKey.substring(shortName.length() + 1) : "";
        int dot = rest.indexOf('.');
        String tier = dot >= 0 ? rest.substring(0, dot) : "";
        boolean isPollinated = purchased.contains("cultivation.t5.manapollinate") && tier.equals("t5") && !shortName.equals("cultivation") && hasCost;
        if (!isPollinated && !engine.tierPrereqMet(shortName, tier, purchased)) {
            return; // Prereqs not yet met
        }

        boolean isMana = engine.costs.hasKey(nodeKey + ".cost.mana") || engine.costs.hasKey(nodeKey + ".use.mana") || isPollinated;
        String costKey = hasCost
            ? (isMana ? (engine.costs.hasKey(nodeKey + ".cost.mana") ? nodeKey + ".cost.mana" : nodeKey + ".cost") : (engine.costs.hasKey(nodeKey + ".cost") ? nodeKey + ".cost" : nodeKey + ".cost.mana"))
            : (isMana ? (engine.costs.hasKey(nodeKey + ".use.mana") ? nodeKey + ".use.mana" : nodeKey + ".use") : (engine.costs.hasKey(nodeKey + ".use") ? nodeKey + ".use" : nodeKey + ".use.mana"));

        if (!engine.costs.hasKey(costKey)) {
            if (isManaTrack) ai.aiGoalieManaIndex++; else ai.aiGoalieGoldIndex++;
            return;
        }

        double cost = engine.costs.getD(costKey);
        double currentBalance = isMana
            ? (isHome ? engine.homeGoalieMana : engine.awayGoalieMana)
            : (isHome ? engine.homeGoalieCurrency : engine.awayGoalieCurrency);

        if (currentBalance < cost) {
            return; // Wait for gold or mana to accumulate before buying
        }

        double balanceBefore = currentBalance;
        engine.handleGoalieTreePurchase(ai, treeKey, nodeKey);

        double balanceAfter = isMana
            ? (isHome ? engine.homeGoalieMana : engine.awayGoalieMana)
            : (isHome ? engine.homeGoalieCurrency : engine.awayGoalieCurrency);

        if (balanceAfter < balanceBefore || purchased.contains(nodeKey)) {
            if (isManaTrack) ai.aiGoalieManaIndex++; else ai.aiGoalieGoldIndex++;
        }
    }
}
