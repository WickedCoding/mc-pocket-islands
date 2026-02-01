package com.wickedsik.personalworlds.util;

import com.wickedsik.personalworlds.compat.EntityCompat;
import com.wickedsik.personalworlds.config.ModConfig;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Centralized visual and audio effects for the PersonalWorlds mod.
 *
 * All effects check the corresponding config flags before playing.
 * This provides a single point for managing all feedback effects.
 */
public final class VisualEffects {

    // ==================== Teleportation Effects ====================

    /**
     * Play departure effects when a player teleports away.
     * Spawns portal particles and plays teleport sound at the departure location.
     *
     * @param player The player teleporting
     */
    public static void playTeleportDepartureEffects(ServerPlayerEntity player) {
        ModConfig config = ModConfig.get();
        ServerWorld world = EntityCompat.getServerWorld(player);
        Vec3d pos = EntityCompat.getPos(player);

        if (config.enableTeleportParticles) {
            // Spawn portal particles at departure location
            world.spawnParticles(
                ParticleTypes.PORTAL,
                pos.x, pos.y + 1, pos.z,
                50,           // count
                0.5, 1.0, 0.5, // spread (x, y, z)
                0.1           // speed
            );
        }

        if (config.enableTeleportSounds) {
            world.playSound(
                null,
                player.getBlockPos(),
                SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                SoundCategory.PLAYERS,
                1.0f,
                1.0f
            );
        }
    }

    /**
     * Play arrival effects when a player arrives at destination.
     * Spawns reverse portal particles and plays teleport sound.
     *
     * @param player The player who just teleported
     */
    public static void playTeleportArrivalEffects(ServerPlayerEntity player) {
        ModConfig config = ModConfig.get();
        ServerWorld world = EntityCompat.getServerWorld(player);
        Vec3d pos = EntityCompat.getPos(player);

        if (config.enableTeleportParticles) {
            // Spawn reverse portal particles at arrival location
            world.spawnParticles(
                ParticleTypes.REVERSE_PORTAL,
                pos.x, pos.y + 1, pos.z,
                30,           // count
                0.5, 1.0, 0.5, // spread (x, y, z)
                0.05          // speed
            );
        }

        if (config.enableTeleportSounds) {
            // Slightly higher pitch for arrival to differentiate
            world.playSound(
                null,
                player.getBlockPos(),
                SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                SoundCategory.PLAYERS,
                0.8f,
                1.2f
            );
        }
    }

    // ==================== Portal Activation Effects ====================

    /**
     * Play effects when a portal is successfully activated.
     * Spawns particles around the portal frame.
     * Note: The portal activation sound is already played in PortalHelper.
     *
     * @param world The world containing the portal
     * @param center The center position of the portal
     */
    public static void playPortalActivationEffects(World world, BlockPos center) {
        if (!ModConfig.get().enablePortalActivationEffects) {
            return;
        }

        if (world instanceof ServerWorld serverWorld) {
            // Spawn end portal particles around the frame
            serverWorld.spawnParticles(
                ParticleTypes.REVERSE_PORTAL,
                center.getX() + 0.5,
                center.getY() + 1.5,
                center.getZ() + 0.5,
                100,          // count
                1.0, 2.0, 1.0, // spread (x, y, z)
                0.1           // speed
            );

            // Add some enchant particles for extra effect
            serverWorld.spawnParticles(
                ParticleTypes.ENCHANT,
                center.getX() + 0.5,
                center.getY() + 2.0,
                center.getZ() + 0.5,
                50,           // count
                1.0, 0.5, 1.0, // spread (x, y, z)
                0.5           // speed
            );
        }
    }

    // ==================== Invitation Effects ====================

    /**
     * Play notification sound when a player receives an invitation.
     *
     * @param guest The player who received the invitation
     */
    public static void playInvitationReceivedEffect(ServerPlayerEntity guest) {
        if (!ModConfig.get().enableInvitationNotifications) {
            return;
        }

        // Pleasant notification sound
        //? if >=1.21 {
        guest.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
        //?} else {
        /*guest.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.5f, 1.2f);
        *///?}
    }

    /**
     * Play warning sound when a player's invitation is revoked
     * (especially when they're about to be ejected).
     *
     * @param guest The player whose invitation was revoked
     */
    public static void playInvitationRevokedEffect(ServerPlayerEntity guest) {
        if (!ModConfig.get().enableInvitationNotifications) {
            return;
        }

        // Warning bass note
        //? if >=1.21 {
        guest.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.7f, 0.5f);
        //?} else {
        /*guest.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.7f, 0.5f);
        *///?}
    }

    /**
     * Play confirmation sound when a player successfully invites someone.
     *
     * @param owner The player who sent the invitation
     */
    public static void playInvitationSentEffect(ServerPlayerEntity owner) {
        if (!ModConfig.get().enableInvitationNotifications) {
            return;
        }

        // Subtle confirmation sound
        //? if >=1.21 {
        owner.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.3f, 1.5f);
        //?} else {
        /*owner.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.3f, 1.5f);
        *///?}
    }

    // ==================== Dimension Entry/Exit Effects ====================

    /**
     * Play ambient effect when entering a personal dimension.
     * Provides audio feedback that the player has arrived somewhere special.
     *
     * @param player The player entering the dimension
     */
    public static void playDimensionEntryEffect(ServerPlayerEntity player) {
        if (!ModConfig.get().enableTeleportSounds) {
            return;
        }

        // Mystical arrival sound
        //? if >=1.21 {
        player.playSound(SoundEvents.BLOCK_BEACON_ACTIVATE, 0.5f, 1.5f);
        //?} else {
        /*player.playSound(SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.AMBIENT, 0.5f, 1.5f);
        *///?}
    }

    /**
     * Play effect when leaving a personal dimension.
     *
     * @param player The player leaving the dimension
     */
    public static void playDimensionExitEffect(ServerPlayerEntity player) {
        if (!ModConfig.get().enableTeleportSounds) {
            return;
        }

        // Subtle deactivation sound
        //? if >=1.21 {
        player.playSound(SoundEvents.BLOCK_BEACON_DEACTIVATE, 0.3f, 1.2f);
        //?} else {
        /*player.playSound(SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.AMBIENT, 0.3f, 1.2f);
        *///?}
    }

    // ==================== Admin Command Effects ====================

    /**
     * Play warning sound for admin destructive commands.
     *
     * @param admin The admin executing the command
     */
    public static void playAdminWarningEffect(ServerPlayerEntity admin) {
        //? if >=1.21 {
        admin.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0f, 0.5f);
        //?} else {
        /*admin.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.MASTER, 1.0f, 0.5f);
        *///?}
    }

    /**
     * Play success sound for admin commands.
     *
     * @param admin The admin who executed the command
     */
    public static void playAdminSuccessEffect(ServerPlayerEntity admin) {
        //? if >=1.21 {
        admin.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 0.3f, 2.0f);
        //?} else {
        /*admin.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 0.3f, 2.0f);
        *///?}
    }

    // Prevent instantiation
    private VisualEffects() {}
}
