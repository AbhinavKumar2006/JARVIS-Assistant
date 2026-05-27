@echo off
setlocal EnableDelayedExpansion
title JARVIS Fast Launcher
color 0A

set "JARVIS_DIR=%~dp0"
cd /d "%JARVIS_DIR%"

set "JAVAFX_LIB=%JARVIS_DIR%lib"
set "MAIN_FILE=JarvisApp.java"
set "CLASS_FILE=JarvisApp.class"


if errorlevel 1 (
    echo ERROR: Jarvis folder not found:
    echo %JARVIS_DIR%
    pause
    exit /b
)

echo Starting JARVIS...
echo.

REM Check if JavaFX lib exists
if not exist "%JAVAFX_LIB%" (
    echo ERROR: JavaFX lib folder not found:
    echo %JAVAFX_LIB%
    pause
    exit /b
)

REM Check if Java file exists
if not exist "%MAIN_FILE%" (
    echo ERROR: %MAIN_FILE% not found in:
    echo %JARVIS_DIR%
    pause
    exit /b
)

REM Check if Ollama server is already running
powershell -NoProfile -Command "try { Invoke-WebRequest -Uri 'http://localhost:11434' -UseBasicParsing -TimeoutSec 1 | Out-Null; exit 0 } catch { exit 1 }" >nul 2>&1

if errorlevel 1 (
    echo Ollama is not running. Starting it now...
    start "Ollama Server" /min cmd /c "ollama serve"
    timeout /t 3 >nul
) else (
    echo Ollama is already running.
)

echo.

REM Compile only if class file is missing or Java file was edited after class file
set "NEED_COMPILE=0"

if not exist "%CLASS_FILE%" (
    set "NEED_COMPILE=1"
) else (
    powershell -NoProfile -Command "if ((Get-Item '%MAIN_FILE%').LastWriteTime -gt (Get-Item '%CLASS_FILE%').LastWriteTime) { exit 1 } else { exit 0 }" >nul 2>&1
    if errorlevel 1 set "NEED_COMPILE=1"
)

if "%NEED_COMPILE%"=="1" (
    echo Compiling JavaFX app...
    javac --module-path "%JAVAFX_LIB%" --add-modules javafx.controls,javafx.fxml "%MAIN_FILE%"

    if errorlevel 1 (
        echo.
        echo ERROR: Compilation failed.
        pause
        exit /b
    )

    echo Compilation successful.
) else (
    echo JavaFX app already compiled. Skipping compilation.
)

echo.
echo Running JARVIS...
echo.

java -Dprism.order=sw --module-path "%JAVAFX_LIB%" --add-modules javafx.controls,javafx.fxml JarvisApp

echo.
echo JARVIS closed.
pause