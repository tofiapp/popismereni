@echo off
REM Slouci denni *_MD1.xlsx do Popis_mereni_MD1.xlsx
REM VERZE: 2026-08-11j
REM
REM Dulezite: musis mit NOVOU sloucit_mereni.ps1 (hledej 2026-08-11j uvnitr).
REM Kdyz vidis chybu DIR=%~dp0, mas STARÝ ps1 — nahrej ho znovu z GitHubu tools/.

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

findstr /C:"2026-08-11j" "%SCRIPT%" >nul
if errorlevel 1 (
  echo.
  echo CHYBA: STARY sloucit_mereni.ps1
  echo V souboru musi byt retezec: 2026-08-11j
  echo.
  echo 1^) Stahni NOVY sloucit_mereni.ps1 z GitHubu ^[slozka tools/^]
  echo 2^) Prepis soubor ve slozce Popis_mereni_MD1
  echo 3^) Pockej na OneDrive sync ^(zelena fajfka^)
  echo 4^) Spust tento BAT znovu
  echo.
  pause
  exit /b 3
)

echo sloucit_mereni.ps1 verze OK ^(2026-08-11j^)
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
