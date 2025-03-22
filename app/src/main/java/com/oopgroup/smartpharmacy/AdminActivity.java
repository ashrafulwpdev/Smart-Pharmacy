package com.oopgroup.smartpharmacy;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.StorageMetadata;
import com.oopgroup.smartpharmacy.adapters.AdminItemAdapter;
import com.oopgroup.smartpharmacy.models.Banner;
import com.oopgroup.smartpharmacy.models.Category;
import com.oopgroup.smartpharmacy.models.LabTest;
import com.oopgroup.smartpharmacy.models.Product;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.Manifest;
import android.content.pm.PackageManager;

public class AdminActivity extends AppCompatActivity implements AdminItemAdapter.OnItemClickListener, NavigationView.OnNavigationItemSelectedListener {
    private static final String TAG = "AdminActivity";
    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int MAX_IMAGE_SIZE_MB = 5;

    // UI Components
    private DrawerLayout drawerLayout;
    private NavigationView navView;
    private Toolbar toolbar;
    private CardView bannerSection, categoriesSection, labTestsSection, productsSection, usersSection, ordersSection, notificationsSection, scannerSection;
    private EditText bannerTitleInput, bannerDescriptionInput, bannerDiscountInput;
    private EditText categoryNameInput;
    private EditText labTestNameInput;
    private EditText productNameInput, productPriceInput, productRatingInput, productReviewCountInput, productQuantityInput, productDiscountedPriceInput;
    private EditText notificationTitleInput, notificationMessageInput, scannerInstructionsInput;
    private MaterialButton uploadBannerImageButton, uploadCategoryImageButton, uploadLabTestImageButton, uploadProductImageButton;
    private MaterialButton addBannerButton, addCategoryButton, addLabTestButton, addProductButton, sendNotificationButton, updateScannerSettingsButton;
    private MaterialButton refreshUsersButton, refreshOrdersButton;
    private Spinner categorySpinner, notificationTargetSpinner;
    private RecyclerView adminRecyclerView;
    private TextView emptyView;

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private DatabaseReference bannersRef, categoriesRef, labTestsRef, productsRef, usersRef;
    private FirebaseFirestore firestore;
    private StorageReference storageRef;

    // Data
    private AdminItemAdapter adminAdapter;
    private List<Object> itemList;
    private List<String> categoryNames;
    private List<String> categoryIds;
    private Uri imageUri;
    private String currentTab = "banner";
    private Banner currentBanner;
    private boolean isAdmin = false;

    // Activity Result API for image picking
    private final ActivityResultLauncher<String> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    imageUri = uri;
                    Toast.makeText(this, "Image selected", Toast.LENGTH_SHORT).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        initializeFirebaseReferences();

        if (currentUser == null) {
            redirectToLogin();
            return;
        }

        Log.d(TAG, "Current User UID: " + currentUser.getUid());

        // Setup Toolbar and Drawer
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawerLayout = findViewById(R.id.drawerLayout);
        navView = findViewById(R.id.navView);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        navView.setNavigationItemSelectedListener(this);

        checkUserRole();
    }

    private void initializeFirebaseReferences() {
        bannersRef = FirebaseDatabase.getInstance().getReference("banners");
        categoriesRef = FirebaseDatabase.getInstance().getReference("categories");
        labTestsRef = FirebaseDatabase.getInstance().getReference("labTests");
        productsRef = FirebaseDatabase.getInstance().getReference("products");
        usersRef = FirebaseDatabase.getInstance().getReference("users");
        firestore = FirebaseFirestore.getInstance();
        storageRef = FirebaseStorage.getInstance().getReference();
    }

    private void redirectToLogin() {
        Toast.makeText(this, "Please log in to access admin panel.", Toast.LENGTH_LONG).show();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private void initializeUI() {
        findViews();
        setupRecyclerView();
        setupClickListeners();
        fetchCategoriesForSpinner();
        showSection("banner"); // Default section
    }

    private void findViews() {
        bannerSection = findViewById(R.id.bannerSection);
        categoriesSection = findViewById(R.id.categoriesSection);
        labTestsSection = findViewById(R.id.labTestsSection);
        productsSection = findViewById(R.id.productsSection);
        usersSection = findViewById(R.id.usersSection);
        ordersSection = findViewById(R.id.ordersSection);
        notificationsSection = findViewById(R.id.notificationsSection);
        scannerSection = findViewById(R.id.scannerSection);
        bannerTitleInput = findViewById(R.id.bannerTitleInput);
        bannerDescriptionInput = findViewById(R.id.bannerDescriptionInput);
        bannerDiscountInput = findViewById(R.id.bannerDiscountInput);
        categoryNameInput = findViewById(R.id.categoryNameInput);
        labTestNameInput = findViewById(R.id.labTestNameInput);
        productNameInput = findViewById(R.id.productNameInput);
        productPriceInput = findViewById(R.id.productPriceInput);
        productRatingInput = findViewById(R.id.productRatingInput);
        productReviewCountInput = findViewById(R.id.productReviewCountInput);
        productQuantityInput = findViewById(R.id.productQuantityInput);
        productDiscountedPriceInput = findViewById(R.id.productDiscountedPriceInput);
        notificationTitleInput = findViewById(R.id.notificationTitleInput);
        notificationMessageInput = findViewById(R.id.notificationMessageInput);
        scannerInstructionsInput = findViewById(R.id.scannerInstructionsInput);
        uploadBannerImageButton = findViewById(R.id.uploadBannerImageButton);
        uploadCategoryImageButton = findViewById(R.id.uploadCategoryImageButton);
        uploadLabTestImageButton = findViewById(R.id.uploadLabTestImageButton);
        uploadProductImageButton = findViewById(R.id.uploadProductImageButton);
        addBannerButton = findViewById(R.id.addBannerButton);
        addCategoryButton = findViewById(R.id.addCategoryButton);
        addLabTestButton = findViewById(R.id.addLabTestButton);
        addProductButton = findViewById(R.id.addProductButton);
        refreshUsersButton = findViewById(R.id.refreshUsersButton);
        refreshOrdersButton = findViewById(R.id.refreshOrdersButton);
        sendNotificationButton = findViewById(R.id.sendNotificationButton);
        updateScannerSettingsButton = findViewById(R.id.updateScannerSettingsButton);
        categorySpinner = findViewById(R.id.categorySpinner);
        notificationTargetSpinner = findViewById(R.id.notificationTargetSpinner);
        adminRecyclerView = findViewById(R.id.adminRecyclerView);
        emptyView = findViewById(R.id.emptyView);
    }

    private void setupRecyclerView() {
        adminRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        adminRecyclerView.setHasFixedSize(true);
        itemList = new ArrayList<>();
        adminAdapter = new AdminItemAdapter(itemList, this::onItemClick, this::onDeleteItem);
        adminRecyclerView.setAdapter(adminAdapter);
        adminRecyclerView.setVisibility(View.VISIBLE);
        Log.d(TAG, "RecyclerView setup completed, adapter attached: " + (adminRecyclerView.getAdapter() != null));
    }

    private void setupClickListeners() {
        uploadBannerImageButton.setOnClickListener(v -> openImagePicker());
        uploadCategoryImageButton.setOnClickListener(v -> openImagePicker());
        uploadLabTestImageButton.setOnClickListener(v -> openImagePicker());
        uploadProductImageButton.setOnClickListener(v -> openImagePicker());
        addBannerButton.setOnClickListener(v -> addOrUpdateBanner());
        addCategoryButton.setOnClickListener(v -> addCategory());
        addLabTestButton.setOnClickListener(v -> addLabTest());
        addProductButton.setOnClickListener(v -> addProduct());
        refreshUsersButton.setOnClickListener(v -> fetchUsers());
        refreshOrdersButton.setOnClickListener(v -> fetchOrders());
        sendNotificationButton.setOnClickListener(v -> sendNotification());
        updateScannerSettingsButton.setOnClickListener(v -> updateScannerSettings());
    }

    private void checkUserRole() {
        if (currentUser == null) {
            redirectToLogin();
            return;
        }
        firestore.collection("users").document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        Log.e(TAG, "User document does not exist!");
                        Toast.makeText(this, "User not found", Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }
                    String role = documentSnapshot.getString("role");
                    if (role == null) {
                        Log.e(TAG, "Role field is missing!");
                        Toast.makeText(this, "Role not set", Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }
                    syncRoleToRealtimeDatabase(currentUser.getUid(), role);
                    if ("admin".equals(role)) {
                        isAdmin = true;
                        initializeUI();
                    } else {
                        Toast.makeText(this, "Access denied. Admins only.", Toast.LENGTH_LONG).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check role: " + e.getMessage());
                    Toast.makeText(this, "Error verifying role: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    finish();
                });
    }

    private void syncRoleToRealtimeDatabase(String uid, String role) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("role", role);
        usersRef.child(uid).updateChildren(userData)
                .addOnFailureListener(e -> Log.e(TAG, "Failed to sync role: " + e.getMessage()));
    }

    private void showSection(String section) {
        Log.d(TAG, "Showing section: " + section);
        currentTab = section;
        bannerSection.setVisibility(section.equals("banner") ? View.VISIBLE : View.GONE);
        categoriesSection.setVisibility(section.equals("categories") ? View.VISIBLE : View.GONE);
        labTestsSection.setVisibility(section.equals("labTests") ? View.VISIBLE : View.GONE);
        productsSection.setVisibility(section.equals("products") ? View.VISIBLE : View.GONE);
        usersSection.setVisibility(section.equals("users") ? View.VISIBLE : View.GONE);
        ordersSection.setVisibility(section.equals("orders") ? View.VISIBLE : View.GONE);
        notificationsSection.setVisibility(section.equals("notifications") ? View.VISIBLE : View.GONE);
        scannerSection.setVisibility(section.equals("scanner") ? View.VISIBLE : View.GONE);

        itemList.clear();
        adminAdapter.updateItems(itemList);
        switch (section) {
            case "banner":
                resetBannerForm();
                fetchBannerForEdit();
                break;
            case "categories":
                fetchCategories();
                break;
            case "labTests":
                fetchLabTests();
                break;
            case "products":
                fetchProducts();
                break;
            case "users":
                fetchUsers();
                break;
            case "orders":
                fetchOrders();
                break;
            case "notifications":
            case "scanner":
                adminAdapter.notifyDataSetChanged();
                break;
        }
    }

    private void showLoading(boolean isLoading) {
        Log.d(TAG, "Loading state: " + isLoading);
        // TODO: Implement a loading indicator (e.g., ProgressBar) if needed
    }

    private void openImagePicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_MEDIA_IMAGES}, PICK_IMAGE_REQUEST);
                return;
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PICK_IMAGE_REQUEST);
                return;
            }
        }
        imagePickerLauncher.launch("image/*");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PICK_IMAGE_REQUEST && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openImagePicker();
            } else {
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[0])) {
                    Toast.makeText(this, "Permission denied. Please enable it in app settings.", Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                } else {
                    Toast.makeText(this, "Permission denied to access images", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void uploadImageAndExecute(String path, OnImageUploadListener listener) {
        if (imageUri == null || currentUser == null) {
            Toast.makeText(this, "Please select an image or log in.", Toast.LENGTH_SHORT).show();
            return;
        }

        try (InputStream inputStream = getContentResolver().openInputStream(imageUri)) {
            int fileSize = inputStream != null ? inputStream.available() : 0;
            if (fileSize > MAX_IMAGE_SIZE_MB * 1024 * 1024) {
                Toast.makeText(this, "File size exceeds " + MAX_IMAGE_SIZE_MB + " MB.", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to check file size: " + e.getMessage());
            Toast.makeText(this, "Error checking file size.", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);
        String fileName = System.currentTimeMillis() + ".jpg";
        StorageReference fileRef = storageRef.child(path).child(fileName);
        StorageMetadata metadata = new StorageMetadata.Builder()
                .setContentType(getContentResolver().getType(imageUri) != null && getContentResolver().getType(imageUri).startsWith("image/") ? getContentResolver().getType(imageUri) : "image/jpeg")
                .build();

        fileRef.putFile(imageUri, metadata)
                .addOnSuccessListener(taskSnapshot -> fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    String imageUrl = uri.toString();
                    imageUri = null;
                    showLoading(false);
                    listener.onSuccess(imageUrl);
                }))
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Image upload failed: " + e.getMessage());
                    Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private interface OnImageUploadListener {
        void onSuccess(String imageUrl);
    }

    private void addOrUpdateBanner() {
        String title = bannerTitleInput.getText().toString().trim();
        String description = bannerDescriptionInput.getText().toString().trim();
        String discount = bannerDiscountInput.getText().toString().trim();

        if (title.isEmpty() || description.isEmpty() || discount.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> bannerData = new HashMap<>();
        bannerData.put("title", title);
        bannerData.put("description", description);
        bannerData.put("discount", discount);

        if (imageUri != null) {
            uploadImageAndExecute("banners", imageUrl -> {
                bannerData.put("imageUrl", imageUrl);
                saveBanner(bannerData);
            });
        } else if (currentBanner != null && currentBanner.getImageUrl() != null) {
            bannerData.put("imageUrl", currentBanner.getImageUrl());
            saveBanner(bannerData);
        } else {
            Toast.makeText(this, "Please upload an image", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveBanner(Map<String, Object> bannerData) {
        showLoading(true);
        String bannerId = currentBanner != null ? currentBanner.getId() : bannersRef.push().getKey();
        bannersRef.child(bannerId).setValue(bannerData)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    Toast.makeText(this, "Banner " + (currentBanner != null ? "updated" : "added"), Toast.LENGTH_SHORT).show();
                    resetBannerForm();
                    fetchBannerForEdit();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Failed to save banner: " + e.getMessage());
                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void resetBannerForm() {
        bannerTitleInput.setText("");
        bannerDescriptionInput.setText("");
        bannerDiscountInput.setText("");
        currentBanner = null;
        addBannerButton.setText(R.string.add_update_banner);
        imageUri = null;
    }

    private void addCategory() {
        String name = categoryNameInput.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter category name", Toast.LENGTH_SHORT).show();
            return;
        }

        if (imageUri == null) {
            Toast.makeText(this, "Please upload an image", Toast.LENGTH_SHORT).show();
            return;
        }

        uploadImageAndExecute("categories", imageUrl -> {
            String categoryId = categoriesRef.push().getKey();
            Category category = new Category(categoryId, name, 0, imageUrl);
            saveCategory(category);
        });
    }

    private void saveCategory(Category category) {
        showLoading(true);
        categoriesRef.child(category.getId()).setValue(category)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    Toast.makeText(this, "Category added", Toast.LENGTH_SHORT).show();
                    categoryNameInput.setText("");
                    imageUri = null;
                    fetchCategories();
                    fetchCategoriesForSpinner();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Failed to add category: " + e.getMessage());
                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void addLabTest() {
        String name = labTestNameInput.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter lab test name", Toast.LENGTH_SHORT).show();
            return;
        }

        if (imageUri == null) {
            Toast.makeText(this, "Please upload an image", Toast.LENGTH_SHORT).show();
            return;
        }

        uploadImageAndExecute("labTests", imageUrl -> {
            String labTestId = labTestsRef.push().getKey();
            LabTest labTest = new LabTest(labTestId, name, imageUrl);
            saveLabTest(labTest);
        });
    }

    private void saveLabTest(LabTest labTest) {
        showLoading(true);
        labTestsRef.child(labTest.getId()).setValue(labTest)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    Toast.makeText(this, "Lab Test added", Toast.LENGTH_SHORT).show();
                    labTestNameInput.setText("");
                    imageUri = null;
                    fetchLabTests();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Failed to add lab test: " + e.getMessage());
                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void addProduct() {
        String name = productNameInput.getText().toString().trim();
        String quantity = productQuantityInput.getText().toString().trim();
        String priceStr = productPriceInput.getText().toString().trim();
        String discountedPriceStr = productDiscountedPriceInput.getText().toString().trim();
        String ratingStr = productRatingInput.getText().toString().trim();
        String reviewCountStr = productReviewCountInput.getText().toString().trim();
        int categoryPosition = categorySpinner.getSelectedItemPosition();

        if (categoryPosition < 0 || categoryIds.isEmpty()) {
            Toast.makeText(this, "Please select a category", Toast.LENGTH_SHORT).show();
            return;
        }
        String categoryId = categoryIds.get(categoryPosition);

        if (name.isEmpty() || quantity.isEmpty() || priceStr.isEmpty() || ratingStr.isEmpty() || reviewCountStr.isEmpty()) {
            Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (imageUri == null) {
            Toast.makeText(this, "Please upload an image", Toast.LENGTH_SHORT).show();
            return;
        }

        double price;
        double discountedPrice;
        int rating;
        int reviewCount;

        try {
            price = Double.parseDouble(priceStr);
            discountedPrice = discountedPriceStr.isEmpty() ? 0.0 : Double.parseDouble(discountedPriceStr);
            rating = Integer.parseInt(ratingStr);
            reviewCount = Integer.parseInt(reviewCountStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid number format in price, rating, or review count", Toast.LENGTH_SHORT).show();
            return;
        }

        uploadImageAndExecute("products", imageUrl -> {
            String productId = productsRef.child(categoryId).push().getKey();
            Product product = new Product(productId, name, price, imageUrl, rating, reviewCount, quantity, price, discountedPrice);
            saveProduct(categoryId, product);
        });
    }

    private void saveProduct(String categoryId, Product product) {
        showLoading(true);
        productsRef.child(categoryId).child(product.getId()).setValue(product)
                .addOnSuccessListener(aVoid -> {
                    updateProductCount(categoryId, 1);
                    showLoading(false);
                    Toast.makeText(this, "Product added", Toast.LENGTH_SHORT).show();
                    resetProductForm();
                    fetchProducts();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Failed to add product: " + e.getMessage());
                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void updateProductCount(String categoryId, int change) {
        categoriesRef.child(categoryId).runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                Category category = mutableData.getValue(Category.class);
                if (category == null) return Transaction.success(mutableData);
                int newCount = category.getProductCount() + change;
                if (newCount < 0) newCount = 0;
                category.setProductCount(newCount);
                mutableData.setValue(category);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                if (error != null) {
                    Log.e(TAG, "Failed to update product count: " + error.getMessage());
                }
            }
        });
    }

    private void resetProductForm() {
        productNameInput.setText("");
        productQuantityInput.setText("");
        productPriceInput.setText("");
        productDiscountedPriceInput.setText("");
        productRatingInput.setText("");
        productReviewCountInput.setText("");
        imageUri = null;
    }

    private void fetchBannerForEdit() {
        showLoading(true);
        Log.d(TAG, "Starting to fetch banners from Firebase");
        bannersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                itemList.clear();
                Log.d(TAG, "Fetching banner, snapshot exists: " + snapshot.exists() + ", children count: " + snapshot.getChildrenCount());
                for (DataSnapshot bannerSnapshot : snapshot.getChildren()) {
                    Banner banner = bannerSnapshot.getValue(Banner.class);
                    if (banner != null) {
                        banner.setId(bannerSnapshot.getKey());
                        itemList.add(banner);
                        Log.d(TAG, "Added banner: " + banner.getTitle() + ", Image URL: " + banner.getImageUrl());
                    } else {
                        Log.w(TAG, "Failed to parse banner at key: " + bannerSnapshot.getKey());
                    }
                }
                adminAdapter.updateItems(itemList);
                showLoading(false);
                updateRecyclerViewVisibility();
                resetBannerForm();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showLoading(false);
                Log.e(TAG, "Failed to fetch banners: " + error.getMessage());
                Toast.makeText(AdminActivity.this, "Fetch banners failed: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void fetchCategories() {
        showLoading(true);
        categoriesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                itemList.clear();
                Log.d(TAG, "Categories snapshot exists: " + snapshot.exists() + ", count: " + snapshot.getChildrenCount());
                for (DataSnapshot categorySnapshot : snapshot.getChildren()) {
                    Category category = categorySnapshot.getValue(Category.class);
                    if (category != null) {
                        category.setId(categorySnapshot.getKey());
                        itemList.add(category);
                        Log.d(TAG, "Added category: " + category.getName());
                    }
                }
                adminAdapter.updateItems(itemList);
                showLoading(false);
                updateRecyclerViewVisibility();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showLoading(false);
                Log.e(TAG, "Failed to fetch categories: " + error.getMessage());
                Toast.makeText(AdminActivity.this, "Failed to fetch categories: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void fetchLabTests() {
        showLoading(true);
        labTestsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                itemList.clear();
                Log.d(TAG, "Fetching lab tests, snapshot exists: " + snapshot.exists());
                for (DataSnapshot labTestSnapshot : snapshot.getChildren()) {
                    LabTest labTest = labTestSnapshot.getValue(LabTest.class);
                    if (labTest != null) {
                        labTest.setId(labTestSnapshot.getKey());
                        itemList.add(labTest);
                        Log.d(TAG, "Added lab test: " + labTest.getName());
                    }
                }
                adminAdapter.updateItems(itemList);
                showLoading(false);
                updateRecyclerViewVisibility();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showLoading(false);
                Log.e(TAG, "Failed to fetch lab tests: " + error.getMessage());
                Toast.makeText(AdminActivity.this, "Failed to fetch lab tests: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void fetchProducts() {
        showLoading(true);
        productsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                itemList.clear();
                Log.d(TAG, "Fetching products, snapshot exists: " + snapshot.exists());
                for (DataSnapshot categorySnapshot : snapshot.getChildren()) {
                    for (DataSnapshot productSnapshot : categorySnapshot.getChildren()) {
                        Product product = productSnapshot.getValue(Product.class);
                        if (product != null) {
                            product.setId(productSnapshot.getKey());
                            itemList.add(product);
                            Log.d(TAG, "Added product: " + product.getName());
                        }
                    }
                }
                adminAdapter.updateItems(itemList);
                showLoading(false);
                updateRecyclerViewVisibility();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showLoading(false);
                Log.e(TAG, "Failed to fetch products: " + error.getMessage());
                Toast.makeText(AdminActivity.this, "Failed to fetch products: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void fetchUsers() {
        showLoading(true);
        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                itemList.clear();
                for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                    String userId = userSnapshot.getKey();
                    String role = userSnapshot.child("role").getValue(String.class);
                    if (role != null && !"admin".equals(role)) {
                        Map<String, Object> user = new HashMap<>();
                        user.put("id", userId);
                        user.put("name", userSnapshot.child("name").getValue(String.class));
                        user.put("email", userSnapshot.child("email").getValue(String.class));
                        itemList.add(user);
                    }
                }
                adminAdapter.updateItems(itemList);
                showLoading(false);
                updateRecyclerViewVisibility();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showLoading(false);
                Log.e(TAG, "Failed to fetch users: " + error.getMessage());
                Toast.makeText(AdminActivity.this, "Failed to fetch users: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void fetchOrders() {
        showLoading(true);
        DatabaseReference ordersRef = FirebaseDatabase.getInstance().getReference("orders");
        ordersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                itemList.clear();
                for (DataSnapshot orderSnapshot : snapshot.getChildren()) {
                    Map<String, Object> order = new HashMap<>();
                    order.put("id", orderSnapshot.getKey());
                    order.put("userId", orderSnapshot.child("userId").getValue(String.class));
                    order.put("status", orderSnapshot.child("status").getValue(String.class));
                    itemList.add(order);
                }
                adminAdapter.updateItems(itemList);
                showLoading(false);
                updateRecyclerViewVisibility();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showLoading(false);
                Log.e(TAG, "Failed to fetch orders: " + error.getMessage());
                Toast.makeText(AdminActivity.this, "Failed to fetch orders: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void sendNotification() {
        String title = notificationTitleInput.getText().toString().trim();
        String message = notificationMessageInput.getText().toString().trim();
        String target = notificationTargetSpinner.getSelectedItem() != null ? notificationTargetSpinner.getSelectedItem().toString() : "";

        if (title.isEmpty() || message.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);
        new android.os.Handler().postDelayed(() -> {
            showLoading(false);
            Toast.makeText(this, "Notification sent to " + target, Toast.LENGTH_SHORT).show();
            notificationTitleInput.setText("");
            notificationMessageInput.setText("");
        }, 2000);
    }

    private void updateScannerSettings() {
        String instructions = scannerInstructionsInput.getText().toString().trim();
        if (instructions.isEmpty()) {
            Toast.makeText(this, "Please provide instructions", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);
        DatabaseReference scannerRef = FirebaseDatabase.getInstance().getReference("scannerSettings");
        Map<String, Object> settings = new HashMap<>();
        settings.put("instructions", instructions);
        scannerRef.setValue(settings)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    Toast.makeText(this, "Scanner settings updated", Toast.LENGTH_SHORT).show();
                    scannerInstructionsInput.setText("");
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Failed to update scanner settings: " + e.getMessage());
                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void fetchCategoriesForSpinner() {
        categoryNames = new ArrayList<>();
        categoryIds = new ArrayList<>();

        categoriesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                categoryNames.clear();
                categoryIds.clear();
                for (DataSnapshot categorySnapshot : snapshot.getChildren()) {
                    String id = categorySnapshot.getKey();
                    String name = categorySnapshot.child("name").getValue(String.class);
                    if (name != null) {
                        categoryNames.add(name);
                        categoryIds.add(id);
                    }
                }
                ArrayAdapter<String> adapter = new ArrayAdapter<>(AdminActivity.this, android.R.layout.simple_spinner_item, categoryNames);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                categorySpinner.setAdapter(adapter);

                ArrayAdapter<CharSequence> notificationAdapter = ArrayAdapter.createFromResource(
                        AdminActivity.this, R.array.notification_targets, android.R.layout.simple_spinner_item);
                notificationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                notificationTargetSpinner.setAdapter(notificationAdapter);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to fetch categories for spinner: " + error.getMessage());
                Toast.makeText(AdminActivity.this, "Failed to load categories for spinner: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onItemClick(Object item) {
        if (item instanceof Banner) {
            editBanner((Banner) item);
        } else if (item instanceof Category) {
            editCategory((Category) item);
        } else if (item instanceof LabTest) {
            editLabTest((LabTest) item);
        } else if (item instanceof Product) {
            editProduct((Product) item);
        } else if (item instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) item;
            if (map.containsKey("email")) {
                Toast.makeText(this, "Editing user: " + map.get("email"), Toast.LENGTH_SHORT).show();
            } else if (map.containsKey("status")) {
                Toast.makeText(this, "Editing order: " + map.get("id"), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void onDeleteItem(Object item) {
        if (item instanceof Banner) {
            deleteBanner((Banner) item);
        } else if (item instanceof Category) {
            deleteCategory((Category) item);
        } else if (item instanceof LabTest) {
            deleteLabTest((LabTest) item);
        } else if (item instanceof Product) {
            deleteProduct((Product) item);
        } else if (item instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) item;
            if (map.containsKey("email")) {
                String userId = (String) map.get("id");
                showLoading(true);
                usersRef.child(userId).removeValue()
                        .addOnSuccessListener(aVoid -> {
                            showLoading(false);
                            Toast.makeText(this, "User deleted", Toast.LENGTH_SHORT).show();
                            fetchUsers();
                        })
                        .addOnFailureListener(e -> deleteFailed(e));
            } else if (map.containsKey("status")) {
                String orderId = (String) map.get("id");
                showLoading(true);
                DatabaseReference ordersRef = FirebaseDatabase.getInstance().getReference("orders");
                ordersRef.child(orderId).removeValue()
                        .addOnSuccessListener(aVoid -> {
                            showLoading(false);
                            Toast.makeText(this, "Order deleted", Toast.LENGTH_SHORT).show();
                            fetchOrders();
                        })
                        .addOnFailureListener(e -> deleteFailed(e));
            }
        }
    }

    private void editBanner(Banner banner) {
        currentBanner = banner;
        bannerTitleInput.setText(banner.getTitle());
        bannerDescriptionInput.setText(banner.getDescription());
        bannerDiscountInput.setText(banner.getDiscount());
        addBannerButton.setText("Update Banner");
    }

    private void editCategory(Category category) {
        categoryNameInput.setText(category.getName());
        addCategoryButton.setText("Update Category");
        addCategoryButton.setOnClickListener(v -> {
            String name = categoryNameInput.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter category name", Toast.LENGTH_SHORT).show();
                return;
            }
            Category updatedCategory = new Category(category.getId(), name, category.getProductCount(), category.getImageUrl());
            if (imageUri != null) {
                uploadImageAndExecute("categories", imageUrl -> {
                    updatedCategory.setImageUrl(imageUrl);
                    updateCategory(updatedCategory);
                });
            } else {
                updateCategory(updatedCategory);
            }
        });
    }

    private void editLabTest(LabTest labTest) {
        labTestNameInput.setText(labTest.getName());
        addLabTestButton.setText("Update Lab Test");
        addLabTestButton.setOnClickListener(v -> {
            String name = labTestNameInput.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter lab test name", Toast.LENGTH_SHORT).show();
                return;
            }
            LabTest updatedLabTest = new LabTest(labTest.getId(), name, labTest.getImageUrl());
            if (imageUri != null) {
                uploadImageAndExecute("labTests", imageUrl -> {
                    updatedLabTest.setImageUrl(imageUrl);
                    updateLabTest(updatedLabTest);
                });
            } else {
                updateLabTest(updatedLabTest);
            }
        });
    }

    private void editProduct(Product product) {
        productNameInput.setText(product.getName());
        productQuantityInput.setText(product.getQuantity());
        productPriceInput.setText(String.valueOf(product.getPrice()));
        productDiscountedPriceInput.setText(product.getDiscountedPrice() != 0.0 ? String.valueOf(product.getDiscountedPrice()) : "");
        productRatingInput.setText(String.valueOf(product.getRating()));
        productReviewCountInput.setText(String.valueOf(product.getReviewCount()));
        addProductButton.setText("Update Product");
        addProductButton.setOnClickListener(v -> {
            String name = productNameInput.getText().toString().trim();
            String quantity = productQuantityInput.getText().toString().trim();
            String priceStr = productPriceInput.getText().toString().trim();
            String discountedPriceStr = productDiscountedPriceInput.getText().toString().trim();
            String ratingStr = productRatingInput.getText().toString().trim();
            String reviewCountStr = productReviewCountInput.getText().toString().trim();
            int categoryPosition = categorySpinner.getSelectedItemPosition();
            if (categoryPosition < 0 || categoryIds.isEmpty()) {
                Toast.makeText(this, "Please select a category", Toast.LENGTH_SHORT).show();
                return;
            }
            String newCategoryId = categoryIds.get(categoryPosition);
            if (name.isEmpty() || quantity.isEmpty() || priceStr.isEmpty() || ratingStr.isEmpty() || reviewCountStr.isEmpty()) {
                Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show();
                return;
            }
            double price;
            double discountedPrice;
            int rating;
            int reviewCount;
            try {
                price = Double.parseDouble(priceStr);
                discountedPrice = discountedPriceStr.isEmpty() ? 0.0 : Double.parseDouble(discountedPriceStr);
                rating = Integer.parseInt(ratingStr);
                reviewCount = Integer.parseInt(reviewCountStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid number format in price, rating, or review count", Toast.LENGTH_SHORT).show();
                return;
            }
            Product updatedProduct = new Product(product.getId(), name, price, product.getImageUrl(), rating, reviewCount, quantity, price, discountedPrice);
            if (imageUri != null) {
                uploadImageAndExecute("products", imageUrl -> {
                    updatedProduct.setImageUrl(imageUrl);
                    updateProduct(product, newCategoryId, updatedProduct);
                });
            } else {
                updateProduct(product, newCategoryId, updatedProduct);
            }
        });
    }

    private void updateCategory(Category category) {
        showLoading(true);
        categoriesRef.child(category.getId()).setValue(category)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    Toast.makeText(this, "Category updated", Toast.LENGTH_SHORT).show();
                    categoryNameInput.setText("");
                    imageUri = null;
                    addCategoryButton.setText("Add Category");
                    addCategoryButton.setOnClickListener(v -> addCategory());
                    fetchCategories();
                    fetchCategoriesForSpinner();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Failed to update category: " + e.getMessage());
                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void updateLabTest(LabTest labTest) {
        showLoading(true);
        labTestsRef.child(labTest.getId()).setValue(labTest)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    Toast.makeText(this, "Lab Test updated", Toast.LENGTH_SHORT).show();
                    labTestNameInput.setText("");
                    imageUri = null;
                    addLabTestButton.setText("Add Lab Test");
                    addLabTestButton.setOnClickListener(v -> addLabTest());
                    fetchLabTests();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Failed to update lab test: " + e.getMessage());
                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void updateProduct(Product originalProduct, String newCategoryId, Product updatedProduct) {
        showLoading(true);
        productsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String oldCategoryId = null;
                for (DataSnapshot categorySnapshot : snapshot.getChildren()) {
                    if (categorySnapshot.child(originalProduct.getId()).exists()) {
                        oldCategoryId = categorySnapshot.getKey();
                        break;
                    }
                }

                if (oldCategoryId == null) {
                    showLoading(false);
                    Toast.makeText(AdminActivity.this, "Original product category not found", Toast.LENGTH_LONG).show();
                    return;
                }

                if (!oldCategoryId.equals(newCategoryId)) {
                    String finalOldCategoryId = oldCategoryId;
                    productsRef.child(oldCategoryId).child(originalProduct.getId()).removeValue()
                            .addOnSuccessListener(aVoid -> {
                                updateProductCount(finalOldCategoryId, -1);
                                productsRef.child(newCategoryId).child(updatedProduct.getId()).setValue(updatedProduct)
                                        .addOnSuccessListener(aVoid2 -> {
                                            updateProductCount(newCategoryId, 1);
                                            finishUpdateProduct();
                                        })
                                        .addOnFailureListener(e -> finishUpdateWithError(e));
                            })
                            .addOnFailureListener(e -> finishUpdateWithError(e));
                } else {
                    productsRef.child(newCategoryId).child(updatedProduct.getId()).setValue(updatedProduct)
                            .addOnSuccessListener(aVoid -> finishUpdateProduct())
                            .addOnFailureListener(e -> finishUpdateWithError(e));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showLoading(false);
                Log.e(TAG, "Failed to find product category: " + error.getMessage());
                Toast.makeText(AdminActivity.this, "Failed: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void finishUpdateProduct() {
        showLoading(false);
        Toast.makeText(this, "Product updated", Toast.LENGTH_SHORT).show();
        resetProductForm();
        addProductButton.setText("Add Product");
        addProductButton.setOnClickListener(v -> addProduct());
        fetchProducts();
    }

    private void finishUpdateWithError(Exception e) {
        showLoading(false);
        Log.e(TAG, "Failed to update product: " + e.getMessage());
        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }

    private void deleteBanner(Banner banner) {
        showLoading(true);
        bannersRef.child(banner.getId()).removeValue()
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    Toast.makeText(this, "Banner deleted", Toast.LENGTH_SHORT).show();
                    fetchBannerForEdit();
                })
                .addOnFailureListener(e -> deleteFailed(e));
    }

    private void deleteCategory(Category category) {
        showLoading(true);
        productsRef.child(category.getId()).removeValue()
                .addOnSuccessListener(aVoid -> {
                    categoriesRef.child(category.getId()).removeValue()
                            .addOnSuccessListener(aVoid2 -> {
                                showLoading(false);
                                Toast.makeText(this, "Category deleted", Toast.LENGTH_SHORT).show();
                                fetchCategories();
                                fetchCategoriesForSpinner();
                            })
                            .addOnFailureListener(e -> deleteFailed(e));
                })
                .addOnFailureListener(e -> deleteFailed(e));
    }

    private void deleteLabTest(LabTest labTest) {
        showLoading(true);
        labTestsRef.child(labTest.getId()).removeValue()
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    Toast.makeText(this, "Lab Test deleted", Toast.LENGTH_SHORT).show();
                    fetchLabTests();
                })
                .addOnFailureListener(e -> deleteFailed(e));
    }

    private void deleteProduct(Product product) {
        showLoading(true);
        productsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String categoryId = null;
                for (DataSnapshot categorySnapshot : snapshot.getChildren()) {
                    if (categorySnapshot.child(product.getId()).exists()) {
                        categoryId = categorySnapshot.getKey();
                        break;
                    }
                }
                if (categoryId != null) {
                    String finalCategoryId = categoryId;
                    productsRef.child(categoryId).child(product.getId()).removeValue()
                            .addOnSuccessListener(aVoid -> {
                                updateProductCount(finalCategoryId, -1);
                                showLoading(false);
                                Toast.makeText(AdminActivity.this, "Product deleted", Toast.LENGTH_SHORT).show();
                                fetchProducts();
                            })
                            .addOnFailureListener(e -> deleteFailed(e));
                } else {
                    showLoading(false);
                    Toast.makeText(AdminActivity.this, "Product category not found", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showLoading(false);
                Log.e(TAG, "Failed to find product category: " + error.getMessage());
                Toast.makeText(AdminActivity.this, "Failed: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void deleteFailed(Exception e) {
        showLoading(false);
        Log.e(TAG, "Delete failed: " + e.getMessage());
        Toast.makeText(this, "Delete failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }

    private void updateRecyclerViewVisibility() {
        if (itemList.isEmpty()) {
            adminRecyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            adminRecyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.nav_banner) {
            showSection("banner");
        } else if (itemId == R.id.nav_categories) {
            showSection("categories");
        } else if (itemId == R.id.nav_lab_tests) {
            showSection("labTests");
        } else if (itemId == R.id.nav_products) {
            showSection("products");
        } else if (itemId == R.id.nav_users) {
            showSection("users");
        } else if (itemId == R.id.nav_orders) {
            showSection("orders");
        } else if (itemId == R.id.nav_notifications) {
            showSection("notifications");
        } else if (itemId == R.id.nav_scanner) {
            showSection("scanner");
        } else if (itemId == R.id.nav_logout) {
            showLogoutConfirmationDialog(); // Call the new method
        }
        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    // New method to show logout confirmation dialog
    private void showLogoutConfirmationDialog() {
        LogoutConfirmationDialog dialog = new LogoutConfirmationDialog(this, this::logoutAndRedirectToLogin);
        dialog.show();
    }

    // Method to handle logout and redirection
    private void logoutAndRedirectToLogin() {
        mAuth.signOut();
        Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    @Override
    public void onBackPressed() {
        Log.d(TAG, "onBackPressed called, Drawer open: " + drawerLayout.isDrawerOpen(GravityCompat.START));
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
            Log.d(TAG, "Drawer closed");
        } else {
            Log.d(TAG, "Proceeding with default back press");
            super.onBackPressed();
        }
    }
}