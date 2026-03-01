#ifndef SOUND_SENSOR_H
#define SOUND_SENSOR_H

#include <Arduino.h>
#include <math.h>

class SoundSensor
{
private:
    static const int MIC_PIN = 35;                     // AUDIO pin connected to GPIO35 (ADC1)
    static const int SAMPLES = 512;                    // Number of samples for RMS calculation
    static constexpr float CALIBRATION_OFFSET = -10.0; // Calibration value in dB (adjusted for realistic readings)

    float dcOffset; // Will be calculated dynamically

public:
    SoundSensor();

    // Lifecycle
    void begin();
    void calibrateDCOffset(); // Calculate the actual DC offset

    // RMS-based sound measurement
    float readSoundLevel(); // Returns sound level in dB
    float readRMSValue();   // Returns RMS AC component
    void printDebugInfo();  // Print detailed debug information
};

#endif // SOUND_SENSOR_H