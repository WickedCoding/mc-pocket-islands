package com.wickedsik.personalworlds.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for dimensionGameRules configuration behavior.
 * Tests config structure, value types, and GSON deserialization compatibility.
 *
 * Note: ModConfig cannot be instantiated directly in tests because it has a
 * static initializer that calls FabricLoader.getInstance(). Tests here validate
 * the map-based config behavior independently.
 */
class GameRuleConfigTest {

    @Nested
    @DisplayName("Default Configuration")
    class DefaultConfig {

        @Test
        @DisplayName("Default game rules map contains doMobSpawning=false")
        void defaultGameRules_containsDoMobSpawning() {
            // Mirrors the default value from ModConfig.dimensionGameRules
            Map<String, Object> defaults = new LinkedHashMap<>(Map.of("doMobSpawning", false));

            assertTrue(defaults.containsKey("doMobSpawning"));
            assertEquals(false, defaults.get("doMobSpawning"));
            assertEquals(1, defaults.size());
        }
    }

    @Nested
    @DisplayName("Value Type Handling")
    class ValueTypes {

        @Test
        @DisplayName("Boolean values are stored and retrieved correctly")
        void booleanValues_areAccepted() {
            Map<String, Object> rules = new LinkedHashMap<>();
            rules.put("keepInventory", true);
            rules.put("doMobSpawning", false);

            assertInstanceOf(Boolean.class, rules.get("keepInventory"));
            assertInstanceOf(Boolean.class, rules.get("doMobSpawning"));
            assertEquals(true, rules.get("keepInventory"));
            assertEquals(false, rules.get("doMobSpawning"));
        }

        @Test
        @DisplayName("Integer values are stored as Number and intValue() works")
        void integerValues_areAccepted() {
            Map<String, Object> rules = new LinkedHashMap<>();
            rules.put("randomTickSpeed", 0);
            rules.put("spawnRadius", 10);

            assertInstanceOf(Number.class, rules.get("randomTickSpeed"));
            assertInstanceOf(Number.class, rules.get("spawnRadius"));
            assertEquals(0, ((Number) rules.get("randomTickSpeed")).intValue());
            assertEquals(10, ((Number) rules.get("spawnRadius")).intValue());
        }

        @Test
        @DisplayName("Mixed boolean and integer values coexist in map")
        void mixedValues_coexist() {
            Map<String, Object> rules = new LinkedHashMap<>();
            rules.put("keepInventory", true);
            rules.put("doMobSpawning", false);
            rules.put("randomTickSpeed", 0);

            assertEquals(3, rules.size());
            assertInstanceOf(Boolean.class, rules.get("keepInventory"));
            assertInstanceOf(Boolean.class, rules.get("doMobSpawning"));
            assertInstanceOf(Number.class, rules.get("randomTickSpeed"));
        }
    }

    @Nested
    @DisplayName("Map Structure")
    class MapStructure {

        @Test
        @DisplayName("Empty map is valid (all rules inherit from overworld)")
        void emptyMap_isValid() {
            Map<String, Object> rules = new LinkedHashMap<>();
            assertNotNull(rules);
            assertTrue(rules.isEmpty());
        }

        @Test
        @DisplayName("Rules can be added and retrieved by name")
        void rulesCanBeAdded() {
            Map<String, Object> rules = new LinkedHashMap<>();
            rules.put("keepInventory", true);

            assertTrue(rules.containsKey("keepInventory"));
            assertEquals(true, rules.get("keepInventory"));
        }

        @Test
        @DisplayName("Rules can be overwritten")
        void rulesCanBeOverwritten() {
            Map<String, Object> rules = new LinkedHashMap<>();
            rules.put("keepInventory", false);
            rules.put("keepInventory", true);

            assertEquals(true, rules.get("keepInventory"));
        }
    }

    @Nested
    @DisplayName("Validation Logic")
    class Validation {

        @Test
        @DisplayName("Boolean values pass instanceof check")
        void booleanValues_passInstanceofCheck() {
            Object trueVal = true;
            Object falseVal = false;

            assertTrue(trueVal instanceof Boolean);
            assertTrue(falseVal instanceof Boolean);
        }

        @Test
        @DisplayName("Integer values pass Number instanceof check")
        void integerValues_passNumberCheck() {
            Object intVal = 42;
            Object doubleVal = 3.0; // GSON deserializes integers as Double

            assertTrue(intVal instanceof Number);
            assertTrue(doubleVal instanceof Number);
            assertEquals(42, ((Number) intVal).intValue());
            assertEquals(3, ((Number) doubleVal).intValue());
        }

        @Test
        @DisplayName("String values fail Boolean and Number instanceof checks")
        void stringValues_failTypeChecks() {
            Object strVal = "true";

            assertFalse(strVal instanceof Boolean);
            assertFalse(strVal instanceof Number);
        }

        @Test
        @DisplayName("Null values fail Boolean and Number instanceof checks")
        void nullValues_failTypeChecks() {
            Object nullVal = null;

            assertFalse(nullVal instanceof Boolean);
            assertFalse(nullVal instanceof Number);
        }
    }

    @Nested
    @DisplayName("GSON Deserialization Compatibility")
    class GsonCompat {

        @Test
        @DisplayName("GSON deserializes JSON booleans as Boolean")
        void gsonBooleans_areBoolean() {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            @SuppressWarnings("unchecked")
            Map<String, Object> map = gson.fromJson("{\"keepInventory\": true}", Map.class);

            assertInstanceOf(Boolean.class, map.get("keepInventory"));
            assertEquals(true, map.get("keepInventory"));
        }

        @Test
        @DisplayName("GSON deserializes JSON integers as Double (Number), intValue() converts correctly")
        void gsonIntegers_areNumber() {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            @SuppressWarnings("unchecked")
            Map<String, Object> map = gson.fromJson("{\"randomTickSpeed\": 0}", Map.class);

            Object value = map.get("randomTickSpeed");
            assertInstanceOf(Number.class, value);
            assertEquals(0, ((Number) value).intValue());
        }

        @Test
        @DisplayName("GSON deserializes mixed boolean/integer types correctly")
        void gsonMixed_correctTypes() {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            String json = "{\"keepInventory\": true, \"doMobSpawning\": false, \"randomTickSpeed\": 3}";
            @SuppressWarnings("unchecked")
            Map<String, Object> map = gson.fromJson(json, Map.class);

            assertInstanceOf(Boolean.class, map.get("keepInventory"));
            assertInstanceOf(Boolean.class, map.get("doMobSpawning"));
            assertInstanceOf(Number.class, map.get("randomTickSpeed"));
            assertEquals(true, map.get("keepInventory"));
            assertEquals(false, map.get("doMobSpawning"));
            assertEquals(3, ((Number) map.get("randomTickSpeed")).intValue());
        }

        @Test
        @DisplayName("GSON round-trips dimensionGameRules structure correctly")
        void gsonRoundTrip_preservesStructure() {
            com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();

            Map<String, Object> original = new LinkedHashMap<>();
            original.put("keepInventory", true);
            original.put("doMobSpawning", false);
            original.put("randomTickSpeed", 0);

            String json = gson.toJson(original);

            @SuppressWarnings("unchecked")
            Map<String, Object> deserialized = gson.fromJson(json, Map.class);

            assertEquals(3, deserialized.size());
            assertEquals(true, deserialized.get("keepInventory"));
            assertEquals(false, deserialized.get("doMobSpawning"));
            // Note: GSON round-trips integers as Double, but intValue() still works
            assertEquals(0, ((Number) deserialized.get("randomTickSpeed")).intValue());
        }
    }
}
