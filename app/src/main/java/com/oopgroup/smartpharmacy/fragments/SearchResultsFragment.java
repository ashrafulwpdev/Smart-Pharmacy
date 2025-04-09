package com.oopgroup.smartpharmacy.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.adapters.BestsellerProductAdapter;
import com.oopgroup.smartpharmacy.models.Product;
import com.softourtech.slt.SLTLoader;

import java.util.ArrayList;
import java.util.List;

public class SearchResultsFragment extends Fragment implements BestsellerProductAdapter.OnAddToCartClickListener,
        BestsellerProductAdapter.OnProductClickListener {

    private static final String TAG = "SearchResultsFragment";

    private RecyclerView searchResultsRecyclerView;
    private LinearLayout noResultsView;
    private Toolbar toolbar;
    private BestsellerProductAdapter productAdapter;
    private List<Product> productList;
    private FirebaseFirestore db;
    private SLTLoader sltLoader;
    private String query;

    public SearchResultsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search_results, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        sltLoader = new SLTLoader(requireActivity(), view.findViewById(R.id.loader_container));
        db = FirebaseFirestore.getInstance();

        // Initialize views
        toolbar = view.findViewById(R.id.toolbar);
        searchResultsRecyclerView = view.findViewById(R.id.searchResultsRecyclerView);
        noResultsView = view.findViewById(R.id.noResultsView);

        // Set up Toolbar
        toolbar.setTitle("Results for \"" + getArguments().getString("query", "") + "\"");
        toolbar.setNavigationOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        // Set up RecyclerView
        productList = new ArrayList<>();
        productAdapter = new BestsellerProductAdapter(requireContext(), productList, this, this);
        searchResultsRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        searchResultsRecyclerView.setAdapter(productAdapter);
        searchResultsRecyclerView.setHasFixedSize(true);

        // Get search query and perform search
        Bundle args = getArguments();
        if (args != null) {
            query = args.getString("query", "");
            toolbar.setTitle("Results for \"" + query + "\"");
            performSearch(query);
        }
    }

    private void performSearch(String query) {
        showLoader();
        db.collection("products")
                .orderBy("nameLower")
                .startAt(query.toLowerCase())
                .endAt(query.toLowerCase() + "\uf8ff")
                .limit(20)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!isAdded()) return;
                    productList.clear();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Product product = doc.toObject(Product.class);
                        product.setId(doc.getId());
                        productList.add(product);
                    }
                    productAdapter.notifyDataSetChanged();

                    // Toggle visibility based on results
                    if (productList.isEmpty()) {
                        searchResultsRecyclerView.setVisibility(View.GONE);
                        noResultsView.setVisibility(View.VISIBLE);
                        Toast.makeText(requireContext(), "No products found for \"" + query + "\"", Toast.LENGTH_SHORT).show();
                    } else {
                        searchResultsRecyclerView.setVisibility(View.VISIBLE);
                        noResultsView.setVisibility(View.GONE);
                        Toast.makeText(requireContext(), "Found " + productList.size() + " results", Toast.LENGTH_SHORT).show();
                    }
                    hideLoader();
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Log.e(TAG, "Search failed: " + e.getMessage());
                    Toast.makeText(requireContext(), "Search failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    searchResultsRecyclerView.setVisibility(View.GONE);
                    noResultsView.setVisibility(View.VISIBLE);
                    hideLoader();
                });
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
    public void onAddToCartClick(Product product) {
        Toast.makeText(requireContext(), product.getName() + " added to cart!", Toast.LENGTH_SHORT).show();
        // Implement cart logic here if needed
    }

    @Override
    public void onProductClick(Product product) {
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
        if (sltLoader != null) {
            sltLoader.onDestroy();
            sltLoader = null;
        }
    }
}