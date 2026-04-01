package com.example.quietzone_app;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

public class ThemeHelper {
    private static final String PREF_NAME = "theme_prefs";
    private static final String KEY_IS_DARK_MODE = "is_dark_mode";

    public static void toggleTheme(Context context) {
        boolean isDarkMode = isDarkMode(context);
        setDarkMode(context, !isDarkMode);
        applyTheme(context);
    }

    public static void setDarkMode(Context context, boolean isDark) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_IS_DARK_MODE, isDark).apply();
    }

    public static boolean isDarkMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_IS_DARK_MODE, false);
    }

    public static void applyTheme(Context context) {
        if (isDarkMode(context)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
    }
}