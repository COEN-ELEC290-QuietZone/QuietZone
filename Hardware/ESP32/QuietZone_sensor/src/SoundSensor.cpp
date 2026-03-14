/******************************************************************************
 * SoundSensor.cpp
 * Sound detector implementation - ESP32 version
 * MAX9814 Microphone Amplifier
 *
 * Connections:
 * The MAX9814 is connected to the ESP32 as follows:
 * (MAX9814 -> ESP32 pin)
 * GND → GND
 * VCC → 3.3V
 * OUT → GPIO 35 (ADC1_CH0)
 *
 ******************************************************************************/

#include "SoundSensor.h"

SoundSensor::SoundSensor() : dcOffset(0.0)
{
}

void SoundSensor::begin()
{
    // Calibrate DC offset for more accurate readings
    calibrateDCOffset();

    Serial.println("[SoundSensor] MAX9814 Microphone initialized");
}

void SoundSensor::calibrateDCOffset()
{
    Serial.println("[SoundSensor] Calibrating DC offset...");

    long sum = 0;
    for (int i = 0; i < SAMPLES; i++)
    {
        sum += analogRead(MIC_PIN);
        delay(1);
    }

    // dcOffset in raw ADC range (0-4095)
    dcOffset = (float)sum / SAMPLES;
    Serial.println("[SoundSensor] DC Offset: " + String(dcOffset, 2));
}

float SoundSensor::readRMSValue()
{
    // Calculate true RMS of audio signal
    long sumSquares = 0;
    int numberOfSamples = 100;

    for (int i = 0; i < numberOfSamples; i++)
    {
        float sample = analogRead(MIC_PIN) - dcOffset;
        sumSquares += sample * sample;
        delayMicroseconds(100); // Sample at ~10kHz
    }

    float meanSquare = (float)sumSquares / numberOfSamples;
    float rms = sqrt(meanSquare);
    return rms;
}

float SoundSensor::readSoundLevel()
{
    float rmsValue = readRMSValue();

    if (rmsValue <= 10) // If RMS is very small, call it silent
    {
        return -80.0;
    }

    float dB = 20 * log10(rmsValue) + CALIBRATION_OFFSET;
    return dB;
}

bool SoundSensor::isSoundDetected()
{
    float rmsValue = readRMSValue();
    return rmsValue > 50; // Threshold for sound detection
}

String SoundSensor::getStatus()
{
    float rmsValue = readRMSValue();

    if (rmsValue < 50)
    {
        return "Quiet";
    }
    else if (rmsValue < 150)
    {
        return "Moderate";
    }
    else
    {
        return "Loud";
    }
}

void SoundSensor::printDebugInfo()
{
    float rmsValue = readRMSValue();
    float dBLevel = readSoundLevel();
    String status = getStatus();

    Serial.println("=== MAX9814 Microphone Debug ===");
    Serial.println("RMS Value: " + String(rmsValue, 2));
    Serial.println("Sound Level: " + String(dBLevel, 1) + " dB");
    Serial.println("Status: " + status);
    Serial.println("DC Offset: " + String(dcOffset, 2));
    Serial.println("================================");
}