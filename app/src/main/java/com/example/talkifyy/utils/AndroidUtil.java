package com.example.talkifyy.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.ImageView;
import android.widget.Toast;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

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
        if (imageUrl == null || imageUrl.isEmpty()) {
            Log.d("AndroidUtil", "Image URL is null or empty, using default");
            imageView.setImageResource(R.drawable.person_icon);
            return;
        }
        
        // Check if this is a base64 data URL
        if (imageUrl.startsWith("data:image")) {
            Log.d("AndroidUtil", "Loading base64 data URL image");
            loadBase64Image(imageUrl, imageView);
        } else {
            Log.d("AndroidUtil", "Loading regular URL image: " + imageUrl);
            // Regular URL - use Glide
            Glide.with(context)
                    .load(imageUrl)
                    .apply(RequestOptions.circleCropTransform())
                    .placeholder(R.drawable.person_icon)
                    .error(R.drawable.person_icon)
                    .into(imageView);
        }
    }
    
    private static void loadBase64Image(String dataUrl, ImageView imageView) {
        try {
            // Extract base64 data from data URL
            // Format: "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD..."
            String base64Data = dataUrl.substring(dataUrl.indexOf(",") + 1);
            
            Log.d("AndroidUtil", "Base64 data length: " + base64Data.length());
            
            // Convert base64 to byte array
            byte[] imageBytes = Base64.decode(base64Data, Base64.DEFAULT);
            
            // Convert byte array to bitmap
            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
            
            if (bitmap != null) {
                Log.d("AndroidUtil", "✅ Base64 image decoded successfully");
                
                // Apply circular crop using Glide
                Glide.with(imageView.getContext())
                        .load(bitmap)
                        .apply(RequestOptions.circleCropTransform())
                        .placeholder(R.drawable.person_icon)
                        .error(R.drawable.person_icon)
                        .into(imageView);
            } else {
                Log.e("AndroidUtil", "❌ Failed to decode base64 image");
                imageView.setImageResource(R.drawable.person_icon);
            }
            
        } catch (Exception e) {
            Log.e("AndroidUtil", "❌ Error loading base64 image", e);
            imageView.setImageResource(R.drawable.person_icon);
        }
    }
}
