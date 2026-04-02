#MQTT library for connecting to the broker
import paho.mqtt.client as mqtt   

#needed for the newer paho-mqtt API version
from paho.mqtt.client import CallbackAPIVersion   

#firebase library
import firebase_admin   

#credentials = auth, db = realtime database, firestore = cloud firestore
from firebase_admin import credentials, db, firestore 

#for parsing JSON messages from ESP32
import json

#for timestamp conversion
from datetime import datetime, timezone

#for background tasks
import threading
import time


##configuration

#path to firebase admin key file
FIREBASE_KEY_PATH = "service_account.json"

#firebase project url
FIREBASE_DB_URL = "https://quiet-zone-1-default-rtdb.firebaseio.com/"

#mqtt broker is running on this same pi
MQTT_BROKER = "localhost"

#subscribe to all sensors, + is a wildcard, matches sensor_1 sensor_2 sensor_3
MQTT_TOPIC = "sensors/+/sound_data"

#averaging window in seconds
AVERAGING_WINDOW = 300  # 5 minutes


##initialize firebase

#load the firebase admin credentials
cred = credentials.Certificate(FIREBASE_KEY_PATH)

firebase_admin.initialize_app(cred, {
    'databaseURL': FIREBASE_DB_URL   #connect to firebase project
})

#point to the 'sound_data' node in firebase, all writes will happen under this node
root_ref = db.reference('sound_data')

#initialize firestore client
fs = firestore.client()


##buffer to store readings for averaging
sensor_buffers = {}  # {sensor_id: {'readings': [values], 'timestamps': [times], 'lock': threading.Lock()}}


def get_or_create_buffer(sensor_id):
    """Get or create a buffer for a specific sensor"""
    if sensor_id not in sensor_buffers:
        sensor_buffers[sensor_id] = {
            'readings': [],
            'lock': threading.Lock()
        }
    return sensor_buffers[sensor_id]


def flush_buffers():
    """Periodically flush buffers and write averages to Firestore"""
    while True:
        time.sleep(AVERAGING_WINDOW)
        
        now = datetime.now(timezone.utc)
        
        for sensor_id, buffer in sensor_buffers.items():
            with buffer['lock']:
                if buffer['readings']:  # Only write if there's data
                    avg_value = sum(buffer['readings']) / len(buffer['readings'])
                    
                    # Write average to Firestore
                    fs.collection('sound_data').document(sensor_id).collection('readings').add({
                        'value': avg_value,
                        'timestamp': now,
                        'count': len(buffer['readings'])  # How many readings in this average
                    })
                    
                    print(f"Synced {sensor_id} average ({avg_value:.2f} dB from {len(buffer['readings'])} readings) to Firestore at {now.strftime('%Y-%m-%d %H:%M:%S UTC')}")
                    
                    # Clear the buffer
                    buffer['readings'] = []


#mqtt callbacks
def on_connect(client, userdata, flags, rc, properties=None):
    print(f"Connected to MQTT Broker with result code {rc}")
    client.subscribe(MQTT_TOPIC)

def on_message(client, userdata, msg):
    try:
        payload = msg.payload.decode()
        data = json.loads(payload)
        db_level = data.get('db_level')
        sensor_id = data.get('sensor_id')

        print(f"Received from {sensor_id}: {db_level} dB")

        # Update 'live' node in realtime database immediately (real-time)
        root_ref.child('live').child(sensor_id).set({
            'value': db_level,
            'timestamp': {".sv": "timestamp"}
        })

        # Add to buffer for averaging
        buffer = get_or_create_buffer(sensor_id)
        with buffer['lock']:
            buffer['readings'].append(db_level)

    except Exception as e:
        print(f"Error: {e}")


#start mqtt client
client = mqtt.Client(CallbackAPIVersion.VERSION2)
client.on_connect = on_connect
client.on_message = on_message

# Start the buffer flushing thread as a daemon
flush_thread = threading.Thread(target=flush_buffers, daemon=True)
flush_thread.start()

client.connect(MQTT_BROKER, 1883, 60)
print("Bridge is running. Waiting for ESP32 data...")
print(f"Firestore data will be averaged and written every {AVERAGING_WINDOW} seconds (5 minutes)")
client.loop_forever()