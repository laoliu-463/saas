$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$library = Join-Path $projectRoot 'harness\scripts\commands\_lib.ps1'
$agentDo = Join-Path $projectRoot 'harness\scripts\commands\agent-do.ps1'

. $library

function Get-AgentDoAst {
    $tokens = $null
    $errors = $null
    $ast = [System.Management.Automation.Language.Parser]::ParseFile(
        $agentDo,
        [ref]$tokens,
        [ref]$errors
    )
    return [pscustomobject]@{ Ast = $ast; Errors = @($errors) }
}

function Write-StableNodeEvidenceFixture {
    param(
        [string]$Directory,
        [string]$Status,
        [string]$RunId = 'run-agent-do-fixture'
    )

    $stableDirectory = Join-Path $Directory 'harness\reports\current'
    $rawDirectory = Join-Path $Directory "runtime\qa\out\$RunId"
    New-Item -ItemType Directory -Path $stableDirectory,$rawDirectory -Force | Out-Null
    $json = Join-Path $stableDirectory 'latest-task10.json'
    $markdown = Join-Path $stableDirectory 'latest-task10.md'
    $rawJson = Join-Path $rawDirectory 'run.json'
    $statusLabel = @{ PASS = '通过'; FAIL = '失败'; BLOCKED = '阻塞'; PARTIAL = '部分完成' }[$Status]
    $report = @{
        schemaVersion = '1.0.0'
        runId = $RunId
        reportKey = 'task10'
        environment = 'test'
        scope = 'backend'
        startedAt = '2026-07-18T00:00:00.000Z'
        startedAtShanghai = '2026-07-18 08:00:00 Asia/Shanghai'
        finishedAt = '2026-07-18T00:00:01.000Z'
        finishedAtShanghai = '2026-07-18 08:00:01 Asia/Shanghai'
        durationMs = 1000
        git = @{
            headSha = ('a' * 40)
            branch = 'codex/task10'
            clean = $true
            changedFiles = @()
            identity = @{ kind = 'COMMIT'; commitSha = ('a' * 40) }
        }
        result = @{
            schemaVersion = '1.0.0'
            status = $Status
            statusLabel = $statusLabel
            summary = "验证结论为 $Status。"
            checks = @(@{
                schemaVersion = '1.0.0'
                checkId = 'verify.fixture'
                title = '验证运行夹具'
                status = $(if ($Status -eq 'PASS') { 'PASS' } elseif ($Status -eq 'FAIL') { 'FAIL' } elseif ($Status -eq 'BLOCKED') { 'BLOCKED' } else { 'WARN' })
                statusLabel = $(if ($Status -eq 'PASS') { '通过' } elseif ($Status -eq 'FAIL') { '失败' } elseif ($Status -eq 'BLOCKED') { '阻塞' } else { '警告' })
                blocking = $true
                summary = '夹具用于验证证据门禁。'
                nextActions = @()
                artifacts = @()
            })
        }
        evidencePaths = @{
            rawJson = "runtime/qa/out/$RunId/run.json"
            stableJson = 'harness/reports/current/latest-task10.json'
            stableMarkdown = 'harness/reports/current/latest-task10.md'
        }
    } | ConvertTo-Json -Depth 10
    Set-Content -LiteralPath $json -Value $report -Encoding UTF8 -NoNewline
    Set-Content -LiteralPath $rawJson -Value $report -Encoding UTF8 -NoNewline
    Set-Content -LiteralPath $markdown -Encoding UTF8 -Value @(
        "运行 ID：$RunId"
        "运行结论：$Status"
        "runtime/qa/out/$RunId/run.json"
        'harness/reports/current/latest-task10.json'
        'harness/reports/current/latest-task10.md'
    )
    return [pscustomobject]@{
        Root = $Directory
        RunId = $RunId
        Json = $json
        RawJson = $rawJson
        Markdown = $markdown
        RawDigest = "sha256:$((Get-FileHash -LiteralPath $rawJson -Algorithm SHA256).Hash.ToLowerInvariant())"
        JsonDigest = "sha256:$((Get-FileHash -LiteralPath $json -Algorithm SHA256).Hash.ToLowerInvariant())"
        MarkdownDigest = "sha256:$((Get-FileHash -LiteralPath $markdown -Algorithm SHA256).Hash.ToLowerInvariant())"
    }
}

Describe 'agent-do delegates code scopes to Node exactly once' {
    It 'preserves the six compatibility parameters' {
        $parsed = Get-AgentDoAst
        $parsed.Errors.Count | Should Be 0
        $names = @($parsed.Ast.ParamBlock.Parameters.Name.VariablePath.UserPath)

        foreach ($name in @(
            'TargetEnv',
            'Scope',
            'ReportKey',
            'BusinessCommand',
            'SkipBusinessValidation',
            'DryRun'
        )) {
            ($names -contains $name) | Should Be $true
        }
        $targetEnv = @($parsed.Ast.ParamBlock.Parameters | Where-Object {
            $_.Name.VariablePath.UserPath -eq 'TargetEnv'
        })[0]
        ($targetEnv.Attributes.Extent.Text -join ' ') | Should Match 'Alias\("Env"\)'
    }

    It 'maps backend frontend and full to one Node verify argument list' {
        foreach ($scope in @('backend', 'frontend', 'full')) {
            $arguments = @(New-HarnessNodeVerifyArguments `
                -Env test `
                -Scope $scope `
                -ReportKey task10 `
                -BusinessCommand 'npm run e2e:v1-p0' `
                -SkipBusinessValidation `
                -DryRun)

            ($arguments -join ' ') | Should Match '^run harness:verify -- '
            @($arguments | Where-Object { $_ -eq '--scope' }).Count | Should Be 1
            $arguments[$arguments.IndexOf('--scope') + 1] | Should Be $scope
            @($arguments | Where-Object { $_ -eq '--env' }).Count | Should Be 1
            @($arguments | Where-Object { $_ -eq '--report-key' }).Count | Should Be 1
            @($arguments | Where-Object { $_ -eq '--skip-business-validation' }).Count | Should Be 1
            @($arguments | Where-Object { $_ -eq '--dry-run' }).Count | Should Be 1
        }
    }

    It 'contains one npm invocation site and routes docs apifox to the legacy mode' {
        $parsed = Get-AgentDoAst
        $commands = @($parsed.Ast.FindAll({
            param($node)
            $node -is [System.Management.Automation.Language.CommandAst]
        }, $true))
        @($commands | Where-Object { $_.GetCommandName() -eq 'npm' }).Count | Should Be 1

        (Get-HarnessAgentDoExecutionMode -Scope backend) | Should Be 'NODE'
        (Get-HarnessAgentDoExecutionMode -Scope frontend) | Should Be 'NODE'
        (Get-HarnessAgentDoExecutionMode -Scope full) | Should Be 'NODE'
        (Get-HarnessAgentDoExecutionMode -Scope docs) | Should Be 'LEGACY'
        (Get-HarnessAgentDoExecutionMode -Scope apifox) | Should Be 'LEGACY'
    }

    It 'removes old code-scope build restart health business and remote deploy calls' {
        $content = Get-Content -Raw -LiteralPath $agentDo

        $content | Should Not Match 'restart-compose\.ps1'
        $content | Should Not Match 'verify-local\.ps1'
        $content | Should Not Match 'deploy-remote\.ps1'
        $content | Should Not Match 'mvn\s+-f\s+backend/pom\.xml'
        $content | Should Not Match 'npm\s+--prefix\s+frontend'
        $content | Should Not Match 'e2e:real-pre:p0:preflight|e2e:v1-p0'
    }

    It 'binds a single machine receipt to the unpredictable invocation id' {
        $invocation = ('c' * 32)
        $payload = @{
            schemaVersion = '1.0.0'
            invocationId = $invocation
            runId = 'run-receipt-001'
            reportKey = 'task10'
            environment = 'test'
            scope = 'backend'
            status = 'PARTIAL'
            evidencePaths = @{
                rawJson = 'runtime/qa/out/run-receipt-001/run.json'
                stableJson = 'harness/reports/current/latest-task10.json'
                stableMarkdown = 'harness/reports/current/latest-task10.md'
            }
            evidenceDigests = @{
                rawJson = ('sha256:' + ('d' * 64))
                stableJson = ('sha256:' + ('d' * 64))
                stableMarkdown = ('sha256:' + ('d' * 64))
            }
        } | ConvertTo-Json -Depth 5 -Compress
        $encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($payload)).TrimEnd('=').Replace('+', '-').Replace('/', '_')
        $output = @('npm noise', "HARNESS_VERIFY_RECEIPT_V1:$encoded")

        $receipt = Get-HarnessNodeVerifyReceipt -Output $output `
            -ExpectedInvocationId $invocation -ExpectedEnv test -ExpectedScope backend `
            -ExpectedReportKey task10
        $receipt.runId | Should Be 'run-receipt-001'
        $thrown = $false
        try {
            Get-HarnessNodeVerifyReceipt -Output $output `
                -ExpectedInvocationId ('d' * 32) -ExpectedEnv test -ExpectedScope backend `
                -ExpectedReportKey task10 | Out-Null
        }
        catch {
            $thrown = $true
        }
        $thrown | Should Be $true
    }

    It 'captures npm stderr notices and decides from the native exit code' {
        $content = Get-Content -Raw -LiteralPath $agentDo

        $content | Should Match '\$ErrorActionPreference\s*=\s*''Continue''[\s\S]*\$nodeOutput\s*=\s*@\(npm @nodeArguments 2>&1\)[\s\S]*\$nodeExitCode\s*=\s*\$LASTEXITCODE'
        $content | Should Match '\$ErrorActionPreference\s*=\s*\$previousNodeErrorActionPreference'
    }
}

Describe 'agent-do consumes Node exits without upgrading status' {
    It 'blocks Git for exit 1 and 3 before reading stable evidence' {
        foreach ($exitCode in @(1, 3)) {
            $missingJson = Join-Path $TestDrive "missing-$exitCode.json"
            $missingMarkdown = Join-Path $TestDrive "missing-$exitCode.md"

            $decision = Resolve-HarnessNodeVerifyDecision `
                -ExitCode $exitCode `
                -StableJsonPath $missingJson `
                -StableMarkdownPath $missingMarkdown `
                -ExpectedEnv test `
                -ExpectedScope backend `
                -ExpectedReportKey task10 `
                -RepoRoot $TestDrive

            $decision.AllowGit | Should Be $false
            $decision.Conclusion | Should Not Be 'PASS'
        }
    }

    It 'allows exit 2 candidate closeout only with consistent BLOCKED or PARTIAL evidence' {
        foreach ($status in @('BLOCKED', 'PARTIAL')) {
            $paths = Write-StableNodeEvidenceFixture -Directory (Join-Path $TestDrive $status) -Status $status

            $decision = Resolve-HarnessNodeVerifyDecision `
                -ExitCode 2 `
                -StableJsonPath $paths.Json `
                -StableMarkdownPath $paths.Markdown `
                -ExpectedEnv test `
                -ExpectedScope backend `
                -ExpectedReportKey task10 `
                -RepoRoot $paths.Root `
                -ExpectedRunId $paths.RunId `
                -ExpectedReceiptStatus $status `
                -ExpectedRawJsonDigest $paths.RawDigest `
                -ExpectedStableJsonDigest $paths.JsonDigest `
                -ExpectedStableMarkdownDigest $paths.MarkdownDigest

            $decision.AllowGit | Should Be $true
            $decision.Conclusion | Should Be $status
            $decision.Conclusion | Should Not Be 'PASS'
        }
    }

    It 'blocks Git for exit 2 when either stable evidence file is missing' {
        $paths = Write-StableNodeEvidenceFixture -Directory (Join-Path $TestDrive 'missing-evidence') -Status 'BLOCKED'
        Remove-Item -LiteralPath $paths.Markdown

        $decision = Resolve-HarnessNodeVerifyDecision `
            -ExitCode 2 `
            -StableJsonPath $paths.Json `
            -StableMarkdownPath $paths.Markdown `
            -ExpectedEnv test `
            -ExpectedScope backend `
            -ExpectedReportKey task10 `
            -RepoRoot $paths.Root `
            -ExpectedRunId $paths.RunId `
            -ExpectedReceiptStatus BLOCKED `
            -ExpectedRawJsonDigest $paths.RawDigest `
            -ExpectedStableJsonDigest $paths.JsonDigest `
            -ExpectedStableMarkdownDigest $paths.MarkdownDigest

        $decision.AllowGit | Should Be $false
        $decision.Conclusion | Should Not Be 'PASS'
    }

    It 'requires PASS evidence for exit 0 and rejects unknown exit codes' {
        $paths = Write-StableNodeEvidenceFixture -Directory (Join-Path $TestDrive 'pass') -Status 'PASS'
        (Resolve-HarnessNodeVerifyDecision `
            -ExitCode 0 `
            -StableJsonPath $paths.Json `
            -StableMarkdownPath $paths.Markdown `
            -ExpectedEnv test `
            -ExpectedScope backend `
            -ExpectedReportKey task10 `
            -RepoRoot $paths.Root `
            -ExpectedRunId $paths.RunId `
            -ExpectedReceiptStatus PASS `
            -ExpectedRawJsonDigest $paths.RawDigest `
            -ExpectedStableJsonDigest $paths.JsonDigest `
            -ExpectedStableMarkdownDigest $paths.MarkdownDigest).AllowGit | Should Be $true

        (Resolve-HarnessNodeVerifyDecision `
            -ExitCode 99 `
            -StableJsonPath $paths.Json `
            -StableMarkdownPath $paths.Markdown `
            -ExpectedEnv test `
            -ExpectedScope backend `
            -ExpectedReportKey task10 `
            -RepoRoot $paths.Root).AllowGit | Should Be $false
    }

    It 'rejects a fresh-looking but incomplete forged report' {
        $root = Join-Path $TestDrive 'forged'
        $stable = Join-Path $root 'harness\reports\current'
        New-Item -ItemType Directory -Path $stable -Force | Out-Null
        $json = Join-Path $stable 'latest-task10.json'
        $markdown = Join-Path $stable 'latest-task10.md'
        Set-Content -LiteralPath $json -Encoding UTF8 -Value '{"runId":"run-forged","reportKey":"task10","environment":"test","scope":"backend","result":{"status":"PARTIAL"}}'
        Set-Content -LiteralPath $markdown -Encoding UTF8 -Value 'run-forged PARTIAL'

        $decision = Resolve-HarnessNodeVerifyDecision `
            -ExitCode 2 -StableJsonPath $json -StableMarkdownPath $markdown `
            -ExpectedEnv test -ExpectedScope backend -ExpectedReportKey task10 `
            -RepoRoot $root -ExpectedRunId run-forged -ExpectedReceiptStatus PARTIAL

        $decision.AllowGit | Should Be $false
    }

    It 'rejects evidence replaced after the Node receipt was captured' {
        $paths = Write-StableNodeEvidenceFixture `
            -Directory (Join-Path $TestDrive 'replaced-after-receipt') -Status 'PARTIAL'
        Add-Content -LiteralPath $paths.Markdown -Encoding UTF8 -Value 'concurrent replacement'

        $decision = Resolve-HarnessNodeVerifyDecision `
            -ExitCode 2 -StableJsonPath $paths.Json -StableMarkdownPath $paths.Markdown `
            -ExpectedEnv test -ExpectedScope backend -ExpectedReportKey task10 `
            -RepoRoot $paths.Root -ExpectedRunId $paths.RunId -ExpectedReceiptStatus PARTIAL `
            -ExpectedRawJsonDigest $paths.RawDigest -ExpectedStableJsonDigest $paths.JsonDigest `
            -ExpectedStableMarkdownDigest $paths.MarkdownDigest

        $decision.AllowGit | Should Be $false
    }
}

Describe 'agent-do rejects direct deployment before external actions' {
    It 'fails in Chinese before Node Git or deploy even in dry-run' {
        $fakeBin = Join-Path $TestDrive 'fake-bin'
        $marker = Join-Path $TestDrive 'external-actions.log'
        New-Item -ItemType Directory -Path $fakeBin -Force | Out-Null
        foreach ($name in @('npm.cmd', 'git.cmd')) {
            Set-Content -LiteralPath (Join-Path $fakeBin $name) -Encoding ASCII -Value @(
                '@echo off',
                'echo external-action>>"%HARNESS_ACTION_MARKER%"',
                'exit /b 0'
            )
        }
        $previousPath = $env:PATH
        $previousMarker = $env:HARNESS_ACTION_MARKER
        $previousPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try {
            $env:PATH = "$fakeBin;$previousPath"
            $env:HARNESS_ACTION_MARKER = $marker
            $output = & powershell -NoProfile -ExecutionPolicy Bypass -File $agentDo `
                -Env test -Scope full -ReportKey task10 -DeployRemote true -DryRun 2>&1
            $exitCode = $LASTEXITCODE
        }
        finally {
            $ErrorActionPreference = $previousPreference
            $env:PATH = $previousPath
            $env:HARNESS_ACTION_MARKER = $previousMarker
        }

        $exitCode | Should Be 1
        ($output -join "`n") | Should Match '普通 Codex 任务禁止直接部署'
        ($output -join "`n") | Should Match 'release/real-pre.*Jenkins'
        $marker | Should Not Exist
        (Get-Content -Raw -LiteralPath $agentDo) | Should Not Match 'deploy-remote\.ps1'
    }
}

Describe 'agent-do serializes the shared local runtime across worktrees' {
    It 'blocks a second lease and releases the queue with the file handle' {
        $lockPath = Join-Path $TestDrive 'runtime-queue\real-pre.lock'
        New-Item -ItemType Directory -Path (Split-Path -Parent $lockPath) -Force | Out-Null
        $held = [System.IO.File]::Open(
            $lockPath,
            [System.IO.FileMode]::OpenOrCreate,
            [System.IO.FileAccess]::ReadWrite,
            [System.IO.FileShare]::None
        )
        try {
            { Enter-HarnessRuntimeQueue -Environment real-pre -LockPath $lockPath -TimeoutSeconds 0 -PollMilliseconds 1 } |
                Should Throw '等待本机 real-pre 运行队列超时；共享 Docker、健康检查和业务验证未启动。'
        }
        finally {
            $held.Dispose()
        }

        $lease = Enter-HarnessRuntimeQueue `
            -Environment real-pre -LockPath $lockPath -TimeoutSeconds 1 -PollMilliseconds 1
        try {
            $lease | Should Not BeNullOrEmpty
        }
        finally {
            Exit-HarnessRuntimeQueue -Lease $lease
        }
    }

    It 'holds the queue around Node verify and always releases it' {
        $content = Get-Content -Raw -LiteralPath $agentDo

        $content | Should Match 'Enter-HarnessRuntimeQueue'
        $content | Should Match 'finally\s*\{[\s\S]*Exit-HarnessRuntimeQueue'
    }
}
