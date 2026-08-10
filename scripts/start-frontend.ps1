<#
.SYNOPSIS
  漫剧AI生成Agent - 前端开发服务启动脚本（Phase-1）
.DESCRIPTION
  启动 Vite 开发服务器，自动安装依赖（node_modules 缺失时）。
  开发服务器监听 127.0.0.1:5170，并代理 /auth /api 到网关 8070。
.EXAMPLE
  .\start-frontend.ps1
#>

$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent $PSScriptRoot
$FrontendRoot = Join-Path $RepoRoot 'comic-drama-frontend'

Write-Host '================================================' -ForegroundColor Cyan
Write-Host '  漫剧AI生成Agent · 前端启动 (Phase-1)' -ForegroundColor Cyan
Write-Host '================================================' -ForegroundColor Cyan

# 1. 检查 Node.js
$node = Get-Command node -ErrorAction SilentlyContinue
if (-not $node) {
  Write-Host '[错误] 未检测到 Node.js，请先安装 Node 18+ (推荐 20 LTS)' -ForegroundColor Red
  exit 1
}
Write-Host "[检查] Node $(node -v)  OK" -ForegroundColor Green

# 2. 依赖检查 / 安装
if (-not (Test-Path (Join-Path $FrontendRoot 'node_modules'))) {
  Write-Host '[安装] node_modules 缺失，执行 npm install ...' -ForegroundColor Yellow
  Push-Location $FrontendRoot
  & npm install --no-audit --no-fund
  $npmExit = $LASTEXITCODE
  Pop-Location
  if ($npmExit -ne 0) { Write-Host 'npm install 失败' -ForegroundColor Red; exit 1 }
} else {
  Write-Host '[检查] node_modules 已存在' -ForegroundColor Green
}

# 3. 启动 Vite
Write-Host '[启动] vite dev server ...' -ForegroundColor Green
Push-Location $FrontendRoot
try {
  & npm run dev
} finally {
  Pop-Location
}
