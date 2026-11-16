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

# Google Play release wizard for the three dot-lab apps (one personal account, one tester group).
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

$Apps = @{
  '1' = @{ Key = 'CATCH'; Name = '리얼뽑기';       Kind = '게임'; Pkg = 'kr.dot.catchpet';
           Aab = (Join-Path $env:LOCALAPPDATA 'catchpet-build\app\outputs\bundle\release\app-release.aab') }
  '2' = @{ Key = 'DUO';   Name = '따로 같이 네컷'; Kind = '앱';   Pkg = '(T-0188 에서 정함)'; Aab = '' }
  '3' = @{ Key = 'PARK';  Name = '주차 앱';        Kind = '앱';   Pkg = '(T-0189 에서 정함)'; Aab = '' }
}

function Pick-App {
  Say '어느 앱인가요?   1) 리얼뽑기   2) 따로 같이 네컷   3) 주차 앱'
  $n = Read-Host '  번호 [Enter = 1]'
  if (-not $n) { $n = '1' }
  if (-not $Apps.ContainsKey($n)) { Warn '1~3 중에서 고르세요. 리얼뽑기로 진행합니다.'; $n = '1' }
  return $Apps[$n]
}

function Show-Dday($App) {
  $start = St-Get "$($App.Key)_START"
  if (-not $start) { Note "$($App.Name): 12명이 모인 날이 아직 기록되지 않았어요."; return }
  $apply = ([datetime]$start).AddDays(14)
  $left = [math]::Ceiling(($apply - (Get-Date).Date).TotalDays)
  if ($left -le 0) { Write-Host "  ✓ $($App.Name): $start 시작 → $($apply.ToString('yyyy-MM-dd')) 부터 프로덕션 신청 가능 (지금 가능)" -ForegroundColor Green }
  else { Say "$($App.Name): $start 시작 → $($apply.ToString('yyyy-MM-dd')) 부터 프로덕션 신청 가능 (D-$left)" }
}

# ── menu ──────────────────────────────────────────────────────────────────

Clear-Host
Write-Host "`n  Google Play 출시 위자드 — 리얼뽑기 · 따로 같이 네컷 · 주차 앱 (계정 하나, 테스터 묶음 하나)" -ForegroundColor Cyan
Note "진행 기록: $StateFile (비밀 정보 없음)"
Write-Host ''
$dev = St-Get 'DEV_NAME'; $grp = St-Get 'TESTER_GROUP'; $cnt = St-Get 'TESTER_COUNT'
Say ("개발자 계정: " + $(if (St-Get 'SIGNUP_DATE') { "$dev ($(St-Get 'SIGNUP_DATE') 가입)" } else { '아직' }))
Say ("테스터 그룹: " + $(if ($grp) { "$grp · 모인 사람 $cnt 명" } else { '아직' }))
foreach ($k in '1','2','3') { if (St-Get "$($Apps[$k].Key)_TRACK") { Show-Dday $Apps[$k] } }
Write-Host ''
Say '1) 개발자 계정 만들기          — 지금 (5단계)'
Say '2) 테스터 12명 모으기          — 지금, 1번과 같이 해도 됨 (3단계)'
Say '3) 앱 비공개 테스트 시작       — Claude 가 AAB(업로드용 앱 파일)를 만든 뒤 (4단계)'
Say '4) 참여 인원 기록 · 14일 날짜 — 사람이 늘거나 줄 때마다'
Say '5) 프로덕션(정식 출시) 신청    — 12명 × 14일이 지난 뒤 (2단계)'
$part = Read-Host "`n  번호"

switch ($part) {

'1' {
  $TotalStages = 5
  Banner 'Google Play 개인 개발자 계정 만들기'

  Stage '준비물 확인'
  Say '시작 전에 아래 5가지가 있어야 해요.'
  Step '계정 주인이 될 Google 계정 — 나중에 옮기기 어려우니 계속 쓸 계정으로'
  Step '해외 결제가 되는 카드 — 등록비 $25 (한 번만, 약 3만 6천 원)'
  Step '신분증 (주민등록증·운전면허증·여권) — 이름이 결제 프로필 이름과 똑같아야 함'
  Step 'Android 10 이상 실폰 (루팅 안 된 것) — 폴드 OK'
  Step '개발자 이름 — Play 스토어 앱 아래에 공개로 보여요'
  Write-Host ''
  [void](Ask-St 'DEV_NAME' '개발자 이름' 'D:OT')
  [void](Ask-St 'OWNER_ACCOUNT' '계정으로 쓸 Google 계정 이메일 (이 PC 기록용)')
  if (-not (Confirm-Yes '카드·신분증·폰 모두 있나요?')) { $script:Skipped += '준비물(카드·신분증·폰) 챙긴 뒤 1번 다시' }
  Wait-Enter 'Enter 로 다음'

  Stage '가입 + 등록비 $25'
  Open-Url 'https://play.google.com/console/signup'
  Step "위 계정($(St-Get 'OWNER_ACCOUNT'))으로 로그인"
  Step '계정 종류에서 "개인(Yourself / 본인)" 을 고른다'
  Warn '"조직(Organization)" 은 고르지 마세요 — 사업자 서류가 필요해요.'
  Step '결제 프로필: 이름·주소를 신분증과 똑같이 (영문/한글 표기도 맞추기)'
  Step "개발자 이름: $(St-Get 'DEV_NAME')"
  Step '연락처 이메일·개발자 이메일 입력 → 메일로 온 인증번호 입력'
  Step '앱 개발 경험 질문은 사실대로 → $25 결제'
  Note '개발자 이메일은 스토어에 공개돼요. 개인 메일을 숨기고 싶으면 별도 Gmail 을 쓰세요.'
  Write-Host ''
  Wait-Enter '결제까지 끝나면 Enter'
  [void](Ask-St 'DEV_EMAIL' '스토어에 보일 개발자 이메일')
  St-Set 'SIGNUP_DATE' (Today)

  Stage '본인 확인 (신분증)'
  Open-Url 'https://play.google.com/console'
  Step '홈 화면의 할 일 목록에서 "본인 확인(Verify your identity)" 을 연다'
  Step '신분증 사진을 올린다 — 결제 프로필 이름과 다르면 그 자리에서 프로필을 고칠 수 있어요'
  Note '검토는 며칠 걸릴 수 있어요. 기다리는 동안 다음 단계와 메뉴 2번(테스터 모으기)을 먼저 해도 됩니다.'
  if (Confirm-Yes '신분증을 제출했나요?') { St-Set 'ID_SUBMITTED' (Today) } else { $script:Skipped += '본인 확인(신분증) 제출' }
  Wait-Enter 'Enter 로 다음'

  Stage 'Android 기기 확인 (실폰)'
  Step 'Play Console 홈에서 "Android 휴대기기 액세스 권한 확인" 할 일을 연다'
  Step '화면의 QR 코드를 폰으로 찍어 "Google Play Console" 앱 설치'
  Step "앱에서 계정 주인($(St-Get 'OWNER_ACCOUNT'))으로 로그인 → 개발자 계정 선택 → 확인(Verify)"
  Note '본인 확인과 기기 확인이 끝나야 전화번호 인증이 열려요. 전화번호 인증 할 일이 보이면 그것도 끝내세요.'
  if (Confirm-Yes '기기 확인을 끝냈나요?') { St-Set 'DEVICE_VERIFIED' (Today) } else { $script:Skipped += 'Android 기기 확인 (본인 확인 승인 뒤 다시)' }
  Wait-Enter 'Enter 로 다음'

  Stage '판매자 계정 (500원 결제용)'
  Say '리얼뽑기의 펫 그림(500원)을 팔려면 판매자 계정이 있어야 해요.'
  Step 'Play Console 왼쪽 메뉴 → 설정 → "결제 프로필" 또는 "판매자 계정" → 만들기'
  Step '이름·주소는 1단계 결제 프로필 그대로, 정산 받을 은행 계좌는 나중에 넣어도 돼요'
  Note '메뉴 이름이 다르거나 안 보이면(본인 확인 전이라 잠겨 있을 수 있음) 화면을 캡처해서 Claude 에게 보내 주세요.'
  Write-Host ''
  Warn '"거래자(trader) 여부 선언" 이 나와도 지금은 넘기세요 (마감 2027-02-01).'
  Note '거래자로 선언하면 유럽·영국 사용자에게 이름·주소·전화번호가 보이고, 아니면 이메일·나라만 보여요. Claude 와 정해요.'
  if (Confirm-Yes '판매자 계정을 만들었나요?') { St-Set 'MERCHANT' (Today) } else { $script:Skipped += '판매자 계정 (본인 확인 승인 뒤 다시 1번 → 5단계)' }
  Wait-Enter 'Enter 로 마무리'
}

'2' {
  $TotalStages = 3
  Banner '테스터 12명 모으기 (세 앱 공용 그룹 하나)'

  Stage 'Google 그룹 만들기'
  Say '테스터를 Google 그룹 하나로 모으면, 세 앱 테스트에 그룹 이메일만 넣으면 끝이에요.'
  Open-Url 'https://groups.google.com/my-groups'
  Step '"그룹 만들기" → 이름: 예) D:OT 테스터 · 그룹 이메일: 예) dot-testers'
  Step '개인정보 설정: "가입할 수 있는 사용자" = "웹에 있는 모든 사용자"(누구나 바로 가입)'
  Step '나머지는 기본값 → 그룹 만들기'
  Write-Host ''
  $grp = Ask-St 'TESTER_GROUP' '그룹 이메일 (예: dot-testers@googlegroups.com)'
  if (-not $grp) { $script:Skipped += '테스터 그룹 이메일'; Wait-Enter; break }
  Wait-Enter 'Enter 로 다음'

  Stage '초대 글 보내기'
  $prefix = ($grp -split '@')[0]
  $join = "https://groups.google.com/g/$prefix"
  St-Set 'TESTER_JOIN' $join
  Say '아래 글을 친구·가족에게 보내세요. 14~15명을 모으면 안전해요 (한 명이 빠져 12명 밑이 되면 14일을 처음부터 다시 셉니다).'
  Copy-Text @"
부탁 하나만 할게! 내가 만든 안드로이드 앱 3개(리얼뽑기 · 따로 같이 네컷 · 주차 앱)를 Play 스토어에 올리려는데, 테스터 12명이 2주 동안 필요해.
1) 이 링크로 그룹 가입 (Play 스토어 쓰는 그 Google 계정으로): $join
2) 앱이 준비되면 설치 링크를 따로 보낼게.
3) 설치하고 14일 동안만 지우지 말아 줘. 가끔 한 번 열어 보고, 이상한 거 있으면 한 줄만 알려 줘!
(안드로이드 폰만 돼. 아이폰은 안 돼 ㅠ)
"@
  Note '테스터 교환 모임(서로 앱 테스트해 주는 커뮤니티)을 쓰면 링크를 Claude 에게 알려 주세요.'
  Wait-Enter 'Enter 로 다음'

  Stage '모인 사람 수 기록'
  Open-Url "$join/members"
  Step '그룹 회원 목록에서 사람 수를 센다 (본인 제외)'
  $n = Ask-St 'TESTER_COUNT' '지금 모인 사람 수'
  if (-not ($n -match '^\d+$') -or [int]$n -lt 12) { Note "아직 $n 명. 사람이 늘면 메뉴 2번 → 3단계만 다시 하거나 메뉴 4번으로 기록하세요." }
  else { Write-Host "  ✓ $n 명 — 앱이 준비되면 메뉴 3번으로." -ForegroundColor Green }
  Wait-Enter 'Enter 로 마무리'
}

'3' {
  $App = Pick-App
  $K = $App.Key
  $TotalStages = 4
  Banner "$($App.Name) 비공개 테스트 시작"

  Stage '앱 만들기'
  if (-not (St-Get 'TESTER_GROUP')) { Warn '테스터 그룹이 아직 없어요 — 메뉴 2번 먼저.' }
  Open-Url 'https://play.google.com/console'
  Step '"앱 만들기" 버튼'
  Step "앱 이름: $($App.Name) · 기본 언어: 한국어 – ko-KR · 앱/게임: $($App.Kind) · 무료"
  Step '정책 동의 체크 2개 → 앱 만들기'
  Note "패키지 이름($($App.Pkg))은 첫 AAB 를 올릴 때 정해지고 바꿀 수 없어요. Claude 가 만든 AAB 만 올리세요."
  St-Set "$($K)_APP" (Today)
  Wait-Enter '앱이 만들어지면 Enter'

  Stage '비공개 테스트 트랙 + AAB 올리기'
  Step '왼쪽 "테스트 및 출시" → "비공개 테스트" → 기본 트랙(알파) "트랙 관리"'
  Step "테스터 탭: 'Google 그룹' 칸에 $(St-Get 'TESTER_GROUP') 입력 · 피드백 받을 이메일 입력 → 저장"
  Step '국가/지역 탭: 대한민국 추가 → 저장'
  Step '"새 버전 만들기" → "Play 앱 서명" 사용(Google 이 서명 키 보관) → 계속'
  $aab = Ask-St "$($K)_AAB" 'AAB 파일 경로 (Claude 가 알려 준 경로)' $App.Aab
  if ($aab -and -not (Test-Path $aab)) { Warn "그 경로에 파일이 없어요: $aab — Claude 에게 AAB 위치를 물어보세요." }
  Step 'AAB 파일을 끌어다 놓기 → 출시 노트: "첫 테스트 버전" → 다음 → 저장'
  Step '"버전 검토" → 오류가 없으면 "비공개 테스트 트랙에 출시 시작"'
  Write-Host ''
  Warn '대시보드 "앱 설정" 할 일(개인정보 처리방침 URL · 앱 액세스 · 광고 · 콘텐츠 등급 · 타겟층 · 데이터 보안 등)이 남아 있으면 검토로 못 보내요.'
  Note '그 답안은 Claude 가 미리 문서로 준비해 둡니다. 막히는 칸은 캡처해서 보내 주세요. 첫 검토는 며칠 걸릴 수 있어요.'
  if (Confirm-Yes '비공개 테스트 출시(검토 보내기)까지 했나요?') { St-Set "$($K)_TRACK" (Today) } else { $script:Skipped += "$($App.Name) 비공개 테스트 출시"; Wait-Enter; break }
  Wait-Enter 'Enter 로 다음'

  Stage '테스터에게 설치 링크 보내기'
  Step '검토가 끝나면: 비공개 테스트 → 트랙 → 테스터 탭 맨 아래 "테스터 참여 방법" → "웹에서 참여" 링크 복사'
  Note '검토가 끝나기 전엔 링크가 안 열려요. 아직이면 Enter 로 넘기고, 승인 뒤 메뉴 3번을 다시 실행하세요.'
  $optin = Ask-St "$($K)_OPTIN" '참여 링크 (https://play.google.com/apps/testing/...)'
  if ($optin) {
    Copy-Text @"
$($App.Name) 테스트 열렸어! 고마워 🙏
1) 이 링크 열고 "테스터 되기" 누르기: $optin
2) 같은 화면의 Play 스토어 링크로 설치
3) 14일 동안 지우지만 말아 줘. 가끔 열어서 써 보고, 이상한 거 있으면 한 줄만 알려 줘!
"@
  } else { $script:Skipped += "$($App.Name) 참여 링크 보내기 (승인 뒤 메뉴 3번)" }
  Wait-Enter 'Enter 로 다음'

  Stage '14일 시작일 기록'
  Say '14일은 "테스터 되기"를 누른 사람이 12명이 된 날부터 셉니다.'
  $n = Read-Host '  지금 "테스터 되기"를 누른 사람 수 (모르면 Enter)'
  if ($n -match '^\d+$' -and [int]$n -ge 12) {
    if (-not (St-Get "$($K)_START")) { St-Set "$($K)_START" (Today) }
    Show-Dday $App
  } else { Note '12명이 되는 날 메뉴 4번으로 기록하세요.' }
  Wait-Enter 'Enter 로 마무리'
}

'4' {
  $App = Pick-App
  $K = $App.Key
  $n = Read-Host '  지금 "테스터 되기"를 누른 사람 수'
  if ($n -match '^\d+$' -and [int]$n -ge 12) {
    if (-not (St-Get "$($K)_START")) { St-Set "$($K)_START" (Today) }
  } elseif ($n) {
    if (St-Get "$($K)_START") {
      Warn "12명 밑으로 떨어졌어요. 다시 12명이 되는 날부터 14일을 새로 셉니다."
      St-Set "$($K)_START" ''
    } else { Note "아직 $n 명 — 12명이 되면 다시 기록하세요." }
  }
  Show-Dday $App
  Write-Host ''
}

'5' {
  $App = Pick-App
  $K = $App.Key
  $TotalStages = 2
  Banner "$($App.Name) 프로덕션(정식 출시) 신청"

  Stage '신청 전 확인'
  Show-Dday $App
  $start = St-Get "$($K)_START"
  if (-not $start -or ([datetime]$start).AddDays(14) -gt (Get-Date)) { Warn '아직 14일이 안 됐어요. 지금 신청하면 거절돼요.' }
  Step '신청서에 "테스터 피드백 요약"과 "그걸 보고 바꾼 점"을 써야 해요.'
  Step 'Claude 에게 "리얼뽑기 프로덕션 신청 답안 써 줘" 라고 하면 테스트 기간 변경 기록으로 초안을 만들어 드려요.'
  Wait-Enter '답안이 준비되면 Enter'

  Stage '신청서 보내기'
  Open-Url 'https://play.google.com/console'
  Step "$($App.Name) → 대시보드 → '프로덕션 액세스 신청'"
  Step '세 부분(비공개 테스트 · 앱 정보 · 출시 준비)을 Claude 답안으로 채운다'
  Step '테스터 모집이 얼마나 쉬웠는지 고르기 → 제출'
  Note '검토는 보통 7일 이하. 테스터 수·참여가 부족하면 "더 테스트하세요" 가 나올 수 있어요.'
  if (Confirm-Yes '제출했나요?') { St-Set "$($K)_APPLIED" (Today) } else { $script:Skipped += "$($App.Name) 프로덕션 신청" }
  Wait-Enter 'Enter 로 마무리'
}

default { Warn '1~5 중에서 골라 주세요. 다시 실행하면 됩니다.'; exit 0 }
}

Finish
Note "진행 기록: $StateFile — 다시 실행하면 여기서 이어집니다."
