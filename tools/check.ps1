<#
.SYNOPSIS
  One entry point for every routine verification, with a short machine-readable summary.

.DESCRIPTION
  quick  JVM tests, debug build and lint. No device needed.
  ui     quick + instrumented suite with the gated UI tests enabled (disposable emulator).
  e2e    Real notification delivery through Android with synthetic emitter apps.
  seed   Installs the debug build and fills it with demo data (survives schema changes).
  shots  seed + screenshots of the four tabs at the given sizes and themes.
  release-smoke  Debug-signed minified release: install, launch, fail if the app crashes.
  all    ui + e2e.

  Only a disposable emulator whose AVD name starts with ApkPreview_ is ever touched.
  Display size, density, font scale and night mode are always restored on exit.

.EXAMPLE
  ./tools/check.ps1 quick
  ./tools/check.ps1 ui
  ./tools/check.ps1 ui -Class br.com.fluxo.app.PlanningUiTest
  ./tools/check.ps1 shots -Themes light,dark
  ./tools/check.ps1 shots -Avd ApkPreview_Phone_API37 -Serial emulator-5558 -Themes light,dark   # 1080x2424 phone
#>
param(
    [Parameter(Position = 0)][ValidateSet('quick', 'ui', 'e2e', 'seed', 'shots', 'release-smoke', 'all')][string]$Mode = 'quick',
    [string]$Serial = 'emulator-5556',
    [string]$Avd = 'ApkPreview_FluxoNotify_20261004',
    [string]$Class = '',
    [string[]]$Sizes = @('native'),
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
            # The old process keeps the AVD locked while it shuts down; starting too early exits silently.
            $gone = (Get-Date).AddSeconds(60)
            while (((& $adb devices) -match [regex]::Escape($Serial)) -and (Get-Date) -lt $gone) { Start-Sleep -Seconds 2 }
            Start-Sleep -Seconds 10
        }
        $booted = $false
        foreach ($attempt in 1..3) {
            Start-Process -FilePath (Join-Path $sdk 'emulator/emulator.exe') -WindowStyle Hidden -ArgumentList @(
                '-avd', $Avd, '-port', $port, '-no-snapshot-save', '-no-boot-anim', '-memory', '4096')
            $appear = (Get-Date).AddSeconds(60)
            while (-not ((& $adb devices) -match [regex]::Escape($Serial)) -and (Get-Date) -lt $appear) { Start-Sleep -Seconds 3 }
            if (-not ((& $adb devices) -match [regex]::Escape($Serial))) { continue }
            $deadline = (Get-Date).AddSeconds(240)
            while (-not (Test-DeviceHealthy) -and (Get-Date) -lt $deadline) { Start-Sleep -Seconds 5 }
            if (Test-DeviceHealthy) { $booted = $true; break }
        }
        if (-not $booted) { throw "Emulator $Avd did not boot after 3 attempts." }
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
    Invoke-Adb install -r (Join-Path $project 'app/build/outputs/apk/github/debug/app-github-debug.apk') | Out-Null
    Invoke-Adb install -r (Join-Path $project 'app/build/outputs/apk/androidTest/github/debug/app-github-debug-androidTest.apk') | Out-Null
    Invoke-Instrument @('-e', 'seedDemo', 'true', '-e', 'seedTheme', $Theme, '-e', 'class', "$package.SeedDemoDataTest")
}

function Wait-AppDrawn {
    # "am start -W" returns before the first Compose frame on a slow emulator; wait for focus, then settle.
    $deadline = (Get-Date).AddSeconds(40)
    while ((Get-Date) -lt $deadline) {
        if ((Invoke-Adb shell dumpsys window) -match "mCurrentFocus=.*$([regex]::Escape($package))/") { break }
        Start-Sleep -Milliseconds 500
    }
    Start-Sleep -Seconds 5
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
        $ok = Invoke-Gradle @(':core:test', ':app:assembleGithubDebug', ':app:assembleGithubDebugAndroidTest', ':app:lintGithubDebug', ':app:assemblePlayRelease') 'quick'
        $summary.jvm = Read-Counts (Join-Path $project 'core/build/test-results/test/*.xml')
        $lint = Join-Path $project 'app/build/reports/lint-results-githubDebug.xml'
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
        $arguments = @(':app:connectedGithubDebugAndroidTest', '-Pandroid.testInstrumentationRunnerArguments.disposableEmulator=true')
        if ($Class) { $arguments += "-Pandroid.testInstrumentationRunnerArguments.class=$Class" }
        $results = Join-Path $project 'app/build/outputs/androidTest-results/connected/debug/flavors/github'
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

    if ($Mode -eq 'release-smoke') {
        # Local smoke test only: signs the minified release with the debug key so `install -r` works over a debug build.
        $env:FLUXO_RELEASE_SIGN_WITH_DEBUG = '1'
        if (-not (Invoke-Gradle @(':app:assembleGithubRelease') 'release')) { throw 'release build failed' }
        $apk = Get-ChildItem (Join-Path $project 'app/build/outputs/apk/github/release') -Filter '*.apk' | Select-Object -First 1
        if (-not $apk) { throw 'release APK not found' }
        Invoke-Adb logcat -c | Out-Null
        Invoke-Adb install -r $apk.FullName | Out-Null
        Invoke-Adb shell am force-stop $package | Out-Null
        Invoke-Adb -Arguments @('shell', 'am', 'start', '-W', '-n', "$package/.MainActivity") | Out-Null
        Start-Sleep -Seconds 8
        $pidNow = (& $adb -s $Serial shell pidof $package 2>$null)
        $fatal = (& $adb -s $Serial logcat -d -b crash 2>$null) -match 'FATAL EXCEPTION'
        if (-not $pidNow -or $fatal) { throw 'release build crashed on launch' }
        $summary.release = "launched, pid $("$pidNow".Trim()), no crash"
    }

    if ($Mode -in 'seed', 'shots') {
        if (-not (Invoke-Gradle @(':app:assembleGithubDebug', ':app:assembleGithubDebugAndroidTest') 'build')) { throw 'build failed' }
        if ($Mode -eq 'seed') { Install-Seeded $Themes[0]; $summary.seed = "demo data installed ($($Themes[0]))" }
    }

    if ($Mode -eq 'shots') {
        if (-not $OutDir) { $OutDir = Join-Path $project ('.artifacts/shots/' + (Get-Date -Format 'yyyyMMdd-HHmmss')) }
        New-Item -ItemType Directory -Path $OutDir -Force | Out-Null
        $count = 0
        foreach ($size in $Sizes) {
            if ($size -eq 'native') {
                # Preferred: overriding the size has crashed system_server on these images. Use a second AVD for big screens.
                if ((Invoke-Adb shell wm size) -notmatch 'Physical size: (\d+)x(\d+)') { throw 'Could not read the display size.' }
                $w = [int]$Matches[1]; $h = [int]$Matches[2]
                if ((Invoke-Adb shell wm density) -notmatch 'Physical density: (\d+)') { throw 'Could not read the display density.' }
                $dpi = [int]$Matches[1]
                $size = "${w}x${h}"
            } else {
                if ($size -notmatch '^(\d+)x(\d+)@(\d+)$') { throw "Size must be 'native' or look like 1080x1920@420, got '$size'." }
                $w = [int]$Matches[1]; $h = [int]$Matches[2]; $dpi = [int]$Matches[3]
                Invoke-Adb shell wm size "${w}x${h}" | Out-Null
                Invoke-Adb shell wm density $dpi | Out-Null
            }
            foreach ($theme in $Themes) {
                try { Install-Seeded $theme } catch {
                    if (Test-DeviceHealthy) { throw }
                    Write-Host 'Device died while seeding; restarting and retrying once.'
                    Start-DisposableEmulator
                    Install-Seeded $theme
                }
                Invoke-Adb shell am force-stop $package | Out-Null
                Invoke-Adb -Arguments @('shell', 'am', 'start', '-W', '-n', "$package/.MainActivity") | Out-Null
                Wait-AppDrawn
                $navY = $h - [int](34 * $dpi / 160)
                $tabs = 'inicio', 'registros', 'planejar', 'ajustes'
                for ($i = 0; $i -lt 4; $i++) {
                    $x = [int]($w * (2 * $i + 1) / 8)
                    Invoke-Adb shell input tap $x $navY | Out-Null
                    Start-Sleep -Milliseconds 2500
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
