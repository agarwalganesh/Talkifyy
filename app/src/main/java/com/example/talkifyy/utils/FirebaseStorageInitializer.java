package com.example.talkifyy.utils;

import android.util.Log;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

/**
 * Helper class to initialize Firebase Storage with proper configuration
 * Solves the "storage bucket not properly configured" error
 */
public class FirebaseStorageInitializer {
    
    private static final String TAG = "FirebaseStorageInit";
    private static boolean initialized = false;
    
    /**
     * Initialize Firebase Storage with proper bucket configuration
     * Call this from your Application class onCreate() method
     */
    public static void initialize() {
        if (initialized) {
            Log.d(TAG, "✅ Firebase Storage already initialized");
            return;
        }
        
        try {
            Log.d(TAG, "🚀 Initializing Firebase Storage...");
            
            // Strategy 1: Try default initialization
            if (initializeDefault()) {
                initialized = true;
                return;
            }
            
            // Strategy 2: Try with explicit bucket URL
            if (initializeWithExplicitBucket()) {
                initialized = true;
                return;
            }
            
            Log.w(TAG, "⚠️ Storage initialization completed with warnings");
            initialized = true;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to initialize Firebase Storage", e);
            // Don't block app startup even if storage init fails
            initialized = true;
        }
    }
    
    private static boolean initializeDefault() {
        try {
            FirebaseStorage storage = FirebaseStorage.getInstance();
            StorageReference ref = storage.getReference();
            String bucket = ref.getBucket();
            
            if (bucket != null && !bucket.isEmpty()) {
                Log.d(TAG, "✅ Default storage initialized successfully");
                Log.d(TAG, "📦 Bucket: " + bucket);
                return true;
            } else {
                Log.w(TAG, "⚠️ Default storage bucket is null");
                return false;
            }
        } catch (Exception e) {
            Log.w(TAG, "⚠️ Default storage initialization failed: " + e.getMessage());
            return false;
        }
    }
    
    private static boolean initializeWithExplicitBucket() {
        try {
            String bucketUrl = "gs://gkg-talkifyy.firebasestorage.app";
            Log.d(TAG, "🔧 Trying explicit bucket: " + bucketUrl);
            
            FirebaseStorage storage = FirebaseStorage.getInstance(bucketUrl);
            StorageReference ref = storage.getReference();
            
            Log.d(TAG, "✅ Explicit bucket storage initialized");
            Log.d(TAG, "📦 Bucket: " + ref.getBucket());
            return true;
            
        } catch (Exception e) {
            Log.w(TAG, "⚠️ Explicit bucket initialization failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Test storage connectivity
     * Call this to verify storage is working
     */
    public static void testConnectivity() {
        try {
            FirebaseStorage storage = FirebaseUtil.getFirebaseStorage();
            StorageReference ref = storage.getReference();
            
            Log.d(TAG, "🔍 Testing storage connectivity...");
            Log.d(TAG, "📁 Storage reference: " + ref.toString());
            Log.d(TAG, "🏢 Bucket: " + ref.getBucket());
            
            // Try a simple operation
            ref.listAll()
                    .addOnSuccessListener(listResult -> {
                        Log.d(TAG, "✅ Storage connectivity test passed");
                        Log.d(TAG, "📂 Items: " + listResult.getItems().size());
                        Log.d(TAG, "📁 Folders: " + listResult.getPrefixes().size());
                    })
                    .addOnFailureListener(e -> {
                        if (e.getMessage() != null && e.getMessage().contains("Permission denied")) {
                            Log.d(TAG, "✅ Storage is working (permission denied is normal)");
                        } else {
                            Log.w(TAG, "⚠️ Storage connectivity test failed: " + e.getMessage());
                        }
                    });
                    
        } catch (Exception e) {
            Log.e(TAG, "❌ Storage connectivity test error", e);
        }
    }
}
