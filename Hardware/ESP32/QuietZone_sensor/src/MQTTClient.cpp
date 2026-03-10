#include "MQTTClient.h"

// =============================================
// CHANGE THIS NUMBER BEFORE FLASHING EACH ESP32
#define SENSOR_NUM 2   // ← 1 for ESP32 #1, change to 2 for ESP32 #2
// =============================================

#define STRINGIFY(x) #x
#define TOSTRING(x) STRINGIFY(x)

// Static member definitions
//const char *MQTTClientManager::ssid = "ESP32_Network";
const char *MQTTClientManager::ssid = "";
//const char *MQTTClientManager::password = "yourpassword123";
const char *MQTTClientManager::password = "";
//const char *MQTTClientManager::mqtt_server = "192.168.4.1";
const char *MQTTClientManager::mqtt_server = "192.168.2.173";
//const char *MQTTClientManager::sensor_id = "esp32_sensor_01";
const char *MQTTClientManager::sensor_id = "sensor_" TOSTRING(SENSOR_NUM);
const char *MQTTClientManager::client_id = "ESP32_SoundSensor_" TOSTRING(SENSOR_NUM);
const char *MQTTClientManager::mqtt_topic = "sensors/sensor_" TOSTRING(SENSOR_NUM) "/sound_data";

MQTTClientManager::MQTTClientManager() : mqttClient(espClient)
{
    lastPublish = 0;
}

void MQTTClientManager::begin()
{
    connectWiFi();
    connectMQTT();
}

void MQTTClientManager::connectWiFi()
{
    Serial.println("[INFO] Connecting to WiFi SSID: " + String(ssid));
    WiFi.begin(ssid, password);

    int attempts = 0;
    while (WiFi.status() != WL_CONNECTED && attempts < 20)
    {
        delay(500);
        Serial.print(".");
        attempts++;
    }

    if (WiFi.status() == WL_CONNECTED)
    {
        Serial.println("\n[INFO] WiFi connected. IP: " + WiFi.localIP().toString());
    }
    else
    {
        Serial.println("\n[ERROR] WiFi connection failed after " + String(attempts) + " attempts.");
    }
}

void MQTTClientManager::connectMQTT()
{
    mqttClient.setServer(mqtt_server, 1883);

    int attempts = 0;
    while (!mqttClient.connected() && attempts < 5)
    {
        Serial.println("[INFO] Connecting to MQTT broker at " + String(mqtt_server) + ":1883");
        if (mqttClient.connect("ESP32_SoundSensor"))
        {
            Serial.println("[INFO] MQTT connected.");
        }
        else
        {
            Serial.println("[ERROR] MQTT connection failed (state=" + String(mqttClient.state()) + "). Retrying...");
            delay(1000);
            attempts++;
        }
    }
}

void MQTTClientManager::maintainConnection()
{
    if (!mqttClient.connected())
    {
        if (WiFi.status() != WL_CONNECTED)
            connectWiFi();
        connectMQTT();
    }
    mqttClient.loop();
}

bool MQTTClientManager::publishSoundData(float dbLevel, String status)
{
    //String payload = "{\"sensor_name\":\"Sensor 1\",\"sensor_id\":\"" + String(sensor_id) + "\",\"db_level\":" + String(dbLevel, 1) + ",\"status\":\"" + status + "\"}";
    String payload = "{\"sensor_name\":\"Sensor " TOSTRING(SENSOR_NUM) "\","
                     "\"sensor_id\":\"" + String(sensor_id) + "\","
                     "\"db_level\":" + String(dbLevel, 1) + ","
                     "\"status\":\"" + status + "\"}";
    //bool published = mqttClient.publish("sensors/esp32/sound_data", payload.c_str());
    bool published = mqttClient.publish(mqtt_topic, payload.c_str());

    if (published)
    {
        Serial.println("[INFO] MQTT publish OK: " + String(sensor_id) + ", " + String(dbLevel, 1) + " dB, Status: " + status);
    }
    else
    {
        Serial.println("[ERROR] MQTT publish failed.");
    }

    return published;
}

bool MQTTClientManager::isConnected()
{
    return mqttClient.connected();
}

bool MQTTClientManager::shouldPublish()
{
    return (millis() - lastPublish > publishInterval);
}

void MQTTClientManager::updateLastPublish()
{
    lastPublish = millis();
}