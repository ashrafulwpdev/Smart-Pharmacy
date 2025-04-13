package com.oopgroup.smartpharmacy.fragments;

import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.oopgroup.smartpharmacy.MainActivity;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.adapters.BestsellerProductAdapter;
import com.oopgroup.smartpharmacy.models.Product;
import com.oopgroup.smartpharmacy.utils.NetworkUtils;

public class ProductDetailsFragment extends Fragment implements BestsellerProductAdapter.OnAddToCartClickListener {

    private static final String TAG = "ProductDetailsFragment";
    private static final long TOAST_COOLDOWN = 2000; // 2 seconds

    private ImageView productImage, addressSelectorIcon;
    private TextView productRating, productTitle, productQuantity, productPrice, productOriginalPrice;
    private TextView productDescription, deliveryDetails, deliveryAddress, quantityText;
    private TextView readMore, viewAll, cartItemCount, cartTotalTextView;
    private MaterialButton decreaseQuantity, increaseQuantity, viewCartButton;
    private RecyclerView similarProductsRecyclerView;
    private BestsellerProductAdapter similarProductAdapter;
    private List<Product> similarProductList;
    private Product product;
    private int quantity = 0;
    private double priceToAdd = 0.0;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private int cartItems = 0;
    private double cartTotal = 0.0;
    private long lastToastTime = 0;

    public ProductDetailsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView called");
        return inflater.inflate(R.layout.fragment_product_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated called");

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // Check network status
        if (!NetworkUtils.isNetworkAvailable(requireContext())) {
            showToast("No internet. Please connect and try again.");
            navigateToNoInternetFragment();
            return;
        }

        if (auth.getCurrentUser() == null) {
            if (isAdded()) {
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new LoginFragment())
                        .commit();
            }
            return;
        }

        // Hide bottom navigation bar
        hideBottomNavigation();

        // Toolbar setup
        Toolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setTitle("Loading...");
        toolbar.setNavigationOnClickListener(v -> {
            if (isAdded()) {
                requireActivity().getSupportFragmentManager().popBackStack();
            }
        });
        toolbar.inflateMenu(R.menu.cart_menu);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_cart) {
                navigateToCartFragment();
                return true;
            }
            return false;
        });

        // Initialize views
        productImage = view.findViewById(R.id.productImage);
        addressSelectorIcon = view.findViewById(R.id.addressSelectorIcon);
        productRating = view.findViewById(R.id.productRating);
        productTitle = view.findViewById(R.id.productTitle);
        productQuantity = view.findViewById(R.id.productQuantity);
        productPrice = view.findViewById(R.id.productPrice);
        productOriginalPrice = view.findViewById(R.id.productOriginalPrice);
        productDescription = view.findViewById(R.id.productDescription);
        deliveryDetails = view.findViewById(R.id.deliveryDetails);
        deliveryAddress = view.findViewById(R.id.deliveryAddress);
        quantityText = view.findViewById(R.id.quantityText);
        decreaseQuantity = view.findViewById(R.id.decreaseQuantity);
        increaseQuantity = view.findViewById(R.id.increaseQuantity);
        readMore = view.findViewById(R.id.readMore);
        viewAll = view.findViewById(R.id.viewAll);
        cartItemCount = view.findViewById(R.id.cartItemCount);
        cartTotalTextView = view.findViewById(R.id.cartTotal);
        viewCartButton = view.findViewById(R.id.viewCartButton);
        similarProductsRecyclerView = view.findViewById(R.id.similarProductsRecyclerView);

        // Handle product data from bundle
        Bundle bundle = getArguments();
        final String[] categoryName = {"Product Details"};
        if (bundle != null) {
            product = bundle.getParcelable("product");
            if (product != null) {
                Log.d(TAG, "Product received: " + product.getName());
                if (product.getCategoryId() != null && !product.getCategoryId().isEmpty()) {
                    db.collection("categories").document(product.getCategoryId())
                            .get(Source.CACHE)
                            .addOnSuccessListener(documentSnapshot -> {
                                if (!isAdded()) return;
                                if (documentSnapshot.exists()) {
                                    String fetchedName = documentSnapshot.getString("name");
                                    categoryName[0] = fetchedName != null ? fetchedName : "Product Details";
                                    toolbar.setTitle(categoryName[0]);
                                } else {
                                    toolbar.setTitle("Product Details");
                                }
                            })
                            .addOnFailureListener(e -> {
                                if (!isAdded()) return;
                                Log.e(TAG, "Failed to fetch category name: " + e.getMessage());
                                toolbar.setTitle("Product Details");
                            });
                } else {
                    toolbar.setTitle("Product Details");
                }
            } else {
                toolbar.setTitle("Product Details");
            }
        } else {
            toolbar.setTitle("Product Details");
        }

        // Populate product details
        if (product != null) {
            Glide.with(requireContext()).load(product.getImageUrl()).into(productImage);
            String ratingText = String.format("%.1f (%d Reviews)", (float) product.getRating() / 2, product.getReviewCount());
            SpannableString spannableString = new SpannableString(ratingText);
            spannableString.setSpan(new ForegroundColorSpan(Color.BLUE), 0, ratingText.indexOf(" "), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannableString.setSpan(new ForegroundColorSpan(Color.parseColor("#0F0E0E")), ratingText.indexOf("("), ratingText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            productRating.setText(spannableString);
            productTitle.setText(product.getName());
            productQuantity.setText(product.getQuantity());
            if (product.getDiscountedPrice() > 0.0) {
                productPrice.setText(String.format("RM%.2f", product.getDiscountedPrice()));
                productOriginalPrice.setText(String.format("RM%.2f", product.getOriginalPrice()));
                productOriginalPrice.setPaintFlags(productOriginalPrice.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                productOriginalPrice.setVisibility(View.VISIBLE);
                priceToAdd = product.getDiscountedPrice();
            } else {
                productPrice.setText(String.format("RM%.2f", product.getOriginalPrice()));
                productOriginalPrice.setVisibility(View.GONE);
                priceToAdd = product.getOriginalPrice();
            }
            productDescription.setText(product.getDescription());
            deliveryDetails.setText("Expected Delivery By " + product.getDeliveryDate());
            deliveryAddress.setTextColor(Color.BLACK);
        }

        // Description read more/less toggle
        productDescription.post(() -> {
            if (!isAdded()) return;
            if (productDescription.getLineCount() > 3) {
                readMore.setVisibility(View.VISIBLE);
            }
        });
        readMore.setOnClickListener(v -> {
            if (!isAdded()) return;
            if (readMore.getText().toString().equals("READ MORE")) {
                productDescription.setMaxLines(Integer.MAX_VALUE);
                readMore.setText("READ LESS");
            } else {
                productDescription.setMaxLines(3);
                readMore.setText("READ MORE");
            }
        });

        // Quantity controls
        quantityText.setText(String.valueOf(quantity));
        decreaseQuantity.setOnClickListener(v -> {
            if (!isAdded() || !NetworkUtils.isNetworkAvailable(requireContext())) {
                showToast("No internet. Please connect to proceed.");
                return;
            }
            if (quantity > 0) {
                quantity--;
                quantityText.setText(String.valueOf(quantity));
                updateCartUI(-1, -priceToAdd);
                updateFirestoreCart(quantity);
            }
        });
        increaseQuantity.setOnClickListener(v -> {
            if (!isAdded() || !NetworkUtils.isNetworkAvailable(requireContext())) {
                showToast("No internet. Please connect to proceed.");
                return;
            }
            quantity++;
            quantityText.setText(String.valueOf(quantity));
            updateCartUI(1, priceToAdd);
            updateFirestoreCart(quantity);
        });

        // Fetch initial cart data and address
        fetchCartDataForProduct();
        fetchAndSetDeliveryAddress();

        // Address selector icon click
        addressSelectorIcon.setOnClickListener(v -> {
            if (!isAdded() || !NetworkUtils.isNetworkAvailable(requireContext())) {
                showToast("No internet. Please connect to proceed.");
                return;
            }
            showAddressDialog();
        });

        // View cart button
        viewCartButton.setOnClickListener(v -> {
            if (!isAdded() || !NetworkUtils.isNetworkAvailable(requireContext())) {
                showToast("No internet. Please connect to proceed.");
                return;
            }
            navigateToCartFragment();
        });

        // Similar products setup
        similarProductList = new ArrayList<>();
        similarProductAdapter = new BestsellerProductAdapter(requireContext(), similarProductList, this);
        similarProductsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        similarProductsRecyclerView.setAdapter(similarProductAdapter);
        fetchSimilarProducts();

        viewAll.setOnClickListener(v -> {
            if (!isAdded() || !NetworkUtils.isNetworkAvailable(requireContext())) {
                showToast("No internet. Please connect to proceed.");
                return;
            }
            ProductsFragment fragment = new ProductsFragment();
            Bundle viewAllArgs = new Bundle();
            viewAllArgs.putString("categoryId", product.getCategoryId());
            viewAllArgs.putString("categoryName", categoryName[0]);
            fragment.setArguments(viewAllArgs);
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit();
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "onStart called");
        hideBottomNavigation();
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume called");
        hideBottomNavigation();
    }

    private void hideBottomNavigation() {
        if (!isAdded()) return;
        if (requireActivity() instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) requireActivity();
            mainActivity.hideBottomNav();
            Log.d(TAG, "hideBottomNav() called in ProductDetailsFragment");
        } else {
            Log.e(TAG, "Activity is not MainActivity, cannot hide bottom nav");
        }
    }

    private void fetchAndSetDeliveryAddress() {
        if (!isAdded()) return;
        String userId = auth.getCurrentUser().getUid();
        db.collection("users")
                .document(userId)
                .get(Source.CACHE)
                .addOnSuccessListener(documentSnapshot -> {
                    if (!isAdded()) return;
                    if (documentSnapshot.exists() && documentSnapshot.contains("lastSelectedAddressId")) {
                        String lastSelectedAddressId = documentSnapshot.getString("lastSelectedAddressId");
                        fetchAddressById(lastSelectedAddressId);
                    } else {
                        fetchDefaultAddress();
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    fetchDefaultAddress();
                });
    }

    private void fetchAddressById(String addressId) {
        if (!isAdded()) return;
        String userId = auth.getCurrentUser().getUid();
        db.collection("users")
                .document(userId)
                .collection("addresses")
                .document(addressId)
                .get(Source.CACHE)
                .addOnSuccessListener(doc -> {
                    if (!isAdded()) return;
                    if (doc.exists()) {
                        setDeliveryAddress(doc.getId(), doc);
                    } else {
                        fetchDefaultAddress();
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    fetchDefaultAddress();
                });
    }

    private void fetchDefaultAddress() {
        if (!isAdded()) return;
        String userId = auth.getCurrentUser().getUid();
        db.collection("users")
                .document(userId)
                .collection("addresses")
                .whereEqualTo("isDefault", true)
                .limit(1)
                .get(Source.CACHE)
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!isAdded()) return;
                    if (!queryDocumentSnapshots.isEmpty()) {
                        DocumentSnapshot doc = queryDocumentSnapshots.getDocuments().get(0);
                        setDeliveryAddress(doc.getId(), doc);
                    } else {
                        db.collection("users")
                                .document(userId)
                                .collection("addresses")
                                .limit(1)
                                .get(Source.CACHE)
                                .addOnSuccessListener(allAddresses -> {
                                    if (!isAdded()) return;
                                    if (!allAddresses.isEmpty()) {
                                        DocumentSnapshot doc = allAddresses.getDocuments().get(0);
                                        setDeliveryAddress(doc.getId(), doc);
                                    } else {
                                        setNoAddress();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    if (!isAdded()) return;
                                    Log.e(TAG, "Failed to fetch addresses: " + e.getMessage());
                                    setErrorAddress();
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Log.e(TAG, "Failed to fetch default address: " + e.getMessage());
                    setErrorAddress();
                });
    }

    private void setDeliveryAddress(String addressId, DocumentSnapshot doc) {
        if (!isAdded()) return;
        String streetAddress = doc.getString("streetAddress");
        String city = doc.getString("city");
        String state = doc.getString("state");
        String postalCode = doc.getString("postalCode");
        String country = doc.getString("country");

        String fullAddress = String.format("%s, %s, %s, %s, %s",
                streetAddress != null ? streetAddress : "",
                city != null ? city : "",
                state != null ? state : "",
                postalCode != null ? postalCode : "",
                country != null ? country : "").replace(", ,", ",").trim();
        if (fullAddress.endsWith(",")) {
            fullAddress = fullAddress.substring(0, fullAddress.length() - 1);
        }

        String displayText = "Delivery Address: " + fullAddress;
        SpannableString spannableString = new SpannableString(displayText);
        spannableString.setSpan(new ForegroundColorSpan(Color.GRAY), 0, "Delivery Address: ".length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableString.setSpan(new ForegroundColorSpan(Color.BLACK), "Delivery Address: ".length(), displayText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        deliveryAddress.setText(spannableString);

        // Pass selected address to CartFragment
        CartFragment cartFragment = (CartFragment) getParentFragmentManager().findFragmentByTag("CartFragment");
        if (cartFragment != null) {
            cartFragment.onAddressSelected(addressId, fullAddress);
        }
    }

    private void setNoAddress() {
        if (!isAdded()) return;
        SpannableString spannableString = new SpannableString("Delivery Address: Not set");
        spannableString.setSpan(new ForegroundColorSpan(Color.GRAY), 0, "Delivery Address: ".length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableString.setSpan(new ForegroundColorSpan(Color.BLACK), "Delivery Address: ".length(), "Delivery Address: Not set".length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        deliveryAddress.setText(spannableString);
    }

    private void setErrorAddress() {
        if (!isAdded()) return;
        SpannableString spannableString = new SpannableString("Delivery Address: Error loading");
        spannableString.setSpan(new ForegroundColorSpan(Color.GRAY), 0, "Delivery Address: ".length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableString.setSpan(new ForegroundColorSpan(Color.BLACK), "Delivery Address: ".length(), "Delivery Address: Error loading".length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        deliveryAddress.setText(spannableString);
    }

    private void showAddressDialog() {
        if (!isAdded()) return;
        AddressDialogFragment dialog = new AddressDialogFragment();
        dialog.setOnAddressSelectedListener((addressId, address) -> {
            if (!isAdded()) return;
            String displayText = "Delivery Address: " + address;
            SpannableString spannableString = new SpannableString(displayText);
            spannableString.setSpan(new ForegroundColorSpan(Color.GRAY), 0, "Delivery Address: ".length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannableString.setSpan(new ForegroundColorSpan(Color.BLACK), "Delivery Address: ".length(), displayText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            deliveryAddress.setText(spannableString);

            // Pass selected address to CartFragment
            CartFragment cartFragment = (CartFragment) getParentFragmentManager().findFragmentByTag("CartFragment");
            if (cartFragment != null) {
                cartFragment.onAddressSelected(addressId, address);
            }

            // Save as last selected address
            String userId = auth.getCurrentUser().getUid();
            db.collection("users")
                    .document(userId)
                    .update("lastSelectedAddressId", addressId)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Last selected address updated: " + addressId))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to update last selected address: " + e.getMessage()));
        });
        dialog.show(requireActivity().getSupportFragmentManager(), "AddressDialogFragment");
    }

    private void navigateToCartFragment() {
        if (!isAdded()) return;
        CartFragment cartFragment = new CartFragment();
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, cartFragment, "CartFragment")
                .addToBackStack(null)
                .commit();
    }

    private void navigateToNoInternetFragment() {
        if (!isAdded()) return;
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new NoInternetFragment())
                .addToBackStack(null)
                .commit();
    }

    private void updateCartUI(int itemChange, double priceChange) {
        if (!isAdded()) return;
        cartItems += itemChange;
        cartTotal += priceChange;
        if (cartItems < 0) cartItems = 0;
        if (cartTotal < 0) cartTotal = 0.0;
        cartItemCount.setText(cartItems + " items");
        cartTotalTextView.setText(String.format("RM%.2f", cartTotal));
    }

    private void fetchCartDataForProduct() {
        if (!isAdded() || product == null || product.getId() == null) return;

        String userId = auth.getCurrentUser().getUid();
        db.collection("cart").document(userId)
                .collection("items")
                .whereEqualTo("productId", product.getId())
                .get(Source.CACHE)
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!isAdded()) return;
                    if (!queryDocumentSnapshots.isEmpty()) {
                        int totalQuantity = 0;
                        double totalPrice = 0.0;
                        DocumentReference primaryRef = null;

                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            Long qty = doc.getLong("quantity");
                            Double price = doc.getDouble("discountedPrice");
                            if (price == null) price = doc.getDouble("originalPrice");
                            if (qty != null && price != null) {
                                totalQuantity += qty.intValue();
                                totalPrice += price * qty;
                                if (primaryRef == null) {
                                    primaryRef = db.collection("cart").document(userId)
                                            .collection("items").document(product.getId());
                                }
                                if (!doc.getId().equals(product.getId())) {
                                    db.collection("cart").document(userId)
                                            .collection("items").document(doc.getId())
                                            .delete();
                                }
                            }
                        }

                        if (primaryRef != null) {
                            cartItems = totalQuantity;
                            quantity = cartItems;
                            cartTotal = totalPrice;
                            quantityText.setText(String.valueOf(quantity));
                            cartItemCount.setText(cartItems + " items");
                            cartTotalTextView.setText(String.format("RM%.2f", cartTotal));

                            Map<String, Object> updates = new HashMap<>();
                            updates.put("quantity", totalQuantity);
                            updates.put("total", totalPrice);
                            updates.put("addedAt", Timestamp.now());
                            primaryRef.update(updates);
                        }
                    } else {
                        cartItems = 0;
                        cartTotal = 0.0;
                        quantity = 0;
                        quantityText.setText("0");
                        cartItemCount.setText("0 items");
                        cartTotalTextView.setText("RM0.00");
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Log.e(TAG, "Failed to fetch cart data: " + e.getMessage());
                });
    }

    private void updateFirestoreCart(int newQuantity) {
        if (!isAdded() || product == null || product.getId() == null) return;

        String userId = auth.getCurrentUser().getUid();
        DocumentReference cartItemRef = db.collection("cart").document(userId)
                .collection("items").document(product.getId());

        double originalPrice = product.getOriginalPrice();
        double discountedPrice = product.getDiscountedPrice() > 0.0 ? product.getDiscountedPrice() : originalPrice;
        double discountPercentage = discountedPrice < originalPrice ?
                ((originalPrice - discountedPrice) / originalPrice) * 100 : 0;

        if (newQuantity > 0) {
            Map<String, Object> cartItem = new HashMap<>();
            cartItem.put("addedAt", Timestamp.now());
            cartItem.put("discountPercentage", discountPercentage);
            cartItem.put("discountedPrice", discountedPrice);
            cartItem.put("imageUrl", product.getImageUrl());
            cartItem.put("originalPrice", originalPrice);
            cartItem.put("productId", product.getId());
            cartItem.put("productName", product.getName());
            cartItem.put("quantity", newQuantity);
            cartItem.put("total", discountedPrice * newQuantity);

            cartItemRef.set(cartItem)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Cart updated: " + product.getName()))
                    .addOnFailureListener(e -> {
                        if (!isAdded()) return;
                        Log.e(TAG, "Failed to update cart: " + e.getMessage());
                    });
        } else {
            cartItemRef.delete()
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Cart item removed: " + product.getName()))
                    .addOnFailureListener(e -> {
                        if (!isAdded()) return;
                        Log.e(TAG, "Failed to remove cart item: " + e.getMessage());
                    });
        }
    }

    private void fetchSimilarProducts() {
        if (!isAdded() || product == null || product.getCategoryId() == null || product.getCategoryId().isEmpty()) return;

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                List<Product> tempList = new ArrayList<>();
                db.collection("products")
                        .whereEqualTo("categoryId", product.getCategoryId())
                        .whereNotEqualTo("id", product.getId())
                        .limit(5)
                        .get(Source.CACHE)
                        .addOnSuccessListener(queryDocumentSnapshots -> {
                            for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                                String id = doc.getId();
                                String name = doc.getString("name");
                                Double price = doc.getDouble("price");
                                String imageUrl = doc.getString("imageUrl");
                                Long ratingLong = doc.getLong("rating");
                                Long reviewCountLong = doc.getLong("reviewCount");
                                String quantity = doc.getString("quantity");
                                Double originalPrice = doc.getDouble("originalPrice");
                                Double discountedPrice = doc.getDouble("discountedPrice");
                                String description = doc.getString("description");
                                String deliveryDate = doc.getString("deliveryDate");
                                String categoryId = doc.getString("categoryId");

                                if (name != null && price != null && imageUrl != null && ratingLong != null && reviewCountLong != null && description != null && categoryId != null) {
                                    int rating = ratingLong.intValue();
                                    int reviewCount = reviewCountLong.intValue();
                                    Product similarProduct = new Product(
                                            id, name, price, imageUrl, rating, reviewCount, quantity,
                                            originalPrice != null ? originalPrice : price,
                                            discountedPrice != null ? discountedPrice : 0.0,
                                            description,
                                            deliveryDate != null ? deliveryDate : "N/A",
                                            "N/A",
                                            categoryId
                                    );
                                    tempList.add(similarProduct);
                                }
                            }
                            new Handler(Looper.getMainLooper()).post(() -> {
                                if (!isAdded()) return;
                                similarProductList.clear();
                                similarProductList.addAll(tempList);
                                similarProductAdapter.notifyDataSetChanged();
                            });
                        })
                        .addOnFailureListener(e -> {
                            new Handler(Looper.getMainLooper()).post(() -> {
                                if (!isAdded()) return;
                                Log.e(TAG, "Failed to load similar products: " + e.getMessage());
                            });
                        });
            } finally {
                executor.shutdown();
            }
        });
    }

    @Override
    public void onAddToCartClick(Product product) {
        if (!isAdded() || !NetworkUtils.isNetworkAvailable(requireContext())) {
            showToast("No internet. Please connect to proceed.");
            return;
        }

        String userId = auth.getCurrentUser().getUid();
        DocumentReference cartItemRef = db.collection("cart").document(userId)
                .collection("items").document(product.getId());

        double originalPrice = product.getOriginalPrice();
        double discountedPrice = product.getDiscountedPrice() > 0.0 ? product.getDiscountedPrice() : originalPrice;
        double discountPercentage = discountedPrice < originalPrice ?
                ((originalPrice - discountedPrice) / originalPrice) * 100 : 0;

        cartItemRef.get().addOnSuccessListener(documentSnapshot -> {
            if (!isAdded()) return;
            if (documentSnapshot.exists()) {
                int currentQuantity = documentSnapshot.getLong("quantity").intValue();
                double currentTotal = documentSnapshot.getDouble("total");

                cartItemRef.update(
                                "quantity", currentQuantity + 1,
                                "total", currentTotal + discountedPrice,
                                "addedAt", Timestamp.now()
                        ).addOnSuccessListener(aVoid -> Log.d(TAG, "Cart updated: " + product.getName()))
                        .addOnFailureListener(e -> {
                            if (!isAdded()) return;
                            Log.e(TAG, "Failed to update cart: " + e.getMessage());
                        });
            } else {
                Map<String, Object> cartItem = new HashMap<>();
                cartItem.put("addedAt", Timestamp.now());
                cartItem.put("discountPercentage", discountPercentage);
                cartItem.put("discountedPrice", discountedPrice);
                cartItem.put("imageUrl", product.getImageUrl());
                cartItem.put("originalPrice", originalPrice);
                cartItem.put("productId", product.getId());
                cartItem.put("productName", product.getName());
                cartItem.put("quantity", 1);
                cartItem.put("total", discountedPrice);

                cartItemRef.set(cartItem)
                        .addOnSuccessListener(aVoid -> Log.d(TAG, "Added to cart: " + product.getName()))
                        .addOnFailureListener(e -> {
                            if (!isAdded()) return;
                            Log.e(TAG, "Failed to add to cart: " + e.getMessage());
                        });
            }
        }).addOnFailureListener(e -> {
            if (!isAdded()) return;
            Log.e(TAG, "Failed to check cart item: " + e.getMessage());
        });
    }

    private void showToast(String message) {
        if (!isAdded()) return;
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastToastTime >= TOAST_COOLDOWN) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            lastToastTime = currentTime;
        }
    }
}