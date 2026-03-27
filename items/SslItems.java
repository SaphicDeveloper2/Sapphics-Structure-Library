package com.sapphic.ssl.items;

import com.sapphic.ssl.SapphicsStructureLibrary;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Registers all items and the creative tab contributed by Sapphics Structure Library.
 */
public final class SslItems {

    // ── Items ─────────────────────────────────────────────────────────────

    public static final SelectionWandItem SELECTION_WAND = register(
            "selection_wand",
            new SelectionWandItem(new Item.Settings().maxCount(1)));

    /**
     * BlockItem for {@link SslBlocks#CONNECTOR_BLOCK}.
     * Registered after {@link SslBlocks} initialises the block.
     */
    public static final BlockItem CONNECTOR_BLOCK_ITEM = register(
            "connector_block",
            new BlockItem(SslBlocks.CONNECTOR_BLOCK, new Item.Settings()));

    /**
     * BlockItem for {@link SslBlocks#LOOT_BARREL}.
     * Added to the SSL creative tab for easy access.
     */
    public static final BlockItem LOOT_BARREL_ITEM = register(
            "loot_barrel",
            new BlockItem(SslBlocks.LOOT_BARREL, new Item.Settings()));

    /**
     * BlockItem for {@link SslBlocks#STRUCTURE_TERRAIN}.
     * Added to the SSL creative tab so developers can grab it without /give.
     */
    public static final BlockItem STRUCTURE_TERRAIN_ITEM = register(
            "structure_terrain",
            new BlockItem(SslBlocks.STRUCTURE_TERRAIN, new Item.Settings()));

    // ── Creative tab ──────────────────────────────────────────────────────

    public static final RegistryKey<ItemGroup> TOOLS_GROUP_KEY = RegistryKey.of(
            RegistryKeys.ITEM_GROUP,
            Identifier.of(SapphicsStructureLibrary.MOD_ID, "tools"));

    private SslItems() {}

    // ── Registration ──────────────────────────────────────────────────────

    private static <T extends Item> T register(String name, T item) {
        return Registry.register(Registries.ITEM,
                Identifier.of(SapphicsStructureLibrary.MOD_ID, name),
                item);
    }

    /**
     * Triggers static initialisation and registers the creative-mode item group.
     * {@link SslBlocks#register()} must be called first so the block exists when
     * {@link #CONNECTOR_BLOCK_ITEM} is initialised.
     * Call from {@link SapphicsStructureLibrary#onInitialize()}.
     */
    public static void register() {
        // Static fields above are initialised by class loading (after SslBlocks).

        // Register the SSL creative tab.  Icon = selection wand.
        Registry.register(Registries.ITEM_GROUP, TOOLS_GROUP_KEY,
                FabricItemGroup.builder()
                        .icon(() -> new ItemStack(SELECTION_WAND))
                        .displayName(Text.translatable("itemGroup.sapphics-structure-library.tools"))
                        .entries((context, entries) -> {
                            entries.add(SELECTION_WAND);
                            entries.add(CONNECTOR_BLOCK_ITEM);
                            entries.add(STRUCTURE_TERRAIN_ITEM);
                            entries.add(LOOT_BARREL_ITEM);
                            entries.add(new ItemStack(Items.DEBUG_STICK));
                        })
                        .build());

        SapphicsStructureLibrary.LOGGER.info("SSL: Items registered.");
    }
}

