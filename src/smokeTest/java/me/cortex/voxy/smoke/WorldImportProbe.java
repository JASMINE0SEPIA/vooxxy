package me.cortex.voxy.smoke;

import me.cortex.voxy.common.config.section.SectionSerializationStorage;
import me.cortex.voxy.common.config.section.SectionStorage;
import me.cortex.voxy.common.config.storage.inmemory.MemoryStorageBackend;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.commonImpl.VoxyInstance;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import me.cortex.voxy.commonImpl.importers.WorldImporter;
import net.minecraft.world.level.Level;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/** Imports copied region files into a fresh database, without live chunk ingestion. */
final class WorldImportProbe {
    private final VoxyInstance instance;
    private final WorldEngine world;
    private final WorldImporter importer;
    private final AtomicInteger completed = new AtomicInteger(-1);
    private final AtomicInteger changes = new AtomicInteger();

    WorldImportProbe(Level sourceRegistry, Path fixture) {
        if (!java.nio.file.Files.isDirectory(fixture)) throw new IllegalArgumentException("Missing import fixture: " + fixture);
        instance = new VoxyInstance() {
            @Override protected SectionStorage createStorage(WorldIdentifier id) {
                return new SectionSerializationStorage(new MemoryStorageBackend());
            }
        };
        instance.updateDedicatedThreads();
        world = instance.getOrCreate(new WorldIdentifier(Level.OVERWORLD, 263L, null), true);
        world.addChangeListener((section, flags, neighbors) -> changes.incrementAndGet());
        importer = new WorldImporter(world, sourceRegistry, instance.getServiceManager(), () -> true);
        importer.importRegionDirectoryAsync(fixture.toFile());
        importer.runImport((done, total) -> {}, completed::set);
    }

    void verifyAndClose() {
        int chunks = completed.get();
        int states = world.getMapper().getBlockStateCount();
        try {
            if (chunks <= 0 || changes.get() <= 0 || states <= 1) {
                throw new IllegalStateException("Region import did not produce terrain: chunks=" + chunks
                        + ", changes=" + changes.get() + ", states=" + states);
            }
            System.out.println("VOXY_SMOKE: isolated region import passed: chunks=" + chunks
                    + ", section updates=" + changes.get() + ", states=" + states);
        } finally {
            importer.shutdown();
            world.releaseRef();
            instance.shutdown();
        }
    }
}
