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

$TotalStages = 8
$Toss = Join-Path (Split-Path -Parent $ScriptDir) 'works\2026-10-26-photo-booth\toss'
Set-Location $Toss
$EnvFile = 'ait.local'   # remembers the appName between runs (not a secret, git-ignored)
$WebUrl = 'https://dot-connects.github.io/dot-lab/works/2026-10-26-photo-booth/site/'

Banner '따로 같이 네컷 · 웹판 + 토스판 실폰 시험'

Stage '오늘 할 일'
Say '밤사이 만든 두 가지를 폰으로 직접 해 봐요.'
Step '웹판: 워터마크(말풍선)·메이킹 영상 잠금·Y2K/다이어리 잠금. 지금은 ?toss=1 을 붙여야만 보여요 (토스 출시 전이라 라이브 사용자에겐 아직 꺼 둠).'
Step '토스판: 토스 앱 안에서 찍기 → 꾸미기 → 광고 보는 동안 영상 2개 만들기 → 보내기·저장.'
Note '시간: 30~40분. 폰 2대가 있으면 친구(B) 쪽까지 해 볼 수 있어요.'
Wait-Enter '준비됐으면 Enter'

Stage '(선택) 배포 키 발급 — 다음부터 Claude 가 바로 올릴 수 있게'
Say '한 번 해 두면 다음 시험판부터는 콘솔에 파일을 직접 올릴 필요가 없어요. 건너뛰어도 오늘 시험엔 지장 없어요.'
if (Confirm-Yes '지금 배포 키를 발급할까요?') {
  Open-Url 'https://apps-in-toss.toss.im/'
  Step '콘솔 → 워크스페이스 선택 → 왼쪽 메뉴 "키" → API 키 발급.'
  Step '권한 범위: 이 앱(따로 같이 네컷 테스트)만 또는 전체 앱. 발급된 키를 복사해요 (다시 볼 수 없으니 바로).'
  Wait-Enter '키를 복사했으면 Enter (다음에 붙여 넣는 창이 떠요)'
  if (-not (Test-Path 'node_modules')) { & npm.cmd install --no-audit --no-fund }
  Say '아래에 키를 붙여 넣으세요 (ait token add). 키는 이 PC 의 ait 설정에만 저장되고 git 에는 안 들어가요.'
  & npx.cmd ait token add
  if ($LASTEXITCODE -ne 0) { Warn '키 등록 실패. 오늘은 콘솔에 직접 올리는 방법으로 가요.'; $script:Skipped += '배포 키' } else { $script:Written += 'ait token' }
}
Wait-Enter

Stage '토스판 파일 만들기 (자동)'
$app = Ask 'AIT_APP_NAME' '콘솔 appName (보통 ttarogachi-test, 그대로 Enter):'
if (-not $app) { $app = 'ttarogachi-test' }
$app = $app.Trim(); Write-Env 'AIT_APP_NAME' $app
Say "appName '$app' 으로 .ait 를 만들어요. 1~3분 걸려요."
if (-not (Test-Path 'node_modules')) { & npm.cmd install --no-audit --no-fund }
$env:AIT_APP_NAME = $app
& npm.cmd run build
$ait = Join-Path $Toss "$app.ait"
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $ait)) { Warn '빌드 실패. 이 창을 캡처해서 Claude 에게 보여 주세요.'; $script:Skipped += '토스판 빌드'; Wait-Enter; Finish; return }
Write-Host '  ✓ 만들었어요: ' -ForegroundColor Green -NoNewline; Write-Host $ait
Note ("   크기 {0:N1} MB" -f ((Get-Item $ait).Length / 1MB))
Wait-Enter

Stage '콘솔에 올리기'
$deployed = $false
if ($script:Written -contains 'ait token' -or (Confirm-Yes '이미 배포 키를 등록해 뒀나요? (아니면 n → 콘솔에 직접 올려요)')) {
  & npx.cmd ait deploy -m 'toss P2 test build'
  $deployed = ($LASTEXITCODE -eq 0)
  if (-not $deployed) { Warn 'ait deploy 실패 → 콘솔에 직접 올려요.' }
}
if (-not $deployed) {
  Open-Url 'https://apps-in-toss.toss.im/'
  Step '콘솔 → 앱 "따로 같이 네컷 테스트" → 왼쪽 "앱 출시" → "등록하기".'
  Step "앱 번들 칸에 이 파일을 올려요:  $app.ait"
  if (Confirm-Yes '파일이 있는 폴더를 열까요?') { Start-Process explorer.exe "/select,`"$ait`"" }
}
Step '오른쪽 "토스앱 테스트" → QR 코드 → 폰 기본 카메라로 찍으면 토스 앱에서 열려요.'
Wait-Enter '폰에서 「따로 같이 네컷」 첫 화면이 열렸으면 Enter'

Stage '토스판 점검 (폰 1대, A 쪽)'
Step '1 첫 화면이 2초 안에 뜨나요? 말랑 부스(분홍)로 바로 시작하나요? (테마 고르기 없음)'
Step '2 시작 → 모양 → 찍기 6장 → 고르기 → 보정 → 보내기. 카메라 허용 팝업이 뜨면 허용.'
Step '3 "친구한테 링크 보내기" → 토스 공유 창이 떠요. 메시지에 토스 링크 + (시험용 웹 링크) 가 같이 있어요.'
Note '   그 메시지를 카톡 "나와의 채팅"으로 보내 두세요. 친구(B) 시험에 써요.'
Step '4 각 화면에서 왼쪽 위 뒤로가기 / 안드로이드 뒤로 버튼: 찍는 중엔 무시, 꾸미기·공유 화면은 "한 번 더 누르면 닫혀요".'
Wait-Enter '여기까지 했으면 Enter'

Stage '친구(B) 쪽 + 꾸미기·광고·저장'
Say 'B 는 둘 중 되는 방법으로: (가) 다른 폰 토스에서 토스 링크 열기, (나) 다른 폰 크롬에서 "시험용 웹 링크" 열기.'
Note '   출시 전 토스 링크는 다른 폰에서 안 열릴 수 있어요. 안 되면 (나) 로. 폰이 1대면 같은 폰 크롬에서 (나).'
Step 'B 가 찍고 올리면, A(토스) 화면에서 "다시 확인" → 꾸미기 화면.'
Step '5 꾸미기: 프레임 19개·스티커 4종 전부 열려 있고, 네컷에 워터마크가 없나요?'
Step '6 "사진 저장" → 공유 창 → 카톡 나와의 채팅으로 보내 저장. 몇 초 걸렸는지.'
Step '7 "영상" → 영상 2개가 나란히 → 아무 칸이나 누르면 "만드는 동안 광고 하나만 봐 줄래?" → [좋아, 2개 다 만들어 줘].'
Step '   광고(테스트)를 끝까지 보고 닫기 → 두 칸이 "보내기·저장" 이 되면 각각 눌러 보내기. 걸린 시간도.'
Step '   한 번은 광고를 중간에 닫아 보기 → "광고가 끝나야 저장할 수 있어요 [다시 보기]" 가 나오는지.'
Step '8 정밀 누끼(움직이는 누끼 n/4 다듬는 중) 동안이나 광고 뒤에 화면이 하얗게 된 적이 있는지.'
Wait-Enter '다 해 봤으면 Enter'

Stage '웹판 점검 (크롬)'
Say "웹판은 링크 끝에 ?toss=1 을 붙여야 새 기능이 보여요:  $WebUrl" + '?toss=1'
Step '꾸미기 화면: Y2K·다이어리 프레임에 파란 "토스" 표시, 누르면 "토스에서 열려요" 시트.'
Step '스티커 탭: Y2K·다이어리 줄에 "· 토스".'
Step '영상: 움직이는 네컷은 받아지고 오른쪽 아래 말풍선 워터마크가 있어요. 메이킹 영상은 "토스 전용 · 미리보기만".'
Step '사진 저장한 것에도 말풍선 워터마크가 있는지.'
Note '   ?toss=1 없이 열면 지금까지와 똑같아야 해요 (라이브 사용자용).'
Wait-Enter '다 봤으면 Enter'

Stage '결과 보내기'
Step 'Claude 대화창에 한 줄씩: 1~8 번 되는지/안 되는지, 걸린 시간, 이상했던 화면(캡처 있으면 같이).'
Step '토스판 화면 맨 아래에 로그 보는 방법은 없어요. 이상하면 그 화면 캡처가 제일 좋아요.'
Wait-Enter '보냈으면 Enter'

Finish
Say '다음: Claude 가 결과를 보고 고친 뒤 새 .ait 를 만들어요. 배포 키를 등록했다면 업로드까지 Claude 가 해요.'
