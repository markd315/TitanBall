package networking;

import de.danielbechler.diff.ObjectDifferBuilder;
import de.danielbechler.diff.node.DiffNode;
import gameserver.engine.GameEngine;

import java.lang.reflect.*;
import java.util.*;

public class GameDiff {
    private Map<String, Object> changes = new HashMap<>();

    public GameDiff(Map<String, Object> changes) {
        this.changes = changes;
    }

    public void apply(GameEngine game) {
        apply(game, this);
    }

    // Diffing method
    public static Map<String, Object> diff(GameEngine oldState, GameEngine newState) {
        DiffNode rootNode = ObjectDifferBuilder.buildDefault().compare(newState, oldState);

        Map<String, Object> changes = new HashMap<>();

        rootNode.visit((node, visit) -> {
            if (node.hasChanges() && !node.hasChildren()) { // Only leaf nodes store actual values
                String path = node.getPath().toString();
                Object changedValue = null;

                // Determine the new value based on state
                switch (node.getState()) {
                    case ADDED:
                        changedValue = node.canonicalGet(newState); // Gets new value
                        break;
                    case CHANGED:
                        changedValue = node.canonicalGet(newState); // Gets modified value
                        break;
                    case REMOVED:
                        changedValue = null; // Field was removed
                        break;
                }
                System.out.println("Diffing " + path + " to " + changedValue);
                changes.put(path, changedValue);
            }
        });
        return changes;
    }

    // Apply the patch method (to update the game state based on diff)
    public static void apply(GameEngine game, GameDiff diff) {
        for (Map.Entry<String, Object> entry : diff.changes.entrySet()) {
            try {
                setFieldByPath(game, entry.getKey(), entry.getValue());
            } catch (Exception e) {
                System.err.println("Failed to apply diff for field: " + entry.getKey());
                e.printStackTrace();
            }
        }
    }

    // Helper to apply the patch based on field paths
    private static void setFieldByPath(Object obj, String path, Object value) throws Exception {
        String[] parts = path.split("\\.");
        Object target = obj;

        for (int i = 0; i < parts.length - 1; i++) {
            String fieldName = parts[i];
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            target = field.get(target);
        }

        Field field = target.getClass().getDeclaredField(parts[parts.length - 1]);
        field.setAccessible(true);
        field.set(target, value);
    }

    public boolean isEmpty() {
        return changes.isEmpty();
    }
}
