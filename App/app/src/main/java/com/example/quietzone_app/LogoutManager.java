package com.example.quietzone_app;

import android.app.Activity;
import android.content.Intent;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessaging;

public final class LogoutManager {

    private LogoutManager() {
    }

    public static void performLogout(Activity activity) {
        FirebaseMessaging.getInstance().unsubscribeFromTopic("sensor_alerts");
        FirebaseListenerRegistry.clearAll();
        FirebaseAuth.getInstance().signOut();
        SessionState.clear(activity);

        Intent intent = new Intent(activity, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }
}
