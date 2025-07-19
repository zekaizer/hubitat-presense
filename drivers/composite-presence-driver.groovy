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
        command "addChildDevice", [[name:"macAddress", type:"STRING", description:"MAC Address (format: AA:BB:CC:DD:EE:FF or AA-BB-CC-DD-EE-FF)"], [name:"deviceLabel", type:"STRING", description:"Device Label (optional)"]]
        command "removeChildDevice", [[name:"deviceId", type:"STRING", description:"Device ID (DNI) or MAC Address of device to remove"]]
        command "removeAllChildren"
        command "updateChildMacAddress", [[name:"deviceId", type:"STRING", description:"Device ID (DNI) of child to update"], [name:"newMacAddress", type:"STRING", description:"New MAC Address"]]
        command "updateChildLabel", [[name:"deviceId", type:"STRING", description:"Device ID (DNI) of child to update"], [name:"newLabel", type:"STRING", description:"New Device Label"]]
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
        section("Default MQTT Settings for Child Devices") {
            input "defaultMqttBroker", "string", title: "Default MQTT Broker IP Address", required: false
            input "defaultMqttPort", "string", title: "Default MQTT Broker Port", defaultValue: "1883", required: false
            input "defaultMqttUsername", "string", title: "Default MQTT Username (optional)", required: false
            input "defaultMqttPassword", "password", title: "Default MQTT Password (optional)", required: false
            input "defaultHeartbeatTimeout", "number", title: "Default Heartbeat Timeout (seconds)", defaultValue: 60, range: "5..3600", required: false
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
    
    // Initialize MQTT connection
    connectMQTT()
    
    // Auto-discover children if enabled
    if (settings.autoAddChildren) {
        autoDiscoverChildren()
    }
    
    // Update child count and evaluate presence
    updateChildStatistics()
    evaluateCompositePresence()
}

def parse(String description) {
    if (debugLogging) log.debug "Parsing: ${description}"
    
    // Parse MQTT messages
    try {
        def parsedMsg = interfaces.mqtt.parseMessage(description)
        def topic = parsedMsg.topic
        def payload = parsedMsg.payload
        
        if (debugLogging) log.debug "MQTT Message - Topic: ${topic}, Payload: ${payload}"
        
        // Check if this is a WiFi presence heartbeat message
        if (topic.contains("/lastseen/epoch")) {
            handleWiFiPresenceHeartbeat(topic, payload)
        }
        
    } catch (Exception e) {
        if (debugLogging) log.debug "Failed to parse MQTT message: ${e.message}"
    }
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

def addChildDevice(macAddress = null, deviceLabel = null) {
    if (debugLogging) log.debug "Adding child device with MAC address: ${macAddress}, label: ${deviceLabel}"
    
    if (!macAddress) {
        log.error "MAC address is required to add child device"
        return null
    }
    
    try {
        // Check if child with this MAC already exists
        def existingChild = findChildByMacAddress(macAddress)
        if (existingChild) {
            log.warn "Child device with MAC address ${macAddress} already exists: ${existingChild.getDisplayName()}"
            return existingChild
        }
        
        // Generate UUID-based DNI
        def childDeviceNetworkId = "composite-presence-${UUID.randomUUID().toString()}"
        
        // Create a new All-in-One Presence child device
        String normalizedMac = normalizeMacAddress(macAddress)
        String finalLabel = deviceLabel ?: "Presence ${normalizedMac}"
        
        def childDevice = addChildDevice(
            "zekaizer", 
            "All-in-One Presence Driver", 
            childDeviceNetworkId,
            [
                name: "All-in-One Presence Child",
                label: finalLabel,
                isComponent: true
            ]
        )
        
        if (childDevice) {
            if (debugLogging) log.debug "Successfully created child device: ${childDevice.getDisplayName()} with DNI: ${childDeviceNetworkId}"
            
            // Set the MAC address and default MQTT settings in the child device
            childDevice.updateSetting("macAddress", macAddress)
            
            // Apply default MQTT settings if configured
            if (settings.defaultMqttBroker) {
                childDevice.updateSetting("mqttBroker", settings.defaultMqttBroker)
            }
            if (settings.defaultMqttPort) {
                childDevice.updateSetting("mqttPort", settings.defaultMqttPort)
            }
            if (settings.defaultMqttUsername) {
                childDevice.updateSetting("mqttUsername", settings.defaultMqttUsername)
            }
            if (settings.defaultMqttPassword) {
                childDevice.updateSetting("mqttPassword", settings.defaultMqttPassword)
            }
            if (settings.defaultHeartbeatTimeout) {
                childDevice.updateSetting("heartbeatTimeout", settings.defaultHeartbeatTimeout)
            }
            
            // Initialize the child device
            childDevice.initialize()
            
            // Subscribe to MQTT topics for this MAC address
            subscribeToMacAddress(macAddress)
            
            updateChildStatistics()
            return childDevice
        } else {
            log.error "Failed to create child device"
        }
    } catch (Exception e) {
        log.error "Exception while adding child device: ${e.message}"
    }
    
    return null
}

def removeChildDevice(deviceId) {
    if (debugLogging) log.debug "Removing child device with ID: ${deviceId}"
    
    if (!deviceId) {
        log.error "Device ID is required to remove child device"
        return
    }
    
    try {
        def childDevice = null
        
        // Try to find by DNI first
        childDevice = getChildDevice(deviceId)
        
        // If not found by DNI, try to find by MAC address
        if (!childDevice) {
            childDevice = findChildByMacAddress(deviceId)
        }
        
        if (childDevice) {
            // Get MAC address before deletion for MQTT unsubscribe
            def macAddress = childDevice.getSetting("macAddress")
            if (macAddress) {
                unsubscribeFromMacAddress(macAddress)
            }
            
            deleteChildDevice(childDevice.getDeviceNetworkId())
            if (debugLogging) log.debug "Successfully removed child device: ${childDevice.getDisplayName()}"
            updateChildStatistics()
            evaluateCompositePresence()
        } else {
            log.warn "Child device not found for ID: ${deviceId}"
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

def normalizeMacAddress(String macAddr) {
    // Convert MAC address from AA:BB:CC:DD:EE:FF to aa-bb-cc-dd-ee-ff format
    // Also handle case conversion to lowercase
    return macAddr?.toLowerCase()?.replace(":", "-")
}

def connectMQTT() {
    try {
        if (!settings.defaultMqttBroker) {
            if (debugLogging) log.debug "MQTT Broker not configured"
            return
        }
        
        if (debugLogging) log.debug "Connecting to MQTT broker: ${settings.defaultMqttBroker}:${settings.defaultMqttPort}"
        
        // Disconnect if already connected
        try {
            interfaces.mqtt.disconnect()
        } catch (Exception e) {
            // Ignore disconnect errors
        }
        
        // Connect to MQTT broker
        String brokerUrl = "tcp://${settings.defaultMqttBroker}:${settings.defaultMqttPort ?: '1883'}"
        String clientId = "hubitat-composite-presence-${device.id}"
        String username = settings.defaultMqttUsername ?: null
        String password = settings.defaultMqttPassword ?: null
        
        interfaces.mqtt.connect(brokerUrl, clientId, username, password)
        
        // Subscribe to topics after connection
        runIn(2, "subscribeToChildTopics")
        
    } catch (Exception e) {
        log.error "Failed to connect to MQTT: ${e.message}"
    }
}

def subscribeToChildTopics() {
    try {
        def children = getChildDevices()
        children.each { child ->
            def macAddress = child.getSetting("macAddress")
            if (macAddress) {
                subscribeToMacAddress(macAddress)
            }
        }
        
        if (debugLogging) {
            log.debug "Subscribed to MQTT topics for ${children.size()} child devices"
        }
        
    } catch (Exception e) {
        log.error "Failed to subscribe to child topics: ${e.message}"
    }
}

def subscribeToMacAddress(String macAddress) {
    try {
        String normalizedMac = normalizeMacAddress(macAddress)
        
        // Subscribe to WiFi presence topics for this MAC address
        String unifiTopic = "UnifiU6Pro/status/mac-${normalizedMac}/lastseen/epoch"
        String asusTopic = "AsusAC68U/status/mac-${normalizedMac}/lastseen/epoch"
        
        interfaces.mqtt.subscribe(unifiTopic)
        interfaces.mqtt.subscribe(asusTopic)
        
        if (debugLogging) {
            log.debug "Subscribed to MQTT topics for MAC ${macAddress}:"
            log.debug "  - ${unifiTopic}"
            log.debug "  - ${asusTopic}"
        }
        
    } catch (Exception e) {
        log.error "Failed to subscribe to MAC address ${macAddress}: ${e.message}"
    }
}

def unsubscribeFromMacAddress(String macAddress) {
    try {
        String normalizedMac = normalizeMacAddress(macAddress)
        
        // Unsubscribe from WiFi presence topics for this MAC address
        String unifiTopic = "UnifiU6Pro/status/mac-${normalizedMac}/lastseen/epoch"
        String asusTopic = "AsusAC68U/status/mac-${normalizedMac}/lastseen/epoch"
        
        interfaces.mqtt.unsubscribe(unifiTopic)
        interfaces.mqtt.unsubscribe(asusTopic)
        
        if (debugLogging) {
            log.debug "Unsubscribed from MQTT topics for MAC ${macAddress}:"
            log.debug "  - ${unifiTopic}"
            log.debug "  - ${asusTopic}"
        }
        
    } catch (Exception e) {
        log.error "Failed to unsubscribe from MAC address ${macAddress}: ${e.message}"
    }
}

def mqttClientStatus(String status) {
    if (debugLogging) log.debug "MQTT client status: ${status}"
    if (status == "connected") {
        subscribeToChildTopics()
    }
}

def findChildByMacAddress(String macAddress) {
    // Find child device by MAC address setting
    def children = getChildDevices()
    return children.find { child ->
        def childMac = child.getSetting("macAddress")
        return childMac && normalizeMacAddress(childMac) == normalizeMacAddress(macAddress)
    }
}

def updateChildMacAddress(String deviceId, String newMacAddress) {
    if (debugLogging) log.debug "Updating MAC address for device ${deviceId} to ${newMacAddress}"
    
    if (!deviceId || !newMacAddress) {
        log.error "Device ID and new MAC address are required"
        return false
    }
    
    try {
        // Find the child device
        def childDevice = getChildDevice(deviceId)
        if (!childDevice) {
            log.error "Child device not found: ${deviceId}"
            return false
        }
        
        // Check if new MAC address is already in use
        def existingChild = findChildByMacAddress(newMacAddress)
        if (existingChild && existingChild.getDeviceNetworkId() != deviceId) {
            log.error "MAC address ${newMacAddress} is already in use by another child device"
            return false
        }
        
        // Get old MAC address for MQTT unsubscribe
        def oldMacAddress = childDevice.getSetting("macAddress")
        if (oldMacAddress) {
            unsubscribeFromMacAddress(oldMacAddress)
        }
        
        // Update MAC address setting
        childDevice.updateSetting("macAddress", newMacAddress)
        
        // Subscribe to new MAC address topics
        subscribeToMacAddress(newMacAddress)
        
        if (debugLogging) log.debug "Successfully updated MAC address for ${childDevice.getDisplayName()}"
        return true
        
    } catch (Exception e) {
        log.error "Exception while updating child MAC address: ${e.message}"
        return false
    }
}

def updateChildLabel(String deviceId, String newLabel) {
    if (debugLogging) log.debug "Updating label for device ${deviceId} to ${newLabel}"
    
    if (!deviceId || !newLabel) {
        log.error "Device ID and new label are required"
        return false
    }
    
    try {
        // Find the child device
        def childDevice = getChildDevice(deviceId)
        if (!childDevice) {
            log.error "Child device not found: ${deviceId}"
            return false
        }
        
        // Update device label
        childDevice.setLabel(newLabel)
        
        if (debugLogging) log.debug "Successfully updated label for ${childDevice.getDisplayName()}"
        return true
        
    } catch (Exception e) {
        log.error "Exception while updating child label: ${e.message}"
        return false
    }
}

def handleWiFiPresenceHeartbeat(String topic, String payload) {
    try {
        // Extract MAC address from topic
        // Topic format: UnifiU6Pro/status/mac-aa-bb-cc-dd-ee-ff/lastseen/epoch
        def macMatch = topic =~ /mac-([a-f0-9-]+)/
        if (!macMatch) {
            if (debugLogging) log.debug "Could not extract MAC address from topic: ${topic}"
            return
        }
        
        String macFromTopic = macMatch[0][1]
        
        // Find the corresponding child device by MAC address
        String macAddress = macFromTopic.replace("-", ":")  // Convert back to colon format
        def childDevice = findChildByMacAddress(macAddress)
        
        if (!childDevice) {
            if (debugLogging) log.debug "No child device found for MAC: ${macFromTopic}"
            return
        }
        
        // Parse epoch timestamp
        Long epochTime = Long.parseLong(payload.trim())
        Long currentTime = now() / 1000 // Convert to seconds
        
        if (debugLogging) {
            log.debug "WiFi heartbeat received for ${childDevice.getDisplayName()}"
            log.debug "Epoch time: ${epochTime}, Current time: ${currentTime}"
        }
        
        // Send heartbeat data to child device
        childDevice.componentHandleHeartbeat(epochTime)
        
    } catch (Exception e) {
        log.error "Failed to handle WiFi presence heartbeat: ${e.message}"
    }
}