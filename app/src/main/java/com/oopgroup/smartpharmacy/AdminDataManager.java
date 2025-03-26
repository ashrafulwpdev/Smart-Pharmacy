package com.oopgroup.smartpharmacy;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
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
import com.oopgroup.smartpharmacy.models.LabTest;
import com.oopgroup.smartpharmacy.models.Product;
import com.oopgroup.smartpharmacy.utils.LoadingSpinnerUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * AdminDataManager: Manages Firebase interactions and all data-related operations using Firestore.
 */
public class AdminDataManager {
    private static final String TAG = "AdminDataManager";
    private static final int MAX_IMAGE_SIZE_MB = 5;

    // Firebase References
    private final FirebaseFirestore db;
    private final StorageReference storageRef;

    // Dependencies
    private final AppCompatActivity activity;
    private final FirebaseUser currentUser;
    private final LoadingSpinnerUtil loadingSpinnerUtil;

    public AdminDataManager(AppCompatActivity activity, FirebaseUser currentUser, LoadingSpinnerUtil loadingSpinnerUtil) {
        this.activity = activity;
        this.currentUser = currentUser;
        this.loadingSpinnerUtil = loadingSpinnerUtil;

        // Initialize Firebase References
        db = FirebaseFirestore.getInstance();
        storageRef = FirebaseStorage.getInstance().getReference();
    }

    /** Checks the user's role in Firestore. */
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

    /** Uploads an image to Firebase Storage and returns the URL. */
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

    /** Saves or updates a banner in Firestore. */
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

    /** Saves a category in Firestore. */
    public void saveCategory(Category category, Runnable onSuccess, Consumer<String> onFailure) {
        showLoading(true);
        db.collection("categories").document(category.getId()).set(category)
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

    /** Saves a lab test in Firestore. */
    public void saveLabTest(LabTest labTest, Runnable onSuccess, Consumer<String> onFailure) {
        showLoading(true);
        db.collection("labTests").document(labTest.getId()).set(labTest)
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

    /** Saves a product in Firestore and updates product count. */
    public void saveProduct(String categoryId, Product product, Runnable onSuccess, Consumer<String> onFailure) {
        showLoading(true);
        db.collection("products").document(product.getId()).set(product)
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

    /** Updates the product count for a category in Firestore. */
    public void updateProductCount(String categoryId, int change) {
        db.collection("categories").document(categoryId)
                .update("productCount", FieldValue.increment(change))
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Product count updated for category: " + categoryId))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update product count: " + e.getMessage());
                    // Handle initial case where category doesn't exist
                    if (e.getMessage().contains("No document to update")) {
                        Category newCategory = new Category(categoryId, "Unknown", change < 0 ? 0 : change, "");
                        db.collection("categories").document(categoryId).set(newCategory);
                    }
                });
    }

    /** Fetches banners from Firestore. */
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

    /** Fetches categories from Firestore. */
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

    /** Fetches lab tests from Firestore. */
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

    /** Fetches products from Firestore. */
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

    /** Fetches users from Firestore (non-admins only). */
    public void fetchUsers(Consumer<List<Object>> onSuccess, Consumer<String> onFailure) {
        showLoading(true);
        List<Object> itemList = new ArrayList<>();
        db.collection("users").whereNotEqualTo("role", "admin").get()
                .addOnSuccessListener(querySnapshot -> {
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Map<String, Object> user = new HashMap<>();
                        user.put("id", doc.getId());
                        user.put("name", doc.getString("name"));
                        user.put("email", doc.getString("email"));
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

    /** Fetches orders from Firestore. */
    public void fetchOrders(Consumer<List<Object>> onSuccess, Consumer<String> onFailure) {
        showLoading(true);
        List<Object> itemList = new ArrayList<>();
        db.collection("orders").get()
                .addOnSuccessListener(querySnapshot -> {
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Map<String, Object> order = new HashMap<>();
                        order.put("id", doc.getId());
                        order.put("userId", doc.getString("userId"));
                        order.put("status", doc.getString("status"));
                        itemList.add(order);
                    }
                    onSuccess.accept(itemList);
                    showLoading(false);
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    onFailure.accept("Failed to fetch orders: " + e.getMessage());
                });
    }

    /** Sends a notification to Firestore. */
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

    /** Updates scanner settings in Firestore. */
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

    /** Fetches categories for spinner population from Firestore. */
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

    /** Deletes a banner from Firestore. */
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

    /** Deletes a category from Firestore (products must be deleted separately). */
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

    /** Deletes a lab test from Firestore. */
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

    /** Deletes a product from Firestore and updates product count. */
    public void deleteProduct(Product product, Runnable onSuccess, Consumer<String> onFailure) {
        showLoading(true);
        db.collection("products").document(product.getId()).delete()
                .addOnSuccessListener(aVoid -> {
                    // Note: categoryId isn't stored in Product, so you'll need to fetch it or pass it
                    // For now, assuming categoryId is managed elsewhere or not needed
                    // updateProductCount(categoryId, -1); // Uncomment if categoryId is available
                    showLoading(false);
                    onSuccess.run();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Delete failed: " + e.getMessage());
                    onFailure.accept("Delete failed: " + e.getMessage());
                });
    }

    /** Deletes a user from Firestore. */
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

    /** Deletes an order from Firestore. */
    public void deleteOrder(String orderId, Runnable onSuccess, Consumer<String> onFailure) {
        showLoading(true);
        db.collection("orders").document(orderId).delete()
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

    /** Updates a category in Firestore. */
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

    /** Updates a lab test in Firestore. */
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

    /** Updates a product in Firestore, handling category changes. */
    public void updateProduct(Product originalProduct, String newCategoryId, Product updatedProduct, Runnable onSuccess, Consumer<String> onFailure) {
        showLoading(true);
        db.collection("products").document(updatedProduct.getId()).set(updatedProduct)
                .addOnSuccessListener(aVoid -> {
                    // Note: If categoryId changes, you'll need to update productCount separately
                    // For simplicity, assuming categoryId is not stored in Product
                    showLoading(false);
                    onSuccess.run();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Failed to update product: " + e.getMessage());
                    onFailure.accept("Failed: " + e.getMessage());
                });
    }

    /** Toggles loading spinner visibility. */
    private void showLoading(boolean isLoading) {
        Log.d(TAG, "Loading state: " + isLoading);
        loadingSpinnerUtil.toggleLoadingSpinner(isLoading);
    }
}