param(
    [string]$Manifest = 'release/real-pre.json',
    [string]$RepoRoot = '',
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
    $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
}
$RepoRoot = (Resolve-Path -LiteralPath $RepoRoot).Path
$manifestPath = Join-Path $RepoRoot $Manifest
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
    throw "Release manifest not found: $Manifest"
}

$python = if (Get-Command python -ErrorAction SilentlyContinue) { 'python' } elseif (Get-Command python3 -ErrorAction SilentlyContinue) { 'python3' } else { throw 'Python executable not found.' }
& $python (Join-Path $RepoRoot 'scripts/verify-real-pre-release.py') $manifestPath
if ($LASTEXITCODE -ne 0) { throw 'Release manifest validation failed.' }

Push-Location $RepoRoot
try {
    $sourceSha = (& $python -c "import json,sys; print(json.load(open(sys.argv[1], encoding='utf-8'))['sourceMainSha'])" $manifestPath).Trim()
    $headSha = (& git rev-parse HEAD).Trim()
    & git merge-base --is-ancestor $sourceSha HEAD
    if ($LASTEXITCODE -ne 0) { throw 'Manifest sourceMainSha is not an ancestor of the current release worktree.' }
    if (-not $DryRun) {
        & git diff --check $sourceSha $headSha -- . ':(exclude)release/real-pre.json'
        if ($LASTEXITCODE -ne 0) { throw 'Release worktree contains whitespace errors outside the manifest.' }
        & git diff --exit-code $sourceSha $headSha -- . ':(exclude)release/real-pre.json'
        if ($LASTEXITCODE -ne 0) { throw 'Release worktree contains non-manifest drift from sourceMainSha.' }
    }
    Write-Output "PASS: release sourceMainSha=$sourceSha; no non-manifest drift."
}
finally { Pop-Location }
