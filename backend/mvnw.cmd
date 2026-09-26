@REM Maven Wrapper script for Windows
@REM Download Maven if not present, then execute
@echo off

set MAVEN_PROJECTBASEDIR=%~dp0
set MAVEN_CMD_LINE_ARGS=%*

for %%i in ("%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties") do (
    for /f "tokens=1,2 delims==" %%a in ('type "%%i"') do (
        if "%%a"=="distributionUrl" set DOWNLOAD_URL=%%b
    )
)

set "MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists"

if not exist "%MAVEN_HOME%" mkdir "%MAVEN_HOME%"

REM Use Maven wrapper JAR if available, otherwise download Maven
where mvn >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    mvn %MAVEN_CMD_LINE_ARGS%
) else (
    echo Maven not found. Please install Maven or use: mvnw.cmd
    echo Download Maven from: https://maven.apache.org/download.cgi
    echo Or install via: choco install maven
    exit /b 1
)
