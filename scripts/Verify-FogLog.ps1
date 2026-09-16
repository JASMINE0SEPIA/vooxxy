[CmdletBinding()]
param([Parameter(Mandatory)][string]$Path)
$ErrorActionPreference = 'Stop'
$body = Get-Content -LiteralPath $Path -Raw
foreach ($required in @('VOXY_FOG: all scenarios completed', 'VOXY_FOG: all Voxy GPU resource wrappers released', 'BUILD SUCCESSFUL')) {
    if (-not $body.Contains($required)) { throw "Incomplete fog run: missing '$required'" }
}
foreach ($scene in @('clear', 'atmosphere', 'oit', 'off', 'water', 'blindness', 'lava', 'horizon-hard', 'horizon-fade')) {
    if (-not $body.Contains("VOXY_FOG: $scene environmental=") -or -not $body.Contains("Saved screenshot as fog-$scene.png")) {
        throw "Missing fog scene: $scene"
    }
}
if ($body -match 'VUID-|hazard detected|/ERROR\]\s+\(Voxy\)|Native Vulkan frame failed') {
    throw 'Fog run contains Vulkan validation or renderer errors'
}
Write-Output "Fog scenario log verified: $Path (inspect screenshots for visual continuity)"
