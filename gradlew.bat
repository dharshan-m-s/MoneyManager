@echo off
setlocal EnableExtensions
set "GRADLE_VERSION=9.5.0"
set "ROOT_DIR=%~dp0"
set "CACHE_DIR=%USERPROFILE%\.gradle\money-manager-gradle\%GRADLE_VERSION%"
set "GRADLE_BIN=%CACHE_DIR%\gradle-%GRADLE_VERSION%\bin\gradle.bat"
set "DIST_ZIP=%CACHE_DIR%\gradle-%GRADLE_VERSION%-bin.zip"
set "DIST_URL=https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip"

if not exist "%GRADLE_BIN%" (
  if not exist "%CACHE_DIR%" mkdir "%CACHE_DIR%"
  if not exist "%DIST_ZIP%" (
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing -Uri '%DIST_URL%' -OutFile '%DIST_ZIP%'"
    if errorlevel 1 exit /b 1
  )
  if exist "%CACHE_DIR%\gradle-%GRADLE_VERSION%" rmdir /s /q "%CACHE_DIR%\gradle-%GRADLE_VERSION%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%DIST_ZIP%' '%CACHE_DIR%'"
  if errorlevel 1 exit /b 1
)

call "%GRADLE_BIN%" --project-dir "%ROOT_DIR%" %*
exit /b %ERRORLEVEL%
