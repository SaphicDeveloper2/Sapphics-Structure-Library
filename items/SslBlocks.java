package com.sapphic.ssl.items;

import com.sapphic.ssl.SapphicsStructureLibrary;
import com.sapphic.ssl.items.LootBarrelBlock;
import com.sapphic.ssl.items.StructureTerrainBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

/**
 * Registers all blocks contributed by Sapphics Structure Library.
 *
 * <p>Block items are registered separately in {@link SslItems} so they can be
 * added to the creative tab alongside the Selection Wand.
 */
public final class SslBlocks {

    /**
     * The Loot Barrel — a developer-only authoring block that encodes loot data
     * for structure pieces.  At generation time it is compiled into a vanilla chest
     * by {@link com.sapphic.ssl.internal.loot.SmartLootEngine}.  Never appears in
     * the final generated world.
     *
     * <p>Intentionally absent from any creative tab; obtain via
     * {@code /give @s sapphics-structure-library:loot_barrel}.
     */
    public static final LootBarrelBlock LOOT_BARREL = register(
            "loot_barrel",
            new LootBarrelBlock(AbstractBlock.Settings.create()
                    .sounds(net.minecraft.sound.BlockSoundGroup.WOOD)
                    .strength(2.5f)
                    .nonOpaque()));

    /**
     * Structure Terrain — a glass-textured placeholder that defers to world terrain
     * at generation time.  The loader skips placement for any block in this palette
     * entry, leaving the world block at that position completely undisturbed.
     *
     * <p>Obtain via {@code /give @s sapphics-structure-library:structure_terrain}.
     */
    public static final StructureTerrainBlock STRUCTURE_TERRAIN = register(
            "structure_terrain",
            new StructureTerrainBlock(
                    AbstractBlock.Settings.create()
                            .mapColor(MapColor.CYAN)
                            .strength(0.3f)          // same as glass
                            .nonOpaque()
                            .allowsSpawning((s, w, p, t) -> false)
                            .solidBlock((s, w, p) -> false)
                            .suffocates((s, w, p) -> false)
                            .blockVision((s, w, p) -> false)));

    /**
     * The Connector Block — placed inside multi-struct piece selections to mark
     * connection endpoints.  The block's horizontal facing defines the outward
     * direction the next piece will attach to.
     *
     * <p>Connector blocks are automatically removed and replaced with the floor
     * block beneath them when a piece is placed by the procedural engine.  They
     * never appear in the final generated world.
     */
    public static final ConnectorBlock CONNECTOR_BLOCK = register(
            "connector_block",
            new ConnectorBlock(AbstractBlock.Settings.create()
                    .sounds(BlockSoundGroup.STONE)
                    .strength(1.5f)
                    .requiresTool()));

    private SslBlocks() {}

    private static <T extends Block> T register(String name, T block) {
        return Registry.register(Registries.BLOCK,
                Identifier.of(SapphicsStructureLibrary.MOD_ID, name),
                block);
    }

    /** Triggers static initialisation — call from {@link SapphicsStructureLibrary#onInitialize()}. */
    public static void register() {
        SapphicsStructureLibrary.LOGGER.info("SSL: Blocks registered.");
    }
}
