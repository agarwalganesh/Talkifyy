package com.example.talkifyy;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

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
        
        Log.d(TAG, "Starting upload to Firebase Storage for user: " + FirebaseUtil.currentUserId());
        Log.d(TAG, "Selected image URI: " + selectedImageUri.toString());
        setInProgress(true);
        
        // Try multiple upload strategies
        uploadWithFallbackStrategies();
    }
    
    void uploadWithFallbackStrategies() {
        // Strategy 1: Try new folder structure first
        uploadToNewPath();
    }
    
    void uploadToNewPath() {
        Log.d(TAG, "Attempting upload to new path structure");
        
        // Create a simple filename
        String fileName = FirebaseUtil.currentUserId() + ".jpg";
        StorageReference imageRef = FirebaseStorage.getInstance()
                .getReference()
                .child("profile_pictures")
                .child(fileName);
        
        Log.d(TAG, "Uploading to path: " + imageRef.getPath());
        
        imageRef.putFile(selectedImageUri)
                .addOnSuccessListener(taskSnapshot -> {
                    Log.d(TAG, "✅ Upload successful to new path");
                    getDownloadUrlAndSave(imageRef);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Upload failed to new path: " + e.getMessage());
                    // Try fallback strategy
                    uploadToLegacyPath();
                })
                .addOnProgressListener(taskSnapshot -> {
                    double progress = (100.0 * taskSnapshot.getBytesTransferred()) / taskSnapshot.getTotalByteCount();
                    Log.d(TAG, "Upload progress: " + (int) progress + "%");
                });
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
        
        // Try uploading to a more permissive path
        String fileName = "user_" + FirebaseUtil.currentUserId() + "_" + System.currentTimeMillis() + ".jpg";
        StorageReference imageRef = FirebaseStorage.getInstance()
                .getReference()
                .child("images")
                .child("profiles")
                .child(fileName);
        
        Log.d(TAG, "Uploading to public path: " + imageRef.getPath());
        
        imageRef.putFile(selectedImageUri)
                .addOnSuccessListener(taskSnapshot -> {
                    Log.d(TAG, "✅ Upload successful to public path");
                    getDownloadUrlAndSave(imageRef);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ All upload strategies failed");
                    handleCompleteUploadFailure(e);
                });
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
