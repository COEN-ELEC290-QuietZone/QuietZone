#include <Arduino.h>
#include <ESP8266WiFi.h>
#include "SoundSensor.h"
#include "MQTTClient.h"

SoundSensor soundSensor;
// MQTTClientManager mqttManager; // Enable when MQTT backend is available

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
    WiFi.mode(WIFI_AP_STA);
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

  Serial.println("[INFO] QuietZone ESP8266 Sensor Starting...");

  soundSensor.begin();
  // mqttManager.begin();

  if (kSetupModeEnabled)
  {
    startSetupHotspot();
  }

  Serial.println("[INFO] Sound sensor initialized!");
  Serial.println("Format: Sound Level (dB) with debug info");
}

void loop()
{
  // mqttManager.maintainConnection();

  float soundLevel = soundSensor.readSoundLevel();
  String soundStatus = soundSensor.getStatus();

  soundSensor.printDebugInfo();

  Serial.println("Simple Sound Level: " + String(soundLevel, 1) + " dB, Status: " + soundStatus);

  // if (mqttManager.isConnected() && mqttManager.shouldPublish())
  // {
  //     if (mqttManager.publishSoundData(soundLevel, soundStatus))
  //     {
  //         Serial.println("[MQTT] Data published: Sensor 1, " + String(soundLevel, 1) + " dB, Status: " + soundStatus);
  //     }
  //     mqttManager.updateLastPublish();
  // }

  Serial.println("---");
  delay(500);
}