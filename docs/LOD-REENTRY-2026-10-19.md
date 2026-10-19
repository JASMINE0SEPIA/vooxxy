# Restore historical LODs with an external Vulkan renderer

Vulkan.15 fixes an external-renderer bootstrap regression: a saved standalone
Blaze3D preference deferred native initialization, although gysf reads LOD data
from `VkRenderCore`. `CausticaBridge.pollScene` returned null before reaching the
API that could establish that provider. Previously visited terrain could therefore
remain visible in gysf's temporary near-terrain cache after lowering view distance,
but disappear after leaving the world cleared that cache.

When an external renderer owns a Vulkan world, Voxy now selects the native data
provider regardless of the saved standalone preference. The preference itself is
preserved. Standalone Blaze3D and OpenGL selection are unchanged. The render-thread
LOD API retries renderer creation once the world and Voxy instance are ready;
restoration does not depend on new chunk ingestion. The bridge no longer rejects
the request before that retry can run. Storage format and external API version 6
are unchanged; no cache migration or deletion is required.

The player's `Greenfield v0.5.4` database was copied and inspected read-only:
49,675 L0-L4 records decompress, with no missing nonempty parent records or child
links. A separate complete physical save copy, current mod set including Sodium
0.9.3-beta.1, and unchanged gysf 0.7.4 form the replay fixture. Dynamic FPS is omitted
only from the test fixture; disabled Chunksmith remains disabled.

The old Vulkan.14 baseline selects Blaze3D and publishes zero historical sources,
GPU LOD instances or sampled far coverage even after a cold start and a stationary
wait. The fixed replay uses 32 -> 6 chunk view distance, exits and re-enters without
movement. Ingestion is disabled before disconnect. It restores 2,017 nonempty far
sources from 2,438 total selected sources, 559 GPU LOD batches and 236 sampled far
sections after 45 seconds of world ticks. Scene epochs change 1 -> 2 -> 3 across
the distance rebuild and world re-entry. Progressive publication remains active;
these counts are not a claim that every fine batch has completed by 45 seconds.

The strict Vulkan gate passes with DLSS-RR/NGX synchronization exercised and no
VUID or synchronization reports. Native RT LOD allocations return to zero at exit
(516,841,728-byte peak, zero deferrals). All 79 provider tests pass, including the
standalone/external renderer-selection regression cases and backend isolation.

Evidence and the development-only replay are in the sibling gysf repository:
`artifacts/2026-10-19/lod-reentry/` and `LodReentryCapture`. The final packaged
artifact additionally receives a separate cold-start replay at the saved low view
distance, with ingestion still disabled, waiting for the pending rebuild to drain.

The final `bd20c89` JAR passes that cold replay: all 2,412 compacted GPU LOD batches
finish, sampled far coverage reaches 3,151 sections, and the pending queue is
empty. All 2,017 nonempty far sources remain selected, ingestion stays disabled,
and there is no temporary view-distance reduction. Reconstruction takes about
225 seconds in this validation-enabled fixture and publishes useful partial
results throughout; this is not a performance guarantee for other worlds.
The Vulkan/NGX gate passes again, native allocations return to zero with no
deferrals, and peak native LOD memory is 1,418,030,608 bytes. The packaged JAR's
496 runtime classes match both the build output and the candidate used for the
full distance-change/re-entry sequence.
