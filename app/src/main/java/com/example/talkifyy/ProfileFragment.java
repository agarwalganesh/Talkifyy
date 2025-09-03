package com.example.talkifyy;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.graphics.Bitmap;
import android.provider.MediaStore;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.talkifyy.model.UserModel;
import com.example.talkifyy.utils.AndroidUtil;
import com.example.talkifyy.utils.FirebaseUtil;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;


public class ProfileFragment extends Fragment {

    private static final String TAG = "ProfileFragment";
    TextView usernameText, phoneText;
    Button logoutBtn;
    ProgressBar progressBar;
    ImageView profileImageView;
    UserModel currentUserModel;
    ActivityResultLauncher<Intent> imagePickLauncher;
    Uri selectedImageUri;

    public ProfileFragment() {
        // Required empty public constructor
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        imagePickLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            Intent data = result.getData();
                            if (data != null && data.getData() != null) {
                                selectedImageUri = data.getData();
                                Log.d(TAG, "Image selected: " + selectedImageUri.toString());
                                AndroidUtil.setProfilePic(getContext(), selectedImageUri, profileImageView);
                                uploadToFirebase();
                            }
                        }
                    }
                }
        );
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        
        usernameText = view.findViewById(R.id.profile_username);
        phoneText = view.findViewById(R.id.profile_phone);
        logoutBtn = view.findViewById(R.id.logout_btn);
        progressBar = view.findViewById(R.id.profile_progress_bar);
        profileImageView = view.findViewById(R.id.profile_image_view);
        
        logoutBtn.setOnClickListener((v) -> {
            FirebaseUtil.logout();
            Intent intent = new Intent(getContext(), SplashActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
        
        profileImageView.setOnClickListener((v) -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            imagePickLauncher.launch(intent);
        });
        
        getUserData();
        
        return view;
    }
    
    void getUserData() {
        setInProgress(true);
        
        FirebaseUtil.currentUserDetails().get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                setInProgress(false);
                if(task.isSuccessful()) {
                    currentUserModel = task.getResult().toObject(UserModel.class);
                    if(currentUserModel != null) {
                        usernameText.setText(currentUserModel.getUsername());
                        phoneText.setText(currentUserModel.getPhone());
                        loadProfilePicture();
                    }
                } else {
                    AndroidUtil.showToast(getContext(), "Failed to load profile data");
                }
            }
        });
    }
    
    void setInProgress(boolean inProgress) {
        if(inProgress) {
            progressBar.setVisibility(View.VISIBLE);
        } else {
            progressBar.setVisibility(View.GONE);
        }
    }
    
    void uploadToFirebase() {
        if (selectedImageUri == null) {
            Log.e(TAG, "Selected image URI is null");
            AndroidUtil.showToast(getContext(), "No image selected");
            return;
        }
        
        // Validate that user is logged in
        if (!FirebaseUtil.isLoggedIn()) {
            Log.e(TAG, "User is not logged in");
            AndroidUtil.showToast(getContext(), "Please login first");
            return;
        }
        
        Log.d(TAG, "🔥 FIREBASE UPLOAD DEBUG START 🔥");
        Log.d(TAG, "👤 User ID: " + FirebaseUtil.currentUserId());
        Log.d(TAG, "🖼️ Image URI: " + selectedImageUri.toString());
        Log.d(TAG, "🔐 Auth state: " + (FirebaseUtil.isLoggedIn() ? "LOGGED_IN" : "NOT_LOGGED_IN"));
        
        // Test Storage connectivity first
        testStorageConnectivity();
        
        setInProgress(true);
        
        // Try emergency direct upload first
        emergencyDirectUpload();
    }
    
    void uploadWithFallbackStrategies() {
        // Strategy 1: Try new folder structure first
        uploadToNewPath();
    }
    
    void uploadToNewPath() {
        Log.d(TAG, "Attempting upload to new path structure");
        
        try {
            // Create a simple filename
            String fileName = FirebaseUtil.currentUserId() + ".jpg";
            StorageReference imageRef = FirebaseUtil.getFirebaseStorage()
                    .getReference()
                    .child("profile_pictures")
                    .child(fileName);
            
            Log.d(TAG, "Uploading to path: " + imageRef.getPath());
            Log.d(TAG, "Using bucket: " + imageRef.getBucket());
            
            imageRef.putFile(selectedImageUri)
                    .addOnSuccessListener(taskSnapshot -> {
                        Log.d(TAG, "✅ Upload successful to new path");
                        getDownloadUrlAndSave(imageRef);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "❌ Upload failed to new path: " + e.getMessage());
                        Log.e(TAG, "Error details: " + e.getClass().getSimpleName());
                        // Try fallback strategy
                        uploadToLegacyPath();
                    })
                    .addOnProgressListener(taskSnapshot -> {
                        double progress = (100.0 * taskSnapshot.getBytesTransferred()) / taskSnapshot.getTotalByteCount();
                        Log.d(TAG, "Upload progress: " + (int) progress + "%");
                    });
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to create storage reference for new path", e);
            uploadToLegacyPath();
        }
    }
    
    void uploadToLegacyPath() {
        Log.d(TAG, "Attempting upload to legacy path structure");
        
        StorageReference imageRef = FirebaseUtil.getCurrentProfilePicStorageRef();
        Log.d(TAG, "Uploading to legacy path: " + imageRef.getPath());
        
        imageRef.putFile(selectedImageUri)
                .addOnSuccessListener(taskSnapshot -> {
                    Log.d(TAG, "✅ Upload successful to legacy path");
                    getDownloadUrlAndSave(imageRef);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Upload failed to legacy path: " + e.getMessage());
                    // Try public upload strategy
                    uploadToPublicPath();
                });
    }
    
    void uploadToPublicPath() {
        Log.d(TAG, "Attempting upload to public path structure");
        
        try {
            // Try uploading to a more permissive path
            String fileName = "user_" + FirebaseUtil.currentUserId() + "_" + System.currentTimeMillis() + ".jpg";
            StorageReference imageRef = FirebaseUtil.getFirebaseStorage()
                    .getReference()
                    .child("images")
                    .child("profiles")
                    .child(fileName);
            
            Log.d(TAG, "Uploading to public path: " + imageRef.getPath());
            Log.d(TAG, "Using bucket: " + imageRef.getBucket());
            
            imageRef.putFile(selectedImageUri)
                    .addOnSuccessListener(taskSnapshot -> {
                        Log.d(TAG, "✅ Upload successful to public path");
                        getDownloadUrlAndSave(imageRef);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "❌ All upload strategies failed");
                        Log.e(TAG, "Final error details: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                        handleCompleteUploadFailure(e);
                    });
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to create storage reference for public path", e);
            handleCompleteUploadFailure(e);
        }
    }
    
    void getDownloadUrlAndSave(StorageReference imageRef) {
        imageRef.getDownloadUrl()
                .addOnSuccessListener(uri -> {
                    String imageUrl = uri.toString();
                    Log.d(TAG, "Got download URL: " + imageUrl);
                    saveUrlToFirestore(imageUrl);
                })
                .addOnFailureListener(e -> {
                    setInProgress(false);
                    Log.e(TAG, "Failed to get download URL", e);
                    AndroidUtil.showToast(getContext(), "Failed to get image URL: " + e.getMessage());
                });
    }
    
    void saveUrlToFirestore(String imageUrl) {
        FirebaseUtil.currentUserDetails().update("profilePicUrl", imageUrl)
                .addOnSuccessListener(aVoid -> {
                    setInProgress(false);
                    Log.d(TAG, "✅ Profile picture URL updated in Firestore");
                    AndroidUtil.showToast(getContext(), "Profile picture updated successfully!");
                    
                    // Update the current user model
                    if (currentUserModel != null) {
                        currentUserModel.setProfilePicUrl(imageUrl);
                    }
                    
                    // Refresh the UI
                    loadProfilePicture();
                })
                .addOnFailureListener(e -> {
                    setInProgress(false);
                    Log.e(TAG, "Failed to update profile picture URL in Firestore", e);
                    AndroidUtil.showToast(getContext(), "Failed to update profile: " + e.getMessage());
                });
    }
    
    void testStorageConnectivity() {
        Log.d(TAG, "🔍 Testing Firebase Storage connectivity...");
        
        try {
            FirebaseStorage storage = FirebaseUtil.getFirebaseStorage();
            StorageReference storageRef = storage.getReference();
            
            // Test if we can get a reference to the storage
            Log.d(TAG, "📁 Storage reference: " + storageRef.toString());
            Log.d(TAG, "🏢 Storage bucket: " + storageRef.getBucket());
            
            // Validate bucket URL format
            String bucketName = storageRef.getBucket();
            if (bucketName == null || bucketName.isEmpty()) {
                Log.e(TAG, "❌ Storage bucket is null or empty!");
                AndroidUtil.showToast(getContext(), "Storage bucket not configured");
                return;
            }
            
            if (!bucketName.contains("gkg-talkifyy")) {
                Log.e(TAG, "❌ Storage bucket name mismatch! Expected: gkg-talkifyy, Got: " + bucketName);
            }
            
            // Try to list files in the root to test connectivity
            storageRef.listAll()
                    .addOnSuccessListener(listResult -> {
                        Log.d(TAG, "✅ Storage connectivity test successful");
                        Log.d(TAG, "📂 Found " + listResult.getItems().size() + " items in storage");
                        Log.d(TAG, "📁 Found " + listResult.getPrefixes().size() + " folders in storage");
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "⚠️ Storage list test failed: " + e.getMessage());
                        
                        // Check specific error types
                        if (e.getMessage() != null) {
                            if (e.getMessage().contains("bucket does not exist")) {
                                Log.e(TAG, "❌ Storage bucket doesn't exist. Need to initialize Firebase Storage.");
                                AndroidUtil.showToast(getContext(), "Storage bucket doesn't exist. Contact support.");
                            } else if (e.getMessage().contains("Permission denied")) {
                                Log.w(TAG, "⚠️ Permission denied for listing (normal, rules are working)");
                            }
                        }
                    });
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Storage connectivity test failed", e);
            AndroidUtil.showToast(getContext(), "Storage connectivity test failed: " + e.getMessage());
        }
    }
    
    void emergencyDirectUpload() {
        Log.d(TAG, "🚨 EMERGENCY: Attempting direct upload with aggressive bucket initialization");
        
        try {
            // Strategy 1: Try with explicit bucket URL first
            uploadWithDirectBucketUrl();
            
        } catch (Exception e) {
            Log.e(TAG, "🚨 Emergency direct upload failed", e);
            // Fall back to original strategies
            uploadWithFallbackStrategies();
        }
    }
    
    void uploadWithDirectBucketUrl() {
        Log.d(TAG, "⚡ Trying upload with direct bucket URL initialization");
        
        try {
            // Direct bucket initialization
            String bucketUrl = "gs://gkg-talkifyy.firebasestorage.app";
            Log.d(TAG, "🎯 Using explicit bucket: " + bucketUrl);
            
            FirebaseStorage storage = FirebaseStorage.getInstance(bucketUrl);
            
            // Test the storage immediately
            StorageReference testRef = storage.getReference();
            String actualBucket = testRef.getBucket();
            Log.d(TAG, "✅ Direct bucket initialized: " + actualBucket);
            
            // Simple upload path
            String fileName = "emergency_" + FirebaseUtil.currentUserId() + "_" + System.currentTimeMillis() + ".jpg";
            StorageReference imageRef = storage.getReference().child(fileName);
            
            Log.d(TAG, "📤 Emergency upload to: " + imageRef.getPath());
            Log.d(TAG, "🏢 Emergency bucket: " + imageRef.getBucket());
            
            imageRef.putFile(selectedImageUri)
                    .addOnSuccessListener(taskSnapshot -> {
                        Log.d(TAG, "🎉 EMERGENCY UPLOAD SUCCESS!");
                        getDownloadUrlAndSave(imageRef);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "💥 Emergency upload failed: " + e.getMessage());
                        Log.e(TAG, "💥 Emergency error class: " + e.getClass().getSimpleName());
                        
                        // Capture full stack trace
                        e.printStackTrace();
                        
                        // Try with test mode rules
                        uploadWithTestModeRules();
                    })
                    .addOnProgressListener(taskSnapshot -> {
                        double progress = (100.0 * taskSnapshot.getBytesTransferred()) / taskSnapshot.getTotalByteCount();
                        Log.d(TAG, "⚡ Emergency progress: " + (int) progress + "%");
                    });
                    
        } catch (Exception e) {
            Log.e(TAG, "💥 Direct bucket URL failed", e);
            e.printStackTrace();
            uploadWithTestModeRules();
        }
    }
    
    void uploadWithTestModeRules() {
        Log.d(TAG, "🧪 Trying upload assuming test mode rules...");
        
        try {
            // Most basic possible upload - no subdirectories
            FirebaseStorage storage = FirebaseStorage.getInstance();
            String fileName = System.currentTimeMillis() + ".jpg";
            StorageReference imageRef = storage.getReference().child(fileName);
            
            Log.d(TAG, "🧪 Test upload to root: " + imageRef.getPath());
            
            imageRef.putFile(selectedImageUri)
                    .addOnSuccessListener(taskSnapshot -> {
                        Log.d(TAG, "🎉 TEST MODE UPLOAD SUCCESS!");
                        getDownloadUrlAndSave(imageRef);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "💥 Test mode upload failed: " + e.getMessage());
                        Log.e(TAG, "💥 Test error class: " + e.getClass().getSimpleName());
                        
                        // Final attempt - show exact error to user
                        AndroidUtil.showToast(getContext(), "Upload failed: " + e.getMessage());
                        
                        // Log everything we can
                        logCompleteEnvironmentInfo(e);
                        
                        // Firebase Storage completely failed - try Firestore base64 upload
                        Log.d(TAG, "🔄 All Firebase Storage methods failed - trying Firestore base64 upload");
                        uploadImageToFirestore();
                    });
                    
        } catch (Exception e) {
            Log.e(TAG, "💥 Test mode setup failed", e);
            logCompleteEnvironmentInfo(e);
            uploadWithFallbackStrategies();
        }
    }
    
    void logCompleteEnvironmentInfo(Exception uploadError) {
        Log.e(TAG, "📊 COMPLETE ENVIRONMENT DEBUG INFO:");
        Log.e(TAG, "📱 App package: " + getContext().getPackageName());
        Log.e(TAG, "🔐 User authenticated: " + FirebaseUtil.isLoggedIn());
        Log.e(TAG, "👤 User ID: " + FirebaseUtil.currentUserId());
        Log.e(TAG, "🖼️ Image URI: " + selectedImageUri.toString());
        
        try {
            FirebaseStorage storage = FirebaseStorage.getInstance();
            StorageReference ref = storage.getReference();
            Log.e(TAG, "📦 Storage bucket: " + ref.getBucket());
            Log.e(TAG, "📁 Storage reference: " + ref.toString());
        } catch (Exception e) {
            Log.e(TAG, "💥 Cannot get storage info: " + e.getMessage());
        }
        
        Log.e(TAG, "💥 Final upload error: " + uploadError.getMessage());
        Log.e(TAG, "💥 Error type: " + uploadError.getClass().getSimpleName());
        uploadError.printStackTrace();
    }
    
    void handleCompleteUploadFailure(Exception e) {
        setInProgress(false);
        Log.e(TAG, "Complete upload failure", e);
        
        String errorMessage = "Upload failed";
        if (e.getMessage() != null) {
            if (e.getMessage().contains("Permission denied")) {
                errorMessage = "Permission denied. Storage rules need to be configured.";
            } else if (e.getMessage().contains("Object does not exist")) {
                errorMessage = "Storage bucket not properly configured.";
            } else if (e.getMessage().contains("Network")) {
                errorMessage = "Network error. Please check your internet connection.";
            } else {
                errorMessage = "Upload failed: " + e.getMessage();
            }
        }
        
        AndroidUtil.showToast(getContext(), errorMessage);
        
        // Show detailed error in logs
        Log.e(TAG, "Detailed error info:");
        Log.e(TAG, "Error message: " + e.getMessage());
        Log.e(TAG, "Error class: " + e.getClass().getSimpleName());
        Log.e(TAG, "User ID: " + FirebaseUtil.currentUserId());
        Log.e(TAG, "Selected URI: " + selectedImageUri.toString());
    }
    
    void uploadImageToFirestore() {
        Log.d(TAG, "📋 EMERGENCY FALLBACK: Uploading image as base64 to Firestore");
        
        try {
            // Convert image to bitmap and then to base64
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContext().getContentResolver(), selectedImageUri);
            
            // Resize bitmap to reduce size (max 300x300)
            int maxSize = 300;
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            
            if (width > maxSize || height > maxSize) {
                float ratio = Math.min((float) maxSize / width, (float) maxSize / height);
                width = Math.round(ratio * width);
                height = Math.round(ratio * height);
                bitmap = Bitmap.createScaledBitmap(bitmap, width, height, false);
            }
            
            // Convert to base64
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
            byte[] imageBytes = baos.toByteArray();
            String base64String = Base64.encodeToString(imageBytes, Base64.DEFAULT);
            
            Log.d(TAG, "📋 Base64 image size: " + base64String.length() + " characters");
            
            // Create data URL for the image
            String dataUrl = "data:image/jpeg;base64," + base64String;
            
            // Save to Firestore
            FirebaseUtil.currentUserDetails().update("profilePicUrl", dataUrl)
                    .addOnSuccessListener(aVoid -> {
                        setInProgress(false);
                        Log.d(TAG, "✅ FIRESTORE FALLBACK SUCCESS!");
                        AndroidUtil.showToast(getContext(), "Profile picture updated successfully!");
                        
                        // Update the current user model
                        if (currentUserModel != null) {
                            currentUserModel.setProfilePicUrl(dataUrl);
                        }
                        
                        // Refresh the UI
                        loadProfilePicture();
                    })
                    .addOnFailureListener(e -> {
                        setInProgress(false);
                        Log.e(TAG, "💥 Firestore fallback failed: " + e.getMessage());
                        AndroidUtil.showToast(getContext(), "Complete upload failure: " + e.getMessage());
                    });
                    
        } catch (Exception e) {
            setInProgress(false);
            Log.e(TAG, "💥 Failed to process image for Firestore upload", e);
            AndroidUtil.showToast(getContext(), "Failed to process image: " + e.getMessage());
        }
    }
    
    void loadProfilePicture() {
        // First try to get the URL from Firestore (more reliable)
        if (currentUserModel != null && currentUserModel.getProfilePicUrl() != null && !currentUserModel.getProfilePicUrl().isEmpty()) {
            Log.d(TAG, "Loading profile picture from Firestore URL: " + currentUserModel.getProfilePicUrl());
            try {
                AndroidUtil.setProfilePic(getContext(), currentUserModel.getProfilePicUrl(), profileImageView);
            } catch (Exception e) {
                Log.e(TAG, "Error loading profile picture from Firestore URL", e);
                profileImageView.setImageResource(R.drawable.person_icon);
            }
        } else {
            // Fallback to Firebase Storage
            Log.d(TAG, "No Firestore URL found, trying Firebase Storage");
            FirebaseUtil.getCurrentProfilePicStorageRef().getDownloadUrl()
                    .addOnSuccessListener(uri -> {
                        Log.d(TAG, "Loaded profile picture from Storage: " + uri.toString());
                        AndroidUtil.setProfilePic(getContext(), uri, profileImageView);
                    })
                    .addOnFailureListener(e -> {
                        Log.d(TAG, "No profile picture found in Storage (this is normal for new users), using default");
                        // Use default profile picture - this is normal for users who haven't uploaded a picture yet
                        profileImageView.setImageResource(R.drawable.person_icon);
                    });
        }
    }
}
