@echo off
:: Enable UTF-8 encoding in the console for file paths
chcp 65001 > nul

:: Define Paths
set "BACKEND_SOURCE=D:\source_code\jewellery360\backend\src"
set "BACKEND_DEST=D:\source_code\jewellBack\jewell360\backend\src"

set "FRONTEND_SOURCE=D:\source_code\jewellery360\frontend\src"
set "FRONTEND_DEST=D:\source_code\jewellBack\jewell360\frontend\src"

echo ===================================================
echo [START] Project Copy Automation Setup
echo ===================================================

:: 1. Copy Backend Files
echo [1/2] Copying Backend Source...
echo From: %BACKEND_SOURCE%
echo To:   %BACKEND_DEST%
robocopy "%BACKEND_SOURCE%" "%BACKEND_DEST%" /E /XO /R:3 /W:5
echo.

:: 2. Copy Frontend Files
echo [2/2] Copying Frontend Source...
echo From: %FRONTEND_SOURCE%
echo To:   %FRONTEND_DEST%
robocopy "%FRONTEND_SOURCE%" "%FRONTEND_DEST%" /E /XO /R:3 /W:5
echo.

echo ===================================================
echo [FINISHED] All source folders copied successfully!
echo ===================================================
pause
