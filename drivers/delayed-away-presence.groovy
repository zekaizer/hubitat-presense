/**
 *  Delayed Away Presence Driver
 *  
 *  Virtual presence device with configurable away delay
 */
metadata {
    definition (name: "Delayed Away Presence", namespace: "custom", author: "Your Name") {
        capability "Presence Sensor"
        capability "Sensor"
        
        attribute "pendingAway", "bool"
        attribute "pendingTime", "string"
        attribute "delayMinutes", "number"
        
        command "arrived"
        command "departed"
        command "startAwayTimer"
        command "cancelPendingAway"
        command "confirmAway"
        command "setDelay", ["number"]
    }
    
    preferences {
        input "defaultDelay", "number", title: "Default Away Delay (minutes)", 
            defaultValue: 60, required: true, range: "1..360"
        input "enableDebug", "bool", title: "Enable Debug Logging", defaultValue: false
    }
}

def installed() {
    initialize()
}

def updated() {
    initialize()
}

def initialize() {
    state.pendingAway = false
    state.awayStartTime = 0
    sendEvent(name: "presence", value: "present")
    sendEvent(name: "pendingAway", value: false)
    sendEvent(name: "delayMinutes", value: defaultDelay ?: 60)
}

def arrived() {
    logDebug "Arrived - cancelling any pending away"
    
    state.pendingAway = false
    state.awayStartTime = 0
    
    sendEvent(name: "presence", value: "present")
    sendEvent(name: "pendingAway", value: false)
    sendEvent(name: "pendingTime", value: "")
    
    unschedule(confirmAwayDelayed)
}

def departed() {
    // Don't change immediately - parent app will call startAwayTimer
    logDebug "Departed called - waiting for timer start"
}

def startAwayTimer() {
    def delay = device.currentValue("delayMinutes") ?: defaultDelay ?: 60
    logInfo "Starting ${delay} minute away timer"
    
    state.pendingAway = true
    state.awayStartTime = now()
    
    def pendingUntil = new Date(now() + (delay * 60 * 1000))
    
    sendEvent(name: "pendingAway", value: true)
    sendEvent(name: "pendingTime", value: pendingUntil.format("HH:mm"))
    
    runIn(delay * 60, confirmAwayDelayed)
}

def cancelPendingAway() {
    if (state.pendingAway) {
        logInfo "Cancelling pending away status"
        
        state.pendingAway = false
        state.awayStartTime = 0
        
        sendEvent(name: "pendingAway", value: false)
        sendEvent(name: "pendingTime", value: "")
        
        unschedule(confirmAwayDelayed)
    }
}

def confirmAway() {
    logInfo "Confirming away status"
    
    state.pendingAway = false
    
    sendEvent(name: "presence", value: "not present")
    sendEvent(name: "pendingAway", value: false)
    sendEvent(name: "pendingTime", value: "")
}

def confirmAwayDelayed() {
    // Double check no one has returned
    if (state.pendingAway) {
        confirmAway()
    }
}

def setDelay(minutes) {
    logInfo "Setting away delay to ${minutes} minutes"
    sendEvent(name: "delayMinutes", value: minutes)
}

def logDebug(msg) {
    if (enableDebug) log.debug msg
}

def logInfo(msg) {
    log.info msg
}