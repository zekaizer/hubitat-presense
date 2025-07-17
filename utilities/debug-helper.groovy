/**
 * Debug Helper for Hubitat Presence System
 * 
 * Provides debugging utilities and diagnostic tools
 */

// Debug logging utilities
class DebugHelper {
    
    static void logPresenceState(device) {
        println "=== Presence State Debug ==="
        println "Device: ${device.displayName}"
        println "Presence: ${device.currentValue('presence')}"
        println "WiFi Status: ${device.currentValue('wifiStatus')}"
        println "GPS Status: ${device.currentValue('gpsStatus')}"
        println "Confidence: ${device.currentValue('confidence')}%"
        println "Method: ${device.currentValue('method')}"
        println "Last WiFi: ${device.currentValue('lastWifiSeen')}"
        println "Last GPS: ${device.currentValue('lastGpsUpdate')}"
        println "=========================="
    }
    
    static void logDeviceState(device) {
        println "=== Device State Debug ==="
        println "Device: ${device.displayName}"
        println "State variables:"
        device.getState().each { key, value ->
            println "  ${key}: ${value}"
        }
        println "=========================="
    }
    
    static void logSystemStatus(app) {
        println "=== System Status Debug ==="
        
        def individuals = app.getChildDevices().findAll { 
            it.deviceNetworkId.startsWith("presence-") 
        }
        
        def anyoneDevice = app.getChildDevice("anyone-home")
        def awayDevice = app.getChildDevice("away-status")
        
        println "Individual Devices (${individuals.size()}):"
        individuals.each { device ->
            println "  - ${device.displayName}: ${device.currentValue('presence')}"
        }
        
        if (anyoneDevice) {
            println "Anyone Device: ${anyoneDevice.currentValue('presence')}"
            println "  Override: ${anyoneDevice.currentValue('overrideStatus')}"
            println "  Auto State: ${anyoneDevice.currentValue('autoPresence')}"
        }
        
        if (awayDevice) {
            println "Away Device: ${awayDevice.currentValue('presence')}"
            println "  Pending: ${awayDevice.currentValue('pendingAway')}"
            println "  Pending Time: ${awayDevice.currentValue('pendingTime')}"
        }
        
        println "=========================="
    }
}

// Diagnostic utilities
class DiagnosticHelper {
    
    static Map runDiagnostics(device) {
        def diagnostics = [:]
        
        // Check basic connectivity
        diagnostics.basic = checkBasicConnectivity(device)
        
        // Check timing issues
        diagnostics.timing = checkTimingIssues(device)
        
        // Check state consistency
        diagnostics.state = checkStateConsistency(device)
        
        // Check configuration
        diagnostics.config = checkConfiguration(device)
        
        return diagnostics
    }
    
    static Map checkBasicConnectivity(device) {
        def result = [:]
        
        // Check WiFi connectivity
        def wifiStatus = device.currentValue('wifiStatus')
        def lastWifiSeen = device.currentValue('lastWifiSeen')
        
        result.wifi = [
            status: wifiStatus,
            recent: lastWifiSeen ? isTimestampRecent(lastWifiSeen, 300) : false,
            lastSeen: lastWifiSeen
        ]
        
        // Check GPS connectivity
        def gpsStatus = device.currentValue('gpsStatus')
        def lastGpsUpdate = device.currentValue('lastGpsUpdate')
        
        result.gps = [
            status: gpsStatus,
            recent: lastGpsUpdate ? isTimestampRecent(lastGpsUpdate, 300) : false,
            lastUpdate: lastGpsUpdate
        ]
        
        return result
    }
    
    static Map checkTimingIssues(device) {
        def result = [:]
        
        // Check for timing mismatches
        def presence = device.currentValue('presence')
        def wifiStatus = device.currentValue('wifiStatus')
        def gpsStatus = device.currentValue('gpsStatus')
        
        result.presenceWifiMismatch = (presence == 'present' && wifiStatus == 'disconnected')
        result.presenceGpsMismatch = (presence == 'not present' && gpsStatus == 'inside')
        
        return result
    }
    
    static Map checkStateConsistency(device) {
        def result = [:]
        
        // Check state variable consistency
        def deviceState = device.getState()
        def presence = device.currentValue('presence')
        
        result.statePresenceMatch = (deviceState.presence == presence)
        result.wifiLastSeenValid = (deviceState.wifiLastSeen != null)
        result.gpsInsideValid = (deviceState.gpsInside != null)
        
        return result
    }
    
    static Map checkConfiguration(device) {
        def result = [:]
        
        // Check configuration values
        result.wifiTimeout = device.getSetting('wifiTimeout') ?: 180
        result.gpsExitDelay = device.getSetting('gpsExitDelay') ?: 120
        result.arrivalDelay = device.getSetting('arrivalDelay') ?: 0
        result.debugEnabled = device.getSetting('enableDebug') ?: false
        
        return result
    }
    
    static boolean isTimestampRecent(timestamp, maxAgeSeconds) {
        try {
            def eventTime = Date.parse("yyyy-MM-dd HH:mm:ss", timestamp)
            def now = new Date()
            def ageSeconds = (now.time - eventTime.time) / 1000
            return ageSeconds <= maxAgeSeconds
        } catch (Exception e) {
            return false
        }
    }
}

// Troubleshooting utilities
class TroubleshootingHelper {
    
    static List<String> diagnosePresenceIssues(device) {
        def issues = []
        
        def diagnostics = DiagnosticHelper.runDiagnostics(device)
        
        // Check for common issues
        if (!diagnostics.basic.wifi.recent) {
            issues.add("WiFi not detected recently - check network connectivity")
        }
        
        if (!diagnostics.basic.gps.recent) {
            issues.add("GPS not updated recently - check GPS app configuration")
        }
        
        if (diagnostics.timing.presenceWifiMismatch) {
            issues.add("Present but WiFi disconnected - possible timing issue")
        }
        
        if (diagnostics.timing.presenceGpsMismatch) {
            issues.add("Away but GPS inside - possible GPS delay issue")
        }
        
        if (!diagnostics.state.statePresenceMatch) {
            issues.add("State variable mismatch - consider device refresh")
        }
        
        // Check configuration issues
        if (diagnostics.config.wifiTimeout > 300) {
            issues.add("WiFi timeout too long - may cause delayed departures")
        }
        
        if (diagnostics.config.gpsExitDelay < 60) {
            issues.add("GPS exit delay too short - may cause false departures")
        }
        
        return issues
    }
    
    static List<String> getRecommendations(device) {
        def recommendations = []
        
        def diagnostics = DiagnosticHelper.runDiagnostics(device)
        
        // Performance recommendations
        if (diagnostics.config.wifiTimeout > 180) {
            recommendations.add("Consider reducing WiFi timeout to 180 seconds for faster response")
        }
        
        if (diagnostics.config.arrivalDelay > 0) {
            recommendations.add("Arrival delay can be removed for faster presence detection")
        }
        
        // Reliability recommendations
        if (!diagnostics.config.debugEnabled) {
            recommendations.add("Enable debug logging to troubleshoot issues")
        }
        
        recommendations.add("Test presence detection in controlled environment")
        recommendations.add("Monitor logs for timing patterns")
        recommendations.add("Verify MQTT and webhook configurations")
        
        return recommendations
    }
}

// Performance monitoring
class PerformanceMonitor {
    
    static Map measurePerformance(device, action) {
        def startTime = System.currentTimeMillis()
        
        // Execute action
        try {
            device."$action"()
        } catch (Exception e) {
            return [
                success: false,
                error: e.message,
                duration: System.currentTimeMillis() - startTime
            ]
        }
        
        def endTime = System.currentTimeMillis()
        
        return [
            success: true,
            duration: endTime - startTime,
            timestamp: new Date().toString()
        ]
    }
    
    static void logPerformanceMetrics(device) {
        println "=== Performance Metrics ==="
        
        def metrics = [
            'wifiDetected': measurePerformance(device, 'wifiDetected'),
            'gpsEntered': measurePerformance(device, 'gpsEntered'),
            'checkPresenceState': measurePerformance(device, 'checkPresenceState'),
            'refresh': measurePerformance(device, 'refresh')
        ]
        
        metrics.each { action, result ->
            println "${action}: ${result.success ? result.duration + 'ms' : 'FAILED - ' + result.error}"
        }
        
        println "=========================="
    }
}

// Test data injection
class TestDataInjector {
    
    static void injectWifiDetection(device, timestamp = null) {
        timestamp = timestamp ?: System.currentTimeMillis()
        
        // Simulate WiFi detection
        device.state.wifiLastSeen = timestamp
        device.sendEvent(name: "wifiStatus", value: "connected")
        device.sendEvent(name: "lastWifiSeen", value: new Date(timestamp).format("yyyy-MM-dd HH:mm:ss"))
        
        println "Injected WiFi detection at ${new Date(timestamp)}"
    }
    
    static void injectGpsEvent(device, action, timestamp = null) {
        timestamp = timestamp ?: System.currentTimeMillis()
        
        if (action == "enter") {
            device.state.gpsInside = true
            device.sendEvent(name: "gpsStatus", value: "inside")
        } else if (action == "exit") {
            device.state.gpsInside = false
            device.sendEvent(name: "gpsStatus", value: "outside")
        }
        
        device.state.gpsLastChange = timestamp
        device.sendEvent(name: "lastGpsUpdate", value: new Date(timestamp).format("yyyy-MM-dd HH:mm:ss"))
        
        println "Injected GPS ${action} at ${new Date(timestamp)}"
    }
    
    static void simulateScenario(device, scenario) {
        println "Simulating scenario: ${scenario}"
        
        switch (scenario) {
            case "arrival":
                injectWifiDetection(device)
                Thread.sleep(1000)
                injectGpsEvent(device, "enter")
                break
                
            case "departure":
                injectGpsEvent(device, "exit")
                Thread.sleep(2000)
                // WiFi timeout simulation
                device.state.wifiLastSeen = System.currentTimeMillis() - (200 * 1000)
                device.sendEvent(name: "wifiStatus", value: "disconnected")
                break
                
            case "false_departure":
                injectGpsEvent(device, "exit")
                Thread.sleep(1000)
                injectWifiDetection(device) // WiFi still connected
                break
        }
    }
}