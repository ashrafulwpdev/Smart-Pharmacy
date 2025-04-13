package com.oopgroup.smartpharmacy.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
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
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Source;
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
import com.oopgroup.smartpharmacy.utils.NetworkUtils;
import com.softourtech.slt.SLTLoader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeFragment extends Fragment implements CategoryGridAdapter.OnCategoryClickListener,
        LabTestGridAdapter.OnLabTestClickListener, BestsellerProductAdapter.OnAddToCartClickListener,
        FeaturedAdapter.OnFeaturedClickListener, BestsellerProductAdapter.OnProductClickListener {

    private static final String TAG = "HomeFragment";
    private static final int AUTO_SCROLL_INTERVAL = 10000;
    private static final long SEARCH_DELAY = 500;
    private static final int MIN_SEARCH_LENGTH = 2;
    private static final long TOAST_COOLDOWN = 2000; // 2 seconds

    private RecyclerView categoriesRecyclerView, featuredRecyclerView, labTestsRecyclerView, bestsellerProductsRecyclerView, allProductsRecyclerView;
    private ViewPager2 bannerViewPager;
    private EditText searchEditText;
    private ImageButton cartButton;
    private ImageView scanButton, searchIcon, clearSearchButton;
    private TextView viewAllCategories, viewAllLabTests, viewAllProducts, viewAllProductsFull;
    private LinearLayout indicatorLayout, searchBarContainer, allProductsTitleContainer;
    private SwipeRefreshLayout swipeRefreshLayout;
    private CategoryGridAdapter categoryAdapter;
    private LabTestGridAdapter labTestAdapter;
    private BestsellerProductAdapter bestsellerProductAdapter, allProductsAdapter;
    private BannerAdapter bannerAdapter;
    private FeaturedAdapter featuredAdapter;
    private List<Category> categoryList, featuredList;
    private List<LabTest> labTestList;
    private List<Product> bestsellerProductList, allProductList;
    private List<Banner> bannerList;
    private CollectionReference bannersRef, categoriesRef, labTestsRef, productsRef, cartItemsRef;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private ListenerRegistration bannerListener, categoriesListener, labTestsListener, productsListener, allProductsListener, cartListener;
    private boolean isLoading = false;
    private Handler autoScrollHandler, searchHandler;
    private Runnable autoScrollRunnable, searchRunnable;
    private boolean isUserInteracting = false;
    private SLTLoader sltLoader;
    private boolean bannersLoaded, categoriesLoaded, labTestsLoaded, productsLoaded, allProductsLoaded;
    private boolean isReturningFromSearch = false;
    private long lastToastTime = 0;

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
        Log.d(TAG, "onViewCreated called");

        FrameLayout loaderContainer = view.findViewById(R.id.loader_container);
        if (loaderContainer == null) {
            Log.e(TAG, "loader_container not found in layout");
            return;
        }
        sltLoader = new SLTLoader(requireActivity(), loaderContainer);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        bannersRef = db.collection("banners");
        categoriesRef = db.collection("categories");
        labTestsRef = db.collection("labTests");
        productsRef = db.collection("products");
        cartItemsRef = db.collection("cart").document(mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : "anonymous").collection("items");

        // Check network status
        if (!NetworkUtils.isNetworkAvailable(requireContext())) {
            showToast("No internet. Please connect and try again.");
            navigateToNoInternetFragment();
            return;
        }

        if (mAuth.getCurrentUser() == null) {
            Log.d(TAG, "User is NOT logged in");
            if (isAdded()) {
                showToast("Please log in to view the home page.");
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new LoginFragment())
                        .commit();
            }
            return;
        } else {
            Log.d(TAG, "User is logged in with UID: " + mAuth.getCurrentUser().getUid());
        }

        initializeUI(view);
        setupBannerAnimation();
        initializeListeners();
        fetchData(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume called");
        if (isReturningFromSearch && searchEditText != null) {
            searchEditText.setText("");
            isReturningFromSearch = false;
        }
        updateCartButton();
    }

    private void initializeUI(View view) {
        bannerViewPager = view.findViewById(R.id.bannerViewPager);
        featuredRecyclerView = view.findViewById(R.id.featured_recycler_view);
        categoriesRecyclerView = view.findViewById(R.id.categoriesRecyclerView);
        labTestsRecyclerView = view.findViewById(R.id.labTestsRecyclerView);
        bestsellerProductsRecyclerView = view.findViewById(R.id.productsRecyclerView);
        allProductsRecyclerView = view.findViewById(R.id.allProductsRecyclerView);
        searchEditText = view.findViewById(R.id.searchEditText);
        searchIcon = view.findViewById(R.id.searchIcon);
        clearSearchButton = view.findViewById(R.id.clearSearchButton);
        cartButton = view.findViewById(R.id.cartButton);
        scanButton = view.findViewById(R.id.scanButton);
        viewAllCategories = view.findViewById(R.id.viewAllCategories);
        viewAllLabTests = view.findViewById(R.id.viewAllLabTests);
        viewAllProducts = view.findViewById(R.id.viewAllProducts);
        viewAllProductsFull = view.findViewById(R.id.viewAllProductsFull);
        indicatorLayout = view.findViewById(R.id.indicatorLayout);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        searchBarContainer = view.findViewById(R.id.search_bar_container);
        allProductsTitleContainer = view.findViewById(R.id.allProductsTitleContainer);

        bannerList = new ArrayList<>();
        categoryList = new ArrayList<>();
        labTestList = new ArrayList<>();
        bestsellerProductList = new ArrayList<>();
        allProductList = new ArrayList<>();
        featuredList = new ArrayList<>();

        // Initialize featuredList with new items
        featuredList.add(new Category("1", "Notification", 0, String.valueOf(R.drawable.default_category_image))); // Replace with ic_notification
        featuredList.add(new Category("2", "Our Team", 0, String.valueOf(R.drawable.default_category_image))); // Replace with ic_team
        featuredList.add(new Category("3", "All Orders", 0, String.valueOf(R.drawable.default_category_image))); // Replace with ic_orders
        featuredList.add(new Category("4", "Payments", 0, String.valueOf(R.drawable.default_category_image))); // Replace with ic_payments

        bannerAdapter = new BannerAdapter(requireContext(), bannerList);
        categoryAdapter = new CategoryGridAdapter(requireContext(), categoryList, this);
        labTestAdapter = new LabTestGridAdapter(requireContext(), labTestList, this);
        bestsellerProductAdapter = new BestsellerProductAdapter(requireContext(), bestsellerProductList, this, this);
        allProductsAdapter = new BestsellerProductAdapter(requireContext(), allProductList, this, this);
        featuredAdapter = new FeaturedAdapter(requireContext(), featuredList, this);

        if (bannerViewPager != null) {
            bannerViewPager.setAdapter(bannerAdapter);
        }
        if (featuredRecyclerView != null) {
            featuredRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
            featuredRecyclerView.setAdapter(featuredAdapter);
            featuredRecyclerView.setHasFixedSize(true);
        }
        if (categoriesRecyclerView != null) {
            int spanCount = getResources().getConfiguration().screenWidthDp < 400 ? 2 : 3;
            categoriesRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), spanCount));
            categoriesRecyclerView.setAdapter(categoryAdapter);
            categoriesRecyclerView.setHasFixedSize(true);
        }
        if (labTestsRecyclerView != null) {
            labTestsRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));
            labTestsRecyclerView.setAdapter(labTestAdapter);
            labTestsRecyclerView.setHasFixedSize(true);
        }
        if (bestsellerProductsRecyclerView != null) {
            bestsellerProductsRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));
            bestsellerProductsRecyclerView.setAdapter(bestsellerProductAdapter);
            bestsellerProductsRecyclerView.setHasFixedSize(true);
        }
        if (allProductsRecyclerView != null) {
            allProductsRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));
            allProductsRecyclerView.setAdapter(allProductsAdapter);
            allProductsRecyclerView.setHasFixedSize(true);
        }

        searchHandler = new Handler(Looper.getMainLooper());
        searchRunnable = () -> {
            if (!isAdded()) return;
            String query = searchEditText.getText().toString().trim();
            if (query.length() >= MIN_SEARCH_LENGTH) {
                navigateToSearchResults(query);
            }
        };

        if (searchEditText != null) {
            searchEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (clearSearchButton != null) {
                        clearSearchButton.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {
                    searchHandler.removeCallbacks(searchRunnable);
                    if (s.length() >= MIN_SEARCH_LENGTH) {
                        searchHandler.postDelayed(searchRunnable, SEARCH_DELAY);
                    }
                }
            });

            searchEditText.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    String query = searchEditText.getText().toString().trim();
                    if (query.length() >= MIN_SEARCH_LENGTH) {
                        if (!NetworkUtils.isNetworkAvailable(requireContext())) {
                            showToast("No internet. Please try again later.");
                            return true;
                        }
                        navigateToSearchResults(query);
                    } else {
                        showToast("Please enter at least " + MIN_SEARCH_LENGTH + " characters");
                    }
                    searchEditText.clearFocus();
                    return true;
                }
                return false;
            });

            searchEditText.setOnFocusChangeListener((v, hasFocus) -> {
                if (searchBarContainer != null) {
                    searchBarContainer.setBackgroundResource(hasFocus ?
                            R.drawable.search_background_focused : R.drawable.search_background);
                }
            });
        }

        if (clearSearchButton != null) {
            clearSearchButton.setOnClickListener(v -> {
                if (searchEditText != null) {
                    searchEditText.setText("");
                    fetchDefaultProducts();
                }
            });
        }

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                if (!isLoading && NetworkUtils.isNetworkAvailable(requireContext())) {
                    refreshData();
                } else {
                    swipeRefreshLayout.setRefreshing(false);
                    showToast(isLoading ? "Loading in progress" : "No internet connection");
                }
            });
        }

        if (cartButton != null) {
            cartButton.setOnClickListener(v -> {
                if (!NetworkUtils.isNetworkAvailable(requireContext())) {
                    showToast("No internet. Please try again later.");
                    return;
                }
                onCartClick();
            });
        }
        if (scanButton != null) {
            scanButton.setOnClickListener(v -> {
                if (!NetworkUtils.isNetworkAvailable(requireContext())) {
                    showToast("No internet. Please try again later.");
                    return;
                }
                onScanClick();
            });
        }
        if (viewAllCategories != null) {
            viewAllCategories.setOnClickListener(v -> {
                if (!NetworkUtils.isNetworkAvailable(requireContext())) {
                    showToast("No internet. Please try again later.");
                    return;
                }
                onViewAllCategoriesClick();
            });
        }
        if (viewAllLabTests != null) {
            viewAllLabTests.setOnClickListener(v -> {
                if (!NetworkUtils.isNetworkAvailable(requireContext())) {
                    showToast("No internet. Please try again later.");
                    return;
                }
                onViewAllLabTestsClick();
            });
        }
        if (viewAllProducts != null) {
            viewAllProducts.setOnClickListener(v -> {
                if (!NetworkUtils.isNetworkAvailable(requireContext())) {
                    showToast("No internet. Please try again later.");
                    return;
                }
                onViewAllProductsClick();
            });
        }
        if (viewAllProductsFull != null) {
            viewAllProductsFull.setOnClickListener(v -> {
                if (!NetworkUtils.isNetworkAvailable(requireContext())) {
                    showToast("No internet. Please try again later.");
                    return;
                }
                onViewAllProductsClick();
            });
        }
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
                if (isAdded()) {
                    updateIndicators(position);
                }
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
            if (!isAdded()) return;
            if (error != null) {
                Log.e(TAG, "Failed to fetch banners: " + error.getMessage());
                showToast("Failed to load banners");
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
                bannerAdapter.notifyDataSetChanged();
                updateIndicators(0);
            }
            bannersLoaded = true;
            checkLoadingComplete();
        });

        categoriesListener = categoriesRef.limit(6).addSnapshotListener((snapshot, error) -> {
            if (!isAdded()) return;
            if (error != null) {
                Log.e(TAG, "Failed to fetch categories: " + error.getMessage());
                showToast("Failed to load categories");
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
                        categoryList.add(new Category(id, name, productCount.intValue(), imageUrl));
                    }
                }
                categoryAdapter.notifyDataSetChanged();
            }
            categoriesLoaded = true;
            checkLoadingComplete();
        });

        labTestsListener = labTestsRef.limit(6).addSnapshotListener((snapshot, error) -> {
            if (!isAdded()) return;
            if (error != null) {
                Log.e(TAG, "Failed to fetch lab tests: " + error.getMessage());
                showToast("Failed to load lab tests");
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
                        labTestList.add(new LabTest(id, name, imageUrl));
                    }
                }
                labTestAdapter.notifyDataSetChanged();
            }
            labTestsLoaded = true;
            checkLoadingComplete();
        });

        productsListener = productsRef.orderBy("rating", Query.Direction.DESCENDING).limit(4).addSnapshotListener((snapshot, error) -> {
            if (!isAdded()) return;
            if (error != null) {
                Log.e(TAG, "Failed to fetch bestseller products: " + error.getMessage());
                showToast("Failed to load bestseller products");
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
                bestsellerProductAdapter.notifyDataSetChanged();
            }
            productsLoaded = true;
            checkLoadingComplete();
        });

        allProductsListener = productsRef.limit(6).addSnapshotListener((snapshot, error) -> {
            if (!isAdded()) return;
            if (error != null) {
                Log.e(TAG, "Failed to fetch all products: " + error.getMessage());
                showToast("Failed to load all products");
                allProductsLoaded = true;
                checkLoadingComplete();
                return;
            }
            allProductList.clear();
            if (snapshot != null) {
                for (QueryDocumentSnapshot doc : snapshot) {
                    Product product = doc.toObject(Product.class);
                    product.setId(doc.getId());
                    allProductList.add(product);
                }
                allProductsAdapter.notifyDataSetChanged();
            }
            allProductsLoaded = true;
            checkLoadingComplete();
        });

        if (mAuth.getCurrentUser() != null) {
            cartListener = cartItemsRef.addSnapshotListener((snapshot, error) -> {
                if (!isAdded()) return;
                if (error != null) {
                    Log.e(TAG, "Failed to fetch cart items: " + error.getMessage());
                    return;
                }
                updateCartButton();
            });
        }
    }

    private void fetchData(boolean showLoadingSpinner) {
        if (isLoading) return;
        isLoading = true;

        bannersLoaded = false;
        categoriesLoaded = false;
        labTestsLoaded = false;
        productsLoaded = false;
        allProductsLoaded = false;

        if (showLoadingSpinner && sltLoader != null && NetworkUtils.isNetworkAvailable(requireContext())) {
            showLoader();
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
        allProductsLoaded = false;

        stopListening();
        initializeListeners();

        if (sltLoader != null && NetworkUtils.isNetworkAvailable(requireContext())) {
            showLoader();
            disableUserInteractions(true);
        }
    }

    private void checkLoadingComplete() {
        if (!isAdded()) return;
        if (bannersLoaded && categoriesLoaded && labTestsLoaded && productsLoaded && allProductsLoaded) {
            isLoading = false;
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!isAdded()) return;
                if (sltLoader != null) hideLoader();
                if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                disableUserInteractions(false);
                if (!isUserInteracting && autoScrollHandler != null && autoScrollRunnable != null) {
                    autoScrollHandler.postDelayed(autoScrollRunnable, AUTO_SCROLL_INTERVAL);
                }
            }, 1000);
        }
    }

    private void disableUserInteractions(boolean disable) {
        if (!isAdded()) return;
        if (bannerViewPager != null) bannerViewPager.setUserInputEnabled(!disable);
        if (featuredRecyclerView != null) featuredRecyclerView.setEnabled(!disable);
        if (categoriesRecyclerView != null) categoriesRecyclerView.setEnabled(!disable);
        if (labTestsRecyclerView != null) labTestsRecyclerView.setEnabled(!disable);
        if (bestsellerProductsRecyclerView != null) bestsellerProductsRecyclerView.setEnabled(!disable);
        if (allProductsRecyclerView != null) allProductsRecyclerView.setEnabled(!disable);
        if (cartButton != null) cartButton.setEnabled(!disable);
        if (scanButton != null) scanButton.setEnabled(!disable);
        if (viewAllCategories != null) viewAllCategories.setEnabled(!disable);
        if (viewAllLabTests != null) viewAllLabTests.setEnabled(!disable);
        if (viewAllProducts != null) viewAllProducts.setEnabled(!disable);
        if (viewAllProductsFull != null) viewAllProductsFull.setEnabled(!disable);
        if (swipeRefreshLayout != null) swipeRefreshLayout.setEnabled(!disable);
    }

    private void stopListening() {
        if (bannerListener != null) {
            bannerListener.remove();
            bannerListener = null;
        }
        if (categoriesListener != null) {
            categoriesListener.remove();
            categoriesListener = null;
        }
        if (labTestsListener != null) {
            labTestsListener.remove();
            labTestsListener = null;
        }
        if (productsListener != null) {
            productsListener.remove();
            productsListener = null;
        }
        if (allProductsListener != null) {
            allProductsListener.remove();
            allProductsListener = null;
        }
        if (cartListener != null) {
            cartListener.remove();
            cartListener = null;
        }
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
            indicatorLayout.addView(indicator);
        }
    }

    private void fetchDefaultProducts() {
        if (!isAdded()) return;
        if (!NetworkUtils.isNetworkAvailable(requireContext())) {
            showToast("No internet. Showing cached products.");
            return;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            productsRef.orderBy("rating", Query.Direction.DESCENDING)
                    .limit(4)
                    .get(Source.CACHE)
                    .addOnSuccessListener(querySnapshot -> {
                        if (!isAdded()) return;
                        List<Product> tempBestsellers = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : querySnapshot) {
                            Product product = doc.toObject(Product.class);
                            product.setId(doc.getId());
                            tempBestsellers.add(product);
                        }
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (!isAdded()) return;
                            bestsellerProductList.clear();
                            bestsellerProductList.addAll(tempBestsellers);
                            bestsellerProductAdapter.notifyDataSetChanged();
                        });
                    })
                    .addOnFailureListener(e -> {
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (!isAdded()) return;
                            Log.e(TAG, "Failed to fetch default products: " + e.getMessage());
                            showToast("Failed to load products");
                        });
                    });

            productsRef.limit(6)
                    .get(Source.CACHE)
                    .addOnSuccessListener(querySnapshot -> {
                        if (!isAdded()) return;
                        List<Product> tempAllProducts = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : querySnapshot) {
                            Product product = doc.toObject(Product.class);
                            product.setId(doc.getId());
                            tempAllProducts.add(product);
                        }
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (!isAdded()) return;
                            allProductList.clear();
                            allProductList.addAll(tempAllProducts);
                            allProductsAdapter.notifyDataSetChanged();
                        });
                    })
                    .addOnFailureListener(e -> {
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (!isAdded()) return;
                            Log.e(TAG, "Failed to fetch default all products: " + e.getMessage());
                            showToast("Failed to load all products");
                        });
                    });
            executor.shutdown();
        });
    }

    private void updateCartButton() {
        if (!isAdded() || mAuth.getCurrentUser() == null || cartItemsRef == null) return;
        cartItemsRef.get(Source.CACHE).addOnSuccessListener(querySnapshot -> {
            if (!isAdded()) return;
            int cartSize = querySnapshot.size();
            if (cartButton != null) {
                cartButton.setContentDescription("Cart (" + cartSize + " items)");
            }
        }).addOnFailureListener(e -> {
            if (!isAdded()) return;
            Log.e(TAG, "Failed to fetch cart size: " + e.getMessage());
        });
    }

    private void navigateToSearchResults(String query) {
        if (!isAdded() || isLoading) return;
        SearchResultsFragment searchResultsFragment = new SearchResultsFragment();
        Bundle args = new Bundle();
        args.putString("query", query);
        searchResultsFragment.setArguments(args);
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, searchResultsFragment)
                .addToBackStack("HomeFragment")
                .commit();
        isReturningFromSearch = true;
    }

    private void navigateToNoInternetFragment() {
        if (!isAdded()) return;
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new NoInternetFragment())
                .addToBackStack(null)
                .commit();
    }

    private void onCartClick() {
        if (!isAdded() || isLoading) return;
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new CartFragment())
                .addToBackStack(null)
                .commit();
    }

    private void onScanClick() {
        if (!isAdded() || isLoading) return;
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
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new ProductsFragment())
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onCategoryClick(Category category) {
        if (!isAdded() || isLoading) return;
        if (!NetworkUtils.isNetworkAvailable(requireContext())) {
            showToast("No internet. Please try again later.");
            return;
        }
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
        if (!NetworkUtils.isNetworkAvailable(requireContext())) {
            showToast("No internet. Please try again later.");
            return;
        }
        LabTestDetailsFragment labTestDetailsFragment = LabTestDetailsFragment.newInstance(labTest.getId());
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, labTestDetailsFragment)
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onAddToCartClick(Product product) {
        if (!isAdded() || isLoading || mAuth.getCurrentUser() == null) return;
        if (!NetworkUtils.isNetworkAvailable(requireContext())) {
            showToast("No internet. Please try again later.");
            return;
        }
        addToCart(product);
    }

    private void addToCart(Product product) {
        if (!isAdded()) return;
        String userId = mAuth.getCurrentUser().getUid();
        double price = product.getDiscountedPrice() > 0 ? product.getDiscountedPrice() : product.getOriginalPrice();
        double discountPercentage = product.getDiscountedPrice() > 0 ?
                ((product.getOriginalPrice() - product.getDiscountedPrice()) / product.getOriginalPrice()) * 100 : 0;

        DocumentReference cartItemRef = db.collection("cart")
                .document(userId)
                .collection("items")
                .document(product.getId());

        cartItemRef.get().addOnSuccessListener(documentSnapshot -> {
            if (!isAdded()) return;
            if (documentSnapshot.exists()) {
                int currentQuantity = documentSnapshot.getLong("quantity").intValue();
                double currentTotal = documentSnapshot.getDouble("total");

                cartItemRef.update(
                        "quantity", currentQuantity + 1,
                        "total", currentTotal + price,
                        "addedAt", com.google.firebase.Timestamp.now()
                ).addOnSuccessListener(aVoid -> {
                    if (!isAdded()) return;
                    showToast(product.getName() + " quantity updated in cart!");
                    updateCartButton();
                }).addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Log.e(TAG, "Failed to update cart item: " + e.getMessage());
                    showToast("Failed to update cart");
                });
            } else {
                Map<String, Object> cartItem = new HashMap<>();
                cartItem.put("productId", product.getId());
                cartItem.put("productName", product.getName());
                cartItem.put("imageUrl", product.getImageUrl());
                cartItem.put("quantity", 1);
                cartItem.put("total", price);
                cartItem.put("originalPrice", product.getOriginalPrice());
                cartItem.put("discountedPrice", product.getDiscountedPrice());
                cartItem.put("discountPercentage", discountPercentage);
                cartItem.put("addedAt", com.google.firebase.Timestamp.now());

                cartItemRef.set(cartItem).addOnSuccessListener(aVoid -> {
                    if (!isAdded()) return;
                    showToast(product.getName() + " added to cart!");
                    updateCartButton();
                }).addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Log.e(TAG, "Failed to add to cart: " + e.getMessage());
                    showToast("Failed to add to cart");
                });
            }
        }).addOnFailureListener(e -> {
            if (!isAdded()) return;
            Log.e(TAG, "Failed to check cart: " + e.getMessage());
            showToast("Error checking cart");
        });
    }

    @Override
    public void onProductClick(Product product) {
        if (!isAdded() || isLoading) return;
        if (!NetworkUtils.isNetworkAvailable(requireContext())) {
            showToast("No internet. Please try again later.");
            return;
        }
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
        if (!NetworkUtils.isNetworkAvailable(requireContext())) {
            showToast("No internet. Please try again later.");
            return;
        }
        switch (category.getName()) {
            case "Notification":
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new NotificationFragment())
                        .addToBackStack(null)
                        .commit();
                break;
            case "Our Team":
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new TeamFragment())
                        .addToBackStack(null)
                        .commit();
                break;
            case "All Orders":
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new OrderFragment())
                        .addToBackStack(null)
                        .commit();
                break;
            case "Payments":
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new PaymentsFragment())
                        .addToBackStack(null)
                        .commit();
                break;
            default:
                showToast("Clicked: " + category.getName());
        }
    }

    private void showLoader() {
        if (!isAdded() || sltLoader == null) return;
        sltLoader.showCustomLoader(new SLTLoader.LoaderConfig(com.softourtech.slt.R.raw.loading_global)
                .setWidthDp(40)
                .setHeightDp(40)
                .setUseRoundedBox(false)
                .setChangeJsonColor(false)
                .setOverlayColor(Color.TRANSPARENT));
        requireView().findViewById(R.id.loader_container).setVisibility(View.VISIBLE);
    }

    private void hideLoader() {
        if (!isAdded() || sltLoader == null) return;
        sltLoader.hideLoader();
        requireView().findViewById(R.id.loader_container).setVisibility(View.GONE);
    }

    private void showToast(String message) {
        if (!isAdded()) return;
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastToastTime >= TOAST_COOLDOWN) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            lastToastTime = currentTime;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "onDestroyView called");
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
        if (allProductsRecyclerView != null) allProductsRecyclerView.setAdapter(null);
        bannerAdapter = null;
        categoryAdapter = null;
        labTestAdapter = null;
        bestsellerProductAdapter = null;
        allProductsAdapter = null;
        featuredAdapter = null;
        bannerViewPager = null;
        featuredRecyclerView = null;
        categoriesRecyclerView = null;
        labTestsRecyclerView = null;
        bestsellerProductsRecyclerView = null;
        allProductsRecyclerView = null;
        searchEditText = null;
        cartButton = null;
        scanButton = null;
        viewAllCategories = null;
        viewAllLabTests = null;
        viewAllProducts = null;
        viewAllProductsFull = null;
        indicatorLayout = null;
        swipeRefreshLayout = null;
        searchBarContainer = null;
        allProductsTitleContainer = null;

        if (sltLoader != null) {
            sltLoader.onDestroy();
            sltLoader = null;
        }
    }
}