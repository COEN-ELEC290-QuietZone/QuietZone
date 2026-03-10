package com.example.quietzone_app;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.github.anastr.speedviewlib.ProgressiveGauge;
import com.github.anastr.speedviewlib.SpeedView;
import com.github.anastr.speedviewlib.LinearGauge;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
// Toolbar
import androidx.appcompat.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import android.content.Intent;

public class NoiseActivity extends AppCompatActivity {

    private SpeedView speedView;
    private TextView soundText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_noise);

        // Edge-to-edge padding
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;

        });

        // (2) TOOLBAR (Sets the toolbar as the app bar for NoiseActivity)
        Toolbar myToolbar = (Toolbar) findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);

        if (getSupportActionBar() != null){

            getSupportActionBar().setDisplayHomeAsUpEnabled(true); //shows left arrow
            getSupportActionBar().setTitle("Room 2");


        }


        // --- Get references to UI elements ---
        soundText = findViewById(R.id.soundText);
        speedView = findViewById(R.id.speedView);

        // --- SpeedView setup ---
        speedView.setMaxSpeed(120);
        speedView.setUnit("dB");
        speedView.setSpeedTextColor(Color.BLACK);
        speedView.speedTo(75f); // dummy value in dB

        // --- Initialize Firebase and listen for live data ---
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference myRef = database.getReference("sound_data/live/sensor_2");

        Log.d("NoiseActivity", "Firebase listener attached to: sound_data/live/sensor_2");

        myRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Log.d("NoiseActivity", "onDataChange called. Exists: " + dataSnapshot.exists());
                if (dataSnapshot.exists()) {
                    try {
                        // The data structure is {value: 450.0, timestamp: 177261431970}
                        // We need to extract the "value" field
                        DataSnapshot valueSnapshot = dataSnapshot.child("value");

                        if (valueSnapshot.exists()) {
                            Object value = valueSnapshot.getValue();
                            Log.d("NoiseActivity", "Raw value: " + value);

                            if (value != null) {
                                float soundLevel;
                                if (value instanceof Double) {
                                    soundLevel = ((Double) value).floatValue();
                                } else if (value instanceof Long) {
                                    soundLevel = ((Long) value).floatValue();
                                } else {
                                    soundLevel = Float.parseFloat(value.toString());
                                }

                                Log.d("NoiseActivity", "Parsed sound level: " + soundLevel);

                                // Update TextView
                                soundText.setText("Sound Level: " + String.format("%.1f", soundLevel) + " dB");

                                // Update gauge
                                speedView.speedTo(soundLevel);
                            }
                        } else {
                            // Fallback: try to parse the whole object as a number (for simple value
                            // structure)
                            Object value = dataSnapshot.getValue();
                            Log.d("NoiseActivity", "Raw value (no 'value' field): " + value);
                            if (value != null) {
                                float soundLevel = Float.parseFloat(value.toString());
                                soundText.setText("Sound Level: " + String.format("%.1f", soundLevel) + " dB");
                                speedView.speedTo(soundLevel);
                            }
                        }
                    } catch (Exception e) {
                        Log.e("NoiseActivity", "Error parsing data", e);
                        soundText.setText("Error reading data: " + e.getMessage());
                    }
                } else {
                    Log.w("NoiseActivity", "No data at path sound_data/live/sensor_2");
                    soundText.setText("Waiting for sensor...");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("NoiseActivity", "Database error: " + databaseError.getMessage());
                soundText.setText("Database Error: " + databaseError.getMessage());
            }
        });
    }

    // (3) Toolbar

    //goes back when left arrow pressed
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    //create menu items in the toolbar
    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu){
        getMenuInflater().inflate(R.menu.menu_noiseactivity, menu);
        return true;
    }

    //what happens when option is clicked
    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        int id = item.getItemId();

        if (id == R.id.action_room1){
            Toast.makeText(this, "Now viewing Room 1", Toast.LENGTH_SHORT).show();
            //Click logic here
            Intent intent = new Intent(NoiseActivity.this, LoginActivity.class);
            startActivity(intent);
            return true;
        }

        if (id == R.id.action_room2){
            Toast.makeText(this, "Now viewing Room 2", Toast.LENGTH_SHORT).show();
            //Click logic here
            Intent intent = new Intent(NoiseActivity.this, NoiseActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}