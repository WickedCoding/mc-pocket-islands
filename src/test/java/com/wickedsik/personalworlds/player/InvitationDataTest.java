package com.wickedsik.personalworlds.player;

import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InvitationData record.
 * Tests record construction and NBT serialization.
 */
class InvitationDataTest {

    private UUID testOwnerUuid;
    private String testOwnerName;
    private long testInvitedAt;

    @BeforeEach
    void setUp() {
        testOwnerUuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        testOwnerName = "DimensionOwner";
        testInvitedAt = System.currentTimeMillis();
    }

    @Nested
    @DisplayName("Record Construction")
    class Construction {

        @Test
        @DisplayName("Constructor with all fields creates valid record")
        void constructor_allFields_createsRecord() {
            InvitationData data = new InvitationData(testOwnerUuid, testOwnerName, testInvitedAt);

            assertNotNull(data);
            assertEquals(testOwnerUuid, data.ownerUuid());
            assertEquals(testOwnerName, data.ownerName());
            assertEquals(testInvitedAt, data.invitedAt());
        }

        @Test
        @DisplayName("Record is immutable")
        void immutability_verified() {
            InvitationData data = new InvitationData(testOwnerUuid, testOwnerName, testInvitedAt);

            // Values cannot change after construction (records are immutable)
            assertEquals(testOwnerName, data.ownerName());
        }

        @Test
        @DisplayName("Constructor accepts null ownerName")
        void constructor_nullName_allowed() {
            // Records don't validate - null is allowed
            InvitationData data = new InvitationData(testOwnerUuid, null, testInvitedAt);

            assertNull(data.ownerName());
        }

        @Test
        @DisplayName("Constructor accepts null UUID")
        void constructor_nullUuid_allowed() {
            InvitationData data = new InvitationData(null, testOwnerName, testInvitedAt);

            assertNull(data.ownerUuid());
        }
    }

    @Nested
    @DisplayName("NBT Serialization")
    class NbtSerialization {

        @Test
        @DisplayName("toNbt creates compound with all fields")
        void toNbt_validData_createsCompound() {
            InvitationData data = new InvitationData(testOwnerUuid, testOwnerName, testInvitedAt);

            NbtCompound nbt = data.toNbt();

            assertNotNull(nbt);
            assertTrue(nbt.containsUuid("OwnerUuid"));
            assertTrue(nbt.contains("OwnerName"));
            assertTrue(nbt.contains("InvitedAt"));
        }

        @Test
        @DisplayName("toNbt stores correct values")
        void toNbt_correctValues() {
            InvitationData data = new InvitationData(testOwnerUuid, testOwnerName, testInvitedAt);

            NbtCompound nbt = data.toNbt();

            assertEquals(testOwnerUuid, nbt.getUuid("OwnerUuid"));
            assertEquals(testOwnerName, nbt.getString("OwnerName"));
            assertEquals(testInvitedAt, nbt.getLong("InvitedAt"));
        }

        @Test
        @DisplayName("fromNbt with valid compound creates record")
        void fromNbt_validCompound_createsRecord() {
            NbtCompound nbt = new NbtCompound();
            nbt.putUuid("OwnerUuid", testOwnerUuid);
            nbt.putString("OwnerName", testOwnerName);
            nbt.putLong("InvitedAt", testInvitedAt);

            InvitationData data = InvitationData.fromNbt(nbt);

            assertNotNull(data);
            assertEquals(testOwnerUuid, data.ownerUuid());
            assertEquals(testOwnerName, data.ownerName());
            assertEquals(testInvitedAt, data.invitedAt());
        }

        @Test
        @DisplayName("Round-trip preserves all fields")
        void roundTrip_preservesAllFields() {
            InvitationData original = new InvitationData(testOwnerUuid, testOwnerName, testInvitedAt);

            NbtCompound nbt = original.toNbt();
            InvitationData restored = InvitationData.fromNbt(nbt);

            assertEquals(original, restored);
        }

        @Test
        @DisplayName("fromNbt with missing name returns empty string")
        void fromNbt_missingName_returnsEmpty() {
            NbtCompound nbt = new NbtCompound();
            nbt.putUuid("OwnerUuid", testOwnerUuid);
            nbt.putLong("InvitedAt", testInvitedAt);
            // Missing OwnerName

            InvitationData data = InvitationData.fromNbt(nbt);

            assertEquals("", data.ownerName());
        }

        @Test
        @DisplayName("fromNbt with missing timestamp returns 0")
        void fromNbt_missingTimestamp_returnsZero() {
            NbtCompound nbt = new NbtCompound();
            nbt.putUuid("OwnerUuid", testOwnerUuid);
            nbt.putString("OwnerName", testOwnerName);
            // Missing InvitedAt

            InvitationData data = InvitationData.fromNbt(nbt);

            assertEquals(0L, data.invitedAt());
        }
    }

    @Nested
    @DisplayName("Timestamp Handling")
    class TimestampHandling {

        @Test
        @DisplayName("Zero timestamp is valid")
        void zeroTimestamp_isValid() {
            InvitationData data = new InvitationData(testOwnerUuid, testOwnerName, 0L);

            assertEquals(0L, data.invitedAt());
        }

        @Test
        @DisplayName("Negative timestamp is valid")
        void negativeTimestamp_isValid() {
            InvitationData data = new InvitationData(testOwnerUuid, testOwnerName, -1000L);

            assertEquals(-1000L, data.invitedAt());
        }

        @Test
        @DisplayName("Future timestamp is valid")
        void futureTimestamp_isValid() {
            long futureTime = System.currentTimeMillis() + 86400000; // +1 day
            InvitationData data = new InvitationData(testOwnerUuid, testOwnerName, futureTime);

            assertEquals(futureTime, data.invitedAt());
        }

        @Test
        @DisplayName("Timestamp round-trip preserves precision")
        void timestampPrecision_preserved() {
            long preciseTime = 1704067200123L; // Specific millisecond
            InvitationData original = new InvitationData(testOwnerUuid, testOwnerName, preciseTime);

            NbtCompound nbt = original.toNbt();
            InvitationData restored = InvitationData.fromNbt(nbt);

            assertEquals(preciseTime, restored.invitedAt());
        }

        @Test
        @DisplayName("Max long timestamp works")
        void maxLongTimestamp_works() {
            InvitationData data = new InvitationData(testOwnerUuid, testOwnerName, Long.MAX_VALUE);

            NbtCompound nbt = data.toNbt();
            InvitationData restored = InvitationData.fromNbt(nbt);

            assertEquals(Long.MAX_VALUE, restored.invitedAt());
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("Same values are equal")
        void equals_sameValues_areEqual() {
            InvitationData data1 = new InvitationData(testOwnerUuid, testOwnerName, testInvitedAt);
            InvitationData data2 = new InvitationData(testOwnerUuid, testOwnerName, testInvitedAt);

            assertEquals(data1, data2);
            assertEquals(data1.hashCode(), data2.hashCode());
        }

        @Test
        @DisplayName("Different owner UUID means not equal")
        void equals_differentUuid_notEqual() {
            InvitationData data1 = new InvitationData(testOwnerUuid, testOwnerName, testInvitedAt);
            InvitationData data2 = new InvitationData(UUID.randomUUID(), testOwnerName, testInvitedAt);

            assertNotEquals(data1, data2);
        }

        @Test
        @DisplayName("Different owner name means not equal")
        void equals_differentName_notEqual() {
            InvitationData data1 = new InvitationData(testOwnerUuid, testOwnerName, testInvitedAt);
            InvitationData data2 = new InvitationData(testOwnerUuid, "DifferentName", testInvitedAt);

            assertNotEquals(data1, data2);
        }

        @Test
        @DisplayName("Different timestamp means not equal")
        void equals_differentTimestamp_notEqual() {
            InvitationData data1 = new InvitationData(testOwnerUuid, testOwnerName, testInvitedAt);
            InvitationData data2 = new InvitationData(testOwnerUuid, testOwnerName, testInvitedAt + 1000);

            assertNotEquals(data1, data2);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Unicode owner name serializes correctly")
        void unicodeName_serializesCorrectly() {
            String unicodeName = "プレイヤー123";
            InvitationData original = new InvitationData(testOwnerUuid, unicodeName, testInvitedAt);

            NbtCompound nbt = original.toNbt();
            InvitationData restored = InvitationData.fromNbt(nbt);

            assertEquals(unicodeName, restored.ownerName());
        }

        @Test
        @DisplayName("Empty owner name serializes correctly")
        void emptyName_serializesCorrectly() {
            InvitationData original = new InvitationData(testOwnerUuid, "", testInvitedAt);

            NbtCompound nbt = original.toNbt();
            InvitationData restored = InvitationData.fromNbt(nbt);

            assertEquals("", restored.ownerName());
        }

        @Test
        @DisplayName("Very long owner name serializes correctly")
        void longName_serializesCorrectly() {
            String longName = "A".repeat(1000);
            InvitationData original = new InvitationData(testOwnerUuid, longName, testInvitedAt);

            NbtCompound nbt = original.toNbt();
            InvitationData restored = InvitationData.fromNbt(nbt);

            assertEquals(longName, restored.ownerName());
        }

        @Test
        @DisplayName("Special characters in name serialize correctly")
        void specialChars_serializeCorrectly() {
            String specialName = "Player<>\"'&\n\t";
            InvitationData original = new InvitationData(testOwnerUuid, specialName, testInvitedAt);

            NbtCompound nbt = original.toNbt();
            InvitationData restored = InvitationData.fromNbt(nbt);

            assertEquals(specialName, restored.ownerName());
        }
    }
}
