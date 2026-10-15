package me.cortex.voxy.client.core.vk.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.RenderProperties;
import me.cortex.voxy.client.core.VoxyRenderSystem;
import me.cortex.voxy.client.core.model.ModelBakerySubsystem;
import me.cortex.voxy.client.core.model.bakery.IAtlasTextureReader;
import me.cortex.voxy.client.core.rendering.RenderDistanceTracker;
import me.cortex.voxy.client.core.rendering.ViewportSelector;
import me.cortex.voxy.client.core.rendering.bounding.StreamedBoundStore;
import me.cortex.voxy.client.core.rendering.building.RenderGenerationService;
import me.cortex.voxy.client.core.rendering.hierachical.AsyncNodeManager;
import me.cortex.voxy.client.core.rendering.util.AbstractDownloadStream;
import me.cortex.voxy.client.core.rendering.util.AbstractUploadStream;
import me.cortex.voxy.client.core.vk.MinecraftVkHost;
import me.cortex.voxy.client.core.vk.MinecraftVkHostAdapter;
import me.cortex.voxy.client.core.vk.VkAtlasTextureReader;
import me.cortex.voxy.client.core.vk.VkBuffer;
import me.cortex.voxy.client.core.vk.VkDownloadStream;
import me.cortex.voxy.client.core.vk.VkFrameCtx;
import me.cortex.voxy.client.core.vk.VkImage2D;
import me.cortex.voxy.client.core.vk.VkUploadStream;
import me.cortex.voxy.client.core.vk.VulkanBackend;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.thread.ServiceManager;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.client.core.rendering.RenderMatrices;
import me.cortex.voxy.client.core.rendering.FogParameters;
import net.minecraft.client.Minecraft;

import java.util.Arrays;
import java.util.List;

public class VkRenderCore {
    private final WorldEngine worldIn;
    private final VkFrameCtx frameCtx;
    private final VkUploadStream uploadStream;
    private final VkDownloadStream downloadStream;

    private final RenderProperties properties;
    private final VkModelStore modelStore;
    private final ModelBakerySubsystem modelService;
    private final RenderGenerationService renderGen;
    private final VkSectionGeometryData geometryData;
    private final AsyncNodeManager nodeManager;
    private final VkNodeCleaner nodeCleaner;
    private final VkTraversal traversal;
    private final VkTerrainRenderer terrainRenderer;
    private final VkCompositor compositor;
    private final VkSSAO ssao;
    private final VkBoundRenderer boundRenderer;
    private final StreamedBoundStore visibleSectionStream;
    private final RenderDistanceTracker renderDistanceTracker;
    private final ViewportSelector<VkViewport> viewportSelector;
    private boolean shutDown = false;
    private long successfulFrames;
    private final ServiceManager services;
    private volatile me.cortex.voxy.api.LodSession externalSession;

    public VkRenderCore(WorldEngine world, ServiceManager sm) {
        world.acquireRef();
        this.worldIn = world;
        this.services = sm;
        Logger.info("Creating Voxy pure-Vulkan render core");

        VkFrameCtx frame = null;
        VkUploadStream upload = null;
        VkDownloadStream download = null;
        RenderProperties props = null;
        VkModelStore models = null;
        ModelBakerySubsystem modelBakery = null;
        RenderGenerationService generation = null;
        VkSectionGeometryData geometry = null;
        VkNodeGpuOps nodeOps = null;
        AsyncNodeManager nodes = null;
        VkNodeCleaner cleaner = null;
        VkTraversal traverse = null;
        VkTerrainRenderer terrain = null;
        VkCompositor compose = null;
        VkSSAO ao = null;
        StreamedBoundStore visible = null;
        VkBoundRenderer bounds = null;
        ViewportSelector<VkViewport> viewports = null;
        RenderDistanceTracker distanceTracker = null;

        try {
            var host = MinecraftVkHost.get();
            if (host == null) throw new IllegalStateException("No Minecraft Vulkan host adapter registered");
            var vctx = VulkanBackend.context();
            frame = new VkFrameCtx(vctx);

            upload = new VkUploadStream(frame, 1 << 26);
            download = new VkDownloadStream(frame, 1 << 25);
            AbstractUploadStream.setInstance(upload);
            AbstractDownloadStream.setInstance(download);

            props = RenderProperties.getRenderProperties();
            IAtlasTextureReader.setInstance(new VkAtlasTextureReader(frame));

            models = new VkModelStore(frame, upload);
            modelBakery = new ModelBakerySubsystem(world.getMapper(), models);
            generation = new RenderGenerationService(world, modelBakery, sm, false);

            final VkFrameCtx selectedFrame = frame;
            visible = new StreamedBoundStore(size -> new VkBuffer(selectedFrame, size));
            if (!me.cortex.voxy.api.VoxyLodApi.externalRendererActive()) {
                long geometryCapacity = vctx.recommendedGeometryCapacityBytes();
                Logger.info("Voxy VK geometry target: " + (geometryCapacity >> 20) + " MiB from "
                        + (vctx.deviceLocalHeapBytes >> 20) + " MiB device-local heap"
                        + (vctx.integratedGpu ? " (integrated GPU policy)" : " (discrete GPU policy)"));
                geometry = new VkSectionGeometryData(frame, 1 << 20, geometryCapacity);

                nodeOps = new VkNodeGpuOps(frame, upload);
                nodes = new AsyncNodeManager(1 << 21, geometry, generation, nodeOps);
                nodeOps = null;

                cleaner = new VkNodeCleaner(frame, upload, download, nodes);
                traverse = new VkTraversal(frame, upload, download, props, nodes, cleaner, generation);
                terrain = new VkTerrainRenderer(frame, upload, download, props, geometry, models);
                compose = new VkCompositor(frame, upload, props, VoxyConfig.CONFIG.getFogMode());
                ao = new VkSSAO(frame, upload, props, VoxyConfig.CONFIG.getSSAOMode());
                bounds = new VkBoundRenderer(frame, upload, props);

                nodes.start();

                final RenderProperties selectedProps = props;
                final VkSectionGeometryData selectedGeometry = geometry;
                viewports = new ViewportSelector<>(() ->
                        new VkViewport(selectedFrame, selectedProps, selectedGeometry.getMaxSectionCount()));

                int minSec = Minecraft.getInstance().level.getMinSectionY() >> 5;
                int maxSec = (Minecraft.getInstance().level.getMaxSectionY() - 1) >> 5;
                final AsyncNodeManager selectedNodes = nodes;
                distanceTracker = new RenderDistanceTracker(40, minSec, maxSec,
                        selectedNodes::addTopLevel, selectedNodes::removeTopLevel);
                distanceTracker.setRenderDistance((int) Math.ceil(VoxyConfig.CONFIG.sectionRenderDistance + 1));

            }
            final AsyncNodeManager activeNodes = nodes;
            world.setDirtyCallback((section, flags, neighbors) -> {
                if (activeNodes != null) activeNodes.worldEvent(section, flags, neighbors);
                var session = this.externalSession;
                if (session != null) session.invalidate(section.key);
            });
            Arrays.stream(world.getMapper().getBiomeEntries()).forEach(modelBakery::addBiome);
            world.getMapper().setBiomeCallback(modelBakery::addBiome);
            frame.flushImmediate();
        } catch (RuntimeException | Error failure) {
            try {
                world.setDirtyCallback(null);
                world.getMapper().setBiomeCallback(null);
                world.getMapper().setStateCallback(null);
            } catch (Throwable cleanup) {
                failure.addSuppressed(cleanup);
            }
            if (nodes != null) {
                try { nodes.stop(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            } else if (nodeOps != null) {
                try { nodeOps.free(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            }
            if (generation != null) {
                try { generation.shutdown(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            }
            if (frame != null) {
                try { frame.waitIdleRetireAll(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            }

            if (modelBakery != null) {
                try { modelBakery.shutdown(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            } else if (models != null) {
                try { models.free(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            }
            if (models != null) {
                try { models.free(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            }
            if (bounds != null) {
                try { bounds.free(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            }
            if (visible != null) {
                try { visible.free(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            }
            if (traverse != null) {
                try { traverse.free(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            }
            if (cleaner != null) {
                try { cleaner.free(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            }
            if (terrain != null) {
                try { terrain.free(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            }
            if (ao != null) {
                try { ao.free(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            }
            if (compose != null) {
                try { compose.free(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            }
            if (viewports != null) {
                try { viewports.free(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            }
            if (geometry != null) {
                try { geometry.free(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            }
            if (download != null) {
                try { download.flushWaitClear(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            }
            if (upload != null) {
                try { upload.free(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            }
            if (download != null) {
                try { download.free(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            }
            if (frame != null) {
                try { frame.drainDeferredDestruction(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
                try { frame.free(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            }

            AbstractUploadStream.clearInstance();
            AbstractDownloadStream.clearInstance();
            IAtlasTextureReader.clearInstance();
            world.releaseRef();
            throw failure;
        }

        this.frameCtx = frame;
        this.uploadStream = upload;
        this.downloadStream = download;
        this.properties = props;
        this.modelStore = models;
        this.modelService = modelBakery;
        this.renderGen = generation;
        this.geometryData = geometry;
        this.nodeManager = nodes;
        this.nodeCleaner = cleaner;
        this.traversal = traverse;
        this.terrainRenderer = terrain;
        this.compositor = compose;
        this.ssao = ao;
        this.visibleSectionStream = visible;
        this.boundRenderer = bounds;
        this.viewportSelector = viewports;
        this.renderDistanceTracker = distanceTracker;

        Logger.info("Voxy pure-Vulkan render core created with " + (this.geometryData == null ? "external LOD provider" : this.geometryData.getMaxCapacity() + " geometry capacity"));
    }

    public void renderFrame(RenderTarget target, MinecraftVkHostAdapter adapter, RenderMatrices matrices,
                            double camX, double camY, double camZ) {
        if (this.geometryData == null || me.cortex.voxy.api.VoxyLodApi.externalRendererActive()) return;
        // A flush can submit/end Minecraft's current command buffer. Acquire
        // the recording buffer only after pending initialization has completed.
        this.frameCtx.flushImmediate();
        var frameCmd = adapter.frameCommandBuffer();
        if (frameCmd == null) {
            Logger.warn("Voxy VK: no frame command buffer at hook point, skipping frame");
            return;
        }
        var crs = Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        if (crs == null || !crs.initialized) return;

        this.frameCtx.beginFrame(frameCmd);
        try {
            if (me.cortex.voxy.commonImpl.VoxyCommon.IS_MINE_IN_ABYSS) {
                int sector = (((int) Math.floor(camX) >> 4) + 512) >> 10;
                camX -= sector << 14;
                camY += (16 + (256 - 32 - sector * 30)) * 16;
            }

            var viewport = this.viewportSelector.getViewport();
            var voxyProjection = VoxyRenderSystem.computeProjectionMat(this.properties, matrices.projection());
            var fog = crs.fogData == null ? null : new FogParameters(
                    crs.fogData.color.x, crs.fogData.color.y, crs.fogData.color.z, crs.fogData.color.w,
                    crs.fogData.environmentalStart, crs.fogData.environmentalEnd,
                    crs.fogData.renderDistanceStart, crs.fogData.renderDistanceEnd);
            viewport.setVanillaProjection(matrices.projection())
                    .setProjection(voxyProjection)
                    .setModelView(matrices.modelView())
                    .setCamera(camX, camY, camZ)
                    .setScreenSize(target.width, target.height)
                    .setFogParameters(fog)
                    .update();
            viewport.frameId++;
            if (viewport.width <= 0 || viewport.height <= 0) return;
            viewport.ensureTargets();

            var rt = new VkCompositor.VkViewportRT(viewport,
                    target.getColorTextureView(), target.getDepthTextureView(), target.width, target.height);

            this.compositor.setupDepthStencil(rt);
            this.boundRenderer.render(viewport, this.visibleSectionStream);
            this.terrainRenderer.renderOpaque(viewport, false);

            this.compositor.offscreenToSampled(viewport);
            viewport.hiZ.buildMipChain(viewport.depthSampleView, viewport.width, viewport.height);
            this.compositor.offscreenToAttachment(viewport);

            this.nodeManager.tick(this.traversal.getNodeBuffer(), this.nodeCleaner);
            this.nodeCleaner.tick(this.traversal.getNodeBuffer());
            this.traversal.doTraversal(viewport);

            this.terrainRenderer.buildDrawCalls(viewport);
            this.terrainRenderer.renderTemporal(viewport);
            this.ssao.compute(viewport, rt);
            this.terrainRenderer.renderTranslucent(viewport);

            this.compositor.offscreenToSampled(viewport);
            this.compositor.composite(rt);

            this.renderDistanceTracker.setCenterAndProcess(viewport.cameraX, viewport.cameraZ);
            this.modelService.tick(900_000);

            //Package every staging/readback allocation made anywhere in this
            //recording interval onto the same Voxy frame before endFrame() queues
            //the host completion callback. This avoids an extra frame of staging
            //retention and removes one frame of draw-count/readback latency.
            this.downloadStream.tick();
            this.uploadStream.tick();
            this.successfulFrames++;
        } finally {
            this.frameCtx.endFrame();
            this.frameCtx.pollRetired();
        }
    }

    /** Advances the provider on the render thread; exported meshes are immutable. */
    public me.cortex.voxy.api.LodScene externalScene(double x, double y, double z, org.joml.Matrix4fc projectionView, int width, int height) {
        if (shutDown || Minecraft.getInstance().level == null) return null;
        if (externalSession == null) {
            var level = Minecraft.getInstance().level;
            externalSession = new me.cortex.voxy.api.LodSession(worldIn, modelService, services, modelStore,level.getMinY(),level.getHeight());
        }
        this.frameCtx.flushImmediate();
        this.modelService.tick(900_000);
        this.uploadStream.tick();
        this.frameCtx.finishExternalUploads();
        var level = Minecraft.getInstance().level;
        return externalSession.tick(x,y,z,level.getMinY(),level.getMaxY(),
                me.cortex.voxy.api.VoxyLodApi.limitExternalDistance(
                        me.cortex.voxy.client.compat.CausticaBridge.requestedDistanceBlocks()),
                new me.cortex.voxy.api.LodView(x,y,z,projectionView,width,height,VoxyConfig.CONFIG.subDivisionSize));
    }

    public int externalAverageColour(int model) { return this.modelStore.averageColour(model); }

    public void setRenderDistance(float renderDistance) {
        if (this.renderDistanceTracker != null) this.renderDistanceTracker.setRenderDistance((int) Math.ceil(renderDistance + 1));
    }

    public void addDebugInfo(List<String> debug) {
        debug.add("VK successful frames/geometry sections: " + this.successfulFrames + "/" + (this.geometryData == null ? 0 : this.geometryData.getSectionCount()));
        debug.add("VK host mode: " + VulkanBackend.statusLine());
        debug.add("VkBuf [#/logical MiB/allocated MiB]: [" + VkBuffer.getCount() + "/"
                + (VkBuffer.getTotalSize() >> 20) + "/" + (VkBuffer.getTotalAllocationSize() >> 20) + "]");
        debug.add("VkImage [#/allocated MiB]: [" + VkImage2D.getCount() + "/"
                + (VkImage2D.getTotalAllocationSize() >> 20) + "]");
        debug.add("VkFrame [current/retired/inFlight/pendingDestroy/eventPool]: ["
                + this.frameCtx.currentFrame() + "/" + this.frameCtx.retiredFrame() + "/"
                + this.frameCtx.inFlightFrameCount() + "/" + this.frameCtx.pendingDestroyCount() + "/"
                + this.frameCtx.pooledEventCount() + "]");
        this.modelService.addDebugData(debug);
        this.renderGen.addDebugData(debug);
        if (this.nodeManager != null) this.nodeManager.addDebug(debug);
        if (this.externalSession != null) this.externalSession.addDebugInfo(debug);
        if (this.ssao != null) this.ssao.addDebugInfo(debug);
    }

    private static void cleanup(String what, Runnable action) {
        try {
            action.run();
        } catch (Throwable failure) {
            Logger.error("Voxy VK cleanup failed: " + what, failure);
        }
    }

    public void shutdown() {
        if (this.shutDown) return;
        this.shutDown = true;
        Logger.info("Shutting down Voxy pure-Vulkan render core");

        var external = this.externalSession;
        this.externalSession = null;
        if (external != null) cleanup("external LOD session", external::close);
        //Stop every CPU producer independently. One failed callback/service must
        //not leave the remaining workers running while GPU resources are freed.
        cleanup("detach world callbacks", () -> {
            this.worldIn.setDirtyCallback(null);
            this.worldIn.getMapper().setBiomeCallback(null);
            this.worldIn.getMapper().setStateCallback(null);
        });
        if (this.nodeManager != null) cleanup("node manager stop", this.nodeManager::stop);
        cleanup("render generation shutdown", this.renderGen::shutdown);

        boolean deviceAlive = MinecraftVkHost.get() != null;
        if (deviceAlive) {
            //Finish already-recorded GPU use before beginning teardown. This is
            //not enough by itself to run Mojang's DestructionQueue; the explicit
            //host drain below advances every destruction slot after all frees
            //have been queued.
            cleanup("initial device idle", this.frameCtx::waitIdleRetireAll);

            //Close readback callbacks before rotating old frame-completion slots;
            //otherwise a late completed readback could feed a node manager that
            //has already been stopped.
            cleanup("download stream close", this.downloadStream::flushWaitClear);

            //Each component is best-effort. In particular, a model/pipeline
            //cleanup failure must never strand the much larger geometry/atlas
            //allocations from the same world.
            cleanup("model bakery shutdown", this.modelService::shutdown);
            cleanup("model store fallback free", this.modelStore::free);
            if (this.boundRenderer != null) cleanup("bound renderer", this.boundRenderer::free);
            cleanup("visible section stream", this.visibleSectionStream::free);
            if (this.traversal != null) cleanup("traversal", this.traversal::free);
            if (this.nodeCleaner != null) cleanup("node cleaner", this.nodeCleaner::free);
            if (this.geometryData != null) cleanup("geometry data", this.geometryData::free);
            if (this.terrainRenderer != null) cleanup("terrain renderer", this.terrainRenderer::free);
            if (this.ssao != null) cleanup("SSAO", this.ssao::free);
            if (this.compositor != null) cleanup("compositor", this.compositor::free);
            if (this.viewportSelector != null) cleanup("viewports", this.viewportSelector::free);
            cleanup("upload stream", this.uploadStream::free);
            cleanup("download stream final free", this.downloadStream::free);

            //Mojang 26.2 rotates a two-slot destruction queue only from submit().
            //Device-idle does not rotate it. Advancing all host slots here makes
            //world unload/reload release VMA allocations before the next world's
            //renderer is created instead of temporarily stacking both worlds.
            cleanup("host deferred destruction drain", this.frameCtx::drainDeferredDestruction);
            cleanup("frame context", this.frameCtx::free);
        } else {
            Logger.warn("Voxy VK: Minecraft's Vulkan device is already gone at shutdown; "
                    + "skipping GPU teardown to avoid destroying objects on a dead device");
        }

        AbstractUploadStream.clearInstance();
        AbstractDownloadStream.clearInstance();
        IAtlasTextureReader.clearInstance();
        this.worldIn.releaseRef();
        Logger.info("VK render core shutdown completed");
    }

    public StreamedBoundStore getVisibleSectionStream() {
        return this.visibleSectionStream;
    }

    public WorldEngine getEngine() {
        return this.worldIn;
    }

    public boolean hasRenderedGeometry() {
        return this.successfulFrames > 0 && (this.geometryData == null ? 0 : this.geometryData.getSectionCount()) > 0;
    }
}
