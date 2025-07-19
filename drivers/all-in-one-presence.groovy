/**
 *  All-in-One Presence Driver
 *  
 *  Copyright (c) 2025 Luke Lee
 *  Licensed under the MIT License
 *
 */

metadata {
    definition (name: "All-in-One Presence Driver", namespace: "zekaizer", author: "Luke Lee", importUrl: "https://raw.githubusercontent.com/zekaizer/hubitat-presense/main/drivers/all-in-one-presence.groovy") {
        capability "PresenceSensor"
        capability "Refresh"
        
        attribute "presence", "enum", ["present", "not present"]
        attribute "lastActivity", "string"
        attribute "wifiPresence", "enum", ["connected", "disconnected"]
        attribute "gpsPresence", "enum", ["entered", "exited"]
        
        command "present"
        command "notPresent"
        command "arrive"
        command "depart"
        command "gpsEnter"
        command "gpsExit"
    }
    
    preferences {
        section("Settings") {
            input "debugLogging", "bool", title: "Enable Debug Logging", defaultValue: false, required: false
        }
        section("Device Settings") {
            input "macAddress", "string", title: "MAC Address (format: AA:BB:CC:DD:EE:FF or AA-BB-CC-DD-EE-FF)", required: true
            input "heartbeatTimeout", "number", title: "Heartbeat Timeout (seconds)", defaultValue: 60, range: "5..3600", required: true
        }
    }
}

def installed() {
    if (debugLogging) log.debug "All-in-One Presence Driver installed"
    initialize()
}

def updated() {
    if (debugLogging) log.debug "All-in-One Presence Driver updated"
    initialize()
}

def initialize() {
    if (debugLogging) log.debug "All-in-One Presence Driver initialized"
    
    // Restore saved state or set initial state
    restoreState()
    
    // Start heartbeat timeout monitoring if we have a saved heartbeat
    if (state.lastHeartbeatEpoch) {
        scheduleHeartbeatTimeoutCheck()
    }
    
    // If this is a component device, MQTT is handled by parent
    if (!parent) {
        log.warn "This device is designed to work as a component of Composite Presence Driver"
    }
}

def parse(String description) {
    if (debugLogging) log.debug "Parsing: ${description}"
    // Component devices don't directly parse MQTT messages
    // MQTT is handled by the parent Composite Presence Driver
}

def componentHandleHeartbeat(Long epochTime) {
    // This method is called by the parent device to deliver heartbeat data
    try {
        Long currentTime = now() / 1000 // Convert to seconds
        
        if (debugLogging) {
            log.debug "Component received WiFi heartbeat"
            log.debug "Epoch time: ${epochTime}, Current time: ${currentTime}"
        }
        
        // Update lastHeartbeat state (for timeout monitoring)
        Date heartbeatDate = new Date(epochTime * 1000)
        String heartbeatStr = heartbeatDate.toString()
        state.lastHeartbeat = heartbeatStr
        state.lastHeartbeatEpoch = epochTime
        
        // Check if heartbeat is recent (within last 30 seconds)
        Long timeDiff = currentTime - epochTime
        if (timeDiff <= 30) {
            // WiFi is connected
            updateWiFiPresence("connected")
            
            // Schedule heartbeat timeout check
            scheduleHeartbeatTimeoutCheck()
        } else {
            if (debugLogging) log.debug "Heartbeat is too old (${timeDiff} seconds), ignoring"
        }
        
    } catch (Exception e) {
        log.error "Failed to handle component heartbeat: ${e.message}"
    }
}


def present() {
    if (debugLogging) log.debug "Manual present command received"
    // Manual command overrides all - set WiFi connected and GPS entered
    updateWiFiPresence("connected")
    updateGPSPresence("entered")
}

def notPresent() {
    if (debugLogging) log.debug "Manual not present command received"
    // Manual command overrides all - set WiFi disconnected and GPS exited
    updateWiFiPresence("disconnected")
    updateGPSPresence("exited")
}

def arrive() {
    present()
}

def depart() {
    notPresent()
}

def refresh() {
    if (debugLogging) log.debug "Refreshing All-in-One Presence Driver"
    sendEvent(name: "lastActivity", value: new Date().toString())
    
    // Notify parent device if this is a component device
    if (parent) {
        try {
            parent.componentRefresh(this.device)
        } catch (Exception e) {
            if (debugLogging) log.debug "Failed to notify parent of refresh: ${e.message}"
        }
    }
}


def normalizeMacAddress(String macAddr) {
    // Convert MAC address from AA:BB:CC:DD:EE:FF to aa-bb-cc-dd-ee-ff format
    // Also handle case conversion to lowercase
    return macAddr?.toLowerCase()?.replace(":", "-")
}

def restoreState() {
    // Restore saved presence states or set defaults
    String savedPresence = state.lastPresence
    String savedActivity = state.lastActivity
    String savedHeartbeat = state.lastHeartbeat
    Long savedHeartbeatEpoch = state.lastHeartbeatEpoch
    String savedWiFiPresence = state.wifiPresence
    String savedGPSPresence = state.gpsPresence
    
    // Restore WiFi presence state
    if (savedWiFiPresence) {
        sendEvent(name: "wifiPresence", value: savedWiFiPresence)
        if (debugLogging) log.debug "Restored WiFi presence state: ${savedWiFiPresence}"
    } else {
        sendEvent(name: "wifiPresence", value: "disconnected")
        state.wifiPresence = "disconnected"
    }
    
    // Restore GPS presence state
    if (savedGPSPresence) {
        sendEvent(name: "gpsPresence", value: savedGPSPresence)
        if (debugLogging) log.debug "Restored GPS presence state: ${savedGPSPresence}"
    } else {
        sendEvent(name: "gpsPresence", value: "exited")
        state.gpsPresence = "exited"
    }
    
    // Restore final presence state
    if (savedPresence) {
        if (debugLogging) log.debug "Restoring saved final presence state: ${savedPresence}"
        sendEvent(name: "presence", value: savedPresence)
    } else {
        if (debugLogging) log.debug "No saved presence state, setting to 'not present'"
        sendEvent(name: "presence", value: "not present")
        state.lastPresence = "not present"
    }
    
    if (savedActivity) {
        sendEvent(name: "lastActivity", value: savedActivity)
    } else {
        String currentTime = new Date().toString()
        sendEvent(name: "lastActivity", value: currentTime)
        state.lastActivity = currentTime
    }
    
    // lastHeartbeat is kept in state for internal use only (no event)
    
    // Restore heartbeat epoch for timeout monitoring
    if (savedHeartbeatEpoch && debugLogging) {
        log.debug "Restored heartbeat epoch: ${savedHeartbeatEpoch}"
    }
}

def updatePresenceState(String presenceValue) {
    // Check if presence state is actually changing
    String currentPresence = device.currentValue("presence")
    boolean isStateChanging = (currentPresence != presenceValue)
    
    // Update presence state and save to state
    String currentTime = new Date().toString()
    
    sendEvent(name: "presence", value: presenceValue)
    sendEvent(name: "lastActivity", value: currentTime)
    
    // Save to state for recovery
    state.lastPresence = presenceValue
    state.lastActivity = currentTime
    
    // Log presence state changes at info level
    if (isStateChanging) {
        log.info "Presence changed from '${currentPresence}' to '${presenceValue}'"
    }
    
    if (debugLogging) log.debug "Presence updated to: ${presenceValue}, saved to state"
}

def scheduleHeartbeatTimeoutCheck() {
    // Get timeout setting (default 60 seconds, minimum 5 seconds)
    Integer timeoutSeconds = Math.max(5, settings.heartbeatTimeout ?: 60)
    
    // Schedule timeout check (overwrite any existing schedule)
    runIn(timeoutSeconds, "checkHeartbeatTimeout", [overwrite: true])
    
    if (debugLogging) log.debug "Scheduled heartbeat timeout check in ${timeoutSeconds} seconds"
}

def checkHeartbeatTimeout() {
    try {
        Long lastHeartbeatEpoch = state.lastHeartbeatEpoch
        if (!lastHeartbeatEpoch) {
            if (debugLogging) log.debug "No heartbeat epoch stored, skipping timeout check"
            return
        }
        
        Long currentTime = now() / 1000 // Convert to seconds
        Long timeSinceLastHeartbeat = currentTime - lastHeartbeatEpoch
        Integer timeoutSeconds = Math.max(5, settings.heartbeatTimeout ?: 60)
        
        if (debugLogging) {
            log.debug "Checking heartbeat timeout: ${timeSinceLastHeartbeat}s since last heartbeat (timeout: ${timeoutSeconds}s)"
        }
        
        if (timeSinceLastHeartbeat > timeoutSeconds) {
            // Heartbeat timeout - set WiFi presence to disconnected
            if (debugLogging) log.debug "WiFi heartbeat timeout detected"
            updateWiFiPresence("disconnected")
        } else {
            // Still within timeout period, schedule another check
            Integer remainingTime = timeoutSeconds - timeSinceLastHeartbeat
            if (remainingTime > 0) {
                runIn(remainingTime.toInteger(), "checkHeartbeatTimeout", [overwrite: true])
                if (debugLogging) log.debug "Rescheduled heartbeat timeout check in ${remainingTime} seconds"
            }
        }
        
    } catch (Exception e) {
        log.error "Failed to check heartbeat timeout: ${e.message}"
    }
}

def updateWiFiPresence(String wifiPresenceValue) {
    // Update WiFi presence state
    String currentWiFiPresence = device.currentValue("wifiPresence")
    
    sendEvent(name: "wifiPresence", value: wifiPresenceValue)
    state.wifiPresence = wifiPresenceValue
    
    if (currentWiFiPresence != wifiPresenceValue) {
        if (debugLogging) log.debug "WiFi presence changed from '${currentWiFiPresence}' to '${wifiPresenceValue}'"
        
        // Apply presence logic
        if (wifiPresenceValue == "connected") {
            // WiFi connected - immediate presence + assume GPS entered
            updateGPSPresence("entered")
            evaluateFinalPresence()
        } else {
            // WiFi disconnected - determine based on GPS enter/exit status
            evaluateFinalPresence()
        }
    }
}

def updateGPSPresence(String gpsPresenceValue) {
    // Update GPS presence state
    String currentGPSPresence = device.currentValue("gpsPresence")
    
    sendEvent(name: "gpsPresence", value: gpsPresenceValue)
    state.gpsPresence = gpsPresenceValue
    
    if (currentGPSPresence != gpsPresenceValue) {
        if (debugLogging) log.debug "GPS presence changed from '${currentGPSPresence}' to '${gpsPresenceValue}'"
        
        // Re-evaluate final presence when GPS changes (only affects when WiFi is disconnected)
        evaluateFinalPresence()
    }
}

def evaluateFinalPresence() {
    String wifiPresence = state.wifiPresence ?: "disconnected"
    String gpsPresence = state.gpsPresence ?: "exited"
    String currentFinalPresence = device.currentValue("presence") ?: "not present"
    
    String finalPresence
    if (wifiPresence == "connected") {
        // WiFi connected - always present (only way to change from "not present" to "present")
        finalPresence = "present"
    } else {
        // WiFi disconnected
        if (gpsPresence == "exited") {
            // GPS exited - set to not present
            finalPresence = "not present"
        } else {
            // GPS entered but WiFi disconnected - maintain current state
            // Do not change "not present" to "present" based on GPS alone
            finalPresence = currentFinalPresence
        }
    }
    
    updateFinalPresenceState(finalPresence)
}

def updateFinalPresenceState(String presenceValue) {
    // Check if presence state is actually changing
    String currentPresence = device.currentValue("presence")
    boolean isStateChanging = (currentPresence != presenceValue)
    
    // Update presence state and save to state
    String currentTime = new Date().toString()
    
    sendEvent(name: "presence", value: presenceValue)
    sendEvent(name: "lastActivity", value: currentTime)
    
    // Save to state for recovery
    state.lastPresence = presenceValue
    state.lastActivity = currentTime
    
    // Notify parent device if this is a component device
    if (parent && isStateChanging) {
        try {
            parent.componentPresenceHandler(this.device, presenceValue)
        } catch (Exception e) {
            if (debugLogging) log.debug "Failed to notify parent of presence change: ${e.message}"
        }
    }
    
    // Log presence state changes at info level
    if (isStateChanging) {
        log.info "Presence changed from '${currentPresence}' to '${presenceValue}'"
    }
    
    if (debugLogging) log.debug "Final presence updated to: ${presenceValue}, saved to state"
}

def gpsEnter() {
    if (debugLogging) log.debug "GPS enter event received"
    updateGPSPresence("entered")
}

def gpsExit() {
    if (debugLogging) log.debug "GPS exit event received"
    updateGPSPresence("exited")
}