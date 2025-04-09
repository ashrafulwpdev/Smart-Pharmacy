package com.oopgroup.smartpharmacy.models;

import java.util.HashMap;
import java.util.Map;

public class Coupon {
    private String id;
    private String code;
    private double discount;
    private boolean isActive; // Added isActive field as a boolean

    // Default constructor for Firestore
    public Coupon() {}

    public Coupon(String id, String code, double discount, boolean isActive) {
        this.id = id;
        this.code = code;
        this.discount = discount;
        this.isActive = isActive;
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

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    // Convert to Map for Firestore
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("code", code);
        map.put("discount", discount);
        map.put("isActive", isActive); // Include isActive
        return map;
    }

    // Convert Firestore document to Coupon object
    public static Coupon fromMap(Map<String, Object> map, String documentId) {
        Coupon coupon = new Coupon();
        coupon.setId(documentId);
        coupon.setCode((String) map.get("code"));
        coupon.setDiscount(((Number) map.get("discount")).doubleValue());

        // Handle isActive: it might be a string ("true") or a boolean (true)
        Object isActiveObj = map.get("isActive");
        if (isActiveObj instanceof Boolean) {
            coupon.setActive((Boolean) isActiveObj);
        } else if (isActiveObj instanceof String) {
            coupon.setActive("true".equalsIgnoreCase((String) isActiveObj));
        } else {
            coupon.setActive(false); // Default to false if the field is missing or invalid
        }

        return coupon;
    }
}