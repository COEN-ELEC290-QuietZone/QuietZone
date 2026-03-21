#ifndef MQTT_CLIENT_H
#define MQTT_CLIENT_H

#include <Arduino.h>
#include <ESP8266WiFi.h>
#include <PubSubClient.h>

class MQTTClientManager
{
private:
    WiFiClient espClient;
    PubSubClient mqttClient;
    unsigned long lastPublish;

    static const char *ssid;
    static const char *password;
    static const char *mqttServer;
    static const char *sensorId;
    static const unsigned long publishInterval = 5000;

public:
    MQTTClientManager();
    void begin();
    void connectWiFi();
    void connectMQTT();
    void maintainConnection();
    bool publishSoundData(float dbLevel, const String &status);
    bool isConnected();
    bool shouldPublish();
    void updateLastPublish();
};

#endif // MQTT_CLIENT_H
