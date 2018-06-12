package com.google.ar.core.examples.java.cloudanchor;

import android.graphics.Bitmap;

public class Treasure {



    private String expiration;
    private String hint;
    private CreateTreasureActivity.TreasureType treasureType;
    private Bitmap hintPicture;
    private double longtitude;
    private double latitude;


    private boolean isTrackingThisTreasure;

    public Treasure(String expiration, String hint, CreateTreasureActivity.TreasureType treasureType, Bitmap hintPicture, double longtitude, double latitude, boolean isTracking) {
        this.expiration = expiration;
        this.hint = hint;
        this.treasureType = treasureType;
        this.hintPicture = hintPicture;
        this.longtitude = longtitude;
        this.latitude = latitude;
        this.isTrackingThisTreasure = isTracking;
    }
    public boolean isTrackingThisTreasure() {
        return isTrackingThisTreasure;
    }

    public void setTrackingThisTreasure(boolean trackingThisTreasure) {
        isTrackingThisTreasure = trackingThisTreasure;
    }


    public String getExpiration() {
        return expiration;
    }

    public String getHint() {
        return hint;
    }

    public void setHint(String hint) {
        this.hint = hint;
    }

    public CreateTreasureActivity.TreasureType getTreasureType() {
        return treasureType;
    }

    public void setTreasureType(CreateTreasureActivity.TreasureType treasureType) {
        this.treasureType = treasureType;
    }

    public Bitmap getHintPicture() {
        return hintPicture;
    }

    public void setHintPicture(Bitmap hintPicture) {
        this.hintPicture = hintPicture;
    }

    public double getLongtitude() {
        return longtitude;
    }

    public void setLongtitude(double longtitude) {
        this.longtitude = longtitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public void setExpiration(String expiration) {
        this.expiration = expiration;
    }


}
