package com.example.quietzone_app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class NoiseActivity extends AppCompatActivity {

    private Button roomButton1;
    private Button roomButton2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_noise);

        // Edge-to-edge padding
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

        roomButton1 = findViewById(R.id.roomButton1);
        roomButton2 = findViewById(R.id.roomButton2);

        roomButton1.setOnClickListener(v -> startActivity(new Intent(this, Room1Activity.class)));
        roomButton2.setOnClickListener(v -> startActivity(new Intent(this, Room2Activity.class)));
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    // (3) Toolbar

    //goes back when left arrow pressed
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    //create menu items in the toolbar
    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu){
        getMenuInflater().inflate(R.menu.menu_noiseactivity, menu);
        return true;
    }

    //what happens when option is clicked
    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        int id = item.getItemId();

        if (id == R.id.action_room1){
            Toast.makeText(this, "Now viewing Room 1", Toast.LENGTH_SHORT).show();
            //Click logic here
            Intent intent = new Intent(NoiseActivity.this, LoginActivity.class);
            startActivity(intent);
            return true;
        }

        if (id == R.id.action_room2){
            Toast.makeText(this, "Now viewing Room 2", Toast.LENGTH_SHORT).show();
            //Click logic here
            Intent intent = new Intent(NoiseActivity.this, NoiseActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}