param(
    [Parameter(Mandatory = $true)]
    [string]$Message,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "_lib.ps1")

$repoRoot = Get-HarnessRepoRoot

function Get-ChangedFiles {
    $OutputEncoding = [System.Text.Encoding]::UTF8
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
    $lines = & git -c core.quotepath=false status --porcelain=v1
    if ($LASTEXITCODE -ne 0) {
        throw "git status failed."
    }
    $files = @()
    foreach ($line in $lines) {
        if ($line.Length -lt 4) {
            continue
        }
        $path = $line.Substring(3).Trim().Trim('"')
        if ($path -match "\s+->\s+") {
            $path = ($path -split "\s+->\s+")[-1].Trim().Trim('"')
        }
        if ($path) {
            $files += $path
        }
    }
    return $files | Sort-Object -Unique
}

function Assert-NoSensitiveFile {
    param([string[]]$Files)

    foreach ($file in $Files) {
        $name = Split-Path -Leaf $file
        $lower = $file.ToLowerInvariant()
        $isEnv = ($name -like ".env*" -and -not $name.EndsWith(".example"))
        $blocked = $isEnv `
            -or $lower.EndsWith(".pem") `
            -or $lower.EndsWith(".key") `
            -or $lower.EndsWith(".p12") `
            -or $lower.EndsWith(".jks") `
            -or $name.ToLowerInvariant().StartsWith("credentials") `
            -or $name.ToLowerInvariant().StartsWith("secrets")
        if ($blocked) {
            throw "Sensitive file must not be committed: $file"
        }
    }
}

function Assert-NoPlainSecrets {
    param([string[]]$Files)

    $pattern = @'
(password|secret|token|client_secret|jwt_secret)\s*[:=]\s*['"]?[A-Za-z0-9_\-/.+=]{12,}
'@.Trim()
    foreach ($file in $Files) {
        $path = Join-Path $repoRoot $file
        $exists = $false
        try { $exists = Test-Path -LiteralPath $path } catch { continue }
        if (-not $exists) {
            continue
        }
        $name = Split-Path -Leaf $file
        if ($name -like "*.md" -or $name -like "*.prompt.md" -or $name -like "*.skill.md") {
            continue
        }
        $hits = Select-String -LiteralPath $path -Pattern $pattern -ErrorAction SilentlyContinue
        foreach ($hit in $hits) {
            $line = $hit.Line
            $hasSkipKeyword = $false
            $skipKeywords = @('REDACTED', 'placeholder', 'example', 'change-me')
            foreach ($kw in $skipKeywords) {
                if ($line.Contains($kw)) {
                    $hasSkipKeyword = $true
                    break
                }
            }
            $hasInterpolation = $line.Contains('$' + '{')
            if ($hasSkipKeyword -eq $true -or $hasInterpolation -eq $true) {
                continue
            }
            throw "Potential plaintext secret in $file line $($hit.LineNumber)."
        }
    }
}

Write-HarnessStage "Git push safe"
Assert-HarnessRepoRoot -RepoRoot $repoRoot

if ([string]::IsNullOrWhiteSpace($Message) -or $Message.Trim().Length -lt 8) {
    throw "Commit message is required and must be readable."
}

Push-Location $repoRoot
try {
    Write-Host "Current git status:"
    git status --short
    if ($LASTEXITCODE -ne 0) {
        throw "git status failed."
    }

    $changedFiles = @(Get-ChangedFiles)
    if ($changedFiles.Count -eq 0) {
        Write-Host "No changes to commit." -ForegroundColor Yellow
        return
    }

    Assert-NoSensitiveFile -Files $changedFiles
    Assert-NoPlainSecrets -Files $changedFiles

    if ($DryRun) {
        Write-Host "DRY-RUN changed files:"
        $changedFiles | ForEach-Object { Write-Host "- $_" }
        Write-Host "DRY-RUN would run: git add -A; git commit -m `"$Message`"; git push"
        return
    }

    foreach ($file in $changedFiles) {
        if (Test-Path -LiteralPath $file) {
            git add -f -- $file
            if ($LASTEXITCODE -ne 0) {
                throw "git staging failed for $file."
            }
        } else {
            $prevErrorActionPreference = $ErrorActionPreference
            $ErrorActionPreference = "Continue"
            try {
                & git rm --cached -f -- $file 2>$null
            } catch {}
            $ErrorActionPreference = $prevErrorActionPreference
        }
    }

    $stagedFiles = & git -c core.quotepath=false diff --cached --name-only
    if ($LASTEXITCODE -ne 0) {
        throw "git diff --cached failed."
    }
    Assert-NoSensitiveFile -Files $stagedFiles
    Assert-NoPlainSecrets -Files $stagedFiles

    git diff --cached --check
    if ($LASTEXITCODE -ne 0) {
        throw "git diff --cached --check failed."
    }

    git commit -m $Message
    if ($LASTEXITCODE -ne 0) {
        throw "git commit failed."
    }

    $commit = (& git rev-parse --short HEAD).Trim()
    Write-Host "Commit: $commit" -ForegroundColor Green

    $branch = (& git branch --show-current).Trim()
    if ([string]::IsNullOrWhiteSpace($branch)) {
        throw "Cannot determine current branch for push."
    }

    git push gitee $branch
    if ($LASTEXITCODE -ne 0) {
        throw "git push gitee failed. Check remote and credentials."
    }

    git push origin $branch
    if ($LASTEXITCODE -ne 0) {
        throw "git push origin failed. Check remote and credentials."
    }

    Write-Host "Git push completed." -ForegroundColor Green
}
finally {
    Pop-Location
}
