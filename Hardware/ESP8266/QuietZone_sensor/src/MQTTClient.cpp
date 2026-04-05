#include "MQTTClient.h"
const char *MQTTClientManager::ssid = "Your_SSID";
const char *MQTTClientManager::password = "Your_PASSWORD";
const char *MQTTClientManager::mqtt_server = "IP_ADDRESS";
const char *MQTTClientManager::sensorId = "12345abcde";
const char *MQTTClientManager::sensorName = "Sensor 1 ESP8266";
const char *MQTTClientManager::location = "Study Room 8266";
MQTTClientManager::MQTTClientManager() : mqttClient(espClient), lastPublish(0)
{
}

void MQTTClientManager::begin()
{
    connectWiFi();
    connectMQTT();
}

void MQTTClientManager::connectWiFi()
{
    Serial.println("[INFO] Connecting to WiFi SSID: " + String(ssid));
    WiFi.mode(WIFI_STA);
    WiFi.begin(ssid, password);

    int attempts = 0;
    while (WiFi.status() != WL_CONNECTED && attempts < 20)
    {
        delay(500);
        Serial.print('.');
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
        if (mqttClient.connect("ESP8266_SoundSensor"))
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
        {
            connectWiFi();
        }
        connectMQTT();
    }
    mqttClient.loop();
}

bool MQTTClientManager::publishSoundData(float dbLevel)
{
    String payload = "{\"sensor_name\":\"" + String(sensorName) + "\",\"sensor_id\":\"" + String(sensorId) +
                     "\",\"db_level\":" + String(dbLevel, 1) +
                     ",\"location\":\"" + String(location) + "\"}";

    bool published = mqttClient.publish("sensors/esp8266/sound_data", payload.c_str());

    if (published)
    {
        Serial.println("[INFO] MQTT publish OK: " + String(sensorName) + ", " + String(dbLevel, 1) + " dB");
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
    return millis() - lastPublish > publishInterval;
}

void MQTTClientManager::updateLastPublish()
{
    lastPublish = millis();
}
