package com.example.quietzone_app;

import android.graphics.Color;
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

public class Room1Activity extends AppCompatActivity {

    private SpeedView speedView;
    private TextView soundText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.room1);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Toolbar myToolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Room 1");
        }

        speedView = findViewById(R.id.speedView);
        soundText = findViewById(R.id.soundText);

        speedView.setMaxSpeed(120);
        speedView.setUnit("dB");
        speedView.setSpeedTextColor(Color.BLACK);

        DatabaseReference room1Ref = FirebaseDatabase.getInstance().getReference("sound_data/live/sensor_1");
        room1Ref.addValueEventListener(new ValueEventListener() {
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
                    }
                } catch (Exception e) {
                    Log.e("Room1Activity", "Error parsing sensor data", e);
                    soundText.setText("Error reading data");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                soundText.setText("Database Error: " + error.getMessage());
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
