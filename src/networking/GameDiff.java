package networking;

import gameserver.engine.GameEngine;

import java.lang.reflect.*;
import java.util.*;

public class GameDiff {
    private final Map<String, Object> changes = new HashMap<>();

    public static GameDiff diff(GameEngine oldState, GameEngine newState) {
        GameDiff gameDiff = new GameDiff();
        computeDiff(oldState, newState, gameDiff, "");
        return gameDiff;
    }

    public static GameEngine apply(GameDiff diff, GameEngine game) {
        applyDiff(diff, game, "");
        return game;
    }

    //Reflection-based deep diff computation
    private static void computeDiff(Object oldObj, Object newObj, GameDiff diff, String path) {
        if (oldObj == null || newObj == null || oldObj.getClass() != newObj.getClass()) {
            diff.changes.put(path, newObj);
            return;
        }

        Class<?> clazz = oldObj.getClass();

        if (clazz.isPrimitive() || clazz.equals(String.class) || clazz.equals(Integer.class) ||
                clazz.equals(Long.class) || clazz.equals(Boolean.class) || clazz.equals(Double.class) ||
                clazz.equals(Float.class) || clazz.equals(UUID.class)) {
            if (!Objects.equals(oldObj, newObj)) {
                diff.changes.put(path, newObj);
            }
            return;
        }

        if (clazz.isArray()) {
            int length = Array.getLength(newObj);
            for (int i = 0; i < length; i++) {
                Object oldElement = i < Array.getLength(oldObj) ? Array.get(oldObj, i) : null;
                Object newElement = Array.get(newObj, i);
                computeDiff(oldElement, newElement, diff, path + "[" + i + "]");
            }
            return;
        }

        if (Collection.class.isAssignableFrom(clazz)) {
            List<?> oldList = (List<?>) oldObj;
            List<?> newList = (List<?>) newObj;

            for (int i = 0; i < newList.size(); i++) {
                Object oldElement = i < oldList.size() ? oldList.get(i) : null;
                computeDiff(oldElement, newList.get(i), diff, path + "[" + i + "]");
            }
            return;
        }

        if (Map.class.isAssignableFrom(clazz)) {
            Map<?, ?> oldMap = (Map<?, ?>) oldObj;
            Map<?, ?> newMap = (Map<?, ?>) newObj;

            for (Object key : newMap.keySet()) {
                computeDiff(oldMap.get(key), newMap.get(key), diff, path + "[" + key + "]");
            }
            return;
        }

        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            try {
                Object oldValue = field.get(oldObj);
                Object newValue = field.get(newObj);
                computeDiff(oldValue, newValue, diff, path + "." + field.getName());
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    private static void applyDiff(GameDiff diff, Object target, String path) {
        if (target == null || diff.changes.isEmpty()) return;

        Class<?> clazz = target.getClass();

        // Apply primitive or directly updatable values
        if (clazz.isPrimitive() || clazz.equals(String.class) || clazz.equals(Integer.class) ||
                clazz.equals(Long.class) || clazz.equals(Boolean.class) || clazz.equals(Double.class) ||
                clazz.equals(Float.class) || clazz.equals(UUID.class)) {
            return; // No modification necessary for primitive root objects
        }

        // Apply changes for nested fields
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            try {
                String fieldPath = path + "." + field.getName();
                if (diff.changes.containsKey(fieldPath)) {
                    field.set(target, diff.changes.get(fieldPath)); // Directly apply the change
                } else {
                    Object fieldValue = field.get(target);
                    applyDiff(diff, fieldValue, fieldPath); // Recurse for nested objects
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public String toString() {
        return changes.toString();
    }

    public boolean isEmpty() {
        return changes.isEmpty();
    }
}