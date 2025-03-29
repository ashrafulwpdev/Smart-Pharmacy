package com.oopgroup.smartpharmacy.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.adapters.CategoryListAdapter;
import com.oopgroup.smartpharmacy.models.Category;

import java.util.ArrayList;
import java.util.List;

public class NavCategoriesFragment extends Fragment implements CategoryListAdapter.OnCategoryClickListener {

    private static final String TAG = "NavCategoriesFragment";

    private RecyclerView categoriesRecyclerView;
    private Toolbar toolbar;
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
        toolbar = view.findViewById(R.id.toolbar);
        categoriesRecyclerView = view.findViewById(R.id.categoriesRecyclerView);

        if (categoriesRecyclerView == null) {
            Log.e(TAG, "categoriesRecyclerView not found in layout");
            return;
        }

        // Set up Toolbar
        toolbar.setTitle("All Categories");
        toolbar.inflateMenu(R.menu.menu_products);  // Inflate the menu with search and cart
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_search) {
                // Add search functionality here if needed
                return true;
            } else if (item.getItemId() == R.id.action_cart) {
                navigateToCartFragment();
                return true;
            }
            return false;
        });

        // Check if user is authenticated
        if (mAuth.getCurrentUser() == null) {
            if (isAdded()) {
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

        // Fetch categories from Firestore
        fetchCategories();
    }

    private void fetchCategories() {
        categoriesListener = categoriesRef.addSnapshotListener((querySnapshot, error) -> {
            if (!isAdded()) return;

            if (error != null) {
                Log.e(TAG, "Failed to load categories: " + error.getMessage());
                return;
            }

            if (querySnapshot != null) {
                categoryList.clear();
                for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                    String id = doc.getId();
                    String name = doc.getString("name");
                    Long productCountLong = doc.getLong("productCount");
                    String imageUrl = doc.getString("imageUrl");

                    if (name != null && productCountLong != null && imageUrl != null) {
                        int productCount = productCountLong.intValue();
                        Category category = new Category(id, name, productCount, imageUrl);
                        categoryList.add(category);
                    }
                }
                categoryAdapter.notifyDataSetChanged();
            }
        });
    }

    private void navigateToCartFragment() {
        if (!isAdded()) return;

        // Navigate to CartFragment
        CartFragment cartFragment = new CartFragment();
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, cartFragment)
                .addToBackStack(null) // Allows returning to NavCategoriesFragment
                .commit();
    }

    @Override
    public void onCategoryClick(Category category) {
        if (!isAdded()) return;

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
            categoriesListener.remove();
            categoriesListener = null;
        }
        if (categoriesRecyclerView != null) {
            categoriesRecyclerView.setAdapter(null);
            categoriesRecyclerView = null;
        }
        categoryAdapter = null;
        toolbar = null;
    }
}