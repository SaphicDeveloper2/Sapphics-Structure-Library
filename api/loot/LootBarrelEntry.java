package com.sapphic.ssl.api.loot;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import org.jetbrains.annotations.NotNull;

/**
 * A single entry in a {@link LootBarrelBlockEntity} SNAPSHOT palette.
 *
 * <p>Stores one item type (stack count is always clamped to 1 — count is irrelevant,
 * only the item identity matters) and an explicit float weight in the range
 * {@value #MIN_WEIGHT}–{@value #MAX_WEIGHT}.
 *
 * <p>Weight semantics: a Diamond at 2.0 and Iron Ingot at 0.5 means Diamond is
 * four times more likely to roll than Iron Ingot.  Absolute values are only
 * meaningful relative to other entries in the same barrel.
 */
public final class LootBarrelEntry {

    // ── Constants ──────────────────────────────────────────────────────────

    public static final float MIN_WEIGHT  = 0.01f;
    public static final float MAX_WEIGHT  = 2.00f;
    public static final int   MAX_ENTRIES = 16;

    // Weights are stored as int (weight × 100) for Property sync and NBT compactness.
    public static final int   INT_MIN     = 1;   // 0.01 × 100
    public static final int   INT_MAX     = 200; // 2.00 × 100

    // ── NBT keys ──────────────────────────────────────────────────────────

    private static final String NBT_ITEM   = "Item";
    private static final String NBT_WEIGHT = "Weight";

    // ── State ─────────────────────────────────────────────────────────────

    private @NotNull ItemStack item;
    private float weight;

    // ── Constructors ───────────────────────────────────────────────────────

    public LootBarrelEntry(@NotNull ItemStack item, float weight) {
        this.item   = item.isEmpty() ? ItemStack.EMPTY : item.copyWithCount(1);
        this.weight = clamp(weight);
    }

    /** Construct an empty entry (no item, default weight 1.0). */
    public LootBarrelEntry() {
        this.item   = ItemStack.EMPTY;
        this.weight = 1.0f;
    }

    // ── Accessors ──────────────────────────────────────────────────────────

    public @NotNull ItemStack item()   { return item; }
    public float              weight() { return weight; }
    public boolean            isEmpty() { return item.isEmpty(); }

    public void setItem(@NotNull ItemStack stack) {
        this.item = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
    }

    public void setWeight(float w) {
        this.weight = clamp(w);
    }

    /** Encode weight as an int (weight × 100) for Property sync. Range: 1–200. */
    public int weightAsInt() {
        return Math.round(weight * 100f);
    }

    /** Decode an int property value back to a float weight. */
    public static float weightFromInt(int intValue) {
        return clamp(intValue / 100f);
    }

    // ── NBT I/O ────────────────────────────────────────────────────────────

    public NbtCompound writeNbt(RegistryWrapper.WrapperLookup registries) {
        NbtCompound tag = new NbtCompound();
        if (!item.isEmpty()) {
            tag.put(NBT_ITEM, item.encode(registries));
        }
        tag.putFloat(NBT_WEIGHT, weight);
        return tag;
    }

    public static LootBarrelEntry readNbt(NbtCompound tag,
                                          RegistryWrapper.WrapperLookup registries) {
        ItemStack stack = ItemStack.EMPTY;
        if (tag.contains(NBT_ITEM)) {
            stack = ItemStack.fromNbt(registries, tag.getCompound(NBT_ITEM))
                             .orElse(ItemStack.EMPTY);
        }
        float w = tag.contains(NBT_WEIGHT) ? tag.getFloat(NBT_WEIGHT) : 1.0f;
        return new LootBarrelEntry(stack, w);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    public static float clamp(float w) {
        return Math.max(MIN_WEIGHT, Math.min(MAX_WEIGHT, w));
    }

    @Override
    public String toString() {
        return "LootBarrelEntry[" + (item.isEmpty() ? "empty" : item.getItem()) +
               " w=" + weight + "]";
    }
}
