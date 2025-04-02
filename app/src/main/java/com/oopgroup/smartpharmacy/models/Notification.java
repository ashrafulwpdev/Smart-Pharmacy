package com.oopgroup.smartpharmacy.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.PropertyName;

public class Notification {
    private String id;
    private String userId;
    private String title;
    private String message;
    private String type;
    private Timestamp createdAt;
    private boolean isRead;

    public Notification() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    @PropertyName("isRead")
    public boolean isRead() { return isRead; }

    @PropertyName("isRead")
    public void setRead(boolean isRead) { this.isRead = isRead; } // Align parameter name with field
}