package me.cortex.voxy.client.mixin.vk;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import me.cortex.voxy.client.core.vk.render.VkFrameBridge;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;
import java.util.OptionalDouble;

@Mixin(LevelRenderer.class)
public abstract class MixinLevelRendererVkFrame {
    @Shadow @Final private LevelTargetBundle targets;

    /** Finish solid rendering, record native work, then resume a LOAD pass. */
    @WrapOperation(method = "lambda$addMainPass$0", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;executeSolid(Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
    private void voxy$renderBetweenSolidAndTransparency(LevelRenderer instance,
            ChunkSectionsToRender sections, FeatureRenderDispatcher.PreparedFrame features,
            RenderPass pass, Operation<Void> original, @Local LocalRef<RenderPass> callerPass) {
        VkFrameBridge.reset();
        original.call(instance, sections, features, pass);
        if (!VkFrameBridge.hasFrame()) return;
        pass.close();
        var target = this.targets.main.get();
        try {
            VkFrameBridge.render(target);
        } finally {
            var resumed = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "Main after Voxy Vulkan", target.getColorTextureView(), Optional.empty(),
                    target.getDepthTextureView(), OptionalDouble.empty());
            callerPass.set(resumed);
            RenderSystem.bindDefaultUniforms(resumed);
        }
    }
}
