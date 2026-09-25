param(
    [string]$Serial = 'emulator-5554',
    [Parameter(Mandatory)][switch]$DisposableEmulator
)
$ErrorActionPreference = 'Stop'
if (-not $DisposableEmulator) { throw 'Explicit disposable-emulator acknowledgement required.' }
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$adb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments)][string[]]$Arguments)
    $result = & $adb -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $result" }
    return ($result -join "`n")
}
if ($Serial -notmatch '^emulator-[0-9]+$') { throw 'Physical devices forbidden.' }
if ((Invoke-Adb shell getprop ro.kernel.qemu).Trim() -ne '1') { throw 'QEMU required.' }
$avdName = ((Invoke-Adb emu avd name) -split "`n")[0].Trim()
if ($avdName -notmatch '^ApkPreview_[A-Za-z0-9_.-]+$') { throw 'Dedicated ApkPreview_ AVD required.' }
$fixtures = [ordered]@{ nubank='com.nu.production'; c6='com.c6bank.app'; itau='com.itau'; unsupported='br.com.fluxo.fixture.unsupported' }
$packages = Invoke-Adb shell pm list packages
foreach ($pkg in $fixtures.Values) {
    if (($packages -split "`n").Trim() -contains "package:$pkg") {
        throw "Package $pkg already installed. Refusing to replace or uninstall an existing app."
    }
}
if (-not (Test-Path (Join-Path $env:JAVA_HOME 'bin/java.exe'))) { throw 'Set JAVA_HOME to installed JDK 21.' }
$installed = [Collections.Generic.List[string]]::new()
$listener = 'br.com.fluxo.app/.BankNotificationListener'
$wasAllowed = (Invoke-Adb shell settings get secure enabled_notification_listeners) -match 'br\.com\.fluxo\.app/(\.BankNotificationListener|br\.com\.fluxo\.app\.BankNotificationListener)'
$instrumented = $false
$restartStarted = $false
function Invoke-RestartPhase([string]$Phase) {
    $output = Invoke-Adb -Arguments @('shell', 'am', 'instrument', '-w', '-r', '-e', 'notificationE2e', 'true', '-e', 'restartPhase', $Phase, '-e', 'class', 'br.com.fluxo.app.NotificationRestartE2eTest', 'br.com.fluxo.app.test/androidx.test.runner.AndroidJUnitRunner')
    $output | Out-File -LiteralPath (Join-Path $evidence ((Get-Date -Format 'yyyyMMdd-HHmmss') + "-restart-$Phase.txt")) -Encoding utf8
    $output
    if ($output -notmatch 'OK \(1 test\)' -or $output -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed') {
        throw "Notification restart phase failed: $Phase"
    }
}
Push-Location $project
try {
    & ./gradlew.bat '-PnotificationE2e=true' :app:assembleDebug :app:assembleDebugAndroidTest :notification-fixture:assembleDebug
    if ($LASTEXITCODE -ne 0) { throw 'E2E build failed.' }
    Invoke-Adb install -r (Join-Path $project 'app/build/outputs/apk/debug/app-debug.apk')
    Invoke-Adb install -r (Join-Path $project 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')
    foreach ($entry in $fixtures.GetEnumerator()) {
        $apk = Join-Path $project "notification-fixture/build/outputs/apk/$($entry.Key)/debug/notification-fixture-$($entry.Key)-debug.apk"
        Invoke-Adb install $apk
        $installed.Add($entry.Value)
        if ([int](Invoke-Adb shell getprop ro.build.version.sdk) -ge 33) {
            Invoke-Adb shell pm grant $entry.Value android.permission.POST_NOTIFICATIONS
        }
    }
    $instrumented = $true
    $result = Invoke-Adb -Arguments @('shell', 'am', 'instrument', '-w', '-r', '-e', 'notificationE2e', 'true', '-e', 'class', 'br.com.fluxo.app.NotificationDeliveryE2eTest', 'br.com.fluxo.app.test/androidx.test.runner.AndroidJUnitRunner')
    $result
    $evidence = Join-Path $project '.artifacts/fluxo-preview/notification-e2e'
    New-Item -ItemType Directory -Path $evidence -Force | Out-Null
    $result | Out-File -LiteralPath (Join-Path $evidence ((Get-Date -Format 'yyyyMMdd-HHmmss') + '.txt')) -Encoding utf8
    if ($result -notmatch 'OK \(6 tests\)' -or $result -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed') {
        throw 'Notification delivery tests did not all pass.'
    }
    $restartStarted = $true
    Invoke-RestartPhase 'seed'
    $beforePid = (& $adb -s $Serial shell pidof br.com.fluxo.app 2>$null)
    Invoke-Adb shell am force-stop br.com.fluxo.app
    $stopDeadline = (Get-Date).AddSeconds(5)
    do {
        $remainingPid = (& $adb -s $Serial shell pidof br.com.fluxo.app 2>$null)
        if (-not $remainingPid -or $remainingPid -ne $beforePid) { break }
        Start-Sleep -Milliseconds 100
    } while ((Get-Date) -lt $stopDeadline)
    if ($beforePid -and $remainingPid -eq $beforePid) { throw 'Original Fluxo process still alive after force-stop.' }
    "before=$beforePid after=$remainingPid" | Out-File -LiteralPath (Join-Path $evidence ((Get-Date -Format 'yyyyMMdd-HHmmss') + '-restart-pids.txt')) -Encoding utf8
    Invoke-RestartPhase 'verify'
} finally {
    $cleanupFailures = [Collections.Generic.List[string]]::new()
    if ($restartStarted) {
        try { Invoke-RestartPhase 'cleanup' } catch { $cleanupFailures.Add('Restart fixture cleanup failed.') }
    }
    if ($instrumented) {
        $operation = if ($wasAllowed) { 'allow_listener' } else { 'disallow_listener' }
        try { Invoke-Adb shell cmd notification $operation $listener } catch { $cleanupFailures.Add('Listener permission restoration failed.') }
    }
    foreach ($pkg in $installed) {
        # Only packages absent at preflight and successfully installed by this invocation.
        try { Invoke-Adb uninstall $pkg } catch { $cleanupFailures.Add("Fixture cleanup failed for $pkg") }
    }
    Pop-Location
    if ($cleanupFailures.Count -gt 0) { throw ($cleanupFailures -join ' ') }
}
