package com.oopgroup.smartpharmacy.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.PropertyName;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class Order implements Serializable {
    private String id;
    private String orderId;
    private String userId;
    private String addressId;
    private String paymentMethod;
    private double grandTotal;
    private String status;
    private Timestamp createdAt;
    private String currency;
    private boolean reorder;  // Renamed field
    private Map<String, Object> totalBreakdown;
    private List<Map<String, Object>> items;
    private int rating;  // Added rating field

    public Order() {}

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getAddressId() { return addressId; }
    public void setAddressId(String addressId) { this.addressId = addressId; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public double getGrandTotal() { return grandTotal; }
    public void setGrandTotal(double grandTotal) { this.grandTotal = grandTotal; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    @PropertyName("isReorder")
    public boolean isReorder() { return reorder; }

    @PropertyName("isReorder")
    public void setReorder(boolean reorder) { this.reorder = reorder; }

    public Map<String, Object> getTotalBreakdown() { return totalBreakdown; }
    public void setTotalBreakdown(Map<String, Object> totalBreakdown) { this.totalBreakdown = totalBreakdown; }

    public List<Map<String, Object>> getItems() { return items; }
    public void setItems(List<Map<String, Object>> items) { this.items = items; }

    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }

    public String getFirstProductId() {
        return (items != null && !items.isEmpty()) ? (String) items.get(0).get("productId") : null;
    }
}