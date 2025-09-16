package com.example.talkifyy;

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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.talkifyy.adapter.SearchUserRecyclerAdapter;
import com.example.talkifyy.model.ChatroomModel;
import com.example.talkifyy.model.UserModel;
import com.example.talkifyy.utils.AndroidUtil;
import com.example.talkifyy.utils.FirebaseUtil;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class GroupInfoActivity extends AppCompatActivity {

    private static final String TAG = "GroupInfoActivity";

    // UI Elements
    ImageButton backBtn;
    ImageButton menuBtn;
    EditText groupNameEdit;
    EditText groupDescriptionEdit;
    ImageButton editGroupNameBtn;
    ImageButton saveGroupNameBtn;
    TextView groupCreatedInfo;
    TextView membersTitle;
    ImageButton addMemberBtn;
    RecyclerView membersRecyclerView;
    Button leaveGroupBtn;
    Button deleteGroupBtn;

    // Data
    private String chatroomId;
    private ChatroomModel groupChatroom;
    private boolean isUserAdmin = false;
    private boolean isEditingName = false;
    private String originalGroupName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_group_info);

        // Apply window insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Get chatroom ID from intent
        chatroomId = getIntent().getStringExtra("chatroomId");
        if (chatroomId == null) {
            AndroidUtil.showToast(this, "Error: Group information not found");
            finish();
            return;
        }

        initializeViews();
        setupClickListeners();
        loadGroupInfo();
    }

    private void initializeViews() {
        backBtn = findViewById(R.id.back_btn);
        menuBtn = findViewById(R.id.menu_btn);
        groupNameEdit = findViewById(R.id.group_name_edit);
        groupDescriptionEdit = findViewById(R.id.group_description_edit);
        editGroupNameBtn = findViewById(R.id.edit_group_name_btn);
        saveGroupNameBtn = findViewById(R.id.save_group_name_btn);
        groupCreatedInfo = findViewById(R.id.group_created_info);
        membersTitle = findViewById(R.id.members_title);
        addMemberBtn = findViewById(R.id.add_member_btn);
        membersRecyclerView = findViewById(R.id.members_recycler_view);
        leaveGroupBtn = findViewById(R.id.leave_group_btn);
        deleteGroupBtn = findViewById(R.id.delete_group_btn);

        // Setup RecyclerView
        membersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    private void setupClickListeners() {
        backBtn.setOnClickListener(v -> {
            if (isEditingName) {
                cancelNameEdit();
            } else {
                onBackPressed();
            }
        });

        editGroupNameBtn.setOnClickListener(v -> startNameEdit());
        
        saveGroupNameBtn.setOnClickListener(v -> saveGroupName());

        addMemberBtn.setOnClickListener(v -> showAddMemberDialog());

        leaveGroupBtn.setOnClickListener(v -> showLeaveGroupDialog());

        deleteGroupBtn.setOnClickListener(v -> showDeleteGroupDialog());

        // Setup text watcher for group name
        groupNameEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Enable save button if name changed
                if (isEditingName) {
                    boolean hasChanged = !s.toString().equals(originalGroupName);
                    saveGroupNameBtn.setVisibility(hasChanged ? View.VISIBLE : View.GONE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadGroupInfo() {
        FirebaseUtil.getGroupInfo(chatroomId,
            chatroom -> {
                this.groupChatroom = chatroom;
                displayGroupInfo();
                checkAdminStatus();
                loadGroupMembers();
            },
            error -> {
                Log.e(TAG, "Failed to load group info", error);
                AndroidUtil.showToast(this, "Failed to load group information");
                finish();
            }
        );
    }

    private void displayGroupInfo() {
        if (groupChatroom == null) return;

        // Set group name
        String groupName = groupChatroom.getGroupName();
        originalGroupName = groupName;
        groupNameEdit.setText(groupName);

        // Set group description
        String description = groupChatroom.getGroupDescription();
        if (description != null && !description.isEmpty()) {
            groupDescriptionEdit.setText(description);
        } else {
            groupDescriptionEdit.setText("No description");
        }

        // Set creation info
        if (groupChatroom.getCreatedTimestamp() != null) {
            Date createdDate = groupChatroom.getCreatedTimestamp().toDate();
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            String dateStr = dateFormat.format(createdDate);
            
            // Get creator info
            if (groupChatroom.getCreatedBy() != null) {
                FirebaseUtil.allUserCollectionReference()
                    .document(groupChatroom.getCreatedBy())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            UserModel creator = documentSnapshot.toObject(UserModel.class);
                            String creatorName = creator != null ? creator.getUsername() : "Unknown";
                            groupCreatedInfo.setText("Created by " + creatorName + " • " + dateStr);
                        }
                    })
                    .addOnFailureListener(e -> {
                        groupCreatedInfo.setText("Created on " + dateStr);
                    });
            } else {
                groupCreatedInfo.setText("Created on " + dateStr);
            }
        }

        // Update members count
        int memberCount = groupChatroom.getUserIds().size();
        membersTitle.setText("Members (" + memberCount + ")");
    }

    private void checkAdminStatus() {
        String currentUserId = FirebaseUtil.currentUserId();
        isUserAdmin = groupChatroom.isUserAdmin(currentUserId);
        
        // Show/hide admin controls
        editGroupNameBtn.setVisibility(isUserAdmin ? View.VISIBLE : View.GONE);
        addMemberBtn.setVisibility(isUserAdmin ? View.VISIBLE : View.GONE);
        deleteGroupBtn.setVisibility(isUserAdmin ? View.VISIBLE : View.GONE);
    }

    private void loadGroupMembers() {
        // TODO: Implement members loading with RecyclerView adapter
        // This would show all group members with their profile pictures and names
        // Admins would have additional controls to remove members or promote to admin
    }

    private void startNameEdit() {
        isEditingName = true;
        originalGroupName = groupNameEdit.getText().toString();
        
        groupNameEdit.setEnabled(true);
        groupNameEdit.requestFocus();
        groupNameEdit.setSelection(groupNameEdit.getText().length());
        
        editGroupNameBtn.setVisibility(View.GONE);
        saveGroupNameBtn.setVisibility(View.VISIBLE);
    }

    private void cancelNameEdit() {
        isEditingName = false;
        
        groupNameEdit.setEnabled(false);
        groupNameEdit.setText(originalGroupName);
        
        editGroupNameBtn.setVisibility(View.VISIBLE);
        saveGroupNameBtn.setVisibility(View.GONE);
    }

    private void saveGroupName() {
        String newGroupName = groupNameEdit.getText().toString().trim();
        
        if (newGroupName.isEmpty()) {
            groupNameEdit.setError("Group name cannot be empty");
            return;
        }

        if (newGroupName.equals(originalGroupName)) {
            cancelNameEdit();
            return;
        }

        // Disable controls while saving
        saveGroupNameBtn.setEnabled(false);
        // Note: ImageButton doesn't have setText, so we'll just disable it

        FirebaseUtil.updateGroupName(chatroomId, newGroupName,
            aVoid -> {
                AndroidUtil.showToast(this, "Group name updated");
                originalGroupName = newGroupName;
                isEditingName = false;
                
                groupNameEdit.setEnabled(false);
                editGroupNameBtn.setVisibility(View.VISIBLE);
                saveGroupNameBtn.setVisibility(View.GONE);
                saveGroupNameBtn.setEnabled(true);
                
                // Update the group model
                if (groupChatroom != null) {
                    groupChatroom.setGroupName(newGroupName);
                }
            },
            error -> {
                Log.e(TAG, "Failed to update group name", error);
                AndroidUtil.showToast(this, "Failed to update group name: " + error.getMessage());
                
                saveGroupNameBtn.setEnabled(true);
            }
        );
    }

    private void showAddMemberDialog() {
        if (!isUserAdmin) {
            AndroidUtil.showToast(this, "Only group admins can add members");
            return;
        }

        // Create a custom dialog layout
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_members, null);
        
        EditText searchInput = dialogView.findViewById(R.id.search_input);
        RecyclerView searchRecyclerView = dialogView.findViewById(R.id.search_recycler_view);
        Button addSelectedBtn = dialogView.findViewById(R.id.add_selected_btn);
        TextView selectedCountText = dialogView.findViewById(R.id.selected_count_text);
        
        // Set up RecyclerView
        searchRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        // Create adapter for searching users
        List<UserModel> selectedUsers = new ArrayList<>();
        
        // Create dialog
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Add Members")
                .setView(dialogView)
                .setNegativeButton("Cancel", null)
                .create();
        
        // Set up search functionality
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String searchTerm = s.toString().trim();
                if (searchTerm.length() >= 2) {
                    searchUsers(searchTerm, searchRecyclerView, selectedUsers, selectedCountText, addSelectedBtn);
                } else {
                    // Clear search results
                    searchRecyclerView.setAdapter(null);
                }
            }
            
            @Override
            public void afterTextChanged(Editable s) {}
        });
        
        // Set up add button
        addSelectedBtn.setOnClickListener(v -> {
            if (selectedUsers.isEmpty()) {
                AndroidUtil.showToast(this, "Please select at least one user to add");
                return;
            }
            
            addMembersToGroup(selectedUsers, dialog);
        });
        
        dialog.show();
    }
    
    private void searchUsers(String searchTerm, RecyclerView recyclerView, List<UserModel> selectedUsers, TextView selectedCountText, Button addBtn) {
        Query query = FirebaseUtil.allUserCollectionReference()
                .whereGreaterThanOrEqualTo("username", searchTerm)
                .whereLessThanOrEqualTo("username", searchTerm + '\uf8ff')
                .limit(20);
        
        FirestoreRecyclerOptions<UserModel> options = new FirestoreRecyclerOptions.Builder<UserModel>()
                .setQuery(query, UserModel.class)
                .build();
        
        SearchUserRecyclerAdapter adapter = new SearchUserRecyclerAdapter(options, this);
        
        adapter.setOnUserSelectListener(new SearchUserRecyclerAdapter.OnUserSelectListener() {
            @Override
            public void onUserSelected(UserModel user) {
                // Check if user is already in the group
                if (groupChatroom.getUserIds().contains(user.getUserId())) {
                    AndroidUtil.showToast(GroupInfoActivity.this, user.getUsername() + " is already in the group");
                    return;
                }
                
                // Check if user is already selected
                boolean alreadySelected = false;
                for (UserModel selectedUser : selectedUsers) {
                    if (selectedUser.getUserId().equals(user.getUserId())) {
                        alreadySelected = true;
                        break;
                    }
                }
                
                if (!alreadySelected) {
                    selectedUsers.add(user);
                    updateSelectedUsersUI(selectedUsers, selectedCountText, addBtn);
                    AndroidUtil.showToast(GroupInfoActivity.this, user.getUsername() + " selected");
                } else {
                    AndroidUtil.showToast(GroupInfoActivity.this, user.getUsername() + " is already selected");
                }
            }
            
            @Override
            public void onUserDeselected(UserModel user) {
                selectedUsers.removeIf(u -> u.getUserId().equals(user.getUserId()));
                updateSelectedUsersUI(selectedUsers, selectedCountText, addBtn);
            }
        });
        
        recyclerView.setAdapter(adapter);
        adapter.startListening();
    }
    
    private void updateSelectedUsersUI(List<UserModel> selectedUsers, TextView selectedCountText, Button addBtn) {
        int count = selectedUsers.size();
        if (count == 0) {
            selectedCountText.setText("No users selected");
            addBtn.setEnabled(false);
        } else {
            selectedCountText.setText(count + " user(s) selected");
            addBtn.setEnabled(true);
        }
    }
    
    private void addMembersToGroup(List<UserModel> usersToAdd, AlertDialog dialog) {
        // Show loading state
        dialog.setCancelable(false);
        
        List<String> userIdsToAdd = new ArrayList<>();
        for (UserModel user : usersToAdd) {
            userIdsToAdd.add(user.getUserId());
        }
        
        FirebaseUtil.addUsersToGroup(chatroomId, userIdsToAdd,
            aVoid -> {
                AndroidUtil.showToast(this, "Members added successfully");
                
                // Update local group model
                if (groupChatroom != null) {
                    List<String> currentUserIds = new ArrayList<>(groupChatroom.getUserIds());
                    currentUserIds.addAll(userIdsToAdd);
                    groupChatroom.setUserIds(currentUserIds);
                    
                    // Update members count display
                    displayGroupInfo();
                }
                
                dialog.dismiss();
            },
            error -> {
                Log.e(TAG, "Failed to add members", error);
                AndroidUtil.showToast(this, "Failed to add members: " + error.getMessage());
                dialog.setCancelable(true);
            }
        );
    }

    private void showLeaveGroupDialog() {
        String groupName = groupChatroom != null ? groupChatroom.getGroupName() : "this group";
        
        new AlertDialog.Builder(this)
            .setTitle("Leave Group")
            .setMessage("Are you sure you want to leave \"" + groupName + "\"?\n\nYou won't be able to see new messages or rejoin unless another group member adds you.")
            .setPositiveButton("Leave", (dialog, which) -> leaveGroup())
            .setNegativeButton("Cancel", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show();
    }

    private void showDeleteGroupDialog() {
        if (!isUserAdmin) {
            AndroidUtil.showToast(this, "Only group admins can delete the group");
            return;
        }

        String groupName = groupChatroom != null ? groupChatroom.getGroupName() : "this group";
        
        new AlertDialog.Builder(this)
            .setTitle("Delete Group")
            .setMessage("Are you sure you want to permanently delete \"" + groupName + "\"?\n\nThis action cannot be undone and all messages will be lost for all members.")
            .setPositiveButton("Delete", (dialog, which) -> deleteGroup())
            .setNegativeButton("Cancel", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show();
    }

    private void leaveGroup() {
        String currentUserId = FirebaseUtil.currentUserId();
        
        // Show loading state
        leaveGroupBtn.setEnabled(false);
        leaveGroupBtn.setText("Leaving...");

        FirebaseUtil.removeUserFromGroup(chatroomId, currentUserId,
            aVoid -> {
                AndroidUtil.showToast(this, "You left the group");
                finish(); // Close the activity
            },
            error -> {
                Log.e(TAG, "Failed to leave group", error);
                AndroidUtil.showToast(this, "Failed to leave group: " + error.getMessage());
                
                leaveGroupBtn.setEnabled(true);
                leaveGroupBtn.setText("Leave Group");
            }
        );
    }

    private void deleteGroup() {
        if (!isUserAdmin) {
            AndroidUtil.showToast(this, "Only group admins can delete the group");
            return;
        }

        // Show loading state
        deleteGroupBtn.setEnabled(false);
        deleteGroupBtn.setText("Deleting...");

        FirebaseUtil.deleteChatConversation(chatroomId,
            aVoid -> {
                AndroidUtil.showToast(this, "Group deleted successfully");
                finish(); // Close the activity
            },
            error -> {
                Log.e(TAG, "Failed to delete group", error);
                AndroidUtil.showToast(this, "Failed to delete group: " + error.getMessage());
                
                deleteGroupBtn.setEnabled(true);
                deleteGroupBtn.setText("Delete Group");
            }
        );
    }

    @Override
    public void onBackPressed() {
        if (isEditingName) {
            cancelNameEdit();
        } else {
            super.onBackPressed();
        }
    }
}