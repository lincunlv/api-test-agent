@echo off
setlocal
cd /d %~dp0

where mvn >nul 2>nul
if errorlevel 1 (
    echo Maven not found in PATH. Please install Maven and JDK 8 first.
    exit /b 1
)

mvn spring-boot:run
