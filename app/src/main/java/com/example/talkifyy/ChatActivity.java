package com.example.talkifyy;

import android.animation.ObjectAnimator;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
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
import com.example.talkifyy.model.ChatMessageModel;
import com.example.talkifyy.model.ChatroomModel;
import com.example.talkifyy.model.UserModel;
import com.example.talkifyy.utils.AndroidUtil;
import com.example.talkifyy.utils.FirebaseUtil;
import com.example.talkifyy.utils.LocalDeletionUtil;
import com.example.talkifyy.utils.NotificationUtil;
import com.example.talkifyy.utils.UnsendConfig;
import com.example.talkifyy.utils.WhatsAppStyleDeletionUtil;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.Query;

import java.util.Arrays;
import java.util.List;

public class ChatActivity extends AppCompatActivity implements UnsendMessageListener, MessageDeletionListener {

    private static final String TAG = "ChatActivity";

    UserModel otherUser;
    String chatroomId;
     ChatroomModel chatroomModel;
     ChatRecyclerAdapter adapter;

    EditText messageInput;
    ImageButton sendMessageBtn;
    ImageButton backBtn;
    TextView otherUsername;
    RecyclerView recyclerView;
    ImageView imageView;
    
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


        //get UserModel
        otherUser = AndroidUtil.getUserModelFromIntent(getIntent());
        chatroomId = FirebaseUtil.getChatroomId(FirebaseUtil.currentUserId(),otherUser.getUserId());
        
        Log.d(TAG, "ChatActivity started");
        Log.d(TAG, "Current user ID: " + FirebaseUtil.currentUserId());
        Log.d(TAG, "Other user ID: " + otherUser.getUserId());
        Log.d(TAG, "Chat room ID: " + chatroomId);

        messageInput = findViewById(R.id.chat_message_input);
        sendMessageBtn = findViewById(R.id.message_send_btn);
        backBtn = findViewById(R.id.back_btn);
        otherUsername = findViewById(R.id.other_username);
        recyclerView = findViewById(R.id.chat_recycler_view);
        imageView = findViewById(R.id.profile_pic_image_view);




        backBtn.setOnClickListener((v)->{
            onBackPressed();
        });
        otherUsername.setText(otherUser.getUsername());
        
        // Load other user's profile picture
        loadOtherUserProfilePicture();

        sendMessageBtn.setOnClickListener((v -> {
            sendMessage();
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
        
        getOrCreateChatroomModel();
        setupChatRecyclerView();
        setupKeyboardListener();
        
        // Test Firestore connection
        testFirestoreConnection();
    }

    void loadOtherUserProfilePicture() {
        // First try to get the URL from the intent data (most efficient)
        if (otherUser.getProfilePicUrl() != null && !otherUser.getProfilePicUrl().isEmpty()) {
            Log.d(TAG, "Loading other user profile picture from intent URL: " + otherUser.getProfilePicUrl());
            AndroidUtil.setProfilePic(ChatActivity.this, otherUser.getProfilePicUrl(), imageView);
        } else {
            // Fallback: get fresh data from Firestore (always up-to-date)
            Log.d(TAG, "No URL in intent, fetching from Firestore");
            FirebaseUtil.allUserCollectionReference().document(otherUser.getUserId())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            UserModel otherUserData = documentSnapshot.toObject(UserModel.class);
                            if (otherUserData != null && otherUserData.getProfilePicUrl() != null && !otherUserData.getProfilePicUrl().isEmpty()) {
                                Log.d(TAG, "Loading other user profile picture from Firestore URL: " + otherUserData.getProfilePicUrl());
                                AndroidUtil.setProfilePic(ChatActivity.this, otherUserData.getProfilePicUrl(), imageView);
                                // Update the local user model with the latest profile pic URL
                                otherUser.setProfilePicUrl(otherUserData.getProfilePicUrl());
                            } else {
                                Log.d(TAG, "No profile picture URL found for other user, using default");
                                imageView.setImageResource(R.drawable.person_icon);
                            }
                        } else {
                            Log.d(TAG, "Other user document does not exist, using default profile picture");
                            imageView.setImageResource(R.drawable.person_icon);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to get other user data from Firestore", e);
                        imageView.setImageResource(R.drawable.person_icon);
                    });
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
                
                // Send notification to the other user
                NotificationUtil.sendNotificationToUser(
                    otherUser.getUserId(),
                    senderName,
                    message,
                    chatroomId
                );
                
                Log.d(TAG, "Notification sent from " + senderName + " to " + otherUser.getUsername());
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to get current user details for notification", e);
            // Send notification anyway with fallback name
            NotificationUtil.sendNotificationToUser(
                otherUser.getUserId(),
                "Someone",
                message,
                chatroomId
            );
        });
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
                    } else {
                        Log.e(TAG, "Failed to create new chatroom", setTask.getException());
                    }
                });
            } else {
                Log.d(TAG, "Existing chatroom model loaded");
            }
        } else {
            Log.e(TAG, "Failed to get chatroom model", task.getException());
            AndroidUtil.showToast(getApplicationContext(), "Failed to load chat. Please try again.");
        }
    });
}


    void setupChatRecyclerView(){
        Log.d(TAG, "Setting up chat RecyclerView for chatroom: " + chatroomId);
        
        Query query = FirebaseUtil.getChatroomMessageReference(chatroomId)
                .orderBy("timestamp", Query.Direction.DESCENDING);

        FirestoreRecyclerOptions<ChatMessageModel> options = new FirestoreRecyclerOptions.Builder<ChatMessageModel>()
                .setQuery(query,ChatMessageModel.class)
                .build();

        // Create adapter with unsend listener
        adapter = new ChatRecyclerAdapter(options, this, chatroomId, this);
        
        // Set unsend time window from configuration
        long unsendTimeWindow = UnsendConfig.getUnsendTimeWindowMinutes(this);
        adapter.setUnsendTimeWindow(unsendTimeWindow);
        
        // Configure WhatsApp-style deletion
        adapter.setMessageDeletionListener(this);
        adapter.setGroupChat(isGroupChat()); // Determine if this is a group chat
        
        LinearLayoutManager manager = new LinearLayoutManager(this);
        manager.setReverseLayout(true);
        recyclerView.setLayoutManager(manager);
        recyclerView.setAdapter(adapter);
        
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                super.onItemRangeInserted(positionStart, itemCount);
                Log.d(TAG, "New messages inserted, count: " + itemCount);
                // Only auto-scroll to new messages if not in selection mode
                if (adapter != null && !adapter.isSelectionMode()) {
                    recyclerView.smoothScrollToPosition(0);
                }
            }
            
            @Override
            public void onItemRangeChanged(int positionStart, int itemCount) {
                super.onItemRangeChanged(positionStart, itemCount);
                Log.d(TAG, "Messages changed, count: " + itemCount);
                // Don't trigger additional updates during selection mode
                if (adapter != null && !adapter.isSelectionMode()) {
                    // Only call notifyDataSetChanged if it's not already being handled
                    // This prevents infinite loops and unnecessary updates
                }
            }
            
            @Override
            public void onItemRangeRemoved(int positionStart, int itemCount) {
                super.onItemRangeRemoved(positionStart, itemCount);
                Log.d(TAG, "Messages removed, count: " + itemCount);
                // Don't auto-scroll when messages are removed during selection
            }
            
            @Override
            public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
                super.onItemRangeMoved(fromPosition, toPosition, itemCount);
                // Prevent scrolling on item moves during selection
            }
        });
        
        // Start listening immediately
        Log.d(TAG, "Starting adapter listening");
        adapter.startListening();
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
     * Show dialog for deleting selected messages with WhatsApp-style options
     */
    private void showDeleteSelectedMessagesDialog() {
        if (adapter == null || adapter.getSelectedCount() == 0) {
            return;
        }
        
        int selectedCount = adapter.getSelectedCount();
        String title = "Delete " + selectedCount + (selectedCount == 1 ? " Message" : " Messages");
        
        // Create options array with descriptions
        String[] options = {
            "Delete for Me\nThis will only remove the message" + (selectedCount > 1 ? "s" : "") + " from your device.",
            "Delete for Everyone\nThis will delete the message" + (selectedCount > 1 ? "s" : "") + " for all participants.",
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
        super.onStart();
        if(adapter != null) {
            adapter.startListening();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(adapter != null) {
            adapter.stopListening();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(adapter != null) {
            adapter.startListening();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if(adapter != null) {
            adapter.stopListening();
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
        // Determine if this is a group chat
        // For now, assume all chats are 1-on-1 (return false)
        // In a full group chat implementation, this would check the chatroom user count
        return false;
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
}
