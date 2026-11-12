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
# no BOM: piping a key to wrangler with [Text.Encoding]::UTF8 put a BOM in front of the secret (2026-11-12, OpenAI 400)
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
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

# The key goes into the Cloudflare Worker secret OPENAI_KEY and the Windows user env var
# DOTLAB_OPENAI_KEY (for Claude's side-by-side test). Never into a file or git.
$WorkerDir = 'C:\DC\DD\dot-lab\works\2026-11-09-catch\worker'

$TotalStages = 3

Banner '우리 집 몬스터 잡기: AI 변신을 GPT 이미지로 (OpenAI API 키)'

Stage 'OpenAI API 키 만들기 + 충전 + 월 상한'
Say '잡은 사진을 털뭉치·피규어 그림으로 바꾸는 데 OpenAI GPT-Image 를 씁니다.'
Say '한 장 약 0.6센트(low) ~ 5센트(medium). ChatGPT/Codex 구독과는 따로 결제됩니다.'
Write-Host ''
Step '① 결제: 카드 등록 후 크레딧 충전 (최소 금액이면 충분, 보통 $5)'
Open-Url 'https://platform.openai.com/settings/organization/billing/overview'
Note '   "Auto recharge"(자동 충전)는 끈 채로 두세요. 충전한 만큼만 쓰고 멈춥니다.'
Write-Host ''
Step '② 월 상한: Limits 에서 월 예산(Monthly budget)을 $5 로'
Open-Url 'https://platform.openai.com/settings/organization/limits'
Write-Host ''
Step '③ 키 만들기: "Create new secret key" → 이름 dot-lab-catch → 만들어진 sk-... 복사'
Open-Url 'https://platform.openai.com/api-keys'
Note '   키는 이 창에서 한 번만 보입니다. 채팅에는 붙이지 마세요.'
Write-Host ''
$key = Ask-Secret 'DOTLAB_OPENAI_KEY' 'OpenAI API 키 붙여넣기 (화면에 안 보임):'
if (-not $key) { $script:Skipped += 'OpenAI 키 (입력 안 함)'; Finish; return }

Stage '키 확인 (무료)'
Say '모델 목록을 한 번 읽어 키가 맞는지만 봅니다. 그림은 만들지 않아 돈이 안 듭니다.'
$ok = $false
try {
  $r = Invoke-RestMethod -Uri 'https://api.openai.com/v1/models' -Headers @{ Authorization = "Bearer $key" } -TimeoutSec 30
  $img = @($r.data | Where-Object { $_.id -like 'gpt-image*' } | ForEach-Object { $_.id })
  Write-Host '  ✓ 키가 맞습니다.' -ForegroundColor Green
  if ($img.Count) { Note "   쓸 수 있는 이미지 모델: $($img -join ', ')" } else { Warn '이미지 모델이 목록에 없습니다. 조직 인증(Verify Organization)이 필요할 수 있습니다.' }
  $ok = $true
} catch {
  $code = $_.Exception.Response.StatusCode.value__
  if ($code -eq 401) { Warn '키가 틀렸습니다 (401). 다시 복사해서 이 wizard 를 다시 실행하세요.'; $script:Skipped += 'OpenAI 키 확인 실패 (401)'; Finish; return }
  Warn "확인 중 다른 오류 (HTTP $code). 키는 일단 저장합니다. Claude 에게 이 문구를 알려 주세요."
  $ok = $true
}
Wait-Enter '다음으로 (Enter)'

Stage '키 저장 (Worker 비밀값 + 이 PC)'
Say 'Cloudflare Worker(dot-lab-catch)의 비밀값 OPENAI_KEY 로 넣습니다. 지금 게임 동작은 바뀌지 않습니다.'
Push-Location $WorkerDir
try {
  $key | npx --yes wrangler@4 secret put OPENAI_KEY
  if ($LASTEXITCODE -eq 0) { Write-Host '  ✓ Worker OPENAI_KEY 저장됨' -ForegroundColor Green }
  else { Warn "wrangler 가 실패했습니다 (exit $LASTEXITCODE). 'npx wrangler@4 login' 후 다시 실행하세요."; $script:Skipped += 'Worker OPENAI_KEY 저장 실패' }
} finally { Pop-Location }
Say 'Claude 가 이 PC 에서 비교 그림(약 $0.2)을 뽑을 수 있게 사용자 환경변수에도 넣습니다.'
Set-UserEnv 'DOTLAB_OPENAI_KEY' $key
$key = $null
Note '이제 Claude 에게 "키 넣었어"라고 말해 주세요.'
Wait-Enter '끝 (Enter)'

Finish
