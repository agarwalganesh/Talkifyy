package com.example.talkifyy.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.ImageView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.example.talkifyy.R;
import com.example.talkifyy.model.UserModel;

public class AndroidUtil {

    public static void showToast(Context context , String message){
        Toast.makeText(context,message,Toast.LENGTH_LONG).show();
    }


    public static void passUserModelAsIntent(Intent intent, UserModel model){
        intent.putExtra("username",model.getUsername());
        intent.putExtra("phone",model.getPhone());
        intent.putExtra("userId",model.getUserId());
        intent.putExtra("fcmToken",model.getFcmToken());
        intent.putExtra("profilePicUrl",model.getProfilePicUrl());


    }


    public static UserModel getUserModelFromIntent(Intent intent){
        UserModel userModel = new UserModel();
        userModel.setUsername(intent.getStringExtra("username"));
        userModel.setPhone(intent.getStringExtra("phone"));
        userModel.setUserId(intent.getStringExtra("userId"));
        userModel.setFcmToken(intent.getStringExtra("fcmToken"));
        userModel.setProfilePicUrl(intent.getStringExtra("profilePicUrl"));
        return userModel;
    }
    
    public static void setProfilePic(Context context, Uri imageUri, ImageView imageView) {
        Glide.with(context)
                .load(imageUri)
                .apply(RequestOptions.circleCropTransform())
                .placeholder(R.drawable.person_icon)
                .error(R.drawable.person_icon)
                .into(imageView);
    }
    
    public static void setProfilePic(Context context, String imageUrl, ImageView imageView) {
        Glide.with(context)
                .load(imageUrl)
                .apply(RequestOptions.circleCropTransform())
                .placeholder(R.drawable.person_icon)
                .error(R.drawable.person_icon)
                .into(imageView);
    }
}
