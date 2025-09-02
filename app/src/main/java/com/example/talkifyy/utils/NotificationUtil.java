package com.example.talkifyy.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.talkifyy.ChatActivity;
import com.example.talkifyy.R;
import com.example.talkifyy.model.UserModel;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class NotificationUtil {
    
    private static final String TAG = "NotificationUtil";
    private static final String CHANNEL_ID = "chat_notifications";
    private static final String CHANNEL_NAME = "Chat Messages";
    private static final String FCM_URL = "https://fcm.googleapis.com/fcm/send";
    
    // You'll need to replace this with your actual Firebase Server Key
    // Get it from Firebase Console > Project Settings > Cloud Messaging > Server Key
    private static final String SERVER_KEY = "YOUR_FIREBASE_SERVER_KEY";
    
    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Receive notifications for new chat messages");
            channel.enableLights(true);
            channel.enableVibration(true);
            
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
            
            Log.d(TAG, "Notification channel created");
        }
    }
    
    public static void showLocalNotification(Context context, String senderName, String message, UserModel otherUser) {
        Log.d(TAG, "Showing local notification from " + senderName + ": " + message);
        
        // Create intent to open chat when notification is tapped
        Intent intent = new Intent(context, ChatActivity.class);
        AndroidUtil.passUserModelAsIntent(intent, otherUser);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context, 
            0, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.chat_icon)
                .setContentTitle(senderName)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(new long[]{0, 500, 1000})
                .setLights(0xFF0000FF, 300, 1000);
        
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        
        try {
            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
            Log.d(TAG, "Local notification shown successfully");
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied for showing notification", e);
        }
    }
    
    public static void sendFCMNotification(String recipientToken, String senderName, String message, String chatId) {
        if (SERVER_KEY.equals("YOUR_FIREBASE_SERVER_KEY")) {
            Log.w(TAG, "FCM Server key not configured. Using local notifications only.");
            return;
        }
        
        Log.d(TAG, "Sending FCM notification to token: " + recipientToken);
        
        OkHttpClient client = new OkHttpClient();
        
        try {
            JSONObject json = new JSONObject();
            json.put("to", recipientToken);
            
            // Notification payload
            JSONObject notification = new JSONObject();
            notification.put("title", senderName);
            notification.put("body", message);
            notification.put("icon", "chat_icon");
            notification.put("sound", "default");
            json.put("notification", notification);
            
            // Data payload for handling in app
            JSONObject data = new JSONObject();
            data.put("senderName", senderName);
            data.put("message", message);
            data.put("chatId", chatId);
            data.put("type", "chat_message");
            json.put("data", data);
            
            RequestBody body = RequestBody.create(
                json.toString(),
                MediaType.parse("application/json; charset=utf-8")
            );
            
            Request request = new Request.Builder()
                    .url(FCM_URL)
                    .post(body)
                    .addHeader("Authorization", "key=" + SERVER_KEY)
                    .addHeader("Content-Type", "application/json")
                    .build();
            
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Failed to send FCM notification", e);
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body().string();
                    if (response.isSuccessful()) {
                        Log.d(TAG, "FCM notification sent successfully: " + responseBody);
                    } else {
                        Log.e(TAG, "FCM notification failed: " + responseBody);
                    }
                }
            });
            
        } catch (JSONException e) {
            Log.e(TAG, "Error creating FCM notification JSON", e);
        }
    }
    
    public static void sendNotificationToUser(String recipientUserId, String senderName, String message, String chatId) {
        Log.d(TAG, "Getting FCM token for user: " + recipientUserId);
        
        // Get recipient's FCM token from Firestore
        FirebaseUtil.allUserCollectionReference().document(recipientUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        UserModel recipient = documentSnapshot.toObject(UserModel.class);
                        if (recipient != null && recipient.getFcmToken() != null && !recipient.getFcmToken().isEmpty()) {
                            Log.d(TAG, "Found FCM token, sending notification");
                            sendFCMNotification(recipient.getFcmToken(), senderName, message, chatId);
                        } else {
                            Log.w(TAG, "Recipient FCM token not found or empty");
                        }
                    } else {
                        Log.w(TAG, "Recipient user document not found");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get recipient FCM token", e);
                });
    }
}
