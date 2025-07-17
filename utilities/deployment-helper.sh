#!/bin/bash

# Hubitat Deployment Helper Script
# Helps with code preparation and deployment to Hubitat hub

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
HUBITAT_IP=""
DRIVERS_DIR="./drivers"
APPS_DIR="./apps"

usage() {
    echo "Usage: $0 [command] [options]"
    echo ""
    echo "Commands:"
    echo "  validate     - Validate Groovy syntax"
    echo "  prepare      - Prepare code for deployment"
    echo "  backup       - Create backup of current deployment"
    echo "  hpm-validate - Validate HPM manifest file"
    echo "  hpm-prepare  - Prepare HPM package for distribution"
    echo "  release      - Create GitHub release package"
    echo "  help         - Show this help message"
    echo ""
    echo "Options:"
    echo "  -h, --help   Show this help message"
    echo "  -v, --verbose Enable verbose output"
}

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

validate_groovy() {
    log_info "Validating Groovy syntax..."
    
    # Check if groovy is installed
    if ! command -v groovy &> /dev/null; then
        log_warn "Groovy not found. Install with: brew install groovy"
        return 1
    fi
    
    # Validate drivers
    if [ -d "$DRIVERS_DIR" ]; then
        log_info "Validating drivers..."
        for file in "$DRIVERS_DIR"/*.groovy; do
            if [ -f "$file" ]; then
                echo "  Checking $(basename "$file")..."
                if groovy -c "$file" > /dev/null 2>&1; then
                    echo "    ✓ Syntax OK"
                else
                    echo "    ✗ Syntax Error"
                    groovy -c "$file"
                    return 1
                fi
            fi
        done
    fi
    
    # Validate apps
    if [ -d "$APPS_DIR" ]; then
        log_info "Validating apps..."
        for file in "$APPS_DIR"/*.groovy; do
            if [ -f "$file" ]; then
                echo "  Checking $(basename "$file")..."
                if groovy -c "$file" > /dev/null 2>&1; then
                    echo "    ✓ Syntax OK"
                else
                    echo "    ✗ Syntax Error"
                    groovy -c "$file"
                    return 1
                fi
            fi
        done
    fi
    
    log_info "All files validated successfully!"
}

prepare_deployment() {
    log_info "Preparing code for deployment..."
    
    # Create deployment directory
    mkdir -p "./deployment"
    
    # Copy and prepare drivers
    if [ -d "$DRIVERS_DIR" ]; then
        cp -r "$DRIVERS_DIR" "./deployment/"
        log_info "Drivers prepared in ./deployment/drivers/"
    fi
    
    # Copy and prepare apps
    if [ -d "$APPS_DIR" ]; then
        cp -r "$APPS_DIR" "./deployment/"
        log_info "Apps prepared in ./deployment/apps/"
    fi
    
    # Create deployment notes
    cat > "./deployment/DEPLOYMENT_NOTES.md" << EOF
# Deployment Notes

Generated: $(date)

## Files to Deploy

### Drivers
$(if [ -d "$DRIVERS_DIR" ]; then ls "$DRIVERS_DIR"/*.groovy | sed 's|.*drivers/|- |'; fi)

### Apps
$(if [ -d "$APPS_DIR" ]; then ls "$APPS_DIR"/*.groovy | sed 's|.*apps/|- |'; fi)

## Deployment Steps

1. Log into Hubitat hub web interface
2. Navigate to "Developer Tools" > "Drivers Code"
3. Click "New Driver" for each driver file
4. Copy and paste the driver code
5. Click "Save"
6. Navigate to "Developer Tools" > "Apps Code"
7. Click "New App" for each app file
8. Copy and paste the app code
9. Click "Save"
10. Navigate to "Apps" > "Add User App"
11. Select and configure the main app

## Post-Deployment Testing

1. Create test devices using the drivers
2. Configure the main app
3. Test presence detection functionality
4. Verify MQTT/webhook integration
5. Test manual overrides
6. Check logging output

EOF
    
    log_info "Deployment package ready in ./deployment/"
}

create_backup() {
    log_info "Creating backup..."
    
    TIMESTAMP=$(date +%Y%m%d_%H%M%S)
    BACKUP_DIR="./backups/backup_$TIMESTAMP"
    
    mkdir -p "$BACKUP_DIR"
    
    # Backup source files
    if [ -d "$DRIVERS_DIR" ]; then
        cp -r "$DRIVERS_DIR" "$BACKUP_DIR/"
    fi
    
    if [ -d "$APPS_DIR" ]; then
        cp -r "$APPS_DIR" "$BACKUP_DIR/"
    fi
    
    # Copy important files
    [ -f "CLAUDE.md" ] && cp "CLAUDE.md" "$BACKUP_DIR/"
    [ -f "initail-design.md" ] && cp "initail-design.md" "$BACKUP_DIR/"
    
    log_info "Backup created at $BACKUP_DIR"
}

validate_hpm_manifest() {
    log_info "Validating HPM manifest..."
    
    if [ ! -f "packageManifest.json" ]; then
        log_error "packageManifest.json not found"
        return 1
    fi
    
    # Check if jq is installed
    if ! command -v jq &> /dev/null; then
        log_warn "jq not found. Install with: brew install jq"
        log_info "Performing basic JSON validation..."
        
        # Basic JSON validation
        if python3 -m json.tool packageManifest.json > /dev/null 2>&1; then
            log_info "✓ JSON syntax is valid"
        else
            log_error "✗ JSON syntax is invalid"
            return 1
        fi
    else
        # Detailed validation with jq
        log_info "Validating JSON structure..."
        
        # Check required fields
        REQUIRED_FIELDS="packageName author version apps drivers"
        for field in $REQUIRED_FIELDS; do
            if jq -e ".$field" packageManifest.json > /dev/null 2>&1; then
                log_info "✓ Required field '$field' present"
            else
                log_error "✗ Required field '$field' missing"
                return 1
            fi
        done
        
        # Check apps array
        APP_COUNT=$(jq '.apps | length' packageManifest.json)
        log_info "✓ Found $APP_COUNT app(s)"
        
        # Check drivers array
        DRIVER_COUNT=$(jq '.drivers | length' packageManifest.json)
        log_info "✓ Found $DRIVER_COUNT driver(s)"
        
        # Validate URLs (basic check)
        jq -r '.apps[].location, .drivers[].location' packageManifest.json | while read url; do
            if [[ $url =~ ^https?:// ]]; then
                log_info "✓ Valid URL format: $url"
            else
                log_error "✗ Invalid URL format: $url"
            fi
        done
    fi
    
    log_info "HPM manifest validation completed!"
}

prepare_hpm_package() {
    log_info "Preparing HPM package..."
    
    # Validate manifest first
    validate_hpm_manifest || return 1
    
    # Create HPM package directory
    mkdir -p "./hpm-package"
    
    # Copy required files
    cp "packageManifest.json" "./hpm-package/"
    cp "LICENSE" "./hpm-package/"
    cp "README.md" "./hpm-package/"
    
    # Copy drivers and apps
    cp -r "$DRIVERS_DIR" "./hpm-package/"
    cp -r "$APPS_DIR" "./hpm-package/"
    
    # Copy documentation
    cp -r "docs/" "./hpm-package/"
    
    # Create package info
    cat > "./hpm-package/PACKAGE_INFO.md" << EOF
# HPM Package Information

Generated: $(date)

## Package Contents

### Drivers
$(ls "$DRIVERS_DIR"/*.groovy | sed 's|.*drivers/|- |')

### Apps
$(ls "$APPS_DIR"/*.groovy | sed 's|.*apps/|- |')

### Documentation
- README.md
- docs/hpm-installation.md
- docs/system-architecture.md
- docs/data-flow.md
- docs/api-reference.md
- docs/integration-guide.md

## Installation

### Via HPM
1. Install Hubitat Package Manager
2. Search for "WiFi GPS Hybrid Presence"
3. Click Install

### Manual Installation
See deployment/ directory for prepared files.

EOF
    
    log_info "HPM package ready in ./hpm-package/"
}

create_release_package() {
    log_info "Creating GitHub release package..."
    
    # Validate code first
    validate_groovy || return 1
    validate_hpm_manifest || return 1
    
    # Create release directory
    RELEASE_DIR="./releases/$(date +%Y%m%d_%H%M%S)"
    mkdir -p "$RELEASE_DIR"
    
    # Prepare deployment files
    prepare_deployment
    
    # Prepare HPM package
    prepare_hpm_package
    
    # Copy everything to release directory
    cp -r "./deployment" "$RELEASE_DIR/"
    cp -r "./hpm-package" "$RELEASE_DIR/"
    
    # Create release notes
    cat > "$RELEASE_DIR/RELEASE_NOTES.md" << EOF
# Release Notes

Version: $(jq -r '.version' packageManifest.json)
Date: $(date)

## Changes
- Updated WiFi priority logic
- GPS timeout logic removed
- Improved documentation
- Added HPM support

## Installation Options

### Option 1: HPM (Recommended)
1. Install via Hubitat Package Manager
2. Search for "WiFi GPS Hybrid Presence"
3. Click Install

### Option 2: Manual Installation
1. Use files in deployment/ directory
2. Follow README.md instructions

## Files Included
- packageManifest.json (HPM manifest)
- deployment/ (Manual installation files)
- hpm-package/ (HPM package files)
- LICENSE
- README.md
- docs/ (Complete documentation)

EOF
    
    # Create ZIP package
    cd "$RELEASE_DIR"
    zip -r "../hubitat-presence-$(jq -r '.version' ../../packageManifest.json).zip" . > /dev/null 2>&1
    cd - > /dev/null
    
    log_info "Release package created at $RELEASE_DIR"
    log_info "ZIP package created at ./releases/hubitat-presence-$(jq -r '.version' packageManifest.json).zip"
}

main() {
    case "$1" in
        "validate")
            validate_groovy
            ;;
        "prepare")
            validate_groovy && prepare_deployment
            ;;
        "backup")
            create_backup
            ;;
        "hpm-validate")
            validate_hpm_manifest
            ;;
        "hpm-prepare")
            prepare_hpm_package
            ;;
        "release")
            create_release_package
            ;;
        "help"|"-h"|"--help")
            usage
            ;;
        *)
            log_error "Unknown command: $1"
            usage
            exit 1
            ;;
    esac
}

# Run main function with all arguments
main "$@"