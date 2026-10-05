package com.wickedsik.personalworlds.util;

import com.wickedsik.personalworlds.compat.EntityCompat;
import com.wickedsik.personalworlds.config.ModConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;

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
    public static void playTeleportDepartureEffects(ServerPlayer player) {
        ModConfig config = ModConfig.get();
        ServerLevel world = EntityCompat.getServerWorld(player);
        Vec3 pos = EntityCompat.getPos(player);

        if (config.enableTeleportParticles) {
            // Spawn portal particles at departure location
            world.sendParticles(
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
                player.blockPosition(),
                SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS,
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
    public static void playTeleportArrivalEffects(ServerPlayer player) {
        ModConfig config = ModConfig.get();
        ServerLevel world = EntityCompat.getServerWorld(player);
        Vec3 pos = EntityCompat.getPos(player);

        if (config.enableTeleportParticles) {
            // Spawn reverse portal particles at arrival location
            world.sendParticles(
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
                player.blockPosition(),
                SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS,
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
    public static void playPortalActivationEffects(Level world, BlockPos center) {
        if (!ModConfig.get().enablePortalActivationEffects) {
            return;
        }

        if (world instanceof ServerLevel serverWorld) {
            // Spawn end portal particles around the frame
            serverWorld.sendParticles(
                ParticleTypes.REVERSE_PORTAL,
                center.getX() + 0.5,
                center.getY() + 1.5,
                center.getZ() + 0.5,
                100,          // count
                1.0, 2.0, 1.0, // spread (x, y, z)
                0.1           // speed
            );

            // Add some enchant particles for extra effect
            serverWorld.sendParticles(
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
    public static void playInvitationReceivedEffect(ServerPlayer guest) {
        if (!ModConfig.get().enableInvitationNotifications) {
            return;
        }

        // Pleasant notification sound
        //? if >=1.21 {
        /*guest.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
        *///?} else {
        guest.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.2f);
        //?}
    }

    /**
     * Play warning sound when a player's invitation is revoked
     * (especially when they're about to be ejected).
     *
     * @param guest The player whose invitation was revoked
     */
    public static void playInvitationRevokedEffect(ServerPlayer guest) {
        if (!ModConfig.get().enableInvitationNotifications) {
            return;
        }

        // Warning bass note
        //? if >=1.21 {
        /*guest.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7f, 0.5f);
        *///?} else {
        guest.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.7f, 0.5f);
        //?}
    }

    /**
     * Play confirmation sound when a player successfully invites someone.
     *
     * @param owner The player who sent the invitation
     */
    public static void playInvitationSentEffect(ServerPlayer owner) {
        if (!ModConfig.get().enableInvitationNotifications) {
            return;
        }

        // Subtle confirmation sound
        //? if >=1.21 {
        /*owner.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.3f, 1.5f);
        *///?} else {
        owner.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.3f, 1.5f);
        //?}
    }

    // ==================== Dimension Entry/Exit Effects ====================

    /**
     * Play ambient effect when entering a personal dimension.
     * Provides audio feedback that the player has arrived somewhere special.
     *
     * @param player The player entering the dimension
     */
    public static void playDimensionEntryEffect(ServerPlayer player) {
        if (!ModConfig.get().enableTeleportSounds) {
            return;
        }

        // Mystical arrival sound
        //? if >=1.21 {
        /*player.playSound(SoundEvents.BEACON_ACTIVATE, 0.5f, 1.5f);
        *///?} else {
        player.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.AMBIENT, 0.5f, 1.5f);
        //?}
    }

    /**
     * Play effect when leaving a personal dimension.
     *
     * @param player The player leaving the dimension
     */
    public static void playDimensionExitEffect(ServerPlayer player) {
        if (!ModConfig.get().enableTeleportSounds) {
            return;
        }

        // Subtle deactivation sound
        //? if >=1.21 {
        /*player.playSound(SoundEvents.BEACON_DEACTIVATE, 0.3f, 1.2f);
        *///?} else {
        player.playNotifySound(SoundEvents.BEACON_DEACTIVATE, SoundSource.AMBIENT, 0.3f, 1.2f);
        //?}
    }

    // ==================== Admin Command Effects ====================

    /**
     * Play warning sound for admin destructive commands.
     *
     * @param admin The admin executing the command
     */
    public static void playAdminWarningEffect(ServerPlayer admin) {
        //? if >=1.21 {
        /*admin.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 0.5f);
        *///?} else {
        admin.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.MASTER, 1.0f, 0.5f);
        //?}
    }

    /**
     * Play success sound for admin commands.
     *
     * @param admin The admin who executed the command
     */
    public static void playAdminSuccessEffect(ServerPlayer admin) {
        //? if >=1.21 {
        /*admin.playSound(SoundEvents.PLAYER_LEVELUP, 0.3f, 2.0f);
        *///?} else {
        admin.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.3f, 2.0f);
        //?}
    }

    // Prevent instantiation
    private VisualEffects() {}
}
