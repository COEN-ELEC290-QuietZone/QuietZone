package com.example.quietzone_app;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
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
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.listener.ChartTouchListener;
import com.github.mikephil.charting.listener.OnChartGestureListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.Timestamp;
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

    private static final float CHART_MIN_VISIBLE_WINDOW_SECONDS = 10f * 60f;
    private static final float CHART_MAX_VISIBLE_WINDOW_SECONDS = 60f * 60f;
    private static final float TARGET_BAR_WIDTH_PX = 5f;
    private static final int MAX_LIST_ROWS = 3000;
    private static final int MAX_RAW_READINGS = 300;
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
    private ChartMarkerView chartMarker;
    private TextView chartTitleLabel;
    private TextView chartDateLabel;
    private TextView soundText;
    private TextView statusText;
    private ReadingsTableAdapter listAdapter;
    private long chartMinTimestampMs = 0L;
    private long chartRangeMs = 0L;
    private String roomDisplayName;

    private enum SortMode {
        TIMESTAMP_DESC, // newest first
        TIMESTAMP_ASC, // oldest first
        VALUE_DESC, // highest dB first
        VALUE_ASC // lowest dB first
    }

    private SortMode currentSortMode = SortMode.TIMESTAMP_DESC;

    private DatabaseReference liveSensorRef;
    private ValueEventListener liveSensorListener;
    private float latestLiveValue = Float.NaN;
    private long latestLiveTimestampMs = 0L;

    // Pagination fields for loading more data as user scrolls
    private QueryDocumentSnapshot lastVisibleDocument = null;
    private boolean isLoadingMore = false;
    private boolean hasMoreData = true;
    private static final int FIRESTORE_PAGE_SIZE = 50;

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
        chartTitleLabel = findViewById(R.id.chartTitleLabel);
        chartDateLabel = findViewById(R.id.chartDateLabel);
        soundText = findViewById(R.id.locationText);
        statusText = findViewById(R.id.statusText);
        ListView readingsList = findViewById(R.id.readingsList);

        chartTitleLabel.setText(R.string.room_sound_level_placeholder);
        chartDateLabel.setText(R.string.room_chart_loading);

        int onSurface = getResources().getColor(R.color.app_on_surface, getTheme());
        configureChart(onSurface);
        setupChartMarker();
        listAdapter = new ReadingsTableAdapter();
        readingsList.setAdapter(listAdapter);

        // Setup scroll listener to load more data as user scrolls down
        readingsList.setOnScrollListener(new android.widget.AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(android.widget.AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(android.widget.AbsListView view, int firstVisibleItem, int visibleItemCount,
                    int totalItemCount) {
                // Load more data when user scrolls near the bottom
                int lastVisibleItem = firstVisibleItem + visibleItemCount;
                if (lastVisibleItem >= totalItemCount - 5 && !isLoadingMore && hasMoreData) {
                    loadMoreFromFirestore();
                }
            }
        });

        // Setup table header sorting
        TextView headerTime = findViewById(R.id.headerTime);
        TextView headerDb = findViewById(R.id.headerDb);
        TextView headerStatus = findViewById(R.id.headerStatus);

        headerTime.setOnClickListener(v -> {
            currentSortMode = (currentSortMode == SortMode.TIMESTAMP_DESC) ? SortMode.TIMESTAMP_ASC
                    : SortMode.TIMESTAMP_DESC;
            applySorting();
            updateSortIndicators(headerTime, headerDb);
        });

        headerDb.setOnClickListener(v -> {
            currentSortMode = (currentSortMode == SortMode.VALUE_DESC) ? SortMode.VALUE_ASC : SortMode.VALUE_DESC;
            applySorting();
            updateSortIndicators(headerTime, headerDb);
        });

        // Status column is not sortable - just show visual feedback
        headerStatus.setClickable(false);
        headerStatus.setFocusable(false);

        // Initialize sort indicators
        updateSortIndicators(headerTime, headerDb);
        fetchFirestoreReadingsOnce(sensorKey);
        attachLiveSensorListener(sensorKey);
    }

    private void fetchFirestoreReadingsOnce(String sensorKey) {
        Query readingsQuery = FirebaseFirestore.getInstance()
                .collection("sound_data")
                .document(sensorKey)
                .collection("readings")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(FIRESTORE_PAGE_SIZE);

        readingsQuery.get().addOnSuccessListener(snapshot -> {
            try {
                firestoreReadings.clear();
                QueryDocumentSnapshot lastDoc = null;
                for (QueryDocumentSnapshot doc : snapshot) {
                    SoundReading reading = parseFirestoreReading(doc);
                    if (reading != null) {
                        firestoreReadings.add(reading);
                    }
                    lastDoc = doc;
                }
                lastVisibleDocument = lastDoc;
                hasMoreData = !snapshot.isEmpty();

                Collections.sort(firestoreReadings, (a, b) -> Long.compare(b.timestampMs, a.timestampMs));
                rebuildMergedReadings();
                refreshUiFromReadings();
            } catch (Exception e) {
                Log.e("RoomActivity", "Error parsing Firestore sensor data", e);
                soundText.setText(getString(R.string.room_status_error));
                updateToolbarSoundLevel(Float.NaN);
            }
        }).addOnFailureListener(e -> {
            Log.e("RoomActivity", "Failed to fetch Firestore sensor data", e);
            soundText.setText(getString(R.string.room_database_error_format, e.getMessage()));
            updateToolbarSoundLevel(Float.NaN);
        });
    }

    private void loadMoreFromFirestore() {
        if (isLoadingMore || !hasMoreData || lastVisibleDocument == null) {
            return;
        }

        isLoadingMore = true;
        String sensorKey = getIntent().getStringExtra(EXTRA_SENSOR_KEY);
        if (sensorKey == null || sensorKey.isEmpty()) {
            sensorKey = "sensor_1";
        }

        Query nextQuery = FirebaseFirestore.getInstance()
                .collection("sound_data")
                .document(sensorKey)
                .collection("readings")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .startAfter(lastVisibleDocument)
                .limit(FIRESTORE_PAGE_SIZE);

        nextQuery.get().addOnSuccessListener(snapshot -> {
            try {
                if (snapshot.isEmpty()) {
                    hasMoreData = false;
                    isLoadingMore = false;
                    return;
                }

                List<SoundReading> newReadings = new ArrayList<>();
                QueryDocumentSnapshot lastDoc = null;
                for (QueryDocumentSnapshot doc : snapshot) {
                    SoundReading reading = parseFirestoreReading(doc);
                    if (reading != null) {
                        newReadings.add(reading);
                    }
                    lastDoc = doc;
                }

                firestoreReadings.addAll(newReadings);
                lastVisibleDocument = lastDoc;

                rebuildMergedReadings();
                listAdapter.notifyDataSetChanged();
                isLoadingMore = false;

                Log.d("RoomActivity", "Loaded " + newReadings.size() + " more readings from Firestore");
            } catch (Exception e) {
                Log.e("RoomActivity", "Error loading more data", e);
                isLoadingMore = false;
            }
        }).addOnFailureListener(e -> {
            Log.e("RoomActivity", "Failed to load more data from Firestore", e);
            isLoadingMore = false;
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

                    // Update title with live value and status
                    chartTitleLabel.setText(getString(R.string.room_sound_level_display_format, latestLiveValue));
                    updateStatus(statusText, latestLiveValue);
                    updateToolbarSoundLevel(latestLiveValue);

                    // Update location from Firebase
                    updateLocationFromSnapshot(snapshot);
                } catch (Exception e) {
                    Log.e("RoomActivity", "Error parsing live sensor value", e);
                    updateToolbarSoundLevel(Float.NaN);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("RoomActivity", "Live listener cancelled", error.toException());
            }
        };
        liveSensorRef.addValueEventListener(liveSensorListener);
    }

    private void updateLocationFromSnapshot(DataSnapshot snapshot) {
        try {
            DataSnapshot locationSnapshot = snapshot.child("location");
            if (locationSnapshot.exists()) {
                Object locationObj = locationSnapshot.getValue();
                if (locationObj != null) {
                    String location = locationObj.toString();
                    soundText.setText("Location: " + location);
                } else {
                    soundText.setText("Location: Not available");
                }
            } else {
                soundText.setText("Location: Not available");
            }
        } catch (Exception e) {
            Log.e("RoomActivity", "Error getting location", e);
            soundText.setText("Location: Error");
        }
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

    private void setupChartMarker() {
        // Set up custom MarkerView for tooltip
        chartMarker = new ChartMarkerView(this, R.layout.marker_view);
        historyChart.setMarker(chartMarker);
    }

    private void updateSortIndicators(TextView headerTime, TextView headerDb) {
        // Reset headers
        headerTime.setText(R.string.room_table_time);
        headerDb.setText(R.string.room_table_db);

        // Add indicator based on current sort mode
        switch (currentSortMode) {
            case TIMESTAMP_ASC:
                headerTime.setText(getString(R.string.room_table_time) + " ▲");
                break;
            case TIMESTAMP_DESC:
                headerTime.setText(getString(R.string.room_table_time) + " ▼");
                break;
            case VALUE_ASC:
                headerDb.setText(getString(R.string.room_table_db) + " ▲");
                break;
            case VALUE_DESC:
                headerDb.setText(getString(R.string.room_table_db) + " ▼");
                break;
        }
    }

    private void applySorting() {
        switch (currentSortMode) {
            case TIMESTAMP_ASC:
                Collections.sort(soundReadings, (a, b) -> Long.compare(a.timestampMs, b.timestampMs));
                break;
            case VALUE_DESC:
                Collections.sort(soundReadings, (a, b) -> Float.compare(b.value, a.value));
                break;
            case VALUE_ASC:
                Collections.sort(soundReadings, (a, b) -> Float.compare(a.value, b.value));
                break;
            case TIMESTAMP_DESC:
            default:
                Collections.sort(soundReadings, (a, b) -> Long.compare(b.timestampMs, a.timestampMs));
                break;
        }
        listAdapter.notifyDataSetChanged();
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
        historyChart.setExtraRightOffset(36f);

        Description description = new Description();
        description.setText("");
        historyChart.setDescription(description);

        Legend legend = historyChart.getLegend();
        legend.setEnabled(false);

        XAxis xAxis = historyChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(true);
        xAxis.setDrawLabels(true);
        xAxis.setLabelCount(6, true);
        xAxis.setGranularityEnabled(true);
        xAxis.setGranularity(5f * 60f);
        xAxis.setAxisMinimum(0f);
        xAxis.setAxisMaximum(1f);
        xAxis.setTextColor(textColor);
        xAxis.setTextSize(10f);
        xAxis.setAvoidFirstLastClipping(false);
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
                    return formatInUserTimeZone(new Date(timestampMs), "MMM d h:mm a");
                }
                return formatInUserTimeZone(new Date(timestampMs), "h:mm a");
            }
        });

        historyChart.getAxisRight().setEnabled(false);
        historyChart.getAxisLeft().setAxisMinimum(30f);
        historyChart.getAxisLeft().setAxisMaximum(100f);
        historyChart.getAxisLeft().setLabelCount(8, true);
        historyChart.getAxisLeft().setDrawGridLines(true);
        historyChart.getAxisLeft().setTextColor(textColor);
        historyChart.getAxisLeft().setTextSize(10f);
    }

    private void renderChart() {
        List<Entry> entries = buildLineEntriesFromReadings();

        if (entries.isEmpty()) {
            historyChart.clear();
            historyChart.invalidate();
            return;
        }

        // Update marker with the correct epoch offset
        if (chartMarker != null) {
            chartMarker.setMinTimestampMs(chartMinTimestampMs);
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
        dataSet.setValueTextColor(lineColor);
        dataSet.setValueTextSize(10f);
        dataSet.setLineWidth(2f);
        dataSet.setDrawCircles(true);
        dataSet.setCircleRadius(3f);
        dataSet.setCircleColor(lineColor);

        // Add transparent fill under the line
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(lineColor);
        dataSet.setFillAlpha(60); // Semi-transparent (0-255)

        LineData lineData = new LineData(dataSet);
        historyChart.setData(lineData);

        float visibleWindow = Math.min(axisMaxSeconds, CHART_MAX_VISIBLE_WINDOW_SECONDS);
        visibleWindow = Math.max(visibleWindow, CHART_MIN_VISIBLE_WINDOW_SECONDS);
        historyChart.setVisibleXRangeMaximum(visibleWindow);
        historyChart.moveViewToX(Math.max(0f, axisMaxSeconds - visibleWindow));

        // Setup gesture listener to update date label on chart scroll
        historyChart.setOnChartGestureListener(new OnChartGestureListener() {
            @Override
            public void onChartGestureStart(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {
            }

            @Override
            public void onChartGestureEnd(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {
                updateChartDateLabelFromScroll();
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
                updateChartDateLabelFromScroll();
            }

            @Override
            public void onChartTranslate(MotionEvent me, float dx, float dy) {
                updateChartDateLabelFromScroll();
            }
        });

        historyChart.invalidate();
    }

    private List<Entry> buildLineEntriesFromReadings() {
        if (soundReadings.isEmpty()) {
            chartMinTimestampMs = 0L;
            chartRangeMs = 0L;
            return new ArrayList<>();
        }

        List<SoundReading> sortedAsc = new ArrayList<>(soundReadings);
        Collections.sort(sortedAsc, (a, b) -> Long.compare(a.timestampMs, b.timestampMs));

        long minTs = sortedAsc.get(0).timestampMs;
        long maxTs = sortedAsc.get(sortedAsc.size() - 1).timestampMs;
        if (maxTs <= minTs) {
            maxTs = minTs + 1000L;
        }

        chartMinTimestampMs = minTs;
        chartRangeMs = maxTs - minTs;

        List<Entry> entries = new ArrayList<>(sortedAsc.size());
        for (SoundReading reading : sortedAsc) {
            float xSeconds = (reading.timestampMs - minTs) / 1000f;
            entries.add(new Entry(xSeconds, reading.value));
        }
        return entries;
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
            Log.w("RoomActivity", "Firestore document missing 'value' and 'db_level' fields: " + doc.getId());
            return null;
        }

        try {
            float soundLevel = Float.parseFloat(valueObj.toString());
            long timestampMs = parseTimestampToMillis(doc.get("timestamp"));
            if (timestampMs <= 0L) {
                Log.w("RoomActivity", "Invalid timestamp for document: " + doc.getId());
                timestampMs = System.currentTimeMillis();
            }
            return new SoundReading(timestampMs, soundLevel, false);
        } catch (NumberFormatException e) {
            Log.w("RoomActivity", "Failed to parse sound level from: " + valueObj, e);
            return null;
        }
    }

    private void rebuildMergedReadings() {
        soundReadings.clear();
        soundReadings.addAll(firestoreReadings);
        // Live readings are not added to the table/graph, only shown in toolbar

        Collections.sort(soundReadings, (a, b) -> Long.compare(b.timestampMs, a.timestampMs));
        trimReadings();
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
                    // Strings without zone info are assumed to be UTC before converting to user
                    // locale.
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
        if (soundReadings.isEmpty()) {
            chartDateLabel.setText(R.string.room_chart_no_data);
            chartTitleLabel.setText(R.string.room_sound_level_placeholder);
            if (Float.isNaN(latestLiveValue)) {
                soundText.setText(getString(R.string.room_status_waiting));
                updateToolbarSoundLevel(Float.NaN);
            } else {
                soundText.setText(getString(R.string.room_sound_level_display_format, latestLiveValue));
                chartTitleLabel.setText(getString(R.string.room_sound_level_display_format, latestLiveValue));
                updateStatus(statusText, latestLiveValue);
                updateToolbarSoundLevel(latestLiveValue);
            }
            applySorting();
            listAdapter.notifyDataSetChanged();
            renderChart();
            return;
        }

        float latestValue = soundReadings.get(0).value;
        soundText.setText(getString(R.string.room_sound_level_display_format, latestValue));
        chartTitleLabel.setText(getString(R.string.room_sound_level_display_format, latestValue));
        updateStatus(statusText, latestValue);
        updateToolbarSoundLevel(latestValue);
        updateChartDateLabel();
        applySorting();
        listAdapter.notifyDataSetChanged();
        renderChart();
    }

    private void updateToolbarSoundLevel(float value) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) {
            return;
        }
        actionBar.setTitle(roomDisplayName);
        actionBar.setSubtitle(null);
    }

    private void updateChartDateLabelFromScroll() {
        if (soundReadings.isEmpty() || chartRangeMs <= 0) {
            return;
        }

        // Get the leftmost visible timestamp
        float lowestVisibleX = historyChart.getLowestVisibleX();
        long leftTimestampMs = chartMinTimestampMs + (long) (lowestVisibleX * 1000f);

        // Get the rightmost visible timestamp
        float highestVisibleX = historyChart.getHighestVisibleX();
        long rightTimestampMs = chartMinTimestampMs + (long) (highestVisibleX * 1000f);

        String start = formatChartRangeTimeText(new Date(leftTimestampMs));
        String end = formatChartRangeTimeText(new Date(rightTimestampMs));
        chartDateLabel.setText(getString(R.string.room_chart_range_format, start, end));
    }

    private void updateChartDateLabel() {
        if (soundReadings.isEmpty()) {
            chartDateLabel.setText(R.string.room_chart_no_data);
            return;
        }

        long minTs = Long.MAX_VALUE;
        long maxTs = Long.MIN_VALUE;
        for (SoundReading reading : soundReadings) {
            if (reading.timestampMs < minTs) {
                minTs = reading.timestampMs;
            }
            if (reading.timestampMs > maxTs) {
                maxTs = reading.timestampMs;
            }
        }

        String start = formatChartRangeTimeText(new Date(minTs));
        String end = formatChartRangeTimeText(new Date(maxTs));
        chartDateLabel.setText(getString(R.string.room_chart_range_format, start, end));
    }

    private String formatChartRangeTimeText(Date time) {
        return formatInUserTimeZone(time, "MMM d, h:mm a");
    }

    private String formatTimeText(Date time) {
        return formatInUserTimeZone(time, "MMM d, h:mm a");
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

        @Override
        public int getCount() {
            return Math.min(soundReadings.size(), MAX_LIST_ROWS);
        }

        @Override
        public Object getItem(int position) {
            return soundReadings.get(position);
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

            SoundReading row = soundReadings.get(position);
            TextView timeCell = rowView.findViewById(R.id.timeCell);
            TextView valueCell = rowView.findViewById(R.id.valueCell);
            TextView statusCell = rowView.findViewById(R.id.statusCell);

            timeCell.setText(formatTimeText(new Date(row.timestampMs)));
            valueCell.setText(String.format(Locale.getDefault(), "%.1f", row.value));
            statusCell.setText(getStatusLabel(row.value));
            statusCell.setTextColor(getStatusColor(row.value));

            return rowView;
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
