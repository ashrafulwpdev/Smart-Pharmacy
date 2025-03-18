package com.oopgroup.smartpharmacy.models;

public class Product {
    private String id;
    private String name;
    private double price;
    private String imageUrl;
    private int rating;
    private int reviewCount;

    public Product() {
        // Default constructor required for Firebase
    }

    public Product(String id, String name, double price, String imageUrl, int rating, int reviewCount) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.imageUrl = imageUrl;
        this.rating = rating;
        this.reviewCount = reviewCount;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }
    public int getReviewCount() { return reviewCount; }
    public void setReviewCount(int reviewCount) { this.reviewCount = reviewCount; }
}