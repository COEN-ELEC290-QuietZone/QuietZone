#ifndef SERIAL_DEBUG_H
#define SERIAL_DEBUG_H

#include <Arduino.h>
#include "BluetoothSerial.h"

class SerialDebugger
{
private:
    BluetoothSerial btSerial;
    unsigned long lastDebugOutput;
    static const unsigned long debugInterval = 1000; // 1 second

public:
    SerialDebugger();
    void begin();
    void print(String message);
    void println(String message);
    void printSoundData(float dbLevel, int rawValue);
    bool shouldPrintDebug();
    void updateLastDebugTime();
};

#endif // SERIAL_DEBUG_H