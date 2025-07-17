# Hubitat WiFi/GPS Hybrid Presence Detection System

Accurate presence detection system combining WiFi and GPS data.

## Key Features

- **Hybrid Detection**: WiFi arrival + GPS departure confirmation
- **False Positive Prevention**: Sophisticated logic prevents incorrect state changes
- **Manual Override**: Manually set presence status when needed
- **Delayed Away**: Configurable delay before confirming away status
- **External Integration**: MQTT/webhook support

## Quick Start

### 1. Validate Code
```bash
./utilities/deployment-helper.sh validate
```

### 2. Prepare Deployment
```bash
./utilities/deployment-helper.sh prepare
```

### 3. Install on Hubitat Hub
1. Access Hubitat web interface
2. Install drivers in Developer Tools > Drivers Code
3. Install app in Developer Tools > Apps Code
4. Configure in Apps > Add User App

## System Components

### Drivers
- **WiFi GPS Hybrid Presence** - Individual device presence detection
- **Anyone Presence Override** - Combined presence + manual control
- **Delayed Away Presence** - Delayed away confirmation

### App
- **WiFi GPS Presence Manager Enhanced** - Complete system management

## Integration Setup

### MQTT (WiFi Detection)
```
Topic: AsusAC68U/status/+/lastseen/epoch
Topic: UnifiU6Pro/status/+/lastseen/epoch
```

### Webhooks (GPS Detection)
```
URL: http://hub-ip/apps/api/[app-id]/gps/[device-id]/[enter|exit]
```

## Development Tools

- `utilities/deployment-helper.sh` - Deployment helper script
- `utilities/development-guide.md` - Detailed development guide
- `utilities/test-suite.groovy` - Test utilities
- `utilities/debug-helper.groovy` - Debugging tools

## File Structure

```
hubitat-presense/
├── drivers/         # Driver code
├── apps/           # App code
├── utilities/      # Development tools
├── deployment/     # Deployment-ready files
└── backups/       # Backup files
```

## Troubleshooting

For detailed development guidance, see `utilities/development-guide.md`.

## License

This project is for personal use.