package com.example.talkifyy;

import android.app.Application;
import android.util.Log;
import com.example.talkifyy.utils.FirebaseStorageInitializer;
import com.google.firebase.FirebaseApp;

/**
 * Application class for Talkifyy
 * Handles Firebase Storage initialization to prevent "bucket not properly configured" errors
 */
public class TalkifyyApplication extends Application {
    
    private static final String TAG = "TalkifyyApplication";
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        Log.d(TAG, "🚀 Talkifyy Application starting...");
        
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
        
        Log.d(TAG, "✅ Talkifyy Application initialized successfully");
    }
}
