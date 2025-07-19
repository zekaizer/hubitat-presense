# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Hubitat Elevation presence detection driver that consolidates multiple presence detection methods (WiFi MQTT, GPS geofencing, manual controls) into a single unified driver. The project consists of a single Groovy driver file and supporting package manifest for Hubitat Package Manager distribution.

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

- WiFi presence is transmitted via MQTT, with a heartbeat approximately every 1 second containing an epoch timestamp
- GPS presence is managed through MakersAPI, with enter/exit events reported

## Driver Design Principles

- The driver's main goal is to quickly determine actual presence and prevent false negatives (mistakenly identifying presence as non-presence)
- Rapid detection of presence is critical, while the detection of non-presence can be less immediate

## Hubitat API Usage

- When using Hubitat API, always check the official Hubitat documentation and use it correctly