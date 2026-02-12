package com.wickedsik.personalworlds.portal;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.text.Text;
import net.minecraft.world.World;

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
    public static final EnumProperty<Direction.Axis> AXIS = Properties.HORIZONTAL_AXIS;

    /**
     * Color property for portal appearance.
     * Determines which texture is used for rendering.
     */
    public static final EnumProperty<PortalColor> COLOR = EnumProperty.of("color", PortalColor.class);

    /**
     * Collision shape for X-axis portals (thin plane facing north/south).
     */
    protected static final VoxelShape X_SHAPE = Block.createCuboidShape(0.0, 0.0, 6.0, 16.0, 16.0, 10.0);

    /**
     * Collision shape for Z-axis portals (thin plane facing east/west).
     */
    protected static final VoxelShape Z_SHAPE = Block.createCuboidShape(6.0, 0.0, 0.0, 10.0, 16.0, 16.0);

    /**
     * Portal cooldown in ticks (100 ticks = 5 seconds).
     * Prevents rapid teleportation flickering.
     */
    private static final int PORTAL_COOLDOWN = 100;

    public PersonalPortalBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState()
            .with(AXIS, Direction.Axis.X)
            .with(COLOR, PortalColor.RED));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(AXIS, COLOR);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(AXIS) == Direction.Axis.Z ? Z_SHAPE : X_SHAPE;
    }

    /**
     * Called when an entity collides with (enters) the portal block.
     * Triggers teleportation for server-side players who don't have portal cooldown.
     */
    //? if >=1.21.11 {
    /*@Override
    protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity, net.minecraft.entity.EntityCollisionHandler handler, boolean bl) {
        handleEntityCollision(state, world, pos, entity);
    }
    *///?} else if >=1.21.5 {
    /*@Override
    protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity, net.minecraft.entity.EntityCollisionHandler handler) {
        handleEntityCollision(state, world, pos, entity);
    }*/
    //?} else if >=1.21 {
    /*@Override
    protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        handleEntityCollision(state, world, pos, entity);
    }*/
    //?} else {
    @Override
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        handleEntityCollision(state, world, pos, entity);
    }
    //?}

    private void handleEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (world.isClient()) {
            return;
        }

        if (!(entity instanceof ServerPlayerEntity player)) {
            return;
        }

        // Prevent mounted players from entering portals
        if (player.hasVehicle()) {
            player.sendMessage(
                Text.translatable("pocketislands.portal.dismount_required"),
                true  // Action bar message (less intrusive)
            );
            return;
        }

        // Check portal cooldown to prevent rapid teleportation
        if (player.hasPortalCooldown()) {
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
    protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, @org.jetbrains.annotations.Nullable net.minecraft.world.block.WireOrientation wireOrientation, boolean notify) {
        handleNeighborUpdate(state, world, pos);
    }
    *///?} else if >=1.21 {
    /*@Override
    protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        handleNeighborUpdate(state, world, pos);
    }*/
    //?} else {
    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        handleNeighborUpdate(state, world, pos);
    }
    //?}

    private void handleNeighborUpdate(BlockState state, World world, BlockPos pos) {
        if (world.isClient()) {
            return;
        }

        Direction.Axis axis = state.get(AXIS);

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
    protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        // In 1.21.5+, onStateReplaced receives the old state
        // Clean up portal ownership when destroyed
        PortalOwnershipManager ownershipManager = PortalOwnershipManager.get(world.getServer());
        ownershipManager.removePortal(world, pos);
        super.onStateReplaced(state, world, pos, moved);
    }
    *///?} else if >=1.21 {
    /*@Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        // Only clean up if the block is actually being removed (not just state change)
        if (!state.isOf(newState.getBlock())) {
            if (world instanceof ServerWorld serverWorld) {
                PortalOwnershipManager ownershipManager = PortalOwnershipManager.get(serverWorld.getServer());
                ownershipManager.removePortal(world, pos);
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }*/
    //?} else {
    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        // Only clean up if the block is actually being removed (not just state change)
        if (!state.isOf(newState.getBlock())) {
            if (world instanceof ServerWorld serverWorld) {
                PortalOwnershipManager ownershipManager = PortalOwnershipManager.get(serverWorld.getServer());
                ownershipManager.removePortal(world, pos);
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }
    //?}

    /**
     * Portal blocks are transparent (not full cubes).
     */
    //? if >=1.21.5 {
    /*@Override
    protected boolean isTransparent(BlockState state) {
        return true;
    }
    *///?} else if >=1.21 {
    /*@Override
    protected boolean isTransparent(BlockState state, BlockView world, BlockPos pos) {
        return true;
    }*/
    //?} else {
    @Override
    public boolean isTransparent(BlockState state, BlockView world, BlockPos pos) {
        return true;
    }
    //?}

    /**
     * Get the axis for a block state.
     */
    public static Direction.Axis getAxis(BlockState state) {
        return state.get(AXIS);
    }
}
