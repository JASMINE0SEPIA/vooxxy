package me.cortex.voxy.client.mixin.vk;

import com.mojang.renderpearl.backend.vulkan.VulkanFeatureSets;
import com.mojang.renderpearl.backend.vulkan.init.FeatureSet;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import me.cortex.voxy.client.core.vk.VoxyVkFeatures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.HashSet;
import java.util.Set;

@Mixin(VulkanFeatureSets.class)
public class MixinVulkanFeatureSets {
    @ModifyReturnValue(method = "optionalFeatureSets", at = @At("RETURN"))
    private static Set<FeatureSet> voxy$requestDeviceFeatures(Set<FeatureSet> original) {
        var features = new HashSet<>(original);
        features.add(VoxyVkFeatures.TERRAIN);
        features.add(VoxyVkFeatures.INDIRECT_COUNT);
        return features;
    }
}
