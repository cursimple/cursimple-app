param(
    [string]$Repo = "x500x/ClassScheduleViewer",
    [string]$OutputDir = ".signing"
)

$ErrorActionPreference = 'Stop'

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw "未找到 gh 命令，请先安装 GitHub CLI。"
}

if ([string]::IsNullOrWhiteSpace($env:GH_TOKEN_class_viewer)) {
    throw "缺少环境变量 GH_TOKEN_class_viewer。"
}

$env:GH_TOKEN = $env:GH_TOKEN_class_viewer

$requiredVars = @(
    "CLASS_VIEWER_KEYSTORE_BASE64",
    "CLASS_VIEWER_KEYSTORE_PASSWORD",
    "CLASS_VIEWER_KEY_ALIAS",
    "CLASS_VIEWER_KEY_PASSWORD"
)

$values = @{}
foreach ($varName in $requiredVars) {
    $value = gh variable get $varName --repo $Repo
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($value)) {
        throw "读取 GitHub Variable 失败：$varName（repo: $Repo）"
    }
    $values[$varName] = $value.Trim()
}

if (-not (Test-Path -Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

$resolvedOutputDir = (Resolve-Path -Path $OutputDir).Path
$keystorePath = Join-Path $resolvedOutputDir "class-viewer.jks"
$keystoreBytes = [Convert]::FromBase64String($values["CLASS_VIEWER_KEYSTORE_BASE64"])
[System.IO.File]::WriteAllBytes($keystorePath, $keystoreBytes)

$env:CLASS_VIEWER_KEYSTORE_FILE = $keystorePath
$env:CLASS_VIEWER_KEYSTORE_PASSWORD = $values["CLASS_VIEWER_KEYSTORE_PASSWORD"]
$env:CLASS_VIEWER_KEY_ALIAS = $values["CLASS_VIEWER_KEY_ALIAS"]
$env:CLASS_VIEWER_KEY_PASSWORD = $values["CLASS_VIEWER_KEY_PASSWORD"]

Write-Host "已写入当前会话签名环境变量：" -ForegroundColor Green
Write-Host "CLASS_VIEWER_KEYSTORE_FILE=$keystorePath" -ForegroundColor Green
Write-Host ""
Write-Host "现在可以执行：" -ForegroundColor Cyan
Write-Host "./gradlew assembleDebug" -ForegroundColor Cyan
Write-Host "./gradlew assembleRelease" -ForegroundColor Cyan
