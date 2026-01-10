package com.wickedsik.personalworlds.portal;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PortalFrame record.
 * Tests geometry calculations for portal interior and frame positions.
 *
 * Note: width/height in PortalFrame refer to INTERIOR dimensions.
 * A 2x3 interior creates a 4x5 total frame (width+2, height+2).
 */
class PortalFrameTest {

    private static final BlockPos ORIGIN = new BlockPos(0, 0, 0);
    private static final BlockPos OFFSET = new BlockPos(100, 64, 200);

    @Nested
    @DisplayName("Record Construction")
    class RecordConstruction {

        @Test
        @DisplayName("Record stores all fields correctly")
        void constructor_storesFields() {
            PortalFrame frame = new PortalFrame(ORIGIN, 2, 3, Direction.Axis.X);

            assertEquals(ORIGIN, frame.bottomLeft());
            assertEquals(2, frame.width());
            assertEquals(3, frame.height());
            assertEquals(Direction.Axis.X, frame.axis());
        }

        @Test
        @DisplayName("Record with different axis stores correctly")
        void constructor_zAxis_storesCorrectly() {
            PortalFrame frame = new PortalFrame(ORIGIN, 2, 3, Direction.Axis.Z);

            assertEquals(Direction.Axis.Z, frame.axis());
        }
    }

    @Nested
    @DisplayName("Interior Position Calculation")
    class InteriorPositions {

        @Test
        @DisplayName("2x3 interior returns 6 positions")
        void getInteriorPositions_2x3_returns6Positions() {
            PortalFrame frame = new PortalFrame(ORIGIN, 2, 3, Direction.Axis.X);

            List<BlockPos> interior = frame.getInteriorPositions();

            assertEquals(6, interior.size()); // 2 wide x 3 tall
        }

        @Test
        @DisplayName("1x1 interior returns 1 position")
        void getInteriorPositions_1x1_returns1Position() {
            PortalFrame frame = new PortalFrame(ORIGIN, 1, 1, Direction.Axis.X);

            List<BlockPos> interior = frame.getInteriorPositions();

            assertEquals(1, interior.size());
        }

        @Test
        @DisplayName("3x4 interior returns 12 positions")
        void getInteriorPositions_3x4_returns12Positions() {
            PortalFrame frame = new PortalFrame(ORIGIN, 3, 4, Direction.Axis.X);

            List<BlockPos> interior = frame.getInteriorPositions();

            assertEquals(12, interior.size()); // 3 x 4
        }

        @Test
        @DisplayName("Interior starts one block inside frame on X-axis portal")
        void getInteriorPositions_xAxis_correctStartPosition() {
            PortalFrame frame = new PortalFrame(ORIGIN, 2, 3, Direction.Axis.X);

            List<BlockPos> interior = frame.getInteriorPositions();

            // First interior position should be at (1, 1, 0) - one up and one east
            BlockPos first = interior.get(0);
            assertEquals(1, first.getX()); // offset east
            assertEquals(1, first.getY()); // one up
            assertEquals(0, first.getZ()); // same Z for X-axis portal
        }

        @Test
        @DisplayName("Interior starts one block inside frame on Z-axis portal")
        void getInteriorPositions_zAxis_correctStartPosition() {
            PortalFrame frame = new PortalFrame(ORIGIN, 2, 3, Direction.Axis.Z);

            List<BlockPos> interior = frame.getInteriorPositions();

            // First interior position should be at (0, 1, 1) - one up and one south
            BlockPos first = interior.get(0);
            assertEquals(0, first.getX()); // same X for Z-axis portal
            assertEquals(1, first.getY()); // one up
            assertEquals(1, first.getZ()); // offset south
        }

        @Test
        @DisplayName("X-axis portal: all interior blocks have same Z")
        void getInteriorPositions_xAxis_sameZ() {
            PortalFrame frame = new PortalFrame(OFFSET, 2, 3, Direction.Axis.X);

            List<BlockPos> interior = frame.getInteriorPositions();

            int expectedZ = OFFSET.getZ();
            assertTrue(interior.stream().allMatch(pos -> pos.getZ() == expectedZ));
        }

        @Test
        @DisplayName("Z-axis portal: all interior blocks have same X")
        void getInteriorPositions_zAxis_sameX() {
            PortalFrame frame = new PortalFrame(OFFSET, 2, 3, Direction.Axis.Z);

            List<BlockPos> interior = frame.getInteriorPositions();

            int expectedX = OFFSET.getX();
            assertTrue(interior.stream().allMatch(pos -> pos.getX() == expectedX));
        }

        @Test
        @DisplayName("Interior respects bottomLeft offset")
        void getInteriorPositions_withOffset_correctPositions() {
            PortalFrame frame = new PortalFrame(OFFSET, 2, 3, Direction.Axis.X);

            List<BlockPos> interior = frame.getInteriorPositions();

            // First position should be at (101, 65, 200)
            BlockPos first = interior.get(0);
            assertEquals(OFFSET.getX() + 1, first.getX());
            assertEquals(OFFSET.getY() + 1, first.getY());
            assertEquals(OFFSET.getZ(), first.getZ());
        }
    }

    @Nested
    @DisplayName("Frame Position Calculation")
    class FramePositions {

        @Test
        @DisplayName("2x3 interior frame has 14 frame blocks")
        void getFramePositions_2x3interior_returns14Positions() {
            // 2x3 interior = 4x5 frame (4+5)*2 - 4 corners = 14
            // Actually: 2*(width+2) + 2*(height+2) - 4 = 2*4 + 2*5 - 4 = 8 + 10 - 4 = 14
            PortalFrame frame = new PortalFrame(ORIGIN, 2, 3, Direction.Axis.X);

            List<BlockPos> frameBlocks = frame.getFramePositions();

            assertEquals(14, frameBlocks.size());
        }

        @Test
        @DisplayName("1x1 interior frame has 8 frame blocks")
        void getFramePositions_1x1interior_returns8Positions() {
            // 1x1 interior = 3x3 frame = perimeter of 8
            PortalFrame frame = new PortalFrame(ORIGIN, 1, 1, Direction.Axis.X);

            List<BlockPos> frameBlocks = frame.getFramePositions();

            assertEquals(8, frameBlocks.size());
        }

        @Test
        @DisplayName("Frame includes bottom-left corner")
        void getFramePositions_includesBottomLeft() {
            PortalFrame frame = new PortalFrame(ORIGIN, 2, 3, Direction.Axis.X);

            List<BlockPos> frameBlocks = frame.getFramePositions();

            assertTrue(frameBlocks.contains(ORIGIN));
        }

        @Test
        @DisplayName("X-axis frame includes correct corners")
        void getFramePositions_xAxis_correctCorners() {
            PortalFrame frame = new PortalFrame(ORIGIN, 2, 3, Direction.Axis.X);
            // Frame is 4 wide (X), 5 tall (Y)

            List<BlockPos> frameBlocks = frame.getFramePositions();

            // Check all four corners
            assertTrue(frameBlocks.contains(new BlockPos(0, 0, 0))); // bottom-left
            assertTrue(frameBlocks.contains(new BlockPos(3, 0, 0))); // bottom-right (0+3)
            assertTrue(frameBlocks.contains(new BlockPos(0, 4, 0))); // top-left (height+1=4)
            assertTrue(frameBlocks.contains(new BlockPos(3, 4, 0))); // top-right
        }

        @Test
        @DisplayName("Z-axis frame includes correct corners")
        void getFramePositions_zAxis_correctCorners() {
            PortalFrame frame = new PortalFrame(ORIGIN, 2, 3, Direction.Axis.Z);
            // Frame is 4 wide (Z), 5 tall (Y)

            List<BlockPos> frameBlocks = frame.getFramePositions();

            // Check all four corners
            assertTrue(frameBlocks.contains(new BlockPos(0, 0, 0))); // bottom-left
            assertTrue(frameBlocks.contains(new BlockPos(0, 0, 3))); // bottom-right (south)
            assertTrue(frameBlocks.contains(new BlockPos(0, 4, 0))); // top-left
            assertTrue(frameBlocks.contains(new BlockPos(0, 4, 3))); // top-right
        }

        @Test
        @DisplayName("Frame positions are distinct")
        void getFramePositions_noDuplicates() {
            PortalFrame frame = new PortalFrame(ORIGIN, 2, 3, Direction.Axis.X);

            List<BlockPos> frameBlocks = frame.getFramePositions();

            assertEquals(frameBlocks.size(), frameBlocks.stream().distinct().count());
        }
    }

    @Nested
    @DisplayName("Center Calculation")
    class CenterCalculation {

        @Test
        @DisplayName("2x3 interior center is at expected position")
        void getCenter_2x3_correctPosition() {
            PortalFrame frame = new PortalFrame(ORIGIN, 2, 3, Direction.Axis.X);

            BlockPos center = frame.getCenter();

            // Center is at: up(1 + 3/2) = up(2), east(1 + 2/2) = east(2)
            // So: (2, 2, 0)
            assertEquals(new BlockPos(2, 2, 0), center);
        }

        @Test
        @DisplayName("1x1 interior center is inside frame")
        void getCenter_1x1_insideFrame() {
            PortalFrame frame = new PortalFrame(ORIGIN, 1, 1, Direction.Axis.X);

            BlockPos center = frame.getCenter();

            // up(1 + 0) = up(1), east(1 + 0) = east(1)
            assertEquals(new BlockPos(1, 1, 0), center);
        }

        @Test
        @DisplayName("Center respects bottomLeft offset")
        void getCenter_withOffset_correctPosition() {
            PortalFrame frame = new PortalFrame(OFFSET, 2, 3, Direction.Axis.X);

            BlockPos center = frame.getCenter();

            // (100+2, 64+2, 200)
            assertEquals(new BlockPos(102, 66, 200), center);
        }

        @Test
        @DisplayName("Z-axis center offsets along Z")
        void getCenter_zAxis_offsetsAlongZ() {
            PortalFrame frame = new PortalFrame(ORIGIN, 2, 3, Direction.Axis.Z);

            BlockPos center = frame.getCenter();

            // up(2), south(2)
            assertEquals(new BlockPos(0, 2, 2), center);
        }

        @Test
        @DisplayName("Even width/height center is floor divided")
        void getCenter_evenDimensions_floorDivision() {
            PortalFrame frame = new PortalFrame(ORIGIN, 4, 4, Direction.Axis.X);

            BlockPos center = frame.getCenter();

            // up(1 + 4/2) = up(3), east(1 + 4/2) = east(3)
            assertEquals(new BlockPos(3, 3, 0), center);
        }

        @Test
        @DisplayName("Odd width/height center is correctly placed")
        void getCenter_oddDimensions_correctCenter() {
            PortalFrame frame = new PortalFrame(ORIGIN, 3, 5, Direction.Axis.X);

            BlockPos center = frame.getCenter();

            // up(1 + 5/2) = up(3), east(1 + 3/2) = east(2)
            assertEquals(new BlockPos(2, 3, 0), center);
        }
    }

    @Nested
    @DisplayName("Axis Orientation")
    class AxisOrientation {

        @Test
        @DisplayName("X-axis portal extends along X dimension")
        void xAxis_extendsAlongX() {
            PortalFrame frame = new PortalFrame(ORIGIN, 2, 3, Direction.Axis.X);

            List<BlockPos> interior = frame.getInteriorPositions();

            // Check that X values vary
            long uniqueX = interior.stream().map(BlockPos::getX).distinct().count();
            assertEquals(2, uniqueX); // width of 2
        }

        @Test
        @DisplayName("Z-axis portal extends along Z dimension")
        void zAxis_extendsAlongZ() {
            PortalFrame frame = new PortalFrame(ORIGIN, 2, 3, Direction.Axis.Z);

            List<BlockPos> interior = frame.getInteriorPositions();

            // Check that Z values vary
            long uniqueZ = interior.stream().map(BlockPos::getZ).distinct().count();
            assertEquals(2, uniqueZ); // width of 2
        }

        @Test
        @DisplayName("Both axes have same height distribution")
        void bothAxes_sameHeightDistribution() {
            PortalFrame xFrame = new PortalFrame(ORIGIN, 2, 3, Direction.Axis.X);
            PortalFrame zFrame = new PortalFrame(ORIGIN, 2, 3, Direction.Axis.Z);

            List<BlockPos> xInterior = xFrame.getInteriorPositions();
            List<BlockPos> zInterior = zFrame.getInteriorPositions();

            // Both should have Y values: 1, 2, 3 (height of 3, starting at y=1)
            long uniqueYx = xInterior.stream().map(BlockPos::getY).distinct().count();
            long uniqueYz = zInterior.stream().map(BlockPos::getY).distinct().count();

            assertEquals(3, uniqueYx);
            assertEquals(3, uniqueYz);
        }
    }

    @Nested
    @DisplayName("Record Equality")
    class RecordEquality {

        @Test
        @DisplayName("Same values are equal")
        void equals_sameValues_equal() {
            PortalFrame frame1 = new PortalFrame(ORIGIN, 2, 3, Direction.Axis.X);
            PortalFrame frame2 = new PortalFrame(ORIGIN, 2, 3, Direction.Axis.X);

            assertEquals(frame1, frame2);
            assertEquals(frame1.hashCode(), frame2.hashCode());
        }

        @Test
        @DisplayName("Different bottomLeft not equal")
        void equals_differentBottomLeft_notEqual() {
            PortalFrame frame1 = new PortalFrame(ORIGIN, 2, 3, Direction.Axis.X);
            PortalFrame frame2 = new PortalFrame(OFFSET, 2, 3, Direction.Axis.X);

            assertNotEquals(frame1, frame2);
        }

        @Test
        @DisplayName("Different axis not equal")
        void equals_differentAxis_notEqual() {
            PortalFrame frame1 = new PortalFrame(ORIGIN, 2, 3, Direction.Axis.X);
            PortalFrame frame2 = new PortalFrame(ORIGIN, 2, 3, Direction.Axis.Z);

            assertNotEquals(frame1, frame2);
        }

        @Test
        @DisplayName("Different dimensions not equal")
        void equals_differentDimensions_notEqual() {
            PortalFrame frame1 = new PortalFrame(ORIGIN, 2, 3, Direction.Axis.X);
            PortalFrame frame2 = new PortalFrame(ORIGIN, 3, 3, Direction.Axis.X);

            assertNotEquals(frame1, frame2);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Large dimensions work correctly")
        void largeDimensions_work() {
            PortalFrame frame = new PortalFrame(ORIGIN, 20, 20, Direction.Axis.X);

            List<BlockPos> interior = frame.getInteriorPositions();

            assertEquals(400, interior.size()); // 20 x 20
        }

        @Test
        @DisplayName("Negative bottomLeft coordinates work")
        void negativeCoordinates_work() {
            BlockPos negative = new BlockPos(-100, -50, -200);
            PortalFrame frame = new PortalFrame(negative, 2, 3, Direction.Axis.X);

            List<BlockPos> interior = frame.getInteriorPositions();

            assertEquals(6, interior.size());
            // First interior at (-99, -49, -200)
            BlockPos first = interior.get(0);
            assertEquals(-99, first.getX());
            assertEquals(-49, first.getY());
        }
    }
}
