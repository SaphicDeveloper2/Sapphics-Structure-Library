package com.sapphic.ssl.items;

import com.sapphic.ssl.api.loot.LootBarrelBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The SSL Loot Barrel — a developer-only block used to define loot data for
 * structure pieces at authoring time.
 *
 * <p>Visually identical to a vanilla barrel (uses the same model JSON via
 * {@code sapphics-structure-library:block/loot_barrel} → barrel texture).
 * Internally it runs in one of two modes:
 *
 * <h3>SNAPSHOT mode (default)</h3>
 * <p>The 27-slot inventory acts as a "weight palette." At structure generation time,
 * {@link com.sapphic.ssl.internal.loot.SmartLootEngine SmartLootEngine} reads the
 * stacks and computes weights from item quantities (20 Iron + 1 Diamond = 20:1 ratio).
 * The barrel is swapped for a vanilla chest that is immediately filled with
 * one-off generated loot. The chest's loot key is cleared before population
 * (defensive design — no infinite-refill loop on repeated opens).
 *
 * <h3>REGISTRY mode</h3>
 * <p>The barrel stores a named table reference. At generation time the barrel is
 * swapped for a chest that points at the named {@code .tsaphloot} or vanilla loot
 * table; content is generated lazily on first player-open.
 *
 * <h2>Developer workflow</h2>
 * <ol>
 *   <li>Obtain via {@code /give @s sapphics-structure-library:loot_barrel}.</li>
 *   <li>Place in your structure piece.  Right-click to open the 27-slot inventory.</li>
 *   <li>SNAPSHOT: fill slots with items in the desired weight ratios.</li>
 *   <li>REGISTRY: set registry key via {@code /tsaph loot setbarrel <pos> tsaphloot <name>}
 *       or {@code vanilla <tableId>}.</li>
 *   <li>Export the selection — the barrel is compiled to a chest at generation time.</li>
 * </ol>
 *
 * <p>This block is intentionally absent from any creative tab.
 * <p><strong>Obfuscated in production builds.</strong>
 */
public class LootBarrelBlock extends BlockWithEntity {

    /**
     * Codec required by {@link BlockWithEntity#getCodec()}.
     * SSL's LootBarrel is a developer-only authoring block — it is never
     * serialised via the block codec path, so a unit codec is sufficient.
     */
    // Block.createCodec takes a Settings→Block function and never eagerly
    // constructs an instance — safe to use in a static field initializer.
    public static final MapCodec<LootBarrelBlock> CODEC =
            Block.createCodec(LootBarrelBlock::new);

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    /**
     * Horizontal facing — carried through so the swapped chest inherits the same
     * facing direction.
     *
     * <p>Typed as {@code Property<Direction>} for cross-version compatibility
     * (see {@link ConnectorBlock} javadoc for rationale).
     */
    @SuppressWarnings("unchecked")
    public static final Property<Direction> FACING =
            (Property<Direction>) Properties.HORIZONTAL_FACING;

    public LootBarrelBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }

    // ── Block state ────────────────────────────────────────────────────────

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    // ── Block entity ───────────────────────────────────────────────────────

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new LootBarrelBlockEntity(pos, state);
    }

    // ── Interaction ────────────────────────────────────────────────────────

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos,
                                 PlayerEntity player, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof LootBarrelBlockEntity barrel) {
            // LootBarrelBlockEntity implements NamedScreenHandlerFactory in api/ so
            // ProGuard preserves the createMenu override — safe to open directly.
            player.openHandledScreen(barrel);
        }
        return ActionResult.CONSUME;
    }

    @Override
    protected ItemActionResult onUseWithItem(net.minecraft.item.ItemStack stack,
                                             BlockState state, World world, BlockPos pos,
                                             PlayerEntity player, Hand hand,
                                             BlockHitResult hit) {
        return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    // ── Drop contents on break ────────────────────────────────────────────

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos,
                                   BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof LootBarrelBlockEntity barrel) {
                barrel.dropContents(world, pos);
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
