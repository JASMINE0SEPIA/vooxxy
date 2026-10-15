package me.cortex.voxy.client.compat;

import me.cortex.voxy.api.*;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Render-thread access to immutable LOD scenes and the optional colour-proxy adapter. */
public final class CausticaBridge {
    private record CachedMesh(long sourceRevision, Mesh mesh) {}
    private static final Map<Long, CachedMesh> cache = new HashMap<>();
    private static List<Mesh> published = List.of();
    private static long epoch = Long.MIN_VALUE, revision;
    private static long convertedRevision;
    private static LodScene previous;
    private CausticaBridge() {}

    public static boolean available() { return VoxyLodApi.VERSION == 5; }
    public static Object deviceHostLock() { return VoxyLodApi.deviceHostLock(); }
    public static void registerExternalRenderer(BooleanSupplier active) { VoxyLodApi.registerExternalRenderer(active); }
    public static long revision() { return revision; }
    public static boolean configuredEnabled() { return VoxyConfig.CONFIG.enabled; }
    public static void setConfiguredEnabled(boolean value) { VoxyConfig.CONFIG.enabled = value; VoxyConfig.CONFIG.save(); }
    public static boolean configuredIngestEnabled() { return VoxyConfig.CONFIG.ingestEnabled; }
    public static void setConfiguredIngestEnabled(boolean value) { VoxyConfig.CONFIG.ingestEnabled = value; VoxyConfig.CONFIG.save(); }
    public static int configuredRenderDistanceChunks() { return Math.round(VoxyConfig.CONFIG.sectionRenderDistance * 32); }
    public static void setConfiguredRenderDistanceChunks(int value) { VoxyConfig.CONFIG.sectionRenderDistance = Math.clamp(value,32,512)/32f; VoxyConfig.CONFIG.save(); }
    public static int renderDistanceChunks() { return Math.min(configuredRenderDistanceChunks(), Integer.getInteger("voxy.external.maxDistanceBlocks",1024)/16); }
    public static void reset() { cache.clear(); published=List.of(); previous=null; epoch=Long.MIN_VALUE; revision++; }

    /** Advance uploads before RT frame recording and return the complete API 5 scene. */
    public static LodScene pollScene(double x, double y, double z) {
        var client = Minecraft.getInstance();
        var renderer = IVoxyRenderSystemHolder.getNullable();
        if (!configuredEnabled() || client.level == null || renderer == null || renderer.vkCore == null) return null;
        var camera = client.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        var projection = new Matrix4f(camera.projectionMatrix).mul(camera.viewRotationMatrix);
        var window = client.getWindow();
        return VoxyLodApi.update(x,y,z,projection,window.getWidth(),window.getHeight());
    }

    public static List<Mesh> poll(double x, double y, double z, int vanillaChunks, int minY, int maxY) {
        LodScene scene = pollScene(x,y,z);
        if (scene == null) { reset(); return published; }
        var renderer = IVoxyRenderSystemHolder.getNullable();
        if (scene == null || scene == previous) return published;
        if (epoch != scene.epoch()) { reset(); epoch=scene.epoch(); }
        // Palette snapshots also change when new biome colours arrive without remeshing geometry.
        if (previous != null && previous.models() != scene.models()) cache.clear();
        var result = new ArrayList<Mesh>();
        var keep = new HashSet<Long>();
        for (LodMesh mesh : scene.meshes()) {
            keep.add(mesh.key);
            CachedMesh cached = cache.get(mesh.key);
            if (cached == null || cached.sourceRevision != mesh.revision) {
                cached = new CachedMesh(mesh.revision,
                        convert(scene,mesh,renderer.vkCore::externalAverageColour,++convertedRevision));
                cache.put(mesh.key,cached);
            }
            Mesh converted = cached.mesh;
            if (converted.opaque.length != 0 || converted.transparent.length != 0) result.add(converted);
        }
        cache.keySet().retainAll(keep);
        previous=scene; published=List.copyOf(result); revision++;
        return published;
    }

    public record Mesh(long key,long version,int originX,int originY,int originZ,int width,int dataPointWidth,byte[] opaque,byte[] transparent) {}

    static Mesh convert(LodScene scene,LodMesh mesh,java.util.function.IntUnaryOperator average,long version) {
        ByteBuffer solid=ByteBuffer.allocate(Math.multiplyExact(mesh.quadCount(),64)).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer transparent=ByteBuffer.allocate(solid.capacity()).order(ByteOrder.LITTLE_ENDIAN);
        var quads=mesh.quads();
        while (quads.hasRemaining()) {
            long packed=quads.get();
            LodModel model=scene.models().get((int)(packed>>>26)&65535);
            if (model == null) throw new IllegalStateException("LOD mesh references an unpublished model");
            LodQuad quad=LodQuad.decode(packed,model,mesh.level);
            var state=model.state();
            boolean water=state.getFluidState().is(FluidTags.WATER), lava=state.getFluidState().is(FluidTags.LAVA);
            boolean glass=!water&&!lava&&(model.flags()&4)!=0;
            int material=water?12:lava?6:state.getLightEmission()>0?15:state.is(BlockTags.LEAVES)?1:glass?0:2;
            int color=average.applyAsInt(model.id());
            int tint=(model.flags()&2)!=0&&model.tint()>=0?scene.colour(model.tint()+quad.biome()):model.tint();
            if (tint != -1 && quad.tintMode()!=0) {
                color=(color&0xFF000000)|((color&255)*((tint>>>16)&255)/255)|(((color>>>8)&255)*((tint>>>8)&255)/255)<<8|(((color>>>16)&255)*(tint&255)/255)<<16;
            }
            // The compact proxy input carries whole-block geometry and average albedo.
            // Textured/cutout faces require the typed API's model atlas, rather than this input.
            float[] p={quad.x(),quad.y(),quad.z()};
            float[][] points={p.clone(),p.clone(),p.clone(),p.clone()};
            points[1][quad.axisU()]+=quad.width(); points[2][quad.axisU()]+=quad.width();
            points[2][quad.axisV()]+=quad.height(); points[3][quad.axisV()]+=quad.height();
            if (((quad.face()&1)==0?-1:1)!=(quad.axisNormal()==1?-1:1)) { var swap=points[1];points[1]=points[3];points[3]=swap; }
            ByteBuffer target=water||glass?transparent:solid;
            for (float[] point:points) {
                target.putShort((short)Math.round(point[0])).putShort((short)Math.round(point[1])).putShort((short)Math.round(point[2])).putShort((short)0);
                target.put((byte)color).put((byte)(color>>>8)).put((byte)(color>>>16)).put((byte)(glass?Math.max(32,color>>>24):255));
                target.put((byte)material).put((byte)(quad.light()&15)).put((byte)((quad.light()>>>4)&15)).put((byte)0);
            }
        }
        return new Mesh(mesh.key,version,mesh.originX(),mesh.originY(),mesh.originZ(),mesh.blockSize(),1<<mesh.level,
                Arrays.copyOf(solid.array(),solid.position()),Arrays.copyOf(transparent.array(),transparent.position()));
    }
}
