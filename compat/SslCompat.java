package com.sapphic.ssl.compat;

import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.Block;

/**
 * Version-aware utility wrapper for Minecraft APIs used by SSL internals.
 *
 * <h2>Design rationale</h2>
 * <p>Across Minecraft 1.21.1–1.21.10 (Yarn), all SSL-relevant APIs are
 * completely stable — the same class names, method signatures, and package
 * paths apply to the entire range.  This class therefore contains a single
 * implementation rather than subclasses, acting purely as a centrally-tested
 * single point of truth for every version-sensitive call site.
 *
 * <p>If SSL is ever compiled against 1.21.11 Yarn (Mojmap-aligned names),
 * a sibling implementation should be created that overrides the methods
 * affected by the package/class renames:
 * {@code ServerWorld→ServerLevel}, {@code WorldChunk→LevelChunk},
 * {@code NbtComponent→CustomData}, etc.
 *
 * <p>Call {@link #get()} everywhere instead of using Minecraft APIs directly
 * so that a future 1.21.11 port only needs to swap implementations here.
 */
public final class SslCompat {

    // FORCE_STATE suppresses neighbour updates during bulk Pass-1 placement.
    // Flag 2 (SEND_TO_CLIENT) forces the change to be dispatched to tracking
    // clients immediately, preventing ghost blocks in already-loaded chunks.
    // Confirmed correct and stable across 1.21.1–1.21.10 from source inspection.
    private static final int PLACE_FLAGS = Block.FORCE_STATE | 2;

    private static final SslCompat INSTANCE = new SslCompat();

    private SslCompat() {}

    /** Return the singleton compatibility helper for this Minecraft version. */
    public static SslCompat get() { return INSTANCE; }

    // ── Block placement ───────────────────────────────────────────────────

    /**
     * Place {@code state} at {@code pos} using the SSL-standard flag combination.
     * Stable across 1.21.1–1.21.10.
     */
    public void setBlockStateSafe(ServerWorld world, BlockPos pos,
                                   BlockState state, int callerFlags) {
        world.setBlockState(pos, state, callerFlags | PLACE_FLAGS);
    }

    /**
     * Expose the raw flag value used by {@link #setBlockStateSafe} so
     * sub-systems (e.g. {@link com.sapphic.ssl.internal.loot.SmartLootEngine})
     * can call {@code world.setBlockState} directly when they need the same
     * placement semantics without the full helper.
     *
     * @return {@code Block.FORCE_STATE | 2}
     */
    public int forceStateFlagValue() {
        return PLACE_FLAGS;
    }

    // ── Registry access ───────────────────────────────────────────────────

    /**
     * Return the default {@link BlockState} for {@code id}, or {@code null}
     * if the block is not registered.
     * {@code Registries.BLOCK.containsId} / {@code .get} stable across 1.21.1–1.21.10.
     */
    public BlockState blockStateById(Identifier id) {
        if (!Registries.BLOCK.containsId(id)) return null;
        return Registries.BLOCK.get(id).getDefaultState();
    }

    /** {@code true} if the block is registered. Stable across 1.21.1–1.21.10. */
    public boolean isBlockRegistered(Identifier id) {
        return Registries.BLOCK.containsId(id);
    }

    /** {@code true} if the item is registered. Stable across 1.21.1–1.21.10. */
    public boolean isItemRegistered(Identifier id) {
        return Registries.ITEM.containsId(id);
    }

    /**
     * Return the {@link RegistryWrapper.WrapperLookup} for {@code world}.
     * {@code ServerWorld.getRegistryManager()} stable across 1.21.1–1.21.10.
     */
    public RegistryWrapper.WrapperLookup registryLookup(ServerWorld world) {
        return world.getRegistryManager();
    }

    // ── ItemStack custom data ─────────────────────────────────────────────

    /**
     * Merge {@code extra} into {@code stack}'s {@code CUSTOM_DATA} component.
     *
     * <p>{@code DataComponentTypes.CUSTOM_DATA} / {@code NbtComponent} API
     * is stable across 1.21.1–1.21.10.  In 1.21.11+ Yarn, {@code NbtComponent}
     * was renamed; update this method in a 1.21.11-compiled sibling if needed.
     */
    public ItemStack mergeCustomData(ItemStack stack, NbtCompound extra) {
        NbtComponent existing = stack.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound base = (existing != null) ? existing.copyNbt() : new NbtCompound();
        extra.getKeys().forEach(k -> base.put(k, extra.get(k)));
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(base));
        return stack;
    }

    /** Human-readable name for logging. */
    public String layerName() {
        return "SslCompat (Minecraft 1.21.1–1.21.10 Yarn — unified)";
    }
}
