@echo off
rem HTML DB App launcher for Windows (requires Java 17 or later on PATH)
cd /d "%~dp0"
set JAR=dist\htmldbapp-1.0.0-win.jar
if exist target\htmldbapp-1.0.0-all.jar set JAR=target\htmldbapp-1.0.0-all.jar
if not exist "%JAR%" (
  echo JAR not found. Run "mvn package" first.
  pause
  exit /b 1
)
start "" javaw -Dfile.encoding=UTF-8 -jar "%JAR%"
