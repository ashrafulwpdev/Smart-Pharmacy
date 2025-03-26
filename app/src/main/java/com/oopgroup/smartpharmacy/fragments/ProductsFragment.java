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
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.adapters.BestsellerProductAdapter;
import com.oopgroup.smartpharmacy.models.Product;

import java.util.ArrayList;
import java.util.List;

public class ProductsFragment extends Fragment implements BestsellerProductAdapter.OnAddToCartClickListener {

    private static final String TAG = "ProductsFragment";

    private RecyclerView productsRecyclerView;
    private TextView productsTitle;
    private BestsellerProductAdapter productAdapter;
    private List<Product> productList;
    private CollectionReference productsRef;
    private FirebaseAuth mAuth;
    private String categoryId;
    private String categoryName;
    private ListenerRegistration productsListener;

    public ProductsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_products, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        productsRef = FirebaseFirestore.getInstance().collection("products");

        // Check if user is authenticated
        if (mAuth.getCurrentUser() == null) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "Please log in to view products.", Toast.LENGTH_LONG).show();
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new LoginFragment())
                        .commit();
            }
            return;
        }

        // Get arguments
        Bundle args = getArguments();
        if (args != null) {
            categoryId = args.getString("categoryId");
            categoryName = args.getString("categoryName");
        }

        // Initialize UI
        productsTitle = view.findViewById(R.id.productsTitle);
        productsRecyclerView = view.findViewById(R.id.productsRecyclerView);
        if (productsRecyclerView == null) {
            Log.e(TAG, "productsRecyclerView not found in layout");
            Toast.makeText(requireContext(), "Error: Products view not found", Toast.LENGTH_LONG).show();
            return;
        }
        productsRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));

        // Set title
        if (categoryName != null) {
            productsTitle.setText(categoryName);
        } else {
            productsTitle.setText("All Products");
        }

        // Initialize list and adapter
        productList = new ArrayList<>();
        productAdapter = new BestsellerProductAdapter(requireContext(), productList, this);
        productsRecyclerView.setAdapter(productAdapter);
        productsRecyclerView.setHasFixedSize(true);

        // Fetch products
        fetchProducts();
    }

    private void fetchProducts() {
        Query query = productsRef;
        if (categoryId != null) {
            // Assuming products have a "categoryId" field linking them to categories
            query = productsRef.whereEqualTo("categoryId", categoryId);
        }

        productsListener = query.addSnapshotListener((snapshot, error) -> {
            if (!isAdded()) return;

            if (error != null) {
                Log.e(TAG, "Failed to load products: " + error.getMessage());
                Toast.makeText(requireContext(), "Failed to load products: " + error.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }

            if (snapshot != null) {
                productList.clear();
                for (QueryDocumentSnapshot doc : snapshot) {
                    String id = doc.getId();
                    String name = doc.getString("name");
                    Double price = doc.getDouble("price");
                    String imageUrl = doc.getString("imageUrl");
                    Long ratingLong = doc.getLong("rating"); // Firestore stores numbers as Long
                    Long reviewCountLong = doc.getLong("reviewCount");
                    String quantity = doc.getString("quantity");
                    Double originalPrice = doc.getDouble("originalPrice");
                    Double discountedPrice = doc.getDouble("discountedPrice");

                    if (name != null && price != null && imageUrl != null && ratingLong != null && reviewCountLong != null) {
                        int rating = ratingLong.intValue();
                        int reviewCount = reviewCountLong.intValue();
                        Product product = new Product(id, name, price, imageUrl, rating, reviewCount, quantity,
                                originalPrice != null ? originalPrice : price,
                                discountedPrice != null ? discountedPrice : 0.0);
                        productList.add(product);
                    }
                }
                productAdapter.notifyDataSetChanged();
                Log.d(TAG, "Products loaded: " + productList.size());
            }
        });
    }

    @Override
    public void onAddToCartClick(Product product) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), "Added to cart: " + product.getName(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (productsListener != null) {
            productsListener.remove();
            productsListener = null;
        }
        if (productsRecyclerView != null) {
            productsRecyclerView.setAdapter(null);
            productsRecyclerView = null;
        }
        productAdapter = null;
        productsTitle = null;
    }
}