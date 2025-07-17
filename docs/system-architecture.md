# System Architecture

## Overview

The Hubitat WiFi/GPS Hybrid Presence Detection System is a comprehensive solution that combines multiple data sources to provide accurate presence detection with minimal false positives.

## System Components

### 1. Core Architecture

```
┌─────────────────────────────────────────────────────────┐
│                External Systems                          │
├─────────────────────────────────────────────────────────┤
│  MQTT Broker              GPS Mobile App                │
│  (WiFi Router)            (Webhooks)                    │
└─────────────────┬───────────────────┬───────────────────┘
                  │                   │
                  │                   │
┌─────────────────▼───────────────────▼───────────────────┐
│                 Parent App                              │
│         (WiFi GPS Presence Manager)                     │
├─────────────────────────────────────────────────────────┤
│  • MQTT Message Processing                              │
│  • GPS Webhook Handling                                 │
│  • Device Management                                     │
│  • Combined Presence Logic                              │
└─────────────────┬───────────────────┬───────────────────┘
                  │                   │
                  │                   │
┌─────────────────▼───────────────────▼───────────────────┐
│                Virtual Devices                          │
├─────────────────────────────────────────────────────────┤
│  Individual       Anyone Home        Delayed Away       │
│  Presence         Override           Presence           │
│  Devices          Device             Device             │
└─────────────────────────────────────────────────────────┘
```

### 2. Component Responsibilities

#### Parent App (`apps/presence-manager.groovy`)
- **Central Controller**: Manages all presence detection logic
- **MQTT Integration**: Receives WiFi presence data from router
- **GPS Integration**: Handles GPS geofencing webhooks
- **Device Orchestration**: Coordinates between individual devices
- **Combined Logic**: Implements "Anyone Home" and "Delayed Away" features

#### Individual Presence Driver (`drivers/wifi-gps-hybrid-presence.groovy`)
- **Hybrid Detection**: Combines WiFi and GPS data with WiFi priority
- **Arrival Logic**: WiFi detection triggers arrival (no GPS entry required)
- **Departure Logic**: WiFi timeout + GPS outside required (WiFi priority)
- **WiFi Priority**: WiFi connected = ignore GPS exit events
- **Confidence Scoring**: Calculates presence confidence level
- **State Management**: Maintains device-specific presence state

#### Anyone Presence Override (`drivers/anyone-presence-override.groovy`)
- **Aggregation**: Combines all individual devices into single status
- **Manual Override**: Allows manual presence control
- **Dashboard Integration**: Provides switch capability
- **Automatic Fallback**: Returns to automatic mode after timeout

#### Delayed Away Presence (`drivers/delayed-away-presence.groovy`)
- **False Positive Prevention**: Delays away confirmation
- **Configurable Timing**: Adjustable delay periods
- **Cancellation Logic**: Cancels delay if someone returns
- **Pending Status**: Shows pending away state

## Data Flow Architecture

### 1. WiFi Detection Flow

```
WiFi Router → MQTT Broker → Parent App → Individual Driver
     │              │            │              │
     │              │            │              ▼
     │              │            │         wifiDetected()
     │              │            │              │
     │              │            │              ▼
     │              │            │         Arrival Logic
     │              │            │              │
     │              │            │              ▼
     │              │            │         updateCombinedPresence()
     │              │            │              │
     │              │            │              ▼
     │              │            │         Anyone Device Update
```

### 2. GPS Detection Flow

```
GPS Mobile App → Webhook → Parent App → Individual Driver
      │              │          │              │
      │              │          │              ▼
      │              │          │         gpsEntered()/gpsExited()
      │              │          │              │
      │              │          │              ▼
      │              │          │         Departure Logic
      │              │          │              │
      │              │          │              ▼
      │              │          │         updateCombinedPresence()
      │              │          │              │
      │              │          │              ▼
      │              │          │         Away Device Update
```

### 3. Combined Presence Logic

```
Individual Devices → Anyone Device → Delayed Away Device
        │                │                    │
        │                │                    ▼
        │                │              startAwayTimer()
        │                │                    │
        │                │                    ▼
        │                │              confirmAway()
        │                │                    │
        │                ▼                    │
        │         Manual Override             │
        │              │                      │
        │              ▼                      │
        │         Dashboard Control           │
        │              │                      │
        ▼              ▼                      ▼
    Rule Machine ← Final Presence State → Automations
```

## Integration Points

### 1. MQTT Integration
- **Broker Connection**: Connects to home network MQTT broker
- **Topic Subscription**: Monitors WiFi device presence topics
- **Message Processing**: Parses MAC addresses and timestamps
- **Device Mapping**: Maps MAC addresses to presence devices

### 2. GPS Webhook Integration
- **Endpoint Exposure**: Provides HTTP endpoints for GPS apps
- **Geofence Events**: Handles enter/exit events
- **Device Identification**: Maps GPS device IDs to presence devices
- **Security**: Validates webhook requests

### 3. Hubitat Platform Integration
- **Capability Compliance**: Implements standard Hubitat capabilities
- **Event System**: Uses Hubitat's event system for state changes
- **Rule Machine**: Compatible with Hubitat's automation engine
- **Dashboard**: Provides dashboard tiles and controls

## State Management

### 1. Individual Device State
```groovy
state.presence = "present|not present"
state.wifiLastSeen = timestamp
state.gpsInside = boolean
state.gpsLastChange = timestamp
state.pendingArrival = boolean
```

### 2. Combined Device State
```groovy
state.overrideMode = "auto|manual-present|manual-away"
state.overrideSetTime = timestamp
state.overrideReason = string
state.individualDevices = map
```

### 3. Delayed Away State
```groovy
state.pendingAway = boolean
state.awayStartTime = timestamp
```

## Timing and Delays

### 1. Arrival Timing
- **WiFi Detection**: Immediate trigger (0-5 seconds)
- **Arrival Delay**: Configurable (default: 0 seconds)
- **GPS Confirmation**: Optional validation

### 2. Departure Timing
- **GPS Exit**: Immediate detection (informational only)
- **WiFi Timeout**: Configurable (default: 180 seconds)
- **Departure Logic**: WiFi timeout + GPS outside required
- **WiFi Priority**: WiFi connected = ignore GPS exit
- **Away Delay**: Configurable (default: 60 minutes)

### 3. Override Timing
- **Manual Override**: Immediate activation
- **Override Timeout**: Configurable (default: 24 hours)
- **Automatic Fallback**: Returns to auto mode after timeout

## Error Handling and Resilience

### 1. Network Failures
- **MQTT Disconnection**: Automatic reconnection attempts
- **WiFi Timeout**: Combined with GPS state for departure decision
- **GPS Events**: Only enter/exit events (no timeout concept)

### 2. State Inconsistencies
- **Periodic Refresh**: Regular state validation
- **Manual Recovery**: Force refresh commands
- **Logging**: Comprehensive debug logging

### 3. Device Failures
- **Individual Device**: Other devices continue functioning
- **Combined Device**: Fallback to individual device states
- **App Failure**: Devices maintain last known state

## Performance Considerations

### 1. Event Processing
- **Asynchronous Processing**: Non-blocking event handling
- **Batch Updates**: Efficient state updates
- **Throttling**: Prevents event flooding

### 2. Memory Management
- **State Cleanup**: Regular cleanup of old state data
- **Efficient Storage**: Minimal state variable usage
- **Cache Management**: Intelligent caching strategies

### 3. Network Optimization
- **Connection Pooling**: Efficient MQTT connections
- **Message Filtering**: Process only relevant messages
- **Compression**: Minimize data transfer

## Security Architecture

### 1. Network Security
- **MQTT Authentication**: Username/password authentication
- **Webhook Validation**: Request validation and sanitization
- **Access Control**: Limited endpoint exposure

### 2. Data Protection
- **Sensitive Data**: No storage of sensitive information
- **Logging**: Careful log data sanitization
- **State Encryption**: Encrypted state storage where possible

### 3. Device Security
- **MAC Address Handling**: Secure MAC address processing
- **Device Identification**: Secure device ID management
- **Access Logging**: Comprehensive access logging