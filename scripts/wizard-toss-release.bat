<# :
@echo off
rem cmd runs only these lines; PowerShell sees them as a block comment.
rem Save this file as UTF-8 WITHOUT BOM (a BOM breaks the first cmd line).
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$WizardPath='%~f0'; iex ([IO.File]::ReadAllText($WizardPath, [Text.Encoding]::UTF8))"
set "rc=%errorlevel%"
pause
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
# STAGES: author this section. One Stage per step the human takes.
# Replace the example below. Set $TotalStages to match the stages you write.
# ──────────────────────────────────────────────────────────────────────────

$TotalStages = 7
$Toss = Join-Path (Split-Path -Parent $ScriptDir) 'works\2026-10-26-photo-booth\toss'
Set-Location $Toss
$EnvFile = 'ait.release.local'   # remembers appName and the ad id between runs (git-ignored)
$Console = 'https://apps-in-toss.toss.im/'
$Privacy = 'https://dot-connects.github.io/dot-lab/works/2026-10-26-photo-booth/site/privacy.html'

Banner '따로 같이 네컷 · 토스 정식 출시'

Stage '오늘 할 일'
Say '콘솔에서 정식 앱을 준비하고, 파일을 만들어 올린 뒤, 검수를 요청해요.'
Step '코드 쪽 준비는 끝났어요. 이 창이 시키는 대로 콘솔만 눌러 주면 돼요.'
Step '검수 결과는 토스가 알려 줘요. 통과하면 Claude 에게 "토스 출시됐어"와 링크를 보내 주세요.'
Warn '실제 광고를 켜면 누적 예상 수익 5,000원 뒤 5영업일 안에 사업자·정산 등록이 필요해요. 안 하면 앱 노출이 멈춰요.'
Wait-Enter

Stage '정식 앱 이름'
Open-Url $Console
Step '콘솔에서 시험 앱(따로 같이 네컷 테스트)의 appName 을 바꿀 수 있는지 봐요.'
Step '바꿀 수 없으면 새 앱을 등록해요. 표시 이름 = 따로 같이 네컷, appName = duo-4cut'
Step '표시 이름에 "테스트"가 없어야 해요.'
$app = Ask 'AIT_APP_NAME' '정식 appName (duo-4cut):'
if (-not $app) { $app = 'duo-4cut' }
$app = $app.Trim(); Write-Env 'AIT_APP_NAME' $app
if ($app -match '-test$') { Warn 'appName 이 -test 로 끝나면 정식 파일이 안 만들어져요. 다시 실행해 주세요.'; $script:Skipped += '정식 appName'; Finish; return }
if ($app -ne 'duo-4cut') { Warn "appName 이 $app 이면 사진 서버(Worker)가 이 앱을 막아요. 여기서 멈추고 Claude 에게 appName 을 알려 주세요."; $script:Skipped += 'Worker 허용 주소'; Finish; return }
Wait-Enter

Stage '광고 ID'
Step '콘솔 → 앱 → 수익화(광고) → 보상형 광고 그룹을 만들어요.'
Step '만들어진 광고 그룹 ID 를 복사해요. (ait-ad-test 로 시작하면 시험 ID 예요. 진짜 ID 가 필요해요.)'
$ad = Ask 'AIT_AD_ID' '광고 그룹 ID:'
if (-not $ad -or $ad -match 'test') { Warn '진짜 광고 ID 가 있어야 정식 파일을 만들 수 있어요.'; $script:Skipped += '광고 ID'; Finish; return }
$ad = $ad.Trim(); Write-Env 'AIT_AD_ID' $ad
Wait-Enter

Stage '정식 파일 만들기 (자동)'
Say "appName '$app' 으로 .ait 를 만들어요. 1~3분 걸려요."
if (-not (Test-Path 'node_modules')) { & npm.cmd install --no-audit --no-fund }
$env:AIT_APP_NAME = $app
$env:AIT_AD_ID = $ad
& npm.cmd run build
$ait = Join-Path $Toss "$app.ait"
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $ait)) { Warn '빌드 실패. 이 창을 캡처해서 Claude 에게 보여 주세요.'; $script:Skipped += '정식 빌드'; Wait-Enter; Finish; return }
Write-Host '  ✓ 만들었어요: ' -ForegroundColor Green -NoNewline; Write-Host $ait
Note ("   크기 {0:N1} MB" -f ((Get-Item $ait).Length / 1MB))
Wait-Enter

Stage '콘솔에 올리고 폰으로 확인'
Open-Url $Console
Step '콘솔 → 앱 → 왼쪽 "앱 출시" → "등록하기".'
Step "앱 번들 칸에 이 파일을 올려요:  $app.ait"
if (Confirm-Yes '파일이 있는 폴더를 열까요?') { Start-Process explorer.exe "/select,`"$ait`"" }
Step '"토스앱 테스트" QR 로 폰에서 열어요.'
Step '확인 1: 처음 화면 글자체가 시험판과 같은가요? 꾸미기 문구·프레임 글자도요.'
Step '확인 2: 친구 링크 시험 버튼과 시험용 웹 링크가 사라졌나요?'
Step '확인 3: 영상 받기 전 광고가 나오고, 끝나면 저장되나요?'
Warn '확인 3 은 진짜 광고가 뜨면 내 광고를 누르지 마세요.'
Wait-Enter '다 확인했으면 Enter'

Stage '앱 정보 입력'
Step '앱 이름: 따로 같이 네컷'
Step '앱 내 기능: 친구와 따로 찍어 한 장의 네컷 만들기'
Step '설명에 "AI 생성" 표현은 쓰지 않아요. 누끼·보정은 생성형 기능이 아니에요.'
Step '스크린샷: 폰에서 첫 화면, 찍는 화면, 꾸미기 화면을 캡처해서 올려요.'
Say  "개인정보 처리방침 링크:  $Privacy"
Open-Url $Privacy
Step '이 페이지를 읽어 보고, 문의처를 다른 곳으로 하고 싶으면 Claude 에게 말해 주세요.'
Wait-Enter

Stage '검수 요청'
Step '콘솔에서 검수(출시) 요청을 눌러요. 이 단계는 되돌리기 어려우니 앞 단계가 끝났을 때만 눌러요.'
Step '반려되면 사유 원문을 그대로 Claude 에게 보내 주세요. 고쳐서 다시 올리면 돼요.'
Step '통과하면 Claude 에게 "토스 출시됐어" + 앱 링크를 보내 주세요. 그때 웹판의 토스 안내·잠금을 켜요.'
Wait-Enter '요청했으면 Enter'
Finish
