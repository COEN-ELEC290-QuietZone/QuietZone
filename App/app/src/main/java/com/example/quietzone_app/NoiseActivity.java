package com.example.quietzone_app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.github.anastr.speedviewlib.SpeedView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class NoiseActivity extends AppCompatActivity {

    private static final String[] ROOMS = { "Room 1", "Room 2" };
    private static final Class<?>[] ROOM_ACTIVITIES = { Room1Activity.class, Room2Activity.class };
    private static final String[] SENSOR_PATHS = {
            "sound_data/live/sensor_1",
            "sound_data/live/sensor_2"
    };

    private final SpeedView[] childSpeedViews = new SpeedView[ROOMS.length];
    private final TextView[] childSoundTexts = new TextView[ROOMS.length];
    private final TextView[] childStatusTexts = new TextView[ROOMS.length];
    private final float[] latestSoundLevels = { Float.NaN, Float.NaN };
    private final DatabaseReference[] sensorRefs = new DatabaseReference[ROOMS.length];
    private final ValueEventListener[] sensorListeners = new ValueEventListener[ROOMS.length];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_noise);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Toolbar myToolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Noise Levels");
        }

        for (int i = 0; i < ROOMS.length; i++) {
            final int idx = i;
            sensorRefs[i] = FirebaseDatabase.getInstance().getReference(SENSOR_PATHS[i]);
            sensorListeners[i] = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (!dataSnapshot.exists()) {
                        if (childSoundTexts[idx] != null) {
                            childSoundTexts[idx].setText("Waiting...");
                        }
                        if (childStatusTexts[idx] != null) {
                            childStatusTexts[idx].setText("Status: Waiting...");
                            childStatusTexts[idx].setTextColor(getResources().getColor(R.color.app_on_surface, getTheme()));
                        }
                        return;
                    }
                    try {
                        DataSnapshot valueSnapshot = dataSnapshot.child("value");
                        Object value = valueSnapshot.exists() ? valueSnapshot.getValue() : dataSnapshot.getValue();
                        if (value != null) {
                            float soundLevel = Float.parseFloat(value.toString());
                            latestSoundLevels[idx] = soundLevel;
                            if (childSoundTexts[idx] != null) {
                                childSoundTexts[idx].setText(String.format("%.1f dB", soundLevel));
                            }
                            if (childSpeedViews[idx] != null) {
                                childSpeedViews[idx].speedTo(soundLevel);
                            }
                            if (childStatusTexts[idx] != null) {
                                applyStatusText(childStatusTexts[idx], soundLevel);
                            }
                        }
                    } catch (Exception e) {
                        Log.e("NoiseActivity", "Error parsing sensor_" + (idx + 1), e);
                        if (childSoundTexts[idx] != null) {
                            childSoundTexts[idx].setText("Error");
                        }
                        if (childStatusTexts[idx] != null) {
                            childStatusTexts[idx].setText("Status: Error");
                            childStatusTexts[idx].setTextColor(getResources().getColor(R.color.app_error, getTheme()));
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    if (childSoundTexts[idx] != null) {
                        childSoundTexts[idx].setText("Error");
                    }
                    if (childStatusTexts[idx] != null) {
                        childStatusTexts[idx].setText("Status: Error");
                        childStatusTexts[idx].setTextColor(getResources().getColor(R.color.app_error, getTheme()));
                    }
                }
            };
            sensorRefs[i].addValueEventListener(sensorListeners[i]);
        }

        ExpandableListView expandableListView = findViewById(R.id.roomExpandableList);
        expandableListView.setIndicatorBoundsRelative(dpToPx(16), dpToPx(48));
        expandableListView.setAdapter(new RoomExpandableAdapter());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (int i = 0; i < sensorRefs.length; i++) {
            if (sensorRefs[i] != null && sensorListeners[i] != null) {
                sensorRefs[i].removeEventListener(sensorListeners[i]);
            }
        }
    }

    private class RoomExpandableAdapter extends BaseExpandableListAdapter {

        @Override
        public int getGroupCount() {
            return ROOMS.length;
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            return 1;
        }

        @Override
        public Object getGroup(int groupPosition) {
            return ROOMS[groupPosition];
        }

        @Override
        public Object getChild(int groupPosition, int childPosition) {
            return "details";
        }

        @Override
        public long getGroupId(int groupPosition) {
            return groupPosition;
        }

        @Override
        public long getChildId(int groupPosition, int childPosition) {
            return groupPosition;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
            TextView tv = convertView instanceof TextView ? (TextView) convertView : new TextView(NoiseActivity.this);
            tv.setText(ROOMS[groupPosition]);
            tv.setTextSize(18);
            tv.setPadding(dpToPx(56), dpToPx(20), dpToPx(16), dpToPx(20));
            tv.setTypeface(null, Typeface.BOLD);
            tv.setTextColor(getResources().getColor(R.color.app_on_surface, getTheme()));
            return tv;
        }

        @Override
        public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
                View convertView, ViewGroup parent) {
            View view = LayoutInflater.from(NoiseActivity.this)
                    .inflate(R.layout.list_item_room_child, parent, false);

            SpeedView sv = view.findViewById(R.id.childSpeedView);
            TextView soundTv = view.findViewById(R.id.childSoundText);
            TextView statusTv = view.findViewById(R.id.childStatusText);
            Button openRoomButton = view.findViewById(R.id.childOpenRoomButton);

            sv.setMaxSpeed(120);
            sv.setUnit("dB");
            int onSurface = getResources().getColor(R.color.app_on_surface, getTheme());
            sv.setSpeedTextColor(onSurface);
            sv.setTextColor(onSurface);
            sv.setUnitTextColor(onSurface);
            float strokePx = 6 * getResources().getDisplayMetrics().density;
            sv.setSpeedometerWidth(strokePx);
            sv.setSpeedTextSize(dpToPx(20));
            sv.setUnitTextSize(dpToPx(10));

            childSpeedViews[groupPosition] = sv;
            childSoundTexts[groupPosition] = soundTv;
            childStatusTexts[groupPosition] = statusTv;
                openRoomButton.setOnClickListener(v ->
                    startActivity(new Intent(NoiseActivity.this, ROOM_ACTIVITIES[groupPosition])));

            float latest = latestSoundLevels[groupPosition];
            if (Float.isNaN(latest)) {
                soundTv.setText("-- dB");
                statusTv.setText("Status: Waiting...");
                statusTv.setTextColor(onSurface);
            } else {
                soundTv.setText(String.format("%.1f dB", latest));
                sv.speedTo(latest);
                applyStatusText(statusTv, latest);
            }

            return view;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return false;
        }
    }

    private void applyStatusText(TextView statusView, float dB) {
        if (dB < 50) {
            statusView.setText("Status: Quiet");
            statusView.setTextColor(Color.parseColor("#4CAF50"));
        } else if (dB < 70) {
            statusView.setText("Status: Moderate");
            statusView.setTextColor(Color.parseColor("#FF9800"));
        } else {
            statusView.setText("Status: Loud");
            statusView.setTextColor(Color.parseColor("#F44336"));
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}