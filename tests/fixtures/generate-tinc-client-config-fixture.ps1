$ErrorActionPreference = 'Stop'

$fixturePath = Join-Path $PSScriptRoot 'tinc-client-config.zip'
Add-Type -AssemblyName System.IO.Compression
$zipStream = [System.IO.File]::Open($fixturePath, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write)
$archive = [System.IO.Compression.ZipArchive]::new($zipStream, [System.IO.Compression.ZipArchiveMode]::Create, $false)
$timestamp = [DateTimeOffset]::Parse('2026-09-08T00:00:00+00:00')

$entries = [ordered]@{
    'office/tinc.conf' = @"
Name = client_fixture
Mode = router
AddressFamily = ipv4
ConnectTo = server_master
"@
    'office/hosts/server_master' = @"
Address = 203.0.113.10
Port = 655
Subnet = 10.254.0.1/32
-----BEGIN RSA PUBLIC KEY-----
MIIBCgKCAQEAzT2IuyY+N7RUo902/Vcx0Q8I0sVNtEM/8L+uE3lXozVTkBRS3R/J
TgsGFiDzvUsIc1rgOt9IaU43CGwmvxMMtSXNthR227js9aojzMb5kQw9iyhUXkNT
2sNloNMqmFDVwqO9hAjuYZ/Kqyy5f6NYy9W8FUAd68apu0yarGRuLCqc1f+RTU2R
Gshint6NNUI2xtcTTck6D3666seZk1y/yCho+CKkxLKy7MGGuHRhiSO0NjPxVBE5
AT85EEtcv1hgQwxvrQhhazt9L6YT451w8V1tVXYaTdj9Xyk5l5TLwlgX346msWVV
GNmzGRjLYtHa+5dxU7nN2pwidfZtL6T88QIDAQAB
-----END RSA PUBLIC KEY-----
"@
}

try {
    foreach ($item in $entries.GetEnumerator()) {
        $entry = $archive.CreateEntry($item.Key, [System.IO.Compression.CompressionLevel]::Optimal)
        $entry.LastWriteTime = $timestamp
        $writer = [System.IO.StreamWriter]::new($entry.Open(), [System.Text.UTF8Encoding]::new($false))
        try {
            $writer.Write($item.Value.Replace("`r`n", "`n"))
        } finally {
            $writer.Dispose()
        }
    }
} finally {
    $archive.Dispose()
    $zipStream.Dispose()
}
