import gameserver.Const;
import gameserver.effects.EffectId;
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

public class AiOwnGoalAvoidanceTest {

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

    @Test
    public void testIsNearFriendlyGoalDetection() {
        GameEngine engine = createStandardGame();
        // HOME team goals are at X around 256-348.
        // homeHiGoal center: ~291, 625
        // lowGoals[0] center: ~326, 407
        // lowGoals[1] center: ~326, 843

        Assert.assertTrue("Directly in front of Home high goal should be detected as near friendly goal",
                engine.isNearFriendlyGoal(TeamAffiliation.HOME, 350, 625, 65.0));

        Assert.assertTrue("Directly in front of Home top sidegoal should be detected as near friendly goal",
                engine.isNearFriendlyGoal(TeamAffiliation.HOME, 360, 407, 65.0));

        Assert.assertTrue("Directly in front of Home bot sidegoal should be detected as near friendly goal",
                engine.isNearFriendlyGoal(TeamAffiliation.HOME, 360, 843, 65.0));

        Assert.assertFalse("Midfield position should NOT be near friendly goal",
                engine.isNearFriendlyGoal(TeamAffiliation.HOME, 1000, 600, 65.0));

        // Positions behind hoops must NOT be flagged as near friendly goals
        Assert.assertFalse("Behind Home high goal must NOT be detected as near friendly goal",
                engine.isNearFriendlyGoal(TeamAffiliation.HOME, 150, 625, 65.0));

        Assert.assertFalse("Behind Home top sidegoal must NOT be detected as near friendly goal",
                engine.isNearFriendlyGoal(TeamAffiliation.HOME, 150, 407, 65.0));

        Assert.assertFalse("Behind Away high goal must NOT be detected as near friendly goal for AWAY",
                engine.isNearFriendlyGoal(TeamAffiliation.AWAY, 1950, 625, 65.0));

        // AWAY team goals are at X around 1775-1856.
        Assert.assertTrue("Directly in front of Away high goal should be detected as near friendly goal for AWAY",
                engine.isNearFriendlyGoal(TeamAffiliation.AWAY, 1750, 625, 65.0));

        Assert.assertFalse("Away goal position should NOT be friendly goal for HOME team",
                engine.isNearFriendlyGoal(TeamAffiliation.HOME, 1750, 625, 65.0));
    }

    @Test
    public void testWouldTitanEnterFriendlyGoal() {
        GameEngine engine = createStandardGame();
        Titan titan = engine.players[2]; // HOME field titan
        titan.setType(TitanType.WARRIOR);
        titan.team = TeamAffiliation.HOME;
        titan.possession = 1;

        // Position titan right outside Home top sidegoal
        // lowGoals[0] is at x=305, y=354, w=43, h=107
        titan.X = 370;
        titan.Y = 380;

        // Moving left (dx = -20) would push ball into goal
        boolean entersOnMoveLeft = engine.wouldTitanEnterFriendlyGoal(titan, -20.0, 0.0);
        Assert.assertTrue("Moving left towards sidegoal must be flagged as entering friendly goal", entersOnMoveLeft);

        // Moving right (dx = +20) away from goal
        boolean entersOnMoveRight = engine.wouldTitanEnterFriendlyGoal(titan, 20.0, 0.0);
        Assert.assertFalse("Moving right away from sidegoal must NOT enter friendly goal", entersOnMoveRight);
    }

    @Test
    public void testBackwardEvadeAvoidsFriendlyGoalWhenNearGoal() {
        GameEngine engine = createStandardGame();
        Titan carrier = engine.players[1]; // HOME team field titan with ball
        carrier.team = TeamAffiliation.HOME;
        carrier.possession = 1;
        carrier.X = 420;
        carrier.Y = 400; // in front of top sidegoal

        Titan defender = engine.players[5]; // AWAY team defender
        defender.team = TeamAffiliation.AWAY;
        defender.X = 500;
        defender.Y = 400;

        double cx = carrier.X + carrier.width / 2.0;
        double cy = carrier.Y + carrier.height / 2.0;

        double[] target = engine.calculateBackwardDiagonalEvadeTarget(carrier, defender, cx, cy);

        // Target must NOT be in the friendly goal danger zone!
        Assert.assertFalse("Evade target must not enter friendly goal danger zone",
                engine.isNearFriendlyGoal(TeamAffiliation.HOME, target[0], target[1], 65.0));

        // It should prefer vertical evasion (target X >= cx - 10) instead of retreating into the net
        Assert.assertTrue("Target X must not plunge backwards into own sidegoal",
                target[0] >= cx - 10.0);
    }

    @Test
    public void testDefenderRetreatAvoidsFriendlyGoal() {
        GameEngine engine = createStandardGame();
        Titan carrier = engine.players[1]; // HOME team field titan with ball
        carrier.team = TeamAffiliation.HOME;
        carrier.possession = 1;
        carrier.X = 420;
        carrier.Y = 400; // in front of top sidegoal

        Titan defender = engine.players[5]; // AWAY defender pressing from front
        defender.team = TeamAffiliation.AWAY;
        defender.X = 490;
        defender.Y = 400;

        // Position other players far away
        for (Titan t : engine.players) {
            if (t != null && t != carrier && t != defender) {
                t.X = 1600;
                t.Y = 1000;
            }
        }

        engine.evaluateOnBallOffense(carrier, TeamAffiliation.AWAY);

        Assert.assertFalse("AI target X and Y must not retreat into friendly goal",
                engine.isNearFriendlyGoal(TeamAffiliation.HOME, carrier.aiTargetX, carrier.aiTargetY, 65.0));
    }

    @Test
    public void testTitanWithBallBehindHoopCanMoveVerticallyAcrossGoalY() {
        GameEngine engine = createStandardGame();
        Titan titan = engine.players[1];
        titan.setType(TitanType.WARRIOR);
        titan.team = TeamAffiliation.HOME;
        titan.possession = 1;
        titan.programmed = true;

        // Position titan behind Home high goal (goal is at x=256, y=583, h=84)
        titan.X = 150;
        titan.Y = 500; // Above the goal Y range

        // Marching order into the middle of the goal Y range (e.g. 625)
        titan.marchingOrderX = 150;
        titan.marchingOrderY = 625;

        double initialY = titan.Y;
        engine.programmedCtrl(titan);

        // Titan MUST have moved towards marchingOrderY, not blocked by any goal block
        Assert.assertTrue("Titan behind goal hoop with ball must move vertically towards goal Y", titan.Y > initialY);
    }

    @Test
    public void testGoalieHoldingBallNeverIntersectsFriendlyHoop() {
        GameEngine engine = createStandardGame();

        // 1. Home Goalie at various positions on and around Home hoops
        Titan homeGoalie = engine.players[0];
        homeGoalie.setType(TitanType.GOALIE);
        homeGoalie.team = TeamAffiliation.HOME;
        homeGoalie.possession = 1;

        double[][] testPositionsHome = {
                {256, 583}, // right on home hi goal
                {200, 583}, // behind home hi goal
                {305, 354}, // on top low goal
                {305, 790}, // on bottom low goal
        };

        for (double[] pos : testPositionsHome) {
            homeGoalie.X = pos[0];
            homeGoalie.Y = pos[1];
            engine.updateBallIfPossessed(homeGoalie, 1);

            Assert.assertFalse("Home goalie ball must never intersect home hi goal at (" + pos[0] + ", " + pos[1] + ")",
                    engine.ballIntersectsEllipse(engine.homeHiGoal));
            Assert.assertFalse("Home goalie ball must never intersect home low goal 0 at (" + pos[0] + ", " + pos[1] + ")",
                    engine.ballIntersectsEllipse(engine.lowGoals[0]));
            Assert.assertFalse("Home goalie ball must never intersect home low goal 1 at (" + pos[0] + ", " + pos[1] + ")",
                    engine.ballIntersectsEllipse(engine.lowGoals[1]));
        }

        // 2. Away Goalie at various positions on and around Away hoops
        Titan awayGoalie = engine.players[1];
        awayGoalie.setType(TitanType.GOALIE);
        awayGoalie.team = TeamAffiliation.AWAY;
        awayGoalie.possession = 1;

        double[][] testPositionsAway = {
                {1786, 583}, // on away hi goal
                {1850, 583}, // behind away hi goal
                {1775, 354}, // on away top low goal
                {1775, 790}, // on away bottom low goal
        };

        for (double[] pos : testPositionsAway) {
            awayGoalie.X = pos[0];
            awayGoalie.Y = pos[1];
            engine.updateBallIfPossessed(awayGoalie, 2);

            Assert.assertFalse("Away goalie ball must never intersect away hi goal at (" + pos[0] + ", " + pos[1] + ")",
                    engine.ballIntersectsEllipse(engine.awayHiGoal));
            Assert.assertFalse("Away goalie ball must never intersect away low goal 2 at (" + pos[0] + ", " + pos[1] + ")",
                    engine.ballIntersectsEllipse(engine.lowGoals[2]));
            Assert.assertFalse("Away goalie ball must never intersect away low goal 3 at (" + pos[0] + ", " + pos[1] + ")",
                    engine.ballIntersectsEllipse(engine.lowGoals[3]));
        }
    }

    @Test
    public void testGoalieClearingThroughOwnHoopNeverScores() throws Exception {
        GameEngine engine = createStandardGame();

        Titan homeGoalie = engine.players[0];
        homeGoalie.setType(TitanType.GOALIE);
        homeGoalie.team = TeamAffiliation.HOME;
        homeGoalie.X = 256;
        homeGoalie.Y = 583;
        homeGoalie.possession = 1;

        // Ball starts on the high goal
        engine.ball.X = 256;
        engine.ball.Y = 583;
        double initialAwayScore = engine.away.score;

        // Goalie shoots
        homeGoalie.actionState = Titan.TitanState.SHOOT;
        homeGoalie.actionFrame = 0;
        engine.xKickPow = 1.0;
        engine.yKickPow = 0.0;

        engine.shootingBall(homeGoalie);

        Assert.assertEquals("Defending goalie clearing ball must NEVER score an own goal",
                initialAwayScore, engine.away.score, 0.001);
    }

    @Test
    public void testGoalieWithPossessionNeverBackwardEvadesIntoOwnGoal() {
        GameEngine engine = createStandardGame();

        Titan homeGoalie = engine.players[0];
        homeGoalie.setType(TitanType.GOALIE);
        homeGoalie.team = TeamAffiliation.HOME;
        homeGoalie.X = 350; // At forward crease limit
        homeGoalie.Y = 583;
        homeGoalie.possession = 1;

        // Target is downfield (pass target)
        homeGoalie.aiTargetX = 1200;
        homeGoalie.aiTargetY = 583;
        homeGoalie.aiTargetAction = 1; // pass
        homeGoalie.aiStuckHorizontalTicks = 5; // Simulating stuck ticks

        // Position all other titans far away
        for (int i = 1; i < engine.players.length; i++) {
            engine.players[i].X = 999000;
            engine.players[i].Y = 999000;
        }

        engine.clients.clear(); // pure AI
        engine.yourPlayerTactics();

        // Goalie must not have backward evasion set into own net (marchingOrderX towards net)
        Assert.assertFalse("Goalie must not be programmed to march backwards into own net",
                homeGoalie.programmed && homeGoalie.marchingOrderX < homeGoalie.X);
    }

    @Test
    public void testGoalieAiBoostsWhenMovingToAssignedDestination() {
        GameEngine engine = createStandardGame();
        Titan homeGoalie = engine.players[0];
        homeGoalie.setType(TitanType.GOALIE);
        homeGoalie.team = TeamAffiliation.HOME;
        homeGoalie.possession = 0;
        homeGoalie.fuel = 100.0;
        homeGoalie.isBoosting = false;

        // Position goalie near back of crease
        homeGoalie.X = 256;
        homeGoalie.Y = 625;

        // Ball is out in field, so goalie target on hoop edge is around (291 + 42 = 333, 625), > 50px away
        engine.ball.X = 800;
        engine.ball.Y = 625;

        engine.clients.clear();
        engine.yourPlayerTactics();

        Assert.assertTrue("Goalie AI should boost when destination is further than 0px away and has fuel",
                homeGoalie.isBoosting);
    }

    @Test
    public void testGoalieAiBoostsEvenForSmallMovementsDueToZeroThreshold() {
        GameEngine engine = createStandardGame();
        Titan homeGoalie = engine.players[0];
        homeGoalie.setType(TitanType.GOALIE);
        homeGoalie.team = TeamAffiliation.HOME;
        homeGoalie.possession = 0;
        homeGoalie.fuel = 100.0;
        homeGoalie.isBoosting = false;

        // Position goalie 5px away from assigned target
        homeGoalie.X = 256;
        homeGoalie.Y = 625;
        homeGoalie.aiTargetX = homeGoalie.X + homeGoalie.width / 2.0 + 5.0; // only 5px away!
        homeGoalie.aiTargetY = homeGoalie.Y + homeGoalie.height / 2.0;

        engine.updateAiBoostDecision(homeGoalie);

        Assert.assertTrue("Goalie AI should boost even for 5px movement because threshold is literally 0",
                homeGoalie.isBoosting);
    }

    @Test
    public void testGoalieAiStopsBoostingWhenNearTargetOrOutOfFuel() {
        GameEngine engine = createStandardGame();
        Titan homeGoalie = engine.players[0];
        homeGoalie.setType(TitanType.GOALIE);
        homeGoalie.team = TeamAffiliation.HOME;
        homeGoalie.possession = 0;

        // 1. When already at assigned destination (dist == 0px)
        homeGoalie.fuel = 100.0;
        engine.evaluateAiDecision(homeGoalie);
        // Place goalie directly at assigned destination
        homeGoalie.X = homeGoalie.aiTargetX - homeGoalie.width / 2.0;
        homeGoalie.Y = homeGoalie.aiTargetY - homeGoalie.height / 2.0;
        homeGoalie.isBoosting = true;

        engine.clients.clear();
        engine.yourPlayerTactics();

        Assert.assertFalse("Goalie AI should not boost when at destination (dist == 0)",
                homeGoalie.isBoosting);

        // 2. When out of fuel
        homeGoalie.fuel = 0.0;
        homeGoalie.X = 256;
        homeGoalie.Y = 625;
        homeGoalie.isBoosting = true;

        engine.yourPlayerTactics();

        Assert.assertFalse("Goalie AI must not boost when fuel is 0",
                homeGoalie.isBoosting);
    }

    @Test
    public void testGoalieAiDoesNotBoostWhenHoldingBall() {
        GameEngine engine = createStandardGame();
        Titan homeGoalie = engine.players[0];
        homeGoalie.setType(TitanType.GOALIE);
        homeGoalie.team = TeamAffiliation.HOME;
        homeGoalie.possession = 1;
        homeGoalie.fuel = 100.0;
        homeGoalie.aiTargetX = 1200;
        homeGoalie.aiTargetY = 625;
        homeGoalie.isBoosting = true;

        engine.clients.clear();
        engine.yourPlayerTactics();

        Assert.assertFalse("Goalie AI holding ball should not boost",
                homeGoalie.isBoosting);
    }
}
