param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$')]
    [string]$Version,

    [Parameter(Mandatory = $true)]
    [string]$OutputPath,

    [datetime]$CreatedAt = [datetime]::UtcNow
)

$metadata = [ordered]@{
    download_url = 'https://github.com/roflsunriz/povo-2.0-revanced/releases/latest/download/povo-2.0-patches.rvp'
    created_at = $CreatedAt.ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ss')
    signature_download_url = $null
    description = 'povo 2.0 のプリペイド・プロモコードを終端ごとに自動適用する ReVanced パッチ'
    version = $Version
}

$parent = Split-Path -Parent $OutputPath
if ($parent) {
    New-Item -ItemType Directory -Force -Path $parent | Out-Null
}
$metadata | ConvertTo-Json | Set-Content -LiteralPath $OutputPath -Encoding utf8NoBOM
