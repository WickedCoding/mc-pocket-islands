package com.wickedsik.personalworlds.portal;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a detected portal frame structure.
 *
 * The frame follows a nether portal-like pattern:
 * <pre>
 * F F F F    (width + 2 frame blocks)
 * F . . F    Interior is 'width' x 'height'
 * F . . F
 * F . . F
 * F F F F    (height + 2 total)
 * </pre>
 *
 * @param bottomLeft The bottom-left corner of the frame (frame block, not interior)
 * @param width Interior width (typically 2)
 * @param height Interior height (typically 3)
 * @param axis The horizontal axis (X or Z) determining portal orientation
 */
public record PortalFrame(
    BlockPos bottomLeft,
    int width,
    int height,
    Direction.Axis axis
) {

    /**
     * Get all interior positions where portal blocks should be placed.
     * These are the air blocks inside the frame.
     *
     * @return List of interior block positions
     */
    public List<BlockPos> getInteriorPositions() {
        List<BlockPos> positions = new ArrayList<>();

        // Determine the horizontal direction based on axis
        // X-axis portal expands along X (EAST direction)
        // Z-axis portal expands along Z (SOUTH direction)
        Direction horizontal = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;

        // Start from one block inside the frame (above bottom, after left edge)
        // bottomLeft is the corner frame block, so interior starts at +1 up and +1 horizontal
        BlockPos start = bottomLeft.up().offset(horizontal);

        for (int h = 0; h < height; h++) {
            for (int w = 0; w < width; w++) {
                BlockPos pos = start.up(h).offset(horizontal, w);
                positions.add(pos);
            }
        }

        return positions;
    }

    /**
     * Get all frame positions (the blocks that form the frame structure).
     * Used for validation and frame-breaking detection.
     *
     * @return List of frame block positions
     */
    public List<BlockPos> getFramePositions() {
        List<BlockPos> positions = new ArrayList<>();

        Direction horizontal = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;

        // Total frame dimensions: (width + 2) wide, (height + 2) tall
        int frameWidth = width + 2;
        int frameHeight = height + 2;

        // Bottom edge (full width)
        for (int w = 0; w < frameWidth; w++) {
            positions.add(bottomLeft.offset(horizontal, w));
        }

        // Top edge (full width)
        for (int w = 0; w < frameWidth; w++) {
            positions.add(bottomLeft.up(frameHeight - 1).offset(horizontal, w));
        }

        // Left edge (excluding corners already added)
        for (int h = 1; h < frameHeight - 1; h++) {
            positions.add(bottomLeft.up(h));
        }

        // Right edge (excluding corners already added)
        for (int h = 1; h < frameHeight - 1; h++) {
            positions.add(bottomLeft.offset(horizontal, frameWidth - 1).up(h));
        }

        return positions;
    }

    /**
     * Get the center position of the portal interior.
     * Useful for effects or sounds.
     *
     * @return Approximate center position
     */
    public BlockPos getCenter() {
        Direction horizontal = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        return bottomLeft
            .up(1 + height / 2)
            .offset(horizontal, 1 + width / 2);
    }
}
