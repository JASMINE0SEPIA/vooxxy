package me.cortex.voxy.api;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.ReadOnlyBufferException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class WaterDomainSamplerTest {
    private static BlockState AIR,WATER;
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        AIR=Blocks.AIR.defaultBlockState(); WATER=Blocks.WATER.defaultBlockState();
    }

    @Test void openInteriorIsCompleteAndConstantCompressed() {
        var tile=sample((x,y,z)->y==0 ? WATER : AIR,0,0);
        assertTrue(tile.complete()); assertEquals(1,tile.clearance().remaining());
        for(int z=0;z<16;z++) for(int x=0;x<16;x++) { assertTrue(tile.known(x,z)); assertEquals(3,tile.clearance(x,z)); }
    }

    @Test void shoreNodesBelongToOneWorldTileAtSignedBoundaries() {
        WaterDomainSampler.Cells cells=(x,y,z)->y==0 && x>=0 ? WATER : AIR;
        var negative=sample(cells,-1,0); var positive=sample(cells,0,0);
        assertEquals(0,negative.clearance(15,0));
        assertEquals(0,positive.clearance(0,0));
        assertEquals(3,positive.clearance(1,0));
        assertThrows(IllegalArgumentException.class,()->negative.clearance(16,0));
    }

    @Test void unknownIsNotKnownAirAndKnownDryCanDetermineTheNode() {
        var unknown=sample((x,y,z)->null,0,0);
        assertFalse(unknown.complete()); assertFalse(unknown.known(0,0)); assertEquals(0,unknown.clearance(0,0));
        var determined=sample((x,y,z)->x==-1 && y==0 && z==-1 ? AIR : null,0,0);
        assertTrue(determined.known(0,0)); assertEquals(0,determined.clearance(0,0));
        assertFalse(determined.known(1,1));
    }

    @Test void fullSlabAndGlassCeilingsLimitTheAvailableExcursion() {
        BlockState bottom=Blocks.STONE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE,SlabType.BOTTOM);
        BlockState top=bottom.setValue(BlockStateProperties.SLAB_TYPE,SlabType.TOP);
        for(var ceiling:new BlockState[]{Blocks.STONE.defaultBlockState(),Blocks.GLASS.defaultBlockState(),bottom,top})
            for(int dy=1;dy<=3;dy++) {
                int ceilingY=dy;
                var tile=sample((x,y,z)->y==0 ? WATER : y==ceilingY ? ceiling : AIR,0,0);
                float expected=dy-1+(ceiling==top ? .5f : 0);
                assertEquals(expected,tile.clearance(0,0),.00005f);
                assertTrue(tile.known(0,0));
            }
    }

    @Test void anotherFluidAboveIsABarrierAndCoveredWaterIsNotAFreeSurface() {
        for(var above:new BlockState[]{WATER,Blocks.LAVA.defaultBlockState()}) {
            assertEquals(0,sample((x,y,z)->y==0 ? WATER : y==1 ? above : AIR,0,0).clearance(0,0));
            assertEquals(1,sample((x,y,z)->y==0 ? WATER : y==2 ? above : AIR,0,0).clearance(0,0));
        }
    }

    @Test void waterloggedRigidTopsAndBottomSlabsHaveDifferentFreeSurfacePolicies() {
        var bottom=Blocks.STONE_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED,true);
        var top=bottom.setValue(BlockStateProperties.SLAB_TYPE,SlabType.TOP);
        assertEquals(3,sample((x,y,z)->y==0 ? bottom : AIR,0,0).clearance(0,0));
        assertEquals(0,sample((x,y,z)->y==0 ? top : AIR,0,0).clearance(0,0));
    }

    @Test void tilePayloadIsOwnedAndCoordinatesDoNotAlias() {
        short[] values=new short[256]; Arrays.fill(values,LodWaterTile.encode(3));
        long[] known={-1,-1,-1,-1};
        var tile=new LodWaterTile(-1,-64,1,7,values,known);
        values[0]=0; known[0]=0; tile.known()[0]=0;
        assertEquals(3,tile.clearance(0,0)); assertTrue(tile.known(0,0));
        assertThrows(ReadOnlyBufferException.class,()->tile.clearance().put((short)0));
        assertNotEquals(LodWaterTile.key(-1,-64,1),LodWaterTile.key(1,-64,-1));
        assertNotEquals(LodWaterTile.key(-1,-64,1),LodWaterTile.key(-1,64,1));
        assertThrows(IllegalArgumentException.class,()->LodWaterTile.key(1<<21,0,0));
    }

    private static LodWaterTile sample(WaterDomainSampler.Cells cells,int x,int z) {
        return new WaterDomainSampler(cells,-64,384).sample(x,0,z);
    }
}
