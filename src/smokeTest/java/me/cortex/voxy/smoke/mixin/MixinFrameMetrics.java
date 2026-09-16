package me.cortex.voxy.smoke.mixin;

import me.cortex.voxy.smoke.FrameMetrics;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MixinFrameMetrics {
    @Inject(method = "renderFrame", at = @At("HEAD"))
    private void smoke$sampleFrame(CallbackInfo ci) {
        FrameMetrics.sample(Minecraft.getInstance().level != null);
    }
}
