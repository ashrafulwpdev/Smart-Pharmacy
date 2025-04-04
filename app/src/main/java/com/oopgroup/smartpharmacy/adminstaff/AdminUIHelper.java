package com.oopgroup.smartpharmacy.adminstaff;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.adapters.AdminItemAdapter;
import com.oopgroup.smartpharmacy.models.Banner;
import com.oopgroup.smartpharmacy.models.Category;
import com.oopgroup.smartpharmacy.models.Coupon;
import com.oopgroup.smartpharmacy.models.LabTest;
import com.oopgroup.smartpharmacy.models.Product;
import com.oopgroup.smartpharmacy.models.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminUIHelper implements AdminItemAdapter.OnItemClickListener {
    private static final String TAG = "AdminUIHelper";
    private static final int PICK_IMAGE_REQUEST = 1;

    private CardView bannerSection, categoriesSection, labTestsSection, productsSection, usersSection, notificationsSection, scannerSection, couponsSection;
    private EditText bannerTitleInput, bannerDescriptionInput, bannerDiscountInput;
    private EditText categoryNameInput;
    private EditText labTestNameInput;
    private EditText productNameInput, productPriceInput, productRatingInput, productReviewCountInput, productQuantityInput, productDiscountPriceInput, productDescriptionInput, productBrandInput;
    private EditText notificationTitleInput, notificationMessageInput;
    private EditText scannerInstructionsInput;
    private EditText couponCodeInput, couponDiscountInput;
    private MaterialButton uploadBannerImageButton, uploadCategoryImageButton, uploadLabTestImageButton, uploadProductImageButton;
    private MaterialButton addBannerButton, addCategoryButton, addLabTestButton, addProductButton, refreshUsersButton, sendNotificationButton, updateScannerSettingsButton, addCouponButton;
    private Spinner categorySpinner, notificationTargetSpinner;
    private RecyclerView adminRecyclerView;
    private TextView emptyView;
    private SwipeRefreshLayout swipeRefreshLayout;

    private final AdminDataManager dataManager;
    private AdminItemAdapter adminAdapter;
    private List<Object> itemList;
    private List<String> categoryNames;
    private List<String> categoryIds;
    private Uri imageUri;
    private String currentTab = "banner";
    private Banner currentBanner;

    private final AppCompatActivity activity;
    private final View rootView;
    private final ActivityResultLauncher<String> imagePickerLauncher;

    public AdminUIHelper(AppCompatActivity activity, AdminDataManager dataManager, View rootView) {
        this.activity = activity;
        this.dataManager = dataManager;
        this.rootView = rootView;
        this.swipeRefreshLayout = rootView.findViewById(R.id.swipeRefreshLayout);
        this.itemList = new ArrayList<>();
        this.categoryNames = new ArrayList<>();
        this.categoryIds = new ArrayList<>();
        this.imagePickerLauncher = ((AdminMainActivity) activity).getImagePickerLauncher();

        adminRecyclerView = rootView.findViewById(R.id.adminRecyclerView);
        setupRecyclerView();
    }

    public void initializeUI() {
        bannerSection = rootView.findViewById(R.id.bannerSection);
        categoriesSection = rootView.findViewById(R.id.categoriesSection);
        labTestsSection = rootView.findViewById(R.id.labTestsSection);
        productsSection = rootView.findViewById(R.id.productsSection);
        usersSection = rootView.findViewById(R.id.usersSection);
        notificationsSection = rootView.findViewById(R.id.notificationsSection);
        scannerSection = rootView.findViewById(R.id.scannerSection);
        couponsSection = rootView.findViewById(R.id.couponsSection);

        bannerTitleInput = rootView.findViewById(R.id.bannerTitleInput);
        bannerDescriptionInput = rootView.findViewById(R.id.bannerDescriptionInput);
        bannerDiscountInput = rootView.findViewById(R.id.bannerDiscountInput);
        categoryNameInput = rootView.findViewById(R.id.categoryNameInput);
        labTestNameInput = rootView.findViewById(R.id.labTestNameInput);
        productNameInput = rootView.findViewById(R.id.productNameInput);
        productPriceInput = rootView.findViewById(R.id.productPriceInput);
        productRatingInput = rootView.findViewById(R.id.productRatingInput);
        productReviewCountInput = rootView.findViewById(R.id.productReviewCountInput);
        productQuantityInput = rootView.findViewById(R.id.productQuantityInput);
        productDiscountPriceInput = rootView.findViewById(R.id.productDiscountPriceInput);
        productDescriptionInput = rootView.findViewById(R.id.productDescriptionInput);
        productBrandInput = rootView.findViewById(R.id.productBrandInput);
        notificationTitleInput = rootView.findViewById(R.id.notificationTitleInput);
        notificationMessageInput = rootView.findViewById(R.id.notificationMessageInput);
        scannerInstructionsInput = rootView.findViewById(R.id.scannerInstructionsInput);
        couponCodeInput = rootView.findViewById(R.id.couponCodeInput);
        couponDiscountInput = rootView.findViewById(R.id.couponDiscountInput);

        uploadBannerImageButton = rootView.findViewById(R.id.uploadBannerImageButton);
        uploadCategoryImageButton = rootView.findViewById(R.id.uploadCategoryImageButton);
        uploadLabTestImageButton = rootView.findViewById(R.id.uploadLabTestImageButton);
        uploadProductImageButton = rootView.findViewById(R.id.uploadProductImageButton);
        addBannerButton = rootView.findViewById(R.id.addBannerButton);
        addCategoryButton = rootView.findViewById(R.id.addCategoryButton);
        addLabTestButton = rootView.findViewById(R.id.addLabTestButton);
        addProductButton = rootView.findViewById(R.id.addProductButton);
        refreshUsersButton = rootView.findViewById(R.id.refreshUsersButton);
        sendNotificationButton = rootView.findViewById(R.id.sendNotificationButton);
        updateScannerSettingsButton = rootView.findViewById(R.id.updateScannerSettingsButton);
        addCouponButton = rootView.findViewById(R.id.addCouponButton);

        categorySpinner = rootView.findViewById(R.id.categorySpinner);
        notificationTargetSpinner = rootView.findViewById(R.id.notificationTargetSpinner);

        emptyView = rootView.findViewById(R.id.emptyView);

        uploadBannerImageButton.setOnClickListener(v -> openImagePicker());
        uploadCategoryImageButton.setOnClickListener(v -> openImagePicker());
        uploadLabTestImageButton.setOnClickListener(v -> openImagePicker());
        uploadProductImageButton.setOnClickListener(v -> openImagePicker());
        addBannerButton.setOnClickListener(v -> addOrUpdateBanner());
        addCategoryButton.setOnClickListener(v -> addCategory());
        addLabTestButton.setOnClickListener(v -> addLabTest());
        addProductButton.setOnClickListener(v -> addProduct());
        refreshUsersButton.setOnClickListener(v -> refreshUsers());
        sendNotificationButton.setOnClickListener(v -> sendNotification());
        updateScannerSettingsButton.setOnClickListener(v -> updateScannerSettings());
        addCouponButton.setOnClickListener(v -> addCoupon());

        fetchCategoriesForSpinner();
        setupNotificationTargetSpinner();

        swipeRefreshLayout.setOnRefreshListener(this::refreshCurrentSection);

        showSection("banner");
    }

    public void refreshCurrentSection() {
        Log.d(TAG, "Refreshing current section: " + currentTab);
        swipeRefreshLayout.setRefreshing(true);
        switch (currentTab) {
            case "banner":
                dataManager.fetchBanners(items -> updateRecyclerView(items, true), this::showError);
                break;
            case "categories":
                dataManager.fetchCategories(items -> updateRecyclerView(items, true), this::showError);
                break;
            case "labTests":
                dataManager.fetchLabTests(items -> updateRecyclerView(items, true), this::showError);
                break;
            case "products":
                dataManager.fetchProducts(items -> updateRecyclerView(items, true), this::showError);
                break;
            case "users":
                refreshUsers();
                break;
            case "coupons":
                dataManager.fetchCoupons(items -> updateRecyclerView(items, true), this::showError);
                break;
            default:
                swipeRefreshLayout.setRefreshing(false);
                break;
        }
    }

    private void setupRecyclerView() {
        if (adminRecyclerView == null) {
            Log.e(TAG, "adminRecyclerView is null.");
            Toast.makeText(activity, "Error: RecyclerView not found", Toast.LENGTH_LONG).show();
            return;
        }

        adminRecyclerView.setNestedScrollingEnabled(false);
        adminRecyclerView.setLayoutManager(new LinearLayoutManager(activity));
        adminRecyclerView.setHasFixedSize(true);
        adminAdapter = new AdminItemAdapter(itemList, this, this::onDeleteItem);
        adminRecyclerView.setAdapter(adminAdapter);
        Log.d(TAG, "RecyclerView setup completed");
    }

    private void setupCategorySpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, categoryNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(adapter);
    }

    private void setupNotificationTargetSpinner() {
        List<String> targets = new ArrayList<>();
        targets.add("All Users");
        targets.add("Specific User");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, targets);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        notificationTargetSpinner.setAdapter(adapter);
    }

    public void showSection(String section) {
        Log.d(TAG, "Showing section: " + section);
        currentTab = section;
        itemList.clear();
        adminAdapter.notifyDataSetChanged();
        adminRecyclerView.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);

        bannerSection.setVisibility(View.GONE);
        categoriesSection.setVisibility(View.GONE);
        labTestsSection.setVisibility(View.GONE);
        productsSection.setVisibility(View.GONE);
        usersSection.setVisibility(View.GONE);
        notificationsSection.setVisibility(View.GONE);
        scannerSection.setVisibility(View.GONE);
        couponsSection.setVisibility(View.GONE);

        switch (section) {
            case "banner":
                bannerSection.setVisibility(View.VISIBLE);
                resetBannerForm();
                dataManager.fetchBanners(items -> updateRecyclerView(items, true), this::showError);
                break;
            case "categories":
                categoriesSection.setVisibility(View.VISIBLE);
                categoryNameInput.setText("");
                dataManager.fetchCategories(items -> updateRecyclerView(items, true), this::showError);
                break;
            case "labTests":
                labTestsSection.setVisibility(View.VISIBLE);
                labTestNameInput.setText("");
                dataManager.fetchLabTests(items -> updateRecyclerView(items, true), this::showError);
                break;
            case "products":
                productsSection.setVisibility(View.VISIBLE);
                resetProductForm();
                dataManager.fetchProducts(items -> updateRecyclerView(items, true), this::showError);
                break;
            case "users":
                usersSection.setVisibility(View.VISIBLE);
                refreshUsers();
                break;
            case "notifications":
                notificationsSection.setVisibility(View.VISIBLE);
                notificationTitleInput.setText("");
                notificationMessageInput.setText("");
                break;
            case "scanner":
                scannerSection.setVisibility(View.VISIBLE);
                fetchScannerInstructions();
                break;
            case "coupons":
                couponsSection.setVisibility(View.VISIBLE);
                couponCodeInput.setText("");
                couponDiscountInput.setText("");
                dataManager.fetchCoupons(items -> updateRecyclerView(items, true), this::showError);
                break;
        }
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
        } else if (item instanceof User) {
            // Handle user edit if needed
        } else if (item instanceof Coupon) {
            editCoupon((Coupon) item);
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
        } else if (item instanceof Coupon) {
            dataManager.deleteCoupon((Coupon) item,
                    () -> {
                        Toast.makeText(activity, "Coupon deleted", Toast.LENGTH_SHORT).show();
                        dataManager.fetchCoupons(this::updateRecyclerView, this::showError);
                    },
                    this::showError);
        }
    }

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
                    Category category = new Category(null, name, 0, imageUrl);
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
                    LabTest labTest = new LabTest(null, name, imageUrl);
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

    private void addProduct() {
        String name = productNameInput.getText().toString().trim();
        String quantity = productQuantityInput.getText().toString().trim();
        String priceStr = productPriceInput.getText().toString().trim();
        String discountPriceStr = productDiscountPriceInput.getText().toString().trim();
        String ratingStr = productRatingInput.getText().toString().trim();
        String reviewCountStr = productReviewCountInput.getText().toString().trim();
        String description = productDescriptionInput.getText().toString().trim();
        String brand = productBrandInput.getText().toString().trim();
        int categoryPosition = categorySpinner.getSelectedItemPosition();

        if (categoryPosition < 0 || categoryIds.isEmpty()) {
            Toast.makeText(activity, "Please select a category", Toast.LENGTH_SHORT).show();
            return;
        }
        String categoryId = categoryIds.get(categoryPosition);

        if (name.isEmpty() || quantity.isEmpty() || priceStr.isEmpty() || ratingStr.isEmpty() || reviewCountStr.isEmpty() || description.isEmpty() || brand.isEmpty()) {
            Toast.makeText(activity, "Please fill all required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (imageUri == null) {
            Toast.makeText(activity, "Please upload an image", Toast.LENGTH_SHORT).show();
            return;
        }

        double price;
        double discountPrice;
        int rating;
        int reviewCount;

        try {
            price = Double.parseDouble(priceStr);
            discountPrice = discountPriceStr.isEmpty() ? 0.0 : Double.parseDouble(discountPriceStr);
            rating = Integer.parseInt(ratingStr);
            reviewCount = Integer.parseInt(reviewCountStr);
        } catch (NumberFormatException e) {
            Toast.makeText(activity, "Invalid number format in price, rating, or review count", Toast.LENGTH_SHORT).show();
            return;
        }

        dataManager.uploadImageAndExecute(imageUri, "products",
                imageUrl -> {
                    double discountPercentage = 0.0;
                    if (discountPrice > 0 && price > discountPrice) {
                        discountPercentage = ((price - discountPrice) / price) * 100;
                        discountPercentage = Math.round(discountPercentage * 10) / 10.0;
                    }

                    Product product = new Product(
                            null, name, price, imageUrl, rating, reviewCount, quantity,
                            price, discountPrice, description, "", "", categoryId
                    );
                    product.setBrand(brand);
                    product.setNameLower(name.toLowerCase());
                    product.setDiscountPercentage(discountPercentage);

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

    private void refreshUsers() {
        dataManager.fetchUsers(items -> {
            updateRecyclerView(items, true);
            swipeRefreshLayout.setRefreshing(false);
        }, error -> {
            showError(error);
            swipeRefreshLayout.setRefreshing(false);
        });
    }

    private void sendNotification() {
        String title = notificationTitleInput.getText().toString().trim();
        String message = notificationMessageInput.getText().toString().trim();
        int targetPosition = notificationTargetSpinner.getSelectedItemPosition();

        if (title.isEmpty() || message.isEmpty()) {
            Toast.makeText(activity, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        String target = targetPosition == 0 ? "all" : "specific";
        dataManager.sendNotification(title, message, target,
                () -> {
                    Toast.makeText(activity, "Notification sent", Toast.LENGTH_SHORT).show();
                    notificationTitleInput.setText("");
                    notificationMessageInput.setText("");
                },
                this::showError);
    }

    private void updateScannerSettings() {
        String instructions = scannerInstructionsInput.getText().toString().trim();
        if (instructions.isEmpty()) {
            Toast.makeText(activity, "Please enter scanner instructions", Toast.LENGTH_SHORT).show();
            return;
        }

        dataManager.updateScannerSettings(instructions,
                () -> Toast.makeText(activity, "Scanner settings updated", Toast.LENGTH_SHORT).show(),
                this::showError);
    }

    private void fetchScannerInstructions() {
        dataManager.fetchScannerSettings(
                instructions -> scannerInstructionsInput.setText(instructions),
                this::showError);
    }

    private void addCoupon() {
        String code = couponCodeInput.getText().toString().trim();
        String discountStr = couponDiscountInput.getText().toString().trim();

        if (code.isEmpty() || discountStr.isEmpty()) {
            Toast.makeText(activity, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        double discount;
        try {
            discount = Double.parseDouble(discountStr);
        } catch (NumberFormatException e) {
            Toast.makeText(activity, "Invalid discount format", Toast.LENGTH_SHORT).show();
            return;
        }

        Coupon coupon = new Coupon(null, code, discount);
        dataManager.saveCoupon(coupon,
                () -> {
                    Toast.makeText(activity, "Coupon added", Toast.LENGTH_SHORT).show();
                    couponCodeInput.setText("");
                    couponDiscountInput.setText("");
                    dataManager.fetchCoupons(this::updateRecyclerView, this::showError);
                },
                this::showError);
    }

    private void resetBannerForm() {
        bannerTitleInput.setText("");
        bannerDescriptionInput.setText("");
        bannerDiscountInput.setText("");
        currentBanner = null;
        addBannerButton.setText(R.string.add_update_banner);
        imageUri = null;
    }

    private void resetProductForm() {
        productNameInput.setText("");
        productQuantityInput.setText("");
        productPriceInput.setText("");
        productDiscountPriceInput.setText("");
        productRatingInput.setText("");
        productReviewCountInput.setText("");
        productDescriptionInput.setText("");
        productBrandInput.setText("");
        imageUri = null;
    }

    private void updateRecyclerView(List<Object> items) {
        updateRecyclerView(items, false);
    }

    private void updateRecyclerView(List<Object> items, boolean stopRefreshing) {
        Log.d(TAG, "Updating RecyclerView with " + items.size() + " items");
        itemList.clear();
        itemList.addAll(items);
        adminAdapter.notifyDataSetChanged();
        if (itemList.isEmpty()) {
            adminRecyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            adminRecyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }
        if (stopRefreshing) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    private void showError(String message) {
        Log.e(TAG, "Error: " + message);
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        swipeRefreshLayout.setRefreshing(false);
    }

    private void fetchCategoriesForSpinner() {
        dataManager.fetchCategoriesForSpinner(
                names -> {
                    categoryNames = names;
                    setupCategorySpinner();
                },
                ids -> categoryIds = ids,
                this::showError);
    }

    private void editBanner(Banner banner) {
        currentBanner = banner;
        bannerTitleInput.setText(banner.getTitle());
        bannerDescriptionInput.setText(banner.getDescription());
        bannerDiscountInput.setText(banner.getDiscount());
        addBannerButton.setText("Update Banner");
        imageUri = null;
    }

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

    private void editProduct(Product product) {
        productNameInput.setText(product.getName());
        productQuantityInput.setText(product.getQuantity());
        productPriceInput.setText(String.valueOf(product.getPrice()));
        productDiscountPriceInput.setText(product.getDiscountedPrice() != 0.0 ? String.valueOf(product.getDiscountedPrice()) : "");
        productRatingInput.setText(String.valueOf(product.getRating()));
        productReviewCountInput.setText(String.valueOf(product.getReviewCount()));
        productDescriptionInput.setText(product.getDescription());
        productBrandInput.setText(product.getBrand());
        int categoryPosition = categoryIds.indexOf(product.getCategoryId());
        if (categoryPosition >= 0) {
            categorySpinner.setSelection(categoryPosition);
        }
        addProductButton.setText("Update Product");
        addProductButton.setOnClickListener(v -> {
            String name = productNameInput.getText().toString().trim();
            String quantity = productQuantityInput.getText().toString().trim();
            String priceStr = productPriceInput.getText().toString().trim();
            String discountPriceStr = productDiscountPriceInput.getText().toString().trim();
            String ratingStr = productRatingInput.getText().toString().trim();
            String reviewCountStr = productReviewCountInput.getText().toString().trim();
            String description = productDescriptionInput.getText().toString().trim();
            String brand = productBrandInput.getText().toString().trim();
            int newCategoryPosition = categorySpinner.getSelectedItemPosition();
            if (newCategoryPosition < 0 || categoryIds.isEmpty()) {
                Toast.makeText(activity, "Please select a category", Toast.LENGTH_SHORT).show();
                return;
            }
            String newCategoryId = categoryIds.get(newCategoryPosition);
            if (name.isEmpty() || quantity.isEmpty() || priceStr.isEmpty() || ratingStr.isEmpty() || reviewCountStr.isEmpty() || description.isEmpty() || brand.isEmpty()) {
                Toast.makeText(activity, "Please fill all required fields", Toast.LENGTH_SHORT).show();
                return;
            }
            double price;
            double discountPrice;
            int rating;
            int reviewCount;
            try {
                price = Double.parseDouble(priceStr);
                discountPrice = discountPriceStr.isEmpty() ? 0.0 : Double.parseDouble(discountPriceStr);
                rating = Integer.parseInt(ratingStr);
                reviewCount = Integer.parseInt(reviewCountStr);
            } catch (NumberFormatException e) {
                Toast.makeText(activity, "Invalid number format in price, rating, or review count", Toast.LENGTH_SHORT).show();
                return;
            }

            if (imageUri != null) {
                dataManager.uploadImageAndExecute(imageUri, "products",
                        imageUrl -> {
                            double discountPercentage = 0.0;
                            if (discountPrice > 0 && price > discountPrice) {
                                discountPercentage = ((price - discountPrice) / price) * 100;
                                discountPercentage = Math.round(discountPercentage * 10) / 10.0;
                            }

                            Product updatedProduct = new Product(
                                    product.getId(), name, price, imageUrl, rating, reviewCount, quantity,
                                    price, discountPrice, description, "", "", newCategoryId
                            );
                            updatedProduct.setBrand(brand);
                            updatedProduct.setNameLower(name.toLowerCase());
                            updatedProduct.setDiscountPercentage(discountPercentage);

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
                double discountPercentage = 0.0;
                if (discountPrice > 0 && price > discountPrice) {
                    discountPercentage = ((price - discountPrice) / price) * 100;
                    discountPercentage = Math.round(discountPercentage * 10) / 10.0;
                }

                Product updatedProduct = new Product(
                        product.getId(), name, price, product.getImageUrl(), rating, reviewCount, quantity,
                        price, discountPrice, description, "", "", newCategoryId
                );
                updatedProduct.setBrand(brand);
                updatedProduct.setNameLower(name.toLowerCase());
                updatedProduct.setDiscountPercentage(discountPercentage);

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

    private void editCoupon(Coupon coupon) {
        couponCodeInput.setText(coupon.getCode());
        couponDiscountInput.setText(String.valueOf(coupon.getDiscount()));
        addCouponButton.setText("Update Coupon");
        addCouponButton.setOnClickListener(v -> {
            String code = couponCodeInput.getText().toString().trim();
            String discountStr = couponDiscountInput.getText().toString().trim();

            if (code.isEmpty() || discountStr.isEmpty()) {
                Toast.makeText(activity, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            double discount;
            try {
                discount = Double.parseDouble(discountStr);
            } catch (NumberFormatException e) {
                Toast.makeText(activity, "Invalid discount format", Toast.LENGTH_SHORT).show();
                return;
            }

            Coupon updatedCoupon = new Coupon(coupon.getId(), code, discount);
            dataManager.updateCoupon(updatedCoupon,
                    () -> {
                        Toast.makeText(activity, "Coupon updated", Toast.LENGTH_SHORT).show();
                        couponCodeInput.setText("");
                        couponDiscountInput.setText("");
                        addCouponButton.setText("Add Coupon");
                        addCouponButton.setOnClickListener(v1 -> addCoupon());
                        dataManager.fetchCoupons(this::updateRecyclerView, this::showError);
                    },
                    this::showError);
        });
    }

    public void handleImageResult(Uri imageUri) {
        if (imageUri != null) {
            this.imageUri = imageUri;
            Toast.makeText(activity, "Image selected", Toast.LENGTH_SHORT).show();
        }
    }
}