/**
 *  WiFi GPS Presence Manager Enhanced
 *  
 *  Manages multiple presence devices with Anyone and Away delay
 */
definition(
    name: "WiFi GPS Presence Manager Enhanced",
    namespace: "zekaizer",
    author: "Luke Lee",
    description: "Advanced presence detection with Anyone and Away delay devices",
    category: "Convenience",
    parent: null,
    iconUrl: "",
    iconX2Url: "",
    singleInstance: true
)

preferences {
    page(name: "mainPage")
    page(name: "devicesPage")
    page(name: "anyoneAwayPage")
    page(name: "mqttPage")
    page(name: "webhookPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Enhanced Presence Manager", install: true, uninstall: true) {
        section("Individual Devices") {
            href "devicesPage", title: "Manage Individual Devices", 
                description: "Configure individual presence devices"
        }
        section("Combined Presence") {
            href "anyoneAwayPage", title: "Anyone & Away Settings",
                description: "Configure Anyone Home and Away delay"
        }
        section("Integrations") {
            def mqttStatus = settings.mqttBroker ? "✓ Configured" : "❌ Required"
            href "mqttPage", title: "MQTT Settings (Required)", description: "Configure MQTT broker for WiFi detection - ${mqttStatus}"
            href "webhookPage", title: "Webhook Settings", description: "Configure GPS geofencing webhooks"
        }
        section("Current Status") {
            paragraph getStatusSummary()
        }
        section("Options") {
            input "enableDebug", "bool", title: "Enable Debug Logging", defaultValue: false
        }
    }
}

def devicesPage() {
    dynamicPage(name: "devicesPage", title: "Presence Devices") {
        section("Add New Device") {
            input "newDeviceName", "text", title: "Device Name (e.g., John's Phone)", submitOnChange: true
            input "newDeviceMAC", "text", title: "WiFi MAC Address (e.g., AA:BB:CC:DD:EE:FF)", submitOnChange: true
            input "newDeviceGPSID", "text", title: "GPS Device ID (e.g., phone1, iphone_john)", submitOnChange: true
            
            if (newDeviceName && newDeviceMAC) {
                paragraph "✓ Device will be created when you press Done"
            }
            
            paragraph "<b>Format Guide:</b>"
            paragraph "• Device Name: Descriptive name for identification"
            paragraph "• MAC Address: Colon-separated format, case insensitive"
            paragraph "• GPS Device ID: Alphanumeric characters, no spaces"
        }
        
        section("Existing Devices") {
            def devices = getChildDevices().findAll { 
                it.deviceNetworkId.startsWith("presence-")
            }
            
            if (devices.size() == 0) {
                paragraph "No devices created yet."
            } else {
                devices.each { device ->
                    def deviceId = device.deviceNetworkId
                    def isEditing = settings["edit_${deviceId}"]
                    
                    if (isEditing) {
                        // Edit mode
                        input "edit_${deviceId}", "bool", title: "Edit Mode", defaultValue: true, submitOnChange: true
                        input "editName_${deviceId}", "text", title: "Device Name", 
                            defaultValue: device.displayName, submitOnChange: true
                        input "editMAC_${deviceId}", "text", title: "WiFi MAC Address (e.g., AA:BB:CC:DD:EE:FF)", 
                            defaultValue: device.data?.mac ?: "", submitOnChange: true
                        input "editGPSID_${deviceId}", "text", title: "GPS Device ID (e.g., phone1, iphone_john)", 
                            defaultValue: device.data?.gpsId ?: "", submitOnChange: true
                        
                        if (settings["editName_${deviceId}"] && settings["editMAC_${deviceId}"]) {
                            paragraph "✓ Changes will be saved"
                        }
                    } else {
                        // View mode
                        def mac = device.data?.mac ?: "Not configured"
                        def gpsId = device.data?.gpsId ?: "Not configured"
                        paragraph "<b>${device.displayName}</b><br>MAC: ${mac}<br>GPS ID: ${gpsId}"
                        input "edit_${deviceId}", "bool", title: "Edit", defaultValue: false, submitOnChange: true
                        input "delete_${deviceId}", "bool", title: "Delete", defaultValue: false, submitOnChange: true
                    }
                    paragraph "―――――――――――――――――――――――――"
                }
            }
        }
    }
}

def anyoneAwayPage() {
    dynamicPage(name: "anyoneAwayPage", title: "Anyone & Away Settings") {
        section("Anyone Home Device") {
            paragraph "Automatically created virtual device that shows 'present' when ANY individual device is present"
            input "anyoneDeviceName", "text", title: "Anyone Device Name (e.g., Anyone Home, Family Present)", 
                defaultValue: "Anyone Home", required: true
        }
        
        section("Away Delay Device") {
            paragraph "Virtual device that delays 'not present' status to prevent false departures"
            input "awayDeviceName", "text", title: "Away Device Name (e.g., Away Status, Departure Status)", 
                defaultValue: "Away Status", required: true
            input "awayDelay", "number", title: "Away Delay (minutes)", 
                defaultValue: 60, required: true, range: "1..360"
            input "awayDelayReset", "bool", title: "Reset delay if someone returns?", 
                defaultValue: true
        }
        
        section("Notifications") {
            input "notifyOnAnyoneChange", "bool", title: "Notify when Anyone status changes", 
                defaultValue: true
            input "notifyOnAwayConfirmed", "bool", title: "Notify when Away is confirmed", 
                defaultValue: true
        }
    }
}

def mqttPage() {
    dynamicPage(name: "mqttPage", title: "MQTT Settings (Required)") {
        section("MQTT Broker Configuration") {
            paragraph "<b>MQTT broker configuration is required for WiFi detection.</b>"
            input "mqttBroker", "text", title: "MQTT Broker IP (e.g., 192.168.1.100)", required: true
            input "mqttPort", "number", title: "MQTT Port", defaultValue: 1883, required: true
            input "mqttUsername", "text", title: "MQTT Username (optional)", required: false
            input "mqttPassword", "password", title: "MQTT Password (optional)", required: false
            
            if (!settings.mqttBroker) {
                paragraph "<div style='color: red;'><b>⚠️ MQTT broker IP is required. WiFi detection will be disabled.</b></div>"
            }
        }
        
        section("Topics") {
            paragraph "Will subscribe to:"
            paragraph "• AsusAC68U/status/+/lastseen/epoch"
            paragraph "• UnifiU6Pro/status/+/lastseen/epoch"
            paragraph ""
            paragraph "<b>Supported MAC Formats:</b>"
            paragraph "• mac-aa-bb-cc-dd-ee-ff"
            paragraph "• aa:bb:cc:dd:ee:ff"
            paragraph "• aa-bb-cc-dd-ee-ff"
        }
    }
}

def webhookPage() {
    dynamicPage(name: "webhookPage", title: "Webhook Settings") {
        section("GPS Webhook Endpoint") {
            paragraph "Configure your GPS app to send webhooks to:"
            paragraph "<b>${getFullApiServerUrl()}/gps/[deviceId]/[action]</b>"
            paragraph "Where [action] is 'enter' or 'exit'"
            paragraph ""
            paragraph "<b>Examples:</b>"
            paragraph "• Enter: ${getFullApiServerUrl()}/gps/phone1/enter"
            paragraph "• Exit: ${getFullApiServerUrl()}/gps/phone1/exit"
            paragraph ""
            paragraph "<b>Supported Apps:</b> Tasker, Shortcuts, IFTTT"
        }
    }
}

def installed() {
    log.info "Installing Enhanced Presence Manager"
    initialize()
}

def updated() {
    log.info "Updating Enhanced Presence Manager"
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    // Create devices if needed
    if (newDeviceName && newDeviceMAC) {
        createPresenceDevice(newDeviceName, newDeviceMAC, newDeviceGPSID)
        app.updateSetting("newDeviceName", "")
        app.updateSetting("newDeviceMAC", "")
        app.updateSetting("newDeviceGPSID", "")
    }
    
    // Handle device edits and deletions
    handleDeviceUpdates()
    
    // Create or update special devices
    createAnyoneDevice()
    createAwayDevice()
    
    // Subscribe to all individual presence devices
    def devices = getChildDevices().findAll { 
        it.deviceNetworkId.startsWith("presence-") 
    }
    
    devices.each { device ->
        subscribe(device, "presence", individualPresenceHandler)
    }
    
    // Always setup MQTT (will handle missing broker gracefully)
    setupMQTT()
    
    // Initial status update
    updateCombinedPresence()
}

def createPresenceDevice(name, mac, gpsId) {
    def dni = "presence-${mac.replaceAll(":", "").toLowerCase()}"
    def existing = getChildDevice(dni)
    
    if (!existing) {
        def device = addChildDevice("zekaizer", "WiFi GPS Hybrid Presence", dni, [
            name: name,
            label: name,
            data: [
                mac: mac,
                gpsId: gpsId
            ]
        ])
        log.info "Created presence device: ${name}"
    }
}

def createAnyoneDevice() {
    def dni = "anyone-home"
    def device = getChildDevice(dni)
    
    if (!device) {
        device = addChildDevice(
            "zekaizer", 
            "Anyone Presence Override", 
            dni, 
            [
                name: anyoneDeviceName ?: "Anyone Home",
                label: anyoneDeviceName ?: "Anyone Home"
            ]
        )
        log.info "Created Anyone Home device"
    }
}

def createAwayDevice() {
    def dni = "away-status"
    def device = getChildDevice(dni)
    
    if (!device) {
        device = addChildDevice(
            "zekaizer", 
            "Delayed Away Presence", 
            dni, 
            [
                name: awayDeviceName ?: "Away Status",
                label: awayDeviceName ?: "Away Status",
                data: [
                    delay: awayDelay ?: 60
                ]
            ]
        )
        log.info "Created Away Status device"
    }
}

// Event Handlers
def individualPresenceHandler(evt) {
    log.info "${evt.device} is ${evt.value}"
    updateCombinedPresence()
}

def updateCombinedPresence() {
    def individuals = getChildDevices().findAll { 
        it.deviceNetworkId.startsWith("presence-") 
    }
    
    // Build device status map
    def deviceMap = [:]
    individuals.each { device ->
        deviceMap[device.displayName] = (device.currentValue("presence") == "present")
    }
    
    def anyoneHome = deviceMap.any { it.value == true }
    def anyoneDevice = getChildDevice("anyone-home")
    def awayDevice = getChildDevice("away-status")
    
    if (anyoneDevice) {
        // Update tracking info
        anyoneDevice.updateIndividualTracking(deviceMap)
        
        // Update automatic presence
        anyoneDevice.updateFromIndividuals(anyoneHome)
        
        // Check current presence (might be overridden)
        def currentPresence = anyoneDevice.currentValue("presence")
        def overrideStatus = anyoneDevice.currentValue("overrideStatus")
        
        // Handle away device based on actual presence (not auto presence)
        if (currentPresence == "present" && awayDevice) {
            // Someone home (auto or manual) - cancel away timer
            unschedule(confirmAway)
            awayDevice.cancelPendingAway()
        } else if (currentPresence == "not present" && awayDevice) {
            // No one home (auto or manual) - start away timer if needed
            if (awayDevice.currentValue("presence") == "present" && 
                !awayDevice.currentValue("pendingAway")) {
                awayDevice.startAwayTimer()
                def delayMinutes = awayDelay ?: 60
                runIn(delayMinutes * 60, confirmAway)
                log.info "Started ${delayMinutes} minute away timer"
                
                if (notifyOnAnyoneChange) {
                    sendNotificationEvent("Everyone has left - away mode in ${delayMinutes} minutes")
                }
            }
        }
        
        // Log status
        if (overrideStatus != "auto") {
            log.info "Anyone device in override mode: ${overrideStatus}"
        }
    }
}

def confirmAway() {
    def awayDevice = getChildDevice("away-status")
    if (awayDevice) {
        awayDevice.confirmAway()
        log.info "Away status confirmed after delay"
        
        if (notifyOnAwayConfirmed) {
            sendNotificationEvent("House is now in Away mode")
        }
    }
}

// Legacy MQTT Handler (replaced by MQTT driver)
// def mqttMessageReceived(topic, payload) {
//     // This method is now handled by the MQTT WiFi Client driver
//     // which calls wifiDeviceDetected(mac, timestamp) directly
// }

// Webhook Handler for GPS
mappings {
    path("/gps/:deviceId/:action") {
        action: [
            GET: "handleGpsWebhook"
        ]
    }
}

def handleGpsWebhook() {
    def deviceId = params.deviceId
    def action = params.action
    
    logDebug "GPS webhook: ${deviceId} - ${action}"
    
    def device = findDeviceByGpsId(deviceId)
    if (device) {
        if (action == "enter") {
            device.gpsEntered()
        } else if (action == "exit") {
            device.gpsExited()
        }
    }
    
    return [status: "ok"]
}

// Helper methods
def findDeviceByMac(mac) {
    def devices = getChildDevices()
    return devices.find { it.data?.mac?.toLowerCase() == mac.toLowerCase() }
}

def findDeviceByGpsId(gpsId) {
    def devices = getChildDevices()
    return devices.find { it.data?.gpsId == gpsId }
}

def getStatusSummary() {
    def individuals = getChildDevices().findAll { 
        it.deviceNetworkId.startsWith("presence-") 
    }
    def anyoneDevice = getChildDevice("anyone-home")
    def awayDevice = getChildDevice("away-status")
    
    def summary = "<b>Individual Devices:</b><br>"
    individuals.each { device ->
        def presence = device.currentValue("presence")
        def method = device.currentValue("method") ?: "unknown"
        def confidence = device.currentValue("confidence") ?: 0
        summary += "• ${device.displayName}: ${presence} (${method}, ${confidence}%)<br>"
    }
    
    summary += "<br><b>Combined Status:</b><br>"
    if (anyoneDevice) {
        def overrideStatus = anyoneDevice.currentValue("overrideStatus")
        summary += "• Anyone Home: ${anyoneDevice.currentValue("presence")}"
        if (overrideStatus != "auto") {
            summary += " (${overrideStatus})"
        }
        summary += "<br>"
    }
    if (awayDevice) {
        def awayStatus = awayDevice.currentValue("presence")
        def pendingTime = awayDevice.currentValue("pendingTime")
        summary += "• Away Status: ${awayStatus}"
        if (pendingTime) {
            summary += " (pending until ${pendingTime})"
        }
        summary += "<br>"
    }
    
    // Add MQTT status
    summary += "<br><b>MQTT Status:</b><br>"
    def mqttDevice = getChildDevice("mqtt-wifi-client")
    if (mqttDevice) {
        def connectionStatus = mqttDevice.currentValue("connectionStatus") ?: "unknown"
        def messageCount = mqttDevice.currentValue("messageCount") ?: 0
        def statusColor = connectionStatus == "connected" ? "green" : 
                         connectionStatus == "error" ? "red" : "orange"
        
        summary += "• Device: Created<br>"
        summary += "• <span style='color: ${statusColor};'>Connection: ${connectionStatus}</span><br>"
        summary += "• Messages received: ${messageCount}<br>"
        
        if (connectionStatus == "connected") {
            summary += "• <span style='color: green;'>✓ WiFi detection active</span><br>"
        } else if (connectionStatus == "error") {
            summary += "• <span style='color: red;'>⚠️ Connection failed - check MQTT settings</span><br>"
        } else {
            summary += "• <span style='color: orange;'>⚠️ WiFi detection disabled - connecting...</span><br>"
        }
    } else if (settings.mqttBroker) {
        summary += "• <span style='color: orange;'>Device: Creating...</span><br>"
        summary += "• <span style='color: orange;'>Check logs for creation status</span><br>"
    } else {
        summary += "• <span style='color: red;'>MQTT broker not configured</span><br>"
        summary += "• <span style='color: red;'>⚠️ WiFi detection disabled</span><br>"
    }
    
    return summary
}

// Device management functions
def handleDeviceUpdates() {
    def devices = getChildDevices().findAll { 
        it.deviceNetworkId.startsWith("presence-") 
    }
    
    devices.each { device ->
        def deviceId = device.deviceNetworkId
        
        // Handle deletions
        if (settings["delete_${deviceId}"]) {
            log.info "Deleting device: ${device.displayName}"
            deleteChildDevice(deviceId)
            app.removeSetting("delete_${deviceId}")
            app.removeSetting("edit_${deviceId}")
            app.removeSetting("editName_${deviceId}")
            app.removeSetting("editMAC_${deviceId}")
            app.removeSetting("editGPSID_${deviceId}")
            return
        }
        
        // Handle edits
        if (settings["edit_${deviceId}"] && 
            settings["editName_${deviceId}"] && 
            settings["editMAC_${deviceId}"]) {
            
            def newName = settings["editName_${deviceId}"]
            def newMAC = settings["editMAC_${deviceId}"]
            def newGPSID = settings["editGPSID_${deviceId}"] ?: ""
            
            log.info "Updating device: ${device.displayName} -> ${newName}"
            
            // Update device label/name
            device.setLabel(newName)
            device.setName(newName)
            
            // Update device data
            device.updateDataValue("mac", newMAC)
            device.updateDataValue("gpsId", newGPSID)
            
            // Clear edit mode
            app.updateSetting("edit_${deviceId}", false)
            app.removeSetting("editName_${deviceId}")
            app.removeSetting("editMAC_${deviceId}")
            app.removeSetting("editGPSID_${deviceId}")
        }
    }
}

// MQTT Setup
def setupMQTT() {
    try {
        log.info "Setting up MQTT connection to ${settings.mqttBroker}:${settings.mqttPort ?: 1883}"
        
        if (settings.mqttBroker) {
            createOrUpdateMQTTDevice()
        } else {
            logDebug "No MQTT broker configured, skipping MQTT setup"
        }
        
    } catch (Exception e) {
        log.error "Failed to setup MQTT: ${e.message}"
    }
}

def createOrUpdateMQTTDevice() {
    def dni = "mqtt-wifi-client"
    def device = getChildDevice(dni)
    
    try {
        if (!device) {
            log.info "Creating MQTT WiFi Client device..."
            device = addChildDevice(
                "zekaizer", 
                "MQTT WiFi Client", 
                dni, 
                [
                    name: "MQTT WiFi Client",
                    label: "MQTT WiFi Client"
                ]
            )
            log.info "Successfully created MQTT WiFi Client device"
        } else {
            log.info "MQTT WiFi Client device already exists, updating settings"
        }
        
        if (device) {
            // Update device settings with error handling
            try {
                device.updateSetting("mqttBroker", settings.mqttBroker)
                device.updateSetting("mqttPort", settings.mqttPort ?: 1883)
                device.updateSetting("mqttUsername", settings.mqttUsername ?: "")
                device.updateSetting("mqttPassword", settings.mqttPassword ?: "")
                device.updateSetting("enableDebug", true)
                device.updateSetting("enableInfo", true)
                log.info "Updated MQTT device settings with logging enabled"
                
                // Give device time to process settings before refresh
                runIn(5, triggerMQTTRefresh)
                
            } catch (Exception e) {
                log.error "Failed to update MQTT device settings: ${e.message}"
            }
        } else {
            log.error "Failed to create or find MQTT device"
        }
        
    } catch (Exception e) {
        log.error "Failed to create MQTT device: ${e.message}"
        log.error "Make sure MQTT WiFi Client driver is properly installed"
    }
}

def triggerMQTTRefresh() {
    def device = getChildDevice("mqtt-wifi-client")
    if (device) {
        try {
            device.refresh()
            log.info "Triggered MQTT device refresh"
        } catch (Exception e) {
            log.error "Failed to refresh MQTT device: ${e.message}"
        }
    }
}

// WiFi Detection Handler (called by MQTT driver)
def wifiDeviceDetected(mac, timestamp) {
    logDebug "WiFi device detected: MAC=${mac}, Timestamp=${timestamp}"
    
    // Check if MQTT is properly configured
    def mqttDevice = getChildDevice("mqtt-wifi-client")
    if (!mqttDevice || mqttDevice.currentValue("connectionStatus") != "connected") {
        logDebug "MQTT not connected, ignoring WiFi detection"
        return
    }
    
    def device = findDeviceByMac(mac)
    if (device) {
        logInfo "WiFi detection for ${device.displayName} (${mac})"
        device.wifiDetected()
    } else {
        logDebug "No matching device found for MAC: ${mac}"
    }
}

def logDebug(msg) {
    if (enableDebug) log.debug msg
}