# External Vulkan LOD development

vooxxy retains its standalone Minecraft 26.3 Vulkan renderer with Sodium. With
gysf active it keeps ingestion, storage, model baking and immutable mesh selection
while suppressing its raster geometry buffers and draws. Sodium is optional in
this external mode. Turn renderer modes through a world reload.

## Imported work

- Official Voxy `263` was inspected through
  `61c1dee5077c73fced865025430c6ce7dc87537c` (2026-10-10 author date).
- Its chunk `DataVersion` migration before import was cherry-picked intact.
- Its fluent `ReuseVertexConsumer.setLineWidth` result was adopted.
- Existing Vulkan synchronization, fluid meshing, environmental fog, safe cleanup
  and cache decoding remain in this fork. Official GL/Iris-only hooks and the
  divergent mapping/storage-format changes were not applied to the Vulkan path.
- API 5 mesh selection, complete hierarchy coverage, cache retention and worker
  coalescing were adapted from `skayla2007/voxy` at
  `0b61b96d1e4a495522ece3c1428e7e78b162c0d2`.

## API

`me.cortex.voxy.api.VoxyLodApi` registers an external renderer before world
creation. `update` is render-thread-only and returns an immutable `LodScene`
containing section keys/revisions, packed quads, fallback ancestors, models,
colours and the provider-owned Vulkan atlas view. The consumer owns its readiness
state. Epoch changes invalidate a prior world session. Provider uploads have a
submission retirement boundary even when no Voxy raster frame executes.

`CausticaBridge.pollScene` advances the provider at client tick start and exposes
the complete immutable API 5 scene to gysf. The consumer copies CPU data and
borrows the atlas image view. Atlas upload barriers include ray-tracing shader
reads when an external RT renderer is active. The legacy `poll` colour-proxy
adapter remains available for older consumers and controlled comparisons.

gysf consumes per-face textures, conditional biome tint, cutout alpha and the
provider's fractional face positions and four-corner fluid heights. Its 3D API
selection must not be passed through a 2D DH coverage filter. Material normal/
specular maps, distant light sampling and full transition acceptance remain
separate work. The selection cap defaults to 1024 blocks and can be selected
with `voxy.external.maxDistanceBlocks`.

gysf 0.2.1 stages overlapping 3D source branches until all replacement BLAS
batches are ready, then publishes each branch atomically. This covers both
refinement and coarsening, as well as same-key geometry revisions. Consumers
must retain selected empty leaves as completion markers: dropping a zero-quad
mesh loses the information needed to remove previously rendered terrain.
The consumer can validate published partitions with
`gysf.voxy.validateCoverage=true`. This uses the existing API 5 contract and
requires no provider ABI change.

With an external renderer active, leaf models retain the cutout layer rather
than standalone Voxy's opaque foliage override. This keeps mip-filtered alpha
coverage available to both radiance and shadow rays; standalone foliage policy
is unchanged.

The paired project is [gysf](https://github.com/JASMINE0SEPIA/gysf), locally the sibling `../gysf`. Its joint smoke run uses an isolated
world, no Sodium and the most recently built vooxxy JAR. No PCL installation
or player save is part of the development build.

The original Voxy license remains unchanged. Caustica's LGPL license does not
relicense Voxy or the proprietary NVIDIA runtime components.

## Initial verification, 2026-10-15

`check` passes 22 API hierarchy/mesh/retention/worker tests. Standalone Sodium
Vulkan lifecycle smoke passes block placement/removal, block-state persistence
(38,303 round trips and a legacy compound), renderer rebuild, window/FOV/distance
change, resource reload, Nether travel and return, save and shutdown. All Voxy
GPU resource wrappers are released at exit. No Vulkan VUID or synchronization
hazards were reported. Minecraft recovered from a swapchain-out-of-date warning
while resizing the window; unrelated OSHI registry warnings remain.

Joint gysf runs prove external meshes become resident RT LOD acceleration
structures with no Sodium. The initial SVGF run reports 3271 RT frames, peak
336 source meshes and 359 RT LOD sections, with Vulkan/synchronization validation
clean. A default DLSS-RR/OMM run also completes; its sole VUID is a known inverted
micromap check in the installed SDK validation layer, documented in gysf's
[validation record](https://github.com/JASMINE0SEPIA/gysf/blob/jasmine/26.3-vulkan/docs/VALIDATION-2026-10-15.md).

This verifies startup, data flow and lifecycle, not final textured/PBR fidelity
or a performance target. PCL mods and saves are outside these tests.
