import gameserver.engine.GameEngine;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.Titan;
import gameserver.entity.TitanType;
import org.junit.Assert;
import org.junit.Test;

public class LaneSpeedTest {

    @Test
    public void testQuarteredUnilateralAndHillSpeedModifiers() {
        GameEngine engine = new GameEngine();
        engine.c = new gameserver.engine.Const();

        // 1. Zero advantage: base speed should equal modified speed
        double baseSpeed = 3.0;
        double neutralSpeed = engine.getLaneMinionSpeed(0, TeamAffiliation.HOME, baseSpeed, 0, 0, 1.0);
        Assert.assertEquals("Neutral speed should equal base speed", baseSpeed, neutralSpeed, 0.001);

        // 2. Maximum HOME advantage (+20 minions net difference in lane 0)
        int homeCount = 20;
        int awayCount = 0;

        // HOME moving right (+1.0, downhill towards enemy goal):
        // Unilateral: +20 * 0.0025 = +0.05 (+5%)
        // Hill: (+1.0) * (+20) * 0.01 = +0.20 (+20%)
        // Total: 1.0 + 0.05 + 0.20 = 1.25 (+25%)
        double homeDownhill = engine.getLaneMinionSpeed(0, TeamAffiliation.HOME, baseSpeed, homeCount, awayCount, 1.0);
        Assert.assertEquals("HOME downhill speed should be 1.25x base speed", baseSpeed * 1.25, homeDownhill, 0.001);

        // HOME moving left (-1.0, uphill away from enemy goal):
        // Unilateral: +0.05 (+5%)
        // Hill: (-1.0) * (+20) * 0.01 = -0.20 (-20%)
        // Total: 1.0 + 0.05 - 0.20 = 0.85 (-15%)
        double homeUphill = engine.getLaneMinionSpeed(0, TeamAffiliation.HOME, baseSpeed, homeCount, awayCount, -1.0);
        Assert.assertEquals("HOME uphill speed should be 0.85x base speed", baseSpeed * 0.85, homeUphill, 0.001);

        // AWAY moving left (-1.0, uphill towards enemy goal against +20 HOME push):
        // Unilateral: -20 * 0.0025 = -0.05 (-5%)
        // Hill: (-1.0) * (+20) * 0.01 = -0.20 (-20%)
        // Total: 1.0 - 0.05 - 0.20 = 0.75 (-25%)
        double awayUphill = engine.getLaneMinionSpeed(0, TeamAffiliation.AWAY, baseSpeed, homeCount, awayCount, -1.0);
        Assert.assertEquals("AWAY uphill speed should be 0.75x base speed", baseSpeed * 0.75, awayUphill, 0.001);

        // AWAY moving right (+1.0, downhill away from enemy goal with +20 HOME push):
        // Unilateral: -0.05 (-5%)
        // Hill: (+1.0) * (+20) * 0.01 = +0.20 (+20%)
        // Total: 1.0 - 0.05 + 0.20 = 1.15 (+15%)
        double awayDownhill = engine.getLaneMinionSpeed(0, TeamAffiliation.AWAY, baseSpeed, homeCount, awayCount, 1.0);
        Assert.assertEquals("AWAY downhill speed should be 1.15x base speed", baseSpeed * 1.15, awayDownhill, 0.001);

        // Vertical movement (dirX = 0.0, hill effect is X-specific, so 0% hill effect):
        // Unilateral: +0.05 (+5%) for HOME
        // Hill: 0.0
        // Total: 1.05
        double homeVertical = engine.getLaneMinionSpeed(0, TeamAffiliation.HOME, baseSpeed, homeCount, awayCount, 0.0);
        Assert.assertEquals("HOME vertical speed should be 1.05x base speed (0% hill effect)", baseSpeed * 1.05, homeVertical, 0.001);
    }

    @Test
    public void testTitanXSpecificSpeedIntegration() {
        GameEngine engine = new GameEngine();
        engine.c = new gameserver.engine.Const();

        Titan titan = new Titan(500, 500, TeamAffiliation.HOME, TitanType.WARRIOR);
        
        // Running right: getDirX() = 1.0
        titan.runRight = 1;
        titan.runLeft = 0;
        Assert.assertEquals(1.0, titan.getDirX(), 0.001);

        // General actualSpeed(context) uses dirX = 0.0 (no hill effect for vertical/general speed)
        double defaultSpeed = titan.actualSpeed(engine);
        double explicitVerticalSpeed = titan.actualSpeed(engine, 0.0);
        Assert.assertEquals(explicitVerticalSpeed, defaultSpeed, 0.001);

        // Explicit horizontal speeds (with dirX = 1.0 for right, dirX = -1.0 for left)
        double rightSpeed = titan.actualSpeed(engine, 1.0);
        double leftSpeed = titan.actualSpeed(engine, -1.0);
        Assert.assertNotEquals(rightSpeed, defaultSpeed);
    }
}
