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

# Firebase for the parking app family push. Writes app/firebase.properties (gitignored) from google-services.json
# and stores the service account as the Worker secret FCM_SA. Neither file goes into git.
$App = 'C:\DC\DD\dot-lab\works\2026-11-02-parking-floor\app'
$Worker = 'C:\DC\DD\dot-lab\works\2026-11-02-parking-floor\worker'
$Downloads = Join-Path $HOME 'Downloads'

function Pick-File([string]$Prompt, [string]$Pattern) {
  $guess = Get-ChildItem $Downloads -Filter $Pattern -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
  if ($guess) { Note "다운로드 폴더에서 찾은 파일: $($guess.FullName)" }
  $p = Read-Host "  $Prompt [Enter = 위 파일]"
  if (-not $p -and $guess) { $p = $guess.FullName }
  if ($p) { $p = $p.Trim('"') }
  if (-not $p -or -not (Test-Path $p)) { Warn '파일을 찾지 못했습니다.'; return $null }
  return $p
}

$TotalStages = 4

Banner '주차층: 가족 알림(Firebase) 연결'

Stage 'Firebase 프로젝트 만들기'
Say '가족 폰을 바로 깨우는 알림 통로입니다. 무료이고, 계정은 지금 쓰는 구글 계정이면 됩니다.'
Open-Url 'https://console.firebase.google.com/'
Step '"프로젝트 만들기" → 이름: dot-lab-parking → 계속.'
Step 'Google 애널리틱스는 꺼도 됩니다(끄기) → 프로젝트 만들기 → 계속.'
Wait-Enter '프로젝트가 만들어졌으면 Enter'

Stage '안드로이드 앱 등록 → google-services.json'
Step '프로젝트 첫 화면에서 안드로이드 아이콘(앱 추가) 클릭.'
Step 'Android 패키지 이름: kr.dot.parkingfloor  (닉네임은 주차층, SHA-1은 비워 둠) → 앱 등록.'
Step '"google-services.json 다운로드" 버튼을 누르고, 나머지 단계는 "다음"으로 넘겨 끝냅니다.'
$gs = Pick-File 'google-services.json 경로' 'google-services*.json'
if ($gs) {
  $j = Get-Content $gs -Raw -Encoding UTF8 | ConvertFrom-Json
  $client = $j.client | Where-Object { $_.client_info.android_client_info.package_name -eq 'kr.dot.parkingfloor' } | Select-Object -First 1
  if (-not $client) { $client = $j.client | Select-Object -First 1 }
  $lines = @(
    "apiKey=$($client.api_key[0].current_key)",
    "appId=$($client.client_info.mobilesdk_app_id)",
    "projectId=$($j.project_info.project_id)",
    "senderId=$($j.project_info.project_number)"
  )
  [IO.File]::WriteAllLines((Join-Path $App 'firebase.properties'), [string[]]$lines)
  $script:Written += 'firebase.properties'
  Write-Host '  ✓ 앱 설정 저장 (git 에 올라가지 않음)' -ForegroundColor Green
} else { $script:Skipped += 'google-services.json → firebase.properties' }
Wait-Enter '다음으로 (Enter)'

Stage '서비스 계정 키 → Cloudflare 비밀값'
Say 'Cloudflare 서버가 Firebase 로 알림을 보낼 때 쓰는 키입니다. Cloudflare 에만 넣고 PC 의 파일은 지웁니다.'
Step 'Firebase 콘솔 왼쪽 위 톱니바퀴 → 프로젝트 설정 → "서비스 계정" 탭.'
Step '"새 비공개 키 생성" → 키 생성 → JSON 파일이 다운로드됩니다.'
$sa = Pick-File '서비스 계정 JSON 경로' '*firebase-adminsdk*.json'
if ($sa) {
  Push-Location $Worker
  try {
    Get-Content $sa -Raw -Encoding UTF8 | & npx --yes wrangler@4 secret put FCM_SA
    if ($LASTEXITCODE -eq 0) {
      Write-Host '  ✓ Cloudflare 에 FCM_SA 저장' -ForegroundColor Green
      $script:Written += 'FCM_SA (Cloudflare)'
      if (Confirm-Yes '키 파일을 PC 에서 지울까요? (권장)') { Remove-Item $sa; Note '지웠습니다.' }
    } else { Warn 'wrangler 저장 실패. 위 메시지를 Claude 에게 보여 주세요.'; $script:Skipped += 'FCM_SA' }
  } finally { Pop-Location }
} else { $script:Skipped += 'FCM_SA' }
Wait-Enter '다음으로 (Enter)'

Stage '끝 → Claude 에게 알리기'
Say 'Claude 에게 "파이어베이스 끝" 이라고 말하면 알림이 들어간 APK 를 다시 빌드해 보냅니다.'
Note 'google-services.json 은 다운로드 폴더에 남아 있어도 괜찮습니다(공개돼도 되는 앱 식별값).'
Wait-Enter '끝 (Enter)'

Finish
