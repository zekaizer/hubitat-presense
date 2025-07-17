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