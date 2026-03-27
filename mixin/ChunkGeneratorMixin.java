package com.sapphic.ssl.mixin;

import com.sapphic.ssl.api.StructureLoaderBridge;
import com.sapphic.ssl.compat.McVersion;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects into {@link ChunkGenerator#generateFeatures} to drain the SSL
 * placement queue and evaluate datapack structure definitions after each
 * freshly-generated chunk completes feature generation.
 *
 * <h2>Version compatibility</h2>
 * <p>This variant targets the descriptor used in <strong>Minecraft 1.21.1–1.21.10</strong>
 * (Yarn), where the first parameter of {@code generateFeatures} is
 * {@code StructureWorldAccess} — the <em>interface</em>:
 * <pre>  generateFeatures(StructureWorldAccess, Chunk, StructureAccessor)V</pre>
 *
 * <p>In Minecraft 1.21.11, Yarn aligned with Mojmap and the first parameter
 * changed to the <em>concrete</em> class ({@code WorldGenRegion}, which in
 * Yarn 1.21.1 is named {@code ChunkRegion}).  Because the two parameter types
 * resolve to <em>different</em> intermediary classes, a single injection cannot
 * match both.  The 1.21.11 variant lives in
 * {@link ChunkGeneratorMixin_1_21_11}.
 *
 * <h2>Activation</h2>
 * {@link SslMixinPlugin#shouldApplyMixin} explicitly enables this mixin only
 * when {@link McVersion#CURRENT} is {@link McVersion#YARN_1_21} and disables
 * it on 1.21.11+.  The {@code require = 0} on the injector is an additional
 * soft-failure safety net; the plugin routing is the primary mechanism.
 *
 * <h2>Why {@code world.toServerWorld()} rather than instanceof</h2>
 * <p>During {@code generateFeatures}, the {@code StructureWorldAccess}
 * argument is a {@code ChunkRegion} — never a {@code ServerWorld} itself.
 * {@code toServerWorld()} unwraps it safely.
 *
 * <h3>Isolation contract</h3>
 * Imports only from {@code com.sapphic.ssl.api} and {@code com.sapphic.ssl.compat}.
 * No {@code internal.*} imports permitted.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {

    @Inject(
        method = "generateFeatures(Lnet/minecraft/world/StructureWorldAccess;" +
                 "Lnet/minecraft/world/chunk/Chunk;" +
                 "Lnet/minecraft/world/gen/StructureAccessor;)V",
        at = @At("RETURN"),
        require = 0   // soft — silently skipped on 1.21.11+ where the descriptor changed
    )
    private void ssl$onFeaturesGenerated(StructureWorldAccess world,
                                         Chunk chunk,
                                         StructureAccessor structureAccessor,
                                         CallbackInfo ci) {
        ServerWorld serverWorld = world.toServerWorld();
        if (serverWorld == null) return;

        ChunkPos pos = chunk.getPos();
        StructureLoaderBridge.processQueuedGenerations(serverWorld, pos);
        StructureLoaderBridge.processChunkQueue(serverWorld, pos);
        StructureLoaderBridge.processChunkDefinitions(serverWorld, pos);
        StructureLoaderBridge.processBossSpawns(serverWorld, pos);
    }
}
