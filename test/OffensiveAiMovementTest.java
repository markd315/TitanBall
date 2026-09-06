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

public class OffensiveAiMovementTest {

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
    public void testHorizontalImpededDetection() {
        GameEngine engine = createStandardGame();
        Titan carrier = engine.players[1]; // HOME team
        carrier.team = TeamAffiliation.HOME;
        carrier.X = 500;
        carrier.Y = 500;

        Titan defender = engine.players[5]; // AWAY team
        defender.team = TeamAffiliation.AWAY;

        // 1. Defender directly ahead in the horizontal corridor (X = 580, Y = 510)
        defender.X = 580;
        defender.Y = 510;
        Titan impeding = engine.getHorizontalImpedingEnemy(carrier, TeamAffiliation.AWAY);
        Assert.assertNotNull("Defender in horizontal corridor should be detected as impeding", impeding);
        Assert.assertEquals("Should detect the correct defender", defender.id, impeding.id);

        // 2. Defender ahead but vertically separated by 150px (different lane/plane)
        defender.Y = 660;
        Titan notImpedingVertical = engine.getHorizontalImpedingEnemy(carrier, TeamAffiliation.AWAY);
        Assert.assertNull("Defender far vertically should not be considered horizontal impediment", notImpedingVertical);

        // 3. Defender behind the carrier along X (X = 400, Y = 500)
        defender.X = 400;
        defender.Y = 500;
        Titan notImpedingBehind = engine.getHorizontalImpedingEnemy(carrier, TeamAffiliation.AWAY);
        Assert.assertNull("Defender behind carrier should not be considered horizontal impediment in forward vector", notImpedingBehind);
    }

    @Test
    public void testBackwardsAndVerticalPassingOptions() {
        GameEngine engine = createStandardGame();
        Titan carrier = engine.players[1]; // HOME team
        carrier.team = TeamAffiliation.HOME;
        carrier.possession = 1;
        carrier.X = 600;
        carrier.Y = 500;

        Titan teammate1 = engine.players[2]; // HOME teammate trailing behind
        teammate1.team = TeamAffiliation.HOME;
        teammate1.X = 460; // 140px behind carrier
        teammate1.Y = 500;

        Titan teammate2 = engine.players[3]; // HOME teammate vertically open
        teammate2.team = TeamAffiliation.HOME;
        teammate2.X = 610;
        teammate2.Y = 640; // 140px vertical separation

        // Put enemies away so they don't block
        for (Titan t : engine.players) {
            if (t != null && t.team == TeamAffiliation.AWAY) {
                t.X = 1500;
                t.Y = 1000;
            }
        }

        // 1. Both teammates are valid passing options
        Titan passTarget = engine.findBackwardsOrVerticalPassTarget(carrier, TeamAffiliation.AWAY);
        Assert.assertNotNull("Should find a backwards or vertical pass target", passTarget);
        Assert.assertTrue("Should select either trailing or vertical teammate",
                passTarget.id.equals(teammate1.id) || passTarget.id.equals(teammate2.id));

        // 2. If passing line to teammate1 is blocked by an enemy, should pick teammate2
        Titan enemyBlocker = engine.players[5];
        enemyBlocker.team = TeamAffiliation.AWAY;
        enemyBlocker.X = 530; // Between carrier (600) and teammate1 (460)
        enemyBlocker.Y = 500;

        Titan safePassTarget = engine.findBackwardsOrVerticalPassTarget(carrier, TeamAffiliation.AWAY);
        Assert.assertNotNull("Should find an unblocked pass target", safePassTarget);
        Assert.assertEquals("Should select teammate2 when teammate1 lane is blocked", teammate2.id, safePassTarget.id);
    }

    @Test
    public void testEvasiveActionPrioritiesWhenImpeded() {
        GameEngine engine = createStandardGame();
        Titan carrier = engine.players[1]; // HOME team
        carrier.team = TeamAffiliation.HOME;
        carrier.possession = 1;
        carrier.X = 500;
        carrier.Y = 500;
        carrier.aiStuckHorizontalTicks = 0;

        Titan defender = engine.players[5]; // AWAY team directly impeding
        defender.team = TeamAffiliation.AWAY;
        defender.X = 570;
        defender.Y = 500;

        // Position other players far away
        for (Titan t : engine.players) {
            if (t != null && t != carrier && t != defender) {
                t.X = 1600;
                t.Y = 1000;
            }
        }

        // Priority 1: When not stuck (stuck ticks < 2), AI should run forward diagonally/vertically
        engine.evaluateOnBallOffense(carrier, TeamAffiliation.AWAY);
        Assert.assertEquals("Action should be RUN", 0, carrier.aiTargetAction);
        Assert.assertTrue("Target X should advance forward (HOME advances +X)", carrier.aiTargetX > carrier.X + carrier.width / 2.0);
        Assert.assertNotEquals("Target Y should run vertically to evade enemy Y", carrier.Y + carrier.height / 2.0, carrier.aiTargetY, 1.0);

        // Priority 2: When stuck (stuck ticks >= 2) and a backwards/vertical pass is available
        carrier.aiStuckHorizontalTicks = 2;
        Titan trailingTeammate = engine.players[2];
        trailingTeammate.team = TeamAffiliation.HOME;
        trailingTeammate.X = 350; // Trailing behind
        trailingTeammate.Y = 500;

        engine.evaluateOnBallOffense(carrier, TeamAffiliation.AWAY);
        Assert.assertEquals("Action should be PASS (Priority 2)", 1, carrier.aiTargetAction);
        Assert.assertEquals("Target should be the trailing teammate", trailingTeammate.X + trailingTeammate.width / 2.0, carrier.aiTargetX, 1.0);

        // Priority 3: When stuck (stuck ticks >= 2) and NO pass option exists, run backwards diagonally to create space
        trailingTeammate.X = 1600; // Move teammate far away out of range
        trailingTeammate.Y = 1000;
        carrier.aiTargetAction = 0;

        engine.evaluateOnBallOffense(carrier, TeamAffiliation.AWAY);
        Assert.assertEquals("Action should be RUN", 0, carrier.aiTargetAction);
        Assert.assertTrue("Target X should retreat backwards diagonally (HOME retreats -X) to create space",
                carrier.aiTargetX < carrier.X + carrier.width / 2.0);
        Assert.assertNotEquals("Target Y should deviate vertically to avoid straight-line trap",
                carrier.Y + carrier.height / 2.0, carrier.aiTargetY, 1.0);
    }

    @Test
    public void testForwardEvadeTargetAndBackwardDiagonalTarget() {
        GameEngine engine = createStandardGame();
        Titan carrier = engine.players[1];
        carrier.team = TeamAffiliation.HOME;
        carrier.X = 600;
        carrier.Y = 400;

        Titan defender = engine.players[5];
        defender.team = TeamAffiliation.AWAY;
        defender.X = 670;
        defender.Y = 400;

        double cx = carrier.X + carrier.width / 2.0;
        double cy = carrier.Y + carrier.height / 2.0;

        double[] fwd = engine.calculateForwardEvadeTarget(carrier, defender, cx, cy);
        Assert.assertTrue("Forward evade target X must be greater than current center for HOME team", fwd[0] > cx);
        Assert.assertTrue("Forward evade target Y must deviate significantly vertically", Math.abs(fwd[1] - cy) >= 100.0);

        double[] bwd = engine.calculateBackwardDiagonalEvadeTarget(carrier, defender, cx, cy);
        Assert.assertTrue("Backward evade target X must be less than current center for HOME team", bwd[0] < cx);
        Assert.assertTrue("Backward evade target Y must deviate significantly vertically", Math.abs(bwd[1] - cy) >= 80.0);
    }
}
