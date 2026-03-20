package com.example.quietzone_app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class AuthActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        Toolbar myToolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);
        int toolbarTextColor = getResources().getColor(R.color.app_on_primary, getTheme());
        myToolbar.setTitleTextColor(toolbarTextColor);
        myToolbar.setSubtitleTextColor(toolbarTextColor);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Login");
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Button loginButton = findViewById(R.id.loginButton);
        EditText usernameInput = findViewById(R.id.usernameInput);
        EditText passwordInput = findViewById(R.id.passwordInput);

        if (loginButton != null) {
            loginButton.setOnClickListener(v -> {
                String email = usernameInput.getText() != null ? usernameInput.getText().toString().trim() : "";
                String password = passwordInput.getText() != null ? passwordInput.getText().toString().trim() : "";

                if (email.isEmpty() || password.isEmpty()) {
                    Toast.makeText(AuthActivity.this, "Please enter username and password", Toast.LENGTH_SHORT).show();
                    return;
                }

                loginButton.setEnabled(false);

                FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                        .addOnSuccessListener(authResult -> {
                            FirebaseUser user = authResult.getUser();
                            if (user != null) {
                                SessionState.setUserSession(AuthActivity.this, user.getUid(), false);
                                Intent intent = new Intent(AuthActivity.this, NoiseActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            }
                        })
                        .addOnFailureListener(e -> {
                            loginButton.setEnabled(true);
                            Toast.makeText(AuthActivity.this, "Login failed: " + e.getMessage(), Toast.LENGTH_SHORT)
                                    .show();
                        });
            });
        }
    }
}
