@echo off
REM MongoDB Atlas Interactive Cluster Launcher - Windows Version
REM Standalone executable launcher for creating real Atlas clusters

set SCRIPT_DIR=%~dp0
set JAR_FILE=%SCRIPT_DIR%bin\AtlasClient.jar

if not exist "%JAR_FILE%" (
    echo ❌ Error: Atlas client JAR not found at %JAR_FILE%
    echo Please run 'mvn clean package -DskipTests' first to build the project.
    exit /b 1
)

echo 🍃 MongoDB Atlas Interactive Cluster Launcher
echo ==============================================
echo.

REM Run the Atlas cluster launcher
java -cp "%JAR_FILE%" com.mongodb.atlas.api.launcher.AtlasClusterLauncher

echo.
echo ✅ Atlas launcher session complete!
pause