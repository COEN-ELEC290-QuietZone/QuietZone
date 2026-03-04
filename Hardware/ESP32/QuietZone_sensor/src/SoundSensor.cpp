/******************************************************************************
 * SoundSensor.cpp
 * Sound detector implementation - ESP32 version
 * Based on SparkFun Sound Detector sample code
 * Byron Jacquot @ SparkFun Electronics
 * February 19, 2014
 * https://github.com/sparkfun/Sound_Detector
 *
 * Adapted for SoundSensor class implementation
 *
 * Connections:
 * The Sound Detector is connected to the ESP32 as follows:
 * (Sound Detector -> ESP32 pin)
 * GND → GND
 * VCC → 3.3V
 * Gate → GPIO 4
 * Envelope → GPIO 35 (ADC1_CH0)
 *
 ******************************************************************************/

#include "SoundSensor.h"

// Hardware connections for SparkFun Sound Detector
#define PIN_GATE_IN 4 // GPIO4 for gate input
#define PIN_LED_OUT 2 // GPIO2 for built-in LED

// Static variables for interrupt handling
static volatile bool soundDetected = false;

// Interrupt service routine
void IRAM_ATTR soundISR()
{
    soundDetected = digitalRead(PIN_GATE_IN);
    digitalWrite(PIN_LED_OUT, soundDetected);
}

SoundSensor::SoundSensor() : dcOffset(0.0)
{
}

void SoundSensor::begin()
{
    // Configure LED pin as output
    pinMode(PIN_LED_OUT, OUTPUT);

    // Configure gate input for interrupt
    pinMode(PIN_GATE_IN, INPUT);
    attachInterrupt(digitalPinToInterrupt(PIN_GATE_IN), soundISR, CHANGE);

    // Calibrate DC offset for more accurate readings
    calibrateDCOffset();

    Serial.println("[SoundSensor] SparkFun Sound Detector initialized");
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

    dcOffset = (float)sum / SAMPLES;
    Serial.println("[SoundSensor] DC Offset: " + String(dcOffset, 2));
}

float SoundSensor::readRMSValue()
{
    // Read the envelope output from SparkFun Sound Detector
    return analogRead(MIC_PIN);
}

float SoundSensor::readSoundLevel()
{
    float envelopeValue = readRMSValue();

    if (envelopeValue <= 1)
    {
        return -80.0; // Very quiet, return a low dB value
    }

    // Convert envelope to dB using SparkFun approach with calibration offset
    float dB = 20 * log10(envelopeValue) + CALIBRATION_OFFSET;
    return dB;
}

bool SoundSensor::isSoundDetected()
{
    return soundDetected;
}

String SoundSensor::getStatus()
{
    float envelopeValue = readRMSValue();

    // Use SparkFun thresholds for status determination
    if (envelopeValue <= 10)
    {
        return "Quiet";
    }
    else if (envelopeValue <= 30)
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
    float envelopeValue = readRMSValue();
    float dBLevel = readSoundLevel();
    String status = getStatus();
    bool gateStatus = isSoundDetected();

    Serial.println("=== SparkFun Sound Detector Debug ===");
    Serial.println("Envelope Value: " + String(envelopeValue));
    Serial.println("Sound Level: " + String(dBLevel, 1) + " dB");
    Serial.println("Status: " + status);
    Serial.println("Gate Detection: " + String(gateStatus ? "SOUND" : "QUIET"));
    Serial.println("DC Offset: " + String(dcOffset, 2));
    Serial.println("=====================================");
}