#Requires -RunAsAdministrator
param(
    [Parameter(Mandatory = $true)][string]$AgentBinary,
    [Parameter(Mandatory = $true)][string]$TincDirectory,
    [Parameter(Mandatory = $true)][ValidatePattern('^S-1-\d+(?:-\d+)+$')][string]$OwnerSid,
    [Parameter(Mandatory = $true)][ValidatePattern('^[a-zA-Z0-9_. -]{1,128}$')][string]$Interface,
    [Parameter(Mandatory = $true)][ValidatePattern('^[A-Fa-f0-9]{64}$')][string]$TincSha256
)

$ErrorActionPreference = 'Stop'
$serviceName = 'TincClientAgent'
$installRoot = Join-Path $env:ProgramFiles $serviceName
$dataRoot = Join-Path $env:ProgramData 'TincClient'

if (Get-Service -Name $serviceName -ErrorAction SilentlyContinue) {
    throw "$serviceName already exists; refusing to overwrite it."
}

$sourceAgent = (Resolve-Path -LiteralPath $AgentBinary).Path
$sourceTinc = (Resolve-Path -LiteralPath $TincDirectory).Path
$sourceTincBinary = Join-Path $sourceTinc 'tincd.exe'
if (-not (Test-Path -LiteralPath $sourceAgent -PathType Leaf)) {
    throw 'AgentBinary must point to vpn-agent.exe.'
}
if (-not (Test-Path -LiteralPath $sourceTincBinary -PathType Leaf)) {
    throw 'TincDirectory must contain tincd.exe.'
}

$actualTincSha256 = (Get-FileHash -LiteralPath $sourceTincBinary -Algorithm SHA256).Hash
if ($actualTincSha256 -ne $TincSha256.ToUpperInvariant()) {
    throw 'tincd.exe SHA-256 does not match the approved value.'
}
$tincVersion = & $sourceTincBinary --version 2>&1
if ($LASTEXITCODE -ne 0 -or (($tincVersion | Out-String) -notmatch '(?im)^tinc version 1\.0\.36(?:\s|$)')) {
    throw 'Only the reviewed tinc version 1.0.36 is accepted.'
}

$adapter = Get-NetAdapter -Name $Interface -IncludeHidden -ErrorAction SilentlyContinue
if (-not $adapter) {
    throw "Dedicated TAP adapter '$Interface' was not found."
}
if ($adapter.InterfaceDescription -notmatch '(?i)^TAP-(?:Windows|Win32) Adapter V9$') {
    throw "Adapter '$Interface' is not a supported TAP Adapter V9 device."
}

if (Test-Path -LiteralPath $installRoot) {
    throw 'Install directory already exists; refusing to overwrite it.'
}
if (Test-Path -LiteralPath (Join-Path $dataRoot 'settings.json')) {
    throw 'Existing settings were found; refusing to overwrite them.'
}

New-Item -ItemType Directory -Path $installRoot, $dataRoot -Force | Out-Null
foreach ($path in @($installRoot, $dataRoot)) {
    $acl = New-Object Security.AccessControl.DirectorySecurity
    $acl.SetAccessRuleProtection($true, $false)
    foreach ($sidText in @('S-1-5-18', 'S-1-5-32-544')) {
        $sid = New-Object Security.Principal.SecurityIdentifier($sidText)
        $rule = New-Object Security.AccessControl.FileSystemAccessRule(
            $sid,
            'FullControl',
            'ContainerInherit,ObjectInherit',
            'None',
            'Allow'
        )
        [void]$acl.AddAccessRule($rule)
    }
    Set-Acl -LiteralPath $path -AclObject $acl
}

Copy-Item -LiteralPath $sourceAgent -Destination (Join-Path $installRoot 'vpn-agent.exe')
$tincRoot = Join-Path $installRoot 'tinc'
New-Item -ItemType Directory -Path $tincRoot | Out-Null
Copy-Item -LiteralPath $sourceTincBinary -Destination (Join-Path $tincRoot 'tincd.exe')

$settings = @{
    data_dir = Join-Path $dataRoot 'data'
    tinc_binary = Join-Path $tincRoot 'tincd.exe'
    interface = $Interface
    interface_prefix = 32
    owner = $OwnerSid
}
[IO.File]::WriteAllText(
    (Join-Path $dataRoot 'settings.json'),
    ($settings | ConvertTo-Json),
    (New-Object Text.UTF8Encoding($false))
)

$servicePath = '"' + (Join-Path $installRoot 'vpn-agent.exe') + '" --service'
New-Service `
    -Name $serviceName `
    -DisplayName 'Tinc Client Agent' `
    -BinaryPathName $servicePath `
    -StartupType Automatic `
    -Description 'Restricted local Tinc VPN management service' | Out-Null
Start-Service -Name $serviceName
(Get-Service -Name $serviceName).WaitForStatus('Running', [TimeSpan]::FromSeconds(20))

$service = Get-CimInstance Win32_Service -Filter "Name='$serviceName'"
[pscustomobject]@{
    ServiceName = $service.Name
    State = $service.State
    StartMode = $service.StartMode
    ProcessId = $service.ProcessId
    Adapter = $adapter.Name
    AdapterDescription = $adapter.InterfaceDescription
    TincSha256 = $actualTincSha256
}
