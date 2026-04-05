package com.example.quietzone_app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

//import android.graphics.Color;
//import android.os.Bundle;
//import android.widget.TextView;

import androidx.activity.EdgeToEdge;
//import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

// Toolbar
//import androidx.appcompat.widget.Toolbar;
//import android.view.Menu;
//import android.view.MenuItem;
//import android.widget.Toast;
//import android.content.Intent;

public class LoginActivity extends AppCompatActivity {

    private EditText usernameInput, passwordInput;
    private Button loginButton;
    private EditText confirmPasswordInput;
    private TextView newUserText, loginTitle;

    private boolean isLoginMode = true;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // If already logged in, skip to dashboard
        if (mAuth.getCurrentUser() != null) {
            fetchUserRoleAndNavigate(mAuth.getCurrentUser().getUid());
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        Toolbar myToolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);
        int toolbarTextColor = getResources().getColor(R.color.app_on_primary, getTheme());
        myToolbar.setTitleTextColor(toolbarTextColor);
        myToolbar.setSubtitleTextColor(toolbarTextColor);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.login_button));
        }
        if (myToolbar.getOverflowIcon() != null) {
            myToolbar.getOverflowIcon().setTint(toolbarTextColor);
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        usernameInput = findViewById(R.id.usernameInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginButton = findViewById(R.id.loginButton);

        confirmPasswordInput = findViewById(R.id.confirmPassword);
        newUserText = findViewById(R.id.Newuser);
        loginTitle = findViewById(R.id.loginTitle); // <-- ONLY ADDITION

        confirmPasswordInput.setVisibility(View.GONE);

        newUserText.setOnClickListener(v -> toggleMode());

        loginButton.setOnClickListener(v -> handleAuth());
    }

    // NEW: animation helper (unchanged)
    private void animateView(View view, boolean show) {
        float from = show ? 0f : 1f;
        float to = show ? 1f : 0f;

        AlphaAnimation anim = new AlphaAnimation(from, to);
        anim.setDuration(200);
        anim.setFillAfter(true);

        view.startAnimation(anim);

        if (show) {
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.GONE);
        }
    }

    private void toggleMode() {
        isLoginMode = !isLoginMode;

        if (isLoginMode) {
            animateView(confirmPasswordInput, false);

            loginButton.setText("Login");
            newUserText.setText("New User?");
            loginTitle.setText("Login");
        } else {
            animateView(confirmPasswordInput, true);

            loginButton.setText("Setup");
            newUserText.setText("Do you already have an account?");
            loginTitle.setText("Setup");
        }
    }

    private void handleAuth() {
        String email = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        // Basic local validation
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (password.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isLoginMode) {

            // Try login first
            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener(authResult -> fetchUserRoleAndNavigate(authResult.getUser().getUid()))
                    .addOnFailureListener(e -> handleAuthError(e));

        } else {

            String confirmPassword = confirmPasswordInput.getText().toString().trim();

            if (!password.equals(confirmPassword)) {
                Toast.makeText(this, "Passwords do not match.", Toast.LENGTH_SHORT).show();
                return;
            }

            registerUser(email, password);
        }
    }

    private void registerUser(String email, String password) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();
                    createFirestoreUserDoc(uid);
                })
                .addOnFailureListener(e -> handleAuthError(e));
    }

    private void createFirestoreUserDoc(String uid) {
        Map<String, Object> userDoc = new HashMap<>();
        userDoc.put("favourites", new ArrayList<>());
        userDoc.put("notification_settings", new HashMap<>());
        userDoc.put("role", "user");

        db.collection("users").document(uid)
                .set(userDoc)
                .addOnSuccessListener(unused -> fetchUserRoleAndNavigate(uid))
                .addOnFailureListener(e -> {
                    Toast.makeText(this,
                            "Account created but failed to save user data. Please try again.",
                            Toast.LENGTH_LONG).show();
                });
    }

    private void fetchUserRoleAndNavigate(String uid) {
        // Get user role from Firestore
        db.collection("users").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String role = documentSnapshot.getString("role");
                        boolean isAdmin = "admin".equalsIgnoreCase(role);

                        // Save user session with UID and admin status
                        SessionState.setUserSession(this, uid, isAdmin);
                        Log.d("LoginActivity", "User logged in - UID: " + uid + ", IsAdmin: " + isAdmin);

                        navigateToDashboard(isAdmin);
                    } else {
                        // User document doesn't exist - create it with default role
                        Log.w("LoginActivity", "User document does not exist for UID: " + uid + ". Creating it now.");
                        createMissingUserDoc(uid);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("LoginActivity", "Failed to fetch user role", e);
                    Toast.makeText(this, "Failed to fetch user data. Please try again.", Toast.LENGTH_SHORT).show();
                    mAuth.signOut();
                });
    }

    private void createMissingUserDoc(String uid) {
        // Create user document if it's missing (for existing users who don't have one)
        Map<String, Object> userDoc = new HashMap<>();
        userDoc.put("favourites", new ArrayList<>());
        userDoc.put("notification_settings", new HashMap<>());
        userDoc.put("role", "user");

        db.collection("users").document(uid)
                .set(userDoc)
                .addOnSuccessListener(unused -> {
                    Log.d("LoginActivity", "User document created for UID: " + uid);
                    // User document created, now proceed with login
                    SessionState.setUserSession(this, uid, false);
                    navigateToDashboard(false);
                })
                .addOnFailureListener(e -> {
                    Log.e("LoginActivity", "Failed to create user document for UID: " + uid, e);
                    Toast.makeText(this, "Failed to complete login. Please try again.", Toast.LENGTH_SHORT).show();
                    mAuth.signOut();
                });
    }

    private void navigateToDashboard(boolean isAdmin) {
        // Both admin and regular users navigate to NoiseActivity
        // Admin can access AdminDashboard from Settings menu
        Intent intent = new Intent(this, NoiseActivity.class);
        Log.d("LoginActivity", "Navigating to NoiseActivity (IsAdmin: " + isAdmin + ")");

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void handleAuthError(Exception e) {
        if (e instanceof FirebaseAuthWeakPasswordException) {
            Toast.makeText(this, "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show();
        } else if (e instanceof FirebaseAuthUserCollisionException) {
            Toast.makeText(this, "An account with this email already exists.", Toast.LENGTH_SHORT).show();
        } else if (e instanceof FirebaseAuthInvalidCredentialsException) {
            Toast.makeText(this, "Invalid email or password.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Authentication failed. Please try again.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}