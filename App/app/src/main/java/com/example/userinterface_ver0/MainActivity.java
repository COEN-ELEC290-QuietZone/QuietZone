package com.example.userinterface_ver0;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    private Button loginButton, settingsButton, noiseButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Initialize buttons
        loginButton = findViewById(R.id.LoginButton);
        settingsButton = findViewById(R.id.SettingsButton);
        noiseButton = findViewById(R.id.NoiseButton);

        // Navigate to LoginActivity
        loginButton.setOnClickListener(v ->
                startActivity(new Intent(this, LoginActivity.class)));

        // Navigate to SettingsActivity
        settingsButton.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        // Navigate to NoiseActivity
        noiseButton.setOnClickListener(v ->
                startActivity(new Intent(this, NoiseActivity.class)));

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
}