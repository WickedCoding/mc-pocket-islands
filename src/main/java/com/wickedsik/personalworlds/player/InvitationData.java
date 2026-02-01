package com.wickedsik.personalworlds.player;

import net.minecraft.nbt.NbtCompound;

import java.util.UUID;

/**
 * Represents a single invitation from a dimension owner to a guest.
 * Stored in PlayerDataManager for persistence.
 *
 * @param ownerUuid The UUID of the dimension owner who sent the invitation
 * @param ownerName Display name of the owner (for UI messages)
 * @param invitedAt Timestamp when the invitation was created
 * @param alwaysWelcome If true, guest can visit even when host is offline/away
 */
public record InvitationData(
    UUID ownerUuid,
    String ownerName,
    long invitedAt,
    boolean alwaysWelcome
) {

    /**
     * Create a standard invitation (not always welcome).
     * Convenience constructor for backward compatibility.
     */
    public InvitationData(UUID ownerUuid, String ownerName, long invitedAt) {
        this(ownerUuid, ownerName, invitedAt, false);
    }

    /**
     * Create a copy of this invitation with the alwaysWelcome flag toggled.
     *
     * @return New InvitationData with toggled alwaysWelcome value
     */
    public InvitationData withToggledAlwaysWelcome() {
        return new InvitationData(ownerUuid, ownerName, invitedAt, !alwaysWelcome);
    }

    /**
     * Serialize this invitation to NBT.
     *
     * @return NBT compound containing invitation data
     */
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        com.wickedsik.personalworlds.compat.NbtCompat.putUuid(nbt, "OwnerUuid", ownerUuid);
        nbt.putString("OwnerName", ownerName);
        nbt.putLong("InvitedAt", invitedAt);
        nbt.putBoolean("AlwaysWelcome", alwaysWelcome);
        return nbt;
    }

    /**
     * Deserialize invitation data from NBT.
     * Backward compatible: old invitations without AlwaysWelcome default to false.
     *
     * @param nbt NBT compound containing invitation data
     * @return Deserialized InvitationData
     */
    public static InvitationData fromNbt(NbtCompound nbt) {
        // Backward compatible: default to false if field doesn't exist
        boolean alwaysWelcome = com.wickedsik.personalworlds.compat.NbtCompat.getBoolean(nbt, "AlwaysWelcome", false);

        return new InvitationData(
            com.wickedsik.personalworlds.compat.NbtCompat.getUuid(nbt, "OwnerUuid"),
            com.wickedsik.personalworlds.compat.NbtCompat.getString(nbt, "OwnerName", "Unknown"),
            com.wickedsik.personalworlds.compat.NbtCompat.getLong(nbt, "InvitedAt", 0L),
            alwaysWelcome
        );
    }
}
