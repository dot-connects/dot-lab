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

# Keys live outside every git repo: %USERPROFILE%\.dot-lab\parking.env
$KeyDir = Join-Path $HOME '.dot-lab'
New-Item -ItemType Directory -Force $KeyDir | Out-Null
Set-Location $KeyDir
$EnvFile = 'parking.env'
$ContentEnv = 'C:\DC\DD\content-system\.env'

function Get-Http([string]$Url) {
  try {
    $r = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 20
    return @{ Code = [int]$r.StatusCode; Body = [string]$r.Content }
  } catch {
    $code = 0
    if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
    return @{ Code = $code; Body = [string]$_.Exception.Message }
  }
}

$TotalStages = 4

Banner '주차 층 앱: 고도·건물 데이터 키 받기 (기상청, 브이월드, 공공데이터포털)'

Stage '기상청 API허브 인증키 (지금 기압 기준값)'
Say '근처 기상 관측소의 분 단위 기압을 받아 폰 기압을 해발고도로 바꿉니다.'
Open-Url 'https://apihub.kma.go.kr/'
Step '회원가입(일반회원) 후 로그인합니다. 연락처 등록까지 해야 API가 열립니다.'
Step '오른쪽 위 마이페이지 → 인증키(authKey)를 복사합니다.'
Note '화면이 다르면 상단 메뉴에서 "인증키" 또는 "API 활용현황"을 찾으세요.'
$kma = Ask-Secret 'KMA_API_KEY' '기상청 authKey 붙여넣기:'
if ($kma) { Write-Env 'KMA_API_KEY' $kma } else { $script:Skipped += 'KMA_API_KEY (기상청 API허브)' }
Wait-Enter '다음으로 (Enter)'

Stage '브이월드 인증키 (건물 높이·층수, 주소)'
Say '좌표 하나로 그 건물의 높이, 지상층수, 지하층수를 받습니다.'
Open-Url 'https://www.vworld.kr/'
Step '회원가입 후 로그인 → 상단 "오픈API" → "인증키 발급"(인증키 관리)로 갑니다.'
Step '서비스명: 주차층테스트 / 서비스 URL: http://localhost'
Step '활용 API는 전부 체크(특히 2D 데이터 API, 지오코더 API)하고 발급합니다.'
Step '발급된 인증키를 복사합니다.'
$vw = Ask-Secret 'VWORLD_KEY' '브이월드 인증키 붙여넣기:'
if ($vw) { Write-Env 'VWORLD_KEY' $vw } else { $script:Skipped += 'VWORLD_KEY (브이월드)' }
Wait-Enter '다음으로 (Enter)'

Stage '공공데이터포털 건축물대장 활용신청 (교차 확인용)'
Say '새 키는 필요 없습니다. 기존 공공데이터포털 키에 권한만 붙입니다.'
Open-Url 'https://www.data.go.kr/data/15134735/openapi.do'
Step '로그인(기존 키를 만든 계정) → "활용신청" → 목적: 개인 연구 → 신청.'
Note '활용신청은 PC 화면에서만 됩니다. 보통 자동승인이고, 반영까지 몇 분~1시간 걸릴 수 있습니다.'
$dg = ''
if (Test-Path $ContentEnv) {
  $line = Get-Content $ContentEnv -Encoding UTF8 | Where-Object { $_ -like 'DATA_GO_KR_KEY=*' } | Select-Object -Last 1
  if ($line) { $dg = $line.Substring('DATA_GO_KR_KEY='.Length) }
}
if ($dg) {
  Write-Env 'DATA_GO_KR_KEY' $dg
  Note '(content-system 에 있던 키를 그대로 복사했습니다. 화면에 보여주지 않습니다.)'
} else {
  $dg = Ask-Secret 'DATA_GO_KR_KEY' '공공데이터포털 일반 인증키(Decoding) 붙여넣기:'
  if ($dg) { Write-Env 'DATA_GO_KR_KEY' $dg } else { $script:Skipped += 'DATA_GO_KR_KEY (공공데이터포털)' }
}
Wait-Enter '신청을 마쳤으면 Enter'

Stage '키 확인'
Say '키마다 한 번씩 불러 봅니다. 실패해도 저장한 키는 지우지 않습니다.'
$kma = _Existing 'KMA_API_KEY'
if ($kma) {
  $r = Get-Http "https://apihub.kma.go.kr/api/typ01/cgi-bin/url/nph-aws2_min?stn=0&disp=1&help=1&authKey=$kma"
  if ($r.Code -eq 200) { Write-Host '  ✓ 기상청: 응답 받음' -ForegroundColor Green }
  else { Warn "기상청: HTTP $($r.Code). 연락처 등록·API 신청 여부를 확인하세요."; $script:Skipped += '기상청 키 확인 실패' }
}
$vw = _Existing 'VWORLD_KEY'
if ($vw) {
  $r = Get-Http "https://api.vworld.kr/req/address?service=address&request=getAddress&type=PARCEL&point=127.0286,37.2636&domain=http://localhost&key=$vw"
  if ($r.Body -match '"status"\s*:\s*"OK"') { Write-Host '  ✓ 브이월드: 주소 조회 됨' -ForegroundColor Green }
  else { Warn "브이월드: 응답이 OK가 아님 (HTTP $($r.Code)). 서비스 URL·활용 API 체크를 확인하세요."; $script:Skipped += '브이월드 키 확인 실패' }
}
$dg = _Existing 'DATA_GO_KR_KEY'
if ($dg) {
  $enc = [uri]::EscapeDataString($dg)
  $r = Get-Http "https://apis.data.go.kr/1613000/BldRgstHubService/getBrTitleInfo?serviceKey=$enc&sigunguCd=41111&bjdongCd=10100&numOfRows=1&_type=json"
  if ($r.Code -eq 200 -and $r.Body -notmatch 'SERVICE_KEY|Unauthorized|등록되지') { Write-Host '  ✓ 건축물대장: 응답 받음' -ForegroundColor Green }
  else { Warn "건축물대장: HTTP $($r.Code). 승인 반영 전일 수 있습니다. 1시간 뒤 다시 실행해 보세요."; $script:Skipped += '건축물대장 확인 (승인 대기일 수 있음)' }
}
Note "키 파일: $KeyDir\parking.env (git 밖)"
Wait-Enter '끝 (Enter)'

Finish
