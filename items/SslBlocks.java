package com.sapphic.ssl.items;

import com.sapphic.ssl.SapphicsStructureLibrary;
import net.minecraft.block.AbstractBlock;
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
