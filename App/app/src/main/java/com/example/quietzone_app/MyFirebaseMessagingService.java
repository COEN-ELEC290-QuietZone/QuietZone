package com.example.quietzone_app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String CHANNEL_ID = "default_channel";
    private static final String CHANNEL_NAME = "Default Channel";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        // Handle FCM messages here
        if (remoteMessage.getNotification() != null) {
            String title = remoteMessage.getNotification().getTitle();
            String body = remoteMessage.getNotification().getBody();
            Log.d("FCM", "Message Notification Title: " + title);
            Log.d("FCM", "Message Notification Body: " + body);

            // Show notification
            showNotification(title, body);
        }

        // If the message contains data payload
        if (remoteMessage.getData().size() > 0) {
            Log.d("FCM", "Message data payload: " + remoteMessage.getData());
            // Handle data payload here (e.g., update app's state)
        }
    }

    @Override
    public void onNewToken(String token) {
        // Handle the token refresh
        Log.d("FCM", "New Token: " + token);
        // Optionally, send this token to your server or backend
    }

    private void showNotification(String title, String body) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

<<<<<<< Updated upstream
        // Create the notification channel for Android 8.0+ (API 26) and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Default Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            notificationManager.createNotificationChannel(channel);
        }

        // Create a notification builder
=======
        // Create the notification channel (only needed for Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            // Make sure to create the channel only once
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }

        // Create the notification builder
>>>>>>> Stashed changes
        Notification.Builder notificationBuilder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationBuilder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            notificationBuilder = new Notification.Builder(this);
        }

<<<<<<< Updated upstream
        // Use your custom notification icon
        notificationBuilder.setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(R.drawable.ic_notifications)  // Custom icon placed in res/drawable
                .setAutoCancel(true);  // Auto cancel the notification when tapped

        // Show the notification
        notificationManager.notify(0, notificationBuilder.build());
=======
        // Ensure you have a valid icon in your drawable folder (replace with your icon)
        notificationBuilder.setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(R.drawable.ic_notifications)  // Custom icon placed in res/drawable
                .setAutoCancel(true)  // Auto cancel the notification when tapped
                .setPriority(Notification.PRIORITY_DEFAULT);  // Set priority for the notification

        // Show the notification
        if (notificationManager != null) {
            notificationManager.notify(0, notificationBuilder.build());
        }
>>>>>>> Stashed changes
    }
}