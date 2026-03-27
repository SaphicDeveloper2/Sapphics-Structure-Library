package com.sapphic.ssl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sapphic.ssl.api.StructureDefinitionReloadListener;
import com.sapphic.ssl.api.StructureLoaderBridge;
import com.sapphic.ssl.api.loot.LootBarrelBlockEntity;
import com.sapphic.ssl.api.loot.LootBarrelPackets;
import com.sapphic.ssl.api.loot.LootBarrelScreenHandler;
import com.sapphic.ssl.command.TsaphCommand;
import com.sapphic.ssl.compat.McVersion;
import com.sapphic.ssl.compat.SslCompat;
import com.sapphic.ssl.internal.loot.LootRegistry;
import com.sapphic.ssl.items.SslBlocks;
import com.sapphic.ssl.items.SslItems;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.ResourceType;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

public class SapphicsStructureLibrary implements ModInitializer {

    public static final String MOD_ID = "sapphics-structure-library";
    public static final Logger LOGGER  = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // Blocks (must be before items — ConnectorBlock item references the block)
        SslBlocks.register();

        // Items
        SslItems.register();

        // Block entity types
        // LootBarrelBlockEntity.TYPE must be set before any world loads.
        LootBarrelBlockEntity.TYPE = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(MOD_ID, "loot_barrel"),
                BlockEntityType.Builder
                        .create(LootBarrelBlockEntity::new, SslBlocks.LOOT_BARREL)
                        .build());

        // Screen handler type for the Loot Barrel custom UI.
        // ScreenHandlerType<T>(factory, featureSet) — passing null featureSet uses
        // the vanilla default (all features enabled), which is correct for a
        // developer tool screen that is never gated behind experimental flags.
        LootBarrelScreenHandler.TYPE = Registry.register(
                Registries.SCREEN_HANDLER,
                Identifier.of(MOD_ID, "loot_barrel"),
                new ScreenHandlerType<>(LootBarrelScreenHandler::new, net.minecraft.resource.featuretoggle.FeatureFlags.VANILLA_FEATURES));

        // Networking — C2S packets for the Loot Barrel UI
        LootBarrelPackets.register();

        // Commands
        CommandRegistrationCallback.EVENT.register(TsaphCommand::register);

        // Register the datapack reload listener.
        // Fires on server start and /reload — scans data/*/ssl_structures/*.json
        // and data/*/ssl_loot/*.tsaphloot across all loaded datapacks.
        ResourceManagerHelper.get(ResourceType.SERVER_DATA)
                .registerReloadListener(new StructureDefinitionReloadListener());

        // Load world-specific loot tables once the server has fully started
        // (world save path becomes available here).  Datapack loot tables are
        // already loaded by StructureDefinitionLoader above; this pass adds
        // world-specific overrides on top.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            LootRegistry.reload(server);
            LOGGER.info("SSL: Loot registry loaded — {} table(s)", LootRegistry.size());
        });

        // Re-load world-specific loot on /reload so custom tables refresh
        ServerLifecycleEvents.START_DATA_PACK_RELOAD.register((server, resourceManager) ->
                LootRegistry.reload(server));

        // Persist queue caches and end all wand sessions on server stop
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            StructureLoaderBridge.onServerStopping();
            StructureLoaderBridge.endAllSessions();
        });

        // Drain the placement queue AND run definition-based placement whenever any
        // chunk becomes accessible.
        //
        // ChunkGeneratorMixin covers the worldgen path (generateFeatures fires after
        // a chunk is freshly generated).  But when the server restarts and loads
        // already-generated chunks from disk, generateFeatures never runs — those
        // chunks arrive via the normal load path and this event is the only hook.
        //
        // Definition placement (processChunkDefinitions) is also called here so that
        // structures from datapack definitions generate correctly on chunk load in all
        // circumstances, not just during initial worldgen.
        //
        // Double-draining is safe: StructureQueue.drain() removes buckets atomically,
        // so a second call for the same chunk position returns an empty list instantly.
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            StructureLoaderBridge.processQueuedGenerations(world, chunk.getPos());
            StructureLoaderBridge.processChunkQueue(world, chunk.getPos());
            StructureLoaderBridge.processChunkDefinitions(world, chunk.getPos());
        });

        // Version detection runs in McVersion static block — log the result here
        LOGGER.info("Sapphics Structure Library 3.0 (Universal) initialised.");
        LOGGER.info("  Minecraft version  : {}", McVersion.CURRENT.name());
        LOGGER.info("  Compatibility layer: {}", SslCompat.get().layerName());
        LOGGER.info("  Full mixin support : {}", McVersion.CURRENT.hasMixinSupport());
    }
}

