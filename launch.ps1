param (
    [string]$IpAddress = ""
)

# 1. Connect to wireless ADB if IP is provided
if ($IpAddress) {
    Write-Host "Connecting to $IpAddress..." -ForegroundColor Cyan
    adb connect $IpAddress
}

# 2. Get connected ADB devices
$devicesOutput = adb devices | Select-String -Pattern "\b(device|emulator)\b" | Where-Object { $_ -notmatch "List of devices attached" }

if ($devicesOutput.Count -eq 0) {
    Write-Host "No devices found. Please connect a device via USB or Wireless ADB." -ForegroundColor Red
    exit 1
}

$selectedDevice = ""

Write-Host "Connected Devices & Emulators:" -ForegroundColor Yellow
for ($i = 0; $i -lt $devicesOutput.Count; $i++) {
    $devLine = $devicesOutput[$i] -split "`t"
    $devName = $devLine[0]
    $devType = $devLine[1]
    Write-Host "  [$($i + 1)] $devName ($devType)" -ForegroundColor Cyan
}

$choice = Read-Host "`nSelect device number to launch on (1-$($devicesOutput.Count))"
$index = [int]$choice - 1

if ($index -ge 0 -and $index -lt $devicesOutput.Count) {
    $selectedDevice = ($devicesOutput[$index] -split "`t")[0]
} else {
    Write-Host "Invalid selection. Exiting." -ForegroundColor Red
    exit 1
}

Write-Host "`nTarget Device: $selectedDevice" -ForegroundColor Green

# 3. Smart Build Check (Skip build if no source files changed since last APK)
$apkPath = "app\build\outputs\apk\debug\app-debug.apk"
$needsBuild = $true

if (Test-Path $apkPath) {
    $apkTime = (Get-Item $apkPath).LastWriteTime
    
    # Check app/src files
    $latestSrcFile = Get-ChildItem -Path "app\src" -Recurse -File | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    
    # Check gradle files in project
    $latestGradleFiles = Get-ChildItem -Path "." -Include "*.gradle.kts", "*.properties", "*.toml" -Recurse -File | 
        Where-Object { $_.FullName -notmatch "\\build\\" -and $_.FullName -notmatch "\\.gradle\\" } | 
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    
    $latestTime = [DateTime]::MinValue
    if ($latestSrcFile) { $latestTime = $latestSrcFile.LastWriteTime }
    if ($latestGradleFiles -and $latestGradleFiles.LastWriteTime -gt $latestTime) { $latestTime = $latestGradleFiles.LastWriteTime }

    if ($latestTime -lt $apkTime) {
        Write-Host "⚡ No source changes detected since last build. Skipping Gradle build..." -ForegroundColor Green
        $needsBuild = $false
    }
}

# 4. Build if needed
if ($needsBuild) {
    Write-Host "🔨 Changes detected (or no APK found). Building app..." -ForegroundColor Yellow
    $buildProcess = Start-Process -NoNewWindow -Wait -PassThru -FilePath ".\gradlew.bat" -ArgumentList "assembleDebug"
    if ($buildProcess.ExitCode -ne 0) {
        Write-Host "❌ Build failed!" -ForegroundColor Red
        exit 1
    }
}

# 5. Install APK
if (-not (Test-Path $apkPath)) {
    Write-Host "❌ APK not found at $apkPath" -ForegroundColor Red
    exit 1
}

Write-Host "📦 Installing app on $selectedDevice..." -ForegroundColor Yellow
$installOutput = adb -s $selectedDevice install -r $apkPath
if ($installOutput -match "Success") {
    Write-Host "✅ Install successful!" -ForegroundColor Green
} else {
    Write-Host $installOutput
}

# 6. Launch App
Write-Host "🚀 Launching Memoly..." -ForegroundColor Cyan
adb -s $selectedDevice shell am start -n com.memoly.dock/.MainActivity

Write-Host "🎉 Done!" -ForegroundColor Green
