#!/bin/bash

# MelonMCShop Plugin Build Script

# Get the directory of this script
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
cd "$SCRIPT_DIR"

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Building MelonMCShop plugin...${NC}"

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}Maven is not installed. Please install Maven to build the plugin.${NC}"
    exit 1
fi

# Navigate to plugin directory
cd plugin

# Clean and package with Maven
echo -e "${YELLOW}Running Maven build...${NC}"
mvn clean package

# Check if the build was successful
if [ $? -ne 0 ]; then
    echo -e "${RED}Build failed. Please check the error messages above.${NC}"
    exit 1
fi

# Get the path to the JAR file
JAR_FILE=$(find target -name "MelonMCShop-*.jar" | grep -v "original")

if [ -z "$JAR_FILE" ]; then
    echo -e "${RED}Could not find the built JAR file.${NC}"
    exit 1
fi

echo -e "${GREEN}Build successful! JAR file created at: $JAR_FILE${NC}"

# Ask if user wants to copy the plugin to the server
read -p "Do you want to copy the plugin to your server plugins directory? (y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    # Ask for the server plugins directory
    read -p "Enter the path to your server plugins directory: " SERVER_PLUGINS_DIR
    
    # Validate the directory
    if [ ! -d "$SERVER_PLUGINS_DIR" ]; then
        echo -e "${RED}Directory does not exist. Creating it now...${NC}"
        mkdir -p "$SERVER_PLUGINS_DIR"
        if [ $? -ne 0 ]; then
            echo -e "${RED}Failed to create directory. Please check permissions.${NC}"
            exit 1
        fi
    fi
    
    # Copy the JAR file
    echo -e "${YELLOW}Copying plugin to server...${NC}"
    cp "$JAR_FILE" "$SERVER_PLUGINS_DIR/MelonMCShop.jar"
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}Plugin successfully copied to: $SERVER_PLUGINS_DIR/MelonMCShop.jar${NC}"
    else
        echo -e "${RED}Failed to copy plugin. Please check permissions.${NC}"
        exit 1
    fi
fi

echo -e "${GREEN}Build process complete!${NC}" 