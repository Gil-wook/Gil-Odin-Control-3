@echo off
chcp 65001 >nul
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Build-Windows.ps1"
if errorlevel 1 (
 echo.
 echo [ERROR] Open build-log.txt, no successful APK produced.
 pause
 exit /b 1
)
echo.
echo [OK] GilOdinControl3-0.7.0-studio-debug.apk
pause
