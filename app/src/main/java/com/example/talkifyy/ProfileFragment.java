package com.example.talkifyy;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.talkifyy.model.UserModel;
import com.example.talkifyy.utils.AndroidUtil;
import com.example.talkifyy.utils.FirebaseUtil;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;


public class ProfileFragment extends Fragment {

    TextView usernameText, phoneText;
    Button logoutBtn;
    ProgressBar progressBar;
    UserModel currentUserModel;

    public ProfileFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        
        usernameText = view.findViewById(R.id.profile_username);
        phoneText = view.findViewById(R.id.profile_phone);
        logoutBtn = view.findViewById(R.id.logout_btn);
        progressBar = view.findViewById(R.id.profile_progress_bar);
        
        logoutBtn.setOnClickListener((v) -> {
            FirebaseUtil.logout();
            Intent intent = new Intent(getContext(), SplashActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
        
        getUserData();
        
        return view;
    }
    
    void getUserData() {
        setInProgress(true);
        
        FirebaseUtil.currentUserDetails().get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                setInProgress(false);
                if(task.isSuccessful()) {
                    currentUserModel = task.getResult().toObject(UserModel.class);
                    if(currentUserModel != null) {
                        usernameText.setText(currentUserModel.getUsername());
                        phoneText.setText(currentUserModel.getPhone());
                    }
                } else {
                    AndroidUtil.showToast(getContext(), "Failed to load profile data");
                }
            }
        });
    }
    
    void setInProgress(boolean inProgress) {
        if(inProgress) {
            progressBar.setVisibility(View.VISIBLE);
        } else {
            progressBar.setVisibility(View.GONE);
        }
    }
}
