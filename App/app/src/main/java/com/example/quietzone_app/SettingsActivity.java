package com.example.quietzone_app;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
<<<<<<< Updated upstream
import android.util.TypedValue;
import android.view.View;
import android.widget.GridLayout;
=======
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.widget.GridLayout;
import android.widget.Toast;
>>>>>>> Stashed changes

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
<<<<<<< Updated upstream
=======
import com.google.android.material.button.MaterialButton;
import com.google.firebase.messaging.FirebaseMessaging;
>>>>>>> Stashed changes

public class SettingsActivity extends AppCompatActivity {

    private static final int NOTIF_PERMISSION_REQUEST = 100;
    private MaterialButton notificationButton;

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

        applyCalculatedGridTileSize();

        View deviceSetupButton = findViewById(R.id.buttonDeviceSetup);
        View logoutButton = findViewById(R.id.button10);
        View themeButton = findViewById(R.id.button2);

        if (deviceSetupButton != null && !SessionState.isAdmin(this)) {
            deviceSetupButton.setVisibility(View.GONE);
        }

        if (deviceSetupButton != null) {
            deviceSetupButton.setOnClickListener(v -> {
                Intent intent = new Intent(SettingsActivity.this, DeviceSetupActivity.class);
                startActivity(intent);
            });
        }

        if (logoutButton != null) {
            logoutButton.setOnClickListener(v -> LogoutManager.performLogout(SettingsActivity.this));
        }

        if (themeButton != null) {
<<<<<<< Updated upstream
            themeButton.setOnClickListener(v -> {
                ThemeHelper.toggleTheme(this);
            });
=======
            themeButton.setOnClickListener(v -> ThemeHelper.toggleTheme(this));
>>>>>>> Stashed changes
        }
    }

<<<<<<< Updated upstream
    private void applyCalculatedGridTileSize() {
        GridLayout settingsGrid = findViewById(R.id.settingsGrid);
        if (settingsGrid == null) {
            return;
        }

        settingsGrid.post(() -> applySquareTileSize(settingsGrid));
        settingsGrid.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft) {
                applySquareTileSize(settingsGrid);
            }
=======
        View adminDashboardButton = findViewById(R.id.buttonAdminDashboard);
        if (adminDashboardButton != null) {
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
        notificationButton = findViewById(R.id.button3);
        if (notificationButton != null) {
            notificationButton.setOnClickListener(v -> {
                if (isNotificationPermissionGranted()) {
                    showDisablePermissionDialog();
                } else {
                    requestNotificationPermission();
                }
                handleFcmToken();
            });
            updateNotificationButtonText();
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
            } else {
                Toast.makeText(this, "Notifications denied", Toast.LENGTH_SHORT).show();
            }
            updateNotificationButtonText();
        }
    }

    private void updateNotificationButtonText() {
        boolean enabled = isNotificationPermissionGranted();
        notificationButton.setText(enabled ? "Notifications: Enabled" : "Notifications: Disabled");
    }

    private void applyCalculatedGridTileSize() {
        GridLayout settingsGrid = findViewById(R.id.settingsGrid);
        if (settingsGrid == null) return;

        settingsGrid.post(() -> applySquareTileSize(settingsGrid));
        settingsGrid.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft) applySquareTileSize(settingsGrid);
>>>>>>> Stashed changes
        });
    }

    private void applySquareTileSize(GridLayout settingsGrid) {
<<<<<<< Updated upstream
        if (settingsGrid.getChildCount() == 0) {
            return;
        }

        int availableWidth = settingsGrid.getWidth() - settingsGrid.getPaddingLeft() - settingsGrid.getPaddingRight();
        if (availableWidth <= 0) {
            return;
        }
=======
        if (settingsGrid.getChildCount() == 0) return;

        int availableWidth = settingsGrid.getWidth() - settingsGrid.getPaddingLeft() - settingsGrid.getPaddingRight();
        if (availableWidth <= 0) return;
>>>>>>> Stashed changes

        View sampleChild = settingsGrid.getChildAt(0);
        GridLayout.LayoutParams sampleLp = (GridLayout.LayoutParams) sampleChild.getLayoutParams();
        int horizontalGap = sampleLp.leftMargin + sampleLp.rightMargin;
        int minTileSize = dpToPx(96);
        int minCellSize = minTileSize + horizontalGap;

        int columns = Math.max(1, availableWidth / Math.max(1, minCellSize));
        settingsGrid.setColumnCount(columns);

        int tileSize = Math.max(1, (availableWidth - (columns * horizontalGap)) / columns);
<<<<<<< Updated upstream

=======
>>>>>>> Stashed changes
        for (int i = 0; i < settingsGrid.getChildCount(); i++) {
            View child = settingsGrid.getChildAt(i);
            GridLayout.LayoutParams lp = (GridLayout.LayoutParams) child.getLayoutParams();
            lp.width = tileSize;
            lp.height = tileSize;
            child.setLayoutParams(lp);
        }
    }

    private int dpToPx(int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()));
<<<<<<< Updated upstream
=======
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateNotificationButtonText();
>>>>>>> Stashed changes
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
<<<<<<< Updated upstream

=======
>>>>>>> Stashed changes
}