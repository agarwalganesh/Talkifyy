package com.example.talkifyy;

import android.app.Application;
import android.util.Log;
import com.example.talkifyy.utils.FirebaseStorageInitializer;
import com.example.talkifyy.utils.NotificationUtil;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;

/**
 * Application class for Talkifyy
 * Handles Firebase Storage initialization to prevent "bucket not properly configured" errors
 */
public class TalkifyyApplication extends Application {
    
    private static final String TAG = "TalkifyyApplication";
    
    @Override
    public void onCreate() {
        try {
            super.onCreate();
            
            Log.d(TAG, "🚀 Talkifyy Application starting...");
            
            // Wrap all initialization in try-catch to prevent app crashes during startup
            initializeFirebase();
            initializeNotifications();
            initializeFCMToken();
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Critical error during Application onCreate", e);
            // Still continue - don't let the app crash during startup
        } finally {
            Log.d(TAG, "✅ Talkifyy Application initialization completed (with or without errors)");
        }
    }
    
    private void initializeFirebase() {
        try {
            // Initialize Firebase App first
            FirebaseApp.initializeApp(this);
            Log.d(TAG, "✅ Firebase App initialized");
            
            // Initialize Firebase Storage with proper configuration
            FirebaseStorageInitializer.initialize();
            Log.d(TAG, "✅ Firebase Storage initialization completed");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error during Firebase initialization", e);
            // Continue app startup even if Firebase has issues
        }
    }
    
    private void initializeNotifications() {
        try {
            // Create notification channel
            NotificationUtil.createNotificationChannel(this);
            Log.d(TAG, "✅ Notification channel created");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error creating notification channel", e);
            // Continue even if notifications fail
        }
    }
    
    private void initializeFCMToken() {
        try {
            Log.d(TAG, "🔥 Initializing FCM token...");
            
            FirebaseMessaging.getInstance().getToken()
                    .addOnCompleteListener(task -> {
                        try {
                            if (!task.isSuccessful()) {
                                Log.w(TAG, "⚠️ Fetching FCM registration token failed", task.getException());
                                return;
                            }

                            // Get new FCM registration token
                            String token = task.getResult();
                            Log.d(TAG, "🏆 FCM registration token: " + token);

                            // TODO: Send token to app server (if needed)
                            // For now, we'll just log it
                        } catch (Exception e) {
                            Log.e(TAG, "❌ Error processing FCM token", e);
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "❌ Error initializing FCM token", e);
        }
    }
}
