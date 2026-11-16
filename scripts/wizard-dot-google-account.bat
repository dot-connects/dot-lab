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

# Shared helpers with wizard-play-console.bat (same state file).
# Progress lives in a plain state file (no secrets), so each part can be run days apart.
# Facts checked 2026-11-16 against Play Console Help: answer/14151465 (12 testers, 14 days in a row),
# answer/13628312 (account info), answer/14316361 (device check), answer/17594752 (trader status).

$StateFile = if ($env:DOT_PLAY_STATE) { $env:DOT_PLAY_STATE } else { Join-Path $env:USERPROFILE '.dot-play-state.txt' }

function St-Get([string]$Key) {
  if (Test-Path $StateFile) {
    $line = Get-Content $StateFile -Encoding UTF8 | Where-Object { $_ -like "$Key=*" } | Select-Object -Last 1
    if ($line) { return $line.Substring($Key.Length + 1) }
  }
  return ''
}

function St-Set([string]$Key, [string]$Value) {
  $lines = @()
  if (Test-Path $StateFile) { $lines = @(Get-Content $StateFile -Encoding UTF8 | Where-Object { $_ -notlike "$Key=*" }) }
  $lines += "$Key=$Value"
  [IO.File]::WriteAllLines($StateFile, [string[]]$lines)
  Write-Host '  ✓ 기록 ' -ForegroundColor Green -NoNewline; Write-Host "$Key = $Value"
}

function Ask-St([string]$Key, [string]$Prompt, [string]$Default = '') {
  $cur = St-Get $Key
  if (-not $cur) { $cur = $Default }
  $hint = if ($cur) { " [Enter = $cur]" } else { '' }
  $v = Read-Host "  $Prompt$hint"
  if (-not $v) { $v = $cur }
  if ($v) { St-Set $Key $v }
  return $v
}

function Today { (Get-Date).ToString('yyyy-MM-dd') }

function Copy-Text([string]$Text) {
  Write-Host ''
  $Text -split "`n" | ForEach-Object { Write-Host "    $_" -ForegroundColor White }
  Write-Host ''
  try { Set-Clipboard -Value $Text; Write-Host '  ✓ 위 글을 클립보드에 복사했어요. 카톡에 붙여 넣으면 됩니다.' -ForegroundColor Green }
  catch { Warn '클립보드 복사 실패 — 위 글을 직접 복사하세요.' }
}


# ── D:OT Google account ──────────────────────────────────────────────────
# A dedicated Google account for D:OT apps: Play Console owner + the public developer email.
# The wizard never asks for the password; it records only the address in the Play wizard's state file,
# so wizard-play-console.bat picks it up as its default.

$TotalStages = 5
Banner 'D:OT 전용 Google 계정 만들기'

Stage '주소 정하기'
Say 'Play 스토어에 공개되는 주소예요. 짧고, 브랜드가 보이고, 오래 쓸 이름으로.'
Step 'Gmail 주소 규칙: 영문 소문자·숫자·마침표(.) 6~30자 (마침표는 무시돼서 dot.lab = dotlab)'
Step '후보를 3개쯤 생각해 두세요 — 이미 쓰는 주소면 가입 화면이 다른 걸 고르라고 해요'
Note '예: dot.lab.apps / dotlab.kr / dot.connects.app (가능한지는 가입 화면에서만 알 수 있어요)'
Write-Host ''
Step '필요한 것: 문자 받을 휴대폰, 실제 생년월일(Play 개발자는 만 18세 이상)'
Wait-Enter 'Enter 로 다음'

Stage '계정 만들기'
Note '지금 크롬이 개인 계정으로 로그인돼 있으면 섞이지 않게 시크릿 창(Ctrl+Shift+N)에 아래 주소를 붙여 넣어도 돼요.'
Open-Url 'https://accounts.google.com/signup'
Step '이름: 성 칸은 비우고 이름 칸에 D:OT (콜론이 안 되면 DOT)'
Step '생년월일·성별: 실제 정보 (본인 확인 때 신분증과 맞춰 봐요)'
Step '"나만의 Gmail 주소 만들기" → 정한 주소 입력 → 안 되면 다음 후보'
Step '비밀번호: 새로 만든 강한 비밀번호, 비밀번호 관리자에 저장 (이 위자드는 비밀번호를 묻지 않아요)'
Step '전화번호 인증 → 복구 이메일: zipgap.note@gmail.com (잃어버렸을 때 되찾는 용)'
Step '약관 동의 → 가입 완료'
Write-Host ''
$mail = Ask-St 'DOT_GMAIL' '만든 주소 (예: dot.lab.apps@gmail.com)'
if ($mail -notmatch '^[a-z0-9.]+@gmail\.com$') { Warn "주소 모양이 이상해요: $mail — 다시 실행해서 고칠 수 있어요." }
Wait-Enter 'Enter 로 다음'

Stage '2단계 인증 켜기 (Play Console 필수)'
Say 'Play 개발자 계정은 2단계 인증이 켜진 계정만 쓸 수 있어요.'
Open-Url 'https://myaccount.google.com/signinoptions/twosv'
Step "$mail 로 로그인된 상태인지 오른쪽 위 프로필 확인"
Step '"2단계 인증 사용" → 휴대폰 확인 → 켜기'
Step '같은 화면 아래 "백업 코드" → 받아서 비밀번호 관리자나 안전한 곳에 보관'
if (Confirm-Yes '2단계 인증을 켰나요?') { St-Set 'DOT_2SV' (Today) } else { $script:Skipped += '2단계 인증 (Play 가입 전에 꼭)' }
Wait-Enter 'Enter 로 다음'

Stage '받은 메일을 개인 메일로도 받기 (선택)'
Say 'D:OT 메일함을 따로 안 열어도 되게, 새 메일을 zipgap.note@gmail.com 으로 전달해 둘 수 있어요.'
Open-Url 'https://mail.google.com/mail/u/0/#settings/fwdandpop'
Step "$mail 의 Gmail 이 열렸는지 확인 (다른 계정이면 오른쪽 위에서 바꾸기)"
Step '"전달 주소 추가" → zipgap.note@gmail.com → 개인 메일함에 온 확인 링크 누르기'
Step '다시 이 화면 → "수신 메일 사본을 다음 주소로 전달" 선택 → 사본은 받은편지함에 보관 → 변경사항 저장'
if (Confirm-Yes '전달을 켰나요?') { St-Set 'DOT_FORWARD' (Today) } else { Note '나중에 해도 돼요.' }
Wait-Enter 'Enter 로 다음'

Stage 'Play 위자드에 넘기기'
if ($mail) { St-Set 'OWNER_ACCOUNT' $mail; St-Set 'DEV_EMAIL' $mail }
else { Warn '주소가 비어 있어 넘기지 않았어요 — 다시 실행해 2단계에서 넣으세요.'; $script:Skipped += 'D:OT 주소 기록' }
Say 'Play 위자드(wizard-play-console.bat) 1번을 실행하면 이 주소가 기본값으로 들어가 있어요.'
Step "Claude 에게 '디오트 메일 만들었어' 라고만 알려 주세요 — 개인정보 처리방침 연락처를 이 주소로 바꿔요."
Note 'Play 가입은 꼭 이 계정으로 로그인한 창에서 하세요. 계정 주인은 나중에 옮기기 어려워요.'
Wait-Enter 'Enter 로 마무리'

Finish
Note "기록 파일: $StateFile"
