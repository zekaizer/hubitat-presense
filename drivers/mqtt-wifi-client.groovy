/**
 *  MQTT WiFi Client Driver
 *  
 *  Connects to MQTT broker to receive WiFi router presence messages
 *  Supports Asus and Unifi router MQTT topics
 */
metadata {
    definition (name: "MQTT WiFi Client", namespace: "zekaizer", author: "Luke Lee") {
        capability "Sensor"
        
        attribute "connectionStatus", "enum", ["connected", "disconnected", "error"]
        attribute "lastMessage", "string"
        attribute "messageCount", "number"
        attribute "trackedDevices", "number"
        
        command "connect"
        command "disconnect"
        command "refresh"
        command "updateSubscriptions", [[name:"macList", type:"JSON_OBJECT"]]
    }
    
    preferences {
        input "mqttBroker", "text", title: "MQTT Broker IP", required: true
        input "mqttPort", "number", title: "MQTT Port", defaultValue: 1883, required: true
        input "mqttUsername", "text", title: "MQTT Username (optional)", required: false
        input "mqttPassword", "password", title: "MQTT Password (optional)", required: false
        input "enableDebug", "bool", title: "Enable Debug Logging", defaultValue: true
        input "enableInfo", "bool", title: "Enable Info Logging", defaultValue: true
        input "wifiTimeout", "number", title: "WiFi Timeout (seconds)", 
            description: "Time before marking device disconnected when no MQTT messages received", 
            defaultValue: 5, required: true, range: "5..300"
    }
}

def installed() {
    log.info "Installing MQTT WiFi Client"
    initialize()
}

def updated() {
    log.info "Updating MQTT WiFi Client - will force reconnect for new subscriptions"
    
    // Force disconnect and clear all scheduled tasks
    try {
        interfaces.mqtt.disconnect()
    } catch (Exception e) {
        // Ignore disconnect errors
    }
    
    unschedule()
    state.retryCount = 0
    
    // Initialize and force connection after short delay
    initialize()
    
    // Ensure reconnection happens even if settings are the same
    if (mqttBroker) {
        runIn(3, connect)
        logInfo "Forced reconnection scheduled for subscription update"
    }
    
    logDebug "Configuration updated"
}

def initialize() {
    state.messageCount = 0
    state.retryCount = 0
    state.lastMessageTime = [:]
    state.deviceStates = [:]
    state.subscribedMACs = []
    
    sendEvent(name: "connectionStatus", value: "disconnected")
    sendEvent(name: "messageCount", value: 0)
    sendEvent(name: "trackedDevices", value: 0)
    
    logInfo "Initializing MQTT WiFi Client"
    
    if (mqttBroker) {
        logInfo "MQTT broker configured: ${mqttBroker}:${mqttPort ?: 1883}"
        runIn(2, connect)
    } else {
        logInfo "No MQTT broker configured - waiting for settings"
        sendEvent(name: "connectionStatus", value: "disconnected")
    }
    
    logDebug "Event-based timeout management enabled"
}

def connect() {
    try {
        if (!mqttBroker) {
            log.error "No MQTT broker configured"
            sendEvent(name: "connectionStatus", value: "error")
            return
        }
        
        def broker = "tcp://${mqttBroker}:${mqttPort ?: 1883}"
        def clientId = "hubitat_wifi_${device.id}_${now()}"
        
        logInfo "Connecting to MQTT broker: ${broker} (attempt ${(state.retryCount ?: 0) + 1})"
        sendEvent(name: "connectionStatus", value: "connecting")
        
        // Disconnect any existing connection first
        try {
            interfaces.mqtt.disconnect()
        } catch (Exception ex) {
            // Ignore disconnect errors
        }
        
        if (mqttUsername && mqttPassword) {
            interfaces.mqtt.connect(broker, clientId, mqttUsername, mqttPassword)
        } else {
            interfaces.mqtt.connect(broker, clientId, null, null)
        }
        
        // Connection status will be updated in mqttClientStatus callback
        
    } catch (Exception e) {
        log.error "Failed to connect to MQTT broker: ${e.message}"
        sendEvent(name: "connectionStatus", value: "error")
        
        // Schedule retry with exponential backoff
        scheduleRetry()
    }
}

def disconnect() {
    try {
        logInfo "Disconnecting from MQTT broker"
        interfaces.mqtt.disconnect()
        sendEvent(name: "connectionStatus", value: "disconnected")
    } catch (Exception e) {
        logDebug "Disconnect error (may be expected): ${e.message}"
    }
}

def refresh() {
    logInfo "Refreshing connection - forcing reconnect to update subscriptions"
    
    // Always force reconnect to ensure updated topic subscriptions
    try {
        disconnect()
        runIn(2, connect)
        logInfo "Scheduled reconnect in 2 seconds"
    } catch (Exception e) {
        logInfo "Error during refresh: ${e.message}"
        // Try direct connect if disconnect fails
        runIn(1, connect)
    }
}

// MQTT status callback - required method
def mqttClientStatus(String status) {
    logInfo "MQTT client status: ${status}"
    
    if (status.startsWith("Error")) {
        log.error "MQTT Error: ${status}"
        sendEvent(name: "connectionStatus", value: "error")
        
        // Schedule retry with exponential backoff
        scheduleRetry()
        
    } else if (status.contains("succeeded")) {
        logInfo "MQTT connection successful"
        sendEvent(name: "connectionStatus", value: "connected")
        state.retryCount = 0  // Reset retry counter on success
        
        // Subscribe to WiFi topics after successful connection
        runIn(1, subscribeToTopics)
        
    } else if (status.contains("disconnected")) {
        logInfo "MQTT disconnected"
        sendEvent(name: "connectionStatus", value: "disconnected")
        
        // Auto-reconnect on unexpected disconnection
        if (mqttBroker) {
            logInfo "Attempting to reconnect..."
            runIn(10, connect)
        }
    }
}

def subscribeToTopics() {
    try {
        // Get MAC list from parent app
        def macList = []
        if (parent != null) {
            macList = parent.getRegisteredMACs() ?: []
        } else {
            logInfo "No parent app found - driver running standalone"
            // When running standalone, you can add test MACs here
            // macList = ["aa:bb:cc:dd:ee:ff"]
        }
        updateSubscriptions(macList)
        
    } catch (Exception e) {
        log.error "Failed to subscribe to topics: ${e.message}"
    }
}

def updateSubscriptions(macList) {
    try {
        if (!macList || macList.size() == 0) {
            logInfo "No MAC addresses to subscribe to"
            return
        }
        
        // Unsubscribe from old topics first
        unsubscribeFromOldTopics()
        
        // Subscribe to specific MAC addresses
        def subscribedCount = 0
        macList.each { mac ->
            def normalizedMAC = mac.toLowerCase().replaceAll(':', '-')
            
            // Subscribe to Asus router topics
            def asusTopic = "AsusAC68U/status/mac-${normalizedMAC}/lastseen/epoch"
            interfaces.mqtt.subscribe(asusTopic)
            logInfo "Subscribed to Asus topic: ${asusTopic}"
            
            // Subscribe to Unifi router topics  
            def unifiTopic = "UnifiU6Pro/status/mac-${normalizedMAC}/lastseen/epoch"
            interfaces.mqtt.subscribe(unifiTopic)
            logInfo "Subscribed to Unifi topic: ${unifiTopic}"
            
            subscribedCount++
        }
        
        // Store subscribed MACs for later cleanup
        state.subscribedMACs = macList
        logInfo "Successfully subscribed to ${subscribedCount} MAC addresses"
        
    } catch (Exception e) {
        log.error "Failed to update subscriptions: ${e.message}"
    }
}

def unsubscribeFromOldTopics() {
    try {
        // Unsubscribe from previously subscribed topics
        state.subscribedMACs?.each { mac ->
            def normalizedMAC = mac.toLowerCase().replaceAll(':', '-')
            
            try {
                interfaces.mqtt.unsubscribe("AsusAC68U/status/mac-${normalizedMAC}/lastseen/epoch")
                interfaces.mqtt.unsubscribe("UnifiU6Pro/status/mac-${normalizedMAC}/lastseen/epoch")
            } catch (Exception e) {
                // Ignore unsubscribe errors
            }
        }
        logDebug "Unsubscribed from old topics"
        
    } catch (Exception e) {
        logDebug "Error unsubscribing from old topics: ${e.message}"
    }
}

// MQTT message parser - required method
def parse(String description) {
    try {
        def msg = interfaces.mqtt.parseMessage(description)
        def topic = msg.topic
        def payload = msg.payload
        
        logDebug "MQTT message received - Topic: ${topic}, Payload: ${payload}"
        
        // Extract MAC address from topic
        def mac = extractMacFromTopic(topic)
        if (mac) {
            processWifiMessage(mac, payload)
        } else {
            // logDebug "Could not extract MAC from topic: ${topic}"
        }
        
    } catch (Exception e) {
        // Reduce error logging to prevent spam
        if ((state.lastErrorTime ?: 0) < (now() - 60000)) {
            log.error "Error parsing MQTT message: ${e.message}"
            state.lastErrorTime = now()
        }
    }
}

def processWifiMessage(mac, payload) {
    def currentTime = now()
    
    // Ensure state maps are initialized
    if (!state.deviceStates) state.deviceStates = [:]
    if (!state.lastMessageTime) state.lastMessageTime = [:]
    if (!state.lastEpochTime) state.lastEpochTime = [:]
    
    // Normalize MAC to colon format for consistency
    mac = mac.toLowerCase().replaceAll(/[-_]/, ':')
    
    def lastState = state.deviceStates[mac]
    logDebug "Processing WiFi message for MAC: ${mac}, Last State: ${lastState}, Payload: ${payload}"
    
    // Always update message tracking for every message received
    state.lastMessageTime[mac] = currentTime
    
    // Store epoch timestamp from payload (in seconds)
    try {
        def epochTime = payload.toLong()
        state.lastEpochTime[mac] = epochTime
        logDebug "Updated epoch time for ${mac}: ${epochTime}"
    } catch (Exception e) {
        logDebug "Failed to parse epoch time from payload: ${payload}"
    }
    
    state.messageCount = (state.messageCount ?: 0) + 1
    
    // Process new connection (state change)
    if (lastState != "connected") {
        logInfo "WiFi device connected: ${mac}"
        state.deviceStates[mac] = "connected"
        
        // Forward to parent app only on state change
        if (parent != null) {
            try {
                parent.wifiDeviceDetected(mac, payload)
            } catch (Exception e) {
                logDebug "Error forwarding to parent app: ${e.message}"
            }
        } else {
            logDebug "No parent app found to forward WiFi detection"
        }
        
        updateDeviceCount()
    } else {
        // Device already connected - just update timestamp
        logDebug "WiFi keepalive for ${mac} at ${new Date()}"
    }
    
    // Schedule timeout check for this device
    scheduleTimeoutCheck(mac)
    
    // Update stats every 10 messages to reduce events
    if (state.messageCount % 10 == 0) {
    sendEvent(name: "messageCount", value: state.messageCount)
    sendEvent(name: "lastMessage", value: "${mac}: ${payload}")
    }
}

def extractMacFromTopic(topic) {
    try {
        // Extract MAC from topic format: router/status/MAC/lastseen/epoch
        // Examples:
        // AsusAC68U/status/mac-aa-bb-cc-dd-ee-ff/lastseen/epoch
        // UnifiU6Pro/status/mac-aa:bb:cc:dd:ee:ff/lastseen/epoch
        
        def patterns = [
            /\/status\/mac-([0-9a-fA-F-]{17})\/lastseen/,  // mac-aa-bb-cc-dd-ee-ff
            /\/status\/mac-([0-9a-fA-F:]{17})\/lastseen/   // mac-aa:bb:cc:dd:ee:ff
        ]
        
        for (pattern in patterns) {
            def matcher = topic =~ pattern
            if (matcher) {
                def mac = matcher[0][1]
                // Normalize to colon format
                mac = mac.replaceAll(/[-_]/, ':').toLowerCase()
                logDebug "Extracted MAC: ${mac} from topic: ${topic}"
                return mac
            }
        }
        
        logDebug "No MAC found in topic: ${topic}"
        logDebug "Supported formats: mac-aa-bb-cc-dd-ee-ff, mac-aa:bb:cc:dd:ee:ff"
        return null
        
    } catch (Exception e) {
        logDebug "Error extracting MAC from topic ${topic}: ${e.message}"
        return null
    }
}

def logDebug(msg) {
    if (enableDebug) log.debug "${device.displayName}: ${msg}"
}

// Event-based timeout management
def scheduleTimeoutCheck(mac) {
    // Cancel previous timeout check for this MAC
    def cleanMac = mac.replaceAll(':', '')
    
    // Use closure instead of dynamic method name
    unschedule("timeoutCheck${cleanMac}")
    
    // Schedule new timeout check
    def timeoutSeconds = wifiTimeout ?: 5
    logDebug "Scheduling timeout check for ${mac} in ${timeoutSeconds + 1} seconds"
    
    runIn(timeoutSeconds + 1, "checkDeviceTimeout", [overwrite: true, data: [mac: mac]])
}

def methodMissing(String name, args) {
    // Handle dynamic timeout check methods
    if (name.startsWith("checkTimeout_")) {
        def mac = name.substring(13).replaceAll('(..)', '$1:').replaceAll(':$', '')
        checkTimeout(mac)
        return null
    }
    // For non-timeout methods, log error instead of throwing exception
    log.error "Method not found: ${name}"
    return null
}

def checkDeviceTimeout(data) {
    def mac = data?.mac
    if (!mac) {
        log.error "checkDeviceTimeout called without MAC address"
        return
    }
    
    def currentEpochSeconds = now() / 1000
    def lastEpochTime = state.lastEpochTime[mac]
    def timeoutSeconds = wifiTimeout ?: 5
    
    if (!lastEpochTime) {
        logDebug "No epoch time recorded for ${mac}"
        return
    }
    
    def elapsed = currentEpochSeconds - lastEpochTime
    
    if (elapsed > timeoutSeconds) {
        // Device has timed out
        logInfo "WiFi device timed out: ${mac} (last seen: ${elapsed}s ago)"
        state.deviceStates[mac] = "disconnected"
        
        // Forward timeout to parent app
        if (parent != null) {
            try {
                parent.wifiDeviceTimeout(mac)
            } catch (Exception e) {
                logDebug "Error forwarding timeout to parent app: ${e.message}"
            }
        }
        
        updateDeviceCount()
    } else {
        // Still within timeout, reschedule
        def remaining = timeoutSeconds - elapsed + 1
        logDebug "Device ${mac} still active, rechecking in ${remaining}s"
        
        runIn(remaining as Integer, "checkDeviceTimeout", [overwrite: true, data: [mac: mac]])
    }
}

// Keep old method for compatibility
def checkTimeout(mac) {
    checkDeviceTimeout([mac: mac])
}

def updateDeviceCount() {
    // Ensure state map is initialized
    if (!state.deviceStates) state.deviceStates = [:]
    
    def connectedCount = state.deviceStates.count { it.value == "connected" }
    sendEvent(name: "trackedDevices", value: connectedCount)
    logDebug "Connected devices: ${connectedCount}"
}


// Centralized retry logic
def scheduleRetry() {
    state.retryCount = (state.retryCount ?: 0) + 1
    if (state.retryCount < 5) {
        def retryDelay = Math.min(60, 10 * Math.pow(2, state.retryCount - 1))
        logDebug "Will retry connection in ${retryDelay} seconds (retry ${state.retryCount}/5)"
        unschedule(connect)
        runIn(retryDelay as Integer, connect)
    } else {
        log.error "Maximum retry attempts reached. Please check MQTT broker settings."
        state.retryCount = 0
    }
}

def logInfo(msg) {
    if (enableInfo != false) log.info "${device.displayName}: ${msg}"
}