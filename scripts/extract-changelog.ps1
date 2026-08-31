param(
    [Parameter(Mandatory = $true)]
    [string]$Version,

    [Parameter(Mandatory = $true)]
    [string]$OutputPath
)

$lines = Get-Content -LiteralPath (Join-Path $PSScriptRoot '..\CHANGELOG.md')
$heading = "## [$Version]"
$matchingHeading = $lines | Where-Object { $_.StartsWith($heading, [System.StringComparison]::Ordinal) } | Select-Object -First 1
$start = [Array]::IndexOf($lines, $matchingHeading)
if ($start -lt 0) {
    throw "CHANGELOG.md に $heading の節がありません。"
}

$end = $lines.Count
for ($index = $start + 1; $index -lt $lines.Count; $index++) {
    if ($lines[$index] -match '^## \[') {
        $end = $index
        break
    }
}

$body = ($lines[($start + 1)..($end - 1)] -join [Environment]::NewLine).Trim()
if (-not $body) {
    throw "CHANGELOG.md の $heading 節が空です。"
}
$body | Set-Content -LiteralPath $OutputPath -Encoding utf8NoBOM
