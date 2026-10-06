@echo off
setlocal
rem ============================================================
rem  One-time setup of the desktop (Electron) part in a project that does not have it yet
rem  (e.g. Jewellery360).  Unzip this kit into the PROJECT ROOT, then double-click this file.
rem ============================================================
set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"
if exist "%ROOT%\package.json" (
  echo package.json already exists in %ROOT% - nothing to set up. Use build-desktop.bat.
  pause
  exit /b 0
)
if not exist "%ROOT%\frontend" ( echo ERROR: run this inside the project root ^(the folder with "frontend" and "backend"^). & pause & exit /b 1 )
if not exist "%ROOT%\template\package.json" ( echo ERROR: the "template" folder from the kit is missing next to this file. & pause & exit /b 1 )

set /p APPNAME=Application name shown to users (example: Jewellery 360): 
set /p APPSLUG=Short name for the files, no spaces (example: Jewellery360): 
if "%APPNAME%"=="" ( echo A name is required. & pause & exit /b 1 )
if "%APPSLUG%"=="" ( echo A short name is required. & pause & exit /b 1 )

xcopy "%ROOT%\template\*" "%ROOT%\" /e /i /y /q >nul || ( echo Copy failed. & pause & exit /b 1 )

set "ROOTDIR=%ROOT%"
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $slug=$env:APPSLUG; $low=$slug.ToLower(); $ini=$slug.Substring(0,[Math]::Min(2,$slug.Length)).ToUpper(); $enc=New-Object Text.UTF8Encoding $false; foreach($f in 'package.json','electron\main.cjs'){ $p=Join-Path $env:ROOTDIR $f; $t=[IO.File]::ReadAllText($p); $t=$t.Replace('@@NAME@@',$env:APPNAME).Replace('@@SLUG@@',$slug).Replace('@@SLUGLOWER@@',$low).Replace('@@APPID@@','com.r2tech.'+$low).Replace('@@INITIALS@@',$ini); [IO.File]::WriteAllText($p,$t,$enc) }"
if errorlevel 1 ( echo Setup failed. & pause & exit /b 1 )

echo.
echo Done. Created package.json and the electron folder for "%APPNAME%".
echo You can delete the "template" folder and this file now.
echo Next: make sure the "config" folder has application.properties ^(database settings^), then run build-desktop.bat
pause
