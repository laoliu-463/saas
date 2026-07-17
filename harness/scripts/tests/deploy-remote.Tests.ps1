$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$deployScript = Join-Path $repoRoot 'harness\scripts\commands\deploy-remote.ps1'
$content = Get-Content -Raw -LiteralPath $deployScript
$tokens = $null
$parseErrors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile(
    $deployScript,
    [ref]$tokens,
    [ref]$parseErrors
)
$parameterNames = @($ast.ParamBlock.Parameters.Name.VariablePath.UserPath)

Describe 'remote deployment safety contract' {
    It 'renders a syntactically valid remote Bash script' {
        $assignment = $ast.Find({
            param($node)
            $node -is [System.Management.Automation.Language.AssignmentStatementAst] -and
                $node.Left.Extent.Text -eq '$remoteScript'
        }, $true)
        $assignment | Should Not BeNullOrEmpty

        $renderBlock = [scriptblock]::Create(@"
`$RemoteDir = '/tmp/saas'
`$ExpectedCommit = '0000000000000000000000000000000000000000'
`$RemoteEnvFile = '/tmp/saas.env'
`$remoteScript = $($assignment.Right.Extent.Text)
`$remoteScript
"@)
        $rendered = & $renderBlock
        $bash = Get-Command bash -ErrorAction SilentlyContinue
        $bash | Should Not BeNullOrEmpty

        $rendered | & $bash.Source -n
        $LASTEXITCODE | Should Be 0
    }

    It 'binds a deployment to an expected commit before Compose changes' {
        $parseErrors.Count | Should Be 0
        ($parameterNames -contains 'ExpectedCommit') | Should Be $true
        $content | Should Match 'git rev-parse HEAD'
        $content | Should Match 'Remote commit mismatch'

        $commitGuard = $content.IndexOf('Remote commit mismatch')
        $firstComposeChange = $content.IndexOf('compose up -d')
        $commitGuard | Should BeGreaterThan -1
        $firstComposeChange | Should BeGreaterThan $commitGuard
    }

    It 'repairs and verifies the repository real-pre env symlink before Compose' {
        $content | Should Match 'ln -sfn'
        $content | Should Match 'readlink -f'
        $content | Should Match 'Remote env link mismatch'

        $linkGuard = $content.IndexOf('Remote env link mismatch')
        $composeFunction = $content.IndexOf('compose()')
        $linkGuard | Should BeGreaterThan -1
        $composeFunction | Should BeGreaterThan $linkGuard
    }

    It 'uses the canonical project and converges both stateful services' {
        $content | Should Match "--project-name 'saas-active'"
        $content | Should Match 'compose up -d postgres-real-pre redis-real-pre'
        $content | Should Match 'com\.docker\.compose\.project\.working_dir'
        $content | Should Match 'com\.docker\.compose\.project\.environment_file'
        $content | Should Match 'Stateful service provenance guard passed'
    }
}
