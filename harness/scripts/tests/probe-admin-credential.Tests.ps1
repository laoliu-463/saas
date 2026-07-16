$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$resolverScript = Join-Path $repoRoot 'harness\scripts\probes\_admin-credential.ps1'

Describe 'probe admin credential contract' {
    It 'provides a dedicated resolver for probe credentials' {
        (Test-Path -LiteralPath $resolverScript) | Should Be $true
    }

    if (Test-Path -LiteralPath $resolverScript) {
        . $resolverScript

        BeforeEach {
            $script:originalLocal = [Environment]::GetEnvironmentVariable('QA_LOCAL_ADMIN_PASSWORD', 'Process')
            $script:originalRemote = [Environment]::GetEnvironmentVariable('QA_REMOTE_ADMIN_PASSWORD', 'Process')
            [Environment]::SetEnvironmentVariable('QA_LOCAL_ADMIN_PASSWORD', $null, 'Process')
            [Environment]::SetEnvironmentVariable('QA_REMOTE_ADMIN_PASSWORD', $null, 'Process')
            $script:envFile = Join-Path ([System.IO.Path]::GetTempPath()) ("saas-probe-admin-{0}.env" -f [guid]::NewGuid())
            $script:localCredential = 'local-test-password'
            Set-Content -LiteralPath $script:envFile -Value (('ADMIN' + '_PASSWORD=') + $script:localCredential) -NoNewline
        }

        AfterEach {
            Remove-Item -LiteralPath $script:envFile -Force -ErrorAction SilentlyContinue
            [Environment]::SetEnvironmentVariable('QA_LOCAL_ADMIN_PASSWORD', $script:originalLocal, 'Process')
            [Environment]::SetEnvironmentVariable('QA_REMOTE_ADMIN_PASSWORD', $script:originalRemote, 'Process')
        }

        It 'reads loopback probe credentials from the local environment file' {
            Resolve-HarnessProbeAdminCredential -BaseUrl 'http://127.0.0.1:8081/api' -LocalEnvFile $script:envFile |
                Should Be $script:localCredential
        }

        It 'does not fall back to a local credential for a remote URL' {
            $failure = $null
            try {
                Resolve-HarnessProbeAdminCredential -BaseUrl 'https://real-pre.example/api' -LocalEnvFile $script:envFile
            } catch {
                $failure = $_
            }

            $failure | Should Not BeNullOrEmpty
            $failure.Exception.Message | Should Match 'QA_REMOTE_ADMIN_PASSWORD'
        }

        It 'reads remote probe credentials only from the dedicated remote environment key' {
            [Environment]::SetEnvironmentVariable('QA_REMOTE_ADMIN_PASSWORD', 'remote-test-password', 'Process')

            Resolve-HarnessProbeAdminCredential -BaseUrl 'https://real-pre.example/api' -LocalEnvFile $script:envFile |
                Should Be 'remote-test-password'
        }

        It 'routes every admin-authenticated probe through the resolver without an inline password' {
            foreach ($probeName in @('product-library-phase2-dryrun.ps1', 'backfill-async.ps1')) {
                $probeContent = Get-Content -Raw -LiteralPath (Join-Path $repoRoot "harness\scripts\probes\$probeName")

                $probeContent | Should Match '_admin-credential\.ps1'
                $probeContent | Should Match 'Resolve-HarnessProbeAdminCredential'
                $probeContent | Should Not Match '\bpassword\s*=\s*["'']'
            }
        }

        It 'keeps the database-comparison dry-run probe on a loopback target' {
            $probeContent = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'harness\scripts\probes\product-library-phase2-dryrun.ps1')

            $probeContent | Should Match 'Test-HarnessProbeLoopbackUrl'
        }
    }
}
