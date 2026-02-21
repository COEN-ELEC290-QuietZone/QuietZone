#include <Arduino.h>
#include "BluetoothSerial.h"

// === BLUETOOTH SETUP ===
BluetoothSerial SerialBT;

// === LED EFFECTS ENUM ===
enum LedEffect
{
  LED_OFF,
  LED_PULSE,
  LED_FAST_BLINK,
  LED_SLOW_BLINK,
  LED_SOLID
};

// === FUNCTION PROTOTYPES ===
void updateMotionDetection();
void updateLedEffects();
void setLedEffect(LedEffect effect);
void handleSerialCommands();
void handleBluetoothCommands();
void printStatistics();
void resetStatistics();
void printHelp();
void dualPrint(String message);
void dualPrintln(String message);

// === PIN DEFINITIONS ===
#define PIR_PIN 32        // HC-SR501 PIR sensor
#define LED_BUILTIN 2     // Built-in LED
#define STATUS_LED_PIN 26 // External status LED (optional)

// === MOTION DETECTION VARIABLES ===
bool motionDetected = false;
bool lastMotionState = false;
bool alertMode = true;
unsigned long lastMotionTime = 0;
unsigned long systemStartTime = 0;

// === STATISTICS & COUNTERS ===
unsigned int totalMotionEvents = 0;
unsigned int motionEventsToday = 0;

// === TIMING CONSTANTS ===
const unsigned long motionTimeout = 1000; // 1 second timeout

LedEffect currentLedEffect = LED_OFF;
unsigned long ledEffectTimer = 0;
bool ledState = false;

// === SETUP FUNCTION ===
void setup()
{
  Serial.begin(115200);
  SerialBT.begin("SmartMotionHub"); // Bluetooth device name
  systemStartTime = millis();

  // Configure pins
  pinMode(PIR_PIN, INPUT);
  pinMode(LED_BUILTIN, OUTPUT);
  pinMode(STATUS_LED_PIN, OUTPUT);

  // Initial states
  digitalWrite(LED_BUILTIN, LOW);
  digitalWrite(STATUS_LED_PIN, LOW);

  dualPrintln("");
  dualPrintln("╔══════════════════════════════════════╗");
  dualPrintln("║        🚀 SMART MOTION HUB 🚀        ║");
  dualPrintln("║     Enhanced PIR Detection System    ║");
  dualPrintln("║       📱 Bluetooth Enabled 📱        ║");
  dualPrintln("╚══════════════════════════════════════╝");
  dualPrintln("");

  dualPrintln("✅ System ready! Monitoring for motion...");
  dualPrintln("📡 Bluetooth: SmartMotionHub");
  dualPrintln("🎛️  Commands: 'stats', 'reset', 'alert on/off', 'help'");
  dualPrintln("");
}

// === MAIN LOOP ===
void loop()
{
  handleSerialCommands();
  handleBluetoothCommands();
  updateMotionDetection();
  updateLedEffects();

  delay(50); // Smooth operation
}

// === MOTION DETECTION LOGIC ===
void updateMotionDetection()
{
  int currentMotionState = digitalRead(PIR_PIN);
  unsigned long currentTime = millis();

  if (currentMotionState == HIGH)
  {
    if (!motionDetected)
    {
      // Motion just started
      motionDetected = true;
      lastMotionTime = currentTime;
      totalMotionEvents++;
      motionEventsToday++;

      // Alert sequence
      if (alertMode)
      {
        setLedEffect(LED_FAST_BLINK);
      }

      dualPrintln("🔴 MOTION DETECTED! [Event #" + String(totalMotionEvents) + "]");
    }
    else
    {
      // Motion continuing
      lastMotionTime = currentTime;
    }
  }
  else
  {
    // No motion detected
    if (motionDetected && (currentTime - lastMotionTime > motionTimeout))
    {
      // Motion just ended
      motionDetected = false;
      setLedEffect(LED_PULSE);

      dualPrintln("🟢 Motion ended.");
    }
  }

  lastMotionState = motionDetected;
}

// === LED EFFECTS SYSTEM ===
void setLedEffect(LedEffect effect)
{
  currentLedEffect = effect;
  ledEffectTimer = millis();
}

void updateLedEffects()
{
  unsigned long currentTime = millis();
  unsigned long elapsed = currentTime - ledEffectTimer;

  switch (currentLedEffect)
  {
  case LED_OFF:
    digitalWrite(LED_BUILTIN, LOW);
    digitalWrite(STATUS_LED_PIN, LOW);
    break;

  case LED_SOLID:
    digitalWrite(LED_BUILTIN, HIGH);
    digitalWrite(STATUS_LED_PIN, HIGH);
    break;

  case LED_FAST_BLINK:
    if (elapsed % 200 < 100)
    {
      digitalWrite(LED_BUILTIN, HIGH);
      digitalWrite(STATUS_LED_PIN, HIGH);
    }
    else
    {
      digitalWrite(LED_BUILTIN, LOW);
      digitalWrite(STATUS_LED_PIN, LOW);
    }
    if (elapsed > 3000)
      setLedEffect(LED_PULSE); // Auto-transition
    break;

  case LED_SLOW_BLINK:
    if (elapsed % 1000 < 500)
    {
      digitalWrite(LED_BUILTIN, HIGH);
    }
    else
    {
      digitalWrite(LED_BUILTIN, LOW);
    }
    break;

  case LED_PULSE:
    // Breathing effect
    int brightness = (sin(elapsed * 0.005) + 1) * 127;
    analogWrite(LED_BUILTIN, brightness);
    if (elapsed > 10000)
      setLedEffect(LED_OFF); // Auto-off after 10s
    break;
  }
}

// === SERIAL COMMANDS ===
void handleSerialCommands()
{
  if (Serial.available())
  {
    String command = Serial.readString();
    command.trim();
    command.toLowerCase();

    if (command == "stats")
    {
      printStatistics();
    }
    else if (command == "reset")
    {
      resetStatistics();
    }
    else if (command == "alert on")
    {
      alertMode = true;
      dualPrintln("🔊 Visual alerts enabled");
    }
    else if (command == "alert off")
    {
      alertMode = false;
      dualPrintln("🔇 Visual alerts disabled");
    }
    else if (command == "help")
    {
      printHelp();
    }
    else
    {
      dualPrintln("❓ Unknown command. Type 'help' for available commands.");
    }
  }
}

// === BLUETOOTH COMMANDS ===
void handleBluetoothCommands()
{
  if (SerialBT.available())
  {
    String command = SerialBT.readString();
    command.trim();
    command.toLowerCase();

    if (command == "stats")
    {
      printStatistics();
    }
    else if (command == "reset")
    {
      resetStatistics();
    }
    else if (command == "alert on")
    {
      alertMode = true;
      dualPrintln("🔊 Visual alerts enabled");
    }
    else if (command == "alert off")
    {
      alertMode = false;
      dualPrintln("🔇 Visual alerts disabled");
    }
    else if (command == "help")
    {
      printHelp();
    }
    else
    {
      dualPrintln("❓ Unknown command. Type 'help' for available commands.");
    }
  }
}

// === UTILITY FUNCTIONS ===
void printStatistics()
{
  dualPrintln("");
  dualPrintln("📊 === MOTION DETECTION STATISTICS ===");
  dualPrintln("🔢 Total motion events: " + String(totalMotionEvents));
  dualPrintln("📅 Events today: " + String(motionEventsToday));
  dualPrintln("🔊 Alert mode: " + String(alertMode ? "ON" : "OFF"));
  dualPrintln("⏰ System uptime: " + String((millis() - systemStartTime) / 60000.0, 1) + " minutes");
  dualPrintln("==========================================");
  dualPrintln("");
}

void resetStatistics()
{
  totalMotionEvents = 0;
  motionEventsToday = 0;
  dualPrintln("🔄 Statistics reset!");
}

void printHelp()
{
  dualPrintln("");
  dualPrintln("🛠️  === AVAILABLE COMMANDS ===");
  dualPrintln("📊 'stats'      - Show motion statistics");
  dualPrintln("🔄 'reset'      - Reset all statistics");
  dualPrintln("🔊 'alert on'   - Enable visual alerts");
  dualPrintln("🔇 'alert off'  - Disable visual alerts");
  dualPrintln("❓ 'help'       - Show this help menu");
  dualPrintln("===============================");
  dualPrintln("");
}

// === DUAL OUTPUT FUNCTIONS ===
void dualPrint(String message)
{
  Serial.print(message);
  SerialBT.print(message);
}

void dualPrintln(String message)
{
  Serial.println(message);
  SerialBT.println(message);
}