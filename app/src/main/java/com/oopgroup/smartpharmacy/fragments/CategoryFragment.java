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
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.adapters.CategoryGridAdapter;
import com.oopgroup.smartpharmacy.models.Category;
import com.oopgroup.smartpharmacy.utils.LoadingSpinnerUtil;

import java.util.ArrayList;
import java.util.List;

public class CategoryFragment extends Fragment implements CategoryGridAdapter.OnCategoryClickListener {

    private RecyclerView categoriesRecyclerView;
    private TextView categoryTitle, viewAllCategories;
    private CategoryGridAdapter categoryAdapter;
    private List<Category> categoryList;
    private CollectionReference categoriesRef;
    private FirebaseAuth mAuth;
    private ListenerRegistration categoriesListener;
    private static final String TAG = "CategoryFragment";
    private LoadingSpinnerUtil loadingSpinnerUtil;
    private boolean isLoading = false;

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
        categoriesRef = FirebaseFirestore.getInstance().collection("categories");

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
        initializeUI(view);

        // Fetch categories
        fetchCategories();
    }

    private void initializeUI(View view) {
        categoryTitle = view.findViewById(R.id.categoryTitle);
        viewAllCategories = view.findViewById(R.id.viewAllCategories);
        categoriesRecyclerView = view.findViewById(R.id.categoriesRecyclerView);

        if (categoriesRecyclerView == null) {
            Log.e(TAG, "categoriesRecyclerView not found in layout");
            if (isAdded()) {
                Toast.makeText(requireContext(), "Error: Categories view not found", Toast.LENGTH_LONG).show();
            }
            return;
        }

        // Initialize LoadingSpinnerUtil
        loadingSpinnerUtil = LoadingSpinnerUtil.initialize(requireContext(), view, R.id.loadingSpinner);
        if (loadingSpinnerUtil == null) {
            Log.e(TAG, "Failed to initialize LoadingSpinnerUtil");
        }

        // Initialize list and adapter
        categoryList = new ArrayList<>();
        categoryAdapter = new CategoryGridAdapter(requireContext(), categoryList, this);

        // Calculate span count based on screen width
        int screenWidthDp = getResources().getConfiguration().screenWidthDp;
        int spanCount = screenWidthDp < 400 ? 2 : 3; // 2 columns for screens < 400dp, 3 for larger

        GridLayoutManager gridLayoutManager = new GridLayoutManager(requireContext(), spanCount);
        categoriesRecyclerView.setLayoutManager(gridLayoutManager);
        categoriesRecyclerView.setAdapter(categoryAdapter);
        categoriesRecyclerView.setHasFixedSize(true);

        // Set up "View All" click listener
        if (viewAllCategories != null) {
            viewAllCategories.setOnClickListener(v -> {
                if (isAdded() && !isLoading) {
                    requireActivity().getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.fragment_container, new NavCategoriesFragment())
                            .addToBackStack(null)
                            .commit();
                }
            });
        }
    }

    private void fetchCategories() {
        if (isLoading) return;
        isLoading = true;

        // Show loading spinner
        if (loadingSpinnerUtil != null) {
            loadingSpinnerUtil.toggleLoadingSpinner(true);
            disableUserInteractions(true);
        }

        categoriesListener = categoriesRef.addSnapshotListener((snapshot, error) -> {
            if (!isAdded()) return;

            if (error != null) {
                Log.e(TAG, "Failed to load categories: " + error.getMessage());
                if (isAdded()) {
                    Toast.makeText(requireContext(), "Failed to load categories: " + error.getMessage(), Toast.LENGTH_LONG).show();
                }
                checkLoadingComplete();
                return;
            }

            categoryList.clear();
            if (snapshot != null) {
                for (QueryDocumentSnapshot doc : snapshot) {  // Fixed: Iterate directly over QuerySnapshot
                    String id = doc.getId();
                    String name = doc.getString("name");
                    Long productCountLong = doc.getLong("productCount");
                    String imageUrl = doc.getString("imageUrl");

                    if (name != null && productCountLong != null && imageUrl != null) {
                        Category category = new Category(id, name, productCountLong.intValue(), imageUrl);
                        categoryList.add(category);
                    }
                }
                if (categoryAdapter != null) {
                    categoryAdapter.notifyDataSetChanged();
                }
            }
            checkLoadingComplete();
        });
    }

    private void checkLoadingComplete() {
        isLoading = false;
        if (loadingSpinnerUtil != null) {
            loadingSpinnerUtil.toggleLoadingSpinner(false);
        }
        disableUserInteractions(false);
    }

    private void disableUserInteractions(boolean disable) {
        if (categoriesRecyclerView != null) categoriesRecyclerView.setEnabled(!disable);
        if (viewAllCategories != null) viewAllCategories.setEnabled(!disable);
    }

    @Override
    public void onCategoryClick(Category category) {
        if (!isAdded() || isLoading) return;
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
        }
        categoryAdapter = null;
        categoriesRecyclerView = null;
        categoryTitle = null;
        viewAllCategories = null;
        loadingSpinnerUtil = null;
    }
}