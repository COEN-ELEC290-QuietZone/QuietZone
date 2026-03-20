package com.example.quietzone_app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    private Button loginButton, noiseButton, settingsButton;

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

        // Initialize buttons
        loginButton = findViewById(R.id.LoginButton);
        noiseButton = findViewById(R.id.NoiseButton);
        settingsButton = findViewById(R.id.SettingsButton);

        // Navigate to LoginActivity
        loginButton.setOnClickListener(v -> startActivity(new Intent(this, LoginActivity.class)));

        // Navigate to NoiseActivity (where sensor data is displayed)
        noiseButton.setOnClickListener(v -> startActivity(new Intent(this, NoiseActivity.class)));

        // Navigate to SettingsActivity
        settingsButton.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
    }
}