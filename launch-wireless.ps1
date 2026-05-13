[CmdletBinding()]
param(
    [string]$Target,
    [switch]$ListTargets
)

$ErrorActionPreference = 'Stop'

$apkPath = Join-Path $PSScriptRoot 'app\build\outputs\apk\debug'
$apk = Join-Path $apkPath 'app-debug.apk'
$appId = 'com.memoly.dock'

function Convert-LocalPropertiesPath {
    param([string]$RawValue)

    $value = $RawValue -replace '\\:', ':'
    $value = $value -replace '\\\\', '\'
    return $value
}

function Get-AndroidSdkPath {
    $candidates = @()

    if ($env:ANDROID_SDK_ROOT) { $candidates += $env:ANDROID_SDK_ROOT }
    if ($env:ANDROID_HOME) { $candidates += $env:ANDROID_HOME }

    $localProperties = Join-Path $PSScriptRoot 'local.properties'
    if (Test-Path $localProperties) {
        $sdkLine = Get-Content $localProperties |
            Where-Object { $_ -match '^sdk\.dir=' } |
            Select-Object -First 1

        if ($sdkLine) {
            $sdkValue = Convert-LocalPropertiesPath ($sdkLine -replace '^sdk\.dir=', '')
            if ($sdkValue) { $candidates += $sdkValue }
        }
    }

    if ($env:LOCALAPPDATA) {
        $candidates += (Join-Path $env:LOCALAPPDATA 'Android\Sdk')
    }

    foreach ($candidate in ($candidates | Where-Object { $_ } | Select-Object -Unique)) {
        if (Test-Path $candidate) {
            return (Resolve-Path $candidate).Path
        }
    }

    throw 'Android SDK not found. Set ANDROID_SDK_ROOT or ensure local.properties contains sdk.dir.'
}

function Get-CommandPath {
    param([string]$CommandName)

    $command = Get-Command $CommandName -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($command) {
        return $command.Source
    }

    return $null
}

function Get-AndroidUserHome {
    $candidates = @()

    if ($env:USERPROFILE) { $candidates += $env:USERPROFILE }

    $userProfile = [Environment]::GetFolderPath('UserProfile')
    if ($userProfile) { $candidates += $userProfile }

    $searchRoot = $PSScriptRoot
    while ($searchRoot) {
        $candidates += $searchRoot
        $parent = Split-Path $searchRoot -Parent
        if (-not $parent -or $parent -eq $searchRoot) {
            break
        }
        $searchRoot = $parent
    }

    foreach ($candidate in ($candidates | Where-Object { $_ } | Select-Object -Unique)) {
        $androidHome = Join-Path $candidate '.android'
        if (Test-Path $androidHome) {
            return $androidHome
        }
    }

    return $null
}

$script:SdkPath = Get-AndroidSdkPath
$script:AdbExe = Join-Path $script:SdkPath 'platform-tools\adb.exe'
$script:EmulatorExe = Join-Path $script:SdkPath 'emulator\emulator.exe'
$script:AndroidUserHome = Get-AndroidUserHome

if ($script:AndroidUserHome) {
    $env:ANDROID_USER_HOME = $script:AndroidUserHome
    $env:ANDROID_SDK_HOME = (Split-Path $script:AndroidUserHome -Parent)
    $env:HOME = (Split-Path $script:AndroidUserHome -Parent)
}

if (-not (Test-Path $script:AdbExe)) {
    $script:AdbExe = Get-CommandPath 'adb'
}

if (-not (Test-Path $script:EmulatorExe)) {
    $script:EmulatorExe = Get-CommandPath 'emulator'
}

if (-not $script:AdbExe) {
    throw "adb not found. Expected it under '$script:SdkPath\platform-tools'."
}

function Invoke-Adb {
    param(
        [Parameter(ValueFromRemainingArguments = $true)]
        [string[]]$Arguments
    )

    & $script:AdbExe @Arguments
}

function Start-AdbServer {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        Invoke-Adb start-server | Out-Null
    }
    finally {
        $ErrorActionPreference = $previous
    }
}

function Get-DetailValue {
    param(
        [string]$Details,
        [string]$Name
    )

    $pattern = '(?:^|\s)' + [regex]::Escape($Name) + ':(?<value>\S+)'
    if ($Details -match $pattern) {
        return ($matches['value'] -replace '_', ' ')
    }

    return $null
}

function Get-RunningAvdName {
    param([string]$Serial)

    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = Invoke-Adb -s $Serial emu avd name 2>&1
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previous
    }

    if ($exitCode -ne 0) {
        return $null
    }

    $text = ($output | Out-String).Trim()
    if (-not $text) {
        return $null
    }

    return ($text -split "`r?`n" |
        ForEach-Object { $_.Trim() } |
        Where-Object { $_ -and $_ -ne 'OK' } |
        Select-Object -First 1)
}

function Get-ConnectedDevices {
    $devices = @()

    Invoke-Adb devices -l |
        Select-Object -Skip 1 |
        ForEach-Object {
            $line = $_.ToString().Trim()
            if (-not $line) { return }

            if ($line -match '^(?<serial>\S+)\s+(?<state>\S+)(?<details>.*)$') {
                if ($matches['state'] -ne 'device') {
                    return
                }

                $serial = $matches['serial']
                $details = $matches['details'].Trim()
                $kind = if ($serial -like 'emulator-*') { 'emulator' } else { 'device' }
                $avdName = if ($kind -eq 'emulator') { Get-RunningAvdName $serial } else { $null }
                $model = Get-DetailValue -Details $details -Name 'model'
                $deviceName = Get-DetailValue -Details $details -Name 'device'
                $product = Get-DetailValue -Details $details -Name 'product'

                $name = if ($kind -eq 'emulator' -and $avdName) {
                    $avdName
                }
                elseif ($model) {
                    $model
                }
                elseif ($deviceName) {
                    $deviceName
                }
                elseif ($product) {
                    $product
                }
                else {
                    $serial
                }

                $devices += [pscustomobject]@{
                    Type        = 'connected'
                    Serial      = $serial
                    Kind        = $kind
                    Name        = $name
                    AvdName     = $avdName
                    Details     = $details
                    DisplayName = if ($kind -eq 'emulator') {
                        "Running emulator - $name [$serial]"
                    }
                    else {
                        "Connected device - $name [$serial]"
                    }
                    Key         = "connected:$serial"
                }
            }
        }

    return $devices
}

function Get-MdnsServices {
    $services = @()

    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $lines = Invoke-Adb mdns services 2>&1
    }
    finally {
        $ErrorActionPreference = $previous
    }

    foreach ($entry in $lines) {
        $line = $entry.ToString().Trim()
        if (-not $line) { continue }
        if ($line -match '^List of discovered mdns services') { continue }

        $tokens = $line -split '\s+' | Where-Object { $_ }
        $endpoint = $tokens | Where-Object { $_ -match '^[^:]+:\d+$' } | Select-Object -First 1
        $serviceToken = $tokens | Where-Object { $_ -match '_adb-tls-(connect|pairing)\._tcp\.?$' } | Select-Object -First 1

        if (-not $endpoint -or -not $serviceToken) {
            continue
        }

        $serviceType = if ($serviceToken -match 'pairing') { 'pairing' } else { 'connect' }
        $deviceHost = ($endpoint -split ':')[0]
        $name = (($tokens | Where-Object { $_ -ne $endpoint -and $_ -ne $serviceToken }) -join ' ').Trim('. ')

        if (-not $name) {
            $name = $endpoint
        }

        $services += [pscustomobject]@{
            ServiceType = $serviceType
            Endpoint    = $endpoint
            DeviceHost  = $deviceHost
            Name        = $name
        }
    }

    return $services
}

function Get-WirelessTargets {
    $services = Get-MdnsServices
    $pairingByHost = @{}

    foreach ($service in ($services | Where-Object { $_.ServiceType -eq 'pairing' })) {
        $pairingByHost[$service.DeviceHost] = $service.Endpoint
    }

    $targets = @()
    foreach ($service in ($services | Where-Object { $_.ServiceType -eq 'connect' })) {
        $targets += [pscustomobject]@{
            Type        = 'wireless'
            Endpoint    = $service.Endpoint
            DeviceHost  = $service.DeviceHost
            PairEndpoint = $pairingByHost[$service.DeviceHost]
            Name        = $service.Name
            DisplayName = "Wireless device - $($service.Name) [$($service.Endpoint)]"
            Key         = "wireless:$($service.Endpoint)"
        }
    }

    return $targets
}

function Get-InstalledAvds {
    $avds = @()

    if ($script:EmulatorExe -and (Test-Path $script:EmulatorExe)) {
        $previous = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try {
            $avds = & $script:EmulatorExe -list-avds 2>$null |
                ForEach-Object { $_.ToString().Trim() } |
                Where-Object { $_ }
        }
        finally {
            $ErrorActionPreference = $previous
        }
    }

    if (-not $avds) {
        $avdHome = Join-Path $HOME '.android\avd'
        if (Test-Path $avdHome) {
            $avds = Get-ChildItem $avdHome -Filter '*.ini' -File |
                ForEach-Object { [System.IO.Path]::GetFileNameWithoutExtension($_.Name) }
        }
    }

    return $avds | Sort-Object -Unique
}

function Get-LaunchTargets {
    $connected = Get-ConnectedDevices
    
    # 1. Deduplicate Connected Devices
    # ADB often shows both IP and mDNS connections for the exact same device.
    $finalConnected = @()
    $ipConnectedNames = @{}
    
    foreach ($c in $connected) {
        if ($c.Serial -match '^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}:\d+$') {
            $ipConnectedNames[$c.Name] = $true
        }
    }

    foreach ($c in $connected) {
        $isDuplicate = $false
        if ($c.Serial -match '^(adb-[^\.]+)') {
            # If an IP-based connection with the same Model Name exists, hide this mDNS clone
            if ($ipConnectedNames.ContainsKey($c.Name)) {
                $isDuplicate = $true
            }
        }
        
        if (-not $isDuplicate) {
            $finalConnected += $c
        }
    }

    $targets = @()
    $targets += $finalConnected
    
    $runningAvdNames = $finalConnected |
        Where-Object { $_.Kind -eq 'emulator' -and $_.AvdName } |
        ForEach-Object { $_.AvdName }

    foreach ($avd in (Get-InstalledAvds | Where-Object { $runningAvdNames -notcontains $_ })) {
        $targets += [pscustomobject]@{
            Type        = 'avd'
            AvdName     = $avd
            DisplayName = "Android emulator - $avd"
            Key         = "avd:$avd"
        }
    }

    return $targets
}

function Write-LaunchTargets {
    param([object[]]$Targets)

    if (-not $Targets -or $Targets.Count -eq 0) {
        Write-Host 'No launch targets found.' -ForegroundColor Yellow
        return
    }

    for ($i = 0; $i -lt $Targets.Count; $i++) {
        $item = $Targets[$i]
        Write-Host ("[{0}] {1}" -f ($i + 1), $item.DisplayName)
        Write-Host ("     key: {0}" -f $item.Key) -ForegroundColor DarkGray
    }
}

function Select-LaunchTarget {
    param(
        [object[]]$Targets,
        [string]$RequestedTarget
    )

    if (-not $Targets -or $Targets.Count -eq 0) {
        throw 'No launch targets found. Connect a device, enable wireless debugging, or install an AVD first.'
    }

    if ($RequestedTarget) {
        $selected = $Targets |
            Where-Object {
                $_.Key -ieq $RequestedTarget -or
                $_.Serial -ieq $RequestedTarget -or
                $_.Endpoint -ieq $RequestedTarget -or
                $_.AvdName -ieq $RequestedTarget
            } |
            Select-Object -First 1

        if (-not $selected) {
            throw "Target '$RequestedTarget' was not found. Run with -ListTargets to see valid options."
        }

        return $selected
    }

    if ($Targets.Count -eq 1) {
        Write-Host "Only one launch target found. Using: $($Targets[0].DisplayName)" -ForegroundColor Green
        return $Targets[0]
    }

    Write-Host ''
    Write-Host 'Select Android launch target:' -ForegroundColor Cyan
    Write-LaunchTargets -Targets $Targets
    Write-Host ''

    while ($true) {
        $rawChoice = Read-Host 'Enter target number'
        $choice = 0

        if ([int]::TryParse($rawChoice, [ref]$choice)) {
            if ($choice -ge 1 -and $choice -le $Targets.Count) {
                return $Targets[$choice - 1]
            }
        }

        Write-Host 'Invalid selection. Enter one of the numbers shown above.' -ForegroundColor Yellow
    }
}

function Try-Connect {
    param([string]$Endpoint)

    Write-Host "Attempting: adb connect $Endpoint" -ForegroundColor Cyan

    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = Invoke-Adb connect $Endpoint 2>&1
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previous
    }

    $output | Out-Host
    $text = ($output | Out-String)

    if ($exitCode -eq 0 -and $text -match 'connected to|already connected to') {
        return $true
    }

    return $false
}

function Connect-WirelessTarget {
    param([object]$Target)

    if (-not (Try-Connect $Target.Endpoint)) {
        if ($Target.PairEndpoint) {
            $pairCode = Read-Host "Enter pairing code for $($Target.PairEndpoint) (or press Enter to cancel)"

            if ($pairCode) {
                Write-Host "Attempting: adb pair $($Target.PairEndpoint)" -ForegroundColor Cyan

                $previous = $ErrorActionPreference
                $ErrorActionPreference = 'Continue'
                try {
                    $pairOutput = Invoke-Adb pair $Target.PairEndpoint $pairCode 2>&1
                    $pairExitCode = $LASTEXITCODE
                }
                finally {
                    $ErrorActionPreference = $previous
                }

                $pairOutput | Out-Host
                $pairText = ($pairOutput | Out-String)

                if ($pairExitCode -ne 0 -or $pairText -notmatch 'Successfully paired|Pairing succeeded') {
                    throw "Pairing failed for '$($Target.PairEndpoint)'."
                }

                if (-not (Try-Connect $Target.Endpoint)) {
                    throw "Connected pairing succeeded, but adb connect still failed for '$($Target.Endpoint)'."
                }
            }
            else {
                throw "Wireless connect failed for '$($Target.Endpoint)'. Pairing was cancelled."
            }
        }
        else {
            throw "Wireless connect failed for '$($Target.Endpoint)'. No pairing endpoint was discovered."
        }
    }

    Start-Sleep -Milliseconds 700

    $device = Get-ConnectedDevices | Where-Object { $_.Serial -eq $Target.Endpoint } | Select-Object -First 1
    if (-not $device) {
        throw "Wireless target '$($Target.Endpoint)' did not appear in 'adb devices'."
    }

    return $device.Serial
}

function Start-SelectedAvd {
    param([string]$AvdName)

    if (-not $script:EmulatorExe) {
        throw "emulator.exe not found. Expected it under '$script:SdkPath\emulator'."
    }

    Write-Host "Starting emulator '$AvdName'..." -ForegroundColor Cyan
    Start-Process -FilePath $script:EmulatorExe -ArgumentList @('-avd', $AvdName) | Out-Null
}

function Wait-ForBootCompleted {
    param(
        [string]$Serial,
        [int]$TimeoutSeconds = 300
    )

    Invoke-Adb -s $Serial wait-for-device | Out-Null

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $previous = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try {
            $output = Invoke-Adb -s $Serial shell getprop sys.boot_completed 2>$null
        }
        finally {
            $ErrorActionPreference = $previous
        }

        $bootCompleted = ($output | Out-String).Trim()
        if ($bootCompleted -eq '1') {
            return
        }

        Start-Sleep -Seconds 3
    }

    throw "Timed out waiting for '$Serial' to finish booting."
}

function Wait-ForStartedAvd {
    param(
        [string]$AvdName,
        [string[]]$ExistingEmulatorSerials
    )

    Write-Host "Waiting for emulator '$AvdName' to come online..." -ForegroundColor Cyan

    $deadline = (Get-Date).AddMinutes(5)
    while ((Get-Date) -lt $deadline) {
        $emulators = Get-ConnectedDevices | Where-Object { $_.Kind -eq 'emulator' }

        $selected = $emulators |
            Where-Object { $_.AvdName -eq $AvdName } |
            Select-Object -First 1

        if (-not $selected) {
            $selected = $emulators |
                Where-Object { $ExistingEmulatorSerials -notcontains $_.Serial } |
                Select-Object -First 1
        }

        if ($selected) {
            Wait-ForBootCompleted -Serial $selected.Serial
            return $selected.Serial
        }

        Start-Sleep -Seconds 3
    }

    throw "Timed out waiting for emulator '$AvdName' to appear in adb."
}

function Test-NeedsBuild {
    param([string]$ApkPath)

    if (-not (Test-Path $ApkPath)) {
        return $true
    }

    $apkTime = (Get-Item $ApkPath).LastWriteTime
    $newerFiles = @()

    $sourceDirs = @(
        (Join-Path $PSScriptRoot 'app\src\main\java'),
        (Join-Path $PSScriptRoot 'app\src\main\res')
    )

    foreach ($dir in $sourceDirs) {
        if (Test-Path $dir) {
            $newerFiles += Get-ChildItem -Path $dir -Recurse -File -ErrorAction SilentlyContinue |
                Where-Object { $_.LastWriteTime -gt $apkTime }
        }
    }

    $trackedFiles = @(
        (Join-Path $PSScriptRoot 'app\src\main\AndroidManifest.xml'),
        (Join-Path $PSScriptRoot 'app\build.gradle.kts'),
        (Join-Path $PSScriptRoot 'build.gradle.kts'),
        (Join-Path $PSScriptRoot 'settings.gradle.kts')
    )

    foreach ($file in $trackedFiles) {
        if ((Test-Path $file) -and ((Get-Item $file).LastWriteTime -gt $apkTime)) {
            $newerFiles += Get-Item $file
        }
    }

    if ($newerFiles.Count -eq 0) {
        Write-Host 'No changes detected - skipping build' -ForegroundColor Green
        return $false
    }

    Write-Host "Detected $($newerFiles.Count) changed file(s) - rebuilding..." -ForegroundColor Yellow
    return $true
}

function Build-DebugApkIfNeeded {
    param([string]$ApkPath)

    if (-not (Test-NeedsBuild -ApkPath $ApkPath)) {
        return
    }

    Write-Host 'Building project...' -ForegroundColor Cyan
    Push-Location $PSScriptRoot
    try {
        & .\gradlew.bat assembleDebug
        if ($LASTEXITCODE -ne 0) {
            throw "Build failed with exit code $LASTEXITCODE"
        }
    }
    finally {
        Pop-Location
    }
}

function Install-Apk {
    param(
        [string]$Serial,
        [string]$ApkPath
    )

    Write-Host "Installing on $Serial..." -ForegroundColor Cyan

    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $installOutput = Invoke-Adb -s $Serial install -r $ApkPath 2>&1
        $installExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previous
    }

    $installOutput | Out-Host

    if ($installExitCode -eq 0) {
        return
    }

    $installText = ($installOutput | Out-String)

    if ($installText -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE') {
        Write-Host 'Detected a signature mismatch with an existing installed package.' -ForegroundColor Yellow
        Write-Host 'Debug APK cannot overwrite the release-signed app directly.' -ForegroundColor Yellow

        $confirm = Read-Host "Uninstall '$appId' from $Serial and retry install? (y/N)"
        if ($confirm -notmatch '^(y|Y)$') {
            throw 'Install aborted due to signature mismatch.'
        }

        Invoke-Adb -s $Serial uninstall $appId | Out-Host

        $previous = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try {
            $retryOutput = Invoke-Adb -s $Serial install -r $ApkPath 2>&1
            $retryExitCode = $LASTEXITCODE
        }
        finally {
            $ErrorActionPreference = $previous
        }

        $retryOutput | Out-Host
        if ($retryExitCode -ne 0) {
            throw 'Install failed after uninstall/retry.'
        }

        return
    }

    if ($installText -match 'device offline') {
        throw "Install failed: '$Serial' is offline."
    }

    throw 'Install failed.'
}

function Launch-App {
    param([string]$Serial)

    Write-Host 'Launching app...' -ForegroundColor Cyan
    Invoke-Adb -s $Serial shell am start -n "$appId/.MainActivity" | Out-Host
}

Start-AdbServer

$targets = Get-LaunchTargets

if ($ListTargets) {
    Write-LaunchTargets -Targets $targets
    exit 0
}

$selectedTarget = Select-LaunchTarget -Targets $targets -RequestedTarget $Target
Write-Host "Selected target: $($selectedTarget.DisplayName)" -ForegroundColor Green

$deviceSerial = $null
$startedAvd = $null

switch ($selectedTarget.Type) {
    'connected' {
        $deviceSerial = $selectedTarget.Serial
    }
    'wireless' {
        $deviceSerial = Connect-WirelessTarget -Target $selectedTarget
    }
    'avd' {
        $existingEmulatorSerials = Get-ConnectedDevices |
            Where-Object { $_.Kind -eq 'emulator' } |
            ForEach-Object { $_.Serial }

        Start-SelectedAvd -AvdName $selectedTarget.AvdName
        $startedAvd = [pscustomobject]@{
            AvdName = $selectedTarget.AvdName
            ExistingEmulatorSerials = $existingEmulatorSerials
        }
    }
    default {
        throw "Unsupported target type '$($selectedTarget.Type)'."
    }
}

Build-DebugApkIfNeeded -ApkPath $apk

if (-not (Test-Path $apk)) {
    throw "APK not found at '$apk'."
}

if (-not $deviceSerial -and $startedAvd) {
    $deviceSerial = Wait-ForStartedAvd `
        -AvdName $startedAvd.AvdName `
        -ExistingEmulatorSerials $startedAvd.ExistingEmulatorSerials
}

if (-not $deviceSerial) {
    throw 'No launchable ADB device was selected.'
}

Install-Apk -Serial $deviceSerial -ApkPath $apk
Launch-App -Serial $deviceSerial

Write-Host 'Done!' -ForegroundColor Green
