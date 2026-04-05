package com.example.quietzone_app;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class AdminDashboardActivity extends AppCompatActivity {

    private LinearLayout sensorsContainer;
    private Handler refreshHandler;
    private Runnable refreshRunnable;
    private static final long REFRESH_INTERVAL_MS = 2000;

    private DatabaseReference liveSensorsRef;
    private ValueEventListener liveSensorsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);

        // Permission check: Only admin users can access this activity
        if (!SessionState.isAdmin(this)) {
            finish();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_admin_dashboard);

        Toolbar toolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(toolbar);
        int toolbarTextColor = getResources().getColor(R.color.app_on_primary, getTheme());
        toolbar.setTitleTextColor(toolbarTextColor);
        toolbar.setSubtitleTextColor(toolbarTextColor);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Admin Dashboard");
        }
        if (toolbar.getOverflowIcon() != null) {
            toolbar.getOverflowIcon().setTint(toolbarTextColor);
        }

        sensorsContainer = findViewById(R.id.sensorsContainer);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        refreshHandler = new Handler(Looper.getMainLooper());
        setupFirebaseListener();
        startAutoRefresh();
    }

    private void setupFirebaseListener() {
        liveSensorsRef = FirebaseDatabase.getInstance().getReference("sound_data/live");
        liveSensorsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Real-time updates handled by refreshSensorStatus
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        };
        liveSensorsRef.addValueEventListener(liveSensorsListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAutoRefresh();
        if (liveSensorsRef != null && liveSensorsListener != null) {
            liveSensorsRef.removeEventListener(liveSensorsListener);
        }
    }

    private void startAutoRefresh() {
        refreshRunnable = () -> {
            refreshSensorStatus();
            refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
        };
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
    }

    private void stopAutoRefresh() {
        if (refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
        }
    }

    private void refreshSensorStatus() {
        liveSensorsRef.get().addOnSuccessListener(snapshot -> {
            sensorsContainer.removeAllViews();
            Map<String, SensorData> sensors = new HashMap<>();

            // Read ALL sensors from Firebase (not just online ones)
            for (DataSnapshot child : snapshot.getChildren()) {
                String sensorKey = child.getKey();
                if (sensorKey == null || sensorKey.trim().isEmpty())
                    continue;

                SensorData sensor = new SensorData();
                sensor.id = sensorKey.trim();

                // Get values from Firebase
                Object valueObj = child.child("value").getValue();
                sensor.value = valueObj != null ? valueObj.toString() : "N/A";

                String firebaseStatus = child.child("status").getValue(String.class);
                sensor.firebaseStatus = firebaseStatus != null ? firebaseStatus : "unknown";

                String location = child.child("location").getValue(String.class);
                sensor.location = location != null ? location : "Unassigned";

                Object lastSeenObj = child.child("lastSeen").getValue();
                sensor.lastSeen = lastSeenObj != null ? lastSeenObj.toString() : "N/A";

                // Get metadata from SharedPreferences
                android.content.SharedPreferences prefs = getSharedPreferences("sensor_data", Context.MODE_PRIVATE);
                sensor.name = prefs.getString(sensorKey.trim() + "_name", "Sensor_" + sensorKey.trim());
                sensor.room = prefs.getString(sensorKey.trim() + "_room", "");

                sensor.isConnected = "online".equalsIgnoreCase(firebaseStatus);

                sensors.put(sensorKey.trim(), sensor);
            }

            if (sensors.isEmpty()) {
                showNoSensorsMessage();
            } else {
                displaySensors(sensors);
            }
        });
    }

    private void showNoSensorsMessage() {
        TextView noSensorsText = new TextView(this);
        noSensorsText.setText("Loading sensors from app...\nOpen the home screen to track sensors.");
        noSensorsText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        noSensorsText.setPadding(dpToPx(16), dpToPx(32), dpToPx(16), dpToPx(16));
        noSensorsText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        sensorsContainer.addView(noSensorsText);
    }

    private void displaySensors(Map<String, SensorData> sensors) {
        for (String sensorId : sensors.keySet()) {
            SensorData sensor = sensors.get(sensorId);
            if (sensor != null) {
                MaterialCardView card = createSensorCard(sensor);
                sensorsContainer.addView(card);
            }
        }
    }

    private MaterialCardView createSensorCard(SensorData sensor) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(16, 8, 16, 8);
        card.setLayoutParams(cardParams);
        card.setCardElevation(dpToPx(4));

        LinearLayout cardContent = new LinearLayout(this);
        cardContent.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        cardContent.setOrientation(LinearLayout.VERTICAL);
        cardContent.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));

        // Sensor Name
        TextView nameText = new TextView(this);
        nameText.setText("Sensor: " + sensor.name);
        nameText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        nameText.setTypeface(null, android.graphics.Typeface.BOLD);
        nameText.setTextColor(getResources().getColor(R.color.app_on_background, getTheme()));
        cardContent.addView(nameText);

        // Connection Status
        TextView statusText = new TextView(this);
        statusText.setText("Status: " + (sensor.isConnected ? "Connected" : "Disconnected"));
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        statusText
                .setTextColor(sensor.isConnected ? getResources().getColor(android.R.color.holo_green_dark, getTheme())
                        : getResources().getColor(android.R.color.holo_red_dark, getTheme()));
        statusText.setPadding(0, dpToPx(8), 0, 0);
        cardContent.addView(statusText);

        // Sensor ID
        TextView idText = new TextView(this);
        idText.setText("ID: " + sensor.id);
        idText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        idText.setTextColor(getResources().getColor(android.R.color.darker_gray, getTheme()));
        idText.setPadding(0, dpToPx(8), 0, 0);
        cardContent.addView(idText);

        // Room
        TextView roomText = new TextView(this);
        roomText.setText("Room: " + (sensor.room.isEmpty() ? "Unassigned" : sensor.room));
        roomText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        roomText.setTextColor(getResources().getColor(android.R.color.darker_gray, getTheme()));
        roomText.setPadding(0, dpToPx(4), 0, 0);
        cardContent.addView(roomText);

        // Firebase Status
        TextView firebaseStatusText = new TextView(this);
        firebaseStatusText.setText("Firebase Status: " + sensor.firebaseStatus);
        firebaseStatusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        firebaseStatusText.setTextColor(getResources().getColor(android.R.color.darker_gray, getTheme()));
        firebaseStatusText.setPadding(0, dpToPx(4), 0, 0);
        cardContent.addView(firebaseStatusText);

        // Location
        TextView locationText = new TextView(this);
        locationText.setText("Location: " + sensor.location);
        locationText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        locationText.setTextColor(getResources().getColor(android.R.color.darker_gray, getTheme()));
        locationText.setPadding(0, dpToPx(4), 0, 0);
        cardContent.addView(locationText);

        // Last Seen
        TextView lastSeenText = new TextView(this);
        lastSeenText.setText("Last Seen: " + formatTimestamp(sensor.lastSeen));
        lastSeenText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        lastSeenText.setTextColor(getResources().getColor(android.R.color.darker_gray, getTheme()));
        lastSeenText.setPadding(0, dpToPx(4), 0, 0);
        cardContent.addView(lastSeenText);

        card.addView(cardContent);
        return card;
    }

    private int dpToPx(int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()));
    }

    private String formatTimestamp(String timestamp) {
        if (timestamp == null || timestamp.equals("N/A")) {
            return "N/A";
        }
        try {
            long milliseconds = Long.parseLong(timestamp);
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM d, yyyy h:mm a",
                    java.util.Locale.getDefault());
            return sdf.format(new java.util.Date(milliseconds));
        } catch (NumberFormatException e) {
            return timestamp;
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private static class SensorData {
        String id;
        String name;
        String type;
        boolean isConnected;
        String room;
        String timeSinceLastUpdate;
        String value; // Sound level from Firebase
        String firebaseStatus; // Status field from Firebase
        String location; // Location field from Firebase
        String lastSeen; // Last seen timestamp from Firebase

        SensorData() {
            this.room = "";
            this.timeSinceLastUpdate = "Never";
            this.value = "N/A";
            this.firebaseStatus = "unknown";
            this.location = "Unassigned";
            this.lastSeen = "N/A";
        }
    }
}