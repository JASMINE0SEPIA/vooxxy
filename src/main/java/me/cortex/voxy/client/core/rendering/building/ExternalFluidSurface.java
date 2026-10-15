package me.cortex.voxy.client.core.rendering.building;

import me.cortex.voxy.common.world.WorldSection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Worker-owned shape context for RT fluid interfaces; never reads a live client world. */
final class ExternalFluidSurface {
    private static final Direction[] DIRECTIONS=Direction.values();
    @FunctionalInterface interface Cells { BlockState get(int x,int y,int z); }
    private final Cells cells;
    private final int worldMinY,worldHeight;
    private int originX,originY,originZ,scale=1;
    private final BlockGetter view=new BlockGetter() {
        @Override public BlockState getBlockState(BlockPos pos) {
            return cells.get(pos.getX()-originX,pos.getY()-originY,pos.getZ()-originZ);
        }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        @Override public int getMinY() { return Math.floorDiv(worldMinY,scale); }
        @Override public int getHeight() { return Math.ceilDiv(worldMinY+worldHeight,scale)-getMinY(); }
    };

    ExternalFluidSurface(Cells cells,int minY,int height) {
        if(height<=0) throw new IllegalArgumentException("Invalid fluid shape world height");
        Math.addExact(minY,height);
        this.cells=cells; worldMinY=minY; worldHeight=height;
    }
    void section(WorldSection section) {
        originX=section.x*32; originY=section.y*32; originZ=section.z*32; scale=1<<section.lvl;
    }
    boolean ceilingOccludes(int x,int y,int z) {
        return neighborOccludes(x,y+1,z,Direction.UP,1f);
    }
    int visibleFaces(BlockState state,FluidState fluid,int x,int y,int z,float[] heights) {
        int result=0;
        for(Direction direction:DIRECTIONS) {
            int face=direction.get3DDataValue();
            // Near RT top faces intentionally do not use self-occlusion for waterlogged cells.
            if(direction!=Direction.UP && selfOccludes(state.getFaceOcclusionShape(direction),direction)) continue;
            int nx=x+direction.getStepX(),ny=y+direction.getStepY(),nz=z+direction.getStepZ();
            if(fluid.getType().isSame(cells.get(nx,ny,nz).getFluidState().getType())) continue;
            float height=faceHeight(face,heights);
            if(!neighborOccludes(nx,ny,nz,direction,height)) result|=1<<face;
        }
        return result;
    }
    private boolean neighborOccludes(int x,int y,int z,Direction direction,float height) {
        BlockState state=cells.get(x,y,z);
        if(state.isAir()) return false;
        BlockPos pos=new BlockPos(originX+x,originY+y,originZ+z);
        return occludes(state.getShape(view,pos),direction,height);
    }
    static boolean occludes(VoxelShape neighbor,Direction direction,float height) {
        return !neighbor.isEmpty() && Shapes.blockOccludes(Shapes.box(0,0,0,1,height,1),neighbor,direction);
    }
    static boolean selfOccludes(VoxelShape self,Direction direction) {
        return !self.isEmpty() && Shapes.blockOccludes(Shapes.block(),self,direction.getOpposite());
    }
    static float faceHeight(int face,float[] h) {
        return switch(face) {
            case 0 -> 1f;
            case 1 -> Math.min(Math.min(h[0],h[1]),Math.min(h[2],h[3]));
            case 2 -> Math.max(h[0],h[2]);
            case 3 -> Math.max(h[1],h[3]);
            case 4 -> Math.max(h[0],h[1]);
            case 5 -> Math.max(h[2],h[3]);
            default -> throw new IllegalArgumentException("Invalid fluid face");
        };
    }
}
