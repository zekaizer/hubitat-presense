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
    }
    
    preferences {
        input "mqttBroker", "text", title: "MQTT Broker IP", required: true
        input "mqttPort", "number", title: "MQTT Port", defaultValue: 1883, required: true
        input "mqttUsername", "text", title: "MQTT Username (optional)", required: false
        input "mqttPassword", "password", title: "MQTT Password (optional)", required: false
        input "enableDebug", "bool", title: "Enable Debug Logging", defaultValue: true
        input "enableInfo", "bool", title: "Enable Info Logging", defaultValue: true
        input "wifiTimeout", "number", title: "WiFi Timeout (seconds)", defaultValue: 180, required: true, range: "30..600"
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
}

def initialize() {
    state.messageCount = 0
    state.retryCount = 0
    state.lastMessageTime = [:]
    state.deviceStates = [:]
    
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
    
    // Start timeout checker
    runEvery1Minute(checkWifiTimeouts)
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
        
        // Retry with backoff
        state.retryCount = (state.retryCount ?: 0) + 1
        if (state.retryCount < 5) {
            def retryDelay = Math.min(60, 10 * state.retryCount)
            logInfo "Will retry connection in ${retryDelay} seconds"
            runIn(retryDelay, connect)
        }
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
        
        // Retry connection with backoff
        state.retryCount = (state.retryCount ?: 0) + 1
        if (state.retryCount < 5) {
            def retryDelay = Math.min(60, 15 * state.retryCount)
            logInfo "Will retry connection in ${retryDelay} seconds (retry ${state.retryCount}/5)"
            runIn(retryDelay, connect)
        } else {
            log.error "Maximum retry attempts reached. Please check MQTT broker settings."
        }
        
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
        // Subscribe to Asus router topics (wildcard for any MAC address)
        interfaces.mqtt.subscribe("AsusAC68U/status/+/lastseen/epoch")
        logInfo "Subscribed to Asus router topics: AsusAC68U/status/+/lastseen/epoch"
        
        // Subscribe to Unifi router topics (wildcard for any MAC address)
        interfaces.mqtt.subscribe("UnifiU6Pro/status/+/lastseen/epoch")
        logInfo "Subscribed to Unifi router topics: UnifiU6Pro/status/+/lastseen/epoch"
        
    } catch (Exception e) {
        log.error "Failed to subscribe to topics: ${e.message}"
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
    
    def lastState = state.deviceStates[mac]
    
    // Update message tracking
    state.lastMessageTime[mac] = currentTime
    state.messageCount = (state.messageCount ?: 0) + 1
    
    // Only process if this is a new connection (state change)
    if (lastState != "connected") {
        logInfo "WiFi device connected: ${mac}"
        state.deviceStates[mac] = "connected"
        
        // Forward to parent app only on state change
        if (parent) {
            parent.wifiDeviceDetected(mac, payload)
        } else {
            logDebug "No parent app found to forward WiFi detection"
        }
        
        updateDeviceCount()
    }
    
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
        // UnifiU6Pro/status/aa:bb:cc:dd:ee:ff/lastseen/epoch
        // AsusAC68U/status/aa-bb-cc-dd-ee-ff/lastseen/epoch
        
        def patterns = [
            /\/status\/mac-([0-9a-fA-F-]{17})\/lastseen/,  // mac-aa-bb-cc-dd-ee-ff
            /\/status\/([0-9a-fA-F:-]{17})\/lastseen/,     // aa:bb:cc:dd:ee:ff or aa-bb-cc-dd-ee-ff
            /\/status\/([0-9a-fA-F_]{17})\/lastseen/       // aa_bb_cc_dd_ee_ff
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
        logDebug "Supported formats: mac-aa-bb-cc-dd-ee-ff, aa:bb:cc:dd:ee:ff, aa-bb-cc-dd-ee-ff"
        return null
        
    } catch (Exception e) {
        logDebug "Error extracting MAC from topic ${topic}: ${e.message}"
        return null
    }
}

def logDebug(msg) {
    if (enableDebug) log.debug "${device.displayName}: ${msg}"
}

// WiFi timeout checker
def checkWifiTimeouts() {
    // Ensure state maps are initialized
    if (!state.deviceStates) state.deviceStates = [:]
    if (!state.lastMessageTime) state.lastMessageTime = [:]
    
    def currentTime = now()
    def timeoutMs = (wifiTimeout ?: 180) * 1000
    def timedOutDevices = []
    
    state.lastMessageTime.each { mac, lastTime ->
        if (state.deviceStates[mac] == "connected" && (currentTime - lastTime) > timeoutMs) {
            timedOutDevices << mac
        }
    }
    
    timedOutDevices.each { mac ->
        logInfo "WiFi device timed out: ${mac}"
        state.deviceStates[mac] = "disconnected"
        
        // Forward timeout to parent app
        if (parent) {
            parent.wifiDeviceTimeout(mac)
        }
    }
    
    if (timedOutDevices.size() > 0) {
        updateDeviceCount()
    }
}

def updateDeviceCount() {
    // Ensure state map is initialized
    if (!state.deviceStates) state.deviceStates = [:]
    
    def connectedCount = state.deviceStates.count { it.value == "connected" }
    sendEvent(name: "trackedDevices", value: connectedCount)
    logDebug "Connected devices: ${connectedCount}"
}

def logInfo(msg) {
    if (enableInfo != false) log.info "${device.displayName}: ${msg}"
}