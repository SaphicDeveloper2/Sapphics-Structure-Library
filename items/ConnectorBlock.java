package com.sapphic.ssl.items;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/**
 * The SSL Connector Block.
 *
 * <p>Placed inside a structure selection to mark a connection endpoint for the
 * multi-struct procedural engine.  The block's {@link Properties#HORIZONTAL_FACING}
 * direction defines which way an adjacent piece will attach — the engine treats
 * the facing as the outward-pointing exit of this connection point.
 *
 * <h2>Behaviour at generation time</h2>
 * <p>When the {@link com.sapphic.ssl.internal.ProceduralEngine} places a piece that
 * contains connector blocks, each connector block is immediately replaced with the
 * floor block directly below it in the world.  If no floor block exists, it is
 * replaced with air.  This ensures the placed structure is seamless — no connector
 * blocks ever appear in the final generated world.
 *
 * <h2>Workflow</h2>
 * <ol>
 *   <li>Build your structure piece in the world.</li>
 *   <li>Place connector blocks at each opening facing outward in the correct
 *       {@link Properties#HORIZONTAL_FACING} direction.</li>
 *   <li>Export the selection as part of a multi-struct session.  The writer
 *       scans for connector blocks and records their relative positions and
 *       facing as {@code ConnectionPoint} records embedded in the piece.</li>
 * </ol>
 *
 * <h2>Version note</h2>
 * <p>{@code FACING} is typed as {@code Property<Direction>} rather than the
 * narrower {@code DirectionProperty} subclass.  {@code DirectionProperty} was
 * removed between Minecraft 1.21.1 and 1.21.3; using the base {@code Property}
 * type avoids a {@link NoClassDefFoundError} on those versions while retaining
 * full runtime behaviour — the value from {@link Properties#HORIZONTAL_FACING}
 * is the same object either way.
 *
 * <p>{@code getPlacementState} is intentionally absent.  The method's parameter
 * type ({@code ItemPlacementContext}) was also removed in 1.21.3.  Connector
 * blocks default to {@link Direction#NORTH} on placement; players rotate them
 * using the standard block-interaction mechanic, or the facing is set directly
 * by the procedural engine during export.
 */
public class ConnectorBlock extends Block {

    /**
     * Horizontal facing — defines the outward connection direction.
     *
     * <p>Typed as {@code Property<Direction>} for cross-version compatibility.
     * See class-level javadoc for details.
     */
    @SuppressWarnings("unchecked")
    public static final Property<Direction> FACING =
            (Property<Direction>) Properties.HORIZONTAL_FACING;

    public ConnectorBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world,
                                     BlockPos pos, ShapeContext context) {
        return VoxelShapes.fullCube();
    }
}
