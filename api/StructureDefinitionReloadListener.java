package com.sapphic.ssl.api;

import com.sapphic.ssl.internal.StructureDefinitionLoader;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

/**
 * Public-API wrapper around the internal {@link StructureDefinitionLoader}.
 *
 * <h3>Why this class exists</h3>
 * <p>{@link StructureDefinitionLoader} lives in {@code com.sapphic.ssl.internal},
 * which is fully obfuscated by ProGuard in production builds.  The Fabric resource
 * reload system resolves {@code getFabricId()} by its exact method name at runtime
 * via the {@code IdentifiableResourceReloadListener} interface.  When the implementing
 * class is obfuscated, the method is renamed and the interface contract breaks,
 * causing an {@link AbstractMethodError} on server start.
 *
 * <p>This wrapper is placed in {@code com.sapphic.ssl.api}, which is fully
 * preserved by ProGuard ({@code -keep}).  The {@code getFabricId()} and
 * {@code reload()} method names therefore survive intact, and the delegation
 * call to the internal loader is the only line that is obfuscated.
 *
 * <p>Register via:
 * <pre>
 *   ResourceManagerHelper.get(ResourceType.SERVER_DATA)
 *       .registerReloadListener(new StructureDefinitionReloadListener());
 * </pre>
 */
public final class StructureDefinitionReloadListener
        implements SimpleSynchronousResourceReloadListener {

    private final StructureDefinitionLoader delegate = new StructureDefinitionLoader();

    @Override
    public Identifier getFabricId() {
        return Identifier.of("ssl", "structure_definitions");
    }

    @Override
    public void reload(ResourceManager manager) {
        delegate.reload(manager);
    }
}
