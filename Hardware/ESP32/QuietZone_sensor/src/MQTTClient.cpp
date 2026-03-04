#include "MQTTClient.h"

// Static member definitions
const char *MQTTClientManager::ssid = "ESP32_Network";
const char *MQTTClientManager::password = "yourpassword123";
const char *MQTTClientManager::mqtt_server = "192.168.4.1";
const char *MQTTClientManager::sensor_id = "esp32_sensor_01";

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
    String payload = "{\"sensor_name\":\"Sensor 1\",\"sensor_id\":\"" + String(sensor_id) + "\",\"db_level\":" + String(dbLevel, 1) + ",\"status\":\"" + status + "\"}";
    bool published = mqttClient.publish("sensors/esp32/sound_data", payload.c_str());

    if (published)
    {
        Serial.println("[INFO] MQTT publish OK: Sensor 1, " + String(dbLevel, 1) + " dB, Status: " + status);
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