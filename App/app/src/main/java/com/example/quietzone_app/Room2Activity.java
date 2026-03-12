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

public class Room2Activity extends AppCompatActivity {

    private SpeedView speedView;
    private TextView soundText;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.room2);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Toolbar myToolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);
        int toolbarTextColor = getResources().getColor(R.color.app_on_primary, getTheme());
        myToolbar.setTitleTextColor(toolbarTextColor);
        myToolbar.setSubtitleTextColor(toolbarTextColor);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Room 2");
            if (myToolbar.getNavigationIcon() != null) {
                myToolbar.getNavigationIcon().setTint(toolbarTextColor);
            }
        }
        if (myToolbar.getOverflowIcon() != null) {
            myToolbar.getOverflowIcon().setTint(toolbarTextColor);
        }

        speedView = findViewById(R.id.speedView);
        soundText = findViewById(R.id.soundText);
        statusText = findViewById(R.id.statusText);

        int onSurface = getResources().getColor(R.color.app_on_surface, getTheme());
        speedView.setMaxSpeed(120);
        speedView.setUnit("dB");
        speedView.setSpeedTextColor(onSurface);
        speedView.setTextColor(onSurface);
        speedView.setUnitTextColor(onSurface);

        DatabaseReference room2Ref = FirebaseDatabase.getInstance().getReference("sound_data/live/sensor_2");
        room2Ref.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (!dataSnapshot.exists()) {
                    soundText.setText("Waiting for sensor...");
                    return;
                }

                try {
                    DataSnapshot valueSnapshot = dataSnapshot.child("value");
                    Object value = valueSnapshot.exists() ? valueSnapshot.getValue() : dataSnapshot.getValue();
                    if (value != null) {
                        float soundLevel = Float.parseFloat(value.toString());
                        soundText.setText("Sound Level: " + String.format("%.1f", soundLevel) + " dB");
                        speedView.speedTo(soundLevel);
                        updateStatus(statusText, soundLevel);
                    }
                } catch (Exception e) {
                    Log.e("Room2Activity", "Error parsing sensor data", e);
                    soundText.setText("Error reading data");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                soundText.setText("Database Error: " + error.getMessage());
            }
        });
    }

    private void updateStatus(TextView view, float dB) {
        if (dB < 50) {
            view.setText("Quiet");
            view.setTextColor(getResources().getColor(R.color.status_quiet, getTheme()));
        } else if (dB < 70) {
            view.setText("Moderate");
            view.setTextColor(getResources().getColor(R.color.status_moderate, getTheme()));
        } else {
            view.setText("Loud");
            view.setTextColor(getResources().getColor(R.color.status_loud, getTheme()));
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
