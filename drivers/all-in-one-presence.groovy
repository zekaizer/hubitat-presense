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
        attribute "lastHeartbeat", "string"
        
        command "present"
        command "not_present"
        command "arrive"
        command "depart"
    }
    
    preferences {
        section("Settings") {
            input "debugLogging", "bool", title: "Enable Debug Logging", defaultValue: false, required: false
        }
        section("MQTT Settings") {
            input "mqttBroker", "string", title: "MQTT Broker IP Address", required: true
            input "mqttPort", "string", title: "MQTT Broker Port", defaultValue: "1883", required: true
            input "mqttUsername", "string", title: "MQTT Username (optional)", required: false
            input "mqttPassword", "password", title: "MQTT Password (optional)", required: false
            input "macAddress", "string", title: "MAC Address (format: AA:BB:CC:DD:EE:FF or AA-BB-CC-DD-EE-FF)", required: true
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
    
    // Set initial state
    sendEvent(name: "presence", value: "not present")
    sendEvent(name: "lastActivity", value: new Date().toString())
    
    // Initialize MQTT connection
    connectMQTT()
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

def handleWiFiPresenceHeartbeat(String topic, String payload) {
    try {
        // Parse epoch timestamp
        Long epochTime = Long.parseLong(payload.trim())
        Long currentTime = now() / 1000 // Convert to seconds
        
        if (debugLogging) {
            log.debug "WiFi heartbeat received from topic: ${topic}"
            log.debug "Epoch time: ${epochTime}, Current time: ${currentTime}"
        }
        
        // Update lastHeartbeat attribute
        Date heartbeatDate = new Date(epochTime * 1000)
        sendEvent(name: "lastHeartbeat", value: heartbeatDate.toString())
        
        // Check if heartbeat is recent (within last 30 seconds)
        Long timeDiff = currentTime - epochTime
        if (timeDiff <= 30) {
            // Device is present
            if (device.currentValue("presence") != "present") {
                if (debugLogging) log.debug "Setting presence to present (WiFi heartbeat)"
                sendEvent(name: "presence", value: "present")
                sendEvent(name: "lastActivity", value: new Date().toString())
            }
        } else {
            if (debugLogging) log.debug "Heartbeat is too old (${timeDiff} seconds), ignoring"
        }
        
    } catch (Exception e) {
        log.error "Failed to handle WiFi presence heartbeat: ${e.message}"
    }
}

def present() {
    if (debugLogging) log.debug "Setting presence to present"
    sendEvent(name: "presence", value: "present")
    sendEvent(name: "lastActivity", value: new Date().toString())
}

def not_present() {
    if (debugLogging) log.debug "Setting presence to not present"
    sendEvent(name: "presence", value: "not present")
    sendEvent(name: "lastActivity", value: new Date().toString())
}

def arrive() {
    present()
}

def depart() {
    not_present()
}

def refresh() {
    if (debugLogging) log.debug "Refreshing All-in-One Presence Driver"
    sendEvent(name: "lastActivity", value: new Date().toString())
}

def connectMQTT() {
    try {
        if (!settings.mqttBroker || !settings.macAddress) {
            log.warn "MQTT Broker or MAC Address not configured"
            return
        }
        
        if (debugLogging) log.debug "Connecting to MQTT broker: ${settings.mqttBroker}:${settings.mqttPort}"
        
        // Disconnect if already connected
        try {
            interfaces.mqtt.disconnect()
        } catch (Exception e) {
            // Ignore disconnect errors
        }
        
        // Connect to MQTT broker
        String brokerUrl = "tcp://${settings.mqttBroker}:${settings.mqttPort}"
        String clientId = "hubitat-presence-${device.id}"
        String username = settings.mqttUsername ?: null
        String password = settings.mqttPassword ?: null
        
        interfaces.mqtt.connect(brokerUrl, clientId, username, password)
        
        // Subscribe to topics after connection
        runIn(2, "subscribeMQTTTopics")
        
    } catch (Exception e) {
        log.error "Failed to connect to MQTT: ${e.message}"
    }
}

def subscribeMQTTTopics() {
    try {
        String macAddr = settings.macAddress
        if (!macAddr) {
            log.warn "MAC Address not configured"
            return
        }
        
        // Normalize MAC address (convert : to -)
        String normalizedMac = normalizeMacAddress(macAddr)
        
        // Subscribe to WiFi presence topics
        String unifiTopic = "UnifiU6Pro/status/mac-${normalizedMac}/lastseen/epoch"
        String asusTopic = "AsusAC68U/status/mac-${normalizedMac}/lastseen/epoch"
        
        interfaces.mqtt.subscribe(unifiTopic)
        interfaces.mqtt.subscribe(asusTopic)
        
        if (debugLogging) {
            log.debug "Subscribed to MQTT topics:"
            log.debug "  - ${unifiTopic}"
            log.debug "  - ${asusTopic}"
        }
        
    } catch (Exception e) {
        log.error "Failed to subscribe to MQTT topics: ${e.message}"
    }
}

def mqttClientStatus(String status) {
    if (debugLogging) log.debug "MQTT client status: ${status}"
    if (status == "connected") {
        subscribeMQTTTopics()
    }
}

def normalizeMacAddress(String macAddr) {
    // Convert MAC address from AA:BB:CC:DD:EE:FF to aa-bb-cc-dd-ee-ff format
    // Also handle case conversion to lowercase
    return macAddr?.toLowerCase()?.replace(":", "-")
}