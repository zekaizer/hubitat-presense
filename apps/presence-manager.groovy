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
            href "mqttPage", title: "MQTT Settings", description: "Configure WiFi detection via MQTT"
            href "webhookPage", title: "Webhook Settings", description: "Configure GPS webhooks"
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
            input "newDeviceName", "text", title: "Device Name", submitOnChange: true
            input "newDeviceMAC", "text", title: "WiFi MAC Address", submitOnChange: true
            input "newDeviceGPSID", "text", title: "GPS Device ID", submitOnChange: true
            
            if (newDeviceName && newDeviceMAC) {
                paragraph "Device will be created when you press Done"
            }
        }
        
        section("Existing Devices") {
            def devices = getChildDevices().findAll { 
                it.deviceNetworkId.startsWith("presence-")
            }
            devices.each { device ->
                paragraph "${device.displayName} - ${device.deviceNetworkId}"
            }
        }
    }
}

def anyoneAwayPage() {
    dynamicPage(name: "anyoneAwayPage", title: "Anyone & Away Settings") {
        section("Anyone Home Device") {
            paragraph "Automatically created virtual device that shows 'present' when ANY individual device is present"
            input "anyoneDeviceName", "text", title: "Anyone Device Name", 
                defaultValue: "Anyone Home", required: true
        }
        
        section("Away Delay Device") {
            paragraph "Virtual device that delays 'not present' status to prevent false departures"
            input "awayDeviceName", "text", title: "Away Device Name", 
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
    dynamicPage(name: "mqttPage", title: "MQTT Settings") {
        section("MQTT Broker") {
            input "mqttBroker", "text", title: "MQTT Broker IP", required: false
            input "mqttPort", "number", title: "MQTT Port", defaultValue: 1883, required: false
            input "mqttUsername", "text", title: "MQTT Username", required: false
            input "mqttPassword", "password", title: "MQTT Password", required: false
        }
        
        section("Topics") {
            paragraph "Will subscribe to:"
            paragraph "• AsusAC68U/status/+/lastseen/epoch"
            paragraph "• UnifiU6Pro/status/+/lastseen/epoch"
        }
    }
}

def webhookPage() {
    dynamicPage(name: "webhookPage", title: "Webhook Settings") {
        section("GPS Webhook Endpoint") {
            paragraph "Configure your GPS app to send webhooks to:"
            paragraph "<b>${getFullApiServerUrl()}/gps/[deviceId]/[action]</b>"
            paragraph "Where [action] is 'enter' or 'exit'"
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
    
    // Subscribe to MQTT if configured
    if (settings.mqttBroker) {
        subscribeMQTT()
    }
    
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

// MQTT Handler
def mqttMessageReceived(topic, payload) {
    logDebug "MQTT message: ${topic} = ${payload}"
    
    // Parse MAC from topic
    def mac = parseMacFromTopic(topic)
    if (mac) {
        def device = findDeviceByMac(mac)
        if (device) {
            device.wifiDetected()
        }
    }
}

def parseMacFromTopic(topic) {
    // Extract MAC from topic: "AsusAC68U/status/mac-XX-XX-XX/lastseen/epoch"
    def matcher = topic =~ /mac-([0-9a-fA-F-]+)/
    if (matcher) {
        return matcher[0][1].replace('-', ':')
    }
    return null
}

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
    
    return summary
}

def logDebug(msg) {
    if (enableDebug) log.debug msg
}