[CmdletBinding()]
param([switch]$Smoke, [switch]$ValidateVulkan, [switch]$Lifecycle, [switch]$Import, [switch]$SyncValidation, [switch]$Travel, [switch]$Fog)
$ErrorActionPreference = 'Stop'
if ($Fog -and ($Lifecycle -or $Import -or $Travel)) { throw 'Run -Fog separately from -Lifecycle, -Import and -Travel.' }
$root = Split-Path -Parent $PSScriptRoot
$previousJavaOptions = $env:JAVA_TOOL_OPTIONS
Push-Location $root
try {
    # Java does not consume HTTPS_PROXY automatically. Scope this to this build.
    if ($env:HTTPS_PROXY) {
        $proxy = [Uri]$env:HTTPS_PROXY
        if ($proxy.Scheme -ne 'http' -or $proxy.UserInfo) {
            throw 'Use Java proxy settings for authenticated or non-HTTP proxies.'
        }
        $env:JAVA_TOOL_OPTIONS = "$previousJavaOptions -Dhttps.proxyHost=$($proxy.Host) -Dhttps.proxyPort=$($proxy.Port) -Dhttp.proxyHost=$($proxy.Host) -Dhttp.proxyPort=$($proxy.Port)"
    }
    & .\gradlew.bat build compileSmokeTestJava --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed ($LASTEXITCODE)" }
    if ($Smoke) {
        New-Item -ItemType Directory -Force run-smoke | Out-Null
        if (-not (Test-Path run-smoke/options.txt)) {
            @'
preferredGraphicsBackend:"vulkan"
renderDistance:6
simulationDistance:5
maxFps:60
enableVsync:false
onboardAccessibility:false
soundCategory_master:0.0
'@ | Set-Content run-smoke/options.txt
        }
        $smokeArgs = @('runSmokeClient', '--console=plain')
        if ($ValidateVulkan -or $SyncValidation) { $smokeArgs += '-PvalidateVulkan' }
        if ($SyncValidation) { $smokeArgs += '-PsyncSmoke' }
        if ($Fog) { $smokeArgs += '-PfogSmoke' }
        if ($Lifecycle) { $smokeArgs += '-PlifecycleSmoke' }
        if ($Travel) { $smokeArgs += '-PlifecycleSmoke'; $smokeArgs += '-PtravelSmoke' }
        if ($Import) {
            $fixture = Join-Path $root 'run-smoke/import-fixture'
            if (-not (Test-Path -LiteralPath $fixture)) {
                $region = Join-Path $root 'run-smoke/saves/voxy-vulkan-26.3-smoke/dimensions/minecraft/overworld/region'
                if (-not (Test-Path -LiteralPath $region)) { throw 'Run the smoke world once before the import test.' }
                New-Item -ItemType Directory -Path $fixture | Out-Null
                Get-ChildItem -LiteralPath $region -Filter '*.mca' | Copy-Item -Destination $fixture
            }
            $smokeArgs += '-PimportSmoke'
        }
        New-Item -ItemType Directory -Force artifacts | Out-Null
        $smokeLog = Join-Path $root ('artifacts/smoke-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.log')
        & .\gradlew.bat @smokeArgs 2>&1 | Tee-Object -FilePath $smokeLog
        if ($LASTEXITCODE -ne 0) { throw "Vulkan smoke client failed ($LASTEXITCODE)" }
        if ($Fog) {
            & (Join-Path $PSScriptRoot 'Verify-FogLog.ps1') -Path $smokeLog
        } else {
            & (Join-Path $PSScriptRoot 'Verify-SmokeLog.ps1') -Path $smokeLog -Lifecycle:$Lifecycle -Import:$Import -Travel:$Travel -SyncValidation:$SyncValidation
        }
    }
} finally {
    Pop-Location
    $env:JAVA_TOOL_OPTIONS = $previousJavaOptions
}
