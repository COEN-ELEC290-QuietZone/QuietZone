package com.example.userinterface_ver0;

import android.graphics.Color;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.github.anastr.speedviewlib.ProgressiveGauge;
import com.github.anastr.speedviewlib.SpeedView;
import com.github.anastr.speedviewlib.LinearGauge;

public class NoiseActivity extends AppCompatActivity {

    private ProgressiveGauge gauge;
    private SpeedView speedView;

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

        // --- ProgressiveGauge setup ---
        gauge = findViewById(R.id.gauge);
        gauge.setMaxSpeed(120);
        gauge.setUnit("dB");
        gauge.setSpeedTextColor(Color.BLACK);
        gauge.speedTo(50f); // dummy value in dB

        // --- SpeedView setup ---
        speedView = findViewById(R.id.speedView);
        speedView.setMaxSpeed(120);
        speedView.setUnit("dB");
        speedView.setSpeedTextColor(Color.BLACK);
        speedView.speedTo(75f); // dummy value in dB
    }
}