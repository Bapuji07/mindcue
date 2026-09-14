$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$AndroidRoot = Join-Path $ProjectRoot 'android'
$Wrapper = Join-Path $AndroidRoot 'gradlew.bat'

if (-not (Test-Path -LiteralPath $Wrapper)) { throw "Gradle wrapper not found: $Wrapper" }
if (-not $env:ANDROID_HOME) {
    $candidate = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    if (Test-Path -LiteralPath $candidate) { $env:ANDROID_HOME = $candidate }
}
if (-not $env:ANDROID_HOME -or -not (Test-Path -LiteralPath $env:ANDROID_HOME)) {
    throw 'Android SDK not found. Set ANDROID_HOME to your Android SDK directory.'
}

Push-Location $AndroidRoot
try { & $Wrapper --no-daemon assembleDebug; if ($LASTEXITCODE -ne 0) { throw 'Android build failed.' } }
finally { Pop-Location }

$apk = Join-Path $AndroidRoot 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path -LiteralPath $apk)) { throw 'Build completed without producing the expected APK.' }
Write-Host "APK ready: $apk" -ForegroundColor Green
