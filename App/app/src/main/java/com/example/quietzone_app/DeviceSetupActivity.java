package com.example.quietzone_app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DeviceSetupActivity extends AppCompatActivity {

    private static final String SETUP_PASSWORD = "config123";
    private static final String PREFIX_IOT = "IOT_";
    private static final String PREFIX_ESP32 = "ESP32_SETUP_";
    private static final String PREFIX_PI = "PI_SETUP_";

    private WifiManager wifiManager;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    private ArrayAdapter<String> ssidAdapter;
    private final List<String> setupSsids = new ArrayList<>();

    private TextView statusText;
    private TextView endpointText;

    private final ActivityResultLauncher<String[]> permissionsLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (hasAllRequiredPermissions()) {
                    startSetupScan();
                } else {
                    showToast("Wi-Fi scan permission is required for setup.");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_device_setup);

        Toolbar toolbar = findViewById(R.id.device_setup_toolbar);
        setSupportActionBar(toolbar);
        int toolbarTextColor = getResources().getColor(R.color.app_on_primary, getTheme());
        toolbar.setTitleTextColor(toolbarTextColor);
        toolbar.setSubtitleTextColor(toolbarTextColor);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Device Setup");
            if (toolbar.getNavigationIcon() != null) {
                toolbar.getNavigationIcon().setTint(toolbarTextColor);
            }
        }

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        statusText = findViewById(R.id.setupStatusText);
        endpointText = findViewById(R.id.setupEndpointText);

        ListView listView = findViewById(R.id.setupDeviceList);
        ssidAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, setupSsids);
        listView.setAdapter(ssidAdapter);

        Button scanButton = findViewById(R.id.scanSetupButton);
        scanButton.setOnClickListener(v -> requestPermissionsAndScan());

        listView.setOnItemClickListener((parent, view, position, id) -> {
            String selectedSsid = setupSsids.get(position);
            connectToSetupNetwork(selectedSsid);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(scanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        requestPermissionsAndScan();
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            unregisterReceiver(scanReceiver);
        } catch (IllegalArgumentException ignored) {
            // Receiver may already be unregistered by the system.
        }

        if (connectivityManager != null && networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
            networkCallback = null;
        }
    }

    private final android.content.BroadcastReceiver scanReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshSetupDevicesFromScan();
        }
    };

    private void requestPermissionsAndScan() {
        if (hasAllRequiredPermissions()) {
            startSetupScan();
            return;
        }

        List<String> missingPermissions = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this,
                        Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        }

        permissionsLauncher.launch(missingPermissions.toArray(new String[0]));
    }

    private boolean hasAllRequiredPermissions() {
        boolean hasLocation = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean hasNearby = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED;
            return hasLocation && hasNearby;
        }

        return hasLocation;
    }

    private void startSetupScan() {
        if (wifiManager == null) {
            showToast("Wi-Fi service unavailable on this device.");
            return;
        }

        boolean started = wifiManager.startScan();
        if (!started) {
            statusText.setText("Scan failed. Showing cached results.");
            refreshSetupDevicesFromScan();
        } else {
            statusText.setText("Scanning setup hotspots...");
        }
    }

    private void refreshSetupDevicesFromScan() {
        if (wifiManager == null) {
            return;
        }

        List<ScanResult> results;
        try {
            results = wifiManager.getScanResults();
        } catch (SecurityException ex) {
            statusText.setText("Wi-Fi scan permission is required for setup.");
            return;
        }

        setupSsids.clear();
        for (ScanResult result : results) {
            if (result == null || result.SSID == null) {
                continue;
            }

            String ssid = result.SSID.trim();
            if (ssid.isEmpty()) {
                continue;
            }

            if (isSetupSsid(ssid) && !setupSsids.contains(ssid)) {
                setupSsids.add(ssid);
            }
        }

        Collections.sort(setupSsids);
        ssidAdapter.notifyDataSetChanged();

        if (setupSsids.isEmpty()) {
            statusText.setText("No setup devices found. Move closer and scan again.");
        } else {
            statusText.setText(String.format("Found %d setup device(s). Tap one to connect.", setupSsids.size()));
        }
    }

    private boolean isSetupSsid(@NonNull String ssid) {
        return ssid.startsWith(PREFIX_IOT)
                || ssid.startsWith(PREFIX_ESP32)
                || ssid.startsWith(PREFIX_PI);
    }

    private void connectToSetupNetwork(@NonNull String ssid) {
        if (connectivityManager == null) {
            showToast("Could not connect to selected setup network.");
            return;
        }

        if (networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
            networkCallback = null;
        }

        WifiNetworkSpecifier specifier = new WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(SETUP_PASSWORD)
                .build();

        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier)
                .build();

        statusText.setText(String.format("Connecting to %s...", ssid));
        endpointText.setText("");

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                runOnUiThread(() -> {
                    connectivityManager.bindProcessToNetwork(network);
                    statusText.setText(String.format("Connected to %s", ssid));

                    if (ssid.startsWith("IOT_PI_") || ssid.startsWith(PREFIX_PI)) {
                        endpointText.setText("Send configuration to: http://192.168.4.1/setup");
                    } else {
                        endpointText.setText("Send configuration to: http://192.168.4.1/config");
                    }

                    showToast(String.format("Connected to %s", ssid));
                });
            }

            @Override
            public void onUnavailable() {
                runOnUiThread(() -> {
                    statusText.setText("Could not connect to selected setup network.");
                    showToast("Could not connect to selected setup network.");
                });
            }
        };

        connectivityManager.requestNetwork(request, networkCallback);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
