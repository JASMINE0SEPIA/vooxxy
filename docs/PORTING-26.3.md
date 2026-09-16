# Minecraft 26.3 native Vulkan port

## Target

Minecraft Java **26.3 release**, Java 25, Fabric Loader 0.19.5,
Fabric API 0.160.6+26.3, Sodium mc26.3-0.9.2-fabric.
Use Minecraft's own Vulkan device, command submission and presentation.

This is a local development fork. A successful compilation is not a claim of
functional parity or runtime validation. Existing upstream license applies.

## Provenance (2026-09-17)

* Official Voxy: https://github.com/MCRcortex/voxy,
  `534d58ec8b4aa412ef314b884295552c69d480a6` (26.2).
* Starting implementation: https://github.com/vistaero/Voxyrium,
  `56ef0f57eff88f2db5b7174f9889485076c9c1be` (26.2).
* Original Vulkan work: https://github.com/MCRcortex/voxy/pull/614,
  https://github.com/cochcoder/voxy/tree/vulkan,
  head `4886c5a7917f8a53cc4db05ba0472578515b4e4b`.
* Vulkan lifetime, VMA budgeting and synchronization fixes:
  https://github.com/DebuNeko233/voxy/tree/vulkan, selected commit
  `9592a570` (before the optional Vitrail/Photon integration). Imported the
  native Vulkan implementation, its mixins and matching command-generation
  shader. This reference still targets 26.2; 26.3 validation is required.
* Minecraft release verified through Mojang's version manifest:
  https://piston-meta.mojang.com/mc/game/version_manifest_v2.json.
  Client SHA-1: `e877b6a07acd633fb3bb475002175cec036e7b87`.
* Dependency versions verified against Fabric metadata and Modrinth's project
  version API. Mod Menu has no version labeled 26.3 at inspection time;
  its old API is compile-only until runtime compatibility is verified.

## Acceptance checklist

Each runtime item needs evidence from a real 26.3 Vulkan client. Inherited
implementations alone do not count as passing.

- [x] Build and package with exact release dependencies (`build` and `compileSmokeTestJava`).
- [x] All required mixins apply on startup and in world.
- [x] Adopt the active Minecraft Vulkan device and use no OpenGL on this path.
- [x] Live chunk ingestion, meshing, hierarchical traversal and distant terrain.
- [x] Persist terrain; restart and render previously visited regions.
- [x] Import an existing world into an isolated test database.
- [x] Opaque terrain, leaves/cutouts, water/lava and biome tint in inspected scenes.
- [x] Live block placement/removal and emissive block-light values reach Voxy storage.
- [x] Near/far depth handoff, environmental fog, SSAO and transparent composition in inspected scenes.
- [x] Camera changes, changing FOV and render distance.
- [x] Window resize and resource reload without invalid GPU resources.
- [x] World unload/reload and all three vanilla dimensions; stable native resource counts.
- [x] Core and synchronization Vulkan validation clean in the acceptance run.
- [x] Record frame intervals, memory and geometry growth during 1,152 blocks of travel.
- [ ] Manual walking/view-bob and fullscreen transitions (not covered by this harness).
- [ ] Multi-hour soak and additional GPU/driver combinations.

Iris shader packs, VR, replay mods and third-party Vulkan replacements are
separate integrations; their old compile-time APIs do not establish 26.3 support.

## Implementation approach

Preserve Voxy's storage, ingestion, CPU mesh generation and GPU-driven Vulkan
pipeline inherited from Voxyrium. Adapt against the actual 26.3 client and
Sodium binaries, especially render hooks, texture layouts, command ownership,
enabled device features and shader contracts. Keep test worlds under the
development run directory, separate from existing launcher instances.

## 26.3 changes verified against the client implementation

* RenderPearl replaces the former Blaze3D GPU packages; SDL replaces GLFW.
* Sodium 0.9.2 draws inside LevelRenderer's live render pass. Capture its
  matrices, finish the solid pass, record Voxy, then resume with LOAD semantics
  before classic transparency or OIT. Never record compute in that live pass.
* Request required storage/64-bit shader features before logical device creation.
  Enable indirect draw count only when Minecraft enabled that feature set.
* Vulkan fence timeout goes directly to `vkWaitSemaphores` in nanoseconds.
  The inherited 30,000 value caused reproducible world-entry timeouts.
* Packed D32S8 depth images must not list D32 as a compatible mutable view
  format. Use the original D32S8 format with a depth-only aspect view.
* Optional-mod mixins are gated on mod presence. The smoke harness is a separate
  source set and always forces `--graphicsBackend VULKAN`.
* BlockState.CODEC can persist a StringTag; preserve the tag type on decoding and
  retain the legacy compound-state data fixer path. Release the world creation
  lock and storage on failure so disconnect cannot deadlock.
* Batch top-level node edits into one final-list upload. Synchronize overlapping
  destination ranges within upload commits; sequential copy commands alone do
  not order write-after-write hazards discovered during travel.
* Coalesce client block and light updates per section, draining at most 128
  sections per tick so active edits do not wait for chunk unload to reach LODs.

Detailed evidence, test limits and the host crash-marker isolation used for
strict synchronization validation are in [VALIDATION-26.3.md](VALIDATION-26.3.md).

The installed PCL instances under `C:\Program Files also\PCL` are read-only
references. No build, launch, configuration, save or mod installation targets
that directory.
