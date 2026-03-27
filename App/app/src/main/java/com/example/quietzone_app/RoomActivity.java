package com.example.quietzone_app;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.listener.ChartTouchListener;
import com.github.mikephil.charting.listener.OnChartGestureListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RoomActivity extends AppCompatActivity {

    public static final String EXTRA_SENSOR_KEY = "extra_sensor_key";
    public static final String EXTRA_ROOM_NAME = "extra_room_name";

    private static final float CHART_MIN_VISIBLE_WINDOW_SECONDS = 20f * 60f;
    private static final float CHART_MAX_VISIBLE_WINDOW_SECONDS = 40f * 60f;
    private static final float TARGET_BAR_WIDTH_PX = 10f;
    private static final int MAX_LIST_ROWS = 3000;
    private static final int MAX_RAW_READINGS = 50000; // Increased since we're paginating
    private static final Pattern UTC_OFFSET_PATTERN = Pattern.compile("^(.*)\\sUTC([+-]\\d{1,2})(?::?(\\d{2}))?$");
    private static final String[] TIMESTAMP_PATTERNS = new String[] {
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd HH:mm:ss.SSSXXX",
            "yyyy-MM-dd HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd HH:mm:ss.SSSZ",
            "yyyy-MM-dd HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss.SSS",
            "yyyy-MM-dd HH:mm:ss"
    };

    private final List<SoundReading> firestoreReadings = new ArrayList<>();
    private final List<SoundReading> liveReadings = new ArrayList<>();
    private final List<SoundReading> soundReadings = new ArrayList<>();

    private LineChart historyChart;
    private TextView chartDateLabel;
    private TextView speedLabel;
    private TextView soundText;
    private TextView statusText;
    private ReadingsTableAdapter listAdapter;
    private long chartMinTimestampMs = 0L;
    private long chartRangeMs = 0L;
    private String roomDisplayName;

    private DatabaseReference liveSensorRef;
    private ValueEventListener liveSensorListener;
    private float latestLiveValue = Float.NaN;
    private long latestLiveTimestampMs = 0L;

    private static final long BUCKET_SIZE_MS = 5L * 60L * 1000L;
    private final List<SoundReading> bucketedReadings = new ArrayList<>();

    // Pagination fields
    private static final int PAGE_SIZE = 500; // Load 500 readings per page
    private QueryDocumentSnapshot lastVisibleDoc = null;
    private boolean isLoadingMore = false;
    private boolean hasMoreReadings = true;
    private String currentSensorKey;

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
        currentSensorKey = sensorKey;

        String roomName = getIntent().getStringExtra(EXTRA_ROOM_NAME);
        if (roomName == null || roomName.trim().isEmpty()) {
            roomName = getString(R.string.room_name_placeholder);
        }
        roomDisplayName = roomName;

        Toolbar myToolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);
        int toolbarTextColor = getResources().getColor(R.color.app_on_primary, getTheme());
        myToolbar.setTitleTextColor(toolbarTextColor);
        myToolbar.setSubtitleTextColor(toolbarTextColor);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(roomDisplayName);
            actionBar.setSubtitle(null);
            if (myToolbar.getNavigationIcon() != null) {
                myToolbar.getNavigationIcon().setTint(toolbarTextColor);
            }
        }
        if (myToolbar.getOverflowIcon() != null) {
            myToolbar.getOverflowIcon().setTint(toolbarTextColor);
        }

        historyChart = findViewById(R.id.historyChart);
        chartDateLabel = findViewById(R.id.chartDateLabel);
        speedLabel = findViewById(R.id.speedLabel);
        soundText = findViewById(R.id.soundText);
        statusText = findViewById(R.id.statusText);
        ListView readingsList = findViewById(R.id.readingsList);

        speedLabel.setText(getString(R.string.room_sound_level_placeholder));
        chartDateLabel.setText(R.string.room_chart_loading);

        int onSurface = getResources().getColor(R.color.app_on_surface, getTheme());
        configureChart(onSurface);
        listAdapter = new ReadingsTableAdapter();
        readingsList.setAdapter(listAdapter);

        // Add scroll listener for pagination
        readingsList.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                // Load more when user scrolls within 5 items of the bottom
                if (firstVisibleItem + visibleItemCount >= totalItemCount - 5 &&
                        totalItemCount > 0 &&
                        hasMoreReadings &&
                        !isLoadingMore) {
                    loadNextPage();
                }
            }
        });

        fetchFirestoreReadingsOnce();
        attachLiveSensorListener(sensorKey);
    }

    private List<SoundReading> bucketReadings(List<SoundReading> readings) {
        if (readings.isEmpty())
            return new ArrayList<>();

        // Use a LinkedHashMap keyed by bucket-floor so insertion order is
        // chronological.
        Map<Long, float[]> buckets = new LinkedHashMap<>(); // key → [sum, count]

        for (SoundReading r : readings) {
            long bucketKey = Math.round((double) r.timestampMs / BUCKET_SIZE_MS) * BUCKET_SIZE_MS;

            float[] acc = buckets.get(bucketKey);
            if (acc == null) {
                acc = new float[] { 0f, 0f };
                buckets.put(bucketKey, acc);
            }
            acc[0] += r.value;
            acc[1] += 1f;
        }

        List<SoundReading> bucketed = new ArrayList<>(buckets.size());
        for (Map.Entry<Long, float[]> entry : buckets.entrySet()) {
            long centerMs = entry.getKey();
            float avg = entry.getValue()[0] / entry.getValue()[1];
            bucketed.add(new SoundReading(centerMs, avg, false));
        }

        // Ensure ascending order for the chart.
        Collections.sort(bucketed, (a, b) -> Long.compare(a.timestampMs, b.timestampMs));
        return bucketed;
    }

    private void fetchFirestoreReadingsOnce() {
        loadNextPage();
    }

    private void loadNextPage() {
        if (isLoadingMore || !hasMoreReadings) {
            return; // Already loading or no more data
        }

        isLoadingMore = true;

        Query readingsQuery = FirebaseFirestore.getInstance()
                .collection("sound_data")
                .document(currentSensorKey)
                .collection("readings")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(PAGE_SIZE);

        // If we have a previous last document, start after it
        if (lastVisibleDoc != null) {
            readingsQuery = readingsQuery.startAfter(lastVisibleDoc);
        }

        readingsQuery.get().addOnSuccessListener(snapshot -> {
            try {
                if (snapshot.isEmpty()) {
                    runOnUiThread(() -> {
                        hasMoreReadings = false;
                        isLoadingMore = false;
                        refreshUiFromReadings();
                    });
                    return;
                }

                // Store the last document for next pagination
                List<DocumentSnapshot> docs = snapshot.getDocuments();
                if (!docs.isEmpty()) {
                    DocumentSnapshot lastDoc = docs.get(docs.size() - 1);
                    if (lastDoc instanceof QueryDocumentSnapshot) {
                        lastVisibleDoc = (QueryDocumentSnapshot) lastDoc;
                    }
                }

                // Add new readings to the list (on background thread, but before UI update)
                for (QueryDocumentSnapshot doc : snapshot) {
                    SoundReading reading = parseFirestoreReading(doc);
                    if (reading != null) {
                        firestoreReadings.add(reading);
                    }
                }

                // Check if there are more readings to fetch
                hasMoreReadings = snapshot.size() >= PAGE_SIZE;

                // Collections.sort(firestoreReadings, (a, b) -> Long.compare(b.timestampMs,
                // a.timestampMs));

                // Update UI on main thread
                runOnUiThread(() -> {
                    rebuildMergedReadings();
                    refreshUiFromReadings();
                    isLoadingMore = false;
                });
            } catch (Exception e) {
                Log.e("RoomActivity", "Error parsing Firestore sensor data", e);
                runOnUiThread(() -> {
                    soundText.setText(getString(R.string.room_status_error));
                    updateToolbarSoundLevel(Float.NaN);
                    isLoadingMore = false;
                });
            }
        }).addOnFailureListener(e -> {
            Log.e("RoomActivity", "Failed to fetch Firestore sensor data", e);
            runOnUiThread(() -> {
                soundText.setText(getString(R.string.room_database_error_format, e.getMessage()));
                updateToolbarSoundLevel(Float.NaN);
                isLoadingMore = false;
            });
        });
    }

    private void attachLiveSensorListener(String sensorKey) {
        liveSensorRef = FirebaseDatabase.getInstance().getReference("sound_data/live").child(sensorKey);
        liveSensorListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    return;
                }

                try {
                    DataSnapshot valueSnapshot = snapshot.child("value");
                    Object value = valueSnapshot.exists() ? valueSnapshot.getValue() : snapshot.getValue();
                    if (value == null) {
                        return;
                    }

                    latestLiveValue = Float.parseFloat(value.toString());
                    latestLiveTimestampMs = extractLiveTimestampMs(snapshot);
                    if (latestLiveTimestampMs <= 0L) {
                        latestLiveTimestampMs = System.currentTimeMillis();
                    }

                    // Update UI on main thread
                    runOnUiThread(() -> {
                        appendLiveReading(latestLiveValue, latestLiveTimestampMs);
                        rebuildMergedReadings();
                        refreshUiFromReadings();
                    });
                } catch (Exception e) {
                    Log.e("RoomActivity", "Error parsing live sensor value", e);
                    runOnUiThread(() -> updateToolbarSoundLevel(Float.NaN));
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
        xAxis.setLabelCount(6, false);
        xAxis.setGranularityEnabled(true);
        xAxis.setGranularity(5f * 60f);
        xAxis.setAxisMinimum(0f);
        xAxis.setAxisMaximum(1f);
        xAxis.setTextColor(textColor);
        xAxis.setTextSize(11f);
        xAxis.setAvoidFirstLastClipping(true);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                if (chartMinTimestampMs <= 0L) {
                    return "";
                }

                long offsetMs = (long) (Math.max(0f, value) * 1000f);
                if (offsetMs > chartRangeMs) {
                    return "";
                }

                long timestampMs = chartMinTimestampMs + offsetMs;
                if (chartRangeMs >= 24L * 60L * 60L * 1000L) {
                    return formatInUserTimeZone(new Date(timestampMs), "MMM d HH:mm");
                }
                return formatInUserTimeZone(new Date(timestampMs), "HH:mm");
            }
        });

        historyChart.getAxisRight().setEnabled(false);
        historyChart.getAxisLeft().setAxisMinimum(0f);
        historyChart.getAxisLeft().setAxisMaximum(120f);
        historyChart.getAxisLeft().setLabelCount(7, true);
        historyChart.getAxisLeft().setDrawGridLines(true);
        historyChart.getAxisLeft().setTextColor(textColor);
        historyChart.getAxisLeft().setTextSize(11f);

        historyChart.setOnChartGestureListener(new OnChartGestureListener() {
            @Override
            public void onChartGestureStart(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {
            }

            @Override
            public void onChartGestureEnd(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {
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
            }

            @Override
            public void onChartScale(MotionEvent me, float scaleX, float scaleY) {
                updateChartDateLabel();
            }

            @Override
            public void onChartTranslate(MotionEvent me, float dX, float dY) {
                updateChartDateLabel();
            }
        });
    }

    private void renderChart() {
        List<Entry> entries = buildLineEntriesFromReadings();

        if (entries.isEmpty()) {
            historyChart.clear();
            historyChart.invalidate();
            return;
        }

        float axisMaxSeconds = Math.max(1f, chartRangeMs / 1000f);
        XAxis xAxis = historyChart.getXAxis();
        xAxis.setAxisMinimum(0f);
        xAxis.setAxisMaximum(axisMaxSeconds);
        xAxis.setGranularity(calculateXAxisGranularity(axisMaxSeconds));

        LineDataSet dataSet = new LineDataSet(entries, "Noise (dB)");
        int lineColor = getResources().getColor(R.color.status_moderate, getTheme());
        dataSet.setColor(lineColor);
        dataSet.setDrawValues(false);
        dataSet.setDrawCircles(true);
        dataSet.setCircleRadius(4f);
        dataSet.setCircleColor(lineColor);
        dataSet.setDrawCircleHole(true);
        dataSet.setLineWidth(2f); // line thickness
        dataSet.setMode(LineDataSet.Mode.LINEAR);
        dataSet.setDrawFilled(true); // fill area under the line
        dataSet.setFillColor(lineColor);
        dataSet.setFillAlpha(50); // transparency of fill (0-255)

        LineData lineData = new LineData(dataSet);
        historyChart.setData(lineData);

        float visibleWindow = Math.min(axisMaxSeconds, CHART_MAX_VISIBLE_WINDOW_SECONDS);
        visibleWindow = Math.max(visibleWindow, CHART_MIN_VISIBLE_WINDOW_SECONDS);
        historyChart.setVisibleXRangeMaximum(visibleWindow);
        historyChart.moveViewToX(Math.max(0f, axisMaxSeconds - visibleWindow));
        historyChart.invalidate();
        historyChart.post(this::updateChartDateLabel);
    }

    private List<Entry> buildLineEntriesFromReadings() {
        synchronized (soundReadings) {
            if (soundReadings.isEmpty()) {
                chartMinTimestampMs = 0L;
                chartRangeMs = 0L;
                return new ArrayList<>();
            }

            List<SoundReading> sortedAsc = new ArrayList<>(soundReadings);
            Collections.sort(sortedAsc, (a, b) -> Long.compare(a.timestampMs, b.timestampMs));
            List<SoundReading> bucketed = bucketReadings(sortedAsc);

            long minTs = bucketed.get(0).timestampMs;
            long maxTs = bucketed.get(bucketed.size() - 1).timestampMs;
            if (maxTs <= minTs)
                maxTs = minTs + 1000L;

            chartMinTimestampMs = minTs;
            chartRangeMs = maxTs - minTs;

            List<Entry> entries = new ArrayList<>(bucketed.size());
            for (SoundReading reading : bucketed) {
                float xSeconds = (reading.timestampMs - minTs) / 1000f;
                entries.add(new Entry(xSeconds, reading.value));
            }
            return entries;
        }
    }

    private float calculateXAxisGranularity(float axisRangeSeconds) {
        if (axisRangeSeconds <= 15f * 60f) {
            return 60f;
        }
        if (axisRangeSeconds <= 60f * 60f) {
            return 5f * 60f;
        }
        if (axisRangeSeconds <= 6f * 60f * 60f) {
            return 15f * 60f;
        }
        return 60f * 60f;
    }

    private SoundReading parseFirestoreReading(QueryDocumentSnapshot doc) {
        Object valueObj = doc.get("value");
        if (valueObj == null) {
            valueObj = doc.get("db_level");
        }
        if (valueObj == null) {
            return null;
        }

        float soundLevel = Float.parseFloat(valueObj.toString());
        Timestamp ts = doc.getTimestamp("timestamp");
        long timestampMs;

        if (ts != null) {
            timestampMs = ts.toDate().getTime();
        } else {
            timestampMs = System.currentTimeMillis();
        }
        if (timestampMs <= 0L) {
            timestampMs = System.currentTimeMillis();
        }
        return new SoundReading(timestampMs, soundLevel, false);
    }

    private void rebuildMergedReadings() {
        synchronized (soundReadings) {
            soundReadings.clear();
            soundReadings.addAll(firestoreReadings);
            soundReadings.addAll(liveReadings);

            Collections.sort(soundReadings, (a, b) -> Long.compare(b.timestampMs, a.timestampMs));
            trimReadings();

            // Rebuild bucketed list for the UI table
            List<SoundReading> sortedAsc = new ArrayList<>(soundReadings);
            Collections.sort(sortedAsc, (a, b) -> Long.compare(a.timestampMs, b.timestampMs));
            bucketedReadings.clear();
            bucketedReadings.addAll(bucketReadings(sortedAsc));
            // Sort descending so newest row is at top of list
            Collections.sort(bucketedReadings, (a, b) -> Long.compare(b.timestampMs, a.timestampMs));
        }
    }

    private void appendLiveReading(float value, long timestampMs) {
        if (Float.isNaN(value) || timestampMs <= 0L) {
            return;
        }

        long maxTimestamp = timestampMs;
        for (int i = 0; i < liveReadings.size(); i++) {
            SoundReading reading = liveReadings.get(i);
            if (reading.timestampMs >= maxTimestamp) {
                maxTimestamp = reading.timestampMs + 1L;
            }
        }

        liveReadings.add(new SoundReading(maxTimestamp, value, true));
        Collections.sort(liveReadings, (a, b) -> Long.compare(b.timestampMs, a.timestampMs));
    }

    private long extractLiveTimestampMs(DataSnapshot snapshot) {
        return parseTimestampToMillis(snapshot.child("timestamp").getValue());
    }

    private long parseTimestampToMillis(Object rawTimestamp) {
        if (rawTimestamp == null) {
            return 0L;
        }

        if (rawTimestamp instanceof Timestamp) {
            return ((Timestamp) rawTimestamp).toDate().getTime();
        }
        if (rawTimestamp instanceof Date) {
            return ((Date) rawTimestamp).getTime();
        }
        if (rawTimestamp instanceof Number) {
            return ((Number) rawTimestamp).longValue();
        }

        String raw = rawTimestamp.toString().trim();
        if (raw.isEmpty()) {
            return 0L;
        }

        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            // Not an epoch value; fall through to date parsing.
        }

        String normalized = normalizeUtcOffset(raw);

        for (String pattern : TIMESTAMP_PATTERNS) {
            try {
                SimpleDateFormat parser = new SimpleDateFormat(pattern, Locale.US);
                if (!pattern.contains("X") && !pattern.contains("Z")) {
                    parser.setTimeZone(TimeZone.getTimeZone("UTC"));
                }
                Date parsed = parser.parse(normalized);
                if (parsed != null) {
                    return parsed.getTime();
                }
            } catch (ParseException ignored) {
                // Try next known format.
            }
        }

        return 0L;
    }

    private String normalizeUtcOffset(String raw) {
        Matcher matcher = UTC_OFFSET_PATTERN.matcher(raw);
        if (!matcher.matches()) {
            return raw;
        }

        String base = matcher.group(1).trim();
        String hoursPart = matcher.group(2);
        String minutesPart = matcher.group(3);

        int hours = 0;
        try {
            hours = Integer.parseInt(hoursPart);
        } catch (NumberFormatException ignored) {
            return raw;
        }

        String sign = hours >= 0 ? "+" : "-";
        int absHours = Math.abs(hours);
        String normalizedMinutes = minutesPart == null ? "00" : minutesPart;

        return String.format(Locale.US, "%s %s%02d:%s", base, sign, absHours, normalizedMinutes);
    }

    private void trimReadings() {
        while (soundReadings.size() > MAX_RAW_READINGS) {
            soundReadings.remove(soundReadings.size() - 1);
        }
        while (firestoreReadings.size() > MAX_RAW_READINGS) {
            firestoreReadings.remove(firestoreReadings.size() - 1);
        }
        while (liveReadings.size() > MAX_RAW_READINGS) {
            liveReadings.remove(liveReadings.size() - 1);
        }
    }

    private void refreshUiFromReadings() {
        synchronized (soundReadings) {
            if (soundReadings.isEmpty()) {
                chartDateLabel.setText(R.string.room_chart_no_data);
                if (Float.isNaN(latestLiveValue)) {
                    soundText.setText(getString(R.string.room_status_waiting));
                    speedLabel.setText(getString(R.string.room_sound_level_placeholder));
                    updateToolbarSoundLevel(Float.NaN);
                } else {
                    soundText.setText(getString(R.string.room_sound_level_display_format, latestLiveValue));
                    speedLabel.setText(getString(R.string.room_sound_level_display_format, latestLiveValue));
                    updateStatus(statusText, latestLiveValue);
                    updateToolbarSoundLevel(latestLiveValue);
                }
                listAdapter.notifyDataSetChanged();
                renderChart();
                return;
            }

            float latestValue = soundReadings.get(0).value;
            soundText.setText(getString(R.string.room_sound_level_display_format, latestValue));
            speedLabel.setText(getString(R.string.room_sound_level_display_format, latestValue));
            updateStatus(statusText, latestValue);
            updateToolbarSoundLevel(latestValue);
            updateChartDateLabel();
            listAdapter.notifyDataSetChanged();
            renderChart();
        }
    }

    private void updateToolbarSoundLevel(float value) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) {
            return;
        }
        actionBar.setTitle(roomDisplayName);
        actionBar.setSubtitle(null);
    }

    private void updateChartDateLabel() {
        if (soundReadings.isEmpty()) {
            chartDateLabel.setText(R.string.room_chart_no_data);
            return;
        }

        if (chartMinTimestampMs <= 0L || chartRangeMs <= 0L) {
            chartDateLabel.setText(formatChartDayText(new Date(soundReadings.get(0).timestampMs)));
            return;
        }

        float lowestVisibleX = Math.max(0f, historyChart.getLowestVisibleX());
        float highestVisibleX = Math.max(lowestVisibleX, historyChart.getHighestVisibleX());

        long startOffsetMs = Math.min(chartRangeMs, Math.max(0L, (long) (lowestVisibleX * 1000f)));
        long endOffsetMs = Math.min(chartRangeMs, Math.max(0L, (long) (highestVisibleX * 1000f)));

        long visibleStartMs = chartMinTimestampMs + startOffsetMs;
        long visibleEndMs = chartMinTimestampMs + endOffsetMs;

        String startDay = formatChartDayText(new Date(visibleStartMs));
        String endDay = formatChartDayText(new Date(visibleEndMs));

        if (startDay.equals(endDay)) {
            chartDateLabel.setText(startDay);
            return;
        }
        chartDateLabel.setText(startDay + " - " + endDay);
    }

    private String formatChartDayText(Date time) {
        return formatInUserTimeZone(time, "MMM dd, yyyy");
    }

    private String formatTimeText(Date time) {
        return formatInUserTimeZone(time, "MMM d, h:mm:ss a");
    }

    private String formatInUserTimeZone(Date time, String pattern) {
        SimpleDateFormat formatter = new SimpleDateFormat(pattern, Locale.getDefault());
        formatter.setTimeZone(TimeZone.getDefault());
        return formatter.format(time);
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

    private static final class SoundReading {
        private final long timestampMs;
        private final float value;
        private final boolean isLive;

        private SoundReading(long timestampMs, float value, boolean isLive) {
            this.timestampMs = timestampMs;
            this.value = value;
            this.isLive = isLive;
        }
    }

    private final class ReadingsTableAdapter extends BaseAdapter {
        private final LayoutInflater inflater = LayoutInflater.from(RoomActivity.this);

        public int getCount() {
            // Add 1 for loading indicator at the bottom if more readings exist
            synchronized (soundReadings) {
                int baseCount = Math.min(bucketedReadings.size(), MAX_LIST_ROWS);
                if (hasMoreReadings && !isLoadingMore) {
                    return baseCount + 1;
                }
                return baseCount;
            }
        }

        @Override
        public Object getItem(int position) {
            synchronized (soundReadings) {
                if (position < bucketedReadings.size()) {
                    return bucketedReadings.get(position);
                }
            }
            return null; // Loading indicator
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            synchronized (soundReadings) {
                // Show loading indicator at the bottom
                if (position >= bucketedReadings.size()) {
                    View loadingView = convertView;
                    if (loadingView == null) {
                        loadingView = inflater.inflate(android.R.layout.simple_list_item_1, parent, false);
                    }
                    TextView textView = loadingView.findViewById(android.R.id.text1);
                    if (textView != null) {
                        textView.setText("Loading more readings...");
                        textView.setPadding(16, 16, 16, 16);
                    }
                    return loadingView;
                }

                if (position >= bucketedReadings.size()) {
                    // Safety check in case list was modified
                    View loadingView = convertView;
                    if (loadingView == null) {
                        loadingView = inflater.inflate(android.R.layout.simple_list_item_1, parent, false);
                    }
                    return loadingView;
                }

                View rowView = convertView;
                if (rowView == null) {
                    rowView = inflater.inflate(R.layout.list_item_reading_row, parent, false);
                }

                SoundReading row = bucketedReadings.get(position);
                TextView timeCell = rowView.findViewById(R.id.timeCell);
                TextView valueCell = rowView.findViewById(R.id.valueCell);
                TextView statusCell = rowView.findViewById(R.id.statusCell);

                // Add null checks to prevent crashes
                if (timeCell != null) {
                    timeCell.setText(formatTimeText(new Date(row.timestampMs)));
                }
                if (valueCell != null) {
                    valueCell.setText(String.format(Locale.getDefault(), "%.1f", row.value));
                }
                if (statusCell != null) {
                    statusCell.setText(getStatusLabel(row.value));
                    statusCell.setTextColor(getStatusColor(row.value));
                }

                return rowView;
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}