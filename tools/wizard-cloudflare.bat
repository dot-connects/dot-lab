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

$TotalStages = 4

Banner 'D:OT lab · Cloudflare 무료 계정 준비 (따로 같이 네컷 저장소)'

Stage '계정 만들기'
Say '네컷 사진·영상을 7일 동안 맡아 둘 저장소용 계정입니다. 무료 범위 안에서 씁니다.'
Open-Url 'https://dash.cloudflare.com/sign-up'
Step '이메일과 비밀번호로 가입하세요. (비밀번호는 이 창에 입력하지 않습니다)'
Step '받은 편지함에서 Cloudflare 인증 메일의 링크를 누르세요.'
Step '"사이트 추가/도메인 연결" 같은 화면이 나오면 건너뛰어도 됩니다. 도메인은 필요 없습니다.'
if (-not (Confirm-Yes '가입과 이메일 인증을 마쳤나요?')) { $script:Skipped += '계정 가입·이메일 인증'; Finish; return }

Stage '저장소(R2) 켜기'
Open-Url 'https://dash.cloudflare.com/?to=/:account/r2/overview'
Step '왼쪽 메뉴의 R2 (Object Storage) 화면에서 시작/구매 버튼을 누르세요.'
Step '결제 수단(카드) 등록을 요구합니다. 무료 범위(월 10GB 저장)를 넘을 때만 청구됩니다.'
Step '요금제는 무료 기본(Standard)을 그대로 두세요.'
Note '화면 문구는 조금 다를 수 있습니다. 막히면 화면을 캡처해서 Claude 에게 보내 주세요.'
Note '저장 공간(버킷)과 7일 자동 삭제 설정은 Claude 가 나중에 합니다. 여기서 만들 필요 없습니다.'
if (-not (Confirm-Yes 'R2 를 켰나요? (R2 개요 화면이 보이면 됩니다)')) { $script:Skipped += 'R2 켜기(결제 수단 등록)' }

Stage '청구 알림 (선택)'
Say '혹시 무료 범위를 넘으면 메일로 알려 주는 안전장치입니다.'
Open-Url 'https://dash.cloudflare.com/?to=/:account/notifications'
Step '알림 추가(Add) 를 누르고 목록에서 사용량 청구(Usage Based Billing) 항목을 찾으세요.'
Step '보이면 R2 저장소를 골라 기준을 가장 낮게 두고 저장하세요.'
Note '항목이 없거나 헷갈리면 건너뛰어도 됩니다. Claude 가 서버 쪽에서 크기 제한을 따로 겁니다.'
if (-not (Confirm-Yes '알림을 만들었나요?')) { $script:Skipped += '청구 알림 (선택)' }

Stage 'Claude 가 배포할 수 있게 연결 (wrangler 로그인)'
Say 'Cloudflare 공식 도구(wrangler)로 이 PC 를 계정에 연결합니다.'
Say '실행하면 브라우저에 허용 화면이 열리고, Allow 를 누르면 끝납니다. 비밀번호는 여기 입력하지 않습니다.'
if (-not (Get-Command npx -ErrorAction SilentlyContinue)) {
  Warn 'npx(Node.js) 가 없습니다. Claude 에게 말해 주세요.'
  $script:Skipped += 'wrangler 로그인 (Node.js 없음)'
} elseif (Confirm-Yes '지금 wrangler 로그인을 실행할까요? (브라우저가 열립니다)') {
  & npx --yes wrangler@4 login
  Say ''
  Say '연결 확인:'
  & npx --yes wrangler@4 whoami
  if (-not (Confirm-Yes '위에 계정 이메일과 Account ID 가 보이나요?')) { $script:Skipped += 'wrangler 로그인 확인' }
} else { $script:Skipped += 'wrangler 로그인' }

Finish
Say '끝났으면 Claude 에게 "클라우드플레어 끝" 이라고 말해 주세요. 나머지 설정은 Claude 가 합니다.'
