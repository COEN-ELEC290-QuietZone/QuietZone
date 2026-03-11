package com.example.quietzone_app;

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

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);

        Toolbar myToolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        applyCalculatedGridTileSize();
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