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
    private Address address; // New field to store address object
    private Integer estimatedDays; // New field for estimated days
    private String paymentMethod;
    private double grandTotal;
    private String status;
    private Timestamp createdAt;
    private String currency;
    private boolean reorder;
    private Map<String, Object> totalBreakdown;
    private List<Map<String, Object>> items;
    private int rating;
    private String orderType; // "LabTest" or "Product"
    private String deliveryBoyName; // For product orders
    private String deliveryBoyPhone; // For product orders
    private String prescriptionUrl; // URL to uploaded prescription, if any
    private Boolean prescriptionApproved; // Approval status of prescription
    private String firstProductId; // Explicit field for first product/lab test ID

    public Order() {
        this.orderType = "LabTest"; // Default to "LabTest" if not specified
        this.prescriptionApproved = null; // Null means not applicable or not yet reviewed
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getAddressId() { return addressId; }
    public void setAddressId(String addressId) { this.addressId = addressId; }

    public Address getAddress() { return address; }
    public void setAddress(Address address) { this.address = address; }

    public Integer getEstimatedDays() { return estimatedDays; }
    public void setEstimatedDays(Integer estimatedDays) { this.estimatedDays = estimatedDays; }

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

    public String getOrderType() { return orderType; }
    public void setOrderType(String orderType) { this.orderType = orderType; }

    public String getDeliveryBoyName() { return deliveryBoyName; }
    public void setDeliveryBoyName(String deliveryBoyName) { this.deliveryBoyName = deliveryBoyName; }

    public String getDeliveryBoyPhone() { return deliveryBoyPhone; }
    public void setDeliveryBoyPhone(String deliveryBoyPhone) { this.deliveryBoyPhone = deliveryBoyPhone; }

    public String getPrescriptionUrl() { return prescriptionUrl; }
    public void setPrescriptionUrl(String prescriptionUrl) { this.prescriptionUrl = prescriptionUrl; }

    public Boolean getPrescriptionApproved() { return prescriptionApproved; }
    public void setPrescriptionApproved(Boolean prescriptionApproved) { this.prescriptionApproved = prescriptionApproved; }

    @PropertyName("firstProductId")
    public String getFirstProductId() { return firstProductId; }

    @PropertyName("firstProductId")
    public void setFirstProductId(String firstProductId) { this.firstProductId = firstProductId; }
}