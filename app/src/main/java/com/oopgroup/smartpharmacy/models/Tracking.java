package com.oopgroup.smartpharmacy.models;

import com.google.firebase.Timestamp;

public class Tracking {
    private String id;
    private String status;
    private Timestamp updatedAt;
    private String details;
    private String userId; // Added userId field

    public Tracking() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}