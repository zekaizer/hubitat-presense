/**
 *  WiFi + GPS Hybrid Presence Driver
 *
 *  Copyright 2025
 *  
 *  Individual device driver for WiFi and GPS based presence detection
 */
metadata {
    definition (name: "WiFi GPS Hybrid Presence", namespace: "zekaizer", author: "Luke Lee") {
        capability "Presence Sensor"
        capability "Sensor"
        capability "Refresh"
        
        attribute "wifiStatus", "enum", ["connected", "disconnected", "unknown"]
        attribute "gpsStatus", "enum", ["inside", "outside", "unknown"]
        attribute "lastWifiSeen", "string"
        attribute "lastGpsUpdate", "string"
        attribute "confidence", "number"
        attribute "method", "string"
        
        command "arrived"
        command "departed"
        command "wifiDetected"
        command "wifiLost"
        command "gpsEntered"
        command "gpsExited"
        command "forceRefresh"
    }
    
    preferences {
        input "deviceName", "text", title: "Device Name", defaultValue: "${device.displayName ?: 'Unknown Device'}", required: true
        input "wifiTimeout", "number", title: "WiFi Timeout (seconds)", defaultValue: 180, required: true
        input "gpsExitDelay", "number", title: "GPS Exit Delay (seconds)", defaultValue: 120, required: true
        input "arrivalDelay", "number", title: "Arrival Delay (seconds)", defaultValue: 0
        input "enableDebug", "bool", title: "Enable Debug Logging", defaultValue: true
        input "enableInfo", "bool", title: "Enable Info Logging", defaultValue: true
    }
}

// State variables
def installed() {
    logDebug "Installing WiFi GPS Hybrid Presence Driver"
    initialize()
}

def updated() {
    logDebug "Updating WiFi GPS Hybrid Presence Driver"
    unschedule()
    initialize()
}

def initialize() {
    state.presence = "not present"
    state.wifiLastSeen = 0
    state.gpsInside = false
    state.gpsLastChange = now()
    state.pendingArrival = false
    
    sendEvent(name: "presence", value: "not present")
    sendEvent(name: "wifiStatus", value: "unknown")
    sendEvent(name: "gpsStatus", value: "unknown")
    sendEvent(name: "confidence", value: 0)
    
    // Schedule periodic state check
    runEvery1Minute(checkPresenceState)
}

// WiFi Detection Methods
def wifiDetected(epochTime = null) {
    def name = deviceName ?: device.displayName ?: "Device"
    logInfo "${name} WiFi detected"
    
    // Use provided epoch time or current time
    if (epochTime) {
        try {
            def epochLong = epochTime.toString().toLong()
            state.wifiLastEpoch = epochLong
            state.wifiLastSeen = epochLong * 1000  // Convert to milliseconds
            logDebug "Using epoch time: ${epochLong}"
        } catch (Exception e) {
            state.wifiLastSeen = now()
            logDebug "Failed to parse epoch time, using current time"
        }
    } else {
        state.wifiLastSeen = now()
    }
    
    sendEvent(name: "wifiStatus", value: "connected")
    sendEvent(name: "lastWifiSeen", value: new Date().format("yyyy-MM-dd HH:mm:ss"))
    
    // Check for immediate arrival
    if (device.currentValue("presence") != "present") {
        if (arrivalDelay > 0) {
            state.pendingArrival = true
            runIn(arrivalDelay, confirmArrival)
            logInfo "Scheduling arrival confirmation in ${arrivalDelay} seconds"
        } else {
            markAsPresent("wifi")
        }
    }
}

def wifiLost() {
    def name = deviceName ?: device.displayName ?: "Device"
    logInfo "${name} WiFi lost"
    
    // Update last seen time to current to start timeout from now
    state.wifiLastSeen = now()
    sendEvent(name: "wifiStatus", value: "disconnected")
    
    // Don't immediately mark as away - wait for GPS confirmation
    runIn(wifiTimeout, checkDeparture)
    logDebug "Will check departure in ${wifiTimeout} seconds"
}

// GPS Methods
def gpsEntered() {
    def name = deviceName ?: device.displayName ?: "Device"
    logInfo "${name} entered GPS geofence"
    
    state.gpsInside = true
    state.gpsLastChange = now()
    sendEvent(name: "gpsStatus", value: "inside")
    sendEvent(name: "lastGpsUpdate", value: new Date().format("yyyy-MM-dd HH:mm:ss"))
    
    // GPS entry is informational only - WiFi handles arrival
    logDebug "GPS entered - informational only, WiFi will handle arrival"
}

def gpsExited() {
    def name = deviceName ?: device.displayName ?: "Device"
    logInfo "${name} exited GPS geofence"
    
    state.gpsInside = false
    state.gpsLastChange = now()
    sendEvent(name: "gpsStatus", value: "outside")
    sendEvent(name: "lastGpsUpdate", value: new Date().format("yyyy-MM-dd HH:mm:ss"))
    
    // Check departure immediately - WiFi priority will be handled in checkDeparture
    checkDeparture()
}

// Presence State Management
def markAsPresent(method) {
    if (device.currentValue("presence") != "present") {
        def name = deviceName ?: device.displayName ?: "Device"
        logInfo "${name} arrived via ${method}"
        sendEvent(name: "presence", value: "present", descriptionText: "${deviceName} has arrived")
        sendEvent(name: "method", value: method)
        sendEvent(name: "confidence", value: calculateConfidence())
        state.presence = "present"
        state.pendingArrival = false
        
        // Note: GPS state is not automatically updated on WiFi arrival
        // GPS state remains as last reported by GPS events
    }
}

def markAsNotPresent(method) {
    if (device.currentValue("presence") != "not present") {
        def name = deviceName ?: device.displayName ?: "Device"
        logInfo "${name} departed via ${method}"
        sendEvent(name: "presence", value: "not present", descriptionText: "${deviceName} has left")
        sendEvent(name: "method", value: method)
        sendEvent(name: "confidence", value: calculateConfidence())
        state.presence = "not present"
    }
}

// Scheduled checks
def confirmArrival() {
    if (state.pendingArrival && isWifiRecent()) {
        markAsPresent("wifi-delayed")
    }
    state.pendingArrival = false
}

def checkDeparture() {
    def wifiRecent = isWifiRecent()
    def gpsOutside = !state.gpsInside
    
    logDebug "Departure check - WiFi recent: ${wifiRecent}, GPS outside: ${gpsOutside}"
    
    // WiFi has priority - if WiFi is connected, ignore GPS exit
    if (wifiRecent) {
        logDebug "WiFi still connected, ignoring GPS exit"
        return
    }
    
    // Only depart if WiFi timeout AND GPS outside
    if (!wifiRecent && gpsOutside) {
        markAsNotPresent("wifi-timeout")
    }
}

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
        // For departure: WiFi has priority
        if (wifiRecent) {
            // WiFi connected, remain present regardless of GPS
            logDebug "WiFi connected, maintaining present status"
        } else {
            // WiFi timeout - check GPS status
            if (!gpsInside) {
                markAsNotPresent("wifi-timeout")
            }
        }
    } else {
        // For arrival: WiFi is sufficient (no GPS entry required)
        if (wifiRecent) {
            markAsPresent("wifi-check")
        }
    }
    
    // Update confidence
    sendEvent(name: "confidence", value: calculateConfidence())
}

// Helper methods
def isWifiRecent() {
    def wifiAge = (now() - state.wifiLastSeen) / 1000
    return wifiAge < wifiTimeout
}

def calculateConfidence() {
    def confidence = 0
    def wifiRecent = isWifiRecent()
    def gpsInside = state.gpsInside
    
    if (wifiRecent) confidence += 50
    if (gpsInside) confidence += 30
    
    // Time-based confidence adjustment
    def wifiAge = (now() - state.wifiLastSeen) / 1000
    if (wifiAge < 60) confidence += 20
    else if (wifiAge < 180) confidence += 10
    
    return Math.min(confidence, 100)
}

// Manual commands
def arrived() {
    logInfo "Manual arrival command"
    markAsPresent("manual")
}

def departed() {
    logInfo "Manual departure command"
    markAsNotPresent("manual")
}

def refresh() {
    logDebug "Refresh requested"
    checkPresenceState()
}

def forceRefresh() {
    logInfo "Force refresh - clearing all states"
    state.clear()
    initialize()
}

// Logging
def logDebug(msg) {
    if (enableDebug) log.debug msg
}

def logInfo(msg) {
    if (enableInfo) log.info msg
}