package me.cortex.voxy.smoke;

import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.client.core.backend.VoxyGraphicsBackend;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.nio.file.Files;
import java.nio.file.Path;

/** Reproducible local single-player smoke run. Only loaded by runSmokeClient. */
public final class VulkanSmokeClient implements ClientModInitializer {
    private static final String WORLD = "voxy-vulkan-26.3-smoke";
    private boolean opened;
    private int ticks;
    private int worldTicks;
    private final boolean lifecycle = Boolean.getBoolean("voxy.smoke.lifecycle");
    private java.util.concurrent.CompletableFuture<Void> reload;
    private final boolean importTest = Boolean.getBoolean("voxy.smoke.import");
    private WorldImportProbe importProbe;
    private final boolean travel = Boolean.getBoolean("voxy.smoke.travel");

    @Override
    public void onInitializeClient() {
        System.out.println("VOXY_SMOKE: synchronization validation requested=" + Boolean.getBoolean("voxy.smoke.sync"));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft client) {
        ticks++;
        if (!opened && client.gui.screen() instanceof TitleScreen && ticks > 40) {
            opened = true;
            verifyStatePersistence();
            verifyFailedWorldCleanup();
            if (VoxyGraphicsBackend.current() != VoxyGraphicsBackend.VULKAN) {
                throw new IllegalStateException("Smoke test requires active Vulkan: " + VoxyGraphicsBackend.statusLine());
            }
            client.options.pauseOnLostFocus = false;
            client.options.renderDistance().set(6);
            client.options.improvedTransparency().set(false);
            client.options.fov().set(70);
            var flows = client.createWorldOpenFlows();
            if (Files.exists(Path.of("saves", WORLD, "level.dat"))) {
                flows.openWorld(WORLD, () -> { throw new IllegalStateException("Smoke world open canceled"); });
            } else {
                flows.createFreshLevel(WORLD,
                        new LevelSettings("Voxy Vulkan 26.3 smoke", GameType.CREATIVE,
                                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false),
                                true, WorldDataConfiguration.DEFAULT),
                        new WorldOptions(2632026L, true, false), WorldPresets::createNormalWorldDimensions,
                        new TitleScreen());
            }
            System.out.println("VOXY_SMOKE: opening isolated test world");
        }
        if (client.level == null || client.player == null) return;
        if (me.cortex.voxy.client.core.vk.render.VkFrameBridge.hasFailed()) {
            throw new IllegalStateException("Native Vulkan frame failed; see preceding renderer error");
        }
        worldTicks++;
        if (importTest && worldTicks == 100) importProbe = new WorldImportProbe(client.level, Path.of("import-fixture"));
        if (importTest && worldTicks == 350) importProbe.verifyAndClose();
        if (worldTicks == 80) {
            client.player.connection.sendCommand("gamemode spectator");
            client.player.connection.sendCommand("tp @s 0 140 0 0 20");
            client.player.connection.sendCommand("time set noon");
            client.player.connection.sendCommand("weather clear");
        }
        if (worldTicks == 100) client.player.connection.sendCommand("fill 16 160 16 23 161 23 minecraft:air");
        if (worldTicks == 180) {
            client.player.connection.sendCommand("fill 16 160 16 23 160 23 minecraft:diamond_block");
            client.player.connection.sendCommand("setblock 18 161 18 minecraft:glowstone");
        }
        if (worldTicks == 260) {
            verifyVoxel(18, 160, 18, net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK, false);
            verifyVoxel(18, 161, 18, net.minecraft.world.level.block.Blocks.GLOWSTONE, true);
            System.out.println("VOXY_SMOKE: live block placement and block-light ingestion passed");
        }
        if (worldTicks == 280) client.player.connection.sendCommand("fill 16 160 16 23 161 23 minecraft:air");
        if (worldTicks == 380) {
            verifyVoxel(18, 160, 18, net.minecraft.world.level.block.Blocks.AIR, false);
            verifyVoxel(18, 161, 18, net.minecraft.world.level.block.Blocks.AIR, false);
            System.out.println("VOXY_SMOKE: live block removal ingestion passed");
        }
        if (worldTicks % 200 == 0) {
            var renderer = IVoxyRenderSystemHolder.getNullable();
            System.out.println("VOXY_SMOKE: tick=" + worldTicks + " backend=" + VoxyGraphicsBackend.statusLine()
                    + " core=" + (renderer != null && renderer.vkCore != null));
            if (renderer != null && renderer.vkCore != null) {
                var debug = new java.util.ArrayList<String>();
                renderer.vkCore.addDebugInfo(debug);
                debug.forEach(line -> System.out.println("VOXY_SMOKE: " + line));
            }
        }
        if (worldTicks == 400 || worldTicks == 800 || worldTicks == 900
                || (lifecycle && (worldTicks == 1300 || worldTicks == 1500 || worldTicks == 1950 || worldTicks == 2200))
                || (travel && (worldTicks == 2600 || worldTicks == 3400))) {
            Screenshot.grab(client.gameDirectory, "smoke-" + worldTicks + ".png",
                    client.gameRenderer.mainRenderTarget(), 1,
                    message -> System.out.println("VOXY_SMOKE: screenshot " + message.getString()));
        }
        if (worldTicks == 500) client.player.connection.sendCommand("tp @s 384 150 0 90 20");
        if (worldTicks == 820) {
            System.out.println("VOXY_SMOKE: disabling LOD renderer for same-camera visual control");
            IVoxyRenderSystemHolder.getNullableHolder().voxy$shutdownRenderer();
        }
        if (worldTicks == 940) {
            System.out.println("VOXY_SMOKE: rebuilding native LOD renderer");
            IVoxyRenderSystemHolder.getNullableHolder().voxy$createRenderer();
        }
        if (lifecycle) {
            if (worldTicks == 1120) {
                client.options.fov().set(90);
                client.options.renderDistance().set(4);
                client.getWindow().setWindowed(1024, 576);
                System.out.println("VOXY_SMOKE: changed FOV, distance and window size");
            }
            if (worldTicks == 1320) {
                client.options.improvedTransparency().set(true);
                System.out.println("VOXY_SMOKE: enabled improved transparency (OIT)");
            }
            if (worldTicks == 1540) {
                reload = client.reloadResourcePacks();
                System.out.println("VOXY_SMOKE: requested resource reload");
            }
            if (worldTicks == 1740) {
                if (reload == null || !reload.isDone()) throw new IllegalStateException("Resource reload did not complete");
                reload.join();
                System.out.println("VOXY_SMOKE: resource reload completed");
            }
            if (worldTicks == 1760) client.player.connection.sendCommand("execute in minecraft:the_nether run tp @s 0 80 0 0 20");
            if (worldTicks == 1980) client.player.connection.sendCommand("execute in minecraft:overworld run tp @s 384 150 0 90 20");
        }
        if (travel) {
            if (worldTicks == 2260) client.player.connection.sendCommand("execute in minecraft:the_end run tp @s 0 120 0 0 30");
            if (worldTicks == 2640) client.player.connection.sendCommand("execute in minecraft:overworld run tp @s 384 150 0 90 20");
            if (worldTicks >= 2700 && worldTicks <= 3300 && worldTicks % 50 == 0) {
                int distance = 384 + ((worldTicks - 2700) / 50) * 96;
                client.player.connection.sendCommand("tp @s " + distance + " 140 0 90 20");
            }
            if (worldTicks == 3340) client.player.connection.sendCommand("tp @s 384 150 0 90 20");
        }
        if (worldTicks == (travel ? 3450 : lifecycle ? 2250 : 1100)) {
            var renderer = IVoxyRenderSystemHolder.getNullable();
            if (renderer == null || renderer.vkCore == null || !renderer.vkCore.hasRenderedGeometry()) {
                throw new IllegalStateException("Native Vulkan core did not render populated geometry");
            }
            System.out.println("VOXY_SMOKE: finished world smoke interval; saving and closing");
            FrameMetrics.report();
            client.disconnectWithSavingScreen();
            if (me.cortex.voxy.client.core.vk.VkBuffer.getCount() != 0
                    || me.cortex.voxy.client.core.vk.VkImage2D.getCount() != 0) {
                throw new IllegalStateException("Voxy GPU resource wrappers remained after world disconnect");
            }
            System.out.println("VOXY_SMOKE: all Voxy GPU resource wrappers released");
            client.stop();
        }
    }

    private static void verifyVoxel(int x, int y, int z, net.minecraft.world.level.block.Block expected, boolean lit) {
        var renderer = IVoxyRenderSystemHolder.getNullable();
        if (renderer == null) throw new IllegalStateException("No renderer for voxel ingestion check");
        var engine = renderer.getEngine();
        var section = engine.acquireIfExists(0, x >> 5, y >> 5, z >> 5);
        if (section == null) throw new IllegalStateException("Edited section was not ingested");
        try {
            long voxel = section.copyData()[me.cortex.voxy.common.world.WorldSection.getIndex(x & 31, y & 31, z & 31)];
            var state = engine.getMapper().getBlockStateFromBlockId(me.cortex.voxy.common.world.other.Mapper.getBlockId(voxel));
            if (!state.is(expected)) throw new IllegalStateException("Stale edited voxel: " + state + ", expected " + expected);
            if (lit && (me.cortex.voxy.common.world.other.Mapper.getLightId(voxel) >> 4) != 15) {
                throw new IllegalStateException("Emissive block light was not ingested");
            }
        } finally {
            section.release();
        }
    }

    private static void verifyStatePersistence() {
        int id = 0;
        for (var block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
            for (var state : block.getStateDefinition().getPossibleStates()) {
                var entry = new me.cortex.voxy.common.world.other.Mapper.StateEntry(++id, state);
                var decoded = me.cortex.voxy.common.world.other.Mapper.StateEntry.deserialize(id, entry.serialize(), new boolean[1]);
                if (decoded.id != id || decoded.state != state) {
                    throw new IllegalStateException("Block state persistence mismatch: " + state + " -> " + decoded.state);
                }
            }
        }
        System.out.println("VOXY_SMOKE: block state persistence round trips passed: " + id);
        try {
            var legacy = new net.minecraft.nbt.CompoundTag();
            legacy.putString("Name", "minecraft:oak_stairs");
            var properties = new net.minecraft.nbt.CompoundTag();
            properties.putString("facing", "east");
            properties.putString("half", "top");
            properties.putString("shape", "straight");
            properties.putString("waterlogged", "false");
            legacy.put("Properties", properties);
            var root = new net.minecraft.nbt.CompoundTag();
            root.putInt("id", 17);
            root.put("block_state", legacy);
            var bytes = new java.io.ByteArrayOutputStream();
            net.minecraft.nbt.NbtIo.writeCompressed(root, bytes);
            var decoded = me.cortex.voxy.common.world.other.Mapper.StateEntry.deserialize(17, bytes.toByteArray(), new boolean[1]);
            var expected = net.minecraft.world.level.block.Blocks.OAK_STAIRS.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.StairBlock.FACING, net.minecraft.core.Direction.EAST)
                    .setValue(net.minecraft.world.level.block.StairBlock.HALF, net.minecraft.world.level.block.state.properties.Half.TOP);
            if (decoded.state != expected) throw new IllegalStateException("Legacy state mapping changed: " + decoded.state);
            System.out.println("VOXY_SMOKE: legacy compound state mapping passed");
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }

    private static void verifyFailedWorldCleanup() {
        var task = new java.util.concurrent.FutureTask<Void>(() -> {
            var instance = new me.cortex.voxy.commonImpl.VoxyInstance() {
                @Override protected me.cortex.voxy.common.config.section.SectionStorage createStorage(
                        me.cortex.voxy.commonImpl.WorldIdentifier id) {
                    throw new IllegalArgumentException("Expected smoke storage failure");
                }
            };
            try {
                instance.getOrCreate(new me.cortex.voxy.commonImpl.WorldIdentifier(
                        net.minecraft.world.level.Level.OVERWORLD, 263L, null));
                throw new AssertionError("Expected failed world creation");
            } catch (IllegalArgumentException expected) {
                if (!"Expected smoke storage failure".equals(expected.getMessage())) throw expected;
            }
            instance.cleanIdle();
            instance.shutdown();
            return null;
        });
        var thread = new Thread(task, "Voxy failed-world regression");
        thread.setDaemon(true);
        thread.start();
        try {
            task.get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new IllegalStateException("Failed world creation stranded cleanup", failure);
        }
        System.out.println("VOXY_SMOKE: failed-world cleanup regression passed");
    }
}
