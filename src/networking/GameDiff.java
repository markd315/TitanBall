package networking;

import gameserver.engine.GameEngine;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

public class GameDiff {
    private Map<String, Object> changes = new HashMap<>();

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

        // Check each element in arrays
        for (int i = 0; i < Math.max(oldLength, newLength); i++) {
            if (i < oldLength && i < newLength) {
                // Both arrays have this index, compare values
                Object oldValue = Array.get(oldArray, i);
                Object newValue = Array.get(newArray, i);

                if (oldValue == null && newValue == null) {
                    continue;
                }

                diffObjects(oldValue, newValue, path + "[" + i + "]", changes);
            } else if (i < newLength) {
                // New array has an extra element
                changes.put(path + "[" + i + "]", Array.get(newArray, i));
            } else {
                // Old array has an element that was removed
                changes.put(path + "[" + i + "]", null);
            }
        }
    }

    /**
     * Compare two collections and populate the changes map
     */
    private static void diffCollections(Collection<?> oldCollection, Collection<?> newCollection,
                                       String path, Map<String, Object> changes) {
        // Convert to arrays and use array diffing
        Object[] oldArray = oldCollection.toArray();
        Object[] newArray = newCollection.toArray();
        diffArrays(oldArray, newArray, path, changes);
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
                // Key was removed
                changes.put(keyPath, null);
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
        for (Map.Entry<String, Object> entry : changes.entrySet()) {
            String path = entry.getKey();
            Object value = entry.getValue();

            applyChange(game, path, value);
        }
    }

    /**
     * Apply a single change to an object
     */
    private static void applyChange(Object target, String path, Object value) {
        // Remove leading slash if present
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        // Split path into components
        String[] parts = path.split("/", 2);
        String currentPart = parts[0];

        try {
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
                        field.set(target, nextTarget);
                    }
                    applyChange(nextTarget, parts[1], value);
                } else {
                    // This is the final field, set the value
                    Object convertedValue = convertValueIfNeeded(value, field.getType());
                    field.set(target, convertedValue);
                }
            } catch (SecurityException e) {
                // Skip fields that can't be accessed due to security restrictions
            }
        } catch (Exception e) {
            // Log error and continue
            System.err.println("Error applying change to path: " + path + ": " + e.getMessage());
        }
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
            throw new IndexOutOfBoundsException("Array index out of bounds: " + index);
        }

        Object element = Array.get(array, index);

        if (remainingPath.isEmpty()) {
            // Set the value directly
            Object convertedValue = convertValueIfNeeded(value, element.getClass());
            Array.set(array, index, convertedValue);
        } else {
            // Navigate deeper
            applyChange(element, remainingPath, value);
        }
    }

    /**
     * Handle a change to a list element
     */
    @SuppressWarnings("unchecked")
    private static void handleListElement(List<?> list, int index, String remainingPath,
                                         Object value) throws Exception {
        // Ensure list is large enough
        while (list.size() <= index) {
            ((List<Object>) list).add(null);
        }

        Object element = list.get(index);

        if (remainingPath.isEmpty()) {
            // Set the value directly
            ((List<Object>) list).set(index, value);
        } else {
            // Create element if null
            if (element == null) {
                // Try to determine the element type
                element = createGenericObject();
                ((List<Object>) list).set(index, element);
            }
            // Navigate deeper
            applyChange(element, remainingPath, value);
        }
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
     * Create a new instance of a class
     */
    private static Object createInstance(Class<?> clazz) throws Exception {
        if (clazz.isArray()) {
            return Array.newInstance(clazz.getComponentType(), 10); // Default size
        } else if (List.class.isAssignableFrom(clazz)) {
            return new ArrayList<>();
        } else if (Set.class.isAssignableFrom(clazz)) {
            return new HashSet<>();
        } else if (Map.class.isAssignableFrom(clazz)) {
            return new HashMap<>();
        } else {
            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                // For classes without a no-arg constructor
                return null;
            }
        }
    }
    
    /**
     * Create a generic object for collections with unknown element types
     */
    private static Object createGenericObject() {
        return new HashMap<String, Object>();
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
        
        // For other types, just return the value and hope it works
        return value;
    }

    public boolean isEmpty() {
        return changes.isEmpty();
    }
}