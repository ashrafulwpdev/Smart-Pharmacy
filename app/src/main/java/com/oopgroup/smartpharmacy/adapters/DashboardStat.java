package com.oopgroup.smartpharmacy.adapters;

public class DashboardStat {
    private String title;
    private String value;
    private int iconRes;

    public DashboardStat(String title, String value, int iconRes) {
        this.title = title;
        this.value = value;
        this.iconRes = iconRes;
    }

    public String getTitle() { return title; }
    public String getValue() { return value; }
    public int getIconRes() { return iconRes; }
    public void setValue(String value) { this.value = value; }
}
