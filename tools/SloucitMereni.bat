@echo off
REM Slouci denni *_MD1.xlsx do Popis_mereni_MD1.xlsx
REM VERZE: 2026-08-11n
REM Soubory ve slozce: SloucitMereni.bat + sloucit_mereni.ps1 (+ souhrn xlsx)
REM Excel tlacitko Aktualizovat odkazuje primo na tento BAT (zadny .cmd navic).

setlocal
chcp 65001 >nul
set "SCRIPT=%~dp0sloucit_mereni.ps1"
set "TARGET=%~1"
if "%TARGET%"=="" set "TARGET=%~dp0."

if not exist "%SCRIPT%" (
  echo CHYBA: nenalezen sloucit_mereni.ps1 vedle BAT
  pause
  exit /b 2
)

echo Spoustim sloucit_mereni.ps1
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT%" -Folder "%TARGET%"
set "ERR=%ERRORLEVEL%"

if not "%ERR%"=="0" (
  echo.
  echo Chyba %ERR%
  pause
  endlocal & exit /b %ERR%
)

endlocal & exit /b 0
