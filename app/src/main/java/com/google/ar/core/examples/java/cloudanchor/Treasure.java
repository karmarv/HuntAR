package com.google.ar.core.examples.java.cloudanchor;

import android.graphics.Bitmap;

import java.io.Serializable;

public class Treasure implements Serializable {


    private Integer roomId;
    private String expiration;
    private String hint;
    private CreateTreasureActivity.TreasureType treasureType;
    private Bitmap hintPicture;
    private String hintPictureUrl;
    private double longitude;
    private double latitude;


    private boolean isTrackingThisTreasure;

    public Treasure(){}
    public Treasure(String expiration, String hint, CreateTreasureActivity.TreasureType treasureType, Bitmap hintPicture, String hintPictureUrl, double longitude, double latitude, boolean isTracking) {
        this.expiration = expiration;
        this.hint = hint;
        this.treasureType = treasureType;
        this.hintPicture = hintPicture;
        this.hintPictureUrl = hintPictureUrl;
        this.longitude = longitude;
        this.latitude = latitude;
        this.isTrackingThisTreasure = isTracking;
    }
    public boolean isTrackingThisTreasure() {
        return isTrackingThisTreasure;
    }

    public void setTrackingThisTreasure(boolean trackingThisTreasure) {
        isTrackingThisTreasure = trackingThisTreasure;
    }

    public Integer getRoomId() {
        return roomId;
    }

    public void setRoomId(Integer roomId) {
        this.roomId = roomId;
    }

    public String getHintPictureUrl() {
        return hintPictureUrl;
    }

    public void setHintPictureUrl(String hintPictureUrl) {
        this.hintPictureUrl = hintPictureUrl;
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

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
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

    @Override
    public String toString() {
        return "Treasure{" +
                "roomId=" + roomId +
                ", expiration='" + expiration + '\'' +
                ", hint='" + hint + '\'' +
                ", treasureType=" + treasureType +
                ", hintPicture=" + hintPicture +
                ", hintPictureUrl='" + hintPictureUrl + '\'' +
                ", longitude=" + longitude +
                ", latitude=" + latitude +
                ", isTrackingThisTreasure=" + isTrackingThisTreasure +
                '}';
    }
}
