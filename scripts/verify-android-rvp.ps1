param(
    [Parameter(Mandatory = $true)]
    [string]$Path
)

$resolved = Resolve-Path -LiteralPath $Path -ErrorAction Stop
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead($resolved.Path)

try {
    $entries = @($archive.Entries | ForEach-Object FullName)
    $dexEntries = @($entries | Where-Object { $_ -match '^classes(?:\d+)?\.dex$' })
    if ($dexEntries.Count -eq 0) {
        throw "Android用DEXがありません。:patches:buildAndroidを実行してください: $Path"
    }
    if ('META-INF/MANIFEST.MF' -notin $entries) {
        throw "RVP manifestがありません: $Path"
    }
    if (-not ($entries | Where-Object { $_ -like 'extensions/*.rve' })) {
        throw "ReVanced extensionがありません: $Path"
    }

    Write-Output "Android RVP検証成功: $($dexEntries -join ', ')"
} finally {
    $archive.Dispose()
}
