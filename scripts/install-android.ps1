$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $ProjectRoot 'android\app\build\outputs\apk\debug\app-debug.apk'
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adb = Join-Path $sdk 'platform-tools\adb.exe'

if (-not (Test-Path -LiteralPath $adb)) { throw 'adb was not found. Install Android SDK Platform Tools.' }
if (-not (Test-Path -LiteralPath $apk)) { throw 'APK not found. Run scripts\build-android.ps1 first.' }
$devices = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\tdevice$' }
if (@($devices).Count -ne 1) {
    throw 'Connect exactly one unlocked Android phone with USB debugging enabled, then accept its authorization prompt.'
}

& $adb install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'APK installation failed.' }
& $adb shell am start -n 'com.secondmemory.android/.MainActivity'
if ($LASTEXITCODE -ne 0) { throw 'The app installed but could not be launched automatically.' }
Write-Host 'Second Memory installed and opened on the phone.' -ForegroundColor Green
