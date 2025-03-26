package com.oopgroup.smartpharmacy.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.adapters.CategoryListAdapter;
import com.oopgroup.smartpharmacy.models.Category;

import java.util.ArrayList;
import java.util.List;

public class NavCategoriesFragment extends Fragment implements CategoryListAdapter.OnCategoryClickListener {

    private static final String TAG = "NavCategoriesFragment";

    private RecyclerView categoriesRecyclerView;
    private ImageButton cartButton;
    private CategoryListAdapter categoryAdapter;
    private List<Category> categoryList;
    private CollectionReference categoriesRef;
    private FirebaseAuth mAuth;
    private ListenerRegistration categoriesListener;

    public NavCategoriesFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_nav_categories, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        categoriesRef = FirebaseFirestore.getInstance().collection("categories");

        // Initialize UI
        categoriesRecyclerView = view.findViewById(R.id.categoriesRecyclerView);
        cartButton = view.findViewById(R.id.cartButton);

        if (categoriesRecyclerView == null) {
            Log.e(TAG, "categoriesRecyclerView not found in layout");
            Toast.makeText(requireContext(), "Error: Categories view not found", Toast.LENGTH_LONG).show();
            return;
        }

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

        // Initialize list and adapter
        categoryList = new ArrayList<>();
        categoryAdapter = new CategoryListAdapter(requireContext(), categoryList, this);
        categoriesRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        categoriesRecyclerView.setAdapter(categoryAdapter);
        categoriesRecyclerView.setHasFixedSize(true);

        // Set up cart button click listener
        if (cartButton != null) {
            cartButton.setOnClickListener(v -> {
                Toast.makeText(requireContext(), "Cart clicked", Toast.LENGTH_SHORT).show();
                // Navigate to CartFragment here if needed
            });
        }

        // Fetch categories from Firestore
        fetchCategories();
    }

    private void fetchCategories() {
        categoriesListener = categoriesRef.addSnapshotListener((querySnapshot, error) -> {
            if (!isAdded()) return;

            if (error != null) {
                Log.e(TAG, "Failed to load categories: " + error.getMessage());
                Toast.makeText(requireContext(), "Failed to load categories: " + error.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }

            if (querySnapshot != null) {
                categoryList.clear();
                for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                    String id = doc.getId();
                    String name = doc.getString("name");
                    Long productCountLong = doc.getLong("productCount"); // Firestore stores numbers as Long
                    String imageUrl = doc.getString("imageUrl");

                    if (name != null && productCountLong != null && imageUrl != null) {
                        int productCount = productCountLong.intValue(); // Convert Long to int
                        Category category = new Category(id, name, productCount, imageUrl);
                        categoryList.add(category);
                    }
                }
                categoryAdapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public void onCategoryClick(Category category) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), "Category clicked: " + category.getName(), Toast.LENGTH_SHORT).show();
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (categoriesListener != null) {
            categoriesListener.remove(); // Remove Firestore listener
            categoriesListener = null;
        }
        if (categoriesRecyclerView != null) {
            categoriesRecyclerView.setAdapter(null);
            categoriesRecyclerView = null;
        }
        categoryAdapter = null;
        cartButton = null;
    }
}