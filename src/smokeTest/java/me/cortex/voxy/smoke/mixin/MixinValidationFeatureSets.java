package me.cortex.voxy.smoke.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.renderpearl.backend.vulkan.VulkanFeatureSets;
import com.mojang.renderpearl.backend.vulkan.init.FeatureSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.HashSet;
import java.util.Set;

/** Test-only isolation of vanilla GPU crash markers, never shipped in Voxy. */
@Mixin(VulkanFeatureSets.class)
abstract class MixinValidationFeatureSets {
    @ModifyReturnValue(method = "optionalFeatureSets", at = @At("RETURN"))
    private static Set<FeatureSet> smoke$withoutCrashMarkers(Set<FeatureSet> original) {
        if (!Boolean.getBoolean("voxy.smoke.sync")) return original;
        var features = new HashSet<>(original);
        features.remove(VulkanFeatureSets.AMD_BUFFER_MARKER_FEATURESET);
        features.remove(VulkanFeatureSets.NV_DIAGNOSTIC_CHECKPOINT_FEATURESET);
        System.out.println("VOXY_SMOKE: sync validation enabled; vanilla crash-marker instrumentation isolated");
        return features;
    }
}
