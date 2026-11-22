<# :
@echo off
rem cmd runs only these lines; PowerShell sees them as a block comment.
rem Save this file as UTF-8 WITHOUT BOM (a BOM breaks the first cmd line).
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$WizardPath='%~f0'; $WizardArgs='%*'; iex ([IO.File]::ReadAllText($WizardPath, [Text.Encoding]::UTF8))"
set "rc=%errorlevel%"
if not defined WP_NOPAUSE pause
exit /b %rc%
#>
#
# A wizard walks a human through a manual procedure, step by step.
# Windows .bat / PowerShell hybrid of the /wizard skill's template.sh:
# double-click it, or run it from any terminal.
#
# Everything above the "STAGES" marker is the wizard library: do not hand-edit
# it. Author the per-step stages below the marker.

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$OutputEncoding = [Text.Encoding]::UTF8
$ScriptDir = Split-Path -Parent $WizardPath

# ──────────────────────────────────────────────────────────────────────────
# Wizard library: consistent UX, identical across every wizard.
# ──────────────────────────────────────────────────────────────────────────

$TotalStages = 0
$script:StageIndex = 0
$script:Written = @()  # names written this run (user env vars, .env keys)
$script:Skipped = @()  # things we couldn't do
$EnvFile = if ($env:ENV_FILE) { $env:ENV_FILE } else { '.env' }

# Banner "Title" shows the opening frame: what this wizard does.
function Banner([string]$Title) {
  Clear-Host
  Write-Host "`n  $Title" -ForegroundColor Cyan
  Write-Host "  $TotalStages stages`n" -ForegroundColor DarkGray
  Write-Host '  You drive the browser; this wizard tells you exactly what to do and' -ForegroundColor DarkGray
  Write-Host '  captures the values you copy back. Stop any time with Ctrl-C and re-run' -ForegroundColor DarkGray
  Write-Host '  later, since it remembers values already saved.' -ForegroundColor DarkGray
  Wait-Enter 'Ready to start?'
}

# Stage "Name" clears the screen, then announces a stage and shows progress.
function Stage([string]$Name) {
  Clear-Host
  $script:StageIndex++
  Write-Host "`n▸ Stage $script:StageIndex/$TotalStages · $Name" -ForegroundColor Cyan
}

function Say([string]$Text)  { Write-Host "  $Text" }
function Step([string]$Text) { Write-Host '  • ' -ForegroundColor Cyan -NoNewline; Write-Host $Text }
function Note([string]$Text) { Write-Host "  $Text" -ForegroundColor DarkGray }
function Warn([string]$Text) { Write-Host "  ⚠ $Text" -ForegroundColor Yellow }

# Open-Url URL shows the link and opens it in the default browser only on y
# (user rule 2026-10-09: never open a link without asking).
function Open-Url([string]$Url) {
  Write-Host '  ↗ ' -ForegroundColor Green -NoNewline; Write-Host $Url
  if ((Read-Host '  ? 브라우저로 열까요? [y/n]') -notmatch '^[Yy]') { Note '열지 않았습니다. 필요하면 위 링크를 직접 여세요.'; return }
  try { Start-Process $Url } catch { Warn "couldn't open a browser, so visit it manually: $Url" }
}

# Wait-Enter "msg" waits for the human to confirm they've done the manual part.
function Wait-Enter([string]$Message = 'Press Enter to continue') { [void](Read-Host "  $Message") }

# Confirm-Yes "question" is a y/N gate; returns $true on yes.
function Confirm-Yes([string]$Question) { (Read-Host "  ? $Question [y/N]") -match '^[Yy]' }

# _Existing NAME: the saved value, from the Windows user env or else $EnvFile.
function _Existing([string]$Name) {
  $v = [Environment]::GetEnvironmentVariable($Name, 'User')
  if ($v) { return $v }
  if (Test-Path $EnvFile) {
    $line = Get-Content $EnvFile -Encoding UTF8 | Where-Object { $_ -like "$Name=*" } | Select-Object -Last 1
    if ($line) { return $line.Substring($Name.Length + 1) }
  }
  return ''
}

# Ask NAME "Prompt" returns a typed value; Enter keeps the saved one on re-runs.
function Ask([string]$Name, [string]$Prompt) {
  $current = _Existing $Name
  $hint = if ($current) { ' [Enter keeps current]' } else { '' }
  $v = Read-Host "  $Prompt$hint"
  if (-not $v) { $v = $current }
  return $v
}

# Ask-Secret NAME "Prompt" is like Ask, but input is hidden.
function Ask-Secret([string]$Name, [string]$Prompt) {
  $current = _Existing $Name
  $hint = if ($current) { ' [Enter keeps current]' } else { '' }
  $secure = Read-Host "  $Prompt$hint" -AsSecureString
  $v = [pscredential]::new('x', $secure).GetNetworkCredential().Password
  if (-not $v) { $v = $current }
  return $v
}

# Set-UserEnv NAME VALUE persists a Windows *user* env var (seen by new
# terminals/processes) and sets it in this process too, so later stages see it.
function Set-UserEnv([string]$Name, [string]$Value) {
  [Environment]::SetEnvironmentVariable($Name, $Value, 'User')
  Set-Item -Path "env:$Name" -Value $Value
  $script:Written += $Name
  Write-Host '  ✓ set ' -ForegroundColor Green -NoNewline
  Write-Host "Windows user env var $Name (new terminals see it)"
}

# Write-Env NAME VALUE upserts NAME=VALUE into $EnvFile (UTF-8, no BOM). Idempotent.
function Write-Env([string]$Name, [string]$Value) {
  $lines = @()
  if (Test-Path $EnvFile) { $lines = @(Get-Content $EnvFile -Encoding UTF8 | Where-Object { $_ -notlike "$Name=*" }) }
  $lines += "$Name=$Value"
  [IO.File]::WriteAllLines((Join-Path (Get-Location) $EnvFile), [string[]]$lines)
  $script:Written += $Name
  Write-Host '  ✓ wrote ' -ForegroundColor Green -NoNewline; Write-Host "$Name → $EnvFile"
}

# Finish clears, then shows a closing summary of everything configured.
function Finish {
  Clear-Host
  Write-Host "`n  ✓ Setup complete" -ForegroundColor Green
  if ($script:Written.Count) { Note "wrote $($script:Written.Count) value(s): $($script:Written -join ' ')" }
  if ($script:Skipped.Count) {
    Write-Host ''; Warn 'still to do by hand:'
    foreach ($s in $script:Skipped) { Note "  - $s" }
  }
  Write-Host ''
}


# ──────────────────────────────────────────────────────────────────────────
# STAGES: 어디댔어 v3 cutover (spec-v3-groups.md section 9, plan Phase 5 / Task P1).
# Claude runs this and relays each gate to the operator in chat; every gate also reads piped answers.
#   wizard-parking-v3-cutover.bat --print            list the commands per stage, run nothing
#   wizard-parking-v3-cutover.bat                    stages 0 2 3 4 5 6 7 in order (R is never part of a full run)
#   wizard-parking-v3-cutover.bat --stage 4          one stage only (0 2 3 4 5 6 7 R); state carries over in scripts\logs
# ──────────────────────────────────────────────────────────────────────────

# Piped runs and log capture: no screen clearing (the library calls Clear-Host; this function wins over the cmdlet).
function Clear-Host { }

# MAINT_SHA: 74eea7e0 "v3 schema, store, group routes and maintenance gate (W1-W3)". The gate and the v3 routes landed in
# the same commit; the gate answers 503 on every /v1 /v2 /v3 route while MAINTENANCE=1, and /v2 there is still the old
# family code on the old tables, so this commit is the maintenance build.
$MaintSha = '74eea7e03c1d13f18abf33ad0ca35b810cfd21fc'
$Repo = 'C:\DC\DD\dot-lab'
$WorkerRel = 'works/2026-11-02-parking-floor/worker'
$Worker = Join-Path $Repo ($WorkerRel -replace '/', '\')
$Base = 'https://dot-lab-parking.dotconnects.workers.dev'
$Db = 'dot-lab-parking'
$Scratch = if ($env:WP_SCRATCH) { $env:WP_SCRATCH } else { Join-Path $env:TEMP 'wp-cutover' }
$MaintTree = Join-Path $Scratch 'wp-maint'
$MaintWorker = Join-Path $MaintTree ($WorkerRel -replace '/', '\')
$KeyFile = Join-Path $HOME '.wherepark\smoke-key'
$LogDir = Join-Path $ScriptDir 'logs'
$StateFile = Join-Path $LogDir 'parking-v3-cutover.state.json'
$LogFile = Join-Path $LogDir ("parking-v3-cutover-{0:yyyyMMdd-HHmmss}.log" -f (Get-Date))

# What each stage runs; shown by --print and again at the top of the stage.
$Plan = [ordered]@{
  '0' = @('npx wrangler@4 whoami',
          'new SMOKE_KEY -> %USERPROFILE%\.wherepark\smoke-key (kept if the file exists)',
          '<SMOKE_KEY> | npx wrangler@4 secret put SMOKE_KEY')
  '2' = @('git worktree add --detach <SCRATCH>\wp-maint <MAINT_SHA>',
          '"1" | npx wrangler@4 secret put MAINTENANCE',
          '(in the worktree worker\) npx wrangler@4 deploy',
          'check: GET /v2/state -> 503 and GET /j -> 200',
          'python tools\smoke_v3.py seed   (env WP_SMOKE_KEY)')
  '3' = @('npx wrangler@4 d1 time-travel info dot-lab-parking   (bookmark -> log + state file)',
          'npx wrangler@4 d1 export dot-lab-parking --remote --output <SCRATCH>\parking-before-v3.sql')
  '4' = @('python tools\migrate_v3.py --remote --dry-run   (operator reads owners / re-keys)',
          'python tools\migrate_v3.py --remote   (re-run if it stops)')
  '5' = @('V3_SHA = git rev-parse HEAD   (worker\ must be clean, MAINT_SHA must be an ancestor)',
          'npx wrangler@4 deploy   (MAINTENANCE stays 1)',
          'python tools\smoke_v3.py check   (env WP_SMOKE_KEY; all PASS)')
  '6' = @('"0" | npx wrangler@4 secret put MAINTENANCE',
          'check: GET /v3/state without auth -> 401 (gate lifted)',
          'operator opens the app on each phone; a park syncs')
  '7' = @('send the v3 APK (Claude sends it with SendUserFile); operator installs it over the old app',
          'offer: git worktree remove <SCRATCH>\wp-maint')
  'R' = @('before stage 6 only',
          'npx wrangler@4 d1 time-travel restore dot-lab-parking --bookmark=<stage 3 bookmark>',
          '(in the worktree worker\) npx wrangler@4 deploy   (MAINT_SHA)',
          '"0" | npx wrangler@4 secret put MAINTENANCE',
          'print the spec section 9 rollback notes')
}

$ArgList = @(("$WizardArgs" -split '\s+') | Where-Object { $_ })
if ($ArgList -contains '--print') {
  Write-Host "`n  어디댔어 v3 cutover: commands per stage (nothing runs)" -ForegroundColor Cyan
  Write-Host "  MAINT_SHA = $MaintSha"
  Write-Host '  V3_SHA    = git rev-parse HEAD (resolved at run time, stage 5)'
  foreach ($k in $Plan.Keys) {
    Write-Host "`n  Stage $k" -ForegroundColor Cyan
    foreach ($line in $Plan[$k]) { Write-Host "    $line" }
  }
  Write-Host ''
  exit 0
}
$Only = $null
$si = [Array]::IndexOf($ArgList, '--stage')
if ($si -ge 0 -and $si + 1 -lt $ArgList.Count) { $Only = $ArgList[$si + 1].ToUpper() }
if ($Only -and -not $Plan.Contains($Only)) { Write-Host "  unknown stage '$Only' (0 2 3 4 5 6 7 R)"; exit 2 }

New-Item -ItemType Directory -Force -Path $LogDir, $Scratch | Out-Null
function Log([string]$Text) { [IO.File]::AppendAllText($LogFile, ("{0:HH:mm:ss} {1}`r`n" -f (Get-Date), $Text), (New-Object Text.UTF8Encoding $false)) }

# state carried between stages (a bookmark, whether stage 6 ran): no secrets in it
function Get-State { if (Test-Path $StateFile) { Get-Content $StateFile -Raw -Encoding UTF8 | ConvertFrom-Json } else { [pscustomobject]@{} } }
function Set-State([string]$Name, $Value) {
  $s = Get-State
  $s | Add-Member -NotePropertyName $Name -NotePropertyValue $Value -Force
  [IO.File]::WriteAllText($StateFile, ($s | ConvertTo-Json), (New-Object Text.UTF8Encoding $false))
}

# Gate "question": y/n, also from piped input. EOF or anything but y is a no.
function Gate([string]$Question) {
  try { $a = Read-Host "  ? $Question [y/n]" } catch { $a = $null }
  Log "gate: $Question -> $a"
  return ("$a" -match '^[Yy]')
}
function Stop-Wizard([string]$Why) {
  Log "stopped: $Why"; Warn "중단: $Why"; Note "로그: $LogFile"; exit 1
}
# Must-Gate: a no stops the wizard, so a later stage never runs on a failed earlier one
function Must-Gate([string]$Question) { if (-not (Gate $Question)) { Stop-Wizard $Question } }

# Run "label" {block} [stdin]: streams and logs the output, returns the exit code. Native stderr is not an error here.
# Blocks read the caller's variables directly (dynamic scope), so they are not given parameters.
function Run([string]$Label, [scriptblock]$Cmd, [string]$In = $null) {
  Step $Label; Log "run: $Label"
  $global:LASTEXITCODE = 0
  $prev = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
  $lines = @()
  try {
    $emit = { process { $s = "$_"; Write-Host "      $s" -ForegroundColor DarkGray; Log "  $s"; $s } }
    if ($null -ne $In) { $lines = @($In | & $Cmd 2>&1 | & $emit) } else { $lines = @(& $Cmd 2>&1 | & $emit) }
  } finally { $ErrorActionPreference = $prev }
  $script:LastOut = $lines -join "`n"
  Log "exit $LASTEXITCODE"
  return $LASTEXITCODE
}

function Http-Status([string]$Url) {
  try { return [int](Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 20).StatusCode }
  catch { if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode } else { return 0 } }
}
function Expect-Status([string]$Url, [int]$Want) {
  $got = Http-Status $Url
  Log "http $Url -> $got (want $Want)"
  if ($got -eq $Want) { Write-Host "  ✓ $Url = $got" -ForegroundColor Green } else { Warn "$Url = $got (기대값 $Want)" }
  return ($got -eq $Want)
}
function Show-Plan([string]$Key) { foreach ($line in $Plan[$Key]) { Note "  $line" } }
function Load-Key { if (Test-Path $KeyFile) { (Get-Content $KeyFile -Raw -Encoding UTF8).Trim() } else { '' } }
function Put-Secret([string]$Name, [string]$Value, [string]$Where) {
  # PowerShell 5.1 pipes to native programs with a BOM unless told otherwise
  $OutputEncoding = New-Object System.Text.UTF8Encoding $false
  Push-Location $Where
  try { return (Run "secret put $Name" { $input | npx --yes wrangler@4 secret put $Name } $Value) } finally { Pop-Location }
}
function Deploy([string]$Where, [string]$What) {
  Push-Location $Where
  try { return (Run "wrangler deploy ($What)" { npx --yes wrangler@4 deploy }) } finally { Pop-Location }
}
function Worker-Dirty { @(git -C $Repo status --porcelain -- $WorkerRel).Count -gt 0 }
function Ensure-MaintTree {
  if (-not (Test-Path $MaintTree)) {
    $rc = Run "git worktree add --detach $MaintTree" { git -C $Repo worktree add --detach $MaintTree $MaintSha }
    if ($rc -ne 0) { Stop-Wizard 'worktree 만들기 실패' }
  }
  $head = (git -C $MaintTree rev-parse HEAD).Trim()
  if ($head -ne $MaintSha) { Stop-Wizard "worktree HEAD $head 가 MAINT_SHA 와 다릅니다" }
}

function S0 {
  Stage '[0] wrangler 로그인 확인 + SMOKE_KEY'
  Show-Plan '0'
  Must-Gate '0단계를 실행할까요?'
  Push-Location $Worker
  try { $rc = Run 'wrangler whoami' { npx --yes wrangler@4 whoami } } finally { Pop-Location }
  if ($rc -ne 0) { Stop-Wizard 'wrangler 로그인 안 됨 (npx wrangler@4 login)' }
  if (-not (Test-Path $KeyFile)) {
    New-Item -ItemType Directory -Force -Path (Split-Path $KeyFile) | Out-Null
    $bytes = New-Object byte[] 24
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    [IO.File]::WriteAllText($KeyFile, (($bytes | ForEach-Object { $_.ToString('x2') }) -join ''), (New-Object Text.UTF8Encoding $false))
    Say "SMOKE_KEY 를 새로 만들어 $KeyFile 에 저장했습니다."
  } else { Say "기존 $KeyFile 을 그대로 씁니다(재실행)." }
  if ((Put-Secret 'SMOKE_KEY' (Load-Key) $Worker) -ne 0) { Stop-Wizard 'SMOKE_KEY 저장 실패' }
  Must-Gate '위 계정이 맞고 SMOKE_KEY 가 저장됐나요?'
}

function S2 {
  Stage '[2] 점검 모드 켜기: 점검용 빌드 배포 + 시드'
  Show-Plan '2'
  Say "점검용 빌드 = MAINT_SHA $($MaintSha.Substring(0, 8)). 켜면 앱은 503 이 됩니다(폰이 잠시 동기화 못 함, 10분 뒤 재시도)."
  Must-Gate '점검 모드를 켜고 점검용 빌드를 배포할까요?'
  Ensure-MaintTree
  if ((Put-Secret 'MAINTENANCE' '1' $Worker) -ne 0) { Stop-Wizard 'MAINTENANCE=1 저장 실패' }
  if ((Deploy $MaintWorker 'MAINT_SHA') -ne 0) { Stop-Wizard '점검용 빌드 배포 실패' }
  $a = Expect-Status "$Base/v2/state" 503
  $b = Expect-Status "$Base/j" 200
  if (-not ($a -and $b)) { Stop-Wizard '앱 503 / 초대 페이지 200 확인 실패' }
  $env:WP_SMOKE_KEY = Load-Key
  Push-Location $Worker
  try { $rc = Run 'smoke_v3.py seed' { python tools\smoke_v3.py seed } } finally { Pop-Location }
  if ($rc -ne 0) { Stop-Wizard 'smoke seed 실패' }
  Must-Gate '앱은 503, /j 는 200, seed 도 PASS 였나요?'
}

function S3 {
  Stage '[3] 백업: D1 time-travel 북마크 + 내보내기'
  Show-Plan '3'
  Must-Gate '백업을 시작할까요?'
  Push-Location $Worker
  try { $rc = Run 'd1 time-travel info' { npx --yes wrangler@4 d1 time-travel info $Db } } finally { Pop-Location }
  if ($rc -ne 0) { Stop-Wizard 'time-travel info 실패' }
  $m = [regex]::Match($script:LastOut, '[0-9a-f]{8}-[0-9a-f]{8}-[0-9a-f]{8}-[0-9a-f]{32}')
  $bookmark = if ($m.Success) { $m.Value } else { try { "$(Read-Host '  북마크를 못 찾았습니다. 위 출력에서 복사해 붙여넣으세요')".Trim() } catch { '' } }
  if (-not $bookmark) { Stop-Wizard '북마크 없음' }
  Set-State 'bookmark' $bookmark
  Log "bookmark: $bookmark"
  Say "북마크: $bookmark  (로그와 state 파일에 저장)"
  $dump = Join-Path $Scratch 'parking-before-v3.sql'
  Push-Location $Worker
  try { $rc = Run 'd1 export --remote' { npx --yes wrangler@4 d1 export $Db --remote --output $dump } } finally { Pop-Location }
  if ($rc -ne 0 -or -not (Test-Path $dump)) { Stop-Wizard 'd1 export 실패' }
  Note "내보내기(개인정보 포함, 커밋 금지, 컷오버 8단계 뒤 삭제): $dump"
  Must-Gate '북마크가 기록됐고 내보내기 파일이 있나요?'
}

function S4 {
  Stage '[4] 마이그레이션: dry-run 확인 뒤 실행'
  Show-Plan '4'
  Must-Gate '먼저 dry-run 을 돌릴까요? (아무것도 쓰지 않음)'
  if (Worker-Dirty) { Warn 'worker\ 에 커밋 안 된 변경이 있습니다(마이그레이션 도구는 현재 파일을 씁니다).' }
  Push-Location $Worker
  try {
    if ((Run 'migrate_v3 --dry-run' { python tools\migrate_v3.py --remote --dry-run }) -ne 0) { Stop-Wizard 'dry-run 실패' }
    Say '위 목록(그룹, 차 주인, 방장/최근 주차자, 새 id 가 붙은 차)을 운영자가 읽고 승인해야 합니다.'
    Must-Gate '목록을 읽었고 이대로 실행해도 좋나요? (운영자 승인)'
    $rc = Run 'migrate_v3 --remote' { python tools\migrate_v3.py --remote }
    while ($rc -ne 0) {
      Warn '중간에 멈췄습니다. 다시 실행해도 안전합니다(이미 한 건 건너뜀).'
      Must-Gate '다시 실행할까요?'
      $rc = Run 'migrate_v3 --remote (re-run)' { python tools\migrate_v3.py --remote }
    }
  } finally { Pop-Location }
  Must-Gate '마이그레이션이 끝났고 결과 숫자가 dry-run 과 같나요?'
}

function S5 {
  Stage '[5] v3 배포 (점검 모드 유지) + 스모크'
  Show-Plan '5'
  $v3 = (git -C $Repo rev-parse HEAD).Trim()
  Log "V3_SHA $v3"
  Say "V3_SHA = $v3"
  git -C $Repo merge-base --is-ancestor $MaintSha $v3
  if ($LASTEXITCODE -ne 0) { Stop-Wizard 'HEAD 가 MAINT_SHA 의 후손이 아닙니다' }
  if (Worker-Dirty) { Stop-Wizard 'worker\ 에 커밋 안 된 변경이 있어 HEAD 와 배포 내용이 달라집니다. 커밋하고 다시 하세요' }
  Must-Gate 'v3 를 배포하고 스모크를 돌릴까요? (점검 모드는 그대로)'
  if ((Deploy $Worker "V3_SHA $($v3.Substring(0, 8))") -ne 0) { Stop-Wizard 'v3 배포 실패' }
  if (-not (Expect-Status "$Base/v3/state" 503)) { Stop-Wizard '점검 모드가 꺼져 있습니다(503 아님)' }
  $env:WP_SMOKE_KEY = Load-Key
  Push-Location $Worker
  try { $rc = Run 'smoke_v3.py check' { python tools\smoke_v3.py check } } finally { Pop-Location }
  if ($rc -ne 0) { Stop-Wizard '스모크 FAIL: 롤백은 --stage R' }
  Must-Gate '스모크가 전부 PASS 였나요?'
}

function S6 {
  Stage '[6] 점검 해제 + 폰 확인'
  Show-Plan '6'
  Warn '이 단계부터 되돌리면 v3 이후 쓰기가 사라집니다(스펙 9절).'
  Must-Gate '점검 모드를 끌까요?'
  if ((Put-Secret 'MAINTENANCE' '0' $Worker) -ne 0) { Stop-Wizard 'MAINTENANCE=0 저장 실패' }
  Set-State 'stage6' (Get-Date).ToString('s')
  if (-not (Expect-Status "$Base/v3/state" 401)) { Warn '401 이 아닙니다. 위 값을 확인하세요.' }
  Step '폰마다 앱을 열어 주세요(안 열면 백그라운드 재시도가 늦어집니다). 주차를 하나 저장해 다른 폰에 뜨는지 봅니다.'
  Must-Gate '주차 기록이 폰끼리 동기화됐나요?'
}

function S7 {
  Stage '[7] APK 설치'
  Show-Plan '7'
  Say 'Claude 가 v3 APK(어디댔어 1.1.0)를 보냅니다. 폰마다 기존 앱 위에 설치하세요(같은 키, 로그인과 데이터 유지).'
  Must-Gate '모든 폰에 설치가 끝났나요?'
  if ((Test-Path $MaintTree) -and (Gate "점검용 worktree($MaintTree)를 지울까요?")) {
    [void](Run 'git worktree remove' { git -C $Repo worktree remove --force $MaintTree })
  }
  Note "내보내기 파일($Scratch\parking-before-v3.sql)은 컷오버 8단계 뒤 삭제하세요."
  Note 'ROADMAP: 7일간 devices.v2_seen 없음 + 6단계 뒤 30일 → v2 계층 제거(별도 계획). 옛 테이블 삭제는 운영자 확인 필요.'
}

function SR {
  Stage '[R] 롤백 (6단계 이전에만)'
  Show-Plan 'R'
  $st = Get-State
  if ($st.stage6) { Stop-Wizard "6단계($($st.stage6))를 이미 했습니다. 지금 복원하면 v3 이후 쓰기가 사라집니다. 운영자와 먼저 상의하세요" }
  $bm = $st.bookmark
  if (-not $bm) { $bm = try { "$(Read-Host '  state 에 북마크가 없습니다. 3단계 북마크를 붙여넣으세요')".Trim() } catch { '' } }
  if (-not $bm) { Stop-Wizard '북마크 없음' }
  Must-Gate "D1 을 북마크 $bm 로 복원하고 점검용 빌드를 다시 배포할까요?"
  Push-Location $Worker
  try { $rc = Run 'd1 time-travel restore' { $input | npx --yes wrangler@4 d1 time-travel restore $Db "--bookmark=$bm" } 'y' } finally { Pop-Location }
  if ($rc -ne 0) { Stop-Wizard 'restore 실패' }
  Ensure-MaintTree
  if ((Deploy $MaintWorker 'MAINT_SHA') -ne 0) { Stop-Wizard '점검용 빌드 배포 실패' }
  if ((Put-Secret 'MAINTENANCE' '0' $Worker) -ne 0) { Stop-Wizard 'MAINTENANCE=0 저장 실패' }
  Say '롤백 완료: 옛 서버 코드 + 복원된 DB, 점검 모드 해제.'
  Note '스펙 9절: 6단계 전 롤백은 잃는 것이 없습니다(북마크 뒤 쓰기를 받지 않았고, 마이그레이션은 R2 를 건드리지 않음).'
  Note '6단계 뒤에는 같은 복원이 v3 쓰기를 잃고, v3 가 지운 R2 사진은 그대로 지워진 채입니다. 7단계 뒤에는 옛 APK 도 필요합니다.'
}

Log "start args='$WizardArgs' maint=$MaintSha"
$Stages = if ($Only) { @($Only) } else { @('0', '2', '3', '4', '5', '6', '7') }
$TotalStages = $Stages.Count
Write-Host "`n  어디댔어 v3 컷오버 · 단계 $($Stages -join ' ')  (로그 $LogFile)" -ForegroundColor Cyan
foreach ($k in $Stages) { & "S$k" }
if (-not $Only) { Note '롤백이 필요하면 --stage R (6단계 이전에만).' }
Finish
