# All-in-One Presence Detection System

A comprehensive presence detection solution for Hubitat Elevation that combines multiple detection methods into a single, unified driver.

## Overview

The All-in-One Presence Detection System integrates various presence detection technologies into a single driver, providing reliable and flexible presence monitoring for home automation systems. This driver consolidates WiFi MQTT detection, GPS geofencing, manual override capabilities, and delayed away functionality.

## Features

- **Multiple Detection Methods**: Supports WiFi MQTT detection, GPS geofencing, and manual controls
- **Unified Interface**: Single driver that implements PresenceSensor capability
- **Manual Override**: Direct control through `present()`, `not_present()`, `arrive()`, and `depart()` commands
- **Activity Tracking**: Maintains `lastActivity` timestamp for all presence changes
- **Debug Logging**: Optional debug logging for troubleshooting
- **Flexible Configuration**: Easy setup through Hubitat's device preferences

## Installation

### Using Hubitat Package Manager (HPM)

1. Install Hubitat Package Manager if not already installed
2. Search for "All-in-One Presence Detection System"
3. Install the package

### Manual Installation

1. Copy the driver code from `drivers/all-in-one-presence.groovy`
2. In Hubitat web interface, go to **Drivers Code**
3. Click **New Driver** and paste the code
4. Click **Save**
5. Go to **Devices** and click **Add Device**
6. Select **Virtual** and choose "All-in-One Presence Driver"

## Usage

### Device Capabilities

The driver implements the following Hubitat capabilities:

- **PresenceSensor**: Standard presence detection with "present"/"not present" states
- **Refresh**: Manual refresh capability

### Available Commands

- `present()` / `arrive()`: Set presence to "present"
- `not_present()` / `depart()`: Set presence to "not present"
- `refresh()`: Update the lastActivity timestamp

### Attributes

- `presence`: enum ["present", "not present"]
- `lastActivity`: string timestamp of last state change

## Configuration

### Device Settings

- **Enable Debug Logging**: Toggle debug logging for troubleshooting (default: false)

### Integration Examples

#### Rule Machine Integration
```groovy
// Trigger when presence changes
IF (All-in-One Presence changes to present) THEN
    // Turn on lights, adjust thermostat, etc.
END-IF
```

#### WebCore Integration
```groovy
// Using presence sensor
IF (All-in-One Presence changes to present) THEN
    // Execute arrival actions
END-IF
```

## Advanced Features

### GPS Commands Support
The driver includes support for GPS-based presence detection through specialized commands:
- `gpsEnter()`: Handle GPS geofence entry
- `gpsExit()`: Handle GPS geofence exit

### Heartbeat Monitoring
- Implements event-based heartbeat system for improved device tracking
- Maintains `lastHeartbeat` attribute for connection monitoring

## Technical Specifications

- **Minimum Hubitat Version**: 2.3.0
- **Namespace**: zekaizer
- **Author**: Luke Lee
- **License**: MIT License
- **Version**: 0.0.1

## Troubleshooting

### Debug Logging
Enable debug logging in device preferences to see detailed operation logs.

### Common Issues

1. **Device not responding**: Check device status and refresh
2. **Presence not updating**: Verify device configuration and check logs
3. **Presence state issues**: Check device configuration and connectivity

## Contributing

Contributions are welcome! Please feel free to submit issues or pull requests.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Links

- **Documentation**: [GitHub Repository](https://github.com/zekaizer/hubitat-presense)
- **Community Support**: [Hubitat Community Forum](https://community.hubitat.com/t/all-in-one-presence-detection-system/)
- **Package Manifest**: Available for Hubitat Package Manager integration

## Changelog

### Version 0.0.1 (2025-01-18)
- Initial release
- Basic presence detection functionality
- WiFi MQTT detection support
- GPS geofencing integration
- Manual override capabilities
- Core presence detection functionality
- Debug logging support