package com.example.quietzone_app;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.github.anastr.speedviewlib.SpeedView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class RoomActivity extends AppCompatActivity {

    public static final String EXTRA_SENSOR_KEY = "extra_sensor_key";
    public static final String EXTRA_ROOM_NAME = "extra_room_name";

    private SpeedView speedView;
    private TextView speedLabel;
    private TextView soundText;
    private TextView statusText;

    private DatabaseReference roomRef;
    private ValueEventListener roomListener;
    private FirebaseListenerRegistry.ListenerHandle roomListenerHandle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.room_template);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        String sensorKey = getIntent().getStringExtra(EXTRA_SENSOR_KEY);
        if (sensorKey == null || sensorKey.trim().isEmpty()) {
            sensorKey = "sensor_1";
        }

        String roomName = getIntent().getStringExtra(EXTRA_ROOM_NAME);
        if (roomName == null || roomName.trim().isEmpty()) {
            roomName = getString(R.string.room_name_placeholder);
        }

        Toolbar myToolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);
        int toolbarTextColor = getResources().getColor(R.color.app_on_primary, getTheme());
        myToolbar.setTitleTextColor(toolbarTextColor);
        myToolbar.setSubtitleTextColor(toolbarTextColor);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(roomName);
            if (myToolbar.getNavigationIcon() != null) {
                myToolbar.getNavigationIcon().setTint(toolbarTextColor);
            }
        }
        if (myToolbar.getOverflowIcon() != null) {
            myToolbar.getOverflowIcon().setTint(toolbarTextColor);
        }

        speedView = findViewById(R.id.speedView);
        speedLabel = findViewById(R.id.speedLabel);
        soundText = findViewById(R.id.soundText);
        statusText = findViewById(R.id.statusText);

        speedLabel.setText(getString(R.string.room_noise_level_format, roomName));

        int onSurface = getResources().getColor(R.color.app_on_surface, getTheme());
        speedView.setMaxSpeed(120);
        speedView.setUnit("dB");
        speedView.setSpeedTextColor(onSurface);
        speedView.setTextColor(onSurface);
        speedView.setUnitTextColor(onSurface);

        roomRef = FirebaseDatabase.getInstance().getReference("sound_data/live").child(sensorKey);
        roomListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (!dataSnapshot.exists()) {
                    soundText.setText(getString(R.string.room_status_waiting));
                    return;
                }

                try {
                    DataSnapshot valueSnapshot = dataSnapshot.child("value");
                    Object value = valueSnapshot.exists() ? valueSnapshot.getValue() : dataSnapshot.getValue();
                    if (value != null) {
                        float soundLevel = Float.parseFloat(value.toString());
                        soundText.setText(getString(R.string.room_sound_level_display_format, soundLevel));
                        speedView.speedTo(soundLevel);
                        updateStatus(statusText, soundLevel);
                    }
                } catch (Exception e) {
                    Log.e("RoomActivity", "Error parsing sensor data", e);
                    soundText.setText(getString(R.string.room_status_error));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                soundText.setText(getString(R.string.room_database_error_format, error.getMessage()));
            }
        };
        roomRef.addValueEventListener(roomListener);
        roomListenerHandle = FirebaseListenerRegistry.register(roomRef, roomListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (roomListenerHandle != null) {
            roomListenerHandle.detachAndUnregister();
            roomListenerHandle = null;
        } else if (roomRef != null && roomListener != null) {
            roomRef.removeEventListener(roomListener);
        }
    }

    private void updateStatus(TextView view, float dB) {
        if (dB < 50) {
            view.setText(R.string.room_group_quiet);
            view.setTextColor(getResources().getColor(R.color.status_quiet, getTheme()));
        } else if (dB < 70) {
            view.setText(R.string.room_group_moderate);
            view.setTextColor(getResources().getColor(R.color.status_moderate, getTheme()));
        } else {
            view.setText(R.string.room_group_loud);
            view.setTextColor(getResources().getColor(R.color.status_loud, getTheme()));
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
