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
            def mqttStatus = settings.mqttBroker ? "✓ 설정됨" : "❌ 필수 설정"
            href "mqttPage", title: "MQTT Settings (필수)", description: "WiFi 감지용 MQTT 브로커 설정 - ${mqttStatus}"
            href "webhookPage", title: "Webhook Settings", description: "GPS 지오펜싱 웹훅 설정"
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
            input "newDeviceName", "text", title: "Device Name (예: John's Phone)", submitOnChange: true
            input "newDeviceMAC", "text", title: "WiFi MAC Address (예: AA:BB:CC:DD:EE:FF)", submitOnChange: true
            input "newDeviceGPSID", "text", title: "GPS Device ID (예: phone1, iphone_john)", submitOnChange: true
            
            if (newDeviceName && newDeviceMAC) {
                paragraph "✓ Device will be created when you press Done"
            }
            
            paragraph "<b>형식 안내:</b>"
            paragraph "• Device Name: 기기를 식별할 이름"
            paragraph "• MAC Address: 콜론(:) 구분, 대소문자 무관"
            paragraph "• GPS Device ID: 영문/숫자, 공백 없이 입력"
        }
        
        section("Existing Devices") {
            def devices = getChildDevices().findAll { 
                it.deviceNetworkId.startsWith("presence-")
            }
            
            if (devices.size() == 0) {
                paragraph "생성된 디바이스가 없습니다."
            } else {
                devices.each { device ->
                    def deviceId = device.deviceNetworkId
                    def isEditing = settings["edit_${deviceId}"]
                    
                    if (isEditing) {
                        // Edit mode
                        input "edit_${deviceId}", "bool", title: "편집 모드", defaultValue: true, submitOnChange: true
                        input "editName_${deviceId}", "text", title: "Device Name", 
                            defaultValue: device.displayName, submitOnChange: true
                        input "editMAC_${deviceId}", "text", title: "WiFi MAC Address (예: AA:BB:CC:DD:EE:FF)", 
                            defaultValue: device.data?.mac ?: "", submitOnChange: true
                        input "editGPSID_${deviceId}", "text", title: "GPS Device ID (예: phone1, iphone_john)", 
                            defaultValue: device.data?.gpsId ?: "", submitOnChange: true
                        
                        if (settings["editName_${deviceId}"] && settings["editMAC_${deviceId}"]) {
                            paragraph "✓ 변경사항이 저장됩니다"
                        }
                    } else {
                        // View mode
                        def mac = device.data?.mac ?: "설정되지 않음"
                        def gpsId = device.data?.gpsId ?: "설정되지 않음"
                        paragraph "<b>${device.displayName}</b><br>MAC: ${mac}<br>GPS ID: ${gpsId}"
                        input "edit_${deviceId}", "bool", title: "편집", defaultValue: false, submitOnChange: true
                        input "delete_${deviceId}", "bool", title: "삭제", defaultValue: false, submitOnChange: true
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
            input "anyoneDeviceName", "text", title: "Anyone Device Name (예: Anyone Home, 가족 재실)", 
                defaultValue: "Anyone Home", required: true
        }
        
        section("Away Delay Device") {
            paragraph "Virtual device that delays 'not present' status to prevent false departures"
            input "awayDeviceName", "text", title: "Away Device Name (예: Away Status, 외출 상태)", 
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
    dynamicPage(name: "mqttPage", title: "MQTT Settings (필수)") {
        section("MQTT Broker 설정") {
            paragraph "<b>WiFi 감지를 위해 MQTT 브로커 설정이 필요합니다.</b>"
            input "mqttBroker", "text", title: "MQTT Broker IP (예: 192.168.1.100)", required: true
            input "mqttPort", "number", title: "MQTT Port", defaultValue: 1883, required: true
            input "mqttUsername", "text", title: "MQTT Username (선택사항)", required: false
            input "mqttPassword", "password", title: "MQTT Password (선택사항)", required: false
            
            if (!settings.mqttBroker) {
                paragraph "<div style='color: red;'><b>⚠️ MQTT 브로커 IP가 필요합니다. WiFi 감지 기능이 비활성화됩니다.</b></div>"
            }
        }
        
        section("Topics") {
            paragraph "Will subscribe to:"
            paragraph "• AsusAC68U/status/mac-+/lastseen/epoch"
            paragraph "• UnifiU6Pro/status/mac-+/lastseen/epoch"
            paragraph ""
            paragraph "<b>MAC 형식:</b> mac-aa-bb-cc-dd-ee-ff"
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
            paragraph "<b>예시:</b>"
            paragraph "• Enter: ${getFullApiServerUrl()}/gps/phone1/enter"
            paragraph "• Exit: ${getFullApiServerUrl()}/gps/phone1/exit"
            paragraph ""
            paragraph "<b>지원 앱:</b> Tasker, Shortcuts, IFTTT"
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
    
    // Subscribe to MQTT if configured
    if (settings.mqttBroker) {
        setupMQTT()
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
        
        summary += "• <span style='color: ${statusColor};'>Connection: ${connectionStatus}</span><br>"
        summary += "• Messages received: ${messageCount}<br>"
        
        if (connectionStatus != "connected") {
            summary += "• <span style='color: red;'>⚠️ WiFi 감지 기능이 비활성화됨</span><br>"
        }
    } else if (settings.mqttBroker) {
        summary += "• <span style='color: orange;'>MQTT 클라이언트 생성 중...</span><br>"
    } else {
        summary += "• <span style='color: red;'>MQTT 브로커가 설정되지 않음</span><br>"
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
    
    if (!device) {
        device = addChildDevice(
            "zekaizer", 
            "MQTT WiFi Client", 
            dni, 
            [
                name: "MQTT WiFi Client",
                label: "MQTT WiFi Client"
            ]
        )
        log.info "Created MQTT WiFi Client device"
    }
    
    // Update device settings
    device.updateSetting("mqttBroker", settings.mqttBroker)
    device.updateSetting("mqttPort", settings.mqttPort ?: 1883)
    device.updateSetting("mqttUsername", settings.mqttUsername ?: "")
    device.updateSetting("mqttPassword", settings.mqttPassword ?: "")
    
    // Trigger device refresh to connect
    device.refresh()
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