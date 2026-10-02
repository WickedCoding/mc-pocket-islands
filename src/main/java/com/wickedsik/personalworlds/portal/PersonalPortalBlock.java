package com.wickedsik.personalworlds.portal;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/**
 * The personal portal block that teleports players to/from their personal dimension.
 *
 * Properties:
 * - AXIS: Horizontal axis (X or Z) for portal orientation
 * - Non-collidable: Entities pass through
 * - Light level 11: Emits moderate light
 * - Unbreakable by hand: Cannot be mined
 *
 * Behavior:
 * - onEntityCollision: Triggers teleportation for players
 * - neighborUpdate: Checks frame validity, breaks if invalid
 */
public class PersonalPortalBlock extends Block {

    /**
     * Axis property for portal orientation (X or Z).
     * X-axis portal faces north/south, Z-axis portal faces east/west.
     */
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    /**
     * Color property for portal appearance.
     * Determines which texture is used for rendering.
     */
    public static final EnumProperty<PortalColor> COLOR = EnumProperty.create("color", PortalColor.class);

    /**
     * Collision shape for X-axis portals (thin plane facing north/south).
     */
    protected static final VoxelShape X_SHAPE = Block.box(0.0, 0.0, 6.0, 16.0, 16.0, 10.0);

    /**
     * Collision shape for Z-axis portals (thin plane facing east/west).
     */
    protected static final VoxelShape Z_SHAPE = Block.box(6.0, 0.0, 0.0, 10.0, 16.0, 16.0);

    /**
     * Portal cooldown in ticks (100 ticks = 5 seconds).
     * Prevents rapid teleportation flickering.
     */
    private static final int PORTAL_COOLDOWN = 100;

    public PersonalPortalBlock(Properties settings) {
        super(settings);
        registerDefaultState(getStateDefinition().any()
            .setValue(AXIS, Direction.Axis.X)
            .setValue(COLOR, PortalColor.RED));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, COLOR);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.Z ? Z_SHAPE : X_SHAPE;
    }

    /**
     * Called when an entity collides with (enters) the portal block.
     * Triggers teleportation for server-side players who don't have portal cooldown.
     */
    //? if >=1.21.11 {
    /*@Override
    protected void entityInside(BlockState state, Level world, BlockPos pos, Entity entity, net.minecraft.world.entity.InsideBlockEffectApplier handler, boolean bl) {
        handleEntityCollision(state, world, pos, entity);
    }
    *///?} else {
    @Override
    public void entityInside(BlockState state, Level world, BlockPos pos, Entity entity) {
        handleEntityCollision(state, world, pos, entity);
    }
    //?}

    private void handleEntityCollision(BlockState state, Level world, BlockPos pos, Entity entity) {
        if (world.isClientSide()) {
            return;
        }

        if (!(entity instanceof ServerPlayer player)) {
            return;
        }

        // Prevent mounted players from entering portals
        if (player.isPassenger()) {
            player.displayClientMessage(
                Component.translatable("pocketislands.portal.dismount_required"),
                true  // Action bar message (less intrusive)
            );
            return;
        }

        // Check portal cooldown to prevent rapid teleportation
        if (player.isOnPortalCooldown()) {
            return;
        }

        // Handle the portal entry (teleportation)
        PortalHelper.handlePortalEntry(player, pos);

        // Set portal cooldown
        player.setPortalCooldown(PORTAL_COOLDOWN);
    }

    /**
     * Called when a neighboring block changes.
     * Checks if the portal frame is still valid; if not, removes this portal block.
     */
    //? if >=1.21.5 {
    /*@Override
    protected void neighborChanged(BlockState state, Level world, BlockPos pos, Block sourceBlock, @org.jetbrains.annotations.Nullable net.minecraft.world.level.redstone.Orientation wireOrientation, boolean notify) {
        handleNeighborUpdate(state, world, pos);
    }
    *///?} else {
    @Override
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        handleNeighborUpdate(state, world, pos);
    }
    //?}

    private void handleNeighborUpdate(BlockState state, Level world, BlockPos pos) {
        if (world.isClientSide()) {
            return;
        }

        Direction.Axis axis = state.getValue(AXIS);

        // Check if the frame is still valid for this portal block
        if (!PortalHelper.isFrameValidForPortal(world, pos, axis)) {
            // Frame broken - remove this portal block
            world.removeBlock(pos, false);
            PersonalWorldsMod.LOGGER.debug("Portal block removed at {} - frame broken", pos);
        }
    }

    /**
     * Called when this block is replaced (broken, changed, etc.).
     * Cleans up portal ownership record when the portal is destroyed.
     */
    //? if >=1.21.5 {
    /*@Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel world, BlockPos pos, boolean moved) {
        // In 1.21.5+, onStateReplaced receives the old state
        // Clean up portal ownership when destroyed
        PortalOwnershipManager ownershipManager = PortalOwnershipManager.get(world.getServer());
        ownershipManager.removePortal(world, pos);
        super.affectNeighborsAfterRemoval(state, world, pos, moved);
    }
    *///?} else {
    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
        // Only clean up if the block is actually being removed (not just state change)
        if (!state.is(newState.getBlock())) {
            if (world instanceof ServerLevel serverWorld) {
                PortalOwnershipManager ownershipManager = PortalOwnershipManager.get(serverWorld.getServer());
                ownershipManager.removePortal(world, pos);
            }
        }
        super.onRemove(state, world, pos, newState, moved);
    }
    //?}

    /**
     * Portal blocks are transparent (not full cubes).
     */
    //? if >=1.21.5 {
    /*@Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }
    *///?} else {
    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter world, BlockPos pos) {
        return true;
    }
    //?}

    /**
     * Get the axis for a block state.
     */
    public static Direction.Axis getAxis(BlockState state) {
        return state.getValue(AXIS);
    }
}
