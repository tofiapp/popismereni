@echo off
setlocal
chcp 65001 >nul
REM Launcher pro Excel tlacitko Aktualizovat (ASCII nazev) — VERZE 2026-08-11j
set "DIR=%~dp0"
if not exist "%DIR%sloucit_mereni.ps1" (
  echo CHYBA: chybi sloucit_mereni.ps1 vedle tohoto CMD
  pause
  exit /b 2
)
findstr /C:"2026-08-11j" "%DIR%sloucit_mereni.ps1" >nul
if errorlevel 1 (
  echo CHYBA: STARY sloucit_mereni.ps1 — nahrej 2026-08-11j z GitHub tools/
  pause
  exit /b 3
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%DIR%sloucit_mereni.ps1" -Folder "%DIR%."
set "ERR=%ERRORLEVEL%"
if not "%ERR%"=="0" (
  echo.
  echo Chyba %ERR%
  pause
)
endlocal & exit /b %ERR%
