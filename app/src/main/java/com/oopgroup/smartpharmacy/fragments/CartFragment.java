package com.oopgroup.smartpharmacy.fragments;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.oopgroup.smartpharmacy.MainActivity;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.adapters.CartAdapter;
import com.oopgroup.smartpharmacy.models.CartItem;
import com.oopgroup.smartpharmacy.models.Coupon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CartFragment extends Fragment implements CartAdapter.OnQuantityChangeListener, AddressDialogFragment.OnAddressSelectedListener {
    private static final String TAG = "CartFragment";
    private static final String CURRENCY = "RM";

    private RecyclerView rvCartItems;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView tvItemTotal, tvDiscount, tvDeliveryFee, tvItemCount, tvOriginalPrice, tvSummarySavedAmount;
    private EditText etCoupon;
    private Button btnApplyCoupon;
    private MaterialButton btnSelectAddress;
    private MaterialButton btnChangeAddress;
    private Toolbar toolbar;
    private SearchView searchView;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private List<CartItem> cartItems;
    private CartAdapter cartAdapter;
    private LinearLayout emptyCartLayout;
    private LinearLayout mainContentLayout;
    private Button btnGoShopping;
    private long lastUpdateTime = 0;
    private static final long DEBOUNCE_DELAY = 500;

    private double itemTotal = 0.0;
    private double originalTotal = 0.0;
    private double discount = 0.0;
    private double deliveryFee = 0.0;
    private double grandTotal = 0.0;

    private String selectedAddressId;
    private String appliedCouponCode;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_cart, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated called");

        toolbar = view.findViewById(R.id.toolbar);
        rvCartItems = view.findViewById(R.id.rv_cart_items);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);
        tvItemTotal = view.findViewById(R.id.tv_item_total);
        tvDiscount = view.findViewById(R.id.tv_discount);
        tvDeliveryFee = view.findViewById(R.id.tv_delivery_fee);
        tvItemCount = view.findViewById(R.id.tv_item_count);
        tvOriginalPrice = view.findViewById(R.id.tv_original_price);
        tvSummarySavedAmount = view.findViewById(R.id.tv_summary_saved_amount);
        etCoupon = view.findViewById(R.id.et_coupon);
        btnApplyCoupon = view.findViewById(R.id.btn_apply_coupon);
        btnSelectAddress = view.findViewById(R.id.btn_select_address);
        btnChangeAddress = view.findViewById(R.id.btn_change_address);
        emptyCartLayout = view.findViewById(R.id.empty_cart_layout);
        mainContentLayout = view.findViewById(R.id.main_content_layout);
        btnGoShopping = view.findViewById(R.id.btn_go_shopping);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        if (mAuth.getCurrentUser() == null) {
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new LoginFragment())
                    .commit();
            return;
        }

        setupToolbar();

        cartItems = new ArrayList<>();
        cartAdapter = new CartAdapter(getContext(), cartItems, this);
        rvCartItems.setLayoutManager(new LinearLayoutManager(getContext()));
        rvCartItems.setAdapter(cartAdapter);

        fetchCartItems();
        fetchInitialAddress();

        btnApplyCoupon.setOnClickListener(v -> {
            String couponCode = etCoupon.getText().toString().trim();
            if (!couponCode.isEmpty()) {
                applyCoupon(couponCode);
            } else {
                Toast.makeText(getContext(), "Please enter a coupon code", Toast.LENGTH_SHORT).show();
            }
        });

        btnChangeAddress.setOnClickListener(v -> showAddressDialog());

        btnSelectAddress.setOnClickListener(v -> {
            if (cartItems.isEmpty()) {
                Log.w(TAG, "Cart is empty, cannot proceed to checkout");
                Toast.makeText(getContext(), "Your cart is empty", Toast.LENGTH_SHORT).show();
                return;
            }
            if (selectedAddressId == null) {
                showAddressDialog();
            } else {
                updateSummary();
                CheckoutFragment checkoutFragment = new CheckoutFragment();
                Bundle args = new Bundle();
                args.putString("selected_address_id", selectedAddressId);
                args.putDouble("grand_total", grandTotal);
                args.putDouble("discount", discount);
                args.putDouble("delivery_fee", deliveryFee);
                args.putString("coupon_code", appliedCouponCode);
                checkoutFragment.setArguments(args);
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, checkoutFragment)
                        .addToBackStack("CheckoutFragment")
                        .commit();
            }
        });

        swipeRefreshLayout.setOnRefreshListener(() -> {
            fetchCartItems();
            fetchInitialAddress();
            swipeRefreshLayout.setRefreshing(false);
        });

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            private final Drawable backgroundDrawable = ContextCompat.getDrawable(getContext(), R.drawable.swipe_background);
            private final Drawable deleteIcon = ContextCompat.getDrawable(getContext(), R.drawable.premium_delete_icon);
            private final int iconMargin = (int) (12 * getResources().getDisplayMetrics().density);

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                CartItem itemToDelete = cartItems.get(position);
                Animation deleteAnimation = AnimationUtils.loadAnimation(getContext(), R.anim.delete_animation);
                viewHolder.itemView.startAnimation(deleteAnimation);
                deleteAnimation.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {}
                    @Override
                    public void onAnimationEnd(Animation animation) {
                        removeItem(position, itemToDelete);
                    }
                    @Override
                    public void onAnimationRepeat(Animation animation) {}
                });
            }

            private void removeItem(int position, CartItem itemToDelete) {
                String userId = mAuth.getCurrentUser().getUid();
                db.collection("cart")
                        .document(userId)
                        .collection("items")
                        .document(itemToDelete.getProductId())
                        .delete()
                        .addOnSuccessListener(aVoid -> {
                            requireActivity().runOnUiThread(() -> {
                                cartItems.remove(position);
                                cartAdapter.notifyItemRemoved(position);
                                updateSummary();
                                updateCartVisibility();
                            });
                        })
                        .addOnFailureListener(e -> {
                            requireActivity().runOnUiThread(() -> cartAdapter.notifyItemChanged(position));
                            Log.e(TAG, "Failed to delete item: " + e.getMessage());
                        });
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder,
                                    float dX, float dY, int actionState, boolean isCurrentlyActive) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    View itemView = viewHolder.itemView;
                    float left = itemView.getRight() + dX;
                    float right = itemView.getRight();
                    if (left < itemView.getLeft()) left = itemView.getLeft();
                    if (backgroundDrawable != null) {
                        backgroundDrawable.setBounds((int) left, itemView.getTop(), (int) right, itemView.getBottom());
                        backgroundDrawable.draw(c);
                    }
                    if (deleteIcon != null) {
                        int iconWidth = deleteIcon.getIntrinsicWidth();
                        int iconHeight = deleteIcon.getIntrinsicHeight();
                        int iconTop = itemView.getTop() + (itemView.getBottom() - itemView.getTop() - iconHeight) / 2;
                        int iconBottom = iconTop + iconHeight;
                        int iconRight = (int) (itemView.getRight() - iconMargin);
                        int iconLeft = iconRight - iconWidth;
                        if (iconLeft < left + iconMargin) {
                            iconLeft = (int) (left + iconMargin);
                            iconRight = iconLeft + iconWidth;
                        }
                        deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                        deleteIcon.draw(c);
                    }
                    super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
                }
            }
        });
        itemTouchHelper.attachToRecyclerView(rvCartItems);

        btnGoShopping.setOnClickListener(v -> {
            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity()).navigateToHome();
            } else {
                Log.e(TAG, "Activity is not MainActivity, cannot navigate to HomeFragment");
            }
        });

        hideBottomNavigation();
    }

    @Override
    public void onStart() {
        super.onStart();
        hideBottomNavigation();
    }

    @Override
    public void onResume() {
        super.onResume();
        hideBottomNavigation();
        fetchCartItems();
    }

    private void hideBottomNavigation() {
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).hideBottomNav();
            Log.d(TAG, "hideBottomNav() called in CartFragment");
        } else {
            Log.e(TAG, "Activity is not MainActivity, cannot hide bottom nav");
        }
    }

    private void setupToolbar() {
        toolbar.setTitle("Shopping Cart");
        toolbar.setNavigationOnClickListener(v -> {
            if (searchView != null && !searchView.isIconified()) {
                searchView.setQuery("", false);
                searchView.setIconified(true);
                toolbar.setTitle("Shopping Cart");
                return;
            }
            requireActivity().getSupportFragmentManager().popBackStack();
        });

        toolbar.inflateMenu(R.menu.menu_cart);
        toolbar.setOnMenuItemClickListener(item -> item.getItemId() == R.id.action_search);

        searchView = (SearchView) toolbar.getMenu().findItem(R.id.action_search).getActionView();
        searchView.setQueryHint("Search products...");
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return true;
            }
            @Override
            public boolean onQueryTextChange(String newText) {
                return true;
            }
        });

        toolbar.getMenu().findItem(R.id.action_search).setOnActionExpandListener(new androidx.appcompat.view.menu.MenuItemImpl.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(android.view.MenuItem item) {
                return true;
            }
            @Override
            public boolean onMenuItemActionCollapse(android.view.MenuItem item) {
                toolbar.setTitle("Shopping Cart");
                return true;
            }
        });
    }

    private void fetchCartItems() {
        String userId = mAuth.getCurrentUser().getUid();
        db.collection("cart")
                .document(userId)
                .collection("items")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Map<String, CartItem> mergedItems = new HashMap<>();
                    List<DocumentReference> docsToDelete = new ArrayList<>();

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        CartItem cartItem = doc.toObject(CartItem.class);
                        cartItem.setId(doc.getId());
                        String productId = cartItem.getProductId();

                        if (mergedItems.containsKey(productId)) {
                            CartItem existingItem = mergedItems.get(productId);
                            int newQuantity = existingItem.getQuantity() + cartItem.getQuantity();
                            double newTotal = existingItem.getTotal() + cartItem.getTotal();
                            existingItem.setQuantity(newQuantity);
                            existingItem.setTotal(newTotal);
                            if (!doc.getId().equals(productId)) {
                                docsToDelete.add(db.collection("cart").document(userId).collection("items").document(doc.getId()));
                            }
                        } else {
                            mergedItems.put(productId, cartItem);
                            if (!doc.getId().equals(productId)) {
                                docsToDelete.add(db.collection("cart").document(userId).collection("items").document(doc.getId()));
                            }
                        }
                    }

                    for (DocumentReference docRef : docsToDelete) {
                        docRef.delete();
                    }

                    for (CartItem item : mergedItems.values()) {
                        db.collection("cart").document(userId)
                                .collection("items")
                                .document(item.getProductId())
                                .set(item);
                    }

                    List<CartItem> newCartItems = new ArrayList<>(mergedItems.values());
                    updateCartItems(newCartItems);
                    Log.d(TAG, "Fetched and merged cart items: " + newCartItems.size());
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to fetch cart items: " + e.getMessage()));
    }

    private void fetchInitialAddress() {
        String userId = mAuth.getCurrentUser().getUid();
        db.collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists() && documentSnapshot.contains("lastSelectedAddressId")) {
                        selectedAddressId = documentSnapshot.getString("lastSelectedAddressId");
                        fetchDeliveryFeeForAddress(selectedAddressId);
                        updateButtonState();
                    } else {
                        db.collection("users")
                                .document(userId)
                                .collection("addresses")
                                .whereEqualTo("isDefault", true)
                                .limit(1)
                                .get()
                                .addOnSuccessListener(queryDocumentSnapshots -> {
                                    if (!queryDocumentSnapshots.isEmpty()) {
                                        QueryDocumentSnapshot doc = (QueryDocumentSnapshot) queryDocumentSnapshots.getDocuments().get(0);
                                        selectedAddressId = doc.getId();
                                        fetchDeliveryFeeForAddress(selectedAddressId);
                                        saveSelectedAddress(selectedAddressId);
                                        updateButtonState();
                                    } else {
                                        selectedAddressId = null;
                                        deliveryFee = 0.0;
                                        updateButtonState();
                                        updateSummary();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    selectedAddressId = null;
                                    deliveryFee = 0.0;
                                    updateButtonState();
                                    updateSummary();
                                    Log.e(TAG, "Failed to fetch default address: " + e.getMessage());
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    selectedAddressId = null;
                    deliveryFee = 0.0;
                    updateButtonState();
                    updateSummary();
                    Log.e(TAG, "Failed to fetch user document: " + e.getMessage());
                });
    }

    private void fetchDeliveryFeeForAddress(String addressId) {
        if (addressId == null) {
            deliveryFee = 0.0;
            updateSummary();
            return;
        }

        String userId = mAuth.getCurrentUser().getUid();
        db.collection("users")
                .document(userId)
                .collection("addresses")
                .document(addressId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String postalCode = doc.getString("postalCode");
                        if (postalCode != null) {
                            db.collection("delivery_fees")
                                    .document(postalCode)
                                    .get()
                                    .addOnSuccessListener(feeDoc -> {
                                        if (feeDoc.exists()) {
                                            double baseFee = feeDoc.getDouble("fee") != null ? feeDoc.getDouble("fee") : 0.0;
                                            double discount = feeDoc.getDouble("discount") != null ? feeDoc.getDouble("discount") : 0.0;
                                            boolean freeDelivery = feeDoc.getBoolean("freeDelivery") != null && feeDoc.getBoolean("freeDelivery");
                                            deliveryFee = freeDelivery ? 0.0 : Math.max(baseFee - discount, 0.0);
                                        } else {
                                            deliveryFee = 0.0;
                                        }
                                        updateSummary();
                                    })
                                    .addOnFailureListener(e -> {
                                        deliveryFee = 0.0;
                                        updateSummary();
                                        Log.e(TAG, "Failed to fetch delivery fee: " + e.getMessage());
                                    });
                        } else {
                            deliveryFee = 0.0;
                            updateSummary();
                        }
                    } else {
                        deliveryFee = 0.0;
                        updateSummary();
                    }
                })
                .addOnFailureListener(e -> {
                    deliveryFee = 0.0;
                    updateSummary();
                    Log.e(TAG, "Failed to fetch address: " + e.getMessage());
                });
    }

    private void saveSelectedAddress(String addressId) {
        String userId = mAuth.getCurrentUser().getUid();
        db.collection("users")
                .document(userId)
                .update("lastSelectedAddressId", addressId)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Selected address saved: " + addressId))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to save selected address: " + e.getMessage()));
    }

    private void applyCoupon(String couponCode) {
        db.collection("coupons")
                .whereEqualTo("code", couponCode)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        QueryDocumentSnapshot doc = (QueryDocumentSnapshot) queryDocumentSnapshots.getDocuments().get(0);
                        Coupon coupon = doc.toObject(Coupon.class);
                        coupon.setId(doc.getId());
                        discount = coupon.getDiscount();
                        appliedCouponCode = couponCode;
                        Toast.makeText(getContext(), "Coupon applied! Discount: " + CURRENCY + " " + String.format("%.2f", discount), Toast.LENGTH_SHORT).show();
                        updateSummary();
                    } else {
                        discount = 0.0;
                        appliedCouponCode = null;
                        Toast.makeText(getContext(), "Invalid or expired coupon code", Toast.LENGTH_SHORT).show();
                        updateSummary();
                    }
                })
                .addOnFailureListener(e -> {
                    discount = 0.0;
                    appliedCouponCode = null;
                    Toast.makeText(getContext(), "Error applying coupon: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Failed to fetch coupon: " + e.getMessage());
                    updateSummary();
                });
    }

    private void updateSummary() {
        originalTotal = 0.0;
        itemTotal = 0.0;
        for (CartItem item : cartItems) {
            originalTotal += item.getOriginalPrice() * item.getQuantity();
            itemTotal += item.getTotal();
        }
        grandTotal = itemTotal - discount + deliveryFee;
        double savedAmount = originalTotal - grandTotal;

        tvItemTotal.setText(String.format("%s %.2f", CURRENCY, itemTotal));
        tvDiscount.setText(String.format("%s %.2f", CURRENCY, discount));
        tvDeliveryFee.setText(deliveryFee == 0.0 ? "Free" : String.format("%s %.2f", CURRENCY, deliveryFee));
        tvItemCount.setText(String.format("%d items", cartItems.size()));
        tvOriginalPrice.setText(String.format("%s %.2f", CURRENCY, grandTotal));
        tvSummarySavedAmount.setText(String.format("%s %.2f", CURRENCY, savedAmount));
        tvSummarySavedAmount.setVisibility(savedAmount > 0 ? View.VISIBLE : View.GONE);
        ((View) tvSummarySavedAmount.getParent()).findViewById(R.id.label_summary_saved_amount).setVisibility(savedAmount > 0 ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onQuantityChanged(CartItem cartItem, int newQuantity) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime < DEBOUNCE_DELAY) return;
        lastUpdateTime = currentTime;

        String userId = mAuth.getCurrentUser().getUid();
        double price = cartItem.getDiscountedPrice() != 0 ? cartItem.getDiscountedPrice() : cartItem.getOriginalPrice();
        double newTotal = price * newQuantity;

        DocumentReference cartItemRef = db.collection("cart")
                .document(userId)
                .collection("items")
                .document(cartItem.getProductId());

        if (newQuantity > 0) {
            cartItemRef.update(
                    "quantity", newQuantity,
                    "total", newTotal,
                    "addedAt", com.google.firebase.Timestamp.now()
            ).addOnSuccessListener(aVoid -> {
                cartItem.setTotal(newTotal);
                cartItem.setQuantity(newQuantity);
                updateSummary();
                updateCartVisibility();
            }).addOnFailureListener(e -> Log.e(TAG, "Failed to update quantity: " + e.getMessage()));
        } else {
            cartItemRef.delete().addOnSuccessListener(aVoid -> {
                cartItems.remove(cartItem);
                cartAdapter.notifyDataSetChanged();
                updateSummary();
                updateCartVisibility();
            }).addOnFailureListener(e -> Log.e(TAG, "Failed to delete item: " + e.getMessage()));
        }
    }

    @Override
    public void onAddressSelected(String addressId, String address) {
        selectedAddressId = addressId;
        saveSelectedAddress(addressId);
        fetchDeliveryFeeForAddress(addressId);
        updateButtonState();
    }

    private void showAddressDialog() {
        AddressDialogFragment dialog = new AddressDialogFragment();
        dialog.setOnAddressSelectedListener(this::onAddressSelected);
        dialog.show(getChildFragmentManager(), "AddressDialogFragment");
    }

    private void updateButtonState() {
        if (selectedAddressId == null) {
            btnSelectAddress.setText("Select Address");
            btnSelectAddress.setTextColor(getResources().getColor(android.R.color.black));
            btnSelectAddress.setBackgroundResource(R.drawable.btn_interactive_white);
            btnChangeAddress.setVisibility(View.GONE);
        } else {
            btnSelectAddress.setText("Checkout");
            btnSelectAddress.setTextColor(getResources().getColor(android.R.color.black));
            btnSelectAddress.setBackgroundResource(R.drawable.btn_interactive_white);
            btnChangeAddress.setVisibility(View.VISIBLE);
        }
        updateSummary();
    }

    private void updateCartVisibility() {
        if (cartItems.isEmpty()) {
            emptyCartLayout.setVisibility(View.VISIBLE);
            mainContentLayout.setVisibility(View.GONE);
        } else {
            emptyCartLayout.setVisibility(View.GONE);
            mainContentLayout.setVisibility(View.VISIBLE);
        }
    }

    private static class CartDiffCallback extends DiffUtil.Callback {
        private final List<CartItem> oldList;
        private final List<CartItem> newList;

        CartDiffCallback(List<CartItem> oldList, List<CartItem> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldList.get(oldItemPosition).getId().equals(newList.get(newItemPosition).getId());
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            CartItem oldItem = oldList.get(oldItemPosition);
            CartItem newItem = newList.get(newItemPosition);
            return oldItem.getQuantity() == newItem.getQuantity() && oldItem.getTotal() == newItem.getTotal();
        }
    }

    private void updateCartItems(List<CartItem> newCartItems) {
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new CartDiffCallback(cartItems, newCartItems));
        cartItems.clear();
        cartItems.addAll(newCartItems);
        diffResult.dispatchUpdatesTo(cartAdapter);
        updateSummary();
        updateCartVisibility();
    }
}