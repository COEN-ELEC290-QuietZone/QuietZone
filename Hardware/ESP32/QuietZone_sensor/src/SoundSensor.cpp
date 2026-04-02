/******************************************************************************
 * SoundSensor.cpp
 * Sound detector implementation - Unified for ESP32 and ESP8266
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

SoundSensor::SoundSensor() : dcOffset(0.0f)
{
}

void SoundSensor::begin()
{
    // Calibrate DC offset for more accurate readings
    calibrateDCOffset();

    Serial.println("[SoundSensor] MAX9814 Microphone initialized (ESP32)");
}

void SoundSensor::calibrateDCOffset()
{
    Serial.println("[SoundSensor] Calibrating DC offset...");

    unsigned long sum = 0;
    for (int i = 0; i < SAMPLES; i++)
    {
        sum += analogRead(MIC_PIN);
        delay(1);
    }

    // dcOffset in raw ADC range (0-4095)
    dcOffset = static_cast<float>(sum) / static_cast<float>(SAMPLES);
    Serial.println("[SoundSensor] DC Offset: " + String(dcOffset, 2));
}

float SoundSensor::readRMSValue()
{
    // Calculate true RMS of audio signal
    const int numberOfSamples = 100;
    float sumSquares = 0.0f;

    for (int i = 0; i < numberOfSamples; i++)
    {
        float sample = static_cast<float>(analogRead(MIC_PIN)) - dcOffset;
        sumSquares += sample * sample;
        delayMicroseconds(100); // Sample at ~10kHz
    }

    float meanSquare = sumSquares / static_cast<float>(numberOfSamples);
    return sqrtf(meanSquare);
}

float SoundSensor::readSoundLevel()
{
    float rmsValue = readRMSValue();
    float rmsVolts = rmsValue * VOLTS_PER_COUNT;

    float dbv = 20.0f * log10f(rmsVolts);
    float dbSPL = dbv - MAX9814_SENSITIVITY_DB + CALIBRATION_OFFSET;

    return dbSPL;
}

bool SoundSensor::isSoundDetected()
{
    return readRMSValue() > 2.0f;
}

String SoundSensor::getStatus()
{
    float rmsValue = readRMSValue();

    if (rmsValue < 12.0f)
    {
        return "Quiet";
    }
    if (rmsValue < 38.0f)
    {
        return "Medium";
    }

    return "Loud";
}

void SoundSensor::printDebugInfo()
{
    float rmsValue = readRMSValue();
    float dBLevel = readSoundLevel();
    String status = getStatus();

    Serial.println("=== MAX9814 Microphone Debug (ESP32) ===");
    Serial.println("RMS Value: " + String(rmsValue, 2));
    Serial.println("Sound Level: " + String(dBLevel, 1) + " dB");
    Serial.println("Status: " + status);
    Serial.println("DC Offset: " + String(dcOffset, 2));
    Serial.println("==========================================");
}