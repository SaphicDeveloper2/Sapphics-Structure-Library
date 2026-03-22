package com.sapphic.ssl.api;

import net.minecraft.server.world.ServerWorld;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Public interface for exporting a region of the world to a {@code .tsaphstruct} file.
 *
 * <p>Obtain an instance via {@link StructureLoaderBridge#getExporter()}.
 */
public interface IStructureExporter {

    /**
     * Read all blocks within {@code box} from {@code world}, encode them into
     * the proprietary binary format, and write the result to {@code destination}.
     *
     * @param world       Source server world.
     * @param box         The selection bounding box (may exceed 48 × 48 × 48).
     * @param destination Output path.  The {@link SaphStructFormat#EXTENSION} suffix
     *                    is appended automatically if absent.
     * @throws IOException          On I/O failure.
     * @throws IllegalStateException If any chunk in the selection is unloaded.
     */
    void export(ServerWorld world, StructureBoundingBox box, Path destination)
            throws IOException;
}
