package com.example.talkifyy;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.talkifyy.adapter.SearchUserRecyclerAdapter;
import com.example.talkifyy.model.UserModel;
import com.example.talkifyy.utils.FirebaseUtil;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.firebase.firestore.Query;

public class SearchUserFragment extends Fragment {

    RecyclerView recyclerView;
    SearchUserRecyclerAdapter adapter;

    public SearchUserFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_search_user, container, false);
        
        recyclerView = view.findViewById(R.id.search_user_recycler_view);
        
        // Set up default view or empty state
        setupRecyclerView("");
        
        return view;
    }

    public void setupRecyclerView(String searchTerm) {
        if (searchTerm.isEmpty()) {
            // Show empty state or all users
            return;
        }

        Query query = FirebaseUtil.allUserCollectionReference()
                .whereGreaterThanOrEqualTo("username", searchTerm)
                .whereLessThanOrEqualTo("username", searchTerm + '\uf8ff');

        FirestoreRecyclerOptions<UserModel> options = new FirestoreRecyclerOptions.Builder<UserModel>()
                .setQuery(query, UserModel.class).build();

        adapter = new SearchUserRecyclerAdapter(options, getContext());
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
        adapter.startListening();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (adapter != null)
            adapter.startListening();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (adapter != null)
            adapter.stopListening();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null)
            adapter.startListening();
    }
}
