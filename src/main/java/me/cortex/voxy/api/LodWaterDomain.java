package me.cortex.voxy.api;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;
import me.cortex.voxy.common.world.other.Mapper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded asynchronous source-water field. Polling never loads sections or computes shapes. */
public final class LodWaterDomain implements AutoCloseable {
    public static final int VERSION=1, MAX_POLL_TILES=4096;
    private static final AtomicLong EPOCHS=new AtomicLong();
    public record Snapshot(long epoch,long revision,List<LodWaterTile> tiles,int pending,int cached,long retainedBytes) {
        public Snapshot { tiles=List.copyOf(tiles); }
    }
    @FunctionalInterface interface Loader {
        LodWaterTile load(int x,int y,int z);
        default void close() {}
    }
    private record Request(long key,int x,int y,int z) {}
    private final Object lock=new Object();
    private final long epoch=EPOCHS.incrementAndGet();
    private final int minY,maxY,maxEntries,maxPending;
    private final long maxBytes;
    private final WorldEngine world;
    private final Loader loader;
    private final Thread worker;
    private final WorldEngine.ISectionChangeCallback listener=(section,flags,neighbors)-> {
        if(section.lvl==0) invalidate(section.key);
    };
    private final Long2ObjectLinkedOpenHashMap<LodWaterTile> cache=new Long2ObjectLinkedOpenHashMap<>();
    private final LongOpenHashSet pending=new LongOpenHashSet(),changes=new LongOpenHashSet();
    private final ArrayDeque<Request> queue=new ArrayDeque<>();
    private Request active;
    private boolean closed,activeInvalidated,reset;
    private long revision,bytes;
    private Throwable failure;

    public LodWaterDomain(WorldEngine world,int minY,int height) {
        this(world,minY,height,new SourceLoader(world,minY,height),32768,1024,16L<<20);
    }
    LodWaterDomain(int minY,int height,Loader loader,int maxEntries,int maxPending,long maxBytes) {
        this(null,minY,height,loader,maxEntries,maxPending,maxBytes);
    }
    private LodWaterDomain(WorldEngine world,int minY,int height,Loader loader,int maxEntries,int maxPending,long maxBytes) {
        if(height<=0 || minY < -4096 || Math.addExact(minY,height)>4096 || maxEntries<1 || maxPending<1 || maxBytes<1024)
            throw new IllegalArgumentException("Invalid canonical water domain limits");
        this.world=world; this.minY=minY; this.maxY=minY+height;
        this.loader=java.util.Objects.requireNonNull(loader); this.maxEntries=maxEntries; this.maxPending=maxPending; this.maxBytes=maxBytes;
        worker=new Thread(this::run,"Voxy canonical water field");
        worker.setDaemon(true);
        if(world!=null) { world.acquireRef(); world.addChangeListener(listener); }
        try { worker.start(); }
        catch(RuntimeException | Error problem) {
            if(world!=null) { world.removeChangeListener(listener); world.releaseRef(); }
            throw problem;
        }
    }

    /** Ordered tile triples (tileX, sourceBlockY, tileZ); return only completed immutable results. */
    public Snapshot poll(int[] xyz) {
        if(xyz.length%3!=0 || xyz.length/3>MAX_POLL_TILES) throw new IllegalArgumentException("Invalid water tile request batch");
        // Validate before enqueueing anything, so a malformed batch has no partial side effects.
        for(int i=0;i<xyz.length;i+=3) {
            LodWaterTile.key(xyz[i],xyz[i+1],xyz[i+2]);
            if(xyz[i+1]<minY || xyz[i+1]>=maxY) throw new IllegalArgumentException("Water layer outside this world");
        }
        synchronized(lock) {
            if(failure!=null) throw new IllegalStateException("Canonical water worker failed",failure);
            if(closed) throw new IllegalStateException("Canonical water domain is closed");
            var result=new ArrayList<LodWaterTile>();
            for(int i=0;i<xyz.length;i+=3) {
                long key=LodWaterTile.key(xyz[i],xyz[i+1],xyz[i+2]);
                var tile=cache.getAndMoveToLast(key);
                if(tile!=null) result.add(tile);
                else if(pending.size()<maxPending && pending.add(key)) queue.addLast(new Request(key,xyz[i],xyz[i+1],xyz[i+2]));
            }
            lock.notifyAll();
            return new Snapshot(epoch,revision,result,pending.size(),cache.size(),bytes);
        }
    }

    void invalidate(long sourceKey) {
        if(WorldEngine.getLevel(sourceKey)!=0) return;
        synchronized(lock) {
            if(closed || reset) return;
            if(changes.size()>=128) { reset=true; changes.clear(); }
            else changes.add(sourceKey);
            lock.notifyAll();
        }
    }

    private void applyChanges() {
        if(reset) { cache.clear(); bytes=0; activeInvalidated=true; revision++; reset=false; }
        for(long key:changes) {
            int bx=WorldEngine.getX(key)*2,by=WorldEngine.getY(key)*32,bz=WorldEngine.getZ(key)*2;
            // Includes the four corner columns, shape-context neighbors and three overhead blocks.
            for(int x=Math.max(-(1<<21),bx-1);x<=Math.min((1<<21)-1,bx+2);x++)
                for(int z=Math.max(-(1<<21),bz-1);z<=Math.min((1<<21)-1,bz+2);z++)
                for(int y=Math.max(minY,by-4);y<=Math.min(maxY-1,by+32);y++) {
                    long affected=LodWaterTile.key(x,y,z);
                    var removed=cache.remove(affected);
                    if(removed!=null) { bytes-=removed.retainedBytes(); revision++; }
                    if(active!=null && active.key==affected) activeInvalidated=true;
                }
        }
        changes.clear();
    }

    private void run() {
        try {
            while(true) {
                Request request;
                synchronized(lock) {
                    applyChanges();
                    while(!closed && queue.isEmpty()) { lock.wait(); applyChanges(); }
                    if(closed) return;
                    request=queue.removeFirst(); active=request; activeInvalidated=false;
                }
                LodWaterTile tile=loader.load(request.x,request.y,request.z);
                if(tile.key()!=request.key) throw new IllegalStateException("Water loader returned another tile");
                synchronized(lock) {
                    applyChanges();
                    active=null;
                    if(closed) return;
                    if(activeInvalidated) { queue.addLast(request); continue; }
                    pending.remove(request.key);
                    tile=tile.withRevision(++revision);
                    cache.putAndMoveToLast(request.key,tile); bytes+=tile.retainedBytes();
                    while(cache.size()>maxEntries || bytes>maxBytes) bytes-=cache.removeFirst().retainedBytes();
                }
            }
        } catch(InterruptedException interrupted) {
            synchronized(lock) { if(!closed) failure=interrupted; }
        } catch(Throwable problem) {
            synchronized(lock) { failure=problem; }
        } finally {
            synchronized(lock) { closed=true; queue.clear(); pending.clear(); changes.clear(); cache.clear(); bytes=0; lock.notifyAll(); }
            try { loader.close(); }
            finally { if(world!=null) { world.removeChangeListener(listener); world.releaseRef(); } }
        }
    }

    @Override public void close() {
        synchronized(lock) { closed=true; lock.notifyAll(); }
        worker.interrupt();
        if(Thread.currentThread()==worker) return;
        boolean interrupted=false;
        while(worker.isAlive()) try { worker.join(); } catch(InterruptedException ignored) { interrupted=true; }
        if(interrupted) Thread.currentThread().interrupt();
    }

    /** At most 32 reusable fine-section arrays (8 MiB); source references live for one tile job. */
    private static final class SourceLoader implements Loader {
        private record Source(long[] data,int coverage,long revision) {}
        private record Acquired(WorldSection section,Source source) {}
        private final WorldEngine world;
        private final int minY,maxY;
        private final Long2ObjectLinkedOpenHashMap<Source> snapshots=new Long2ObjectLinkedOpenHashMap<>();
        private final Long2ObjectOpenHashMap<Acquired> acquired=new Long2ObjectOpenHashMap<>();
        SourceLoader(WorldEngine world,int minY,int height) { this.world=java.util.Objects.requireNonNull(world); this.minY=minY; maxY=Math.addExact(minY,height); }
        @Override public LodWaterTile load(int x,int y,int z) {
            try { return new WaterDomainSampler(this::state,minY,maxY-minY).sample(x,y,z); }
            finally { for(var value:acquired.values()) if(value.section!=null) value.section.release(WorldSection.RELEASE_HINT_POSSIBLE_REUSE); acquired.clear(); }
        }
        private BlockState state(int x,int y,int z) {
            if(y<minY || y>=maxY) return Blocks.AIR.defaultBlockState();
            long key=WorldEngine.getWorldSectionId(0,x>>5,y>>5,z>>5);
            var value=acquired.get(key);
            if(value==null) {
                // Shape contexts supplied by other mods may query unbounded neighborhoods. Retain
                // a bounded snapshot and report unknown rather than letting one query pin the world.
                if(acquired.size()>=16) return null;
                var section=world.acquireIfExists(key);
                acquired.put(key,new Acquired(section,null)); // release even if copying/allocation fails
                Source source=null;
                if(section!=null && section.getSourceCoverage()!=0) {
                    long version=section.getRenderRevision();
                    source=snapshots.getAndMoveToLast(key);
                    if(source==null || source.revision!=version || source.coverage!=section.getSourceCoverage()) {
                        long[] data=source!=null ? source.data : null;
                        if(data==null && snapshots.size()>=32) {
                            var iterator=snapshots.long2ObjectEntrySet().iterator();
                            while(iterator.hasNext()) { var entry=iterator.next(); if(!acquired.containsKey(entry.getLongKey())) { data=entry.getValue().data; iterator.remove(); break; } }
                        }
                        if(data==null) data=new long[WorldSection.SECTION_VOLUME];
                        section.copyDataTo(data);
                        source=new Source(data,section.getSourceCoverage(),version);
                        snapshots.putAndMoveToLast(key,source);
                    }
                }
                value=new Acquired(section,source); acquired.put(key,value);
            }
            var source=value.source;
            int lx=x&31,ly=y&31,lz=z&31;
            if(source==null || (source.coverage & (1<<WorldSection.getChildIndex(lx>>4,ly>>4,lz>>4)))==0) return null;
            return world.getMapper().getBlockStateFromBlockId(Mapper.getBlockId(source.data[WorldSection.getIndex(lx,ly,lz)]));
        }
        @Override public void close() { snapshots.clear(); acquired.clear(); }
    }
}
