package com.example.quietzone_app;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "sound_readings")
public class SoundReadingEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public long timestampMs;
    public float value;
    public String sensorKey;

    public SoundReadingEntity(long timestampMs, float value, String sensorKey) {
        this.timestampMs = timestampMs;
        this.value = value;
        this.sensorKey = sensorKey;
    }
}
