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
# ──────────────────────────────────────────────────────────────────────────

# The key goes straight into the Cloudflare Worker secret (never into a file or git).
$WorkerDir = 'C:\DC\DD\dot-lab\works\2026-11-09-catch\worker'
$WorkerUrl = 'https://dot-lab-catch.dotconnects.workers.dev/transform'
$Sample = 'C:\DC\DD\dot-lab\works\2026-11-09-catch\out\s2\white-dog-cut.png'

$TotalStages = 3

Banner '우리 집 몬스터 잡기: AI 변신 켜기 (Gemini 이미지 키)'

Stage 'Gemini API 키 만들기 (결제 연결 필요)'
Say '잡은 사진을 닮은 녀석 그림으로 바꾸는 데 Google Gemini 이미지 모델을 씁니다.'
Say '한 장 약 0.07달러(약 100원). 서버가 하루 100장(기기당 5장)에서 멈춥니다.'
Open-Url 'https://aistudio.google.com/apikey'
Step '로그인 → "API 키 만들기"(Create API key) → 새 프로젝트 또는 기존 프로젝트 선택.'
Step '이미지 모델은 무료 등급에서 막힐 수 있습니다. 키 옆 "결제 설정"(Set up billing)으로 결제를 연결하세요.'
Note '이미 결제가 연결된 Gemini 키가 있으면 그 키를 써도 됩니다.'
Note '권장: Google Cloud 콘솔 → 결제 → 예산 및 알림에서 월 1만원 알림을 걸어 두세요.'
$key = Ask-Secret 'GEMINI_CATCH_KEY_UNUSED' 'Gemini API 키 붙여넣기 (화면에 안 보임):'
if (-not $key) { $script:Skipped += 'Gemini 키 (입력 안 함)'; Finish; return }

Stage 'Worker 에 키 넣기'
Say 'Cloudflare Worker(dot-lab-catch)의 비밀값 GEMINI_KEY 로 넣습니다. 파일에는 남기지 않습니다.'
Push-Location $WorkerDir
try {
  $key | npx --yes wrangler@4 secret put GEMINI_KEY
  if ($LASTEXITCODE -eq 0) { Write-Host '  ✓ GEMINI_KEY 저장됨' -ForegroundColor Green }
  else { Warn "wrangler 가 실패했습니다 (exit $LASTEXITCODE). 'npx wrangler@4 login' 후 다시 실행하세요."; $script:Skipped += 'GEMINI_KEY 저장 실패' }
} finally { Pop-Location }
$key = $null
Wait-Enter '다음으로 (Enter)'

Stage '한 장 시험 (약 100원)'
Say '테스트 사진(흰 개) 한 장을 보내 그림이 오는지 봅니다. 몇 초~30초 걸립니다.'
if (Confirm-Yes '지금 시험할까요?') {
  $b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($Sample))
  $body = @{ style = 'fluff'; kind = 'dog'; device = 'dev-wizard0001'; image = $b64 } | ConvertTo-Json -Compress
  try {
    $r = Invoke-WebRequest -Uri $WorkerUrl -Method Post -Body $body -ContentType 'application/json' -Headers @{ Origin = 'https://dot-connects.github.io' } -UseBasicParsing -TimeoutSec 120
    $j = $r.Content | ConvertFrom-Json
    $out = Join-Path $env:TEMP 'catch-wizard-test.jpg'
    [IO.File]::WriteAllBytes($out, [Convert]::FromBase64String($j.image))
    Write-Host "  ✓ 그림 받음: $out" -ForegroundColor Green
    if (Confirm-Yes '그림을 열어 볼까요?') { Start-Process $out }
  } catch {
    $msg = $_.ErrorDetails.Message; if (-not $msg) { $msg = $_.Exception.Message }
    Warn "실패: $msg"
    Note 'off = 키가 아직 안 들어감 / model = 키·결제 문제일 수 있음. Claude 에게 이 문구를 알려 주세요.'
    $script:Skipped += '시험 실패'
  }
}
Wait-Enter '끝 (Enter)'

Finish
