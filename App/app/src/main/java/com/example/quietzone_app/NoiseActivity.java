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
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.github.anastr.speedviewlib.SpeedView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NoiseActivity extends AppCompatActivity {

    private final List<RoomItem> rooms = new ArrayList<>();
    private final Map<String, RoomItem> roomBySensorKey = new LinkedHashMap<>();
    private final Map<String, Long> favoriteRooms = new HashMap<>();

    private ExpandableListView expandableListView;
    private RoomExpandableAdapter roomAdapter;
    private DatabaseReference liveSensorsRef;
    private DatabaseReference userFavoritesRef;
    private ValueEventListener liveSensorsListener;
    private ValueEventListener favoritesListener;
    private FirebaseListenerRegistry.ListenerHandle liveSensorsHandle;

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
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }

        setupNavigation();

        expandableListView = findViewById(R.id.roomExpandableList);
        expandableListView.setGroupIndicator(null);
        roomAdapter = new RoomExpandableAdapter();
        expandableListView.setAdapter(roomAdapter);

        String userId = FirebaseAuth.getInstance().getUid();
        if (userId != null) {
            userFavoritesRef = FirebaseDatabase.getInstance().getReference("users").child(userId).child("favorites");
            favoritesListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    favoriteRooms.clear();
                    for (DataSnapshot child : snapshot.getChildren()) {
                        favoriteRooms.put(child.getKey(), child.getValue(Long.class));
                    }
                    sortRooms();
                    roomAdapter.notifyDataSetChanged();
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                }
            };
            userFavoritesRef.addValueEventListener(favoritesListener);
        }

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
        liveSensorsHandle = FirebaseListenerRegistry.register(liveSensorsRef, liveSensorsListener);
    }

    private void setupNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setSelectedItemId(R.id.nav_home);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
                return true;
            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            }
            return id == R.id.nav_home;
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (liveSensorsHandle != null) {
            liveSensorsHandle.detachAndUnregister();
            liveSensorsHandle = null;
        } else if (liveSensorsRef != null && liveSensorsListener != null) {
            liveSensorsRef.removeEventListener(liveSensorsListener);
        }
        if (userFavoritesRef != null && favoritesListener != null) {
            userFavoritesRef.removeEventListener(favoritesListener);
        }
        for (RoomItem room : rooms) {
            if (room.sensorHandle != null) {
                room.sensorHandle.detachAndUnregister();
                room.sensorHandle = null;
            } else if (room.sensorRef != null && room.sensorListener != null) {
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

        // We don't sort here anymore, we sort in sortRooms()
        List<String> keysToRemove = new ArrayList<>();
        for (String existingKey : roomBySensorKey.keySet()) {
            if (!sensorKeys.contains(existingKey)) {
                keysToRemove.add(existingKey);
            }
        }
        for (String removedKey : keysToRemove) {
            RoomItem removed = roomBySensorKey.remove(removedKey);
            if (removed != null && removed.sensorHandle != null) {
                removed.sensorHandle.detachAndUnregister();
                removed.sensorHandle = null;
            } else if (removed != null && removed.sensorRef != null && removed.sensorListener != null) {
                removed.sensorRef.removeEventListener(removed.sensorListener);
            }
        }

        for (String sensorKey : sensorKeys) {
            if (!roomBySensorKey.containsKey(sensorKey)) {
                RoomItem room = new RoomItem();
                room.sensorKey = sensorKey;
                room.roomName = toRoomName(sensorKey);
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

        sortRooms();
        roomAdapter.notifyDataSetChanged();
    }

    private void sortRooms() {
        Collections.sort(rooms, (r1, r2) -> {
            boolean isFav1 = favoriteRooms.containsKey(r1.sensorKey);
            boolean isFav2 = favoriteRooms.containsKey(r2.sensorKey);

            if (isFav1 && !isFav2)
                return -1;
            if (!isFav1 && isFav2)
                return 1;

            if (isFav1 && isFav2) {
                Long t1 = favoriteRooms.get(r1.sensorKey);
                Long t2 = favoriteRooms.get(r2.sensorKey);
                if (t1 != null && t2 != null) {
                    return t1.compareTo(t2);
                }
            }

            return Integer.compare(extractSensorIndex(r1.sensorKey), extractSensorIndex(r2.sensorKey));
        });
    }

    private void attachSensorListener(RoomItem room) {
        room.sensorRef = FirebaseDatabase.getInstance().getReference("sound_data/live").child(room.sensorKey);
        room.sensorListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                // Track sensor in SharedPreferences
                saveSensorTracking(room.sensorKey, room.roomName);

                if (!dataSnapshot.exists()) {
                    room.latestSoundLevel = Float.NaN;
                    updateRoomUi(room);
                    return;
                }

                try {
                    DataSnapshot valueSnapshot = dataSnapshot.child("value");
                    Object value = valueSnapshot.exists() ? valueSnapshot.getValue() : dataSnapshot.getValue();
                    if (value != null) {
                        room.latestSoundLevel = Float.parseFloat(value.toString());
                        updateRoomUi(room);
                    }
                } catch (Exception e) {
                    Log.e("NoiseActivity", "Error parsing " + room.sensorKey, e);
                    room.latestSoundLevel = Float.NaN;
                    updateRoomUi(room);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                room.latestSoundLevel = Float.NaN;
                updateRoomUi(room);
            }
        };
        room.sensorRef.addValueEventListener(room.sensorListener);
        room.sensorHandle = FirebaseListenerRegistry.register(room.sensorRef, room.sensorListener);
    }

    private void saveSensorTracking(String sensorKey, String roomName) {
        android.content.SharedPreferences prefs = getSharedPreferences("sensor_data", android.content.Context.MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        
        // Get existing configured IDs
        String allConfigured = prefs.getString("all_configured_ids", "");
        
        // Add this sensor ID if not already present
        if (!allConfigured.contains(sensorKey)) {
            if (allConfigured.isEmpty()) {
                allConfigured = sensorKey;
            } else {
                allConfigured += "," + sensorKey;
            }
            editor.putString("all_configured_ids", allConfigured);
        }
        
        // Save sensor metadata
        editor.putString(sensorKey + "_name", roomName);
        editor.putString(sensorKey + "_room", roomName);
        
        // Update timestamp of when data was actually RECEIVED from Firebase
        // This is the key difference - we only update this when onDataChange is called
        editor.putLong(sensorKey + "_lastDataReceivedMs", System.currentTimeMillis());
        
        editor.apply();
    }

    private void updateRoomUi(RoomItem room) {
        float soundLevel = room.latestSoundLevel;

        if (room.soundText != null) {
            room.soundText.setText(Float.isNaN(soundLevel)
                    ? getString(R.string.room_sound_placeholder)
                    : getString(R.string.room_sound_format, soundLevel));
        }

        // ✅ ALWAYS update (removed threshold)
        if (room.speedView != null && !Float.isNaN(soundLevel)) {
            updateSpeedometerInstant(room.speedView, soundLevel);
            room.lastDisplayedSpeed = soundLevel;
        }

        if (room.statusText != null) {
            if (Float.isNaN(soundLevel)) {
                room.statusText.setText(getString(R.string.room_status_waiting));
                room.statusText.setTextColor(getResources().getColor(R.color.app_on_surface, getTheme()));
            } else {
                applyStatusText(room.statusText, soundLevel);
            }
        }

        if (room.groupStatusText != null) {
            if (Float.isNaN(soundLevel)) {
                room.groupStatusText.setText(getString(R.string.room_group_status_waiting));
                room.groupStatusText.setTextColor(getResources().getColor(R.color.app_on_surface, getTheme()));
            } else {
                applyGroupStatusText(room.groupStatusText, soundLevel);
            }
        }
    }

    private String toRoomName(String sensorKey) {
        if (sensorKey != null && !sensorKey.trim().isEmpty()) {
            return getString(R.string.room_name_format, sensorKey);
        }
        return getString(R.string.room_name_placeholder);
    }

    private int extractSensorIndex(String sensorKey) {
        if (sensorKey == null)
            return Integer.MAX_VALUE;
        int underscore = sensorKey.lastIndexOf('_');
        if (underscore < 0 || underscore + 1 >= sensorKey.length())
            return Integer.MAX_VALUE;
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
            View view = convertView == null
                    ? LayoutInflater.from(NoiseActivity.this).inflate(R.layout.list_item_room_group, parent, false)
                    : convertView;

            ImageView heartIcon = view.findViewById(R.id.heartIcon);
            TextView roomNameTv = view.findViewById(R.id.groupRoomName);
            TextView groupStatusTv = view.findViewById(R.id.groupRoomStatus);

            boolean isFavorite = favoriteRooms.containsKey(room.sensorKey);
            heartIcon.setImageResource(isFavorite ? R.drawable.ic_heart_filled : R.drawable.ic_heart_border);
            heartIcon.setOnClickListener(v -> toggleFavorite(room.sensorKey, isFavorite));

            roomNameTv.setText(room.roomName);
            room.groupStatusText = groupStatusTv;
            updateRoomUi(room);

            return view;
        }

        private void toggleFavorite(String sensorKey, boolean currentlyFavorite) {
            if (userFavoritesRef == null)
                return;
            if (currentlyFavorite) {
                userFavoritesRef.child(sensorKey).removeValue();
            } else {
                userFavoritesRef.child(sensorKey).setValue(ServerValue.TIMESTAMP);
            }
        }

        @Override
        public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
                View convertView, ViewGroup parent) {

            RoomItem room = rooms.get(groupPosition);

            View view = convertView;
            if (view == null) {
                view = LayoutInflater.from(NoiseActivity.this)
                        .inflate(R.layout.list_item_room_child, parent, false);
            }

            room.speedView = view.findViewById(R.id.childSpeedView);
            room.soundText = view.findViewById(R.id.childSoundText);
            room.statusText = view.findViewById(R.id.childStatusText);
            Button openRoomButton = view.findViewById(R.id.childOpenRoomButton);

            room.speedView.setMaxSpeed(120);
            room.speedView.setUnit("dB");
            room.speedView.setSpeedTextSize(0);
            room.speedView.setWithTremble(false);

            // ✅ FORCE update when view is created
            if (!Float.isNaN(room.latestSoundLevel)) {
                updateSpeedometerInstant(room.speedView, room.latestSoundLevel);
                room.lastDisplayedSpeed = room.latestSoundLevel;
            }

            openRoomButton.setOnClickListener(v -> {
                Intent roomIntent = new Intent(NoiseActivity.this, RoomActivity.class);
                roomIntent.putExtra(RoomActivity.EXTRA_SENSOR_KEY, room.sensorKey);
                roomIntent.putExtra(RoomActivity.EXTRA_ROOM_NAME, room.roomName);
                startActivity(roomIntent);
            });

            updateRoomUi(room);

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
        float latestSoundLevel;
        float lastDisplayedSpeed = Float.NaN;
        DatabaseReference sensorRef;
        ValueEventListener sensorListener;
        FirebaseListenerRegistry.ListenerHandle sensorHandle;
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

    private void updateSpeedometerInstant(SpeedView speedView, float level) {
        float clampedLevel = Math.max(0f, Math.min(level, speedView.getMaxSpeed()));
        speedView.speedTo(clampedLevel, 0);

        // ✅ FORCE redraw (important)
        speedView.invalidate();
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.menu_noiseactivity, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == R.id.action_add) {
            Toast.makeText(this, "Add device", Toast.LENGTH_SHORT).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}