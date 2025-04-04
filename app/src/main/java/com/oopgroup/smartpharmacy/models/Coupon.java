package com.oopgroup.smartpharmacy.models;

import java.util.HashMap;
import java.util.Map;

public class Coupon {
    private String id;
    private String code;
    private double discount;

    // Default constructor for Firestore
    public Coupon() {}

    public Coupon(String id, String code, double discount) {
        this.id = id;
        this.code = code;
        this.discount = discount;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public double getDiscount() {
        return discount;
    }

    public void setDiscount(double discount) {
        this.discount = discount;
    }

    // Convert to Map for Firestore (if needed)
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("code", code);
        map.put("discount", discount);
        return map;
    }
}