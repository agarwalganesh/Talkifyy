package com.example.talkifyy;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.talkifyy.adapter.SearchUserRecyclerAdapter;
import com.example.talkifyy.model.UserModel;
import com.example.talkifyy.utils.AndroidUtil;
import com.example.talkifyy.utils.FirebaseUtil;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GroupCreateActivity extends AppCompatActivity {

    private static final String TAG = "GroupCreateActivity";

    EditText groupNameInput;
    EditText groupDescriptionInput;
    EditText searchUsersInput;
    ImageButton backBtn;
    ImageButton searchBtn;
    Button createGroupBtn;
    TextView selectedUsersTitle;
    RecyclerView selectedUsersRecyclerView;
    RecyclerView searchResultsRecyclerView;

    SearchUserRecyclerAdapter searchAdapter;
    
    // Selected users for the group
    Set<String> selectedUserIds = new HashSet<>();
    List<UserModel> selectedUsers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_group_create);

        // Apply window insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initializeViews();
        setupClickListeners();
        setupTextWatchers();
    }

    private void initializeViews() {
        groupNameInput = findViewById(R.id.group_name_input);
        groupDescriptionInput = findViewById(R.id.group_description_input);
        searchUsersInput = findViewById(R.id.search_users_input);
        backBtn = findViewById(R.id.back_btn);
        searchBtn = findViewById(R.id.search_btn);
        createGroupBtn = findViewById(R.id.create_group_btn);
        selectedUsersTitle = findViewById(R.id.selected_users_title);
        selectedUsersRecyclerView = findViewById(R.id.selected_users_recycler_view);
        searchResultsRecyclerView = findViewById(R.id.search_results_recycler_view);

        // Set focus to group name input
        groupNameInput.requestFocus();

        // Setup RecyclerViews
        selectedUsersRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        searchResultsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    private void setupClickListeners() {
        backBtn.setOnClickListener(v -> onBackPressed());

        createGroupBtn.setOnClickListener(v -> createGroup());

        searchBtn.setOnClickListener(v -> {
            String searchTerm = searchUsersInput.getText().toString().trim();
            if (searchTerm.isEmpty() || searchTerm.length() < 2) {
                searchUsersInput.setError("Enter at least 2 characters to search");
                return;
            }
            setupSearchRecyclerView(searchTerm);
        });
    }

    private void setupTextWatchers() {
        // Group name text watcher
        groupNameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateCreateButtonState();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Search input text watcher
        searchUsersInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String searchTerm = s.toString().trim();
                if (searchTerm.length() >= 2) {
                    setupSearchRecyclerView(searchTerm);
                } else if (searchAdapter != null) {
                    searchAdapter.stopListening();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupSearchRecyclerView(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return;
        }

        Query query = FirebaseUtil.allUserCollectionReference()
                .whereGreaterThanOrEqualTo("username", searchTerm)
                .whereLessThanOrEqualTo("username", searchTerm + '\uf8ff');

        FirestoreRecyclerOptions<UserModel> options = new FirestoreRecyclerOptions.Builder<UserModel>()
                .setQuery(query, UserModel.class)
                .build();

        if (searchAdapter != null) {
            searchAdapter.stopListening();
        }

        searchAdapter = new SearchUserRecyclerAdapter(options, getApplicationContext()) {
            @Override
            protected void onUserClick(UserModel user) {
                toggleUserSelection(user);
            }
        };

        searchResultsRecyclerView.setAdapter(searchAdapter);
        searchAdapter.startListening();
    }

    private void toggleUserSelection(UserModel user) {
        String userId = user.getUserId();
        String currentUserId = FirebaseUtil.currentUserId();

        // Don't allow selecting current user
        if (userId.equals(currentUserId)) {
            AndroidUtil.showToast(this, "You can't add yourself to the group");
            return;
        }

        if (selectedUserIds.contains(userId)) {
            // Remove user from selection
            selectedUserIds.remove(userId);
            selectedUsers.removeIf(u -> u.getUserId().equals(userId));
            AndroidUtil.showToast(this, user.getUsername() + " removed from group");
        } else {
            // Add user to selection
            selectedUserIds.add(userId);
            selectedUsers.add(user);
            AndroidUtil.showToast(this, user.getUsername() + " added to group");
        }

        updateSelectedUsersDisplay();
        updateCreateButtonState();
    }

    private void updateSelectedUsersDisplay() {
        int count = selectedUsers.size();
        selectedUsersTitle.setText("Selected Users (" + count + ")");

        // Here you could set up an adapter for selectedUsersRecyclerView to show selected users
        // For now, we'll just update the count
    }

    private void updateCreateButtonState() {
        String groupName = groupNameInput.getText().toString().trim();
        boolean hasName = !groupName.isEmpty() && groupName.length() >= 2;
        boolean hasUsers = selectedUsers.size() >= 1; // At least 1 other user besides creator

        boolean enabled = hasName && hasUsers;
        createGroupBtn.setEnabled(enabled);
        
        if (enabled) {
            createGroupBtn.setBackgroundTintList(getResources().getColorStateList(R.color.my_primary, null));
        } else {
            createGroupBtn.setBackgroundTintList(getResources().getColorStateList(R.color.light_gray, null));
        }
    }

    private void createGroup() {
        String groupName = groupNameInput.getText().toString().trim();
        String groupDescription = groupDescriptionInput.getText().toString().trim();

        if (groupName.isEmpty()) {
            groupNameInput.setError("Group name is required");
            return;
        }

        if (selectedUsers.isEmpty()) {
            AndroidUtil.showToast(this, "Please select at least one user to add to the group");
            return;
        }

        // Disable button to prevent multiple clicks
        createGroupBtn.setEnabled(false);
        createGroupBtn.setText("Creating...");

        // Create list of user IDs including the creator
        List<String> userIds = new ArrayList<>(selectedUserIds);
        String currentUserId = FirebaseUtil.currentUserId();
        if (!userIds.contains(currentUserId)) {
            userIds.add(currentUserId);
        }

        Log.d(TAG, "Creating group: " + groupName + " with " + userIds.size() + " members");

        FirebaseUtil.createGroupChat(
            groupName,
            groupDescription,
            userIds,
            currentUserId,
            groupId -> {
                Log.d(TAG, "Group created successfully: " + groupId);
                AndroidUtil.showToast(this, "Group created successfully!");
                
                // Navigate to the new group chat
                Intent intent = new Intent(GroupCreateActivity.this, ChatActivity.class);
                
                // Create a UserModel representing the group for the intent
                UserModel groupUser = new UserModel();
                groupUser.setUserId(groupId);
                groupUser.setUsername(groupName);
                AndroidUtil.passUserModelAsIntent(intent, groupUser);
                
                intent.putExtra("isGroup", true);
                intent.putExtra("chatroomId", groupId);
                
                startActivity(intent);
                finish();
            },
            error -> {
                Log.e(TAG, "Failed to create group", error);
                AndroidUtil.showToast(this, "Failed to create group: " + error.getMessage());
                
                // Re-enable button
                createGroupBtn.setEnabled(true);
                createGroupBtn.setText("Create Group");
                updateCreateButtonState();
            }
        );
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (searchAdapter != null) {
            searchAdapter.startListening();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (searchAdapter != null) {
            searchAdapter.stopListening();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (searchAdapter != null) {
            searchAdapter.startListening();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (searchAdapter != null) {
            searchAdapter.stopListening();
        }
    }
}