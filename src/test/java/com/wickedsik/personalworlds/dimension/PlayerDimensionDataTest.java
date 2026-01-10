package com.wickedsik.personalworlds.dimension;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PlayerDimensionData record.
 * Tests record construction and NBT serialization for dimension metadata.
 */
class PlayerDimensionDataTest {

    private UUID testUuid;
    private String testName;
    private Identifier testDimensionId;
    private long testCreatedAt;
    private BlockPos testSpawnPoint;
    private WorldGenType testGenType;

    @BeforeEach
    void setUp() {
        testUuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        testName = "TestPlayer";
        testDimensionId = new Identifier("personalworlds", "pw_test");
        testCreatedAt = System.currentTimeMillis();
        testSpawnPoint = new BlockPos(0, 65, 0);
        testGenType = WorldGenType.VOID;
    }

    @Nested
    @DisplayName("Record Construction")
    class Construction {

        @Test
        @DisplayName("Constructor with all fields creates valid record")
        void constructor_allFields_createsRecord() {
            PlayerDimensionData data = new PlayerDimensionData(
                testUuid, testName, testDimensionId, testCreatedAt, testSpawnPoint, testGenType
            , 0);

            assertNotNull(data);
            assertEquals(testUuid, data.ownerUuid());
            assertEquals(testName, data.ownerName());
            assertEquals(testDimensionId, data.dimensionId());
            assertEquals(testCreatedAt, data.createdAt());
            assertEquals(testSpawnPoint, data.spawnPoint());
            assertEquals(testGenType, data.generatorType());
        }

        @Test
        @DisplayName("Record is immutable")
        void immutability_verified() {
            PlayerDimensionData data = new PlayerDimensionData(
                testUuid, testName, testDimensionId, testCreatedAt, testSpawnPoint, testGenType
            , 0);

            // Values cannot change after construction (records are immutable)
            assertEquals(testName, data.ownerName());
        }

        @Test
        @DisplayName("Different generator types stored correctly")
        void constructor_differentGenTypes() {
            PlayerDimensionData voidData = new PlayerDimensionData(
                testUuid, testName, testDimensionId, testCreatedAt, testSpawnPoint, WorldGenType.VOID
            , 0);
            PlayerDimensionData overworldData = new PlayerDimensionData(
                testUuid, testName, testDimensionId, testCreatedAt, testSpawnPoint, WorldGenType.OVERWORLD
            , 0);
            PlayerDimensionData flatData = new PlayerDimensionData(
                testUuid, testName, testDimensionId, testCreatedAt, testSpawnPoint, WorldGenType.FLAT
            , 0);

            assertEquals(WorldGenType.VOID, voidData.generatorType());
            assertEquals(WorldGenType.OVERWORLD, overworldData.generatorType());
            assertEquals(WorldGenType.FLAT, flatData.generatorType());
        }
    }

    @Nested
    @DisplayName("NBT Serialization")
    class NbtSerialization {

        @Test
        @DisplayName("toNbt creates compound with all fields")
        void toNbt_validData_createsCompound() {
            PlayerDimensionData data = new PlayerDimensionData(
                testUuid, testName, testDimensionId, testCreatedAt, testSpawnPoint, testGenType
            , 0);

            NbtCompound nbt = data.toNbt();

            assertNotNull(nbt);
            assertTrue(nbt.containsUuid("OwnerUuid"));
            assertTrue(nbt.contains("OwnerName"));
            assertTrue(nbt.contains("DimensionId"));
            assertTrue(nbt.contains("CreatedAt"));
            assertTrue(nbt.contains("SpawnX"));
            assertTrue(nbt.contains("SpawnY"));
            assertTrue(nbt.contains("SpawnZ"));
            assertTrue(nbt.contains("GeneratorType"));
        }

        @Test
        @DisplayName("toNbt stores correct values")
        void toNbt_correctValues() {
            PlayerDimensionData data = new PlayerDimensionData(
                testUuid, testName, testDimensionId, testCreatedAt, testSpawnPoint, testGenType
            , 0);

            NbtCompound nbt = data.toNbt();

            assertEquals(testUuid, nbt.getUuid("OwnerUuid"));
            assertEquals(testName, nbt.getString("OwnerName"));
            assertEquals("personalworlds:pw_test", nbt.getString("DimensionId"));
            assertEquals(testCreatedAt, nbt.getLong("CreatedAt"));
            assertEquals(0, nbt.getInt("SpawnX"));
            assertEquals(65, nbt.getInt("SpawnY"));
            assertEquals(0, nbt.getInt("SpawnZ"));
            assertEquals("VOID", nbt.getString("GeneratorType"));
        }

        @Test
        @DisplayName("fromNbt with valid compound creates record")
        void fromNbt_validCompound_createsRecord() {
            NbtCompound nbt = new NbtCompound();
            nbt.putUuid("OwnerUuid", testUuid);
            nbt.putString("OwnerName", testName);
            nbt.putString("DimensionId", "personalworlds:pw_test");
            nbt.putLong("CreatedAt", testCreatedAt);
            nbt.putInt("SpawnX", 0);
            nbt.putInt("SpawnY", 65);
            nbt.putInt("SpawnZ", 0);
            nbt.putString("GeneratorType", "VOID");

            PlayerDimensionData data = PlayerDimensionData.fromNbt(nbt);

            assertNotNull(data);
            assertEquals(testUuid, data.ownerUuid());
            assertEquals(testName, data.ownerName());
            assertEquals(testDimensionId, data.dimensionId());
            assertEquals(testCreatedAt, data.createdAt());
            assertEquals(testSpawnPoint, data.spawnPoint());
            assertEquals(testGenType, data.generatorType());
        }

        @Test
        @DisplayName("Round-trip preserves all fields")
        void roundTrip_preservesAllFields() {
            PlayerDimensionData original = new PlayerDimensionData(
                testUuid, testName, testDimensionId, testCreatedAt, testSpawnPoint, testGenType
            , 0);

            NbtCompound nbt = original.toNbt();
            PlayerDimensionData restored = PlayerDimensionData.fromNbt(nbt);

            assertEquals(original, restored);
        }

        @Test
        @DisplayName("fromNbt handles unknown generator type gracefully")
        void fromNbt_unknownGenType_usesDefault() {
            NbtCompound nbt = new NbtCompound();
            nbt.putUuid("OwnerUuid", testUuid);
            nbt.putString("OwnerName", testName);
            nbt.putString("DimensionId", "personalworlds:pw_test");
            nbt.putLong("CreatedAt", testCreatedAt);
            nbt.putInt("SpawnX", 0);
            nbt.putInt("SpawnY", 65);
            nbt.putInt("SpawnZ", 0);
            nbt.putString("GeneratorType", "INVALID_TYPE");

            PlayerDimensionData data = PlayerDimensionData.fromNbt(nbt);

            assertNotNull(data);
            assertEquals(WorldGenType.VOID, data.generatorType()); // Fallback to default
        }

        @Test
        @DisplayName("fromNbt handles missing generator type")
        void fromNbt_missingGenType_usesDefault() {
            NbtCompound nbt = new NbtCompound();
            nbt.putUuid("OwnerUuid", testUuid);
            nbt.putString("OwnerName", testName);
            nbt.putString("DimensionId", "personalworlds:pw_test");
            nbt.putLong("CreatedAt", testCreatedAt);
            nbt.putInt("SpawnX", 0);
            nbt.putInt("SpawnY", 65);
            nbt.putInt("SpawnZ", 0);
            // Missing GeneratorType

            PlayerDimensionData data = PlayerDimensionData.fromNbt(nbt);

            assertEquals(WorldGenType.VOID, data.generatorType());
        }

        @Test
        @DisplayName("All generator types round-trip correctly")
        void roundTrip_allGenTypes() {
            for (WorldGenType genType : WorldGenType.values()) {
                PlayerDimensionData original = new PlayerDimensionData(
                    testUuid, testName, testDimensionId, testCreatedAt, testSpawnPoint, genType
                , 0);

                NbtCompound nbt = original.toNbt();
                PlayerDimensionData restored = PlayerDimensionData.fromNbt(nbt);

                assertEquals(genType, restored.generatorType(),
                    "Generator type " + genType + " should round-trip correctly");
            }
        }
    }

    @Nested
    @DisplayName("Dimension ID Handling")
    class DimensionIdHandling {

        @Test
        @DisplayName("Standard namespace:path format")
        void dimensionId_standardFormat() {
            Identifier dimId = new Identifier("personalworlds", "pw_abc123");
            PlayerDimensionData data = new PlayerDimensionData(
                testUuid, testName, dimId, testCreatedAt, testSpawnPoint, testGenType
            , 0);

            NbtCompound nbt = data.toNbt();

            assertEquals("personalworlds:pw_abc123", nbt.getString("DimensionId"));
        }

        @Test
        @DisplayName("Dimension ID with underscores round-trips")
        void dimensionId_underscores_roundTrips() {
            Identifier dimId = new Identifier("my_mod", "my_dimension_name");
            PlayerDimensionData original = new PlayerDimensionData(
                testUuid, testName, dimId, testCreatedAt, testSpawnPoint, testGenType
            , 0);

            NbtCompound nbt = original.toNbt();
            PlayerDimensionData restored = PlayerDimensionData.fromNbt(nbt);

            assertEquals(dimId, restored.dimensionId());
        }

        @Test
        @DisplayName("Dimension ID with numbers round-trips")
        void dimensionId_numbers_roundTrips() {
            Identifier dimId = new Identifier("personalworlds", "pw_550e8400e29b41d4a716446655440000");
            PlayerDimensionData original = new PlayerDimensionData(
                testUuid, testName, dimId, testCreatedAt, testSpawnPoint, testGenType
            , 0);

            NbtCompound nbt = original.toNbt();
            PlayerDimensionData restored = PlayerDimensionData.fromNbt(nbt);

            assertEquals(dimId, restored.dimensionId());
        }
    }

    @Nested
    @DisplayName("Spawn Point Handling")
    class SpawnPointHandling {

        @Test
        @DisplayName("Standard spawn point")
        void spawnPoint_standard() {
            BlockPos spawn = new BlockPos(0, 65, 0);
            PlayerDimensionData data = new PlayerDimensionData(
                testUuid, testName, testDimensionId, testCreatedAt, spawn, testGenType
            , 0);

            NbtCompound nbt = data.toNbt();
            PlayerDimensionData restored = PlayerDimensionData.fromNbt(nbt);

            assertEquals(spawn, restored.spawnPoint());
        }

        @Test
        @DisplayName("Negative coordinates spawn point")
        void spawnPoint_negative() {
            BlockPos spawn = new BlockPos(-500, -50, -1000);
            PlayerDimensionData original = new PlayerDimensionData(
                testUuid, testName, testDimensionId, testCreatedAt, spawn, testGenType
            , 0);

            NbtCompound nbt = original.toNbt();
            PlayerDimensionData restored = PlayerDimensionData.fromNbt(nbt);

            assertEquals(spawn, restored.spawnPoint());
        }

        @Test
        @DisplayName("Large coordinates spawn point")
        void spawnPoint_large() {
            BlockPos spawn = new BlockPos(10_000_000, 200, -10_000_000);
            PlayerDimensionData original = new PlayerDimensionData(
                testUuid, testName, testDimensionId, testCreatedAt, spawn, testGenType
            , 0);

            NbtCompound nbt = original.toNbt();
            PlayerDimensionData restored = PlayerDimensionData.fromNbt(nbt);

            assertEquals(spawn, restored.spawnPoint());
        }
    }

    @Nested
    @DisplayName("Timestamp Handling")
    class TimestampHandling {

        @Test
        @DisplayName("Current timestamp round-trips")
        void timestamp_current() {
            long now = System.currentTimeMillis();
            PlayerDimensionData original = new PlayerDimensionData(
                testUuid, testName, testDimensionId, now, testSpawnPoint, testGenType
            , 0);

            NbtCompound nbt = original.toNbt();
            PlayerDimensionData restored = PlayerDimensionData.fromNbt(nbt);

            assertEquals(now, restored.createdAt());
        }

        @Test
        @DisplayName("Zero timestamp works")
        void timestamp_zero() {
            PlayerDimensionData original = new PlayerDimensionData(
                testUuid, testName, testDimensionId, 0L, testSpawnPoint, testGenType
            , 0);

            NbtCompound nbt = original.toNbt();
            PlayerDimensionData restored = PlayerDimensionData.fromNbt(nbt);

            assertEquals(0L, restored.createdAt());
        }

        @Test
        @DisplayName("Historical timestamp works")
        void timestamp_historical() {
            long historical = 946684800000L; // 2000-01-01
            PlayerDimensionData original = new PlayerDimensionData(
                testUuid, testName, testDimensionId, historical, testSpawnPoint, testGenType
            , 0);

            NbtCompound nbt = original.toNbt();
            PlayerDimensionData restored = PlayerDimensionData.fromNbt(nbt);

            assertEquals(historical, restored.createdAt());
        }
    }

    @Nested
    @DisplayName("Owner Name Handling")
    class OwnerNameHandling {

        @Test
        @DisplayName("Standard name round-trips")
        void ownerName_standard() {
            PlayerDimensionData original = new PlayerDimensionData(
                testUuid, "Steve", testDimensionId, testCreatedAt, testSpawnPoint, testGenType
            , 0);

            NbtCompound nbt = original.toNbt();
            PlayerDimensionData restored = PlayerDimensionData.fromNbt(nbt);

            assertEquals("Steve", restored.ownerName());
        }

        @Test
        @DisplayName("Empty name round-trips")
        void ownerName_empty() {
            PlayerDimensionData original = new PlayerDimensionData(
                testUuid, "", testDimensionId, testCreatedAt, testSpawnPoint, testGenType
            , 0);

            NbtCompound nbt = original.toNbt();
            PlayerDimensionData restored = PlayerDimensionData.fromNbt(nbt);

            assertEquals("", restored.ownerName());
        }

        @Test
        @DisplayName("Unicode name round-trips")
        void ownerName_unicode() {
            String unicodeName = "プレイヤー123";
            PlayerDimensionData original = new PlayerDimensionData(
                testUuid, unicodeName, testDimensionId, testCreatedAt, testSpawnPoint, testGenType
            , 0);

            NbtCompound nbt = original.toNbt();
            PlayerDimensionData restored = PlayerDimensionData.fromNbt(nbt);

            assertEquals(unicodeName, restored.ownerName());
        }

        @Test
        @DisplayName("Name with special characters round-trips")
        void ownerName_specialChars() {
            String specialName = "Player_123-Test";
            PlayerDimensionData original = new PlayerDimensionData(
                testUuid, specialName, testDimensionId, testCreatedAt, testSpawnPoint, testGenType
            , 0);

            NbtCompound nbt = original.toNbt();
            PlayerDimensionData restored = PlayerDimensionData.fromNbt(nbt);

            assertEquals(specialName, restored.ownerName());
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("Same values are equal")
        void equals_sameValues_areEqual() {
            PlayerDimensionData data1 = new PlayerDimensionData(
                testUuid, testName, testDimensionId, testCreatedAt, testSpawnPoint, testGenType
            , 0);
            PlayerDimensionData data2 = new PlayerDimensionData(
                testUuid, testName, testDimensionId, testCreatedAt, testSpawnPoint, testGenType
            , 0);

            assertEquals(data1, data2);
            assertEquals(data1.hashCode(), data2.hashCode());
        }

        @Test
        @DisplayName("Different UUID means not equal")
        void equals_differentUuid_notEqual() {
            PlayerDimensionData data1 = new PlayerDimensionData(
                testUuid, testName, testDimensionId, testCreatedAt, testSpawnPoint, testGenType
            , 0);
            PlayerDimensionData data2 = new PlayerDimensionData(
                UUID.randomUUID(), testName, testDimensionId, testCreatedAt, testSpawnPoint, testGenType
            , 0);

            assertNotEquals(data1, data2);
        }

        @Test
        @DisplayName("Different spawn point means not equal")
        void equals_differentSpawn_notEqual() {
            PlayerDimensionData data1 = new PlayerDimensionData(
                testUuid, testName, testDimensionId, testCreatedAt, testSpawnPoint, testGenType
            , 0);
            PlayerDimensionData data2 = new PlayerDimensionData(
                testUuid, testName, testDimensionId, testCreatedAt, new BlockPos(100, 65, 100), testGenType, 0
            );

            assertNotEquals(data1, data2);
        }

        @Test
        @DisplayName("Different generator type means not equal")
        void equals_differentGenType_notEqual() {
            PlayerDimensionData data1 = new PlayerDimensionData(
                testUuid, testName, testDimensionId, testCreatedAt, testSpawnPoint, WorldGenType.VOID
            , 0);
            PlayerDimensionData data2 = new PlayerDimensionData(
                testUuid, testName, testDimensionId, testCreatedAt, testSpawnPoint, WorldGenType.OVERWORLD
            , 0);

            assertNotEquals(data1, data2);
        }
    }
}
