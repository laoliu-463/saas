param(
    [string]$DbHost = "localhost",
    [int]$DbPort = 5432,
    [string]$DbUser = "saas",
    [string]$DbPassword = "saas123",
    [string]$DbName = "colonel_saas_test",
    [switch]$Recreate
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$psql = "D:\DevTools\Database\postersql\bin\psql.exe"
$createdb = "D:\DevTools\Database\postersql\bin\createdb.exe"
$dropdb = "D:\DevTools\Database\postersql\bin\dropdb.exe"

if (!(Test-Path $psql)) {
    throw "psql.exe not found: $psql"
}

$env:PGPASSWORD = $DbPassword

if ($Recreate) {
    & $dropdb -h $DbHost -p $DbPort -U $DbUser --if-exists $DbName | Out-Null
}

$exists = & $psql -h $DbHost -p $DbPort -U $DbUser -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$DbName';"
if (-not $exists) {
    & $createdb -h $DbHost -p $DbPort -U $DbUser $DbName | Out-Null
}

& $psql -h $DbHost -p $DbPort -U $DbUser -d $DbName -v ON_ERROR_STOP=1 -f (Join-Path $root "src\main\resources\db\init-db.sql")
& $psql -h $DbHost -p $DbPort -U $DbUser -d $DbName -v ON_ERROR_STOP=1 -f (Join-Path $root "src\main\resources\db\test\schema.sql")

Write-Host "test database ready:" $DbName
