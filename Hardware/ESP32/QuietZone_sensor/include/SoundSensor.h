#ifndef SOUND_SENSOR_H
#define SOUND_SENSOR_H

#include <Arduino.h>
#include <math.h>

class SoundSensor
{
private:
    static const int MIC_PIN = 35;  // AUDIO pin connected to GPIO35 (ADC1)
    static const int SAMPLES = 512; // Number of samples for calibration
    // ESP32 ADC: 12-bit range (0-4095), Vref = 3.3V, so LSB = 3.3/4095 = 0.000805V per count
    static constexpr float ADC_VREF = 3.3f;
    static constexpr float ADC_RESOLUTION = 4095.0f;
    static constexpr float VOLTS_PER_COUNT = ADC_VREF / ADC_RESOLUTION; // 0.000805V
    // MAX9814 sensitivity: -37 dBV/Pa (output voltage for 1 Pascal pressure)
    static constexpr float MAX9814_SENSITIVITY_DB = 0.0f; // dBV/Pa
    static constexpr float CALIBRATION_OFFSET = 35.0f;    // 35 dB = reference adjustment

    float dcOffset; // Will be calculated dynamically

public:
    SoundSensor();

    // Lifecycle
    void begin();
    void calibrateDCOffset(); // Calculate the actual DC offset from live samples

    // Sound measurement
    float readSoundLevel(); // Returns sound level in dB SPL
    float readRMSValue();   // Returns RMS value in ADC counts
    void printDebugInfo();  // Print detailed debug information with status
    bool isSoundDetected(); // Returns true if sound detected above threshold
    String getStatus();     // Returns status as string (Quiet, Moderate, Loud)
};

#endif // SOUND_SENSOR_H