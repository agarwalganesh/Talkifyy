package com.example.talkifyy.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.talkifyy.fragments.OnboardingPageFragment;

public class OnboardingPagerAdapter extends FragmentStateAdapter {
    
    private static final int NUM_PAGES = 3;
    
    // Onboarding page data
    private final OnboardingPageData[] pages = {
        new OnboardingPageData(
            "Welcome to Talkifyy",
            "Connect with friends and family through secure, fast messaging",
            "💬"
        ),
        new OnboardingPageData(
            "Stay Connected",
            "Real-time messaging with Instagram-style notifications and beautiful chat interface",
            "🔔"
        ),
        new OnboardingPageData(
            "Get Started Today",
            "Join thousands of users enjoying seamless communication. Your conversations, your way!",
            "🚀"
        )
    };
    
    public OnboardingPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }
    
    @NonNull
    @Override
    public Fragment createFragment(int position) {
        OnboardingPageData pageData = pages[position];
        return OnboardingPageFragment.newInstance(
            pageData.title,
            pageData.description,
            pageData.emoji
        );
    }
    
    @Override
    public int getItemCount() {
        return NUM_PAGES;
    }
    
    // Inner class to hold page data
    public static class OnboardingPageData {
        public final String title;
        public final String description;
        public final String emoji;
        
        public OnboardingPageData(String title, String description, String emoji) {
            this.title = title;
            this.description = description;
            this.emoji = emoji;
        }
    }
}