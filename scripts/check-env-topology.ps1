param(
    [switch]$AsJson
)

$ErrorActionPreference = "Stop"

function Get-RepoRoot {
    $scriptPath = $PSCommandPath
    if (-not $scriptPath) {
        $scriptPath = $MyInvocation.PSCommandPath
    }
    if (-not $scriptPath) {
        throw "Unable to resolve script path."
    }
    $scriptDir = Split-Path -Parent $scriptPath
    return (Resolve-Path (Join-Path $scriptDir "..")).Path
}

function Test-HttpOk {
    param([string]$Url)

    try {
        $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
        return [pscustomobject]@{
            ok = ($response.StatusCode -ge 200 -and $response.StatusCode -lt 400)
            status = $response.StatusCode
            error = $null
        }
    }
    catch {
        return [pscustomobject]@{
            ok = $false
            status = $null
            error = $_.Exception.Message
        }
    }
}

function Get-ListeningEntries {
    param([int[]]$Ports)

    return Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
        Where-Object { $_.LocalPort -in $Ports } |
        Sort-Object LocalPort, OwningProcess
}

function Get-ProcessCommandLineMap {
    param([int[]]$ProcessIds)

    $map = @{}
    if (-not $ProcessIds -or $ProcessIds.Count -eq 0) {
        return $map
    }

    $filter = ($ProcessIds | ForEach-Object { "ProcessId=$_" }) -join " or "
    $processes = Get-CimInstance Win32_Process -Filter $filter -ErrorAction SilentlyContinue
    foreach ($proc in $processes) {
        $map[[int]$proc.ProcessId] = [pscustomobject]@{
            name = $proc.Name
            commandLine = $proc.CommandLine
        }
    }
    return $map
}

$repoRoot = Get-RepoRoot
$ports = 3000, 3001, 8080, 8081, 5432, 5433, 6379, 6380
$listeners = Get-ListeningEntries -Ports $ports
$processMap = Get-ProcessCommandLineMap -ProcessIds ($listeners.OwningProcess | Select-Object -Unique)

$portSummary = foreach ($port in $ports) {
    $entries = $listeners | Where-Object { $_.LocalPort -eq $port }
    [pscustomobject]@{
        port = $port
        listeners = @(
            foreach ($entry in $entries) {
                $proc = $processMap[[int]$entry.OwningProcess]
                [pscustomobject]@{
                    localAddress = $entry.LocalAddress
                    processId = $entry.OwningProcess
                    processName = $proc.name
                    commandLine = $proc.commandLine
                }
            }
        )
    }
}

$httpChecks = [ordered]@{
    frontend3000 = Test-HttpOk 'http://localhost:3000'
    frontend3001 = Test-HttpOk 'http://localhost:3001'
    backend8080 = Test-HttpOk 'http://localhost:8080/api/system/health'
    backend8081 = Test-HttpOk 'http://localhost:8081/api/system/health'
}

$violations = New-Object System.Collections.Generic.List[string]

$node3001 = @(
    $portSummary | Where-Object { $_.port -eq 3001 } | Select-Object -ExpandProperty listeners |
        Where-Object { $_.processName -eq 'node.exe' }
)
if ($node3001.Count -gt 0) {
    $violations.Add("Port 3001 has a local node/vite listener; real-pre frontend must be the only source on 3001.")
}

$node3000 = @(
    $portSummary | Where-Object { $_.port -eq 3000 } | Select-Object -ExpandProperty listeners |
        Where-Object { $_.processName -eq 'node.exe' }
)
if ($node3000.Count -eq 0) {
    $violations.Add("No local frontend process detected on port 3000.")
}

$redis6379 = @(
    $portSummary | Where-Object { $_.port -eq 6379 } | Select-Object -ExpandProperty listeners |
        Where-Object { $_.processName -like 'redis-server*' }
)
if ($redis6379.Count -gt 0) {
    $violations.Add("A local redis-server is listening on 6379; stop it unless it is explicitly required.")
}

if (-not $httpChecks.frontend3000.ok) {
    $violations.Add("Frontend on 3000 is not reachable.")
}
if (-not $httpChecks.frontend3001.ok) {
    $violations.Add("Frontend on 3001 is not reachable.")
}
if (-not $httpChecks.backend8080.ok) {
    $violations.Add("Health check on 8080 is not reachable.")
}
if (-not $httpChecks.backend8081.ok) {
    $violations.Add("Health check on 8081 is not reachable.")
}

$result = [pscustomobject]@{
    generatedAt = (Get-Date).ToString('s')
    repoRoot = $repoRoot
    summary = [pscustomobject]@{
        standardTopology = '3000/8080 + 3001/8081'
        status = if ($violations.Count -eq 0) { 'OK' } else { 'WARN' }
        violationCount = $violations.Count
    }
    httpChecks = $httpChecks
    ports = $portSummary
    violations = @($violations)
}

if ($AsJson) {
    $result | ConvertTo-Json -Depth 6
    return
}

Write-Host "Current topology: 3000/8080 + 3001/8081" -ForegroundColor Cyan
Write-Host "Status: $($result.summary.status)" -ForegroundColor $(if ($violations.Count -eq 0) { 'Green' } else { 'Yellow' })
Write-Host ""

foreach ($check in $httpChecks.GetEnumerator()) {
    $label = $check.Key
    $value = $check.Value
    $statusText = if ($value.ok) { "OK ($($value.status))" } else { "ERROR ($($value.error))" }
    Write-Host ("{0,-12} {1}" -f $label, $statusText)
}

Write-Host ""
Write-Host "Port listeners:" -ForegroundColor Cyan
foreach ($portItem in $portSummary) {
    Write-Host "[$($portItem.port)]"
    if (-not $portItem.listeners -or $portItem.listeners.Count -eq 0) {
        Write-Host "  (none)"
        continue
    }
    foreach ($listener in $portItem.listeners) {
        Write-Host "  $($listener.localAddress) pid=$($listener.processId) $($listener.processName)"
        if ($listener.commandLine) {
            Write-Host "    $($listener.commandLine)"
        }
    }
}

Write-Host ""
if ($violations.Count -eq 0) {
    Write-Host "No topology violations detected." -ForegroundColor Green
} else {
    Write-Host "Violations:" -ForegroundColor Yellow
    foreach ($violation in $violations) {
        Write-Host " - $violation"
    }
}
