package me.cortex.voxy.client.mixin.vk;

import com.mojang.renderpearl.backend.vulkan.VulkanCommandEncoder;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//Minecraft 26.2 keeps one persistent command encoder on VulkanDevice. Reading
// that field directly avoids relying on createCommandEncoder() implementation
// details when Voxy needs the command buffer currently recording the frame.
@Mixin(VulkanDevice.class)
public interface AccessorVulkanDevice {
    @Accessor("enabledFeatures")
    com.mojang.renderpearl.backend.vulkan.init.FeatureSet voxy$enabledFeatures();

    @Accessor("commandEncoder")
    VulkanCommandEncoder voxy$commandEncoder();
}
