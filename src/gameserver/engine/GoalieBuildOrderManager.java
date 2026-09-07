package gameserver.engine;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import gameserver.entity.Titan;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

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

    public static void tickAiGoalieBuildOrder(GameEngine engine, Titan ai) {
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
