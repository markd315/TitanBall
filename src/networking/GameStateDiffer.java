package networking;

import gameserver.engine.GameEngine;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class GameStateDiffer {
    
    public static GameStateDiff computeDiff(GameEngine last, GameEngine current) {
        GameStateDiff diff = new GameStateDiff();
        if (last == null) {
            diff.fullState = current; // Send full state if no previous version exists
            return diff;
        }

        diff.changedFields = new HashMap<>();
        
        for (Field field : GameEngine.class.getDeclaredFields()) {
            field.setAccessible(true);
            try {
                Object lastValue = field.get(last);
                Object currentValue = field.get(current);

                if (!Objects.equals(lastValue, currentValue)) {
                    diff.changedFields.put(field.getName(), currentValue);
                }
            } catch (IllegalAccessException e) {
                System.err.println("Error accessing field: " + field.getName());
            }
        }
        return diff;
    }

    public static void applyPatch(GameEngine target, GameStateDiff patch) {
        if (patch.fullState != null) {
            // Full state update
            for (Field field : GameEngine.class.getDeclaredFields()) {
                System.out.println("Applying full patch to field: " + field.getName());
                field.setAccessible(true);
                try {
                    field.set(target, field.get(patch.fullState));
                } catch (IllegalAccessException e) {
                    System.err.println("Error applying full patch to field: " + field.getName());
                }
            }
            return;
        }

        // Apply only changed fields
        for (Map.Entry<String, Object> entry : patch.changedFields.entrySet()) {
            try {
                Field field = GameEngine.class.getDeclaredField(entry.getKey());
                field.setAccessible(true);
                field.set(target, entry.getValue());
            } catch (NoSuchFieldException | IllegalAccessException e) {
                System.err.println("Error applying diff to field: " + entry.getKey());
            }
        }
    }
}