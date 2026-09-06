import gameserver.engine.CollisionMath;
import gameserver.engine.GameEngine;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.Titan;
import gameserver.entity.TitanType;
import gameserver.entity.minions.Wall;
import networking.PlayerDivider;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BallWallCollisionTest {

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
    public void testVerticalWallLeftFaceMirrorReflection() {
        GameEngine engine = createStandardGame();
        // Place wall at (1000, 500), width=12, height=120
        Wall wall = new Wall(engine, 1000, 500);
        wall.width = 12;
        wall.height = 120;
        engine.entityPool.add(wall);

        // Place ball to the left of the wall, moving right and slightly down
        engine.ball.X = 970;
        engine.ball.Y = 550;
        engine.xKickPow = 0.25;  // moving right
        engine.yKickPow = -0.10; // moving down in Cartesian / screen

        // Simulate 800 substeps
        double dx = 0.05 * engine.xKickPow;
        double dy = -0.05 * engine.yKickPow;
        double[] vel = new double[]{ dx, dy };

        // Step until collision
        gameserver.entity.Entity[] snap = engine.entityPool.toArray(new gameserver.entity.Entity[0]);
        for (int i = 0; i < 800; i++) {
            engine.ball.X += vel[0];
            engine.ball.Y += vel[1];
            engine.bounceWalls(snap, vel);
        }

        // Must reflect horizontally (xKickPow < 0) and not penetrate inside the wall
        Assert.assertTrue("xKickPow must be negative after bouncing off left face", engine.xKickPow < 0);
        Assert.assertTrue("Ball must be to the left of the wall", engine.ball.X + engine.ball.width <= wall.X + 0.01);
    }

    @Test
    public void testFieldBoundaryReflections() {
        GameEngine engine = createStandardGame();
        
        // 1. Right wall bounce
        engine.ball.X = engine.c.MAX_X + 1;
        engine.ball.Y = 500;
        engine.xKickPow = 0.25;
        engine.yKickPow = 0.0;
        engine.bounceWalls();
        Assert.assertEquals("Ball should be clamped to MAX_X", engine.c.MAX_X, engine.ball.X, 0.001);
        Assert.assertTrue("xKickPow should be reversed to negative", engine.xKickPow < 0);

        // 2. Left wall bounce
        engine.ball.X = engine.c.MIN_X - 1;
        engine.ball.Y = 500;
        engine.xKickPow = -0.25;
        engine.yKickPow = 0.0;
        engine.bounceWalls();
        Assert.assertEquals("Ball should be clamped to MIN_X", engine.c.MIN_X, engine.ball.X, 0.001);
        Assert.assertTrue("xKickPow should be reversed to positive", engine.xKickPow > 0);

        // 3. Top wall bounce
        engine.ball.X = 500;
        engine.ball.Y = engine.c.MIN_Y - 1;
        engine.yKickPow = 0.25; // moving up
        engine.bounceWalls();
        Assert.assertEquals("Ball should be clamped to MIN_Y", engine.c.MIN_Y, engine.ball.Y, 0.001);
        Assert.assertTrue("yKickPow should be reversed to negative (moving down)", engine.yKickPow < 0);

        // 4. Bottom wall bounce
        engine.ball.X = 500;
        engine.ball.Y = engine.c.MAX_Y + 1;
        engine.yKickPow = -0.25; // moving down
        engine.bounceWalls();
        Assert.assertEquals("Ball should be clamped to MAX_Y", engine.c.MAX_Y, engine.ball.Y, 0.001);
        Assert.assertTrue("yKickPow should be reversed to positive (moving up)", engine.yKickPow > 0);
    }

    @Test
    public void testDeadwallsStopsBall() {
        GameEngine engine = createStandardGame();
        engine.homeGoaliePurchasedUpgrades.add("fortress.t4.deadwalls");

        // Hit home back wall (MIN_X)
        engine.ball.X = engine.c.MIN_X - 1;
        engine.ball.Y = 500;
        engine.xKickPow = -0.25;
        engine.yKickPow = 0.1;
        engine.bounceWalls();

        Assert.assertEquals("Ball X should be clamped at MIN_X", engine.c.MIN_X, engine.ball.X, 0.001);
        Assert.assertEquals("xKickPow must be 0 on deadwalls", 0.0, engine.xKickPow, 0.0001);
        Assert.assertEquals("yKickPow must be 0 on deadwalls", 0.0, engine.yKickPow, 0.0001);
    }

    @Test
    public void testCollisionMathCollisionSide() {
        CollisionMath.Bounds mover = new CollisionMath.Bounds(95, 100, 10, 10);
        CollisionMath.Bounds obstacle = new CollisionMath.Bounds(100, 50, 20, 100);

        // Mover moving right (dx > 0) hitting left face of obstacle
        CollisionMath.CollisionSide side = CollisionMath.getCollisionSide(mover, obstacle, 1.0, 0.0);
        Assert.assertEquals(CollisionMath.CollisionSide.LEFT, side);

        // Mover moving down (dy > 0) hitting top face of obstacle
        CollisionMath.Bounds moverTop = new CollisionMath.Bounds(105, 45, 10, 10);
        CollisionMath.CollisionSide sideTop = CollisionMath.getCollisionSide(moverTop, obstacle, 0.0, 1.0);
        Assert.assertEquals(CollisionMath.CollisionSide.TOP, sideTop);
    }

    @Test
    public void testShootingBallWallCollisionNoPassThrough() throws Exception {
        GameEngine engine = createStandardGame();
        Titan shooter = engine.players[3]; // Forward
        shooter.team = TeamAffiliation.HOME;
        shooter.possession = 1;
        shooter.actionState = Titan.TitanState.SHOOT;
        shooter.actionFrame = 0;
        shooter.X = 800;
        shooter.Y = 500;

        // Spawn a vertical wall in front of the shot at X=900
        Wall wall = new Wall(engine, 900, 450);
        wall.width = 12;
        wall.height = 120;
        engine.entityPool.add(wall);

        // Aim directly at the wall
        engine.xKickPow = 0.25;
        engine.yKickPow = 0.0;

        // Run all 20 frames of shooting
        for (int f = 0; f < 20; f++) {
            if (shooter.actionState == Titan.TitanState.SHOOT) {
                engine.shootingBall(shooter);
            }
        }

        // Ball must have bounced and ended up to the left of the wall (never passed through to X > 912)
        Assert.assertTrue("Ball must not pass through the wall (ball.X <= 900)", engine.ball.X <= wall.X);
    }

    @Test
    public void testCornerHitMirroredReflectionNoDirectionReversal() {
        GameEngine engine = createStandardGame();
        // Place vertical wall pane at (1000, 500), width=12, height=120
        Wall wall = new Wall(engine, 1000, 500);
        wall.width = 12;
        wall.height = 120;
        engine.entityPool.add(wall);

        // Place ball near top corner of wall, moving right (xKickPow > 0) and down (yKickPow < 0)
        engine.ball.X = 990;
        engine.ball.Y = 495; // slightly above top edge (500)
        engine.xKickPow = 0.3;
        engine.yKickPow = -0.2; // moving down

        double dx = 0.05 * engine.xKickPow;
        double dy = -0.05 * engine.yKickPow;
        double[] vel = new double[]{ dx, dy };

        gameserver.entity.Entity[] snap = engine.entityPool.toArray(new gameserver.entity.Entity[0]);
        engine.bounceWalls(snap, vel);

        // Horizontal velocity component should negate (mirror reflection), vertical component preserved
        Assert.assertTrue("xKickPow must flip to negative on left face hit", engine.xKickPow < 0);
        Assert.assertTrue("yKickPow must preserve sign (no direction reversal)", engine.yKickPow < 0);
    }

    @Test
    public void testPortalNullCreatedByIdAndLoopSafety() {
        GameEngine engine = createStandardGame();
        gameserver.entity.minions.Portal portal = new gameserver.entity.minions.Portal(); // createdById is null
        Titan titan = engine.players[0];
        titan.team = TeamAffiliation.HOME;

        // Trigger collision with null createdById should not throw NPE
        portal.triggerCollide(engine, titan);

        // Spawn portal with valid titan and place solid wall around destination
        gameserver.entity.minions.Portal p1 = new gameserver.entity.minions.Portal(TeamAffiliation.HOME, titan, engine.entityPool, 200, 200, engine);
        gameserver.entity.minions.Portal p2 = new gameserver.entity.minions.Portal(TeamAffiliation.HOME, titan, engine.entityPool, 400, 400, engine);
        engine.entityPool.add(p1);
        engine.entityPool.add(p2);

        // Trigger collision with titan inside portal - must terminate cleanly without freezing
        p1.triggerCollide(engine, titan);
        Assert.assertEquals(titan.getX(), 400 + 25 - 35, 1.0);
    }
}
