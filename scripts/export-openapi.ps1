param(
    [string]$BaseUrl = $env:OPENAPI_BASE_URL,
    [string]$Group = $env:OPENAPI_GROUP,
    [string]$Output = $env:OPENAPI_OUTPUT,
    [string]$BearerToken = $env:OPENAPI_BEARER_TOKEN
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
    $BaseUrl = "http://127.0.0.1:8080/api"
}
if ([string]::IsNullOrWhiteSpace($Group)) {
    $Group = "apifox"
}
if ([string]::IsNullOrWhiteSpace($Output)) {
    $Output = "docs/openapi/saas-openapi.json"
}

$endpoint = $BaseUrl.TrimEnd("/") + "/v3/api-docs"
if (-not [string]::IsNullOrWhiteSpace($Group) -and $Group -ne "root") {
    $endpoint = $endpoint + "/" + $Group
}

$headers = @{}
if (-not [string]::IsNullOrWhiteSpace($BearerToken)) {
    $headers["Authorization"] = "Bearer $BearerToken"
}

$outputPath = Join-Path (Get-Location) $Output
$outputDir = Split-Path -Parent $outputPath
if (-not (Test-Path -LiteralPath $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir | Out-Null
}

$response = Invoke-WebRequest -Uri $endpoint -Headers $headers -UseBasicParsing -TimeoutSec 60
if ([string]::IsNullOrWhiteSpace($response.Content)) {
    throw "OpenAPI export failed: empty response from $endpoint"
}

$json = $response.Content | ConvertFrom-Json
if ([string]::IsNullOrWhiteSpace($json.openapi) -or $null -eq $json.paths) {
    throw "OpenAPI export failed: response is not an OpenAPI document"
}

$response.Content | Set-Content -LiteralPath $outputPath -Encoding UTF8
$pathCount = ($json.paths.PSObject.Properties | Measure-Object).Count

Write-Host "OpenAPI exported: $Output"
Write-Host "Source: $endpoint"
Write-Host "Paths: $pathCount"
