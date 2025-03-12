package networking;

import gameserver.engine.GameEngine;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

public class GameDiff {
    private Map<String, Object> changes = new HashMap<>();
    // Track object identity to handle circular references
    private static final ThreadLocal<IdentityHashMap<Object, String>> processedObjects =
            ThreadLocal.withInitial(() -> new IdentityHashMap<>());

    public GameDiff() {} // For kryo

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
        try {
            processedObjects.get().clear(); // Clear the identity map before starting
            diffObjects(oldObj, newObj, "", changes);
        } finally {
            processedObjects.get().clear(); // Clean up after diffing
        }
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

        // Check for circular references
        String existingPath = processedObjects.get().get(newObj);
        if (existingPath != null) {
            // We've seen this object before, record it as a reference
            changes.put(path + "/___ref", existingPath);
            return;
        }

        // Record this object's path for circular reference detection
        if (!(newObj instanceof String) && !(newObj instanceof Number) &&
            !(newObj instanceof Boolean) && !(newObj instanceof Character)) {
            processedObjects.get().put(newObj, path);
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

        // Record array length change if different
        if (oldLength != newLength) {
            changes.put(path + "/___length", newLength);
        }

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
     * Compare two collections and populate the changes map with an improved approach
     * that handles identity better and detects structural changes
     */
    private static void diffCollections(Collection<?> oldCollection, Collection<?> newCollection,
                                       String path, Map<String, Object> changes) {
        // Record size change if different
        if (oldCollection.size() != newCollection.size()) {
            changes.put(path + "/___size", newCollection.size());
        }

        // Handle List specifically - preserve order and identity
        if (oldCollection instanceof List && newCollection instanceof List) {
            diffLists((List<?>) oldCollection, (List<?>) newCollection, path, changes);
            return;
        }

        // For other collections, convert to arrays and use array diffing
        // but with identity tracking improvements
        Object[] oldArray = oldCollection.toArray();
        Object[] newArray = newCollection.toArray();
        diffArrays(oldArray, newArray, path, changes);
    }

    /**
     * Compare two lists with improved identity handling
     */
    private static void diffLists(List<?> oldList, List<?> newList, String path, Map<String, Object> changes) {
        int oldSize = oldList.size();
        int newSize = newList.size();

        // Detect if elements were removed or added at specific positions
        // by comparing identity and content

        // Track which elements in the old list have been matched
        boolean[] matched = new boolean[oldSize];

        // First pass: try to match items in order as much as possible
        for (int i = 0; i < Math.min(oldSize, newSize); i++) {
            Object oldValue = oldList.get(i);
            Object newValue = newList.get(i);

            // If they're the same reference or equal, they match
            if (oldValue == newValue || (oldValue != null && newValue != null && oldValue.equals(newValue))) {
                matched[i] = true;
                continue;
            }

            // Different values at this position, record the change
            diffObjects(oldValue, newValue, path + "[" + i + "]", changes);
            matched[i] = true;
        }

        // Second pass: handle added items
        for (int i = oldSize; i < newSize; i++) {
            Object newValue = newList.get(i);
            changes.put(path + "[" + i + "]", newValue);
        }

        // Mark removed items as null
        for (int i = newSize; i < oldSize; i++) {
            changes.put(path + "[" + i + "]", null);
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
        try {
            // Reference resolution map for handling object references
            Map<String, Object> referenceMap = new HashMap<>();

            // First pass: collect all objects for reference resolution
            for (String path : changes.keySet()) {
                referenceMap.put(path, null);
            }

            // Second pass: apply changes while resolving references
            for (Map.Entry<String, Object> entry : changes.entrySet()) {
                String path = entry.getKey();
                Object value = entry.getValue();

                // Skip reference markers in first pass
                if (path.endsWith("/___ref") || path.endsWith("/___size") || path.endsWith("/___length")) {
                    continue;
                }

                try {
                    Object result = applyChange(game, path, value, referenceMap);
                    // Store the result for potential references
                    referenceMap.put(path, result);
                } catch (Exception e) {
                    System.err.println("Error applying change at path " + path + ": " + e.getMessage());
                    // Continue with other changes
                }
            }

            // Third pass: resolve references
            for (Map.Entry<String, Object> entry : changes.entrySet()) {
                String path = entry.getKey();
                Object value = entry.getValue();

                if (path.endsWith("/___ref")) {
                    String targetPath = path.substring(0, path.length() - 7); // Remove "/___ref"
                    String referencePath = (String) value;

                    // Get the referenced object
                    Object referencedObject = referenceMap.get(referencePath);
                    if (referencedObject != null) {
                        try {
                            // Apply the reference
                            applyChange(game, targetPath, referencedObject, referenceMap);
                        } catch (Exception e) {
                            System.err.println("Error resolving reference at path " + targetPath + ": " + e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Critical error applying diff: " + e.getMessage());
            e.printStackTrace();
            // Log the error but don't crash
        }
    }

    /**
     * Apply a single change to an object
     * @return The object that was modified or created at this path
     */
    private static Object applyChange(Object target, String path, Object value,
                                Map<String, Object> referenceMap) throws Exception {
        // Remove leading slash if present
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        // Split path into components
        String[] parts = path.split("/", 2);
        String currentPart = parts[0];

        // Check if this part references an array element
        if (currentPart.contains("[") && currentPart.endsWith("]")) {
            return applyArrayChange(target, currentPart, parts.length > 1 ? parts[1] : "", value, referenceMap);
        }

        // Get field for this part
        Field field = findField(target.getClass(), currentPart);
        if (field == null) {
            throw new IllegalArgumentException("Field not found: " + currentPart);
        }

        // Skip static and final fields
        int modifiers = field.getModifiers();
        if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) {
            return null;
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
                        return null;
                    }
                    field.set(target, nextTarget);
                }
                return applyChange(nextTarget, parts[1], value, referenceMap);
            } else {
                // This is the final field, set the value
                if (value != null || isPrimitiveOrBoxed(field.getType())) {
                    // Only set non-null values or primitives (which can be default values)
                    Object convertedValue = convertValueIfNeeded(value, field.getType());
                    field.set(target, convertedValue);
                    return convertedValue;
                }
                return field.get(target);
            }
        } catch (SecurityException e) {
            // Skip fields that can't be accessed due to security restrictions
            throw new Exception("Security restrictions prevent accessing " + currentPart, e);
        }
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
    private static Object applyArrayChange(Object target, String currentPart, String remainingPath,
                                        Object value, Map<String, Object> referenceMap) throws Exception {
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
            return null;
        }

        field.setAccessible(true);
        Object collection = field.get(target);

        if (collection == null) {
            // Create a new collection if null
            collection = createInstance(field.getType());
            if (collection == null) {
                return null;
            }
            field.set(target, collection);
        }

        // Handle different collection types
        if (collection.getClass().isArray()) {
            return handleArrayElement(collection, index, remainingPath, value, referenceMap);
        } else if (collection instanceof List) {
            return handleListElement((List<?>) collection, index, remainingPath, value, referenceMap);
        } else {
            throw new UnsupportedOperationException("Unsupported collection type: " + collection.getClass());
        }
    }

    /**
     * Handle a change to an array element
     */
    private static Object handleArrayElement(Object array, int index, String remainingPath,
                                          Object value, Map<String, Object> referenceMap) throws Exception {
        // Check for special length marker
        if (remainingPath.equals("___length")) {
            // Resize array
            int newLength = (Integer) value;
            Object newArray = Array.newInstance(array.getClass().getComponentType(), newLength);

            // Copy existing elements
            int oldLength = Array.getLength(array);
            for (int i = 0; i < Math.min(oldLength, newLength); i++) {
                Object element = Array.get(array, i);
                if (element != null) {
                    Array.set(newArray, i, element);
                }
            }

            // We can't directly resize the array, but we can replace the reference
            // This is a bit tricky and would need a specific logic for the target field
            return newArray;
        }

        // Ensure array is large enough
        int length = Array.getLength(array);
        if (index >= length) {
            // Resize array to accommodate the new index
            int newLength = index + 1;
            Object newArray = Array.newInstance(array.getClass().getComponentType(), newLength);

            // Copy existing elements
            for (int i = 0; i < length; i++) {
                Array.set(newArray, i, Array.get(array, i));
            }

            // Replace original array reference with new expanded array
            array = newArray;
        }

        Object element = Array.get(array, index);

        if (remainingPath.isEmpty()) {
            // Set the value directly
            if (value != null || isPrimitiveOrBoxed(array.getClass().getComponentType())) {
                Object convertedValue = convertValueIfNeeded(value, array.getClass().getComponentType());
                Array.set(array, index, convertedValue);
                return convertedValue;
            }
            return element;
        } else if (element != null) {
            // Navigate deeper
            return applyChange(element, remainingPath, value, referenceMap);
        } else if (value != null) {
            // Create a new element if needed
            Class<?> componentType = array.getClass().getComponentType();
            element = createInstance(componentType);
            if (element != null) {
                Array.set(array, index, element);
                return applyChange(element, remainingPath, value, referenceMap);
            }
        }
        return null;
    }

    /**
     * Handle a change to a list element with improved support for adding/removing
     */
    @SuppressWarnings("unchecked")
    private static Object handleListElement(List<?> list, int index, String remainingPath,
                                         Object value, Map<String, Object> referenceMap) throws Exception {
        // Check for special size marker
        if (remainingPath.equals("___size")) {
            int newSize = (Integer) value;

            // Shrink list if needed
            while (list.size() > newSize) {
                ((List<Object>) list).remove(list.size() - 1);
            }

            // Expand list if needed
            while (list.size() < newSize) {
                ((List<Object>) list).add(null);
            }

            return list;
        }

        if (index < 0) {
            throw new IndexOutOfBoundsException("Negative index: " + index);
        }

        // For safety, never expand a list more than 10000 elements at once
        if (index >= list.size() && index - list.size() > 10000) {
            throw new IndexOutOfBoundsException("Index too large: " + index);
        }

        // Ensure list is large enough
        while (list.size() <= index) {
            try {
                // Try to get the component type for the list
                Class<?> elementType = getListComponentType(list);
                Object defaultValue = null;

                if (elementType != null && !Object.class.equals(elementType)) {
                    defaultValue = createInstance(elementType);
                }

                // Add the element (null or default)
                ((List<Object>) list).add(defaultValue);
            } catch (Exception e) {
                // If we can't add elements safely, stop
                return null;
            }
        }

        Object element = list.get(index);

        if (remainingPath.isEmpty()) {
            // Direct value update
            if (value == null && !isPrimitiveOrBoxed(element != null ? element.getClass() : Object.class)) {
                // For reference types, null value means removal
                if (index < list.size()) {
                    ((List<Object>) list).set(index, null);
                }
            } else {
                // Regular update
                ((List<Object>) list).set(index, value);
            }
            return value;
        } else {
            // Navigate deeper
            if (element == null && remainingPath.length() > 0) {
                // Need to create element for deeper navigation
                Class<?> elementType = getListComponentType(list);
                if (elementType != null) {
                    element = createInstance(elementType);
                    ((List<Object>) list).set(index, element);
                }
            }

            if (element != null) {
                return applyChange(element, remainingPath, value, referenceMap);
            }
        }
        return null;
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
}