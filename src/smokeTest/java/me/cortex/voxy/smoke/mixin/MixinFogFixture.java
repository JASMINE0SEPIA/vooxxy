package me.cortex.voxy.smoke.mixin;

import me.cortex.voxy.smoke.FogSmokeProbe;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Controlled atmosphere before Voxy's normal fog-mode policy. Test mod only. */
@Mixin(AtmosphericFogEnvironment.class)
abstract class MixinFogFixture {
    @Inject(method = "setupFog", at = @At("RETURN"))
    private void smoke$atmosphere(FogData data, Camera camera, ClientLevel level, float distance,
                                  DeltaTracker delta, CallbackInfo ci) {
        FogSmokeProbe.applyAtmosphere(data);
    }
}
