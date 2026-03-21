package com.example.quietzone_app;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RoomActivity extends AppCompatActivity {

    public static final String EXTRA_SENSOR_KEY = "extra_sensor_key";
    public static final String EXTRA_ROOM_NAME = "extra_room_name";

    private static final int MAX_POINTS = 30;
    private static final int MAX_RAW_READINGS = 300;
    private static final long FIVE_MINUTES_MS = 5L * 60L * 1000L;
    private final List<Float> recentValues = new ArrayList<>();
    private final List<ReadingRow> readingRows = new ArrayList<>();

    private LineChart historyChart;
    private TextView speedLabel;
    private TextView soundText;
    private TextView statusText;
    private ReadingsTableAdapter listAdapter;

    private ListenerRegistration roomListenerRegistration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.room_template);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        String sensorKey = getIntent().getStringExtra(EXTRA_SENSOR_KEY);
        if (sensorKey == null || sensorKey.trim().isEmpty()) {
            sensorKey = "sensor_1";
        }

        String roomName = getIntent().getStringExtra(EXTRA_ROOM_NAME);
        if (roomName == null || roomName.trim().isEmpty()) {
            roomName = getString(R.string.room_name_placeholder);
        }

        Toolbar myToolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);
        int toolbarTextColor = getResources().getColor(R.color.app_on_primary, getTheme());
        myToolbar.setTitleTextColor(toolbarTextColor);
        myToolbar.setSubtitleTextColor(toolbarTextColor);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(roomName);
            if (myToolbar.getNavigationIcon() != null) {
                myToolbar.getNavigationIcon().setTint(toolbarTextColor);
            }
        }
        if (myToolbar.getOverflowIcon() != null) {
            myToolbar.getOverflowIcon().setTint(toolbarTextColor);
        }

        historyChart = findViewById(R.id.historyChart);
        speedLabel = findViewById(R.id.speedLabel);
        soundText = findViewById(R.id.soundText);
        statusText = findViewById(R.id.statusText);
        ListView readingsList = findViewById(R.id.readingsList);

        speedLabel.setText(getString(R.string.room_noise_level_format, roomName));

        int onSurface = getResources().getColor(R.color.app_on_surface, getTheme());
        configureChart(onSurface);
        listAdapter = new ReadingsTableAdapter();
        readingsList.setAdapter(listAdapter);

        Query readingsQuery = FirebaseFirestore.getInstance()
                .collection("sound_data")
                .document(sensorKey)
                .collection("readings")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(MAX_RAW_READINGS);

        roomListenerRegistration = readingsQuery.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                soundText.setText(getString(R.string.room_database_error_format, error.getMessage()));
                return;
            }

            if (snapshot == null || snapshot.isEmpty()) {
                soundText.setText(getString(R.string.room_status_waiting));
                return;
            }

            try {
                recentValues.clear();
                readingRows.clear();
                Map<Long, BucketAccumulator> buckets = new LinkedHashMap<>();

                for (QueryDocumentSnapshot doc : snapshot) {
                    Object valueObj = doc.get("value");
                    if (valueObj == null) {
                        valueObj = doc.get("db_level");
                    }
                    if (valueObj == null) {
                        continue;
                    }

                    float soundLevel = Float.parseFloat(valueObj.toString());

                    Timestamp ts = doc.getTimestamp("timestamp");
                    Date time = ts != null ? ts.toDate() : new Date();
                    long bucketStart = (time.getTime() / FIVE_MINUTES_MS) * FIVE_MINUTES_MS;

                    BucketAccumulator bucket = buckets.get(bucketStart);
                    if (bucket == null) {
                        bucket = new BucketAccumulator(bucketStart);
                        buckets.put(bucketStart, bucket);
                    }
                    bucket.add(soundLevel);
                }

                for (BucketAccumulator bucket : buckets.values()) {
                    if (recentValues.size() >= MAX_POINTS) {
                        break;
                    }

                    float avgValue = bucket.getAverage();
                    recentValues.add(avgValue);
                    readingRows.add(new ReadingRow(
                            formatBucketTime(bucket.bucketStartMs),
                            String.format(Locale.getDefault(), "%.1f", avgValue),
                            getStatusLabel(avgValue),
                            getStatusColor(avgValue)));
                }

                if (!recentValues.isEmpty()) {
                    float soundLevel = recentValues.get(0);
                    soundText.setText(getString(R.string.room_sound_level_display_format, soundLevel));
                    updateStatus(statusText, soundLevel);
                    listAdapter.notifyDataSetChanged();
                    renderChart();
                } else {
                    soundText.setText(getString(R.string.room_status_waiting));
                }
            } catch (Exception e) {
                Log.e("RoomActivity", "Error parsing Firestore sensor data", e);
                soundText.setText(getString(R.string.room_status_error));
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (roomListenerRegistration != null) {
            roomListenerRegistration.remove();
            roomListenerRegistration = null;
        }
    }

    private void updateStatus(TextView view, float dB) {
        if (dB < 50) {
            view.setText(R.string.room_group_quiet);
            view.setTextColor(getResources().getColor(R.color.status_quiet, getTheme()));
        } else if (dB < 70) {
            view.setText(R.string.room_group_moderate);
            view.setTextColor(getResources().getColor(R.color.status_moderate, getTheme()));
        } else {
            view.setText(R.string.room_group_loud);
            view.setTextColor(getResources().getColor(R.color.status_loud, getTheme()));
        }
    }

    private void configureChart(int textColor) {
        historyChart.setTouchEnabled(true);
        historyChart.setPinchZoom(true);
        historyChart.setDoubleTapToZoomEnabled(false);
        historyChart.setDrawGridBackground(false);
        historyChart.setExtraBottomOffset(6f);

        Description description = new Description();
        description.setText("");
        historyChart.setDescription(description);

        Legend legend = historyChart.getLegend();
        legend.setEnabled(false);

        XAxis xAxis = historyChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(true);
        xAxis.setDrawLabels(false);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(textColor);

        historyChart.getAxisRight().setEnabled(false);
        historyChart.getAxisLeft().setAxisMinimum(0f);
        historyChart.getAxisLeft().setAxisMaximum(120f);
        historyChart.getAxisLeft().setLabelCount(7, true);
        historyChart.getAxisLeft().setDrawGridLines(true);
        historyChart.getAxisLeft().setTextColor(textColor);
    }

    private void addReading(float value) {
        Date now = new Date();

        recentValues.add(0, value);
        readingRows.add(0, new ReadingRow(
                formatTimeText(now),
                String.format(Locale.getDefault(), "%.1f", value),
                getStatusLabel(value),
                getStatusColor(value)));

        if (recentValues.size() > MAX_POINTS) {
            recentValues.remove(recentValues.size() - 1);
        }
        if (readingRows.size() > MAX_POINTS) {
            readingRows.remove(readingRows.size() - 1);
        }

        listAdapter.notifyDataSetChanged();
        renderChart();
    }

    private void renderChart() {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < recentValues.size(); i++) {
            entries.add(new Entry(i, recentValues.get(i)));
        }

        LineDataSet dataSet = new LineDataSet(entries, "Noise (dB)");
        int lineColor = getResources().getColor(R.color.status_moderate, getTheme());
        dataSet.setColor(lineColor);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setCubicIntensity(0.2f);
        dataSet.setDrawValues(false);
        dataSet.setLineWidth(3f);
        dataSet.setDrawCircles(false);
        dataSet.setDrawCircleHole(false);
        dataSet.setHighlightEnabled(false);
        dataSet.setDrawHorizontalHighlightIndicator(false);
        dataSet.setDrawVerticalHighlightIndicator(false);

        historyChart.setData(new LineData(dataSet));
        historyChart.getXAxis().setAxisMinimum(0f);
        historyChart.getXAxis().setAxisMaximum(Math.max(1, recentValues.size() - 1));
        historyChart.invalidate();
    }

    private String formatTimeText(Date time) {
        return new SimpleDateFormat("MMM d, h:mm:ss a", Locale.getDefault()).format(time);
    }

    private String formatBucketTime(long bucketStartMs) {
        return new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(new Date(bucketStartMs));
    }

    private String getStatusLabel(float dB) {
        if (dB < 50f) {
            return getString(R.string.room_group_quiet);
        }
        if (dB < 70f) {
            return getString(R.string.room_group_moderate);
        }
        return getString(R.string.room_group_loud);
    }

    private int getStatusColor(float dB) {
        if (dB < 50f) {
            return getResources().getColor(R.color.status_quiet, getTheme());
        }
        if (dB < 70f) {
            return getResources().getColor(R.color.status_moderate, getTheme());
        }
        return getResources().getColor(R.color.status_loud, getTheme());
    }

    private static final class ReadingRow {
        private final String timeText;
        private final String valueText;
        private final String statusText;
        private final int statusColor;

        private ReadingRow(String timeText, String valueText, String statusText, int statusColor) {
            this.timeText = timeText;
            this.valueText = valueText;
            this.statusText = statusText;
            this.statusColor = statusColor;
        }
    }

    private static final class BucketAccumulator {
        private final long bucketStartMs;
        private float sum;
        private int count;

        private BucketAccumulator(long bucketStartMs) {
            this.bucketStartMs = bucketStartMs;
        }

        private void add(float value) {
            sum += value;
            count++;
        }

        private float getAverage() {
            if (count == 0) {
                return 0f;
            }
            return sum / count;
        }
    }

    private final class ReadingsTableAdapter extends BaseAdapter {
        private final LayoutInflater inflater = LayoutInflater.from(RoomActivity.this);

        @Override
        public int getCount() {
            return readingRows.size();
        }

        @Override
        public Object getItem(int position) {
            return readingRows.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View rowView = convertView;
            if (rowView == null) {
                rowView = inflater.inflate(R.layout.list_item_reading_row, parent, false);
            }

            ReadingRow row = readingRows.get(position);
            TextView timeCell = rowView.findViewById(R.id.timeCell);
            TextView valueCell = rowView.findViewById(R.id.valueCell);
            TextView statusCell = rowView.findViewById(R.id.statusCell);

            timeCell.setText(row.timeText);
            valueCell.setText(row.valueText);
            statusCell.setText(row.statusText);
            statusCell.setTextColor(row.statusColor);

            return rowView;
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
