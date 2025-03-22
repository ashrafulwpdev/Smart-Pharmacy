package com.oopgroup.smartpharmacy.fragments;

import android.os.Bundle;
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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.adapters.BestsellerProductAdapter;
import com.oopgroup.smartpharmacy.models.Product;
import java.util.ArrayList;
import java.util.List;

public class ProductsFragment extends Fragment implements BestsellerProductAdapter.OnAddToCartClickListener {

    private RecyclerView productsRecyclerView;
    private TextView productsTitle;
    private BestsellerProductAdapter productAdapter;
    private List<Product> productList;
    private DatabaseReference productsRef;
    private FirebaseAuth mAuth;
    private String categoryId;
    private String categoryName;

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
        productsRef = FirebaseDatabase.getInstance().getReference("products");

        // Check if user is authenticated
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(requireContext(), "Please log in to view products.", Toast.LENGTH_LONG).show();
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new LoginFragment())
                    .commit();
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

        // Fetch products
        fetchProducts();
    }

    private void fetchProducts() {
        DatabaseReference queryRef = productsRef;
        if (categoryId != null) {
            queryRef = productsRef.child(categoryId);
        }

        queryRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                productList.clear();
                if (categoryId != null) {
                    for (DataSnapshot productSnapshot : snapshot.getChildren()) {
                        String id = productSnapshot.getKey();
                        String name = productSnapshot.child("name").getValue(String.class);
                        Double price = productSnapshot.child("price").getValue(Double.class);
                        String imageUrl = productSnapshot.child("imageUrl").getValue(String.class);
                        Integer rating = productSnapshot.child("rating").getValue(Integer.class);
                        Integer reviewCount = productSnapshot.child("reviewCount").getValue(Integer.class);
                        String quantity = productSnapshot.child("quantity").getValue(String.class);
                        Double originalPrice = productSnapshot.child("originalPrice").getValue(Double.class);
                        Double discountedPrice = productSnapshot.child("discountedPrice").getValue(Double.class);

                        if (name != null && price != null && imageUrl != null && rating != null && reviewCount != null) {
                            Product product = new Product(id, name, price, imageUrl, rating, reviewCount, quantity, originalPrice != null ? originalPrice : price, discountedPrice != null ? discountedPrice : 0.0);
                            productList.add(product);
                        }
                    }
                } else {
                    for (DataSnapshot categorySnapshot : snapshot.getChildren()) {
                        for (DataSnapshot productSnapshot : categorySnapshot.getChildren()) {
                            String id = productSnapshot.getKey();
                            String name = productSnapshot.child("name").getValue(String.class);
                            Double price = productSnapshot.child("price").getValue(Double.class);
                            String imageUrl = productSnapshot.child("imageUrl").getValue(String.class);
                            Integer rating = productSnapshot.child("rating").getValue(Integer.class);
                            Integer reviewCount = productSnapshot.child("reviewCount").getValue(Integer.class);
                            String quantity = productSnapshot.child("quantity").getValue(String.class);
                            Double originalPrice = productSnapshot.child("originalPrice").getValue(Double.class);
                            Double discountedPrice = productSnapshot.child("discountedPrice").getValue(Double.class);

                            if (name != null && price != null && imageUrl != null && rating != null && reviewCount != null) {
                                Product product = new Product(id, name, price, imageUrl, rating, reviewCount, quantity, originalPrice != null ? originalPrice : price, discountedPrice != null ? discountedPrice : 0.0);
                                productList.add(product);
                            }
                        }
                    }
                }
                productAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(requireContext(), "Failed to load products: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onAddToCartClick(Product product) {
        Toast.makeText(requireContext(), "Added to cart: " + product.getName(), Toast.LENGTH_SHORT).show();
    }
}