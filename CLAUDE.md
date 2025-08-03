# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Hubitat Elevation presence detection driver that consolidates multiple presence detection methods (WiFi MQTT, GPS geofencing, manual controls) into a single unified driver. The project consists of a single Groovy driver file and supporting package manifest for Hubitat Package Manager distribution.

The driver now includes integration with homebridge-securitysystem for advanced presence and guest management through HomeKit Security System interface.

## Architecture

- **Single Driver Architecture**: The entire functionality is contained within `drivers/all-in-one-presence.groovy`
- **Hubitat Framework**: Uses Hubitat's device driver framework with standard capabilities (PresenceSensor, Refresh)
- **Event-Driven**: Presence state changes trigger sendEvent() calls to update device attributes
- **Package Distribution**: Configured for distribution via Hubitat Package Manager using `packageManifest.json`

## Key Components

### Driver Capabilities
- `PresenceSensor`: Core presence detection with "present"/"not present" states
- `Refresh`: Manual refresh capability for updating lastActivity timestamp

### Command Interface
- `present()` / `arrive()`: Set presence to "present"
- `not_present()` / `depart()`: Set presence to "not present" 
- `refresh()`: Update lastActivity timestamp

### Device Attributes
- `presence`: enum ["present", "not present"] - main presence state
- `lastActivity`: string timestamp of last state change
- `lastHeartbeat`: string timestamp of last WiFi heartbeat received

## Development Notes

- The driver follows Hubitat's standard lifecycle methods: `installed()`, `updated()`, `initialize()`
- Debug logging is configurable via device preferences
- All state changes update both presence and lastActivity attributes
- The driver is designed to be extended with additional presence detection methods (WiFi MQTT, GPS, etc.)

## Package Management

- Version information and metadata are maintained in `packageManifest.json`
- The package is configured for GitHub raw file distribution
- Minimum Hubitat version requirement: 2.3.0
- Licensed under MIT License

## Project Philosophy

- This driver is a personal project that aims to avoid over-engineering
- New feature additions and modifications require explicit user confirmation

## Presence Detection Methods

### WiFi MQTT Presence Detection
- WiFi presence is transmitted via MQTT, with a heartbeat approximately every 1 second containing an epoch timestamp
- MQTT topics: `UnifiU6Pro/status/mac-{macaddr}/lastseen/epoch` and `AsusAC68U/status/mac-{macaddr}/lastseen/epoch`
- MAC address normalization: supports both colon (:) and dash (-) formats, normalized to lowercase with dashes
- Heartbeat timeout: presence is set to "present" if heartbeat is within 30 seconds
- State persistence: presence states are saved to device state for recovery after restarts

### GPS Presence Detection
- GPS presence is managed through MakersAPI, with enter/exit events reported

## Driver Design Principles

- The driver's main goal is to quickly determine actual presence and prevent false negatives (mistakenly identifying presence as non-presence)
- Rapid detection of presence is critical, while the detection of non-presence can be less immediate
- State recovery: driver restores last known presence state after hub restart or driver reload

## Hubitat API Usage

- When using Hubitat API, always check the official [Hubitat documentation](https://docs2.hubitat.com/en/developer/driver/overview) and use it correctly

## Security System Integration

### Overview
The driver supports integration with homebridge-securitysystem to provide a unified presence and guest management interface through HomeKit's Security System.

### Configuration
1. Enable Security System Integration in driver preferences
2. Set the homebridge-securitysystem server URL and port
3. Configure homebridge-securitysystem webhook to call Hubitat MakerAPI endpoint:
   - Command: `setSecuritySystemMode`
   - Parameter: mode (off, home, away, night)

### Mode Mapping
- **Off**: Guest access enabled (overrides presence detection)
- **Home**: Residents present, no guests
- **Away**: No one present
- **Night**: Not currently used

### Implementation Details
- When Security System integration is enabled, it replaces the legacy Anyone Motion and Guest Access Lock devices
- The driver automatically updates Security System mode based on presence:
  - Any resident present → Home mode
  - No residents present → Away mode
  - User can manually set Off mode for guest access
- Off mode acts as a presence override - the system behaves as if someone is always present

## MQTT Behavior Notes

- When WiFi is disconnected, MQTT heartbeat messages stop coming
- Heartbeat timeout detection is needed to automatically set presence to "not present"