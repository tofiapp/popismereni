@echo off
REM Slouci denni *_MD1.xlsx do Popis_mereni_MD1.xlsx
REM Struktura OneDrive:
REM   Popis_mereni_MD1\
REM     Popis_mereni_MD1.xlsx
REM     MD1_popis_dny\YYMMDD_N_MD1.xlsx
REM
REM Pouziti:
REM   1) Preetáhni slozku Popis_mereni_MD1 na tento .bat
REM   2) Nebo bat + ps1 dej do Popis_mereni_MD1 a dej dvojklik
REM NIC se neinstaluje — Windows PowerShell.

setlocal
chcp 65001 >nul
set "SCRIPT=%~dp0sloucit_mereni.ps1"
set "TARGET=%~1"
if "%TARGET%"=="" set "TARGET=%CD%"

powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT%" -Folder "%TARGET%"
set "ERR=%ERRORLEVEL%"

if not "%ERR%"=="0" (
  echo.
  echo Chyba %ERR%. Zkus:
  echo   powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT%" -Folder "%TARGET%"
) else (
  echo.
  echo Excel by se mel otevrit sam. Stiskni klavesu pro zavreni tohoto okna.
)

echo.
pause
endlocal & exit /b %ERR%
