package com.example.quietzone_app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ProfileActivity extends AppCompatActivity {

    private TextView nameText, emailText, roomsText, nameCardText, emailCardText, savedRoomsCount;
    private DatabaseReference userFavoritesRef;
    private DatabaseReference liveSensorsRef;
    private ValueEventListener favoritesListener;
    private ValueEventListener liveSensorsListener;
    private List<FavoriteItem> currentFavorites = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_profile);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Toolbar myToolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Profile");
        }

        nameText = findViewById(R.id.profileNameText);
        emailText = findViewById(R.id.profileEmailText);
        roomsText = findViewById(R.id.profileRoomsText);
        nameCardText = findViewById(R.id.profileNameCardText);
        emailCardText = findViewById(R.id.profileEmailText);
        savedRoomsCount = findViewById(R.id.savedRoomsCount);

        setupBottomNavigation();
        loadUserData();
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setSelectedItemId(R.id.nav_profile);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                startActivity(new Intent(this, NoiseActivity.class));
                return true;
            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            }
            return id == R.id.nav_profile;
        });
    }

    private void loadUserData() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            String userEmail = user.getEmail();
            emailText.setText(userEmail);
            emailCardText.setText(userEmail);

            // Display "Admin" if user is admin, otherwise show Firebase display name or
            // default to "User"
            String displayName = SessionState.isAdmin(this) ? "Admin"
                    : (user.getDisplayName() != null ? user.getDisplayName() : "User");
            nameText.setText(displayName);
            nameCardText.setText(displayName);

            userFavoritesRef = FirebaseDatabase.getInstance().getReference("users")
                    .child(user.getUid()).child("favorites");

            favoritesListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    currentFavorites.clear();
                    if (snapshot.exists()) {
                        for (DataSnapshot child : snapshot.getChildren()) {
                            String sensorKey = child.getKey();
                            Long timestamp = child.getValue(Long.class);
                            if (sensorKey != null && timestamp != null) {
                                currentFavorites.add(new FavoriteItem(sensorKey, timestamp));
                            }
                        }

                        // Sort by timestamp (the order they were favorited)
                        Collections.sort(currentFavorites, (f1, f2) -> f1.timestamp.compareTo(f2.timestamp));

                        // Update saved rooms count
                        savedRoomsCount.setText(String.valueOf(currentFavorites.size()));

                        // Fetch location names from Firebase live sensors
                        attachLiveSensorsListener();
                    } else {
                        savedRoomsCount.setText("0");
                        roomsText.setText("No favorite rooms yet.");
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e("ProfileActivity", "Error loading favorites", error.toException());
                }
            };
            userFavoritesRef.addValueEventListener(favoritesListener);
        }
    }

    private void attachLiveSensorsListener() {
        liveSensorsRef = FirebaseDatabase.getInstance().getReference("sound_data/live");
        liveSensorsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                StringBuilder sb = new StringBuilder();
                for (FavoriteItem fav : currentFavorites) {
                    DataSnapshot sensorSnapshot = snapshot.child(fav.sensorKey);
                    String location = sensorSnapshot.child("location").getValue(String.class);
                    String roomName = (location != null && !location.trim().isEmpty()) ? location : "Unassigned";
                    sb.append("• ").append(roomName).append("\n");
                }
                if (sb.length() > 0) {
                    sb.setLength(sb.length() - 1); // Remove trailing newline
                }
                roomsText.setText(sb.toString());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("ProfileActivity", "Error loading live sensors", error.toException());
            }
        };
        liveSensorsRef.addValueEventListener(liveSensorsListener);
    }

    private static class FavoriteItem {
        String sensorKey;
        Long timestamp;

        FavoriteItem(String sensorKey, Long timestamp) {
            this.sensorKey = sensorKey;
            this.timestamp = timestamp;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (userFavoritesRef != null && favoritesListener != null) {
            userFavoritesRef.removeEventListener(favoritesListener);
        }
        if (liveSensorsRef != null && liveSensorsListener != null) {
            liveSensorsRef.removeEventListener(liveSensorsListener);
        }
    }
}