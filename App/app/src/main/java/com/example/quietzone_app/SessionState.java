package com.example.quietzone_app;

import android.content.Context;
import android.content.SharedPreferences;

public final class SessionState {

    private static final String PREFS_NAME = "quietzone_session";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_IS_ADMIN = "is_admin";

    private SessionState() {
    }

    public static void setUserSession(Context context, String userId, boolean isAdmin) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_USER_ID, userId)
                .putBoolean(KEY_IS_ADMIN, isAdmin)
                .apply();
    }

    public static String getUserId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_USER_ID, null);
    }

    public static boolean isAdmin(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_IS_ADMIN, false);
    }

    public static void setAdmin(Context context, boolean isAdmin) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_IS_ADMIN, isAdmin).apply();
    }

    public static void clear(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }
}
