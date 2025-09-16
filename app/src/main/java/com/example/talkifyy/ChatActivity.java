package com.example.talkifyy;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.talkifyy.adapter.ChatRecyclerAdapter;
import com.example.talkifyy.adapter.UnsendMessageListener;
import com.example.talkifyy.adapter.MessageDeletionListener;
import com.example.talkifyy.adapter.EditMessageListener;
import com.example.talkifyy.utils.ImageUploadUtil;
import com.example.talkifyy.model.ImageMetadata;
import java.util.ArrayList;
import java.util.List;
import com.example.talkifyy.model.ChatMessageModel;
import com.example.talkifyy.model.ChatroomModel;
import com.example.talkifyy.model.UserModel;
import com.example.talkifyy.utils.AndroidUtil;
import com.example.talkifyy.utils.FirebaseUtil;
import com.example.talkifyy.utils.NotificationUtil;
import com.example.talkifyy.utils.LocalDeletionUtil;
import com.example.talkifyy.utils.UnsendConfig;
import com.example.talkifyy.utils.WhatsAppStyleDeletionUtil;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.Query;
import com.google.firebase.storage.StorageReference;

import java.util.Arrays;
import java.util.List;

public class ChatActivity extends AppCompatActivity implements UnsendMessageListener, MessageDeletionListener, EditMessageListener {

    private static final String TAG = "ChatActivity";

    UserModel otherUser;
    String chatroomId;
     ChatroomModel chatroomModel;
     ChatRecyclerAdapter adapter;

    EditText messageInput;
    ImageButton sendMessageBtn;
    ImageButton backBtn;
    ImageButton attachmentBtn;
    TextView otherUsername;
    RecyclerView recyclerView;
    ImageView imageView;
    
    // Image functionality
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private Uri cameraImageUri;
    private static final int MAX_IMAGES = 5; // Maximum images to select
    
    // Multi-select UI elements
    RelativeLayout selectionActionBar;
    TextView selectionCountText;
    TextView selectAllBtn;
    TextView deleteSelectedBtn;
    TextView cancelSelectionBtn;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        
        // Apply system window insets only to specific views to prevent toolbar issues
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.toolbar), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.bottom_layout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            return insets;
        });


        //get UserModel or Group info
        otherUser = AndroidUtil.getUserModelFromIntent(getIntent());
        
        // Check if this is a group chat from intent
        boolean isGroupFromIntent = getIntent().getBooleanExtra("isGroup", false);
        String chatroomIdFromIntent = getIntent().getStringExtra("chatroomId");
        
        if (isGroupFromIntent && chatroomIdFromIntent != null) {
            // This is a group chat
            chatroomId = chatroomIdFromIntent;
            // Create a dummy UserModel for group chats to prevent null pointer issues
            if (otherUser == null) {
                otherUser = new UserModel();
                otherUser.setUserId("group_" + chatroomId);
                otherUser.setUsername(getIntent().getStringExtra("groupName") != null ? 
                    getIntent().getStringExtra("groupName") : "Group Chat");
            }
        } else {
            // This is a 1-on-1 chat
            if (otherUser != null && otherUser.getUserId() != null && !otherUser.getUserId().isEmpty()) {
                chatroomId = FirebaseUtil.getChatroomId(FirebaseUtil.currentUserId(), otherUser.getUserId());
            } else {
                Log.e(TAG, "❌ otherUser is null or has no userId for individual chat");
                
                // Try to recover with a more lenient approach
                if (otherUser != null) {
                    Log.w(TAG, "⚠️ Attempting to recover with available user data");
                    
                    // If we have some user data but no userId, generate one or use a fallback
                    if (otherUser.getUserId() == null || otherUser.getUserId().isEmpty()) {
                        // Try to extract from intent extras directly
                        String userIdFromIntent = getIntent().getStringExtra("userId");
                        if (userIdFromIntent != null && !userIdFromIntent.isEmpty()) {
                            otherUser.setUserId(userIdFromIntent);
                            chatroomId = FirebaseUtil.getChatroomId(FirebaseUtil.currentUserId(), userIdFromIntent);
                            Log.d(TAG, "✅ Recovered using userId from intent: " + userIdFromIntent);
                        } else {
                            // Show error and go back
                            AndroidUtil.showToast(this, "Unable to load chat - missing user ID");
                            finish();
                            return;
                        }
                    } else {
                        chatroomId = FirebaseUtil.getChatroomId(FirebaseUtil.currentUserId(), otherUser.getUserId());
                    }
                } else {
                    // Complete failure - go back to main activity
                    AndroidUtil.showToast(this, "Unable to load chat - user data missing");
                    Intent mainIntent = new Intent(this, MainActivity.class);
                    mainIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(mainIntent);
                    finish();
                    return;
                }
            }
        }
        
        Log.d(TAG, "ChatActivity started");
        Log.d(TAG, "Current user ID: " + FirebaseUtil.currentUserId());
        Log.d(TAG, "Is group from intent: " + isGroupFromIntent);
        if (otherUser != null) {
            Log.d(TAG, "Other user ID: " + otherUser.getUserId());
        }
        Log.d(TAG, "Chat room ID: " + chatroomId);
        
        // Clear notification count for this chat when user opens it
        if (isGroupFromIntent) {
            // For group chats, clear notifications using chatroom ID
            NotificationUtil.clearMessageCount(chatroomId);
            Log.d(TAG, "🔔 Cleared group notification count for opened chat");
        } else if (otherUser != null && otherUser.getUserId() != null) {
            NotificationUtil.clearMessageCount(otherUser.getUserId());
            Log.d(TAG, "🔔 Cleared individual notification count for opened chat");
        }

        messageInput = findViewById(R.id.chat_message_input);
        sendMessageBtn = findViewById(R.id.message_send_btn);
        attachmentBtn = findViewById(R.id.attachment_btn);
        backBtn = findViewById(R.id.back_btn);
        otherUsername = findViewById(R.id.other_username);
        recyclerView = findViewById(R.id.chat_recycler_view);
        imageView = findViewById(R.id.profile_pic_image_view);




        backBtn.setOnClickListener((v)->{
            onBackPressed();
        });
        
        // Set header text - will be updated once we load chatroom info
        if (isGroupFromIntent) {
            String groupName = getIntent().getStringExtra("groupName");
            otherUsername.setText(groupName != null ? groupName : "Group Chat");
        } else if (otherUser != null) {
            otherUsername.setText(otherUser.getUsername());
        } else {
            otherUsername.setText("Chat");
        }
        
        // Load profile picture
        loadProfilePicture();
        
        // Setup header click listener
        setupHeaderClickListener();
        

        sendMessageBtn.setOnClickListener((v -> {
            sendMessage();
        }));
        
        attachmentBtn.setOnClickListener((v -> {
            Log.d(TAG, "Attachment button clicked");
            showImagePickerDialog();
        }));
        
        // Handle Enter key press and IME action
        messageInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND || 
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                sendMessage();
                return true;
            }
            return false;
        });

        // Initialize multi-select UI elements
        setupSelectionActionBar();
        
        // Initialize image picker functionality
        setupImagePickers();
        
        // Wrap all setup operations in try-catch to prevent crashes
        try {
            getOrCreateChatroomModel();
        } catch (Exception e) {
            Log.e(TAG, "❌ Error creating chatroom model", e);
            AndroidUtil.showToast(this, "Error loading chat data");
        }
        
        try {
            setupChatRecyclerView();
        } catch (Exception e) {
            Log.e(TAG, "❌ Error setting up chat RecyclerView", e);
            AndroidUtil.showToast(this, "Error setting up chat display");
        }
        
        try {
            setupKeyboardListener();
        } catch (Exception e) {
            Log.e(TAG, "❌ Error setting up keyboard listener", e);
        }
        
        // Test Firestore connection with error handling
        try {
            testFirestoreConnection();
        } catch (Exception e) {
            Log.e(TAG, "❌ Error testing Firestore connection", e);
        }
    }

    void loadProfilePicture() {
        Log.d(TAG, "🖼️ Starting profile image load");
        
        // Clear any existing tint on the ImageView that might interfere
        imageView.setImageTintList(null);
        imageView.clearColorFilter();
        
        // Check if this will be a group chat (we'll know for sure after loading chatroom model)
        boolean isGroupFromIntent = getIntent().getBooleanExtra("isGroup", false);
        
        if (isGroupFromIntent) {
            // This is a group chat - use default group icon for now
            Log.d(TAG, "📱 Loading group profile picture placeholder");
            setDefaultGroupImage();
            return;
        }
        
        // This is a 1-on-1 chat - load user's profile picture
        if (otherUser == null) {
            Log.e(TAG, "❌ otherUser is null for individual chat, using default image");
            setDefaultProfileImage();
            return;
        }
        Log.d(TAG, "🔍 User ID: " + otherUser.getUserId());
        
        // First try to get the URL from the intent data (most efficient)
        String intentProfileUrl = otherUser.getProfilePicUrl();
        Log.d(TAG, "📱 Profile URL from intent: " + (intentProfileUrl != null ? intentProfileUrl : "null"));
        
        if (intentProfileUrl != null && !intentProfileUrl.isEmpty() && !"null".equals(intentProfileUrl)) {
            Log.d(TAG, "✅ Loading profile picture from intent URL");
            AndroidUtil.setProfilePic(ChatActivity.this, intentProfileUrl, imageView);
        } else {
            // Fallback: get fresh data from Firestore (always up-to-date)
            Log.d(TAG, "🔄 No valid URL in intent, fetching fresh data from Firestore...");
            
            FirebaseUtil.allUserCollectionReference().document(otherUser.getUserId())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        Log.d(TAG, "📄 Firestore document exists: " + documentSnapshot.exists());
                        
                        if (documentSnapshot.exists()) {
                            UserModel otherUserData = documentSnapshot.toObject(UserModel.class);
                            if (otherUserData != null) {
                                String firestoreUrl = otherUserData.getProfilePicUrl();
                                Log.d(TAG, "🔥 Profile URL from Firestore: " + (firestoreUrl != null ? firestoreUrl : "null"));
                                
                                if (firestoreUrl != null && !firestoreUrl.isEmpty() && !"null".equals(firestoreUrl)) {
                                    Log.d(TAG, "✅ Loading profile picture from Firestore URL");
                                    AndroidUtil.setProfilePic(ChatActivity.this, firestoreUrl, imageView);
                                    // Update the local user model with the latest profile pic URL
                                    otherUser.setProfilePicUrl(firestoreUrl);
                                } else {
                                    Log.d(TAG, "⚠️ No valid profile picture URL in Firestore, using default");
                                    setDefaultProfileImage();
                                }
                            } else {
                                Log.w(TAG, "⚠️ Failed to parse user data from Firestore, using default");
                                setDefaultProfileImage();
                            }
                        } else {
                            Log.w(TAG, "⚠️ User document does not exist in Firestore, using default");
                            setDefaultProfileImage();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "❌ Failed to get user data from Firestore", e);
                        setDefaultProfileImage();
                    });
        }
    }
    
    private void setDefaultProfileImage() {
        Log.d(TAG, "🎭 Setting default profile image");
        imageView.setImageResource(R.drawable.person_icon);
        // Apply a light gray tint only to the default icon
        imageView.setImageTintList(android.content.res.ColorStateList.valueOf(
            getResources().getColor(R.color.light_gray, null)));
    }
    
    private void setDefaultGroupImage() {
        Log.d(TAG, "👥 Setting default group image");
        imageView.setImageResource(R.drawable.person_icon); // You can use a different group icon
        // Apply a different tint for groups
        imageView.setImageTintList(android.content.res.ColorStateList.valueOf(
            getResources().getColor(R.color.my_primary, null)));
    }
    
    private void setupHeaderClickListener() {
        Log.d(TAG, "📱 Setting up header click listener");
        
        // Make the profile picture clickable
        imageView.setOnClickListener(v -> openChatProfile());
        
        // Make the username clickable
        otherUsername.setOnClickListener(v -> openChatProfile());
        
        // Make the entire profile section clickable (optional)
        findViewById(R.id.profile_pic_layout).setOnClickListener(v -> openChatProfile());
    }
    
    private void updateHeaderForChat() {
        if (chatroomModel != null && chatroomModel.isGroup()) {
            // Update header for group chat
            otherUsername.setText(chatroomModel.getGroupName());
            Log.d(TAG, "Updated header for group: " + chatroomModel.getGroupName());
        } else {
            // Keep the original user name for 1-on-1 chats
            otherUsername.setText(otherUser.getUsername());
            Log.d(TAG, "Header remains for user: " + otherUser.getUsername());
        }
    }
    
    private void openChatProfile() {
        if (isGroupChat()) {
            Log.d(TAG, "👥 Opening group profile for: " + (chatroomModel != null ? chatroomModel.getGroupName() : "Group"));
            
            Intent intent = new Intent(ChatActivity.this, GroupInfoActivity.class);
            intent.putExtra("chatroomId", chatroomId);
            startActivity(intent);
        } else {
            Log.d(TAG, "👤 Opening user profile for: " + otherUser.getUsername());
            
            Intent intent = new Intent(ChatActivity.this, UserProfileActivity.class);
            AndroidUtil.passUserModelAsIntent(intent, otherUser);
            startActivity(intent);
        }
    }
    
    void sendMessage() {
        String message = messageInput.getText().toString().trim();
        if(message.isEmpty()) {
            return;
        }
        if(message.length() > 1000) {
            messageInput.setError("Message is too long (max 1000 characters)");
            return;
        }
        // Clear any previous errors
        messageInput.setError(null);
        sendMessageToUser(message);
    }
    
    void sendMessageToUser(String message){
        Log.d(TAG, "Attempting to send message: " + message);
        
        if(chatroomModel == null) {
            Log.e(TAG, "Chatroom model is null, cannot send message");
            AndroidUtil.showToast(getApplicationContext(), "Chat not ready. Please wait a moment.");
            return;
        }

        chatroomModel.setLastMessageTimestamp(Timestamp.now());
        chatroomModel.setLastMessageSenderId(FirebaseUtil.currentUserId());
        chatroomModel.setLastMessage(message);
        FirebaseUtil.getChatroomReference(chatroomId).set(chatroomModel);

        ChatMessageModel chatMessageModel = new ChatMessageModel(message,FirebaseUtil.currentUserId(),Timestamp.now());
        Log.d(TAG, "Sending message to Firestore: " + message);
        
        FirebaseUtil.getChatroomMessageReference(chatroomId).add(chatMessageModel)
                .addOnCompleteListener(new OnCompleteListener<DocumentReference>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentReference> task) {
                        if(task.isSuccessful()){
                            Log.d(TAG, "Message sent successfully: " + message);
                            messageInput.setText("");
                            sendNotification(message);
                        } else {
                            Log.e(TAG, "Failed to send message", task.getException());
                            AndroidUtil.showToast(getApplicationContext(), "Failed to send message. Please try again.");
                        }
                    }
                });
    }
    
    void sendNotification(String message) {
        Log.d(TAG, "Sending notification for message: " + message);
        
        // Get current user's name first
        FirebaseUtil.currentUserDetails().get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                UserModel currentUser = documentSnapshot.toObject(UserModel.class);
                String senderName = (currentUser != null && currentUser.getUsername() != null) ? 
                    currentUser.getUsername() : "Someone";
                
                if (isGroupChat()) {
                    // For group chats, send notification to all members except the sender
                    sendGroupNotification(senderName, message, chatroomId);
                } else {
                    // For 1-on-1 chats, send notification to the other user
                    NotificationUtil.sendNotificationToUser(
                        otherUser.getUserId(),
                        senderName,
                        message,
                        chatroomId
                    );
                    
                    Log.d(TAG, "Notification sent from " + senderName + " to " + otherUser.getUsername());
                }
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to get current user details for notification", e);
            // Send notification anyway with fallback name
            if (isGroupChat()) {
                sendGroupNotification("Someone", message, chatroomId);
            } else {
                NotificationUtil.sendNotificationToUser(
                    otherUser.getUserId(),
                    "Someone",
                    message,
                    chatroomId
                );
            }
        });
    }
    
    void sendGroupNotification(String senderName, String message, String chatroomId) {
        Log.d(TAG, "📢 Sending group notification from: " + senderName);
        Log.d(TAG, "Group chatroom ID: " + chatroomId);
        Log.d(TAG, "Is group chat: " + isGroupChat());
        
        if (chatroomModel != null && chatroomModel.getUserIds() != null) {
            String currentUserId = FirebaseUtil.currentUserId();
            String groupTitle = chatroomModel.getGroupName() != null ? chatroomModel.getGroupName() : "Group Chat";
            
            Log.d(TAG, "👥 Group members count: " + chatroomModel.getUserIds().size());
            Log.d(TAG, "🏢 Group title: " + groupTitle);
            Log.d(TAG, "📱 Current user ID: " + currentUserId);
            
            // Send notification to all group members except the sender
            int notificationsSent = 0;
            for (String userId : chatroomModel.getUserIds()) {
                if (!userId.equals(currentUserId)) {
                    Log.d(TAG, "📤 Sending notification to user: " + userId);
                    
                    // Send group notification with proper format
                    // The notification will show: "Group Name" as title and "Sender: Message" as content
                    NotificationUtil.sendGroupNotificationToUser(
                        userId,
                        groupTitle,      // Group name as the notification title
                        senderName,      // Individual sender name
                        message,         // The actual message
                        chatroomId       // Group chat room ID
                    );
                    
                    notificationsSent++;
                    Log.d(TAG, "✅ Group notification sent to: " + userId + " for group: " + groupTitle);
                } else {
                    Log.d(TAG, "⏭️ Skipping notification to sender: " + userId);
                }
            }
            
            Log.d(TAG, "🎉 Total group notifications sent: " + notificationsSent);
        } else {
            Log.e(TAG, "❌ Cannot send group notification - chatroom model or user IDs are null");
            Log.e(TAG, "Chatroom model null: " + (chatroomModel == null));
            if (chatroomModel != null) {
                Log.e(TAG, "User IDs null: " + (chatroomModel.getUserIds() == null));
            }
        }
    }

void getOrCreateChatroomModel(){
    Log.d(TAG, "Getting or creating chatroom model for: " + chatroomId);
    
    FirebaseUtil.getChatroomReference(chatroomId).get().addOnCompleteListener(task -> {
        if(task.isSuccessful()){
            chatroomModel = task.getResult().toObject(ChatroomModel.class);
            if(chatroomModel==null){
                Log.d(TAG, "Creating new chatroom model");
                //first time chat
                chatroomModel = new ChatroomModel(
                        chatroomId,
                        Arrays.asList(FirebaseUtil.currentUserId(),otherUser.getUserId()),
                        Timestamp.now(),
                        ""
                );
                FirebaseUtil.getChatroomReference(chatroomId).set(chatroomModel).addOnCompleteListener(setTask -> {
                    if(setTask.isSuccessful()) {
                        Log.d(TAG, "New chatroom created successfully");
                        
                        // Update adapter's group chat status for new chatroom
                        if (adapter != null) {
                            boolean isGroup = isGroupChat();
                            Log.d(TAG, "🆕 Updating adapter group chat status for new chatroom: " + isGroup);
                            adapter.setGroupChat(isGroup);
                        }
                    } else {
                        Log.e(TAG, "Failed to create new chatroom", setTask.getException());
                    }
                });
            } else {
                Log.d(TAG, "Existing chatroom model loaded");
                updateHeaderForChat();
                
                // Update adapter's group chat status now that we have the chatroom model
                if (adapter != null) {
                    boolean isGroup = isGroupChat();
                    Log.d(TAG, "🔄 Updating adapter group chat status after model loaded: " + isGroup);
                    adapter.setGroupChat(isGroup);
                }
            }
        } else {
            Log.e(TAG, "Failed to get chatroom model", task.getException());
            AndroidUtil.showToast(getApplicationContext(), "Failed to load chat. Please try again.");
        }
    });
}


    void setupChatRecyclerView(){
        try {
            Log.d(TAG, "🔄 Setting up MEMORY-EFFICIENT chat RecyclerView for chatroom: " + chatroomId);
            
            // CRITICAL: Add memory management and pagination to prevent crashes
            Query query = FirebaseUtil.getChatroomMessageReference(chatroomId)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(50); // LIMIT TO 50 MESSAGES TO PREVENT MEMORY CRASHES

            FirestoreRecyclerOptions<ChatMessageModel> options = new FirestoreRecyclerOptions.Builder<ChatMessageModel>()
                    .setQuery(query, ChatMessageModel.class)
                    .setLifecycleOwner(this)  // AUTO CLEANUP TO PREVENT MEMORY LEAKS
                    .build();

            // Create adapter with comprehensive error handling
            try {
                adapter = new ChatRecyclerAdapter(options, this, chatroomId, this);
                
                // Configure adapter safely
                safelyConfigureAdapter();
                
            } catch (Exception e) {
                Log.e(TAG, "❌ CRITICAL: Failed to create ChatRecyclerAdapter", e);
                AndroidUtil.showToast(this, "Error setting up chat - trying recovery");
                
                // FALLBACK: Try with minimal configuration
                createFallbackAdapter();
                return;
            }
            
            // Setup RecyclerView with memory optimization
            try {
                setupRecyclerViewWithMemoryOptimization();
            } catch (Exception e) {
                Log.e(TAG, "❌ Error setting up RecyclerView", e);
                AndroidUtil.showToast(this, "Error displaying messages");
                return;
            }
            
            // Setup data observer with crash protection
            try {
                setupDataObserverWithCrashProtection();
            } catch (Exception e) {
                Log.e(TAG, "❌ Error setting up data observer", e);
            }
            
            // Start listening with error handling
            try {
                Log.d(TAG, "✅ Starting adapter listening with crash protection");
                adapter.startListening();
            } catch (Exception e) {
                Log.e(TAG, "❌ CRITICAL: Failed to start adapter listening", e);
                AndroidUtil.showToast(this, "Error connecting to chat - please restart the app");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "❌ CATASTROPHIC: Complete failure in setupChatRecyclerView", e);
            AndroidUtil.showToast(this, "Critical error - please restart the app");
            
            // Emergency fallback - go back to main activity
            try {
                Intent mainIntent = new Intent(this, MainActivity.class);
                mainIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(mainIntent);
                finish();
            } catch (Exception ex) {
                Log.e(TAG, "❌ Even emergency fallback failed", ex);
            }
        }
    }
    
    /**
     * Safely configure adapter with all settings
     */
    private void safelyConfigureAdapter() {
        try {
            // Set unsend time window from configuration
            long unsendTimeWindow = UnsendConfig.getUnsendTimeWindowMinutes(this);
            adapter.setUnsendTimeWindow(unsendTimeWindow);
            Log.d(TAG, "✅ Unsend time window set: " + unsendTimeWindow + " minutes");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error setting unsend time window", e);
        }
        
        try {
            // Configure WhatsApp-style deletion
            adapter.setMessageDeletionListener(this);
            Log.d(TAG, "✅ Message deletion listener set");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error setting message deletion listener", e);
        }
        
        try {
            // Configure message editing
            adapter.setEditMessageListener(this);
            Log.d(TAG, "✅ Edit message listener set");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error setting edit message listener", e);
        }
        
        try {
            boolean isGroup = isGroupChat();
            Log.d(TAG, "📱 Setting adapter group chat status: " + isGroup);
            adapter.setGroupChat(isGroup);
        } catch (Exception e) {
            Log.e(TAG, "❌ Error setting group chat status", e);
        }
    }
    
    /**
     * Create fallback adapter with minimal configuration if main adapter fails
     */
    private void createFallbackAdapter() {
        try {
            Log.w(TAG, "⚠️ Creating FALLBACK adapter with minimal configuration");
            
            // Even more limited query for fallback
            Query fallbackQuery = FirebaseUtil.getChatroomMessageReference(chatroomId)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(20); // EVEN MORE LIMITED

            FirestoreRecyclerOptions<ChatMessageModel> fallbackOptions = new FirestoreRecyclerOptions.Builder<ChatMessageModel>()
                    .setQuery(fallbackQuery, ChatMessageModel.class)
                    .setLifecycleOwner(this)
                    .build();

            adapter = new ChatRecyclerAdapter(fallbackOptions, this, chatroomId, this);
            
            // Minimal configuration
            adapter.setGroupChat(false); // Default to individual chat
            
            Log.d(TAG, "✅ Fallback adapter created successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ CRITICAL: Even fallback adapter creation failed", e);
            AndroidUtil.showToast(this, "Unable to load chat - please restart the app");
        }
    }
    
    /**
     * Setup RecyclerView with memory optimization
     */
    private void setupRecyclerViewWithMemoryOptimization() {
        LinearLayoutManager manager = new LinearLayoutManager(this);
        manager.setReverseLayout(true);
        
        // MEMORY OPTIMIZATION: Set item prefetch count to reduce memory usage
        manager.setInitialPrefetchItemCount(10);
        
        recyclerView.setLayoutManager(manager);
        recyclerView.setAdapter(adapter);
        
        // MEMORY OPTIMIZATION: Set recycler pool sizes
        recyclerView.getRecycledViewPool().setMaxRecycledViews(0, 20);
        
        // MEMORY OPTIMIZATION: Set item animator to null to reduce animations and memory usage
        recyclerView.setItemAnimator(null);
        
        Log.d(TAG, "✅ RecyclerView configured with memory optimization");
    }
    
    /**
     * Setup data observer with comprehensive crash protection
     */
    private void setupDataObserverWithCrashProtection() {
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                try {
                    super.onItemRangeInserted(positionStart, itemCount);
                    Log.d(TAG, "📨 New messages inserted, count: " + itemCount);
                    
                    // CRASH PROTECTION: Check adapter and selection mode safely
                    if (adapter != null && recyclerView != null && !adapter.isSelectionMode() && itemCount > 0) {
                        // MEMORY OPTIMIZATION: Smooth scroll only if not too many items
                        if (itemCount <= 5) {
                            recyclerView.post(() -> {
                                try {
                                    recyclerView.smoothScrollToPosition(0);
                                } catch (Exception e) {
                                    Log.e(TAG, "❌ Error scrolling to position", e);
                                }
                            });
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "❌ Error in onItemRangeInserted", e);
                }
            }
            
            @Override
            public void onItemRangeChanged(int positionStart, int itemCount) {
                try {
                    super.onItemRangeChanged(positionStart, itemCount);
                    Log.d(TAG, "🔄 Messages changed, count: " + itemCount);
                } catch (Exception e) {
                    Log.e(TAG, "❌ Error in onItemRangeChanged", e);
                }
            }
            
            @Override
            public void onItemRangeRemoved(int positionStart, int itemCount) {
                try {
                    super.onItemRangeRemoved(positionStart, itemCount);
                    Log.d(TAG, "🗑️ Messages removed, count: " + itemCount);
                } catch (Exception e) {
                    Log.e(TAG, "❌ Error in onItemRangeRemoved", e);
                }
            }
            
            @Override
            public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
                try {
                    super.onItemRangeMoved(fromPosition, toPosition, itemCount);
                    Log.d(TAG, "📍 Messages moved from " + fromPosition + " to " + toPosition);
                } catch (Exception e) {
                    Log.e(TAG, "❌ Error in onItemRangeMoved", e);
                }
            }
        });
        
        Log.d(TAG, "✅ Data observer configured with crash protection");
    }
    
    void setupKeyboardListener() {
        // Listen for keyboard changes and scroll to bottom when keyboard opens
        findViewById(R.id.main).getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                // This will be called when keyboard state changes
                // Only auto-scroll if not in selection mode to prevent unwanted scrolling
                if (adapter != null && adapter.getItemCount() > 0 && !adapter.isSelectionMode()) {
                    // Small delay to ensure layout is complete
                    recyclerView.postDelayed(() -> {
                        recyclerView.smoothScrollToPosition(0);
                    }, 100);
                }
            }
        });
        
        // Also scroll when EditText gets focus
        messageInput.setOnFocusChangeListener((v, hasFocus) -> {
            // Only auto-scroll when EditText gets focus if not in selection mode
            if (hasFocus && adapter != null && adapter.getItemCount() > 0 && !adapter.isSelectionMode()) {
                recyclerView.postDelayed(() -> {
                    recyclerView.smoothScrollToPosition(0);
                }, 200);
            }
        });
    }
    
    void testFirestoreConnection() {
        // Test if we can read from the chatroom messages collection
        Log.d(TAG, "Testing Firestore connection...");
        
        FirebaseUtil.getChatroomMessageReference(chatroomId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Firestore read successful. Message count: " + task.getResult().size());
                        if (task.getResult().size() > 0) {
                            task.getResult().forEach(document -> {
                                ChatMessageModel msg = document.toObject(ChatMessageModel.class);
                                Log.d(TAG, "Found message: " + msg.getMessage() + " from " + msg.getSenderId());
                            });
                        }
                    } else {
                        Log.e(TAG, "Firestore read failed", task.getException());
                    }
                });
    }
    
    // Implementation of UnsendMessageListener interface
    @Override
    public void onUnsendMessage(String messageId, String chatroomId) {
        Log.d(TAG, "Deleting message: " + messageId + " from chatroom: " + chatroomId);
        
        long unsendTimeWindow = UnsendConfig.getUnsendTimeWindowMinutes(this);
        
        // Check if the message can be deleted (anyone can delete any message now)
        FirebaseUtil.canUnsendMessage(chatroomId, messageId, unsendTimeWindow,
                canUnsend -> {
                    if (canUnsend) {
                        // Proceed with deleting the message
                        performUnsendMessage(messageId, chatroomId);
                    } else {
                        AndroidUtil.showToast(getApplicationContext(), 
                            "Message not found or already deleted.");
                    }
                },
                error -> {
                    Log.e(TAG, "Error checking message existence", error);
                    AndroidUtil.showToast(getApplicationContext(), 
                        "Failed to delete message: " + error.getMessage());
                }
        );
    }
    
    private void performUnsendMessage(String messageId, String chatroomId) {
        Log.d(TAG, "Performing delete for message: " + messageId);
        
        // Show loading message
        AndroidUtil.showToast(getApplicationContext(), "Deleting message...");
        
        FirebaseUtil.unsendMessage(chatroomId, messageId,
                aVoid -> {
                    Log.d(TAG, "Message deleted successfully: " + messageId);
                    AndroidUtil.showToast(getApplicationContext(), "Message deleted");
                    
                    // Update chatroom last message if this was the last message
                    FirebaseUtil.updateChatroomLastMessageIfNeeded(chatroomId, messageId);
                    
                    // The UI will be updated automatically due to Firestore real-time listeners
                },
                error -> {
                    Log.e(TAG, "Failed to delete message: " + messageId, error);
                    String errorMessage = "Failed to delete message: " + error.getMessage();
                    AndroidUtil.showToast(getApplicationContext(), errorMessage);
                }
        );
    }
    
    // Multi-select functionality methods
    
    private void setupSelectionActionBar() {
        selectionActionBar = findViewById(R.id.selection_action_bar);
        selectionCountText = findViewById(R.id.selection_count_text);
        selectAllBtn = findViewById(R.id.select_all_btn);
        deleteSelectedBtn = findViewById(R.id.delete_selected_btn);
        cancelSelectionBtn = findViewById(R.id.cancel_selection_btn);
        
        selectAllBtn.setOnClickListener(v -> {
            if (adapter != null) {
                adapter.selectAll();
            }
        });
        
        deleteSelectedBtn.setOnClickListener(v -> {
            if (adapter != null) {
                showDeleteSelectedMessagesDialog();
            }
        });
        
        cancelSelectionBtn.setOnClickListener(v -> {
            if (adapter != null) {
                adapter.exitSelectionMode();
            }
        });
    }
    
    @Override
    public void onUnsendMultipleMessages(List<String> messageIds, String chatroomId) {
        Log.d(TAG, "Deleting multiple messages: " + messageIds.size() + " messages from chatroom: " + chatroomId);
        
        AndroidUtil.showToast(getApplicationContext(), "Deleting " + messageIds.size() + " messages...");
        
        int totalMessages = messageIds.size();
        final int[] deletedCount = {0};
        final int[] failedCount = {0};
        
        for (String messageId : messageIds) {
            FirebaseUtil.unsendMessage(chatroomId, messageId,
                    aVoid -> {
                        deletedCount[0]++;
                        Log.d(TAG, "Message deleted successfully: " + messageId + " (" + deletedCount[0] + "/" + totalMessages + ")");
                        
                        // Check if all messages have been processed
                        if (deletedCount[0] + failedCount[0] == totalMessages) {
                            String message = deletedCount[0] + " messages deleted";
                            if (failedCount[0] > 0) {
                                message += ", " + failedCount[0] + " failed";
                            }
                            AndroidUtil.showToast(getApplicationContext(), message);
                            
                            // Update chatroom last message if needed
                            FirebaseUtil.updateChatroomLastMessageIfNeeded(chatroomId, messageIds.get(messageIds.size() - 1));
                        }
                    },
                    error -> {
                        failedCount[0]++;
                        Log.e(TAG, "Failed to delete message: " + messageId, error);
                        
                        // Check if all messages have been processed
                        if (deletedCount[0] + failedCount[0] == totalMessages) {
                            String message = deletedCount[0] + " messages deleted";
                            if (failedCount[0] > 0) {
                                message += ", " + failedCount[0] + " failed";
                            }
                            AndroidUtil.showToast(getApplicationContext(), message);
                        }
                    }
            );
        }
    }
    
    @Override
    public void onSelectionModeChanged(boolean isSelectionMode, int selectedCount) {
        Log.d(TAG, "Selection mode changed: " + isSelectionMode + ", selected count: " + selectedCount);
        
        if (isSelectionMode) {
            if (selectionActionBar.getVisibility() != View.VISIBLE) {
                // Animate selection action bar in
                animateSelectionBarIn();
            }
            
            // Update count text with animation
            String countText = selectedCount + " selected";
            if (!selectionCountText.getText().toString().equals(countText)) {
                animateCountTextChange(countText);
            }
            
            // Enable/disable buttons based on selection with animation
            animateButtonState(deleteSelectedBtn, selectedCount > 0);
            
        } else {
            if (selectionActionBar.getVisibility() == View.VISIBLE) {
                // Animate selection action bar out
                animateSelectionBarOut();
            }
        }
    }
    
    private void animateSelectionBarIn() {
        selectionActionBar.setVisibility(View.VISIBLE);
        selectionActionBar.setTranslationY(-selectionActionBar.getHeight());
        selectionActionBar.setAlpha(0f);
        
        selectionActionBar.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(300)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withStartAction(() -> {
                    // Update RecyclerView layout to account for selection bar
                    RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) recyclerView.getLayoutParams();
                    params.addRule(RelativeLayout.BELOW, R.id.selection_action_bar);
                    recyclerView.setLayoutParams(params);
                })
                .start();
    }
    
    private void animateSelectionBarOut() {
        selectionActionBar.animate()
                .translationY(-selectionActionBar.getHeight())
                .alpha(0f)
                .setDuration(250)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    selectionActionBar.setVisibility(View.GONE);
                    
                    // Restore RecyclerView layout
                    RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) recyclerView.getLayoutParams();
                    params.removeRule(RelativeLayout.BELOW);
                    params.addRule(RelativeLayout.BELOW, R.id.toolbar);
                    recyclerView.setLayoutParams(params);
                })
                .start();
    }
    
    private void animateCountTextChange(String newText) {
        // Fade out, change text, fade in
        selectionCountText.animate()
                .alpha(0f)
                .setDuration(100)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    selectionCountText.setText(newText);
                    selectionCountText.animate()
                            .alpha(1f)
                            .setDuration(100)
                            .setInterpolator(new AccelerateDecelerateInterpolator())
                            .start();
                })
                .start();
    }
    
    private void animateButtonState(TextView button, boolean enabled) {
        if (button.isEnabled() != enabled) {
            button.setEnabled(enabled);
            
            // Scale animation to indicate state change
            button.animate()
                    .scaleX(enabled ? 1.1f : 0.9f)
                    .scaleY(enabled ? 1.1f : 0.9f)
                    .alpha(enabled ? 1.0f : 0.6f)
                    .setDuration(150)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .withEndAction(() -> {
                        button.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(100)
                                .setInterpolator(new AccelerateDecelerateInterpolator())
                                .start();
                    })
                    .start();
        }
    }
    
    /**
     * Show dialog for deleting selected messages with clean options
     */
    private void showDeleteSelectedMessagesDialog() {
        if (adapter == null || adapter.getSelectedCount() == 0) {
            return;
        }
        
        int selectedCount = adapter.getSelectedCount();
        String title = "Delete " + selectedCount + (selectedCount == 1 ? " Message" : " Messages");
        
        // Create clean options array without descriptions
        String[] options = {
            "Delete for Me",
            "Delete for Everyone",
            "Cancel"
        };
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title)
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: // Delete for Me
                            performDeleteSelectedForMe();
                            break;
                        case 1: // Delete for Everyone
                            showDeleteForEveryoneConfirmation(selectedCount);
                            break;
                        case 2: // Cancel
                        default:
                            // Do nothing - dialog will dismiss
                            break;
                    }
                });
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    /**
     * Perform delete for me operation for selected messages
     */
    private void performDeleteSelectedForMe() {
        if (adapter != null) {
            adapter.deleteSelectedMessagesLocally();
        }
    }
    
    /**
     * Show confirmation for "Delete for Everyone" for multiple messages
     */
    private void showDeleteForEveryoneConfirmation(int messageCount) {
        String confirmationMessage = WhatsAppStyleDeletionUtil.createDeletionConfirmationMessage(
            true, messageCount, null);
        
        new AlertDialog.Builder(this)
                .setTitle("Delete for Everyone")
                .setMessage(confirmationMessage)
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (adapter != null) {
                        adapter.deleteSelectedMessages();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    // Menu methods
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.chat_options_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_delete_chat) {
            showDeleteChatConfirmationDialog();
            return true;
        } else if (item.getItemId() == R.id.menu_clear_chat) {
            showClearChatConfirmationDialog();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }
    
    // Delete chat confirmation dialog
    private void showDeleteChatConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Chat")
                .setMessage("Are you sure you want to delete this entire conversation with " + otherUser.getUsername() + "?\n\nThis action cannot be undone and the chat will be removed from both users.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    deleteChatConversation();
                })
                .setNegativeButton("Cancel", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }
    
    // Clear chat confirmation dialog
    private void showClearChatConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Clear Chat History")
                .setMessage("Are you sure you want to clear all messages in this conversation with " + otherUser.getUsername() + "?\n\nThis will delete all messages but keep the chat.")
                .setPositiveButton("Clear", (dialog, which) -> {
                    clearChatHistory();
                })
                .setNegativeButton("Cancel", null)
                .setIcon(android.R.drawable.ic_dialog_info)
                .show();
    }
    
    // Delete entire chat conversation
    private void deleteChatConversation() {
        Log.d(TAG, "Deleting chat conversation: " + chatroomId);
        
        // Show loading message
        AndroidUtil.showToast(getApplicationContext(), "Deleting chat...");
        
        FirebaseUtil.deleteChatConversation(chatroomId,
                aVoid -> {
                    Log.d(TAG, "Chat conversation deleted successfully");
                    AndroidUtil.showToast(getApplicationContext(), "Chat deleted successfully");
                    
                    // Close the activity and go back to main activity
                    finish();
                },
                error -> {
                    Log.e(TAG, "Failed to delete chat conversation", error);
                    String errorMessage = "Failed to delete chat: " + error.getMessage();
                    AndroidUtil.showToast(getApplicationContext(), errorMessage);
                }
        );
    }
    
    // Clear chat history
    private void clearChatHistory() {
        Log.d(TAG, "Clearing chat history: " + chatroomId);
        
        // Show loading message
        AndroidUtil.showToast(getApplicationContext(), "Clearing chat history...");
        
        FirebaseUtil.clearChatHistory(chatroomId,
                aVoid -> {
                    Log.d(TAG, "Chat history cleared successfully");
                    AndroidUtil.showToast(getApplicationContext(), "Chat history cleared");
                    
                    // The RecyclerView will automatically update due to Firestore listeners
                },
                error -> {
                    Log.e(TAG, "Failed to clear chat history", error);
                    String errorMessage = "Failed to clear chat history: " + error.getMessage();
                    AndroidUtil.showToast(getApplicationContext(), errorMessage);
                }
        );
    }
    
    @Override
    public void onBackPressed() {
        if (adapter != null && adapter.isSelectionMode()) {
            adapter.exitSelectionMode();
        } else {
            super.onBackPressed();
        }
    }
    
    @Override
    protected void onStart() {
        try {
            super.onStart();
            Log.d(TAG, "▶️ ChatActivity onStart - CRASH PROTECTED");
            if(adapter != null) {
                adapter.startListening();
                Log.d(TAG, "✅ Adapter listening resumed");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error in onStart", e);
            AndroidUtil.showToast(this, "Error resuming chat");
        }
    }

    @Override
    protected void onStop() {
        try {
            super.onStop();
            Log.d(TAG, "⏹️ ChatActivity onStop - CRASH PROTECTED");
            if(adapter != null) {
                adapter.stopListening();
                Log.d(TAG, "✅ Adapter listening paused");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error in onStop", e);
        }
    }

    @Override
    protected void onResume() {
        try {
            super.onResume();
            Log.d(TAG, "🔄 ChatActivity onResume - CRASH PROTECTED");
            if(adapter != null) {
                adapter.startListening();
                Log.d(TAG, "✅ Adapter listening resumed");
            }
            
            // MEMORY MANAGEMENT: Suggest garbage collection on resume
            System.gc();
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error in onResume", e);
            AndroidUtil.showToast(this, "Error resuming chat");
        }
    }
    
    @Override
    protected void onPause() {
        try {
            super.onPause();
            Log.d(TAG, "⏸️ ChatActivity onPause - CRASH PROTECTED");
            if(adapter != null) {
                adapter.stopListening();
                Log.d(TAG, "✅ Adapter listening paused");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error in onPause", e);
        }
    }
    
    @Override
    protected void onDestroy() {
        try {
            super.onDestroy();
            Log.d(TAG, "📴 ChatActivity onDestroy - COMPREHENSIVE CLEANUP");
            
            // CRITICAL: Clean up all resources to prevent memory leaks and crashes
            performComprehensiveCleanup();
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error during onDestroy", e);
        } finally {
            Log.d(TAG, "✅ ChatActivity destroyed - memory cleaned");
        }
    }
    
    /**
     * CRITICAL: Perform comprehensive cleanup to prevent memory leaks and crashes
     */
    private void performComprehensiveCleanup() {
        // Stop adapter and clear all references
        try {
            if (adapter != null) {
                adapter.stopListening();
                adapter = null;
                Log.d(TAG, "✅ Adapter cleaned up");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error cleaning adapter", e);
        }
        
        // Clear RecyclerView to free memory
        try {
            if (recyclerView != null) {
                recyclerView.setAdapter(null);
                recyclerView.setLayoutManager(null);
                recyclerView.clearOnScrollListeners();
                Log.d(TAG, "✅ RecyclerView cleaned up");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error cleaning RecyclerView", e);
        }
        
        // Clear all image views to free bitmap memory
        try {
            if (imageView != null) {
                imageView.setImageDrawable(null);
            }
            Log.d(TAG, "✅ Image views cleaned up");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error cleaning image views", e);
        }
        
        // Clear text views and references
        try {
            otherUser = null;
            chatroomModel = null;
            chatroomId = null;
            Log.d(TAG, "✅ Object references cleared");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error clearing references", e);
        }
        
        // Force garbage collection to free memory immediately
        try {
            System.gc();
            Log.d(TAG, "🗑️ Garbage collection requested");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error requesting garbage collection", e);
        }
    }
    
    // Implementation of MessageDeletionListener interface
    
    @Override
    public void onDeleteForMe(String messageId, String chatroomId) {
        Log.d(TAG, "Delete for me: " + messageId);
        
        // Use optimistic update - update UI immediately
        LocalDeletionUtil.markMessageAsLocallyDeleted(this, chatroomId, messageId);
        
        // Refresh adapter to hide the message
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        
        AndroidUtil.showToast(getApplicationContext(), "Message deleted for you");
        
        // Log the action
        WhatsAppStyleDeletionUtil.logDeletionAction(
            "DELETE_FOR_ME", messageId, chatroomId, FirebaseUtil.currentUserId(), true);
    }
    
    @Override
    public void onDeleteForEveryone(String messageId, String chatroomId) {
        Log.d(TAG, "Delete for everyone: " + messageId);
        
        // Show confirmation dialog first
        showDeleteForEveryoneConfirmation(messageId, chatroomId, false);
    }
    
    @Override
    public void onDeleteMultipleForMe(List<String> messageIds, String chatroomId) {
        Log.d(TAG, "Delete multiple for me: " + messageIds.size() + " messages");
        
        // Use optimistic update - update UI immediately
        for (String messageId : messageIds) {
            LocalDeletionUtil.markMessageAsLocallyDeleted(this, chatroomId, messageId);
        }
        
        // Refresh adapter to hide the messages
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        
        AndroidUtil.showToast(getApplicationContext(), 
            messageIds.size() + " messages deleted for you");
        
        // Log the action
        WhatsAppStyleDeletionUtil.logDeletionAction(
            "DELETE_MULTIPLE_FOR_ME", messageIds.toString(), chatroomId, FirebaseUtil.currentUserId(), true);
    }
    
    @Override
    public void onDeleteMultipleForEveryone(List<String> messageIds, String chatroomId) {
        Log.d(TAG, "Delete multiple for everyone: " + messageIds.size() + " messages");
        
        // Show confirmation dialog first
        showDeleteMultipleForEveryoneConfirmation(messageIds, chatroomId);
    }
    
    @Override
    public boolean isUserAdmin(String chatroomId) {
        // For 1-on-1 chats, the current user can always delete their own messages
        // For group chats, this would check actual admin status from the database
        // Since this is currently a 1-on-1 chat system, return false (no special admin privileges)
        return false;
    }
    
    @Override
    public long getRecallWindowHours() {
        // Return 48 hours as default recall window
        return 48;
    }
    
    // Helper methods
    
    private boolean isGroupChat() {
        // Check if this is a group chat based on chatroomModel
        return chatroomModel != null && chatroomModel.isGroup();
    }
    
    /**
     * Show confirmation dialog for "Delete for Everyone" single message
     */
    private void showDeleteForEveryoneConfirmation(String messageId, String chatroomId, boolean isMultiple) {
        // Get the message to show time remaining if applicable
        FirebaseUtil.getChatroomMessageReference(chatroomId)
                .document(messageId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        ChatMessageModel message = documentSnapshot.toObject(ChatMessageModel.class);
                        String timeRemaining = WhatsAppStyleDeletionUtil.getRecallTimeRemaining(message);
                        
                        String confirmationMessage = WhatsAppStyleDeletionUtil.createDeletionConfirmationMessage(
                            true, 1, timeRemaining);
                        
                        new AlertDialog.Builder(this)
                                .setTitle("Delete for Everyone")
                                .setMessage(confirmationMessage)
                                .setPositiveButton("Delete", (dialog, which) -> {
                                    performDeleteForEveryone(messageId, chatroomId);
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    }
                })
                .addOnFailureListener(e -> {
                    // Fallback without time remaining
                    String confirmationMessage = WhatsAppStyleDeletionUtil.createDeletionConfirmationMessage(
                        true, 1, null);
                    
                    new AlertDialog.Builder(this)
                            .setTitle("Delete for Everyone")
                            .setMessage(confirmationMessage)
                            .setPositiveButton("Delete", (dialog, which) -> {
                                performDeleteForEveryone(messageId, chatroomId);
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                });
    }
    
    /**
     * Show confirmation dialog for "Delete for Everyone" multiple messages
     */
    private void showDeleteMultipleForEveryoneConfirmation(List<String> messageIds, String chatroomId) {
        String confirmationMessage = WhatsAppStyleDeletionUtil.createDeletionConfirmationMessage(
            true, messageIds.size(), null);
        
        new AlertDialog.Builder(this)
                .setTitle("Delete for Everyone")
                .setMessage(confirmationMessage)
                .setPositiveButton("Delete", (dialog, which) -> {
                    performDeleteMultipleForEveryone(messageIds, chatroomId);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    /**
     * Perform the actual "Delete for Everyone" operation with optimistic UI
     */
    private void performDeleteForEveryone(String messageId, String chatroomId) {
        Log.d(TAG, "Performing delete for everyone: " + messageId);
        
        // Show immediate feedback
        AndroidUtil.showToast(getApplicationContext(), "Deleting message...");
        
        // First mark the message as deleted in the database
        FirebaseUtil.getChatroomMessageReference(chatroomId)
                .document(messageId)
                .update("deletedForEveryone", true, 
                       "deletedForEveryoneTimestamp", Timestamp.now(),
                       "deletedByUserId", FirebaseUtil.currentUserId())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Message marked as deleted for everyone: " + messageId);
                    AndroidUtil.showToast(getApplicationContext(), "Message deleted");
                    
                    // Update chatroom last message if needed
                    FirebaseUtil.updateChatroomLastMessageIfNeeded(chatroomId, messageId);
                    
                    // Log success
                    WhatsAppStyleDeletionUtil.logDeletionAction(
                        "DELETE_FOR_EVERYONE", messageId, chatroomId, FirebaseUtil.currentUserId(), true);
                })
                .addOnFailureListener(error -> {
                    Log.e(TAG, "Failed to delete message for everyone: " + messageId, error);
                    AndroidUtil.showToast(getApplicationContext(), "Failed to delete message");
                    
                    // Log failure
                    WhatsAppStyleDeletionUtil.logDeletionAction(
                        "DELETE_FOR_EVERYONE", messageId, chatroomId, FirebaseUtil.currentUserId(), false);
                });
    }
    
    /**
     * Perform the actual "Delete for Everyone" operation for multiple messages
     */
    private void performDeleteMultipleForEveryone(List<String> messageIds, String chatroomId) {
        Log.d(TAG, "Performing delete for everyone: " + messageIds.size() + " messages");
        
        AndroidUtil.showToast(getApplicationContext(), "Deleting " + messageIds.size() + " messages...");
        
        final int[] completedCount = {0};
        final int[] successCount = {0};
        final int totalCount = messageIds.size();
        
        for (String messageId : messageIds) {
            FirebaseUtil.getChatroomMessageReference(chatroomId)
                    .document(messageId)
                    .update("deletedForEveryone", true,
                           "deletedForEveryoneTimestamp", Timestamp.now(),
                           "deletedByUserId", FirebaseUtil.currentUserId())
                    .addOnCompleteListener(task -> {
                        completedCount[0]++;
                        if (task.isSuccessful()) {
                            successCount[0]++;
                        }
                        
                        // Check if all messages have been processed
                        if (completedCount[0] == totalCount) {
                            String resultMessage = successCount[0] + " messages deleted";
                            if (successCount[0] < totalCount) {
                                resultMessage += ", " + (totalCount - successCount[0]) + " failed";
                            }
                            AndroidUtil.showToast(getApplicationContext(), resultMessage);
                            
                            // Log the batch operation
                            WhatsAppStyleDeletionUtil.logDeletionAction(
                                "DELETE_MULTIPLE_FOR_EVERYONE", messageIds.toString(), 
                                chatroomId, FirebaseUtil.currentUserId(), successCount[0] > 0);
                        }
                    });
        }
    }
    
    // Implementation of EditMessageListener interface
    
    @Override
    public void onEditMessage(String messageId, String currentMessage) {
        Log.d(TAG, "Edit message requested: " + messageId + ", current: " + currentMessage);
        showEditMessageDialog(messageId, currentMessage);
    }
    
    /**
     * Show edit message dialog
     */
    private void showEditMessageDialog(String messageId, String currentMessage) {
        // Create EditText for editing
        EditText editText = new EditText(this);
        editText.setText(currentMessage);
        editText.setSelection(currentMessage.length()); // Place cursor at end
        editText.setMaxLines(5);
        editText.setHint("Edit your message...");
        
        // Apply some styling to match the app theme
        editText.setBackgroundResource(R.drawable.edit_text_rounded_corner);
        editText.setPadding(32, 24, 32, 24);
        
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Edit Message")
                .setView(editText)
                .setPositiveButton("Save", null) // Set to null initially to override click behavior
                .setNegativeButton("Cancel", null)
                .create();
        
        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String newMessage = editText.getText().toString().trim();
                
                if (newMessage.isEmpty()) {
                    editText.setError("Message cannot be empty");
                    return;
                }
                
                if (newMessage.length() > 1000) {
                    editText.setError("Message is too long (max 1000 characters)");
                    return;
                }
                
                if (newMessage.equals(currentMessage)) {
                    // No changes made
                    dialog.dismiss();
                    return;
                }
                
                // Perform the edit
                performEditMessage(messageId, newMessage);
                dialog.dismiss();
            });
        });
        
        dialog.show();
        
        // Focus EditText - keyboard will show automatically
        editText.requestFocus();
    }
    
    /**
     * Perform the actual message editing operation
     */
    private void performEditMessage(String messageId, String newMessage) {
        Log.d(TAG, "Performing edit message: " + messageId + ", new: " + newMessage);
        
        AndroidUtil.showToast(getApplicationContext(), "Updating message...");
        
        // Update the message in Firestore
        FirebaseUtil.getChatroomMessageReference(chatroomId)
                .document(messageId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        ChatMessageModel message = documentSnapshot.toObject(ChatMessageModel.class);
                        if (message != null && message.canBeEdited(FirebaseUtil.currentUserId())) {
                            // Edit the message
                            message.editMessage(newMessage);
                            
                            // Update in Firestore
                            FirebaseUtil.getChatroomMessageReference(chatroomId)
                                    .document(messageId)
                                    .set(message)
                                    .addOnSuccessListener(aVoid -> {
                                        Log.d(TAG, "Message edited successfully: " + messageId);
                                        AndroidUtil.showToast(getApplicationContext(), "Message updated");
                                        
                                        // Update chatroom last message if this was the last message
                                        if (chatroomModel != null && messageId.equals(chatroomModel.getLastMessageSenderId())) {
                                            chatroomModel.setLastMessage(newMessage);
                                            FirebaseUtil.getChatroomReference(chatroomId).set(chatroomModel);
                                        }
                                    })
                                    .addOnFailureListener(error -> {
                                        Log.e(TAG, "Failed to edit message: " + messageId, error);
                                        AndroidUtil.showToast(getApplicationContext(), "Failed to update message");
                                    });
                        } else {
                            AndroidUtil.showToast(getApplicationContext(), "Message can no longer be edited");
                        }
                    } else {
                        AndroidUtil.showToast(getApplicationContext(), "Message not found");
                    }
                })
                .addOnFailureListener(error -> {
                    Log.e(TAG, "Failed to get message for editing: " + messageId, error);
                    AndroidUtil.showToast(getApplicationContext(), "Failed to load message");
                });
    }
    
    // Image functionality methods
    
    private void setupImagePickers() {
        try {
            // Setup image picker launcher
            imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    try {
                        Log.d(TAG, "Image picker result received. Result code: " + result.getResultCode());
                        if (result.getResultCode() == RESULT_OK) {
                            if (result.getData() != null) {
                                Log.d(TAG, "Data received from picker");
                                handleImagePickerResult(result.getData());
                            } else if (cameraImageUri != null) {
                                Log.d(TAG, "Camera image captured: " + cameraImageUri);
                                handleCameraResult();
                            } else {
                                Log.e(TAG, "No data received from image picker");
                                AndroidUtil.showToast(this, "No image selected");
                            }
                        } else {
                            Log.d(TAG, "Image picker cancelled or failed");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error handling image picker result", e);
                        AndroidUtil.showToast(this, "Error processing selected image");
                    }
                }
            );
        
            // Setup permission launcher
            requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    try {
                        if (isGranted) {
                            openCamera();
                        } else {
                            AndroidUtil.showToast(this, "Camera permission is required to take photos");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error handling camera permission result", e);
                        AndroidUtil.showToast(this, "Error with camera permission");
                    }
                }
            );
        } catch (Exception e) {
            Log.e(TAG, "Error setting up image pickers", e);
            AndroidUtil.showToast(this, "Error initializing image picker");
        }
    }
    
    private void showImagePickerDialog() {
        Log.d(TAG, "Showing image picker dialog");
        String[] options = {"Camera", "Gallery", "Multiple Photos"};
        
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Select Image")
                    .setItems(options, (dialog, which) -> {
                        Log.d(TAG, "Image picker option selected: " + which);
                        switch (which) {
                            case 0: // Camera
                                Log.d(TAG, "Camera option selected");
                                checkCameraPermission();
                                break;
                            case 1: // Gallery (single)
                                Log.d(TAG, "Single gallery option selected");
                                openGallery(false);
                                break;
                            case 2: // Gallery (multiple)
                                Log.d(TAG, "Multiple gallery option selected");
                                openGallery(true);
                                break;
                        }
                    })
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing image picker dialog", e);
            AndroidUtil.showToast(this, "Error showing image options");
        }
    }
    
    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }
    
    private void openCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            // Create temporary file for camera image
            cameraImageUri = createTempImageUri();
            if (cameraImageUri != null) {
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
                imagePickerLauncher.launch(cameraIntent);
            }
        } else {
            AndroidUtil.showToast(this, "No camera app found");
        }
    }
    
    private void openGallery(boolean multiple) {
        Log.d(TAG, "Opening gallery, multiple: " + multiple);
        
        Intent galleryIntent = new Intent(Intent.ACTION_GET_CONTENT);
        galleryIntent.setType("image/*");
        galleryIntent.addCategory(Intent.CATEGORY_OPENABLE);
        
        // Add flags for persistent permissions
        galleryIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | 
                              Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        
        if (multiple) {
            galleryIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            Log.d(TAG, "Multiple selection enabled");
        }
        
        try {
            imagePickerLauncher.launch(galleryIntent);
            Log.d(TAG, "Gallery intent launched successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch gallery intent", e);
            AndroidUtil.showToast(this, "Unable to open gallery");
        }
    }
    
    private Uri createTempImageUri() {
        try {
            java.io.File imageFile = new java.io.File(getCacheDir(), "temp_camera_image_" + System.currentTimeMillis() + ".jpg");
            return androidx.core.content.FileProvider.getUriForFile(this, 
                getPackageName() + ".provider", imageFile);
        } catch (Exception e) {
            Log.e(TAG, "Error creating temp image URI", e);
            return null;
        }
    }
    
    private void handleImagePickerResult(Intent data) {
        try {
            java.util.List<Uri> selectedImages = new java.util.ArrayList<>();
            
            if (data.getData() != null) {
                // Single image selected
                Log.d(TAG, "Single image selected: " + data.getData());
                Uri imageUri = data.getData();
                
                // Take persistent permission for content URIs
                if (imageUri.getScheme() != null && imageUri.getScheme().equals("content")) {
                    try {
                        getContentResolver().takePersistableUriPermission(imageUri, 
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        Log.d(TAG, "Took persistent permission for single image: " + imageUri);
                    } catch (SecurityException e) {
                        Log.w(TAG, "Could not take persistent permission for: " + imageUri, e);
                    }
                }
                
                selectedImages.add(imageUri);
            } else if (data.getClipData() != null) {
                // Multiple images selected
                int count = Math.min(data.getClipData().getItemCount(), MAX_IMAGES);
                Log.d(TAG, "Multiple images selected: " + count);
                
                for (int i = 0; i < count; i++) {
                    Uri imageUri = data.getClipData().getItemAt(i).getUri();
                    
                    // Take persistent permission for each content URI
                    if (imageUri.getScheme() != null && imageUri.getScheme().equals("content")) {
                        try {
                            getContentResolver().takePersistableUriPermission(imageUri, 
                                Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            Log.d(TAG, "Took persistent permission for image " + (i+1) + ": " + imageUri);
                        } catch (SecurityException e) {
                            Log.w(TAG, "Could not take persistent permission for image " + (i+1) + ": " + imageUri, e);
                        }
                    }
                    
                    selectedImages.add(imageUri);
                    Log.d(TAG, "Added image " + (i+1) + ": " + imageUri);
                }
            }
            
            if (!selectedImages.isEmpty()) {
                Log.d(TAG, "Processing " + selectedImages.size() + " selected images");
                if (selectedImages.size() == 1) {
                    sendSingleImage(selectedImages.get(0));
                } else {
                    sendMultipleImages(selectedImages);
                }
            } else {
                Log.e(TAG, "No images found in picker result");
                AndroidUtil.showToast(this, "No images selected");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling image picker result", e);
            AndroidUtil.showToast(this, "Error processing selected images");
        }
    }
    
    private void handleCameraResult() {
        try {
            if (cameraImageUri != null) {
                Log.d(TAG, "Processing camera image: " + cameraImageUri);
                sendSingleImage(cameraImageUri);
            } else {
                Log.e(TAG, "Camera image URI is null");
                AndroidUtil.showToast(this, "Failed to capture image");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling camera result", e);
            AndroidUtil.showToast(this, "Error processing camera image");
        }
    }
    
    private void sendSingleImage(Uri imageUri) {
        Log.d(TAG, "Starting single image upload: " + imageUri);
        
        if (imageUri == null) {
            Log.e(TAG, "Image URI is null");
            AndroidUtil.showToast(this, "Invalid image selected");
            return;
        }
        
        if (chatroomId == null || chatroomId.isEmpty()) {
            Log.e(TAG, "Chatroom ID is null or empty");
            AndroidUtil.showToast(this, "Chat not ready. Please try again.");
            return;
        }
        
        // Create single image message with local URI (for immediate display)
        sendSingleImageMessage(imageUri);
        
        /* TODO: Re-enable actual single image upload
        AndroidUtil.showToast(this, "Uploading image...");
        
        try {
            ImageUploadUtil.uploadImage(this, imageUri, chatroomId, new ImageUploadUtil.ImageUploadListener() {
                @Override
                public void onUploadProgress(int progress) {
                    Log.d(TAG, "Upload progress: " + progress + "%");
                    runOnUiThread(() -> {
                        // Could show progress here
                    });
                }
                
                @Override
                public void onUploadSuccess(String downloadUrl, ImageMetadata metadata) {
                    Log.d(TAG, "Image uploaded successfully: " + downloadUrl);
                    runOnUiThread(() -> {
                        try {
                            // Create image message
                            ChatMessageModel imageMessage = new ChatMessageModel(
                                FirebaseUtil.currentUserId(),
                                Timestamp.now(),
                                downloadUrl,
                                null, // No caption for now
                                metadata
                            );
                            
                            // Send to Firestore
                            sendImageMessageToFirestore(imageMessage);
                            AndroidUtil.showToast(ChatActivity.this, "Image sent!");
                        } catch (Exception e) {
                            Log.e(TAG, "Error creating image message", e);
                            AndroidUtil.showToast(ChatActivity.this, "Failed to send image");
                        }
                    });
                }
                
                @Override
                public void onUploadFailure(Exception e) {
                    Log.e(TAG, "Failed to upload image: " + imageUri, e);
                    runOnUiThread(() -> {
                        AndroidUtil.showToast(ChatActivity.this, "Failed to send image: " + e.getMessage());
                    });
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in image upload", e);
            AndroidUtil.showToast(this, "Failed to upload image: " + e.getMessage());
        }
        */
    }
    
    private void sendSingleImageMessage(Uri imageUri) {
        Log.d(TAG, "Creating single image message with local URI: " + imageUri);
        
        try {
            if(chatroomModel == null) {
                Log.e(TAG, "Chatroom model is null in single image message");
                AndroidUtil.showToast(getApplicationContext(), "Chat not ready. Please wait a moment.");
                return;
            }
            
            // Create single image message using the constructor
            ChatMessageModel singleImageMessage = new ChatMessageModel(
                FirebaseUtil.currentUserId(),
                Timestamp.now(),
                imageUri.toString(),
                null, // No caption for now
                null  // No metadata for local images
            );
            
            // Update chatroom with last message
            chatroomModel.setLastMessageTimestamp(Timestamp.now());
            chatroomModel.setLastMessageSenderId(FirebaseUtil.currentUserId());
            chatroomModel.setLastMessage(singleImageMessage.getMessage());
            FirebaseUtil.getChatroomReference(chatroomId).set(chatroomModel);
            
            // Add message to Firestore
            FirebaseUtil.getChatroomMessageReference(chatroomId)
                    .add(singleImageMessage)
                    .addOnSuccessListener(documentReference -> {
                        Log.d(TAG, "Single image message sent successfully");
                        AndroidUtil.showToast(getApplicationContext(), "Image sent!");
                        // Send notification
                        sendNotification(singleImageMessage.getMessage());
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to send single image message", e);
                        AndroidUtil.showToast(getApplicationContext(), "Failed to send image");
                    });
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating single image message", e);
            AndroidUtil.showToast(getApplicationContext(), "Error sending image");
        }
    }
    
    // TEMPORARY TEST METHOD for single image
    private void sendTestSingleImageMessage(Uri imageUri) {
        Log.d(TAG, "Sending test single image message: " + imageUri);
        
        String fileName = imageUri.toString().substring(imageUri.toString().lastIndexOf("/") + 1);
        String testMessage = "📷 Image selected: " + fileName + " (test mode)";
        
        if(chatroomModel == null) {
            Log.e(TAG, "Chatroom model is null in single image test");
            AndroidUtil.showToast(getApplicationContext(), "Chat not ready. Please wait a moment.");
            return;
        }

        chatroomModel.setLastMessageTimestamp(Timestamp.now());
        chatroomModel.setLastMessageSenderId(FirebaseUtil.currentUserId());
        chatroomModel.setLastMessage(testMessage);
        FirebaseUtil.getChatroomReference(chatroomId).set(chatroomModel);

        ChatMessageModel testChatMessage = new ChatMessageModel(testMessage, FirebaseUtil.currentUserId(), Timestamp.now());
        
        FirebaseUtil.getChatroomMessageReference(chatroomId).add(testChatMessage)
                .addOnCompleteListener(task -> {
                    if(task.isSuccessful()){
                        Log.d(TAG, "Test single image message sent successfully");
                        AndroidUtil.showToast(getApplicationContext(), "Test: Single image selected!");
                    } else {
                        Log.e(TAG, "Failed to send test single image message", task.getException());
                        AndroidUtil.showToast(getApplicationContext(), "Failed to send test message");
                    }
                });
    }
    
    private void sendMultipleImages(java.util.List<Uri> imageUris) {
        Log.d(TAG, "Starting multiple images upload: " + imageUris.size() + " images");
        
        if (imageUris == null || imageUris.isEmpty()) {
            Log.e(TAG, "No images to upload");
            AndroidUtil.showToast(this, "No images selected");
            return;
        }
        
        if (chatroomId == null || chatroomId.isEmpty()) {
            Log.e(TAG, "Chatroom ID is null or empty");
            AndroidUtil.showToast(this, "Chat not ready. Please try again.");
            return;
        }
        
        // Create multiple images message with local URIs (for immediate display)
        sendMultipleImagesMessage(imageUris);
        
        /* TODO: Re-enable actual multiple image upload
        AndroidUtil.showToast(this, "Uploading " + imageUris.size() + " images...");
        
        ImageUploadUtil.uploadMultipleImages(this, imageUris, chatroomId, new ImageUploadUtil.MultipleImageUploadListener() {
            @Override
            public void onUploadProgress(int completedCount, int totalCount) {
                // Could update progress here
            }
            
            @Override
            public void onUploadSuccess(java.util.List<String> downloadUrls) {
                // Create multiple images message
                ChatMessageModel multiImageMessage = new ChatMessageModel(
                    FirebaseUtil.currentUserId(),
                    Timestamp.now(),
                    downloadUrls,
                    null // No caption for now
                );
                
                // Send to Firestore
                sendImageMessageToFirestore(multiImageMessage);
                AndroidUtil.showToast(ChatActivity.this, downloadUrls.size() + " images sent!");
            }
            
            @Override
            public void onUploadFailure(Exception e) {
                Log.e(TAG, "Failed to upload images", e);
                AndroidUtil.showToast(ChatActivity.this, "Failed to send images. Please try again.");
            }
        });
        */
    }
    
    private void sendMultipleImagesMessage(java.util.List<Uri> imageUris) {
        Log.d(TAG, "Creating multiple images message with local URIs: " + imageUris.size() + " images");
        
        try {
            if(chatroomModel == null) {
                Log.e(TAG, "Chatroom model is null in multiple images message");
                AndroidUtil.showToast(getApplicationContext(), "Chat not ready. Please wait a moment.");
                return;
            }
            
            // Convert Uri list to String list for ChatMessageModel
            java.util.List<String> imageUrlStrings = new java.util.ArrayList<>();
            for (Uri uri : imageUris) {
                imageUrlStrings.add(uri.toString());
            }
            
            // Create multiple images message using the constructor
            ChatMessageModel multiImageMessage = new ChatMessageModel(
                FirebaseUtil.currentUserId(),
                Timestamp.now(),
                imageUrlStrings,
                null // No caption for now
            );
            
            // Update chatroom with last message
            chatroomModel.setLastMessageTimestamp(Timestamp.now());
            chatroomModel.setLastMessageSenderId(FirebaseUtil.currentUserId());
            chatroomModel.setLastMessage(multiImageMessage.getMessage());
            FirebaseUtil.getChatroomReference(chatroomId).set(chatroomModel);
            
            // Add message to Firestore
            FirebaseUtil.getChatroomMessageReference(chatroomId)
                    .add(multiImageMessage)
                    .addOnSuccessListener(documentReference -> {
                        Log.d(TAG, "Multiple images message sent successfully");
                        AndroidUtil.showToast(getApplicationContext(), imageUris.size() + " images sent!");
                        // Send notification
                        sendNotification(multiImageMessage.getMessage());
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to send multiple images message", e);
                        AndroidUtil.showToast(getApplicationContext(), "Failed to send images");
                    });
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating multiple images message", e);
            AndroidUtil.showToast(getApplicationContext(), "Error sending images");
        }
    }
    
    // TEMPORARY TEST METHOD for multiple images
    private void sendTestMultipleImagesMessage(int imageCount) {
        Log.d(TAG, "Sending test multiple images message: " + imageCount + " images");
        
        String testMessage = "📷 " + imageCount + " images selected (test mode)";
        
        if(chatroomModel == null) {
            Log.e(TAG, "Chatroom model is null in multiple images test");
            AndroidUtil.showToast(getApplicationContext(), "Chat not ready. Please wait a moment.");
            return;
        }

        chatroomModel.setLastMessageTimestamp(Timestamp.now());
        chatroomModel.setLastMessageSenderId(FirebaseUtil.currentUserId());
        chatroomModel.setLastMessage(testMessage);
        FirebaseUtil.getChatroomReference(chatroomId).set(chatroomModel);

        ChatMessageModel testChatMessage = new ChatMessageModel(testMessage, FirebaseUtil.currentUserId(), Timestamp.now());
        
        FirebaseUtil.getChatroomMessageReference(chatroomId).add(testChatMessage)
                .addOnCompleteListener(task -> {
                    if(task.isSuccessful()){
                        Log.d(TAG, "Test multiple images message sent successfully");
                        AndroidUtil.showToast(getApplicationContext(), "Test: " + imageCount + " images selected!");
                    } else {
                        Log.e(TAG, "Failed to send test multiple images message", task.getException());
                        AndroidUtil.showToast(getApplicationContext(), "Failed to send test message");
                    }
                });
    }
    
    private void sendImageMessageToFirestore(ChatMessageModel imageMessage) {
        // Update chatroom with last message
        if (chatroomModel != null) {
            chatroomModel.setLastMessageTimestamp(Timestamp.now());
            chatroomModel.setLastMessageSenderId(FirebaseUtil.currentUserId());
            chatroomModel.setLastMessage(imageMessage.getMessage());
            FirebaseUtil.getChatroomReference(chatroomId).set(chatroomModel);
        }
        
        // Add message to Firestore
        FirebaseUtil.getChatroomMessageReference(chatroomId)
                .add(imageMessage)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Image message sent successfully");
                    // Send notification
                    sendNotification(imageMessage.getMessage());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to send image message", e);
                    AndroidUtil.showToast(this, "Failed to send image message");
                });
    }
    
    // TEMPORARY TEST METHOD
    private void sendTestImageMessage(String imageUriString) {
        Log.d(TAG, "Sending test image message: " + imageUriString);
        
        String testMessage = "📷 Image selected: " + imageUriString.substring(imageUriString.lastIndexOf("/") + 1);
        
        if(chatroomModel == null) {
            Log.e(TAG, "Chatroom model is null in test");
            AndroidUtil.showToast(getApplicationContext(), "Chat not ready. Please wait a moment.");
            return;
        }

        chatroomModel.setLastMessageTimestamp(Timestamp.now());
        chatroomModel.setLastMessageSenderId(FirebaseUtil.currentUserId());
        chatroomModel.setLastMessage(testMessage);
        FirebaseUtil.getChatroomReference(chatroomId).set(chatroomModel);

        ChatMessageModel testChatMessage = new ChatMessageModel(testMessage, FirebaseUtil.currentUserId(), Timestamp.now());
        
        FirebaseUtil.getChatroomMessageReference(chatroomId).add(testChatMessage)
                .addOnCompleteListener(task -> {
                    if(task.isSuccessful()){
                        Log.d(TAG, "Test image message sent successfully");
                        AndroidUtil.showToast(getApplicationContext(), "Test image message sent!");
                    } else {
                        Log.e(TAG, "Failed to send test message", task.getException());
                        AndroidUtil.showToast(getApplicationContext(), "Failed to send test message");
                    }
                });
    }
    
    // MOCK IMAGE MESSAGE for testing display without upload
    private void createMockImageMessage(Uri imageUri) {
        Log.d(TAG, "Creating mock image message with local URI: " + imageUri);
        
        try {
            if(chatroomModel == null) {
                Log.e(TAG, "Chatroom model is null in mock image message");
                AndroidUtil.showToast(getApplicationContext(), "Chat not ready. Please wait a moment.");
                return;
            }
            
            // Grant persistent URI permissions
            try {
                getContentResolver().takePersistableUriPermission(imageUri, 
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
                Log.d(TAG, "Granted persistent URI permission for: " + imageUri);
            } catch (Exception e) {
                Log.w(TAG, "Could not grant persistent URI permission: " + e.getMessage());
            }
            
            // Create a mock image message with local URI (for display testing)
            ChatMessageModel mockImageMessage = new ChatMessageModel(
                FirebaseUtil.currentUserId(),
                Timestamp.now(),
                imageUri.toString(), // Use local URI temporarily
                "Selected image: " + imageUri.getLastPathSegment(), // Better caption
                new ImageMetadata(200, 200, 1024, "selected_image.jpg", "image/jpeg")
            );
            
            // Update chatroom
            chatroomModel.setLastMessageTimestamp(Timestamp.now());
            chatroomModel.setLastMessageSenderId(FirebaseUtil.currentUserId());
            chatroomModel.setLastMessage("📷 Photo");
            FirebaseUtil.getChatroomReference(chatroomId).set(chatroomModel);
            
            // Send mock image message to Firestore
            FirebaseUtil.getChatroomMessageReference(chatroomId)
                    .add(mockImageMessage)
                    .addOnSuccessListener(documentReference -> {
                        Log.d(TAG, "Mock image message sent successfully");
                        AndroidUtil.showToast(this, "Mock image message sent!");
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to send mock image message", e);
                        AndroidUtil.showToast(this, "Failed to send mock image message");
                    });
                    
        } catch (Exception e) {
            Log.e(TAG, "Error creating mock image message", e);
            AndroidUtil.showToast(this, "Error creating image message: " + e.getMessage());
        }
    }
    
    // TEST IMAGE with drawable resource
    private void createTestImageWithDrawable() {
        Log.d(TAG, "Creating test image message with drawable resource");
        
        try {
            if(chatroomModel == null) {
                Log.e(TAG, "Chatroom model is null in drawable test");
                AndroidUtil.showToast(getApplicationContext(), "Chat not ready. Please wait a moment.");
                return;
            }
            
            // Create image message with drawable resource URI
            String drawableUri = "android.resource://" + getPackageName() + "/" + R.drawable.chat_icon;
            
            ChatMessageModel testImageMessage = new ChatMessageModel(
                FirebaseUtil.currentUserId(),
                Timestamp.now(),
                drawableUri,
                "Test with drawable resource",
                new ImageMetadata(200, 200, 1024, "test_drawable.png", "image/png")
            );
            
            // Update chatroom
            chatroomModel.setLastMessageTimestamp(Timestamp.now());
            chatroomModel.setLastMessageSenderId(FirebaseUtil.currentUserId());
            chatroomModel.setLastMessage("📷 Test Image");
            FirebaseUtil.getChatroomReference(chatroomId).set(chatroomModel);
            
            // Send test image message to Firestore
            FirebaseUtil.getChatroomMessageReference(chatroomId)
                    .add(testImageMessage)
                    .addOnSuccessListener(documentReference -> {
                        Log.d(TAG, "Test drawable image message sent successfully");
                        AndroidUtil.showToast(this, "Test drawable image sent!");
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to send test drawable image message", e);
                        AndroidUtil.showToast(this, "Failed to send test drawable message");
                    });
                    
        } catch (Exception e) {
            Log.e(TAG, "Error creating test drawable image message", e);
            AndroidUtil.showToast(this, "Error creating drawable image message: " + e.getMessage());
        }
    }
    
}
