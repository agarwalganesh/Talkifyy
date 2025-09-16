package com.example.talkifyy;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class SwipeableImageViewerActivity extends AppCompatActivity {
    
    private static final String TAG = "SwipeableImageViewer";
    
    private ViewPager2 viewPager;
    private TextView imageCounter;
    private TextView imageCaption;
    private TextView senderName;
    private ImageView closeButton;
    private ImageView shareButton;
    
    private List<String> imageUrls;
    private int currentPosition;
    private String caption;
    private String sender;
    private String chatroomId;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Make activity fullscreen
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
        setContentView(R.layout.activity_swipeable_image_viewer);
        
        initializeViews();
        getIntentData();
        setupViewPager();
        setupClickListeners();
    }
    
    private void initializeViews() {
        viewPager = findViewById(R.id.image_viewpager);
        imageCounter = findViewById(R.id.image_counter);
        imageCaption = findViewById(R.id.image_caption);
        senderName = findViewById(R.id.sender_name);
        closeButton = findViewById(R.id.close_button);
        shareButton = findViewById(R.id.share_button);
    }
    
    private void getIntentData() {
        Intent intent = getIntent();
        
        // Get image URLs list
        imageUrls = intent.getStringArrayListExtra("imageUrls");
        if (imageUrls == null) {
            imageUrls = new ArrayList<>();
        }
        
        // Get starting position
        currentPosition = intent.getIntExtra("currentPosition", 0);
        
        // Get caption and sender
        caption = intent.getStringExtra("imageCaption");
        sender = intent.getStringExtra("senderName");
        
        // Get chatroom ID for proper back navigation
        chatroomId = intent.getStringExtra("chatroomId");
        
        Log.d(TAG, "Received " + imageUrls.size() + " images, starting at position " + currentPosition + ", chatroomId: " + chatroomId);
        
        // Validate data
        if (imageUrls.isEmpty()) {
            Log.e(TAG, "No image URLs provided");
            Toast.makeText(this, "No images to display", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Ensure valid position
        if (currentPosition >= imageUrls.size()) {
            currentPosition = 0;
        }
    }
    
    private void setupViewPager() {
        if (imageUrls.isEmpty()) return;
        
        // Create adapter for swipeable images
        SwipeableImageAdapter adapter = new SwipeableImageAdapter(imageUrls);
        viewPager.setAdapter(adapter);
        
        // Set starting position
        viewPager.setCurrentItem(currentPosition, false);
        
        // Update UI for current position
        updateImageInfo(currentPosition);
        
        // Listen for page changes
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                currentPosition = position;
                updateImageInfo(position);
                Log.d(TAG, "Swiped to image " + (position + 1) + " of " + imageUrls.size());
            }
        });
    }
    
    private void updateImageInfo(int position) {
        // Update counter (e.g., "1 / 5")
        String counterText = (position + 1) + " / " + imageUrls.size();
        imageCounter.setText(counterText);
        
        // Update caption and sender
        if (caption != null && !caption.isEmpty()) {
            imageCaption.setVisibility(View.VISIBLE);
            imageCaption.setText(caption);
        } else {
            imageCaption.setVisibility(View.GONE);
        }
        
        if (sender != null && !sender.isEmpty()) {
            senderName.setVisibility(View.VISIBLE);
            senderName.setText("From: " + sender);
        } else {
            senderName.setVisibility(View.GONE);
        }
    }
    
    private void setupClickListeners() {
        // Close button
        closeButton.setOnClickListener(v -> {
            Log.d(TAG, "Close button clicked");
            navigateBack();
        });
        
        // Share button
        shareButton.setOnClickListener(v -> {
            Log.d(TAG, "Share button clicked for image " + (currentPosition + 1));
            shareCurrentImage();
        });
        
        // Click on image to toggle UI visibility
        // This will be handled by the adapter
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
        Log.d(TAG, "Navigating back from SwipeableImageViewer");
        finish();
    }
    
    private void shareCurrentImage() {
        if (currentPosition < imageUrls.size()) {
            String currentImageUrl = imageUrls.get(currentPosition);
            
            try {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("image/*");
                
                // For content URIs, we can share directly
                if (currentImageUrl.startsWith("content://")) {
                    android.net.Uri imageUri = android.net.Uri.parse(currentImageUrl);
                    shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri);
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    
                    String shareText = "Image " + (currentPosition + 1) + " of " + imageUrls.size();
                    if (caption != null && !caption.isEmpty()) {
                        shareText += "\\n" + caption;
                    }
                    shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
                    
                    startActivity(Intent.createChooser(shareIntent, "Share Image"));
                } else {
                    Toast.makeText(this, "Cannot share this image", Toast.LENGTH_SHORT).show();
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error sharing image", e);
                Toast.makeText(this, "Error sharing image", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    // Adapter for ViewPager2 to display swipeable images
    private static class SwipeableImageAdapter extends RecyclerView.Adapter<SwipeableImageAdapter.ImageViewHolder> {
        
        private final List<String> imageUrls;
        
        public SwipeableImageAdapter(List<String> imageUrls) {
            this.imageUrls = imageUrls;
        }
        
        @Override
        public ImageViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            ImageView imageView = new ImageView(parent.getContext());
            imageView.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT));
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setBackgroundColor(0xFF000000); // Black background
            
            return new ImageViewHolder(imageView);
        }
        
        @Override
        public void onBindViewHolder(ImageViewHolder holder, int position) {
            String imageUrl = imageUrls.get(position);
            Log.d(TAG, "Loading image for swipe view: " + imageUrl + " at position " + position);
            
            try {
                android.net.Uri imageUri = null;
                
                // Handle different URI formats
                if (imageUrl.startsWith("content://") || imageUrl.startsWith("file://")) {
                    imageUri = android.net.Uri.parse(imageUrl);
                }
                
                // Load image with Glide for full-screen viewing
                com.bumptech.glide.Glide.with(holder.imageView.getContext())
                    .load(imageUri != null ? imageUri : imageUrl)
                    .fitCenter()
                    .placeholder(android.R.color.black)
                    .error(android.R.color.black)
                    .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                        @Override
                        public boolean onLoadFailed(com.bumptech.glide.load.engine.GlideException e, Object model, 
                            com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, 
                            boolean isFirstResource) {
                            Log.e(TAG, "Failed to load swipe image: " + imageUrl, e);
                            return false;
                        }
                        
                        @Override
                        public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model,
                            com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                            com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                            Log.d(TAG, "Successfully loaded swipe image: " + imageUrl);
                            return false;
                        }
                    })
                    .into(holder.imageView);
                    
            } catch (Exception e) {
                Log.e(TAG, "Error loading swipe image: " + imageUrl, e);
                holder.imageView.setImageResource(android.R.color.black);
            }
        }
        
        @Override
        public int getItemCount() {
            return imageUrls.size();
        }
        
        static class ImageViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;
            
            public ImageViewHolder(android.view.View itemView) {
                super(itemView);
                this.imageView = (ImageView) itemView;
            }
        }
    }
}