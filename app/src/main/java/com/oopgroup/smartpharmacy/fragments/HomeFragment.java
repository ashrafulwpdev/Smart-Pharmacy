package com.oopgroup.smartpharmacy.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.widget.ViewPager2;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieComposition;
import com.airbnb.lottie.LottieCompositionFactory;
import com.airbnb.lottie.LottieDrawable;
import com.airbnb.lottie.LottieListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.adapters.BannerAdapter;
import com.oopgroup.smartpharmacy.adapters.BestsellerProductAdapter;
import com.oopgroup.smartpharmacy.adapters.CategoryGridAdapter;
import com.oopgroup.smartpharmacy.adapters.FeaturedAdapter;
import com.oopgroup.smartpharmacy.adapters.LabTestGridAdapter;
import com.oopgroup.smartpharmacy.models.Banner;
import com.oopgroup.smartpharmacy.models.Category;
import com.oopgroup.smartpharmacy.models.LabTest;
import com.oopgroup.smartpharmacy.models.Product;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment implements CategoryGridAdapter.OnCategoryClickListener,
        LabTestGridAdapter.OnLabTestClickListener, BestsellerProductAdapter.OnAddToCartClickListener,
        BannerAdapter.OnOrderNowClickListener, FeaturedAdapter.OnFeaturedClickListener {

    private static final String TAG = "HomeFragment";

    private RecyclerView categoriesRecyclerView, featuredRecyclerView, labTestsRecyclerView, bestsellerProductsRecyclerView;
    private ViewPager2 bannerViewPager;
    private EditText searchEditText;
    private ImageButton cartButton;
    private ImageView scanButton;
    private TextView viewAllCategories, viewAllLabTests, viewAllProducts;
    private LinearLayout indicatorLayout;
    private SwipeRefreshLayout swipeRefreshLayout;
    private LottieAnimationView loadingSpinner;
    private CategoryGridAdapter categoryAdapter;
    private LabTestGridAdapter labTestAdapter;
    private BestsellerProductAdapter bestsellerProductAdapter;
    private BannerAdapter bannerAdapter;
    private FeaturedAdapter featuredAdapter;
    private List<Category> categoryList;
    private List<LabTest> labTestList;
    private List<Product> bestsellerProductList;
    private List<Banner> bannerList;
    private List<Category> featuredList;
    private DatabaseReference bannersRef, categoriesRef, labTestsRef, productsRef;
    private FirebaseAuth mAuth;
    private ValueEventListener bannerListener, categoriesListener, labTestsListener, productsListener;
    private boolean isLoading = false;

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

        // Initialize UI first
        initializeUI(view);

        // Check authentication after UI setup
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

        // Initialize listeners and start listening
        initializeListeners();
        fetchData(true); // Initial fetch with loading spinner
    }

    private void initializeUI(View view) {
        // Find views with null checks
        bannerViewPager = view.findViewById(R.id.bannerViewPager);
        if (bannerViewPager == null) Log.e(TAG, "bannerViewPager not found");

        featuredRecyclerView = view.findViewById(R.id.featured_recycler_view);
        if (featuredRecyclerView == null) Log.e(TAG, "featured_recycler_view not found");

        categoriesRecyclerView = view.findViewById(R.id.categoriesRecyclerView);
        if (categoriesRecyclerView == null) Log.e(TAG, "categoriesRecyclerView not found");

        labTestsRecyclerView = view.findViewById(R.id.labTestsRecyclerView);
        if (labTestsRecyclerView == null) Log.e(TAG, "labTestsRecyclerView not found");

        bestsellerProductsRecyclerView = view.findViewById(R.id.productsRecyclerView);
        if (bestsellerProductsRecyclerView == null) Log.e(TAG, "productsRecyclerView not found");

        searchEditText = view.findViewById(R.id.searchEditText);
        cartButton = view.findViewById(R.id.cartButton);
        scanButton = view.findViewById(R.id.scanButton);
        viewAllCategories = view.findViewById(R.id.viewAllCategories);
        viewAllLabTests = view.findViewById(R.id.viewAllLabTests);
        viewAllProducts = view.findViewById(R.id.viewAllProducts);
        indicatorLayout = view.findViewById(R.id.indicatorLayout);
        if (indicatorLayout == null) Log.e(TAG, "indicatorLayout not found");

        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        if (swipeRefreshLayout == null) Log.e(TAG, "swipeRefreshLayout not found");

        loadingSpinner = view.findViewById(R.id.loadingSpinner);
        if (loadingSpinner == null) Log.e(TAG, "loadingSpinner not found");

        // Inside initializeUI() method
        if (loadingSpinner != null) {
            LottieCompositionFactory.fromRawRes(requireContext(), R.raw.loading_global)
                    .addListener(new LottieListener<LottieComposition>() {
                        @Override
                        public void onResult(LottieComposition composition) {
                            Log.d(TAG, "Lottie animation loaded successfully");
                            loadingSpinner.setComposition(composition);
                            // Use LottieDrawable.INFINITE instead of LottieAnimationView.INFINITE
                            loadingSpinner.setRepeatCount(LottieDrawable.INFINITE);
                            loadingSpinner.playAnimation();
                        }
                    })
                    .addFailureListener(throwable -> {
                        Log.e(TAG, "Failed to load Lottie animation: " + throwable.getMessage());
                        // Hide the loading spinner if the animation fails to load
                        loadingSpinner.setVisibility(View.GONE);
                        Toast.makeText(requireContext(), "Failed to load loading animation", Toast.LENGTH_SHORT).show();
                    });

        }

        // Initialize lists
        bannerList = new ArrayList<>();
        categoryList = new ArrayList<>();
        labTestList = new ArrayList<>();
        bestsellerProductList = new ArrayList<>();
        featuredList = new ArrayList<>();

        // Initialize featured list with static data
        featuredList.add(new Category("1", "Medicines", 0, String.valueOf(R.drawable.medicines)));
        featuredList.add(new Category("2", "Healthcare", 0, String.valueOf(R.drawable.healthcare)));
        featuredList.add(new Category("3", "Lab Test", 0, String.valueOf(R.drawable.labtest)));

        // Initialize adapters
        bannerAdapter = new BannerAdapter(requireContext(), bannerList, this);
        categoryAdapter = new CategoryGridAdapter(requireContext(), categoryList, this);
        labTestAdapter = new LabTestGridAdapter(requireContext(), labTestList, this);
        bestsellerProductAdapter = new BestsellerProductAdapter(requireContext(), bestsellerProductList, this);
        featuredAdapter = new FeaturedAdapter(requireContext(), featuredList, this);

        // Set up ViewPager2
        if (bannerViewPager != null) {
            bannerViewPager.setAdapter(bannerAdapter);
            bannerViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    updateIndicators(position);
                }
            });
        }

        // Set up Featured RecyclerView (horizontal)
        if (featuredRecyclerView != null) {
            LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false);
            featuredRecyclerView.setLayoutManager(layoutManager);
            featuredRecyclerView.setAdapter(featuredAdapter);
            featuredRecyclerView.setHasFixedSize(true);
            featuredAdapter.notifyDataSetChanged(); // Ensure adapter is updated
        }

        // Set up Categories RecyclerView (dynamic columns)
        if (categoriesRecyclerView != null) {
            int screenWidthDp = getResources().getConfiguration().screenWidthDp;
            int spanCount = screenWidthDp < 400 ? 2 : 3; // 2 columns for screens < 400dp, 3 for larger
            GridLayoutManager gridLayoutManager = new GridLayoutManager(requireContext(), spanCount);
            categoriesRecyclerView.setLayoutManager(gridLayoutManager);
            categoriesRecyclerView.setAdapter(categoryAdapter);
            categoriesRecyclerView.setHasFixedSize(true);
        }

        // Set up Lab Tests RecyclerView (3 columns)
        if (labTestsRecyclerView != null) {
            labTestsRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 3));
            labTestsRecyclerView.setAdapter(labTestAdapter);
            labTestsRecyclerView.setHasFixedSize(true);
        }

        // Set up Products RecyclerView (2 columns)
        if (bestsellerProductsRecyclerView != null) {
            bestsellerProductsRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));
            bestsellerProductsRecyclerView.setAdapter(bestsellerProductAdapter);
            bestsellerProductsRecyclerView.setHasFixedSize(true);
        }

        // Set up SwipeRefreshLayout
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                if (!isLoading) {
                    fetchData(false); // Refresh data without showing loading spinner
                }
            });
        }

        // Set up click listeners with null checks
        if (cartButton != null) {
            cartButton.setOnClickListener(v -> onCartClick());
        }
        if (scanButton != null) {
            scanButton.setOnClickListener(v -> onScanClick());
        }
        if (viewAllCategories != null) {
            viewAllCategories.setOnClickListener(v -> onViewAllCategoriesClick());
        }
        if (viewAllLabTests != null) {
            viewAllLabTests.setOnClickListener(v -> onViewAllLabTestsClick());
        }
        if (viewAllProducts != null) {
            viewAllProducts.setOnClickListener(v -> onViewAllProductsClick());
        }
    }

    private void initializeListeners() {
        bannerListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                bannerList.clear();
                for (DataSnapshot bannerSnapshot : snapshot.getChildren()) {
                    Banner banner = bannerSnapshot.getValue(Banner.class);
                    if (banner != null) {
                        banner.setId(bannerSnapshot.getKey());
                        bannerList.add(banner);
                    }
                }
                bannerAdapter.notifyDataSetChanged();
                updateIndicators(0); // Update indicators when data changes
                checkLoadingComplete();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to fetch banners: " + error.getMessage());
                if (isAdded()) Toast.makeText(requireContext(), "Failed to load banners", Toast.LENGTH_SHORT).show();
                checkLoadingComplete();
            }
        };

        categoriesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                categoryList.clear();
                for (DataSnapshot categorySnapshot : snapshot.getChildren()) {
                    String id = categorySnapshot.getKey();
                    String name = categorySnapshot.child("name").getValue(String.class);
                    Integer productCount = categorySnapshot.child("productCount").getValue(Integer.class);
                    String imageUrl = categorySnapshot.child("imageUrl").getValue(String.class);

                    if (name != null && productCount != null && imageUrl != null) {
                        Category category = new Category(id, name, productCount, imageUrl);
                        categoryList.add(category);
                        Log.d(TAG, "Added category: " + name);
                    }
                }
                Log.d(TAG, "Total categories loaded: " + categoryList.size());
                categoryAdapter.notifyDataSetChanged();
                checkLoadingComplete();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to fetch categories: " + error.getMessage());
                if (isAdded()) Toast.makeText(requireContext(), "Failed to load categories", Toast.LENGTH_SHORT).show();
                checkLoadingComplete();
            }
        };

        labTestsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                labTestList.clear();
                for (DataSnapshot labTestSnapshot : snapshot.getChildren()) {
                    String id = labTestSnapshot.getKey();
                    String name = labTestSnapshot.child("name").getValue(String.class);
                    String imageUrl = labTestSnapshot.child("imageUrl").getValue(String.class);

                    if (name != null && imageUrl != null) {
                        LabTest labTest = new LabTest(id, name, imageUrl);
                        labTestList.add(labTest);
                    }
                }
                labTestAdapter.notifyDataSetChanged();
                checkLoadingComplete();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to fetch lab tests: " + error.getMessage());
                if (isAdded()) Toast.makeText(requireContext(), "Failed to load lab tests", Toast.LENGTH_SHORT).show();
                checkLoadingComplete();
            }
        };

        productsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                bestsellerProductList.clear();
                for (DataSnapshot categorySnapshot : snapshot.getChildren()) {
                    for (DataSnapshot productSnapshot : categorySnapshot.getChildren()) {
                        Product product = productSnapshot.getValue(Product.class);
                        if (product != null) {
                            product.setId(productSnapshot.getKey());
                            bestsellerProductList.add(product);
                        }
                    }
                }
                bestsellerProductAdapter.notifyDataSetChanged();
                checkLoadingComplete();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to fetch products: " + error.getMessage());
                if (isAdded()) Toast.makeText(requireContext(), "Failed to load products", Toast.LENGTH_SHORT).show();
                checkLoadingComplete();
            }
        };
    }

    private void fetchData(boolean showLoadingSpinner) {
        if (isLoading) return;
        isLoading = true;

        if (showLoadingSpinner && loadingSpinner != null) {
            loadingSpinner.setVisibility(View.VISIBLE);
        }

        // Remove existing listeners to avoid duplicate callbacks
        stopListening();

        // Start listening for new data
        if (bannersRef != null && bannerListener != null) {
            bannersRef.addListenerForSingleValueEvent(bannerListener);
        }
        if (categoriesRef != null && categoriesListener != null) {
            categoriesRef.limitToFirst(6).addListenerForSingleValueEvent(categoriesListener);
        }
        if (labTestsRef != null && labTestsListener != null) {
            labTestsRef.limitToFirst(6).addListenerForSingleValueEvent(labTestsListener);
        }
        if (productsRef != null && productsListener != null) {
            productsRef.orderByChild("rating").limitToLast(4).addListenerForSingleValueEvent(productsListener);
        }
    }

    private void checkLoadingComplete() {
        // Check if all data has been loaded
        boolean bannersLoaded = !bannerList.isEmpty() || bannersRef == null;
        boolean categoriesLoaded = !categoryList.isEmpty() || categoriesRef == null;
        boolean labTestsLoaded = !labTestList.isEmpty() || labTestsRef == null;
        boolean productsLoaded = !bestsellerProductList.isEmpty() || productsRef == null;

        if (bannersLoaded && categoriesLoaded && labTestsLoaded && productsLoaded) {
            isLoading = false;
            if (loadingSpinner != null) {
                loadingSpinner.setVisibility(View.GONE);
            }
            if (swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(false);
            }
        }
    }

    private void stopListening() {
        if (bannersRef != null && bannerListener != null) bannersRef.removeEventListener(bannerListener);
        if (categoriesRef != null && categoriesListener != null) categoriesRef.removeEventListener(categoriesListener);
        if (labTestsRef != null && labTestsListener != null) labTestsRef.removeEventListener(labTestsListener);
        if (productsRef != null && productsListener != null) productsRef.removeEventListener(productsListener);
    }

    private void updateIndicators(int position) {
        if (!isAdded() || indicatorLayout == null) return;
        indicatorLayout.removeAllViews(); // Clear existing indicators

        // Dynamically create indicators based on banner count
        for (int i = 0; i < bannerList.size(); i++) {
            ImageView indicator = new ImageView(requireContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(8, 0, 8, 0); // Spacing between indicators
            indicator.setLayoutParams(params);
            indicator.setImageResource(i == position ? R.drawable.indicator_active : R.drawable.indicator_inactive);
            indicatorLayout.addView(indicator);
        }
    }

    private void onCartClick() {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), "Cart clicked", Toast.LENGTH_SHORT).show();
        // TODO: Navigate to CartFragment
    }

    private void onScanClick() {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), "Scan clicked", Toast.LENGTH_SHORT).show();
        // TODO: Implement scan functionality
    }

    private void onViewAllCategoriesClick() {
        if (!isAdded()) return;
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new CategoryFragment())
                .addToBackStack(null)
                .commit();
    }

    private void onViewAllLabTestsClick() {
        if (!isAdded()) return;
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new LabTestFragment())
                .addToBackStack(null)
                .commit();
    }

    private void onViewAllProductsClick() {
        if (!isAdded()) return;
        ProductsFragment productsFragment = new ProductsFragment();
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, productsFragment)
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onOrderNowClick(Banner banner) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), "Order Now clicked for: " + banner.getTitle(), Toast.LENGTH_SHORT).show();
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
    public void onLabTestClick(LabTest labTest) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), "Lab Test clicked: " + labTest.getName(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onAddToCartClick(Product product) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), "Added to cart: " + product.getName(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onFeaturedClick(Category category) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), "Featured clicked: " + category.getName(), Toast.LENGTH_SHORT).show();
        if ("Lab Test".equals(category.getName())) {
            onViewAllLabTestsClick();
        } else {
            onCategoryClick(category);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopListening();
        if (bannerViewPager != null) bannerViewPager.setAdapter(null);
        if (featuredRecyclerView != null) featuredRecyclerView.setAdapter(null);
        if (categoriesRecyclerView != null) categoriesRecyclerView.setAdapter(null);
        if (labTestsRecyclerView != null) labTestsRecyclerView.setAdapter(null);
        if (bestsellerProductsRecyclerView != null) bestsellerProductsRecyclerView.setAdapter(null);
        bannerAdapter = null;
        categoryAdapter = null;
        labTestAdapter = null;
        bestsellerProductAdapter = null;
        featuredAdapter = null;
        bannerViewPager = null;
        featuredRecyclerView = null;
        categoriesRecyclerView = null;
        labTestsRecyclerView = null;
        bestsellerProductsRecyclerView = null;
        searchEditText = null;
        cartButton = null;
        scanButton = null;
        viewAllCategories = null;
        viewAllLabTests = null;
        viewAllProducts = null;
        indicatorLayout = null;
        swipeRefreshLayout = null;
        loadingSpinner = null;
    }
}