import gameserver.effects.EffectId;
import gameserver.engine.Ability;
import gameserver.engine.CollisionMath;
import gameserver.engine.GameEngine;
import gameserver.engine.GameOptions;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.Titan;
import gameserver.entity.TitanType;
import gameserver.gamemanager.GamePhase;
import networking.ClientPacket;
import networking.PlayerDivider;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class UnilateralScoringTest {

    private GameEngine createStandardGame() {
        GameEngine engine = new GameEngine();
        List<PlayerDivider> clients = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            PlayerDivider pd = new PlayerDivider(Arrays.asList(i));
            pd.id = i;
            clients.add(pd);
        }
        engine.clients = clients;
        engine.options = new GameOptions();
        engine.options.playToIndex = 0;
        engine.colliders = new ArrayList<>();
        engine.initializeServer();
        engine.phase = GamePhase.INGAME;
        engine.lastControlPacket = new ClientPacket[clients.size()];
        for (int i = 0; i < engine.lastControlPacket.length; i++) {
            engine.lastControlPacket[i] = new ClientPacket();
        }
        return engine;
    }

    /**
     * Test 1: Warrior Flash (140px) with 14 castlag frames.
     * The flash teleports 140px, but enters a 14-frame (350ms) castlag window
     * where actionState is TitanState.A2 before returning to IDLE.
     */
    @Test
    public void testWarriorFlashBypassesDefenderStealZone() throws Exception {
        GameEngine engine = createStandardGame();
        
        // Attacker (Home Warrior) starting with the ball 126px away from Away Hi Goal
        Titan attacker = engine.players[2]; // Home Warrior
        attacker.setType(TitanType.WARRIOR);
        attacker.team = TeamAffiliation.HOME;
        attacker.X = 1660; // 126px away from Away Hi Goal (x=1786)
        attacker.Y = 583;  // aligned with Hi Goal
        attacker.possession = 1;
        engine.home.hasBall = true;
        engine.away.hasBall = false;

        // Defender (Away Golem with largest champion steal radius = 22) directly in front of attacker
        Titan defender = engine.players[6]; // Away player
        defender.setType(TitanType.GOLEM);
        defender.team = TeamAffiliation.AWAY;
        defender.X = 1720; // 60px ahead of attacker, blocking direct path
        defender.Y = 583;
        defender.possession = 0;

        // Goalie is centered at home/away goal box
        Titan goalie = engine.players[1]; // Away Goalie
        goalie.X = engine.c.GOALIE_XA_MIN;
        goalie.Y = 583;

        // Verify that before flashing, defender is within range to contest if attacker walks forward slowly
        double initialDist = Math.hypot((attacker.X + 35) - (defender.X + 35), (attacker.Y + 35) - (defender.Y + 35));
        Assert.assertEquals("Defender is 60px away in the path", 60.0, initialDist, 1.0);

        // Attacker casts W (Flash) aimed towards the center-right of the goal
        engine.lastControlPacket[engine.clientIndex(attacker)].posX = 1850;
        engine.lastControlPacket[engine.clientIndex(attacker)].posY = (int) attacker.Y + 35;
        engine.lastControlPacket[engine.clientIndex(attacker)].camX = 0;
        engine.lastControlPacket[engine.clientIndex(attacker)].camY = 0;

        // Cast Flash
        Ability abilities = new Ability();
        boolean flashed = abilities.castW(engine, attacker);
        Assert.assertTrue("Flash ability should succeed", flashed);

        // Attacker should have teleported 140px forward, jumping over the defender at X=1720
        Assert.assertTrue("Attacker X should have jumped past defender (X > 1720)", attacker.X > defender.X);
        Assert.assertEquals("Attacker moved 140px forward", 1800.0, attacker.X, 1.0);

        // Verify that Warrior has 14 rCastFrames configured
        Assert.assertEquals("Warrior rCastFrames must be 14 frames", 14, attacker.rCastFrames);
    }

    /**
     * Test 2: Boost Ratios:
     * - Normal boost without ball: 1.50x
     * - Dasher with ball boost: 1.30x
     * Dasher holding ball (3.55 * 1.30 = 4.615) is faster than an unboosted defender (3.57),
     * BUT a boosting defender without ball (3.57 * 1.50 = 5.355) is faster than the Dasher carrier,
     * allowing defensive recovery and counterplay.
     */
    @Test
    public void testBoostRatiosBalance() {
        GameEngine engine = createStandardGame();

        Titan dasher = engine.players[2];
        dasher.setType(TitanType.DASHER);
        dasher.team = TeamAffiliation.HOME;
        dasher.possession = 1;
        dasher.isBoosting = true;
        dasher.fuel = 50.0;

        Titan unboostedDefender = engine.players[5];
        unboostedDefender.setType(TitanType.WARRIOR);
        unboostedDefender.team = TeamAffiliation.AWAY;
        unboostedDefender.possession = 0;
        unboostedDefender.isBoosting = false;

        Titan boostingDefender = engine.players[6];
        boostingDefender.setType(TitanType.WARRIOR);
        boostingDefender.team = TeamAffiliation.AWAY;
        boostingDefender.possession = 0;
        boostingDefender.isBoosting = true;
        boostingDefender.fuel = 50.0;

        double dasherCarrierSpeed = dasher.actualSpeed(engine);
        double unboostedDefSpeed = unboostedDefender.actualSpeed(engine);
        double boostingDefSpeed = boostingDefender.actualSpeed(engine);

        // 1. Dasher holding ball (4.615) outpaces unboosted defender (3.57)
        Assert.assertTrue("Dasher holding ball must be faster than unboosted defender",
                dasherCarrierSpeed > unboostedDefSpeed);

        // 2. Chasing boosting defender (5.355) outpaces Dasher holding ball (4.615)
        Assert.assertTrue("Boosting defender without ball must be faster than Dasher with ball",
                boostingDefSpeed > dasherCarrierSpeed);

        // 3. Exact ratio checks
        Assert.assertEquals("Carrier boost ratio is 1.30x", 3.55 * 1.30, dasherCarrierSpeed, 0.05);
        Assert.assertEquals("Normal boost ratio is 1.50x", 3.57 * 1.50, boostingDefSpeed, 0.05);
    }

    /**
     * Test 3: Goalie Lateral Speed vs 3-Hoop Vertical Geometry.
     * Distance between Center Hi Goal (y=583) and Top Low Goal (y=354) is 229 px.
     * Goalie speed is 2.70 px/tick (108 px/sec).
     */
    @Test
    public void testGoalieCannotCoverSideHoopFromCenterInTime() {
        GameEngine engine = createStandardGame();

        Titan goalie = engine.players[1]; // Away goalie
        goalie.setType(TitanType.GOALIE);
        goalie.team = TeamAffiliation.AWAY;
        goalie.X = engine.c.GOALIE_XA_MIN;
        goalie.Y = 583; // Centered at Hi Goal

        // Distance to Top Low Goal (Y=354)
        double distY = Math.abs(goalie.Y - engine.c.getI("goal.low.y"));
        Assert.assertEquals("Distance between Hi Goal and Low Goal is 229px", 229.0, distY, 1.0);

        double goalieSpeed = goalie.speed; // 2.70 px/tick
        int ticksToReachLowGoal = (int) Math.ceil(distY / goalieSpeed);
        double secondsToReach = (ticksToReachLowGoal * engine.c.GAMETICK_MS) / 1000.0;

        // Goalie requires over 2 seconds to shift to low goal at 2.70 px/tick
        Assert.assertTrue("Goalie requires over 1.4s to reposition to side goal", secondsToReach >= 1.4);
    }

    /**
     * Test 4: Walk-in Goal Detection.
     * When an attacker in possession flashes/walks so the ball intersects the goal ellipse,
     * the engine scores instantly on the tick detectGoals() runs without needing a throw.
     */
    @Test
    public void testDirectWalkInGoalScoring() {
        GameEngine engine = createStandardGame();

        Titan attacker = engine.players[2];
        attacker.setType(TitanType.WARRIOR);
        attacker.team = TeamAffiliation.HOME;
        attacker.possession = 1;
        engine.home.hasBall = true;
        engine.away.hasBall = false;

        // Position attacker right on the Away Hi Goal (x=1786, y=583, w=70, h=84)
        attacker.X = 1786;
        attacker.Y = 583;
        engine.ball.X = attacker.X + 35 - engine.ball.centerDist;
        engine.ball.Y = attacker.Y + 35 - engine.ball.centerDist;

        double initialScore = engine.home.score;
        engine.detectGoals();

        Assert.assertTrue("Ball in hoop ellipse triggers immediate goal scoring",
                engine.home.score > initialScore);
    }

    /**
     * Test 5: Warrior Flash Castlag Locks Movement & Actions.
     * During the 14 castlag frames (350ms):
     * 1. actionState must be TitanState.A2.
     * 2. Movement keys sent by the client must NOT cause the titan to translate.
     * 3. Shooting requests must be rejected (cannot shoot during A2).
     * 4. After 14 ticks of attack2(t), actionState returns to IDLE and movement resumes.
     */
    @Test
    public void testWarriorCastlagPreventsImmediateMovementAndShooting() {
        GameEngine engine = createStandardGame();

        Titan warrior = engine.players[2];
        warrior.setType(TitanType.WARRIOR);
        warrior.team = TeamAffiliation.HOME;
        warrior.X = 500;
        warrior.Y = 500;
        warrior.possession = 1;
        engine.home.hasBall = true;

        PlayerDivider pd = engine.clients.get(1); // selection for players[2] is 3 -> pd.id 2
        pd.selection = 3;

        // Player aims right and presses R (Flash)
        int cIdx = engine.clientIndex(warrior);
        engine.lastControlPacket[cIdx].posX = 700;
        engine.lastControlPacket[cIdx].posY = (int) warrior.Y + 35;
        engine.lastControlPacket[cIdx].camX = 0;
        engine.lastControlPacket[cIdx].camY = 0;

        ClientPacket rPacket = new ClientPacket();
        rPacket.R = true;
        rPacket.posX = 700;
        rPacket.posY = (int) warrior.Y + 35;
        rPacket.camX = 0;
        rPacket.camY = 0;
        engine.processClientPacket(pd, rPacket);

        // Flash happened: warrior moved 140px and entered A2
        Assert.assertEquals("Warrior is in A2 castlag state", Titan.TitanState.A2, warrior.actionState);
        Assert.assertTrue("Warrior isStunned is true during castlag", engine.effectPool.isStunned(warrior));
        Assert.assertTrue("Warrior isRooted is true during castlag", engine.effectPool.isRooted(warrior));
        double postFlashX = warrior.X;
        Assert.assertEquals("Warrior teleported 140px", 640.0, postFlashX, 1.0);

        // While in castlag, player sends move key (RIGHT) and shot attempt
        ClientPacket moveAndShotPacket = new ClientPacket();
        moveAndShotPacket.RIGHT = true;
        moveAndShotPacket.shotBtn = true;
        moveAndShotPacket.posX = 800;
        moveAndShotPacket.posY = 500;
        engine.processClientPacket(pd, moveAndShotPacket);

        // 1. Action state should remain A2, not changed to SHOOT
        Assert.assertEquals("Shooting must be blocked during castlag", Titan.TitanState.A2, warrior.actionState);

        // 2. Simulating gametick movement should NOT move warrior
        boolean canRun = (warrior.actionState == Titan.TitanState.IDLE);
        Assert.assertFalse("canRun must be false during castlag", canRun);

        // Direct calls to movement controllers during castlag MUST be no-ops
        engine.runRightCtrl(warrior);
        engine.runLeftCtrl(warrior);
        engine.runUpCtrl(warrior);
        engine.runDownCtrl(warrior);
        engine.programmedCtrl(warrior);
        Assert.assertEquals("Warrior X must not move during castlag", postFlashX, warrior.X, 0.01);

        // Simulate (rCastFrames - 1) ticks of castlag: warrior must remain at postFlashX
        for (int frame = 0; frame < warrior.rCastFrames - 1; frame++) {
            engine.attack2(warrior);
            Assert.assertEquals("Warrior remains in A2 during castlag frames", Titan.TitanState.A2, warrior.actionState);
            engine.runRightCtrl(warrior);
            Assert.assertEquals("Warrior X must not move during castlag", postFlashX, warrior.X, 0.01);
        }

        // On final tick, attack2 finishes and restores IDLE and resumes movement
        engine.attack2(warrior);
        Assert.assertEquals("Warrior returns to IDLE after castlag frames", Titan.TitanState.IDLE, warrior.actionState);
        Assert.assertEquals("Movement resumed after castlag", 1, warrior.runRight);
    }

    /**
     * Test 6: RTS Click-to-Move (MV_CLICK) blocked during castlag, then resumes on IDLE.
     */
    @Test
    public void testRtsClickToMoveBlockedDuringCastlag() {
        GameEngine engine = createStandardGame();

        Titan warrior = engine.players[2];
        warrior.setType(TitanType.WARRIOR);
        warrior.team = TeamAffiliation.HOME;
        warrior.X = 500;
        warrior.Y = 500;
        warrior.actionState = Titan.TitanState.A2;
        warrior.actionFrame = 0;

        PlayerDivider pd = engine.clients.get(1);
        pd.selection = 3;

        // Player right-clicks to move while in A2
        ClientPacket rtsClick = new ClientPacket();
        rtsClick.MV_CLICK = true;
        rtsClick.posX = 900;
        rtsClick.posY = 500;
        engine.processClientPacket(pd, rtsClick);

        // Marching order destination updated
        Assert.assertEquals(900.0, warrior.marchingOrderX, 0.01);

        // programmedCtrl must not move the warrior while in castlag
        engine.programmedCtrl(warrior);
        Assert.assertEquals("Warrior position must remain unchanged during castlag", 500.0, warrior.X, 0.01);

        // When castlag ends, programmedCtrl moves warrior towards destination
        warrior.actionState = Titan.TitanState.IDLE;
        engine.programmedCtrl(warrior);
        Assert.assertTrue("Warrior resumes moving towards destination once IDLE", warrior.X > 500.0);
    }

    /**
     * Test 7: Flat +0.32 Shot Power Configuration.
     * All titans have base + 0.32 throwPower (Marksman 1.82, Warrior 1.12, Support 1.14).
     */
    @Test
    public void testFlatShotPowerValues() {
        GameEngine engine = createStandardGame();

        Titan marksman = new Titan(0, 0, TeamAffiliation.HOME, TitanType.MARKSMAN);
        Titan warrior = new Titan(0, 0, TeamAffiliation.HOME, TitanType.WARRIOR);
        Titan support = new Titan(0, 0, TeamAffiliation.HOME, TitanType.SUPPORT);

        Assert.assertEquals("Marksman throwPower should be 1.82 (flat +0.32)", 1.82, marksman.throwPower, 0.01);
        Assert.assertEquals("Warrior throwPower should be 1.12 (flat +0.32)", 1.12, warrior.throwPower, 0.01);
        Assert.assertEquals("Support throwPower should be 1.14 (flat +0.32)", 1.14, support.throwPower, 0.01);
    }

    /**
     * Test 8: Goalie save when positioned to the rear of his hoops.
     * Making a save while behind the goal line must gain possession, hold ball outside hoop,
     * record save stats, and NOT trigger an own goal.
     */
    @Test
    public void testGoalieSaveDoesNotTriggerOwnGoalWhenPositionedBehindHoop() throws Exception {
        GameEngine engine = createStandardGame();

        // Home Goalie positioned at X=200, Y=583 (behind Home Hi Goal at X=256)
        Titan homeGoalie = engine.players[0]; // Home Goalie (numSel=1)
        homeGoalie.setType(TitanType.GOALIE);
        homeGoalie.team = TeamAffiliation.HOME;
        homeGoalie.X = 200;
        homeGoalie.Y = 583;
        homeGoalie.possession = 0;

        // Away shooter shooting towards Home Hi Goal
        Titan shooter = engine.players[4]; // Away Titan
        shooter.setType(TitanType.WARRIOR);
        shooter.team = TeamAffiliation.AWAY;
        shooter.X = 500;
        shooter.Y = 583;
        shooter.actionState = Titan.TitanState.SHOOT;
        shooter.actionFrame = 1;
        engine.xKickPow = -1.0;
        engine.yKickPow = 0.0;

        // Place ball flying towards Home Goalie (near Goalie's intercept hitbox)
        engine.ball.X = 230;
        engine.ball.Y = 583;

        double initialAwayScore = engine.away.score;

        // Execute shot substep
        engine.shootingBall(shooter);

        // 1. Goalie must have gained possession
        Assert.assertEquals("Goalie must gain possession upon save", 1, homeGoalie.possession);

        // 2. Score must not have increased
        Assert.assertEquals("Away team score must not increase on goalie save", initialAwayScore, engine.away.score, 0.001);

        // 3. Ball must be pushed outside the goal hoop
        boolean inHoop = engine.ballIntersectsEllipse(engine.hiGoals[0]);
        Assert.assertFalse("Ball must be held outside goal hoop", inHoop);
    }
}
