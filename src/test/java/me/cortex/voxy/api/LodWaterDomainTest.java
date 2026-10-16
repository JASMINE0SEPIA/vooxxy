package me.cortex.voxy.api;

import me.cortex.voxy.common.config.section.SectionSerializationStorage;
import me.cortex.voxy.common.config.storage.inmemory.MemoryStorageBackend;
import me.cortex.voxy.common.voxelization.VoxelizedSection;
import me.cortex.voxy.common.voxelization.WorldVoxilizedSectionMipper;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.other.Mapper;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LodWaterDomainTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void pollingDoesNotWaitForSourceIoAndPendingRequestsAreDeduplicated() throws Exception {
        var started=new CountDownLatch(1); var release=new CountDownLatch(1); var loads=new AtomicInteger();
        try(var domain=new LodWaterDomain(-64,384,(x,y,z)->{
            loads.incrementAndGet(); started.countDown(); await(release); return tile(x,y,z,3);
        },8,3,4096)) {
            assertTimeout(Duration.ofSeconds(1),()->assertEquals(0,domain.poll(new int[]{0,0,0}).tiles().size()));
            assertTrue(started.await(2,TimeUnit.SECONDS));
            assertEquals(1,domain.poll(new int[]{0,0,0,0,0,0}).pending());
            release.countDown();
            assertEquals(3,waitTile(domain,0,0,0,0).clearance(0,0)); assertEquals(1,loads.get());
        } finally { release.countDown(); }
    }

    @Test void invalidatedInFlightResultsAreRebuiltBeforePublication() throws Exception {
        var started=new CountDownLatch(1); var release=new CountDownLatch(1); var loads=new AtomicInteger();
        try(var domain=new LodWaterDomain(-64,384,(x,y,z)->{
            int n=loads.incrementAndGet(); if(n==1) { started.countDown(); await(release); } return tile(x,y,z,n);
        },8,3,4096)) {
            domain.poll(new int[]{0,0,0}); assertTrue(started.await(2,TimeUnit.SECONDS));
            domain.invalidate(WorldEngine.getWorldSectionId(0,0,0,0)); release.countDown();
            assertEquals(2,waitTile(domain,0,0,0,0).clearance(0,0)); assertEquals(2,loads.get());
        } finally { release.countDown(); }
    }

    @Test void cacheAndQueueHaveHardLimits() throws Exception {
        var started=new CountDownLatch(1); var release=new CountDownLatch(1);
        try(var domain=new LodWaterDomain(-64,384,(x,y,z)->{ started.countDown(); await(release); return tile(x,y,z,3); },2,3,1024)) {
            var first=domain.poll(new int[]{0,0,0,1,0,0,2,0,0,3,0,0});
            assertEquals(3,first.pending()); assertTrue(started.await(2,TimeUnit.SECONDS)); release.countDown();
            waitIdle(domain);
            var snapshot=domain.poll(new int[0]); assertEquals(2,snapshot.cached()); assertTrue(snapshot.retainedBytes()<=1024);
            assertEquals(0,domain.poll(new int[]{0,0,0}).tiles().size());
        } finally { release.countDown(); }
    }

    @Test void sourceBorderAndOverheadInvalidationRetainsUnrelatedTiles() throws Exception {
        try(var domain=new LodWaterDomain(-64,384,(x,y,z)->tile(x,y,z,3),8,8,4096)) {
            var affected=waitTile(domain,2,-3,0,0); var unrelated=waitTile(domain,3,0,0,0);
            domain.invalidate(WorldEngine.getWorldSectionId(0,0,0,0));
            assertTrue(waitTile(domain,2,-3,0,affected.revision()).revision()>affected.revision());
            assertEquals(unrelated.revision(),waitTile(domain,3,0,0,0).revision());
        }
    }

    @Test void changeFloodCoalescesToAResetWithoutPublishingTheInFlightResult() throws Exception {
        var started=new CountDownLatch(1); var release=new CountDownLatch(1); var loads=new AtomicInteger();
        try(var domain=new LodWaterDomain(-64,384,(x,y,z)->{
            int n=loads.incrementAndGet(); if(n==1) { started.countDown(); await(release); } return tile(x,y,z,n);
        },8,3,4096)) {
            domain.poll(new int[]{0,0,0}); assertTrue(started.await(2,TimeUnit.SECONDS));
            for(int x=0;x<256;x++) domain.invalidate(WorldEngine.getWorldSectionId(0,1000+x,0,0));
            release.countDown(); assertEquals(2,waitTile(domain,0,0,0,0).clearance(0,0));
            assertEquals(2,loads.get()); assertEquals(0,domain.poll(new int[0]).pending());
        } finally { release.countDown(); }
    }

    @Test void malformedBatchesCannotEnqueuePartialWork() {
        try(var domain=new LodWaterDomain(-64,384,(x,y,z)->tile(x,y,z,3),8,3,4096)) {
            assertThrows(IllegalArgumentException.class,()->domain.poll(new int[]{0,0,0,0,-65,0}));
            assertThrows(IllegalArgumentException.class,()->domain.poll(new int[]{0,0}));
            assertThrows(IllegalArgumentException.class,()->domain.poll(new int[(LodWaterDomain.MAX_POLL_TILES+1)*3]));
            assertEquals(0,domain.poll(new int[0]).pending());
        }
    }

    @Test void loaderFailureSurfacesAndClosingAnIdleDomainIsIdempotent() throws Exception {
        var domain=new LodWaterDomain(-64,384,(x,y,z)->{throw new IllegalArgumentException("fixture source failure");},8,3,4096);
        try {
            domain.poll(new int[]{0,0,0});
            assertThrows(IllegalStateException.class,()->waitTile(domain,0,0,0,0));
        } finally { domain.close(); domain.close(); }
    }

    @Test void realSourceSnapshotsRefreshOnEditsAndReleaseEveryWorldReference() throws Exception {
        var world=world();
        try {
            for(int x=-1;x<=0;x++) for(int z=-1;z<=0;z++) plane(world,x,z,false);
            try(var domain=new LodWaterDomain(world,-64,384)) {
                assertTrue(world.isWorldUsed());
                var open=waitTile(domain,0,0,0,0);
                assertTrue(open.complete()); assertEquals(3,open.clearance(0,0));
                plane(world,0,0,true);
                var covered=waitTile(domain,0,0,0,open.revision());
                assertEquals(1,covered.clearance(0,0),.00005);
                assertEquals(3,covered.clearance(2,2));
                var unknown=waitTile(domain,2,0,0,0); assertFalse(unknown.complete());
                for(int x=1;x<=2;x++) for(int z=-1;z<=0;z++) plane(world,x,z,false);
                assertTrue(waitTile(domain,2,0,0,unknown.revision()).complete());
            }
            assertFalse(world.isWorldUsed());
        } finally { world.free(); }
    }

    @Test void knowledgeOnlyAirUpdatesResolveUnknownTilesWithoutRemeshing() throws Exception {
        var world=world(); var geometry=new AtomicInteger();
        world.setDirtyCallback((section,flags,neighbors)->geometry.incrementAndGet());
        try {
            try(var domain=new LodWaterDomain(world,-64,384)) {
                var unknown=waitTile(domain,2,0,0,0); assertFalse(unknown.complete());
                for(int x=1;x<=2;x++) for(int z=-1;z<=0;z++) {
                    var source=VoxelizedSection.createEmpty().setPosition(x,0,z);
                    Arrays.fill(source.section,0,4096,Mapper.airWithLight(15));
                    WorldVoxilizedSectionMipper.mipSection(source,world.getMapper());
                    me.cortex.voxy.common.world.WorldUpdater.insertUpdate(world,source);
                }
                var known=waitTile(domain,2,0,0,unknown.revision());
                assertTrue(known.complete()); assertEquals(0,known.clearance(0,0));
                assertEquals(0,geometry.get());
            }
        } finally { world.free(); }
    }

    static LodWaterTile waitTile(LodWaterDomain domain,int x,int y,int z,long after) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<deadline) {
            for(var value:domain.poll(new int[]{x,y,z}).tiles()) if(value.revision()>after) return value;
            Thread.sleep(2);
        }
        throw new AssertionError("Water tile did not complete: "+x+","+y+","+z);
    }
    private static void waitIdle(LodWaterDomain domain) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<deadline) { if(domain.poll(new int[0]).pending()==0) return; Thread.sleep(2); }
        fail("Water queue did not drain");
    }
    private static LodWaterTile tile(int x,int y,int z,float clearance) {
        return new LodWaterTile(x,y,z,0,new short[]{LodWaterTile.encode(clearance)},new long[]{-1,-1,-1,-1});
    }
    private static void await(CountDownLatch latch) {
        try { if(!latch.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("Fixture latch timed out"); }
        catch(InterruptedException e) { throw new IllegalStateException(e); }
    }
    private static void plane(WorldEngine world,int x,int z,boolean ceiling) {
        var source=VoxelizedSection.createEmpty().setPosition(x,0,z);
        Arrays.fill(source.section,0,4096,Mapper.airWithLight(15));
        long water=Mapper.composeMappingId((byte)15,world.getMapper().getIdForBlockState(Blocks.WATER.defaultBlockState()),0);
        Arrays.fill(source.section,0,256,water); source.lvl0NonAirCount=256;
        if(ceiling) { source.section[2*256]=Mapper.composeMappingId((byte)15,world.getMapper().getIdForBlockState(Blocks.STONE.defaultBlockState()),0); source.lvl0NonAirCount++; }
        WorldVoxilizedSectionMipper.mipSection(source,world.getMapper());
        me.cortex.voxy.common.world.WorldUpdater.insertUpdate(world,source);
    }
    private static WorldEngine world() {
        var world=new WorldEngine(new SectionSerializationStorage(new MemoryStorageBackend()));
        world.setSaveCallback((engine,section,nonBlocking,acquired)->{engine.storage.saveSection(section);section.setNotDirty();if(acquired)section.release();return true;});
        return world;
    }
}
