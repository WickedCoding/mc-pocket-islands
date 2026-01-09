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
 */
public record InvitationData(
    UUID ownerUuid,
    String ownerName,
    long invitedAt
) {

    /**
     * Serialize this invitation to NBT.
     *
     * @return NBT compound containing invitation data
     */
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putUuid("OwnerUuid", ownerUuid);
        nbt.putString("OwnerName", ownerName);
        nbt.putLong("InvitedAt", invitedAt);
        return nbt;
    }

    /**
     * Deserialize invitation data from NBT.
     *
     * @param nbt NBT compound containing invitation data
     * @return Deserialized InvitationData
     */
    public static InvitationData fromNbt(NbtCompound nbt) {
        return new InvitationData(
            nbt.getUuid("OwnerUuid"),
            nbt.getString("OwnerName"),
            nbt.getLong("InvitedAt")
        );
    }
}
