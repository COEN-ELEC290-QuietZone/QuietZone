#include "SoundSensor.h"

SoundSensor::SoundSensor() : dcOffset(0)
{
}

void SoundSensor::begin()
{
    analogReadResolution(12); // ESP32 default = 12-bit (0–4095)
    calibrateDCOffset();

    Serial.println("Sound Level Monitor Initialized");
    Serial.println("DC Offset: " + String(dcOffset, 1));
    Serial.println("Calibration Offset: " + String(CALIBRATION_OFFSET) + " dB");
}

void SoundSensor::calibrateDCOffset()
{
    Serial.println("Calculating DC offset...");
    long sum = 0;
    for (int i = 0; i < 1000; i++)
    {
        sum += analogRead(MIC_PIN);
        delay(1);
    }
    dcOffset = sum / 1000.0;
}

float SoundSensor::readRMSValue()
{
    float sumSquares = 0;

    // Collect samples for RMS calculation
    for (int i = 0; i < SAMPLES; i++)
    {
        int sample = analogRead(MIC_PIN);

        // Calculate AC component (remove actual DC bias)
        float acSample = sample - dcOffset;
        sumSquares += acSample * acSample;

        delayMicroseconds(200); // Small delay between samples
    }

    // Calculate RMS value of AC component
    return sqrt(sumSquares / SAMPLES);
}

float SoundSensor::readSoundLevel()
{
    float rmsAC = readRMSValue();

    // Convert RMS to decibels (with calibration offset)
    if (rmsAC > 1.0) // Avoid log of very small numbers
    {
        return 20 * log10(rmsAC) + CALIBRATION_OFFSET;
    }
    else
    {
        return CALIBRATION_OFFSET - 60; // Very quiet baseline
    }
}

void SoundSensor::printDebugInfo()
{
    int maxSample = 0;
    int minSample = 4095;
    float sumSquares = 0;

    // Collect samples and track min/max
    for (int i = 0; i < SAMPLES; i++)
    {
        int sample = analogRead(MIC_PIN);

        if (sample > maxSample)
            maxSample = sample;
        if (sample < minSample)
            minSample = sample;

        float acSample = sample - dcOffset;
        sumSquares += acSample * acSample;

        delayMicroseconds(200);
    }

    float rmsAC = sqrt(sumSquares / SAMPLES);
    float soundLevel_dB = (rmsAC > 1.0) ? 20 * log10(rmsAC) + CALIBRATION_OFFSET : CALIBRATION_OFFSET - 60;

    Serial.print("Range: ");
    Serial.print(minSample);
    Serial.print("-");
    Serial.print(maxSample);
    Serial.print(", RMS_AC: ");
    Serial.print(rmsAC, 2);
    Serial.print(", Sound Level: ");
    Serial.print(soundLevel_dB, 1);
    Serial.println(" dB");
}