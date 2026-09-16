package me.cortex.voxy.client.mixin.minecraft;

import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import me.cortex.voxy.commonImpl.VoxyCommon;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class MixinClientLevel implements me.cortex.voxy.client.IVoxelUpdateQueue {

    @Unique private final java.util.Set<Long> voxy$pendingSections = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @Override
    public void voxy$queueSectionUpdate(SectionPos section) {
        if (VoxyConfig.CONFIG.ingestEnabled) this.voxy$pendingSections.add(section.asLong());
    }

    @Unique
    private int bottomSectionY;

    @Shadow public abstract ClientChunkCache getChunkSource();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void voxy$getBottom(
            final ClientPacketListener connection,
            final ClientLevel.ClientLevelData levelData,
            final ResourceKey<Level> dimension,
            final Holder<DimensionType> dimensionType,
            final int serverChunkRadius,
            final int serverSimulationDistance,
            final LevelExtractor levelExtractor,
            final boolean isDebug,
            final long biomeZoomSeed,
            final int seaLevel,
            CallbackInfo cir) {
        this.bottomSectionY = ((Level)(Object)this).getMinY()>>4;
    }

    @Inject(method = "setBlocksDirty", at = @At("TAIL"))
    private void voxy$injectIngestOnStateChange(BlockPos pos, BlockState old, BlockState updated, CallbackInfo cir) {
        if (old == updated) return;
        this.voxy$queueSectionUpdate(SectionPos.of(pos));
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void voxy$flushSectionUpdates(java.util.function.BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        if (!VoxyConfig.CONFIG.ingestEnabled || VoxyCommon.getInstance() == null) {
            this.voxy$pendingSections.clear();
            return;
        }
        if (this.voxy$pendingSections.isEmpty()) return;
        var self = (Level)(Object)this;
        var wi = WorldIdentifier.of(self);
        if (wi == null) return;
        // A fill command or light propagation can dirty one section many times.
        // Defer snapshots until the tick completes, and bound work per tick.
        var updates = this.voxy$pendingSections.iterator();
        for (int remaining = 128; remaining > 0 && updates.hasNext(); remaining--) {
            var csp = SectionPos.of(updates.next());
            updates.remove();
            var chunk = self.getChunk(csp.x(), csp.z(), ChunkStatus.FULL, false);
            int index = csp.y() - this.bottomSectionY;
            if (chunk != null && index >= 0 && index < chunk.getSections().length) {
                var section = chunk.getSection(index);
                var lp = self.getLightEngine();

                var blp = lp.getLayerListener(LightLayer.BLOCK).getDataLayerData(csp);
                var slp = lp.getLayerListener(LightLayer.SKY).getDataLayerData(csp);

                VoxelIngestService.rawIngest(wi, section, csp.x(), csp.y(), csp.z(), blp == null ? null : blp.copy(), slp == null ? null : slp.copy());
            }
        }
    }
}
