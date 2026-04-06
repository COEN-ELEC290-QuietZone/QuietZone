#include "SoundSensor.h"

SoundSensor::SoundSensor() : dcOffset(0.0f)
{
}

void SoundSensor::begin()
{
    calibrateDCOffset();
    Serial.println("[SoundSensor] MAX9814 Microphone initialized (ESP8266)");
}

void SoundSensor::calibrateDCOffset()
{
    Serial.println("[SoundSensor] Calibrating DC offset...");

    unsigned long sum = 0;
    for (int i = 0; i < SAMPLES; i++)
    {
        sum += analogRead(MIC_PIN);
        delay(2);
    }

    dcOffset = static_cast<float>(sum) / static_cast<float>(SAMPLES);
    Serial.println("[SoundSensor] DC Offset: " + String(dcOffset, 2));
}

float SoundSensor::readRMSValue()
{
    const int numberOfSamples = 100;
    float sumSquares = 0.0f;

    for (int i = 0; i < numberOfSamples; i++)
    {
        float sample = static_cast<float>(analogRead(MIC_PIN)) - dcOffset;
        sumSquares += sample * sample;
        delayMicroseconds(150);
    }

    float meanSquare = sumSquares / static_cast<float>(numberOfSamples);
    return sqrtf(meanSquare);
}

float SoundSensor::readSoundLevel()
{
    float rmsValue = readRMSValue();

    // if (rmsValue <= 3.0f)
    // {
    //     return -80.0f;
    // }

    // Convert RMS ADC counts to voltage
    float rmsVolts = rmsValue * VOLTS_PER_COUNT;

    // dB SPL = 20 * log10(Vrms / Vref) + sensitivity_compensation + calibration
    // Using 1V as dBV reference and MAX9814 sensitivity
    float dbv = 20.0f * log10f(rmsVolts);
    float dbSPL = dbv - MAX9814_SENSITIVITY_DB + CALIBRATION_OFFSET;

    return dbSPL;
}

bool SoundSensor::isSoundDetected()
{
    return readRMSValue() > 12.0f;
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
        return "Moderate";
    }

    return "Loud";
}

void SoundSensor::printDebugInfo()
{
    float rmsValue = readRMSValue();
    float dBLevel = readSoundLevel();
    String status = getStatus();

    Serial.println("=== MAX9814 Microphone Debug (ESP8266) ===");
    Serial.println("RMS Value: " + String(rmsValue, 2));
    Serial.println("Sound Level: " + String(dBLevel, 1) + " dB");
    Serial.println("Status: " + status);
    Serial.println("DC Offset: " + String(dcOffset, 2));
    Serial.println("=");
}
