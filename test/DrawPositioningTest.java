import gameserver.engine.GameEngine;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.Titan;
import networking.PlayerDivider;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DrawPositioningTest {

    private GameEngine createStandardGame() {
        GameEngine engine = new GameEngine();
        List<PlayerDivider> clients = new ArrayList<>();
        // 4v4 setup: 1 goalie + 3 field players per team (8 total players)
        for (int i = 1; i <= 8; i++) {
            PlayerDivider pd = new PlayerDivider(Arrays.asList(i));
            pd.id = i;
            clients.add(pd);
        }
        engine.clients = clients;
        engine.players = Arrays.copyOf(engine.players, 8);
        engine.initializeServer();
        return engine;
    }

    @Test
    public void testGameStartDrawPositioningExactEquality() {
        GameEngine engine = createStandardGame();

        // In 4v4 (3 field slots), slotIndex 1 is top forward:
        // HOME top forward is index 3, AWAY top forward is index 6
        Titan homeForward = engine.players[3];
        Titan awayForward = engine.players[6];

        double ballLeft = engine.ball.X;
        double ballRight = engine.ball.X + engine.ball.width;

        double homeDistToBall = ballLeft - (homeForward.X + homeForward.width);
        double awayDistToBall = awayForward.X - ballRight;

        Assert.assertEquals("On game start, distance to ball must be exactly equal for both forwards",
                homeDistToBall, awayDistToBall, 0.001);

        double homeCenterDist = (engine.ball.X + engine.ball.width / 2.0) - (homeForward.X + homeForward.width / 2.0);
        double awayCenterDist = (awayForward.X + awayForward.width / 2.0) - (engine.ball.X + engine.ball.width / 2.0);

        Assert.assertEquals("On game start, center distance to ball must be exactly equal for both forwards",
                homeCenterDist, awayCenterDist, 0.001);
    }

    @Test
    public void testBlueGoalResetWhiteForwardCloser() {
        GameEngine engine = createStandardGame();

        // Blue (HOME) scores
        engine.lastScoredTeam = TeamAffiliation.HOME;
        engine.resetPosSel();

        Titan homeForward = engine.players[3];
        Titan awayForward = engine.players[6];

        double ballLeft = engine.ball.X;
        double ballRight = engine.ball.X + engine.ball.width;

        double homeDistToBall = ballLeft - (homeForward.X + homeForward.width);
        double awayDistToBall = awayForward.X - ballRight;

        Assert.assertTrue("Off a blue goal, white forward must be closer to the ball than blue forward",
                awayDistToBall < homeDistToBall);
    }

    @Test
    public void testWhiteGoalResetBlueForwardCloser() {
        GameEngine engine = createStandardGame();

        // White (AWAY) scores
        engine.lastScoredTeam = TeamAffiliation.AWAY;
        engine.resetPosSel();

        Titan homeForward = engine.players[3];
        Titan awayForward = engine.players[6];

        double ballLeft = engine.ball.X;
        double ballRight = engine.ball.X + engine.ball.width;

        double homeDistToBall = ballLeft - (homeForward.X + homeForward.width);
        double awayDistToBall = awayForward.X - ballRight;

        Assert.assertTrue("Off a white goal, blue forward must be closer to the ball than white forward",
                homeDistToBall < awayDistToBall);
    }
}
