package com.example.quietzone_app;

import android.os.Bundle;
import android.util.Log; // Required for error logging
import android.widget.TextView; // Required to talk to your UI

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

// --- Firebase Imports ---
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Handle screen padding for modern displays
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // --- 1. Link the Java variable to the XML ID ---
        TextView statusTextView = findViewById(R.id.soundText);

        // --- 2. Initialize Firebase ---
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        // Make sure this path matches exactly what your bridge.py sends!
        DatabaseReference myRef = database.getReference("sound_data/live/sensor_1");

        // --- 3. Listen for Live Data ---
        myRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                // Check if data actually exists to avoid crashes
                if (dataSnapshot.exists()) {
                    // Assuming your Firebase structure is: sensor_1 -> value: 75
                    Object valObj = dataSnapshot.child("value").getValue();
                    if (valObj != null) {
                        String value = valObj.toString();
                        statusTextView.setText("Noise Level: " + value + " dB");
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Log the error if something goes wrong (like permissions)
                Log.w("QuietZone", "Firebase read failed.", error.toException());
            }
        });
    }
}