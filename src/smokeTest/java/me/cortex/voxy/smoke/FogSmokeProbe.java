package me.cortex.voxy.smoke;

import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.client.core.NormalRenderPipeline.FogMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.world.level.material.FogType;

/** Fixed-camera before/after images, followed by real water/lava/status fog. */
public final class FogSmokeProbe {
    private static boolean forced;
    private static boolean dense;
    private final float originalDistance = VoxyConfig.CONFIG.sectionRenderDistance;

    public static void applyAtmosphere(FogData data) {
        if (!forced) return;
        data.environmentalStart = dense ? 32f : 100_000_000f;
        data.environmentalEnd = dense ? 512f : 100_000_000f;
        data.color.set(0.65f, 0.75f, 0.9f, 1f);
    }

    public void tick(Minecraft client, int tick) {
        if (tick == 1) {
            forced = true;
            dense = false;
            client.options.renderDistance().set(10);
            client.options.improvedTransparency().set(false);
            mode(FogMode.FOG_AND_FADE);
            command(client, "gamemode spectator");
            // Remove residue from earlier versions of this isolated fixture.
            command(client, "fill 368 145 -16 400 161 16 minecraft:air");
            command(client, "fill 380 240 -4 388 248 4 minecraft:air");
            command(client, "tp @s 384 150 0 90 20");
            command(client, "time set noon");
            command(client, "weather clear");
            command(client, "effect clear @s");
        }
        if (tick == 160) capture(client, "clear");
        if (tick == 180) dense = true;
        if (tick == 280) capture(client, "atmosphere");
        if (tick == 300) client.options.improvedTransparency().set(true);
        if (tick == 380) capture(client, "oit");
        if (tick == 400) mode(FogMode.OFF);
        if (tick == 460) {
            capture(client, "off");
            if (fog(client).environmentalEnd < 1_000_000) throw new IllegalStateException("OFF did not remove atmospheric fog");
        }
        if (tick == 480) {
            forced = false;
            mode(FogMode.FOG_AND_FADE);
            command(client, "fill 380 240 -4 388 248 4 minecraft:stone hollow");
            command(client, "fill 381 241 -3 387 247 3 minecraft:water");
            command(client, "tp @s 384 242 0 90 20");
        }
        if (tick == 560) {
            requireFluid(client, FogType.WATER);
            capture(client, "water");
        }
        if (tick == 580) {
            command(client, "fill 381 241 -3 387 247 3 minecraft:air");
            command(client, "effect give @s minecraft:blindness 30 0 true");
        }
        if (tick == 640) {
            if (fog(client).environmentalEnd > 10) throw new IllegalStateException("Blindness fog missing");
            capture(client, "blindness");
        }
        if (tick == 660) {
            command(client, "effect clear @s");
            command(client, "fill 381 241 -3 387 247 3 minecraft:lava");
        }
        if (tick == 740) {
            requireFluid(client, FogType.LAVA);
            capture(client, "lava");
        }
        if (tick == 760) {
            command(client, "fill 380 240 -4 388 248 4 minecraft:air");
            command(client, "tp @s 384 150 0 90 20");
            command(client, "effect clear @s");
        }
        if (tick == 780) {
            forced = true;
            dense = false;
            VoxyConfig.CONFIG.sectionRenderDistance = 0.5f;
            mode(FogMode.OFF);
        }
        if (tick == 880) capture(client, "horizon-hard");
        if (tick == 900) mode(FogMode.FADE);
        if (tick == 1000) capture(client, "horizon-fade");
        if (tick == 1040) {
            var renderer = IVoxyRenderSystemHolder.getNullable();
            if (renderer == null || renderer.vkCore == null || !renderer.vkCore.hasRenderedGeometry()) {
                throw new IllegalStateException("Fog probe did not render native terrain");
            }
            System.out.println("VOXY_FOG: all scenarios completed; saving and closing");
            forced = false;
            VoxyConfig.CONFIG.sectionRenderDistance = originalDistance;
            VoxyConfig.CONFIG.setFogMode(FogMode.FOG_AND_FADE);
            client.disconnectWithSavingScreen();
            if (me.cortex.voxy.client.core.vk.VkBuffer.getCount() != 0
                    || me.cortex.voxy.client.core.vk.VkImage2D.getCount() != 0) {
                throw new IllegalStateException("Fog probe retained GPU resources after disconnect");
            }
            System.out.println("VOXY_FOG: all Voxy GPU resource wrappers released");
            client.stop();
        }
    }

    private static FogData fog(Minecraft client) {
        return client.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.fogData;
    }

    private static void requireFluid(Minecraft client, FogType expected) {
        if (client.gameRenderer.mainCamera().getFluidInCamera() != expected || fog(client).environmentalEnd >= 1_000_000) {
            throw new IllegalStateException("Missing " + expected + " fog");
        }
    }

    private static void command(Minecraft client, String command) { client.player.connection.sendCommand(command); }

    private static void mode(FogMode mode) {
        VoxyConfig.CONFIG.setFogMode(mode);
        var holder = IVoxyRenderSystemHolder.getNullableHolder();
        holder.voxy$shutdownRenderer();
        holder.voxy$createRenderer();
    }

    private static void capture(Minecraft client, String name) {
        var data = fog(client);
        System.out.println("VOXY_FOG: " + name + " environmental=" + data.environmentalStart + "/" + data.environmentalEnd
                + " mode=" + VoxyConfig.CONFIG.getFogMode());
        Screenshot.grab(client.gameDirectory, "fog-" + name + ".png", client.gameRenderer.mainRenderTarget(), 1,
                message -> System.out.println("VOXY_FOG: " + message.getString()));
    }
}
