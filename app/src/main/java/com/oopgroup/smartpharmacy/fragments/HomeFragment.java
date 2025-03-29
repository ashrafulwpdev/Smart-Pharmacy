package com.oopgroup.smartpharmacy.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
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
import com.oopgroup.smartpharmacy.utils.LoadingSpinnerUtil;

import android.text.Editable;
import android.text.TextWatcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HomeFragment extends Fragment implements CategoryGridAdapter.OnCategoryClickListener,
        LabTestGridAdapter.OnLabTestClickListener, BestsellerProductAdapter.OnAddToCartClickListener,
        BannerAdapter.OnOrderNowClickListener, FeaturedAdapter.OnFeaturedClickListener,
        BestsellerProductAdapter.OnProductClickListener {

    private static final String TAG = "HomeFragment";
    private static final int AUTO_SCROLL_INTERVAL = 10000; // 10 seconds
    private static final long SEARCH_DELAY = 500; // 500ms delay for debouncing search

    private RecyclerView categoriesRecyclerView, featuredRecyclerView, labTestsRecyclerView, bestsellerProductsRecyclerView;
    private ViewPager2 bannerViewPager;
    private EditText searchEditText;
    private ImageButton cartButton;
    private ImageView scanButton;
    private TextView viewAllCategories, viewAllLabTests, viewAllProducts;
    private LinearLayout indicatorLayout;
    private SwipeRefreshLayout swipeRefreshLayout;
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
    private CollectionReference bannersRef, categoriesRef, labTestsRef, productsRef;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private ListenerRegistration bannerListener, categoriesListener, labTestsListener, productsListener;
    private boolean isLoading = false;
    private Handler autoScrollHandler, searchHandler;
    private Runnable autoScrollRunnable, searchRunnable;
    private boolean isUserInteracting = false;
    private LoadingSpinnerUtil loadingSpinnerUtil;
    private boolean bannersLoaded, categoriesLoaded, labTestsLoaded, productsLoaded;

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
        db = FirebaseFirestore.getInstance();
        bannersRef = db.collection("banners");
        categoriesRef = db.collection("categories");
        labTestsRef = db.collection("labTests");
        productsRef = db.collection("products");

        // Initialize UI first
        initializeUI(view);

        // Check authentication after UI setup
        if (mAuth.getCurrentUser() == null) {
            Log.d(TAG, "User is NOT logged in");
            if (isAdded()) {
                Toast.makeText(requireContext(), "Please log in to view the home page.", Toast.LENGTH_LONG).show();
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new LoginFragment())
                        .commit();
            }
            return;
        } else {
            Log.d(TAG, "User is logged in with UID: " + mAuth.getCurrentUser().getUid());
        }

        // Setup banner animation and start listening
        setupBannerAnimation();
        initializeListeners();
        fetchData(true); // Initial fetch with loading spinner
    }

    private void initializeUI(View view) {
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

        loadingSpinnerUtil = LoadingSpinnerUtil.initialize(requireContext(), view, R.id.loadingSpinner);
        if (loadingSpinnerUtil == null) Log.e(TAG, "Failed to initialize LoadingSpinnerUtil");

        bannerList = new ArrayList<>();
        categoryList = new ArrayList<>();
        labTestList = new ArrayList<>();
        bestsellerProductList = new ArrayList<>();
        featuredList = new ArrayList<>();

        featuredList.add(new Category("1", "Medicines", 0, String.valueOf(R.drawable.medicines)));
        featuredList.add(new Category("2", "Healthcare", 0, String.valueOf(R.drawable.healthcare)));
        featuredList.add(new Category("3", "Lab Test", 0, String.valueOf(R.drawable.labtest)));

        bannerAdapter = new BannerAdapter(requireContext(), bannerList, this);
        categoryAdapter = new CategoryGridAdapter(requireContext(), categoryList, this);
        labTestAdapter = new LabTestGridAdapter(requireContext(), labTestList, this);
        bestsellerProductAdapter = new BestsellerProductAdapter(requireContext(), bestsellerProductList, this, this);
        featuredAdapter = new FeaturedAdapter(requireContext(), featuredList, this);

        if (bannerViewPager != null) {
            bannerViewPager.setAdapter(bannerAdapter);
        }

        if (featuredRecyclerView != null) {
            LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false);
            featuredRecyclerView.setLayoutManager(layoutManager);
            featuredRecyclerView.setAdapter(featuredAdapter);
            featuredRecyclerView.setHasFixedSize(true);
            featuredAdapter.notifyDataSetChanged();
        }

        if (categoriesRecyclerView != null) {
            int screenWidthDp = getResources().getConfiguration().screenWidthDp;
            int spanCount = screenWidthDp < 400 ? 2 : 3;
            GridLayoutManager gridLayoutManager = new GridLayoutManager(requireContext(), spanCount);
            categoriesRecyclerView.setLayoutManager(gridLayoutManager);
            categoriesRecyclerView.setAdapter(categoryAdapter);
            categoriesRecyclerView.setHasFixedSize(true);
        }

        if (labTestsRecyclerView != null) {
            labTestsRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 3));
            labTestsRecyclerView.setAdapter(labTestAdapter);
            labTestsRecyclerView.setHasFixedSize(true);
        }

        if (bestsellerProductsRecyclerView != null) {
            bestsellerProductsRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));
            bestsellerProductsRecyclerView.setAdapter(bestsellerProductAdapter);
            bestsellerProductsRecyclerView.setHasFixedSize(true);
        }

        // Initialize search handler
        searchHandler = new Handler(Looper.getMainLooper());
        searchRunnable = () -> {
            String query = searchEditText.getText().toString().trim();
            performFirestoreSearch(query);
        };

        if (searchEditText != null) {
            searchEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    searchHandler.removeCallbacks(searchRunnable);
                    searchHandler.postDelayed(searchRunnable, SEARCH_DELAY);
                }
            });
        }

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                if (!isLoading) {
                    refreshData();
                } else {
                    swipeRefreshLayout.setRefreshing(false);
                }
            });
        }

        if (cartButton != null) cartButton.setOnClickListener(v -> onCartClick());
        if (scanButton != null) scanButton.setOnClickListener(v -> onScanClick());
        if (viewAllCategories != null) viewAllCategories.setOnClickListener(v -> onViewAllCategoriesClick());
        if (viewAllLabTests != null) viewAllLabTests.setOnClickListener(v -> onViewAllLabTestsClick());
        if (viewAllProducts != null) viewAllProducts.setOnClickListener(v -> onViewAllProductsClick());
    }

    private void setupBannerAnimation() {
        if (bannerViewPager == null) return;

        autoScrollHandler = new Handler(Looper.getMainLooper());
        autoScrollRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isAdded() || bannerList.isEmpty() || isUserInteracting || isLoading) {
                    autoScrollHandler.postDelayed(this, AUTO_SCROLL_INTERVAL);
                    return;
                }

                int currentItem = bannerViewPager.getCurrentItem();
                int totalItems = bannerList.size();
                int nextItem = (currentItem + 1) % totalItems;

                bannerViewPager.setCurrentItem(nextItem, true);
                autoScrollHandler.postDelayed(this, AUTO_SCROLL_INTERVAL);
            }
        };

        autoScrollHandler.postDelayed(autoScrollRunnable, AUTO_SCROLL_INTERVAL);

        bannerViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateIndicators(position);
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                    isUserInteracting = true;
                    autoScrollHandler.removeCallbacks(autoScrollRunnable);
                } else if (state == ViewPager2.SCROLL_STATE_IDLE && !isLoading) {
                    isUserInteracting = false;
                    autoScrollHandler.removeCallbacks(autoScrollRunnable);
                    autoScrollHandler.postDelayed(autoScrollRunnable, AUTO_SCROLL_INTERVAL);
                }
            }
        });
    }

    private void initializeListeners() {
        bannerListener = bannersRef.addSnapshotListener((snapshot, error) -> {
            Log.d(TAG, "Fetching banners from Firestore");
            if (!isAdded()) return;
            if (error != null) {
                Log.e(TAG, "Failed to fetch banners: " + error.getMessage());
                if (isAdded()) Toast.makeText(requireContext(), "Failed to load banners", Toast.LENGTH_SHORT).show();
                bannersLoaded = true;
                checkLoadingComplete();
                return;
            }
            bannerList.clear();
            if (snapshot != null) {
                for (QueryDocumentSnapshot doc : snapshot) {
                    Banner banner = doc.toObject(Banner.class);
                    banner.setId(doc.getId());
                    bannerList.add(banner);
                }
                if (bannerAdapter != null) {
                    bannerAdapter.updateBanners(bannerList);
                    Log.d(TAG, "Banners loaded: " + bannerList.size());
                }
                updateIndicators(0);
            }
            bannersLoaded = true;
            checkLoadingComplete();
        });

        categoriesListener = categoriesRef.limit(6).addSnapshotListener((snapshot, error) -> {
            Log.d(TAG, "Fetching categories from Firestore");
            if (!isAdded()) return;
            if (error != null) {
                Log.e(TAG, "Failed to fetch categories: " + error.getMessage());
                if (isAdded()) Toast.makeText(requireContext(), "Failed to load categories", Toast.LENGTH_SHORT).show();
                categoriesLoaded = true;
                checkLoadingComplete();
                return;
            }
            categoryList.clear();
            if (snapshot != null) {
                for (QueryDocumentSnapshot doc : snapshot) {
                    String id = doc.getId();
                    String name = doc.getString("name");
                    Long productCount = doc.getLong("productCount");
                    String imageUrl = doc.getString("imageUrl");
                    if (name != null && productCount != null && imageUrl != null) {
                        Category category = new Category(id, name, productCount.intValue(), imageUrl);
                        categoryList.add(category);
                        Log.d(TAG, "Added category: " + name);
                    }
                }
                Log.d(TAG, "Total categories loaded: " + categoryList.size());
                if (categoryAdapter != null) categoryAdapter.notifyDataSetChanged();
            }
            categoriesLoaded = true;
            checkLoadingComplete();
        });

        labTestsListener = labTestsRef.limit(6).addSnapshotListener((snapshot, error) -> {
            Log.d(TAG, "Fetching lab tests from Firestore");
            if (!isAdded()) return;
            if (error != null) {
                Log.e(TAG, "Failed to fetch lab tests: " + error.getMessage());
                if (isAdded()) Toast.makeText(requireContext(), "Failed to load lab tests", Toast.LENGTH_SHORT).show();
                labTestsLoaded = true;
                checkLoadingComplete();
                return;
            }
            labTestList.clear();
            if (snapshot != null) {
                for (QueryDocumentSnapshot doc : snapshot) {
                    String id = doc.getId();
                    String name = doc.getString("name");
                    String imageUrl = doc.getString("imageUrl");
                    if (name != null && imageUrl != null) {
                        LabTest labTest = new LabTest(id, name, imageUrl);
                        labTestList.add(labTest);
                    }
                }
                if (labTestAdapter != null) labTestAdapter.notifyDataSetChanged();
            }
            labTestsLoaded = true;
            checkLoadingComplete();
        });

        productsListener = productsRef.orderBy("rating", Query.Direction.DESCENDING).limit(4).addSnapshotListener((snapshot, error) -> {
            Log.d(TAG, "Fetching products from Firestore");
            if (!isAdded()) return;
            if (error != null) {
                Log.e(TAG, "Failed to fetch products: " + error.getMessage());
                if (isAdded()) Toast.makeText(requireContext(), "Failed to load products", Toast.LENGTH_SHORT).show();
                productsLoaded = true;
                checkLoadingComplete();
                return;
            }
            bestsellerProductList.clear();
            if (snapshot != null) {
                for (QueryDocumentSnapshot doc : snapshot) {
                    Product product = doc.toObject(Product.class);
                    product.setId(doc.getId());
                    bestsellerProductList.add(product);
                }
                if (bestsellerProductAdapter != null) bestsellerProductAdapter.notifyDataSetChanged();
            }
            productsLoaded = true;
            checkLoadingComplete();
        });
    }

    private void fetchData(boolean showLoadingSpinner) {
        if (isLoading) return;
        isLoading = true;

        bannersLoaded = false;
        categoriesLoaded = false;
        labTestsLoaded = false;
        productsLoaded = false;

        if (showLoadingSpinner && loadingSpinnerUtil != null) {
            loadingSpinnerUtil.toggleLoadingSpinner(true);
            disableUserInteractions(true);
        }
    }

    private void refreshData() {
        if (isLoading) return;
        isLoading = true;

        bannersLoaded = false;
        categoriesLoaded = false;
        labTestsLoaded = false;
        productsLoaded = false;

        stopListening();
        initializeListeners();

        if (loadingSpinnerUtil != null) {
            loadingSpinnerUtil.toggleLoadingSpinner(true);
            disableUserInteractions(true);
        }
    }

    private void checkLoadingComplete() {
        if (bannersLoaded && categoriesLoaded && labTestsLoaded && productsLoaded) {
            isLoading = false;
            if (loadingSpinnerUtil != null) {
                loadingSpinnerUtil.toggleLoadingSpinner(false);
            }
            if (swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(false);
            }
            disableUserInteractions(false);
            if (!isUserInteracting && autoScrollHandler != null && autoScrollRunnable != null) {
                autoScrollHandler.postDelayed(autoScrollRunnable, AUTO_SCROLL_INTERVAL);
            }
            Log.d(TAG, "Loading complete: All data fetched");
        }
    }

    private void disableUserInteractions(boolean disable) {
        if (bannerViewPager != null) bannerViewPager.setUserInputEnabled(!disable);
        if (featuredRecyclerView != null) featuredRecyclerView.setEnabled(!disable);
        if (categoriesRecyclerView != null) categoriesRecyclerView.setEnabled(!disable);
        if (labTestsRecyclerView != null) labTestsRecyclerView.setEnabled(!disable);
        if (bestsellerProductsRecyclerView != null) bestsellerProductsRecyclerView.setEnabled(!disable);
        if (cartButton != null) cartButton.setEnabled(!disable);
        if (scanButton != null) scanButton.setEnabled(!disable);
        if (viewAllCategories != null) viewAllCategories.setEnabled(!disable);
        if (viewAllLabTests != null) viewAllLabTests.setEnabled(!disable);
        if (viewAllProducts != null) viewAllProducts.setEnabled(!disable);
        if (swipeRefreshLayout != null) swipeRefreshLayout.setEnabled(!disable);
    }

    private void stopListening() {
        if (bannerListener != null) { bannerListener.remove(); bannerListener = null; }
        if (categoriesListener != null) { categoriesListener.remove(); categoriesListener = null; }
        if (labTestsListener != null) { labTestsListener.remove(); labTestsListener = null; }
        if (productsListener != null) { productsListener.remove(); productsListener = null; }
    }

    private void updateIndicators(int position) {
        if (!isAdded() || indicatorLayout == null) return;

        indicatorLayout.removeAllViews();

        for (int i = 0; i < bannerList.size(); i++) {
            ImageView indicator = new ImageView(requireContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(12, 0, 12, 0);
            indicator.setLayoutParams(params);
            indicator.setImageResource(i == position ? R.drawable.indicator_active : R.drawable.indicator_inactive);

            if (i == position) {
                indicator.setScaleX(1.0f);
                indicator.setScaleY(1.0f);
                indicator.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start();
            } else {
                indicator.setScaleX(1.0f);
                indicator.setScaleY(1.0f);
                indicator.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
            }

            indicatorLayout.addView(indicator);
        }
    }

    private void performFirestoreSearch(String query) {
        if (!isAdded() || bestsellerProductAdapter == null) return;

        if (loadingSpinnerUtil != null) {
            loadingSpinnerUtil.toggleLoadingSpinner(true);
        }
        disableUserInteractions(true);

        if (query.isEmpty()) {
            fetchDefaultProducts();
            return;
        }

        productsRef
                .orderBy("nameLower")
                .startAt(query.toLowerCase())
                .endAt(query.toLowerCase() + "\uf8ff")
                .limit(10)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!isAdded()) return;

                    bestsellerProductList.clear();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Product product = doc.toObject(Product.class);
                        product.setId(doc.getId());
                        bestsellerProductList.add(product);
                    }
                    bestsellerProductAdapter.notifyDataSetChanged();

                    if (bestsellerProductList.isEmpty()) {
                        Toast.makeText(requireContext(), "No products found for \"" + query + "\"", Toast.LENGTH_SHORT).show();
                    }

                    if (loadingSpinnerUtil != null) {
                        loadingSpinnerUtil.toggleLoadingSpinner(false);
                    }
                    disableUserInteractions(false);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Log.e(TAG, "Search failed: " + e.getMessage());
                    Toast.makeText(requireContext(), "Failed to search products: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    if (loadingSpinnerUtil != null) {
                        loadingSpinnerUtil.toggleLoadingSpinner(false);
                    }
                    disableUserInteractions(false);
                });
    }

    private void fetchDefaultProducts() {
        if (!isAdded()) return;

        if (loadingSpinnerUtil != null) {
            loadingSpinnerUtil.toggleLoadingSpinner(true);
        }
        disableUserInteractions(true);

        productsRef.orderBy("rating", Query.Direction.DESCENDING)
                .limit(4)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!isAdded()) return;

                    bestsellerProductList.clear();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Product product = doc.toObject(Product.class);
                        product.setId(doc.getId());
                        bestsellerProductList.add(product);
                    }
                    bestsellerProductAdapter.notifyDataSetChanged();

                    if (loadingSpinnerUtil != null) {
                        loadingSpinnerUtil.toggleLoadingSpinner(false);
                    }
                    disableUserInteractions(false);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Log.e(TAG, "Failed to fetch default products: " + e.getMessage());
                    Toast.makeText(requireContext(), "Failed to load products", Toast.LENGTH_SHORT).show();
                    if (loadingSpinnerUtil != null) {
                        loadingSpinnerUtil.toggleLoadingSpinner(false);
                    }
                    disableUserInteractions(false);
                });
    }

    private void onCartClick() {
        if (!isAdded() || isLoading) return;

        // Navigate to CartFragment
        CartFragment cartFragment = new CartFragment();
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, cartFragment)
                .addToBackStack(null) // Allows user to return to HomeFragment
                .commit();
    }

    private void onScanClick() {
        if (!isAdded() || isLoading) return;
        Toast.makeText(requireContext(), "Scan clicked", Toast.LENGTH_SHORT).show();
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new ScannerFragment())
                .addToBackStack(null)
                .commit();
    }

    private void onViewAllCategoriesClick() {
        if (!isAdded() || isLoading) return;
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new CategoryFragment())
                .addToBackStack(null)
                .commit();
    }

    private void onViewAllLabTestsClick() {
        if (!isAdded() || isLoading) return;
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new LabTestFragment())
                .addToBackStack(null)
                .commit();
    }

    private void onViewAllProductsClick() {
        if (!isAdded() || isLoading) return;
        ProductsFragment productsFragment = new ProductsFragment();
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, productsFragment)
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onOrderNowClick(Banner banner) {
        if (!isAdded() || isLoading) return;
        Toast.makeText(requireContext(), "Order Now clicked for: " + banner.getTitle(), Toast.LENGTH_SHORT).show();
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
    public void onLabTestClick(LabTest labTest) {
        if (!isAdded() || isLoading) return;
        Toast.makeText(requireContext(), "Lab Test clicked: " + labTest.getName(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onAddToCartClick(Product product) {
        if (!isAdded() || isLoading) return;

        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(requireContext(), "Please log in to add items to cart.", Toast.LENGTH_SHORT).show();
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new LoginFragment())
                    .addToBackStack(null)
                    .commit();
            return;
        }

        String userId = mAuth.getCurrentUser().getUid();
        double price = (product.getDiscountedPrice() > 0.0 && product.getDiscountedPrice() < product.getPrice())
                ? product.getDiscountedPrice()
                : product.getPrice();

        if (price <= 0.0) {
            Log.e(TAG, "Invalid price for product: " + product.getName());
            Toast.makeText(requireContext(), "Cannot add " + product.getName() + " to cart: Invalid price", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("cart")
                .document(userId)
                .collection("items")
                .whereEqualTo("productId", product.getId())
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!isAdded()) return;

                    if (!querySnapshot.isEmpty()) {
                        // Item exists, update quantity
                        QueryDocumentSnapshot existingItem = (QueryDocumentSnapshot) querySnapshot.getDocuments().get(0);
                        int currentQuantity = existingItem.getLong("quantity").intValue();
                        double currentTotal = existingItem.getDouble("total");

                        db.collection("cart")
                                .document(userId)
                                .collection("items")
                                .document(existingItem.getId())
                                .update(
                                        "quantity", currentQuantity + 1,
                                        "total", currentTotal + price,
                                        "addedAt", com.google.firebase.Timestamp.now()
                                )
                                .addOnSuccessListener(aVoid -> {
                                    if (isAdded()) {
                                        Toast.makeText(requireContext(), product.getName() + " quantity updated in cart!", Toast.LENGTH_SHORT).show();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    if (isAdded()) {
                                        Log.e(TAG, "Failed to update cart: " + e.getMessage());
                                        Toast.makeText(requireContext(), "Failed to update cart: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                    }
                                });
                    } else {
                        // New item, add it with full details
                        Map<String, Object> cartItem = new HashMap<>();
                        cartItem.put("productId", product.getId());
                        cartItem.put("productName", product.getName());
                        cartItem.put("imageUrl", product.getImageUrl());
                        cartItem.put("quantity", 1);
                        cartItem.put("total", price);
                        cartItem.put("originalPrice", product.getPrice());
                        cartItem.put("discountedPrice", product.getDiscountedPrice());
                        cartItem.put("discountPercentage", product.getDiscountedPrice() > 0 ? ((product.getPrice() - product.getDiscountedPrice()) / product.getPrice() * 100) : 0);
                        cartItem.put("addedAt", com.google.firebase.Timestamp.now());

                        db.collection("cart")
                                .document(userId)
                                .collection("items")
                                .add(cartItem)
                                .addOnSuccessListener(documentReference -> {
                                    if (isAdded()) {
                                        Toast.makeText(requireContext(), product.getName() + " added to cart!", Toast.LENGTH_SHORT).show();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    if (isAdded()) {
                                        Log.e(TAG, "Failed to add to cart: " + e.getMessage());
                                        Toast.makeText(requireContext(), "Failed to add to cart: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        Log.e(TAG, "Failed to check cart: " + e.getMessage());
                        Toast.makeText(requireContext(), "Failed to add to cart: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    @Override
    public void onProductClick(Product product) {
        if (!isAdded() || isLoading) return;

        Bundle args = new Bundle();
        args.putParcelable("product", product);
        ProductDetailsFragment productDetailsFragment = new ProductDetailsFragment();
        productDetailsFragment.setArguments(args);

        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, productDetailsFragment)
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onFeaturedClick(Category category) {
        if (!isAdded() || isLoading) return;
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
        if (autoScrollHandler != null && autoScrollRunnable != null) {
            autoScrollHandler.removeCallbacks(autoScrollRunnable);
        }
        if (searchHandler != null && searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
        }
        autoScrollHandler = null;
        autoScrollRunnable = null;
        searchHandler = null;
        searchRunnable = null;

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
        if (loadingSpinnerUtil != null) {
            loadingSpinnerUtil.cleanup();
            loadingSpinnerUtil = null;
        }
    }
}