package com.oopgroup.smartpharmacy;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.FirebaseDatabase;
import com.oopgroup.smartpharmacy.adapters.AdminItemAdapter;
import com.oopgroup.smartpharmacy.models.Banner;
import com.oopgroup.smartpharmacy.models.Category;
import com.oopgroup.smartpharmacy.models.LabTest;
import com.oopgroup.smartpharmacy.models.Product;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AdminUIHelper: Manages UI components, form handling, user interactions, and swipe-to-refresh.
 */
public class AdminUIHelper implements AdminItemAdapter.OnItemClickListener {
    private static final String TAG = "AdminUIHelper";
    private static final int PICK_IMAGE_REQUEST = 1;

    // UI Components
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
    private SwipeRefreshLayout swipeRefreshLayout;

    // Data and Adapter
    private final AdminDataManager dataManager;
    private AdminItemAdapter adminAdapter;
    private List<Object> itemList;
    private List<String> categoryNames;
    private List<String> categoryIds;
    private Uri imageUri;
    private String currentTab = "banner";
    private Banner currentBanner;

    // Activity Reference
    private final AppCompatActivity activity;

    // Image Picker
    private final ActivityResultLauncher<String> imagePickerLauncher;

    public AdminUIHelper(AppCompatActivity activity, AdminDataManager dataManager, SwipeRefreshLayout swipeRefreshLayout) {
        this.activity = activity;
        this.dataManager = dataManager;
        this.swipeRefreshLayout = swipeRefreshLayout;
        this.itemList = new ArrayList<>();
        this.categoryNames = new ArrayList<>();
        this.categoryIds = new ArrayList<>();

        // Initialize Image Picker
        imagePickerLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        imageUri = uri;
                        Toast.makeText(activity, "Image selected", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    /** Initializes all UI components, listeners, and swipe-to-refresh. */
    public void initializeUI() {
        findViews();
        setupRecyclerView();
        setupClickListeners();
        fetchCategoriesForSpinner();
        showSection("banner"); // Default section
    }

    /** Refreshes the data for the currently active section. */
    public void refreshCurrentSection() {
        Log.d(TAG, "Refreshing current section: " + currentTab);
        swipeRefreshLayout.setRefreshing(true); // Show the refresh indicator
        switch (currentTab) {
            case "banner":
                dataManager.fetchBanners(items -> {
                    Log.d(TAG, "Fetched " + items.size() + " banners");
                    updateRecyclerView(items);
                    swipeRefreshLayout.setRefreshing(false);
                }, error -> {
                    showError(error);
                    swipeRefreshLayout.setRefreshing(false);
                });
                break;
            case "categories":
                dataManager.fetchCategories(items -> {
                    updateRecyclerView(items);
                    swipeRefreshLayout.setRefreshing(false);
                }, error -> {
                    showError(error);
                    swipeRefreshLayout.setRefreshing(false);
                });
                break;
            case "labTests":
                dataManager.fetchLabTests(items -> {
                    updateRecyclerView(items);
                    swipeRefreshLayout.setRefreshing(false);
                }, error -> {
                    showError(error);
                    swipeRefreshLayout.setRefreshing(false);
                });
                break;
            case "products":
                dataManager.fetchProducts(items -> {
                    updateRecyclerView(items);
                    swipeRefreshLayout.setRefreshing(false);
                }, error -> {
                    showError(error);
                    swipeRefreshLayout.setRefreshing(false);
                });
                break;
            case "users":
                dataManager.fetchUsers(items -> {
                    updateRecyclerView(items);
                    swipeRefreshLayout.setRefreshing(false);
                }, error -> {
                    showError(error);
                    swipeRefreshLayout.setRefreshing(false);
                });
                break;
            case "orders":
                dataManager.fetchOrders(items -> {
                    updateRecyclerView(items);
                    swipeRefreshLayout.setRefreshing(false);
                }, error -> {
                    showError(error);
                    swipeRefreshLayout.setRefreshing(false);
                });
                break;
            default:
                swipeRefreshLayout.setRefreshing(false); // No refresh for notifications or scanner
                break;
        }
    }

    /** Finds all views by ID. */
    private void findViews() {
        bannerSection = activity.findViewById(R.id.bannerSection);
        categoriesSection = activity.findViewById(R.id.categoriesSection);
        labTestsSection = activity.findViewById(R.id.labTestsSection);
        productsSection = activity.findViewById(R.id.productsSection);
        usersSection = activity.findViewById(R.id.usersSection);
        ordersSection = activity.findViewById(R.id.ordersSection);
        notificationsSection = activity.findViewById(R.id.notificationsSection);
        scannerSection = activity.findViewById(R.id.scannerSection);
        bannerTitleInput = activity.findViewById(R.id.bannerTitleInput);
        bannerDescriptionInput = activity.findViewById(R.id.bannerDescriptionInput);
        bannerDiscountInput = activity.findViewById(R.id.bannerDiscountInput);
        categoryNameInput = activity.findViewById(R.id.categoryNameInput);
        labTestNameInput = activity.findViewById(R.id.labTestNameInput);
        productNameInput = activity.findViewById(R.id.productNameInput);
        productPriceInput = activity.findViewById(R.id.productPriceInput);
        productRatingInput = activity.findViewById(R.id.productRatingInput);
        productReviewCountInput = activity.findViewById(R.id.productReviewCountInput);
        productQuantityInput = activity.findViewById(R.id.productQuantityInput);
        productDiscountedPriceInput = activity.findViewById(R.id.productDiscountedPriceInput);
        notificationTitleInput = activity.findViewById(R.id.notificationTitleInput);
        notificationMessageInput = activity.findViewById(R.id.notificationMessageInput);
        scannerInstructionsInput = activity.findViewById(R.id.scannerInstructionsInput);
        uploadBannerImageButton = activity.findViewById(R.id.uploadBannerImageButton);
        uploadCategoryImageButton = activity.findViewById(R.id.uploadCategoryImageButton);
        uploadLabTestImageButton = activity.findViewById(R.id.uploadLabTestImageButton);
        uploadProductImageButton = activity.findViewById(R.id.uploadProductImageButton);
        addBannerButton = activity.findViewById(R.id.addBannerButton);
        addCategoryButton = activity.findViewById(R.id.addCategoryButton);
        addLabTestButton = activity.findViewById(R.id.addLabTestButton);
        addProductButton = activity.findViewById(R.id.addProductButton);
        refreshUsersButton = activity.findViewById(R.id.refreshUsersButton);
        refreshOrdersButton = activity.findViewById(R.id.refreshOrdersButton);
        sendNotificationButton = activity.findViewById(R.id.sendNotificationButton);
        updateScannerSettingsButton = activity.findViewById(R.id.updateScannerSettingsButton);
        categorySpinner = activity.findViewById(R.id.categorySpinner);
        notificationTargetSpinner = activity.findViewById(R.id.notificationTargetSpinner);
        adminRecyclerView = activity.findViewById(R.id.adminRecyclerView);
        emptyView = activity.findViewById(R.id.emptyView);
    }

    /** Sets up the RecyclerView with adapter. */
    private void setupRecyclerView() {
        adminRecyclerView.setLayoutManager(new LinearLayoutManager(activity));
        adminRecyclerView.setHasFixedSize(true);
        adminAdapter = new AdminItemAdapter(itemList, this, this::onDeleteItem);
        adminRecyclerView.setAdapter(adminAdapter);
        Log.d(TAG, "RecyclerView setup completed");
    }

    /** Sets up all button click listeners. */
    private void setupClickListeners() {
        uploadBannerImageButton.setOnClickListener(v -> openImagePicker());
        uploadCategoryImageButton.setOnClickListener(v -> openImagePicker());
        uploadLabTestImageButton.setOnClickListener(v -> openImagePicker());
        uploadProductImageButton.setOnClickListener(v -> openImagePicker());
        addBannerButton.setOnClickListener(v -> addOrUpdateBanner());
        addCategoryButton.setOnClickListener(v -> addCategory());
        addLabTestButton.setOnClickListener(v -> addLabTest());
        addProductButton.setOnClickListener(v -> addProduct());
        refreshUsersButton.setOnClickListener(v -> dataManager.fetchUsers(this::updateRecyclerView, this::showError));
        refreshOrdersButton.setOnClickListener(v -> dataManager.fetchOrders(this::updateRecyclerView, this::showError));
        sendNotificationButton.setOnClickListener(v -> sendNotification());
        updateScannerSettingsButton.setOnClickListener(v -> updateScannerSettings());
    }

    /** Shows the specified section and fetches relevant data. */
    public void showSection(String section) {
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

        // Reset the UI before fetching new data
        itemList.clear();
        adminAdapter.notifyDataSetChanged();
        adminRecyclerView.setVisibility(View.GONE); // Hide RecyclerView initially
        emptyView.setVisibility(View.VISIBLE); // Show emptyView until data is loaded

        switch (section) {
            case "banner":
                resetBannerForm();
                dataManager.fetchBanners(this::updateRecyclerView, this::showError);
                break;
            case "categories":
                dataManager.fetchCategories(this::updateRecyclerView, this::showError);
                break;
            case "labTests":
                dataManager.fetchLabTests(this::updateRecyclerView, this::showError);
                break;
            case "products":
                dataManager.fetchProducts(this::updateRecyclerView, this::showError);
                break;
            case "users":
                dataManager.fetchUsers(this::updateRecyclerView, this::showError);
                break;
            case "orders":
                dataManager.fetchOrders(this::updateRecyclerView, this::showError);
                break;
            case "notifications":
            case "scanner":
                updateRecyclerView(new ArrayList<>()); // Ensure empty state for these sections
                break;
        }
    }

    /** Opens the image picker with permission handling. */
    private void openImagePicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.READ_MEDIA_IMAGES}, PICK_IMAGE_REQUEST);
                return;
            }
        } else {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PICK_IMAGE_REQUEST);
                return;
            }
        }
        imagePickerLauncher.launch("image/*");
    }

    /** Adds or updates a banner. */
    private void addOrUpdateBanner() {
        String title = bannerTitleInput.getText().toString().trim();
        String description = bannerDescriptionInput.getText().toString().trim();
        String discount = bannerDiscountInput.getText().toString().trim();

        if (title.isEmpty() || description.isEmpty() || discount.isEmpty()) {
            Toast.makeText(activity, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> bannerData = new HashMap<>();
        bannerData.put("title", title);
        bannerData.put("description", description);
        bannerData.put("discount", discount);

        if (imageUri != null) {
            dataManager.uploadImageAndExecute(imageUri, "banners",
                    imageUrl -> {
                        bannerData.put("imageUrl", imageUrl);
                        dataManager.saveBanner(bannerData, currentBanner != null ? currentBanner.getId() : null,
                                () -> {
                                    Toast.makeText(activity, "Banner " + (currentBanner != null ? "updated" : "added"), Toast.LENGTH_SHORT).show();
                                    resetBannerForm();
                                    dataManager.fetchBanners(this::updateRecyclerView, this::showError);
                                },
                                this::showError);
                    },
                    this::showError);
        } else if (currentBanner != null && currentBanner.getImageUrl() != null) {
            bannerData.put("imageUrl", currentBanner.getImageUrl());
            dataManager.saveBanner(bannerData, currentBanner.getId(),
                    () -> {
                        Toast.makeText(activity, "Banner updated", Toast.LENGTH_SHORT).show();
                        resetBannerForm();
                        dataManager.fetchBanners(this::updateRecyclerView, this::showError);
                    },
                    this::showError);
        } else {
            Toast.makeText(activity, "Please upload an image", Toast.LENGTH_SHORT).show();
        }
    }

    /** Adds a new category. */
    private void addCategory() {
        String name = categoryNameInput.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(activity, "Please enter category name", Toast.LENGTH_SHORT).show();
            return;
        }

        if (imageUri == null) {
            Toast.makeText(activity, "Please upload an image", Toast.LENGTH_SHORT).show();
            return;
        }

        dataManager.uploadImageAndExecute(imageUri, "categories",
                imageUrl -> {
                    String categoryId = FirebaseDatabase.getInstance().getReference("categories").push().getKey();
                    Category category = new Category(categoryId, name, 0, imageUrl);
                    dataManager.saveCategory(category,
                            () -> {
                                Toast.makeText(activity, "Category added", Toast.LENGTH_SHORT).show();
                                categoryNameInput.setText("");
                                imageUri = null;
                                dataManager.fetchCategories(this::updateRecyclerView, this::showError);
                                fetchCategoriesForSpinner();
                            },
                            this::showError);
                },
                this::showError);
    }

    /** Adds a new lab test. */
    private void addLabTest() {
        String name = labTestNameInput.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(activity, "Please enter lab test name", Toast.LENGTH_SHORT).show();
            return;
        }

        if (imageUri == null) {
            Toast.makeText(activity, "Please upload an image", Toast.LENGTH_SHORT).show();
            return;
        }

        dataManager.uploadImageAndExecute(imageUri, "labTests",
                imageUrl -> {
                    String labTestId = FirebaseDatabase.getInstance().getReference("labTests").push().getKey();
                    LabTest labTest = new LabTest(labTestId, name, imageUrl);
                    dataManager.saveLabTest(labTest,
                            () -> {
                                Toast.makeText(activity, "Lab Test added", Toast.LENGTH_SHORT).show();
                                labTestNameInput.setText("");
                                imageUri = null;
                                dataManager.fetchLabTests(this::updateRecyclerView, this::showError);
                            },
                            this::showError);
                },
                this::showError);
    }

    /** Adds a new product. */
    private void addProduct() {
        String name = productNameInput.getText().toString().trim();
        String quantity = productQuantityInput.getText().toString().trim();
        String priceStr = productPriceInput.getText().toString().trim();
        String discountedPriceStr = productDiscountedPriceInput.getText().toString().trim();
        String ratingStr = productRatingInput.getText().toString().trim();
        String reviewCountStr = productReviewCountInput.getText().toString().trim();
        int categoryPosition = categorySpinner.getSelectedItemPosition();

        if (categoryPosition < 0 || categoryIds.isEmpty()) {
            Toast.makeText(activity, "Please select a category", Toast.LENGTH_SHORT).show();
            return;
        }
        String categoryId = categoryIds.get(categoryPosition);

        if (name.isEmpty() || quantity.isEmpty() || priceStr.isEmpty() || ratingStr.isEmpty() || reviewCountStr.isEmpty()) {
            Toast.makeText(activity, "Please fill all required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (imageUri == null) {
            Toast.makeText(activity, "Please upload an image", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(activity, "Invalid number format in price, rating, or review count", Toast.LENGTH_SHORT).show();
            return;
        }

        dataManager.uploadImageAndExecute(imageUri, "products",
                imageUrl -> {
                    String productId = FirebaseDatabase.getInstance().getReference("products").child(categoryId).push().getKey();
                    Product product = new Product(productId, name, price, imageUrl, rating, reviewCount, quantity, price, discountedPrice);
                    dataManager.saveProduct(categoryId, product,
                            () -> {
                                Toast.makeText(activity, "Product added", Toast.LENGTH_SHORT).show();
                                resetProductForm();
                                dataManager.fetchProducts(this::updateRecyclerView, this::showError);
                            },
                            this::showError);
                },
                this::showError);
    }

    /** Sends a notification. */
    private void sendNotification() {
        String title = notificationTitleInput.getText().toString().trim();
        String message = notificationMessageInput.getText().toString().trim();
        String target = notificationTargetSpinner.getSelectedItem() != null ? notificationTargetSpinner.getSelectedItem().toString() : "";

        if (title.isEmpty() || message.isEmpty()) {
            Toast.makeText(activity, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        dataManager.sendNotification(title, message, target,
                () -> {
                    Toast.makeText(activity, "Notification sent to " + target, Toast.LENGTH_SHORT).show();
                    notificationTitleInput.setText("");
                    notificationMessageInput.setText("");
                },
                this::showError);
    }

    /** Updates scanner settings. */
    private void updateScannerSettings() {
        String instructions = scannerInstructionsInput.getText().toString().trim();
        if (instructions.isEmpty()) {
            Toast.makeText(activity, "Please provide instructions", Toast.LENGTH_SHORT).show();
            return;
        }

        dataManager.updateScannerSettings(instructions,
                () -> {
                    Toast.makeText(activity, "Scanner settings updated", Toast.LENGTH_SHORT).show();
                    scannerInstructionsInput.setText("");
                },
                this::showError);
    }

    /** Resets the banner form fields. */
    private void resetBannerForm() {
        bannerTitleInput.setText("");
        bannerDescriptionInput.setText("");
        bannerDiscountInput.setText("");
        currentBanner = null;
        addBannerButton.setText(R.string.add_update_banner);
        imageUri = null;
    }

    /** Resets the product form fields. */
    private void resetProductForm() {
        productNameInput.setText("");
        productQuantityInput.setText("");
        productPriceInput.setText("");
        productDiscountedPriceInput.setText("");
        productRatingInput.setText("");
        productReviewCountInput.setText("");
        imageUri = null;
    }

    /** Updates RecyclerView with fetched data and toggles visibility. */
    private void updateRecyclerView(List<Object> items) {
        Log.d(TAG, "Updating RecyclerView with " + items.size() + " items");
        itemList.clear();
        itemList.addAll(items);
        adminAdapter.notifyDataSetChanged(); // Notify adapter of data change
        if (itemList.isEmpty()) {
            Log.d(TAG, "Item list is empty, hiding RecyclerView and showing emptyView");
            adminRecyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            Log.d(TAG, "Item list is not empty, showing RecyclerView and hiding emptyView");
            adminRecyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }
    }

    /** Displays error messages. */
    private void showError(String message) {
        Log.e(TAG, "Error: " + message);
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
    }

    /** Fetches categories for the spinner. */
    private void fetchCategoriesForSpinner() {
        dataManager.fetchCategoriesForSpinner(
                names -> {
                    categoryNames = names;
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, categoryNames);
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    categorySpinner.setAdapter(adapter);
                },
                ids -> categoryIds = ids,
                this::showError);

        ArrayAdapter<CharSequence> notificationAdapter = ArrayAdapter.createFromResource(
                activity, R.array.notification_targets, android.R.layout.simple_spinner_item);
        notificationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        notificationTargetSpinner.setAdapter(notificationAdapter);
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
                Toast.makeText(activity, "Editing user: " + map.get("email"), Toast.LENGTH_SHORT).show();
            } else if (map.containsKey("status")) {
                Toast.makeText(activity, "Editing order: " + map.get("id"), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void onDeleteItem(Object item) {
        if (item instanceof Banner) {
            dataManager.deleteBanner((Banner) item,
                    () -> {
                        Toast.makeText(activity, "Banner deleted", Toast.LENGTH_SHORT).show();
                        dataManager.fetchBanners(this::updateRecyclerView, this::showError);
                    },
                    this::showError);
        } else if (item instanceof Category) {
            dataManager.deleteCategory((Category) item,
                    () -> {
                        Toast.makeText(activity, "Category deleted", Toast.LENGTH_SHORT).show();
                        dataManager.fetchCategories(this::updateRecyclerView, this::showError);
                        fetchCategoriesForSpinner();
                    },
                    this::showError);
        } else if (item instanceof LabTest) {
            dataManager.deleteLabTest((LabTest) item,
                    () -> {
                        Toast.makeText(activity, "Lab Test deleted", Toast.LENGTH_SHORT).show();
                        dataManager.fetchLabTests(this::updateRecyclerView, this::showError);
                    },
                    this::showError);
        } else if (item instanceof Product) {
            dataManager.deleteProduct((Product) item,
                    () -> {
                        Toast.makeText(activity, "Product deleted", Toast.LENGTH_SHORT).show();
                        dataManager.fetchProducts(this::updateRecyclerView, this::showError);
                    },
                    this::showError);
        } else if (item instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) item;
            if (map.containsKey("email")) {
                String userId = (String) map.get("id");
                dataManager.deleteUser(userId,
                        () -> {
                            Toast.makeText(activity, "User deleted", Toast.LENGTH_SHORT).show();
                            dataManager.fetchUsers(this::updateRecyclerView, this::showError);
                        },
                        this::showError);
            } else if (map.containsKey("status")) {
                String orderId = (String) map.get("id");
                dataManager.deleteOrder(orderId,
                        () -> {
                            Toast.makeText(activity, "Order deleted", Toast.LENGTH_SHORT).show();
                            dataManager.fetchOrders(this::updateRecyclerView, this::showError);
                        },
                        this::showError);
            }
        }
    }

    /** Edits a banner by populating the form. */
    private void editBanner(Banner banner) {
        currentBanner = banner;
        bannerTitleInput.setText(banner.getTitle());
        bannerDescriptionInput.setText(banner.getDescription());
        bannerDiscountInput.setText(banner.getDiscount());
        addBannerButton.setText("Update Banner");
    }

    /** Edits a category by populating the form and setting update logic. */
    private void editCategory(Category category) {
        categoryNameInput.setText(category.getName());
        addCategoryButton.setText("Update Category");
        addCategoryButton.setOnClickListener(v -> {
            String name = categoryNameInput.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(activity, "Please enter category name", Toast.LENGTH_SHORT).show();
                return;
            }
            Category updatedCategory = new Category(category.getId(), name, category.getProductCount(), category.getImageUrl());
            if (imageUri != null) {
                dataManager.uploadImageAndExecute(imageUri, "categories",
                        imageUrl -> {
                            updatedCategory.setImageUrl(imageUrl);
                            dataManager.updateCategory(updatedCategory,
                                    () -> {
                                        Toast.makeText(activity, "Category updated", Toast.LENGTH_SHORT).show();
                                        categoryNameInput.setText("");
                                        imageUri = null;
                                        addCategoryButton.setText("Add Category");
                                        addCategoryButton.setOnClickListener(v1 -> addCategory());
                                        dataManager.fetchCategories(this::updateRecyclerView, this::showError);
                                        fetchCategoriesForSpinner();
                                    },
                                    this::showError);
                        },
                        this::showError);
            } else {
                dataManager.updateCategory(updatedCategory,
                        () -> {
                            Toast.makeText(activity, "Category updated", Toast.LENGTH_SHORT).show();
                            categoryNameInput.setText("");
                            imageUri = null;
                            addCategoryButton.setText("Add Category");
                            addCategoryButton.setOnClickListener(v1 -> addCategory());
                            dataManager.fetchCategories(this::updateRecyclerView, this::showError);
                            fetchCategoriesForSpinner();
                        },
                        this::showError);
            }
        });
    }

    /** Edits a lab test by populating the form and setting update logic. */
    private void editLabTest(LabTest labTest) {
        labTestNameInput.setText(labTest.getName());
        addLabTestButton.setText("Update Lab Test");
        addLabTestButton.setOnClickListener(v -> {
            String name = labTestNameInput.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(activity, "Please enter lab test name", Toast.LENGTH_SHORT).show();
                return;
            }
            LabTest updatedLabTest = new LabTest(labTest.getId(), name, labTest.getImageUrl());
            if (imageUri != null) {
                dataManager.uploadImageAndExecute(imageUri, "labTests",
                        imageUrl -> {
                            updatedLabTest.setImageUrl(imageUrl);
                            dataManager.updateLabTest(updatedLabTest,
                                    () -> {
                                        Toast.makeText(activity, "Lab Test updated", Toast.LENGTH_SHORT).show();
                                        labTestNameInput.setText("");
                                        imageUri = null;
                                        addLabTestButton.setText("Add Lab Test");
                                        addLabTestButton.setOnClickListener(v1 -> addLabTest());
                                        dataManager.fetchLabTests(this::updateRecyclerView, this::showError);
                                    },
                                    this::showError);
                        },
                        this::showError);
            } else {
                dataManager.updateLabTest(updatedLabTest,
                        () -> {
                            Toast.makeText(activity, "Lab Test updated", Toast.LENGTH_SHORT).show();
                            labTestNameInput.setText("");
                            imageUri = null;
                            addLabTestButton.setText("Add Lab Test");
                            addLabTestButton.setOnClickListener(v1 -> addLabTest());
                            dataManager.fetchLabTests(this::updateRecyclerView, this::showError);
                        },
                        this::showError);
            }
        });
    }

    /** Edits a product by populating the form and setting update logic. */
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
                Toast.makeText(activity, "Please select a category", Toast.LENGTH_SHORT).show();
                return;
            }
            String newCategoryId = categoryIds.get(categoryPosition);
            if (name.isEmpty() || quantity.isEmpty() || priceStr.isEmpty() || ratingStr.isEmpty() || reviewCountStr.isEmpty()) {
                Toast.makeText(activity, "Please fill all required fields", Toast.LENGTH_SHORT).show();
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
                Toast.makeText(activity, "Invalid number format in price, rating, or review count", Toast.LENGTH_SHORT).show();
                return;
            }
            Product updatedProduct = new Product(product.getId(), name, price, product.getImageUrl(), rating, reviewCount, quantity, price, discountedPrice);
            if (imageUri != null) {
                dataManager.uploadImageAndExecute(imageUri, "products",
                        imageUrl -> {
                            updatedProduct.setImageUrl(imageUrl);
                            dataManager.updateProduct(product, newCategoryId, updatedProduct,
                                    () -> {
                                        Toast.makeText(activity, "Product updated", Toast.LENGTH_SHORT).show();
                                        resetProductForm();
                                        addProductButton.setText("Add Product");
                                        addProductButton.setOnClickListener(v1 -> addProduct());
                                        dataManager.fetchProducts(this::updateRecyclerView, this::showError);
                                    },
                                    this::showError);
                        },
                        this::showError);
            } else {
                dataManager.updateProduct(product, newCategoryId, updatedProduct,
                        () -> {
                            Toast.makeText(activity, "Product updated", Toast.LENGTH_SHORT).show();
                            resetProductForm();
                            addProductButton.setText("Add Product");
                            addProductButton.setOnClickListener(v1 -> addProduct());
                            dataManager.fetchProducts(this::updateRecyclerView, this::showError);
                        },
                        this::showError);
            }
        });
    }
}