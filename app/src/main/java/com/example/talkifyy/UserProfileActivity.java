package com.example.talkifyy;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.talkifyy.model.UserModel;
import com.example.talkifyy.utils.AndroidUtil;
import com.example.talkifyy.utils.FirebaseUtil;

public class UserProfileActivity extends AppCompatActivity {

    private static final String TAG = "UserProfileActivity";

    UserModel userModel;
    
    // UI Components
    ImageButton backBtn;
    ImageView profileImageView;
    TextView usernameTextView;
    TextView phoneTextView;
    TextView joinedDateTextView;
    TextView userIdTextView;
    
    // Action buttons
    LinearLayout callButton;
    LinearLayout messageButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);
        
        // Apply system window insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Get UserModel from intent
        userModel = AndroidUtil.getUserModelFromIntent(getIntent());
        
        Log.d(TAG, "Opening profile for user: " + userModel.getUsername());
        Log.d(TAG, "User ID: " + userModel.getUserId());

        // Initialize UI components
        initializeViews();
        
        // Setup UI
        setupViews();
        
        // Load user data
        loadUserData();
    }

    private void initializeViews() {
        backBtn = findViewById(R.id.back_btn);
        profileImageView = findViewById(R.id.profile_image_view);
        usernameTextView = findViewById(R.id.username_text);
        phoneTextView = findViewById(R.id.phone_text);
        joinedDateTextView = findViewById(R.id.joined_date_text);
        userIdTextView = findViewById(R.id.user_id_text);
        
        // Initialize action buttons
        callButton = findViewById(R.id.call_button);
        messageButton = findViewById(R.id.message_button);
    }

    private void setupViews() {
        // Back button functionality
        backBtn.setOnClickListener(v -> onBackPressed());
        
        // Set basic user information from intent
        if (userModel.getUsername() != null) {
            usernameTextView.setText(userModel.getUsername());
        }
        
        if (userModel.getPhone() != null) {
            phoneTextView.setText(userModel.getPhone());
        }
        
        if (userModel.getUserId() != null) {
            userIdTextView.setText("ID: " + userModel.getUserId().substring(0, Math.min(8, userModel.getUserId().length())) + "...");
        }
        
        // Setup action button listeners
        setupActionButtons();
    }

    private void loadUserData() {
        Log.d(TAG, "Loading fresh user data from Firestore...");
        
        // First, load the profile picture
        loadProfilePicture();
        
        // Then load fresh user data from Firestore
        FirebaseUtil.allUserCollectionReference()
                .document(userModel.getUserId())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        UserModel freshUserData = documentSnapshot.toObject(UserModel.class);
                        if (freshUserData != null) {
                            Log.d(TAG, "Fresh user data loaded successfully");
                            updateUIWithFreshData(freshUserData);
                        } else {
                            Log.w(TAG, "Failed to parse fresh user data");
                        }
                    } else {
                        Log.w(TAG, "User document does not exist");
                        AndroidUtil.showToast(this, "User information not found");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load fresh user data", e);
                    AndroidUtil.showToast(this, "Failed to load user information");
                });
    }

    private void loadProfilePicture() {
        Log.d(TAG, "Loading profile picture...");
        
        // Try intent data first
        String profileUrl = userModel.getProfilePicUrl();
        if (profileUrl != null && !profileUrl.isEmpty() && !"null".equals(profileUrl)) {
            Log.d(TAG, "Loading profile picture from intent");
            AndroidUtil.setProfilePic(this, profileUrl, profileImageView);
        } else {
            // Fallback to Firestore
            Log.d(TAG, "Loading profile picture from Firestore");
            FirebaseUtil.allUserCollectionReference()
                    .document(userModel.getUserId())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            UserModel userData = documentSnapshot.toObject(UserModel.class);
                            if (userData != null && userData.getProfilePicUrl() != null && !userData.getProfilePicUrl().isEmpty()) {
                                AndroidUtil.setProfilePic(this, userData.getProfilePicUrl(), profileImageView);
                            } else {
                                setDefaultProfileImage();
                            }
                        } else {
                            setDefaultProfileImage();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to load profile picture", e);
                        setDefaultProfileImage();
                    });
        }
    }

    private void setDefaultProfileImage() {
        profileImageView.setImageResource(R.drawable.person_icon);
        profileImageView.setImageTintList(android.content.res.ColorStateList.valueOf(
                getResources().getColor(R.color.light_gray, null)));
    }

    private void updateUIWithFreshData(UserModel freshData) {
        // Update username if available
        if (freshData.getUsername() != null && !freshData.getUsername().isEmpty()) {
            usernameTextView.setText(freshData.getUsername());
        }
        
        // Update phone if available
        if (freshData.getPhone() != null && !freshData.getPhone().isEmpty()) {
            phoneTextView.setText(freshData.getPhone());
        }
        
        // Update profile picture if available
        if (freshData.getProfilePicUrl() != null && !freshData.getProfilePicUrl().isEmpty()) {
            AndroidUtil.setProfilePic(this, freshData.getProfilePicUrl(), profileImageView);
        }
        
        // Show joined date if available
        if (freshData.getCreatedTimestamp() != null) {
            String joinedDate = android.text.format.DateFormat.format("MMM dd, yyyy", 
                    freshData.getCreatedTimestamp().toDate()).toString();
            joinedDateTextView.setText("Joined: " + joinedDate);
        }
        
        // Update local user model
        userModel = freshData;
        
        Log.d(TAG, "UI updated with fresh data");
    }
    
    private void setupActionButtons() {
        Log.d(TAG, "Setting up action button listeners");
        
        // Call button functionality
        if (callButton != null) {
            callButton.setOnClickListener(v -> {
                Log.d(TAG, "Call button clicked for: " + userModel.getUsername());
                makePhoneCall();
            });
        }
        
        // Message button functionality
        if (messageButton != null) {
            messageButton.setOnClickListener(v -> {
                Log.d(TAG, "Message button clicked for: " + userModel.getUsername());
                openChatWithUser();
            });
        }
    }
    
    private void makePhoneCall() {
        String phoneNumber = userModel.getPhone();
        if (phoneNumber != null && !phoneNumber.isEmpty()) {
            Log.d(TAG, "Initiating call to: " + phoneNumber);
            
            // Create call intent
            Intent callIntent = new Intent(Intent.ACTION_DIAL);
            callIntent.setData(Uri.parse("tel:" + phoneNumber));
            
            try {
                startActivity(callIntent);
                Log.d(TAG, "Call intent started successfully");
            } catch (Exception e) {
                Log.e(TAG, "Failed to start call intent", e);
                AndroidUtil.showToast(this, "Unable to make call");
            }
        } else {
            Log.w(TAG, "Phone number not available for call");
            AndroidUtil.showToast(this, "Phone number not available");
        }
    }
    
    private void openChatWithUser() {
        Log.d(TAG, "Opening chat with user: " + userModel.getUsername());
        
        try {
            // Create intent to open ChatActivity
            Intent chatIntent = new Intent(UserProfileActivity.this, ChatActivity.class);
            
            // Pass user data to the chat activity
            AndroidUtil.passUserModelAsIntent(chatIntent, userModel);
            
            // Start the chat activity
            startActivity(chatIntent);
            
            // Finish current activity to go back to chat instead of profile
            finish();
            
            Log.d(TAG, "Chat activity started successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to open chat with user", e);
            AndroidUtil.showToast(this, "Unable to open chat");
        }
    }
}
