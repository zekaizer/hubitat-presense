# Integration Guide

## Overview

This guide provides detailed instructions for integrating the Hubitat WiFi/GPS Hybrid Presence Detection System with external systems and services.

## MQTT Integration (WiFi Detection)

### Supported Router Types

#### ASUS Routers
- **Model**: AC68U, AC86U, AX88U series
- **Firmware**: AsusWRT-Merlin recommended
- **Topic Format**: `AsusAC68U/status/mac-{mac}/lastseen/epoch`

#### Ubiquiti UniFi
- **Model**: Dream Machine, USG, CloudKey
- **Controller**: UniFi Network Controller
- **Topic Format**: `UnifiU6Pro/status/mac-{mac}/lastseen/epoch`

### MQTT Broker Setup

#### Option 1: Mosquitto (Recommended)
```bash
# Install Mosquitto
sudo apt-get install mosquitto mosquitto-clients

# Configure mosquitto.conf
listener 1883
allow_anonymous true
# Or with authentication:
# allow_anonymous false
# password_file /etc/mosquitto/passwd

# Start service
sudo systemctl start mosquitto
sudo systemctl enable mosquitto
```

#### Option 2: Home Assistant MQTT Add-on
1. Install MQTT Broker add-on in Home Assistant
2. Configure username/password
3. Start the add-on
4. Note IP address and port (default: 1883)

### Router Configuration

#### ASUS Router Setup
1. **Enable MQTT in router firmware**:
   ```bash
   # SSH into router
   ssh admin@192.168.1.1
   
   # Install MQTT publisher script
   wget https://raw.githubusercontent.com/user/asus-mqtt/master/install.sh
   chmod +x install.sh
   ./install.sh
   ```

2. **Configure MQTT settings**:
   ```bash
   # Edit configuration
   vi /opt/mqtt/config.conf
   
   # Add broker settings
   MQTT_BROKER=192.168.1.100
   MQTT_PORT=1883
   MQTT_USERNAME=hubitat
   MQTT_PASSWORD=password
   TOPIC_PREFIX=AsusAC68U/status
   ```

#### UniFi Controller Setup
1. **Enable MQTT in UniFi Controller**:
   - Login to UniFi Controller
   - Go to Settings > System > Advanced
   - Enable "MQTT" under Integrations
   - Configure broker settings

2. **Configure device tracking**:
   - Go to Settings > WiFi > WiFi Groups
   - Enable "Client Device Tracking"
   - Set tracking interval to 30 seconds

### Hubitat MQTT Configuration

#### App Configuration
1. **Navigate to MQTT Settings**:
   - Open Hubitat web interface
   - Go to Apps > WiFi GPS Presence Manager
   - Click "MQTT Settings"

2. **Configure Broker Settings**:
   ```
   MQTT Broker IP: 192.168.1.100
   MQTT Port: 1883
   MQTT Username: hubitat
   MQTT Password: password
   ```

3. **Verify Connection**:
   - Check app logs for connection status
   - Test with known device MAC address

#### Topic Patterns
The system subscribes to these topic patterns:
```
AsusAC68U/status/+/lastseen/epoch
UnifiU6Pro/status/+/lastseen/epoch
```

MAC addresses in topics should be formatted as:
- `mac-aa-bb-cc-dd-ee-ff` (dashes)
- System converts to `aa:bb:cc:dd:ee:ff` (colons)

### Testing MQTT Integration

#### Using Mosquitto Tools
```bash
# Test publish
mosquitto_pub -h 192.168.1.100 -t "AsusAC68U/status/mac-aa-bb-cc-dd-ee-ff/lastseen/epoch" -m "1640995200"

# Test subscribe
mosquitto_sub -h 192.168.1.100 -t "AsusAC68U/status/+/lastseen/epoch"
```

#### Using MQTT Explorer
1. Download MQTT Explorer application
2. Connect to broker
3. Subscribe to `AsusAC68U/status/+/lastseen/epoch`
4. Publish test message to verify device detection

---

## GPS Integration (Webhooks)

### Supported GPS Apps

#### Tasker (Android)
1. **Create Location Profile**:
   ```
   Profile: Home Location
   Context: Location > Your Home Address
   Radius: 100 meters
   ```

2. **Configure Enter Task**:
   ```
   Task: GPS Enter
   Action: HTTP Request
   Method: GET
   URL: http://hubitat-ip/apps/api/app-id/gps/phone1/enter
   ```

3. **Configure Exit Task**:
   ```
   Task: GPS Exit
   Action: HTTP Request
   Method: GET
   URL: http://hubitat-ip/apps/api/app-id/gps/phone1/exit
   ```

#### Shortcuts (iOS)
1. **Create Enter Shortcut**:
   - Open Shortcuts app
   - Create new automation
   - Trigger: Arrive at location
   - Action: Get Contents of URL
   - URL: `http://hubitat-ip/apps/api/app-id/gps/phone1/enter`

2. **Create Exit Shortcut**:
   - Open Shortcuts app
   - Create new automation
   - Trigger: Leave location
   - Action: Get Contents of URL
   - URL: `http://hubitat-ip/apps/api/app-id/gps/phone1/exit`

#### IFTTT Integration
1. **Create Enter Applet**:
   - Trigger: Android Location > You enter an area
   - Action: Webhooks > Make a web request
   - URL: `http://hubitat-ip/apps/api/app-id/gps/phone1/enter`
   - Method: GET

2. **Create Exit Applet**:
   - Trigger: Android Location > You exit an area
   - Action: Webhooks > Make a web request
   - URL: `http://hubitat-ip/apps/api/app-id/gps/phone1/exit`
   - Method: GET

### Webhook Configuration

#### Getting Webhook URLs
1. **Access Hubitat App**:
   - Open WiFi GPS Presence Manager
   - Go to "Webhook Settings"
   - Note the base URL format

2. **URL Structure**:
   ```
   Base URL: http://hubitat-ip/apps/api/app-id/gps/
   Full URL: http://hubitat-ip/apps/api/app-id/gps/{deviceId}/{action}
   
   Examples:
   - Enter: http://192.168.1.50/apps/api/123/gps/phone1/enter
   - Exit: http://192.168.1.50/apps/api/123/gps/phone1/exit
   ```

#### Security Considerations
1. **Local Network Only**: Webhooks work only on local network
2. **No Authentication**: URLs are unprotected for simplicity
3. **Firewall**: Ensure Hubitat hub is not exposed to internet

### Testing GPS Integration

#### Manual Testing
```bash
# Test enter event
curl "http://192.168.1.50/apps/api/123/gps/phone1/enter"

# Test exit event
curl "http://192.168.1.50/apps/api/123/gps/phone1/exit"
```

#### Expected Response
```json
{
  "status": "ok"
}
```

---

## Home Assistant Integration

### MQTT Discovery
Home Assistant can automatically discover presence devices:

#### Configuration
```yaml
# configuration.yaml
mqtt:
  discovery: true
  discovery_prefix: homeassistant

# Optional: Manual device configuration
binary_sensor:
  - platform: mqtt
    name: "John's Phone Presence"
    state_topic: "hubitat/presence/phone1"
    payload_on: "present"
    payload_off: "not present"
    device_class: presence
```

### Webhook Integration
Use Home Assistant webhook automation:

```yaml
# automations.yaml
- id: presence_enter
  alias: "GPS Enter"
  trigger:
    platform: webhook
    webhook_id: gps_enter_phone1
  action:
    service: rest_command.hubitat_gps_enter
    data:
      device_id: phone1

rest_command:
  hubitat_gps_enter:
    url: "http://hubitat-ip/apps/api/app-id/gps/{{ device_id }}/enter"
    method: GET
```

---

## Rule Machine Integration

### Creating Presence Rules

#### Basic Presence Rule
```
Rule: Arrival Actions
Trigger: John's Phone Presence changes to present
Actions:
- Turn on lights
- Set thermostat to 72°F
- Send notification
```

#### Combined Presence Rule
```
Rule: Everyone Left
Trigger: Anyone Home changes to not present
Conditions: Away Status is not present
Actions:
- Turn off all lights
- Set thermostat to away mode
- Arm security system
```

#### Advanced Presence Rule
```
Rule: Delayed Away Actions
Trigger: Away Status changes to not present
Actions:
- Wait 5 minutes
- IF Anyone Home is still not present:
  - Turn off all devices
  - Lock doors
  - Send away notification
```

### Presence Attributes in Rules

#### Available Attributes
- `presence`: present/not present
- `confidence`: 0-100 percentage
- `method`: detection method (wifi, gps, manual)
- `wifiStatus`: connected/disconnected
- `gpsStatus`: inside/outside

#### Example Rule with Attributes
```
Rule: High Confidence Arrival
Trigger: John's Phone Presence changes to present
Conditions: 
- John's Phone confidence > 80
- John's Phone method is wifi
Actions:
- Turn on lights immediately
```

---

## Dashboard Integration

### Hubitat Dashboard Tiles

#### Presence Tile
```
Device: John's Phone Presence
Tile Type: Presence
Template: Standard
Color: Green (present), Red (not present)
```

#### Switch Tile (Anyone Device)
```
Device: Anyone Home
Tile Type: Switch
Template: Switch
Label: Manual Override
```

#### Attribute Tiles
```
Device: John's Phone Presence
Tile Type: Attribute
Attribute: confidence
Template: Standard
Label: Confidence
```

### Custom Dashboard CSS
```css
/* Presence tile styling */
.tile.presence[data-value="present"] {
    background-color: #4CAF50 !important;
}

.tile.presence[data-value="not present"] {
    background-color: #f44336 !important;
}

/* Confidence tile styling */
.tile.attribute .tile-title {
    font-size: 14px;
}

.tile.attribute .tile-contents {
    font-size: 24px;
    font-weight: bold;
}
```

---

## Advanced Integrations

### Node-RED Integration

#### MQTT Flow
```json
[
  {
    "id": "mqtt-in",
    "type": "mqtt in",
    "topic": "AsusAC68U/status/+/lastseen/epoch",
    "broker": "mqtt-broker"
  },
  {
    "id": "process-presence",
    "type": "function",
    "func": "// Process MQTT presence data\nvar mac = msg.topic.split('/')[2].replace('mac-', '').replace(/-/g, ':');\nvar timestamp = parseInt(msg.payload);\nvar isRecent = (Date.now() - timestamp * 1000) < 180000; // 3 minutes\n\nmsg.payload = {\n  mac: mac,\n  present: isRecent,\n  timestamp: timestamp\n};\n\nreturn msg;"
  }
]
```

### Grafana Monitoring

#### Data Source Configuration
```yaml
# influxdb.conf
[[influxdb]]
  name = "presence_data"
  url = "http://localhost:8086"
  database = "hubitat"
  username = "admin"
  password = "password"
```

#### Sample Queries
```sql
-- Presence over time
SELECT presence FROM presence_events WHERE device = 'phone1' AND time > now() - 1d

-- Confidence trends
SELECT mean(confidence) FROM presence_events WHERE time > now() - 1d GROUP BY time(1h)

-- Detection method distribution
SELECT count(*) FROM presence_events WHERE time > now() - 1d GROUP BY method
```

---

## Troubleshooting

### Common Issues

#### MQTT Connection Problems
1. **Check broker status**:
   ```bash
   sudo systemctl status mosquitto
   ```

2. **Verify network connectivity**:
   ```bash
   ping mqtt-broker-ip
   telnet mqtt-broker-ip 1883
   ```

3. **Check logs**:
   ```bash
   tail -f /var/log/mosquitto/mosquitto.log
   ```

#### GPS Webhook Issues
1. **Verify URL format**:
   - Check app ID in URL
   - Ensure device ID matches configuration
   - Verify action is "enter" or "exit"

2. **Test network connectivity**:
   ```bash
   curl -v "http://hubitat-ip/apps/api/app-id/gps/device1/enter"
   ```

3. **Check Hubitat logs**:
   - Go to Logs in Hubitat interface
   - Look for GPS webhook messages

#### Device Detection Problems
1. **MAC address format**:
   - Ensure MAC addresses match exactly
   - Check for case sensitivity
   - Verify dash vs colon formatting

2. **Timing issues**:
   - Check WiFi timeout settings
   - Verify GPS delay settings
   - Review confidence thresholds

### Debug Mode
Enable debug logging in all components:
1. Individual drivers: Set "Enable Debug Logging" to true
2. Manager app: Set "Enable Debug Logging" to true
3. Monitor logs for detailed event information

### Performance Optimization
1. **MQTT message rate**: Limit to once per minute per device
2. **GPS polling**: Use geofence events, not continuous polling
3. **Log management**: Disable debug logging in production
4. **Device limits**: Recommend maximum 10 devices per system

---

## Security Best Practices

### Network Security
1. **Isolate IoT network**: Use separate VLAN for IoT devices
2. **Firewall rules**: Block unnecessary outbound connections
3. **Regular updates**: Keep firmware updated on all devices

### MQTT Security
1. **Authentication**: Use username/password authentication
2. **TLS encryption**: Enable TLS for MQTT connections
3. **Topic ACLs**: Restrict topic access per client

### Webhook Security
1. **Local network only**: Never expose webhooks to internet
2. **Rate limiting**: Implement rate limiting on webhook endpoints
3. **Input validation**: Validate all webhook parameters

### Device Security
1. **MAC randomization**: Be aware of device MAC randomization
2. **Device rotation**: Update device IDs periodically
3. **Monitoring**: Monitor for unusual presence patterns