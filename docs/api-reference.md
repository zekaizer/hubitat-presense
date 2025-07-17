# API Reference

## Overview

This document provides comprehensive API reference for all drivers and apps in the Hubitat WiFi/GPS Hybrid Presence Detection System.

## WiFi GPS Hybrid Presence Driver

### Metadata

```groovy
name: "WiFi GPS Hybrid Presence"
namespace: "custom"
capabilities: ["Presence Sensor", "Sensor", "Refresh"]
```

### Core Logic

The driver implements **WiFi Priority Logic**:
- **Arrival**: WiFi detection triggers immediate arrival (no GPS entry required)
- **Departure**: WiFi connected = ignore GPS exit; requires WiFi timeout + GPS outside
- **GPS Role**: Informational only, WiFi has full priority

### Attributes

| Attribute | Type | Values | Description |
|-----------|------|--------|-------------|
| `presence` | enum | `present`, `not present` | Current presence state |
| `wifiStatus` | enum | `connected`, `disconnected`, `unknown` | WiFi connection status |
| `gpsStatus` | enum | `inside`, `outside`, `unknown` | GPS geofence status |
| `lastWifiSeen` | string | ISO timestamp | Last WiFi detection time |
| `lastGpsUpdate` | string | ISO timestamp | Last GPS update time |
| `confidence` | number | 0-100 | Presence confidence percentage |
| `method` | string | Detection method | How presence was determined |

### Commands

#### Core Commands

##### `arrived()`
Manually sets device as present.
```groovy
device.arrived()
```

##### `departed()`
Manually sets device as not present.
```groovy
device.departed()
```

##### `refresh()`
Refreshes device state and recalculates presence.
```groovy
device.refresh()
```

##### `forceRefresh()`
Clears all state and reinitializes device.
```groovy
device.forceRefresh()
```

#### WiFi Commands

##### `wifiDetected()`
Called when WiFi presence is detected.
```groovy
device.wifiDetected()
```
- Updates `wifiStatus` to "connected"
- Updates `lastWifiSeen` timestamp
- **Triggers arrival logic** if not present (primary arrival method)

##### `wifiLost()`
Called when WiFi connection is lost.
```groovy
device.wifiLost()
```
- Updates `wifiStatus` to "disconnected"
- Schedules departure check after timeout

#### GPS Commands

##### `gpsEntered()`
Called when device enters GPS geofence.
```groovy
device.gpsEntered()
```
- Updates `gpsStatus` to "inside"
- Updates `lastGpsUpdate` timestamp
- **Informational only** - does not trigger arrival

##### `gpsExited()`
Called when device exits GPS geofence.
```groovy
device.gpsExited()
```
- Updates `gpsStatus` to "outside"
- Updates `lastGpsUpdate` timestamp
- **Checks departure immediately** (WiFi priority logic applies)

### Preferences

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `deviceName` | text | - | Display name for device |
| `wifiTimeout` | number | 180 | WiFi timeout in seconds |
| `gpsExitDelay` | number | 120 | GPS exit delay in seconds (deprecated) |
| `arrivalDelay` | number | 0 | Arrival delay in seconds |
| `enableDebug` | bool | true | Enable debug logging |
| `enableInfo` | bool | true | Enable info logging |

### State Variables

| Variable | Type | Description |
|----------|------|-------------|
| `presence` | string | Current presence state |
| `wifiLastSeen` | number | WiFi last seen timestamp |
| `gpsInside` | boolean | GPS inside geofence |
| `gpsLastChange` | number | GPS last change timestamp |
| `pendingArrival` | boolean | Arrival pending confirmation |

### Methods

#### `isWifiRecent()`
Checks if WiFi was detected recently.
```groovy
def recent = isWifiRecent()
// Returns: boolean
```

#### `calculateConfidence()`
Calculates presence confidence score.
```groovy
def confidence = calculateConfidence()
// Returns: number (0-100)
```

#### `checkDeparture()`
**Updated Logic**: Checks departure with WiFi priority.
```groovy
def checkDeparture() {
    def wifiRecent = isWifiRecent()
    def gpsOutside = !state.gpsInside
    
    // WiFi has priority - if WiFi connected, ignore GPS exit
    if (wifiRecent) {
        logDebug "WiFi still connected, ignoring GPS exit"
        return
    }
    
    // Only depart if WiFi timeout AND GPS outside
    if (!wifiRecent && gpsOutside) {
        markAsNotPresent("wifi-timeout")
    }
}
```

#### `markAsPresent(method)`
Marks device as present with specified method.
```groovy
markAsPresent("wifi")
// method: string - detection method
```

#### `markAsNotPresent(method)`
Marks device as not present with specified method.
```groovy
markAsNotPresent("wifi-timeout")
// method: string - detection method
```

---

## Anyone Presence Override Driver

### Metadata

```groovy
name: "Anyone Presence Override"
namespace: "custom"
capabilities: ["Presence Sensor", "Switch", "Sensor"]
```

### Attributes

| Attribute | Type | Values | Description |
|-----------|------|--------|-------------|
| `presence` | enum | `present`, `not present` | Current presence state |
| `switch` | enum | `on`, `off` | Switch state for dashboard |
| `overrideStatus` | enum | `auto`, `manual-present`, `manual-away` | Override mode |
| `autoPresence` | enum | `present`, `not present` | Automatic presence state |
| `individualCount` | number | 0+ | Number of present individuals |
| `presentDevices` | string | Device names | List of present devices |
| `lastOverrideTime` | string | ISO timestamp | Last override time |
| `overrideReason` | string | Text | Override reason |

### Commands

#### Core Commands

##### `arrived()`
Sets presence to present (if in auto mode).
```groovy
device.arrived()
```

##### `departed()`
Sets presence to not present (if in auto mode).
```groovy
device.departed()
```

#### Switch Commands

##### `on()`
Turns switch on (manual present).
```groovy
device.on()
```

##### `off()`
Turns switch off (manual away).
```groovy
device.off()
```

#### Override Commands

##### `setManualPresent(reason)`
Sets manual present override.
```groovy
device.setManualPresent("Dashboard control")
// reason: string (optional) - override reason
```

##### `setManualAway(reason)`
Sets manual away override.
```groovy
device.setManualAway("Vacation mode")
// reason: string (optional) - override reason
```

##### `clearOverride()`
Clears manual override and returns to auto mode.
```groovy
device.clearOverride()
```

##### `setAutoMode()`
Alias for `clearOverride()`.
```groovy
device.setAutoMode()
```

#### System Commands

##### `updateFromIndividuals(anyonePresent)`
Updates presence based on individual devices.
```groovy
device.updateFromIndividuals(true)
// anyonePresent: boolean
```

##### `updateIndividualTracking(deviceMap)`
Updates individual device tracking.
```groovy
device.updateIndividualTracking([
    "Device1": true,
    "Device2": false
])
// deviceMap: Map<String, Boolean>
```

### Preferences

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `overrideTimeout` | number | 24 | Override timeout in hours (0=no timeout) |
| `notifyOnOverride` | bool | true | Send notifications on override |
| `enableDebug` | bool | false | Enable debug logging |

### State Variables

| Variable | Type | Description |
|----------|------|-------------|
| `overrideMode` | string | Current override mode |
| `overrideSetTime` | number | Override set timestamp |
| `overrideReason` | string | Override reason |
| `individualDevices` | map | Individual device states |

---

## Delayed Away Presence Driver

### Metadata

```groovy
name: "Delayed Away Presence"
namespace: "custom"
capabilities: ["Presence Sensor", "Sensor"]
```

### Attributes

| Attribute | Type | Values | Description |
|-----------|------|--------|-------------|
| `presence` | enum | `present`, `not present` | Current presence state |
| `pendingAway` | bool | `true`, `false` | Away confirmation pending |
| `pendingTime` | string | HH:MM format | Time when away will be confirmed |
| `delayMinutes` | number | 1-360 | Current delay in minutes |

### Commands

#### Core Commands

##### `arrived()`
Sets presence to present and cancels pending away.
```groovy
device.arrived()
```

##### `departed()`
Triggers away timer (called by parent app).
```groovy
device.departed()
```

#### Timer Commands

##### `startAwayTimer()`
Starts the away confirmation timer.
```groovy
device.startAwayTimer()
```

##### `cancelPendingAway()`
Cancels pending away status.
```groovy
device.cancelPendingAway()
```

##### `confirmAway()`
Immediately confirms away status.
```groovy
device.confirmAway()
```

##### `setDelay(minutes)`
Sets the away delay.
```groovy
device.setDelay(90)
// minutes: number (1-360)
```

### Preferences

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `defaultDelay` | number | 60 | Default away delay in minutes |
| `enableDebug` | bool | false | Enable debug logging |

### State Variables

| Variable | Type | Description |
|----------|------|-------------|
| `pendingAway` | boolean | Away confirmation pending |
| `awayStartTime` | number | Away timer start timestamp |

---

## WiFi GPS Presence Manager Enhanced (App)

### Definition

```groovy
name: "WiFi GPS Presence Manager Enhanced"
namespace: "custom"
singleInstance: true
```

### Configuration Pages

#### Main Page (`mainPage`)
Primary configuration interface.

#### Devices Page (`devicesPage`)
Individual device management.

#### Anyone & Away Page (`anyoneAwayPage`)
Combined presence configuration.

#### MQTT Page (`mqttPage`)
MQTT broker settings.

#### Webhook Page (`webhookPage`)
GPS webhook configuration.

### Settings

#### Device Settings
| Setting | Type | Description |
|---------|------|-------------|
| `newDeviceName` | text | New device name |
| `newDeviceMAC` | text | New device MAC address |
| `newDeviceGPSID` | text | New device GPS ID |

#### Combined Presence Settings
| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `anyoneDeviceName` | text | "Anyone Home" | Anyone device name |
| `awayDeviceName` | text | "Away Status" | Away device name |
| `awayDelay` | number | 60 | Away delay in minutes |
| `awayDelayReset` | bool | true | Reset delay if someone returns |

#### MQTT Settings
| Setting | Type | Description |
|---------|------|-------------|
| `mqttBroker` | text | MQTT broker IP address |
| `mqttPort` | number | MQTT broker port |
| `mqttUsername` | text | MQTT username |
| `mqttPassword` | password | MQTT password |

#### Notification Settings
| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `notifyOnAnyoneChange` | bool | true | Notify on anyone status change |
| `notifyOnAwayConfirmed` | bool | true | Notify when away is confirmed |
| `enableDebug` | bool | false | Enable debug logging |

### Event Handlers

#### `individualPresenceHandler(evt)`
Handles individual device presence changes.
```groovy
subscribe(device, "presence", individualPresenceHandler)
```

#### `mqttMessageReceived(topic, payload)`
Handles MQTT messages.
```groovy
// Called automatically by MQTT integration
```

### Webhook Endpoints

#### GPS Webhook
```
GET /apps/api/{appId}/gps/{deviceId}/{action}
```

Parameters:
- `deviceId`: GPS device identifier
- `action`: "enter" or "exit"

Example:
```
GET /apps/api/123/gps/phone1/enter
GET /apps/api/123/gps/phone1/exit
```

### Methods

#### Device Management

##### `createPresenceDevice(name, mac, gpsId)`
Creates a new presence device.
```groovy
createPresenceDevice("John's Phone", "aa:bb:cc:dd:ee:ff", "phone1")
```

##### `createAnyoneDevice()`
Creates the anyone home device.
```groovy
createAnyoneDevice()
```

##### `createAwayDevice()`
Creates the delayed away device.
```groovy
createAwayDevice()
```

#### Presence Logic

##### `updateCombinedPresence()`
Updates combined presence status.
```groovy
updateCombinedPresence()
```

##### `confirmAway()`
Confirms away status after delay.
```groovy
confirmAway()
```

#### Helper Methods

##### `findDeviceByMac(mac)`
Finds device by MAC address.
```groovy
def device = findDeviceByMac("aa:bb:cc:dd:ee:ff")
```

##### `findDeviceByGpsId(gpsId)`
Finds device by GPS ID.
```groovy
def device = findDeviceByGpsId("phone1")
```

##### `parseMacFromTopic(topic)`
Parses MAC address from MQTT topic.
```groovy
def mac = parseMacFromTopic("router/status/mac-aa-bb-cc/lastseen")
// Returns: "aa:bb:cc"
```

##### `getStatusSummary()`
Gets current system status summary.
```groovy
def summary = getStatusSummary()
// Returns: HTML formatted status string
```

---

## Updated Logic Summary

### WiFi Priority Logic

#### Arrival Logic
```groovy
// WiFi detection = immediate arrival (no GPS entry required)
def wifiDetected() {
    markAsPresent("wifi")  // Direct arrival
}

// GPS entry = informational only
def gpsEntered() {
    // Update GPS status only, no arrival logic
    logDebug "GPS entered - informational only"
}
```

#### Departure Logic
```groovy
// GPS exit with WiFi priority check
def gpsExited() {
    checkDeparture()  // Immediate check with WiFi priority
}

def checkDeparture() {
    if (isWifiRecent()) {
        return  // WiFi connected = ignore GPS exit
    }
    
    if (!isWifiRecent() && !state.gpsInside) {
        markAsNotPresent("wifi-timeout")  // Both conditions met
    }
}
```

### Key Changes from Original Design

1. **GPS Timeout Removed**: GPS only sends enter/exit events
2. **WiFi Priority**: WiFi connection overrides GPS exit
3. **Simplified Arrival**: WiFi detection alone triggers arrival
4. **Departure Logic**: Requires WiFi timeout AND GPS outside
5. **GPS Role**: Informational only, no direct presence control

---

## Common Patterns

### Event Handling
```groovy
// Subscribe to events
subscribe(device, "presence", eventHandler)

// Event handler
def eventHandler(evt) {
    log.info "${evt.device} ${evt.name} is ${evt.value}"
}
```

### State Management
```groovy
// Set state
state.myVariable = "value"

// Get state
def value = state.myVariable

// Clear state
state.clear()
```

### Scheduling
```groovy
// One-time scheduling
runIn(60, methodName)

// Recurring scheduling
runEvery1Minute(methodName)
runEvery5Minutes(methodName)
runEvery1Hour(methodName)

// Cancel scheduling
unschedule(methodName)
unschedule() // Cancel all
```

### Event Generation
```groovy
// Send event
sendEvent(name: "presence", value: "present")

// Send event with description
sendEvent(
    name: "presence", 
    value: "present", 
    descriptionText: "Device has arrived"
)
```

### Logging
```groovy
// Conditional logging
def logDebug(msg) {
    if (enableDebug) log.debug msg
}

def logInfo(msg) {
    log.info msg
}

def logWarn(msg) {
    log.warn msg
}

def logError(msg) {
    log.error msg
}
```

---

## Error Handling

### Common Exceptions
```groovy
try {
    // Risky operation
    device.someMethod()
} catch (Exception e) {
    log.error "Operation failed: ${e.message}"
}
```

### Validation
```groovy
// Validate input
if (!deviceId) {
    log.error "Device ID is required"
    return [error: "Device ID is required"]
}

// Validate device exists
def device = findDeviceById(deviceId)
if (!device) {
    log.error "Device not found: ${deviceId}"
    return [error: "Device not found"]
}
```

### Recovery
```groovy
// Automatic recovery
def retryOperation() {
    try {
        performOperation()
    } catch (Exception e) {
        log.warn "Operation failed, retrying in 60 seconds"
        runIn(60, retryOperation)
    }
}
```