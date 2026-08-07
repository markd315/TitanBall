import gameserver.engine.GameEngine;
import gameserver.engine.GoalHoop;
import gameserver.gamemanager.ManagedGame;
import gameserver.targeting.ShapePayload;
import networking.PlayerDivider;
import org.joda.time.Instant;
import org.junit.Test;
import static org.junit.Assert.*;

import gameserver.entity.Titan;
import gameserver.entity.TitanType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SerializationTest {

    @Test
    public void testGameEngineDeepClone() {
        GameEngine gameEngine = new GameEngine();
        gameEngine.gameId = "test-game-123";
        
        // Populate clients with PlayerDivider
        List<PlayerDivider> clients = new ArrayList<>();
        PlayerDivider pd = new PlayerDivider(Arrays.asList(1, 2, 3));
        pd.id = 456;
        pd.email = "test@example.com";
        pd.ready = true;
        pd.wasVictorious = 0;
        clients.add(pd);
        gameEngine.clients = clients;
        
        // Add a GoalHoop with nextAvailable set
        GoalHoop goal = new GoalHoop();
        goal.nextAvailable = Instant.now();
        goal.onCooldown = true;
        gameEngine.homeHiGoal = goal;
        
        // Add a ShapePayload with dispUntil set
        ShapePayload shape = new ShapePayload();
        shape.dispUntil = Instant.now();
        shape.disp = true;
        gameEngine.colliders = new ArrayList<>();
        gameEngine.colliders.add(shape);

        // Setup locked player type to verify serialization bug
        Titan titan = gameEngine.players[2];
        titan.setType(TitanType.ARTISAN);
        titan.typeAndMasteriesLocked = true;
        
        System.out.println("[SerializationTest] Performing deepClone on GameEngine...");
        Object clonedObj = ManagedGame.deepClone(gameEngine);
        assertNotNull("Cloned object should not be null", clonedObj);
        
        assertTrue("Cloned object should be an instance of GameEngine", clonedObj instanceof GameEngine);
        GameEngine cloned = (GameEngine) clonedObj;
        
        assertEquals("test-game-123", cloned.gameId);
        assertEquals(1, cloned.clients.size());
        assertEquals(456, cloned.clients.get(0).id);
        assertEquals("test@example.com", cloned.clients.get(0).email);
        assertTrue(cloned.clients.get(0).ready);
        
        assertNotNull(cloned.homeHiGoal.nextAvailable);
        assertTrue(cloned.homeHiGoal.onCooldown);
        
        assertNotNull(cloned.colliders.get(0).dispUntil);
        assertTrue(cloned.colliders.get(0).disp);

        // Verify that the locked player type is preserved
        Titan clonedTitan = cloned.players[2];
        assertNotNull("Cloned titan at index 2 should not be null", clonedTitan);
        assertEquals(TitanType.ARTISAN, clonedTitan.getType());
        assertTrue(clonedTitan.typeAndMasteriesLocked);

        System.out.println("[SerializationTest] DeepClone test passed successfully!");
    }
}
