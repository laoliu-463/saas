[Console]::OutputEncoding=[System.Text.Encoding]::UTF8
$OutputEncoding=[System.Text.Encoding]::UTF8

Write-Host "=== Codex 进程概览 ==="
Get-Process codex -ErrorAction SilentlyContinue |
  Select-Object Id, CPU, StartTime |
  Sort-Object CPU -Descending |
  Format-Table -AutoSize

Write-Host "`n=== Codex PID 详情 (Top 5 by CPU) ==="
Get-Process codex -ErrorAction SilentlyContinue |
  Sort-Object CPU -Descending |
  Select-Object -First 5 |
  ForEach-Object {
    $cmd = (Get-CimInstance Win32_Process -Filter ("ProcessId=" + $_.Id)).CommandLine
    "{0}`tPID={1}`tCPU={2}s`tStart={3}`n  CMD: {4}" -f "---", $_.Id, $_.CPU, $_.StartTime, $cmd
  }

Write-Host "`n=== Codex 子进程 (任何 pwsh/powershell 父 PID 是 codex) ==="
$codex_pids = (Get-Process codex -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Id)
$children = Get-CimInstance Win32_Process |
  Where-Object { $_.ParentProcessId -in $codex_pids -or $_.CommandLine -like '*codex*' -or $_.CommandLine -like '*agent-do*' } |
  Select-Object ProcessId, ParentProcessId, CreationDate, Name, CommandLine
if ($children) {
  $children | Format-Table -AutoSize -Wrap
} else {
  Write-Host "  (no children)"
}

Write-Host "`n=== 任何 agent-do 在跑? ==="
Get-CimInstance Win32_Process |
  Where-Object { $_.CommandLine -like '*agent-do*' } |
  Format-Table ProcessId, CreationDate, CommandLine -AutoSize -Wrap