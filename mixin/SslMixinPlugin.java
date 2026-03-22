package com.sapphic.ssl.mixin;

import com.sapphic.ssl.compat.McVersion;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Mixin config plugin that runs early version detection before any mixin fires.
 *
 * <h2>What this does</h2>
 * <ul>
 *   <li>Triggers {@link McVersion} static initialisation so the version is
 *       detected and logged before the first mixin injection fires.</li>
 *   <li>Disables {@code ChunkGeneratorMixin_1_21_11} unconditionally — it was
 *       written for a planned 1.21.11 port that is not yet active.  All
 *       supported versions (1.21.1–1.21.3) use {@code ChunkGeneratorMixin}.</li>
 *   <li>All other mixins are always enabled.</li>
 * </ul>
 *
 * <p>Registered in {@code sapphics-structure-library.mixins.json}:
 * <pre>  "plugin": "com.sapphic.ssl.mixin.SslMixinPlugin"</pre>
 *
 * <p>This class is in {@code com.sapphic.ssl.mixin}, kept fully by ProGuard,
 * so its name survives obfuscation — required for Mixin to find it at startup.
 */
public final class SslMixinPlugin implements IMixinConfigPlugin {

    private static final String MIXIN_CHUNK_GEN_1_21_11 = "ChunkGeneratorMixin_1_21_11";

    @Override
    public void onLoad(String mixinPackage) {
        // Triggers McVersion static block — detection + logging happens here,
        // before any mixin injection, guaranteed.
        McVersion ignored = McVersion.CURRENT;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String simpleName = mixinClassName.contains(".")
                ? mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1)
                : mixinClassName;

        // ChunkGeneratorMixin_1_21_11 targets descriptor changes planned for
        // a future 1.21.11 port.  Disable it for the current 1.21.1–1.21.3 range.
        if (MIXIN_CHUNK_GEN_1_21_11.equals(simpleName)) return false;

        return true;
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
