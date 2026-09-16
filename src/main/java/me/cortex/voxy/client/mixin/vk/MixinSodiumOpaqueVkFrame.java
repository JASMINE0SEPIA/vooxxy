package me.cortex.voxy.client.mixin.vk;

import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuSampler;
import me.cortex.voxy.client.core.vk.render.VkFrameBridge;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.oit.OitStage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 26.3 draws Sodium inside a caller-owned render pass. Only capture here. */
@Mixin(value = SodiumWorldRenderer.class, remap = false)
public class MixinSodiumOpaqueVkFrame {
    @Inject(method = "drawChunkLayer", at = @At("TAIL"), remap = false)
    private void voxy$captureOpaqueFrame(RenderPass pass, ChunkSectionLayerGroup group,
            ChunkRenderMatrices matrices, double x, double y, double z,
            GpuSampler sampler, OitStage stage, CallbackInfo ci) {
        if (group == ChunkSectionLayerGroup.OPAQUE) {
            VkFrameBridge.capture(matrices, x, y, z);
        }
    }
}
