package com.example.quietzone_app;

import android.content.Context;
import android.widget.TextView;

import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ChartMarkerView extends MarkerView {

    private final TextView tvContent;
    private long minTimestampMs = 0L;

    public ChartMarkerView(Context context, int layoutResource) {
        super(context, layoutResource);
        tvContent = findViewById(R.id.marker_content);
    }

    /** Call this every time the chart is re-rendered so the marker has the correct epoch offset. */
    public void setMinTimestampMs(long minTimestampMs) {
        this.minTimestampMs = minTimestampMs;
    }

    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        long realMs = minTimestampMs + (long) (e.getX() * 1000f);
        String timeStr = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                .format(new Date(realMs));
        tvContent.setText(String.format(Locale.getDefault(), "%.1f dB\n%s", e.getY(), timeStr));
        super.refreshContent(e, highlight);
    }

    @Override
    public MPPointF getOffset() {
        // Centre the bubble horizontally above the tap point, with 10px gap
        return new MPPointF(-(getWidth() / 2f), -getHeight() - 10f);
    }
}