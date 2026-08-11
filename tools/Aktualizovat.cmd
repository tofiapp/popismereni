@echo off
setlocal
chcp 65001 >nul
REM Launcher pro Excel tlacitko Aktualizovat (ASCII nazev souboru)
set "DIR=%~dp0"
if not exist "%DIR%sloucit_mereni.ps1" (
  echo CHYBA: chybi sloucit_mereni.ps1 vedle tohoto CMD
  pause
  exit /b 2
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%DIR%sloucit_mereni.ps1" -Folder "%DIR%."
set "ERR=%ERRORLEVEL%"
if not "%ERR%"=="0" (
  echo.
  echo Chyba %ERR%
  pause
)
endlocal & exit /b %ERR%
