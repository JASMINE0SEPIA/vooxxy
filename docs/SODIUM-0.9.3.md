# Sodium 0.9.3-alpha.1 compatibility

The installed Minecraft 26.3 instance failed before client initialization because
Voxy's metadata only accepted Sodium `>=0.9.2- <=0.9.2`. The installed Sodium
`0.9.3-alpha.1+mc26.3` was rejected with `HARD_DEP_NO_CANDIDATE`.

Voxy `0.2.20-26.3-vulkan.3-dev` compiles against
`mc26.3-0.9.3-alpha.1-fabric` and explicitly accepts `0.9.2` and `0.9.3-alpha.1`.
It does not claim compatibility with untested future prereleases. Fabric API's
build/runtime baseline is now `0.161.0+26.3`, matching the installed instance.
The README remains the requested single sentence.

The [Sodium upstream comparison](https://github.com/CaffeineMC/sodium/compare/mc26.3-0.9.2...mc26.3-0.9.3-alpha.1)
changes fluid rendering, a GL fallback workaround and a video-settings option;
it does not change Voxy's primary integration interfaces. Binary comparison of
all 37 directly imported Sodium classes found identical class bytes between the
installed new JAR and the previous cached 0.9.2 JAR. No renderer API edits were
necessary; compilation and runtime checks still matter for mixin targets.

## Reproduction

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25'
.\scripts\Build-26.3.ps1 -Smoke -Lifecycle -SyncValidation
```

The first compatibility run used copies of the instance's ten other mod JARs,
including Iris 1.11.6, Farmer's Delight, Jade, Middle,
Veinminer, Mouse Tweaks, skin layers, Dynamic FPS and Fabric Language Kotlin.
Sodium, Fabric API and Voxy are supplied by the development runtime itself.
Only the isolated test save is opened; user saves are not used. The existing validation fixture
disables Minecraft's GPU crash-marker writes, as documented in the original
validation record, while retaining Vulkan synchronization validation.

## Additional Iris startup failure

After the Sodium dependency check passed, Iris 1.11.6 independently aborted the
JVM during `Iris.duringRenderSystemInit -> setDebug -> IrisRenderSystem`.
`SamplerLimits` called `GL11C.glGetInteger` without an OpenGL context on the native
Vulkan backend. The installed instance explicitly selects `vulkan` as well.
Turning shader packs off does not skip this initialization call.

The failed run is retained in `artifacts/sodium-093/with-iris-failed.log`.
The follow-up run excludes Iris, retaining the other nine mod JARs. Installation
backs up and disables the Iris JAR for this Vulkan instance; it preserves its
configuration and shader packs. This is not a claim that Iris shader packs now
work with the native Vulkan backend.

## Validated result (2026-09-26)

With Iris excluded, the complete lifecycle run passed with Sodium
`0.9.3-alpha.1+mc26.3`, Fabric API `0.161.0+26.3`, and the remaining installed
mods. It covered 38,303 block-state persistence round trips, live placement,
removal and block-light ingestion, populated native Vulkan LOD frames, renderer
recreation, FOV/distance/window changes, OIT, resource reload, and an overworld /
nether round trip. LOD on/off and OIT screenshots were inspected. At disconnect,
Voxy's GPU buffer and image wrapper counts returned to zero. The log verifier
passed with no Vulkan VUIDs, synchronization hazards or Voxy renderer errors.
Windows OSHI performance-counter warnings remain unrelated to this repair.

The local evidence is `artifacts/sodium-093/passed.log` and the accompanying
screenshots. The older 0.9.2 allowance retains the previously validated release;
this new lifecycle run specifically validates 0.9.3-alpha.1. No player save or
launcher configuration is part of the installation change.
