#Requires -RunAsAdministrator
param(
    [Parameter(Mandatory = $true)][string]$AgentBinary,
    [Parameter(Mandatory = $true)][ValidatePattern('^[A-Fa-f0-9]{64}$')][string]$AgentSha256
)

$ErrorActionPreference = 'Stop'
$serviceName = 'TincClientAgent'
$installRoot = Join-Path $env:ProgramFiles $serviceName
$installedAgent = Join-Path $installRoot 'vpn-agent.exe'
$expectedServicePath = '"' + $installedAgent + '" --service'
$sourceAgent = (Resolve-Path -LiteralPath $AgentBinary).Path

$service = Get-CimInstance Win32_Service -Filter "Name='$serviceName'"
if (-not $service) {
    throw "$serviceName is not installed."
}
if ($service.StartName -ne 'LocalSystem' -or $service.PathName -ne $expectedServicePath) {
    throw 'Service identity or executable path does not match the managed installation.'
}
if (-not (Test-Path -LiteralPath $installedAgent -PathType Leaf)) {
    throw 'The installed Agent binary is missing.'
}
if (-not (Test-Path -LiteralPath (Join-Path $env:ProgramData 'TincClient\settings.json') -PathType Leaf)) {
    throw 'Managed Agent settings are missing.'
}

$sourceHash = (Get-FileHash -LiteralPath $sourceAgent -Algorithm SHA256).Hash
if ($sourceHash -ne $AgentSha256.ToUpperInvariant()) {
    throw 'Source Agent SHA-256 does not match the approved value.'
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backup = Join-Path $installRoot "vpn-agent.exe.bak-$stamp"
$temporary = Join-Path $installRoot "vpn-agent.exe.tmp-$stamp"
Copy-Item -LiteralPath $installedAgent -Destination $backup
$oldHash = (Get-FileHash -LiteralPath $backup -Algorithm SHA256).Hash

try {
    Stop-Service -Name $serviceName
    (Get-Service -Name $serviceName).WaitForStatus('Stopped', [TimeSpan]::FromSeconds(20))
    Copy-Item -LiteralPath $sourceAgent -Destination $temporary
    if ((Get-FileHash -LiteralPath $temporary -Algorithm SHA256).Hash -ne $sourceHash) {
        throw 'Copied Agent hash verification failed.'
    }
    Move-Item -LiteralPath $temporary -Destination $installedAgent -Force
    Start-Service -Name $serviceName
    (Get-Service -Name $serviceName).WaitForStatus('Running', [TimeSpan]::FromSeconds(20))
} catch {
    Remove-Item -LiteralPath $temporary -Force -ErrorAction SilentlyContinue
    Stop-Service -Name $serviceName -Force -ErrorAction SilentlyContinue
    Copy-Item -LiteralPath $backup -Destination $installedAgent -Force
    Start-Service -Name $serviceName -ErrorAction SilentlyContinue
    throw
}

$running = Get-CimInstance Win32_Service -Filter "Name='$serviceName'"
[pscustomobject]@{
    ServiceName = $running.Name
    State = $running.State
    StartMode = $running.StartMode
    ProcessId = $running.ProcessId
    OldSha256 = $oldHash
    NewSha256 = $sourceHash
    BackupName = Split-Path -Leaf $backup
    SettingsPreserved = Test-Path -LiteralPath (Join-Path $env:ProgramData 'TincClient\settings.json')
}
