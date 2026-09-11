import gameserver.engine.CollisionMath;
import gameserver.engine.GameEngine;
import gameserver.engine.GameOptions;
import gameserver.engine.TeamAffiliation;
import gameserver.engine.TitanPathfinder;
import gameserver.entity.Entity;
import gameserver.entity.Titan;
import gameserver.entity.TitanType;
import gameserver.entity.minions.LaneMinion;
import gameserver.gamemanager.GamePhase;
import networking.ClientPacket;
import networking.PlayerDivider;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TitanPathfinderTest {

    private GameEngine createTestGame() {
        GameEngine engine = new GameEngine();
        List<PlayerDivider> clients = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            PlayerDivider pd = new PlayerDivider(Arrays.asList(i));
            pd.id = i;
            clients.add(pd);
        }
        engine.clients = clients;
        engine.options = new GameOptions();
        engine.colliders = new ArrayList<>();
        engine.initializeServer();
        engine.phase = GamePhase.INGAME;
        engine.lastControlPacket = new ClientPacket[clients.size()];
        for (int i = 0; i < engine.lastControlPacket.length; i++) {
            engine.lastControlPacket[i] = new ClientPacket();
        }
        return engine;
    }

    @Test
    public void testDirectLineOfSightProducesSingleSegment() {
        boolean[] emptyGrid = new boolean[TitanPathfinder.TOTAL_CELLS];
        int[][] path = TitanPathfinder.computePath(100, 300, 400, 300, emptyGrid);
        Assert.assertNotNull("Direct unobstructed path should succeed", path);
        Assert.assertEquals("Direct line of sight should produce 1 waypoint", 1, path.length);
        Assert.assertEquals(400, path[0][0]);
        Assert.assertEquals(300, path[0][1]);
    }

    @Test
    public void testRoutesAroundSolidObstacle() {
        boolean[] grid = new boolean[TitanPathfinder.TOTAL_CELLS];

        // Block a vertical wall between X=280..320 and Y=200..400
        int minCol = TitanPathfinder.worldToCol(280);
        int maxCol = TitanPathfinder.worldToCol(320);
        int minRow = TitanPathfinder.worldToRow(200);
        int maxRow = TitanPathfinder.worldToRow(400);

        for (int r = minRow; r <= maxRow; r++) {
            for (int c = minCol; c <= maxCol; c++) {
                grid[TitanPathfinder.toCellIdx(c, r)] = true;
            }
        }

        // Move from (200, 300) to (400, 300) directly across the wall
        int[][] path = TitanPathfinder.computePath(200, 300, 400, 300, grid);
        Assert.assertNotNull("Should find a path around the obstacle", path);
        Assert.assertTrue("Should require multiple straight line segments", path.length > 1);
        Assert.assertTrue("Should not exceed max waypoints limit of 10", path.length <= 10);

        // Final waypoint should be the destination
        int[] lastWp = path[path.length - 1];
        Assert.assertEquals(400, lastWp[0]);
        Assert.assertEquals(300, lastWp[1]);
    }

    @Test
    public void testIgnoresLaneMinionsInStaticGrid() {
        GameEngine engine = createTestGame();
        // Add non-solid LaneMinion to entityPool
        LaneMinion lm = new LaneMinion(300, 300, TeamAffiliation.HOME, 1);
        lm.solid = false;
        engine.entityPool.add(lm);

        boolean[] staticGrid = TitanPathfinder.buildStaticGrid(engine);
        int col = TitanPathfinder.worldToCol(300);
        int row = TitanPathfinder.worldToRow(300);
        Assert.assertFalse("LaneMinion should not block static grid", staticGrid[TitanPathfinder.toCellIdx(col, row)]);
    }

    @Test
    public void testPathInvalidationTriggers() {
        GameEngine engine = createTestGame();
        Titan t = engine.players[0];
        t.X = 500;
        t.Y = 500;
        t.marchingOrderX = 800;
        t.marchingOrderY = 500;
        t.programmed = true;

        // 1. Initial execution calculates path
        TitanPathfinder.executeProgrammedMovement(engine, t);
        Assert.assertNotNull("Path should be computed", t.pathWaypoints);

        // 2. New movement command invalidates
        t.marchingOrderX = 900;
        t.invalidatePath();
        Assert.assertNull("Path should be invalidated", t.pathWaypoints);

        // 3. Recomputes with new anchor
        TitanPathfinder.executeProgrammedMovement(engine, t);
        Assert.assertNotNull("Path should be recomputed", t.pathWaypoints);
        Assert.assertEquals(900, t.pathAnchorOrderX);
    }
}
