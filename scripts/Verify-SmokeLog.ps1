[CmdletBinding()]
param([Parameter(Mandatory)][string]$Path, [switch]$Lifecycle, [switch]$Import, [switch]$Travel, [switch]$SyncValidation)
$ErrorActionPreference = 'Stop'
$lines = Get-Content -LiteralPath $Path
$body = $lines -join "`n"
foreach ($required in @('block state persistence round trips passed', 'legacy compound state mapping passed',
    'live block placement and block-light ingestion passed', 'live block removal ingestion passed',
    'failed-world cleanup regression passed', 'finished world smoke interval; saving and closing',
    'VK render core shutdown completed', 'BUILD SUCCESSFUL')) {
    if (-not $body.Contains($required)) { throw "Incomplete smoke run: missing '$required' in $Path" }
}
$scenarioMarkers = @('backend=minecraft=vulkan, renderer=native')
if ($Lifecycle -or $Travel) { $scenarioMarkers += @('resource reload completed', 'enabled improved transparency (OIT)') }
if ($Import) { $scenarioMarkers += 'isolated region import passed' }
if ($Travel) { $scenarioMarkers += @('smoke-2600.png', 'smoke-3400.png', 'frame intervals (ms;') }
if ($SyncValidation -or $body.Contains('synchronization validation requested=true')) {
    $scenarioMarkers += 'sync validation enabled; vanilla crash-marker instrumentation isolated'
}
foreach ($marker in $scenarioMarkers) {
    if (-not $body.Contains($marker)) { throw "Missing scenario evidence '$marker' in $Path" }
}
if ($body -notmatch 'VK successful frames/geometry sections: [1-9][0-9]*/[1-9][0-9]*') {
    throw "No populated native Vulkan frames in $Path"
}
$failures = $lines | Where-Object { $_ -match 'VUID-|hazard detected|/ERROR\]\s+\(Voxy\)|Native Vulkan frame failed' }
if ($failures) { throw "Smoke validation failed in ${Path}:`n$($failures | Select-Object -First 12 | Out-String)" }
Write-Output "Voxy smoke log verified: $Path"
