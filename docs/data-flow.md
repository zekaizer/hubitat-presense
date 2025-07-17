# Data Flow Documentation

## Overview

This document describes the detailed data flow through the Hubitat WiFi/GPS Hybrid Presence Detection System, including event processing, state transitions, and inter-component communication.

## Core Data Flow Patterns

### 1. WiFi Detection Flow

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   WiFi Router   │    │   MQTT Broker   │    │   Parent App    │
│                 │    │                 │    │                 │
│ Device connects │───▶│ Topic:          │───▶│ mqttMessageRx() │
│ to network      │    │ router/status/  │    │                 │
│                 │    │ mac-XX/lastseen │    │                 │
└─────────────────┘    └─────────────────┘    └─────────┬───────┘
                                                        │
                                                        ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│Individual Driver│    │   Parent App    │    │   Parent App    │
│                 │    │                 │    │                 │
│ wifiDetected()  │◀───│ findDeviceByMAC │◀───│ parseMACFromTopic│
│                 │    │                 │    │                 │
│                 │    │                 │    │                 │
└─────────┬───────┘    └─────────────────┘    └─────────────────┘
          │
          ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│Individual Driver│    │Individual Driver│    │   Parent App    │
│                 │    │                 │    │                 │
│ markAsPresent() │───▶│ sendEvent()     │───▶│individualPresence│
│                 │    │ presence="present"│  │ Handler()       │
│                 │    │                 │    │                 │
└─────────────────┘    └─────────────────┘    └─────────┬───────┘
                                                        │
                                                        ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  Anyone Device  │    │  Anyone Device  │    │  Delayed Away   │
│                 │    │                 │    │    Device       │
│updateFromIndivs │◀───│updateCombined   │───▶│cancelPendingAway│
│                 │    │ Presence()      │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

### 2. GPS Detection Flow

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  GPS Mobile App │    │   Webhook API   │    │   Parent App    │
│                 │    │                 │    │                 │
│ Geofence Enter/ │───▶│ /gps/deviceId/  │───▶│handleGpsWebhook │
│ Exit Event      │    │ enter|exit      │    │                 │
│                 │    │                 │    │                 │
└─────────────────┘    └─────────────────┘    └─────────┬───────┘
                                                        │
                                                        ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│Individual Driver│    │   Parent App    │    │   Parent App    │
│                 │    │                 │    │                 │
│ gpsEntered()/   │◀───│findDeviceByGPS  │◀───│ Parse deviceId  │
│ gpsExited()     │    │ Id()            │    │ and action      │
│                 │    │                 │    │                 │
└─────────┬───────┘    └─────────────────┘    └─────────────────┘
          │
          ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│Individual Driver│    │Individual Driver│    │   Parent App    │
│                 │    │                 │    │                 │
│checkDeparture() │───▶│ sendEvent()     │───▶│individualPresence│
│ (WiFi priority) │    │ (if WiFi timeout│    │ Handler()       │
│                 │    │  + GPS outside) │    │                 │
└─────────────────┘    └─────────────────┘    └─────────┬───────┘
                                                        │
                                                        ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  Anyone Device  │    │  Anyone Device  │    │  Delayed Away   │
│                 │    │                 │    │    Device       │
│updateFromIndivs │◀───│updateCombined   │───▶│ startAwayTimer()│
│                 │    │ Presence()      │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

### 3. Combined Presence Logic Flow

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  Individual     │    │   Parent App    │    │  Anyone Device  │
│  Devices        │    │                 │    │                 │
│ State Changes   │───▶│updateCombined   │───▶│updateFromIndivs │
│                 │    │ Presence()      │    │                 │
│                 │    │                 │    │                 │
└─────────────────┘    └─────────────────┘    └─────────┬───────┘
                                                        │
                                                        ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  Anyone Device  │    │  Anyone Device  │    │  Anyone Device  │
│                 │    │                 │    │                 │
│ overrideStatus  │───▶│ Auto Mode?      │───▶│ Update presence │
│ == "auto"       │    │                 │    │ attribute       │
│                 │    │                 │    │                 │
└─────────────────┘    └─────────────────┘    └─────────┬───────┘
                                                        │
                                                        ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  Delayed Away   │    │  Delayed Away   │    │  Delayed Away   │
│    Device       │    │    Device       │    │    Device       │
│ Anyone present? │───▶│ Cancel timer    │───▶│ Start timer     │
│                 │    │ OR              │    │                 │
│                 │    │                 │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## Detailed Event Processing

### 1. WiFi Detection Event Processing

#### Step 1: MQTT Message Reception
```groovy
def mqttMessageReceived(topic, payload) {
    // Input: "AsusAC68U/status/mac-aa-bb-cc-dd-ee-ff/lastseen/epoch"
    // Payload: "1640995200" (timestamp)
    
    logDebug "MQTT message: ${topic} = ${payload}"
    
    // Parse MAC address from topic
    def mac = parseMacFromTopic(topic)
    // Result: "aa:bb:cc:dd:ee:ff"
}
```

#### Step 2: Device Lookup
```groovy
def findDeviceByMac(mac) {
    def devices = getChildDevices()
    return devices.find { 
        it.data?.mac?.toLowerCase() == mac.toLowerCase() 
    }
}
```

#### Step 3: WiFi Detection Processing
```groovy
def wifiDetected() {
    // Update state
    state.wifiLastSeen = now()
    
    // Send events
    sendEvent(name: "wifiStatus", value: "connected")
    sendEvent(name: "lastWifiSeen", value: new Date().format("yyyy-MM-dd HH:mm:ss"))
    
    // Check for arrival
    if (device.currentValue("presence") != "present") {
        if (arrivalDelay > 0) {
            state.pendingArrival = true
            runIn(arrivalDelay, confirmArrival)
        } else {
            markAsPresent("wifi")
        }
    }
}
```

### 2. GPS Detection Event Processing

#### Step 1: Webhook Reception
```groovy
def handleGpsWebhook() {
    def deviceId = params.deviceId  // From URL path
    def action = params.action      // "enter" or "exit"
    
    logDebug "GPS webhook: ${deviceId} - ${action}"
    
    def device = findDeviceByGpsId(deviceId)
    if (device) {
        if (action == "enter") {
            device.gpsEntered()
        } else if (action == "exit") {
            device.gpsExited()
        }
    }
}
```

#### Step 2: GPS Event Processing
```groovy
def gpsEntered() {
    state.gpsInside = true
    state.gpsLastChange = now()
    
    sendEvent(name: "gpsStatus", value: "inside")
    sendEvent(name: "lastGpsUpdate", value: new Date().format("yyyy-MM-dd HH:mm:ss"))
    
    // GPS entry alone doesn't trigger arrival
    if (device.currentValue("presence") != "present") {
        logDebug "GPS entered but waiting for WiFi confirmation"
    }
}

def gpsExited() {
    state.gpsInside = false
    state.gpsLastChange = now()
    
    sendEvent(name: "gpsStatus", value: "outside")
    sendEvent(name: "lastGpsUpdate", value: new Date().format("yyyy-MM-dd HH:mm:ss"))
    
    // Schedule departure check after delay
    runIn(gpsExitDelay, checkDeparture)
}
```

### 3. Presence State Transitions

#### Arrival State Transition
```
not present → [WiFi Detected] → pending arrival → [Delay] → present
                                       │
                                       ▼
                                [GPS Entry] (informational only)
```

#### Departure State Transition
```
present → [GPS Exit] → [WiFi Check] → [WiFi Connected?] → ignore GPS exit
                              │              │
                              │              ▼
                              │        [WiFi Timeout] → not present
                              │
                              ▼
                        [GPS Outside + WiFi Timeout] → not present
```

## State Management Details

### 1. Individual Device State Variables

```groovy
// Presence state
state.presence = "present" | "not present"

// WiFi tracking
state.wifiLastSeen = 1640995200000  // timestamp
state.pendingArrival = false        // boolean

// GPS tracking
state.gpsInside = true              // boolean
state.gpsLastChange = 1640995200000 // timestamp
```

### 2. Combined Device State Variables

```groovy
// Override management
state.overrideMode = "auto" | "manual-present" | "manual-away"
state.overrideSetTime = 1640995200000  // timestamp
state.overrideReason = "Dashboard switch"

// Individual device tracking
state.individualDevices = [
    "Device1": true,
    "Device2": false
]
```

### 3. State Synchronization

```groovy
def checkPresenceState() {
    def currentPresence = device.currentValue("presence")
    def wifiRecent = isWifiRecent()
    def gpsInside = state.gpsInside
    
    // Update WiFi status
    if (wifiRecent != (device.currentValue("wifiStatus") == "connected")) {
        sendEvent(name: "wifiStatus", value: wifiRecent ? "connected" : "disconnected")
    }
    
    // Calculate new presence state
    if (currentPresence == "present") {
        // For departure: need GPS outside + WiFi timeout
        if (!gpsInside && !wifiRecent) {
            def timeSinceGpsExit = (now() - state.gpsLastChange) / 1000
            if (timeSinceGpsExit > gpsExitDelay) {
                markAsNotPresent("timeout")
            }
        }
    } else {
        // For arrival: WiFi is sufficient
        if (wifiRecent) {
            markAsPresent("wifi-check")
        }
    }
}
```

## Error Handling and Recovery

### 1. Network Failure Recovery

```groovy
def handleMqttDisconnection() {
    // Automatic reconnection
    if (settings.mqttBroker) {
        try {
            subscribeMQTT()
        } catch (Exception e) {
            logError "MQTT reconnection failed: ${e.message}"
            runIn(60, handleMqttDisconnection)  // Retry in 1 minute
        }
    }
}
```

### 2. State Recovery

```groovy
def forceRefresh() {
    logInfo "Force refresh - clearing all states"
    state.clear()
    initialize()
}
```

### 3. Consistency Checks

```groovy
def validateStateConsistency() {
    def presence = device.currentValue("presence")
    def wifiStatus = device.currentValue("wifiStatus")
    def gpsStatus = device.currentValue("gpsStatus")
    
    // Check for inconsistencies
    if (presence == "present" && wifiStatus == "disconnected" && gpsStatus == "outside") {
        logWarn "Inconsistent state detected - running recovery"
        checkPresenceState()
    }
}
```

## Performance Optimization

### 1. Event Batching

```groovy
def batchStateUpdates() {
    // Batch multiple state updates into single operation
    def events = []
    events.add([name: "presence", value: "present"])
    events.add([name: "confidence", value: calculateConfidence()])
    events.add([name: "method", value: "wifi"])
    
    // Send all events at once
    events.each { event ->
        sendEvent(event)
    }
}
```

### 2. Caching Strategy

```groovy
def isWifiRecent() {
    // Cache calculation result for 30 seconds
    def cacheKey = "wifiRecent_${now() / 30000}"
    if (state.cache?."${cacheKey}") {
        return state.cache."${cacheKey}"
    }
    
    def wifiAge = (now() - state.wifiLastSeen) / 1000
    def result = wifiAge < wifiTimeout
    
    state.cache = state.cache ?: [:]
    state.cache."${cacheKey}" = result
    
    return result
}
```

### 3. Scheduled Processing

```groovy
def initialize() {
    // Schedule periodic state check every minute
    runEvery1Minute(checkPresenceState)
    
    // Schedule cleanup every hour
    runEvery1Hour(cleanupOldData)
}
```

## Logging and Monitoring

### 1. Event Logging

```groovy
def logPresenceChange(oldState, newState, method) {
    logInfo "Presence changed: ${oldState} → ${newState} via ${method}"
    
    // Additional context logging
    logDebug "WiFi: ${device.currentValue('wifiStatus')}"
    logDebug "GPS: ${device.currentValue('gpsStatus')}"
    logDebug "Confidence: ${device.currentValue('confidence')}%"
}
```

### 2. Performance Monitoring

```groovy
def measurePerformance(action) {
    def startTime = now()
    
    try {
        // Execute action
        "$action"()
        
        def duration = now() - startTime
        logDebug "Performance: ${action} completed in ${duration}ms"
        
    } catch (Exception e) {
        logError "Performance: ${action} failed - ${e.message}"
    }
}
```

### 3. Health Checks

```groovy
def runHealthCheck() {
    def health = [:]
    
    // Check WiFi connectivity
    health.wifi = isWifiRecent()
    
    // Check GPS updates
    health.gps = isGpsRecent()
    
    // Check state consistency
    health.stateConsistent = validateStateConsistency()
    
    logInfo "Health check: ${health}"
    return health
}
```