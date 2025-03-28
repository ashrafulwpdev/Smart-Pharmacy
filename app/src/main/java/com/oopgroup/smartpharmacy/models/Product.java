package com.oopgroup.smartpharmacy.models;

import android.os.Parcel;
import android.os.Parcelable;

public class Product implements Parcelable {
    private String id;
    private String name;
    private String nameLower;
    private double price;
    private String imageUrl;
    private int rating;
    private int reviewCount;
    private String quantity;
    private double originalPrice;
    private double discountedPrice;
    private String description;
    private String deliveryDate;
    private String deliveryAddress;
    private String categoryId;
    private String brand;
    private double discountPercentage;
    private int stability; // Added to match Firestore field

    // No-argument constructor required for Firestore
    public Product() {
    }

    // Constructor with arguments (updated to include nameLower and stability)
    public Product(String id, String name, double price, String imageUrl, int rating, int reviewCount,
                   String quantity, double originalPrice, double discountedPrice, String description,
                   String deliveryDate, String deliveryAddress, String categoryId) {
        this.id = id;
        this.name = name;
        this.nameLower = name != null ? name.toLowerCase() : null;
        this.price = price;
        this.imageUrl = imageUrl;
        this.rating = rating;
        this.reviewCount = reviewCount;
        this.quantity = quantity;
        this.originalPrice = originalPrice;
        this.discountedPrice = discountedPrice;
        this.description = description;
        this.deliveryDate = deliveryDate;
        this.deliveryAddress = deliveryAddress;
        this.categoryId = categoryId;
        this.stability = 0; // Default value since it’s not in constructor params yet
    }

    // Full constructor including all fields (optional, for completeness)
    public Product(String id, String name, double price, String imageUrl, int rating, int reviewCount,
                   String quantity, double originalPrice, double discountedPrice, String description,
                   String deliveryDate, String deliveryAddress, String categoryId, String brand,
                   double discountPercentage, int stability) {
        this.id = id;
        this.name = name;
        this.nameLower = name != null ? name.toLowerCase() : null;
        this.price = price;
        this.imageUrl = imageUrl;
        this.rating = rating;
        this.reviewCount = reviewCount;
        this.quantity = quantity;
        this.originalPrice = originalPrice;
        this.discountedPrice = discountedPrice;
        this.description = description;
        this.deliveryDate = deliveryDate;
        this.deliveryAddress = deliveryAddress;
        this.categoryId = categoryId;
        this.brand = brand;
        this.discountPercentage = discountPercentage;
        this.stability = stability;
    }

    // Parcelable constructor
    protected Product(Parcel in) {
        id = in.readString();
        name = in.readString();
        nameLower = in.readString();
        price = in.readDouble();
        imageUrl = in.readString();
        rating = in.readInt();
        reviewCount = in.readInt();
        quantity = in.readString();
        originalPrice = in.readDouble();
        discountedPrice = in.readDouble();
        description = in.readString();
        deliveryDate = in.readString();
        deliveryAddress = in.readString();
        categoryId = in.readString();
        brand = in.readString();
        discountPercentage = in.readDouble();
        stability = in.readInt(); // Added
    }

    // Parcelable CREATOR
    public static final Creator<Product> CREATOR = new Creator<Product>() {
        @Override
        public Product createFromParcel(Parcel in) {
            return new Product(in);
        }

        @Override
        public Product[] newArray(int size) {
            return new Product[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(name);
        dest.writeString(nameLower);
        dest.writeDouble(price);
        dest.writeString(imageUrl);
        dest.writeInt(rating);
        dest.writeInt(reviewCount);
        dest.writeString(quantity);
        dest.writeDouble(originalPrice);
        dest.writeDouble(discountedPrice);
        dest.writeString(description);
        dest.writeString(deliveryDate);
        dest.writeString(deliveryAddress);
        dest.writeString(categoryId);
        dest.writeString(brand);
        dest.writeDouble(discountPercentage);
        dest.writeInt(stability); // Added
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.nameLower = name != null ? name.toLowerCase() : null; // Sync nameLower with name
    }

    public String getNameLower() {
        return nameLower;
    }

    public void setNameLower(String nameLower) {
        this.nameLower = nameLower;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public int getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(int reviewCount) {
        this.reviewCount = reviewCount;
    }

    public String getQuantity() {
        return quantity;
    }

    public void setQuantity(String quantity) {
        this.quantity = quantity;
    }

    public double getOriginalPrice() {
        return originalPrice;
    }

    public void setOriginalPrice(double originalPrice) {
        this.originalPrice = originalPrice;
    }

    public double getDiscountedPrice() {
        return discountedPrice;
    }

    public void setDiscountedPrice(double discountedPrice) {
        this.discountedPrice = discountedPrice;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDeliveryDate() {
        return deliveryDate;
    }

    public void setDeliveryDate(String deliveryDate) {
        this.deliveryDate = deliveryDate;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public double getDiscountPercentage() {
        return discountPercentage;
    }

    public void setDiscountPercentage(double discountPercentage) {
        this.discountPercentage = discountPercentage;
    }

    public int getStability() {
        return stability;
    }

    public void setStability(int stability) {
        this.stability = stability;
    }
}