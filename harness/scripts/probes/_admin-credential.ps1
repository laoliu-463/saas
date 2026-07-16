$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot '..\commands\_lib.ps1')

function Test-HarnessProbeLoopbackUrl {
    param([Parameter(Mandatory = $true)][string]$BaseUrl)

    $uri = $null
    if (-not [uri]::TryCreate($BaseUrl, [uriKind]::Absolute, [ref]$uri)) {
        throw "BaseUrl must be an absolute URL: $BaseUrl"
    }

    return @('127.0.0.1', '::1', 'localhost').Contains($uri.Host.ToLowerInvariant())
}

function Resolve-HarnessProbeAdminCredential {
    param(
        [Parameter(Mandatory = $true)][string]$BaseUrl,
        [string]$AdminPassword = '',
        [string]$LocalEnvFile = ''
    )

    $explicit = $AdminPassword.Trim()
    if ($explicit) {
        return $explicit
    }

    if (Test-HarnessProbeLoopbackUrl -BaseUrl $BaseUrl) {
        $localOverride = [Environment]::GetEnvironmentVariable('QA_LOCAL_ADMIN_PASSWORD', 'Process')
        if (-not [string]::IsNullOrWhiteSpace($localOverride)) {
            return $localOverride.Trim()
        }

        if ([string]::IsNullOrWhiteSpace($LocalEnvFile)) {
            throw 'Local probe credential requires LocalEnvFile or QA_LOCAL_ADMIN_PASSWORD.'
        }
        $localValue = (Read-HarnessEnvFile -Path $LocalEnvFile)['ADMIN_PASSWORD']
        if (-not [string]::IsNullOrWhiteSpace($localValue)) {
            return $localValue.Trim()
        }
        throw "Local probe credential is missing from $LocalEnvFile."
    }

    $remoteOverride = [Environment]::GetEnvironmentVariable('QA_REMOTE_ADMIN_PASSWORD', 'Process')
    if (-not [string]::IsNullOrWhiteSpace($remoteOverride)) {
        return $remoteOverride.Trim()
    }
    throw 'Remote probe credential is required. Set QA_REMOTE_ADMIN_PASSWORD for the current process.'
}
