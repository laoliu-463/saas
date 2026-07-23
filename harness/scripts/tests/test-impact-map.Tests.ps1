$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$mapPath = Join-Path $repoRoot 'harness\checks\impact-map.json'
$backendTestRoot = Join-Path $repoRoot 'backend\src\test\java'
$frontendSrcRoot = Join-Path $repoRoot 'frontend\src'

function Convert-PathGlobToRegex {
    param([Parameter(Mandatory = $true)][string]$Pattern)

    $normalized = $Pattern.Replace('\', '/')
    $builder = New-Object System.Text.StringBuilder

    for ($i = 0; $i -lt $normalized.Length; $i++) {
        if (($i + 2) -lt $normalized.Length -and $normalized.Substring($i, 3) -eq '**/') {
            [void]$builder.Append('(?:.*/)?')
            $i += 2
            continue
        }

        if (($i + 1) -lt $normalized.Length -and $normalized.Substring($i, 2) -eq '**') {
            [void]$builder.Append('.*')
            $i++
            continue
        }

        $character = [string]$normalized[$i]
        if ($character -eq '*') {
            [void]$builder.Append('[^/]*')
        } elseif ($character -eq '?') {
            [void]$builder.Append('[^/]')
        } else {
            [void]$builder.Append([regex]::Escape($character))
        }
    }

    return '^' + $builder.ToString() + '$'
}

$frontendFiles = @(Get-ChildItem -LiteralPath $frontendSrcRoot -Recurse -File -ErrorAction SilentlyContinue)
$frontendRelativePaths = @($frontendFiles | ForEach-Object {
    $_.FullName.Substring($repoRoot.Length).TrimStart('\', '/').Replace('\', '/')
})
$frontendTestRelativePaths = @($frontendRelativePaths | Where-Object { $_ -match '\.test\.(ts|tsx)$' })

if (-not (Test-Path -LiteralPath $mapPath)) {
    throw "test-impact-map.json not found at $mapPath"
}

$mapJson = Get-Content -Raw -Encoding UTF8 -LiteralPath $mapPath
$map = $mapJson | ConvertFrom-Json

Describe 'test-impact-map.json schema contract' {

    It 'has schemaVersion 1' {
        ($map.schemaVersion) | Should Be 1
    }

    It 'has non-empty rules array' {
        ($map.rules.Count) | Should BeGreaterThan 0
    }

    It 'has riskRank with R0..R3 numeric values' {
        ($map.riskRank.R0) | Should Be 0
        ($map.riskRank.R1) | Should Be 1
        ($map.riskRank.R2) | Should Be 2
        ($map.riskRank.R3) | Should Be 3
    }

    It 'has mergeSemantics block' {
        ($map.mergeSemantics.risk) | Should Not BeNullOrEmpty
        ($map.mergeSemantics.backendTests) | Should Not BeNullOrEmpty
        ($map.mergeSemantics.frontendTests) | Should Not BeNullOrEmpty
    }

    It 'has surefire.excludeGlobs covering mapper/*MapperTest' {
        $joined = ($map.surefire.excludeGlobs -join ' ')
        ($joined -match 'mapper.*MapperTest') | Should Be $true
    }
}

Describe 'rule IDs are unique' {
    It 'no duplicate rule id' {
        $ids = @($map.rules | ForEach-Object { $_.id })
        ($ids.Count) | Should Be (($ids | Sort-Object -Unique).Count)
    }
}

Describe 'rule Risk values are legal' {
    It 'every rule has risk in {R0,R1,R2,R3}' {
        $valid = @('R0', 'R1', 'R2', 'R3')
        $bad = @()
        foreach ($r in $map.rules) {
            if ($valid -notcontains $r.risk) { $bad += $r.id }
        }
        ($bad.Count) | Should Be 0
        if ($bad.Count -gt 0) { throw "rules with invalid risk: $($bad -join ', ')" }
    }
}

Describe 'rule paths and tests are non-empty where applicable' {
    It 'every rule has at least paths' {
        $bad = @()
        foreach ($r in $map.rules) {
            $hasPaths = ($r.PSObject.Properties.Name -contains 'paths') -and ($r.paths.Count -gt 0)
            if (-not $hasPaths) { $bad += $r.id }
        }
        ($bad.Count) | Should Be 0
        if ($bad.Count -gt 0) { throw "rules missing paths: $($bad -join ', ')" }
    }
}

Describe 'exact test classes exist on disk' {
    It 'every literal test class in backendTests is resolvable' {
        $missing = @()
        foreach ($r in $map.rules) {
            if (-not ($r.PSObject.Properties.Name -contains 'backendTests')) { continue }
            foreach ($t in $r.backendTests) {
                if ($t -match '[\*\?\[]') { continue }
                $matches = @(Get-ChildItem -Path $backendTestRoot -Recurse -Filter "${t}.java" -ErrorAction SilentlyContinue)
                if ($matches.Count -eq 0) { $missing += "$($r.id):$t" }
            }
        }
        ($missing.Count) | Should Be 0
        if ($missing.Count -gt 0) { throw "missing literal test classes: $($missing -join ', ')" }
    }
}

Describe 'glob patterns are effective against current tests' {
    It 'every backendTests glob matches at least one existing test file (outside mapper/)' {
        $empty = @()
        foreach ($r in $map.rules) {
            if (-not ($r.PSObject.Properties.Name -contains 'backendTests')) { continue }
            foreach ($t in $r.backendTests) {
                if ($t -notmatch '[\*\?\[]') { continue }
                $matches = @(Get-ChildItem -Path $backendTestRoot -Recurse -Filter "${t}.java" -ErrorAction SilentlyContinue |
                    Where-Object { $_.FullName -notmatch '[\\/]mapper[\\/]' })
                if ($matches.Count -eq 0) { $empty += "$($r.id):$t" }
            }
        }
        ($empty.Count) | Should Be 0
        if ($empty.Count -gt 0) { throw "ineffective globs (0 hits): $($empty -join ', ')" }
    }
}

Describe 'rule overlap is deterministic' {
    It 'merging order (R2) + database-schema (R3) yields R3 with union of backendTests' {
        $r1 = $map.rules | Where-Object { $_.id -eq 'order' } | Select-Object -First 1
        $r2 = $map.rules | Where-Object { $_.id -eq 'database-schema' } | Select-Object -First 1
        ($r1 -ne $null) | Should Be $true
        ($r2 -ne $null) | Should Be $true

        $riskRank = $map.riskRank
        $riskA = [int]$riskRank."$($r1.risk)"
        $riskB = [int]$riskRank."$($r2.risk)"
        $mergedRisk = if ($riskA -gt $riskB) { $r1.risk } else { $r2.risk }
        ($mergedRisk) | Should Be 'R3'

        $mergedTests = (@($r1.backendTests) + @($r2.backendTests)) | Sort-Object -Unique
        ($mergedTests.Count) | Should BeGreaterThan ($r1.backendTests.Count)
        ($mergedTests.Count) | Should BeGreaterThan ($r2.backendTests.Count)
    }
}

Describe 'selective release flow contracts' {
    It 'agent-do.ps1 exposes application, deployment and CI scopes' {
        $agentDo = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'harness\scripts\commands\agent-do.ps1')
        ($agentDo -match 'ContentMaintenance\s*=\s*"plan"') | Should Be $true
        ($agentDo -match 'backend.*frontend.*full.*docs.*apifox.*deploy.*ci') | Should Be $true
        ($agentDo -match '\[ValidateSet\("dev",\s*"close"\)\]\s*\[string\]\$Phase') | Should Be $false
    }

    It 'Jenkinsfile has the PR-B SHA Gate and RUN_BACKEND_TEST=false by default' {
        # PR-B rewrote this contract: the Jenkinsfile MUST reference the
        # canonical SHA Gate entry script, MUST inject GITHUB_TOKEN via
        # withCredentials, and MUST expose RUN_BACKEND_TEST defaulting to
        # false so the GHA gate is the single source of truth.
        $jf = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'Jenkinsfile')
        ($jf -match 'verify-github-ci-gate\.sh') | Should Be $true
        ($jf -match 'github-actions-read-token') | Should Be $true
        ($jf -match "booleanParam\(name:\s*'RUN_BACKEND_TEST',\s*defaultValue:\s*false") | Should Be $true
        # The Backend Test stage must be guarded by RUN_BACKEND_TEST.
        ($jf -match "expression \{ return params\.RUN_BACKEND_TEST \}") | Should Be $true
        # The release job must consume immutable images and serialize all mutations.
        ($jf -match "stage\('Pull Immutable Images'\)") | Should Be $true
        ($jf -match "lock\(resource:\s*'saas-real-pre-deploy'") | Should Be $true
        ($jf -match 'docker compose[^\r\n]+--no-build') | Should Be $true
        ($jf -match '(?m)^\s*docker compose[^\r\n]+\sbuild') | Should Be $false
        ($jf -match '(?m)^\s*docker build(?:\s|$)') | Should Be $false
        ($jf -match 'evidence-collect\.sh\s+release-manifest') | Should Be $true
    }

    It 'ci.yml has the three required jobs and no github-actions-read-token' {
        $ci = Get-Content -Raw -LiteralPath (Join-Path $repoRoot '.github\workflows\ci.yml')
        ($ci -match 'Backend tests') | Should Be $true
        ($ci -match 'Frontend tests and typecheck') | Should Be $true
        ($ci -match 'Repository governance') | Should Be $true
        ($ci -match 'github-actions-read-token') | Should Be $false
    }

    It '_lib.ps1 unchanged (no PR #2 functions)' {
        $lib = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'harness\scripts\commands\_lib.ps1')
        ($lib -match 'Resolve-HarnessTestImpactMap') | Should Be $false
        ($lib -match 'Get-HarnessRiskLevel') | Should Be $false
        ($lib -match 'Assert-HarnessPushRange') | Should Be $false
    }

    It 'git-push-safe.ps1 unchanged (no Push Range Gate yet)' {
        $gps = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'harness\scripts\commands\git-push-safe.ps1')
        ($gps -match '\$StartHead') | Should Be $false
        ($gps -match '\$RemoteHead') | Should Be $false
        ($gps -match '\$TaskCommitSha') | Should Be $false
        ($gps -match 'PUBLISH_RANGE_DIRTY') | Should Be $false
    }
}

Describe 'PR #1 file additions only' {
    It 'the three new files exist' {
        (Test-Path -LiteralPath (Join-Path $repoRoot 'harness\checks\impact-map.json')) | Should Be $true
        (Test-Path -LiteralPath (Join-Path $repoRoot 'docs\harness-maintenance\legacy-rules\governance\risk-routing.md')) | Should Be $true
        (Test-Path -LiteralPath $PSCommandPath) | Should Be $true
    }
}

Describe 'test-impact-map.json frontend risk coverage' {
    It 'every frontendPaths glob matches at least one existing frontend source file' {
        $missing = @()
        foreach ($r in $map.rules) {
            if (-not ($r.PSObject.Properties.Name -contains 'frontendPaths')) { continue }
            if ($r.frontendPaths.Count -eq 0) { continue }
            foreach ($path in $r.frontendPaths) {
                $regex = Convert-PathGlobToRegex $path
                $matches = @($frontendRelativePaths | Where-Object { $_ -match $regex })
                if ($matches.Count -eq 0) {
                    $missing += "$($r.id):frontendPath:$path"
                }
            }
        }
        ($missing.Count) | Should Be 0
        if ($missing.Count -gt 0) { throw "ineffective frontendPaths globs: $($missing -join ', ')" }
    }

    It 'every frontendTests glob matches at least one existing frontend test file' {
        $empty = @()
        foreach ($r in $map.rules) {
            if (-not ($r.PSObject.Properties.Name -contains 'frontendTests')) { continue }
            foreach ($testPath in $r.frontendTests) {
                $regex = Convert-PathGlobToRegex $testPath
                $matches = @($frontendTestRelativePaths | Where-Object { $_ -match $regex })
                if ($matches.Count -eq 0) {
                    $empty += "$($r.id):frontendTest:$testPath"
                }
            }
        }
        ($empty.Count) | Should Be 0
        if ($empty.Count -gt 0) { throw "ineffective frontendTests globs: $($empty -join ', ')" }
    }

    It 'harness-rules risk level is R3 (matches the doc classification: changes engine behavior)' {
        $hr = $map.rules | Where-Object { $_.id -eq 'harness-rules' } | Select-Object -First 1
        ($hr -ne $null) | Should Be $true
        ($hr.risk) | Should Be 'R3'
    }

    It 'docs-only rule excludes harness/AGENTS/CLAUDE/CONTRIBUTING so they auto-promote to R3' {
        $docs = $map.rules | Where-Object { $_.id -eq 'docs-only' } | Select-Object -First 1
        ($docs -ne $null) | Should Be $true
        ($docs.risk) | Should Be 'R0'
        ($docs.excludes -join ' ') | Should Match 'AGENTS\.md'
        ($docs.excludes -join ' ') | Should Match 'CLAUDE\.md'
        ($docs.excludes -join ' ') | Should Match 'CONTRIBUTING\.md'
        ($docs.excludes -join ' ') | Should Match 'harness/policy'
    }

    It 'merge semantics documents priority as tie-breaker only, tests as union' {
        ($map.mergeSemantics.backendTests -join ' ') | Should Match 'union|并集'
        ($map.mergeSemantics.priority) | Should Match 'always|永远'
        ($map.mergeSemantics.priority) | Should Match 'arbitrat|仲裁'
    }

    It 'failClosed matrix is complete (R0/R1/R2/R3 all present)' {
        ($map.failClosed.R0) | Should Not BeNullOrEmpty
        ($map.failClosed.R1) | Should Not BeNullOrEmpty
        ($map.failClosed.R2) | Should Match 'FAIL'
        ($map.failClosed.R3) | Should Match 'FAIL'
    }
}
