package com.example.quietzone_app;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
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
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.listener.OnChartGestureListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
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

    private static final int MAX_CHART_POINTS = 10000;
    private static final float CHART_VISIBLE_WINDOW = 8f;
    private static final int MAX_LIST_ROWS = 10000;
    private static final int MAX_RAW_READINGS = 10000;
    private static final long FIFTEEN_MINUTES_MS = 15L * 60L * 1000L;
    private final List<Float> recentValues = new ArrayList<>();
    private final List<String> recentTimeLabels = new ArrayList<>();
    private final List<ReadingRow> readingRows = new ArrayList<>();
    private final List<String> activeChartLabels = new ArrayList<>();

    private LineChart historyChart;
    private TextView speedLabel;
    private TextView soundText;
    private TextView statusText;
    private TextView chartDateLabel;
    private ReadingsTableAdapter listAdapter;

    private ListenerRegistration roomListenerRegistration;
    private DatabaseReference liveSensorRef;
    private ValueEventListener liveSensorListener;
    private float latestLiveValue = Float.NaN;
    private long latestLiveTimestampMs = 0L;
    private int currentXAxisLabelStep = 1;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault());

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
        chartDateLabel = findViewById(R.id.chartDateLabel);
        ListView readingsList = findViewById(R.id.readingsList);

        speedLabel.setText(getString(R.string.room_noise_level_format, roomName));

        int onSurface = getResources().getColor(R.color.app_on_surface, getTheme());
        configureChart(onSurface);
        listAdapter = new ReadingsTableAdapter();
        readingsList.setAdapter(listAdapter);
        attachLiveSensorListener(sensorKey);

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
                if (Float.isNaN(latestLiveValue)) {
                    soundText.setText(getString(R.string.room_status_waiting));
                } else {
                    soundText.setText(getString(R.string.room_sound_level_display_format, latestLiveValue));
                    updateStatus(statusText, latestLiveValue);
                    renderChart();
                }
                return;
            }

            try {
                recentValues.clear();
                recentTimeLabels.clear();
                readingRows.clear();
                Map<Long, BucketAccumulator> chartBuckets = new LinkedHashMap<>();

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

                    if (readingRows.size() < MAX_LIST_ROWS) {
                        readingRows.add(new ReadingRow(
                                formatTimeText(time),
                                String.format(Locale.getDefault(), "%.1f", soundLevel),
                                getStatusLabel(soundLevel),
                                getStatusColor(soundLevel),
                                false));
                    }

                    long chartBucketStart = (time.getTime() / FIFTEEN_MINUTES_MS) * FIFTEEN_MINUTES_MS;

                    BucketAccumulator chartBucket = chartBuckets.get(chartBucketStart);
                    if (chartBucket == null) {
                        chartBucket = new BucketAccumulator(chartBucketStart);
                        chartBuckets.put(chartBucketStart, chartBucket);
                    }
                    chartBucket.add(soundLevel);
                }

                for (BucketAccumulator chartBucket : chartBuckets.values()) {
                    if (recentValues.size() >= MAX_CHART_POINTS) {
                        break;
                    }

                    float avgValue = chartBucket.getAverage();
                    recentValues.add(avgValue);
                    recentTimeLabels.add(formatBucketTime(chartBucket.bucketStartMs));
                }

                upsertLiveReadingRow();

                if (!recentValues.isEmpty()) {
                    float soundLevel = Float.isNaN(latestLiveValue) ? recentValues.get(0) : latestLiveValue;
                    soundText.setText(getString(R.string.room_sound_level_display_format, soundLevel));
                    updateStatus(statusText, soundLevel);
                    listAdapter.notifyDataSetChanged();
                    renderChart();
                } else {
                    if (Float.isNaN(latestLiveValue)) {
                        soundText.setText(getString(R.string.room_status_waiting));
                    } else {
                        soundText.setText(getString(R.string.room_sound_level_display_format, latestLiveValue));
                        updateStatus(statusText, latestLiveValue);
                        listAdapter.notifyDataSetChanged();
                        renderChart();
                    }
                }
            } catch (Exception e) {
                Log.e("RoomActivity", "Error parsing Firestore sensor data", e);
                soundText.setText(getString(R.string.room_status_error));
            }
        });
    }

    private void attachLiveSensorListener(String sensorKey) {
        liveSensorRef = FirebaseDatabase.getInstance().getReference("sound_data/live").child(sensorKey);
        liveSensorListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    latestLiveValue = Float.NaN;
                    return;
                }

                try {
                    DataSnapshot valueSnapshot = snapshot.child("value");
                    Object value = valueSnapshot.exists() ? valueSnapshot.getValue() : snapshot.getValue();
                    if (value == null) {
                        latestLiveValue = Float.NaN;
                        return;
                    }

                    latestLiveValue = Float.parseFloat(value.toString());
                    latestLiveTimestampMs = System.currentTimeMillis();
                    upsertLiveReadingRow();
                    listAdapter.notifyDataSetChanged();
                    soundText.setText(getString(R.string.room_sound_level_display_format, latestLiveValue));
                    updateStatus(statusText, latestLiveValue);
                    renderChart();
                } catch (Exception e) {
                    Log.e("RoomActivity", "Error parsing live sensor value", e);
                    latestLiveValue = Float.NaN;
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("RoomActivity", "Live listener cancelled", error.toException());
            }
        };
        liveSensorRef.addValueEventListener(liveSensorListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (roomListenerRegistration != null) {
            roomListenerRegistration.remove();
            roomListenerRegistration = null;
        }
        if (liveSensorRef != null && liveSensorListener != null) {
            liveSensorRef.removeEventListener(liveSensorListener);
            liveSensorListener = null;
            liveSensorRef = null;
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
        historyChart.setDragEnabled(true);
        historyChart.setScaleEnabled(true);
        historyChart.setScaleXEnabled(true);
        historyChart.setScaleYEnabled(false);
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
        xAxis.setDrawLabels(true);
        xAxis.setLabelCount(2, true);
        xAxis.setGranularity(1f);
        xAxis.setAxisMinimum(0f);
        xAxis.setAxisMaximum(1f);
        xAxis.setTextColor(textColor);
        xAxis.setTextSize(11f);
        xAxis.setAvoidFirstLastClipping(true);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int index = Math.round(value);
                if (index < 0 || index >= activeChartLabels.size()) {
                    return "";
                }
                if (currentXAxisLabelStep > 1 && index % currentXAxisLabelStep != 0) {
                    return "";
                }
                return activeChartLabels.get(index);
            }
        });

        historyChart.getAxisRight().setEnabled(false);
        historyChart.getAxisLeft().setAxisMinimum(0f);
        historyChart.getAxisLeft().setAxisMaximum(120f);
        historyChart.getAxisLeft().setLabelCount(7, true);
        historyChart.getAxisLeft().setDrawGridLines(true);
        historyChart.getAxisLeft().setTextColor(textColor);
        historyChart.getAxisLeft().setTextSize(11f);

        // Add gesture listener for chart scroll/scale to update date display
        historyChart.setOnChartGestureListener(new OnChartGestureListener() {
            @Override
            public void onChartGestureStart(MotionEvent me,
                    com.github.mikephil.charting.listener.ChartTouchListener.ChartGesture lastPerformedGesture) {
            }

            @Override
            public void onChartGestureEnd(MotionEvent me,
                    com.github.mikephil.charting.listener.ChartTouchListener.ChartGesture lastPerformedGesture) {
                updateChartDateLabel();
            }

            @Override
            public void onChartLongPressed(MotionEvent me) {
            }

            @Override
            public void onChartDoubleTapped(MotionEvent me) {
            }

            @Override
            public void onChartSingleTapped(MotionEvent me) {
            }

            @Override
            public void onChartFling(MotionEvent me1, MotionEvent me2, float velocityX, float velocityY) {
                updateChartDateLabel();
            }

            @Override
            public void onChartScale(MotionEvent me, float scaleX, float scaleY) {
            }

            @Override
            public void onChartTranslate(MotionEvent me, float dx, float dy) {
            }
        });
    }

    private void addReading(float value) {
        Date now = new Date();

        recentValues.add(0, value);
        readingRows.add(0, new ReadingRow(
                formatTimeText(now),
                String.format(Locale.getDefault(), "%.1f", value),
                getStatusLabel(value),
                getStatusColor(value),
                false));

        if (recentValues.size() > MAX_CHART_POINTS) {
            recentValues.remove(recentValues.size() - 1);
        }
        if (readingRows.size() > MAX_LIST_ROWS) {
            readingRows.remove(readingRows.size() - 1);
        }

        listAdapter.notifyDataSetChanged();
        renderChart();
    }

    private void renderChart() {
        List<Float> chartValues = getChartValuesWithLivePoint();
        List<String> chartLabels = getChartLabelsWithLivePoint();

        activeChartLabels.clear();
        activeChartLabels.addAll(chartLabels);

        List<Entry> entries = new ArrayList<>();
        int totalPoints = chartValues.size();
        for (int i = 0; i < chartValues.size(); i++) {
            float value = chartValues.get(chartValues.size() - 1 - i);
            entries.add(new Entry(i, value));
        }

        LineDataSet dataSet = new LineDataSet(entries, "Noise (dB)");
        int lineColor = getResources().getColor(R.color.status_moderate, getTheme());
        dataSet.setColor(lineColor);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setDrawValues(true);
        dataSet.setValueTextColor(lineColor);
        dataSet.setValueTextSize(10f);
        dataSet.setLineWidth(3.5f);
        dataSet.setDrawCircles(true);
        dataSet.setCircleColor(lineColor);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawCircleHole(false);
        dataSet.setHighlightEnabled(true);
        dataSet.setDrawHorizontalHighlightIndicator(false);
        dataSet.setDrawVerticalHighlightIndicator(true);

        historyChart.setData(new LineData(dataSet));
        float axisMax = Math.max(0f, totalPoints - 1f);
        XAxis xAxis = historyChart.getXAxis();
        xAxis.setAxisMinimum(0f);
        xAxis.setAxisMaximum(axisMax);
        currentXAxisLabelStep = calculateXAxisLabelStep(totalPoints);
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(Math.max(2, (totalPoints / Math.max(1, currentXAxisLabelStep)) + 1), true);
        historyChart.setVisibleXRangeMaximum(CHART_VISIBLE_WINDOW);
        historyChart.moveViewToX(Math.max(0f, totalPoints - CHART_VISIBLE_WINDOW));
        historyChart.invalidate();
        updateChartDateLabel();
    }

    private List<Float> getChartValuesWithLivePoint() {
        List<Float> chartValues = new ArrayList<>(recentValues);
        if (!Float.isNaN(latestLiveValue)) {
            if (chartValues.isEmpty()) {
                chartValues.add(latestLiveValue);
            } else {
                chartValues.set(0, latestLiveValue);
            }
        }
        return chartValues;
    }

    private List<String> getChartLabelsWithLivePoint() {
        List<String> labels = new ArrayList<>(recentTimeLabels);
        if (!Float.isNaN(latestLiveValue) && latestLiveTimestampMs > 0L) {
            String liveLabel = formatBucketTime(latestLiveTimestampMs);
            if (labels.isEmpty()) {
                labels.add(liveLabel);
            } else {
                labels.set(0, liveLabel);
            }
        }
        return labels;
    }

    private int calculateXAxisLabelStep(int pointCount) {
        if (pointCount <= 8) {
            return 1;
        }
        if (pointCount <= 16) {
            return 2;
        }
        if (pointCount <= 24) {
            return 3;
        }
        return 4;
    }

    private void upsertLiveReadingRow() {
        if (Float.isNaN(latestLiveValue) || latestLiveTimestampMs <= 0L) {
            return;
        }

        ReadingRow liveRow = new ReadingRow(
                formatTimeText(new Date(latestLiveTimestampMs)),
                String.format(Locale.getDefault(), "%.1f", latestLiveValue),
                getStatusLabel(latestLiveValue),
                getStatusColor(latestLiveValue),
                true);

        if (!readingRows.isEmpty() && readingRows.get(0).isLive) {
            readingRows.set(0, liveRow);
        } else {
            readingRows.add(0, liveRow);
        }

        if (readingRows.size() > MAX_LIST_ROWS) {
            readingRows.remove(readingRows.size() - 1);
        }
    }

    private String formatTimeText(Date time) {
        return new SimpleDateFormat("MMM d, h:mm:ss a", Locale.getDefault()).format(time);
    }

    private String formatBucketTime(long bucketStartMs) {
        return new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(new Date(bucketStartMs));
    }

    private void updateChartDateLabel() {
        if (activeChartLabels.isEmpty()) {
            chartDateLabel.setText("Loading...");
            return;
        }

        // Get the lowest visible x value (leftmost point on chart)
        float lowestVisibleX = historyChart.getLowestVisibleX();
        int lowestIndex = Math.max(0, Math.round(lowestVisibleX));
        lowestIndex = Math.min(lowestIndex, activeChartLabels.size() - 1);

        // Extract date from the lowest visible label
        String visibleLabel = activeChartLabels.get(lowestIndex);
        // Format: "MMM d, h:mm a" -> extract date part (e.g., "Mar 21")
        String[] parts = visibleLabel.split(", ");
        if (parts.length > 0) {
            chartDateLabel.setText("Date: " + parts[0]);
        } else {
            chartDateLabel.setText("Date: " + visibleLabel);
        }
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
        private final boolean isLive;

        private ReadingRow(String timeText, String valueText, String statusText, int statusColor, boolean isLive) {
            this.timeText = timeText;
            this.valueText = valueText;
            this.statusText = statusText;
            this.statusColor = statusColor;
            this.isLive = isLive;
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
