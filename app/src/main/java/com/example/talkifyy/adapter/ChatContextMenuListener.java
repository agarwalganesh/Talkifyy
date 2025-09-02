package com.example.talkifyy.adapter;

import com.example.talkifyy.model.ChatroomModel;
import com.example.talkifyy.model.UserModel;

public interface ChatContextMenuListener {
    void onOpenChat(UserModel otherUser);
    void onDeleteChat(String chatroomId, UserModel otherUser);
}
