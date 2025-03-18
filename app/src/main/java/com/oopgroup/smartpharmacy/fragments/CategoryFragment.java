package com.oopgroup.smartpharmacy.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.adapters.CategoryGridAdapter;
import com.oopgroup.smartpharmacy.models.Category;

import java.util.ArrayList;
import java.util.List;

public class CategoryFragment extends Fragment implements CategoryGridAdapter.OnCategoryClickListener {

    private RecyclerView categoriesRecyclerView;
    private TextView categoryTitle;
    private CategoryGridAdapter categoryAdapter;
    private List<Category> categoryList;
    private DatabaseReference categoriesRef;
    private FirebaseAuth mAuth;
    private ValueEventListener categoriesListener; // Store the listener as a field
    private static final String TAG = "CategoryFragment";

    public CategoryFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_category, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        categoriesRef = FirebaseDatabase.getInstance().getReference("categories");

        // Check if user is authenticated
        if (mAuth.getCurrentUser() == null) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "Please log in to view categories.", Toast.LENGTH_LONG).show();
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new LoginFragment())
                        .commit();
            }
            return;
        }

        // Initialize UI
        categoryTitle = view.findViewById(R.id.categoryTitle);
        categoriesRecyclerView = view.findViewById(R.id.categoriesRecyclerView);
        categoriesRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 3));

        // Initialize list and adapter
        categoryList = new ArrayList<>();
        categoryAdapter = new CategoryGridAdapter(requireContext(), categoryList, this);
        categoriesRecyclerView.setAdapter(categoryAdapter);

        // Fetch categories
        fetchCategories();
    }

    private void fetchCategories() {
        categoriesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isAdded()) {
                    categoryList.clear();
                    for (DataSnapshot categorySnapshot : snapshot.getChildren()) {
                        String id = categorySnapshot.getKey();
                        String name = categorySnapshot.child("name").getValue(String.class);
                        Integer productCount = categorySnapshot.child("productCount").getValue(Integer.class);
                        String imageUrl = categorySnapshot.child("imageUrl").getValue(String.class);

                        if (name != null && productCount != null && imageUrl != null) {
                            Category category = new Category(id, name, productCount, imageUrl);
                            categoryList.add(category);
                        }
                    }
                    categoryAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to fetch categories: " + error.getMessage());
                if (isAdded()) {
                    Toast.makeText(requireContext(), "Failed to load categories: " + error.getMessage(), Toast.LENGTH_LONG).show();
                } else {
                    Log.e(TAG, "Categories listener cancelled, fragment detached");
                }
            }
        };
        if (categoriesRef != null && categoriesListener != null) {
            categoriesRef.addValueEventListener(categoriesListener);
        }
    }

    @Override
    public void onCategoryClick(Category category) {
        if (isAdded()) {
            Bundle args = new Bundle();
            args.putString("categoryId", category.getId());
            args.putString("categoryName", category.getName());
            ProductsFragment productsFragment = new ProductsFragment();
            productsFragment.setArguments(args);
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, productsFragment)
                    .addToBackStack(null)
                    .commit();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Remove the listener to prevent callbacks after detachment
        if (categoriesRef != null && categoriesListener != null) {
            categoriesRef.removeEventListener(categoriesListener);
        }
        // Nullify references to prevent memory leaks
        categoriesRef = null;
        categoriesListener = null;
    }
}