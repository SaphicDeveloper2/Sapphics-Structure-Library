package com.sapphic.ssl.api.loot;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;

/**
 * Custom screen handler for {@link LootBarrelBlockEntity}.
 *
 * <p>Provides 27 barrel inventory slots (the weight palette) plus the standard
 * 36-slot player inventory, with a synced {@link Property} that carries the
 * barrel's current {@link LootBarrelBlockEntity.Mode} to the client so the
 * screen can render the correct UI panel.
 *
 * <p>Lives in {@code api/} to avoid ProGuard rename — this class implements
 * the vanilla {@link ScreenHandler} interface and must keep its method names.
 *
 * <p><strong>Obfuscated in production builds — class name only, not method names
 * (api/ exclusion rule).</strong>
 */
public class LootBarrelScreenHandler extends ScreenHandler {

    /**
     * Registered in {@code SapphicsStructureLibrary.onInitialize()}.
     * Not a compile-time constant — populated once the game registry is live.
     */
    public static ScreenHandlerType<LootBarrelScreenHandler> TYPE;

    // ── Synced property indices ────────────────────────────────────────────
    /** Property index 0 — current {@link LootBarrelBlockEntity.Mode} wire byte (0 or 1). */
    private static final int PROP_MODE = 0;

    // ── Slot layout ────────────────────────────────────────────────────────
    /** First barrel slot index. */
    public static final int BARREL_SLOT_START = 0;
    /** Exclusive end of barrel slots. */
    public static final int BARREL_SLOT_END   = 27;
    /** First player inventory slot index. */
    public static final int PLAYER_INV_START  = 27;

    // ── Panel + slot layout ───────────────────────────────────────────────
    // PANEL_H: height of the custom weight/registry panel above the vanilla container.
    // All slot Y positions are standard vanilla generic_27 offsets shifted down by PANEL_H.
    // The drawBackground call in LootBarrelScreen draws the vanilla texture at y + PANEL_H
    // so the texture slot markings line up with the actual slot widgets.
    public static final int PANEL_H      = 78;

    public static final int GRID_X       = 8;
    // Barrel slots: vanilla container row 0 is at y=18, shifted by PANEL_H
    public static final int GRID_Y       = 18 + PANEL_H;    // = 96
    // Player inventory: vanilla position y=84, shifted
    public static final int PLAYER_INV_Y = 84 + PANEL_H;    // = 162
    // Hotbar: vanilla position y=142, shifted
    public static final int HOTBAR_Y     = 142 + PANEL_H;   // = 220

    private final Inventory barrelInventory;
    private final LootBarrelBlockEntity barrel;

    // ── Construction ──────────────────────────────────────────────────────

    /** Server-side constructor — called by {@link LootBarrelBlockEntity#createMenu}. */
    public LootBarrelScreenHandler(int syncId,
                                   PlayerInventory playerInventory,
                                   Inventory barrelInv,
                                   LootBarrelBlockEntity barrel) {
        super(TYPE, syncId);
        this.barrelInventory = barrelInv;
        this.barrel          = barrel;

        checkSize(barrelInv, 27);
        barrelInv.onOpen(playerInventory.player);

        // ── 27 barrel slots (3 rows × 9) ──────────────────────────────────
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(barrelInv, row * 9 + col,
                        GRID_X + col * 18,
                        GRID_Y  + row * 18));
            }
        }

        // ── Player inventory (3 rows) ──────────────────────────────────────
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        GRID_X + col * 18,
                        PLAYER_INV_Y + row * 18));
            }
        }

        // ── Player hotbar ─────────────────────────────────────────────────
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col,
                    GRID_X + col * 18,
                    HOTBAR_Y));
        }

        // ── Synced property: mode ──────────────────────────────────────────
        // Sends barrel.getMode().wireValue() to the client on every tick that it changes.
        addProperty(new net.minecraft.screen.Property() {
            @Override public int get()         { return barrel.getMode().wireValue(); }
            @Override public void set(int val) { barrel.setMode(LootBarrelBlockEntity.Mode.fromWire((byte) val)); }
        });
    }

    /** Client-side constructor — called by the {@link ScreenHandlerType} factory. */
    public LootBarrelScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory,
             new net.minecraft.inventory.SimpleInventory(27),
             createDummyBarrel());
    }

    private static LootBarrelBlockEntity createDummyBarrel() {
        // BlockEntity validates that the block state's block matches the
        // registered BlockEntityType — must pass the loot barrel's own state,
        // not air (which would throw IllegalStateException on the client).
        return new LootBarrelBlockEntity(net.minecraft.util.math.BlockPos.ORIGIN,
                com.sapphic.ssl.items.SslBlocks.LOOT_BARREL.getDefaultState());
    }

    // ── Accessors used by LootBarrelScreen ────────────────────────────────

    public Inventory getBarrelInventory()       { return barrelInventory; }
    public LootBarrelBlockEntity getBarrel()    { return barrel; }

    /**
     * Live mode read — driven by the synced property on the client, by direct
     * field read on the server.
     */
    public LootBarrelBlockEntity.Mode getMode() {
        return barrel.getMode();
    }

    // ── ScreenHandler contract ─────────────────────────────────────────────

    @Override
    public boolean canUse(PlayerEntity player) {
        return barrelInventory.canPlayerUse(player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.hasStack()) {
            ItemStack stack  = slot.getStack();
            result           = stack.copy();

            if (index < BARREL_SLOT_END) {
                // Barrel → player inventory
                if (!insertItem(stack, PLAYER_INV_START, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Player → barrel
                if (!insertItem(stack, BARREL_SLOT_START, BARREL_SLOT_END, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (stack.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }
        }
        return result;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        barrelInventory.onClose(player);
    }
}
