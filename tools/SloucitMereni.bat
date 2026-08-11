@echo off
REM Slouci vsechny *_MD1.xlsx ve slozce do Souhrn_mereni.xlsx
REM Pouziti:
REM   1) Preetáhni slozku OneDrive na tento .bat
REM   2) Nebo spust ve slozce se soubory (dvojklik)
REM Potrebuje Python 3 v PATH (python nebo py).

setlocal
set "SCRIPT=%~dp0sloucit_mereni.py"
set "TARGET=%~1"
if "%TARGET%"=="" set "TARGET=%CD%"

where py >nul 2>&1
if %ERRORLEVEL%==0 (
  py -3 "%SCRIPT%" "%TARGET%"
  goto :done
)

where python >nul 2>&1
if %ERRORLEVEL%==0 (
  python "%SCRIPT%" "%TARGET%"
  goto :done
)

echo Python 3 nebyl nalezen.
echo Nainstaluj z https://www.python.org/downloads/  (zapni "Add python.exe to PATH")
echo nebo z Microsoft Store: Python 3.12
pause
exit /b 1

:done
echo.
pause
endlocal
