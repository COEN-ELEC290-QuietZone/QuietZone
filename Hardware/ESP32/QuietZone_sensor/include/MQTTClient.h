#ifndef MQTT_CLIENT_H
#define MQTT_CLIENT_H

#include <Arduino.h>
#include <WiFi.h>
#include <PubSubClient.h>

class MQTTClientManager
{
private:
    WiFiClient espClient;
    PubSubClient mqttClient;
    unsigned long lastPublish;

    static const char *ssid;
    static const char *password;
    static const char *mqtt_server;
    static const char *sensor_id;
    static const unsigned long publishInterval = 5000; // 5 seconds

public:
    MQTTClientManager();
    void begin();
    void connectWiFi();
    void connectMQTT();
    void maintainConnection();
    bool publishSoundData(float dbLevel);
    bool isConnected();
    bool shouldPublish();
    void updateLastPublish();
};

#endif // MQTT_CLIENT_H