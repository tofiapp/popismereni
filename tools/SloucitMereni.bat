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
REM VERZE: 2026-08-11i
REM
REM Pozn.: Spravny nazev slozky je Popis_mereni_MD1 / Popis_měření_MD1
REM        Kdyz v ceste vidis "SprĂˇva" / "mÄ›Ĺ™enĂ­", je to jen spatne kodovani zobrazeni.

setlocal
chcp 65001 >nul
set "SCRIPT=%~dp0sloucit_mereni.ps1"
set "TARGET=%~1"
REM Z Excelu / zástupce: vychozi = slozka tohoto BAT (Unicode OK přes %~dp0)
if "%TARGET%"=="" set "TARGET=%~dp0."

if not exist "%SCRIPT%" (
  echo CHYBA: nenalezen sloucit_mereni.ps1 vedle BAT
  echo Stahni sloucit_mereni.ps1 do stejne slozky jako tento bat.
  pause
  exit /b 2
)

echo Spoustim sloucit_mereni.ps1
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT%" -Folder "%TARGET%"
set "ERR=%ERRORLEVEL%"

if not "%ERR%"=="0" (
  echo.
  echo Chyba %ERR%. Zkus dvojklik na Aktualizovat.cmd nebo:
  echo   powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT%" -Folder "%~dp0."
  echo.
  pause
  endlocal & exit /b %ERR%
)

endlocal & exit /b 0
