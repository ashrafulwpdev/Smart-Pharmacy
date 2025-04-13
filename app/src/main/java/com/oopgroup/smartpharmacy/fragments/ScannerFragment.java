package com.oopgroup.smartpharmacy.fragments;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.adapters.PrescriptionResultAdapter;
import com.oopgroup.smartpharmacy.models.PrescriptionItem;
import com.oopgroup.smartpharmacy.models.Product;
import com.softourtech.slt.SLTLoader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import me.xdrop.fuzzywuzzy.FuzzySearch;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ScannerFragment extends Fragment {

    private static final String TAG = "ScannerFragment";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    private static final int STORAGE_PERMISSION_REQUEST_CODE = 101;
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";
    private static final String GEMINI_API_KEY = "AIzaSyDBHlu5O0ShYeu-_vkR9Mfkd1k3ub30b5o"; // Replace with your actual key

    private Toolbar toolbar;
    private Button scanWithCameraButton;
    private Button uploadImageButton;
    private EditText prescriptionInput;
    private Button processPrescriptionButton;
    private RecyclerView prescriptionResultsRecyclerView;
    private SLTLoader sltLoader;
    private PrescriptionResultAdapter adapter;
    private List<PrescriptionItem> prescriptionItems;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private OkHttpClient client;
    private TextRecognizer textRecognizer;
    private Uri photoUri;

    private final ActivityResultLauncher<Uri> takePictureLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            result -> {
                if (result) processImageForText(photoUri);
                else Toast.makeText(requireContext(), "Photo capture cancelled", Toast.LENGTH_SHORT).show();
            });

    private final ActivityResultLauncher<String> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) processImageForText(uri);
                else Toast.makeText(requireContext(), "Image selection cancelled", Toast.LENGTH_SHORT).show();
            });

    public ScannerFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_scanner, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        client = new OkHttpClient();
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        toolbar = view.findViewById(R.id.toolbar);
        scanWithCameraButton = view.findViewById(R.id.scanWithCameraButton);
        uploadImageButton = view.findViewById(R.id.uploadImageButton);
        prescriptionInput = view.findViewById(R.id.prescriptionInput);
        processPrescriptionButton = view.findViewById(R.id.processPrescriptionButton);
        prescriptionResultsRecyclerView = view.findViewById(R.id.prescriptionResultsRecyclerView);
        sltLoader = new SLTLoader(requireActivity(), view.findViewById(R.id.loader_container));

        toolbar.setNavigationOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        prescriptionItems = new ArrayList<>();
        adapter = new PrescriptionResultAdapter(requireContext(), prescriptionItems, this::onAddToCartClick, this::onSuggestAlternativeClick);
        prescriptionResultsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        prescriptionResultsRecyclerView.setAdapter(adapter);

        scanWithCameraButton.setOnClickListener(v -> scanWithCamera());
        uploadImageButton.setOnClickListener(v -> uploadImageFromGallery());
        processPrescriptionButton.setOnClickListener(v -> processPrescription());
    }

    private void scanWithCamera() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
            return;
        }
        try {
            File photoFile = createImageFile();
            photoUri = FileProvider.getUriForFile(requireContext(), "com.oopgroup.smartpharmacy.fileprovider", photoFile);
            takePictureLauncher.launch(photoUri);
        } catch (IOException e) {
            Toast.makeText(requireContext(), "Error preparing camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void uploadImageFromGallery() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, STORAGE_PERMISSION_REQUEST_CODE);
            return;
        }
        pickImageLauncher.launch("image/*");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            scanWithCamera();
        } else if (requestCode == STORAGE_PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            uploadImageFromGallery();
        } else {
            Toast.makeText(requireContext(), "Permission denied", Toast.LENGTH_SHORT).show();
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = requireActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    private void processImageForText(Uri imageUri) {
        showLoader();
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(requireActivity().getContentResolver(), imageUri);
            InputImage image = InputImage.fromBitmap(bitmap, 0);
            textRecognizer.process(image)
                    .addOnSuccessListener(text -> {
                        prescriptionInput.setText(text.getText());
                        hideLoader();
                        if (text.getText().isEmpty()) {
                            Toast.makeText(requireContext(), "No text found in the image", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(e -> {
                        hideLoader();
                        Toast.makeText(requireContext(), "Failed to extract text: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        } catch (IOException e) {
            hideLoader();
            Toast.makeText(requireContext(), "Error processing image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void processPrescription() {
        String prescriptionText = prescriptionInput.getText().toString().trim();
        if (prescriptionText.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter or scan a prescription", Toast.LENGTH_SHORT).show();
            return;
        }
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(requireContext(), "You must be logged in to process prescriptions.", Toast.LENGTH_LONG).show();
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new LoginFragment())
                    .addToBackStack(null)
                    .commit();
            return;
        }
        showLoader();
        extractMedicinesFromPrescription(prescriptionText);
    }

    private String stripMarkdown(String content) {
        return content.replaceAll("(?s)```json\\s*(.*?)\\s*```", "$1").trim();
    }

    private void extractMedicinesFromPrescription(String prescriptionText) {
        if (!isNetworkAvailable()) {
            hideLoader();
            Toast.makeText(requireContext(), "No internet connection", Toast.LENGTH_SHORT).show();
            return;
        }

        JSONObject requestBody = new JSONObject();
        try {
            JSONObject content = new JSONObject();
            JSONArray parts = new JSONArray();
            parts.put(new JSONObject().put("text", "You are a helpful pharmacy assistant. Extract medicine names, dosage, frequency, quantity, and instructions from the following prescription text. Return ONLY a valid JSON array in this exact format: [{\"name\": \"Medicine\", \"dosage\": \"500mg\", \"frequency\": \"3 times a day\", \"quantity\": 1, \"instructions\": \"after food\"}]. Prescription text: " + prescriptionText));
            content.put("parts", parts);
            requestBody.put("contents", new JSONArray().put(content));
        } catch (JSONException e) {
            hideLoader();
            Toast.makeText(requireContext(), "Error preparing request", Toast.LENGTH_SHORT).show();
            return;
        }

        String url = GEMINI_API_URL + "?key=" + GEMINI_API_KEY;
        Request request = new Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(MediaType.parse("application/json"), requestBody.toString()))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() -> {
                    hideLoader();
                    Toast.makeText(requireContext(), "Failed to extract medicines: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    requireActivity().runOnUiThread(() -> {
                        hideLoader();
                        Toast.makeText(requireContext(), "API error: " + response.code(), Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                try {
                    String responseBody = response.body().string();
                    JSONObject jsonResponse = new JSONObject(responseBody);
                    String content = jsonResponse.getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text");
                    String cleanedContent = stripMarkdown(content);
                    JSONArray medicines = new JSONArray(cleanedContent);

                    prescriptionItems.clear();
                    for (int i = 0; i < medicines.length(); i++) {
                        JSONObject medicine = medicines.getJSONObject(i);
                        PrescriptionItem item = new PrescriptionItem();
                        item.setName(medicine.optString("name").trim());
                        item.setDosage(medicine.optString("dosage"));
                        item.setFrequency(medicine.optString("frequency"));
                        item.setQuantity(medicine.optInt("quantity", 1));
                        item.setInstructions(medicine.optString("instructions"));
                        prescriptionItems.add(item);
                    }
                    matchMedicinesWithFirestore();
                } catch (JSONException e) {
                    requireActivity().runOnUiThread(() -> {
                        hideLoader();
                        Toast.makeText(requireContext(), "Error parsing response: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void matchMedicinesWithFirestore() {
        db.collection("products")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Product> products = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Product product = doc.toObject(Product.class);
                        product.setId(doc.getId());
                        products.add(product);
                    }

                    for (PrescriptionItem item : prescriptionItems) {
                        boolean found = false;
                        for (Product product : products) {
                            int similarity = FuzzySearch.ratio(product.getName().toLowerCase(), item.getName().toLowerCase());
                            if (similarity > 80) {
                                item.setInStock(true);
                                item.setProductId(product.getId());
                                item.setPrice(product.getPrice());
                                item.setImageUrl(product.getImageUrl() != null ? product.getImageUrl() : "");
                                item.setDiscountedPrice(product.getDiscountedPrice());
                                item.setDiscountPercentage(product.getDiscountPercentage());
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            item.setInStock(false);
                            suggestAlternatives(item);
                        }
                    }

                    requireActivity().runOnUiThread(() -> {
                        prescriptionResultsRecyclerView.setVisibility(View.VISIBLE);
                        adapter.notifyDataSetChanged();
                        hideLoader();
                    });
                })
                .addOnFailureListener(e -> {
                    hideLoader();
                    Toast.makeText(requireContext(), "Failed to fetch products: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void suggestAlternatives(PrescriptionItem item) {
        JSONObject requestBody = new JSONObject();
        try {
            JSONObject content = new JSONObject();
            JSONArray parts = new JSONArray();
            parts.put(new JSONObject().put("text", "Suggest 3 alternatives for the following medicine with similar active ingredients or uses. Return ONLY a JSON object with a 'suggestions' array containing the alternatives. Medicine: " + item.getName() + " " + item.getDosage()));
            content.put("parts", parts);
            requestBody.put("contents", new JSONArray().put(content));
        } catch (JSONException e) {
            return;
        }

        String url = GEMINI_API_URL + "?key=" + GEMINI_API_KEY;
        Request request = new Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(MediaType.parse("application/json"), requestBody.toString()))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "Failed to suggest alternatives", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseBody = response.body().string();
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        String content = jsonResponse.getJSONArray("candidates")
                                .getJSONObject(0)
                                .getJSONObject("content")
                                .getJSONArray("parts")
                                .getJSONObject(0)
                                .getString("text");
                        String cleanedContent = stripMarkdown(content);
                        JSONObject alternatives = new JSONObject(cleanedContent);
                        JSONArray suggestions = alternatives.getJSONArray("suggestions");
                        List<String> alternativeList = new ArrayList<>();
                        for (int i = 0; i < suggestions.length(); i++) {
                            alternativeList.add(suggestions.getString(i));
                        }
                        item.setAlternatives(alternativeList);
                        requireActivity().runOnUiThread(() -> adapter.notifyDataSetChanged());
                    } catch (JSONException e) {
                        // Silent failure for alternatives
                    }
                }
            }
        });
    }

    private void onAddToCartClick(PrescriptionItem item) {
        if (!isAdded() || mAuth.getCurrentUser() == null) {
            Toast.makeText(requireContext(), "Please log in to add items to cart.", Toast.LENGTH_SHORT).show();
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new LoginFragment())
                    .addToBackStack(null)
                    .commit();
            return;
        }

        if (!item.isInStock() || item.getProductId() == null) {
            Toast.makeText(requireContext(), item.getName() + " is not available in stock.", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = mAuth.getCurrentUser().getUid();
        double price = item.getDiscountedPrice() != 0 ? item.getDiscountedPrice() : item.getPrice();
        DocumentReference cartItemRef = db.collection("cart")
                .document(userId)
                .collection("items")
                .document(item.getProductId());

        showLoader();
        cartItemRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                int currentQuantity = documentSnapshot.getLong("quantity").intValue();
                double currentTotal = documentSnapshot.getDouble("total");

                cartItemRef.update(
                        "quantity", currentQuantity + 1,
                        "total", currentTotal + price,
                        "addedAt", com.google.firebase.Timestamp.now()
                ).addOnSuccessListener(aVoid -> {
                    requireActivity().runOnUiThread(() -> {
                        hideLoader();
                        Toast.makeText(requireContext(), item.getName() + " quantity updated in cart!", Toast.LENGTH_SHORT).show();
                        updateCartBadge(); // Assuming HomeFragment updates a badge
                    });
                }).addOnFailureListener(e -> {
                    requireActivity().runOnUiThread(() -> {
                        hideLoader();
                        Toast.makeText(requireContext(), "Failed to update cart", Toast.LENGTH_SHORT).show();
                    });
                    Log.e(TAG, "Failed to update cart item: " + e.getMessage());
                });
            } else {
                Map<String, Object> cartItem = new HashMap<>();
                cartItem.put("productId", item.getProductId());
                cartItem.put("productName", item.getName());
                cartItem.put("imageUrl", item.getImageUrl() != null ? item.getImageUrl() : "");
                cartItem.put("quantity", item.getQuantity());
                cartItem.put("total", price * item.getQuantity());
                cartItem.put("originalPrice", item.getPrice());
                cartItem.put("discountedPrice", item.getDiscountedPrice());
                cartItem.put("discountPercentage", item.getDiscountPercentage());
                cartItem.put("addedAt", com.google.firebase.Timestamp.now());

                cartItemRef.set(cartItem).addOnSuccessListener(aVoid -> {
                    requireActivity().runOnUiThread(() -> {
                        hideLoader();
                        Toast.makeText(requireContext(), item.getName() + " added to cart!", Toast.LENGTH_SHORT).show();
                        updateCartBadge(); // Assuming HomeFragment updates a badge
                    });
                }).addOnFailureListener(e -> {
                    requireActivity().runOnUiThread(() -> {
                        hideLoader();
                        Toast.makeText(requireContext(), "Failed to add to cart", Toast.LENGTH_SHORT).show();
                    });
                    Log.e(TAG, "Failed to add to cart: " + e.getMessage());
                });
            }
        }).addOnFailureListener(e -> {
            requireActivity().runOnUiThread(() -> {
                hideLoader();
                Toast.makeText(requireContext(), "Error checking cart", Toast.LENGTH_SHORT).show();
            });
            Log.e(TAG, "Failed to check cart: " + e.getMessage());
        });
    }

    private void onSuggestAlternativeClick(PrescriptionItem item) {
        Toast.makeText(requireContext(), "Alternatives for " + item.getName() + ": " + item.getAlternatives(), Toast.LENGTH_LONG).show();
    }

    private void updateCartBadge() {
        // Placeholder: Implement this to update cart badge like HomeFragment
        // Example: ((MainActivity) requireActivity()).updateCartBadge();
        Log.d(TAG, "Cart badge update called (implement as per HomeFragment)");
    }

    private boolean isNetworkAvailable() {
        android.net.ConnectivityManager connectivityManager = (android.net.ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        android.net.NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private void showLoader() {
        if (sltLoader != null) {
            sltLoader.showCustomLoader(new SLTLoader.LoaderConfig(com.softourtech.slt.R.raw.loading_global)
                    .setWidthDp(40)
                    .setHeightDp(40)
                    .setUseRoundedBox(true)
                    .setChangeJsonColor(false));
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
    public void onDestroyView() {
        super.onDestroyView();
        if (sltLoader != null) {
            sltLoader.onDestroy();
            sltLoader = null;
        }
        if (textRecognizer != null) {
            textRecognizer.close();
        }
    }
}