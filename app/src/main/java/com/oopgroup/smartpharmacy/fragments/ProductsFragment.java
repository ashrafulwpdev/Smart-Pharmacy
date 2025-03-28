package com.oopgroup.smartpharmacy.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.adapters.BestsellerProductAdapter;
import com.oopgroup.smartpharmacy.models.Category;
import com.oopgroup.smartpharmacy.models.Product;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProductsFragment extends Fragment implements BestsellerProductAdapter.OnAddToCartClickListener,
        BestsellerProductAdapter.OnProductClickListener, FilterDialogFragment.OnFilterAppliedListener {
    private static final String TAG = "ProductsFragment";

    private RecyclerView productsRecyclerView;
    private BestsellerProductAdapter bestsellerAdapter;
    private List<Product> productList;
    private List<Category> categories;
    private List<String> selectedCategories;
    private List<String> selectedBrands;
    private List<Double> selectedDiscounts;
    private double[] combinedPriceRange;
    private Toolbar toolbar;
    private LinearLayout filterButtonLayout;
    private ListenerRegistration productsListener;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String categoryId;
    private String categoryName;
    private String searchQuery = "";
    private boolean filtersClearedOnBackPress = false;
    private SearchView searchView;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        productList = new ArrayList<>();
        categories = new ArrayList<>();
        selectedCategories = new ArrayList<>();
        selectedBrands = new ArrayList<>();
        selectedDiscounts = new ArrayList<>();
        combinedPriceRange = null;
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        Bundle args = getArguments();
        if (args != null) {
            categoryId = args.getString("categoryId");
            categoryName = args.getString("categoryName");
        }

        requireActivity().getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (searchView != null && !searchView.isIconified()) {
                    searchView.setQuery("", false);
                    searchView.setIconified(true);
                    toolbar.setTitle(categoryName != null ? categoryName : "All Products");
                    return;
                }

                if (hasActiveFilters()) {
                    clearFilters();
                    filtersClearedOnBackPress = true;
                    Toast.makeText(getContext(), "Filters cleared. Press back again to exit.", Toast.LENGTH_SHORT).show();
                } else {
                    filtersClearedOnBackPress = false;
                    requireActivity().getSupportFragmentManager().popBackStack();
                }
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_products, container, false);

        productsRecyclerView = view.findViewById(R.id.productsRecyclerView);
        toolbar = view.findViewById(R.id.toolbar);
        filterButtonLayout = view.findViewById(R.id.filterButtonLayout);

        if (mAuth.getCurrentUser() == null) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "Please log in to view products.", Toast.LENGTH_LONG).show();
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new LoginFragment())
                        .commit();
            }
            return view;
        }

        setupRecyclerView();
        setupToolbar();
        setupFilterButton();

        fetchCategories();
        fetchProducts();

        return view;
    }

    private void setupRecyclerView() {
        productsRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        bestsellerAdapter = new BestsellerProductAdapter(getContext(), productList, this, this);
        productsRecyclerView.setAdapter(bestsellerAdapter);
    }

    private void setupToolbar() {
        toolbar.setTitle(categoryName != null ? categoryName : "All Products");
        toolbar.setNavigationOnClickListener(v -> {
            if (searchView != null && !searchView.isIconified()) {
                searchView.setQuery("", false);
                searchView.setIconified(true);
                toolbar.setTitle(categoryName != null ? categoryName : "All Products");
                return;
            }

            if (hasActiveFilters()) {
                clearFilters();
                filtersClearedOnBackPress = true;
                Toast.makeText(getContext(), "Filters cleared. Press back again to exit.", Toast.LENGTH_SHORT).show();
            } else {
                filtersClearedOnBackPress = false;
                requireActivity().getSupportFragmentManager().popBackStack();
            }
        });
        toolbar.inflateMenu(R.menu.menu_products);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_cart) {
                Toast.makeText(requireContext(), "Cart clicked", Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });

        searchView = (SearchView) toolbar.getMenu().findItem(R.id.action_search).getActionView();
        searchView.setQueryHint("Search products...");
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchQuery = query.trim().toLowerCase();
                fetchProducts();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                searchQuery = newText.trim().toLowerCase();
                fetchProducts();
                return true;
            }
        });

        toolbar.getMenu().findItem(R.id.action_search).setOnActionExpandListener(new android.view.MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(android.view.MenuItem item) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(android.view.MenuItem item) {
                searchQuery = "";
                fetchProducts();
                toolbar.setTitle(categoryName != null ? categoryName : "All Products");
                return true;
            }
        });
    }

    private void setupFilterButton() {
        filterButtonLayout.setOnClickListener(v -> showFilterDialog());
    }

    private void showFilterDialog() {
        FilterDialogFragment filterDialog = FilterDialogFragment.newInstance(
                categories,
                selectedCategories,
                selectedBrands != null ? selectedBrands : new ArrayList<>(),
                selectedDiscounts,
                combinedPriceRange != null ? List.of(combinedPriceRange) : new ArrayList<>(),
                categoryId
        );
        filterDialog.setTargetFragment(this, 0);
        filterDialog.show(getParentFragmentManager(), "FilterDialogFragment");
    }

    private void fetchCategories() {
        db.collection("categories")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    categories.clear();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Category category = doc.toObject(Category.class);
                        category.setId(doc.getId());
                        categories.add(category);
                    }
                    if (categoryId != null && !selectedCategories.contains(categoryId)) {
                        selectedCategories.add(categoryId);
                    }
                    fetchProducts();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch categories: " + e.getMessage());
                    Toast.makeText(getContext(), "Failed to load categories: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void fetchProducts() {
        if (productsListener != null) {
            productsListener.remove();
        }

        Query query = db.collection("products");

        if (!selectedCategories.isEmpty()) {
            query = query.whereIn("categoryId", selectedCategories);
        }

        if (!selectedBrands.isEmpty()) {
            query = query.whereIn("brand", selectedBrands);
        }

        if (!searchQuery.isEmpty()) {
            query = query.whereGreaterThanOrEqualTo("nameLower", searchQuery)
                    .whereLessThan("nameLower", searchQuery + "\uf8ff");
        }

        Log.d(TAG, "Fetching products with filters - Categories: " + selectedCategories +
                ", Brands: " + selectedBrands +
                ", Discounts: " + selectedDiscounts +
                ", Combined Price Range: " + (combinedPriceRange != null ? "[" + combinedPriceRange[0] + ", " + combinedPriceRange[1] + "]" : "null") +
                ", Search Query: " + searchQuery);

        productsListener = query.addSnapshotListener((querySnapshot, error) -> {
            if (error != null) {
                Log.e(TAG, "Error fetching products: " + error.getMessage());
                Toast.makeText(getContext(), "Failed to load products: " + error.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }

            if (querySnapshot != null) {
                List<Product> filteredProducts = new ArrayList<>();
                for (QueryDocumentSnapshot doc : querySnapshot) {
                    Product product = doc.toObject(Product.class);
                    product.setId(doc.getId());

                    boolean matchesDiscount = selectedDiscounts.isEmpty();
                    if (!selectedDiscounts.isEmpty()) {
                        Double discount = product.getDiscountPercentage();
                        if (discount != null) {
                            Double minDiscount = Collections.min(selectedDiscounts);
                            if (discount >= minDiscount) {
                                matchesDiscount = true;
                            }
                        }
                    }
                    if (!matchesDiscount) {
                        continue;
                    }

                    boolean matchesPrice = combinedPriceRange == null;
                    if (combinedPriceRange != null) {
                        Double price = product.getPrice();
                        if (price != null) {
                            double minPrice = combinedPriceRange[0];
                            double maxPrice = combinedPriceRange[1];
                            if (price >= minPrice && price <= maxPrice) {
                                matchesPrice = true;
                            }
                        }
                    }
                    if (!matchesPrice) {
                        continue;
                    }

                    filteredProducts.add(product);
                }

                productList.clear();
                productList.addAll(filteredProducts);
                bestsellerAdapter.notifyDataSetChanged();

                if (productList.isEmpty()) {
                    Toast.makeText(getContext(), "No products found matching the filters.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public void onFilterApplied(List<String> selectedCategories, List<String> selectedBrands,
                                List<Double> discountRange, double[] combinedPriceRange) {
        this.selectedCategories = selectedCategories;
        this.selectedBrands = selectedBrands;
        this.selectedDiscounts = discountRange;
        this.combinedPriceRange = combinedPriceRange;
        Log.d(TAG, "Filters applied - Discounts: " + this.selectedDiscounts +
                ", Combined Price Range: " + (combinedPriceRange != null ? "[" + combinedPriceRange[0] + ", " + combinedPriceRange[1] + "]" : "null"));
        fetchProducts();
    }

    @Override
    public void onClearFilters() {
        clearFilters();
    }

    private void clearFilters() {
        selectedCategories.clear();
        if (categoryId != null) {
            selectedCategories.add(categoryId);
        }
        selectedBrands.clear();
        selectedDiscounts.clear();
        combinedPriceRange = null;
        searchQuery = "";
        fetchProducts();
    }

    private boolean hasActiveFilters() {
        return !selectedBrands.isEmpty() ||
                !selectedDiscounts.isEmpty() ||
                combinedPriceRange != null ||
                !searchQuery.isEmpty() ||
                (categoryId == null && !selectedCategories.isEmpty()) ||
                (categoryId != null && selectedCategories.size() > 1);
    }

    @Override
    public void onAddToCartClick(Product product) {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(getContext(), "Please log in to add items to cart.", Toast.LENGTH_SHORT).show();
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new LoginFragment())
                    .addToBackStack(null)
                    .commit();
            return;
        }

        String userId = mAuth.getCurrentUser().getUid();
        double price = product.getDiscountedPrice() != 0 ? product.getDiscountedPrice() : product.getPrice();

        // Check if item already exists in cart
        db.collection("cart")
                .document(userId)
                .collection("items")
                .whereEqualTo("productId", product.getId())
                .get()
                .addOnSuccessListener(querySnapshot -> {
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
                                    Toast.makeText(getContext(), product.getName() + " quantity updated in cart!", Toast.LENGTH_SHORT).show();
                                });
                    } else {
                        // New item, add it
                        Map<String, Object> cartItem = new HashMap<>();
                        cartItem.put("productId", product.getId());
                        cartItem.put("quantity", 1);
                        cartItem.put("total", price);
                        cartItem.put("addedAt", com.google.firebase.Timestamp.now());

                        db.collection("cart")
                                .document(userId)
                                .collection("items")
                                .add(cartItem)
                                .addOnSuccessListener(documentReference -> {
                                    Toast.makeText(getContext(), product.getName() + " added to cart!", Toast.LENGTH_SHORT).show();
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to add to cart: " + e.getMessage());
                    Toast.makeText(getContext(), "Failed to add to cart: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    @Override
    public void onProductClick(Product product) {
        ProductDetailsFragment productDetailsFragment = new ProductDetailsFragment();
        Bundle args = new Bundle();
        args.putParcelable("product", product);
        productDetailsFragment.setArguments(args);

        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, productDetailsFragment)
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (productsListener != null) {
            productsListener.remove();
        }
    }
}