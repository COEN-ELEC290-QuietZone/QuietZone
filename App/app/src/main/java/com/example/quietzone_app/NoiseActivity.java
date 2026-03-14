package com.example.quietzone_app;

import android.content.Intent;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NoiseActivity extends AppCompatActivity {

    private final List<RoomItem> rooms = new ArrayList<>();
    private final Map<String, RoomItem> roomBySensorKey = new LinkedHashMap<>();

    private ExpandableListView expandableListView;
    private RoomExpandableAdapter roomAdapter;
    private DatabaseReference liveSensorsRef;
    private ValueEventListener liveSensorsListener;

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
        }

        expandableListView = findViewById(R.id.roomExpandableList);
        expandableListView.setGroupIndicator(null);
        roomAdapter = new RoomExpandableAdapter();
        expandableListView.setAdapter(roomAdapter);

        liveSensorsRef = FirebaseDatabase.getInstance().getReference("sound_data/live");
        liveSensorsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                syncRoomsFromDatabase(snapshot);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("NoiseActivity", "Failed loading room list", error.toException());
            }
        };
        liveSensorsRef.addValueEventListener(liveSensorsListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (liveSensorsRef != null && liveSensorsListener != null) {
            liveSensorsRef.removeEventListener(liveSensorsListener);
        }
        for (RoomItem room : rooms) {
            if (room.sensorRef != null && room.sensorListener != null) {
                room.sensorRef.removeEventListener(room.sensorListener);
            }
        }
    }

    private void syncRoomsFromDatabase(DataSnapshot liveSnapshot) {
        List<String> sensorKeys = new ArrayList<>();
        for (DataSnapshot child : liveSnapshot.getChildren()) {
            String sensorKey = child.getKey();
            if (sensorKey != null && !sensorKey.trim().isEmpty()) {
                sensorKeys.add(sensorKey);
            }
        }

        Collections.sort(sensorKeys, Comparator.comparingInt(this::extractSensorIndex));

        List<String> keysToRemove = new ArrayList<>();
        for (String existingKey : roomBySensorKey.keySet()) {
            if (!sensorKeys.contains(existingKey)) {
                keysToRemove.add(existingKey);
            }
        }
        for (String removedKey : keysToRemove) {
            RoomItem removed = roomBySensorKey.remove(removedKey);
            if (removed != null && removed.sensorRef != null && removed.sensorListener != null) {
                removed.sensorRef.removeEventListener(removed.sensorListener);
            }
        }

        for (String sensorKey : sensorKeys) {
            if (!roomBySensorKey.containsKey(sensorKey)) {
                RoomItem room = new RoomItem();
                room.sensorKey = sensorKey;
                room.roomName = toRoomName(sensorKey);
                room.targetActivity = toRoomActivity(sensorKey);
                room.latestSoundLevel = Float.NaN;
                roomBySensorKey.put(sensorKey, room);
                attachSensorListener(room);
            }
        }

        rooms.clear();
        for (String sensorKey : sensorKeys) {
            RoomItem room = roomBySensorKey.get(sensorKey);
            if (room != null) {
                rooms.add(room);
            }
        }

        roomAdapter.notifyDataSetChanged();
    }

    private void attachSensorListener(RoomItem room) {
        room.sensorRef = FirebaseDatabase.getInstance().getReference("sound_data/live").child(room.sensorKey);
        room.sensorListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (!dataSnapshot.exists()) {
                    room.latestSoundLevel = Float.NaN;
                    if (room.soundText != null) {
                        room.soundText.setText(getString(R.string.room_sound_placeholder));
                    }
                    if (room.groupStatusText != null) {
                        room.groupStatusText.setText(getString(R.string.room_group_status_waiting));
                        room.groupStatusText.setTextColor(getResources().getColor(R.color.app_on_surface, getTheme()));
                    }
                    if (room.statusText != null) {
                        room.statusText.setText(getString(R.string.room_status_waiting));
                        room.statusText.setTextColor(getResources().getColor(R.color.app_on_surface, getTheme()));
                    }
                    return;
                }

                try {
                    DataSnapshot valueSnapshot = dataSnapshot.child("value");
                    Object value = valueSnapshot.exists() ? valueSnapshot.getValue() : dataSnapshot.getValue();
                    if (value != null) {
                        float soundLevel = Float.parseFloat(value.toString());
                        room.latestSoundLevel = soundLevel;
                        if (room.soundText != null) {
                            room.soundText.setText(getString(R.string.room_sound_format, soundLevel));
                        }
                        if (room.speedView != null) {
                            if (Float.isNaN(room.lastDisplayedSpeed)
                                    || Math.abs(soundLevel - room.lastDisplayedSpeed) > 1.0f) {
                                room.speedView.speedTo(soundLevel);
                                room.lastDisplayedSpeed = soundLevel;
                            }
                        }
                        if (room.statusText != null) {
                            applyStatusText(room.statusText, soundLevel);
                        }
                        if (room.groupStatusText != null) {
                            applyGroupStatusText(room.groupStatusText, soundLevel);
                        }
                    }
                } catch (Exception e) {
                    Log.e("NoiseActivity", "Error parsing " + room.sensorKey, e);
                    room.latestSoundLevel = Float.NaN;
                    if (room.soundText != null) {
                        room.soundText.setText(getString(R.string.room_sound_placeholder));
                    }
                    if (room.groupStatusText != null) {
                        room.groupStatusText.setText(getString(R.string.room_group_status_error));
                        room.groupStatusText.setTextColor(getResources().getColor(R.color.app_on_surface, getTheme()));
                    }
                    if (room.statusText != null) {
                        room.statusText.setText(getString(R.string.room_status_error));
                        room.statusText.setTextColor(getResources().getColor(R.color.white, getTheme()));
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                room.latestSoundLevel = Float.NaN;
                if (room.soundText != null) {
                    room.soundText.setText(getString(R.string.room_sound_placeholder));
                }
                if (room.groupStatusText != null) {
                    room.groupStatusText.setText(getString(R.string.room_group_status_error));
                    room.groupStatusText.setTextColor(getResources().getColor(R.color.app_on_surface, getTheme()));
                }
                if (room.statusText != null) {
                    room.statusText.setText(getString(R.string.room_status_error));
                    room.statusText.setTextColor(getResources().getColor(R.color.white, getTheme()));
                }
            }
        };
        room.sensorRef.addValueEventListener(room.sensorListener);
    }

    private String toRoomName(String sensorKey) {
        int sensorIndex = extractSensorIndex(sensorKey);
        if (sensorIndex > 0) {
            return getString(R.string.room_name_format, sensorIndex);
        }
        return sensorKey;
    }

    private Class<?> toRoomActivity(String sensorKey) {
        switch (sensorKey) {
            case "sensor_1":
                return Room1Activity.class;
            case "sensor_2":
                return Room2Activity.class;
            default:
                return null;
        }
    }

    private int extractSensorIndex(String sensorKey) {
        if (sensorKey == null) {
            return Integer.MAX_VALUE;
        }
        int underscore = sensorKey.lastIndexOf('_');
        if (underscore < 0 || underscore + 1 >= sensorKey.length()) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(sensorKey.substring(underscore + 1));
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private class RoomExpandableAdapter extends BaseExpandableListAdapter {

        @Override
        public int getGroupCount() {
            return rooms.size();
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            return 1;
        }

        @Override
        public Object getGroup(int groupPosition) {
            return rooms.get(groupPosition);
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
            RoomItem room = rooms.get(groupPosition);
            View view = convertView;
            if (view == null) {
                view = LayoutInflater.from(NoiseActivity.this)
                        .inflate(R.layout.list_item_room_group, parent, false);
            }

            TextView roomNameTv = view.findViewById(R.id.groupRoomName);
            TextView groupStatusTv = view.findViewById(R.id.groupRoomStatus);

            int onSurface = getResources().getColor(R.color.app_on_surface, getTheme());
            roomNameTv.setText(room.roomName);
            roomNameTv.setTextColor(onSurface);
            room.groupStatusText = groupStatusTv;

            if (Float.isNaN(room.latestSoundLevel)) {
                groupStatusTv.setText(getString(R.string.room_group_status_waiting));
                groupStatusTv.setTextColor(onSurface);
            } else {
                applyGroupStatusText(groupStatusTv, room.latestSoundLevel);
            }

            return view;
        }

        @Override
        public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
                View convertView, ViewGroup parent) {
            RoomItem room = rooms.get(groupPosition);
            View view = LayoutInflater.from(NoiseActivity.this)
                    .inflate(R.layout.list_item_room_child, parent, false);

            SpeedView sv = view.findViewById(R.id.childSpeedView);
            TextView soundTv = view.findViewById(R.id.childSoundText);
            TextView statusTv = view.findViewById(R.id.childStatusText);
            Button openRoomButton = view.findViewById(R.id.childOpenRoomButton);

            sv.setMaxSpeed(120);
            sv.setUnit("dB");
            int onSurface = getResources().getColor(R.color.app_on_surface, getTheme());
            sv.setTextColor(onSurface);
            sv.setUnitTextColor(onSurface);
            float strokePx = 6 * getResources().getDisplayMetrics().density;
            sv.setSpeedometerWidth(strokePx);
            sv.setSpeedTextSize(0);
            sv.setUnitTextSize(dpToPx(10));

            room.speedView = sv;
            room.soundText = soundTv;
            room.statusText = statusTv;
            openRoomButton.setOnClickListener(v -> {
                if (room.targetActivity != null) {
                    startActivity(new Intent(NoiseActivity.this, room.targetActivity));
                }
            });
            openRoomButton.setEnabled(room.targetActivity != null);
            if (room.targetActivity == null) {
                openRoomButton.setText(R.string.room_button_no_details);
            }

            float latest = room.latestSoundLevel;
            if (Float.isNaN(latest)) {
                soundTv.setText(R.string.room_sound_placeholder);
                statusTv.setText(R.string.room_status_waiting);
                statusTv.setTextColor(onSurface);
                sv.speedTo(0);
            } else {
                soundTv.setText(getString(R.string.room_sound_format, latest));
                sv.speedTo(latest);
                room.lastDisplayedSpeed = latest;
                applyStatusText(statusTv, latest);
            }

            return view;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return false;
        }
    }

    private static class RoomItem {
        String sensorKey;
        String roomName;
        Class<?> targetActivity;
        float latestSoundLevel;
        float lastDisplayedSpeed = Float.NaN;
        DatabaseReference sensorRef;
        ValueEventListener sensorListener;
        TextView groupStatusText;
        SpeedView speedView;
        TextView soundText;
        TextView statusText;
    }

    private void applyGroupStatusText(TextView statusView, float dB) {
        if (dB < 50) {
            statusView.setText(R.string.room_group_quiet);
            statusView.setTextColor(getResources().getColor(R.color.status_quiet, getTheme()));
        } else if (dB < 70) {
            statusView.setText(R.string.room_group_moderate);
            statusView.setTextColor(getResources().getColor(R.color.status_moderate, getTheme()));
        } else {
            statusView.setText(R.string.room_group_loud);
            statusView.setTextColor(getResources().getColor(R.color.status_loud, getTheme()));
        }
    }

    private void applyStatusText(TextView statusView, float dB) {
        if (dB < 50) {
            statusView.setText(R.string.room_status_quiet);
            statusView.setTextColor(getResources().getColor(R.color.status_quiet, getTheme()));
        } else if (dB < 70) {
            statusView.setText(R.string.room_status_moderate);
            statusView.setTextColor(getResources().getColor(R.color.status_moderate, getTheme()));
        } else {
            statusView.setText(R.string.room_status_loud);
            statusView.setTextColor(getResources().getColor(R.color.status_loud, getTheme()));
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