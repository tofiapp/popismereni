@echo off
REM Slouci denni *_MD1.xlsx do Popis_mereni_MD1.xlsx
REM Struktura OneDrive:
REM   Popis_mereni_MD1\
REM     Popis_mereni_MD1.xlsx
REM     Dny\YYMMDD_N_MD1.xlsx
REM     Dny\slouceno\...
REM
REM Pouziti:
REM   1) Preetáhni slozku Popis_mereni_MD1 na tento .bat
REM   2) Nebo bat + ps1 dej do Popis_mereni_MD1 a dej dvojklik
REM NIC se neinstaluje — Windows PowerShell.
REM VERZE: 2026-08-11f

setlocal
chcp 65001 >nul
set "SCRIPT=%~dp0sloucit_mereni.ps1"
set "TARGET=%~1"
REM Dulezite: z Excel hyperlinku muze byt CD jinde → vychozi = slozka bat
if "%TARGET%"=="" set "TARGET=%~dp0"
REM Oriznout koncove lomitko (Join-Path / Resolve-Path to zvladnou i s nim)
if "%TARGET:~-1%"=="\" set "TARGET=%TARGET:~0,-1%"

if not exist "%SCRIPT%" (
  echo CHYBA: nenalezen %SCRIPT%
  echo Stahni sloucit_mereni.ps1 do stejne slozky jako tento bat.
  pause
  exit /b 2
)

echo Spoustim: %SCRIPT%
echo Slozka:   %TARGET%
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT%" -Folder "%TARGET%"
set "ERR=%ERRORLEVEL%"

if not "%ERR%"=="0" (
  echo.
  echo Chyba %ERR%. Zkus:
  echo   powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT%" -Folder "%TARGET%"
  echo.
  pause
  endlocal & exit /b %ERR%
)

endlocal & exit /b 0
