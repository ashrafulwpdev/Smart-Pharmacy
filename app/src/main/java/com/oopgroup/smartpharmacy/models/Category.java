package com.oopgroup.smartpharmacy.models;

public class Category {
    private String id;
    private String name;
    private int productCount;
    private String imageUrl;

    public Category() {
        // Default constructor required for Firebase
    }

    public Category(String id, String name, int productCount, String imageUrl) {
        this.id = id;
        this.name = name;
        this.productCount = productCount;
        this.imageUrl = imageUrl;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getProductCount() { return productCount; }
    public void setProductCount(int productCount) { this.productCount = productCount; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}