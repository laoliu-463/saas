$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$mapPath = Join-Path $repoRoot 'harness\rules\test-impact-map.json'
$backendTestRoot = Join-Path $repoRoot 'backend\src\test\java'

if (-not (Test-Path -LiteralPath $mapPath)) {
    throw "test-impact-map.json not found at $mapPath"
}

$mapJson = Get-Content -Raw -LiteralPath $mapPath
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

Describe 'agent-do / Jenkinsfile / ci.yml / _lib.ps1 / git-push-safe unchanged after PR #1' {
    It 'agent-do.ps1 still has old parameter set' {
        $agentDo = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'harness\scripts\commands\agent-do.ps1')
        ($agentDo -match 'ContentMaintenance\s*=\s*"plan"') | Should Be $true
        ($agentDo -match 'backend.*frontend.*full.*docs.*apifox') | Should Be $true
        ($agentDo -match '\[ValidateSet\("dev",\s*"close"\)\]\s*\[string\]\$Phase') | Should Be $false
    }

    It 'Jenkinsfile still has 17 stages and no RUN_BACKEND_TEST' {
        $jf = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'Jenkinsfile')
        $stageCount = ([regex]::Matches($jf, "stage\('[^']+'\)")).Count
        ($stageCount) | Should Be 17
        ($jf -match 'RUN_BACKEND_TEST') | Should Be $false
    }

    It 'ci.yml has the three required jobs and no github-actions-read-token' {
        $ci = Get-Content -Raw -LiteralPath (Join-Path $repoRoot '.github\workflows\ci.yml')
        ($ci -match 'Backend tests') | Should Be $true
        ($ci -match 'Frontend tests and build') | Should Be $true
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
        (Test-Path -LiteralPath (Join-Path $repoRoot 'harness\rules\test-impact-map.json')) | Should Be $true
        (Test-Path -LiteralPath (Join-Path $repoRoot 'harness\rules\governance\risk-routing.md')) | Should Be $true
        (Test-Path -LiteralPath $PSCommandPath) | Should Be $true
    }
}