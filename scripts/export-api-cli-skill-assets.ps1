param(
    [string]$OpenApiFile = "docs/openapi/saas-openapi.json",
    [string]$FullOutput = "docs/openapi/openapi-full.json",
    [string]$BusinessOutput = "docs/openapi/openapi-business.json",
    [string]$SdkDebugOutput = "docs/openapi/openapi-sdk-debug.json",
    [string]$ManifestOutput = ".claude/skills/saas-api-cli-skill/references/project-assets-manifest.md"
)

$ErrorActionPreference = "Stop"

function Get-ProjectRoot {
    return (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
}

function Resolve-ProjectPath {
    param(
        [Parameter(Mandatory = $true)][string]$RepoRoot,
        [Parameter(Mandatory = $true)][string]$Path
    )

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return $Path
    }
    return (Join-Path $RepoRoot $Path)
}

function Get-RelativePath {
    param(
        [Parameter(Mandatory = $true)][string]$RepoRoot,
        [Parameter(Mandatory = $true)][string]$Path
    )

    $root = (Resolve-Path -LiteralPath $RepoRoot).Path.TrimEnd("\")
    $resolved = (Resolve-Path -LiteralPath $Path).Path
    $prefix = $root + "\"
    if ($resolved -eq $root) {
        return "."
    }
    if (-not $resolved.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Path is outside project root: $resolved"
    }
    return $resolved.Substring($prefix.Length).Replace("\", "/")
}

function Copy-JsonObject {
    param([Parameter(Mandatory = $true)]$Value)
    return ($Value | ConvertTo-Json -Depth 100 | ConvertFrom-Json)
}

function Test-SdkDebugPath {
    param([Parameter(Mandatory = $true)][string]$Path)

    $lower = $Path.ToLowerInvariant()
    return $lower.StartsWith("/douyin") `
        -or $lower.Contains("/douyin/") `
        -or $lower.Contains("douyin") `
        -or $lower.Contains("webhook") `
        -or $lower.Contains("oauth") `
        -or $lower.Contains("probe")
}

function Get-OpenApiStats {
    param([Parameter(Mandatory = $true)]$Doc)

    $pathCount = @($Doc.paths.PSObject.Properties).Count
    $operationCount = 0
    foreach ($pathProperty in $Doc.paths.PSObject.Properties) {
        foreach ($methodProperty in $pathProperty.Value.PSObject.Properties) {
            if ($methodProperty.Name.ToLowerInvariant() -in @("get", "post", "put", "delete", "patch", "options", "head")) {
                $operationCount++
            }
        }
    }

    $schemaCount = 0
    if ($Doc.components -and $Doc.components.schemas) {
        $schemaCount = @($Doc.components.schemas.PSObject.Properties).Count
    }

    $tagCount = 0
    if ($Doc.tags) {
        $tagCount = @($Doc.tags).Count
    }

    return [pscustomobject]@{
        paths = $pathCount
        operations = $operationCount
        schemas = $schemaCount
        tags = $tagCount
    }
}

function New-OpenApiSplit {
    param(
        [Parameter(Mandatory = $true)]$Source,
        [Parameter(Mandatory = $true)][bool]$SdkDebug,
        [Parameter(Mandatory = $true)][string]$TitleSuffix,
        [Parameter(Mandatory = $true)][string]$SourcePath
    )

    $selectedPaths = [ordered]@{}
    $usedTags = [System.Collections.Generic.HashSet[string]]::new()

    foreach ($pathProperty in $Source.paths.PSObject.Properties) {
        $path = $pathProperty.Name
        $isSdkPath = Test-SdkDebugPath -Path $path
        if ($SdkDebug -ne $isSdkPath) {
            continue
        }

        $selectedPaths[$path] = $pathProperty.Value
        foreach ($methodProperty in $pathProperty.Value.PSObject.Properties) {
            if ($methodProperty.Name.ToLowerInvariant() -notin @("get", "post", "put", "delete", "patch", "options", "head")) {
                continue
            }

            $operation = $methodProperty.Value
            if ($operation.tags) {
                foreach ($tag in $operation.tags) {
                    [void]$usedTags.Add([string]$tag)
                }
            }
        }
    }

    $split = Copy-JsonObject -Value $Source
    if ($split.info -and $split.info.title) {
        $split.info.title = "$($Source.info.title) - $TitleSuffix"
    }

    $split | Add-Member -MemberType NoteProperty -Name paths -Value ([pscustomobject]$selectedPaths) -Force

    if ($Source.tags) {
        $selectedTags = @($Source.tags | Where-Object { $_.name -and $usedTags.Contains([string]$_.name) })
        if ($selectedTags.Count -gt 0) {
            $split | Add-Member -MemberType NoteProperty -Name tags -Value $selectedTags -Force
        }
    }

    $split | Add-Member -MemberType NoteProperty -Name "x-saas-generated-from" -Value $SourcePath -Force
    $split | Add-Member -MemberType NoteProperty -Name "x-saas-generated-at" -Value ((Get-Date).ToString("o")) -Force
    return $split
}

function Write-JsonFile {
    param(
        [Parameter(Mandatory = $true)]$Value,
        [Parameter(Mandatory = $true)][string]$Path
    )

    $parent = Split-Path -Parent $Path
    if (-not (Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
    }
    $Value | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $Path -Encoding UTF8
}

function New-ManifestRecord {
    param(
        [Parameter(Mandatory = $true)][string]$RepoRoot,
        [Parameter(Mandatory = $true)][System.IO.FileInfo]$Item
    )

    return [pscustomobject]@{
        path = Get-RelativePath -RepoRoot $RepoRoot -Path $Item.FullName
        bytes = $Item.Length
        updatedAt = $Item.LastWriteTime.ToString("yyyy-MM-dd HH:mm:ss")
    }
}

function Get-InterfaceDocs {
    param([Parameter(Mandatory = $true)][string]$RepoRoot)

    $docsRoot = Join-Path $RepoRoot "docs"
    if (-not (Test-Path -LiteralPath $docsRoot)) {
        return @()
    }

    return @(Get-ChildItem -Recurse -File -LiteralPath $docsRoot | Where-Object {
        $relative = (Get-RelativePath -RepoRoot $RepoRoot -Path $_.FullName).ToLowerInvariant()
        $name = $_.Name.ToLowerInvariant()
        $relative.StartsWith("docs/openapi/") `
            -or $relative.StartsWith("docs/接口/") `
            -or $relative.StartsWith("docs/对接/") `
            -or $relative.StartsWith("docs/验收/") `
            -or $name.Contains("api") `
            -or $name.Contains("接口") `
            -or $name.Contains("测试") `
            -or $name.Contains("验收") `
            -or $name.Contains("mcp")
    } | ForEach-Object {
        New-ManifestRecord -RepoRoot $RepoRoot -Item $_
    } | Sort-Object path)
}

function Get-TestAssets {
    param([Parameter(Mandatory = $true)][string]$RepoRoot)

    $items = @()
    foreach ($root in @("tests", "backend/src/test", "frontend/src", "runtime/qa", "scripts/qa")) {
        $fullRoot = Join-Path $RepoRoot $root
        if (-not (Test-Path -LiteralPath $fullRoot)) {
            continue
        }

        $items += @(Get-ChildItem -Recurse -File -LiteralPath $fullRoot | Where-Object {
            $relative = (Get-RelativePath -RepoRoot $RepoRoot -Path $_.FullName).ToLowerInvariant()
            if ($relative.Contains("/out/") -or $relative.Contains("/screenshots/")) {
                return $false
            }

            $name = $_.Name.ToLowerInvariant()
            $extension = $_.Extension.ToLowerInvariant()
            $name.EndsWith(".spec.ts") `
                -or $name.EndsWith(".test.ts") `
                -or $name.EndsWith(".test.cjs") `
                -or $extension -in @(".java", ".jmx", ".cjs", ".js", ".ts", ".json", ".md", ".yml", ".xml")
        })
    }

    return @($items | Sort-Object FullName -Unique | ForEach-Object {
        New-ManifestRecord -RepoRoot $RepoRoot -Item $_
    } | Sort-Object path)
}

function Write-Manifest {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)]$FullStats,
        [Parameter(Mandatory = $true)]$BusinessStats,
        [Parameter(Mandatory = $true)]$SdkDebugStats,
        [Parameter(Mandatory = $true)][object[]]$InterfaceDocs,
        [Parameter(Mandatory = $true)][object[]]$TestAssets
    )

    $parent = Split-Path -Parent $Path
    if (-not (Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
    }

    $generatedAt = Get-Date -Format "yyyy-MM-dd HH:mm:ss zzz"
    $lines = @(
        "# SAAS API CLI / Skill Asset Manifest",
        "",
        "- Generated at: $generatedAt",
        "- Source OpenAPI: $OpenApiFile",
        "- Full OpenAPI: $FullOutput",
        "- Business OpenAPI: $BusinessOutput",
        "- SDK debug OpenAPI: $SdkDebugOutput",
        "- Interface docs indexed: $($InterfaceDocs.Count)",
        "- Test assets indexed: $($TestAssets.Count)",
        "",
        "## Contents",
        "",
        "- OpenAPI outputs",
        "- Interface docs",
        "- Test assets",
        "",
        "## OpenAPI Outputs",
        "",
        "| Asset | Path | Paths | Operations | Schemas | Tags |",
        "| --- | --- | ---: | ---: | ---: | ---: |",
        "| Full | $FullOutput | $($FullStats.paths) | $($FullStats.operations) | $($FullStats.schemas) | $($FullStats.tags) |",
        "| Business | $BusinessOutput | $($BusinessStats.paths) | $($BusinessStats.operations) | $($BusinessStats.schemas) | $($BusinessStats.tags) |",
        "| SDK debug | $SdkDebugOutput | $($SdkDebugStats.paths) | $($SdkDebugStats.operations) | $($SdkDebugStats.schemas) | $($SdkDebugStats.tags) |",
        "",
        "## Interface Docs",
        "",
        "~~~text"
    )
    $lines += @($InterfaceDocs | ForEach-Object { "$($_.path) | bytes=$($_.bytes) | updatedAt=$($_.updatedAt)" })
    $lines += @(
        "~~~",
        "",
        "## Test Assets",
        "",
        "~~~text"
    )
    $lines += @($TestAssets | ForEach-Object { "$($_.path) | bytes=$($_.bytes) | updatedAt=$($_.updatedAt)" })
    $lines += "~~~"

    Set-Content -LiteralPath $Path -Value $lines -Encoding UTF8
}

$repoRoot = Get-ProjectRoot
$openApiPath = Resolve-ProjectPath -RepoRoot $repoRoot -Path $OpenApiFile
$fullOutputPath = Resolve-ProjectPath -RepoRoot $repoRoot -Path $FullOutput
$businessOutputPath = Resolve-ProjectPath -RepoRoot $repoRoot -Path $BusinessOutput
$sdkDebugOutputPath = Resolve-ProjectPath -RepoRoot $repoRoot -Path $SdkDebugOutput
$manifestPath = Resolve-ProjectPath -RepoRoot $repoRoot -Path $ManifestOutput

if (-not (Test-Path -LiteralPath $openApiPath)) {
    throw "OpenAPI source file not found: $OpenApiFile"
}

$source = Get-Content -Raw -LiteralPath $openApiPath -Encoding UTF8 | ConvertFrom-Json
if (-not $source.openapi -or -not $source.paths) {
    throw "OpenAPI source file is not a valid OpenAPI document: $OpenApiFile"
}

$full = Copy-JsonObject -Value $source
$full | Add-Member -MemberType NoteProperty -Name "x-saas-generated-from" -Value $OpenApiFile -Force
$full | Add-Member -MemberType NoteProperty -Name "x-saas-generated-at" -Value ((Get-Date).ToString("o")) -Force

$business = New-OpenApiSplit -Source $source -SdkDebug $false -TitleSuffix "business" -SourcePath $OpenApiFile
$sdkDebug = New-OpenApiSplit -Source $source -SdkDebug $true -TitleSuffix "sdk-debug" -SourcePath $OpenApiFile

$fullStats = Get-OpenApiStats -Doc $full
$businessStats = Get-OpenApiStats -Doc $business
$sdkDebugStats = Get-OpenApiStats -Doc $sdkDebug

if ($businessStats.paths -eq 0) {
    throw "Business OpenAPI split produced zero paths."
}
if ($sdkDebugStats.paths -eq 0) {
    throw "SDK debug OpenAPI split produced zero paths."
}

Write-JsonFile -Value $full -Path $fullOutputPath
Write-JsonFile -Value $business -Path $businessOutputPath
Write-JsonFile -Value $sdkDebug -Path $sdkDebugOutputPath

$interfaceDocs = Get-InterfaceDocs -RepoRoot $repoRoot
$testAssets = Get-TestAssets -RepoRoot $repoRoot
Write-Manifest -Path $manifestPath `
    -FullStats $fullStats `
    -BusinessStats $businessStats `
    -SdkDebugStats $sdkDebugStats `
    -InterfaceDocs $interfaceDocs `
    -TestAssets $testAssets

Write-Host "API CLI / Skill assets exported."
Write-Host "Full OpenAPI: $FullOutput ($($fullStats.paths) paths, $($fullStats.operations) operations)"
Write-Host "Business OpenAPI: $BusinessOutput ($($businessStats.paths) paths, $($businessStats.operations) operations)"
Write-Host "SDK debug OpenAPI: $SdkDebugOutput ($($sdkDebugStats.paths) paths, $($sdkDebugStats.operations) operations)"
Write-Host "Skill manifest: $ManifestOutput ($($interfaceDocs.Count) docs, $($testAssets.Count) test assets)"
