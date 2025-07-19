/**
 *  Composite Presence Driver
 *  
 *  Copyright (c) 2025 Luke Lee
 *  Licensed under the MIT License
 *
 *  Parent driver that aggregates multiple All-in-One Presence devices
 *  to create "anyone home" functionality
 */

metadata {
    definition (name: "Composite Presence Driver", namespace: "zekaizer", author: "Luke Lee", importUrl: "https://raw.githubusercontent.com/zekaizer/hubitat-presense/main/drivers/composite-presence-driver.groovy") {
        capability "PresenceSensor"
        capability "Refresh"
        
        attribute "presence", "enum", ["present", "not present"]
        attribute "lastActivity", "string"
        attribute "childCount", "number"
        attribute "presentCount", "number"
        
        command "present"
        command "notPresent"
        command "arrive"
        command "depart"
        command "addChildDevice"
        command "removeChildDevice", [[name:"deviceId", type:"STRING", description:"Device ID to remove"]]
        command "removeAllChildren"
    }
    
    preferences {
        section("Settings") {
            input "debugLogging", "bool", title: "Enable Debug Logging", defaultValue: false, required: false
            input "aggregationMode", "enum", title: "Aggregation Mode", 
                  options: ["anyone": "Anyone Present (OR logic)", "everyone": "Everyone Present (AND logic)"], 
                  defaultValue: "anyone", required: true
        }
        section("Child Device Management") {
            input "autoAddChildren", "bool", title: "Auto-discover All-in-One Presence devices", defaultValue: false, required: false
        }
    }
}

def installed() {
    if (debugLogging) log.debug "Composite Presence Driver installed"
    initialize()
}

def updated() {
    if (debugLogging) log.debug "Composite Presence Driver updated"
    initialize()
}

def initialize() {
    if (debugLogging) log.debug "Composite Presence Driver initialized"
    
    // Restore saved state or set initial state
    restoreState()
    
    // Auto-discover children if enabled
    if (settings.autoAddChildren) {
        autoDiscoverChildren()
    }
    
    // Update child count and evaluate presence
    updateChildStatistics()
    evaluateCompositePresence()
}

def parse(String description) {
    // This parent driver doesn't parse physical device messages
    if (debugLogging) log.debug "Parse called with: ${description}"
}

def present() {
    if (debugLogging) log.debug "Manual present command received"
    updatePresenceState("present")
}

def notPresent() {
    if (debugLogging) log.debug "Manual not present command received"
    updatePresenceState("not present")
}

def arrive() {
    present()
}

def depart() {
    notPresent()
}

def refresh() {
    if (debugLogging) log.debug "Refreshing Composite Presence Driver"
    updateChildStatistics()
    evaluateCompositePresence()
    sendEvent(name: "lastActivity", value: new Date().toString())
}

def addChildDevice() {
    if (debugLogging) log.debug "Adding child device"
    
    try {
        // Create a new All-in-One Presence child device
        def childDeviceNetworkId = "composite-presence-child-${now()}"
        def childDevice = addChildDevice(
            "zekaizer", 
            "All-in-One Presence Driver", 
            childDeviceNetworkId,
            [
                name: "All-in-One Presence Child",
                label: "Presence Child ${getChildDevices().size() + 1}",
                isComponent: true
            ]
        )
        
        if (childDevice) {
            if (debugLogging) log.debug "Successfully created child device: ${childDevice.getDisplayName()}"
            updateChildStatistics()
            return childDevice
        } else {
            log.error "Failed to create child device"
        }
    } catch (Exception e) {
        log.error "Exception while adding child device: ${e.message}"
    }
}

def removeChildDevice(deviceId) {
    if (debugLogging) log.debug "Removing child device: ${deviceId}"
    
    try {
        def childDevice = getChildDevice(deviceId)
        if (childDevice) {
            deleteChildDevice(deviceId)
            if (debugLogging) log.debug "Successfully removed child device: ${deviceId}"
            updateChildStatistics()
            evaluateCompositePresence()
        } else {
            log.warn "Child device not found: ${deviceId}"
        }
    } catch (Exception e) {
        log.error "Exception while removing child device: ${e.message}"
    }
}

def removeAllChildren() {
    if (debugLogging) log.debug "Removing all child devices"
    
    try {
        def children = getChildDevices()
        children.each { child ->
            deleteChildDevice(child.getDeviceNetworkId())
        }
        if (debugLogging) log.debug "Successfully removed ${children.size()} child devices"
        updateChildStatistics()
        evaluateCompositePresence()
    } catch (Exception e) {
        log.error "Exception while removing all child devices: ${e.message}"
    }
}

def autoDiscoverChildren() {
    if (debugLogging) log.debug "Auto-discovering All-in-One Presence devices"
    
    // This would need to be implemented based on Hubitat's device discovery capabilities
    // For now, this is a placeholder for future enhancement
    log.info "Auto-discovery not yet implemented - please add children manually"
}

def componentPresenceHandler(childDevice, presenceValue) {
    // This method is called by child devices when their presence changes
    if (debugLogging) log.debug "Child device ${childDevice.getDisplayName()} presence changed to: ${presenceValue}"
    
    // Re-evaluate composite presence when any child changes
    evaluateCompositePresence()
}

def componentRefresh(childDevice) {
    // Handle refresh requests from child devices
    if (debugLogging) log.debug "Child device ${childDevice.getDisplayName()} requested refresh"
    childDevice.refresh()
}

def updateChildStatistics() {
    def children = getChildDevices()
    def childCount = children.size()
    def presentCount = 0
    
    children.each { child ->
        def presenceValue = child.currentValue("presence")
        if (presenceValue == "present") {
            presentCount++
        }
    }
    
    sendEvent(name: "childCount", value: childCount)
    sendEvent(name: "presentCount", value: presentCount)
    
    if (debugLogging) log.debug "Child statistics updated: ${presentCount}/${childCount} present"
}

def evaluateCompositePresence() {
    def children = getChildDevices()
    def aggregationMode = settings.aggregationMode ?: "anyone"
    def compositePresence = "not present"
    
    if (children.size() == 0) {
        // No children - default to not present
        compositePresence = "not present"
        if (debugLogging) log.debug "No child devices - setting composite presence to 'not present'"
    } else {
        def presentCount = 0
        children.each { child ->
            def presenceValue = child.currentValue("presence")
            if (presenceValue == "present") {
                presentCount++
            }
        }
        
        if (aggregationMode == "anyone") {
            // OR logic - any child present means composite is present
            compositePresence = (presentCount > 0) ? "present" : "not present"
        } else if (aggregationMode == "everyone") {
            // AND logic - all children must be present
            compositePresence = (presentCount == children.size()) ? "present" : "not present"
        }
        
        if (debugLogging) {
            log.debug "Composite presence evaluation (${aggregationMode}): ${presentCount}/${children.size()} present -> ${compositePresence}"
        }
    }
    
    updatePresenceState(compositePresence)
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
        log.info "Composite presence changed from '${currentPresence}' to '${presenceValue}'"
    }
    
    if (debugLogging) log.debug "Composite presence updated to: ${presenceValue}, saved to state"
}

def restoreState() {
    // Restore saved presence states or set defaults
    String savedPresence = state.lastPresence
    String savedActivity = state.lastActivity
    
    if (savedPresence) {
        if (debugLogging) log.debug "Restoring saved composite presence state: ${savedPresence}"
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
    
    // Initialize child statistics
    sendEvent(name: "childCount", value: 0)
    sendEvent(name: "presentCount", value: 0)
}