package com.oopgroup.smartpharmacy.adminstaff;

import android.net.Uri;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.oopgroup.smartpharmacy.models.Banner;
import com.oopgroup.smartpharmacy.models.Category;
import com.oopgroup.smartpharmacy.models.Coupon;
import com.oopgroup.smartpharmacy.models.LabTest;
import com.oopgroup.smartpharmacy.models.Product;
import com.oopgroup.smartpharmacy.models.User;
import com.oopgroup.smartpharmacy.utils.LoadingSpinnerUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class AdminDataManager {
    private static final String TAG = "AdminDataManager";
    private static final int MAX_IMAGE_SIZE_MB = 5;

    private final FirebaseFirestore db;
    private final StorageReference storageRef;
    private final AppCompatActivity activity;
    private final FirebaseUser currentUser;
    private final LoadingSpinnerUtil loadingSpinnerUtil; // Nullable field

    // Updated constructor to make LoadingSpinnerUtil optional
    public AdminDataManager(AppCompatActivity activity, FirebaseUser currentUser, LoadingSpinnerUtil loadingSpinnerUtil) {
        this.activity = activity;
        this.currentUser = currentUser;
        this.loadingSpinnerUtil = loadingSpinnerUtil; // Can be null
        db = FirebaseFirestore.getInstance();
        storageRef = FirebaseStorage.getInstance().getReference();
    }

    // Overloaded constructor without LoadingSpinnerUtil
    public AdminDataManager(AppCompatActivity activity, FirebaseUser currentUser) {
        this(activity, currentUser, null);
    }

    public void checkUserRole(Consumer<String> onSuccess, Consumer<String> onFailure) {
        if (currentUser == null) {
            onFailure.accept("No user logged in");
            return;
        }
        db.collection("users").document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        onFailure.accept("User document does not exist!");
                        return;
                    }
                    String role = documentSnapshot.getString("role");
                    if (role == null) {
                        onFailure.accept("Role field is missing!");
                        return;
                    }
                    onSuccess.accept(role);
                })
                .addOnFailureListener(e -> onFailure.accept(e.getMessage()));
    }

    public void uploadImageAndExecute(Uri imageUri, String path, Consumer<String> onSuccess, Consumer<String> onFailure) {
        if (imageUri == null || currentUser == null) {
            onFailure.accept("Please select an image or log in.");
            return;
        }

        try (InputStream inputStream = activity.getContentResolver().openInputStream(imageUri)) {
            int fileSize = inputStream != null ? inputStream.available() : 0;
            if (fileSize > MAX_IMAGE_SIZE_MB * 1024 * 1024) {
                onFailure.accept("File size exceeds " + MAX_IMAGE_SIZE_MB + " MB.");
                return;
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to check file size: " + e.getMessage());
            onFailure.accept("Error checking file size.");
            return;
        }

        showLoading(true);
        String fileName = System.currentTimeMillis() + ".jpg";
        StorageReference fileRef = storageRef.child(path).child(fileName);
        StorageMetadata metadata = new StorageMetadata.Builder()
                .setContentType(activity.getContentResolver().getType(imageUri) != null && activity.getContentResolver().getType(imageUri).startsWith("image/") ? activity.getContentResolver().getType(imageUri) : "image/jpeg")
                .build();

        fileRef.putFile(imageUri, metadata)
                .addOnSuccessListener(taskSnapshot -> fileRef.getDownloadUrl()
                        .addOnSuccessListener(uri -> {
                            showLoading(false);
                            onSuccess.accept(uri.toString());
                        }))
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Image upload failed: " + e.getMessage());
                    onFailure.accept("Upload failed: " + e.getMessage());
                });
    }

    public void saveBanner(Map<String, Object> bannerData, String bannerId, Runnable onSuccess, Consumer<String> onFailure) {
        showLoading(true);
        String id = bannerId != null ? bannerId : db.collection("banners").document().getId();
        db.collection("banners").document(id).set(bannerData)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    onSuccess.run();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Failed to save banner: " + e.getMessage());
                    onFailure.accept("Failed: " + e.getMessage());
                });
    }

    public void saveCategory(Category category, Runnable onSuccess, Consumer<String> onFailure) {
        showLoading(true);
        String id = category.getId() != null ? category.getId() : db.collection("categories").document().getId();
        category.setId(id);
        db.collection("categories").document(id).set(category)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    onSuccess.run();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Failed to add category: " + e.getMessage());
                    onFailure.accept("Failed: " + e.getMessage());
                });
    }

    public void saveLabTest(LabTest labTest, Runnable onSuccess, Consumer<String> onFailure) {
        showLoading(true);
        String id = labTest.getId() != null ? labTest.getId() : db.collection("labTests").document().getId();
        labTest.setId(id);
        db.collection("labTests").document(id).set(labTest)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    onSuccess.run();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Failed to add lab test: " + e.getMessage());
                    onFailure.accept("Failed: " + e.getMessage());
                });
    }

    public void saveProduct(String categoryId, Product product, Runnable onSuccess, Consumer<String> onFailure) {
        showLoading(true);
        String id = product.getId() != null ? product.getId() : db.collection("products").document().getId();
        product.setId(id);
        db.collection("products").document(id).set(product)
                .addOnSuccessListener(aVoid -> {
                    updateProductCount(categoryId, 1);
                    showLoading(false);
                    onSuccess.run();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Failed to add product: " + e.getMessage());
                    onFailure.accept("Failed: " + e.getMessage());
                });
    }

    public void updateProductCount(String categoryId, int change) {
        db.collection("categories").document(categoryId)
                .update("productCount", FieldValue.increment(change))
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Product count updated for category: " + categoryId))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update product count: " + e.getMessage());
                    if (e.getMessage().contains("No document to update")) {
                        Category newCategory = new Category(categoryId, "Unknown", change < 0 ? 0 : change, "");
                        db.collection("categories").document(categoryId).set(newCategory);
                    }
                });
    }

    public void fetchBanners(Consumer<List<Object>> onSuccess, Consumer<String> onFailure) {
        showLoading(true);
        List<Object> itemList = new ArrayList<>();
        db.collection("banners").get()
                .addOnSuccessListener(querySnapshot -> {
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Banner banner = doc.toObject(Banner.class);
                        banner.setId(doc.getId());
                        itemList.add(banner);
                        Log.d(TAG, "Added banner: " + banner.getTitle());
                    }
                    onSuccess.accept(itemList);
                    showLoading(false);
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    onFailure.accept("Fetch banners failed: " + e.getMessage());
                });
    }

    public void fetchCategories(Consumer<List<Object>> onSuccess, Consumer<String> onFailure) {
        showLoading(true);
        List<Object> itemList = new ArrayList<>();
        db.collection("categories").get()
                .addOnSuccessListener(querySnapshot -> {
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Category category = doc.toObject(Category.class);
                        category.setId(doc.getId());
                        itemList.add(category);
                        Log.d(TAG, "Added category: " + category.getName());
                    }
                    onSuccess.accept(itemList);
                    showLoading(false);
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    onFailure.accept("Failed to fetch categories: " + e.getMessage());
                });
    }

    public void fetchLabTests(Consumer<List<Object>> onSuccess, Consumer<String> onFailure) {
        showLoading(true);
        List<Object> itemList = new ArrayList<>();
        db.collection("labTests").get()
                .addOnSuccessListener(querySnapshot -> {
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        LabTest labTest = doc.toObject(LabTest.class);
                        labTest.setId(doc.getId());
                        itemList.add(labTest);
                        Log.d(TAG, "Added lab test: " + labTest.getName());
                    }
                    onSuccess.accept(itemList);
                    showLoading(false);
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    onFailure.accept("Failed to fetch lab tests: " + e.getMessage());
                });
    }

    public void fetchProducts(Consumer<List<Object>> onSuccess, Consumer<String> onFailure) {
        showLoading(true);
        List<Object> itemList = new ArrayList<>();
        db.collection("products").get()
                .addOnSuccessListener(querySnapshot -> {
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Product product = doc.toObject(Product.class);
                        product.setId(doc.getId());
                        itemList.add(product);
                        Log.d(TAG, "Added product: " + product.getName());
                    }
                    onSuccess.accept(itemList);
                    showLoading(false);
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    onFailure.accept("Failed to fetch products: " + e.getMessage());
                });
    }

    public void fetchUsers(Consumer<List<Object>> onSuccess, Consumer<String> onFailure) {
        showLoading(true);
        List<Object> itemList = new ArrayList<>();
        db.collection("users").whereNotEqualTo("role", "admin").get()
                .addOnSuccessListener(querySnapshot -> {
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        User user = doc.toObject(User.class);
                        user.setId(doc.getId());
                        itemList.add(user);
                    }
                    onSuccess.accept(itemList);
                    showLoading(false);
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    onFailure.accept("Failed to fetch users: " + e.getMessage());
                });
    }

    public void sendNotification(String title, String message, String target, Runnable onSuccess, Consumer<String> onFailure) {
        showLoading(true);
        String notificationId = db.collection("notifications").document().getId();
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("title", title);
        notificationData.put("message", message);
        notificationData.put("target", target);
        notificationData.put("timestamp", System.currentTimeMillis());

        db.collection("notifications").document(notificationId).set(notificationData)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    onSuccess.run();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Failed to send notification: " + e.getMessage());
                    onFailure.accept("Failed to send notification: " + e.getMessage());
                });
    }

    public void updateScannerSettings(String instructions, Runnable onSuccess, Consumer<String> onFailure) {
        showLoading(true);
        Map<String, Object> settings = new HashMap<>();
        settings.put("instructions", instructions);
        db.collection("scannerSettings").document("settings").set(settings)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    onSuccess.run();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Failed to update scanner settings: " + e.getMessage());
                    onFailure.accept("Failed: " + e.getMessage());
                });
    }

    public void fetchScannerSettings(Consumer<String> onSuccess, Consumer<String> onFailure) {
        showLoading(true);
        db.collection("scannerSettings").document("settings").get()
                .addOnSuccessListener(documentSnapshot -> {
                    String instructions = documentSnapshot.getString("instructions");
                    onSuccess.accept(instructions != null ? instructions : "");
                    showLoading(false);
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    onFailure.accept("Failed to fetch scanner settings: " + e.getMessage());
                });
    }

    public void fetchCategoriesForSpinner(Consumer<List<String>> namesConsumer, Consumer<List<String>> idsConsumer, Consumer<String> onFailure) {
        List<String> categoryNames = new ArrayList<>();
        List<String> categoryIds = new ArrayList<>();
        db.collection("categories").get()
                .addOnSuccessListener(querySnapshot -> {
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String name = doc.getString("name");
                        if (name != null) {
                            categoryNames.add(name);
                            categoryIds.add(doc.getId());
                        }
                    }
                    namesConsumer.accept(categoryNames);
                    idsConsumer.accept(categoryIds);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch categories for spinner: " + e.getMessage());
                    onFailure.accept("Failed to load categories for spinner: " + e.getMessage());
                });
    }

    public void deleteBanner(Banner banner, Runnable onSuccess, Consumer<String> onFailure) {
        showLoading(true);
        db.collection("banners").document(banner.getId()).delete()
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    onSuccess.run();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Delete failed: " + e.getMessage());
                    onFailure.accept("Delete failed: " + e.getMessage());
                });
    }

    public void deleteCategory(Category category, Runnable onSuccess, Consumer<String> onFailure) {
        showLoading(true);
        db.collection("categories").document(category.getId()).delete()
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    onSuccess.run();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Delete failed: " + e.getMessage());
                    onFailure.accept("Delete failed: " + e.getMessage());
                });
    }

    public void deleteLabTest(LabTest labTest, Runnable onSuccess, Consumer<String> onFailure) {
        showLoading(true);
        db.collection("labTests").document(labTest.getId()).delete()
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    onSuccess.run();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Delete failed: " + e.getMessage());
                    onFailure.accept("Delete failed: " + e.getMessage());
                });
    }

    public void deleteProduct(Product product, Runnable onSuccess, Consumer<String> onFailure) {
        showLoading(true);
        db.collection("products").document(product.getId()).delete()
                .addOnSuccessListener(aVoid -> {
                    updateProductCount(product.getCategoryId(), -1);
                    showLoading(false);
                    onSuccess.run();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Delete failed: " + e.getMessage());
                    onFailure.accept("Delete failed: " + e.getMessage());
                });
    }

    public void deleteUser(String userId, Runnable onSuccess, Consumer<String> onFailure) {
        showLoading(true);
        db.collection("users").document(userId).delete()
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    onSuccess.run();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Delete failed: " + e.getMessage());
                    onFailure.accept("Delete failed: " + e.getMessage());
                });
    }

    public void updateCategory(Category category, Runnable onSuccess, Consumer<String> onFailure) {
        showLoading(true);
        db.collection("categories").document(category.getId()).set(category)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    onSuccess.run();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Failed to update category: " + e.getMessage());
                    onFailure.accept("Failed: " + e.getMessage());
                });
    }

    public void updateLabTest(LabTest labTest, Runnable onSuccess, Consumer<String> onFailure) {
        showLoading(true);
        db.collection("labTests").document(labTest.getId()).set(labTest)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    onSuccess.run();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Failed to update lab test: " + e.getMessage());
                    onFailure.accept("Failed: " + e.getMessage());
                });
    }

    public void updateProduct(Product originalProduct, String newCategoryId, Product updatedProduct, Runnable onSuccess, Consumer<String> onFailure) {
        showLoading(true);
        db.collection("products").document(updatedProduct.getId()).set(updatedProduct)
                .addOnSuccessListener(aVoid -> {
                    if (!originalProduct.getCategoryId().equals(newCategoryId)) {
                        updateProductCount(originalProduct.getCategoryId(), -1);
                        updateProductCount(newCategoryId, 1);
                    }
                    showLoading(false);
                    onSuccess.run();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Failed to update product: " + e.getMessage());
                    onFailure.accept("Failed: " + e.getMessage());
                });
    }

    public void saveCoupon(Coupon coupon, Runnable onSuccess, Consumer<String> onFailure) {
        showLoading(true);
        String id = coupon.getId() != null ? coupon.getId() : db.collection("coupons").document().getId();
        coupon.setId(id);
        db.collection("coupons").document(id).set(coupon.toMap())
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    onSuccess.run();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Failed to save coupon: " + e.getMessage());
                    onFailure.accept("Failed to save coupon: " + e.getMessage());
                });
    }

    public void fetchCoupons(Consumer<List<Object>> onSuccess, Consumer<String> onFailure) {
        showLoading(true);
        List<Object> itemList = new ArrayList<>();
        db.collection("coupons").get()
                .addOnSuccessListener(querySnapshot -> {
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Coupon coupon = Coupon.fromMap(doc.getData(), doc.getId());
                        itemList.add(coupon);
                    }
                    onSuccess.accept(itemList);
                    showLoading(false);
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    onFailure.accept("Failed to fetch coupons: " + e.getMessage());
                });
    }

    public void updateCoupon(Coupon coupon, Runnable onSuccess, Consumer<String> onFailure) {
        showLoading(true);
        db.collection("coupons").document(coupon.getId()).set(coupon.toMap())
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    onSuccess.run();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Failed to update coupon: " + e.getMessage());
                    onFailure.accept("Failed: " + e.getMessage());
                });
    }

    public void deleteCoupon(Coupon coupon, Runnable onSuccess, Consumer<String> onFailure) {
        showLoading(true);
        // Soft delete: set isActive to false
        coupon.setActive(false);
        db.collection("coupons").document(coupon.getId()).set(coupon.toMap())
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    Log.d(TAG, "Coupon soft-deleted (isActive set to false): " + coupon.getId());
                    onSuccess.run();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Failed to soft-delete coupon: " + e.getMessage());
                    onFailure.accept("Failed to soft-delete coupon: " + e.getMessage());
                });
    }

    private void fixCouponsIsActiveField() {
        db.collection("coupons")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Object isActiveObj = doc.get("isActive");
                        if (isActiveObj == null) {
                            db.collection("coupons")
                                    .document(doc.getId())
                                    .update("isActive", true)
                                    .addOnSuccessListener(aVoid -> {
                                        Log.d(TAG, "Added isActive: true to coupon: " + doc.getId());
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Failed to add isActive to coupon " + doc.getId() + ": " + e.getMessage(), e);
                                    });
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch coupons for isActive fix: " + e.getMessage(), e);
                });
    }

    private void showLoading(boolean show) {
        if (loadingSpinnerUtil != null) {
            loadingSpinnerUtil.toggleLoadingSpinner(show);
        } else {
            Log.w(TAG, "Loading spinner utility is null, skipping visibility toggle");
        }
    }
}