package com.oopgroup.smartpharmacy.models;

import java.util.ArrayList;
import java.util.List;

public class PrescriptionItem {
    private String name;
    private String dosage;
    private String frequency;
    private int quantity;
    private String instructions;
    private boolean inStock;
    private String productId;
    private double price;
    private String imageUrl; // Added for cart alignment
    private double discountedPrice; // Added for cart alignment
    private double discountPercentage; // Added for cart alignment
    private List<String> alternatives;

    public PrescriptionItem() {
        this.quantity = 1; // Default value
        this.instructions = ""; // Default value
        this.imageUrl = ""; // Default value
        this.discountedPrice = 0.0; // Default value
        this.discountPercentage = 0.0; // Default value
        this.alternatives = new ArrayList<>();
    }

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDosage() { return dosage; }
    public void setDosage(String dosage) { this.dosage = dosage; }

    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }

    public boolean isInStock() { return inStock; }
    public void setInStock(boolean inStock) { this.inStock = inStock; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public double getDiscountedPrice() { return discountedPrice; }
    public void setDiscountedPrice(double discountedPrice) { this.discountedPrice = discountedPrice; }

    public double getDiscountPercentage() { return discountPercentage; }
    public void setDiscountPercentage(double discountPercentage) { this.discountPercentage = discountPercentage; }

    public List<String> getAlternatives() { return alternatives; }
    public void setAlternatives(List<String> alternatives) { this.alternatives = alternatives; }
}