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
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.github.mikephil.charting.highlight.Highlight;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

// Removed invalid lifecycleScope and coroutine imports
import com.example.quietzone_app.AppDatabase;
import com.example.quietzone_app.SoundReadingDao;
import com.example.quietzone_app.SoundReadingEntity;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RoomActivity extends AppCompatActivity {

    public static final String EXTRA_SENSOR_KEY = "extra_sensor_key";
    public static final String EXTRA_ROOM_NAME = "extra_room_name";

    private static final float CHART_MIN_VISIBLE_WINDOW_SECONDS = 10f * 60f;
    private static final float CHART_MAX_VISIBLE_WINDOW_SECONDS = 60f * 60f;
    private static final float CHART_X_AXIS_MARGIN_RATIO = 0.05f;
    private static final float CHART_X_AXIS_MIN_MARGIN_SECONDS = 15f;
    private static final float TARGET_BAR_WIDTH_PX = 5f;
    private static final int PAGE_SIZE = 50;
    private static final int MAX_CHART_READINGS = 300;
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

<<<<<<< Updated upstream
    // Sorting options
    enum SortMode {
        TIMESTAMP_DESC, // newest first
        TIMESTAMP_ASC, // oldest first
        VALUE_DESC, // highest dB first
        VALUE_ASC, // lowest dB first
        STATUS_GROUP // group by Quiet/Moderate/Loud
    }

    private LineChart historyChart;
    private TextView chartDateLabel;
    private TextView statusText;
    private ListView readingsList;
    private ReadingsTableAdapter listAdapter;
    private android.widget.Button btnSortTimestampDesc;
    private android.widget.Button btnSortValue;
    private android.widget.Button btnSortStatus;

    private long chartMinTimestampMs = 0L;
    private long chartRangeMs = 0L;
    private String roomDisplayName;
    private String sensorKey;

    // Pagination state
    private final List<SoundReading> allReadings = new ArrayList<>();
    private QueryDocumentSnapshot lastVisible = null;
    private boolean isLoading = false;
    private boolean hasMoreData = true;
    private SortMode currentSortMode = SortMode.TIMESTAMP_DESC;
    private boolean isScrolling = false;

    // Room database
    private AppDatabase db;
    private SoundReadingDao soundReadingDao;

=======
    private final List<SoundReading> firestoreReadings = new ArrayList<>();
    private final List<SoundReading> liveReadings = new ArrayList<>();
    private final List<SoundReading> soundReadings = new ArrayList<>();

    private BarChart historyChart;
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

>>>>>>> Stashed changes
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

        sensorKey = getIntent().getStringExtra(EXTRA_SENSOR_KEY);
        if (sensorKey == null || sensorKey.trim().isEmpty()) {
            sensorKey = "sensor_1";
        }

        String roomName = getIntent().getStringExtra(EXTRA_ROOM_NAME);
        if (roomName == null || roomName.trim().isEmpty()) {
            roomName = getString(R.string.room_name_placeholder);
        }
        roomDisplayName = roomName;

        db = AppDatabase.getInstance(this);
        soundReadingDao = db.soundReadingDao();

        setupToolbar();
        setupViews();
        setupListPagination();

        // Load from local DB first, then update from Firestore
        loadFromLocalDbAndUpdate();
    }

    private void loadFromLocalDbAndUpdate() {
        // 1. Load from local DB in background
        new Thread(() -> {
            List<SoundReadingEntity> cached = soundReadingDao.getReadingsForSensor(sensorKey);
            runOnUiThread(() -> {
                allReadings.clear();
                for (SoundReadingEntity entity : cached) {
                    allReadings.add(new SoundReading(entity.timestampMs, entity.value));
                }
                applyCurrentSorting();
                updateUI();

                // 2. Fetch from Firestore in background
                fetchAndCacheFromFirestore();
            });
        }).start();
    }

    private void fetchAndCacheFromFirestore() {
        Query query = buildQuery();
        query.get().addOnSuccessListener(snapshot -> {
            List<SoundReading> newReadings = new ArrayList<>();
            List<SoundReadingEntity> entities = new ArrayList<>();
            QueryDocumentSnapshot lastDoc = null;
            for (QueryDocumentSnapshot doc : snapshot) {
                SoundReading reading = parseFirestoreReading(doc);
                if (reading != null) {
                    newReadings.add(reading);
                    entities.add(new SoundReadingEntity(reading.timestampMs, reading.value, sensorKey));
                }
                lastDoc = doc;
            }
            if (!newReadings.isEmpty()) {
                allReadings.clear();
                allReadings.addAll(newReadings);
                applyCurrentSorting();
                updateUI();
                // Save to local DB in background
                new Thread(() -> {
                    soundReadingDao.deleteReadingsForSensor(sensorKey);
                    soundReadingDao.insertAll(entities);
                }).start();
            }
        });
    }

    private void setupToolbar() {
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
    }

    private void setupViews() {
        historyChart = findViewById(R.id.historyChart);
        chartDateLabel = findViewById(R.id.chartDateLabel);
<<<<<<< Updated upstream
=======
        speedLabel = findViewById(R.id.speedLabel);
        soundText = findViewById(R.id.childSoundText);
>>>>>>> Stashed changes
        statusText = findViewById(R.id.statusText);
        readingsList = findViewById(R.id.readingsList);

<<<<<<< Updated upstream
=======
        speedLabel.setText(getString(R.string.room_sound_level_placeholder));
>>>>>>> Stashed changes
        chartDateLabel.setText(R.string.room_chart_loading);
        // Removed chartPointInfo, now using MarkerView for tooltip

        int onSurface = getResources().getColor(R.color.app_on_surface, getTheme());
        configureChart(onSurface);
<<<<<<< Updated upstream
        setupChartMarker();

        // Setup sort buttons
        btnSortTimestampDesc = findViewById(R.id.btn_sort_recent);
        btnSortValue = findViewById(R.id.btn_sort_loudest);
        btnSortStatus = findViewById(R.id.btn_sort_status);

        btnSortTimestampDesc.setOnClickListener(v -> setSortMode(SortMode.TIMESTAMP_DESC));
        btnSortValue.setOnClickListener(v -> setSortMode(SortMode.VALUE_DESC));
        btnSortStatus.setOnClickListener(v -> setSortMode(SortMode.STATUS_GROUP));

        listAdapter = new ReadingsTableAdapter();
        readingsList.setAdapter(listAdapter);
    }

    private void setupChartMarker() {
        // Set up custom MarkerView for tooltip
        ChartMarkerView marker = new ChartMarkerView(this, R.layout.marker_view);
        historyChart.setMarker(marker);
    }

    private void setupListPagination() {
        readingsList.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                isScrolling = (scrollState != SCROLL_STATE_IDLE);
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                // Load more when user scrolls near the end
                if (!isScrolling)
                    return;
                if (isLoading || !hasMoreData)
                    return;

                int lastVisibleItem = firstVisibleItem + visibleItemCount;
                if (lastVisibleItem >= totalItemCount - 5) {
                    loadMoreReadings();
                }
            }
        });
    }

    private void loadMoreReadings() {
        if (isLoading || !hasMoreData) {
            return;
        }

        isLoading = true;
        Query query = buildQuery();

        query.get().addOnSuccessListener(snapshot -> {
            try {
                if (snapshot.isEmpty()) {
                    hasMoreData = false;
                    isLoading = false;
                    updateUI();
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

                allReadings.addAll(newReadings);
                if (lastDoc != null) {
                    lastVisible = lastDoc;
                }

                // Load one more to check if there's more data
                if (snapshot.size() < PAGE_SIZE) {
                    hasMoreData = false;
                }

                applyCurrentSorting();
                updateUI();
                isLoading = false;

            } catch (Exception e) {
                Log.e("RoomActivity", "Error parsing Firestore data", e);
                isLoading = false;
            }
        }).addOnFailureListener(e -> {
            Log.e("RoomActivity", "Failed to fetch data", e);
            isLoading = false;
        });
    }

    private Query buildQuery() {
        Query query = FirebaseFirestore.getInstance()
                .collection("sound_data")
                .document(sensorKey)
                .collection("readings")
                .orderBy("timestamp", Query.Direction.DESCENDING);
=======
        listAdapter = new ReadingsTableAdapter();
        readingsList.setAdapter(listAdapter);
        fetchFirestoreReadingsOnce(sensorKey);
        attachLiveSensorListener(sensorKey);
    }

    private void fetchFirestoreReadingsOnce(String sensorKey) {
        Query readingsQuery = FirebaseFirestore.getInstance()
                .collection("sound_data")
                .document(sensorKey)
                .collection("readings")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(MAX_RAW_READINGS);

        readingsQuery.get().addOnSuccessListener(snapshot -> {
            try {
                firestoreReadings.clear();
                for (QueryDocumentSnapshot doc : snapshot) {
                    SoundReading reading = parseFirestoreReading(doc);
                    if (reading != null) {
                        firestoreReadings.add(reading);
                    }
                }

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

    private void attachLiveSensorListener(String sensorKey) {
        liveSensorRef = FirebaseDatabase.getInstance().getReference("sound_data/live").child(sensorKey);
        liveSensorListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    return;
                }
>>>>>>> Stashed changes

        if (lastVisible != null) {
            query = query.startAfter(lastVisible);
        }

<<<<<<< Updated upstream
        return query.limit(PAGE_SIZE);
    }

    private SoundReading parseFirestoreReading(QueryDocumentSnapshot doc) {
        Object valueObj = doc.get("value");
        if (valueObj == null) {
            valueObj = doc.get("db_level");
        }
        if (valueObj == null) {
            return null;
        }

        try {
            float soundLevel = Float.parseFloat(valueObj.toString());
            long timestampMs = parseTimestampToMillis(doc.get("timestamp"));
            if (timestampMs <= 0L) {
                timestampMs = System.currentTimeMillis();
            }
            return new SoundReading(timestampMs, soundLevel);
        } catch (Exception e) {
            Log.e("RoomActivity", "Error parsing reading", e);
            return null;
        }
    }

    private void applyCurrentSorting() {
        switch (currentSortMode) {
            case TIMESTAMP_ASC:
                Collections.sort(allReadings, (a, b) -> Long.compare(a.timestampMs, b.timestampMs));
                break;
            case VALUE_DESC:
                Collections.sort(allReadings, (a, b) -> Float.compare(b.value, a.value));
                break;
            case VALUE_ASC:
                Collections.sort(allReadings, (a, b) -> Float.compare(a.value, b.value));
                break;
            case STATUS_GROUP:
                sortByStatusGroup();
                break;
            case TIMESTAMP_DESC:
            default:
                Collections.sort(allReadings, (a, b) -> Long.compare(b.timestampMs, a.timestampMs));
                break;
        }
    }

    private void sortByStatusGroup() {
        // Group: Quiet (< 50) → Moderate (50-70) → Loud (≥ 70)
        // Within each group, sort by timestamp DESC
        Collections.sort(allReadings, (a, b) -> {
            int groupA = getStatusGroup(a.value);
            int groupB = getStatusGroup(b.value);
            if (groupA != groupB) {
                return Integer.compare(groupA, groupB);
=======
                    latestLiveValue = Float.parseFloat(value.toString());
                    latestLiveTimestampMs = extractLiveTimestampMs(snapshot);
                    if (latestLiveTimestampMs <= 0L) {
                        latestLiveTimestampMs = System.currentTimeMillis();
                    }

                    appendLiveReading(latestLiveValue, latestLiveTimestampMs);
                    rebuildMergedReadings();
                    refreshUiFromReadings();
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
                    return formatInUserTimeZone(new Date(timestampMs), "MMM d h:mm a");
                }
                return formatInUserTimeZone(new Date(timestampMs), "h:mm:ss a");
>>>>>>> Stashed changes
            }
            return Long.compare(b.timestampMs, a.timestampMs);
        });
    }

<<<<<<< Updated upstream
    private int getStatusGroup(float dB) {
        if (dB < 50)
            return 0;
        if (dB < 70)
            return 1;
        return 2;
    }

    public void setSortMode(SortMode mode) {
        if (currentSortMode != mode) {
            currentSortMode = mode;
            applyCurrentSorting();
            listAdapter.notifyDataSetChanged();
            renderChart();
        }
    }

    private void updateUI() {
        if (allReadings.isEmpty()) {
            chartDateLabel.setText(R.string.room_chart_no_data);
            statusText.setText("");
            listAdapter.notifyDataSetChanged();
            renderChart();
            return;
        }

        // Display latest reading based on current sort
        SoundReading latest = allReadings.get(0);
        // Compose status string: "--db NoiseLevel: Quiet"
        String statusLabel = getStatusLabelWithDb(latest.value);
        statusText.setText(statusLabel);
        updateChartDateLabel();
        listAdapter.notifyDataSetChanged();
        renderChart();
    }

    private void renderChart() {
        List<Entry> entries = buildEntriesFromReadings();
=======
        historyChart.getAxisRight().setEnabled(false);
        historyChart.getAxisLeft().setAxisMinimum(0f);
        historyChart.getAxisLeft().setAxisMaximum(120f);
        historyChart.getAxisLeft().setLabelCount(7, true);
        historyChart.getAxisLeft().setDrawGridLines(true);
        historyChart.getAxisLeft().setTextColor(textColor);
        historyChart.getAxisLeft().setTextSize(11f);
    }

    private void renderChart() {
        List<BarEntry> entries = buildBarEntriesFromReadings();
>>>>>>> Stashed changes

        if (entries.isEmpty()) {
            historyChart.clear();
            historyChart.invalidate();
            return;
        }

        float axisMaxSeconds = Math.max(1f, chartRangeMs / 1000f);
        float xAxisMarginSeconds = Math.max(CHART_X_AXIS_MIN_MARGIN_SECONDS,
                axisMaxSeconds * CHART_X_AXIS_MARGIN_RATIO);
        float axisMinimum = Math.max(0f, 0f - xAxisMarginSeconds);
        float axisMaximum = axisMaxSeconds + xAxisMarginSeconds;

        BarDataSet dataSet = new BarDataSet(entries, "Noise (dB)");
        int lineColor = getResources().getColor(R.color.status_moderate, getTheme());
        dataSet.setColor(lineColor);
        dataSet.setDrawValues(false);
        dataSet.setValueTextColor(lineColor);
        dataSet.setValueTextSize(10f);
<<<<<<< Updated upstream
        dataSet.setLineWidth(2f);

        // Draw circles (dots) for each data point
        dataSet.setDrawCircles(true);
        dataSet.setCircleRadius(4f);
        dataSet.setCircleColor(lineColor);
        dataSet.setCircleHoleRadius(2f);
        dataSet.setCircleHoleColor(android.graphics.Color.WHITE);

        // Fill area under the line with transparent orange
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(getResources().getColor(R.color.status_moderate, getTheme()));
        dataSet.setFillAlpha(80);

        dataSet.setHighLightColor(lineColor);

        LineData lineData = new LineData(dataSet);
        historyChart.setData(lineData);

        // Set X axis limits - full range
        XAxis xAxis = historyChart.getXAxis();
        xAxis.setAxisMinimum(axisMinimum);
        xAxis.setAxisMaximum(axisMaximum);

        // Force MPAndroidChart to show more labels by setting label count
        // Show at most every 3-5 data points to avoid overcrowding
        int labelCount = Math.max(3, Math.min(entries.size() / 3, 15));
        xAxis.setLabelCount(labelCount, false);

        // Auto-scale Y axis based on data
        float minY = Float.MAX_VALUE;
        float maxY = Float.MIN_VALUE;
        for (Entry entry : entries) {
            if (entry.getY() < minY) {
                minY = entry.getY();
            }
            if (entry.getY() > maxY) {
                maxY = entry.getY();
            }
        }

        // Add 10% padding above and below
        float padding = (maxY - minY) * 0.1f;
        if (padding == 0)
            padding = 5f;

        historyChart.getAxisLeft().setAxisMinimum(Math.max(0f, minY - padding));
        historyChart.getAxisLeft().setAxisMaximum(maxY + padding);

        // Set visible window to show a reasonable time range
        float visibleWindowSeconds = Math.min(axisMaxSeconds, 30f * 60f);
        if (visibleWindowSeconds < 60f) {
            visibleWindowSeconds = Math.min(axisMaxSeconds, 5f * 60f);
        }

        historyChart.setVisibleXRangeMaximum(visibleWindowSeconds);
        historyChart.moveViewToX(Math.max(axisMinimum, axisMaximum - visibleWindowSeconds));

        historyChart.invalidate();
    }

    private List<Entry> buildEntriesFromReadings() {
        if (allReadings.isEmpty()) {
=======
        dataSet.setHighLightAlpha(100);

        BarData barData = new BarData(dataSet);
        float contentWidthPx = historyChart.getViewPortHandler().contentWidth();
        float barWidthInXAxisUnits;
        if (contentWidthPx > 0f) {
            float unitsPerPx = axisMaxSeconds / contentWidthPx;
            barWidthInXAxisUnits = Math.max(0.5f, unitsPerPx * TARGET_BAR_WIDTH_PX);
        } else {
            // Fallback before first layout pass.
            float granularity = calculateXAxisGranularity(axisMaxSeconds);
            barWidthInXAxisUnits = Math.max(0.5f, granularity * 0.1f);
        }
        barData.setBarWidth(barWidthInXAxisUnits);
        historyChart.setData(barData);

        float visibleWindow = Math.min(axisMaxSeconds, CHART_MAX_VISIBLE_WINDOW_SECONDS);
        visibleWindow = Math.max(visibleWindow, CHART_MIN_VISIBLE_WINDOW_SECONDS);
        historyChart.setVisibleXRangeMaximum(visibleWindow);
        historyChart.moveViewToX(Math.max(0f, axisMaxSeconds - visibleWindow));
        historyChart.invalidate();
    }

    private List<BarEntry> buildBarEntriesFromReadings() {
        if (soundReadings.isEmpty()) {
>>>>>>> Stashed changes
            chartMinTimestampMs = 0L;
            chartRangeMs = 0L;
            return new ArrayList<>();
        }

        // Use only recent readings for chart (max 300)
        List<SoundReading> chartReadings = new ArrayList<>(allReadings);
        if (chartReadings.size() > MAX_CHART_READINGS) {
            chartReadings = new ArrayList<>(chartReadings.subList(0, MAX_CHART_READINGS));
        }

        // Sort by timestamp for chart display
        Collections.sort(chartReadings, (a, b) -> Long.compare(a.timestampMs, b.timestampMs));

        long minTs = chartReadings.get(0).timestampMs;
        long maxTs = chartReadings.get(chartReadings.size() - 1).timestampMs;
        if (maxTs <= minTs) {
            maxTs = minTs + 1000L;
        }

        chartMinTimestampMs = minTs;
        chartRangeMs = maxTs - minTs;

<<<<<<< Updated upstream
        List<Entry> entries = new ArrayList<>(chartReadings.size());
        for (SoundReading reading : chartReadings) {
=======
        List<BarEntry> entries = new ArrayList<>(sortedAsc.size());
        for (SoundReading reading : sortedAsc) {
>>>>>>> Stashed changes
            float xSeconds = (reading.timestampMs - minTs) / 1000f;
            entries.add(new BarEntry(xSeconds, reading.value));
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

<<<<<<< Updated upstream
    private void configureChart(int textColor) {
        historyChart.setTouchEnabled(true);
        historyChart.setDragEnabled(true);
        historyChart.setDragXEnabled(true);
        historyChart.setDragYEnabled(false);
        historyChart.setScaleEnabled(true);
        historyChart.setScaleXEnabled(true);
        historyChart.setScaleYEnabled(false);
        historyChart.setPinchZoom(true);
        historyChart.setDoubleTapToZoomEnabled(true);
        historyChart.setDrawGridBackground(false);
        historyChart.setExtraBottomOffset(20f);

        Description description = new Description();
        description.setText("");
        historyChart.setDescription(description);

        Legend legend = historyChart.getLegend();
        legend.setEnabled(false);

        XAxis xAxis = historyChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawLabels(true);
        xAxis.setGranularityEnabled(false);
        xAxis.setTextColor(textColor);
        xAxis.setTextSize(8f);
        xAxis.setAvoidFirstLastClipping(false);
        xAxis.setLabelRotationAngle(45f);
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
                Date date = new Date(timestampMs);

                // Show HH:MM format for each data point
                return formatInUserTimeZone(date, "HH:mm");
            }
        });

        historyChart.getAxisRight().setEnabled(false);
        // Y axis will be auto-scaled based on data
        historyChart.getAxisLeft().setTextColor(textColor);
        historyChart.getAxisLeft().setTextSize(11f);
        historyChart.getAxisLeft().setLabelCount(6, true);
        historyChart.getAxisLeft().setDrawGridLines(true);
    }

    // Returns a string like "45.2 dB: Quiet"
    private String getStatusLabelWithDb(float dB) {
        String level;
        int color;
        if (dB < 50) {
            level = getString(R.string.room_group_quiet);
            color = getResources().getColor(R.color.status_quiet, getTheme());
        } else if (dB < 70) {
            level = getString(R.string.room_group_moderate);
            color = getResources().getColor(R.color.status_moderate, getTheme());
        } else {
            level = getString(R.string.room_group_loud);
            color = getResources().getColor(R.color.status_loud, getTheme());
        }
        // Set color for statusText
        statusText.setTextColor(color);
        return String.format("%.1f dB: %s", dB, level);
    }

    private void updateChartDateLabel() {
        if (allReadings.isEmpty()) {
            chartDateLabel.setText(R.string.room_chart_no_data);
=======
    private SoundReading parseFirestoreReading(QueryDocumentSnapshot doc) {
        Object valueObj = doc.get("value");
        if (valueObj == null) {
            valueObj = doc.get("db_level");
        }
        if (valueObj == null) {
            return null;
        }

        float soundLevel = Float.parseFloat(valueObj.toString());
        long timestampMs = parseTimestampToMillis(doc.get("timestamp"));
        if (timestampMs <= 0L) {
            timestampMs = System.currentTimeMillis();
        }
        return new SoundReading(timestampMs, soundLevel, false);
    }

    private void rebuildMergedReadings() {
        soundReadings.clear();
        soundReadings.addAll(firestoreReadings);
        soundReadings.addAll(liveReadings);

        Collections.sort(soundReadings, (a, b) -> Long.compare(b.timestampMs, a.timestampMs));
        trimReadings();
    }

    private void appendLiveReading(float value, long timestampMs) {
        if (Float.isNaN(value) || timestampMs <= 0L) {
>>>>>>> Stashed changes
            return;
        }

        long minTs = Long.MAX_VALUE;
        long maxTs = Long.MIN_VALUE;
        for (SoundReading reading : allReadings) {
            if (reading.timestampMs < minTs) {
                minTs = reading.timestampMs;
            }
            if (reading.timestampMs > maxTs) {
                maxTs = reading.timestampMs;
            }
        }

        // Check if data spans multiple days
        Calendar calMin = Calendar.getInstance();
        calMin.setTimeInMillis(minTs);
        Calendar calMax = Calendar.getInstance();
        calMax.setTimeInMillis(maxTs);

        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        String minDate = dateFormat.format(new Date(minTs));
        String maxDate = dateFormat.format(new Date(maxTs));

        if (minDate.equals(maxDate)) {
            // Same day - show single date
            chartDateLabel.setText(minDate);
        } else {
            // Multiple days - show range
            chartDateLabel.setText(minDate + " to " + maxDate);
        }
    }

    private String formatChartRangeTimeText(Date time) {
        return formatInUserTimeZone(time, "MMM d, h:mm:ss a");
    }

    private String formatTimeText(Date time) {
        // Simple readable format: "Mar 29, 2:23 PM"
        SimpleDateFormat formatter = new SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault());
        return formatter.format(time);
    }

    private String formatInUserTimeZone(Date time, String pattern) {
        SimpleDateFormat formatter = new SimpleDateFormat(pattern, Locale.getDefault());
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

    private long parseTimestampToMillis(Object rawTimestamp) {
        if (rawTimestamp == null) {
            return 0L;
        }

        // Handle Firebase Timestamp directly
        if (rawTimestamp.getClass().getSimpleName().equals("Timestamp")) {
            try {
                // Firebase Timestamp has toDate() method
                java.lang.reflect.Method toDateMethod = rawTimestamp.getClass().getMethod("toDate");
                Date date = (Date) toDateMethod.invoke(rawTimestamp);
                return date.getTime();
            } catch (Exception e) {
                Log.e("RoomActivity", "Error converting Firebase Timestamp", e);
            }
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

<<<<<<< Updated upstream
=======
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
        return formatInUserTimeZone(time, "MMM d, h:mm:ss a");
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

>>>>>>> Stashed changes
    private static final class SoundReading {
        final long timestampMs;
        final float value;

        SoundReading(long timestampMs, float value) {
            this.timestampMs = timestampMs;
            this.value = value;
        }
    }

    private final class ReadingsTableAdapter extends BaseAdapter {
        private final LayoutInflater inflater = LayoutInflater.from(RoomActivity.this);

        @Override
        public int getCount() {
            return allReadings.size();
        }

        @Override
        public Object getItem(int position) {
            return allReadings.get(position);
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

            SoundReading row = allReadings.get(position);
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