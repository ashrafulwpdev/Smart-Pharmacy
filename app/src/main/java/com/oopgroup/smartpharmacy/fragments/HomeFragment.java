package com.oopgroup.smartpharmacy.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
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
import com.softourtech.slt.SLTLoader;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.text.Editable;
import android.text.TextWatcher;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment implements CategoryGridAdapter.OnCategoryClickListener,
        LabTestGridAdapter.OnLabTestClickListener, BestsellerProductAdapter.OnAddToCartClickListener,
        BannerAdapter.OnOrderNowClickListener, FeaturedAdapter.OnFeaturedClickListener,
        BestsellerProductAdapter.OnProductClickListener {

    private static final String TAG = "HomeFragment";
    private static final int AUTO_SCROLL_INTERVAL = 10000;
    private static final long SEARCH_DELAY = 500; // Delay for search trigger
    private static final int MIN_SEARCH_LENGTH = 2; // Minimum characters to trigger search

    private RecyclerView categoriesRecyclerView, featuredRecyclerView, labTestsRecyclerView, bestsellerProductsRecyclerView;
    private ViewPager2 bannerViewPager;
    private EditText searchEditText;
    private ImageButton cartButton;
    private ImageView scanButton, searchIcon, clearSearchButton;
    private TextView viewAllCategories, viewAllLabTests, viewAllProducts;
    private LinearLayout indicatorLayout, searchBarContainer;
    private SwipeRefreshLayout swipeRefreshLayout;
    private CategoryGridAdapter categoryAdapter;
    private LabTestGridAdapter labTestAdapter;
    private BestsellerProductAdapter bestsellerProductAdapter;
    private BannerAdapter bannerAdapter;
    private FeaturedAdapter featuredAdapter;
    private List<Category> categoryList, featuredList;
    private List<LabTest> labTestList;
    private List<Product> bestsellerProductList;
    private List<Banner> bannerList;
    private CollectionReference bannersRef, categoriesRef, labTestsRef, productsRef;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private ListenerRegistration bannerListener, categoriesListener, labTestsListener, productsListener;
    private boolean isLoading = false;
    private Handler autoScrollHandler, searchHandler;
    private Runnable autoScrollRunnable, searchRunnable;
    private boolean isUserInteracting = false;
    private SLTLoader sltLoader;
    private boolean bannersLoaded, categoriesLoaded, labTestsLoaded, productsLoaded;
    private boolean isReturningFromSearch = false; // Flag to prevent re-trigger on back

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

        initializeUI(view);

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

        setupBannerAnimation();
        initializeListeners();
        fetchData(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isReturningFromSearch) {
            searchEditText.setText(""); // Clear search text when returning
            isReturningFromSearch = false;
        }
    }

    private void initializeUI(View view) {
        bannerViewPager = view.findViewById(R.id.bannerViewPager);
        featuredRecyclerView = view.findViewById(R.id.featured_recycler_view);
        categoriesRecyclerView = view.findViewById(R.id.categoriesRecyclerView);
        labTestsRecyclerView = view.findViewById(R.id.labTestsRecyclerView);
        bestsellerProductsRecyclerView = view.findViewById(R.id.productsRecyclerView);
        searchEditText = view.findViewById(R.id.searchEditText);
        searchIcon = view.findViewById(R.id.searchIcon);
        clearSearchButton = view.findViewById(R.id.clearSearchButton);
        cartButton = view.findViewById(R.id.cartButton);
        scanButton = view.findViewById(R.id.scanButton);
        viewAllCategories = view.findViewById(R.id.viewAllCategories);
        viewAllLabTests = view.findViewById(R.id.viewAllLabTests);
        viewAllProducts = view.findViewById(R.id.viewAllProducts);
        indicatorLayout = view.findViewById(R.id.indicatorLayout);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        searchBarContainer = view.findViewById(R.id.search_bar_container);

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

        if (bannerViewPager != null) bannerViewPager.setAdapter(bannerAdapter);
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
            labTestsRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 3));
            labTestsRecyclerView.setAdapter(labTestAdapter);
            labTestsRecyclerView.setHasFixedSize(true);
        }
        if (bestsellerProductsRecyclerView != null) {
            bestsellerProductsRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));
            bestsellerProductsRecyclerView.setAdapter(bestsellerProductAdapter);
            bestsellerProductsRecyclerView.setHasFixedSize(true);
        }

        searchHandler = new Handler(Looper.getMainLooper());
        searchRunnable = () -> {
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
                    clearSearchButton.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                    animateSearchIcon(searchIcon, s.length() > 0);
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
                        navigateToSearchResults(query);
                    } else {
                        Toast.makeText(requireContext(), "Please enter at least " + MIN_SEARCH_LENGTH + " characters", Toast.LENGTH_SHORT).show();
                    }
                    searchEditText.clearFocus();
                    return true;
                }
                return false;
            });

            searchEditText.setOnFocusChangeListener((v, hasFocus) -> {
                searchBarContainer.setBackgroundResource(hasFocus ?
                        R.drawable.search_background_focused : R.drawable.search_background);
            });
        }

        if (clearSearchButton != null) {
            clearSearchButton.setOnClickListener(v -> {
                searchEditText.setText("");
                fetchDefaultProducts();
            });
        }

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                if (!isLoading) refreshData();
                else swipeRefreshLayout.setRefreshing(false);
            });
        }

        if (cartButton != null) cartButton.setOnClickListener(v -> onCartClick());
        if (scanButton != null) scanButton.setOnClickListener(v -> onScanClick());
        if (viewAllCategories != null) viewAllCategories.setOnClickListener(v -> onViewAllCategoriesClick());
        if (viewAllLabTests != null) viewAllLabTests.setOnClickListener(v -> onViewAllLabTestsClick());
        if (viewAllProducts != null) viewAllProducts.setOnClickListener(v -> onViewAllProductsClick());
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
        isReturningFromSearch = true; // Set flag for return handling
    }

    private void animateSearchIcon(ImageView searchIcon, boolean isActive) {
        if (searchIcon == null) return;
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(searchIcon, "scaleX", isActive ? 1.2f : 1.0f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(searchIcon, "scaleY", isActive ? 1.2f : 1.0f);
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(scaleX, scaleY);
        animatorSet.setDuration(200);
        animatorSet.start();
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
            if (!isAdded()) return;
            if (error != null) {
                Log.e(TAG, "Failed to fetch banners: " + error.getMessage());
                Toast.makeText(requireContext(), "Failed to load banners", Toast.LENGTH_SHORT).show();
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
                bannerAdapter.updateBanners(bannerList);
                updateIndicators(0);
            }
            bannersLoaded = true;
            checkLoadingComplete();
        });

        categoriesListener = categoriesRef.limit(6).addSnapshotListener((snapshot, error) -> {
            if (!isAdded()) return;
            if (error != null) {
                Log.e(TAG, "Failed to fetch categories: " + error.getMessage());
                Toast.makeText(requireContext(), "Failed to load categories", Toast.LENGTH_SHORT).show();
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
                Toast.makeText(requireContext(), "Failed to load lab tests", Toast.LENGTH_SHORT).show();
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
                Log.e(TAG, "Failed to fetch products: " + error.getMessage());
                Toast.makeText(requireContext(), "Failed to load products", Toast.LENGTH_SHORT).show();
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
    }

    private void fetchData(boolean showLoadingSpinner) {
        if (isLoading) return;
        isLoading = true;

        bannersLoaded = false;
        categoriesLoaded = false;
        labTestsLoaded = false;
        productsLoaded = false;

        if (showLoadingSpinner && sltLoader != null) {
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

        stopListening();
        initializeListeners();

        if (sltLoader != null) {
            showLoader();
            disableUserInteractions(true);
        }
    }

    private void checkLoadingComplete() {
        if (bannersLoaded && categoriesLoaded && labTestsLoaded && productsLoaded) {
            isLoading = false;
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
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
            indicatorLayout.addView(indicator);
        }
    }

    private void fetchDefaultProducts() {
        if (!isAdded()) return;
        showLoader();
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
                    hideLoader();
                    disableUserInteractions(false);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Log.e(TAG, "Failed to fetch default products: " + e.getMessage());
                    Toast.makeText(requireContext(), "Failed to load products", Toast.LENGTH_SHORT).show();
                    hideLoader();
                    disableUserInteractions(false);
                });
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
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new ProductsFragment())
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
        Toast.makeText(requireContext(), product.getName() + " added to cart!", Toast.LENGTH_SHORT).show();
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

    private void showLoader() {
        if (sltLoader != null) {
            sltLoader.showCustomLoader(new SLTLoader.LoaderConfig(com.softourtech.slt.R.raw.loading_global)
                    .setWidthDp(40)
                    .setHeightDp(40)
                    .setUseRoundedBox(false)
                    .setChangeJsonColor(false)
                    .setOverlayColor(Color.TRANSPARENT));
            requireView().findViewById(R.id.loader_container).setVisibility(View.VISIBLE);
        }
    }

    private void hideLoader() {
        if (sltLoader != null) {
            sltLoader.hideLoader();
            requireView().findViewById(R.id.loader_container).setVisibility(View.GONE);
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

        if (sltLoader != null) {
            sltLoader.onDestroy();
            sltLoader = null;
        }
    }
}