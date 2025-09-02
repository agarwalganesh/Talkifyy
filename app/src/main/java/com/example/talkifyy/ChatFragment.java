package com.example.talkifyy;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.example.talkifyy.adapter.ChatContextMenuListener;
import com.example.talkifyy.adapter.RecentChatRecyclerAdapter;
import com.example.talkifyy.model.ChatroomModel;
import com.example.talkifyy.utils.AndroidUtil;
import com.example.talkifyy.utils.FirebaseUtil;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.firebase.firestore.Query;




import com.example.talkifyy.model.UserModel;


public class ChatFragment extends Fragment implements ChatContextMenuListener {

        private static final String TAG = "ChatFragment";
        RecyclerView recyclerView;
        RecentChatRecyclerAdapter adapter;


        public ChatFragment() {
        }
        @Override


        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View view =  inflater.inflate(R.layout.fragment_chat, container, false);
            recyclerView = view.findViewById(R.id.recycler_view);
            setupRecyclerView();

            return view;
        }

        void setupRecyclerView(){
            // Add safety check for fragment state
            if(!isAdded()) {
                return;
            }
            
            Query query = FirebaseUtil.allChatroomCollectionReference()
                    .whereArrayContains("userIds", FirebaseUtil.currentUserId())
                    .orderBy("lastMessageTimestamp",Query.Direction.DESCENDING);

            FirestoreRecyclerOptions<ChatroomModel> options = new FirestoreRecyclerOptions.Builder<ChatroomModel>()
                    .setQuery(query,ChatroomModel.class).build();

            adapter = new RecentChatRecyclerAdapter(options,getContext());
            adapter.setChatContextMenuListener(this);
            recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            recyclerView.setAdapter(adapter);
            adapter.startListening();
        }

        @Override
        public void onStart() {
            super.onStart();
            if(adapter!=null)
                adapter.startListening();
        }

        @Override
        public void onStop() {
            super.onStop();
            if(adapter!=null)
                adapter.stopListening();
        }

        @Override
        public void onResume() {
            super.onResume();
            if(adapter!=null)
                adapter.notifyDataSetChanged();
        }
        
        @Override
        public void onDestroy() {
            super.onDestroy();
            if(adapter!=null) {
                adapter.cleanup();
            }
        }
        
        // ChatContextMenuListener implementation
        @Override
        public void onOpenChat(UserModel otherUser) {
            Intent intent = new Intent(getActivity(), ChatActivity.class);
            AndroidUtil.passUserModelAsIntent(intent, otherUser);
            startActivity(intent);
        }
        
        @Override
        public void onDeleteChat(String chatroomId, UserModel otherUser) {
            String displayName = (otherUser != null && otherUser.getUsername() != null) 
                    ? otherUser.getUsername() 
                    : "Unknown User";
            Log.d(TAG, "Deleting chat: " + chatroomId + " with user: " + displayName);
            
            FirebaseUtil.deleteChatConversation(chatroomId,
                    aVoid -> {
                        if (isAdded()) {
                            Toast.makeText(getContext(), "Chat with " + displayName + " deleted", Toast.LENGTH_SHORT).show();
                            Log.d(TAG, "Chat deleted successfully: " + chatroomId);
                        }
                    },
                    e -> {
                        if (isAdded()) {
                            Toast.makeText(getContext(), "Failed to delete chat", Toast.LENGTH_SHORT).show();
                            Log.e(TAG, "Failed to delete chat: " + chatroomId, e);
                        }
                    });
        }
}
