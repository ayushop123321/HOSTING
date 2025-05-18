@echo off
:: MelonMCShop Plugin Build Script for Windows

echo Building MelonMCShop plugin...

:: Check if Maven is installed
where mvn >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo Maven is not installed. Please install Maven to build the plugin.
    exit /b 1
)

:: Navigate to plugin directory
cd plugin

:: Clean and package with Maven
echo Running Maven build...
call mvn clean package

:: Check if the build was successful
if %ERRORLEVEL% neq 0 (
    echo Build failed. Please check the error messages above.
    exit /b 1
)

:: Get the path to the JAR file
for /f "delims=" %%a in ('dir /b /s target\MelonMCShop-*.jar ^| findstr /v "original"') do set JAR_FILE=%%a

if "%JAR_FILE%"=="" (
    echo Could not find the built JAR file.
    exit /b 1
)

echo Build successful! JAR file created at: %JAR_FILE%

:: Ask if user wants to copy the plugin to the server
set /p COPY_PLUGIN=Do you want to copy the plugin to your server plugins directory? (y/n): 

if /i "%COPY_PLUGIN%"=="y" (
    :: Ask for the server plugins directory
    set /p SERVER_PLUGINS_DIR=Enter the path to your server plugins directory: 
    
    :: Validate the directory
    if not exist "%SERVER_PLUGINS_DIR%" (
        echo Directory does not exist. Creating it now...
        mkdir "%SERVER_PLUGINS_DIR%"
        if %ERRORLEVEL% neq 0 (
            echo Failed to create directory. Please check permissions.
            exit /b 1
        )
    )
    
    :: Copy the JAR file
    echo Copying plugin to server...
    copy "%JAR_FILE%" "%SERVER_PLUGINS_DIR%\MelonMCShop.jar"
    
    if %ERRORLEVEL% equ 0 (
        echo Plugin successfully copied to: %SERVER_PLUGINS_DIR%\MelonMCShop.jar
    ) else (
        echo Failed to copy plugin. Please check permissions.
        exit /b 1
    )
)

echo Build process complete! 