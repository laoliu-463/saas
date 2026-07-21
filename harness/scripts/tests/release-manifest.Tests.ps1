$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$manifestScript = Join-Path $repoRoot 'scripts\verify-real-pre-release.py'
$exampleManifest = Join-Path $repoRoot 'release\real-pre.example.json'
$jenkinsfile = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'Jenkinsfile')
$ci = Get-Content -Raw -LiteralPath (Join-Path $repoRoot '.github\workflows\ci.yml')

Describe 'immutable release manifest contract' {
    It 'accepts the checked-in shape example' {
        $python = if (Get-Command python -ErrorAction SilentlyContinue) { 'python' } elseif (Get-Command python3 -ErrorAction SilentlyContinue) { 'python3' } else { throw 'Python executable not found.' }
        $output = & $python $manifestScript $exampleManifest
        $LASTEXITCODE | Should Be 0
        ($output -join "`n") | Should Match 'PASS: release manifest validated'
    }

    It 'requires Jenkins to pull immutable references without building' {
        $jenkinsfile | Should Match 'docker pull "\$BACKEND_IMAGE"'
        $jenkinsfile | Should Match 'docker pull "\$FRONTEND_IMAGE"'
        $jenkinsfile | Should Not Match '(?m)^\s*docker compose[^\r\n]+\sbuild'
        $jenkinsfile | Should Match "credentialsId: 'saas-container-registry'"
    }

    It 'builds immutable images only on main after the CI Gate' {
        $ci | Should Match 'immutable_images:'
        $ci | Should Match 'needs: ci_gate'
        $ci | Should Match 'packages:\s*write'
        $ci | Should Match 'docker push "\$backend_ref"'
        $ci | Should Match 'actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02'
    }
}
