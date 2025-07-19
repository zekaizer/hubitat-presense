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
        
        command "present"
        command "not_present"
        command "arrive"
        command "depart"
    }
    
    preferences {
        section("Settings") {
            input "debugLogging", "bool", title: "Enable Debug Logging", defaultValue: false, required: false
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
}

def parse(String description) {
    if (debugLogging) log.debug "Parsing: ${description}"
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