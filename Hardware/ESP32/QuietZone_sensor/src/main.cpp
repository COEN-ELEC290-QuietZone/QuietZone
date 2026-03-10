#include <Arduino.h>
#include "SoundSensor.h"
#include "MQTTClient.h"  // Commented out until Raspberry Pi is available

// Create instances
SoundSensor soundSensor;
MQTTClientManager mqttManager;  // Commented out until Raspberry Pi is available

void setup()
{
    Serial.begin(115200);
    delay(1000);

    Serial.println("[INFO] QuietZone Sensor Starting...");

    // Initialize sound sensor
    soundSensor.begin();

    // Initialize MQTT connection
    mqttManager.begin();  // Commented out until Raspberry Pi is available

    Serial.println("[INFO] Sound sensor initialized!");
    Serial.println("Format: Sound Level (dB) with debug info");
}

void loop()
{
    // Maintain MQTT connection
    mqttManager.maintainConnection();  // Commented out until Raspberry Pi is available

    // Read sound level and status
    float soundLevel = soundSensor.readSoundLevel();
    String soundStatus = soundSensor.getStatus();

    // Print detailed debug information
    soundSensor.printDebugInfo();

    // Print simple sound level for monitoring
    Serial.println("Simple Sound Level: " + String(soundLevel, 1) + " dB, Status: " + soundStatus);

    // Publish to MQTT if connected and interval elapsed
    if (mqttManager.isConnected() && mqttManager.shouldPublish())
     {
         if (mqttManager.publishSoundData(soundLevel, soundStatus))
         {
             Serial.println("[MQTT] Data published: Sensor 1, " + String(soundLevel, 1) + " dB, Status: " + soundStatus);
         }
         mqttManager.updateLastPublish();
     }

    Serial.println("---");
    delay(500); // Update every 500ms for easier reading
}