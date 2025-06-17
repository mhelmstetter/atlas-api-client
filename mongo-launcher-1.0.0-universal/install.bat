@echo off
REM MongoLauncher Installation Script for Windows
REM This script installs MongoLauncher to a directory in PATH and sets up necessary configurations

setlocal enabledelayedexpansion

REM Configuration
set INSTALL_DIR=%ProgramFiles%\MongoLauncher
set SCRIPT_NAME=mongo-launcher.bat
set JAR_NAME=mongo-launcher.jar

echo.
echo ┌─────────────────────────────────────────────┐
echo │          MongoLauncher Installer           │
echo │     MongoDB Cluster Management Tool        │
echo └─────────────────────────────────────────────┘
echo.

REM Check if running as administrator
net session >nul 2>&1
if %errorLevel% == 0 (
    echo Installing MongoLauncher system-wide...
) else (
    echo Error: This installer requires administrator privileges.
    echo Please run this script as Administrator.
    pause
    exit /b 1
)

REM Check if Java is installed
echo Checking Java installation...
java -version >nul 2>&1
if %errorLevel% neq 0 (
    echo Error: Java is not installed or not in PATH.
    echo Please install Java 17 or later and try again.
    echo Download from: https://adoptium.net/
    pause
    exit /b 1
)

REM Check Java version (simplified check)
for /f "tokens=3" %%i in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set JAVA_VERSION=%%i
    set JAVA_VERSION=!JAVA_VERSION:"=!
)

echo ✓ Java found: !JAVA_VERSION!

REM Get script directory
set SCRIPT_DIR=%~dp0

REM Check if JAR file exists
if not exist "%SCRIPT_DIR%bin\%JAR_NAME%" (
    echo Error: %JAR_NAME% not found in %SCRIPT_DIR%bin\
    echo Please build the project first: mvn package
    pause
    exit /b 1
)

echo Installing MongoLauncher...

REM Create installation directory
if not exist "%INSTALL_DIR%" (
    mkdir "%INSTALL_DIR%"
)

REM Copy JAR file
echo Copying JAR file to %INSTALL_DIR%...
copy "%SCRIPT_DIR%bin\%JAR_NAME%" "%INSTALL_DIR%\" >nul
if %errorLevel% neq 0 (
    echo Error: Failed to copy JAR file
    pause
    exit /b 1
)

REM Create wrapper batch script
echo Creating wrapper script...
(
echo @echo off
echo REM MongoLauncher wrapper script
echo set JAR_FILE="%INSTALL_DIR%\%JAR_NAME%"
echo.
echo if not exist %%JAR_FILE%% ^(
echo     echo Error: MongoLauncher JAR file not found at %%JAR_FILE%%
echo     echo Please reinstall MongoLauncher
echo     exit /b 1
echo ^)
echo.
echo REM Pass all arguments to the JAR
echo java -jar %%JAR_FILE%% %%*
) > "%INSTALL_DIR%\%SCRIPT_NAME%"

REM Add to PATH if not already there
echo Adding MongoLauncher to system PATH...
set "NEW_PATH=%INSTALL_DIR%"

REM Check if already in PATH
echo %PATH% | findstr /i "%NEW_PATH%" >nul
if %errorLevel% neq 0 (
    REM Add to system PATH permanently
    for /f "tokens=2*" %%a in ('reg query "HKLM\SYSTEM\CurrentControlSet\Control\Session Manager\Environment" /v PATH 2^>nul') do set "CURRENT_PATH=%%b"
    
    if defined CURRENT_PATH (
        reg add "HKLM\SYSTEM\CurrentControlSet\Control\Session Manager\Environment" /v PATH /t REG_EXPAND_SZ /d "!CURRENT_PATH!;%NEW_PATH%" /f >nul
    ) else (
        reg add "HKLM\SYSTEM\CurrentControlSet\Control\Session Manager\Environment" /v PATH /t REG_EXPAND_SZ /d "%NEW_PATH%" /f >nul
    )
    
    if %errorLevel% neq 0 (
        echo Warning: Failed to add to system PATH
        echo Please manually add %INSTALL_DIR% to your PATH
    ) else (
        echo ✓ Added to system PATH
    )
) else (
    echo ✓ Already in PATH
)

echo.
echo ✓ MongoLauncher installed successfully!
echo.

REM Test installation in new command prompt environment
echo Testing installation...
set "PATH=%PATH%;%INSTALL_DIR%"
call "%INSTALL_DIR%\%SCRIPT_NAME%" --version 2>nul
if %errorLevel% neq 0 (
    echo Warning: Installation test failed
    echo You may need to restart your command prompt
) else (
    echo ✓ Installation test passed
)

echo.
echo Installation complete!
echo.
echo Usage:
echo   mongo-launcher launch --help    # Get help for launching clusters
echo   mongo-launcher config show      # Show current configuration  
echo   mongo-launcher --help           # Show all available commands
echo.
echo Configuration:
echo   Config directory: %%APPDATA%%\MongoLauncher
echo   To set defaults: mongo-launcher config set ^<key^> ^<value^>
echo.

REM Offer to create initial configuration
set CONFIG_DIR=%APPDATA%\MongoLauncher
if not exist "%CONFIG_DIR%\config.json" (
    echo.
    set /p SETUP_CONFIG="Would you like to set up initial configuration? [Y/n]: "
    if /i "!SETUP_CONFIG!"=="y" goto setup_config
    if "!SETUP_CONFIG!"=="" goto setup_config
    goto end_install
    
    :setup_config
    echo Setting up initial configuration...
    
    REM Ask for Atlas Project ID
    set /p ATLAS_PROJECT_ID="Enter your Atlas Project ID (optional): "
    if not "!ATLAS_PROJECT_ID!"=="" (
        call "%INSTALL_DIR%\%SCRIPT_NAME%" config set defaultAtlasProjectId "!ATLAS_PROJECT_ID!"
        echo ✓ Atlas Project ID set
    )
    
    REM Ask for default MongoDB version
    set /p MONGO_VERSION="Enter default MongoDB version [7.0]: "
    if "!MONGO_VERSION!"=="" set MONGO_VERSION=7.0
    call "%INSTALL_DIR%\%SCRIPT_NAME%" config set defaultMongoVersion "!MONGO_VERSION!"
    echo ✓ Default MongoDB version set to !MONGO_VERSION!
    
    echo Initial configuration complete!
    echo.
    echo View your configuration: mongo-launcher config show
)

:end_install
echo.
echo MongoLauncher is ready to use!
echo.
echo Note: You may need to restart your command prompt or IDE for PATH changes to take effect.
pause