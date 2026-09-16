#Requires -RunAsAdministrator
param(
    [Parameter(Mandatory = $true)][string]$OutputPath
)

$ErrorActionPreference = 'Stop'
$settingsPath = Join-Path $env:ProgramData 'TincClient\settings.json'
$settings = Get-Content -LiteralPath $settingsPath -Raw | ConvertFrom-Json
$expectedDataRoot = [IO.Path]::GetFullPath((Join-Path $env:ProgramData 'TincClient\data'))
$actualDataRoot = [IO.Path]::GetFullPath([string]$settings.data_dir)
if ($actualDataRoot -ne $expectedDataRoot) {
    throw 'Agent data root is outside the managed directory.'
}

$activePath = Join-Path $actualDataRoot 'active.json'
$active = Get-Content -LiteralPath $activePath -Raw | ConvertFrom-Json
if ($active.generation -notmatch '^gen-[A-Za-z0-9_.-]{1,128}$' -or
    $active.identity.sid -notmatch '^[A-Za-z0-9_]{1,64}$' -or
    $active.identity.net_name -notmatch '^[A-Za-z0-9_]{1,64}$') {
    throw 'Active configuration index contains an invalid identifier.'
}

$generationRoot = [IO.Path]::GetFullPath((Join-Path (Join-Path $actualDataRoot 'generations') $active.generation))
$expectedGenerationParent = [IO.Path]::GetFullPath((Join-Path $actualDataRoot 'generations')) + [IO.Path]::DirectorySeparatorChar
if (-not $generationRoot.StartsWith($expectedGenerationParent, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Active generation escapes the managed directory.'
}

function Get-NormalizedSha256([string]$Path) {
    $text = [IO.File]::ReadAllText($Path, [Text.Encoding]::UTF8).Replace("`r`n", "`n").Trim()
    $bytes = [Text.Encoding]::UTF8.GetBytes($text)
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-', '').ToLowerInvariant() }
    finally { $sha.Dispose() }
}

$clientHost = Join-Path (Join-Path $generationRoot 'hosts') $active.identity.sid
$serverHost = Join-Path $generationRoot 'hosts\server_master'
$privateKey = Join-Path $generationRoot 'rsa_key.priv'
$sidBytes = [Text.Encoding]::UTF8.GetBytes([string]$active.identity.sid)
$sidSha = [Security.Cryptography.SHA256]::Create()
try { $sidHash = ([BitConverter]::ToString($sidSha.ComputeHash($sidBytes))).Replace('-', '').ToLowerInvariant().Substring(0, 12) }
finally { $sidSha.Dispose() }

$adapterIp = @(Get-NetIPAddress -InterfaceAlias ([string]$settings.interface) -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty IPAddress)
$routes = @(Get-NetRoute -InterfaceAlias ([string]$settings.interface) -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty DestinationPrefix)

$result = [ordered]@{
    active = $true
    netName = [string]$active.identity.net_name
    sidHash = $sidHash
    nodeIp = [string]$active.identity.node_ip
    clientHostSha256 = Get-NormalizedSha256 $clientHost
    serverHostSha256 = Get-NormalizedSha256 $serverHost
    privateKeyPresent = (Test-Path -LiteralPath $privateKey -PathType Leaf) -and ((Get-Item -LiteralPath $privateKey).Length -gt 0)
    adapter = [string]$settings.interface
    adapterIpv4 = $adapterIp
    routes = $routes
}
[IO.File]::WriteAllText(
    ([IO.Path]::GetFullPath($OutputPath)),
    ($result | ConvertTo-Json -Depth 4),
    (New-Object Text.UTF8Encoding($false))
)
