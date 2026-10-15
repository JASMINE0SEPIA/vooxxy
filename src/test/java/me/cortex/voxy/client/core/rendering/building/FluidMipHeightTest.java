package me.cortex.voxy.client.core.rendering.building;

import me.cortex.voxy.api.LodFluidHeight;
import me.cortex.voxy.common.config.section.SectionSerializationStorage;
import me.cortex.voxy.common.config.storage.inmemory.MemoryStorageBackend;
import me.cortex.voxy.common.voxelization.VoxelizedSection;
import me.cortex.voxy.common.voxelization.WorldVoxilizedSectionMipper;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;
import me.cortex.voxy.common.world.other.Mapper;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Real ingestion mips and fluid-corner calculation; no GPU, live world or model bakery. */
class FluidMipHeightTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void flatSourceWaterKeepsItsWorldHeightAcrossEveryMip() throws Exception {
        try (var fixture = new Fixture()) {
            float worst = 0;
            for (int top = 0; top < 16; top++) {
                var source = VoxelizedSection.createEmpty();
                long water = Mapper.composeMappingId((byte) 0xff,
                        fixture.world.getMapper().getIdForBlockState(Blocks.WATER.defaultBlockState()), 0);
                Arrays.fill(source.section, 0, (top + 1) * 256, water);
                WorldVoxilizedSectionMipper.mipSection(source, fixture.world.getMapper());
                for (int level = 0; level <= WorldEngine.MAX_LOD_LAYER; level++) {
                    float actual = fixture.surface(source, level, top);
                    float expected = top + 8f / 9f;
                    worst = Math.max(worst, Math.abs(actual - expected));
                    System.out.println("VOXY_FLUID_MIP: top=" + top + " level=" + level
                            + " expected=" + expected + " actual=" + actual);
                }
            }
            assertEquals(0, worst, .00025f, "Flat water acquired a world-space step when its LOD changed");
        }
    }

    @Test void flowingLayersKeepHeightAtSignedWorldOrigins() throws Exception {
        try (var fixture = new Fixture()) {
            int cases = 0;
            float worst = 0;
            for (int flow : new int[]{1, 3, 7, 8, 15}) for (int top : new int[]{0, 6, 15}) {
                var state = Blocks.WATER.defaultBlockState().setValue(BlockStateProperties.LEVEL, flow);
                long water = Mapper.composeMappingId((byte) 0xab, fixture.world.getMapper().getIdForBlockState(state), 0);
                var source = VoxelizedSection.createEmpty();
                Arrays.fill(source.section, 0, (top + 1) * 256, water);
                WorldVoxilizedSectionMipper.mipSection(source, fixture.world.getMapper());
                for (int base : new int[]{-512, 512}) for (int level = 0; level <= WorldEngine.MAX_LOD_LAYER; level++) {
                    float actual = fixture.surface(source, level, top, base);
                    float expected = base + top + state.getFluidState().getOwnHeight();
                    worst = Math.max(worst, Math.abs(actual - expected));
                    assertEquals(expected, actual, .00025f, "flow=" + flow + ", top=" + top + ", level=" + level + ", base=" + base);
                    cases++;
                }
            }
            System.out.println("VOXY_FLUID_MIP: flowing cases=" + cases + " worstWorldError=" + worst);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final WorldEngine world = new WorldEngine(new SectionSerializationStorage(new MemoryStorageBackend()));

        float surface(VoxelizedSection source, int level, int top) throws Exception {
            return surface(source, level, top, 0);
        }

        float surface(VoxelizedSection source, int level, int top, int base) throws Exception {
            var section = world.acquire(level, -1, Math.floorDiv(base, 32 << level), 1);
            var factory = new RenderDataFactory(world, null, false, false, true, -4096, 8192);
            try {
                Arrays.fill(section._unsafeGetRawDataArray(), 0);
                int width = 16 >> level;
                for (int y = 0; y < width; y++) for (int z = 0; z < 32; z++) for (int x = 0; x < 32; x++)
                    section.set(x, y, z, source.get(level, x % width, y, z % width));
                var sourceField = RenderDataFactory.class.getDeclaredField("fluidSourceSection");
                sourceField.setAccessible(true);
                sourceField.set(factory, section);
                var surfaceField = RenderDataFactory.class.getDeclaredField("externalFluidSurface");
                surfaceField.setAccessible(true);
                ((ExternalFluidSurface) surfaceField.get(factory)).section(section);
                var cacheField = RenderDataFactory.class.getDeclaredField("fluidCornerHeightCache");
                cacheField.setAccessible(true);
                Arrays.fill((int[]) cacheField.get(factory), -1);
                var pack = RenderDataFactory.class.getDeclaredMethod("packFluidCornerHeights", int.class, int.class, int.class);
                pack.setAccessible(true);
                int y = top >> level;
                pack.invoke(factory, 16, y, 16);
                var preciseField = RenderDataFactory.class.getDeclaredField("preciseFluidCache");
                preciseField.setAccessible(true);
                long precise = ((long[]) preciseField.get(factory))[WorldSection.getIndex(16, y, 16)];
                float height = LodFluidHeight.decode(precise, 0);
                for (int corner = 1; corner < 4; corner++) assertEquals(height, LodFluidHeight.decode(precise, corner));
                return base + (y + height) * (1 << level);
            } finally {
                var release = RenderDataFactory.class.getDeclaredMethod("releaseFluidNeighborSections");
                release.setAccessible(true);
                release.invoke(factory);
                factory.free();
                section.release();
            }
        }

        @Override public void close() { world.free(); }
    }
}
