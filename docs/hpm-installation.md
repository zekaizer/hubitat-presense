# HPM Installation Guide

## Overview

This guide explains how to install the WiFi GPS Hybrid Presence System using Hubitat Package Manager (HPM) for a simplified, one-click installation experience.

## Prerequisites

### 1. Install Hubitat Package Manager (HPM)
If you haven't already installed HPM:

1. **Download HPM**:
   - Go to [HPM GitHub Repository](https://github.com/HubitatCommunity/hubitatpackagemanager)
   - Follow the installation instructions

2. **Install HPM App**:
   - Copy the HPM app code
   - Install in Hubitat Developer Tools > Apps Code
   - Create HPM app instance

### 2. Verify Network Access
- Ensure your Hubitat hub has internet access
- Verify GitHub access is not blocked by firewall

## Installation Methods

### Method 1: HPM Search (Recommended)

1. **Open HPM**:
   - Go to Apps > Hubitat Package Manager
   - Click "Browse by Tags" or "Search"

2. **Search for Package**:
   - Search for "WiFi GPS Hybrid Presence"
   - Or search by tag: "Presence"

3. **Install Package**:
   - Click "Install" button
   - Review package details
   - Click "Next" to continue

4. **Confirm Installation**:
   - HPM will show all components to be installed:
     - WiFi GPS Presence Manager Enhanced (App)
     - WiFi GPS Hybrid Presence (Driver)
     - Anyone Presence Override (Driver)
     - Delayed Away Presence (Driver)
   - Click "Install"

### Method 2: Manual Repository Import

1. **Import by URL**:
   - Open HPM
   - Click "Install" > "From a URL"
   - Enter: `https://raw.githubusercontent.com/zekaizer/hubitat-presense/main/packageManifest.json`
   - Click "Next"

2. **Follow Installation Steps**:
   - Review package information
   - Click "Install"
   - Wait for completion

## Post-Installation Setup

### 1. Create App Instance

1. **Navigate to Apps**:
   - Go to Apps > Add User App
   - Select "WiFi GPS Presence Manager Enhanced"

2. **Configure Basic Settings**:
   - Enter app name (e.g., "Presence Manager")
   - Click "Done"

### 2. Add Individual Devices

1. **Go to Device Management**:
   - Open the app
   - Click "Manage Individual Devices"

2. **Add First Device**:
   - Enter device name (e.g., "John's Phone")
   - Enter WiFi MAC address
   - Enter GPS device ID
   - Click "Done"

3. **Repeat for Additional Devices**:
   - Add all family members' devices
   - Ensure unique MAC addresses and GPS IDs

### 3. Configure MQTT (WiFi Detection)

1. **MQTT Settings**:
   - Click "MQTT Settings" in app
   - Enter MQTT broker IP address
   - Enter port (default: 1883)
   - Enter username/password if required
   - Click "Done"

2. **Test MQTT Connection**:
   - Check app logs for connection status
   - Verify topic subscription

### 4. Configure GPS Webhooks

1. **Get Webhook URLs**:
   - Click "Webhook Settings" in app
   - Note the webhook URL format
   - URLs will be: `http://hub-ip/apps/api/app-id/gps/device-id/enter`

2. **Configure GPS Apps**:
   - Set up Tasker, Shortcuts, or IFTTT
   - Use the webhook URLs for enter/exit events

### 5. Test System

1. **Test Individual Devices**:
   - Verify each device shows up in device list
   - Test WiFi detection
   - Test GPS events

2. **Test Combined Presence**:
   - Verify "Anyone Home" device works
   - Test "Away Status" delayed functionality
   - Test manual override features

## Comparison: HPM vs Manual Installation

### HPM Installation Benefits

| Feature | HPM | Manual |
|---------|-----|---------|
| **Installation Time** | 2-3 minutes | 15-20 minutes |
| **Steps Required** | 3 steps | 8+ steps |
| **Error Prone** | Low | High |
| **Updates** | One-click | Manual per file |
| **Dependency Management** | Automatic | Manual |
| **Version Control** | Built-in | Manual tracking |

### HPM Installation Process

```
1. Search "WiFi GPS Hybrid Presence"
2. Click "Install"
3. Configure app instance
```

### Manual Installation Process

```
1. Download/copy driver 1 code
2. Create new driver 1 in Hubitat
3. Paste and save driver 1
4. Download/copy driver 2 code
5. Create new driver 2 in Hubitat
6. Paste and save driver 2
7. Download/copy driver 3 code
8. Create new driver 3 in Hubitat
9. Paste and save driver 3
10. Download/copy app code
11. Create new app in Hubitat
12. Paste and save app
13. Create app instance
14. Configure settings
```

## Updates and Maintenance

### Automatic Updates via HPM

1. **Check for Updates**:
   - Open HPM
   - Click "Update" tab
   - Look for "WiFi GPS Hybrid Presence System"

2. **Install Updates**:
   - Click "Update Available"
   - Review changelog
   - Click "Update"

3. **Verify Update**:
   - Check version numbers
   - Test functionality
   - Review new features

### Update Notifications

HPM can notify you of available updates:
- Enable notifications in HPM settings
- Set check frequency (daily/weekly)
- Choose notification method

## Troubleshooting HPM Installation

### Common Issues

#### 1. Package Not Found
```
Problem: Search doesn't find the package
Solution: 
- Check internet connectivity
- Try manual URL import
- Verify package name spelling
```

#### 2. Installation Fails
```
Problem: Installation stops with error
Solution:
- Check Hubitat logs
- Verify GitHub access
- Try manual installation as backup
```

#### 3. Partial Installation
```
Problem: Some drivers install, others don't
Solution:
- Check for existing drivers with same name
- Remove conflicting drivers
- Restart installation
```

#### 4. Update Problems
```
Problem: Updates fail or don't apply
Solution:
- Check for local modifications
- Backup settings before update
- Try uninstall/reinstall if needed
```

### Getting Help

1. **Check HPM Logs**:
   - Go to Apps > HPM > Logs
   - Look for error messages

2. **Community Support**:
   - Post in [Hubitat Community](https://community.hubitat.com)
   - Include HPM logs and error details

3. **GitHub Issues**:
   - Report bugs on [GitHub Issues](https://github.com/zekaizer/hubitat-presense/issues)
   - Include installation details

## Advanced HPM Features

### Beta Channel
Access beta versions for testing:
1. Open HPM settings
2. Enable "Beta Channel"
3. Install beta versions for testing

### Backup Before Updates
HPM can backup your settings:
1. Enable "Backup Before Updates"
2. Choose backup location
3. Restore if needed

### Custom Repositories
Add custom package repositories:
1. Open HPM settings
2. Add custom repository URL
3. Browse additional packages

## Migration from Manual Installation

### If Already Manually Installed

1. **Backup Current Settings**:
   - Export device configurations
   - Note current settings

2. **Remove Manual Installation**:
   - Delete existing drivers
   - Remove existing app
   - Clean up device instances

3. **Install via HPM**:
   - Follow HPM installation steps
   - Restore settings and configurations

4. **Verify Migration**:
   - Test all functionality
   - Check device assignments
   - Verify automations still work

### Settings Migration Script

```bash
#!/bin/bash
# Backup current settings before migration
echo "Backing up current presence settings..."
# Add backup commands here
echo "Backup completed. Proceed with HPM installation."
```

## Best Practices

### 1. Pre-Installation
- Backup current hub settings
- Document existing device configurations
- Test in development environment first

### 2. During Installation
- Follow HPM prompts carefully
- Don't interrupt installation process
- Monitor logs for errors

### 3. Post-Installation
- Test all devices immediately
- Verify MQTT and GPS connections
- Update documentation with new settings

### 4. Maintenance
- Enable automatic update notifications
- Review changelogs before updating
- Keep backups of working configurations

## Conclusion

HPM installation provides a significantly improved experience for installing and maintaining the WiFi GPS Hybrid Presence System. The one-click installation and automatic updates make it the recommended installation method for most users.

For advanced users who need customization or are contributing to development, manual installation is still available as an option.