package com.wickedsik.personalworlds.player;

//? if >=1.20.2 {
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ReturnData record.
 * Tests record construction and NBT serialization for player return positions.
 *
 * Note: These tests require Minecraft's registry to be initialized, which works
 * in 1.20.2+ but fails in 1.20.1 without full game bootstrap. Therefore, these
 * tests are conditionally compiled for 1.20.2+ only.
 */
class ReturnDataTest {

    private RegistryKey<World> testDimension;
    private BlockPos testPosition;
    private float testYaw;
    private float testPitch;

    @BeforeEach
    void setUp() {
        testDimension = RegistryKey.of(RegistryKeys.WORLD, new Identifier("minecraft", "overworld"));
        testPosition = new BlockPos(100, 65, -200);
        testYaw = 90.0f;
        testPitch = -15.0f;
    }

    @Nested
    @DisplayName("Record Construction")
    class Construction {

        @Test
        @DisplayName("Constructor with all fields creates valid record")
        void constructor_allFields_createsRecord() {
            ReturnData data = new ReturnData(testDimension, testPosition, testYaw, testPitch);

            assertNotNull(data);
            assertEquals(testDimension, data.dimension());
            assertEquals(testPosition, data.position());
            assertEquals(testYaw, data.yaw());
            assertEquals(testPitch, data.pitch());
        }

        @Test
        @DisplayName("Record stores negative coordinates")
        void constructor_negativeCoords_stores() {
            BlockPos negative = new BlockPos(-500, -60, -1000);
            ReturnData data = new ReturnData(testDimension, negative, testYaw, testPitch);

            assertEquals(negative, data.position());
        }

        @Test
        @DisplayName("Record stores extreme yaw values")
        void constructor_extremeYaw_stores() {
            ReturnData data = new ReturnData(testDimension, testPosition, 359.9f, testPitch);

            assertEquals(359.9f, data.yaw(), 0.001f);
        }

        @Test
        @DisplayName("Record stores extreme pitch values")
        void constructor_extremePitch_stores() {
            ReturnData data = new ReturnData(testDimension, testPosition, testYaw, -89.9f);

            assertEquals(-89.9f, data.pitch(), 0.001f);
        }
    }

    @Nested
    @DisplayName("NBT Serialization")
    class NbtSerialization {

        @Test
        @DisplayName("toNbt creates compound with all fields")
        void toNbt_validData_createsCompound() {
            ReturnData data = new ReturnData(testDimension, testPosition, testYaw, testPitch);

            NbtCompound nbt = data.toNbt();

            assertNotNull(nbt);
            assertTrue(nbt.contains("Dimension"));
            assertTrue(nbt.contains("X"));
            assertTrue(nbt.contains("Y"));
            assertTrue(nbt.contains("Z"));
            assertTrue(nbt.contains("Yaw"));
            assertTrue(nbt.contains("Pitch"));
        }

        @Test
        @DisplayName("toNbt stores correct dimension string")
        void toNbt_correctDimension() {
            ReturnData data = new ReturnData(testDimension, testPosition, testYaw, testPitch);

            NbtCompound nbt = data.toNbt();

            assertEquals("minecraft:overworld", nbt.getString("Dimension"));
        }

        @Test
        @DisplayName("toNbt stores correct coordinates")
        void toNbt_correctCoordinates() {
            ReturnData data = new ReturnData(testDimension, testPosition, testYaw, testPitch);

            NbtCompound nbt = data.toNbt();

            assertEquals(100, nbt.getInt("X"));
            assertEquals(65, nbt.getInt("Y"));
            assertEquals(-200, nbt.getInt("Z"));
        }

        @Test
        @DisplayName("toNbt stores correct rotation")
        void toNbt_correctRotation() {
            ReturnData data = new ReturnData(testDimension, testPosition, testYaw, testPitch);

            NbtCompound nbt = data.toNbt();

            assertEquals(90.0f, nbt.getFloat("Yaw"), 0.001f);
            assertEquals(-15.0f, nbt.getFloat("Pitch"), 0.001f);
        }

        @Test
        @DisplayName("fromNbt with valid compound creates record")
        void fromNbt_validCompound_createsRecord() {
            NbtCompound nbt = new NbtCompound();
            nbt.putString("Dimension", "minecraft:overworld");
            nbt.putInt("X", 100);
            nbt.putInt("Y", 65);
            nbt.putInt("Z", -200);
            nbt.putFloat("Yaw", 90.0f);
            nbt.putFloat("Pitch", -15.0f);

            ReturnData data = ReturnData.fromNbt(nbt);

            assertNotNull(data);
            assertEquals("minecraft", data.dimension().getValue().getNamespace());
            assertEquals("overworld", data.dimension().getValue().getPath());
            assertEquals(new BlockPos(100, 65, -200), data.position());
            assertEquals(90.0f, data.yaw(), 0.001f);
            assertEquals(-15.0f, data.pitch(), 0.001f);
        }

        @Test
        @DisplayName("Round-trip preserves all fields")
        void roundTrip_preservesAllFields() {
            ReturnData original = new ReturnData(testDimension, testPosition, testYaw, testPitch);

            NbtCompound nbt = original.toNbt();
            ReturnData restored = ReturnData.fromNbt(nbt);

            assertEquals(original.dimension().getValue(), restored.dimension().getValue());
            assertEquals(original.position(), restored.position());
            assertEquals(original.yaw(), restored.yaw(), 0.001f);
            assertEquals(original.pitch(), restored.pitch(), 0.001f);
        }

        @Test
        @DisplayName("Round-trip with negative coordinates")
        void roundTrip_negativeCoordinates() {
            BlockPos negative = new BlockPos(-500, -60, -1000);
            ReturnData original = new ReturnData(testDimension, negative, testYaw, testPitch);

            NbtCompound nbt = original.toNbt();
            ReturnData restored = ReturnData.fromNbt(nbt);

            assertEquals(negative, restored.position());
        }

        @Test
        @DisplayName("Round-trip with extreme rotation")
        void roundTrip_extremeRotation() {
            ReturnData original = new ReturnData(testDimension, testPosition, 359.9f, -89.9f);

            NbtCompound nbt = original.toNbt();
            ReturnData restored = ReturnData.fromNbt(nbt);

            assertEquals(359.9f, restored.yaw(), 0.001f);
            assertEquals(-89.9f, restored.pitch(), 0.001f);
        }
    }

    @Nested
    @DisplayName("Dimension Keys")
    class DimensionKeys {

        @Test
        @DisplayName("Overworld dimension key serializes correctly")
        void overworldDimension_serializesCorrectly() {
            RegistryKey<World> overworld = RegistryKey.of(RegistryKeys.WORLD, new Identifier("minecraft", "overworld"));
            ReturnData data = new ReturnData(overworld, testPosition, testYaw, testPitch);

            NbtCompound nbt = data.toNbt();

            assertEquals("minecraft:overworld", nbt.getString("Dimension"));
        }

        @Test
        @DisplayName("Nether dimension key serializes correctly")
        void netherDimension_serializesCorrectly() {
            RegistryKey<World> nether = RegistryKey.of(RegistryKeys.WORLD, new Identifier("minecraft", "the_nether"));
            ReturnData data = new ReturnData(nether, testPosition, testYaw, testPitch);

            NbtCompound nbt = data.toNbt();

            assertEquals("minecraft:the_nether", nbt.getString("Dimension"));
        }

        @Test
        @DisplayName("End dimension key serializes correctly")
        void endDimension_serializesCorrectly() {
            RegistryKey<World> end = RegistryKey.of(RegistryKeys.WORLD, new Identifier("minecraft", "the_end"));
            ReturnData data = new ReturnData(end, testPosition, testYaw, testPitch);

            NbtCompound nbt = data.toNbt();

            assertEquals("minecraft:the_end", nbt.getString("Dimension"));
        }

        @Test
        @DisplayName("Custom dimension key serializes correctly")
        void customDimension_serializesCorrectly() {
            RegistryKey<World> custom = RegistryKey.of(RegistryKeys.WORLD, new Identifier("personalworlds", "pw_test"));
            ReturnData data = new ReturnData(custom, testPosition, testYaw, testPitch);

            NbtCompound nbt = data.toNbt();
            ReturnData restored = ReturnData.fromNbt(nbt);

            assertEquals("personalworlds", restored.dimension().getValue().getNamespace());
            assertEquals("pw_test", restored.dimension().getValue().getPath());
        }

        @Test
        @DisplayName("Dimension with underscores serializes correctly")
        void dimensionWithUnderscores_serializesCorrectly() {
            RegistryKey<World> custom = RegistryKey.of(RegistryKeys.WORLD,
                new Identifier("my_mod", "my_cool_dimension"));
            ReturnData data = new ReturnData(custom, testPosition, testYaw, testPitch);

            NbtCompound nbt = data.toNbt();
            ReturnData restored = ReturnData.fromNbt(nbt);

            assertEquals("my_mod:my_cool_dimension", nbt.getString("Dimension"));
            assertEquals("my_mod", restored.dimension().getValue().getNamespace());
            assertEquals("my_cool_dimension", restored.dimension().getValue().getPath());
        }
    }

    @Nested
    @DisplayName("Coordinate Edge Cases")
    class CoordinateEdgeCases {

        @Test
        @DisplayName("Zero coordinates work")
        void zeroCoordinates_work() {
            BlockPos zero = new BlockPos(0, 0, 0);
            ReturnData original = new ReturnData(testDimension, zero, 0f, 0f);

            NbtCompound nbt = original.toNbt();
            ReturnData restored = ReturnData.fromNbt(nbt);

            assertEquals(zero, restored.position());
            assertEquals(0f, restored.yaw());
            assertEquals(0f, restored.pitch());
        }

        @Test
        @DisplayName("Maximum Y coordinate works")
        void maxY_works() {
            BlockPos maxY = new BlockPos(0, 320, 0);
            ReturnData original = new ReturnData(testDimension, maxY, testYaw, testPitch);

            NbtCompound nbt = original.toNbt();
            ReturnData restored = ReturnData.fromNbt(nbt);

            assertEquals(320, restored.position().getY());
        }

        @Test
        @DisplayName("Minimum Y coordinate works")
        void minY_works() {
            BlockPos minY = new BlockPos(0, -64, 0);
            ReturnData original = new ReturnData(testDimension, minY, testYaw, testPitch);

            NbtCompound nbt = original.toNbt();
            ReturnData restored = ReturnData.fromNbt(nbt);

            assertEquals(-64, restored.position().getY());
        }

        @Test
        @DisplayName("Large coordinates work")
        void largeCoordinates_work() {
            BlockPos large = new BlockPos(10_000_000, 100, -10_000_000);
            ReturnData original = new ReturnData(testDimension, large, testYaw, testPitch);

            NbtCompound nbt = original.toNbt();
            ReturnData restored = ReturnData.fromNbt(nbt);

            assertEquals(large, restored.position());
        }
    }

    @Nested
    @DisplayName("Rotation Edge Cases")
    class RotationEdgeCases {

        @Test
        @DisplayName("Negative yaw works")
        void negativeYaw_works() {
            ReturnData original = new ReturnData(testDimension, testPosition, -90.0f, testPitch);

            NbtCompound nbt = original.toNbt();
            ReturnData restored = ReturnData.fromNbt(nbt);

            assertEquals(-90.0f, restored.yaw(), 0.001f);
        }

        @Test
        @DisplayName("Yaw beyond 360 works")
        void yawBeyond360_works() {
            ReturnData original = new ReturnData(testDimension, testPosition, 450.0f, testPitch);

            NbtCompound nbt = original.toNbt();
            ReturnData restored = ReturnData.fromNbt(nbt);

            assertEquals(450.0f, restored.yaw(), 0.001f);
        }

        @Test
        @DisplayName("Maximum pitch (+90) works")
        void maxPitch_works() {
            ReturnData original = new ReturnData(testDimension, testPosition, testYaw, 90.0f);

            NbtCompound nbt = original.toNbt();
            ReturnData restored = ReturnData.fromNbt(nbt);

            assertEquals(90.0f, restored.pitch(), 0.001f);
        }

        @Test
        @DisplayName("Minimum pitch (-90) works")
        void minPitch_works() {
            ReturnData original = new ReturnData(testDimension, testPosition, testYaw, -90.0f);

            NbtCompound nbt = original.toNbt();
            ReturnData restored = ReturnData.fromNbt(nbt);

            assertEquals(-90.0f, restored.pitch(), 0.001f);
        }
    }

    @Nested
    @DisplayName("Record Equality")
    class RecordEquality {

        @Test
        @DisplayName("Same values are equal")
        void equals_sameValues_equal() {
            ReturnData data1 = new ReturnData(testDimension, testPosition, testYaw, testPitch);
            ReturnData data2 = new ReturnData(testDimension, testPosition, testYaw, testPitch);

            assertEquals(data1, data2);
            assertEquals(data1.hashCode(), data2.hashCode());
        }

        @Test
        @DisplayName("Different position not equal")
        void equals_differentPosition_notEqual() {
            ReturnData data1 = new ReturnData(testDimension, testPosition, testYaw, testPitch);
            ReturnData data2 = new ReturnData(testDimension, new BlockPos(0, 0, 0), testYaw, testPitch);

            assertNotEquals(data1, data2);
        }

        @Test
        @DisplayName("Different dimension not equal")
        void equals_differentDimension_notEqual() {
            RegistryKey<World> other = RegistryKey.of(RegistryKeys.WORLD, new Identifier("minecraft", "the_nether"));
            ReturnData data1 = new ReturnData(testDimension, testPosition, testYaw, testPitch);
            ReturnData data2 = new ReturnData(other, testPosition, testYaw, testPitch);

            assertNotEquals(data1, data2);
        }

        @Test
        @DisplayName("Different yaw not equal")
        void equals_differentYaw_notEqual() {
            ReturnData data1 = new ReturnData(testDimension, testPosition, testYaw, testPitch);
            ReturnData data2 = new ReturnData(testDimension, testPosition, testYaw + 1.0f, testPitch);

            assertNotEquals(data1, data2);
        }
    }
}
//?}
