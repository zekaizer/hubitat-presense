/**
 *  All-in-One Presence Driver
 *
 *  Copyright (c) 2025 Luke Lee
 *  Licensed under the MIT License
 *
 */

// Constants
@groovy.transform.Field static final Integer HEARTBEAT_FRESHNESS_SECONDS = 30
@groovy.transform.Field static final Integer DEFAULT_HEARTBEAT_TIMEOUT_SECONDS = 60

metadata {
    definition (name: "All-in-One Presence Driver", namespace: "zekaizer", author: "Luke Lee", importUrl: "https://raw.githubusercontent.com/zekaizer/hubitat-presense/main/drivers/all-in-one-presence.groovy") {
        capability "PresenceSensor"
        capability "Refresh"
        capability "Initialize"
        
        attribute "presence", "enum", ["present", "not present"]
        attribute "lastActivity", "string"
        attribute "wifiPresence", "enum", ["connected", "disconnected"]
        attribute "gpsPresence", "enum", ["entered", "exited"]
        
        command "present"
        command "notPresent"
        command "gpsEnter"
        command "gpsExit"
    }
    
    preferences {
        section("Component Device") {
            input "debugLogging", "bool", title: "Enable Debug Logging", defaultValue: false, required: false
        }
    }
}

def installed() {
    log.info "All-in-One Presence Driver installed"
    state.lastInitializeReason = "installed"
    initialize()
}

def updated() {
    log.info "All-in-One Presence Driver updated"
    state.lastInitializeReason = "updated"
    initialize()
}

def initialize() {
    log.info "All-in-One Presence Driver initializing (reason: ${state.lastInitializeReason ?: 'manual'})"
    state.lastInitialized = new Date().toString()

    // Restore saved state or set initial state
    restoreState()

    // Backup schedule to recover runIn if lost (every 5 minutes)
    schedule("0 */5 * * * ?", "ensureHeartbeatMonitoring")

    // Reset heartbeat epoch to 0 (sentinel: waiting for first heartbeat after reboot)
    // Prevents false timeout from stale epoch during hub downtime
    if (state.lastHeartbeatEpoch != null) {
        state.lastHeartbeatEpoch = 0
    }

    // If this is a component device, MQTT is handled by parent
    if (!parent) {
        log.warn "This device is designed to work as a component of Composite Presence Driver"
    }

    // Log initialization summary
    log.info "Initialized: wifiPresence=${state.wifiPresence ?: 'unknown'}, gpsPresence=${state.gpsPresence ?: 'unknown'}, presence=${state.lastPresence ?: 'unknown'}, lastHeartbeat=${state.lastHeartbeat ?: 'none'}"
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
        
        // Check if heartbeat is recent (within freshness threshold)
        Long timeDiff = currentTime - epochTime
        if (timeDiff <= HEARTBEAT_FRESHNESS_SECONDS) {
            // Only update stored epoch if newer (prevents stale heartbeat from overwriting fresh one)
            if (!state.lastHeartbeatEpoch || epochTime > state.lastHeartbeatEpoch) {
                Date heartbeatDate = new Date(epochTime * 1000)
                state.lastHeartbeat = heartbeatDate.toString()
                state.lastHeartbeatEpoch = epochTime
            }

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


def scheduleHeartbeatTimeoutCheck() {
    Integer timeoutSeconds = getHeartbeatTimeout()

    // Schedule timeout check (overwrite any existing schedule)
    runIn(timeoutSeconds, "checkHeartbeatTimeout", [overwrite: true])

    if (debugLogging) log.debug "Scheduled heartbeat timeout check in ${timeoutSeconds} seconds"
}

def checkHeartbeatTimeout() {
    try {
        Long lastHeartbeatEpoch = state.lastHeartbeatEpoch
        if (lastHeartbeatEpoch == null) {
            if (debugLogging) log.debug "No heartbeat epoch stored, skipping timeout check"
            return
        }
        if (lastHeartbeatEpoch == 0) {
            // Epoch reset after reboot - waiting for first real heartbeat
            if (debugLogging) log.debug "Heartbeat epoch reset (reboot recovery), skipping timeout check"
            return
        }

        Long currentTime = now() / 1000 // Convert to seconds
        Long timeSinceLastHeartbeat = currentTime - lastHeartbeatEpoch
        Integer timeoutSeconds = getHeartbeatTimeout()

        if (debugLogging) {
            log.debug "Checking heartbeat timeout: ${timeSinceLastHeartbeat}s since last heartbeat (timeout: ${timeoutSeconds}s)"
        }

        if (timeSinceLastHeartbeat >= timeoutSeconds) {
            // Heartbeat timeout - set WiFi presence to disconnected
            if (debugLogging) log.debug "WiFi heartbeat timeout detected"
            updateWiFiPresence("disconnected")
        } else {
            // Still within timeout period, schedule another check
            Long remainingTime = timeoutSeconds - timeSinceLastHeartbeat
            if (remainingTime > 0) {
                runIn(remainingTime.intValue(), "checkHeartbeatTimeout", [overwrite: true])
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
            // Set GPS to entered silently without triggering evaluateFinalPresence
            sendEvent(name: "gpsPresence", value: "entered")
            state.gpsPresence = "entered"
            if (debugLogging) log.debug "GPS presence set to 'entered' (assumed from WiFi connection)"
            
            // Evaluate final presence only once
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
        
        // Check if parent's Security System is in away mode or targeting away (hint from user)
        def parentSecurityMode = parent?.currentValue("securitySystemStatus")
        // Get target mode from parent
        def parentTargetMode = parent?.currentValue("securitySystemTargetMode")

        if (debugLogging) log.debug "Parent Security System - current: ${parentSecurityMode}, target: ${parentTargetMode}"
        
        if (parentSecurityMode == "away" || parentTargetMode == "away") {
            // User explicitly set away mode or targeting away - WiFi disconnect alone is enough
            finalPresence = "not present"
            if (debugLogging) log.debug "Security System is/targeting away - WiFi disconnect triggers not present"
        } else if (gpsPresence == "exited") {
            // Normal behavior: GPS exited - set to not present
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

private Integer getHeartbeatTimeout() {
    Integer timeout = DEFAULT_HEARTBEAT_TIMEOUT_SECONDS
    if (parent) {
        def parentTimeout = parent.getSetting("defaultHeartbeatTimeout")
        timeout = Math.max(5, parentTimeout ?: DEFAULT_HEARTBEAT_TIMEOUT_SECONDS)
    }
    return timeout
}

def ensureHeartbeatMonitoring() {
    // Recovery mechanism: ensure runIn schedule exists
    // This is called periodically by schedule() to recover from runIn loss
    try {
        if (state.lastHeartbeatEpoch == null) {
            if (debugLogging) log.debug "ensureHeartbeatMonitoring: no heartbeat epoch, skipping"
            return
        }
        if (state.lastHeartbeatEpoch == 0) {
            // Epoch reset after reboot but no heartbeat received in 5+ minutes
            if (device.currentValue("wifiPresence") == "connected") {
                log.warn "ensureHeartbeatMonitoring: no heartbeat since reboot, setting disconnected"
                updateWiFiPresence("disconnected")
            }
            return
        }

        Long currentTime = now() / 1000
        Long timeSinceLastHeartbeat = currentTime - state.lastHeartbeatEpoch
        Integer timeoutSeconds = getHeartbeatTimeout()

        if (timeSinceLastHeartbeat >= timeoutSeconds) {
            // Already timed out - check if we missed it
            if (device.currentValue("wifiPresence") == "connected") {
                log.warn "ensureHeartbeatMonitoring: detected missed timeout (${timeSinceLastHeartbeat}s since last heartbeat)"
                updateWiFiPresence("disconnected")
            }
        } else {
            // Not yet timed out - ensure runIn is scheduled
            scheduleHeartbeatTimeoutCheck()
            if (debugLogging) log.debug "ensureHeartbeatMonitoring: refreshed runIn schedule"
        }
    } catch (Exception e) {
        log.error "ensureHeartbeatMonitoring failed: ${e.message}"
    }
}