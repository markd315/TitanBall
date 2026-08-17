import authserver.matchmaking.Matchmaker;
import gameserver.gamemanager.ManagedGame;
import gameserver.engine.GameEngine;
import gameserver.engine.GameOptions;
import gameserver.entity.TitanType;
import networking.PlayerDivider;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;

public class MatchmakingTest {

    private Matchmaker setupMockMatchmaker() throws Exception {
        final Matchmaker mm = new Matchmaker();
        
        // Use Java's reflection and dynamic proxy to mock ApplicationContext and SpringContext without external mocking library dependencies.
        final authserver.SpringContext mockSpringCtx = (authserver.SpringContext) java.lang.reflect.Proxy.newProxyInstance(
            authserver.SpringContext.class.getClassLoader(),
            new Class<?>[] { authserver.SpringContext.class },
            new java.lang.reflect.InvocationHandler() {
                @Override
                public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
                    if (method.getName().equals("getMatchmaker")) {
                        return mm;
                    }
                    return null;
                }
            }
        );

        org.springframework.context.ApplicationContext mockAppCtx = (org.springframework.context.ApplicationContext) java.lang.reflect.Proxy.newProxyInstance(
            org.springframework.context.ApplicationContext.class.getClassLoader(),
            new Class<?>[] { org.springframework.context.ApplicationContext.class },
            new java.lang.reflect.InvocationHandler() {
                @Override
                public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
                    if (method.getName().equals("getBean") && args.length == 1 && args[0] == authserver.SpringContext.class) {
                        return mockSpringCtx;
                    }
                    return null;
                }
            }
        );
        
        java.lang.reflect.Field field = authserver.SpringContextBridge.class.getDeclaredField("applicationContext");
        field.setAccessible(true);
        field.set(null, mockAppCtx);
        
        return mm;
    }

    @Test
    public void testRule1ChosenClassPreservedInGameEngine() throws Exception {
        Matchmaker mm = setupMockMatchmaker();
        
        // 3v3 options
        GameOptions op = new GameOptions("/0/0/1/5/2/9999/10/12");
        
        // Player 1 chose MAGE, Player 2 chose RANGER, Player 3 chose GOALIE
        // Player 4, 5, 6 chose WARRIOR / GOALIE
        mm.playerClasses.put("p1@test.com", "MAGE");
        mm.playerClasses.put("p2@test.com", "RANGER");
        mm.playerClasses.put("p3@test.com", "GOALIE");
        mm.playerClasses.put("p4@test.com", "WARRIOR");
        mm.playerClasses.put("p5@test.com", "WARRIOR");
        mm.playerClasses.put("p6@test.com", "GOALIE");
        
        List<String> gameFor = Arrays.asList(
            "p1@test.com", "p2@test.com", "p3@test.com",
            "p4@test.com", "p5@test.com", "p6@test.com"
        );
        
        ManagedGame game = new ManagedGame("test-game-1", op, gameFor);
        
        // Construct the clients list to pass to GameEngine
        List<PlayerDivider> clients = new ArrayList<>();
        for (String email : gameFor) {
            int slot = game.preAssignedSlots.get(email);
            PlayerDivider pd = new PlayerDivider();
            pd.email = email;
            pd.setPossibleSelectionSafe(Collections.singletonList(slot));
            clients.add(pd);
        }
        
        GameEngine engine = new GameEngine("test-game-1", clients, op, game);
        
        // Verify class types are correctly initialized
        for (PlayerDivider pd : clients) {
            int slot = pd.possibleSelection.get(0);
            TitanType actualType = engine.players[slot - 1].getType();
            String chosen = mm.playerClasses.get(pd.email);
            
            if (slot == 1 || slot == 2) {
                Assert.assertEquals(TitanType.GOALIE, actualType);
            } else {
                if ("MAGE".equals(chosen)) {
                    Assert.assertEquals(TitanType.MAGE, actualType);
                } else if ("RANGER".equals(chosen)) {
                    Assert.assertEquals(TitanType.RANGER, actualType);
                } else if ("WARRIOR".equals(chosen)) {
                    Assert.assertEquals(TitanType.WARRIOR, actualType);
                }
            }
        }
    }

    @Test
    public void testRule2PartnersWithOneGoalieKeptTogether() throws Exception {
        Matchmaker mm = setupMockMatchmaker();
        GameOptions op = new GameOptions("/0/0/1/5/2/9999/10/12");
        
        // Partners: p1 and p2 mutually requested each other.
        // p1 chose GOALIE, p2 chose WARRIOR.
        // Goalie count in their partner component is exactly 1 (p1).
        // Therefore, p1 and p2 MUST be placed on the same team.
        mm.partnerPool.put("p1@test.com", new HashSet<>(Collections.singletonList("p2")));
        mm.partnerPool.put("p2@test.com", new HashSet<>(Collections.singletonList("p1")));
        
        mm.playerClasses.put("p1@test.com", "GOALIE");
        mm.playerClasses.put("p2@test.com", "WARRIOR");
        mm.playerClasses.put("p3@test.com", "MAGE");
        mm.playerClasses.put("p4@test.com", "WARRIOR");
        mm.playerClasses.put("p5@test.com", "RANGER");
        mm.playerClasses.put("p6@test.com", "GOALIE"); // Another goalie on the opponent side
        
        List<String> gameFor = Arrays.asList(
            "p1@test.com", "p2@test.com", "p3@test.com",
            "p4@test.com", "p5@test.com", "p6@test.com"
        );
        
        ManagedGame game = new ManagedGame("test-game-2", op, gameFor);
        
        int p1Slot = game.preAssignedSlots.get("p1@test.com");
        int p2Slot = game.preAssignedSlots.get("p2@test.com");
        
        boolean p1Home = (p1Slot == 1 || (p1Slot >= 3 && p1Slot <= 5));
        boolean p2Home = (p2Slot == 1 || (p2Slot >= 3 && p2Slot <= 5));
        
        Assert.assertEquals("p1 and p2 must be on the same team", p1Home, p2Home);
    }

    @Test
    public void testRule3ReassignedToGoalieIfNoGuardian() throws Exception {
        Matchmaker mm = setupMockMatchmaker();
        GameOptions op = new GameOptions("/0/0/1/5/2/9999/10/12");
        
        // No one in the lobby chose GOALIE.
        // We expect one player on Home team to be reassigned to goalie (slot 1)
        // and one player on Away team to be reassigned to goalie (slot 2).
        mm.playerClasses.put("p1@test.com", "WARRIOR");
        mm.playerClasses.put("p2@test.com", "WARRIOR");
        mm.playerClasses.put("p3@test.com", "WARRIOR");
        mm.playerClasses.put("p4@test.com", "MAGE");
        mm.playerClasses.put("p5@test.com", "RANGER");
        mm.playerClasses.put("p6@test.com", "MAGE");
        
        List<String> gameFor = Arrays.asList(
            "p1@test.com", "p2@test.com", "p3@test.com",
            "p4@test.com", "p5@test.com", "p6@test.com"
        );
        
        ManagedGame game = new ManagedGame("test-game-3", op, gameFor);
        
        // Assert goalie slots are claimed
        Assert.assertTrue(game.preAssignedSlots.containsValue(1));
        Assert.assertTrue(game.preAssignedSlots.containsValue(2));
        
        // Find which players were reassigned to goalie
        String homeGoalie = null;
        String awayGoalie = null;
        for (Map.Entry<String, Integer> entry : game.preAssignedSlots.entrySet()) {
            if (entry.getValue() == 1) {
                homeGoalie = entry.getKey();
            } else if (entry.getValue() == 2) {
                awayGoalie = entry.getKey();
            }
        }
        
        Assert.assertNotNull(homeGoalie);
        Assert.assertNotNull(awayGoalie);
        
        // The reassigned players were indeed originally non-goalies
        Assert.assertNotEquals("GOALIE", mm.playerClasses.get(homeGoalie));
        Assert.assertNotEquals("GOALIE", mm.playerClasses.get(awayGoalie));
    }

    @Test
    public void testMinimizeReassignments() throws Exception {
        Matchmaker mm = setupMockMatchmaker();
        GameOptions op = new GameOptions("/0/0/1/5/2/9999/10/12");
        
        // Two players chose GOALIE.
        // The algorithm should distribute them evenly: one on home team, one on away team.
        // This avoids any goalie choice being forced to play fielder, and avoids any fielder being forced to play goalie.
        mm.playerClasses.put("p1@test.com", "GOALIE");
        mm.playerClasses.put("p2@test.com", "GOALIE");
        mm.playerClasses.put("p3@test.com", "WARRIOR");
        mm.playerClasses.put("p4@test.com", "WARRIOR");
        mm.playerClasses.put("p5@test.com", "MAGE");
        mm.playerClasses.put("p6@test.com", "RANGER");
        
        List<String> gameFor = Arrays.asList(
            "p1@test.com", "p2@test.com", "p3@test.com",
            "p4@test.com", "p5@test.com", "p6@test.com"
        );
        
        ManagedGame game = new ManagedGame("test-game-4", op, gameFor);
        
        int p1Slot = game.preAssignedSlots.get("p1@test.com");
        int p2Slot = game.preAssignedSlots.get("p2@test.com");
        
        // One must be home goalie (slot 1), the other must be away goalie (slot 2)
        Assert.assertTrue((p1Slot == 1 && p2Slot == 2) || (p1Slot == 2 && p2Slot == 1));
    }
}
