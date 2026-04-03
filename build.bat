@echo off
if /I not "%~1"=="__NF_BUILD_INNER__" (
    "%SystemRoot%\System32\cmd.exe" /V:ON /S /C CALL "%~f0" __NF_BUILD_INNER__ %*
    exit /b %ERRORLEVEL%
)
shift

chcp 65001 >nul 2>&1
cd /d "%~dp0"

set "GIT_BRANCH_WHITELIST="
set "GIT_BRANCH_BLACKLIST=maintenance/*;dev/*"

set "JDK_PROPERTIES_FILE=jdks.properties"
set "GRADLE_USER_HOME=D:\Data\Gradle"

set "FILTER_PS=%~dp0build__filter_branches.ps1"
if not exist "%FILTER_PS%" (
    echo Error: Missing "%FILTER_PS%"
    exit /b 1
)

if defined GIT_BRANCH_WHITELIST (
    echo Mode: whitelist (patterns: !GIT_BRANCH_WHITELIST!^) ...
) else (
    echo Mode: all remote branches except blacklist (patterns: !GIT_BRANCH_BLACKLIST!^) ...
)

for /f "usebackq tokens=*" %%b in (`powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%FILTER_PS%"`) do (
    call :build_branch %%b
)

git checkout main >nul 2>&1
exit /b 0

:build_branch
set "branch_name=%~1"
echo ===================================================================
echo Building branch: !branch_name!

git checkout !branch_name! >nul 2>&1
if errorlevel 1 (
    echo Error: Failed to switch to branch !branch_name!
    exit /b 1
)

git pull origin !branch_name! >nul 2>&1
if errorlevel 1 (
    echo Error: Failed to pull remote code for branch !branch_name!
    exit /b 1
)

set "java_version="
for /f "tokens=3 delims== " %%v in ('findstr /r /c:"def javaVer *=" build.gradle') do (
    set "java_version=%%v"
)

set "java_version=!java_version:"=!"
set "java_version=!java_version: =!"

echo Detected Java version: !java_version!

set "jdk_path="
for /f "usebackq tokens=1,2 delims==" %%j in ("!JDK_PROPERTIES_FILE!") do (
    if /i "%%j"=="jdk!java_version!" set "jdk_path=%%k"
)

if not defined jdk_path (
    echo Error: Could not find JDK path for Java version !java_version!
    exit /b 1
)

set "JAVA_HOME=!jdk_path!"
set "PATH=!JAVA_HOME!\bin;!PATH!"

call gradlew.bat clean build
if errorlevel 1 (
    echo Error: Build failed for branch !branch_name!
    exit /b 1
)

echo Build completed for branch !branch_name!
exit /b 0
