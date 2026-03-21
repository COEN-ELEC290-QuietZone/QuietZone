#ifndef SOUND_SENSOR_H
#define SOUND_SENSOR_H

#include <Arduino.h>
#include <math.h>

class SoundSensor
{
private:
    // ESP8266 has a single ADC input (A0), 10-bit range: 0-1023.
    static const int MIC_PIN = A0;
    static const int SAMPLES = 256;
    static constexpr float CALIBRATION_OFFSET = 0.0f;

    float dcOffset;

public:
    SoundSensor();

    void begin();
    void calibrateDCOffset();

    float readSoundLevel();
    float readRMSValue();
    void printDebugInfo();
    bool isSoundDetected();
    String getStatus();
};

#endif // SOUND_SENSOR_H
