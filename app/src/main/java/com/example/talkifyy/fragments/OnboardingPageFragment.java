package com.example.talkifyy.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.talkifyy.R;

public class OnboardingPageFragment extends Fragment {
    
    private static final String ARG_TITLE = "title";
    private static final String ARG_DESCRIPTION = "description";
    private static final String ARG_EMOJI = "emoji";
    
    private String title;
    private String description;
    private String emoji;
    
    public static OnboardingPageFragment newInstance(String title, String description, String emoji) {
        OnboardingPageFragment fragment = new OnboardingPageFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putString(ARG_DESCRIPTION, description);
        args.putString(ARG_EMOJI, emoji);
        fragment.setArguments(args);
        return fragment;
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            title = getArguments().getString(ARG_TITLE);
            description = getArguments().getString(ARG_DESCRIPTION);
            emoji = getArguments().getString(ARG_EMOJI);
        }
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_onboarding_page, container, false);
        
        TextView titleText = view.findViewById(R.id.titleText);
        TextView descriptionText = view.findViewById(R.id.descriptionText);
        TextView emojiText = view.findViewById(R.id.emojiText);
        
        titleText.setText(title);
        descriptionText.setText(description);
        emojiText.setText(emoji);
        
        return view;
    }
}