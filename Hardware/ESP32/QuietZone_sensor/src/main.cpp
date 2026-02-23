#include <Arduino.h>
#include "BluetoothSerial.h"
#include <WiFi.h>
#include <PubSubClient.h>

// === BLUETOOTH SETUP ===
BluetoothSerial SerialBT;

// === WIFI & MQTT SETUP ===
const char *ssid = "ESP32_Network";       // Your Pi hotspot name
const char *password = "yourpassword123"; // Your Pi hotspot password
const char *mqtt_server = "192.168.4.1";  // Pi's static IP (always this)

WiFiClient espClient;
PubSubClient mqttClient(espClient);

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
void connectWiFi();
void connectMQTT();
void publishMotionEvent(bool motionState);

// === PIN DEFINITIONS ===
#define PIR_PIN 32
#ifndef LED_BUILTIN
#define LED_BUILTIN 2
#endif
#define STATUS_LED_PIN 26

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
const unsigned long motionTimeout = 1000;

LedEffect currentLedEffect = LED_OFF;
unsigned long ledEffectTimer = 0;
bool ledState = false;

// === WIFI CONNECTION ===
void connectWiFi()
{
  dualPrintln("📡 Connecting to Pi hotspot...");
  WiFi.begin(ssid, password);

  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED && attempts < 20)
  {
    delay(500);
    Serial.print(".");
    attempts++;
  }

  if (WiFi.status() == WL_CONNECTED)
  {
    dualPrintln("\n✅ WiFi connected! IP: " + WiFi.localIP().toString());
  }
  else
  {
    dualPrintln("\n❌ WiFi failed. Will retry in loop.");
  }
}

// === MQTT CONNECTION ===
void connectMQTT()
{
  mqttClient.setServer(mqtt_server, 1883);

  int attempts = 0;
  while (!mqttClient.connected() && attempts < 5)
  {
    dualPrintln("🔌 Connecting to MQTT broker...");
    if (mqttClient.connect("ESP32_MotionSensor"))
    {
      dualPrintln("✅ MQTT connected!");
    }
    else
    {
      dualPrintln("❌ MQTT failed. Retrying...");
      delay(1000);
      attempts++;
    }
  }
}

// === PUBLISH MOTION EVENT ===
void publishMotionEvent(bool motionState)
{
  if (!mqttClient.connected())
    connectMQTT();

  if (motionState)
  {
    // Send motion detected with event count
    String payload = "{\"motion\":true,\"event\":" + String(totalMotionEvents) + "}";
    mqttClient.publish("sensors/esp32/motion", payload.c_str());
    dualPrintln("📤 MQTT sent: motion detected");
  }
  else
  {
    String payload = "{\"motion\":false}";
    mqttClient.publish("sensors/esp32/motion", payload.c_str());
    dualPrintln("📤 MQTT sent: motion ended");
  }
}

// === SETUP FUNCTION ===
void setup()
{
  Serial.begin(115200);
  SerialBT.begin("SmartMotionHub");
  systemStartTime = millis();

  pinMode(PIR_PIN, INPUT);
  pinMode(LED_BUILTIN, OUTPUT);
  pinMode(STATUS_LED_PIN, OUTPUT);

  digitalWrite(LED_BUILTIN, LOW);
  digitalWrite(STATUS_LED_PIN, LOW);

  dualPrintln("");
  dualPrintln("╔══════════════════════════════════════╗");
  dualPrintln("║        🚀 SMART MOTION HUB 🚀        ║");
  dualPrintln("║     Enhanced PIR Detection System    ║");
  dualPrintln("║       📱 Bluetooth + MQTT 📱         ║");
  dualPrintln("╚══════════════════════════════════════╝");
  dualPrintln("");

  connectWiFi();
  connectMQTT();

  dualPrintln("✅ System ready! Monitoring for motion...");
  dualPrintln("");
}

// === MAIN LOOP ===
void loop()
{
  // Keep MQTT connection alive
  if (!mqttClient.connected())
  {
    if (WiFi.status() != WL_CONNECTED)
      connectWiFi();
    connectMQTT();
  }
  mqttClient.loop();

  handleSerialCommands();
  handleBluetoothCommands();
  updateMotionDetection();
  updateLedEffects();

  delay(50);
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
      motionDetected = true;
      lastMotionTime = currentTime;
      totalMotionEvents++;
      motionEventsToday++;

      if (alertMode)
        setLedEffect(LED_FAST_BLINK);

      dualPrintln("🔴 MOTION DETECTED! [Event #" + String(totalMotionEvents) + "]");
      publishMotionEvent(true); // ← Send to Raspberry Pi
    }
    else
    {
      lastMotionTime = currentTime;
    }
  }
  else
  {
    if (motionDetected && (currentTime - lastMotionTime > motionTimeout))
    {
      motionDetected = false;
      setLedEffect(LED_PULSE);

      dualPrintln("🟢 Motion ended.");
      publishMotionEvent(false); // ← Send to Raspberry Pi
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
      setLedEffect(LED_PULSE);
    break;

  case LED_SLOW_BLINK:
    if (elapsed % 1000 < 500)
      digitalWrite(LED_BUILTIN, HIGH);
    else
      digitalWrite(LED_BUILTIN, LOW);
    break;

  case LED_PULSE:
    int brightness = (sin(elapsed * 0.005) + 1) * 127;
    analogWrite(LED_BUILTIN, brightness);
    if (elapsed > 10000)
      setLedEffect(LED_OFF);
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
      printStatistics();
    else if (command == "reset")
      resetStatistics();
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
      printHelp();
    else
      dualPrintln("❓ Unknown command. Type 'help' for available commands.");
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
      printStatistics();
    else if (command == "reset")
      resetStatistics();
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
      printHelp();
    else
      dualPrintln("❓ Unknown command. Type 'help' for available commands.");
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
  dualPrintln("📡 WiFi status: " + String(WiFi.status() == WL_CONNECTED ? "Connected" : "Disconnected"));
  dualPrintln("🔌 MQTT status: " + String(mqttClient.connected() ? "Connected" : "Disconnected"));
  dualPrintln("⏰ Uptime: " + String((millis() - systemStartTime) / 60000.0, 1) + " minutes");
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