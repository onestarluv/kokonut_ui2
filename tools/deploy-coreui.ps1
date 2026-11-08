<#
  CoreUi 빌드 → 서버 JAR 교체 → CoreUi.zip 생성 → Nexo external_packs 적용.
  서버가 켜져 있으면 아무것도 바꾸지 않고 종료한다.

  사용: deploy-coreui.cmd 더블클릭, 또는
        powershell -ExecutionPolicy Bypass -File tools\deploy-coreui.ps1 [-ServerDir D:\egoverse\home1] [-SkipBuild]
#>
param(
    [string]$ServerDir = 'D:\egoverse\home1',
    [string]$CoreDir = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
function Step($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }
function Fail($msg) { Write-Host "실패: $msg" -ForegroundColor Red; exit 1 }
function SameFile($a, $b) { (Get-FileHash $a).Hash -eq (Get-FileHash $b).Hash }

$plugins = Join-Path $ServerDir 'plugins'
$uiDir = Join-Path $CoreDir 'Kokonut_UI'
$builtJar = Join-Path $uiDir 'build\libs\Kokonut_UI-cbt-1.0.jar'
$serverJar = Join-Path $plugins 'Kokonut_UI-cbt-1.0.jar'
$legacyJar = Join-Path $plugins 'CoreUi-1.0.jar'
$dataDir = Join-Path $plugins 'Kokonut_UI'
if (-not (Test-Path $dataDir)) { $dataDir = Join-Path $plugins 'CoreUi' }
$packZip = Join-Path $dataDir 'Pack\CoreUi.zip'
$nexoZip = Join-Path $plugins 'Nexo\pack\external_packs\CoreUi.zip'

# 1. 서버가 꺼져 있는지 확인 (월드 session.lock을 잡고 있으면 실행 중)
Step '서버 상태 확인'
$level = 'world'
$props = Join-Path $ServerDir 'server.properties'
if (Test-Path $props) {
    $line = Get-Content $props | Where-Object { $_ -match '^level-name=' } | Select-Object -First 1
    if ($line) { $level = $line.Substring(11).Trim() }
}
$lock = Join-Path $ServerDir "$level\session.lock"
if (Test-Path $lock) {
    try { [System.IO.File]::Open($lock, 'Open', 'ReadWrite', 'None').Close() }
    catch { Fail "서버가 실행 중입니다($lock 사용 중). 서버를 끄고 다시 실행하세요." }
}
Write-Host '서버 꺼짐 확인'

# 2. 빌드
if (-not $SkipBuild) {
    Step 'CoreUi 빌드'
    Push-Location $CoreDir
    try { & .\gradlew.bat buildUi -x deployPlugins -q; if ($LASTEXITCODE -ne 0) { Fail '빌드 실패' } }
    finally { Pop-Location }
}
if (-not (Test-Path $builtJar)) { Fail "빌드 결과물이 없습니다: $builtJar" }

# 3. 백업
Step '백업'
$backup = Join-Path $ServerDir ("plugin-backups\CoreUi\" + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Force $backup | Out-Null
foreach ($f in @($serverJar, $legacyJar, $packZip, $nexoZip)) {
    if (Test-Path $f) { Copy-Item $f (Join-Path $backup ((Split-Path (Split-Path $f) -Leaf) + '_' + (Split-Path $f -Leaf))) }
}
Write-Host $backup

# 4. JAR 교체
Step 'JAR 교체'
if (Test-Path $legacyJar) {
    $resolvedLegacy = [IO.Path]::GetFullPath($legacyJar)
    if ([IO.Path]::GetDirectoryName($resolvedLegacy) -ne [IO.Path]::GetFullPath($plugins)) { Fail 'Unexpected legacy JAR path' }
    Move-Item -LiteralPath $resolvedLegacy -Destination (Join-Path $backup 'CoreUi-1.0.jar')
}
Copy-Item $builtJar $serverJar -Force
if (-not (SameFile $builtJar $serverJar)) { Fail 'JAR 복사 검증 실패' }
Write-Host "교체 완료 ($((Get-Item $builtJar).LastWriteTime))"

# 5. CoreUi.zip 생성 (Kokonut_HUD/BetterHud가 있으면 26.3 텍스트 셰이더는 HUD 쪽에 맡김)
Step 'CoreUi.zip 생성'
$external = [bool](Get-ChildItem $plugins -Filter '*.jar' | Where-Object { $_.Name -match '(?i)^(Kokonut[-_]HUD|BetterHud)' })
$cache = Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1'
$paperApi = Get-ChildItem (Join-Path $cache 'io.papermc.paper\paper-api') -Recurse -Filter 'paper-api-*.jar' |
    Where-Object { $_.Name -notmatch 'sources|javadoc' } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $paperApi) { Fail 'Gradle 캐시에서 paper-api JAR를 찾지 못했습니다.' }
$libs = Get-ChildItem (Join-Path $ServerDir 'libraries') -Recurse -Filter '*.jar' |
    Where-Object { $_.Name -match 'adventure|examination|snakeyaml|guava|gson|option|jspecify|annotations|bungeecord-chat|joml|fastutil|log4j-api|slf4j' }
$cp = (@($serverJar, $paperApi.FullName) + ($libs | ForEach-Object FullName)) -join ';'
& java -cp $cp (Join-Path $PSScriptRoot 'PackGen.java') $dataDir $serverJar $external.ToString().ToLower()
if ($LASTEXITCODE -ne 0) { Fail '팩 생성 실패' }

# 6. Nexo 적용
Step 'Nexo external_packs 적용'
Copy-Item $packZip $nexoZip -Force
if (-not (SameFile $packZip $nexoZip)) { Fail 'Nexo 복사 검증 실패' }

Write-Host "`n완료. 서버를 켜면 Nexo가 새 CoreUi 팩을 합쳐 업로드합니다." -ForegroundColor Green
