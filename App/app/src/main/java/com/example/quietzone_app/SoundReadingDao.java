package com.example.quietzone_app;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface SoundReadingDao {
    @Query("SELECT * FROM sound_readings WHERE sensorKey = :sensorKey ORDER BY timestampMs DESC")
    List<SoundReadingEntity> getReadingsForSensor(String sensorKey);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<SoundReadingEntity> readings);

    @Query("DELETE FROM sound_readings WHERE sensorKey = :sensorKey")
    void deleteReadingsForSensor(String sensorKey);
}
