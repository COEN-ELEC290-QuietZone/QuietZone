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
import java.util.Locale;
import java.util.Map;

public class AdminDashboardActivity extends AppCompatActivity {

    private LinearLayout sensorsContainer;
    private Handler refreshHandler;
    private Runnable refreshRunnable;
    private static final long REFRESH_INTERVAL_MS = 2000; // Update every 2 seconds
    private static final int DISCONNECTION_THRESHOLD = 5; // Mark disconnected after 5 missed refreshes
    
    private DatabaseReference liveSensorsRef;
    private ValueEventListener liveSensorsListener;
    private Map<String, Integer> sensorMissedRefreshCount = new HashMap<>(); // Track consecutive refreshes without data

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
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
        // Listen to Firebase for real-time sensor data
        liveSensorsRef = FirebaseDatabase.getInstance().getReference("sound_data/live");
        liveSensorsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Just receive the data, the refreshSensorStatus will process it
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

    private void loadSensorStatus() {
        sensorsContainer.removeAllViews();
        Map<String, SensorData> sensors = getSensorData();

        if (sensors.isEmpty()) {
            showNoSensorsMessage();
        } else {
            displaySensors(sensors);
        }
    }

    private void refreshSensorStatus() {
        // Get current Firebase snapshot to check which sensors are actively sending data
        liveSensorsRef.get().addOnSuccessListener(snapshot -> {
            // Get list of sensors currently sending data to Firebase
            java.util.Set<String> activeSensorsInFirebase = new java.util.HashSet<>();
            for (DataSnapshot child : snapshot.getChildren()) {
                String sensorKey = child.getKey();
                if (sensorKey != null && !sensorKey.trim().isEmpty()) {
                    activeSensorsInFirebase.add(sensorKey);
                }
            }
            
            // Get all configured sensors
            android.content.SharedPreferences prefs = getSharedPreferences("sensor_data", Context.MODE_PRIVATE);
            String allConfigured = prefs.getString("all_configured_ids", "");
            
            if (!allConfigured.isEmpty()) {
                String[] configuredIds = allConfigured.split(",");
                
                // Update missed refresh count for each sensor
                for (String id : configuredIds) {
                    if (!id.trim().isEmpty()) {
                        if (activeSensorsInFirebase.contains(id.trim())) {
                            // Sensor is currently sending data - reset counter
                            sensorMissedRefreshCount.put(id.trim(), 0);
                        } else {
                            // Sensor is not in Firebase data - increment miss counter
                            int missCount = sensorMissedRefreshCount.getOrDefault(id.trim(), 0);
                            sensorMissedRefreshCount.put(id.trim(), missCount + 1);
                        }
                    }
                }
            }
            
            // Reload the display with updated status
            sensorsContainer.removeAllViews();
            Map<String, SensorData> sensors = getSensorData();
            
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
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(16, 8, 16, 8);
        card.setLayoutParams(cardParams);
        card.setCardElevation(dpToPx(4));

        LinearLayout cardContent = new LinearLayout(this);
        cardContent.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
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
        statusText.setTextColor(sensor.isConnected ?
                getResources().getColor(android.R.color.holo_green_dark, getTheme()) :
                getResources().getColor(android.R.color.holo_red_dark, getTheme()));
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

        // Last Update
        TextView lastUpdateText = new TextView(this);
        lastUpdateText.setText("Last Update: " + sensor.timeSinceLastUpdate);
        lastUpdateText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        lastUpdateText.setTextColor(getResources().getColor(android.R.color.darker_gray, getTheme()));
        lastUpdateText.setPadding(0, dpToPx(4), 0, 0);
        cardContent.addView(lastUpdateText);

        card.addView(cardContent);
        return card;
    }

    private Map<String, SensorData> getSensorData() {
        Map<String, SensorData> sensors = new HashMap<>();
        
        // Get sensor names from SharedPreferences
        android.content.SharedPreferences prefs = getSharedPreferences("sensor_data", Context.MODE_PRIVATE);
        String allConfigured = prefs.getString("all_configured_ids", "");
        
        if (!allConfigured.isEmpty()) {
            String[] ids = allConfigured.split(",");
            for (String id : ids) {
                if (!id.trim().isEmpty()) {
                    SensorData sensor = new SensorData();
                    sensor.id = id.trim();
                    sensor.name = prefs.getString(id.trim() + "_name", "Unknown Sensor_" + id.trim());
                    sensor.type = prefs.getString(id.trim() + "_type", "Unknown");
                    sensor.room = prefs.getString(id.trim() + "_room", "");
                    
                    // Check if sensor missed 5 or more refreshes
                    int missCount = sensorMissedRefreshCount.getOrDefault(id.trim(), 0);
                    
                    if (missCount >= DISCONNECTION_THRESHOLD) {
                        // Sensor missed 5 refreshes - mark as disconnected
                        sensor.isConnected = false;
                        sensor.timeSinceLastUpdate = "Disconnected (" + missCount + " missed refreshes)";
                    } else if (missCount > 0) {
                        // Sensor is missing data but not yet at threshold
                        sensor.isConnected = true; // Still show as connected while we're counting
                        sensor.timeSinceLastUpdate = "Active (" + missCount + "/" + DISCONNECTION_THRESHOLD + " missed)";
                    } else {
                        // Sensor is actively sending data
                        sensor.isConnected = true;
                        sensor.timeSinceLastUpdate = "Active";
                    }
                    
                    sensors.put(id.trim(), sensor);
                }
            }
        }

        return sensors;
    }

    private String formatTimeDifference(long diffMs) {
        if (diffMs <= 0) {
            return "Never";
        }
        
        long seconds = diffMs / 1000;
        if (seconds < 60) {
            return seconds + " seconds ago";
        }
        
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + " minutes ago";
        }
        
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + " hours ago";
        }
        
        long days = hours / 24;
        return days + " days ago";
    }

    private int dpToPx(int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()));
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    // Inner class to hold sensor data
    private static class SensorData {
        String id;
        String name;
        String type;
        boolean isConnected;
        String room;
        String timeSinceLastUpdate;
        long lastSeenPercentile;

        SensorData() {
            this.room = "";
            this.timeSinceLastUpdate = "Never";
            this.lastSeenPercentile = 0;
        }
    }
}
