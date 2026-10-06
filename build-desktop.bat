@echo off
setlocal enabledelayedexpansion
rem ============================================================
rem  Desktop build - works for BIZ360, SMART360, Jewellery360 (same layout in each project)
rem
rem  Put this file in the PROJECT ROOT (the folder that has package.json, frontend, backend, config, electron).
rem  Double-click it, or run:   build-desktop.bat "E:\Customer1\biz"
rem  The .exe is only built when none exists in release\ (it never changes between versions; the app logic is in the .jar).
rem  Force a new exe:   build-desktop.bat "" exe     (or:  build-desktop.bat "E:\Customer1\biz" exe)
rem  The optional folder = an already installed copy: only the NEW .exe and .jar are copied into it
rem  (its config, runtime, logs, backup and database are never touched) = easy customer update.
rem
rem  Result:  release\<app>-app\   <app>.exe + lib\<app>.jar + config\ + runtime\
rem  Copy that folder to any PC that has PostgreSQL installed and run the exe.
rem ============================================================
set ROOT=%~dp0
if "%ROOT:~-1%"=="\" set ROOT=%ROOT:~0,-1%
set FRONTEND=%ROOT%\frontend
set BACKEND=%ROOT%\backend
set STATIC=%BACKEND%\src\main\resources\static
set UPDATE_DIR=%~1

for %%I in ("%ROOT%") do set APP=%%~nxI
set OUT=%ROOT%\release\%APP%-app

rem ---- where the Java runtime comes from (first one found is used) ----
set "RUNTIME_SRC="
echo Looking for the Java runtime...
if exist "%ROOT%\runtime\java\bin\java.exe" set "RUNTIME_SRC=%ROOT%\runtime"
if not defined RUNTIME_SRC if exist "%ROOT%\..\_shared\runtime\java\bin\java.exe" set "RUNTIME_SRC=%ROOT%\..\_shared\runtime"
if not defined RUNTIME_SRC if exist "%ROOT%\..\biz\runtime\java\bin\java.exe" set "RUNTIME_SRC=%ROOT%\..\biz\runtime"
if not defined RUNTIME_SRC if exist "%ROOT%\..\BIZ360\runtime\java\bin\java.exe" set "RUNTIME_SRC=%ROOT%\..\BIZ360\runtime"
if defined RUNTIME_SRC (echo   found: %RUNTIME_SRC%) else (echo   not found in: %ROOT%\runtime , %ROOT%\..\_shared\runtime , %ROOT%\..\biz\runtime , %ROOT%\..\BIZ360\runtime)

if not defined RUNTIME_SRC (
  echo   Downloading Java 21 runtime once into %ROOT%\..\_shared\runtime ...
  set "SHARED=%ROOT%\..\_shared"
  if exist "!SHARED!\runtime\java" rmdir /s /q "!SHARED!\runtime\java"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; $z=Join-Path $env:TEMP 'jre21.zip'; $x=Join-Path $env:TEMP 'jre21x'; if(Test-Path $x){Remove-Item $x -Recurse -Force}; Invoke-WebRequest -UseBasicParsing -Uri 'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jre/hotspot/normal/eclipse' -OutFile $z; Expand-Archive -Force $z $x; $d=Get-ChildItem $x -Directory | Select-Object -First 1; New-Item -ItemType Directory -Force '!SHARED!\runtime' | Out-Null; Move-Item $d.FullName '!SHARED!\runtime\java'"
  if exist "!SHARED!\runtime\java\bin\java.exe" (
    set "RUNTIME_SRC=!SHARED!\runtime"
    echo   Java runtime ready: !SHARED!\runtime
  ) else (
    echo   Download failed. Download "Temurin 21 JRE zip for Windows x64" from adoptium.net, and unzip it so that
    echo   %ROOT%\..\_shared\runtime\java\bin\java.exe exists, then run this file again.
  )
)

if not exist "%ROOT%\package.json" (
  echo ERROR: no package.json in %ROOT% - this project has no desktop setup yet.
  echo        Run init-desktop.bat once ^(from desktop-kit.zip^), then run this file again.
  goto :fail
)

echo.
echo ===== 1/5  Building the web screens =====
cd /d "%FRONTEND%" || goto :fail
if not exist node_modules call npm install || goto :fail
call npm run build || goto :fail
if not exist "%FRONTEND%\dist\index.html" (
  echo ERROR: frontend\dist\index.html was not created.
  goto :fail
)

echo.
echo ===== 2/5  Putting the screens inside the backend =====
if exist "%STATIC%" rmdir /s /q "%STATIC%"
mkdir "%STATIC%" || goto :fail
xcopy "%FRONTEND%\dist\*" "%STATIC%\" /e /i /y /q || goto :fail

echo.
echo ===== 3/5  Building the backend .jar =====
cd /d "%BACKEND%" || goto :fail
call gradlew.bat clean bootJar || goto :fail
set JAR=
for %%f in ("%BACKEND%\build\libs\*.jar") do (
  echo %%~nxf | findstr /i /c:"-plain" >nul || set JAR=%%f
)
if "%JAR%"=="" (
  echo ERROR: no jar found in backend\build\libs.
  goto :fail
)
for %%f in ("%JAR%") do set JARNAME=%%~nxf
echo Jar: %JARNAME%
copy /y "%JAR%" "%BACKEND%\%JARNAME%" >nul || goto :fail

echo.
echo ===== 4/5  Building the Portable .exe =====
cd /d "%ROOT%" || goto :fail
set EXE=
for %%f in ("%ROOT%\release\*Portable*.exe") do set EXE=%%f
if defined EXE if /i not "%~2"=="exe" (
  echo The exe already exists - skipping the exe build ^(only the jar changes^). To rebuild the exe too, add exe as 2nd argument.
  goto :exe_ready
)
if not exist node_modules call npm install || goto :fail
if exist "%ROOT%\release\*Portable*.exe" del /q "%ROOT%\release\*Portable*.exe"
set TRY=0
:pack
set /a TRY+=1
if exist "%ROOT%\release\win-unpacked.tmp" rmdir /s /q "%ROOT%\release\win-unpacked.tmp"
if exist "%ROOT%\release\win-unpacked" rmdir /s /q "%ROOT%\release\win-unpacked"
call npx electron-builder --win portable
if errorlevel 1 (
  if !TRY! lss 3 (
    echo.
    echo Packaging failed ^(attempt !TRY! of 3^) - a file was probably locked by antivirus/IDE. Retrying in 10 seconds...
    timeout /t 10 /nobreak >nul
    goto :pack
  )
  echo.
  echo Still locked. Close the app, Explorer windows showing the release folder, then add the project folder to Windows Defender exclusions and try again.
  goto :fail
)
set EXE=
for %%f in ("%ROOT%\release\*Portable*.exe") do set EXE=%%f
if "%EXE%"=="" (
  echo ERROR: the Portable exe was not found in release\.
  goto :fail
)
:exe_ready
for %%f in ("%EXE%") do set EXENAME=%%~nxf

echo.
echo ===== 5/5  Assembling the ready-to-run folder =====
if exist "%OUT%" rmdir /s /q "%OUT%"
mkdir "%OUT%\lib" || goto :fail
copy /y "%EXE%" "%OUT%\%EXENAME%" >nul || goto :fail
copy /y "%JAR%" "%OUT%\lib\%JARNAME%" >nul || goto :fail
xcopy "%ROOT%\config\*" "%OUT%\config\" /e /i /y /q >nul || goto :fail
if not exist "%OUT%\config\logback-spring.xml" if exist "%BACKEND%\src\main\resources\logback-spring.xml" copy /y "%BACKEND%\src\main\resources\logback-spring.xml" "%OUT%\config\logback-spring.xml" >nul
if not exist "%OUT%\config\application.properties" if exist "%BACKEND%\src\main\resources\application.properties" copy /y "%BACKEND%\src\main\resources\application.properties" "%OUT%\config\application.properties" >nul
if not exist "%OUT%\config\logback-spring.xml" echo WARNING: config\logback-spring.xml is missing - the app will not start without it.
if not exist "%OUT%\config\application.properties" echo WARNING: config\application.properties is missing - the app will not start without it.
if defined RUNTIME_SRC (
  echo Java runtime from: %RUNTIME_SRC%
  xcopy "%RUNTIME_SRC%\*" "%OUT%\runtime\" /e /i /y /q >nul || goto :fail
) else (
  echo.
  echo WARNING: no Java runtime found. Put a "runtime" folder ^(runtime\java\bin\java.exe^) in the project root
  echo          or in ..\_shared\runtime and run again - or copy it next to the exe by hand.
)

if not "%UPDATE_DIR%"=="" (
  echo.
  echo ===== Updating the installed copy: %UPDATE_DIR% =====
  if not exist "%UPDATE_DIR%\lib" (
    echo ERROR: "%UPDATE_DIR%\lib" not found - that is not an installed copy.
    goto :fail
  )
  copy /y "%JAR%" "%UPDATE_DIR%\lib\%JARNAME%" >nul || goto :fail
  copy /y "%EXE%" "%UPDATE_DIR%\%EXENAME%" >nul || goto :fail
  echo Updated .exe and .jar. Config, runtime, logs and backup were not touched.
)

echo.
echo ============================================================
echo  DONE.  Ready-to-run folder:  %OUT%
echo ============================================================
explorer "%OUT%"
pause
exit /b 0

:fail
echo.
echo ************************************************************
echo  BUILD FAILED - read the message just above this box.
echo ************************************************************
pause
exit /b 1
