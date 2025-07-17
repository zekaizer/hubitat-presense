/**
 *  Anyone Presence with Manual Override
 *  
 *  Virtual presence device that combines all individual presence sensors
 *  with manual override capability
 */
metadata {
    definition (name: "Anyone Presence Override", namespace: "zekaizer", author: "Luke Lee") {
        capability "Presence Sensor"
        capability "Switch"  // For manual override
        capability "Sensor"
        
        attribute "overrideStatus", "enum", ["auto", "manual-present", "manual-away"]
        attribute "autoPresence", "enum", ["present", "not present"]
        attribute "individualCount", "number"
        attribute "presentDevices", "string"
        attribute "lastOverrideTime", "string"
        attribute "overrideReason", "string"
        
        command "arrived"
        command "departed"  
        command "setManualPresent", ["string"]  // reason parameter
        command "setManualAway", ["string"]     // reason parameter
        command "clearOverride"
        command "setAutoMode"
        command "updateFromIndividuals", ["boolean"]  // anyonePresent parameter
        command "updateIndividualTracking", ["object"]  // deviceMap parameter
    }
    
    preferences {
        input "overrideTimeout", "number", title: "Manual Override Timeout (hours, 0=no timeout)", 
            defaultValue: 24, required: true, range: "0..168"
        input "notifyOnOverride", "bool", title: "Notify when override is set/cleared", 
            defaultValue: true
        input "enableDebug", "bool", title: "Enable Debug Logging", defaultValue: false
    }
}

def installed() {
    initialize()
}

def updated() {
    unschedule()
    initialize()
}

def initialize() {
    state.overrideMode = "auto"
    state.overrideSetTime = 0
    state.overrideReason = ""
    state.individualDevices = [:]
    
    sendEvent(name: "presence", value: "not present")
    sendEvent(name: "overrideStatus", value: "auto")
    sendEvent(name: "autoPresence", value: "not present")
    sendEvent(name: "individualCount", value: 0)
    sendEvent(name: "switch", value: "off")
}

// Switch capability for dashboard control
def on() {
    setManualPresent("Dashboard switch")
}

def off() {
    setManualAway("Dashboard switch")
}

// Manual Override Commands
def setManualPresent(reason = "Manual") {
    logInfo "Setting manual PRESENT override: ${reason}"
    
    state.overrideMode = "manual-present"
    state.overrideSetTime = now()
    state.overrideReason = reason
    
    sendEvent(name: "presence", value: "present")
    sendEvent(name: "switch", value: "on")
    sendEvent(name: "overrideStatus", value: "manual-present")
    sendEvent(name: "lastOverrideTime", value: new Date().format("yyyy-MM-dd HH:mm"))
    sendEvent(name: "overrideReason", value: reason)
    
    // Schedule timeout if configured
    if (overrideTimeout > 0) {
        runIn(overrideTimeout * 3600, overrideTimeoutHandler)
        logDebug "Override will expire in ${overrideTimeout} hours"
    }
    
    if (notifyOnOverride) {
        sendNotificationEvent("Presence manually set to PRESENT: ${reason}")
    }
}

def setManualAway(reason = "Manual") {
    logInfo "Setting manual AWAY override: ${reason}"
    
    state.overrideMode = "manual-away"
    state.overrideSetTime = now()
    state.overrideReason = reason
    
    sendEvent(name: "presence", value: "not present")
    sendEvent(name: "switch", value: "off")
    sendEvent(name: "overrideStatus", value: "manual-away")
    sendEvent(name: "lastOverrideTime", value: new Date().format("yyyy-MM-dd HH:mm"))
    sendEvent(name: "overrideReason", value: reason)
    
    // Schedule timeout if configured
    if (overrideTimeout > 0) {
        runIn(overrideTimeout * 3600, overrideTimeoutHandler)
        logDebug "Override will expire in ${overrideTimeout} hours"
    }
    
    if (notifyOnOverride) {
        sendNotificationEvent("Presence manually set to AWAY: ${reason}")
    }
}

def clearOverride() {
    logInfo "Clearing manual override"
    
    state.overrideMode = "auto"
    state.overrideReason = ""
    unschedule(overrideTimeoutHandler)
    
    sendEvent(name: "overrideStatus", value: "auto")
    sendEvent(name: "overrideReason", value: "")
    
    // Restore automatic presence
    def autoState = device.currentValue("autoPresence")
    sendEvent(name: "presence", value: autoState)
    sendEvent(name: "switch", value: autoState == "present" ? "on" : "off")
    
    if (notifyOnOverride) {
        sendNotificationEvent("Presence override cleared - returning to automatic")
    }
}

def setAutoMode() {
    clearOverride()
}

// Automatic presence update from individuals
def updateFromIndividuals(anyonePresent) {
    logDebug "Updating from individuals: ${anyonePresent}"
    
    // Always update the automatic state
    def newAutoState = anyonePresent ? "present" : "not present"
    sendEvent(name: "autoPresence", value: newAutoState)
    
    // Only update actual presence if in auto mode
    if (state.overrideMode == "auto") {
        sendEvent(name: "presence", value: newAutoState)
        sendEvent(name: "switch", value: anyonePresent ? "on" : "off")
    } else {
        logDebug "In override mode (${state.overrideMode}), not updating presence"
    }
}

// Standard presence commands (can be overridden)
def arrived() {
    if (state.overrideMode == "auto") {
        sendEvent(name: "presence", value: "present")
        sendEvent(name: "switch", value: "on")
    } else {
        logDebug "Arrived ignored due to override mode: ${state.overrideMode}"
    }
}

def departed() {
    if (state.overrideMode == "auto") {
        sendEvent(name: "presence", value: "not present")
        sendEvent(name: "switch", value: "off")
    } else {
        logDebug "Departed ignored due to override mode: ${state.overrideMode}"
    }
}

// Override timeout handler
def overrideTimeoutHandler() {
    logInfo "Override timeout reached"
    clearOverride()
}

// Update individual device tracking
def updateIndividualTracking(deviceMap) {
    state.individualDevices = deviceMap
    
    def presentList = deviceMap.findAll { it.value == true }.collect { it.key }
    def presentCount = presentList.size()
    
    sendEvent(name: "individualCount", value: presentCount)
    sendEvent(name: "presentDevices", value: presentList.join(", ") ?: "None")
}

def logDebug(msg) {
    if (enableDebug) log.debug "${device.displayName}: ${msg}"
}

def logInfo(msg) {
    log.info "${device.displayName}: ${msg}"
}