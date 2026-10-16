package me.cortex.voxy.common.world;

import me.cortex.voxy.common.config.section.SectionSerializationStorage;
import me.cortex.voxy.common.config.storage.inmemory.MemoryStorageBackend;
import me.cortex.voxy.common.voxelization.VoxelizedSection;
import me.cortex.voxy.common.voxelization.WorldVoxilizedSectionMipper;
import me.cortex.voxy.common.world.other.Mapper;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SourceCoverageTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void allEightSignedChunkOctantsCanBecomeKnownEvenWhenTheyAreAir() {
        var world = world();
        try {
            for (int x = -2; x <= -1; x++) for (int y = -2; y <= -1; y++) for (int z = -2; z <= -1; z++) {
                var source = air(world, x, y, z);
                WorldUpdater.insertUpdate(world, source);
                var section = world.acquire(0, -1, -1, -1);
                try { assertTrue(section.isSourceKnown((x & 1) * 16, (y & 1) * 16, (z & 1) * 16)); }
                finally { section.release(); }
            }
            var section = world.acquire(0, -1, -1, -1);
            try {
                assertEquals(255, section.getSourceCoverage());
                assertEquals(0, section.getNonEmptyChildren());
                assertEquals(0, section.getNonEmptyBlockCount());
            } finally { section.release(); }
        } finally { world.free(); }
    }

    @Test void unchangedAirKnowledgeNotifiesOnceAndIsSaved() {
        var world = world();
        var changed = new AtomicInteger();
        var geometry = new AtomicInteger();
        world.setDirtyCallback((section,flags,neighbors)->geometry.incrementAndGet());
        world.addChangeListener((section, flags, neighbors) -> {
            if (section.lvl == 0) { assertNotEquals(0, flags & WorldEngine.UPDATE_TYPE_SOURCE_KNOWLEDGE_BIT); changed.incrementAndGet(); }
        });
        try {
            var source = air(world, 0, 0, 0);
            WorldUpdater.insertUpdate(world, source);
            assertEquals(1, changed.get());
            assertEquals(0, geometry.get(), "Source-only air knowledge must not enqueue geometry rebuilds");
            WorldUpdater.insertUpdate(world, source);
            assertEquals(1, changed.get());
        } finally { world.free(); }
    }

    @Test void sourceKnowledgeRoundTripsAndLegacyMetadataRemainsUnknown() {
        var world = world();
        var section = world.acquire(0, 0, 0, 0);
        try {
            assertFalse(section.isSourceKnown(0, 0, 0));
            assertTrue(section.markSourceKnown(0, 0, 0));
            var copy = SaveLoadSystem3.serialize(section).copy();
            try {
                section.sourceCoverage = 0;
                assertTrue(SaveLoadSystem3.deserialize(section, copy));
                assertTrue(section.isSourceKnown(0, 0, 0));
                assertFalse(section.isSourceKnown(16, 0, 0));
                long metadata = MemoryUtil.memGetLong(copy.address + 8);
                MemoryUtil.memPutLong(copy.address + 8, metadata & 0xffffffL);
                assertTrue(SaveLoadSystem3.deserialize(section, copy));
                assertEquals(0, section.getSourceCoverage());
                assertFalse(section.isSourceKnown(0, 0, 0));
            } finally { copy.free(); }
        } finally { section.release(); world.free(); }
    }

    @Test void cachedMissingAirDoesNotBecomeKnownOnTheSecondAcquire() {
        var world = world();
        try {
            assertNull(world.acquireIfExists(0, 0, 0, 0));
            var section = world.acquireIfExists(0, 0, 0, 0);
            if (section != null) try {
                assertEquals(0, section.getSourceCoverage());
                assertFalse(section.isSourceKnown(0, 0, 0));
            } finally { section.release(); }
        } finally { world.free(); }
    }

    @Test void revisionIsPublishedBeforeListenersIncludingUnsavedUpdates() {
        var world=world();
        var section=world.acquire(0,0,0,0);
        try {
            long before=section.getRenderRevision();
            world.addChangeListener((changed,flags,neighbors)->assertTrue(changed.getRenderRevision()>before,
                    "A snapshot listener saw the pre-update revision"));
            world.markDirty(section,WorldEngine.UPDATE_TYPE_BLOCK_BIT|WorldEngine.UPDATE_TYPE_DONT_SAVE,0);
            assertFalse(section.shouldSave());
        } finally { section.release(); world.free(); }
    }

    @Test void coarseReconstructionCannotRetainAnExactSourceClaim() {
        var world=world();
        try {
            var source=air(world,0,0,0);
            WorldUpdater.insertUpdate(world,source);
            source.sourceExact=false;
            WorldUpdater.insertUpdate(world,source);
            var section=world.acquire(0,0,0,0);
            try { assertEquals(0,section.getSourceCoverage()); }
            finally { section.release(); }
        } finally { world.free(); }
    }

    private static VoxelizedSection air(WorldEngine world, int x, int y, int z) {
        var source = VoxelizedSection.createEmpty().setPosition(x, y, z);
        Arrays.fill(source.section, 0, 4096, Mapper.airWithLight(15));
        WorldVoxilizedSectionMipper.mipSection(source, world.getMapper());
        return source;
    }

    private static WorldEngine world() {
        var world = new WorldEngine(new SectionSerializationStorage(new MemoryStorageBackend()));
        world.setSaveCallback((engine, section, nonBlocking, acquired) -> {
            engine.storage.saveSection(section); section.setNotDirty();
            if (acquired) section.release();
            return true;
        });
        return world;
    }
}
