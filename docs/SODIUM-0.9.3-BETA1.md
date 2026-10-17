# Sodium 0.9.3-beta.1 compatibility

The player's 2026-10-17 startup failed at Fabric resolution with `NEG_HARD_DEP`:
Voxy Vulkan.13 explicitly blocked every Sodium version newer than 0.9.3-alpha.1.
This was a metadata rejection before renderer initialization, not evidence that
Sodium cannot render on Minecraft's native Vulkan backend or a measured gysf conflict.

Vulkan.14 compiles against `mc26.3-0.9.3-beta.1-fabric` and admits exactly the
three qualified versions: 0.9.2, 0.9.3-alpha.1 and 0.9.3-beta.1. Future releases
and intermediate unqualified prereleases remain blocked. Sodium is still an
optional suggestion, so external gysf operation without Sodium remains supported.
Three new tests use Fabric Loader's real `VersionPredicate` against the processed
metadata, including the installed `+mc26.3` build suffix and the hard `breaks` rules.
All 76 provider tests and the beta.1 compilation pass.

Binary comparison of the installed beta.1 and cached alpha.1 finds 34 of Voxy's
35 explicitly named Sodium import classes identical. The changed class is
`VideoSettingsScreen`; its `javap -p` signatures are unchanged. All 22 classes
in the two wildcard-imported configuration API packages are identical as well.
The [official beta.1 release](https://github.com/CaffeineMC/sodium/releases/tag/mc26.3-0.9.3-beta.1)
also describes the video-settings close fix. No Java API adaptation was necessary;
runtime mixin and joint renderer checks remain separate from compilation.

The joint fixture copies the player's `Nuovo mondo` save, current configuration,
resource packs and JARs, including the installed Sodium beta.1 and Chunksmith.
It runs unchanged gysf 0.7.3 with the exact Vulkan.14 artifact built from `0b86522c`.
Only the isolated copy omits Dynamic FPS to avoid background throttling. The player
profile is not opened as a writable runtime directory. Evidence, source pins,
protected file hashes, the original loader failure, build/runtime logs and the
old installed provider are retained under the sibling gysf repository's ignored
`artifacts/2026-10-17/sodium-beta1` directory.

The exact three-mod combination loads and renders successfully under Vulkan
validation layer 1.4.363. The copied world's existing Chunksmith task also runs
during this test. Four explicit after-tick renderer rebuilds pass (6 -> 32 -> 6
-> 32, then a same-distance F3+A-style rebuild), with 2,810 live provider-boundary
checks, 2,813 rendered frames, 106 near ownership checks and 33,004 finer-source
checks. DLSS-RR, NGX clear synchronization, 3x frame generation and opacity
micromaps are active. There are zero VUID/synchronization reports. Native RT LOD
allocation returns to zero (205,733,280-byte peak, zero deferrals), as do the water
field and bounds cache. The strict gysf Vulkan log gate passes.

The delivered provider JAR matches all 496 compiled main classes, both mixin
configurations and all processed metadata fields. Loom's additional nested-JAR
declarations are checked against actual archive entries. The regression harness
is development-only and is not packaged. gysf and the player's Sodium JAR are
unchanged. This establishes startup and the tested rebuild path for the joint
stack; it is not a measured Sodium speedup, full visual acceptance or a separate
standalone beta.1 lifecycle qualification.
