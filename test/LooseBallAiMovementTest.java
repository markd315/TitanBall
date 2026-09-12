import gameserver.effects.EffectId;
import gameserver.effects.effects.EmptyEffect;
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

public class LooseBallAiMovementTest {

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
        // Ensure all players start unpossessed and idle
        for (Titan t : engine.players) {
            if (t != null) {
                t.possession = 0;
                t.actionState = Titan.TitanState.IDLE;
            }
        }
        return engine;
    }

    @Test
    public void testTeammateCloserDistance_aiTakesAttackingPosture() {
        GameEngine engine = createStandardGame();
        Titan ai = engine.players[2]; // HOME field titan
        ai.team = TeamAffiliation.HOME;
        ai.possession = 0;
        ai.fuel = 50.0;
        ai.speed = 3.5;
        ai.X = 700;
        ai.Y = 480;

        Titan teammate = engine.players[3]; // HOME field titan
        teammate.team = TeamAffiliation.HOME;
        teammate.possession = 0;
        teammate.fuel = 50.0;
        teammate.speed = 3.5;
        teammate.X = 920;
        teammate.Y = 500;

        // Position all other players far away
        for (Titan t : engine.players) {
            if (t != null && t != ai && t != teammate) {
                t.X = 1800;
                t.Y = 1000;
            }
        }

        // Loose ball at (1000, 500)
        engine.ball.X = 1000;
        engine.ball.Y = 500;
        double ballCX = engine.ball.X + engine.ball.width / 2.0;
        double ballCY = engine.ball.Y + engine.ball.height / 2.0;

        engine.evaluateAiDecision(ai);

        // AI should NOT run towards the ball
        Assert.assertFalse("AI should not target ball center when teammate is closer",
                Math.abs(ai.aiTargetX - ballCX) < 1.0 && Math.abs(ai.aiTargetY - ballCY) < 1.0);

        // AI should target a forward attacking posture (HOME forward is +X, so targetX > ballCX)
        Assert.assertTrue("AI target should be forward downfield from ball", ai.aiTargetX > ballCX);

        // Distance from ball to AI target should be ~1 pass length away (~297 px)
        double distFromBall = Math.hypot(ai.aiTargetX - ballCX, ai.aiTargetY - ballCY);
        Assert.assertTrue("Target posture should be approximately one pass length away (~260-320px), got: " + distFromBall,
                distFromBall >= 260.0 && distFromBall <= 320.0);
    }

    @Test
    public void testAiCloser_aiRunsToLooseBall() {
        GameEngine engine = createStandardGame();
        Titan ai = engine.players[2]; // HOME field titan
        ai.team = TeamAffiliation.HOME;
        ai.possession = 0;
        ai.fuel = 50.0;
        ai.speed = 3.5;
        ai.X = 940;
        ai.Y = 500;

        Titan teammate = engine.players[3]; // HOME field titan
        teammate.team = TeamAffiliation.HOME;
        teammate.possession = 0;
        teammate.fuel = 50.0;
        teammate.speed = 3.5;
        teammate.X = 700;
        teammate.Y = 500;

        for (Titan t : engine.players) {
            if (t != null && t != ai && t != teammate) {
                t.X = 1800;
                t.Y = 1000;
            }
        }

        engine.ball.X = 1000;
        engine.ball.Y = 500;
        double ballCX = engine.ball.X + engine.ball.width / 2.0;
        double ballCY = engine.ball.Y + engine.ball.height / 2.0;

        engine.evaluateAiDecision(ai);

        // AI is closest and should pursue the ball directly
        Assert.assertEquals("AI should target loose ball center X", ballCX, ai.aiTargetX, 1.0);
        Assert.assertEquals("AI should target loose ball center Y", ballCY, ai.aiTargetY, 1.0);
    }

    @Test
    public void testMovespeedDifferenceAdjustment_teammateFurtherButFaster() {
        GameEngine engine = createStandardGame();
        Titan ai = engine.players[2]; // HOME field titan, slow Golem (no fuel)
        ai.team = TeamAffiliation.HOME;
        ai.possession = 0;
        ai.speed = 3.2;
        ai.fuel = 0.0;
        ai.boostFactor = 1.0;
        // Place AI at dist 200 from ball -> ETA = 200 / 3.2 = 62.5 ticks
        ai.X = 800 - ai.width / 2.0;
        ai.Y = 500 - ai.height / 2.0;

        Titan teammate = engine.players[3]; // HOME field titan, fast/boosted
        teammate.team = TeamAffiliation.HOME;
        teammate.possession = 0;
        teammate.speed = 3.6;
        teammate.fuel = 50.0;
        teammate.boostFactor = 1.55; // chase speed ~ 3.6 * 1.55 = 5.58
        // Place teammate at dist 220 from ball -> ETA = 220 / 5.58 = 39.4 ticks
        // Teammate is FURTHER in distance (220 > 200), but FASTER in arrival time (39.4 < 62.5)!
        teammate.X = 780 - teammate.width / 2.0;
        teammate.Y = 500 - teammate.height / 2.0;

        for (Titan t : engine.players) {
            if (t != null && t != ai && t != teammate) {
                t.X = 1800;
                t.Y = 1000;
            }
        }

        engine.ball.X = 1000;
        engine.ball.Y = 500;
        double ballCX = engine.ball.X + engine.ball.width / 2.0;
        double ballCY = engine.ball.Y + engine.ball.height / 2.0;

        engine.evaluateAiDecision(ai);

        // Teammate arrives first when adjusting for movespeed differences!
        Assert.assertFalse("AI should defer to faster teammate even if teammate is further in Euclidean distance",
                Math.abs(ai.aiTargetX - ballCX) < 1.0 && Math.abs(ai.aiTargetY - ballCY) < 1.0);
        Assert.assertTrue("AI should get into forward attacking posture", ai.aiTargetX > ballCX);
    }

    @Test
    public void testMovespeedDifferenceAdjustment_aiFurtherButFaster() {
        GameEngine engine = createStandardGame();
        Titan ai = engine.players[2]; // HOME field titan, fast/boosted
        ai.team = TeamAffiliation.HOME;
        ai.possession = 0;
        ai.speed = 3.6;
        ai.fuel = 50.0;
        ai.boostFactor = 1.55; // chase speed ~ 5.58
        // Distance 220 from ball -> ETA = 39.4 ticks
        ai.X = 780 - ai.width / 2.0;
        ai.Y = 500 - ai.height / 2.0;

        Titan teammate = engine.players[3]; // HOME field titan, slow Golem (no fuel)
        teammate.team = TeamAffiliation.HOME;
        teammate.possession = 0;
        teammate.speed = 3.2;
        teammate.fuel = 0.0;
        teammate.boostFactor = 1.0;
        // Distance 200 from ball -> ETA = 62.5 ticks
        teammate.X = 800 - teammate.width / 2.0;
        teammate.Y = 500 - teammate.height / 2.0;

        for (Titan t : engine.players) {
            if (t != null && t != ai && t != teammate) {
                t.X = 1800;
                t.Y = 1000;
            }
        }

        engine.ball.X = 1000;
        engine.ball.Y = 500;
        double ballCX = engine.ball.X + engine.ball.width / 2.0;
        double ballCY = engine.ball.Y + engine.ball.height / 2.0;

        engine.evaluateAiDecision(ai);

        // AI arrives first when adjusting for movespeed -> AI targets the loose ball
        Assert.assertEquals("AI should target loose ball center X", ballCX, ai.aiTargetX, 1.0);
        Assert.assertEquals("AI should target loose ball center Y", ballCY, ai.aiTargetY, 1.0);
    }

    @Test
    public void testTeammateIncapacitatedOrDead_aiRetrievesBall() {
        GameEngine engine = createStandardGame();
        Titan ai = engine.players[2];
        ai.team = TeamAffiliation.HOME;
        ai.possession = 0;
        ai.fuel = 50.0;
        ai.speed = 3.5;
        ai.X = 700;
        ai.Y = 500;

        Titan teammate = engine.players[3];
        teammate.team = TeamAffiliation.HOME;
        teammate.possession = 0;
        teammate.X = 950; // closer in distance
        teammate.Y = 500;

        // Teammate is dead
        engine.effectPool.addUniqueEffect(new EmptyEffect(5000, teammate, EffectId.DEAD), engine);

        for (Titan t : engine.players) {
            if (t != null && t != ai && t != teammate) {
                t.X = 1800;
                t.Y = 1000;
            }
        }

        engine.ball.X = 1000;
        engine.ball.Y = 500;
        double ballCX = engine.ball.X + engine.ball.width / 2.0;

        engine.evaluateAiDecision(ai);

        // AI must not defer to a dead teammate
        Assert.assertEquals("AI should target loose ball when closer teammate is dead", ballCX, ai.aiTargetX, 1.0);
    }

    @Test
    public void testGoalieOutsideCrease_ignoredForLooseBall() {
        GameEngine engine = createStandardGame();
        Titan ai = engine.players[2]; // Field player
        ai.team = TeamAffiliation.HOME;
        ai.possession = 0;
        ai.fuel = 50.0;
        ai.speed = 3.5;
        ai.X = 800;
        ai.Y = 500;

        Titan goalie = engine.players[0]; // Goalie
        goalie.team = TeamAffiliation.HOME;
        goalie.possession = 0;
        goalie.setType(TitanType.GOALIE);
        goalie.X = 300;
        goalie.Y = 500;

        // Ball is at midfield (outside goalie crease)
        engine.ball.X = 1000;
        engine.ball.Y = 500;
        double ballCX = engine.ball.X + engine.ball.width / 2.0;

        for (Titan t : engine.players) {
            if (t != null && t != ai && t != goalie) {
                t.X = 1800;
                t.Y = 1000;
            }
        }

        engine.evaluateAiDecision(ai);

        // Goalie does not leave crease, AI field player must retrieve ball
        Assert.assertEquals("Field AI should retrieve loose ball outside goalie box", ballCX, ai.aiTargetX, 1.0);
    }

    @Test
    public void testForwardAttackingPostureFlankSwitchWhenBlocked() {
        GameEngine engine = createStandardGame();
        Titan ai = engine.players[2];
        ai.team = TeamAffiliation.HOME;
        ai.possession = 0;
        ai.X = 700;
        ai.Y = 400; // Above ball -> default preferred vertical offset is upwards (-210)

        Titan teammate = engine.players[3];
        teammate.team = TeamAffiliation.HOME;
        teammate.possession = 0;
        teammate.X = 950;
        teammate.Y = 500;

        engine.ball.X = 1000;
        engine.ball.Y = 500;
        double ballCX = engine.ball.X + engine.ball.width / 2.0;
        double ballCY = engine.ball.Y + engine.ball.height / 2.0;

        // Place an enemy right on the upper passing lane (accounting for sprite center)
        Titan enemyBlocker = engine.players[5];
        enemyBlocker.team = TeamAffiliation.AWAY;
        enemyBlocker.X = ballCX + 105 - enemyBlocker.width / 2.0;
        enemyBlocker.Y = ballCY - 105 - enemyBlocker.height / 2.0;

        for (Titan t : engine.players) {
            if (t != null && t != ai && t != teammate && t != enemyBlocker) {
                t.X = 1800;
                t.Y = 1000;
            }
        }

        engine.evaluateAiDecision(ai);

        // Since upper lane is blocked by enemy, AI should switch to lower flank (+Y)
        Assert.assertTrue("AI should switch to unblocked flank (Y > ballCY)", ai.aiTargetY > ballCY);
    }
}
