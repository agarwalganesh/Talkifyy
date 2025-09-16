package com.example.talkifyy;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.target.Target;

public class ImageViewerActivity extends AppCompatActivity {
    
    private static final String TAG = "ImageViewerActivity";
    
    private ImageView fullscreenImageView;
    private ProgressBar loadingProgress;
    private ImageButton backButton;
    private TextView imageInfo;
    
    private String imageUrl;
    private String imageCaption;
    private String senderName;
    private String chatroomId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);
        
        // Hide status bar for full immersive experience
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | 
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
        
        initViews();
        getImageData();
        setupClickListeners();
        loadImage();
    }
    
    private void initViews() {
        fullscreenImageView = findViewById(R.id.fullscreen_image_view);
        loadingProgress = findViewById(R.id.loading_progress);
        backButton = findViewById(R.id.back_button);
        imageInfo = findViewById(R.id.image_info);
    }
    
    private void getImageData() {
        Intent intent = getIntent();
        imageUrl = intent.getStringExtra("imageUrl");
        imageCaption = intent.getStringExtra("imageCaption");
        senderName = intent.getStringExtra("senderName");
        
        // Get chatroom ID for proper back navigation
        chatroomId = intent.getStringExtra("chatroomId");
        
        Log.d(TAG, "Image URL: " + imageUrl);
        Log.d(TAG, "Caption: " + imageCaption);
        Log.d(TAG, "Sender: " + senderName);
        Log.d(TAG, "Chatroom ID: " + chatroomId);
        
        if (imageUrl == null || imageUrl.isEmpty()) {
            Log.e(TAG, "No image URL provided");
            Toast.makeText(this, "No image to display", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Set image info
        StringBuilder info = new StringBuilder();
        if (senderName != null && !senderName.isEmpty()) {
            info.append("From: ").append(senderName);
        }
        if (imageCaption != null && !imageCaption.isEmpty()) {
            if (info.length() > 0) info.append("\n");
            info.append(imageCaption);
        }
        
        if (info.length() > 0) {
            imageInfo.setText(info.toString());
            imageInfo.setVisibility(View.VISIBLE);
        } else {
            imageInfo.setVisibility(View.GONE);
        }
    }
    
    private void setupClickListeners() {
        backButton.setOnClickListener(v -> navigateBack());
        
        // Click anywhere on image to toggle UI
        fullscreenImageView.setOnClickListener(v -> toggleUI());
    }
    
    private void loadImage() {
        loadingProgress.setVisibility(View.VISIBLE);
        
        try {
            Uri imageUri = null;
            
            // Handle different URI formats like in ChatRecyclerAdapter
            if (imageUrl.startsWith("content://") || imageUrl.startsWith("file://")) {
                imageUri = Uri.parse(imageUrl);
                Log.d(TAG, "Loading local URI: " + imageUri);
            } else if (imageUrl.startsWith("android.resource://")) {
                imageUri = Uri.parse(imageUrl);
                Log.d(TAG, "Loading resource URI: " + imageUri);
            } else if (imageUrl.startsWith("http")) {
                Log.d(TAG, "Loading remote URL: " + imageUrl);
            }
            
            Glide.with(this)
                .load(imageUri != null ? imageUri : imageUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .listener(new RequestListener<android.graphics.drawable.Drawable>() {
                    @Override
                    public boolean onLoadFailed(GlideException e, Object model, Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                        Log.e(TAG, "Failed to load image: " + imageUrl, e);
                        runOnUiThread(() -> {
                            loadingProgress.setVisibility(View.GONE);
                            Toast.makeText(ImageViewerActivity.this, "Failed to load image", Toast.LENGTH_SHORT).show();
                        });
                        return false;
                    }
                    
                    @Override
                    public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, Target<android.graphics.drawable.Drawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                        Log.d(TAG, "Image loaded successfully");
                        runOnUiThread(() -> {
                            loadingProgress.setVisibility(View.GONE);
                        });
                        return false;
                    }
                })
                .into(fullscreenImageView);
                
        } catch (Exception e) {
            Log.e(TAG, "Error loading image", e);
            loadingProgress.setVisibility(View.GONE);
            Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void toggleUI() {
        if (backButton.getVisibility() == View.VISIBLE) {
            // Hide UI
            backButton.setVisibility(View.GONE);
            imageInfo.setVisibility(View.GONE);
        } else {
            // Show UI
            backButton.setVisibility(View.VISIBLE);
            if (imageCaption != null && !imageCaption.isEmpty()) {
                imageInfo.setVisibility(View.VISIBLE);
            }
        }
    }
    
    @Override
    public void onBackPressed() {
        Log.d(TAG, "System back button pressed");
        navigateBack();
    }
    
    /**
     * Navigate back to the previous activity
     */
    private void navigateBack() {
        Log.d(TAG, "Navigating back from ImageViewer");
        finish();
    }
}