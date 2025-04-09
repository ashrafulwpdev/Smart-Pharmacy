package com.oopgroup.smartpharmacy.models;

public class LabTest {
    private String id;
    private String name;
    private String imageUrl;
    private String tests; // Comma-separated string of tests (e.g., "Blood Sugar Test,Blood Profile - CBC (26 Tests),...")
    private int stock; // Stock quantity
    private double actualPrice; // Actual price (for internal use)
    private double price; // Customer-facing price
    private String provider; // Added provider field

    public LabTest() {
        // Default constructor required for Firebase
    }

    public LabTest(String id, String name, String imageUrl) {
        this.id = id;
        this.name = name;
        this.imageUrl = imageUrl;
    }

    public LabTest(String id, String name, String imageUrl, String tests, double price) {
        this.id = id;
        this.name = name;
        this.imageUrl = imageUrl;
        this.tests = tests;
        this.price = price;
    }

    public LabTest(String id, String name, String imageUrl, String tests, int stock, double actualPrice, double price) {
        this.id = id;
        this.name = name;
        this.imageUrl = imageUrl;
        this.tests = tests;
        this.stock = stock;
        this.actualPrice = actualPrice;
        this.price = price;
    }

    // Added constructor to include provider
    public LabTest(String id, String name, String imageUrl, String tests, int stock, double actualPrice, double price, String provider) {
        this.id = id;
        this.name = name;
        this.imageUrl = imageUrl;
        this.tests = tests;
        this.stock = stock;
        this.actualPrice = actualPrice;
        this.price = price;
        this.provider = provider;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getTests() { return tests; }
    public void setTests(String tests) { this.tests = tests; }
    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }
    public double getActualPrice() { return actualPrice; }
    public void setActualPrice(double actualPrice) { this.actualPrice = actualPrice; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public String getProvider() { return provider; } // Added getter
    public void setProvider(String provider) { this.provider = provider; } // Added setter
}