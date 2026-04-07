package com.example.quietzone_app;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;
import android.widget.TextView;

import com.google.firebase.messaging.FirebaseMessaging;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsActivity extends AppCompatActivity {

    private static final int NOTIF_PERMISSION_REQUEST = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);

        Toolbar myToolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);
        int toolbarTextColor = getResources().getColor(R.color.app_on_primary, getTheme());
        myToolbar.setTitleTextColor(toolbarTextColor);
        myToolbar.setSubtitleTextColor(toolbarTextColor);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            getSupportActionBar().setTitle("Settings");
        }
        if (myToolbar.getOverflowIcon() != null) {
            myToolbar.getOverflowIcon().setTint(toolbarTextColor);
        }

        BottomNavigationView bottomNavigation = findViewById(R.id.bottomNavigation);
        bottomNavigation.setSelectedItemId(R.id.nav_settings);
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                startActivity(new Intent(SettingsActivity.this, NoiseActivity.class));
                finish();
                return true;
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(SettingsActivity.this, ProfileActivity.class));
                finish();
                return true;
            } else if (id == R.id.nav_settings) {
                return true;
            }
            return false;
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Update profile name based on user type
        TextView profileName = findViewById(R.id.profileName);
        if (profileName != null) {
            if (SessionState.isAdmin(this)) {
                profileName.setText("Admin Settings");
            } else {
                profileName.setText("Student Settings");
            }
        }

        View logoutButton = findViewById(R.id.button10);
        View themeButton = findViewById(R.id.button2);
        SwitchMaterial darkModeToggle = findViewById(R.id.darkModeToggle);
        TextView darkModeStatus = findViewById(R.id.darkModeStatus);

        // Update dark mode status on load
        updateDarkModeStatus(darkModeStatus);

        // Set initial switch state
        if (darkModeToggle != null) {
            darkModeToggle.setChecked(ThemeHelper.isDarkMode(this));
        }

        if (logoutButton != null) {
            logoutButton.setOnClickListener(v -> showLogoutConfirmationDialog());
        }

        if (darkModeToggle != null) {
            darkModeToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked != ThemeHelper.isDarkMode(this)) {
                    ThemeHelper.toggleTheme(this);
                    updateDarkModeStatus(darkModeStatus);
                }
            });
        }

        if (themeButton != null) {
            themeButton.setOnClickListener(v -> {
                if (darkModeToggle != null) {
                    darkModeToggle.setChecked(!darkModeToggle.isChecked());
                }
            });
        }

        View adminDashboardButton = findViewById(R.id.buttonAdminDashboard);

        if (adminDashboardButton != null) {
            // Only show admin dashboard button to admin users
            if (!SessionState.isAdmin(this)) {
                adminDashboardButton.setVisibility(View.GONE);
            } else {
                adminDashboardButton.setOnClickListener(v -> {
                    Intent intent = new Intent(SettingsActivity.this, AdminDashboardActivity.class);
                    startActivity(intent);
                });
            }
        }

        // Notifications button
        View notificationButton = findViewById(R.id.button3);
        if (notificationButton != null) {
            notificationButton.setOnClickListener(v -> {
                if (isNotificationPermissionGranted()) {
                    showDisablePermissionDialog();
                    handleFcmToken();
                } else {
                    requestNotificationPermission();
                }
            });
        }

        // Privacy button
        View privacyButton = findViewById(R.id.button4);
        if (privacyButton != null) {
            privacyButton.setOnClickListener(v -> {
                Intent intent = new Intent(SettingsActivity.this, PrivacyActivity.class);
                startActivity(intent);
            });
        }

        // Help button
        View helpButton = findViewById(R.id.button6);
        if (helpButton != null) {
            helpButton.setOnClickListener(v -> {
                Intent intent = new Intent(SettingsActivity.this, HelpActivity.class);
                startActivity(intent);
            });
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void showLogoutConfirmationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Log Out")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Log Out", (dialog, which) -> {
                    LogoutManager.performLogout(SettingsActivity.this);
                })
                .setNegativeButton("Stay Logged In", (dialog, which) -> {
                    dialog.dismiss();
                })
                .setCancelable(true)
                .show();
    }

    private void updateDarkModeStatus(TextView darkModeStatus) {
        if (darkModeStatus != null) {
            boolean isDarkMode = ThemeHelper.isDarkMode(this);
            if (isDarkMode) {
                darkModeStatus.setText("Dark mode on");
            } else {
                darkModeStatus.setText("Dark mode off");
            }
        }
    }

    private boolean isNotificationPermissionGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void handleFcmToken() {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) return;
            String token = task.getResult();

            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("FCM Token", token);
            clipboard.setPrimaryClip(clip);

            Toast.makeText(this, "FCM Token copied to clipboard!", Toast.LENGTH_SHORT).show();

            new AlertDialog.Builder(this)
                    .setTitle("FCM Token Copied")
                    .setMessage(token)
                    .setPositiveButton("OK", null)
                    .show();
        });
    }

    private void showDisablePermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Notifications Enabled")
                .setMessage("To disable notifications, please go to System Settings. Would you like to go there now?")
                .setPositiveButton("Go to Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIF_PERMISSION_REQUEST
            );
        } else {
            Toast.makeText(this, "Notifications allowed by default on this version", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIF_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notifications enabled", Toast.LENGTH_SHORT).show();
                handleFcmToken();
                showDisablePermissionDialog();
            } else {
                Toast.makeText(this, "Notifications denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

}