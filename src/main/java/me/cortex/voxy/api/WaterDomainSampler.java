package me.cortex.voxy.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/** Source snapshots only. Null means unknown, not air. No live Minecraft world is read. */
final class WaterDomainSampler {
    @FunctionalInterface interface Cells { BlockState get(int x,int y,int z); }
    private final Cells cells;
    private final int minY,height;
    private boolean unknownShapeContext;
    private final BlockGetter view = new BlockGetter() {
        @Override public BlockState getBlockState(BlockPos pos) {
            var state=cells.get(pos.getX(),pos.getY(),pos.getZ());
            if(state==null) { unknownShapeContext=true; return Blocks.AIR.defaultBlockState(); }
            return state;
        }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        @Override public int getMinY() { return minY; }
        @Override public int getHeight() { return height; }
    };

    WaterDomainSampler(Cells cells,int minY,int height) { this.cells=cells; this.minY=minY; this.height=height; }

    LodWaterTile sample(int tileX,int waterY,int tileZ) {
        short[] clearance=new short[LodWaterTile.NODES];
        long[] known=new long[4];
        int originX=Math.multiplyExact(tileX,16),originZ=Math.multiplyExact(tileZ,16);
        // Each lattice node is stored exactly once. Cells crossing a tile edge read the neighboring
        // owner instead of interpolating two independently updated copies of a shared border node.
        for(int z=0;z<16;z++) for(int x=0;x<16;x++) {
            float value=node(originX+x,waterY,originZ+z);
            int index=z*16+x;
            if(Float.isFinite(value)) { clearance[index]=LodWaterTile.encode(value); known[index>>>6] |= 1L<<index; }
        }
        return new LodWaterTile(tileX,waterY,tileZ,0,clearance,known);
    }

    private float node(int x,int y,int z) {
        float minimum=LodWaterTile.MAX_CLEARANCE;
        boolean unknown=false;
        for(int dx=-1;dx<=0;dx++) for(int dz=-1;dz<=0;dz++) {
            float value=column(x+dx,y,z+dz);
            if(value==0) return 0; // A known dry/covered column determines this shore node.
            if(Float.isNaN(value)) unknown=true; else minimum=Math.min(minimum,value);
        }
        return unknown ? Float.NaN : minimum;
    }

    private float column(int x,int y,int z) {
        BlockState state=cells.get(x,y,z);
        if(state==null) return Float.NaN;
        FluidState fluid=state.getFluidState();
        if(!fluid.getType().isSame(Fluids.WATER) && !fluid.is(FluidTags.WATER)) return 0;
        unknownShapeContext=false;
        var own=state.getShape(view,new BlockPos(x,y,z));
        if(unknownShapeContext) return Float.NaN;
        // A rigid shape reaching the water cell's top cannot host displaced free water there.
        if(!own.isEmpty() && own.max(Direction.Axis.Y)>=1) return 0;
        for(int dy=1;dy<=3;dy++) {
            state=cells.get(x,y+dy,z);
            if(state==null) return Float.NaN;
            if(!state.getFluidState().isEmpty()) return dy-1;
            if(state.isAir()) continue;
            unknownShapeContext=false;
            var shape=state.getShape(view,new BlockPos(x,y+dy,z));
            if(unknownShapeContext) return Float.NaN;
            if(!shape.isEmpty()) return Math.clamp(dy-1+(float)shape.min(Direction.Axis.Y),0,LodWaterTile.MAX_CLEARANCE);
        }
        return LodWaterTile.MAX_CLEARANCE;
    }
}
