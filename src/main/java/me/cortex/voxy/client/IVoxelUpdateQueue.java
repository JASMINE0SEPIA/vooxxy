package me.cortex.voxy.client;

import net.minecraft.core.SectionPos;

/** Per-client-world coalescing of terrain and light changes. */
public interface IVoxelUpdateQueue {
    void voxy$queueSectionUpdate(SectionPos section);
}
