package networking;

import gameserver.engine.GameEngine;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;

/**
 * GameDiff is a utility that compares two objects recursively using reflection
 * and extracts all fields that have changed. The differences are stored in a map where
 * key paths (e.g. "players[0].X") represent the fields that changed, and values represent
 * the new values of those fields.
 *
 * This delta compression mechanism ensures that instead of sending the full game state on every
 * frame, the server can send only the changes (diffs), saving bandwidth. Diffs are then applied
 * client-side.
 */
public class GameDiff implements Serializable {
    private static final long serialVersionUID = 1L;

    private Map<String, Object> changes = new HashMap<>();
    // Special marker for REMOVE operations
    private static final Object REMOVAL_MARKER = new Object();

    public GameDiff() {} //For kryo

    public GameDiff(Map<String, Object> changes) {
        this.changes = changes;
    }

    /**
     * Compare two objects and return a map of differences
     * @param oldObj The original object
     * @param newObj The new object with changes
     * @return A map with paths to changed values and their new values
     */
    public static Map<String, Object> diff(Object oldObj, Object newObj) {
        Map<String, Object> changes = new HashMap<>();
        diffObjects(oldObj, newObj, "", changes);
        return changes;
    }

    /**
     * Recursively compare two objects and populate the changes map
     */
    private static void diffObjects(Object oldObj, Object newObj, String path, Map<String, Object> changes) {
        // If both are null or same reference, no changes
        if (oldObj == newObj) {
            return;
        }

        // If one is null but not the other, it's a change
        if (oldObj == null || newObj == null) {
            changes.put(path, newObj);
            return;
        }

        Class<?> clazz = oldObj.getClass();

        // If classes don't match, it's a complete change
        if (!clazz.equals(newObj.getClass())) {
            changes.put(path, newObj);
            return;
        }

        // Handle primitive types and strings
        if (clazz.isPrimitive() || oldObj instanceof String || oldObj instanceof Number ||
            oldObj instanceof Boolean || oldObj instanceof Character) {
            if (!oldObj.equals(newObj)) {
                changes.put(path, newObj);
            }
            return;
        }

        // Handle arrays
        if (clazz.isArray()) {
            diffArrays(oldObj, newObj, path, changes);
            return;
        }

        // Handle collections
        if (oldObj instanceof Collection) {
            diffCollections((Collection<?>) oldObj, (Collection<?>) newObj, path, changes);
            return;
        }

        // Handle maps
        if (oldObj instanceof Map) {
            diffMaps((Map<?, ?>) oldObj, (Map<?, ?>) newObj, path, changes);
            return;
        }

        // For all other objects, use reflection to compare fields
        diffFields(oldObj, newObj, path, changes);
    }

    /**
     * Compare two arrays and populate the changes map
     */
    private static void diffArrays(Object oldArray, Object newArray, String path, Map<String, Object> changes) {
        int oldLength = Array.getLength(oldArray);
        int newLength = Array.getLength(newArray);

        // First, mark elements that are in old array but not in new array for removal
        if (oldLength > newLength) {
            for (int i = newLength; i < oldLength; i++) {
                changes.put(path + "[" + i + "]", REMOVAL_MARKER);
            }
        }

        // Check each element in arrays
        for (int i = 0; i < Math.min(oldLength, newLength); i++) {
            Object oldValue = Array.get(oldArray, i);
            Object newValue = Array.get(newArray, i);

            if (oldValue == null && newValue == null) {
                continue;
            }

            diffObjects(oldValue, newValue, path + "[" + i + "]", changes);
        }

        // Add new elements
        for (int i = oldLength; i < newLength; i++) {
            changes.put(path + "[" + i + "]", Array.get(newArray, i));
        }
    }

    /**
     * Compare two collections and populate the changes map
     * Improved to handle removal operations properly
     */
    private static void diffCollections(Collection<?> oldCollection, Collection<?> newCollection,
                                       String path, Map<String, Object> changes) {
        // Special handling for lists to preserve synchronized lists like in EffectPool
        if (oldCollection instanceof List && newCollection instanceof List) {
            diffLists((List<?>) oldCollection, (List<?>) newCollection, path, changes);
            return;
        }

        // For other collection types, convert to arrays and use array diffing
        Object[] oldArray = oldCollection.toArray();
        Object[] newArray = newCollection.toArray();
        diffArrays(oldArray, newArray, path, changes);
    }

    /**
     * Compare two lists with special handling for removals
     */
    private static void diffLists(List<?> oldList, List<?> newList, String path, Map<String, Object> changes) {
        int oldSize = oldList.size();
        int newSize = newList.size();

        // Store original collections size to handle removal operations
        changes.put(path + "/_size", newSize);

        // Diff the common elements
        for (int i = 0; i < Math.min(oldSize, newSize); i++) {
            Object oldValue = oldList.get(i);
            Object newValue = newList.get(i);

            if (oldValue == null && newValue == null) {
                continue;
            }

            diffObjects(oldValue, newValue, path + "[" + i + "]", changes);
        }

        // Add new elements
        for (int i = oldSize; i < newSize; i++) {
            changes.put(path + "[" + i + "]", newList.get(i));
        }

        // Mark removed elements
        for (int i = newSize; i < oldSize; i++) {
            changes.put(path + "[" + i + "]", REMOVAL_MARKER);
        }
    }

    /**
     * Compare two maps and populate the changes map
     */
    private static void diffMaps(Map<?, ?> oldMap, Map<?, ?> newMap, String path, Map<String, Object> changes) {
        // Check for changes in existing keys
        for (Object key : oldMap.keySet()) {
            String keyPath = path + "/" + key.toString();

            if (newMap.containsKey(key)) {
                diffObjects(oldMap.get(key), newMap.get(key), keyPath, changes);
            } else {
                // Key was removed - use removal marker instead of null
                changes.put(keyPath, REMOVAL_MARKER);
            }
        }

        // Check for new keys
        for (Object key : newMap.keySet()) {
            if (!oldMap.containsKey(key)) {
                String keyPath = path + "/" + key.toString();
                changes.put(keyPath, newMap.get(key));
            }
        }
    }

    /**
     * Use reflection to compare fields between two objects
     */
    private static void diffFields(Object oldObj, Object newObj, String path, Map<String, Object> changes) {
        Class<?> clazz = oldObj.getClass();

        // Get all fields (including private ones)
        for (Field field : getAllAccessibleFields(clazz)) {
            // Skip static and final fields to avoid reflection issues
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) {
                continue;
            }

            try {
                field.setAccessible(true);
                Object oldValue = field.get(oldObj);
                Object newValue = field.get(newObj);

                String fieldPath = path.isEmpty() ? "/" + field.getName() : path + "/" + field.getName();
                diffObjects(oldValue, newValue, fieldPath, changes);
            } catch (IllegalAccessException | SecurityException e) {
                // Skip fields that can't be accessed due to security restrictions
                continue;
            }
        }
    }

    /**
     * Get all accessible fields for a class including inherited fields,
     * but skip fields from system classes to avoid reflection issues
     */
    private static List<Field> getAllAccessibleFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();

        // Get fields from this class and all superclasses
        Class<?> currentClass = clazz;
        while (currentClass != null && !isSystemClass(currentClass)) {
            fields.addAll(Arrays.asList(currentClass.getDeclaredFields()));
            currentClass = currentClass.getSuperclass();
        }

        return fields;
    }

    /**
     * Check if the class is a system class that should be skipped for reflection
     */
    private static boolean isSystemClass(Class<?> clazz) {
        String className = clazz.getName();
        return className.startsWith("java.") ||
               className.startsWith("javax.") ||
               className.startsWith("sun.") ||
               className.startsWith("com.sun.") ||
               className.equals("Object");
    }

    /**
     * Apply a diff to a GameEngine object
     * @param game The game to update
     */
    public void apply(GameEngine game) {
        try {
            Map<String, Integer> listSizes = new HashMap<>();

            // First pass: collect list sizes
            for (Map.Entry<String, Object> entry : changes.entrySet()) {
                String path = entry.getKey();
                Object value = entry.getValue();

                if (path.endsWith("/_size") && value instanceof Integer) {
                    listSizes.put(path.substring(0, path.length() - 6), (Integer) value);
                }
            }

            // Second pass: apply all changes except removals from lists
            for (Map.Entry<String, Object> entry : changes.entrySet()) {
                String path = entry.getKey();
                Object value = entry.getValue();

                // Skip size metadata entries
                if (path.endsWith("/_size")) {
                    continue;
                }

                try {
                    if (value != REMOVAL_MARKER) {
                        applyChange(game, path, value);
                    }
                } catch (Exception e) {
                    System.err.println("Error applying change at path " + path + ": " + e.getMessage());
                    // Continue with other changes
                }
            }

            // Third pass: handle list sizing and removal operations
            for (Map.Entry<String, Integer> entry : listSizes.entrySet()) {
                try {
                    applyListResize(game, entry.getKey(), entry.getValue());
                } catch (Exception e) {
                    System.err.println("Error resizing list at path " + entry.getKey() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Critical error applying diff: " + e.getMessage());
            // Log the error but don't crash
        }
    }

    /**
     * Resize a list to match the target size, properly removing excess elements
     */
    private static void applyListResize(Object target, String path, int targetSize) throws Exception {
        Object obj = resolveObjectAtPath(target, path);

        if (obj instanceof List) {
            List<?> list = (List<?>) obj;

            // If the list is too large, remove excess elements
            while (list.size() > targetSize) {
                ((List) list).remove(list.size() - 1);
            }
        }
    }

    /**
     * Resolve the object at the given path
     */
    private static Object resolveObjectAtPath(Object target, String path) throws Exception {
        if (path.isEmpty() || path.equals("/")) {
            return target;
        }

        // Remove leading slash if present
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        // Split path into components
        String[] parts = path.split("/", 2);
        String currentPart = parts[0];

        // Check if this part references an array element
        if (currentPart.contains("[") && currentPart.endsWith("]")) {
            int bracketIndex = currentPart.indexOf('[');
            String fieldName = currentPart.substring(0, bracketIndex);
            int index = Integer.parseInt(currentPart.substring(bracketIndex + 1, currentPart.length() - 1));

            Field field = findField(target.getClass(), fieldName);
            if (field == null) {
                throw new IllegalArgumentException("Field not found: " + fieldName);
            }

            field.setAccessible(true);
            Object collection = field.get(target);

            if (collection == null) {
                return null;
            }

            Object element = null;
            if (collection.getClass().isArray() && index < Array.getLength(collection)) {
                element = Array.get(collection, index);
            } else if (collection instanceof List && index < ((List<?>) collection).size()) {
                element = ((List<?>) collection).get(index);
            } else {
                return null;
            }

            if (parts.length > 1) {
                return resolveObjectAtPath(element, parts[1]);
            }
            return element;
        } else {
            Field field = findField(target.getClass(), currentPart);
            if (field == null) {
                throw new IllegalArgumentException("Field not found: " + currentPart);
            }

            field.setAccessible(true);
            Object value = field.get(target);

            if (parts.length > 1 && value != null) {
                return resolveObjectAtPath(value, parts[1]);
            }
            return value;
        }
    }

    /**
     * Apply a single change to an object
     */
    private static void applyChange(Object target, String path, Object value) throws Exception {
        // Remove leading slash if present
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        // Split path into components
        String[] parts = path.split("/", 2);
        String currentPart = parts[0];

        // Check if this part references an array element
        if (currentPart.contains("[") && currentPart.endsWith("]")) {
            applyArrayChange(target, currentPart, parts.length > 1 ? parts[1] : "", value);
            return;
        }

        // Get field for this part
        Field field = findField(target.getClass(), currentPart);
        if (field == null) {
            throw new IllegalArgumentException("Field not found: " + currentPart);
        }

        // Skip static and final fields
        int modifiers = field.getModifiers();
        if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) {
            return;
        }

        try {
            field.setAccessible(true);

            if (parts.length > 1) {
                // We need to traverse deeper
                Object nextTarget = field.get(target);
                if (nextTarget == null) {
                    // Create a new instance if the field is null
                    nextTarget = createInstance(field.getType());
                    if (nextTarget == null) {
                        // If we can't create an instance, we can't continue
                        return;
                    }
                    field.set(target, nextTarget);
                }
                applyChange(nextTarget, parts[1], value);
            } else {
                // This is the final field, set the value
                if (value != null || isPrimitiveOrBoxed(field.getType())) {
                    // Special handling for REMOVAL_MARKER
                    if (value == REMOVAL_MARKER) {
                        // Set to null or default for primitive types
                        if (field.getType().isPrimitive()) {
                            field.set(target, getDefaultPrimitiveValue(field.getType()));
                        } else {
                            field.set(target, null);
                        }
                    } else {
                        // Normal value setting
                        Object convertedValue = convertValueIfNeeded(value, field.getType());
                        field.set(target, convertedValue);
                    }
                }
            }
        } catch (SecurityException e) {
            // Skip fields that can't be accessed due to security restrictions
            throw new Exception("Security restrictions prevent accessing " + currentPart, e);
        }
    }

    /**
     * Get default value for primitive types
     */
    private static Object getDefaultPrimitiveValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0.0f;
        if (type == byte.class) return (byte)0;
        if (type == short.class) return (short)0;
        if (type == char.class) return (char)0;
        return null;
    }

    /**
     * Check if a class is a primitive type or its boxed equivalent
     */
    private static boolean isPrimitiveOrBoxed(Class<?> clazz) {
        return clazz.isPrimitive() ||
               clazz == Boolean.class ||
               clazz == Character.class ||
               clazz == Byte.class ||
               clazz == Short.class ||
               clazz == Integer.class ||
               clazz == Long.class ||
               clazz == Float.class ||
               clazz == Double.class;
    }

    /**
     * Apply a change to an array or collection element
     */
    private static void applyArrayChange(Object target, String currentPart, String remainingPath,
                                        Object value) throws Exception {
        // Parse the field name and index
        int bracketIndex = currentPart.indexOf('[');
        String fieldName = currentPart.substring(0, bracketIndex);
        int index = Integer.parseInt(currentPart.substring(bracketIndex + 1, currentPart.length() - 1));

        Field field = findField(target.getClass(), fieldName);
        if (field == null) {
            throw new IllegalArgumentException("Field not found: " + fieldName);
        }

        // Skip static and final fields
        int modifiers = field.getModifiers();
        if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) {
            return;
        }

        field.setAccessible(true);
        Object collection = field.get(target);

        if (collection == null) {
            // Create a new collection if null
            collection = createInstance(field.getType());
            if (collection == null) {
                return;
            }
            field.set(target, collection);
        }

        // Handle different collection types
        if (collection.getClass().isArray()) {
            handleArrayElement(collection, index, remainingPath, value);
        } else if (collection instanceof List) {
            handleListElement((List<?>) collection, index, remainingPath, value);
        } else {
            throw new UnsupportedOperationException("Unsupported collection type: " + collection.getClass());
        }
    }

    /**
     * Handle a change to an array element
     */
    private static void handleArrayElement(Object array, int index, String remainingPath,
                                          Object value) throws Exception {
        // Ensure array is large enough
        int length = Array.getLength(array);
        if (index >= length) {
            // We can't resize arrays, so just ignore this change
            return;
        }

        Object element = Array.get(array, index);

        if (remainingPath.isEmpty()) {
            // Handle removal marker specially
            if (value == REMOVAL_MARKER) {
                Array.set(array, index, null);
            }
            // Set the value directly otherwise
            else if (value != null || isPrimitiveOrBoxed(array.getClass().getComponentType())) {
                Object convertedValue = convertValueIfNeeded(value, array.getClass().getComponentType());
                Array.set(array, index, convertedValue);
            }
        } else if (element != null) {
            // Only navigate deeper if element is not null
            applyChange(element, remainingPath, value);
        }
    }

    /**
     * Handle a change to a list element
     */
    @SuppressWarnings("unchecked")
    private static void handleListElement(List<?> list, int index, String remainingPath,
                                         Object value) throws Exception {
        if (index < 0) {
            throw new IndexOutOfBoundsException("Negative index: " + index);
        }

        // For safety, never expand a list more than 10000 elements at once
        if (index >= list.size() && index - list.size() > 10000) {
            throw new IndexOutOfBoundsException("Index too large: " + index);
        }

        // If handling a removal marker and the index is valid, just return
        // (removals are handled in the final resizing phase)
        if (value == REMOVAL_MARKER && index < list.size()) {
            return;
        }

        // Ensure list is large enough but with safety
        while (list.size() <= index) {
            try {
                // Try to get the component type for the list
                Class<?> elementType = getListComponentType(list);
                Object defaultValue = null;

                if (elementType != null && !Object.class.equals(elementType)) {
                    defaultValue = createInstance(elementType);
                }

                // Only add non-null values if possible
                if (defaultValue != null) {
                    ((List<Object>) list).add(defaultValue);
                } else {
                    // As a last resort, add null, but only if necessary
                    if (remainingPath.isEmpty() && value != null && value != REMOVAL_MARKER) {
                        ((List<Object>) list).add(null);
                    } else {
                        // Can't continue with null values and nested paths
                        return;
                    }
                }
            } catch (Exception e) {
                // If we can't add elements safely, stop
                return;
            }
        }

        if (remainingPath.isEmpty()) {
            // Set the value directly if not a removal marker
            if (value != null && value != REMOVAL_MARKER) {
                ((List<Object>) list).set(index, value);
            }
        } else {
            Object element = list.get(index);
            if (element != null) {
                // Navigate deeper only if element is not null
                applyChange(element, remainingPath, value);
            }
        }
    }

    /**
     * Attempt to determine the component type of a list
     */
    private static Class<?> getListComponentType(List<?> list) {
        // Try to infer from existing elements
        for (Object item : list) {
            if (item != null) {
                return item.getClass();
            }
        }
        return null;
    }

    /**
     * Find a field in a class or its superclasses, but skip system classes
     */
    private static Field findField(Class<?> clazz, String fieldName) {
        Class<?> currentClass = clazz;
        while (currentClass != null && !isSystemClass(currentClass)) {
            try {
                return currentClass.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                // Try superclass
                currentClass = currentClass.getSuperclass();
            }
        }
        return null;
    }

    /**
     * Create a new instance of a class safely
     */
    private static Object createInstance(Class<?> clazz) {
        try {
            if (clazz.isArray()) {
                return Array.newInstance(clazz.getComponentType(), 0); // Start with empty array
            } else if (List.class.isAssignableFrom(clazz)) {
                return new ArrayList<>();
            } else if (Set.class.isAssignableFrom(clazz)) {
                return new HashSet<>();
            } else if (Map.class.isAssignableFrom(clazz)) {
                return new HashMap<>();
            } else if (String.class.equals(clazz)) {
                return "";
            } else if (Boolean.class.equals(clazz) || boolean.class.equals(clazz)) {
                return Boolean.FALSE;
            } else if (Integer.class.equals(clazz) || int.class.equals(clazz)) {
                return 0;
            } else if (Long.class.equals(clazz) || long.class.equals(clazz)) {
                return 0L;
            } else if (Double.class.equals(clazz) || double.class.equals(clazz)) {
                return 0.0;
            } else if (Float.class.equals(clazz) || float.class.equals(clazz)) {
                return 0.0f;
            } else if (!clazz.isInterface() && !Modifier.isAbstract(clazz.getModifiers())) {
                return clazz.getDeclaredConstructor().newInstance();
            }
        } catch (Exception e) {
            // Ignore and return null
        }
        return null;
    }

    /**
     * Convert a value if needed to match the target type
     */
    private static Object convertValueIfNeeded(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }

        // If value is already of the right type, return it
        if (targetType.isAssignableFrom(value.getClass())) {
            return value;
        }

        try {
            // Handle primitive type conversions
            if (targetType == int.class || targetType == Integer.class) {
                if (value instanceof Number) {
                    return ((Number) value).intValue();
                }
                return Integer.parseInt(value.toString());
            } else if (targetType == boolean.class || targetType == Boolean.class) {
                if (value instanceof Boolean) {
                    return value;
                }
                return Boolean.parseBoolean(value.toString());
            } else if (targetType == long.class || targetType == Long.class) {
                if (value instanceof Number) {
                    return ((Number) value).longValue();
                }
                return Long.parseLong(value.toString());
            } else if (targetType == double.class || targetType == Double.class) {
                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                }
                return Double.parseDouble(value.toString());
            } else if (targetType == float.class || targetType == Float.class) {
                if (value instanceof Number) {
                    return ((Number) value).floatValue();
                }
                return Float.parseFloat(value.toString());
            }
        } catch (Exception e) {
            // If conversion fails, return null for reference types or default for primitives
            if (targetType.isPrimitive()) {
                if (targetType == boolean.class) return false;
                if (targetType == int.class) return 0;
                if (targetType == long.class) return 0L;
                if (targetType == double.class) return 0.0;
                if (targetType == float.class) return 0.0f;
                if (targetType == byte.class) return (byte)0;
                if (targetType == short.class) return (short)0;
                if (targetType == char.class) return (char)0;
            }
            return null;
        }
        
        // For other types, just return the value and hope it works
        return value;
    }

    public boolean isEmpty() {
        return changes.isEmpty();
    }

    /**
     * Calculate the ratio of the diff size to the original state size in bytes
     *
     * @param previousState The original game state
     * @return A ratio between 0 and 1 representing the relative size of the diff
     */
    public double getChangeRatio(GameEngine previousState) {
        try {
            // Serialize the diff
            ByteArrayOutputStream diffOut = new ByteArrayOutputStream();
            ObjectOutputStream diffOos = new ObjectOutputStream(diffOut);
            diffOos.writeObject(this);
            diffOos.close();
            int diffSize = diffOut.size();

            // Serialize the full state
            ByteArrayOutputStream stateOut = new ByteArrayOutputStream();
            ObjectOutputStream stateOos = new ObjectOutputStream(stateOut);
            stateOos.writeObject(previousState);
            stateOos.close();
            int stateSize = stateOut.size();

            // Calculate ratio
            return (double) diffSize / stateSize;
        } catch (Exception e) {
            e.printStackTrace();
            return 1.0; // If serialization fails, default to sending full state
        }
    }
}