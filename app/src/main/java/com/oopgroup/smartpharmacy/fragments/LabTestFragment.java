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
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.adapters.BestsellerProductAdapter;
import com.oopgroup.smartpharmacy.adapters.LabTestGridAdapter;
import com.oopgroup.smartpharmacy.models.LabTest;
import com.oopgroup.smartpharmacy.models.Product;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LabTestFragment extends Fragment implements LabTestGridAdapter.OnLabTestClickListener,
        BestsellerProductAdapter.OnAddToCartClickListener, BestsellerProductAdapter.OnProductClickListener {

    private static final String TAG = "LabTestFragment";

    private RecyclerView labTestsRecyclerView, bestsellerProductsRecyclerView;
    private Toolbar toolbar;
    private TextView viewAllProducts;
    private LabTestGridAdapter labTestAdapter;
    private BestsellerProductAdapter bestsellerProductAdapter;
    private List<LabTest> labTestList;
    private List<Product> bestsellerProductList;
    private CollectionReference labTestsRef, productsRef, cartItemsRef;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private ListenerRegistration labTestsListener, productsListener;

    public LabTestFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_lab_test, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        labTestsRef = db.collection("labTests");
        productsRef = db.collection("products");
        cartItemsRef = db.collection("cart").document(mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : "anonymous").collection("items");

        // Initialize UI
        toolbar = view.findViewById(R.id.toolbar);
        labTestsRecyclerView = view.findViewById(R.id.labTestsRecyclerView);
        bestsellerProductsRecyclerView = view.findViewById(R.id.bestsellerProductsRecyclerView);
        viewAllProducts = view.findViewById(R.id.viewAllProducts);

        if (labTestsRecyclerView == null || bestsellerProductsRecyclerView == null) {
            Log.e(TAG, "RecyclerView not found in layout");
            Toast.makeText(requireContext(), "Error: View not found", Toast.LENGTH_LONG).show();
            return;
        }

        // Set up Toolbar
        toolbar.setTitle("Lab Tests");
        toolbar.inflateMenu(R.menu.menu_products);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_search) {
                Toast.makeText(requireContext(), "Search clicked", Toast.LENGTH_SHORT).show();
                // Add search functionality if needed
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
                Toast.makeText(requireContext(), "Please log in to view lab tests.", Toast.LENGTH_LONG).show();
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new LoginFragment())
                        .commit();
            }
            return;
        }

        // Initialize lists and adapters
        labTestList = new ArrayList<>();
        bestsellerProductList = new ArrayList<>();
        labTestAdapter = new LabTestGridAdapter(requireContext(), labTestList, this);
        bestsellerProductAdapter = new BestsellerProductAdapter(requireContext(), bestsellerProductList, this, this);

        // Set up Lab Tests RecyclerView (2 items per row)
        labTestsRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        labTestsRecyclerView.setAdapter(labTestAdapter);
        labTestsRecyclerView.setHasFixedSize(true);

        // Set up Bestseller Products RecyclerView (2 items per row)
        bestsellerProductsRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        bestsellerProductsRecyclerView.setAdapter(bestsellerProductAdapter);
        bestsellerProductsRecyclerView.setHasFixedSize(true);

        // Set up View All button
        if (viewAllProducts != null) {
            viewAllProducts.setOnClickListener(v -> navigateToProductsFragment());
        }

        // Fetch data
        fetchLabTests();
        fetchBestsellerProducts();
    }

    private void fetchLabTests() {
        labTestsListener = labTestsRef.addSnapshotListener((snapshot, error) -> {
            if (!isAdded()) return;

            if (error != null) {
                Log.e(TAG, "Failed to load lab tests: " + error.getMessage());
                if (isAdded()) {
                    Toast.makeText(requireContext(), "Failed to load lab tests: " + error.getMessage(), Toast.LENGTH_LONG).show();
                }
                return;
            }

            labTestList.clear();
            if (snapshot != null) {
                for (QueryDocumentSnapshot doc : snapshot) {
                    String id = doc.getId();
                    String name = doc.getString("name");
                    String imageUrl = doc.getString("imageUrl");
                    String tests = doc.getString("tests");
                    Double price = doc.getDouble("price");

                    if (name != null && imageUrl != null) {
                        LabTest labTest = new LabTest(id, name, imageUrl, tests, price != null ? price : 0.0);
                        labTestList.add(labTest);
                    }
                }
                labTestAdapter.updateLabTests(labTestList);
            }
        });
    }

    private void fetchBestsellerProducts() {
        productsListener = productsRef
                .orderBy("rating", Query.Direction.DESCENDING)
                .limit(4)
                .addSnapshotListener((snapshot, error) -> {
                    if (!isAdded()) return;

                    if (error != null) {
                        Log.e(TAG, "Failed to load bestseller products: " + error.getMessage());
                        if (isAdded()) {
                            Toast.makeText(requireContext(), "Failed to load bestseller products", Toast.LENGTH_SHORT).show();
                        }
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
                });
    }

    private void navigateToCartFragment() {
        if (!isAdded()) return;

        CartFragment cartFragment = new CartFragment();
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, cartFragment)
                .addToBackStack(null)
                .commit();
    }

    private void navigateToProductsFragment() {
        if (!isAdded()) return;

        ProductsFragment productsFragment = new ProductsFragment();
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, productsFragment)
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onLabTestClick(LabTest labTest) {
        if (!isAdded()) return;

        LabTestDetailsFragment detailsFragment = LabTestDetailsFragment.newInstance(labTest.getId());
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, detailsFragment)
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onAddToCartClick(Product product) {
        if (!isAdded() || mAuth.getCurrentUser() == null) {
            Toast.makeText(requireContext(), "Please log in to add items to cart.", Toast.LENGTH_SHORT).show();
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new LoginFragment())
                    .addToBackStack(null)
                    .commit();
            return;
        }

        String userId = mAuth.getCurrentUser().getUid();
        double price = product.getDiscountedPrice() != 0 ? product.getDiscountedPrice() : product.getPrice();
        DocumentReference cartItemRef = cartItemsRef.document(product.getId());

        cartItemRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                int currentQuantity = documentSnapshot.getLong("quantity").intValue();
                double currentTotal = documentSnapshot.getDouble("total");

                cartItemRef.update(
                        "quantity", currentQuantity + 1,
                        "total", currentTotal + price,
                        "addedAt", com.google.firebase.Timestamp.now()
                ).addOnSuccessListener(aVoid -> {
                    Toast.makeText(requireContext(), product.getName() + " quantity updated in cart!", Toast.LENGTH_SHORT).show();
                }).addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update cart item: " + e.getMessage());
                    Toast.makeText(requireContext(), "Failed to update cart", Toast.LENGTH_SHORT).show();
                });
            } else {
                Map<String, Object> cartItem = new HashMap<>();
                cartItem.put("productId", product.getId());
                cartItem.put("productName", product.getName());
                cartItem.put("imageUrl", product.getImageUrl());
                cartItem.put("quantity", 1);
                cartItem.put("total", price);
                cartItem.put("originalPrice", product.getPrice());
                cartItem.put("discountedPrice", product.getDiscountedPrice());
                cartItem.put("discountPercentage", product.getDiscountPercentage());
                cartItem.put("addedAt", com.google.firebase.Timestamp.now());

                cartItemRef.set(cartItem).addOnSuccessListener(aVoid -> {
                    Toast.makeText(requireContext(), product.getName() + " added to cart!", Toast.LENGTH_SHORT).show();
                }).addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to add to cart: " + e.getMessage());
                    Toast.makeText(requireContext(), "Failed to add to cart", Toast.LENGTH_SHORT).show();
                });
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to check cart: " + e.getMessage());
            Toast.makeText(requireContext(), "Error checking cart", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onProductClick(Product product) {
        if (!isAdded()) return;

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
    public void onDestroyView() {
        super.onDestroyView();
        if (labTestsListener != null) {
            labTestsListener.remove();
            labTestsListener = null;
        }
        if (productsListener != null) {
            productsListener.remove();
            productsListener = null;
        }
        if (labTestsRecyclerView != null) {
            labTestsRecyclerView.setAdapter(null);
        }
        if (bestsellerProductsRecyclerView != null) {
            bestsellerProductsRecyclerView.setAdapter(null);
        }
        labTestAdapter = null;
        bestsellerProductAdapter = null;
        labTestsRecyclerView = null;
        bestsellerProductsRecyclerView = null;
        toolbar = null;
        viewAllProducts = null;
    }
}