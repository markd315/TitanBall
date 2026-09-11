import gameserver.engine.GameEngine;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.Entity;
import gameserver.entity.Titan;
import gameserver.entity.minions.BallPortal;
import networking.PlayerDivider;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class WallPortalsTest {

    private GameEngine createStandardGame() {
        GameEngine engine = new GameEngine();
        List<PlayerDivider> clients = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            PlayerDivider pd = new PlayerDivider(Arrays.asList(i));
            pd.id = i;
            clients.add(pd);
        }
        engine.clients = clients;
        engine.initializeServer();
        return engine;
    }

    @Test
    public void testWallPortalsCountAndAlignment() {
        GameEngine engine = createStandardGame();

        // Purchase wallportals upgrade for HOME goalie
        engine.homeGoaliePurchasedUpgrades.add("cultivation.t6.wallportals");
        engine.homeGoalieAbilities.recreatePermanentEntities(engine);

        List<BallPortal> portals = engine.entityPool.stream()
                .filter(e -> e instanceof BallPortal && ((BallPortal) e).destinationX != null)
                .map(e -> (BallPortal) e)
                .collect(Collectors.toList());

        // Default count is 15 on top/bottom (15 top + 15 bot = 30)
        // Midfield count is 1 (1 backwall + 1 midfield = 2)
        // Total = 32 portals
        Assert.assertEquals("Expected 32 total wall portals (30 horizontal + 2 vertical)", 32, portals.size());

        List<BallPortal> topPortals = portals.stream()
                .filter(p -> p.Y == engine.c.MIN_Y)
                .collect(Collectors.toList());
        Assert.assertEquals("Expected 15 top wall portals", 15, topPortals.size());

        int expectedBotY = engine.c.MAX_Y - 40 + engine.c.getI("ball.h"); // 988 - 40 + 30 = 978
        List<BallPortal> botPortals = portals.stream()
                .filter(p -> p.Y == expectedBotY)
                .collect(Collectors.toList());
        Assert.assertEquals("Expected 15 bottom wall portals", 15, botPortals.size());

        // Verify bottom wall alignment: bottom edge must be flush with court wall at 1018
        for (BallPortal p : botPortals) {
            Assert.assertEquals("Bottom edge of bot portal must align with court boundary at 1018", 1018, (int) (p.Y + p.height));
        }

        // Verify top-to-bottom gaps are smaller than ball width (30px) so no pass-through gaps exist
        topPortals.sort((a, b) -> Double.compare(a.X, b.X));
        for (int i = 0; i < topPortals.size() - 1; i++) {
            double gap = topPortals.get(i + 1).X - (topPortals.get(i).X + topPortals.get(i).width);
            Assert.assertTrue("Horizontal gap (" + gap + ") must be less than ball width 30", gap < 30.0);
        }

        // Verify midfield and behind-goal portals
        int expectedMidY = (engine.c.MIN_Y + expectedBotY) / 2; // (232 + 978) / 2 = 605
        List<BallPortal> verticalPortals = portals.stream()
                .filter(p -> p.Y == expectedMidY)
                .collect(Collectors.toList());
        Assert.assertEquals("Expected 2 vertical portals (1 backwall, 1 midfield)", 2, verticalPortals.size());

        BallPortal pBack = verticalPortals.stream().filter(p -> p.X == 1970).findFirst().orElse(null);
        BallPortal pMid = verticalPortals.stream().filter(p -> p.X == 1024).findFirst().orElse(null);

        Assert.assertNotNull("Must have backwall portal at X=1970", pBack);
        Assert.assertNotNull("Must have midfield portal at X=1024", pMid);

        Assert.assertEquals("Behind-goal and midfield portals must share the exact same Y value", pBack.Y, pMid.Y, 0.001);
        Assert.assertEquals("Portals must be centered at Y=625 (aligned with high goal)", 625.0, pBack.Y + pBack.height / 2.0, 0.001);

        // Verify bidirectional linking
        Assert.assertEquals(pMid.id, pBack.destinationId);
        Assert.assertEquals((int) pMid.X, (int) pBack.destinationX);
        Assert.assertEquals((int) pMid.Y, (int) pBack.destinationY);

        Assert.assertEquals(pBack.id, pMid.destinationId);
        Assert.assertEquals((int) pBack.X, (int) pMid.destinationX);
        Assert.assertEquals((int) pBack.Y, (int) pMid.destinationY);
    }
}
