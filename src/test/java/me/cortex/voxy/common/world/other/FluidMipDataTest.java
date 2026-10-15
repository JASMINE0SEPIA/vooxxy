package me.cortex.voxy.common.world.other;

import me.cortex.voxy.common.config.section.SectionSerializationStorage;
import me.cortex.voxy.common.config.storage.inmemory.MemoryStorageBackend;
import me.cortex.voxy.common.voxelization.VoxelizedSection;
import me.cortex.voxy.common.voxelization.WorldVoxilizedSectionMipper;
import me.cortex.voxy.common.world.SaveLoadSystem3;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldUpdater;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class FluidMipDataTest {
    private static final long LOW_BITS = (1L << 27) - 1;
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void heightDoesNotChangeMappingFieldsAndSurvivesTheSectionSerializer() {
        var world = world();
        var section = world.acquire(4, -1, -1, 1);
        try {
            long mapping = Mapper.composeMappingId((byte) 0xed, world.getMapper().getIdForBlockState(Blocks.WATER.defaultBlockState()), 417);
            long value = FluidMipData.withHeight(mapping, 4, Math.round((13 + 8f / 9f) * 65536));
            assertEquals(mapping, value & ~LOW_BITS);
            assertEquals(417, Mapper.getBiomeId(value));
            assertEquals(0xed, Mapper.getLightId(value));
            assertFalse(Mapper.isAir(value));
            assertTrue(FluidMipData.hasHeight(Mapper.withLight(value, 0x13), 4));
            assertFalse(FluidMipData.hasHeight(value, 3));
            assertFalse(FluidMipData.hasHeight(mapping, 4));
            section.set(31, 31, 31, value);
            var saved = SaveLoadSystem3.serialize(section).copy();
            try {
                section.set(31, 31, 31, 0);
                assertTrue(SaveLoadSystem3.deserialize(section, saved));
                assertEquals(value, section._unsafeGetRawDataArray()[32767]);
            } finally { saved.free(); }
        } finally { section.release(); world.free(); }
    }

    @Test void highestFluidSourceWinsEvenWhenOpacityOrderingChoosesAnotherChild() {
        var world = world();
        try {
            var mapper = world.getMapper();
            long low = Mapper.composeMappingId((byte) 0, mapper.getIdForBlockState(Blocks.WATER.defaultBlockState()
                    .setValue(BlockStateProperties.LEVEL, 7)), 0);
            long high = Mapper.composeMappingId((byte) 0, mapper.getIdForBlockState(Blocks.WATER.defaultBlockState()), 0);
            long value = Mipper.mip(0, low, 0, 0, high, 0, 0, 0, mapper, 1);
            assertEquals(Mapper.getBlockId(low), Mapper.getBlockId(value));
            assertEquals((1 + 8f / 9f) / 2, FluidMipData.cellHeight(value, 1), .00001f);
        } finally { world.free(); }
    }

    @Test void oldChildrenKeepAnExplicitUnknownHeightFallback() {
        var world = world();
        try {
            long water = Mapper.composeMappingId((byte) 0, world.getMapper().getIdForBlockState(Blocks.WATER.defaultBlockState()), 0);
            long value = Mipper.mip(water, 0, 0, 0, 0, 0, 0, 0, world.getMapper(), 2);
            assertFalse(FluidMipData.hasHeight(value, 2));
            assertEquals(water, value);
        } finally { world.free(); }
    }

    @Test void anotherFluidTypeCannotRaiseTheSelectedSurface() {
        var world = world();
        try {
            var mapper = world.getMapper();
            long lava = Mapper.composeMappingId((byte) 0, mapper.getIdForBlockState(Blocks.LAVA.defaultBlockState()), 0);
            long water = Mapper.composeMappingId((byte) 0, mapper.getIdForBlockState(Blocks.WATER.defaultBlockState()), 0);
            long value = Mipper.mip(0, lava, 0, 0, water, 0, 0, 0, mapper, 1);
            assertEquals(Mapper.getBlockId(lava), Mapper.getBlockId(value));
            assertEquals(Blocks.LAVA.defaultBlockState().getFluidState().getOwnHeight() / 2,
                    FluidMipData.cellHeight(value, 1), .00001f);
        } finally { world.free(); }
    }

    @Test void reingestingUnchangedFineWaterUpgradesEveryOldParent() {
        var world = world();
        world.setSaveCallback((engine, section, nonBlocking, acquired) -> {
            engine.storage.saveSection(section);
            section.setNotDirty();
            if (acquired) section.release();
            return true;
        });
        try {
            long water = Mapper.composeMappingId((byte) 0xff, world.getMapper().getIdForBlockState(Blocks.WATER.defaultBlockState()), 0);
            var fresh = VoxelizedSection.createEmpty().setPosition(-1, -1, 1);
            fresh.lvl0NonAirCount = 256;
            Arrays.fill(fresh.section, 0, 256, water);
            WorldVoxilizedSectionMipper.mipSection(fresh, world.getMapper());
            assertTrue(fresh.hasFluidMipData);
            var old = new VoxelizedSection(fresh.section.clone()).setPosition(-1, -1, 1);
            old.lvl0NonAirCount = 256;
            for (int i = 4096; i < old.section.length; i++) old.section[i] &= ~LOW_BITS;
            WorldUpdater.insertUpdate(world, old);
            // The identical fine data used to stop WorldUpdater before it compared level one.
            WorldUpdater.insertUpdate(world, fresh);
            for (int level = 1; level <= 4; level++) {
                var section = world.acquire(level, fresh.x >> (level + 1), fresh.y >> (level + 1), fresh.z >> (level + 1));
                try {
                    int mask = (1 << (level + 1)) - 1;
                    long value = section._unsafeGetRawDataArray()[me.cortex.voxy.common.world.WorldSection.getIndex(
                            (fresh.x & mask) << (4 - level), (fresh.y & mask) << (4 - level), (fresh.z & mask) << (4 - level))];
                    assertTrue(FluidMipData.hasHeight(value, level), "Parent level " + level + " was left in the old format");
                } finally { section.release(); }
            }
            fresh.zero();
            WorldVoxilizedSectionMipper.mipSection(fresh, world.getMapper());
            assertFalse(fresh.hasFluidMipData);
            WorldUpdater.insertUpdate(world, fresh);
            for (int level = 0; level <= 4; level++) {
                var section = world.acquire(level, fresh.x >> (level + 1), fresh.y >> (level + 1), fresh.z >> (level + 1));
                try {
                    // Cells outside this inserted chunk retain the tracker's initialized sky light.
                    for (long value : section._unsafeGetRawDataArray()) {
                        assertTrue(Mapper.isAir(value), "Water removal left a mapped fluid");
                        assertEquals(0, value & LOW_BITS, "Water removal left stale mip metadata");
                    }
                    assertEquals(0, section.getNonEmptyChildren());
                } finally { section.release(); }
            }
        } finally { world.free(); }
    }

    private static WorldEngine world() {
        return new WorldEngine(new SectionSerializationStorage(new MemoryStorageBackend()));
    }
}
