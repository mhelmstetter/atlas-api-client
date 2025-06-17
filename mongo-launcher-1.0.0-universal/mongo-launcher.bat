@echo off
set SCRIPT_DIR=%~dp0
java -jar "%SCRIPT_DIR%mongo-launcher.jar" %*
