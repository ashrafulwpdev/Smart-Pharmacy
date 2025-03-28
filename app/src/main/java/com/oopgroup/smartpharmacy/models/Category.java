package com.oopgroup.smartpharmacy.models;

import android.os.Parcel;
import android.os.Parcelable;

public class Category implements Parcelable {
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

    // Parcelable constructor
    protected Category(Parcel in) {
        id = in.readString();
        name = in.readString();
        productCount = in.readInt();
        imageUrl = in.readString();
    }

    // Parcelable CREATOR
    public static final Creator<Category> CREATOR = new Creator<Category>() {
        @Override
        public Category createFromParcel(Parcel in) {
            return new Category(in);
        }

        @Override
        public Category[] newArray(int size) {
            return new Category[size];
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
        dest.writeInt(productCount);
        dest.writeString(imageUrl);
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
    }

    public int getProductCount() {
        return productCount;
    }

    public void setProductCount(int productCount) {
        this.productCount = productCount;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}