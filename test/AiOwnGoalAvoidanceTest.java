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
}
