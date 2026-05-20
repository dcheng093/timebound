@echo off
REM Build script for TimeBound plugin
REM This ensures the plugin.yml is included in the JAR

setlocal enabledelayedexpansion

REM Check if Maven is installed
if exist "C:\Users\User\.maven\maven-3.9.16\bin\mvn.cmd" (
    echo Found Maven at C:\Users\User\.maven\maven-3.9.16\bin\mvn.cmd
    call "C:\Users\User\.maven\maven-3.9.16\bin\mvn.cmd" clean package
    if !errorlevel! equ 0 (
        echo.
        echo ============================================
        echo BUILD SUCCESSFUL!
        echo ============================================
        echo JAR File Location: target\timebound-1.0.jar
        echo.
        echo Copy this JAR to your PaperMC plugins folder.
        echo.
        pause
    ) else (
        echo.
        echo BUILD FAILED with error level !errorlevel!
        echo.
        pause
    )
) else (
    echo ERROR: Maven not found at expected location
    echo Please ensure Maven is installed at: C:\Users\User\.maven\maven-3.9.16\bin\mvn.cmd
    pause
)
