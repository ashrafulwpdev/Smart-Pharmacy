package com.oopgroup.smartpharmacy;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
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
import com.google.firebase.storage.StorageMetadata;

public class AdminActivity extends AppCompatActivity implements AdminItemAdapter.OnItemActionListener {
    private static final String TAG = "AdminActivity";
    private static final int PICK_IMAGE_REQUEST = 1;
    private Button tabBanner, tabCategories, tabLabTests, tabProducts;
    private CardView bannerSection, categoriesSection, labTestsSection, productsSection;
    private EditText bannerTitleInput, bannerDescriptionInput, bannerDiscountInput;
    private EditText categoryNameInput, productCountInput;
    private EditText labTestNameInput;
    private EditText productNameInput, productPriceInput, productRatingInput, productReviewCountInput;
    private Button uploadBannerImageButton, uploadCategoryImageButton, uploadLabTestImageButton, uploadProductImageButton;
    private Button addBannerButton, addCategoryButton, addLabTestButton, addProductButton;
    private Spinner categorySpinner;
    private RecyclerView adminRecyclerView;
    private AdminItemAdapter adminAdapter;
    private List<Object> itemList;
    private List<String> categoryNames;
    private List<String> categoryIds;
    private DatabaseReference bannersRef, categoriesRef, labTestsRef, productsRef, usersRef;
    private FirebaseFirestore firestore;
    private StorageReference storageRef;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private boolean isAdmin = false;
    private Uri imageUri;
    private String currentTab = "categories";
    private Banner currentBanner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        // Initialize App Check (use Debug mode for emulator testing)
        try {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance());
            Log.d(TAG, "App Check initialized with Debug mode for testing");
        } catch (Exception e) {
            Log.e(TAG, "App Check initialization failed: " + e.getMessage());
        }

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        initializeFirebaseReferences();

        if (currentUser == null) {
            redirectToLogin();
            return;
        }

        Log.d(TAG, "Current User UID: " + currentUser.getUid());
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
        showSection("categories");
    }

    private void findViews() {
        tabBanner = findViewById(R.id.tabBanner);
        tabCategories = findViewById(R.id.tabCategories);
        tabLabTests = findViewById(R.id.tabLabTests);
        tabProducts = findViewById(R.id.tabProducts);
        bannerSection = findViewById(R.id.bannerSection);
        categoriesSection = findViewById(R.id.categoriesSection);
        labTestsSection = findViewById(R.id.labTestsSection);
        productsSection = findViewById(R.id.productsSection);
        bannerTitleInput = findViewById(R.id.bannerTitleInput);
        bannerDescriptionInput = findViewById(R.id.bannerDescriptionInput);
        bannerDiscountInput = findViewById(R.id.bannerDiscountInput);
        categoryNameInput = findViewById(R.id.categoryNameInput);
        productCountInput = findViewById(R.id.productCountInput);
        labTestNameInput = findViewById(R.id.labTestNameInput);
        productNameInput = findViewById(R.id.productNameInput);
        productPriceInput = findViewById(R.id.productPriceInput);
        productRatingInput = findViewById(R.id.productRatingInput);
        productReviewCountInput = findViewById(R.id.productReviewCountInput);
        uploadBannerImageButton = findViewById(R.id.uploadBannerImageButton);
        uploadCategoryImageButton = findViewById(R.id.uploadCategoryImageButton);
        uploadLabTestImageButton = findViewById(R.id.uploadLabTestImageButton);
        uploadProductImageButton = findViewById(R.id.uploadProductImageButton);
        addBannerButton = findViewById(R.id.addBannerButton);
        addCategoryButton = findViewById(R.id.addCategoryButton);
        addLabTestButton = findViewById(R.id.addLabTestButton);
        addProductButton = findViewById(R.id.addProductButton);
        categorySpinner = findViewById(R.id.categorySpinner);
        adminRecyclerView = findViewById(R.id.adminRecyclerView);
    }

    private void setupRecyclerView() {
        adminRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        itemList = new ArrayList<>();
        categoryNames = new ArrayList<>();
        categoryIds = new ArrayList<>();
        adminAdapter = new AdminItemAdapter(this, itemList, currentTab, this);
        adminRecyclerView.setAdapter(adminAdapter);
    }

    private void setupClickListeners() {
        tabBanner.setOnClickListener(v -> showSection("banner"));
        tabCategories.setOnClickListener(v -> showSection("categories"));
        tabLabTests.setOnClickListener(v -> showSection("labTests"));
        tabProducts.setOnClickListener(v -> showSection("products"));
        uploadBannerImageButton.setOnClickListener(v -> openImagePicker());
        uploadCategoryImageButton.setOnClickListener(v -> openImagePicker());
        uploadLabTestImageButton.setOnClickListener(v -> openImagePicker());
        uploadProductImageButton.setOnClickListener(v -> openImagePicker());
        addBannerButton.setOnClickListener(v -> addOrUpdateBanner());
        addCategoryButton.setOnClickListener(v -> addCategory());
        addLabTestButton.setOnClickListener(v -> addLabTest());
        addProductButton.setOnClickListener(v -> addProduct());
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
                        Toast.makeText(AdminActivity.this, "User document not found", Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }
                    String role = documentSnapshot.getString("role");
                    Log.d(TAG, "User role from Firestore: " + role);
                    if (role == null) {
                        Log.e(TAG, "Role field is missing!");
                        Toast.makeText(AdminActivity.this, "Role not set", Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }
                    // Sync the role to Realtime Database
                    syncRoleToRealtimeDatabase(currentUser.getUid(), role);
                    if ("admin".equals(role)) {
                        isAdmin = true;
                        initializeUI();
                    } else {
                        Toast.makeText(AdminActivity.this, "Access denied. Admins only.", Toast.LENGTH_LONG).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check user role: " + e.getMessage());
                    Toast.makeText(AdminActivity.this, "Failed to verify role", Toast.LENGTH_LONG).show();
                    finish();
                });
    }

    private void syncRoleToRealtimeDatabase(String uid, String role) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("role", role);
        usersRef.child(uid).updateChildren(userData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Role synced to Realtime Database: " + role);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to sync role to Realtime Database: " + e.getMessage());
                    Toast.makeText(this, "Failed to sync role: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void showSection(String section) {
        currentTab = section;
        bannerSection.setVisibility(section.equals("banner") ? View.VISIBLE : View.GONE);
        categoriesSection.setVisibility(section.equals("categories") ? View.VISIBLE : View.GONE);
        labTestsSection.setVisibility(section.equals("labTests") ? View.VISIBLE : View.GONE);
        productsSection.setVisibility(section.equals("products") ? View.VISIBLE : View.GONE);

        itemList.clear();
        adminAdapter = new AdminItemAdapter(this, itemList, currentTab, this);
        adminRecyclerView.setAdapter(adminAdapter);

        switch (section) {
            case "banner":
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
        }
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, "Select Image"), PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            imageUri = data.getData();
            Toast.makeText(this, "Image selected", Toast.LENGTH_SHORT).show();
        }
    }

    private void uploadImageAndExecute(String path, OnImageUploadListener onSuccess) {
        if (imageUri == null || currentUser == null) {
            Toast.makeText(this, "Please select an image or log in.", Toast.LENGTH_SHORT).show();
            return;
        }

        try (InputStream inputStream = getContentResolver().openInputStream(imageUri)) {
            int fileSize = inputStream != null ? inputStream.available() : 0;
            Log.d(TAG, "File size: " + fileSize + " bytes");
            if (fileSize > 5 * 1024 * 1024) {
                Toast.makeText(this, "File size exceeds 5 MB.", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to check file size: " + e.getMessage());
            Toast.makeText(this, "Error checking file size.", Toast.LENGTH_SHORT).show();
            return;
        }

        currentUser.getIdToken(true)
                .addOnSuccessListener(result -> {
                    Log.d(TAG, "Token refreshed: " + result.getToken());
                    firestore.collection("users").document(currentUser.getUid()).get()
                            .addOnSuccessListener(documentSnapshot -> {
                                String role = documentSnapshot.getString("role");
                                Log.d(TAG, "Role from Firestore during upload: " + role);
                                if ("admin".equals(role)) {
                                    String fileName = System.currentTimeMillis() + ".jpg";
                                    StorageReference fileRef = storageRef.child(path).child(fileName);
                                    Log.d(TAG, "Uploading to: " + fileRef.getPath() + " with UID: " + currentUser.getUid());

                                    String contentType = getContentResolver().getType(imageUri);
                                    Log.d(TAG, "Content type before upload: " + contentType);

                                    StorageMetadata metadata = new StorageMetadata.Builder()
                                            .setContentType(contentType != null && contentType.startsWith("image/") ? contentType : "image/jpeg")
                                            .build();
                                    Log.d(TAG, "Metadata content type: " + metadata.getContentType());

                                    fileRef.putFile(imageUri, metadata)
                                            .addOnProgressListener(taskSnapshot -> {
                                                Log.d(TAG, "Upload progress: " + taskSnapshot.getBytesTransferred() + "/" + taskSnapshot.getTotalByteCount());
                                            })
                                            .addOnSuccessListener(taskSnapshot -> fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                                                String imageUrl = uri.toString();
                                                Log.d(TAG, "Image uploaded: " + imageUrl);
                                                imageUri = null;
                                                onSuccess.onSuccess(imageUrl);
                                            }))
                                            .addOnFailureListener(e -> {
                                                Log.e(TAG, "Upload failed: " + e.getMessage(), e);
                                                Log.d(TAG, "Storage path attempted: " + fileRef.getPath());
                                                Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                            });
                                } else {
                                    Log.e(TAG, "User is not admin during upload!");
                                    Toast.makeText(this, "Unauthorized: Not an admin.", Toast.LENGTH_LONG).show();
                                }
                            })
                            .addOnFailureListener(e -> Log.e(TAG, "Firestore read failed during upload: " + e.getMessage()));
                })
                .addOnFailureListener(e -> Log.e(TAG, "Token refresh failed: " + e.getMessage()));
    }

    private interface OnImageUploadListener {
        void onSuccess(String imageUrl);
    }

    private void addOrUpdateBanner() {
        String title = bannerTitleInput.getText().toString().trim();
        String description = bannerDescriptionInput.getText().toString().trim();
        String discount = bannerDiscountInput.getText().toString().trim();

        if (title.isEmpty() || description.isEmpty() || discount.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, "Please upload an image.", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveBanner(Map<String, Object> bannerData) {
        String bannerId = currentBanner != null ? currentBanner.getId() : bannersRef.push().getKey();
        bannersRef.child(bannerId).setValue(bannerData)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Banner " + (currentBanner != null ? "updated" : "added"), Toast.LENGTH_SHORT).show();
                    resetBannerForm();
                    fetchBannerForEdit();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save banner: " + e.getMessage());
                    Toast.makeText(this, "Failed to save banner: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void resetBannerForm() {
        bannerTitleInput.setText("");
        bannerDescriptionInput.setText("");
        bannerDiscountInput.setText("");
        currentBanner = null;
    }

    private void addCategory() {
        String name = categoryNameInput.getText().toString().trim();
        String productCountStr = productCountInput.getText().toString().trim();

        if (name.isEmpty() || productCountStr.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_SHORT).show();
            return;
        }

        int productCount = Integer.parseInt(productCountStr);
        uploadImageAndExecute("categories", imageUrl -> {
            String categoryId = categoriesRef.push().getKey();
            Category category = new Category(categoryId, name, productCount, imageUrl);
            categoriesRef.child(categoryId).setValue(category)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Category added.", Toast.LENGTH_SHORT).show();
                        categoryNameInput.setText("");
                        productCountInput.setText("");
                        fetchCategories();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to add category: " + e.getMessage());
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        });
    }

    private void addLabTest() {
        String name = labTestNameInput.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_SHORT).show();
            return;
        }

        uploadImageAndExecute("labTests", imageUrl -> {
            String labTestId = labTestsRef.push().getKey();
            LabTest labTest = new LabTest(labTestId, name, imageUrl);
            labTestsRef.child(labTestId).setValue(labTest)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Lab Test added.", Toast.LENGTH_SHORT).show();
                        labTestNameInput.setText("");
                        fetchLabTests();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to add lab test: " + e.getMessage());
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        });
    }

    private void addProduct() {
        String name = productNameInput.getText().toString().trim();
        String priceStr = productPriceInput.getText().toString().trim();
        String ratingStr = productRatingInput.getText().toString().trim();
        String reviewCountStr = productReviewCountInput.getText().toString().trim();
        int categoryPosition = categorySpinner.getSelectedItemPosition();
        if (categoryPosition < 0 || categoryIds.isEmpty()) {
            Toast.makeText(this, "Please select a category.", Toast.LENGTH_SHORT).show();
            return;
        }
        String categoryId = categoryIds.get(categoryPosition);

        if (name.isEmpty() || priceStr.isEmpty() || ratingStr.isEmpty() || reviewCountStr.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_SHORT).show();
            return;
        }

        double price = Double.parseDouble(priceStr);
        int rating = Integer.parseInt(ratingStr);
        int reviewCount = Integer.parseInt(reviewCountStr);

        uploadImageAndExecute("products", imageUrl -> {
            String productId = productsRef.child(categoryId).push().getKey();
            Product product = new Product(productId, name, price, imageUrl, rating, reviewCount);
            productsRef.child(categoryId).child(productId).setValue(product)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Product added.", Toast.LENGTH_SHORT).show();
                        resetProductForm();
                        fetchProducts();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to add product: " + e.getMessage());
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        });
    }

    private void resetProductForm() {
        productNameInput.setText("");
        productPriceInput.setText("");
        productRatingInput.setText("");
        productReviewCountInput.setText("");
    }

    private void fetchBannerForEdit() {
        bannersRef.limitToFirst(1).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                itemList.clear();
                if (snapshot.exists()) {
                    for (DataSnapshot bannerSnapshot : snapshot.getChildren()) {
                        Banner banner = bannerSnapshot.getValue(Banner.class);
                        if (banner != null) {
                            banner.setId(bannerSnapshot.getKey());
                            currentBanner = banner;
                            bannerTitleInput.setText(banner.getTitle());
                            bannerDescriptionInput.setText(banner.getDescription());
                            bannerDiscountInput.setText(banner.getDiscount());
                            itemList.add(banner);
                        }
                    }
                }
                adminAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to fetch banner: " + error.getMessage());
            }
        });
    }

    private void fetchCategories() {
        categoriesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                itemList.clear();
                for (DataSnapshot categorySnapshot : snapshot.getChildren()) {
                    Category category = categorySnapshot.getValue(Category.class);
                    if (category != null) {
                        category.setId(categorySnapshot.getKey());
                        itemList.add(category);
                    }
                }
                adminAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to fetch categories: " + error.getMessage());
            }
        });
    }

    private void fetchLabTests() {
        labTestsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                itemList.clear();
                for (DataSnapshot labTestSnapshot : snapshot.getChildren()) {
                    LabTest labTest = labTestSnapshot.getValue(LabTest.class);
                    if (labTest != null) {
                        labTest.setId(labTestSnapshot.getKey());
                        itemList.add(labTest);
                    }
                }
                adminAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to fetch lab tests: " + error.getMessage());
            }
        });
    }

    private void fetchProducts() {
        productsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                itemList.clear();
                for (DataSnapshot categorySnapshot : snapshot.getChildren()) {
                    for (DataSnapshot productSnapshot : categorySnapshot.getChildren()) {
                        Product product = productSnapshot.getValue(Product.class);
                        if (product != null) {
                            product.setId(productSnapshot.getKey());
                            itemList.add(product);
                        }
                    }
                }
                adminAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to fetch products: " + error.getMessage());
            }
        });
    }

    private void fetchCategoriesForSpinner() {
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
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to fetch categories for spinner: " + error.getMessage());
            }
        });
    }

    @Override
    public void onEditItem(Object item) {
        if (item instanceof Banner) {
            editBanner((Banner) item);
        } else if (item instanceof Category) {
            editCategory((Category) item);
        } else if (item instanceof LabTest) {
            editLabTest((LabTest) item);
        } else if (item instanceof Product) {
            editProduct((Product) item);
        }
    }

    private void editBanner(Banner banner) {
        currentBanner = banner;
        bannerTitleInput.setText(banner.getTitle());
        bannerDescriptionInput.setText(banner.getDescription());
        bannerDiscountInput.setText(banner.getDiscount());
        addBannerButton.setText("Update Banner");
        addBannerButton.setOnClickListener(v -> addOrUpdateBanner());
    }

    private void editCategory(Category category) {
        categoryNameInput.setText(category.getName());
        productCountInput.setText(String.valueOf(category.getProductCount()));
        addCategoryButton.setText("Update Category");
        addCategoryButton.setOnClickListener(v -> {
            String name = categoryNameInput.getText().toString().trim();
            String productCountStr = productCountInput.getText().toString().trim();
            if (name.isEmpty() || productCountStr.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_SHORT).show();
                return;
            }
            int productCount = Integer.parseInt(productCountStr);
            Category updatedCategory = new Category(category.getId(), name, productCount, category.getImageUrl());
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
                Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_SHORT).show();
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
        productPriceInput.setText(String.valueOf(product.getPrice()));
        productRatingInput.setText(String.valueOf(product.getRating()));
        productReviewCountInput.setText(String.valueOf(product.getReviewCount()));
        addProductButton.setText("Update Product");
        addProductButton.setOnClickListener(v -> {
            String name = productNameInput.getText().toString().trim();
            String priceStr = productPriceInput.getText().toString().trim();
            String ratingStr = productRatingInput.getText().toString().trim();
            String reviewCountStr = productReviewCountInput.getText().toString().trim();
            int categoryPosition = categorySpinner.getSelectedItemPosition();
            if (categoryPosition < 0 || categoryIds.isEmpty()) {
                Toast.makeText(this, "Please select a category.", Toast.LENGTH_SHORT).show();
                return;
            }
            String categoryId = categoryIds.get(categoryPosition);
            if (name.isEmpty() || priceStr.isEmpty() || ratingStr.isEmpty() || reviewCountStr.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_SHORT).show();
                return;
            }
            double price = Double.parseDouble(priceStr);
            int rating = Integer.parseInt(ratingStr);
            int reviewCount = Integer.parseInt(reviewCountStr);
            Product updatedProduct = new Product(product.getId(), name, price, product.getImageUrl(), rating, reviewCount);
            if (imageUri != null) {
                uploadImageAndExecute("products", imageUrl -> {
                    updatedProduct.setImageUrl(imageUrl);
                    updateProduct(categoryId, updatedProduct);
                });
            } else {
                updateProduct(categoryId, updatedProduct);
            }
        });
    }

    private void updateCategory(Category category) {
        categoriesRef.child(category.getId()).setValue(category)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Category updated.", Toast.LENGTH_SHORT).show();
                    categoryNameInput.setText("");
                    productCountInput.setText("");
                    addCategoryButton.setText("Add Category");
                    addCategoryButton.setOnClickListener(v -> addCategory());
                    fetchCategories();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update category: " + e.getMessage());
                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void updateLabTest(LabTest labTest) {
        labTestsRef.child(labTest.getId()).setValue(labTest)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Lab Test updated.", Toast.LENGTH_SHORT).show();
                    labTestNameInput.setText("");
                    addLabTestButton.setText("Add Lab Test");
                    addLabTestButton.setOnClickListener(v -> addLabTest());
                    fetchLabTests();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update lab test: " + e.getMessage());
                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void updateProduct(String categoryId, Product product) {
        productsRef.child(categoryId).child(product.getId()).setValue(product)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Product updated.", Toast.LENGTH_SHORT).show();
                    resetProductForm();
                    addProductButton.setText("Add Product");
                    addProductButton.setOnClickListener(v -> addProduct());
                    fetchProducts();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update product: " + e.getMessage());
                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    @Override
    public void onDeleteItem(Object item) {
        if (item instanceof Banner) {
            Banner banner = (Banner) item;
            bannersRef.child(banner.getId()).removeValue()
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Banner deleted.", Toast.LENGTH_SHORT).show();
                        resetBannerForm();
                        fetchBannerForEdit();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to delete banner: " + e.getMessage());
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        } else if (item instanceof Category) {
            Category category = (Category) item;
            categoriesRef.child(category.getId()).removeValue()
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Category deleted.", Toast.LENGTH_SHORT).show();
                        fetchCategories();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to delete category: " + e.getMessage());
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        } else if (item instanceof LabTest) {
            LabTest labTest = (LabTest) item;
            labTestsRef.child(labTest.getId()).removeValue()
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Lab Test deleted.", Toast.LENGTH_SHORT).show();
                        fetchLabTests();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to delete lab test: " + e.getMessage());
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        } else if (item instanceof Product) {
            Product product = (Product) item;
            productsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    for (DataSnapshot categorySnapshot : snapshot.getChildren()) {
                        if (categorySnapshot.child(product.getId()).exists()) {
                            categorySnapshot.getRef().child(product.getId()).removeValue()
                                    .addOnSuccessListener(aVoid -> {
                                        Toast.makeText(AdminActivity.this, "Product deleted.", Toast.LENGTH_SHORT).show();
                                        fetchProducts();
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Failed to delete product: " + e.getMessage());
                                        Toast.makeText(AdminActivity.this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                    });
                            break;
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "Failed to find product: " + error.getMessage());
                }
            });
        }
    }
}