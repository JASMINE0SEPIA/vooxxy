package me.cortex.voxy.client.core.vk;

import com.mojang.renderpearl.backend.vulkan.VulkanFeatureSets;
import com.mojang.renderpearl.backend.vulkan.init.FeatureSet;
import com.mojang.renderpearl.backend.vulkan.init.VulkanFeature;
import java.util.Set;

/** Requested before vkCreateDevice; physical support alone is insufficient. */
public final class VoxyVkFeatures {
    public static final FeatureSet TERRAIN = new FeatureSet("Voxy terrain", Set.of(), Set.of(
            new VulkanFeature(VulkanFeatureSets.VK10_FEATURES_STRUCT, "fragmentStoresAndAtomics"),
            new VulkanFeature(VulkanFeatureSets.VK10_FEATURES_STRUCT, "vertexPipelineStoresAndAtomics"),
            new VulkanFeature(VulkanFeatureSets.VK10_FEATURES_STRUCT, "shaderInt64")));
    public static final FeatureSet INDIRECT_COUNT = new FeatureSet("Voxy indirect draw count", Set.of(), Set.of(
            new VulkanFeature(VulkanFeatureSets.VK12_FEATURES_STRUCT, "drawIndirectCount")));
    private VoxyVkFeatures() {}
}
