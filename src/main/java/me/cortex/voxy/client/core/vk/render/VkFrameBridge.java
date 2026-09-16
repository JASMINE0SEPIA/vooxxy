package me.cortex.voxy.client.core.vk.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.client.core.vk.MinecraftVkHost;
import me.cortex.voxy.client.core.vk.MinecraftVkHostAdapter;
import me.cortex.voxy.common.Logger;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;

import java.lang.ref.WeakReference;

/** Render-thread handoff from Sodium's draw to the enclosing pass boundary. */
public final class VkFrameBridge {
    private static ChunkRenderMatrices matrices;
    private static double x, y, z;
    private static WeakReference<VkRenderCore> failedCore = new WeakReference<>(null);

    private VkFrameBridge() {}

    public static void reset() { matrices = null; }

    public static boolean hasFailed() { return failedCore.get() != null; }

    public static void capture(ChunkRenderMatrices frameMatrices, double camX, double camY, double camZ) {
        matrices = frameMatrices;
        x = camX;
        y = camY;
        z = camZ;
    }

    public static boolean hasFrame() {
        var renderer = IVoxyRenderSystemHolder.getNullable();
        return matrices != null && MinecraftVkHost.get() instanceof MinecraftVkHostAdapter
                && renderer != null && renderer.vkCore != null && failedCore.get() != renderer.vkCore;
    }

    public static void render(RenderTarget target) {
        var frameMatrices = matrices;
        reset();
        var renderer = IVoxyRenderSystemHolder.getNullable();
        if (frameMatrices == null || renderer == null || renderer.vkCore == null
                || !(MinecraftVkHost.get() instanceof MinecraftVkHostAdapter adapter)) return;
        var core = renderer.vkCore;
        try {
            core.renderFrame(target, adapter, frameMatrices, x, y, z);
            failedCore.clear();
        } catch (RuntimeException failure) {
            failedCore = new WeakReference<>(core);
            Logger.error("Voxy 26.3 Vulkan frame failed; renderer disabled until world reload", failure);
        }
    }
}
