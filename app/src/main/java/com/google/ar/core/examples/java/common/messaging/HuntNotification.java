package com.google.ar.core.examples.java.common.messaging;

import com.google.gson.Gson;

import java.io.Serializable;
import java.sql.Timestamp;

public class HuntNotification implements Serializable{

    private Long roomId;                 // Room Number
    private String type;
    private String displayName;             // "Hunt AR App"
    private String hostedAnchorId;          // "ua-6d0c06b57559a5d39d04de6bbee7f171"
    private String identifyStatus;          // Public:"Created" -> Hidden:"Found"
	private String notificationHint;        // Hint for the treasure
    private String notificationTitle;
    private String notificationMessage;
    private String notificationImageurl;
    private String notificationStatus;     // Send:"Created" -> ""
    private Timestamp updatedAt;

    public HuntNotification() {
        super();
    }

    public HuntNotification(Long id, String type, String displayName, String hostedAnchorId, String identifyStatus, String notification_title, String notification_message, String notification_imageurl, String notification_status, Timestamp updatedAtTimeStamp) {
        this.roomId = id;
        this.type = type;
        this.displayName = displayName;
        this.hostedAnchorId = hostedAnchorId;
        this.identifyStatus = identifyStatus;
        this.notificationTitle = notification_title;
        this.notificationMessage = notification_message;
        this.notificationImageurl = notification_imageurl;
        this.notificationStatus = notification_status;
        this.updatedAt = updatedAtTimeStamp;
    }


    public HuntNotification(Long id, String hostedAnchorId) {
        this.roomId = id;
        this.type = "treasure";
        this.displayName = "Hunt App";
        this.hostedAnchorId = hostedAnchorId;
        this.identifyStatus = "created";
        this.notificationTitle = "A treasure has been planted for you to find";
        this.notificationMessage = "Click to start your hunt now";
        this.notificationImageurl = "https://api.androidhive.info/images/minion.jpg";
        this.notificationStatus = "created";
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }


    /**
     * Json serialization
     *
     * @return
     */
    public String toJson() {
        return new Gson().toJson(this).toString();
    }

    /**
     * Json deserialization
     *
     * @param json
     * @return
     */
    public HuntNotification fromJson(String json){
        HuntNotification tn = new Gson().fromJson(json, HuntNotification.class);
        return tn;
    }


    public String getNotificationHint() {
        return notificationHint;
    }

    public void setNotificationHint(String notificationHint) {
        this.notificationHint = notificationHint;
    }
    public Long getRoomId() {
        return roomId;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getHostedAnchorId() {
        return hostedAnchorId;
    }

    public void setHostedAnchorId(String hostedAnchorId) {
        this.hostedAnchorId = hostedAnchorId;
    }

    public String getIdentifyStatus() {
        return identifyStatus;
    }

    public void setIdentifyStatus(String identifyStatus) {
        this.identifyStatus = identifyStatus;
    }

    public String getNotificationTitle() {
        return notificationTitle;
    }

    public void setNotificationTitle(String notificationTitle) {
        this.notificationTitle = notificationTitle;
    }

    public String getNotificationMessage() {
        return notificationMessage;
    }

    public void setNotificationMessage(String notificationMessage) {
        this.notificationMessage = notificationMessage;
    }

    public String getNotificationImageurl() {
        return notificationImageurl;
    }

    public void setNotificationImageurl(String notificationImageurl) {
        this.notificationImageurl = notificationImageurl;
    }

    public String getNotificationStatus() {
        return notificationStatus;
    }

    public void setNotificationStatus(String notificationStatus) {
        this.notificationStatus = notificationStatus;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "HuntNotification{" +
                "roomId=" + roomId +
                ", type='" + type + '\'' +
                ", displayName='" + displayName + '\'' +
                ", hostedAnchorId='" + hostedAnchorId + '\'' +
                ", identifyStatus='" + identifyStatus + '\'' +
                ", notificationHint='" + notificationHint + '\'' +
                ", notificationTitle='" + notificationTitle + '\'' +
                ", notificationMessage='" + notificationMessage + '\'' +
                ", notificationImageurl='" + notificationImageurl + '\'' +
                ", notificationStatus='" + notificationStatus + '\'' +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
