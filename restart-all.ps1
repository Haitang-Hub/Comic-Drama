<#
.SYNOPSIS
  Comic Drama AI - Restart All Services
.DESCRIPTION
  Stop all running services, optionally rebuild backend, then start in order:
    Task -> Workflow -> Resource -> Gateway -> Frontend -> Mock
.PARAMETER SkipBuild
  Skip Maven build
.PARAMETER SkipMock
  Skip Mock model server
.EXAMPLE
  .\restart-all.ps1
  .\restart-all.ps1 -SkipBuild
#>
param(
  [switch]$SkipBuild,
  [switch]$SkipMock
)

$ErrorActionPreference = 'Continue'
$RepoRoot  = $PSScriptRoot
$BackendRoot = Join-Path $RepoRoot 'comic-drama'
$FrontendRoot = Join-Path $RepoRoot 'comic-drama-frontend'

$WINDOW_PREFIX = 'ComicDrama'

$services = @(
  @{ Name = 'comic-task-service';     Title = 'Task';     Port = 8103 }
  @{ Name = 'comic-workflow-service'; Title = 'Workflow'; Port = 8104 }
  @{ Name = 'comic-resource-service'; Title = 'Resource'; Port = 8105 }
  @{ Name = 'comic-gateway';          Title = 'Gateway';  Port = 8070 }
)
$frontendPort = 5170
$mockPort = 9876

function Write-Step($m) { Write-Host "`n[$m]" -ForegroundColor Cyan }
function Write-OK($m)   { Write-Host "  [OK] $m" -ForegroundColor Green }
function Write-W2($m)   { Write-Host "  [!]  $m" -ForegroundColor Yellow }
function Write-Err($m)  { Write-Host "  [X]  $m" -ForegroundColor Red }

function Wait-Port-Ready($port, $name, $timeoutSec = 90) {
  $sw = [System.Diagnostics.Stopwatch]::StartNew()
  while ($sw.Elapsed.TotalSeconds -lt $timeoutSec) {
    $ok = (Test-NetConnection -ComputerName 127.0.0.1 -Port $port -WarningAction SilentlyContinue).TcpTestSucceeded
    if ($ok) { Write-OK "$name (:$port) ready"; return $true }
    Start-Sleep -Seconds 2
  }
  Write-W2 "$name (:$port) wait timeout"
  return $false
}

function Find-ServiceJar($svcName) {
  $jarDir = Join-Path $BackendRoot "$svcName\target"
  if (-not (Test-Path $jarDir)) { return $null }
  $jars = Get-ChildItem -Path $jarDir -Filter "*.jar" -ErrorAction SilentlyContinue
  foreach ($j in $jars) {
    if ($j.Name -like "$svcName*.jar" -and $j.Name -notlike '*sources*' -and $j.Name -notlike '*javadoc*') {
      return $j
    }
  }
  return $null
}

function Stop-AllServiceWindows {
  param([switch]$Wait)
  # 1. Kill by window title prefix
  $wins = Get-Process -Name cmd,powershell,pwsh -ErrorAction SilentlyContinue | Where-Object {
    $_.MainWindowTitle -and $_.MainWindowTitle.StartsWith($WINDOW_PREFIX)
  }
  foreach ($w in $wins) {
    try { Stop-Process -Id $w.Id -Force -ErrorAction Stop; Write-OK "Window closed: $($w.MainWindowTitle)" }
    catch { Write-W2 "Failed to close window: $($w.MainWindowTitle)" }
  }
  # 2. Kill by port
  $allPorts = ($services | ForEach-Object { $_.Port }) + $frontendPort + $mockPort
  foreach ($port in $allPorts) {
    $conns = netstat -ano | Select-String ":$port\s+.*LISTENING"
    foreach ($line in $conns) {
      $pidStr = ($line -split '\s+')[-1].Trim()
      if ($pidStr -match '^\d+$' -and $pidStr -ne '0') {
        try { Stop-Process -Id ([int]$pidStr) -Force -ErrorAction Stop; Write-OK "Port $port (PID $pidStr) stopped" }
        catch { Write-W2 "Port $port (PID $pidStr) stop failed" }
      }
    }
  }
  if ($Wait) { Start-Sleep -Seconds 3 }
}

function Start-CmdWindow {
  param(
    [string]$Title,
    [string]$WorkDir,
    [string]$Cmd
  )
  $fullTitle = "$WINDOW_PREFIX-$Title"
  # Use cmd /k to avoid PowerShell nested quote issues
  # title sets window title, cd /d changes drive+dir, then run command
  $argStr = "/k title $fullTitle & cd /d `"$WorkDir`" & $Cmd"
  Start-Process cmd -ArgumentList $argStr
}

Write-Step 'Paths'
Write-Host "  RepoRoot:     $RepoRoot" -ForegroundColor Gray
Write-Host "  BackendRoot:  $BackendRoot" -ForegroundColor Gray
Write-Host "  FrontendRoot: $FrontendRoot" -ForegroundColor Gray
if (-not (Test-Path $BackendRoot))  { Write-Err "BackendRoot not found"; exit 1 }
if (-not (Test-Path $FrontendRoot)) { Write-Err "FrontendRoot not found"; exit 1 }
Write-OK 'Paths verified'

Write-Step 'Stop all existing service windows and processes'
Stop-AllServiceWindows -Wait

Write-Step 'Environment check'
$mysqlOk = (Test-NetConnection -ComputerName 127.0.0.1 -Port 3306 -WarningAction SilentlyContinue).TcpTestSucceeded
if ($mysqlOk) { Write-OK 'MySQL (3306)' } else { Write-Err 'MySQL not running'; exit 1 }
$node = Get-Command node -ErrorAction SilentlyContinue
if ($node) { Write-OK "Node.js $(node -v)" } else { Write-Err 'Node.js not installed'; exit 1 }
$py = Get-Command python -ErrorAction SilentlyContinue
if ($py) { Write-OK 'Python' } else { Write-W2 'Python not installed, skip Mock'; $SkipMock = $true }
$jcmd = Get-Command java -ErrorAction SilentlyContinue
if ($jcmd) { Write-OK 'Java' } else { Write-Err 'Java not installed'; exit 1 }

if (-not $SkipBuild) {
  Write-Step 'Maven build'
  Push-Location $BackendRoot
  & mvn clean install -DskipTests "-Duser.language=en" "-Duser.country=US"
  $mvnExit = $LASTEXITCODE
  Pop-Location
  if ($mvnExit -ne 0) { Write-Err 'Maven build failed'; exit 1 }
  Write-OK 'Maven build done'
} else {
  Write-W2 'Skip Maven build'
  $missing = $false
  foreach ($svc in $services) {
    $j = Find-ServiceJar $svc.Name
    if (-not $j) { $missing = $true; break }
  }
  if ($missing) {
    Write-W2 'Jar missing, run mvn install'
    Push-Location $BackendRoot
    & mvn install -DskipTests "-Duser.language=en" "-Duser.country=US"
    Pop-Location
    Write-OK 'Build done'
  }
}

Write-Step 'Start backend services'
foreach ($svc in $services) {
  $jar = Find-ServiceJar $svc.Name
  if (-not $jar) {
    Write-Err "$($svc.Name) jar not found"
    continue
  }
  Write-Host "  [START] $($svc.Name) ($($svc.Title)) :$($svc.Port)" -ForegroundColor Green
  Start-CmdWindow -Title $svc.Title -WorkDir $BackendRoot -Cmd "java -jar `"$($jar.FullName)`""
  Start-Sleep -Seconds 3
}
Write-OK 'Backend services started'

Write-Step 'Start frontend Vite'
if (-not (Test-Path (Join-Path $FrontendRoot 'node_modules'))) {
  Write-W2 'node_modules missing, run npm install'
  Push-Location $FrontendRoot
  & npm install --no-audit --no-fund
  Pop-Location
}
# 启动前端前确保网关(8070)已就绪，避免页面打开时过早请求报 ECONNREFUSED/服务器错误
Write-Step 'Wait for Gateway ready (before start frontend)'
Wait-Port-Ready 8070 'Gateway' 90 | Out-Null
Start-CmdWindow -Title 'Frontend' -WorkDir $FrontendRoot -Cmd 'npm run dev'
Write-OK "Frontend started (port $frontendPort)"

if (-not $SkipMock) {
  Write-Step 'Start Mock model server'
  $mockScript = Join-Path $RepoRoot 'scripts\mock_model_server.py'
  if (Test-Path $mockScript) {
    Start-CmdWindow -Title 'Mock' -WorkDir $RepoRoot -Cmd "python `"$mockScript`" --port $mockPort --key mock-test-key-12345"
    Write-OK "Mock server started (port $mockPort)"
  } else {
    Write-W2 "Mock script not found: $mockScript"
  }
}

Write-Step 'Wait for key services'
Wait-Port-Ready 8070 'Gateway' 90 | Out-Null
Wait-Port-Ready $frontendPort 'Frontend' 90 | Out-Null

Write-Host "`n================================================" -ForegroundColor Cyan
Write-Host "  All services started!" -ForegroundColor Green
Write-Host "================================================" -ForegroundColor Cyan
Write-Host "  Frontend:      http://127.0.0.1:$frontendPort" -ForegroundColor White
Write-Host "  Gateway API:   http://127.0.0.1:8070" -ForegroundColor White
if (-not $SkipMock) {
  Write-Host "  Mock model:    http://127.0.0.1:$mockPort" -ForegroundColor White
}
Write-Host "  Demo account:  admin / 123456" -ForegroundColor Yellow
Write-Host "================================================" -ForegroundColor Cyan
Write-Host "Each service runs in its own window. Run this script again to stop & restart all.`n" -ForegroundColor Gray