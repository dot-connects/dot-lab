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

# The key goes only into the Cloudflare Worker secret ANTHROPIC_API_KEY. Never into a file, an env var or git.
$WorkerDir = 'C:\DC\DD\dot-lab\works\2026-11-24-travel-book\worker'

$TotalStages = 3

Banner '여행 책자: 사진 고르기·글쓰기용 Claude API 키'

Stage 'Claude Console 에서 키 만들기 + 월 상한'
Say '여행 책자는 작은 미리보기 사진을 Claude(Sonnet)에게 보여 주고 고르기·캡션·에세이를 받습니다.'
Say '책 한 권 약 10~20센트. Claude 구독(Max)과는 따로, Console 크레딧에서 나갑니다.'
Say 'Worker 에도 월 10달러 상한이 걸려 있습니다.'
Write-Host ''
Step '① 월 상한: Limits 에서 이 워크스페이스(또는 조직) 월 지출 한도를 확인 (예: $20)'
Open-Url 'https://console.anthropic.com/settings/limits'
Write-Host ''
Step '② 키 만들기: "Create Key" → 이름 dot-lab-travel-book → 만들어진 sk-ant-... 복사'
Open-Url 'https://console.anthropic.com/settings/keys'
Note '   키는 이 창에서 한 번만 보입니다. 채팅에는 붙이지 마세요.'
Write-Host ''
$key = Ask-Secret 'NONE_TRAVEL_BOOK' 'Claude API 키 붙여넣기 (화면에 안 보임):'
if (-not $key) { $script:Skipped += 'Claude API 키 (입력 안 함)'; Finish; return }

Stage '키 확인 (무료)'
Say '모델 목록을 한 번 읽어 키가 맞는지만 봅니다. 돈이 들지 않습니다.'
try {
  $r = Invoke-RestMethod -Uri 'https://api.anthropic.com/v1/models' -Headers @{ 'x-api-key' = $key; 'anthropic-version' = '2023-06-01' } -TimeoutSec 30
  $ids = @($r.data | ForEach-Object { $_.id })
  Write-Host '  ✓ 키가 맞습니다.' -ForegroundColor Green
  if ($ids -contains 'claude-sonnet-5-5') { Note '   claude-sonnet-5-5 사용 가능' } else { Warn 'claude-sonnet-5-5 가 목록에 없습니다. Claude 에게 알려 주세요.' }
} catch {
  $code = $_.Exception.Response.StatusCode.value__
  if ($code -eq 401) { Warn '키가 틀렸습니다 (401). 다시 복사해서 이 wizard 를 다시 실행하세요.'; $script:Skipped += 'Claude 키 확인 실패 (401)'; Finish; return }
  Warn "확인 중 다른 오류 (HTTP $code). 키는 일단 저장합니다. Claude 에게 이 문구를 알려 주세요."
}
Wait-Enter '다음으로 (Enter)'

Stage '키 저장 (Worker 비밀값)'
Say 'Cloudflare Worker(dot-lab-travel-book)의 비밀값 ANTHROPIC_API_KEY 로 넣습니다.'
Push-Location $WorkerDir
try {
  $key | npx --yes wrangler@4 secret put ANTHROPIC_API_KEY
  if ($LASTEXITCODE -eq 0) { Write-Host '  ✓ Worker ANTHROPIC_API_KEY 저장됨' -ForegroundColor Green }
  else { Warn "wrangler 가 실패했습니다 (exit $LASTEXITCODE). 'npx wrangler@4 login' 후 다시 실행하세요."; $script:Skipped += 'Worker ANTHROPIC_API_KEY 저장 실패' }
} finally { Pop-Location }
$key = $null
Note '이제 Claude 에게 "키 넣었어"라고 말해 주세요.'
Wait-Enter '끝 (Enter)'

Finish
