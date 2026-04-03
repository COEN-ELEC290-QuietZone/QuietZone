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
import androidx.appcompat.widget.PopupMenu;
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
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.github.mikephil.charting.listener.OnChartGestureListener;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.ViewPortHandler;

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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RoomActivity extends AppCompatActivity implements OnChartGestureListener {

    public static final String EXTRA_SENSOR_KEY = "extra_sensor_key";
    public static final String EXTRA_ROOM_NAME = "extra_room_name";

    private static final float CHART_MIN_VISIBLE_WINDOW_SECONDS = 10f * 60f;
    private static final float CHART_MAX_VISIBLE_WINDOW_SECONDS = 60f * 60f;
    private static final float CHART_X_AXIS_MARGIN_SECONDS = 175f;
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
    private ChartMarkerView chartMarker;
    private ReadingsTableAdapter listAdapter;
    private android.widget.Button btnResetSort;

    // Header column clickable fields
    private TextView headerTime;
    private TextView headerDb;
    private TextView headerStatus;
    private boolean isTimeAscending = false;
    private boolean isDbAscending = false;
    private boolean isStatusAscending = false;

    // Status filter (null = no filter, otherwise filter by status like "Quiet",
    // "Moderate", "Loud")
    private String statusFilterValue = null;

    private long chartMinTimestampMs = 0L;
    private long chartRangeMs = 0L;
    private String roomDisplayName;
    private String sensorKey;

    // Set to track hidden gap-filler entry indices
    private Set<Integer> hiddenEntryIndices = new HashSet<>();

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
        statusText = findViewById(R.id.statusText);
        readingsList = findViewById(R.id.readingsList);

        chartDateLabel.setText(R.string.room_chart_loading);
        // Removed chartPointInfo, now using MarkerView for tooltip

        int onSurface = getResources().getColor(R.color.app_on_surface, getTheme());
        configureChart(onSurface);
        setupChartMarker();

        // Setup reset sort button
        btnResetSort = findViewById(R.id.btn_reset_sort);
        btnResetSort.setOnClickListener(v -> onResetSortClicked());

        // Setup header column click listeners
        headerTime = findViewById(R.id.headerTime);
        headerDb = findViewById(R.id.headerDb);
        headerStatus = findViewById(R.id.headerStatus);

        if (headerTime != null) {
            headerTime.setOnClickListener(v -> onHeaderTimeClicked());
        }
        if (headerDb != null) {
            headerDb.setOnClickListener(v -> onHeaderDbClicked());
        }
        // Status column shows filter popup menu
        if (headerStatus != null) {
            headerStatus.setOnClickListener(v -> showStatusFilterPopup(v));
        }

        listAdapter = new ReadingsTableAdapter();
        readingsList.setAdapter(listAdapter);
    }

    private void setupChartMarker() {
        // Set up custom MarkerView for tooltip
        chartMarker = new ChartMarkerView(this, R.layout.marker_view);
        chartMarker.setHiddenEntryIndices(hiddenEntryIndices);
        historyChart.setMarker(chartMarker);
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

        if (lastVisible != null) {
            query = query.startAfter(lastVisible);
        }

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
            }
            return Long.compare(b.timestampMs, a.timestampMs);
        });
    }

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

    private void onHeaderTimeClicked() {
        // Toggle between DESC (newest first) and ASC (oldest first)
        if (currentSortMode == SortMode.TIMESTAMP_DESC) {
            currentSortMode = SortMode.TIMESTAMP_ASC;
            isTimeAscending = true;
        } else {
            currentSortMode = SortMode.TIMESTAMP_DESC;
            isTimeAscending = false;
        }
        applyCurrentSorting();
        updateHeaderIndicators();
        listAdapter.notifyDataSetChanged();
        renderChart();
    }

    private void onHeaderDbClicked() {
        // Toggle between DESC (loudest first) and ASC (quietest first)
        if (currentSortMode == SortMode.VALUE_DESC) {
            currentSortMode = SortMode.VALUE_ASC;
            isDbAscending = true;
        } else {
            currentSortMode = SortMode.VALUE_DESC;
            isDbAscending = false;
        }
        applyCurrentSorting();
        updateHeaderIndicators();
        listAdapter.notifyDataSetChanged();
        renderChart();
    }

    private void onHeaderStatusClicked() {
        currentSortMode = SortMode.STATUS_GROUP;
        isStatusAscending = !isStatusAscending;
        applyCurrentSorting();
        updateHeaderIndicators();
        listAdapter.notifyDataSetChanged();
        renderChart();
    }

    private void showStatusFilterPopup(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("All (No Filter)");
        popup.getMenu().add(getString(R.string.room_group_quiet));
        popup.getMenu().add(getString(R.string.room_group_moderate));
        popup.getMenu().add(getString(R.string.room_group_loud));

        popup.setOnMenuItemClickListener(item -> {
            String selectedFilter = item.getTitle().toString();
            if (selectedFilter.equals("All (No Filter)")) {
                statusFilterValue = null;
            } else {
                statusFilterValue = selectedFilter;
            }
            applyStatusFilter();
            return true;
        });
        popup.show();
    }

    private void applyStatusFilter() {
        // Filter the list based on statusFilterValue
        if (statusFilterValue == null) {
            // No filter - show all readings
            listAdapter.notifyDataSetChanged();
        } else {
            // Show only readings with matching status
            listAdapter.notifyDataSetChanged();
        }
        renderChart();
    }

    private void onResetSortClicked() {
        // Reset to default sort mode (newest first by timestamp) and clear filters
        currentSortMode = SortMode.TIMESTAMP_DESC;
        isTimeAscending = false;
        isDbAscending = false;
        isStatusAscending = false;
        statusFilterValue = null;
        applyCurrentSorting();
        updateHeaderIndicators();
        listAdapter.notifyDataSetChanged();
        renderChart();
    }

    private void updateHeaderIndicators() {
        if (headerTime == null || headerDb == null || headerStatus == null) {
            return;
        }

        // Reset all headers to default text (without arrows)
        headerTime.setText(getString(R.string.room_table_time));
        headerDb.setText(getString(R.string.room_table_db));

        // Update Status header with filter indicator if active
        if (statusFilterValue != null) {
            headerStatus.setText(getString(R.string.room_table_status) + " ⊙");
        } else {
            headerStatus.setText(getString(R.string.room_table_status));
        }

        // Update the active header with arrow indicator only
        switch (currentSortMode) {
            case TIMESTAMP_DESC:
            case TIMESTAMP_ASC:
                headerTime.setText(getString(R.string.room_table_time) + (isTimeAscending ? " ↑" : " ↓"));
                break;
            case VALUE_DESC:
            case VALUE_ASC:
                headerDb.setText(getString(R.string.room_table_db) + (isDbAscending ? " ↑" : " ↓"));
                break;
            case STATUS_GROUP:
                // Status column does not show sort indicators
                break;
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
        updateHeaderIndicators();
        listAdapter.notifyDataSetChanged();
        renderChart();
    }

    private void renderChart() {
        List<Entry> entries = buildEntriesFromReadings();

        if (chartMarker != null) {
            chartMarker.setMinTimestampMs(chartMinTimestampMs);
            chartMarker.setHiddenEntryIndices(hiddenEntryIndices);
        }

        if (entries.isEmpty()) {
            historyChart.clear();
            historyChart.invalidate();
            return;
        }

        float axisMaxSeconds = Math.max(1f, chartRangeMs / 1000f);
        float xAxisMarginSeconds = CHART_X_AXIS_MARGIN_SECONDS;
        float axisMinimum = -xAxisMarginSeconds;
        float axisMaximum = axisMaxSeconds + xAxisMarginSeconds;

        LineDataSet dataSet = new LineDataSet(entries, "Noise (dB)");
        int lineColor = getResources().getColor(R.color.status_moderate, getTheme());
        dataSet.setColor(lineColor);
        dataSet.setDrawValues(false);
        dataSet.setValueTextColor(lineColor);
        dataSet.setValueTextSize(10f);
        dataSet.setLineWidth(2f);
        dataSet.setDrawValues(false);
        dataSet.setDrawCircles(true);
        dataSet.setDrawFilled(true);
        dataSet.setMode(LineDataSet.Mode.LINEAR);

        // Draw circles (dots) for each data point, except for hidden gap-filler entries
        dataSet.setDrawCircles(true);

        List<Integer> circleColors = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            if (hiddenEntryIndices.contains(i)) {
                // Transparent color for hidden entries (no dot visible)
                circleColors.add(android.graphics.Color.TRANSPARENT);
            } else {
                circleColors.add(lineColor);
            }
        }
        dataSet.setCircleColors(circleColors);
        dataSet.setCircleRadius(4f);
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

        // Auto-scale Y axis based on data (ignore hidden gap-filler entries)
        float minY = Float.MAX_VALUE;
        float maxY = Float.MIN_VALUE;
        for (int i = 0; i < entries.size(); i++) {
            // Skip hidden entries in Y-axis calculation
            if (hiddenEntryIndices.contains(i)) {
                continue;
            }
            Entry entry = entries.get(i);
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

    // private static final long GAP_THRESHOLD_MS = 10*60*1000; // 2 seconds (since
    // 0.5s interval)
    private List<Entry> buildEntriesFromReadings() {
        if (allReadings.isEmpty()) {
            chartMinTimestampMs = 0L;
            chartRangeMs = 0L;
            hiddenEntryIndices.clear();
            return new ArrayList<>();
        }

        // Limit readings (optional, keep your logic)
        List<SoundReading> chartReadings = new ArrayList<>(allReadings);
        if (chartReadings.size() > MAX_CHART_READINGS) {
            chartReadings = new ArrayList<>(chartReadings.subList(0, MAX_CHART_READINGS));
        }

        // Sort by timestamp ASC (important for gap detection)
        Collections.sort(chartReadings, (a, b) -> Long.compare(a.timestampMs, b.timestampMs));
        long gapThreshold = 10 * 60 * 1000; // fallback = 10 min

        if (chartReadings.size() >= 2) {
            long expectedInterval = chartReadings.get(1).timestampMs - chartReadings.get(0).timestampMs;

            // Safety: avoid weird values (like 0 or negative)
            if (expectedInterval > 0) {
                gapThreshold = expectedInterval * 2;
            }
        }
        long minTs = chartReadings.get(0).timestampMs;
        long maxTs = chartReadings.get(chartReadings.size() - 1).timestampMs;
        if (maxTs <= minTs) {
            maxTs = minTs + 1000L;
        }

        chartMinTimestampMs = minTs;
        chartRangeMs = maxTs - minTs;

        List<Entry> entries = new ArrayList<>();
        hiddenEntryIndices.clear();

        long GAP_FILLER_INTERVAL = 5 * 60 * 1000; // 5 minutes

        SoundReading prev = null;

        for (SoundReading reading : chartReadings) {

            if (prev != null) {
                long diff = reading.timestampMs - prev.timestampMs;

                if (diff > gapThreshold) {
                    // Add hidden gap-filler entries (0dB) at start and end of gap window
                    // Start of gap (5 min after previous reading)
                    long gapStartTs = prev.timestampMs + GAP_FILLER_INTERVAL;
                    float gapStartX = (gapStartTs - minTs) / 1000f;

                    // End of gap (5 min before current reading)
                    long gapEndTs = reading.timestampMs - GAP_FILLER_INTERVAL;
                    float gapEndX = (gapEndTs - minTs) / 1000f;

                    // Add gap-start marker (0dB, hidden)
                    int startIdx = entries.size();
                    entries.add(new Entry(gapStartX, 0f));
                    hiddenEntryIndices.add(startIdx);

                    // Add gap-end marker (0dB, hidden)
                    int endIdx = entries.size();
                    entries.add(new Entry(gapEndX, 0f));
                    hiddenEntryIndices.add(endIdx);
                }
            }

            float xSeconds = (reading.timestampMs - minTs) / 1000f;
            entries.add(new Entry(xSeconds, reading.value));

            prev = reading;
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
        historyChart.setOnChartGestureListener(this);

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
            return;
        }
        // Get the visible range from the chart viewport
        float lowestVisibleX = historyChart.getLowestVisibleX();
        float highestVisibleX = historyChart.getHighestVisibleX();

        // Convert from chart coordinates (seconds) back to milliseconds
        long visibleMinTs = chartMinTimestampMs + (long) (Math.max(0f, lowestVisibleX) * 1000f);
        long visibleMaxTs = chartMinTimestampMs + (long) (Math.max(0f, highestVisibleX) * 1000f);

        // If no valid chart range, fall back to all data range
        if (chartMinTimestampMs <= 0L || chartRangeMs <= 0L) {
            visibleMinTs = Long.MAX_VALUE;
            visibleMaxTs = Long.MIN_VALUE;
            for (SoundReading reading : allReadings) {
                if (reading.timestampMs < visibleMinTs) {
                    visibleMinTs = reading.timestampMs;
                }
                if (reading.timestampMs > visibleMaxTs) {
                    visibleMaxTs = reading.timestampMs;
                }
            }
        }

        // Clamp values to actual data range
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

        visibleMinTs = Math.max(visibleMinTs, minTs);
        visibleMaxTs = Math.min(visibleMaxTs, maxTs);

        // Check if visible data spans multiple days
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault());
        String minDate = dateFormat.format(new Date(visibleMinTs));
        String maxDate = dateFormat.format(new Date(visibleMaxTs));

        if (minDate.equals(maxDate)) {
            // Same day - show single date
            chartDateLabel.setText(minDate);
        } else {
            // Multiple days - show range
            chartDateLabel.setText(minDate + " - " + maxDate);
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

        private List<SoundReading> getFilteredReadings() {
            if (statusFilterValue == null) {
                return allReadings;
            }

            List<SoundReading> filtered = new ArrayList<>();
            for (SoundReading reading : allReadings) {
                String readingStatus = getStatusLabel(reading.value);
                if (readingStatus.equals(statusFilterValue)) {
                    filtered.add(reading);
                }
            }
            return filtered;
        }

        @Override
        public int getCount() {
            return getFilteredReadings().size();
        }

        @Override
        public Object getItem(int position) {
            return getFilteredReadings().get(position);
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

            SoundReading row = getFilteredReadings().get(position);
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

    // OnChartGestureListener implementations
    @Override
    public void onChartGestureStart(MotionEvent me,
            com.github.mikephil.charting.listener.ChartTouchListener.ChartGesture lastPerformedGesture) {
        // Update date label when gesture starts (optional)
    }

    @Override
    public void onChartGestureEnd(MotionEvent me,
            com.github.mikephil.charting.listener.ChartTouchListener.ChartGesture lastPerformedGesture) {
        // Update date label when gesture ends
        updateChartDateLabel();
    }

    @Override
    public void onChartLongPressed(MotionEvent me) {
        // Handle long press
    }

    @Override
    public void onChartDoubleTapped(MotionEvent me) {
        // Update date label when double tap (zoom) occurs
        updateChartDateLabel();
    }

    @Override
    public void onChartSingleTapped(MotionEvent me) {
        // Handle single tap
    }

    @Override
    public void onChartFling(MotionEvent me1, MotionEvent me2, float velocityX, float velocityY) {
        // Update date label when user flings/scrolls
        updateChartDateLabel();
    }

    @Override
    public void onChartScale(MotionEvent me, float scaleX, float scaleY) {
        // Update date label when scaling occurs
        updateChartDateLabel();
    }

    @Override
    public void onChartTranslate(MotionEvent me, float dX, float dY) {
        // Update date label when chart is translated/panned
        updateChartDateLabel();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}