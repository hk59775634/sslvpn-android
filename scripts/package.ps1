#Requires -Version 5.1
<#
.SYNOPSIS
  Build debug or release APK for sslvpn-android.

.EXAMPLE
  .\scripts\package.ps1
  .\scripts\package.ps1 -Release
  .\scripts\package.ps1 -Task assembleDebug
#>
param(
    [switch]$Release,
    [ValidateSet("assembleDebug", "assembleRelease", "bundleRelease")]
    [string]$Task = "assembleDebug"
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $RepoRoot

$JdkPath = Join-Path $RepoRoot ".tools\jdk17"
if (Test-Path (Join-Path $JdkPath "bin\java.exe")) {
    $env:JAVA_HOME = $JdkPath
    $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
    Write-Host "JAVA_HOME=$env:JAVA_HOME"
} elseif (-not $env:JAVA_HOME) {
    Write-Warning "JAVA_HOME not set and .tools\jdk17 not found. Install JDK 17 or set JAVA_HOME."
}

$SdkPath = Join-Path $RepoRoot ".tools\android-sdk"
if (Test-Path $SdkPath) {
    $env:ANDROID_SDK_ROOT = $SdkPath
    $env:ANDROID_HOME = $SdkPath
    Write-Host "ANDROID_SDK_ROOT=$env:ANDROID_SDK_ROOT"
}

$LocalProps = Join-Path $RepoRoot "local.properties"
if (-not (Test-Path $LocalProps)) {
    $sdkDir = $SdkPath.Replace("\", "/")
    "sdk.dir=$sdkDir" | Set-Content -Path $LocalProps -Encoding UTF8
    Write-Host "Wrote local.properties with sdk.dir=$sdkDir"
}

if ($Release) {
    $Task = "assembleRelease"
}

$Gradle = Join-Path $RepoRoot "gradlew.bat"
if (-not (Test-Path $Gradle)) {
    throw "gradlew.bat not found at $Gradle"
}

Write-Host "Running: gradlew.bat :app:$Task --no-daemon"
& $Gradle ":app:$Task" --no-daemon
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

if ($Task -eq "assembleDebug") {
    $Apk = Join-Path $RepoRoot "app\build\outputs\apk\debug\app-debug.apk"
} elseif ($Task -eq "assembleRelease") {
    $Apk = Join-Path $RepoRoot "app\build\outputs\apk\release\app-release.apk"
} else {
    $Apk = Join-Path $RepoRoot "app\build\outputs\bundle\release\app-release.aab"
}

if (Test-Path $Apk) {
    $item = Get-Item $Apk
    Write-Host ""
    Write-Host "OK: $($item.FullName)"
    Write-Host "Size: $([math]::Round($item.Length / 1MB, 2)) MB"
} else {
    Write-Host "Build finished but artifact not found at expected path: $Apk"
}
