<#
.SYNOPSIS
  漫剧AI生成Agent - 后端微服务一键启动脚本（Phase-1）
.DESCRIPTION
  按依赖顺序启动：eureka -> auth/system/task/workflow/resource -> gateway
  每个服务在新 PowerShell 窗口中以 java -jar 方式运行。
  需先执行 mvn 编译（脚本会自动检测 target，缺失则触发编译）。
.PARAMETER SkipBuild
  跳过 Maven 编译检查（已编译过时使用）
.EXAMPLE
  .\start-backend.ps1
  .\start-backend.ps1 -SkipBuild
#>
param(
  [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent $PSScriptRoot
$BackendRoot = Join-Path $RepoRoot 'comic-drama'

Write-Host '================================================' -ForegroundColor Cyan
Write-Host '  漫剧AI生成Agent · 后端启动 (Phase-1)' -ForegroundColor Cyan
Write-Host '================================================' -ForegroundColor Cyan

# 1. 前置检查：MySQL
Write-Host '[检查] MySQL 端口 3306 ...' -NoNewline
$mysqlOk = (Test-NetConnection -ComputerName 127.0.0.1 -Port 3306 -WarningAction SilentlyContinue).TcpTestSucceeded
if ($mysqlOk) {
  Write-Host ' OK' -ForegroundColor Green
} else {
  Write-Host ' 未检测到!' -ForegroundColor Red
  Write-Host '  请先启动 MySQL，并导入 comic_drama.sql（库名 comic_drama，root/123456）' -ForegroundColor Yellow
  Read-Host '按回车继续，或 Ctrl+C 退出'
}

# 2. Maven 编译
if (-not $SkipBuild) {
  $needBuild = $false
  foreach ($svc in @('comic-eureka','comic-gateway','comic-auth-service','comic-system-service','comic-task-service','comic-workflow-service','comic-resource-service')) {
    $jarDir = Join-Path $BackendRoot "$svc\target"
    if (-not (Get-ChildItem -Path $jarDir -Filter '*.jar' -ErrorAction SilentlyContinue | Where-Object { $_.Name -notmatch 'sources|javadoc' })) {
      $needBuild = $true; break
    }
  }
  if ($needBuild) {
    Write-Host '[编译] 执行 mvn clean install -DskipTests ...' -ForegroundColor Yellow
    Push-Location $BackendRoot
    & mvn clean install -DskipTests "-Duser.language=en" "-Duser.country=US"
    $mvnExit = $LASTEXITCODE
    Pop-Location
    if ($mvnExit -ne 0) { Write-Host 'Maven 编译失败，请检查报错' -ForegroundColor Red; exit 1 }
  } else {
    Write-Host '[编译] 各服务 target 已存在 jar，跳过编译（-SkipBuild 可显式跳过）' -ForegroundColor Green
  }
}

# 3. 服务定义（顺序即启动顺序）
$services = @(
  @{ Name = 'comic-eureka';           Title = 'Eureka 注册中心';    Port = 8761 }
  @{ Name = 'comic-auth-service';     Title = 'Auth 认证服务';      Port = 8101 }
  @{ Name = 'comic-system-service';   Title = 'System 系统服务';    Port = 8102 }
  @{ Name = 'comic-task-service';     Title = 'Task 任务服务';      Port = 8103 }
  @{ Name = 'comic-workflow-service'; Title = 'Workflow 流水线服务'; Port = 8104 }
  @{ Name = 'comic-resource-service';Title = 'Resource 资源服务';  Port = 8105 }
  @{ Name = 'comic-gateway';          Title = 'Gateway 网关';       Port = 8070 }
)

function Start-Service($svc) {
  $jar = Get-ChildItem -Path (Join-Path $BackendRoot "$($svc.Name)\target") -Filter "$($svc.Name)-*.jar" |
         Where-Object { $_.Name -notmatch 'sources|javadoc' } | Select-Object -First 1
  if (-not $jar) { Write-Host "  $($svc.Name) jar 未找到，跳过" -ForegroundColor Red; return }
  Write-Host "[启动] $($svc.Name) ($($svc.Title)) :$($svc.Port)" -ForegroundColor Green
  Start-Process powershell -ArgumentList "-NoExit", "-Command", "Write-Host '$($svc.Title) ($($svc.Name))' -ForegroundColor Cyan; java -jar `"$($jar.FullName)`""
}

foreach ($svc in $services) {
  Start-Service $svc
  # eureka 启动后等待其就绪，其余服务间隔 2s
  if ($svc.Name -eq 'comic-eureka') {
    Write-Host '  等待 Eureka 就绪（约 15s）...' -ForegroundColor Yellow
    Start-Sleep -Seconds 15
  } else {
    Start-Sleep -Seconds 2
  }
}

Write-Host ''
Write-Host '================================================' -ForegroundColor Cyan
Write-Host '  后端全部启动指令已发出！' -ForegroundColor Green
Write-Host '  Eureka 控制台: http://127.0.0.1:8761' -ForegroundColor Cyan
Write-Host '  网关入口:     http://127.0.0.1:8070' -ForegroundColor Cyan
Write-Host '  演示账号:     admin / 123456' -ForegroundColor Cyan
Write-Host '================================================' -ForegroundColor Cyan
Write-Host '提示：各服务运行在独立窗口，关闭窗口即停止服务' -ForegroundColor Gray
