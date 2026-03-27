package com.sapphic.ssl.api.loot;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.sapphic.ssl.items.LootBarrelBlock;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Block entity for {@link LootBarrelBlock}.
 *
 * <h3>Mode semantics</h3>
 * <ul>
 *   <li>{@link Mode#SNAPSHOT} — The 27-slot inventory encodes the weight palette.
 *       At placement time, {@link SmartLootEngine} reads the stacks, computes
 *       item-count → weight ratios, and immediately fills the replacement chest.
 *       The chest's loot key is cleared before population (defensive design).</li>
 *   <li>{@link Mode#REGISTRY} — {@link #registryKey} points to a named
 *       {@code .tsaphloot} table or vanilla loot-table key resolved at
 *       placement time.  Useful for globally-updatable loot shared across
 *       multiple structure pieces.</li>
 * </ul>
 *
 * <p>NBT layout:
 * <pre>
 * {
 *   "SslBarrelMode":  0 | 1        // 0 = SNAPSHOT, 1 = REGISTRY
 *   "SslRegistryKey": "dungeon_chest" | "minecraft:chests/simple_dungeon"
 *   "Items": [...]                 // standard inventory list (SNAPSHOT only)
 * }
 * </pre>
 *
 * <p><strong>Obfuscated in production builds.</strong>
 */
public class LootBarrelBlockEntity extends BlockEntity implements net.minecraft.screen.NamedScreenHandlerFactory {

    /**
     * Static handle populated by {@code SapphicsStructureLibrary.onInitialize()}.
     * Not a compile-time constant — the registry entry does not exist until runtime.
     */
    public static BlockEntityType<LootBarrelBlockEntity> TYPE;

    // ── NBT keys ──────────────────────────────────────────────────────────

    private static final String NBT_MODE     = "SslBarrelMode";
    private static final String NBT_REG_KEY  = "SslRegistryKey";
    private static final String NBT_ITEMS    = "Items";
    private static final String NBT_WEIGHTS  = "SslWeightOverrides";

    // ── State ─────────────────────────────────────────────────────────────

    /** Operational mode — governs how the barrel is compiled at generation time. */
    public enum Mode {
        /** Weights baked from inventory item counts. Wire byte: {@code 0}. */
        SNAPSHOT((byte) 0),
        /** Pointer to a named table. Wire byte: {@code 1}. */
        REGISTRY((byte) 1);

        private final byte wire;
        Mode(byte wire) { this.wire = wire; }
        public byte wireValue() { return wire; }
        public static Mode fromWire(byte b) {
            return switch (b) {
                case 0  -> SNAPSHOT;
                case 1  -> REGISTRY;
                default -> SNAPSHOT;
            };
        }
    }

    private Mode mode = Mode.SNAPSHOT;

    /**
     * In {@link Mode#REGISTRY}: the tsaphloot table name (e.g. {@code "dungeon_chest"})
     * or a full vanilla loot-table key (e.g. {@code "minecraft:chests/simple_dungeon"}).
     * Null in SNAPSHOT mode.
     */
    private @Nullable String registryKey = null;

    /**
     * 27-slot weight palette (SNAPSHOT mode only).
     * Each distinct item type contributes weight equal to its total stack count.
     */
    private final DefaultedList<ItemStack> items =
            DefaultedList.ofSize(27, ItemStack.EMPTY);

    /**
     * Per-item weight overrides. Key = item registry ID string.
     * When present, this value is used as the weight instead of the
     * summed stack count. Allows setting weights without counting out
     * exact item quantities in the inventory.
     */
    private final Map<String, Integer> weightOverrides = new HashMap<>();

    // ── Construction ───────────────────────────────────────────────────────

    public LootBarrelBlockEntity(BlockPos pos, BlockState state) {
        super(TYPE, pos, state);
    }

    // ── Public accessors used by SmartLootEngine ───────────────────────────

    public Mode getMode()                     { return mode; }
    public @Nullable String getRegistryKey()  { return registryKey; }

    /** Live view of the 27-slot inventory — do not mutate after export. */
    public DefaultedList<ItemStack> getItems() { return items; }

    /**
     * Returns an unmodifiable view of the current weight overrides.
     * Use {@link #setWeightOverride} and {@link #clearWeightOverrides} to mutate.
     */
    public Map<String, Integer> getWeightOverrides() {
        return java.util.Collections.unmodifiableMap(weightOverrides);
    }

    /**
     * Remove all weight overrides.
     * Called on the client-side dummy barrel before applying a {@code SyncWeights}
     * packet so the map exactly matches server truth.
     */
    public void clearWeightOverrides() {
        weightOverrides.clear();
        markDirty();
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        markDirty();
    }

    public void setRegistryKey(@Nullable String key) {
        this.registryKey = key;
        markDirty();
    }

    /**
     * Sets a per-item weight override (SNAPSHOT mode).
     *
     * @param itemId  Full item registry ID string.
     * @param weight  The weight to apply (>= 1) or 0 to remove.
     */
    public void setWeightOverride(String itemId, int weight) {
        if (weight <= 0) {
            weightOverrides.remove(itemId);
        } else {
            weightOverrides.put(itemId, weight);
        }
        markDirty();
    }

    /**
     * Drops all inventory contents as item entities (called on block break).
     * Only meaningful in SNAPSHOT mode; REGISTRY barrels typically have empty inventories.
     */
    public void dropContents(World world, BlockPos pos) {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                net.minecraft.util.ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }
        items.replaceAll(s -> ItemStack.EMPTY);
    }

    // ── NamedScreenHandlerFactory ──────────────────────────────────────────
    //
    // Implemented here rather than on the block because this class lives in the
    // api/ package which is excluded from ProGuard obfuscation.  If this class
    // were in internal/ the override would be renamed and Minecraft's interface
    // dispatch would fail with AbstractMethodError at runtime.

    @Override
    public Text getDisplayName() {
        return Text.translatable("container.sapphics-structure-library.loot_barrel");
    }

    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory,
                                              PlayerEntity player) {
        return new LootBarrelScreenHandler(syncId, playerInventory,
                new BarrelInventoryDelegate(items, this), this);
    }

    /**
     * Thin {@link Inventory} wrapper over the barrel's live {@link DefaultedList}.
     * Every mutation is immediately visible in the list and marks the block entity dirty.
     * Kept package-private; nothing outside {@link LootBarrelBlockEntity} needs it.
     */
    private static final class BarrelInventoryDelegate implements Inventory {
        private final DefaultedList<ItemStack> items;
        private final LootBarrelBlockEntity    owner;

        BarrelInventoryDelegate(DefaultedList<ItemStack> items, LootBarrelBlockEntity owner) {
            this.items = items;
            this.owner = owner;
        }

        @Override public int size()              { return items.size(); }
        @Override public boolean isEmpty()       { return items.stream().allMatch(ItemStack::isEmpty); }
        @Override public ItemStack getStack(int slot)            { return items.get(slot); }
        @Override public ItemStack removeStack(int slot, int amount) {
            ItemStack result = Inventories.splitStack(items, slot, amount);
            if (!result.isEmpty()) owner.markDirty();
            return result;
        }
        @Override public ItemStack removeStack(int slot) {
            ItemStack stack = items.get(slot);
            items.set(slot, ItemStack.EMPTY);
            if (!stack.isEmpty()) owner.markDirty();
            return stack;
        }
        @Override public void setStack(int slot, ItemStack stack) {
            items.set(slot, stack);
            if (stack.getCount() > getMaxCountPerStack()) stack.setCount(getMaxCountPerStack());
            owner.markDirty();
        }
        @Override public void markDirty()        { owner.markDirty(); }
        @Override public boolean canPlayerUse(PlayerEntity player) { return true; }
        @Override public void clear()            { items.replaceAll(s -> ItemStack.EMPTY); owner.markDirty(); }
    }

    // ── NBT serialisation ─────────────────────────────────────────────────

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putByte(NBT_MODE, mode.wireValue());
        if (registryKey != null) {
            nbt.putString(NBT_REG_KEY, registryKey);
        }
        // Always serialise items (empty list is fine for REGISTRY barrels)
        Inventories.writeNbt(nbt, items, registries);
        // Weight overrides
        NbtCompound weightsTag = new NbtCompound();
        weightOverrides.forEach((id, w) -> weightsTag.putInt(id, w));
        nbt.put(NBT_WEIGHTS, weightsTag);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        mode        = Mode.fromWire(nbt.getByte(NBT_MODE));
        registryKey = nbt.contains(NBT_REG_KEY) ? nbt.getString(NBT_REG_KEY) : null;
        Inventories.readNbt(nbt, items, registries);
        weightOverrides.clear();
        if (nbt.contains(NBT_WEIGHTS)) {
            NbtCompound weightsTag = nbt.getCompound(NBT_WEIGHTS);
            for (String key : weightsTag.getKeys()) {
                weightOverrides.put(key, weightsTag.getInt(key));
            }
        }
    }

    // ── Static helpers ────────────────────────────────────────────────────

    /**
     * Read a LootBarrelBlockEntity's mode and parameters directly from raw NBT
     * (used by {@link SmartLootEngine} during structure placement when no live
     * block entity is available — the block entity NBT was stored in the
     * {@code .tsaphstruct} file).
     */
    public static Mode readModeFromNbt(NbtCompound nbt) {
        return Mode.fromWire(nbt.getByte(NBT_MODE));
    }

    public static Map<String, Integer> readWeightOverridesFromNbt(NbtCompound nbt) {
        Map<String, Integer> out = new HashMap<>();
        if (nbt.contains("SslWeightOverrides")) {
            NbtCompound tag = nbt.getCompound("SslWeightOverrides");
            for (String key : tag.getKeys()) out.put(key, tag.getInt(key));
        }
        return out;
    }

    public static @Nullable String readRegistryKeyFromNbt(NbtCompound nbt) {
        return nbt.contains(NBT_REG_KEY) ? nbt.getString(NBT_REG_KEY) : null;
    }

    /**
     * Read the snapshot inventory from raw NBT into a new list.
     * The returned list is mutable; callers may safely modify it.
     */
    public static DefaultedList<ItemStack> readItemsFromNbt(
            NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        DefaultedList<ItemStack> out = DefaultedList.ofSize(27, ItemStack.EMPTY);
        Inventories.readNbt(nbt, out, registries);
        return out;
    }
}