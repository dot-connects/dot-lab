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
$Spike = Join-Path (Split-Path -Parent $ScriptDir) 'works\2026-10-26-photo-booth\toss-spike'
Set-Location $Spike
$EnvFile = 'ait.local'   # remembers the appName between runs (not a secret, git-ignored)

Banner '따로 같이 네컷 · 토스 시험판 올리기'

Stage '준비물 확인'
Say '토스 앱 안에서 카메라·녹화·저장·광고가 되는지 시험판으로 확인하는 과정이에요.'
Say '운영자가 할 일은 콘솔 가입 → 앱 등록 → 파일 올리기 → 폰으로 QR 찍고 누르기, 네 가지예요.'
Say ''
Step '만 19세 이상이어야 해요 (콘솔 가입·QR 테스트 조건).'
Step '폰에 본인 명의 토스 앱이 로그인돼 있어야 해요.'
Step 'PC 브라우저로 콘솔을 쓰고, 마지막에 폰 카메라로 QR 을 찍어요.'
Note '시간: 처음이면 15~20분. 중간에 Ctrl-C 로 멈췄다가 다시 실행해도 돼요.'
Wait-Enter '준비됐으면 Enter'

Stage '앱인토스 콘솔 가입'
Open-Url 'https://apps-in-toss.toss.im/'
Step '오른쪽 위 로그인/시작하기 → 토스 앱으로 본인 인증 (토스 비즈니스 회원 기반 가입).'
Step '약관 동의까지 마치면 워크스페이스 만들기 화면이 나와요.'
Note '이미 가입했다면 그냥 Enter.'
Wait-Enter '가입이 끝나면 Enter'

Stage '워크스페이스 만들기'
Step '워크스페이스 이름: 아무거나 알아보기 쉬운 이름 (예: dotlab). 다른 사람과 겹치면 안 돼요.'
Step '제작자 이름: 미니앱 화면에 "만든 사람"으로 보이는 이름이에요.'
Note '   규칙: 공백 포함 10자 이내, 한글·영문·숫자만 (":" 같은 특수문자 불가 → "D:OT" 는 안 됨).'
Note '   추천: DOT LAB  또는  닷랩.   나중에 "내 정보" 메뉴에서 바꿀 수 있어요.'
Step '만든 사람에게 대표관리자 권한이 자동으로 붙어요. 멤버 초대는 지금 필요 없어요.'
Note '워크스페이스는 사업자(개인)당 1개만 만들 수 있어요.'
Wait-Enter '워크스페이스를 만들었으면 Enter'

Stage '시험용 앱 등록 + appName 정하기'
Say '시험용 앱을 따로 등록해요. 진짜 출시용 앱은 나중에 같은 워크스페이스에 하나 더 등록하면 돼요.'
Step '왼쪽 "앱" 메뉴 → "+등록하기".'
Step '앱 이름: 따로 같이 네컷 테스트   (나중에 바꿀 수 있어요)'
Step '영문 앱 이름: Ttarogachi Test   (15자 이내 명사형)'
Step 'appName: ttarogachi-test   ← 한 번 정하면 절대 못 바꿔요. 영문 소문자·숫자·하이픈(-)만 쓰세요.'
Step '앱 유형: 비게임'
Step '나머지 칸(로고·설명 등)은 비워 두고 저장해도 돼요.'
Say ''
$app = Ask 'AIT_APP_NAME' '콘솔에 입력한 appName 을 그대로 적어 주세요:'
while (-not $app) { $app = Read-Host '  appName 이 비어 있어요. 다시 적어 주세요' }
$app = $app.Trim()
while ($app -notmatch '^[a-z][a-z0-9-]{1,40}$') {
  Warn "'$app' 은 영문 소문자로 시작하고 소문자·숫자·하이픈(-)만 써야 해요."
  $app = (Read-Host '  콘솔에 입력한 appName 을 다시 적어 주세요').Trim()
}
Write-Env 'AIT_APP_NAME' $app
Wait-Enter

Stage '시험판 파일 만들기 (자동)'
Say "appName '$app' 으로 시험판 파일(.ait)을 만들어요. 1~3분 걸려요."
if (-not (Test-Path 'node_modules')) {
  Say '처음이라 패키지를 설치해요…'
  & npm.cmd install --no-audit --no-fund
  if ($LASTEXITCODE -ne 0) { Warn 'npm install 실패. 이 창을 캡처해서 Claude 에게 보여 주세요.'; $script:Skipped += 'npm install'; Wait-Enter; Finish; return }
}
$env:AIT_APP_NAME = $app
& npm.cmd run build
$ait = Join-Path $Spike "$app.ait"
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $ait)) {
  Warn '빌드 실패. 이 창을 캡처해서 Claude 에게 보여 주세요.'; $script:Skipped += '시험판 빌드'; Wait-Enter; Finish; return
}
Write-Host ''
Write-Host '  ✓ 만들었어요: ' -ForegroundColor Green -NoNewline; Write-Host $ait
Note ("   크기 {0:N1} MB" -f ((Get-Item $ait).Length / 1MB))
if (Confirm-Yes '파일이 있는 폴더를 열까요? (다음 단계에서 이 파일을 올려요)') { Start-Process explorer.exe "/select,`"$ait`"" }
Wait-Enter

Stage '콘솔에 올리고 폰에서 열기'
Step '콘솔 → 앱 "따로 같이 네컷 테스트" → 왼쪽 "앱 출시" 메뉴 → "등록하기".'
Step "앱 번들 칸에 방금 만든 파일을 올려요:  $app.ait"
Note '   "번들 파일을 읽지 못했어요" 가 뜨면 한 번 더 올려 보고, 그래도 안 되면 캡처해서 Claude 에게.'
Step '올라가면 오른쪽 "토스앱 테스트" 버튼 → QR 코드가 나와요.'
Step '폰 기본 카메라로 QR 을 찍으면 토스 앱에서 시험판이 열려요.'
Note '   QR 테스트 조건: 토스 앱 로그인 + 워크스페이스 멤버 + 만 19세 이상.'
Wait-Enter '폰에서 "토스 시험판" 화면이 열렸으면 Enter'

Stage '폰에서 시험하고 결과 보내기'
Say '화면 위에서부터 차례로 눌러 주세요. 각 칸 제목 옆에 성공/실패가 떠요.'
Step '1 카메라 켜기 → "허용". 내 얼굴이 움직이면 성공.'
Step '2 MediaRecorder 2초 → 영상이 아래에서 재생되면 성공. WebCodecs 2초, 8초도 한 번씩.'
Step '3 영상 저장 → 갤러리(또는 내 파일) 앱을 열어 영상이 있고 재생되는지 직접 봐 주세요. 사진 저장도.'
Step '4 빠른 누끼 → 정밀 누끼. 정밀 누끼는 10~60초 걸릴 수 있어요. 화면이 하얗게 되면 그것도 결과예요.'
Step '5 광고 보고 영상 받기 (테스트) → 광고를 끝까지 보고 닫기.'
Step '6 공유 링크 만들기 → 공유 창이 뜨면 그냥 닫아도 돼요.'
Say ''
Step '맨 아래 "결과 복사" → Claude 대화창에 붙여 넣어 주세요.'
Note '   폰에 Claude 앱이 없으면 카톡 "나와의 채팅"에 붙여 넣고 PC 에서 복사해도 돼요.'
Note '   갤러리에서 본 것(영상이 있었는지, 재생됐는지)도 한 줄 같이 적어 주세요.'
Wait-Enter '다 보냈으면 Enter'

Finish
Say "다음: Claude 가 결과를 보고 토스 이식(P2) 여부를 판정해요. 시험판을 고치면 이 파일을 다시 실행해 새 .ait 를 올리면 돼요."
