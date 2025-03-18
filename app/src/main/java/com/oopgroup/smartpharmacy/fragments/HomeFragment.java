package com.oopgroup.smartpharmacy.fragments;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.adapters.BestsellerProductAdapter;
import com.oopgroup.smartpharmacy.adapters.CategoryGridAdapter;
import com.oopgroup.smartpharmacy.adapters.LabTestGridAdapter;
import com.oopgroup.smartpharmacy.models.Banner;
import com.oopgroup.smartpharmacy.models.Category;
import com.oopgroup.smartpharmacy.models.LabTest;
import com.oopgroup.smartpharmacy.models.Product;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment implements CategoryGridAdapter.OnCategoryClickListener,
        LabTestGridAdapter.OnLabTestClickListener, BestsellerProductAdapter.OnAddToCartClickListener {

    private static final String TAG = "HomeFragment";

    private GridLayout categoryGrid, labTestsGrid;
    private RecyclerView bestsellerProductsRecyclerView;
    private TextView bannerTitle, bannerDescription, bannerDiscount, viewAllCategories, viewAllLabTests, viewAllProducts;
    private ImageView bannerImage;
    private Button orderNowButton;
    private CategoryGridAdapter categoryAdapter;
    private LabTestGridAdapter labTestAdapter;
    private BestsellerProductAdapter bestsellerProductAdapter;
    private List<Category> categoryList;
    private List<LabTest> labTestList;
    private List<Product> bestsellerProductList;
    private DatabaseReference bannersRef, categoriesRef, labTestsRef, productsRef;
    private FirebaseAuth mAuth;
    private ValueEventListener bannerListener, categoriesListener, labTestsListener, productsListener;

    public HomeFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        bannersRef = FirebaseDatabase.getInstance().getReference("banners");
        categoriesRef = FirebaseDatabase.getInstance().getReference("categories");
        labTestsRef = FirebaseDatabase.getInstance().getReference("labTests");
        productsRef = FirebaseDatabase.getInstance().getReference("products");

        // Check if user is authenticated
        if (mAuth.getCurrentUser() == null) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "Please log in to view the home page.", Toast.LENGTH_LONG).show();
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new LoginFragment())
                        .commit();
            }
            return;
        }

        // Initialize UI
        initializeUI(view);

        // Initialize listeners
        initializeListeners();

        // Start listening
        startListening();
    }

    private void initializeUI(View view) {
        bannerTitle = view.findViewById(R.id.bannerTitle);
        bannerDescription = view.findViewById(R.id.bannerDescription);
        bannerDiscount = view.findViewById(R.id.bannerDiscount);
        bannerImage = view.findViewById(R.id.bannerImage);
        orderNowButton = view.findViewById(R.id.orderNowButton);
        categoryGrid = view.findViewById(R.id.categoryGrid);
        labTestsGrid = view.findViewById(R.id.labTestsGrid);
        bestsellerProductsRecyclerView = view.findViewById(R.id.bestsellerProductsRecyclerView);
        viewAllCategories = view.findViewById(R.id.viewAllCategories);
        viewAllLabTests = view.findViewById(R.id.viewAllLabTests);
        viewAllProducts = view.findViewById(R.id.viewAllProducts);
        bestsellerProductsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false));

        // Initialize lists and adapters
        categoryList = new ArrayList<>();
        labTestList = new ArrayList<>();
        bestsellerProductList = new ArrayList<>();
        categoryAdapter = new CategoryGridAdapter(requireContext(), categoryList, this);
        labTestAdapter = new LabTestGridAdapter(requireContext(), labTestList, this);
        bestsellerProductAdapter = new BestsellerProductAdapter(requireContext(), bestsellerProductList, this);
        bestsellerProductsRecyclerView.setAdapter(bestsellerProductAdapter);

        // Set up click listeners
        orderNowButton.setOnClickListener(v -> onOrderNowClick());
        viewAllCategories.setOnClickListener(v -> onViewAllCategoriesClick());
        viewAllLabTests.setOnClickListener(v -> onViewAllLabTestsClick());
        viewAllProducts.setOnClickListener(v -> onViewAllProductsClick());
    }

    private void initializeListeners() {
        bannerListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isAdded() && snapshot.exists()) {
                    for (DataSnapshot bannerSnapshot : snapshot.getChildren()) {
                        updateBanner(bannerSnapshot);
                        break; // Only take the first banner
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to fetch banner: " + error.getMessage());
                if (isAdded()) {
                    Toast.makeText(requireContext(), "Failed to load banner", Toast.LENGTH_SHORT).show();
                }
            }
        };

        categoriesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isAdded()) {
                    categoryList.clear();
                    for (DataSnapshot categorySnapshot : snapshot.getChildren()) {
                        Category category = categorySnapshot.getValue(Category.class);
                        if (category != null) categoryList.add(category);
                    }
                    updateCategoryGrid();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to fetch categories: " + error.getMessage());
                if (isAdded()) {
                    Toast.makeText(requireContext(), "Failed to load categories", Toast.LENGTH_SHORT).show();
                } else {
                    Log.e(TAG, "Categories listener cancelled, fragment detached");
                }
            }
        };

        labTestsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isAdded()) {
                    labTestList.clear();
                    for (DataSnapshot labTestSnapshot : snapshot.getChildren()) {
                        LabTest labTest = labTestSnapshot.getValue(LabTest.class);
                        if (labTest != null) labTestList.add(labTest);
                    }
                    updateLabTestsGrid();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to fetch lab tests: " + error.getMessage());
                if (isAdded()) {
                    Toast.makeText(requireContext(), "Failed to load lab tests", Toast.LENGTH_SHORT).show();
                } else {
                    Log.e(TAG, "Lab tests listener cancelled, fragment detached");
                }
            }
        };

        productsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isAdded()) {
                    bestsellerProductList.clear();
                    for (DataSnapshot categorySnapshot : snapshot.getChildren()) {
                        for (DataSnapshot productSnapshot : categorySnapshot.getChildren()) {
                            Product product = productSnapshot.getValue(Product.class);
                            if (product != null) bestsellerProductList.add(product);
                        }
                    }
                    bestsellerProductAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to fetch products: " + error.getMessage());
                if (isAdded()) {
                    Toast.makeText(requireContext(), "Failed to load products", Toast.LENGTH_SHORT).show();
                } else {
                    Log.e(TAG, "Products listener cancelled, fragment detached");
                }
            }
        };
    }

    private void startListening() {
        if (bannersRef != null && bannerListener != null) {
            bannersRef.limitToFirst(1).addValueEventListener(bannerListener);
        }
        if (categoriesRef != null && categoriesListener != null) {
            categoriesRef.limitToFirst(6).addValueEventListener(categoriesListener);
        }
        if (labTestsRef != null && labTestsListener != null) {
            labTestsRef.limitToFirst(6).addValueEventListener(labTestsListener);
        }
        if (productsRef != null && productsListener != null) {
            productsRef.orderByChild("rating").limitToLast(4).addValueEventListener(productsListener);
        }
    }

    private void stopListening() {
        if (bannersRef != null && bannerListener != null) {
            bannersRef.removeEventListener(bannerListener);
        }
        if (categoriesRef != null && categoriesListener != null) {
            categoriesRef.removeEventListener(categoriesListener);
        }
        if (labTestsRef != null && labTestsListener != null) {
            labTestsRef.removeEventListener(labTestsListener);
        }
        if (productsRef != null && productsListener != null) {
            productsRef.removeEventListener(productsListener);
        }
    }

    private void updateBanner(DataSnapshot bannerSnapshot) {
        if (!isAdded()) return;
        String id = bannerSnapshot.getKey();
        String title = bannerSnapshot.child("title").getValue(String.class);
        String description = bannerSnapshot.child("description").getValue(String.class);
        String discount = bannerSnapshot.child("discount").getValue(String.class);
        String imageUrl = bannerSnapshot.child("imageUrl").getValue(String.class);

        if (title != null && description != null && discount != null && imageUrl != null) {
            bannerTitle.setText(title);
            bannerDescription.setText(description);
            bannerDiscount.setText(discount);
            Glide.with(this).load(imageUrl)
                    .placeholder(R.drawable.default_banner_image)
                    .error(R.drawable.default_banner_image)
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            Log.e(TAG, "Glide failed to load banner image: " + (e != null ? e.getMessage() : "Unknown error"));
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            return false;
                        }
                    })
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(bannerImage);
        }
    }

    private void updateCategoryGrid() {
        if (!isAdded()) return;
        categoryGrid.removeAllViews();
        for (int i = 0; i < categoryList.size() && i < 6; i++) {
            View view = LayoutInflater.from(requireContext()).inflate(R.layout.item_category_grid, categoryGrid, false);
            ImageView categoryImage = view.findViewById(R.id.categoryImage);
            TextView categoryName = view.findViewById(R.id.categoryName);
            Category category = categoryList.get(i);
            categoryName.setText(category.getName());
            Glide.with(this).load(category.getImageUrl())
                    .placeholder(R.drawable.default_category_image)
                    .error(R.drawable.default_category_image)
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            Log.e(TAG, "Glide failed to load category image: " + (e != null ? e.getMessage() : "Unknown error"));
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            return false;
                        }
                    })
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(categoryImage);
            view.setOnClickListener(v -> onCategoryClick(category));
            categoryGrid.addView(view);
        }
    }

    private void updateLabTestsGrid() {
        if (!isAdded()) return;
        labTestsGrid.removeAllViews();
        for (int i = 0; i < labTestList.size() && i < 6; i++) {
            View view = LayoutInflater.from(requireContext()).inflate(R.layout.item_lab_test_grid, labTestsGrid, false);
            ImageView labTestImage = view.findViewById(R.id.labTestImage);
            TextView labTestName = view.findViewById(R.id.labTestName);
            LabTest labTest = labTestList.get(i);
            labTestName.setText(labTest.getName());
            Glide.with(this).load(labTest.getImageUrl())
                    .placeholder(R.drawable.default_lab_test_image)
                    .error(R.drawable.default_lab_test_image)
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            Log.e(TAG, "Glide failed to load lab test image: " + (e != null ? e.getMessage() : "Unknown error"));
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            return false;
                        }
                    })
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(labTestImage);
            view.setOnClickListener(v -> onLabTestClick(labTest));
            labTestsGrid.addView(view);
        }
    }

    private void onOrderNowClick() {
        if (isAdded()) {
            Toast.makeText(requireContext(), "Order Now clicked", Toast.LENGTH_SHORT).show();
        }
    }

    private void onViewAllCategoriesClick() {
        if (isAdded()) {
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new CategoryFragment())
                    .addToBackStack(null)
                    .commit();
        }
    }

    private void onViewAllLabTestsClick() {
        if (isAdded()) {
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new LabTestFragment())
                    .addToBackStack(null)
                    .commit();
        }
    }

    private void onViewAllProductsClick() {
        if (isAdded()) {
            ProductsFragment productsFragment = new ProductsFragment();
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, productsFragment)
                    .addToBackStack(null)
                    .commit();
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
    public void onLabTestClick(LabTest labTest) {
        if (isAdded()) {
            Toast.makeText(requireContext(), "Lab Test clicked: " + labTest.getName(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onAddToCartClick(Product product) {
        if (isAdded()) {
            Toast.makeText(requireContext(), "Added to cart: " + product.getName(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopListening();
        // Nullify references to prevent memory leaks
        bannerListener = null;
        categoriesListener = null;
        labTestsListener = null;
        productsListener = null;
        bannersRef = null;
        categoriesRef = null;
        labTestsRef = null;
        productsRef = null;
    }
}