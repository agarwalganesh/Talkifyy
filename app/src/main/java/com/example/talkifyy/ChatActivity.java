package com.example.talkifyy;

import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.talkifyy.adapter.ChatRecyclerAdapter;
import com.example.talkifyy.model.ChatMessageModel;
import com.example.talkifyy.model.ChatroomModel;
import com.example.talkifyy.model.UserModel;
import com.example.talkifyy.utils.AndroidUtil;
import com.example.talkifyy.utils.FirebaseUtil;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.Query;

import java.util.Arrays;

public class ChatActivity extends AppCompatActivity {

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

        getOrCreateChatroomModel();
        setupChatRecyclerView();
        setupKeyboardListener();
        
        // Test Firestore connection
        testFirestoreConnection();
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
//                            sendNotification(message);
                        } else {
                            Log.e(TAG, "Failed to send message", task.getException());
                            AndroidUtil.showToast(getApplicationContext(), "Failed to send message. Please try again.");
                        }
                    }
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

        adapter = new ChatRecyclerAdapter(options,this);
        LinearLayoutManager manager = new LinearLayoutManager(this);
        manager.setReverseLayout(true);
        recyclerView.setLayoutManager(manager);
        recyclerView.setAdapter(adapter);
        
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                super.onItemRangeInserted(positionStart, itemCount);
                Log.d(TAG, "New messages inserted, count: " + itemCount);
                recyclerView.smoothScrollToPosition(0);
            }
            
            @Override
            public void onItemRangeChanged(int positionStart, int itemCount) {
                super.onItemRangeChanged(positionStart, itemCount);
                Log.d(TAG, "Messages changed, count: " + itemCount);
                adapter.notifyDataSetChanged();
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
                if (adapter != null && adapter.getItemCount() > 0) {
                    // Small delay to ensure layout is complete
                    recyclerView.postDelayed(() -> {
                        recyclerView.smoothScrollToPosition(0);
                    }, 100);
                }
            }
        });
        
        // Also scroll when EditText gets focus
        messageInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && adapter != null && adapter.getItemCount() > 0) {
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
}
