@echo off
REM Slouci vsechny *_MD1.xlsx ve slozce do Souhrn_mereni.xlsx
REM NIC se neinstaluje — pouziva Windows PowerShell (soucast Windows).
REM Pouziti:
REM   1) Preetáhni slozku OneDrive na tento .bat
REM   2) Nebo bat zkopiruj do slozky se soubory a dej dvojklik

setlocal
set "SCRIPT=%~dp0sloucit_mereni.ps1"
set "TARGET=%~1"
if "%TARGET%"=="" set "TARGET=%CD%"

powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT%" -Folder "%TARGET%"
set "ERR=%ERRORLEVEL%"

if not "%ERR%"=="0" (
  echo.
  echo Chyba %ERR%. Zkus spustit znovu, nebo otevri PowerShell rucne:
  echo   powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT%" -Folder "%TARGET%"
)

echo.
pause
endlocal & exit /b %ERR%
