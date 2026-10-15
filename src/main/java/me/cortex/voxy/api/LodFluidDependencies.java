package me.cortex.voxy.api;

import me.cortex.voxy.common.world.WorldEngine;
import java.util.function.LongConsumer;

/** Corner averaging can depend on diagonal cells and their ceilings across all three section axes. */
final class LodFluidDependencies {
    private LodFluidDependencies() { }
    static void forEach(long key,LongConsumer invalidate) {
        int level=WorldEngine.getLevel(key),x=WorldEngine.getX(key),y=WorldEngine.getY(key),z=WorldEngine.getZ(key);
        for(int dy=-1;dy<=1;dy++) for(int dz=-1;dz<=1;dz++) for(int dx=-1;dx<=1;dx++)
            invalidate.accept(WorldEngine.getWorldSectionId(level,x+dx,y+dy,z+dz));
    }
}
