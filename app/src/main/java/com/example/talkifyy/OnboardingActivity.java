package com.example.talkifyy;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.example.talkifyy.adapter.OnboardingPagerAdapter;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class OnboardingActivity extends AppCompatActivity {
    
    private static final String PREFS_NAME = "TalkifyyPrefs";
    private static final String KEY_FIRST_TIME = "first_time_user";
    
    private ViewPager2 viewPager;
    private TabLayout pageIndicator;
    private Button nextButton;
    private Button skipButton;
    private Button getStartedButton;
    
    private OnboardingPagerAdapter adapter;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);
        
        initViews();
        setupViewPager();
        setupClickListeners();
    }
    
    private void initViews() {
        viewPager = findViewById(R.id.viewPager);
        pageIndicator = findViewById(R.id.pageIndicator);
        nextButton = findViewById(R.id.nextButton);
        skipButton = findViewById(R.id.skipButton);
        getStartedButton = findViewById(R.id.getStartedButton);
    }
    
    private void setupViewPager() {
        adapter = new OnboardingPagerAdapter(this);
        viewPager.setAdapter(adapter);
        
        // Setup page indicator dots
        new TabLayoutMediator(pageIndicator, viewPager, (tab, position) -> {
            // Tab configuration handled by TabLayoutMediator
        }).attach();
        
        // Handle page changes
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updateButtonsForPage(position);
            }
        });
    }
    
    private void setupClickListeners() {
        nextButton.setOnClickListener(v -> {
            int currentPage = viewPager.getCurrentItem();
            if (currentPage < adapter.getItemCount() - 1) {
                viewPager.setCurrentItem(currentPage + 1);
            }
        });
        
        skipButton.setOnClickListener(v -> finishOnboarding());
        
        getStartedButton.setOnClickListener(v -> finishOnboarding());
    }
    
    private void updateButtonsForPage(int position) {
        if (position == adapter.getItemCount() - 1) {
            // Last page - show "Get Started" button
            nextButton.setVisibility(View.GONE);
            skipButton.setVisibility(View.GONE);
            getStartedButton.setVisibility(View.VISIBLE);
        } else {
            // First and middle pages - show Next and Skip buttons
            nextButton.setVisibility(View.VISIBLE);
            skipButton.setVisibility(View.VISIBLE);
            getStartedButton.setVisibility(View.GONE);
        }
    }
    
    private void finishOnboarding() {
        // Mark onboarding as completed
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_FIRST_TIME, false).apply();
        
        // Navigate to login flow
        Intent intent = new Intent(this, LoginPhoneNumberActivity.class);
        startActivity(intent);
        finish();
        
        // Add smooth transition
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }
    
    // Static method to check if onboarding should be shown
    public static boolean shouldShowOnboarding(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getBoolean(KEY_FIRST_TIME, true);
    }
}