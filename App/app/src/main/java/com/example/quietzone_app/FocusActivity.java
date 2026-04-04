package com.example.quietzone_app;

import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class FocusActivity extends AppCompatActivity {
    private TextView timerText;
    private MaterialButton pauseResumeButton, stopButton;

    private Handler handler = new Handler();
    private long startTime, timeInMilliseconds, timeSwapBuff, updatedTime = 0L;
    private boolean isRunning = false;
    private long sessionStartTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_focus);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        timerText = findViewById(R.id.timerText);
        pauseResumeButton = findViewById(R.id.pauseResumeButton);
        stopButton = findViewById(R.id.stopButton);

        sessionStartTime = System.currentTimeMillis();
        startTimer();

        pauseResumeButton.setOnClickListener(v -> {
            if (isRunning) {
                pauseTimer();
            } else {
                startTimer();
            }
        });

        stopButton.setOnClickListener(v -> endSession());
    }

    private void startTimer() {
        startTime = SystemClock.uptimeMillis();
        handler.postDelayed(updateTimerThread, 0);
        isRunning = true;
        pauseResumeButton.setText("Pause");
    }

    private void pauseTimer() {
        timeSwapBuff += timeInMilliseconds;
        handler.removeCallbacks(updateTimerThread);
        isRunning = false;
        pauseResumeButton.setText("Resume");
    }

    private void endSession() {
        pauseTimer();
        long totalSeconds = updatedTime / 1000;
        if (totalSeconds < 5) { // Minimum 5 seconds to save
            Toast.makeText(this, "Session too short to save!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        saveSessionToFirebase(totalSeconds);
    }

    private Runnable updateTimerThread = new Runnable() {
        public void run() {
            timeInMilliseconds = SystemClock.uptimeMillis() - startTime;
            updatedTime = timeSwapBuff + timeInMilliseconds;
            int secs = (int) (updatedTime / 1000);
            int mins = secs / 60;
            secs = secs % 60;
            timerText.setText(String.format(Locale.getDefault(), "%02d:%02d", mins, secs));
            handler.postDelayed(this, 0);
        }
    };

    private void saveSessionToFirebase(long durationSeconds) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("users").child(uid).child("focus_history");
        String dateKey = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        Map<String, Object> session = new HashMap<>();
        session.put("startTime", sessionStartTime);
        session.put("endTime", System.currentTimeMillis());
        session.put("duration", durationSeconds);
        session.put("date", dateKey);

        ref.push().setValue(session).addOnSuccessListener(aVoid -> {
            showSuccessDialog(durationSeconds);
        });
    }

    private void showSuccessDialog(long duration) {
        new AlertDialog.Builder(this)
                .setTitle("Session Completed!")
                .setMessage("You focused for " + (duration / 60) + "m " + (duration % 60) + "s")
                .setPositiveButton("Awesome", (d, w) -> finish())
                .setCancelable(false)
                .show();
    }
}