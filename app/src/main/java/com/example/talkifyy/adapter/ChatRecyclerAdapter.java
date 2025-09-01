package com.example.talkifyy.adapter;


import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.talkifyy.R;
import com.example.talkifyy.model.ChatMessageModel;
import com.example.talkifyy.utils.FirebaseUtil;
import com.firebase.ui.firestore.FirestoreRecyclerAdapter;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.firebase.firestore.FirebaseFirestoreException;


public class ChatRecyclerAdapter extends FirestoreRecyclerAdapter<ChatMessageModel, ChatRecyclerAdapter.ChatModelViewHolder> {

        private static final String TAG = "ChatRecyclerAdapter";
        Context context;

        public ChatRecyclerAdapter(@NonNull FirestoreRecyclerOptions<ChatMessageModel> options, Context context) {
            super(options);
            this.context = context;
        }

        @Override
        protected void onBindViewHolder(@NonNull ChatModelViewHolder holder, int position, @NonNull ChatMessageModel model) {
            Log.d(TAG, "onBindViewHolder called for position: " + position + ", message: " + model.getMessage() + ", senderId: " + model.getSenderId());
            
            if(model.getSenderId().equals(FirebaseUtil.currentUserId())){
                Log.d(TAG, "Message from current user (right side)");
                holder.leftChatLayout.setVisibility(View.GONE);
                holder.rightChatLayout.setVisibility(View.VISIBLE);
                holder.rightChatTextview.setText(model.getMessage());
            }else{
                Log.d(TAG, "Message from other user (left side)");
                holder.rightChatLayout.setVisibility(View.GONE);
                holder.leftChatLayout.setVisibility(View.VISIBLE);
                holder.leftChatTextview.setText(model.getMessage());
            }
        }

        @NonNull
        @Override
        public ChatModelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Log.d(TAG, "onCreateViewHolder called");
            View view = LayoutInflater.from(context).inflate(R.layout.chat_message_recycler_row,parent,false);
            return new ChatModelViewHolder(view);
        }
        
        @Override
        public void onDataChanged() {
            super.onDataChanged();
            Log.d(TAG, "onDataChanged called, itemCount: " + getItemCount());
        }
        
        @Override
        public void onError(@NonNull FirebaseFirestoreException e) {
            super.onError(e);
            Log.e(TAG, "Firestore RecyclerAdapter error", e);
        }

        class ChatModelViewHolder extends RecyclerView.ViewHolder{

            LinearLayout leftChatLayout,rightChatLayout;
            TextView leftChatTextview,rightChatTextview;

            public ChatModelViewHolder(@NonNull View itemView) {
                super(itemView);

                leftChatLayout = itemView.findViewById(R.id.left_chat_layout);
                rightChatLayout = itemView.findViewById(R.id.right_chat_layout);
                leftChatTextview = itemView.findViewById(R.id.left_chat_textview);
                rightChatTextview = itemView.findViewById(R.id.right_chat_textview);
            }
        }
}
