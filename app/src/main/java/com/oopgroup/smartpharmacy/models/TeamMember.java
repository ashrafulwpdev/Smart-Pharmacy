package com.oopgroup.smartpharmacy.models;

public class TeamMember {
    private String name;
    private String role;
    private String description;
    private int photoResId; // Resource ID for the photo

    public TeamMember(String name, String role, String description, int photoResId) {
        this.name = name;
        this.role = role;
        this.description = description;
        this.photoResId = photoResId;
    }

    public String getName() { return name; }
    public String getRole() { return role; }
    public String getDescription() { return description; }
    public int getPhotoResId() { return photoResId; }
}