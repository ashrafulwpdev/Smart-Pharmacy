package com.oopgroup.smartpharmacy.fragments;

import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.models.Category;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FilterDialogFragment extends BottomSheetDialogFragment {
    private static final String TAG = "FilterDialogFragment";

    private LinearLayout brandsSection, brandsOptions, productTypeSection, productTypeOptions,
            discountSection, discountOptions, priceSection, priceOptions;
    private ImageView brandsArrow, productTypeArrow, discountArrow, priceArrow, closeButton;
    private TextView brandsTitle, productTypeTitle, discountTitle, priceTitle, discountNote, priceNote;
    private CheckBox checkboxDiscount5, checkboxDiscount10, checkboxDiscount20, checkboxDiscount30,
            checkboxDiscount40, checkboxDiscount50, checkboxDiscount60, checkboxDiscount70;
    private CheckBox checkboxPrice5_10, checkboxPrice10_20, checkboxPrice20_30, checkboxPrice30_40,
            checkboxPrice40_50, checkboxPrice50_100, checkboxPrice100Above;
    private Button clearAllButton, applyFilterButton;
    private FirebaseFirestore db;

    // Arguments passed from ProductsFragment
    private List<Category> categories;
    private List<String> selectedCategories;
    private List<String> selectedBrands;
    private List<Double> discountRange;
    private List<double[]> priceRange;
    private String categoryId;

    // Lists to store dynamically created checkboxes
    private List<CheckBox> brandCheckboxes = new ArrayList<>();
    private List<CheckBox> productTypeCheckboxes = new ArrayList<>();
    private List<CheckBox> discountCheckboxes = new ArrayList<>();
    private List<CheckBox> priceCheckboxes = new ArrayList<>();

    // Listener to pass filter data back to ProductsFragment
    public interface OnFilterAppliedListener {
        void onFilterApplied(List<String> selectedCategories, List<String> selectedBrands,
                             List<Double> discountRange, double[] combinedPriceRange);
        void onClearFilters();
    }

    public static FilterDialogFragment newInstance(List<Category> categories, List<String> selectedCategories,
                                                   List<String> selectedBrands, List<Double> discountRange,
                                                   List<double[]> priceRange, String categoryId) {
        FilterDialogFragment fragment = new FilterDialogFragment();
        Bundle args = new Bundle();
        args.putParcelableArrayList("categories", new ArrayList<Parcelable>(categories));
        args.putStringArrayList("selectedCategories", new ArrayList<>(selectedCategories));
        args.putStringArrayList("selectedBrands", new ArrayList<>(selectedBrands));
        args.putSerializable("discountRange", new ArrayList<>(discountRange));
        args.putSerializable("priceRange", new ArrayList<>(priceRange));
        args.putString("categoryId", categoryId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(BottomSheetDialogFragment.STYLE_NORMAL, R.style.CustomBottomSheetTheme);

        // Retrieve arguments
        if (getArguments() != null) {
            categories = getArguments().getParcelableArrayList("categories");
            selectedCategories = getArguments().getStringArrayList("selectedCategories");
            selectedBrands = getArguments().getStringArrayList("selectedBrands");
            discountRange = (List<Double>) getArguments().getSerializable("discountRange");
            priceRange = (List<double[]>) getArguments().getSerializable("priceRange");
            categoryId = getArguments().getString("categoryId");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.filter_dialog, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Set maximum height for the BottomSheetDialog to enable proper scrolling
        BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
        if (dialog != null) {
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setPeekHeight((int) (getResources().getDisplayMetrics().heightPixels * 0.9));
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        }

        // Initialize Firestore
        db = FirebaseFirestore.getInstance();

        // Initialize views
        brandsSection = view.findViewById(R.id.brandsSection);
        brandsOptions = view.findViewById(R.id.brandsOptions);
        productTypeSection = view.findViewById(R.id.productTypeSection);
        productTypeOptions = view.findViewById(R.id.productTypeOptions);
        discountSection = view.findViewById(R.id.discountSection);
        discountOptions = view.findViewById(R.id.discountOptions);
        priceSection = view.findViewById(R.id.priceSection);
        priceOptions = view.findViewById(R.id.priceOptions);
        brandsArrow = view.findViewById(R.id.brandsArrow);
        productTypeArrow = view.findViewById(R.id.productTypeArrow);
        discountArrow = view.findViewById(R.id.discountArrow);
        priceArrow = view.findViewById(R.id.priceArrow);
        closeButton = view.findViewById(R.id.closeButton);
        brandsTitle = brandsSection.findViewById(R.id.brandsTitle);
        productTypeTitle = productTypeSection.findViewById(R.id.productTypeTitle);
        discountTitle = discountSection.findViewById(R.id.discountTitle);
        priceTitle = priceSection.findViewById(R.id.priceTitle);
        discountNote = new TextView(getContext());
        priceNote = new TextView(getContext());
        clearAllButton = view.findViewById(R.id.clearAllButton);
        applyFilterButton = view.findViewById(R.id.applyFilterButton);

        // Initialize CheckBox instances
        checkboxDiscount5 = new CheckBox(getContext());
        checkboxDiscount10 = new CheckBox(getContext());
        checkboxDiscount20 = new CheckBox(getContext());
        checkboxDiscount30 = new CheckBox(getContext());
        checkboxDiscount40 = new CheckBox(getContext());
        checkboxDiscount50 = new CheckBox(getContext());
        checkboxDiscount60 = new CheckBox(getContext());
        checkboxDiscount70 = new CheckBox(getContext());
        checkboxPrice5_10 = new CheckBox(getContext());
        checkboxPrice10_20 = new CheckBox(getContext());
        checkboxPrice20_30 = new CheckBox(getContext());
        checkboxPrice30_40 = new CheckBox(getContext());
        checkboxPrice40_50 = new CheckBox(getContext());
        checkboxPrice50_100 = new CheckBox(getContext());
        checkboxPrice100Above = new CheckBox(getContext());

        // Add discount and price checkboxes to their respective lists
        discountCheckboxes.addAll(Arrays.asList(
                checkboxDiscount5, checkboxDiscount10, checkboxDiscount20, checkboxDiscount30,
                checkboxDiscount40, checkboxDiscount50, checkboxDiscount60, checkboxDiscount70));
        priceCheckboxes.addAll(Arrays.asList(
                checkboxPrice5_10, checkboxPrice10_20, checkboxPrice20_30, checkboxPrice30_40,
                checkboxPrice40_50, checkboxPrice50_100, checkboxPrice100Above));

        // Prevent theme from overriding button backgrounds
        clearAllButton.setBackgroundTintList(null);
        applyFilterButton.setBackgroundTintList(null);

        // Add a note above the discount options
        discountNote.setText("Note: If multiple discounts are selected, products with at least the lowest selected discount will be shown.");
        discountNote.setTextColor(ContextCompat.getColor(getContext(), android.R.color.black));
        discountNote.setTextSize(12);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        noteParams.setMargins(16, 8, 16, 8);
        discountNote.setLayoutParams(noteParams);
        discountOptions.addView(discountNote);

        // Add a note above the price options
        priceNote.setText("Note: If multiple price ranges are selected, products within the combined range will be shown.");
        priceNote.setTextColor(ContextCompat.getColor(getContext(), android.R.color.black));
        priceNote.setTextSize(12);
        priceNote.setLayoutParams(noteParams);
        priceOptions.addView(priceNote);

        // Log the initial discount range for debugging
        Log.d(TAG, "Initial discount range: " + discountRange);
        Log.d(TAG, "Initial price range: " + (priceRange != null && !priceRange.isEmpty() ?
                Arrays.toString(priceRange.get(0)) : "null"));

        // Initialize the UI with passed arguments
        initializeFilterOptions();

        // Fetch data from Firestore
        fetchBrands();
        fetchProductTypes();
        fetchDiscountCounts();
        fetchPriceCounts();

        // Expand/Collapse Brands Section
        brandsSection.setOnClickListener(v -> {
            if (brandsOptions.getVisibility() == View.VISIBLE) {
                brandsOptions.setVisibility(View.GONE);
                brandsArrow.setImageResource(R.drawable.arrow_left);
                brandsTitle.setTextColor(ContextCompat.getColor(getContext(), android.R.color.black));
            } else {
                brandsOptions.setVisibility(View.VISIBLE);
                brandsArrow.setImageResource(R.drawable.arrow_down);
                brandsTitle.setTextColor(ContextCompat.getColor(getContext(), R.color.accent_blue));
            }
        });

        // Expand/Collapse Product Type Section
        productTypeSection.setOnClickListener(v -> {
            if (productTypeOptions.getVisibility() == View.VISIBLE) {
                productTypeOptions.setVisibility(View.GONE);
                productTypeArrow.setImageResource(R.drawable.arrow_left);
                productTypeTitle.setTextColor(ContextCompat.getColor(getContext(), android.R.color.black));
            } else {
                productTypeOptions.setVisibility(View.VISIBLE);
                productTypeArrow.setImageResource(R.drawable.arrow_down);
                productTypeTitle.setTextColor(ContextCompat.getColor(getContext(), R.color.accent_blue));
            }
        });

        // Expand/Collapse Discount Section
        discountSection.setOnClickListener(v -> {
            if (discountOptions.getVisibility() == View.VISIBLE) {
                discountOptions.setVisibility(View.GONE);
                discountArrow.setImageResource(R.drawable.arrow_left);
                discountTitle.setTextColor(ContextCompat.getColor(getContext(), android.R.color.black));
            } else {
                discountOptions.setVisibility(View.VISIBLE);
                discountArrow.setImageResource(R.drawable.arrow_down);
                discountTitle.setTextColor(ContextCompat.getColor(getContext(), R.color.accent_blue));
            }
        });

        // Expand/Collapse Price Section
        priceSection.setOnClickListener(v -> {
            if (priceOptions.getVisibility() == View.VISIBLE) {
                priceOptions.setVisibility(View.GONE);
                priceArrow.setImageResource(R.drawable.arrow_left);
                priceTitle.setTextColor(ContextCompat.getColor(getContext(), android.R.color.black));
            } else {
                priceOptions.setVisibility(View.VISIBLE);
                priceArrow.setImageResource(R.drawable.arrow_down);
                priceTitle.setTextColor(ContextCompat.getColor(getContext(), R.color.accent_blue));
            }
        });

        // Close button
        closeButton.setOnClickListener(v -> dismiss());

        // Clear All button
        clearAllButton.setOnClickListener(v -> {
            // Clear all checkboxes
            for (CheckBox checkBox : brandCheckboxes) {
                checkBox.setChecked(false);
            }
            for (CheckBox checkBox : productTypeCheckboxes) {
                if (categoryId == null || !categoryId.equals(checkBox.getTag())) {
                    checkBox.setChecked(false);
                }
            }
            for (CheckBox checkBox : discountCheckboxes) {
                checkBox.setChecked(false);
            }
            for (CheckBox checkBox : priceCheckboxes) {
                checkBox.setChecked(false);
            }

            // Notify ProductsFragment to clear filters
            if (getTargetFragment() instanceof OnFilterAppliedListener) {
                OnFilterAppliedListener listener = (OnFilterAppliedListener) getTargetFragment();
                listener.onClearFilters();
            }
        });

        // Apply Filter button
        applyFilterButton.setOnClickListener(v -> {
            List<String> selectedBrandsList = new ArrayList<>();
            List<String> selectedCategoriesList = new ArrayList<>();
            List<Double> selectedDiscounts = new ArrayList<>();
            List<double[]> selectedPriceRanges = new ArrayList<>();

            // Collect selected brands
            for (CheckBox checkBox : brandCheckboxes) {
                if (checkBox.isChecked()) {
                    String brand = checkBox.getText().toString().split(" \\(")[0];
                    selectedBrandsList.add(brand);
                }
            }

            // Collect selected categories (using category IDs)
            for (CheckBox checkBox : productTypeCheckboxes) {
                if (checkBox.isChecked()) {
                    String catId = (String) checkBox.getTag();
                    selectedCategoriesList.add(catId);
                }
            }

            // Collect selected discounts
            selectedDiscounts.clear();
            if (checkboxDiscount5.isChecked()) selectedDiscounts.add(5.0);
            if (checkboxDiscount10.isChecked()) selectedDiscounts.add(10.0);
            if (checkboxDiscount20.isChecked()) selectedDiscounts.add(20.0);
            if (checkboxDiscount30.isChecked()) selectedDiscounts.add(30.0);
            if (checkboxDiscount40.isChecked()) selectedDiscounts.add(40.0);
            if (checkboxDiscount50.isChecked()) selectedDiscounts.add(50.0);
            if (checkboxDiscount60.isChecked()) selectedDiscounts.add(60.0);
            if (checkboxDiscount70.isChecked()) selectedDiscounts.add(70.0);

            // Collect selected price ranges
            selectedPriceRanges.clear();
            if (checkboxPrice5_10.isChecked()) selectedPriceRanges.add(new double[]{5, 10});
            if (checkboxPrice10_20.isChecked()) selectedPriceRanges.add(new double[]{10, 20});
            if (checkboxPrice20_30.isChecked()) selectedPriceRanges.add(new double[]{20, 30});
            if (checkboxPrice30_40.isChecked()) selectedPriceRanges.add(new double[]{30, 40});
            if (checkboxPrice40_50.isChecked()) selectedPriceRanges.add(new double[]{40, 50});
            if (checkboxPrice50_100.isChecked()) selectedPriceRanges.add(new double[]{50, 100});
            if (checkboxPrice100Above.isChecked()) selectedPriceRanges.add(new double[]{100, Double.MAX_VALUE});

            // Combine the selected price ranges into a single range
            double[] combinedPriceRange = null;
            if (!selectedPriceRanges.isEmpty()) {
                double minPrice = Double.MAX_VALUE;
                double maxPrice = Double.MIN_VALUE;
                for (double[] range : selectedPriceRanges) {
                    minPrice = Math.min(minPrice, range[0]);
                    maxPrice = Math.max(maxPrice, range[1]);
                }
                combinedPriceRange = new double[]{minPrice, maxPrice};
            }

            // Log the selected discounts and combined price range for debugging
            Log.d(TAG, "Applying filters - Selected Discounts: " + selectedDiscounts);
            Log.d(TAG, "Applying filters - Combined Price Range: " +
                    (combinedPriceRange != null ? "[" + combinedPriceRange[0] + ", " + combinedPriceRange[1] + "]" : "null"));

            // Pass the selected filters back to the ProductsFragment
            if (getTargetFragment() instanceof OnFilterAppliedListener) {
                OnFilterAppliedListener listener = (OnFilterAppliedListener) getTargetFragment();
                listener.onFilterApplied(selectedCategoriesList, selectedBrandsList, selectedDiscounts, combinedPriceRange);
            }
            dismiss();
        });
    }

    private void initializeFilterOptions() {
        // Initialize discounts based on the passed discountRange
        if (discountRange != null) {
            for (Double discount : discountRange) {
                if (discount == 5.0) checkboxDiscount5.setChecked(true);
                else if (discount == 10.0) checkboxDiscount10.setChecked(true);
                else if (discount == 20.0) checkboxDiscount20.setChecked(true);
                else if (discount == 30.0) checkboxDiscount30.setChecked(true);
                else if (discount == 40.0) checkboxDiscount40.setChecked(true);
                else if (discount == 50.0) checkboxDiscount50.setChecked(true);
                else if (discount == 60.0) checkboxDiscount60.setChecked(true);
                else if (discount == 70.0) checkboxDiscount70.setChecked(true);
            }
        }

        // Initialize price ranges based on the passed priceRange
        if (priceRange != null && !priceRange.isEmpty()) {
            double minPrice = priceRange.get(0)[0];
            double maxPrice = priceRange.get(0)[1];

            if (minPrice <= 10 && maxPrice >= 5) checkboxPrice5_10.setChecked(true);
            if (minPrice <= 20 && maxPrice >= 10) checkboxPrice10_20.setChecked(true);
            if (minPrice <= 30 && maxPrice >= 20) checkboxPrice20_30.setChecked(true);
            if (minPrice <= 40 && maxPrice >= 30) checkboxPrice30_40.setChecked(true);
            if (minPrice <= 50 && maxPrice >= 40) checkboxPrice40_50.setChecked(true);
            if (minPrice <= 100 && maxPrice >= 50) checkboxPrice50_100.setChecked(true);
            if (maxPrice >= 100) checkboxPrice100Above.setChecked(true);
        }
    }

    private void fetchBrands() {
        Query query = db.collection("products");
        if (categoryId != null) {
            query = query.whereEqualTo("categoryId", categoryId);
        }

        query.get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Map<String, Integer> brandCounts = new HashMap<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String brand = doc.getString("brand");
                        if (brand != null) {
                            brandCounts.put(brand, brandCounts.getOrDefault(brand, 0) + 1);
                        }
                    }
                    brandsOptions.removeAllViews();
                    brandCheckboxes.clear();
                    for (Map.Entry<String, Integer> entry : brandCounts.entrySet()) {
                        LinearLayout checkboxLayout = new LinearLayout(getContext());
                        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT);
                        layoutParams.setMargins(0, 8, 0, 8);
                        checkboxLayout.setLayoutParams(layoutParams);
                        checkboxLayout.setOrientation(LinearLayout.HORIZONTAL);
                        checkboxLayout.setGravity(Gravity.CENTER_VERTICAL);

                        CheckBox checkBox = new CheckBox(getContext());
                        checkBox.setText(entry.getKey());
                        checkBox.setTextColor(ContextCompat.getColor(getContext(), android.R.color.black));
                        checkBox.setButtonDrawable(R.drawable.checkbox_selector);
                        LinearLayout.LayoutParams checkBoxParams = new LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1);
                        checkBoxParams.setMargins(0, 0, 16, 0);
                        checkBox.setLayoutParams(checkBoxParams);
                        checkBox.setPadding(32, 0, 0, 0);

                        if (selectedBrands != null && selectedBrands.contains(entry.getKey())) {
                            checkBox.setChecked(true);
                        }

                        TextView countView = new TextView(getContext());
                        countView.setText("(" + entry.getValue() + ")");
                        countView.setTextColor(ContextCompat.getColor(getContext(), android.R.color.black));
                        countView.setLayoutParams(new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT));
                        countView.setPadding(8, 0, 0, 0);

                        checkboxLayout.addView(checkBox);
                        checkboxLayout.addView(countView);

                        brandsOptions.addView(checkboxLayout);
                        brandCheckboxes.add(checkBox);
                    }
                    if (brandCounts.isEmpty()) {
                        Toast.makeText(getContext(), "No brands available", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Failed to load brands: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void fetchProductTypes() {
        db.collection("categories")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Map<String, String> categoryMap = new HashMap<>();
                    Map<String, String> categoryIdMap = new HashMap<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String catId = doc.getId();
                        String categoryName = doc.getString("name");
                        if (catId != null && categoryName != null) {
                            categoryMap.put(catId, categoryName);
                            categoryIdMap.put(categoryName, catId);
                        }
                    }

                    Query productQuery = db.collection("products");
                    if (categoryId != null) {
                        productQuery = productQuery.whereEqualTo("categoryId", categoryId);
                    }

                    productQuery.get()
                            .addOnSuccessListener(productSnapshots -> {
                                Map<String, Integer> categoryCounts = new HashMap<>();
                                for (QueryDocumentSnapshot doc : productSnapshots) {
                                    String catId = doc.getString("categoryId");
                                    if (catId != null && categoryMap.containsKey(catId)) {
                                        String categoryName = categoryMap.get(catId);
                                        categoryCounts.put(categoryName, categoryCounts.getOrDefault(categoryName, 0) + 1);
                                    }
                                }

                                if (categoryId == null) {
                                    for (Map.Entry<String, String> entry : categoryMap.entrySet()) {
                                        if (!categoryCounts.containsKey(entry.getValue())) {
                                            categoryCounts.put(entry.getValue(), 0);
                                        }
                                    }
                                }

                                productTypeOptions.removeAllViews();
                                productTypeCheckboxes.clear();
                                for (Map.Entry<String, Integer> entry : categoryCounts.entrySet()) {
                                    LinearLayout checkboxLayout = new LinearLayout(getContext());
                                    LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                                            LinearLayout.LayoutParams.MATCH_PARENT,
                                            LinearLayout.LayoutParams.WRAP_CONTENT);
                                    layoutParams.setMargins(0, 8, 0, 8);
                                    checkboxLayout.setLayoutParams(layoutParams);
                                    checkboxLayout.setOrientation(LinearLayout.HORIZONTAL);
                                    checkboxLayout.setGravity(Gravity.CENTER_VERTICAL);

                                    CheckBox checkBox = new CheckBox(getContext());
                                    checkBox.setText(entry.getKey());
                                    checkBox.setTextColor(ContextCompat.getColor(getContext(), android.R.color.black));
                                    checkBox.setButtonDrawable(R.drawable.checkbox_selector);
                                    LinearLayout.LayoutParams checkBoxParams = new LinearLayout.LayoutParams(
                                            0,
                                            LinearLayout.LayoutParams.WRAP_CONTENT,
                                            1);
                                    checkBoxParams.setMargins(0, 0, 16, 0);
                                    checkBox.setLayoutParams(checkBoxParams);
                                    checkBox.setPadding(32, 0, 0, 0);

                                    String catId = categoryIdMap.get(entry.getKey());
                                    checkBox.setTag(catId);

                                    if (categoryId != null && catId != null && catId.equals(categoryId)) {
                                        checkBox.setChecked(true);
                                        checkBox.setEnabled(false);
                                    } else if (selectedCategories != null && selectedCategories.contains(catId)) {
                                        checkBox.setChecked(true);
                                    }

                                    TextView countView = new TextView(getContext());
                                    countView.setText("(" + entry.getValue() + ")");
                                    countView.setTextColor(ContextCompat.getColor(getContext(), android.R.color.black));
                                    countView.setLayoutParams(new LinearLayout.LayoutParams(
                                            LinearLayout.LayoutParams.WRAP_CONTENT,
                                            LinearLayout.LayoutParams.WRAP_CONTENT));
                                    countView.setPadding(8, 0, 0, 0);

                                    checkboxLayout.addView(checkBox);
                                    checkboxLayout.addView(countView);

                                    productTypeOptions.addView(checkboxLayout);
                                    productTypeCheckboxes.add(checkBox);
                                }
                                if (categoryCounts.isEmpty()) {
                                    Toast.makeText(getContext(), "No categories available", Toast.LENGTH_SHORT).show();
                                }
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(getContext(), "Failed to load products: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Failed to load categories: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void fetchDiscountCounts() {
        Query query = db.collection("products");
        if (categoryId != null) {
            query = query.whereEqualTo("categoryId", categoryId);
        }

        query.get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    int count5 = 0, count10 = 0, count20 = 0, count30 = 0, count40 = 0, count50 = 0, count60 = 0, count70 = 0;
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Double discount = doc.getDouble("discountPercentage");
                        if (discount == null) continue;
                        if (discount >= 5) count5++;
                        if (discount >= 10) count10++;
                        if (discount >= 20) count20++;
                        if (discount >= 30) count30++;
                        if (discount >= 40) count40++;
                        if (discount >= 50) count50++;
                        if (discount >= 60) count60++;
                        if (discount >= 70) count70++;
                    }

                    // Clear existing views (except the note)
                    discountOptions.removeViews(1, discountOptions.getChildCount() - 1);

                    // Add discount checkboxes dynamically
                    addDiscountCheckbox("At Least 5% Off", count5, checkboxDiscount5, 5.0);
                    addDiscountCheckbox("At Least 10% Off", count10, checkboxDiscount10, 10.0);
                    addDiscountCheckbox("At Least 20% Off", count20, checkboxDiscount20, 20.0);
                    addDiscountCheckbox("At Least 30% Off", count30, checkboxDiscount30, 30.0);
                    addDiscountCheckbox("At Least 40% Off", count40, checkboxDiscount40, 40.0);
                    addDiscountCheckbox("At Least 50% Off", count50, checkboxDiscount50, 50.0);
                    addDiscountCheckbox("At Least 60% Off", count60, checkboxDiscount60, 60.0);
                    addDiscountCheckbox("At Least 70% Off", count70, checkboxDiscount70, 70.0);
                });
    }

    private void addDiscountCheckbox(String text, int count, CheckBox checkBox, double discountValue) {
        LinearLayout checkboxLayout = new LinearLayout(getContext());
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(0, 8, 0, 8);
        checkboxLayout.setLayoutParams(layoutParams);
        checkboxLayout.setOrientation(LinearLayout.HORIZONTAL);
        checkboxLayout.setGravity(Gravity.CENTER_VERTICAL);

        checkBox.setText(text);
        checkBox.setTextColor(ContextCompat.getColor(getContext(), android.R.color.black));
        checkBox.setButtonDrawable(R.drawable.checkbox_selector);
        LinearLayout.LayoutParams checkBoxParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1);
        checkBoxParams.setMargins(0, 0, 16, 0);
        checkBox.setLayoutParams(checkBoxParams);
        checkBox.setPadding(32, 0, 0, 0);

        // Pre-check based on discountRange
        if (discountRange != null && discountRange.contains(discountValue)) {
            checkBox.setChecked(true);
        }

        TextView countView = new TextView(getContext());
        countView.setText("(" + count + ")");
        countView.setTextColor(ContextCompat.getColor(getContext(), android.R.color.black));
        countView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        countView.setPadding(8, 0, 0, 0);

        checkboxLayout.addView(checkBox);
        checkboxLayout.addView(countView);

        discountOptions.addView(checkboxLayout);
    }

    private void fetchPriceCounts() {
        Query query = db.collection("products");
        if (categoryId != null) {
            query = query.whereEqualTo("categoryId", categoryId);
        }

        query.get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    int count5_10 = 0, count10_20 = 0, count20_30 = 0, count30_40 = 0,
                            count40_50 = 0, count50_100 = 0, count100Above = 0;
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Double price = doc.getDouble("price");
                        if (price == null) continue;
                        if (price >= 5 && price <= 10) count5_10++;
                        if (price >= 10 && price <= 20) count10_20++;
                        if (price >= 20 && price <= 30) count20_30++;
                        if (price >= 30 && price <= 40) count30_40++;
                        if (price >= 40 && price <= 50) count40_50++;
                        if (price >= 50 && price <= 100) count50_100++;
                        if (price >= 100) count100Above++;
                    }

                    // Clear existing views (except the note)
                    priceOptions.removeViews(1, priceOptions.getChildCount() - 1);

                    // Add price checkboxes dynamically
                    addPriceCheckbox("RM 5 - RM 10", count5_10, checkboxPrice5_10, new double[]{5, 10});
                    addPriceCheckbox("RM 10 - RM 20", count10_20, checkboxPrice10_20, new double[]{10, 20});
                    addPriceCheckbox("RM 20 - RM 30", count20_30, checkboxPrice20_30, new double[]{20, 30});
                    addPriceCheckbox("RM 30 - RM 40", count30_40, checkboxPrice30_40, new double[]{30, 40});
                    addPriceCheckbox("RM 40 - RM 50", count40_50, checkboxPrice40_50, new double[]{40, 50});
                    addPriceCheckbox("RM 50 - RM 100", count50_100, checkboxPrice50_100, new double[]{50, 100});
                    addPriceCheckbox("RM 100 & Above", count100Above, checkboxPrice100Above, new double[]{100, Double.MAX_VALUE});
                });
    }

    private void addPriceCheckbox(String text, int count, CheckBox checkBox, double[] range) {
        LinearLayout checkboxLayout = new LinearLayout(getContext());
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(0, 8, 0, 8);
        checkboxLayout.setLayoutParams(layoutParams);
        checkboxLayout.setOrientation(LinearLayout.HORIZONTAL);
        checkboxLayout.setGravity(Gravity.CENTER_VERTICAL);

        checkBox.setText(text);
        checkBox.setTextColor(ContextCompat.getColor(getContext(), android.R.color.black));
        checkBox.setButtonDrawable(R.drawable.checkbox_selector);
        LinearLayout.LayoutParams checkBoxParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1);
        checkBoxParams.setMargins(0, 0, 16, 0);
        checkBox.setLayoutParams(checkBoxParams);
        checkBox.setPadding(32, 0, 0, 0);

        // Pre-check based on priceRange
        if (priceRange != null && !priceRange.isEmpty()) {
            double minPrice = priceRange.get(0)[0];
            double maxPrice = priceRange.get(0)[1];
            if (minPrice <= range[1] && maxPrice >= range[0]) {
                checkBox.setChecked(true);
            }
        }

        TextView countView = new TextView(getContext());
        countView.setText("(" + count + ")");
        countView.setTextColor(ContextCompat.getColor(getContext(), android.R.color.black));
        countView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        countView.setPadding(8, 0, 0, 0);

        checkboxLayout.addView(checkBox);
        checkboxLayout.addView(countView);

        priceOptions.addView(checkboxLayout);
    }
}