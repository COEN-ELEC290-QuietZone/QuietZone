#include <Arduino.h>
#include <WiFi.h>
#include "SoundSensor.h"
#include "MQTTClient.h" // Commented out until Raspberry Pi is available

// Create instances
SoundSensor soundSensor;
// MQTTClientManager mqttManager; // Commented out until Raspberry Pi is available

namespace
{
    constexpr bool kSetupModeEnabled = true;
    const char *kSetupPassword = "config123";

    String buildSetupSsid()
    {
        String mac = WiFi.macAddress();
        mac.replace(":", "");

        int idStart = mac.length() >= 4 ? mac.length() - 4 : 0;
        String id = mac.substring(idStart);
        id.toUpperCase();

        return "IOT_ESP_" + id;
    }

    void startSetupHotspot()
    {
        WiFi.mode(WIFI_MODE_APSTA);
        String ssid = buildSetupSsid();
        bool started = WiFi.softAP(ssid.c_str(), kSetupPassword);

        if (started)
        {
            Serial.println("[SETUP] Hotspot started: " + ssid);
            Serial.println("[SETUP] AP IP: " + WiFi.softAPIP().toString());
        }
        else
        {
            Serial.println("[SETUP][ERROR] Failed to start setup hotspot");
        }
    }
} // namespace

void setup()
{
    Serial.begin(115200);
    delay(1000);

    Serial.println("[INFO] QuietZone Sensor Starting...");

    // Initialize sound sensor
    soundSensor.begin();

    // Initialize MQTT connection
    // mqttManager.begin();  // Commented out until Raspberry Pi is available

    if (kSetupModeEnabled)
    {
        startSetupHotspot();
    }

    Serial.println("[INFO] Sound sensor initialized!");
    Serial.println("Format: Sound Level (dB) with debug info");
}

void loop()
{
    // Maintain MQTT connection
    // mqttManager.maintainConnection();  // Commented out until Raspberry Pi is available

    // Read sound level and status
    float soundLevel = soundSensor.readSoundLevel();
    String soundStatus = soundSensor.getStatus();

    // Print detailed debug information
    soundSensor.printDebugInfo();

    // Print simple sound level for monitoring
    Serial.println("Simple Sound Level: " + String(soundLevel, 1) + " dB, Status: " + soundStatus);

    // Publish to MQTT if connected and interval elapsed
    // if (mqttManager.isConnected() && mqttManager.shouldPublish())
    //  {
    //      if (mqttManager.publishSoundData(soundLevel, soundStatus))
    //      {
    //          Serial.println("[MQTT] Data published: Sensor 1, " + String(soundLevel, 1) + " dB, Status: " + soundStatus);
    //      }
    //      mqttManager.updateLastPublish();
    //  }

    Serial.println("---");
    delay(500); // Update every 500ms for easier reading
}