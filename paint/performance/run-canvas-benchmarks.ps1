param(
    [Parameter(Mandatory)][ValidateSet('before','candidate','after')][string]$Stage,
    [string]$Serial = 'emulator-5556'
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$adb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
if ($Serial -notmatch '^emulator-\d+$') { throw 'Only a local emulator may run these tests.' }
$avd = & $adb -s $Serial emu avd name
if ($LASTEXITCODE -ne 0 -or $avd[0].Trim() -ne 'Small_Desktop') { throw 'Use the local Small Desktop emulator.' }
$androidUser = (& $adb -s $Serial shell am get-current-user).Trim()
$saved = Join-Path $repo "paint/build/canvas-benchmarks/$Stage"
New-Item -ItemType Directory -Force -Path $saved | Out-Null
$app = Join-Path $repo 'paint/build/outputs/apk/debug/paint-debug.apk'
$test = Join-Path $repo 'paint/build/outputs/apk/androidTest/debug/paint-debug-androidTest.apk'
$harness = Join-Path $repo 'paint/src/androidTest/java/com/kkps/kdesktopsuite/paint/TransformPerformanceTest.kt'
if (!(Test-Path -LiteralPath (Join-Path $saved 'app.apk'))) {
    Copy-Item -LiteralPath $app -Destination (Join-Path $saved 'app.apk')
    Copy-Item -LiteralPath $test -Destination (Join-Path $saved 'test.apk')
    Copy-Item -LiteralPath $harness -Destination $saved
    Copy-Item -LiteralPath (Join-Path $repo 'paint/src') -Destination (Join-Path $saved 'src') -Recurse
    git -C $repo diff --binary | Set-Content -Encoding utf8 (Join-Path $saved 'source.diff')
}
$app = Join-Path $saved 'app.apk'
$test = Join-Path $saved 'test.apk'
$harness = Join-Path $saved 'TransformPerformanceTest.kt'
$revision = (git -C $repo rev-parse HEAD).Trim()
$metadata = [ordered]@{
    stage = $Stage; revision = $revision; measuredAtUtc = [DateTime]::UtcNow.ToString('o')
    appSha256 = (Get-FileHash -LiteralPath $app -Algorithm SHA256).Hash
    testSha256 = (Get-FileHash -LiteralPath $test -Algorithm SHA256).Hash
    harnessSha256 = (Get-FileHash -LiteralPath $harness -Algorithm SHA256).Hash
    serial = $Serial; avd = 'Small_Desktop'; androidUser = $androidUser
    display = (& $adb -s $Serial shell wm size) -join "`n"
    density = (& $adb -s $Serial shell wm density) -join "`n"
}
& $adb -s $Serial install -r -t --user $androidUser $app
if ($LASTEXITCODE -ne 0) { throw 'App installation failed.' }
& $adb -s $Serial install -r -t --user $androidUser $test
if ($LASTEXITCODE -ne 0) { throw 'Test installation failed.' }
$runs = @()
foreach ($run in 1..3) {
    $label = "canvas-$Stage-$run"
    Write-Output "Starting $label"
    $log = Join-Path $saved "$label.log"
    if (!(Test-Path -LiteralPath $log) -or !(Select-String -LiteralPath $log -Pattern 'OK \(2 tests\)' -Quiet)) {
        & $adb -s $Serial shell am instrument --user $androidUser -w -e class com.kkps.kdesktopsuite.paint.TransformPerformanceTest -e performanceStage $label com.kkps.kdesktopsuite.paint.test/androidx.test.runner.AndroidJUnitRunner > $log
    }
    if (!(Select-String -LiteralPath $log -Pattern 'OK \(2 tests\)' -Quiet)) {
        Get-Content -LiteralPath $log -Tail 30
        throw "Benchmark failed: $label"
    }
    # Other chats may build/install the same app; never accept a run against a replacement APK.
    $installedPath = (& $adb -s $Serial shell pm path --user $androidUser com.kkps.kdesktopsuite.paint | Select-Object -First 1) -replace '^package:',''
    if (!$installedPath) { throw 'The benchmark app was removed during measurement.' }
    $installedHash = (& $adb -s $Serial shell sha256sum $installedPath.Trim()) -split '\s+' | Select-Object -First 1
    if ($installedHash -ne $metadata.appSha256) { throw 'The installed APK changed during measurement; discard this run.' }
    $entry = [ordered]@{ run = $run }
    foreach ($method in @('rasterTimings','livePreviewAndApplyTimings')) {
        $json = & $adb -s $Serial exec-out run-as com.kkps.kdesktopsuite.paint --user $androidUser cat "cache/transform-performance-$label-$method.json"
        if ($LASTEXITCODE -ne 0) { throw "Cannot read $method report." }
        $entry[$method] = ($json -join "`n") | ConvertFrom-Json
    }
    $runs += $entry
    $report = [ordered]@{ metadata = $metadata; runs = $runs }
    $report | ConvertTo-Json -Depth 100 | Set-Content -Encoding utf8 (Join-Path $PSScriptRoot "canvas-$Stage.json")
    Write-Output "Finished $label"
}
