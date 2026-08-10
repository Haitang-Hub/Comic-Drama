@echo off
REM ============================================================
REM   Comic Drama AI - Restart All Services
REM   Double-click to run, or run restart-all.ps1 in PowerShell
REM ============================================================
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0restart-all.ps1" %*
echo.
pause
