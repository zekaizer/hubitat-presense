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
        capability "Refresh"
        
        attribute "lastActivity", "string"
        attribute "childCount", "number"
        attribute "presentCount", "number"
        attribute "securitySystemStatus", "enum", ["off", "home", "away", "night"]
        
        command "createChildDevice", [[name:"macAddress", type:"STRING", description:"MAC Address (format: AA:BB:CC:DD:EE:FF or AA-BB-CC-DD-EE-FF)"], [name:"deviceLabel", type:"STRING", description:"Device Label (optional)"]]
        command "removeChildDevice", [[name:"deviceId", type:"STRING", description:"Device ID (DNI) or MAC Address of device to remove"]]
        command "removeAllChildren"
        command "updateChildMacAddress", [[name:"deviceId", type:"STRING", description:"Device ID (DNI) of child to update"], [name:"newMacAddress", type:"STRING", description:"New MAC Address"]]
        command "updateChildLabel", [[name:"deviceId", type:"STRING", description:"Device ID (DNI) of child to update"], [name:"newLabel", type:"STRING", description:"New Device Label"]]
        command "setSecuritySystemMode", [[name:"mode", type:"ENUM", constraints: ["off", "home", "away", "night"], description:"Security system mode from webhook"]]
    }
    
    preferences {
        section("Settings") {
            input "debugLogging", "bool", title: "Enable Debug Logging", defaultValue: false, required: false
        }
        section("Default MQTT Settings for Child Devices") {
            input "defaultMqttBroker", "string", title: "Default MQTT Broker IP Address", required: false
            input "defaultMqttPort", "string", title: "Default MQTT Broker Port", defaultValue: "1883", required: false
            input "defaultMqttUsername", "string", title: "Default MQTT Username (optional)", required: false
            input "defaultMqttPassword", "password", title: "Default MQTT Password (optional)", required: false
            input "defaultHeartbeatTimeout", "number", title: "Default Heartbeat Timeout (seconds)", defaultValue: 60, range: "5..3600", required: false
        }
        section("Security System Integration") {
            input "securitySystemEnabled", "bool", title: "Enable Security System Integration", defaultValue: false, required: false
            input "securitySystemUrl", "string", title: "Homebridge Security System URL", description: "e.g., http://192.168.1.100", required: false
            input "securitySystemPort", "string", title: "Homebridge Security System Port", defaultValue: "8585", required: false
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
    
    // Create appropriate control devices based on settings
    if (settings.securitySystemEnabled) {
        // Initialize Security System mode
        if (!state.securitySystemMode) {
            state.securitySystemMode = "home"
            sendEvent(name: "securitySystemStatus", value: "home")
        } else {
            sendEvent(name: "securitySystemStatus", value: state.securitySystemMode)
        }
    } else {
        // Create or find legacy devices
        createAnyonePresenceDevice()
        createGuestPresenceDevice()
    }

    def children = getChildDevices()
        
    children.each { child ->
        // Only count "All-in-One Presence Child" devices, skip Anyone Presence
        if (child.name != "All-in-One Presence Child")
            return
        
        // Initialize child device settings
        if (debugLogging) log.debug "Initializing child device: ${child.getDisplayName()}"
        child.initialize()
    }

    // Update child count (which will also update anyone presence)
    updateChildStatistics()
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


def refresh() {
    if (debugLogging) log.debug "Refreshing Composite Presence Driver"
    // Update statistics (which will also update anyone presence)
    updateChildStatistics()
    sendEvent(name: "lastActivity", value: new Date().toString())
}

def createChildDevice(macAddress = null, deviceLabel = null) {
    if (debugLogging) log.debug "Creating child device with MAC address: ${macAddress}, label: ${deviceLabel}"
    
    if (!macAddress || macAddress.trim().isEmpty()) {
        log.error "Valid MAC address is required to create child device"
        return null
    }
    
    // Validate MAC address format
    if (!isValidMacAddress(macAddress)) {
        log.error "Invalid MAC address format: ${macAddress}. Expected format: AA:BB:CC:DD:EE:FF or AA-BB-CC-DD-EE-FF"
        return null
    }
    
    try {
        // Check if child with this MAC already exists
        def existingChild = findChildByMacAddress(macAddress)
        if (existingChild) {
            log.warn "Child device with MAC address ${macAddress} already exists: ${existingChild.getDisplayName()}"
            return existingChild
        }
        
        // Generate UUID-based DNI with device.id prefix
        def childDeviceNetworkId = "composite-presence-${device.id}-${UUID.randomUUID().toString()}"
        
        // Create a new All-in-One Presence child device
        String normalizedMac = normalizeMacAddress(macAddress)
        String finalLabel = deviceLabel ?: "Presence ${normalizedMac}"
        
        def childDevice = this.addChildDevice(
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
            
            // Store MAC address in child device data for immediate access
            childDevice.updateDataValue("macAddress", macAddress)
            if (debugLogging) log.debug "Set MAC address data value '${macAddress}' for child device"
            
            // Use runIn to delay setting configuration after device is fully created
            runIn(1, "configureChildDevice", [data: [deviceId: childDeviceNetworkId, macAddress: macAddress]])
            
            // Subscribe to MQTT topics will be done after configuration is complete
            
            updateChildStatistics()
            return childDevice
        } else {
            log.error "Failed to create child device - addChildDevice returned null"
        }
    } catch (Exception e) {
        log.error "Exception while creating child device: ${e.message}"
        log.error "Stack trace: ${e.getStackTrace()}"
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
            def macAddress = childDevice.getDataValue("macAddress") ?: childDevice.getSetting("macAddress")
            if (macAddress) {
                unsubscribeFromMacAddress(macAddress)
            }
            
            deleteChildDevice(childDevice.getDeviceNetworkId())
            if (debugLogging) log.debug "Successfully removed child device: ${childDevice.getDisplayName()}"
            updateChildStatistics()
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
    } catch (Exception e) {
        log.error "Exception while removing all child devices: ${e.message}"
    }
}


def createAnyonePresenceDevice() {
    // Legacy method - kept for backward compatibility
    // Only create if Security System is not enabled
    if (settings.securitySystemEnabled) {
        if (debugLogging) log.debug "Security System enabled, skipping Anyone Motion device creation"
        return
    }
    
    // Check if Anyone Presence device already exists
    def anyoneDni = "composite-presence-${device.id}-anyone"
    def anyoneDevice = getChildDevice(anyoneDni)
    
    if (!anyoneDevice) {
        try {
            if (debugLogging) log.debug "Creating Anyone Motion child device using built-in Generic Component Motion Sensor"
            
            anyoneDevice = addChildDevice(
                "hubitat",
                "Generic Component Motion Sensor",
                anyoneDni,
                [
                    name: "Anyone Motion",
                    label: "Anyone Motion",
                    isComponent: true
                ]
            )
            
            if (anyoneDevice) {
                if (debugLogging) log.debug "Successfully created Anyone Motion device: ${anyoneDevice.getDisplayName()}"
                
                // Mark this as the special "anyone" device
                anyoneDevice.updateDataValue("deviceType", "anyone")
                
                // Set initial state to inactive
                anyoneDevice.parse([[name: "motion", value: "inactive", descriptionText: "${anyoneDevice.displayName} is inactive"]])
            } else {
                log.error "Failed to create Anyone Motion device"
            }
            
        } catch (Exception e) {
            log.error "Exception while creating Anyone Motion device: ${e.message}"
        }
    } else {
        if (debugLogging) log.debug "Anyone Motion device already exists: ${anyoneDevice.getDisplayName()}"
    }
}

def createGuestPresenceDevice() {
    // Legacy method - kept for backward compatibility
    // Only create if Security System is not enabled
    if (settings.securitySystemEnabled) {
        if (debugLogging) log.debug "Security System enabled, skipping Guest Access Lock creation"
        return
    }
    
    // Check if Guest Presence device already exists
    def guestDni = "composite-presence-${device.id}-guest"
    def guestDevice = getChildDevice(guestDni)
    
    if (!guestDevice) {
        try {
            if (debugLogging) log.debug "Creating Guest Access Lock using built-in Generic Component Lock"
            
            guestDevice = addChildDevice(
                "hubitat",
                "Generic Component Lock",
                guestDni,
                [
                    name: "Guest Access",
                    label: "Guest Access Lock",
                    isComponent: true
                ]
            )
            
            if (guestDevice) {
                if (debugLogging) log.debug "Successfully created Guest Access Lock: ${guestDevice.getDisplayName()}"
                
                // Mark this as the special "guest" device
                guestDevice.updateDataValue("deviceType", "guest")
                
                // Set initial state to locked (no guest access)
                guestDevice.sendEvent(name: "lock", value: "locked", descriptionText: "${guestDevice.displayName} is locked")
            } else {
                log.error "Failed to create Guest Access Lock"
            }
            
        } catch (Exception e) {
            log.error "Exception while creating Guest Access Lock: ${e.message}"
        }
    } else {
        if (debugLogging) log.debug "Guest Access Lock already exists: ${guestDevice.getDisplayName()}"
    }
}


def configureChildDevice(data) {
    try {
        def deviceId = data.deviceId
        def macAddress = data.macAddress
        
        if (debugLogging) log.debug "Configuring child device ${deviceId} with MAC ${macAddress}"
        
        def childDevice = getChildDevice(deviceId)
        if (!childDevice) {
            log.error "Child device not found: ${deviceId}"
            return
        }
        
        // No need to set individual settings - child device is managed by parent
        if (debugLogging) log.debug "Child device configured as component - no individual settings needed"
        
        // Initialize the child device after settings are applied
        childDevice.initialize()
        
        // Subscribe to MQTT topics for this MAC address
        subscribeToMacAddress(macAddress)
        
        // Update child statistics after configuration
        updateChildStatistics()
        
        if (debugLogging) log.debug "Completed configuration of child device ${childDevice.getDisplayName()}"
        
    } catch (Exception e) {
        log.error "Exception while configuring child device: ${e.message}"
    }
}

def componentPresenceHandler(childDevice, presenceValue) {
    // This method is called by child devices when their presence changes
    log.info "Child device ${childDevice.getDisplayName()} presence changed to: ${presenceValue}"
    
    // Use runIn to ensure child device state is fully updated before checking statistics
    runIn(1, "updateChildStatisticsDelayed", [overwrite: true])
}

def componentRefresh(childDevice) {
    // Handle refresh requests from child devices
    // IMPORTANT: Do nothing here to prevent infinite loop
    // Statistics will be updated through componentPresenceHandler when presence changes
    if (debugLogging) log.debug "Child device ${childDevice.getDisplayName()} refresh acknowledged"
}

def componentUnlock(childDevice) {
    // Handle Guest Access Lock unlocking (guest access enabled)
    // Legacy method - only used when Security System is not enabled
    def deviceType = childDevice.getDataValue("deviceType")
    if (deviceType == "guest" && !settings.securitySystemEnabled) {
        log.info "Guest Access Lock UNLOCKED - Guest access enabled"
        // Update the lock state
        childDevice.sendEvent(name: "lock", value: "unlocked", descriptionText: "${childDevice.displayName} is unlocked")
        // Use runIn to ensure lock state is fully updated before checking statistics
        runIn(1, "updateChildStatisticsDelayed", [overwrite: true])
    }
}

def componentLock(childDevice) {
    // Handle Guest Access Lock locking (guest access disabled)
    // Legacy method - only used when Security System is not enabled
    def deviceType = childDevice.getDataValue("deviceType")
    if (deviceType == "guest" && !settings.securitySystemEnabled) {
        log.info "Guest Access Lock LOCKED - Guest access disabled"
        // Update the lock state
        childDevice.sendEvent(name: "lock", value: "locked", descriptionText: "${childDevice.displayName} is locked")
        // Use runIn to ensure lock state is fully updated before checking statistics
        runIn(1, "updateChildStatisticsDelayed", [overwrite: true])
    }
}

def updateChildStatisticsDelayed() {
    // Delayed version of updateChildStatistics to handle timing issues
    if (debugLogging) log.debug "Delayed child statistics update triggered"
    updateChildStatistics()
}

def updateChildStatistics() {
    def children = getChildDevices()
    def childCount = 0
    def presentCount = 0
    def guestPresent = false
    
    // Check Security System mode first
    if (settings.securitySystemEnabled && state.securitySystemMode == "off") {
        guestPresent = true
        if (debugLogging) log.debug "Security System is in OFF mode - Guest access enabled"
    }
    
    if (debugLogging) log.debug "Updating child statistics for ${children.size()} children:"
    
    children.each { child ->
        def deviceType = child.getDataValue("deviceType")
        
        
        // Check Guest Access Lock (legacy mode)
        if (deviceType == "guest") {
            def lockValue = child.currentValue("lock")
            if (lockValue == "unlocked") {
                guestPresent = true
                if (debugLogging) log.debug "  Guest Access Lock is UNLOCKED - Guest present"
            } else {
                guestPresent = false
                if (debugLogging) log.debug "  Guest Access Lock is LOCKED - No guest"
            }
            return
        }
        
        // Skip Anyone Motion device
        if (deviceType == "anyone") {
            return
        }
        
        // Only count "All-in-One Presence Child" devices
        if (child.name != "All-in-One Presence Child")
            return
        
        childCount++
        def presenceValue = child.currentValue("presence")
        def macAddress = child.getDataValue("macAddress") ?: child.getSetting("macAddress")
        
        
        if (presenceValue == "present") {
            if (debugLogging) log.debug "  Child ${child.getDisplayName()} (MAC: ${macAddress}): presence = '${presenceValue}'"
            presentCount++
        }
    }
    
    sendEvent(name: "childCount", value: childCount)
    sendEvent(name: "presentCount", value: presentCount)
    
    if (debugLogging) log.debug "Child statistics updated: ${presentCount}/${childCount} present, Guest: ${guestPresent}"
    
    // Update anyone presence with guest override
    if (settings.securitySystemEnabled) {
        // Update Security System mode based on presence
        if (presentCount > 0) {
            updateSecuritySystemMode("home")
        } else {
            updateSecuritySystemMode("away")
        }
    } else {
        // Legacy mode: update Anyone Motion
        updateAnyonePresence(presentCount, guestPresent)
    }
}

def updateAnyonePresence(presentCount, guestPresent = false) {
    // Legacy method - only used when Security System is not enabled
    if (settings.securitySystemEnabled) {
        if (debugLogging) log.debug "Security System enabled, skipping Anyone Motion update"
        return
    }
    
    def anyoneDni = "composite-presence-${device.id}-anyone"
    def anyoneDevice = getChildDevice(anyoneDni)
    if (!anyoneDevice) {
        createAnyonePresenceDevice()
        anyoneDevice = getChildDevice(anyoneDni)
        if (!anyoneDevice) {
            return
        }
    }
    
    // If guest is present, always set motion to active
    def anyoneMotion = (presentCount > 0 || guestPresent) ? "active" : "inactive"

    log.info "Updating Anyone Motion to: ${anyoneMotion} (presentCount: ${presentCount}, guestPresent: ${guestPresent})"
    def currentAnyoneMotion = anyoneDevice.currentValue("motion")
    if (currentAnyoneMotion != anyoneMotion) {
        anyoneDevice.parse([[name: "motion", value: anyoneMotion, descriptionText: "${anyoneDevice.displayName} is ${anyoneMotion}"]])
    }
}



def restoreState() {
    // Restore saved activity or set default
    String savedActivity = state.lastActivity
    
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

def isValidMacAddress(String macAddr) {
    // Validate MAC address format: AA:BB:CC:DD:EE:FF or AA-BB-CC-DD-EE-FF
    if (!macAddr) return false
    
    // Remove whitespace and convert to uppercase for validation
    String cleanMac = macAddr.trim().toUpperCase()
    
    // Check colon format: AA:BB:CC:DD:EE:FF
    if (cleanMac.matches(/^[0-9A-F]{2}:[0-9A-F]{2}:[0-9A-F]{2}:[0-9A-F]{2}:[0-9A-F]{2}:[0-9A-F]{2}$/)) {
        return true
    }
    
    // Check dash format: AA-BB-CC-DD-EE-FF
    if (cleanMac.matches(/^[0-9A-F]{2}-[0-9A-F]{2}-[0-9A-F]{2}-[0-9A-F]{2}-[0-9A-F]{2}-[0-9A-F]{2}$/)) {
        return true
    }
    
    return false
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
            def macAddress = child.getDataValue("macAddress") ?: child.getSetting("macAddress")
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
    // Find child device by MAC address data value
    def children = getChildDevices()
    if (debugLogging) log.debug "Searching for MAC '${macAddress}' among ${children.size()} children"
    
    return children.find { child ->
        // Try getDataValue first (immediate), then getSetting (persistent)
        def childMac = child.getDataValue("macAddress") ?: child.getSetting("macAddress")
        def normalizedChildMac = normalizeMacAddress(childMac)
        def normalizedSearchMac = normalizeMacAddress(macAddress)
        
        if (debugLogging) {
            log.debug "Comparing child MAC '${childMac}' (normalized: '${normalizedChildMac}') with search MAC '${macAddress}' (normalized: '${normalizedSearchMac}')"
        }
        
        return childMac && normalizedChildMac == normalizedSearchMac
    }
}

def updateChildMacAddress(String deviceId, String newMacAddress) {
    if (debugLogging) log.debug "Updating MAC address for device ${deviceId} to ${newMacAddress}"
    
    if (!deviceId || !newMacAddress) {
        log.error "Device ID and new MAC address are required"
        return false
    }
    
    // Validate MAC address format
    if (!isValidMacAddress(newMacAddress)) {
        log.error "Invalid MAC address format: ${newMacAddress}. Expected format: AA:BB:CC:DD:EE:FF or AA-BB-CC-DD-EE-FF"
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
        def oldMacAddress = childDevice.getDataValue("macAddress") ?: childDevice.getSetting("macAddress")
        if (oldMacAddress) {
            unsubscribeFromMacAddress(oldMacAddress)
        }
        
        // Update MAC address setting and data value
        childDevice.updateSetting("macAddress", newMacAddress)
        childDevice.updateDataValue("macAddress", newMacAddress)
        
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
        if (debugLogging) log.debug "Converted MAC from topic '${macFromTopic}' to '${macAddress}'"
        
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

def setSecuritySystemMode(String mode) {
    if (!settings.securitySystemEnabled) {
        log.warn "Security System integration is not enabled"
        return
    }
    
    log.info "Security System mode changed to: ${mode}"
    
    // Store the mode in state
    state.securitySystemMode = mode
    
    // Update the Security System status attribute
    sendEvent(name: "securitySystemStatus", value: mode, descriptionText: "Security System is in ${mode} mode")
    
    // If mode is off, it acts like guest access is enabled
    // Trigger presence update with delay to ensure state is saved
    runIn(1, "updateChildStatisticsDelayed", [overwrite: true])
}

def updateSecuritySystemMode(String mode) {
    if (!settings.securitySystemEnabled || !settings.securitySystemUrl || !settings.securitySystemPort) {
        if (debugLogging) log.debug "Security System integration not properly configured"
        return
    }
    
    // Don't update if mode is already off (user manually set)
    if (state.securitySystemMode == "off") {
        if (debugLogging) log.debug "Security System is in off mode (guest access), skipping automatic update"
        return
    }
    
    try {
        String url = "${settings.securitySystemUrl}:${settings.securitySystemPort}/${mode}"
        
        if (debugLogging) log.debug "Sending Security System mode update: ${mode} to ${url}"
        
        def params = [
            uri: url,
            timeout: 10,
            ignoreSSLIssues: true
        ]
        
        httpGet(params) { response ->
            if (response.status == 200) {
                log.info "Successfully updated Security System to ${mode} mode"
                // Update local state to match
                state.securitySystemMode = mode
                
                // Update the Security System status attribute
                sendEvent(name: "securitySystemStatus", value: mode, descriptionText: "Security System is in ${mode} mode")
            } else {
                log.error "Failed to update Security System mode: ${response.status}"
            }
        }
        
    } catch (Exception e) {
        log.error "Exception while updating Security System mode: ${e.message}"
    }
}