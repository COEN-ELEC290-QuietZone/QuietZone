package com.example.quietzone_app;

import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.GridLayout;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class SettingsActivity extends AppCompatActivity {

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
            themeButton.setOnClickListener(v -> {
                ThemeHelper.toggleTheme(this);
            });
        }
    }

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
        });
    }

    private void applySquareTileSize(GridLayout settingsGrid) {
        if (settingsGrid.getChildCount() == 0) {
            return;
        }

        int availableWidth = settingsGrid.getWidth() - settingsGrid.getPaddingLeft() - settingsGrid.getPaddingRight();
        if (availableWidth <= 0) {
            return;
        }

        View sampleChild = settingsGrid.getChildAt(0);
        GridLayout.LayoutParams sampleLp = (GridLayout.LayoutParams) sampleChild.getLayoutParams();
        int horizontalGap = sampleLp.leftMargin + sampleLp.rightMargin;
        int minTileSize = dpToPx(96);
        int minCellSize = minTileSize + horizontalGap;

        int columns = Math.max(1, availableWidth / Math.max(1, minCellSize));
        settingsGrid.setColumnCount(columns);

        int tileSize = Math.max(1, (availableWidth - (columns * horizontalGap)) / columns);

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
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

}