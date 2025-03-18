package com.oopgroup.smartpharmacy.models;

public class Banner {
    private String id;
    private String title;
    private String description;
    private String discount;
    private String imageUrl;

    public Banner() {
        // Default constructor required for Firebase
    }

    public Banner(String id, String title, String description, String discount, String imageUrl) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.discount = discount;
        this.imageUrl = imageUrl;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDiscount() { return discount; }
    public void setDiscount(String discount) { this.discount = discount; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}