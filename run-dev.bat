@echo off
rem Builds Lifeblood and starts the dev copy of BTA with the mod loaded.
rem Double-click this file, or run it from a terminal. The first start takes a minute.
for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-21*") do set "JAVA_HOME=%%D"
if not defined JAVA_HOME (
	echo JDK 21 not found in "C:\Program Files\Eclipse Adoptium". Install Eclipse Temurin 21.
	pause
	exit /b 1
)
cd /d "%~dp0"
call gradlew.bat runClient
if errorlevel 1 pause
