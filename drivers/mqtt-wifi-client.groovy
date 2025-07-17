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
        
        command "connect"
        command "disconnect"
        command "refresh"
    }
    
    preferences {
        input "mqttBroker", "text", title: "MQTT Broker IP", required: true
        input "mqttPort", "number", title: "MQTT Port", defaultValue: 1883, required: true
        input "mqttUsername", "text", title: "MQTT Username (optional)", required: false
        input "mqttPassword", "password", title: "MQTT Password (optional)", required: false
        input "enableDebug", "bool", title: "Enable Debug Logging", defaultValue: false
    }
}

def installed() {
    log.info "Installing MQTT WiFi Client"
    initialize()
}

def updated() {
    log.info "Updating MQTT WiFi Client"
    disconnect()
    unschedule()
    initialize()
}

def initialize() {
    state.messageCount = 0
    state.retryCount = 0
    sendEvent(name: "connectionStatus", value: "disconnected")
    sendEvent(name: "messageCount", value: 0)
    
    logInfo "Initializing MQTT WiFi Client"
    
    if (mqttBroker) {
        logInfo "MQTT broker configured: ${mqttBroker}:${mqttPort ?: 1883}"
        runIn(3, connect)
    } else {
        logInfo "No MQTT broker configured"
        sendEvent(name: "connectionStatus", value: "error")
    }
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
    logInfo "Refreshing connection"
    if (device.currentValue("connectionStatus") != "connected") {
        connect()
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
        // Subscribe to Asus router topics (mac-aa-bb-cc-dd-ee-ff format)
        interfaces.mqtt.subscribe("AsusAC68U/status/mac-+/lastseen/epoch")
        logInfo "Subscribed to Asus router topics: AsusAC68U/status/mac-+/lastseen/epoch"
        
        // Subscribe to Unifi router topics (mac-aa-bb-cc-dd-ee-ff format)
        interfaces.mqtt.subscribe("UnifiU6Pro/status/mac-+/lastseen/epoch")
        logInfo "Subscribed to Unifi router topics: UnifiU6Pro/status/mac-+/lastseen/epoch"
        
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
        
        // Update last message info
        sendEvent(name: "lastMessage", value: "${topic}: ${payload}")
        state.messageCount = (state.messageCount ?: 0) + 1
        sendEvent(name: "messageCount", value: state.messageCount)
        
        // Extract MAC address from topic
        def mac = extractMacFromTopic(topic)
        if (mac) {
            // Forward to parent app
            if (parent) {
                parent.wifiDeviceDetected(mac, payload)
            } else {
                logDebug "No parent app found to forward WiFi detection"
            }
        } else {
            logDebug "Could not extract MAC from topic: ${topic}"
        }
        
    } catch (Exception e) {
        log.error "Error parsing MQTT message: ${e.message}"
    }
}

def extractMacFromTopic(topic) {
    try {
        // Extract MAC from topic format: mac-aa-bb-cc-dd-ee-ff
        // Examples:
        // AsusAC68U/status/mac-aa-bb-cc-dd-ee-ff/lastseen/epoch
        // UnifiU6Pro/status/mac-aa-bb-cc-dd-ee-ff/lastseen/epoch
        
        def pattern = /mac-([0-9a-fA-F-]{17})/  // mac-aa-bb-cc-dd-ee-ff
        def matcher = topic =~ pattern
        
        if (matcher) {
            def mac = matcher[0][1]
            // Convert to colon format: aa-bb-cc-dd-ee-ff -> aa:bb:cc:dd:ee:ff
            mac = mac.replaceAll('-', ':').toLowerCase()
            logDebug "Extracted MAC: ${mac} from topic: ${topic}"
            return mac
        }
        
        logDebug "No MAC found in topic: ${topic} (expected format: mac-aa-bb-cc-dd-ee-ff)"
        return null
        
    } catch (Exception e) {
        logDebug "Error extracting MAC from topic ${topic}: ${e.message}"
        return null
    }
}

def logDebug(msg) {
    if (enableDebug) log.debug "${device.displayName}: ${msg}"
}

def logInfo(msg) {
    log.info "${device.displayName}: ${msg}"
}