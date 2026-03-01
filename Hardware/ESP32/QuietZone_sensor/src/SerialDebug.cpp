#include "SerialDebug.h"

SerialDebugger::SerialDebugger()
{
    lastDebugOutput = 0;
}

void SerialDebugger::begin()
{
    Serial.begin(115200);
    btSerial.begin("SmartSoundHub");

    println("");
    println("================================================");
    println("  QuietZone Sound Sensor - v1.0");
    println("  Serial @ 115200 baud | BT: SmartSoundHub");
    println("================================================");
    println("");
}

void SerialDebugger::print(String message)
{
    Serial.print(message);
    btSerial.print(message);
}

void SerialDebugger::println(String message)
{
    Serial.println(message);
    btSerial.println(message);
}

void SerialDebugger::printSoundData(float dbLevel, int rawValue)
{
    println("[DATA] Sound: " + String(dbLevel, 1) + " dB  |  ADC: " + String(rawValue));
}

bool SerialDebugger::shouldPrintDebug()
{
    return (millis() - lastDebugOutput > debugInterval);
}

void SerialDebugger::updateLastDebugTime()
{
    lastDebugOutput = millis();
}