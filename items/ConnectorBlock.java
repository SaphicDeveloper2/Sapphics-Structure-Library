package com.sapphic.ssl.items;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
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
 * removed between Minecraft 1.21.3 and 1.21.4; using the base {@code Property}
 * type avoids a {@link NoClassDefFoundError} on future versions while retaining
 * full runtime behaviour — the value from {@link Properties#HORIZONTAL_FACING}
 * is the same object either way.
 *
 * <p>{@code ItemPlacementContext} is available and stable across the supported
 * range (1.21.1–1.21.3) and is used by {@link #getPlacementState} to orient the
 * block toward the player's look direction on placement.  If SSL is ever ported
 * to 1.21.4+, where {@code ItemPlacementContext} was removed, this method and its
 * import should be dropped and the version note updated.
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

    /**
     * Orient the block toward the player's horizontal look direction on placement.
     *
     * <p>Connection point blocks mark the <em>outward</em> exit of a structure
     * opening, so the facing should match the direction the player is looking
     * (i.e. toward the opening) rather than back at the player.  This is the
     * opposite convention from blocks like pistons and dispensers.
     *
     * <p>Example: standing inside a north-facing corridor doorway looking north →
     * the placed connector will have {@code facing=north}, which the
     * {@link com.sapphic.ssl.internal.ProceduralEngine} interprets as "the next
     * piece attaches from the north side."
     *
     * <p>{@code ItemPlacementContext} is stable across Minecraft 1.21.1–1.21.3
     * (the supported range).  See the class-level version note for details.
     */
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing());
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world,
                                     BlockPos pos, ShapeContext context) {
        return VoxelShapes.fullCube();
    }
}