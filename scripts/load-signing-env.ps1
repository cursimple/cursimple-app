param(
    [string]$OutputDir = ".signing"
)

$ErrorActionPreference = 'Stop'

function Add-GitHubEnvValue {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [Parameter(Mandatory = $true)]
        [string]$Value
    )

    if ([string]::IsNullOrWhiteSpace($env:GITHUB_ENV)) {
        return
    }

    $delimiter = "EOF_$([Guid]::NewGuid().ToString('N'))"
    @(
        "$Name<<$delimiter"
        $Value
        $delimiter
    ) | Add-Content -LiteralPath $env:GITHUB_ENV -Encoding utf8
}

function Mask-GitHubValue {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Value
    )

    if ($env:GITHUB_ACTIONS -eq 'true' -and -not [string]::IsNullOrEmpty($Value)) {
        Write-Output "::add-mask::$Value"
    }
}

function Require-EnvValue {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    $value = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "缺少环境变量：$Name"
    }

    return $value
}

$keystoreBase64 = Require-EnvValue -Name "CLASS_VIEWER_KEYSTORE_BASE64"
$keystorePassword = Require-EnvValue -Name "CLASS_VIEWER_KEYSTORE_PASSWORD"
$keyAlias = Require-EnvValue -Name "CLASS_VIEWER_KEY_ALIAS"
$keyPassword = Require-EnvValue -Name "CLASS_VIEWER_KEY_PASSWORD"

if (-not (Test-Path -Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

$resolvedOutputDir = (Resolve-Path -Path $OutputDir).Path
$keystorePath = Join-Path $resolvedOutputDir "class-viewer.jks"
$keystoreBytes = [Convert]::FromBase64String($keystoreBase64.Trim())
[System.IO.File]::WriteAllBytes($keystorePath, $keystoreBytes)

Mask-GitHubValue -Value $keystoreBase64
Mask-GitHubValue -Value $keystorePassword
Mask-GitHubValue -Value $keyPassword

$env:CLASS_VIEWER_KEYSTORE_FILE = $keystorePath
$env:CLASS_VIEWER_KEYSTORE_PASSWORD = $keystorePassword
$env:CLASS_VIEWER_KEY_ALIAS = $keyAlias
$env:CLASS_VIEWER_KEY_PASSWORD = $keyPassword

Add-GitHubEnvValue -Name "CLASS_VIEWER_KEYSTORE_FILE" -Value $keystorePath
Add-GitHubEnvValue -Name "CLASS_VIEWER_KEYSTORE_PASSWORD" -Value $keystorePassword
Add-GitHubEnvValue -Name "CLASS_VIEWER_KEY_ALIAS" -Value $keyAlias
Add-GitHubEnvValue -Name "CLASS_VIEWER_KEY_PASSWORD" -Value $keyPassword

Write-Host "已加载签名环境：" -ForegroundColor Green
Write-Host "CLASS_VIEWER_KEYSTORE_FILE=$keystorePath" -ForegroundColor Green
