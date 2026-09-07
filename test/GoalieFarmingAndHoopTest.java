import gameserver.Const;
import gameserver.effects.EffectId;
import gameserver.engine.CollisionMath;
import gameserver.engine.GameEngine;
import gameserver.engine.GameOptions;
import gameserver.engine.GoalHoop;
import gameserver.engine.StatEngine;
import gameserver.engine.TeamAffiliation;
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

public class GoalieFarmingAndHoopTest {

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
    public void testGoalieLastHitPriorityOverFullHealth() {
        GameEngine engine = createStandardGame();
        Titan goalie = engine.players[0]; // Home Goalie
        goalie.team = TeamAffiliation.HOME;
        goalie.setType(TitanType.GOALIE);

        // Friendly carrier possesses ball to trigger offense mode
        Titan carrier = engine.players[1];
        carrier.team = TeamAffiliation.HOME;
        carrier.possession = 1;

        // Create two enemy minions in range:
        // Minion A has 3.0 HP (guaranteed kill threshold <= 5.0)
        LaneMinion lowHpMinion = new LaneMinion(goalie.X + 150, goalie.Y, TeamAffiliation.AWAY, 1);
        lowHpMinion.health = 3.0;

        // Minion B has 22.5 HP (full health)
        LaneMinion fullHpMinion = new LaneMinion(goalie.X + 100, goalie.Y, TeamAffiliation.AWAY, 1);
        fullHpMinion.health = 22.5;

        engine.entityPool.add(lowHpMinion);
        engine.entityPool.add(fullHpMinion);

        // Call goalie minion farming
        engine.tickAiGoalieMinionFarming(goalie);

        // Verify lowHpMinion was chosen and killed (health reached 0.0)
        Assert.assertEquals("Low HP minion should be prioritized for last hit and killed", 0.0, lowHpMinion.getHealth(), 0.001);
        Assert.assertEquals("Full HP minion should be left untouched", 22.5, fullHpMinion.getHealth(), 0.001);
    }

    @Test
    public void testGoalieCreaseConstraintAcrossMidfield() {
        GameEngine engine = createStandardGame();
        Titan goalie = engine.players[0]; // Home Goalie
        goalie.team = TeamAffiliation.HOME;
        goalie.setType(TitanType.GOALIE);

        Titan carrier = engine.players[1];
        carrier.team = TeamAffiliation.HOME;
        carrier.possession = 1;

        // Place enemy minions in Top lane (lane 0, Y ~ 407.5)
        LaneMinion topMinion = new LaneMinion(500, 407.5, TeamAffiliation.AWAY, 0);
        engine.entityPool.add(topMinion);

        // 1. Ball on friendly side of midfield (X = 500 < 1042)
        engine.ball.X = 500;
        engine.evaluateGoalieDecision(goalie);

        // Goalie must hold primary crease center position
        GoalHoop homeHoop = engine.homeHiGoal;
        double hoopCY = homeHoop.y + homeHoop.h / 2.0;
        Assert.assertEquals("Goalie must hold primary crease line when ball is not across midfield",
                hoopCY + 10.0, goalie.aiTargetY, 5.0);

        // 2. Ball crosses midfield (X = 1500 > 1042)
        engine.ball.X = 1500;
        engine.evaluateGoalieDecision(goalie);

        // Goalie shifts towards Top lane to farm, but must NEVER wander beyond 60px from crease line (583 - 60 = 523)
        double creaseTop = homeHoop.y - 60.0;
        Assert.assertTrue("Goalie aiTargetY must not wander beyond 60px above crease top (523px)",
                goalie.aiTargetY >= creaseTop - 0.001);
        Assert.assertTrue("Goalie aiTargetY should have shifted towards top lane within crease",
                goalie.aiTargetY < hoopCY);
    }

    @Test
    public void testHoopBounceExtraKick() {
        GameEngine engine = createStandardGame();
        GoalHoop hoop = engine.lowGoals[0];
        CollisionMath.EllipseData ell = hoop.ellipseData();

        // Place ball on the hoop center
        engine.ball.X = ell.centerX() - engine.ball.centerDist;
        engine.ball.Y = ell.centerY() - engine.ball.centerDist;

        engine.minorHoopBounce();

        // Check distance from hoop center to ball center
        double ballCX = engine.ball.X + engine.ball.centerDist;
        double ballCY = engine.ball.Y + engine.ball.centerDist;
        double distFromCenter = Math.hypot(ballCX - ell.centerX(), ballCY - ell.centerY());

        // Radius of ellipse is ell.radiusX() and ell.radiusY(). Extra kick is 30px.
        // Distance from center must exceed ellipse radius + extra kick
        double minExpectedDist = Math.min(ell.radiusX(), ell.radiusY()) + 30.0;
        Assert.assertTrue("Ball should be kicked out at least radius + 30px away from hoop center: dist=" + distFromCenter + ", minExpected=" + minExpectedDist,
                distFromCenter >= minExpectedDist - 1.0);
    }

    @Test
    public void testSidegoalCooldownIncreased() {
        GameEngine engine = createStandardGame();
        GoalHoop hoop = engine.lowGoals[0];

        // Trigger sidegoal
        hoop.trigger(engine.c.HOOP_SIDEGOAL_CD_MS);

        Assert.assertTrue("Hoop should be on cooldown", hoop.onCooldown);
        Assert.assertFalse("Hoop should not be ready immediately", hoop.checkReady());
        Assert.assertEquals("Sidegoal cooldown should be 1800ms", 1800, engine.c.HOOP_SIDEGOAL_CD_MS);
    }

    @Test
    public void testGoalieDefendsNearestEdgeWhenAttackedFromBehind() {
        GameEngine engine = createStandardGame();
        Titan goalie = engine.players[0]; // Home Goalie
        goalie.team = TeamAffiliation.HOME;
        goalie.setType(TitanType.GOALIE);

        GoalHoop homeHoop = engine.homeHiGoal;
        double hoopCX = homeHoop.x + homeHoop.w / 2.0;

        // 1. Ball in FRONT of Home hoop (ballCX > hoopCX)
        engine.ball.X = hoopCX + 150;
        engine.ball.Y = homeHoop.y + homeHoop.h / 2.0;
        engine.evaluateGoalieDecision(goalie);
        double frontTargetX = goalie.aiTargetX;

        // 2. Ball BEHIND Home hoop (ballCX < hoopCX, e.g. X = 100)
        engine.ball.X = 100;
        engine.ball.Y = homeHoop.y + homeHoop.h / 2.0;
        engine.evaluateGoalieDecision(goalie);
        double rearTargetX = goalie.aiTargetX;

        // Goalie must defend the edge nearest the ball:
        // When behind the hoop, the goalie's targetX must be further to the left (rear) than when in front
        Assert.assertTrue("Goalie targetX when ball is behind hoop (" + rearTargetX + ") must be rear of targetX when in front (" + frontTargetX + ")",
                rearTargetX < frontTargetX);
    }

    @Test
    public void testGoalieRemainsAnchoredToCenterGoalOnOrthogonalPass() {
        GameEngine engine = createStandardGame();
        Titan goalie = engine.players[0]; // Home Goalie
        goalie.team = TeamAffiliation.HOME;
        goalie.setType(TitanType.GOALIE);

        GoalHoop homeHoop = engine.homeHiGoal;
        double hoopCY = homeHoop.y + homeHoop.h / 2.0;
        double hoopTop = homeHoop.y;
        double hoopBottom = homeHoop.y + homeHoop.h;

        // Opponent takes ball
        Titan enemyCarrier = engine.players[1];
        enemyCarrier.team = TeamAffiliation.AWAY;

        // Ball centered at X = 600, Y = hoopCY. Moving upward towards top wing (orthogonal pass)
        engine.ball.X = 600;
        engine.ball.Y = hoopCY - engine.ball.height / 2.0;
        engine.xKickPow = 0.0;
        engine.yKickPow = 0.5;
        engine.evaluateGoalieDecision(goalie);

        // Goalie must remain anchored strictly to the center goal hoop and NOT slide away into the wings
        Assert.assertTrue("Goalie targetY (" + goalie.aiTargetY + ") must remain within center goal hoop vertical bounds (" + hoopTop + " to " + hoopBottom + ")",
                goalie.aiTargetY >= hoopTop - 10.0 && goalie.aiTargetY <= hoopBottom + 20.0);
    }

    @Test
    public void testAiGoalieBuildOrderForksGoldAndMana() {
        GameEngine engine = createStandardGame();
        Titan goalie = engine.players[0]; // Home Goalie
        goalie.team = TeamAffiliation.HOME;
        goalie.setType(TitanType.GOALIE);

        // Find the "cult-fort" build preset (containing manafrenzy and homeward)
        List<String> cultFortOrder = GameEngine.GOALIE_BUILD_PRESETS.stream()
                .filter(order -> order.contains("cultivation.t4.manafrenzy") && order.contains("fortress.t1.homeward"))
                .findFirst()
                .orElse(GameEngine.GOALIE_BUILD_PRESETS.get(0));
        engine.setAiGoalieBuildOrder(goalie, cultFortOrder);

        // Verify tracks were forked properly
        Assert.assertEquals("Gold track should have 11 nodes", 11, goalie.aiGoalieGoldOrder.size());
        Assert.assertEquals("Mana track should have 1 node", 1, goalie.aiGoalieManaOrder.size());
        Assert.assertEquals("Mana node should be manafrenzy", "cultivation.t4.manafrenzy", goalie.aiGoalieManaOrder.get(0));
        Assert.assertEquals("First gold node should be manawell", "cultivation.t1.manawell", goalie.aiGoalieGoldOrder.get(0));

        // Grant 275 gold to buy initial Cultivation gold upgrades
        engine.homeGoalieCurrency = 275.0;
        engine.homeGoalieMana = 0.0;

        for (int i = 0; i < 4; i++) {
            engine.tickAiGoalieBuildOrder(goalie);
        }

        Assert.assertEquals("Gold index should be 4 after basic cultivation", 4, goalie.aiGoalieGoldIndex);
        Assert.assertEquals("Mana index should still be 0", 0, goalie.aiGoalieManaIndex);

        // Now next gold node is fortress.t1.homeward (50g) and next mana node is manafrenzy (275 mana)
        // With 0 mana and 100 gold, goalie must NOT stall — it should purchase fortress.t1.homeward!
        engine.homeGoalieCurrency = 100.0;
        engine.homeGoalieMana = 0.0;
        engine.tickAiGoalieBuildOrder(goalie);

        Assert.assertTrue("fortress.t1.homeward must be purchased with gold without being blocked by mana gate",
                engine.homeGoaliePurchasedUpgrades.contains("fortress.t1.homeward"));
        Assert.assertEquals("Gold index should advance to 5", 5, goalie.aiGoalieGoldIndex);
        Assert.assertEquals("Mana index should still be 0 waiting for mana", 0, goalie.aiGoalieManaIndex);

        // Once mana is accumulated, manafrenzy should be purchased
        engine.homeGoalieMana = 300.0;
        engine.tickAiGoalieBuildOrder(goalie);

        Assert.assertTrue("cultivation.t4.manafrenzy should be purchased once mana is available",
                engine.homeGoaliePurchasedUpgrades.contains("cultivation.t4.manafrenzy"));
        Assert.assertEquals("Mana index should advance to 1", 1, goalie.aiGoalieManaIndex);
    }

    @Test
    public void testAllTwoTreeTechCombinationsRepresented() {
        // Assert that GOALIE_BUILD_PRESETS was loaded from files and has at least 10 presets
        Assert.assertTrue("GOALIE_BUILD_PRESETS should have at least 10 presets", GameEngine.GOALIE_BUILD_PRESETS.size() >= 10);

        // Required 2-tree combinations
        String[][] requiredPairs = {
            {"siege", "fortress"},
            {"siege", "empowerment"},
            {"siege", "cultivation"},
            {"fortress", "empowerment"},
            {"fortress", "cultivation"},
            {"cultivation", "empowerment"}
        };

        for (String[] pair : requiredPairs) {
            String treeA = pair[0];
            String treeB = pair[1];
            boolean found = false;
            for (List<String> preset : GameEngine.GOALIE_BUILD_PRESETS) {
                boolean hasA = preset.stream().anyMatch(k -> k.startsWith(treeA + "."));
                boolean hasB = preset.stream().anyMatch(k -> k.startsWith(treeB + "."));
                if (hasA && hasB) {
                    found = true;
                    break;
                }
            }
            Assert.assertTrue("Tech tree combination " + treeA + " + " + treeB + " must be represented in GOALIE_BUILD_PRESETS", found);
        }
    }
}

