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
    // ADC: 10-bit (0-1023), Vref = 3.3V, so LSB = 3.3/1023 = 0.00322V per count
    static constexpr float ADC_VREF = 3.3f;
    static constexpr float ADC_RESOLUTION = 1023.0f;
    static constexpr float VOLTS_PER_COUNT = ADC_VREF / ADC_RESOLUTION; // 0.00322V
    // MAX9814 sensitivity: -37 dBV/Pa (output voltage for 1 Pascal pressure)
    // Reference: 1V (dBV scale) or ~0.0045V equivalent (20 µPa standard)
    static constexpr float MAX9814_SENSITIVITY_DB = 20.0f; // dBV/Pa
    static constexpr float CALIBRATION_OFFSET = 94.0f;      // 94 dB = reference adjustment

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
