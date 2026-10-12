<#
.SYNOPSIS
  One entry point for every routine verification, with a short machine-readable summary.

.DESCRIPTION
  quick  JVM tests, debug build and lint. No device needed.
  ui     quick + instrumented suite with the gated UI tests enabled (disposable emulator).
  e2e    Real notification delivery through Android with synthetic emitter apps.
  seed   Installs the debug build and fills it with demo data (survives schema changes).
  shots  seed + screenshots of the four tabs at the given sizes and themes.
  all    ui + e2e.

  Only a disposable emulator whose AVD name starts with ApkPreview_ is ever touched.
  Display size, density, font scale and night mode are always restored on exit.

.EXAMPLE
  ./tools/check.ps1 quick
  ./tools/check.ps1 ui
  ./tools/check.ps1 ui -Class br.com.fluxo.app.PlanningUiTest
  ./tools/check.ps1 shots -Sizes 320x640@160,1080x1920@420 -Themes light,dark
#>
param(
    [Parameter(Position = 0)][ValidateSet('quick', 'ui', 'e2e', 'seed', 'shots', 'all')][string]$Mode = 'quick',
    [string]$Serial = 'emulator-5556',
    [string]$Avd = 'ApkPreview_FluxoNotify_20261004',
    [string]$Class = '',
    [string[]]$Sizes = @('320x640@160'),
    [string[]]$Themes = @('light'),
    [string]$OutDir = ''
)
$ErrorActionPreference = 'Stop'
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$adb = Join-Path $sdk 'platform-tools/adb.exe'
$package = 'br.com.fluxo.app'
$summary = [ordered]@{ mode = $Mode }

function Resolve-Jdk {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin/java.exe'))) { return }
    $candidates = @('C:\Program Files\Java', 'C:\Program Files\Eclipse Adoptium') |
        Where-Object { Test-Path $_ } |
        ForEach-Object { Get-ChildItem $_ -Directory -Filter 'jdk-21*' } |
        Sort-Object Name -Descending
    if (-not $candidates) { throw 'JDK 21 not found. Set JAVA_HOME to an installed JDK 21.' }
    $env:JAVA_HOME = $candidates[0].FullName
}

function Invoke-Gradle([string[]]$Arguments, [string]$LogName) {
    $log = Join-Path $project "build/check-$LogName.log"
    New-Item -ItemType Directory -Path (Split-Path $log) -Force | Out-Null
    & (Join-Path $project 'gradlew.bat') @Arguments --console=plain *> $log
    $ok = $LASTEXITCODE -eq 0
    if (-not $ok) {
        Get-Content $log | Select-String -Pattern ' FAILED|^e: |error:|AssertionError|What went wrong' -Context 0, 3 |
            Select-Object -First 25 | ForEach-Object { Write-Host $_ }
        Write-Host "Full log: $log"
    }
    return $ok
}

function Read-Counts([string]$Glob) {
    $total = 0; $failed = 0; $errors = 0; $skipped = 0
    Get-ChildItem $Glob -ErrorAction SilentlyContinue | ForEach-Object {
        $head = (Get-Content $_.FullName -TotalCount 3) -join "`n"
        $total += [int]([regex]::Match($head, 'tests="(\d+)"').Groups[1].Value)
        $failed += [int]([regex]::Match($head, 'failures="(\d+)"').Groups[1].Value)
        $errors += [int]([regex]::Match($head, 'errors="(\d+)"').Groups[1].Value)
        $skipped += [int]([regex]::Match($head, 'skipped="(\d+)"').Groups[1].Value)
    }
    return "$total tests, $failed failures, $errors errors, $skipped skipped"
}

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments)][string[]]$Arguments)
    $result = & $adb -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "adb failed ($($Arguments -join ' ')): $result" }
    return ($result -join "`n")
}

function Test-DeviceHealthy {
    try {
        if ((& $adb -s $Serial shell getprop sys.boot_completed 2>$null) -notmatch '1') { return $false }
        # A wedged system_server answers getprop but has no window service.
        return ((& $adb -s $Serial shell wm size 2>&1) -match 'Physical size')
    } catch { return $false }
}

function Start-DisposableEmulator {
    if ($Serial -notmatch '^emulator-(\d+)$') { throw 'Only emulators are allowed.' }
    $port = $Matches[1]
    if (-not (Test-DeviceHealthy)) {
        if ((& $adb devices) -match [regex]::Escape($Serial)) {
            Write-Host "Emulator $Serial is unhealthy; restarting it."
            & $adb -s $Serial emu kill 2>$null | Out-Null
            Start-Sleep -Seconds 8
        }
        Start-Process -FilePath (Join-Path $sdk 'emulator/emulator.exe') -WindowStyle Hidden -ArgumentList @(
            '-avd', $Avd, '-port', $port, '-no-snapshot-save', '-no-boot-anim', '-memory', '4096')
        $deadline = (Get-Date).AddSeconds(240)
        while (-not (Test-DeviceHealthy)) {
            if ((Get-Date) -gt $deadline) { throw "Emulator $Avd did not boot within 240 seconds." }
            Start-Sleep -Seconds 5
        }
    }
    if ((Invoke-Adb shell getprop ro.kernel.qemu).Trim() -ne '1') { throw 'QEMU required.' }
    $name = ((Invoke-Adb emu avd name) -split "`n")[0].Trim()
    if ($name -notmatch '^ApkPreview_') { throw "Refusing to use AVD '$name': a disposable ApkPreview_ AVD is required." }
}

function Restore-Display {
    foreach ($command in @('wm size reset', 'wm density reset', 'settings put system font_scale 1.0', 'cmd uimode night no')) {
        try { & $adb -s $Serial shell $command 2>$null | Out-Null } catch { }
    }
}

function Invoke-Instrument([string[]]$Extras) {
    $arguments = @('shell', 'am', 'instrument', '-w', '-r') + $Extras +
        @("$package.test/androidx.test.runner.AndroidJUnitRunner")
    $output = Invoke-Adb -Arguments $arguments
    if ($output -notmatch 'OK \(\d+ tests?\)' -or $output -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed') {
        Write-Host $output
        throw 'Instrumentation did not pass.'
    }
}

function Install-Seeded([string]$Theme) {
    Invoke-Adb install -r (Join-Path $project 'app/build/outputs/apk/debug/app-debug.apk') | Out-Null
    Invoke-Adb install -r (Join-Path $project 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk') | Out-Null
    Invoke-Instrument @('-e', 'seedDemo', 'true', '-e', 'seedTheme', $Theme, '-e', 'class', "$package.SeedDemoDataTest")
}

function Save-Screenshot([string]$Path) {
    # Binary-safe: PowerShell redirection would corrupt the PNG stream.
    cmd /c "`"$adb`" -s $Serial exec-out screencap -p > `"$Path`""
    if (-not (Test-Path $Path) -or (Get-Item $Path).Length -lt 2000) { throw "Screenshot failed: $Path" }
}

Push-Location $project
$exit = 0
try {
    Resolve-Jdk
    $needsDevice = $Mode -ne 'quick'

    if ($Mode -in 'quick', 'ui', 'all') {
        $ok = Invoke-Gradle @(':core:test', ':app:assembleDebug', ':app:assembleDebugAndroidTest', ':app:lintDebug') 'quick'
        $summary.jvm = Read-Counts (Join-Path $project 'core/build/test-results/test/*.xml')
        $lint = Join-Path $project 'app/build/reports/lint-results-debug.xml'
        if (Test-Path $lint) {
            $text = Get-Content $lint -Raw
            $summary.lint = "$(([regex]::Matches($text, 'severity="(Error|Fatal)"')).Count) errors, $(([regex]::Matches($text, 'severity="Warning"')).Count) warnings"
        }
        $summary.build = if ($ok) { 'ok' } else { 'FAILED' }
        if (-not $ok) { throw 'quick checks failed' }
    }

    if ($needsDevice) { Start-DisposableEmulator }

    if ($Mode -in 'ui', 'all') {
        $env:ANDROID_SERIAL = $Serial
        $arguments = @(':app:connectedDebugAndroidTest', '-Pandroid.testInstrumentationRunnerArguments.disposableEmulator=true')
        if ($Class) { $arguments += "-Pandroid.testInstrumentationRunnerArguments.class=$Class" }
        $results = Join-Path $project 'app/build/outputs/androidTest-results/connected/debug'
        $ok = Invoke-Gradle $arguments 'ui'
        if (-not $ok -and -not (Test-DeviceHealthy)) {
            Write-Host 'Device died during the run; restarting and retrying once.'
            Start-DisposableEmulator
            $ok = Invoke-Gradle $arguments 'ui'
        }
        $summary.android = Read-Counts (Join-Path $results '*.xml')
        if (-not $ok) { throw 'instrumented tests failed' }
    }

    if ($Mode -in 'e2e', 'all') {
        $log = Join-Path $project 'build/check-e2e.log'
        & (Join-Path $PSScriptRoot 'notification-e2e/run.ps1') -Serial $Serial -DisposableEmulator *> $log
        $text = Get-Content $log -Raw
        $delivery = [regex]::Match($text, 'OK \((\d+) tests\)').Value
        $summary.e2e = if ($LASTEXITCODE -eq 0 -and $delivery) { "$delivery delivery, restart phases ok" } else { 'FAILED' }
        if ($summary.e2e -eq 'FAILED') { Write-Host "Full log: $log"; throw 'notification e2e failed' }
    }

    if ($Mode -in 'seed', 'shots') {
        if (-not (Invoke-Gradle @(':app:assembleDebug', ':app:assembleDebugAndroidTest') 'build')) { throw 'build failed' }
        if ($Mode -eq 'seed') { Install-Seeded $Themes[0]; $summary.seed = "demo data installed ($($Themes[0]))" }
    }

    if ($Mode -eq 'shots') {
        if (-not $OutDir) { $OutDir = Join-Path $project ('.artifacts/shots/' + (Get-Date -Format 'yyyyMMdd-HHmmss')) }
        New-Item -ItemType Directory -Path $OutDir -Force | Out-Null
        $count = 0
        foreach ($size in $Sizes) {
            if ($size -notmatch '^(\d+)x(\d+)@(\d+)$') { throw "Size must look like 1080x1920@420, got '$size'." }
            $w = [int]$Matches[1]; $h = [int]$Matches[2]; $dpi = [int]$Matches[3]
            Invoke-Adb shell wm size "${w}x${h}" | Out-Null
            Invoke-Adb shell wm density $dpi | Out-Null
            foreach ($theme in $Themes) {
                Install-Seeded $theme
                Invoke-Adb shell am force-stop $package | Out-Null
                Invoke-Adb shell am start -n "$package/.MainActivity" | Out-Null
                Start-Sleep -Seconds 4
                $navY = $h - [int](34 * $dpi / 160)
                $tabs = 'inicio', 'registros', 'planejar', 'ajustes'
                for ($i = 0; $i -lt 4; $i++) {
                    $x = [int]($w * (2 * $i + 1) / 8)
                    Invoke-Adb shell input tap $x $navY | Out-Null
                    Start-Sleep -Milliseconds 1200
                    Save-Screenshot (Join-Path $OutDir "$($tabs[$i])-$size-$theme.png"); $count++
                    Invoke-Adb shell input swipe ([int]($w / 2)) ([int]($h * 0.75)) ([int]($w / 2)) ([int]($h * 0.25)) 300 | Out-Null
                    Start-Sleep -Milliseconds 900
                    Save-Screenshot (Join-Path $OutDir "$($tabs[$i])-$size-$theme-2.png"); $count++
                }
            }
        }
        $summary.shots = "$count screenshots in $OutDir"
    }
} catch {
    $summary.error = $_.Exception.Message
    $exit = 1
} finally {
    if ($Mode -ne 'quick') { Restore-Display }
    Pop-Location
}

Write-Host ''
Write-Host '== check summary =='
$summary.GetEnumerator() | ForEach-Object { Write-Host ("{0,-8} {1}" -f $_.Key, $_.Value) }
exit $exit
