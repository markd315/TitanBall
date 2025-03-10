import gameserver.engine.GameEngine;
import networking.GameDiff;
import org.junit.jupiter.api.Test;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class GameDiffTest {

    @Test
    void testNestedObjectDiffing() {
        // Create original game state
        GameEngine oldState = new GameEngine();
        // Create a modified game state
        GameEngine newState = new GameEngine();
        newState.players[2].isBoosting = true;
        newState.away.score = 150;

        // Run diff
        Map<String, Object> changes = GameDiff.diff(oldState, newState);

        // Print changes (for debug purposes)
        changes.forEach((key, value) -> System.out.println("Changed " + key + " -> " + value));

        // Assertions
        assertEquals(2, changes.size()); // Expecting 4 changes
        assertEquals(150.0, changes.get("/away/score")); // Score changed
        assertNotNull(changes.get("/players[2]/isBoosting"));
    }

    @Test
    void testPatch()    {
        // Create original game state
        GameEngine patchState = new GameEngine();

        HashMap<String, Object> changes = new HashMap<>();
        changes.put("/away/score", 150);
        changes.put("/players[2]/isBoosting", true);
        GameDiff d = new GameDiff(changes);
        // Run diff
        d.apply(patchState);

        assertEquals(150.0, patchState.away.score);
        assertEquals(true, patchState.players[2].isBoosting);
    }
}
