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
# Testers come from ACT Party (kr.testerparty.tester_party, actparty.app): a free 1:1 exchange board.
# It needs a live closed test (opt-in link or package name), so recruiting starts after menu 3.
# Read 2026-11-16: actparty.app how.html, guides/google-group.html, guides/production-form.html, terms.html.

function Notes-File($App) { Join-Path $env:USERPROFILE ".dot-play-feedback-$($App.Key).txt" }

Clear-Host
Write-Host "`n  Google Play 출시 위자드 — 리얼뽑기 · 따로 같이 네컷 · 주차 앱 (계정 하나, 테스트 그룹 하나)" -ForegroundColor Cyan
Note "진행 기록: $StateFile (비밀 정보 없음)"
Write-Host ''
$dev = St-Get 'DEV_NAME'; $grp = St-Get 'TESTER_GROUP'
Say ("개발자 계정: " + $(if (St-Get 'SIGNUP_DATE') { "$dev ($(St-Get 'SIGNUP_DATE') 가입)" } else { '아직' }))
Say ("테스트 그룹: " + $(if ($grp) { $grp } else { '아직' }))
foreach ($k in '1','2','3') {
  $a = $Apps[$k]
  if (St-Get "$($a.Key)_TRACK") {
    $t = St-Get "$($a.Key)_TRADES"
    if ($t) { Say "$($a.Name): ACT Party 맞교환 $t 건" }
    Show-Dday $a
  }
}
Write-Host ''
Say '1) 개발자 계정 만들기            — 지금 (5단계)'
Say '2) 테스트 그룹 만들기            — 지금, 1번과 같이 해도 됨 (2단계)'
Say '3) 앱 비공개 테스트 시작         — Claude 가 AAB(업로드용 앱 파일)를 만든 뒤 (3단계)'
Say '4) ACT Party 로 테스터 맞교환    — 3번 검토가 끝나 참여 링크가 나온 뒤 (4단계)'
Say '5) 참여 인원 기록 · 14일 날짜   — 사람이 늘거나 줄 때마다'
Say '6) 프로덕션(정식 출시) 신청      — 12명 × 14일이 지난 뒤 (2단계)'
$part = Read-Host "`n  번호"

switch ($part) {

'1' {
  $TotalStages = 5
  Banner 'Google Play 개인 개발자 계정 만들기'

  Stage '준비물 확인'
  Say '시작 전에 아래 5가지가 있어야 해요.'
  Step '계정 주인이 될 Google 계정 — 나중에 옮기기 어려우니 계속 쓸 개인 Gmail 로 (회사·학교 Workspace 계정 X)'
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
  Note '검토는 며칠 걸릴 수 있어요. 기다리는 동안 다음 단계와 메뉴 2번(테스트 그룹)을 먼저 해도 됩니다.'
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
  $TotalStages = 2
  Banner '테스트 그룹 만들기 (세 앱 공용 Google 그룹 하나)'

  Stage 'Google 그룹 만들기'
  Say 'ACT Party 상대와 지인 모두 이 그룹에 가입하면 테스터가 돼요. 세 앱 테스트에 그룹 이메일만 넣으면 끝이에요.'
  Open-Url 'https://groups.google.com/my-groups'
  Step "계정 주인($(St-Get 'OWNER_ACCOUNT')) 개인 Gmail 로 로그인 (Workspace 계정 그룹은 밖에서 가입 불가)"
  Step '"그룹 만들기" → 이름: 예) D:OT 테스터 · 그룹 이메일: 예) dot-testers'
  Step '"그룹을 검색할 수 있는 사용자" = "웹의 모든 사용자"'
  Step '"그룹에 가입할 수 있는 사용자" = "웹의 모든 사용자가 가입 가능" (요청 승인 방식 X — 한 명씩 승인해야 해요)'
  Step '나머지는 기본값 → 그룹 만들기'
  Note '이미 만들었다면 그룹 설정 → 일반에서 위 두 칸만 확인하세요. 비공개로 두면 상대에게 "콘텐츠를 사용할 수 없음" 이 떠요.'
  Write-Host ''
  $grp = Ask-St 'TESTER_GROUP' '그룹 이메일 (예: dot-testers@googlegroups.com)'
  if (-not $grp) { $script:Skipped += '테스트 그룹 이메일'; Wait-Enter; break }
  St-Set 'TESTER_JOIN' ("https://groups.google.com/g/" + ($grp -split '@')[0])
  Wait-Enter 'Enter 로 다음'

  Stage '지인 테스터 (선택)'
  Say '주 테스터는 ACT Party 맞교환(메뉴 4번)이에요. 안드로이드 쓰는 지인이 있으면 같이 넣으면 맞교환 수가 줄어요.'
  Say '지인에게는 앱이 열린 뒤(메뉴 3번 끝) 참여 링크와 함께 보내는 게 한 번에 끝나서 편해요.'
  if (Confirm-Yes '지금 지인에게 그룹 가입부터 부탁할까요? (초대 글을 클립보드에 복사)') {
    Copy-Text @"
부탁 하나만 할게! 내가 만든 안드로이드 앱을 Play 스토어에 올리려는데 테스터가 필요해.
1) 이 링크로 그룹 가입 (Play 스토어 쓰는 그 Google 계정으로): $(St-Get 'TESTER_JOIN')
2) 앱이 열리면 설치 링크를 따로 보낼게. 설치하고 14일 동안만 지우지 말아 줘!
(안드로이드 폰만 돼)
"@
  }
  Wait-Enter 'Enter 로 마무리'
}

'3' {
  $App = Pick-App
  $K = $App.Key
  $TotalStages = 3
  Banner "$($App.Name) 비공개 테스트 시작"

  Stage '앱 만들기'
  if (-not (St-Get 'TESTER_GROUP')) { Warn '테스트 그룹이 아직 없어요 — 메뉴 2번 먼저.' }
  Open-Url 'https://play.google.com/console'
  Step '"앱 만들기" 버튼'
  Step "앱 이름: $($App.Name) · 기본 언어: 한국어 – ko-KR · 앱/게임: $($App.Kind) · 무료"
  Step '정책 동의 체크 2개 → 앱 만들기'
  Note "패키지 이름($($App.Pkg))은 첫 AAB 를 올릴 때 정해지고 바꿀 수 없어요. Claude 가 만든 AAB 만 올리세요."
  St-Set "$($K)_APP" (Today)
  Wait-Enter '앱이 만들어지면 Enter'

  Stage '비공개 테스트 트랙 + AAB 올리기'
  Step '왼쪽 "테스트 및 출시" → "테스트" → "비공개 테스트" → 기본 트랙(알파) "트랙 관리"'
  Step "테스터 탭: 'Google 그룹' 칸에 $(St-Get 'TESTER_GROUP') 전체 주소 입력 → 칩으로 바뀌면 저장 · 피드백 이메일 입력"
  Step '국가/지역 탭: "모든 국가/지역" 추가 → 저장 (ACT Party 상대는 해외에도 있어요. 빠지면 "내 나라에서 사용 불가" 로 조용히 탈락)'
  Step '"새 버전 만들기" → "Play 앱 서명" 사용(Google 이 서명 키 보관) → 계속'
  $aab = Ask-St "$($K)_AAB" 'AAB 파일 경로 (Claude 가 알려 준 경로)' $App.Aab
  if ($aab -and -not (Test-Path $aab)) { Warn "그 경로에 파일이 없어요: $aab — Claude 에게 AAB 위치를 물어보세요." }
  Step 'AAB 파일을 끌어다 놓기 → 출시 노트: "첫 테스트 버전" → 다음 → 저장'
  Step '"버전 검토" → 오류가 없으면 "비공개 테스트 트랙에 출시 시작"'
  Step '"게시 개요"에서 변경사항을 검토로 보내기 (테스터·국가 변경도 여기서 보내야 적용돼요)'
  Write-Host ''
  Warn '대시보드 "앱 설정" 할 일(개인정보 처리방침 URL · 앱 액세스 · 광고 · 콘텐츠 등급 · 타겟층 · 데이터 보안 등)이 남아 있으면 검토로 못 보내요.'
  Note '그 답안은 Claude 가 미리 문서로 준비해 둡니다. 막히는 칸은 캡처해서 보내 주세요. 첫 검토는 며칠 걸릴 수 있어요.'
  if (Confirm-Yes '검토 보내기까지 했나요?') { St-Set "$($K)_TRACK" (Today) } else { $script:Skipped += "$($App.Name) 비공개 테스트 출시"; Wait-Enter; break }
  Wait-Enter 'Enter 로 다음'

  Stage '참여 링크 받기'
  Step '검토가 끝나면: 비공개 테스트 → 트랙 → 테스터 탭 맨 아래 "테스터 참여 방법" → "웹에서 참여" 링크 복사'
  Note '검토가 끝나기 전엔 링크가 안 열려요. 아직이면 Enter 로 넘기고, 승인 뒤 메뉴 3번 → 이 단계만 다시 하세요.'
  $optin = Ask-St "$($K)_OPTIN" '참여 링크 (https://play.google.com/apps/testing/...)'
  if ($optin) {
    Say '이 링크로 메뉴 4번(ACT Party 등록)을 하면 됩니다.'
    if (Confirm-Yes '지인에게 보낼 설치 안내 글도 복사할까요?') {
      Copy-Text @"
$($App.Name) 테스트 열렸어! 고마워 🙏
1) 그룹 가입 (아직이면): $(St-Get 'TESTER_JOIN')
2) 이 링크 열고 "테스터 되기": $optin
3) 같은 화면의 Play 스토어 링크로 설치 → 14일 동안 지우지 말기. 이상한 거 있으면 한 줄만 알려 줘!
"@
    }
  } else { $script:Skipped += "$($App.Name) 참여 링크 (승인 뒤 메뉴 3번)" }
  Wait-Enter 'Enter 로 마무리'
}

'4' {
  $App = Pick-App
  $K = $App.Key
  $TotalStages = 4
  Banner "$($App.Name) 테스터 맞교환 (ACT Party)"

  Stage 'ACT Party 앱 설치'
  if (-not (St-Get "$($K)_OPTIN")) { Warn '참여 링크가 아직 없어요 — 메뉴 3번을 끝낸 뒤 오세요. (등록에 참여 링크나 패키지 이름이 필요해요)' }
  Say 'ACT Party = 개발자끼리 서로의 비공개 테스트에 참여해 주는 무료 맞교환 앱이에요. 유료 기능 없음.'
  Open-Url 'https://play.google.com/store/apps/details?id=kr.testerparty.tester_party'
  Step 'PC 에서 열면 "설치" → 폰 선택, 또는 폰 Play 스토어에서 "ACT Party" 검색'
  Step '앱을 열고 안내대로 가입·로그인'
  Step '"사용 정보 접근(Usage access)" 허용 요청이 나오면 허용 — 상대 앱을 매일 열었는지 이걸로 자동 기록해요'
  Note '웹 콘솔도 있어요: https://actparty.app  (앱 안 화면 이름은 아직 직접 확인 못 함 — 다르면 캡처해서 Claude 에게)'
  Wait-Enter '가입이 끝나면 Enter'

  Stage '내 앱 등록'
  Open-Url 'https://actparty.app/console.html?new=1'
  Step '앱 등록(새 앱)에 아래 둘 중 하나를 넣는다:'
  Say "    참여 링크: $(St-Get "$($K)_OPTIN")"
  Say "    패키지 이름: $($App.Pkg)"
  Step '설명은 짧게: 어떤 앱인지 한 줄 + "매일 한 번 열어 주세요"'
  Warn 'Play 스토어 리뷰·별점을 서로 써 주자는 요청은 금지예요 (Play 정책 위반, ACT Party 약관 위반).'
  Wait-Enter '등록이 끝나면 Enter'

  Stage '맞교환하기 (14~15건)'
  Say '1:1 맞교환이에요: 상대가 내 앱을 14일 테스트하면, 나도 상대 앱을 14일 테스트해요.'
  Step '게시판에서 상대 앱을 골라 교환 제안 → 또는 내 앱에 온 제안을 수락'
  Step '상대 앱: 상대 그룹 가입 → 상대 참여 링크에서 "테스터 되기" → Play 스토어에서 설치 → 한 번 열기'
  Step '14일 동안 지우지 않고, 하루 한 번 열기 (ACT Party 가 자동 기록, 상대에게 내 기록이 보여요)'
  Step '12명 + 여유로 14~15건을 목표로 — 한 명이 빠지면 그 사람 대신 들어온 사람이 14일을 새로 채워야 해요'
  Write-Host ''
  Warn "내 폰에 남의 앱 14~15개를 14일 동안 둬야 해요. 중간에 지우면 상대도 빠질 수 있고 내 평판(카르마)이 깎여요."
  Note '한 번의 교환으로 우리 앱 세 개를 같이 테스트받을 수 있는지는 페이지에 없어요 — 리얼뽑기부터 하고, 상대에게 물어보세요.'
  $t = Ask-St "$($K)_TRADES" '지금까지 성사된 교환 수'
  Wait-Enter 'Enter 로 다음'

  Stage '피드백 모으기 (정식 출시 신청에 필요)'
  Say '정식 출시 신청서에 "테스터 피드백 요약"과 "그걸 보고 바꾼 점"을 써야 해요 (각 300자).'
  Step 'ACT Party 채팅·피드백으로 받은 말을 날짜와 함께 메모 파일에 적어 두세요'
  Step 'Claude 에게 "피드백 메모 반영해 줘" 하면 고칠 건 고치고, 바꾼 점을 같은 파일에 적어 둘게요'
  $nf = Notes-File $App
  if (-not (Test-Path $nf)) { [IO.File]::WriteAllText($nf, "# $($App.Name) 테스터 피드백 (날짜 · 누가 · 내용)`r`n`r`n") }
  Note "메모 파일: $nf"
  if (Confirm-Yes '메모 파일을 메모장으로 열까요?') { Start-Process notepad.exe $nf }
  Write-Host ''
  Say '상대 12명이 "테스터 되기"를 누른 날, 메뉴 5번으로 기록하면 신청 가능 날짜가 나와요.'
  Wait-Enter 'Enter 로 마무리'
}

'5' {
  $App = Pick-App
  $K = $App.Key
  Say 'Play Console → 비공개 테스트 → 트랙에서 참여(opt-in)한 테스터 수를 보거나, ACT Party 진행 중 교환 수 + 지인 수로 셉니다.'
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

'6' {
  $App = Pick-App
  $K = $App.Key
  $TotalStages = 2
  Banner "$($App.Name) 프로덕션(정식 출시) 신청"

  Stage '신청 전 확인'
  Show-Dday $App
  $start = St-Get "$($K)_START"
  if (-not $start -or ([datetime]$start).AddDays(14) -gt (Get-Date)) { Warn '아직 14일이 안 됐어요. 지금 신청하면 거절돼요.' }
  Say '신청서는 3부분 9문항, 자유 입력은 각 300자예요. 결정적인 건 두 개:'
  Step '"테스터에게 받은 피드백 요약과 모은 방법"'
  Step '"비공개 테스트로 알게 된 것을 바탕으로 앱을 어떻게 바꿨는지"'
  Note "피드백 메모: $(Notes-File $App)"
  Step "Claude 에게 `"$($App.Name) 프로덕션 신청 답안 써 줘`" — 메모와 테스트 기간 변경 기록으로 9문항 초안을 만들어 드려요."
  Wait-Enter '답안이 준비되면 Enter'

  Stage '신청서 보내기'
  Open-Url 'https://play.google.com/console'
  Step "$($App.Name) → 대시보드 → '프로덕션 액세스 신청'"
  Step '세 부분(비공개 테스트 · 앱 정보 · 출시 준비)을 Claude 답안으로 채운다'
  Step '테스터 모집 난이도: 사실대로 (테스터 교환 앱으로 모았다고 써도 돼요)'
  Note '검토는 보통 7일 이하. 거절되면 다시 신청하면 되고, 테스터들의 참여 기간은 그대로 유지돼요.'
  if (Confirm-Yes '제출했나요?') { St-Set "$($K)_APPLIED" (Today) } else { $script:Skipped += "$($App.Name) 프로덕션 신청" }
  Wait-Enter 'Enter 로 마무리'
}

default { Warn '1~6 중에서 골라 주세요. 다시 실행하면 됩니다.'; exit 0 }
}

Finish
Note "진행 기록: $StateFile — 다시 실행하면 여기서 이어집니다."
