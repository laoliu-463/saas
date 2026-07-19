param(
    [Alias("Env")]
    [ValidateSet("real-pre")]
    [string]$TargetEnv = "real-pre",
    [string]$RemoteHost = "saas",
    [string]$RemoteDir = "/opt/saas/app",
    [string]$RemoteEnvFile = "/opt/saas/env/.env.real-pre",
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

throw "旧远端部署入口已停用：禁止普通 Codex 任务 SSH、git pull 或现场构建。请将提交合并到 release/real-pre，由 Jenkins 唯一发布队列部署。"
