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

public class CallForBallTest {

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
    public void testIsEnemyBetweenDetection() {
        GameEngine engine = createStandardGame();
        Titan bot = engine.players[1]; // HOME team
        bot.team = TeamAffiliation.HOME;
        bot.X = 500;
        bot.Y = 500;

        Titan human = engine.players[2]; // HOME teammate
        human.team = TeamAffiliation.HOME;
        human.X = 900;
        human.Y = 500;

        Titan enemy = engine.players[5]; // AWAY enemy
        enemy.team = TeamAffiliation.AWAY;

        // 1. Enemy directly between bot and human
        enemy.X = 700;
        enemy.Y = 500;
        Assert.assertTrue("Enemy standing directly in lane between teammates must be detected",
                engine.isEnemyBetween(bot, human));

        // 2. Enemy far off to the side (different lane)
        enemy.Y = 800;
        Assert.assertFalse("Enemy in another lane should not be between teammates",
                engine.isEnemyBetween(bot, human));

        // 3. Enemy behind the thrower
        enemy.X = 300;
        enemy.Y = 500;
        Assert.assertFalse("Enemy behind thrower should not be between teammates",
                engine.isEnemyBetween(bot, human));

        // 4. Enemy behind the receiver
        enemy.X = 1100;
        enemy.Y = 500;
        Assert.assertFalse("Enemy behind receiver should not be between teammates",
                engine.isEnemyBetween(bot, human));
    }

    @Test
    public void testCallForBallGroundPassWhenClear() {
        GameEngine engine = createStandardGame();
        // Client 1 controls player 1 (Human caller)
        PlayerDivider humanClient = engine.clients.get(0);
        humanClient.isAi = false;
        humanClient.selection = 1;

        // Player 2 is AI friendly titan with the ball
        for (int i = 1; i < engine.clients.size(); i++) {
            engine.clients.get(i).isAi = true;
            engine.clients.get(i).selection = 99; // Not selecting player 2
        }

        Titan humanCaller = engine.players[0]; // player 1
        humanCaller.team = TeamAffiliation.HOME;
        humanCaller.possession = 0;
        humanCaller.X = 800;
        humanCaller.Y = 500;

        Titan aiBot = engine.players[1]; // player 2
        aiBot.team = TeamAffiliation.HOME;
        aiBot.possession = 1;
        aiBot.actionState = Titan.TitanState.IDLE;
        aiBot.X = 500;
        aiBot.Y = 500;
        engine.ball.X = aiBot.X + 35 - engine.ball.centerDist;
        engine.ball.Y = aiBot.Y + 35 - engine.ball.centerDist;

        // Put all enemies far away
        for (Titan t : engine.players) {
            if (t != null && t.team == TeamAffiliation.AWAY) {
                t.X = 1800;
                t.Y = 1000;
            }
        }

        // Caller presses Call for Ball (E)
        ClientPacket packet = new ClientPacket();
        packet.callForBall = true;
        engine.processClientPacket(humanClient, packet);

        // AI bot should have immediately executed a ground pass (SHOOT state)
        Assert.assertEquals("AI bot should immediately enter SHOOT state for ground pass",
                Titan.TitanState.SHOOT, aiBot.actionState);
        Assert.assertTrue("Ball kick power along X should be directed towards caller (+X)",
                engine.xKickPow > 0);
    }

    @Test
    public void testCallForBallLobWhenEnemyBetween() {
        GameEngine engine = createStandardGame();
        PlayerDivider humanClient = engine.clients.get(0);
        humanClient.isAi = false;
        humanClient.selection = 1;

        for (int i = 1; i < engine.clients.size(); i++) {
            engine.clients.get(i).isAi = true;
            engine.clients.get(i).selection = 99;
        }

        Titan humanCaller = engine.players[0];
        humanCaller.team = TeamAffiliation.HOME;
        humanCaller.possession = 0;
        humanCaller.X = 900;
        humanCaller.Y = 500;

        Titan aiBot = engine.players[1];
        aiBot.team = TeamAffiliation.HOME;
        aiBot.possession = 1;
        aiBot.actionState = Titan.TitanState.IDLE;
        aiBot.X = 500;
        aiBot.Y = 500;
        engine.ball.X = aiBot.X + 35 - engine.ball.centerDist;
        engine.ball.Y = aiBot.Y + 35 - engine.ball.centerDist;

        // Place enemy directly between aiBot and humanCaller
        Titan enemy = engine.players[5];
        enemy.team = TeamAffiliation.AWAY;
        enemy.X = 700;
        enemy.Y = 500;

        // Caller presses Call for Ball
        ClientPacket packet = new ClientPacket();
        packet.callForBall = true;
        engine.processClientPacket(humanClient, packet);

        // AI bot should have immediately executed a LOB pass over the enemy
        Assert.assertEquals("AI bot should immediately enter LOB state when enemy is in passing lane",
                Titan.TitanState.LOB, aiBot.actionState);
        Assert.assertTrue("Ball kick power along X should be directed towards caller (+X)",
                engine.xKickPow > 0);
    }
}
