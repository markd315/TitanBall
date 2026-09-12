import gameserver.engine.GameEngine;
import gameserver.engine.StatEngine;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.Titan;
import gameserver.entity.TitanType;
import networking.PlayerDivider;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CultivationGoldSpendTest {

    private GameEngine createStandardGame() {
        GameEngine engine = new GameEngine();
        List<PlayerDivider> clients = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            PlayerDivider pd = new PlayerDivider(Arrays.asList(i));
            pd.id = i;
            pd.email = "player" + i + "@titan.test";
            clients.add(pd);
        }
        engine.clients = clients;
        engine.initializeServer();
        return engine;
    }

    @Test
    public void testCultivationGoldUpgradeExcludedFromGoldSpend() {
        GameEngine engine = createStandardGame();
        Titan goalie = engine.players[0]; // HOME goalie
        goalie.team = TeamAffiliation.HOME;
        goalie.setType(TitanType.GOALIE);

        // Give goalie starting gold and mana
        engine.homeGoalieCurrency = 500.0;
        engine.homeGoalieMana = 500.0;

        PlayerDivider goalieClient = engine.clientFromTitan(goalie);
        String goalieEmail = goalieClient.email;

        // 1. Purchase cultivation T1 upgrade (manawell: costs 75 gold)
        engine.handleGoalieTreePurchase(goalie, "GOALIE_TREE_CULTIVATION", "cultivation.t1.manawell");

        Assert.assertTrue("manawell should be marked as purchased",
                engine.homeGoaliePurchasedUpgrades.contains("cultivation.t1.manawell"));
        Assert.assertEquals("Goalie gold should be deducted by 75", 425.0, engine.homeGoalieCurrency, 0.001);

        double upgradesGold = engine.stats.getGamestats()
                .get(StatEngine.StatEnum.UPGRADESGOLD.ordinal())
                .getOrDefault(goalieEmail, 0.0);
        Assert.assertEquals("Cultivation gold upgrades must be excluded from gold spend (UPGRADESGOLD)",
                0.0, upgradesGold, 0.001);

        // 2. Purchase Fortress T1 upgrade (homeward: costs 50 gold)
        engine.handleGoalieTreePurchase(goalie, "GOALIE_TREE_FORTRESS", "fortress.t1.homeward");

        Assert.assertTrue("homeward should be marked as purchased",
                engine.homeGoaliePurchasedUpgrades.contains("fortress.t1.homeward"));
        Assert.assertEquals("Goalie gold should be deducted by 50", 375.0, engine.homeGoalieCurrency, 0.001);

        upgradesGold = engine.stats.getGamestats()
                .get(StatEngine.StatEnum.UPGRADESGOLD.ordinal())
                .getOrDefault(goalieEmail, 0.0);
        Assert.assertEquals("Fortress gold upgrades should be tracked in gold spend (UPGRADESGOLD)",
                50.0, upgradesGold, 0.001);

        // 3. Purchase Cultivation T3 upgrade (manacompounding: costs 150 gold)
        engine.handleGoalieTreePurchase(goalie, "GOALIE_TREE_CULTIVATION", "cultivation.t3.manacompounding");

        Assert.assertTrue("manacompounding should be marked as purchased",
                engine.homeGoaliePurchasedUpgrades.contains("cultivation.t3.manacompounding"));
        Assert.assertEquals("Goalie gold should be deducted by 150", 225.0, engine.homeGoalieCurrency, 0.001);

        upgradesGold = engine.stats.getGamestats()
                .get(StatEngine.StatEnum.UPGRADESGOLD.ordinal())
                .getOrDefault(goalieEmail, 0.0);
        Assert.assertEquals("Cultivation T3 gold upgrades must still be excluded from UPGRADESGOLD",
                50.0, upgradesGold, 0.001);

        // 3b. Purchase second Cultivation T3 upgrade (highermanacap: costs 100 gold) to satisfy T4 prereq (requires 2 in T3)
        engine.handleGoalieTreePurchase(goalie, "GOALIE_TREE_CULTIVATION", "cultivation.t3.highermanacap");

        Assert.assertTrue("highermanacap should be marked as purchased",
                engine.homeGoaliePurchasedUpgrades.contains("cultivation.t3.highermanacap"));
        Assert.assertEquals("Goalie gold should be deducted by 100", 125.0, engine.homeGoalieCurrency, 0.001);

        upgradesGold = engine.stats.getGamestats()
                .get(StatEngine.StatEnum.UPGRADESGOLD.ordinal())
                .getOrDefault(goalieEmail, 0.0);
        Assert.assertEquals("Cultivation T3 highermanacap must also be excluded from UPGRADESGOLD",
                50.0, upgradesGold, 0.001);

        // 4. Purchase Cultivation T4 mana upgrade (manavines: costs 250 mana)
        engine.handleGoalieTreePurchase(goalie, "GOALIE_TREE_CULTIVATION", "cultivation.t4.manavines");

        Assert.assertTrue("manavines should be marked as purchased",
                engine.homeGoaliePurchasedUpgrades.contains("cultivation.t4.manavines"));
        Assert.assertEquals("Goalie mana should be deducted by 250", 250.0, engine.homeGoalieMana, 0.001);

        double manaSpent = engine.stats.getGamestats()
                .get(StatEngine.StatEnum.MANASPENT.ordinal())
                .getOrDefault(goalieEmail, 0.0);
        Assert.assertEquals("Mana upgrades should be tracked in MANASPENT",
                250.0, manaSpent, 0.001);
    }
}
