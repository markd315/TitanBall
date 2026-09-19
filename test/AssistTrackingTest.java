import gameserver.engine.CollisionMath;
import gameserver.engine.GameEngine;
import gameserver.engine.GameOptions;
import gameserver.engine.StatEngine;
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

public class AssistTrackingTest {

    private GameEngine createStandardGame() {
        GameEngine engine = new GameEngine();
        List<PlayerDivider> clients = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            PlayerDivider pd = new PlayerDivider(Arrays.asList(i));
            pd.id = i;
            pd.email = "player" + i + "@test.com";
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
    public void testPassAndCenterGoalAssist() throws Exception {
        GameEngine engine = createStandardGame();

        Titan passer = engine.players[2]; // Home Outfield 1
        passer.setType(TitanType.WARRIOR);
        passer.team = TeamAffiliation.HOME;
        passer.possession = 1;
        engine.lastPossessed = passer.id;
        engine.home.hasBall = true;

        Titan scorer = engine.players[3]; // Home Outfield 2
        scorer.setType(TitanType.MARKSMAN);
        scorer.team = TeamAffiliation.HOME;
        scorer.possession = 0;

        // Position scorer near passer
        passer.X = 1000;
        passer.Y = 500;
        scorer.X = 1100;
        scorer.Y = 500;

        // Center ball on passer
        engine.ball.X = (int) Math.round(passer.getX() + passer.width / 2.0 - engine.ball.centerDist);
        engine.ball.Y = (int) Math.round(passer.getY() + passer.height / 2.0 - engine.ball.centerDist);

        // Passer shoots directly at scorer (a pass)
        engine.serverMouseRoutine(passer, (int) scorer.X + 35, (int) scorer.Y + 35, 1, 0, 0);
        Assert.assertEquals("Passer should be in SHOOT state", Titan.TitanState.SHOOT, passer.actionState);

        System.out.println("KickPow: " + engine.xKickPow + ", " + engine.yKickPow);
        // Advance ticks of shooting until ball reaches scorer
        for (int frame = 0; frame < 20 && scorer.possession == 0; frame++) {
            System.out.println("Frame " + frame + ": passerState=" + passer.actionState + ", frame=" + passer.actionFrame + ", ball=(" + engine.ball.X + "," + engine.ball.Y + "), scorerPoss=" + scorer.possession);
            if (passer.actionState == Titan.TitanState.SHOOT) {
                engine.shootingBall(passer);
            }
        }
        for (int i = 0; i < engine.players.length; i++) {
            Titan p = engine.players[i];
            if (p.possession == 1) {
                System.out.println("Player index " + i + " has possession! ID=" + p.id + ", passerID=" + passer.id + ", scorerID=" + scorer.id);
            }
        }
        System.out.println("After loop: scorerPoss=" + scorer.possession + ", lastHomePasser=" + engine.lastHomePasser);

        // Verify scorer now has possession and passer was recorded
        Assert.assertEquals("Scorer should have possession", 1, scorer.possession);
        Assert.assertEquals("lastHomePasser should be passer", passer.id, engine.lastHomePasser);

        // Now move scorer to goal and score
        scorer.X = engine.awayHiGoal.x + engine.awayHiGoal.w / 2.0 - 35;
        scorer.Y = engine.awayHiGoal.y + engine.awayHiGoal.h / 2.0 - 35;
        engine.ball.X = engine.awayHiGoal.x + engine.awayHiGoal.w / 2.0 - engine.ball.centerDist;
        engine.ball.Y = engine.awayHiGoal.y + engine.awayHiGoal.h / 2.0 - engine.ball.centerDist;

        engine.detectGoals();

        // Check stats
        PlayerDivider passerClient = engine.clientFromTitan(passer);
        PlayerDivider scorerClient = engine.clientFromTitan(scorer);

        Double goals = engine.stats.getStat(StatEngine.StatEnum.GOALS).get(scorerClient.email);
        Double assists = engine.stats.getStat(StatEngine.StatEnum.GOALASSISTS).get(passerClient.email);

        System.out.println("Goals for scorer: " + goals);
        System.out.println("Assists for passer: " + assists);

        Assert.assertNotNull("Scorer should have goals", goals);
        Assert.assertEquals(1.0, goals.doubleValue(), 0.001);
        Assert.assertNotNull("Passer should have goal assist", assists);
        Assert.assertEquals(1.0, assists.doubleValue(), 0.001);
    }

    @Test
    public void testPassAndSideGoalAssist() throws Exception {
        GameEngine engine = createStandardGame();

        Titan passer = engine.players[2]; // Home Outfield 1
        passer.setType(TitanType.WARRIOR);
        passer.team = TeamAffiliation.HOME;
        passer.possession = 1;
        engine.lastPossessed = passer.id;
        engine.home.hasBall = true;

        Titan scorer = engine.players[3]; // Home Outfield 2
        scorer.setType(TitanType.MARKSMAN);
        scorer.team = TeamAffiliation.HOME;
        scorer.possession = 0;

        // Position scorer near passer
        passer.X = 1000;
        passer.Y = 500;
        scorer.X = 1100;
        scorer.Y = 500;

        // Center ball on passer
        engine.ball.X = (int) Math.round(passer.getX() + passer.width / 2.0 - engine.ball.centerDist);
        engine.ball.Y = (int) Math.round(passer.getY() + passer.height / 2.0 - engine.ball.centerDist);

        // Passer shoots directly at scorer (a pass)
        engine.serverMouseRoutine(passer, (int) scorer.X + 35, (int) scorer.Y + 35, 1, 0, 0);

        for (int frame = 0; frame < 20 && scorer.possession == 0; frame++) {
            if (passer.actionState == Titan.TitanState.SHOOT) {
                engine.shootingBall(passer);
            }
        }

        Assert.assertEquals("Scorer should have possession", 1, scorer.possession);
        Assert.assertEquals("lastHomePasser should be passer", passer.id, engine.lastHomePasser);

        // Position scorer on Away low goal (side goal)
        scorer.X = engine.lowGoals[2].x + engine.lowGoals[2].w / 2.0 - 35;
        scorer.Y = engine.lowGoals[2].y + engine.lowGoals[2].h / 2.0 - 35;
        engine.ball.X = engine.lowGoals[2].x + engine.lowGoals[2].w / 2.0 - engine.ball.centerDist;
        engine.ball.Y = engine.lowGoals[2].y + engine.lowGoals[2].h / 2.0 - engine.ball.centerDist;

        engine.detectGoals();

        PlayerDivider passerClient = engine.clientFromTitan(passer);
        PlayerDivider scorerClient = engine.clientFromTitan(scorer);

        Double sidegoals = engine.stats.getStat(StatEngine.StatEnum.SIDEGOALS).get(scorerClient.email);
        Double sideassists = engine.stats.getStat(StatEngine.StatEnum.SIDEGOALASSISTS).get(passerClient.email);

        Assert.assertNotNull("Scorer should have side goals", sidegoals);
        Assert.assertEquals(1.0, sidegoals.doubleValue(), 0.001);
        Assert.assertNotNull("Passer should have side goal assist", sideassists);
        Assert.assertEquals(1.0, sideassists.doubleValue(), 0.001);
    }

    @Test
    public void testEnemyTouchNullifiesAssist() {
        GameEngine engine = createStandardGame();

        Titan passer = engine.players[2]; // Home Outfield 1
        passer.setType(TitanType.WARRIOR);
        passer.team = TeamAffiliation.HOME;

        Titan enemy = engine.players[6]; // Away Outfield 1
        enemy.setType(TitanType.GOLEM);
        enemy.team = TeamAffiliation.AWAY;

        Titan scorer = engine.players[3]; // Home Outfield 2
        scorer.setType(TitanType.MARKSMAN);
        scorer.team = TeamAffiliation.HOME;

        // 1. Passer touches ball
        engine.registerBallTouch(passer);
        // 2. Enemy intercepts/touches ball
        engine.registerBallTouch(enemy);
        // 3. Scorer recovers ball
        engine.registerBallTouch(scorer);

        // Scorer scores in center goal
        scorer.X = 1786;
        scorer.Y = 583;
        engine.ball.X = scorer.X + 35 - engine.ball.centerDist;
        engine.ball.Y = scorer.Y + 35 - engine.ball.centerDist;

        engine.detectGoals();

        PlayerDivider passerClient = engine.clientFromTitan(passer);
        Double assists = engine.stats.getStat(StatEngine.StatEnum.GOALASSISTS).get(passerClient.email);

        Assert.assertNull("Passer should NOT receive assist after enemy touches ball", assists);
    }

    @Test
    public void testSoloGoalHasNoAssist() {
        GameEngine engine = createStandardGame();

        Titan scorer = engine.players[3]; // Home Outfield 2
        scorer.setType(TitanType.MARKSMAN);
        scorer.team = TeamAffiliation.HOME;

        // Scorer picks up ball alone
        engine.registerBallTouch(scorer);

        // Scorer scores in center goal
        scorer.X = 1786;
        scorer.Y = 583;
        engine.ball.X = scorer.X + 35 - engine.ball.centerDist;
        engine.ball.Y = scorer.Y + 35 - engine.ball.centerDist;

        engine.detectGoals();

        PlayerDivider scorerClient = engine.clientFromTitan(scorer);
        Double goals = engine.stats.getStat(StatEngine.StatEnum.GOALS).get(scorerClient.email);
        Assert.assertNotNull("Scorer should have goal", goals);
        Assert.assertEquals(1.0, goals.doubleValue(), 0.001);

        for (PlayerDivider pd : engine.clients) {
            Double ast = engine.stats.getStat(StatEngine.StatEnum.GOALASSISTS).get(pd.email);
            Assert.assertNull("No player should have an assist on solo goal", ast);
        }
    }

    @Test
    public void testBotScorerHumanAssists() {
        GameEngine engine = new GameEngine();
        // Only 1 human player in game (e.g. index 3, Home Outfield 1)
        List<PlayerDivider> clients = new ArrayList<>();
        PlayerDivider humanClient = new PlayerDivider(Arrays.asList(3));
        humanClient.id = 1;
        humanClient.email = "human@test.com";
        clients.add(humanClient);
        engine.clients = clients;
        engine.options = new GameOptions();
        engine.options.playToIndex = 0;
        engine.colliders = new ArrayList<>();
        engine.initializeServer();
        engine.phase = GamePhase.INGAME;

        Titan humanPasser = engine.players[2]; // Home Outfield 1 (Human)
        humanPasser.setType(TitanType.WARRIOR);
        humanPasser.team = TeamAffiliation.HOME;

        Titan botScorer = engine.players[3]; // Home Outfield 2 (Bot - no client in engine.clients)
        botScorer.setType(TitanType.MARKSMAN);
        botScorer.team = TeamAffiliation.HOME;

        // Human touches ball, then bot touches ball
        engine.registerBallTouch(humanPasser);
        engine.registerBallTouch(botScorer);

        // Bot scores center goal
        botScorer.X = engine.awayHiGoal.x + engine.awayHiGoal.w / 2.0 - 35;
        botScorer.Y = engine.awayHiGoal.y + engine.awayHiGoal.h / 2.0 - 35;
        engine.ball.X = engine.awayHiGoal.x + engine.awayHiGoal.w / 2.0 - engine.ball.centerDist;
        engine.ball.Y = engine.awayHiGoal.y + engine.awayHiGoal.h / 2.0 - engine.ball.centerDist;

        engine.detectGoals();

        // Verify human passer received GOALASSISTS
        Double assists = engine.stats.getStat(StatEngine.StatEnum.GOALASSISTS).get(humanClient.email);
        Assert.assertNotNull("Human should receive assist even when scorer is a bot", assists);
        Assert.assertEquals(1.0, assists.doubleValue(), 0.001);
    }
}
