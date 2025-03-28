package com.oopgroup.smartpharmacy.fragments;

import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.adapters.BestsellerProductAdapter;
import com.oopgroup.smartpharmacy.models.Product;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProductDetailsFragment extends Fragment implements BestsellerProductAdapter.OnAddToCartClickListener {

    private static final String TAG = "ProductDetailsFragment";

    private ImageView productImage;
    private TextView productRating, productTitle, productQuantity, productPrice, productOriginalPrice;
    private TextView productDescription, deliveryDetails, deliveryAddress, quantityText;
    private TextView readMore, viewAll, cartItemCount, cartTotalTextView;
    private MaterialButton decreaseQuantity, increaseQuantity, viewCartButton;
    private RecyclerView similarProductsRecyclerView;
    private BestsellerProductAdapter similarProductAdapter;
    private List<Product> similarProductList;
    private Product product;
    private int quantity = 0; // Default to 0
    private double priceToAdd = 0.0; // Class-level variable for price
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private int cartItems = 0; // Per-product cart items
    private double cartTotal = 0.0; // Per-product cart total

    public ProductDetailsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_product_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize Firestore and Auth
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // Check if user is authenticated
        if (auth.getCurrentUser() == null) {
            Toast.makeText(requireContext(), "Please log in to view product details.", Toast.LENGTH_LONG).show();
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new LoginFragment())
                    .commit();
            return;
        }

        // Initialize Toolbar
        Toolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setTitle("Loading..."); // Temporary title while fetching
        toolbar.setNavigationOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
        toolbar.inflateMenu(R.menu.cart_menu);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_cart) {
                Log.d(TAG, "Cart icon clicked");
                Toast.makeText(requireContext(), "Navigate to Cart", Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });

        // Initialize UI elements
        productImage = view.findViewById(R.id.productImage);
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

        // Get product data from arguments
        Bundle bundle = getArguments();
        final String[] categoryName = {"Product Details"}; // Default fallback
        if (bundle != null) {
            product = bundle.getParcelable("product"); // Use getParcelable to retrieve the Product
            if (product != null) {
                Log.d(TAG, "Product received: " + product.getName() + ", CategoryId: " + product.getCategoryId());
                if (product.getCategoryId() != null && !product.getCategoryId().isEmpty()) {
                    // Fetch category name from Firestore
                    db.collection("categories").document(product.getCategoryId())
                            .get()
                            .addOnSuccessListener(documentSnapshot -> {
                                if (documentSnapshot.exists()) {
                                    String fetchedName = documentSnapshot.getString("name");
                                    Log.d(TAG, "Category name fetched: " + fetchedName);
                                    categoryName[0] = fetchedName != null ? fetchedName : "Product Details";
                                    toolbar.setTitle(categoryName[0]);
                                } else {
                                    Log.w(TAG, "Category document does not exist for ID: " + product.getCategoryId());
                                    toolbar.setTitle("Product Details");
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to fetch category name: " + e.getMessage());
                                toolbar.setTitle("Product Details");
                            });
                } else {
                    Log.w(TAG, "CategoryId is null or empty");
                    toolbar.setTitle("Product Details");
                }
            } else {
                Log.e(TAG, "Product is null in bundle");
                toolbar.setTitle("Product Details");
            }
        } else {
            Log.e(TAG, "Bundle is null");
            toolbar.setTitle("Product Details");
        }

        // Populate UI with product data
        if (product != null) {
            Glide.with(requireContext()).load(product.getImageUrl()).into(productImage);

            // Set product rating without star (relying on XML icon)
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
            deliveryDetails.setText("EXPECTED DELIVERY BY " + product.getDeliveryDate());
            deliveryAddress.setText("DELIVERY ADDRESS: " + product.getDeliveryAddress());
        }

        // Handle "Read More" for description
        productDescription.post(() -> {
            if (productDescription.getLineCount() > 3) {
                readMore.setVisibility(View.VISIBLE);
            }
        });

        readMore.setOnClickListener(v -> {
            if (readMore.getText().toString().equals("READ MORE")) {
                productDescription.setMaxLines(Integer.MAX_VALUE);
                readMore.setText("READ LESS");
            } else {
                productDescription.setMaxLines(3);
                readMore.setText("READ MORE");
            }
        });

        // Quantity selector with instant cart update
        quantityText.setText(String.valueOf(quantity));

        decreaseQuantity.setOnClickListener(v -> {
            if (quantity > 0) {
                quantity--;
                quantityText.setText(String.valueOf(quantity));
                updateCartUI(-1, -priceToAdd);
                updateFirestoreCart(-1, -priceToAdd); // Decrease in Firestore
            }
        });

        increaseQuantity.setOnClickListener(v -> {
            quantity++;
            quantityText.setText(String.valueOf(quantity));
            updateCartUI(1, priceToAdd);
            updateFirestoreCart(1, priceToAdd); // Increase in Firestore
        });

        // Fetch initial cart data from Firestore for this product
        fetchCartDataForProduct();

        // Dummy View Cart button (to be implemented later)
        viewCartButton.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "View Cart - To be implemented later", Toast.LENGTH_SHORT).show();
        });

        // Setup Similar Products RecyclerView
        similarProductList = new ArrayList<>();
        similarProductAdapter = new BestsellerProductAdapter(requireContext(), similarProductList, this);
        similarProductsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        similarProductsRecyclerView.setAdapter(similarProductAdapter);

        // Fetch similar products
        fetchSimilarProducts();

        // Handle "View All" click
        viewAll.setOnClickListener(v -> {
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

        // Hide bottom navigation bar
        RelativeLayout bottomNav = requireActivity().findViewById(R.id.bottomNav);
        if (bottomNav != null) {
            bottomNav.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Show bottom navigation bar when leaving the fragment
        RelativeLayout bottomNav = requireActivity().findViewById(R.id.bottomNav);
        if (bottomNav != null) {
            bottomNav.setVisibility(View.VISIBLE);
        }
    }

    private void updateCartUI(int itemChange, double priceChange) {
        cartItems += itemChange;
        cartTotal += priceChange;
        if (cartItems < 0) cartItems = 0; // Prevent negative items
        if (cartTotal < 0) cartTotal = 0.0; // Prevent negative total
        cartItemCount.setText(cartItems + " items");
        cartTotalTextView.setText(String.format("RM%.2f", cartTotal));
    }

    private void fetchCartDataForProduct() {
        if (product == null || product.getId() == null) return;

        String userId = auth.getCurrentUser().getUid();
        DocumentReference userDoc = db.collection("users").document(userId);
        userDoc.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists() && documentSnapshot.contains("cart")) {
                Map<String, Object> cart = (Map<String, Object>) documentSnapshot.get("cart");
                if (cart != null) {
                    List<Map<String, Object>> items = (List<Map<String, Object>>) cart.get("items");
                    if (items != null) {
                        cartItems = 0;
                        cartTotal = 0.0;
                        for (Map<String, Object> item : items) {
                            String productId = (String) item.get("productId");
                            if (productId != null && productId.equals(product.getId())) {
                                Object priceObj = item.get("price");
                                Object qtyObj = item.get("quantity");
                                if (priceObj instanceof Double && qtyObj instanceof Long) {
                                    Double price = (Double) priceObj;
                                    Long qty = (Long) qtyObj;
                                    cartItems = qty.intValue();
                                    cartTotal = price * qty;
                                    quantity = cartItems; // Sync UI quantity with Firestore
                                    quantityText.setText(String.valueOf(quantity));
                                }
                            }
                        }
                        cartItemCount.setText(cartItems + " items");
                        cartTotalTextView.setText(String.format("RM%.2f", cartTotal));
                    }
                }
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to fetch cart data: " + e.getMessage());
            Toast.makeText(requireContext(), "Failed to load cart", Toast.LENGTH_SHORT).show();
        });
    }

    private void updateFirestoreCart(int qtyChange, double priceChange) {
        if (product == null || product.getId() == null) return;

        String userId = auth.getCurrentUser().getUid();
        DocumentReference userDoc = db.collection("users").document(userId);

        userDoc.get().addOnSuccessListener(documentSnapshot -> {
            Map<String, Object> cartItem = new HashMap<>();
            cartItem.put("productId", product.getId());
            cartItem.put("quantity", quantity);
            cartItem.put("price", priceToAdd);

            if (documentSnapshot.exists() && documentSnapshot.contains("cart")) {
                Map<String, Object> cart = (Map<String, Object>) documentSnapshot.get("cart");
                List<Map<String, Object>> items = (List<Map<String, Object>>) cart.get("items");
                boolean itemExists = false;

                if (items != null) {
                    for (int i = 0; i < items.size(); i++) {
                        Map<String, Object> item = items.get(i);
                        if (product.getId().equals(item.get("productId"))) {
                            itemExists = true;
                            if (quantity > 0) {
                                // Update existing item
                                items.set(i, cartItem);
                                userDoc.update("cart.items", items,
                                                "cart.total", FieldValue.increment(priceChange))
                                        .addOnSuccessListener(aVoid -> Log.d(TAG, "Cart item updated"));
                            } else {
                                // Remove item if quantity is 0
                                items.remove(i);
                                userDoc.update("cart.items", items,
                                                "cart.total", FieldValue.increment(priceChange))
                                        .addOnSuccessListener(aVoid -> Log.d(TAG, "Cart item removed"));
                            }
                            break;
                        }
                    }
                }

                if (!itemExists && quantity > 0) {
                    // Add new item
                    userDoc.update("cart.items", FieldValue.arrayUnion(cartItem),
                                    "cart.total", FieldValue.increment(priceChange))
                            .addOnSuccessListener(aVoid -> Log.d(TAG, "New cart item added"));
                }
            } else if (quantity > 0) {
                // Create new cart
                Map<String, Object> cart = new HashMap<>();
                List<Map<String, Object>> items = new ArrayList<>();
                items.add(cartItem);
                cart.put("items", items);
                cart.put("total", priceToAdd * quantity);
                userDoc.update("cart", cart)
                        .addOnSuccessListener(aVoid -> Log.d(TAG, "New cart created"));
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to update cart: " + e.getMessage());
            Toast.makeText(requireContext(), "Failed to update cart", Toast.LENGTH_SHORT).show();
        });
    }

    private void fetchSimilarProducts() {
        if (product == null || product.getCategoryId() == null || product.getCategoryId().isEmpty()) return;

        db.collection("products")
                .whereEqualTo("categoryId", product.getCategoryId())
                .whereNotEqualTo("id", product.getId())
                .limit(5)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    similarProductList.clear();
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
                        String deliveryAddress = doc.getString("deliveryAddress");
                        String categoryId = doc.getString("categoryId");

                        if (name != null && price != null && imageUrl != null && ratingLong != null && reviewCountLong != null && description != null && categoryId != null) {
                            int rating = ratingLong.intValue();
                            int reviewCount = reviewCountLong.intValue();
                            Product similarProduct = new Product(
                                    id,
                                    name,
                                    price,
                                    imageUrl,
                                    rating,
                                    reviewCount,
                                    quantity,
                                    originalPrice != null ? originalPrice : price,
                                    discountedPrice != null ? discountedPrice : 0.0,
                                    description,
                                    deliveryDate != null ? deliveryDate : "N/A",
                                    deliveryAddress != null ? deliveryAddress : "N/A",
                                    categoryId
                            );
                            similarProductList.add(similarProduct);
                        }
                    }
                    similarProductAdapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load similar products: " + e.getMessage());
                    Toast.makeText(requireContext(), "Failed to load similar products", Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onAddToCartClick(Product product) {
        String userId = auth.getCurrentUser().getUid();
        DocumentReference userDoc = db.collection("users").document(userId);

        double priceToAdd = product.getDiscountedPrice() > 0.0 ? product.getDiscountedPrice() : product.getOriginalPrice();
        Map<String, Object> cartItem = new HashMap<>();
        cartItem.put("productId", product.getId());
        cartItem.put("quantity", 1);
        cartItem.put("price", priceToAdd);

        userDoc.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists() && documentSnapshot.contains("cart")) {
                userDoc.update("cart.items", FieldValue.arrayUnion(cartItem),
                                "cart.total", FieldValue.increment(priceToAdd))
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(requireContext(), "Added to cart: " + product.getName(), Toast.LENGTH_SHORT).show();
                        });
            } else {
                Map<String, Object> cart = new HashMap<>();
                List<Map<String, Object>> items = new ArrayList<>();
                items.add(cartItem);
                cart.put("items", items);
                cart.put("total", priceToAdd);
                userDoc.update("cart", cart).addOnSuccessListener(aVoid -> {
                    Toast.makeText(requireContext(), "Added to cart: " + product.getName(), Toast.LENGTH_SHORT).show();
                });
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to add to cart: " + e.getMessage());
            Toast.makeText(requireContext(), "Failed to add to cart", Toast.LENGTH_SHORT).show();
        });
    }
}