package com.sapphic.ssl.mixin;

import com.sapphic.ssl.api.StructureLoaderBridge;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.11+ variant of the {@code generateFeatures} injection.
 *
 * <h2>Why this is a separate class</h2>
 * <p>In Minecraft 1.21.1–1.21.10 (Yarn), {@code ChunkGenerator.generateFeatures}
 * takes a {@code StructureWorldAccess} (an <em>interface</em>) as its first
 * parameter.  In 1.21.11, Yarn aligned with Mojmap and changed that parameter
 * to the <em>concrete</em> class ({@code WorldGenRegion} → Yarn-1.21.1 name
 * {@code ChunkRegion}).  Because the two parameter types map to <em>different</em>
 * intermediary classes, the method descriptors differ at the bytecode level and
 * a single {@code @Inject} cannot match both simultaneously.
 *
 * <h2>Descriptor differences</h2>
 * <pre>
 *   1.21.1–1.21.10  generateFeatures(StructureWorldAccess, Chunk, StructureAccessor)V
 *   1.21.11+        generateFeatures(ChunkRegion,          Chunk, StructureAccessor)V
 *                               ↑ concrete class, not the interface
 * </pre>
 * In Yarn 1.21.1, {@code ChunkRegion} is the class that Yarn 1.21.11 renamed
 * {@code WorldGenRegion} (Mojmap).  Both compile to the same intermediary class,
 * so this source file is compiled once against 1.21.1 Yarn and the descriptor
 * is remapped to the correct intermediary form at build time.
 *
 * <h2>Activation</h2>
 * {@link SslMixinPlugin#shouldApplyMixin} returns {@code true} for this class
 * only when {@link com.sapphic.ssl.compat.McVersion#CURRENT} is
 * {@link com.sapphic.ssl.compat.McVersion#MOJMAP_1_21_11}.
 * The sibling {@link ChunkGeneratorMixin} is disabled on that version.
 *
 * <h2>Isolation contract</h2>
 * Imports only from {@code com.sapphic.ssl.api} and {@code com.sapphic.ssl.compat}.
 * No {@code internal.*} imports permitted.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin_1_21_11 {

    @Inject(
        method = "generateFeatures(Lnet/minecraft/world/ChunkRegion;" +
                 "Lnet/minecraft/world/chunk/Chunk;" +
                 "Lnet/minecraft/world/gen/StructureAccessor;)V",
        at = @At("RETURN"),
        require = 0   // soft — silently skipped if 1.21.11 has not yet changed to this descriptor
    )
    private void ssl$onFeaturesGenerated_1_21_11(ChunkRegion world,
                                                  Chunk chunk,
                                                  StructureAccessor structureAccessor,
                                                  CallbackInfo ci) {
        // ChunkRegion implements StructureWorldAccess and exposes toServerWorld().
        // At intermediary level ChunkRegion == WorldGenRegion (Mojmap / Yarn 1.21.11).
        ServerWorld serverWorld = world.toServerWorld();
        if (serverWorld == null) return;

        ChunkPos pos = chunk.getPos();
        StructureLoaderBridge.processChunkQueue(serverWorld, pos);
        StructureLoaderBridge.processChunkDefinitions(serverWorld, pos);
    }
}
