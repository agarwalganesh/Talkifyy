package com.example.talkifyy.adapter;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.talkifyy.ChatActivity;
import com.example.talkifyy.R;
import com.example.talkifyy.model.UserModel;
import com.example.talkifyy.utils.AndroidUtil;
import com.example.talkifyy.utils.FirebaseUtil;
import com.firebase.ui.firestore.FirestoreRecyclerAdapter;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;

public class SearchUserRecyclerAdapter extends FirestoreRecyclerAdapter<UserModel, SearchUserRecyclerAdapter.UserModelViewHolder> {

    private final Context context;
    private OnUserSelectListener userSelectListener;

    public SearchUserRecyclerAdapter(@NonNull FirestoreRecyclerOptions<UserModel> options, Context context) {
        super(options);
        this.context = context;
    }
    
    public void setOnUserSelectListener(OnUserSelectListener listener) {
        this.userSelectListener = listener;
    }

    @Override
    protected void onBindViewHolder(@NonNull UserModelViewHolder holder, int position, @NonNull UserModel model) {
        holder.usernameText.setText(model.getUsername());
        holder.phoneText.setText(model.getPhone());

        if(model.getUserId().equals(FirebaseUtil.currentUserId())){
            holder.usernameText.setText(model.getUsername() + " (Me)");
        }

        // Load profile picture - prioritize Firestore URLs (always up-to-date)
        if (model.getProfilePicUrl() != null && !model.getProfilePicUrl().isEmpty()) {
            AndroidUtil.setProfilePic(context, model.getProfilePicUrl(), holder.profilePic);
        } else {
            // Use default profile picture for users without profile pictures
            holder.profilePic.setImageResource(R.drawable.person_icon);
        }


        holder.itemView.setOnClickListener(v -> {
            onUserClick(model);
        });
    }
    
    /**
     * Handle user click - can be overridden for custom behavior
     * @param user The clicked user
     */
    protected void onUserClick(UserModel user) {
        if (userSelectListener != null) {
            userSelectListener.onUserSelected(user);
        } else {
            // Default behavior: Navigate to chat activity
            Intent intent = new Intent(context, ChatActivity.class);
            AndroidUtil.passUserModelAsIntent(intent, user);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    @NonNull
    @Override
    public UserModelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.search_user_recycler_row, parent, false);
        return new UserModelViewHolder(view);
    }

    static class UserModelViewHolder extends RecyclerView.ViewHolder {
        TextView usernameText;
        TextView phoneText;
        ImageView profilePic;

        public UserModelViewHolder(@NonNull View itemView) {
            super(itemView);
            usernameText = itemView.findViewById(R.id.user_name_text);
            phoneText = itemView.findViewById(R.id.phone_text);
            profilePic = itemView.findViewById(R.id.profile_pic_image_view);
        }
    }
    
    public interface OnUserSelectListener {
        void onUserSelected(UserModel user);
        void onUserDeselected(UserModel user);
    }
}
