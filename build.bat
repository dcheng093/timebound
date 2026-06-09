@echo off
REM Build script for TimeBound plugin
REM This ensures the plugin.yml is included in the JAR

setlocal enabledelayedexpansion

REM Locate Maven: prefer the bundled path, otherwise fall back to mvn on PATH
set "MVN_CMD="
if exist "C:\Users\User\.maven\maven-3.9.16\bin\mvn.cmd" (
    set "MVN_CMD=C:\Users\User\.maven\maven-3.9.16\bin\mvn.cmd"
) else (
    for %%I in (mvn.cmd mvn.bat mvn) do (
        if not defined MVN_CMD (
            for /f "delims=" %%P in ('where %%I 2^>nul') do (
                if not defined MVN_CMD set "MVN_CMD=%%P"
            )
        )
    )
)

if defined MVN_CMD (
    echo Found Maven at !MVN_CMD!
    call "!MVN_CMD!" clean package
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
    echo ERROR: Maven not found.
    echo Install Maven and make sure 'mvn' is on your PATH, then run this again.
    echo Note: this plugin targets Java 21, so a JDK 21 must also be installed.
    pause
)
