/*
 * Copyright 2018 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ar.core.examples.java.cloudanchor;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.ar.core.examples.java.common.messaging.AppController;
import com.google.ar.core.examples.java.common.messaging.HuntNotification;
import com.google.common.base.Preconditions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/** A helper class to manage all communications with Firebase. */
class FirebaseManager {
  private static final String TAG =
      HuntTreasureActivity.class.getSimpleName() + "." + FirebaseManager.class.getSimpleName();

  /** Listener for a new room code. */
  interface RoomCodeListener {

    /** Invoked when a new room code is available from Firebase. */
    void onNewRoomCode(Long newRoomCode);

    /** Invoked if a Firebase Database Error happened while fetching the room code. */
    void onError(DatabaseError error);
  }

  /** Listener for a new cloud anchor ID. */
  interface CloudAnchorIdListener {

    /** Invoked when a new cloud anchor ID is available. */
    void onNewCloudAnchorId(String cloudAnchorId);
  }

  // Names of the nodes used in the Firebase Database
  private static final String ROOT_FIREBASE_HOTSPOTS = "hotspot_list";
  private static final String ROOT_LAST_ROOM_CODE = "last_room_code";

  // Some common keys and values used when writing to the Firebase Database.
  private static final String KEY_IDENTIFY_STATUS = "identify_status";
  private static final String KEY_NOTIFICATION_STATUS = "notification_status";
  private static final String KEY_NOTIFICATION_MSG = "notification_msg";
  private static final String KEY_DISPLAY_NAME = "display_name";
  private static final String KEY_ANCHOR_ID = "hosted_anchor_id";
  private static final String KEY_TIMESTAMP = "updated_at_timestamp";
  private static final String DISPLAY_NAME_VALUE = "Hunt AR App";

  private static String SERVER_API_KEY= "AIzaSyAEZkjyxfBtXdvgFo5toXaoxhS79K4gEVo";

  private final FirebaseApp app;
  private final DatabaseReference hotspotListRef;
  private final DatabaseReference roomCodeRef;
  private DatabaseReference currentRoomRef = null;
  private ValueEventListener currentRoomListener = null;

  /**
   * Default constructor for the FirebaseManager.
   *
   * @param context The application context.
   */
  FirebaseManager(Context context) {
    app = FirebaseApp.initializeApp(context);
    if (app != null) {
      DatabaseReference rootRef = FirebaseDatabase.getInstance(app).getReference();
      hotspotListRef = rootRef.child(ROOT_FIREBASE_HOTSPOTS);
      roomCodeRef = rootRef.child(ROOT_LAST_ROOM_CODE);

      DatabaseReference.goOnline();
      Log.d(TAG, "Successfully connected to Firebase Database!");
    } else {
      Log.d(TAG, "Could not connect to Firebase Database!");
      hotspotListRef = null;
      roomCodeRef = null;
    }
  }



  public void subscribeNotifications(Context context){
    String channelId  =  context.getString(R.string.default_notification_channel_id);
    String channelName = context.getString(R.string.default_notification_channel_name);
    // Register the notification handler
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      Log.i(TAG,"Initialized notification handler");
      // Create channel to show notifications.
      NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
      // Create the notification message content
      NotificationChannel mChannel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW);
      mChannel.setDescription("Treasure local ");
      mChannel.enableLights(true);
      mChannel.setLightColor(Color.RED);
      mChannel.enableVibration(true);
      mChannel.setVibrationPattern(new long[]{100, 200, 300, 400, 500, 400, 300, 200, 400});
      notificationManager.createNotificationChannel(mChannel);
    }

    Log.d(TAG, "Subscribing to news topic");
    // [START subscribe_topics]
    FirebaseMessaging.getInstance().subscribeToTopic(channelName)
            .addOnCompleteListener(new OnCompleteListener<Void>() {
              @Override
              public void onComplete(@NonNull Task<Void> task) {
                String msg = context.getString(R.string.msg_subscribed);
                if (!task.isSuccessful()) {
                  msg = context.getString(R.string.msg_subscribe_failed);
                }
                Log.d(TAG, "Topic: "+channelName+", Message:"+msg);
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
              }
            });
    // [END subscribe_topics]
  }

  /**
   * Send messages to other subscribers for treasure notification.
   */
  public void sendUpstreamMessage(Context context, HuntNotification notification){
    String topicName = context.getString(R.string.default_notification_channel_name);
    JSONObject json = new JSONObject();
    try {
          /*
            {
              "topic": "News",
              "to": "/topics/News",
              "token": "",
              "notification": {
                "title": "curl FCM Message",
                "body": "This is a Firebase Cloud Messaging Topic Message!"
              },
              "data": {
                "payload": "serialized notification"
              }
            }
           */
      JSONObject notif = new JSONObject();
        notif.put("title",notification.getNotificationTitle());
        notif.put("body",notification.getNotificationMessage());
      JSONObject data=new JSONObject();
        data.put("payload",notification.toJson());
      // Set the json payload
      json.put("topic", topicName);
      json.put("to", "/topics/"+topicName);
      json.put("token", "");
      json.put("notification",notif);
      json.put("data",data);
      Log.i(TAG, "JSON Request : " + json.toString());
    }
    catch (JSONException e) {
      Log.e(TAG,e.getMessage());
    }

        /* Ref: https://firebase.google.com/docs/cloud-messaging/android/topic-messaging
        $ curl -X POST -H "Authorization:key=AIzaSyAEZkjyxfBtXdvgFo5toXaoxhS79K4gEVo" -H "Content-Type: application/json" -d '{ "topic" : "News", "to": "/topics/News","token": "", "notification": { "title": "curl FCM Message", "body": "This is a Firebase Cloud Messaging Topic Message!" }, "data": {"message": "This is a Firebase Cloud Messaging Topic M
essage!"}}' https://fcm.googleapis.com/fcm/send
         */
    try {
      JsonObjectRequest jsonObjectRequest = new JsonObjectRequest("https://fcm.googleapis.com/fcm/send",
              json, new Response.Listener<JSONObject>() {
        @Override
        public void onResponse(JSONObject response) {
          Log.i(TAG, "JSON Response: " + response.toString());
        }
      }, new Response.ErrorListener() {
        @Override
        public void onErrorResponse(VolleyError error) {
          Log.i(TAG, "JSON Error: " + error.toString());
        }
      }) {
        @Override
        public Map<String, String> getHeaders(){
          Map<String, String> params = new HashMap<>();
          params.put("Authorization", "key=" + SERVER_API_KEY);
          params.put("Content-Type", "application/json");
          Log.i(TAG, "Params: " + params.toString());
          return params;
        }
      };
      AppController.getInstance(context).addToRequestQueue(jsonObjectRequest);
    } catch (Exception e) {
      Log.e(TAG,e.getMessage());
    }
  }

  /**
   * Gets a new room code from the Firebase Database.
   * Invokes the listener method when a new room code is available.
   */
  void getNewRoomCode(RoomCodeListener listener) {
    Preconditions.checkNotNull(app, "Firebase App was null");
    roomCodeRef.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            Long nextCode = Long.valueOf(1);
            Object currVal = currentData.getValue();
            if (currVal != null) {
              Long lastCode = Long.valueOf(currVal.toString());
              nextCode = lastCode + 1;
            }
            currentData.setValue(nextCode);
            return Transaction.success(currentData);
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            if (!committed) {
              listener.onError(error);
              return;
            }
            Long roomCode = currentData.getValue(Long.class);
            listener.onNewRoomCode(roomCode);
          }
        });
  }

  /** Stores the given anchor ID in the given room code. */
  void storeAnchorIdInRoom(Long roomCode, String cloudAnchorId) {
    Preconditions.checkNotNull(app, "Firebase App was null");
    DatabaseReference roomRef = hotspotListRef.child(String.valueOf(roomCode));
    roomRef.child(KEY_DISPLAY_NAME).setValue(DISPLAY_NAME_VALUE);
    roomRef.child(KEY_ANCHOR_ID).setValue(cloudAnchorId);
    roomRef.child(KEY_NOTIFICATION_MSG).setValue("Notification: You should look for treasure id "+roomCode);
    roomRef.child(KEY_NOTIFICATION_STATUS).setValue("Created"); // Created -> Read -> Started
    roomRef.child(KEY_IDENTIFY_STATUS).setValue("None");
    roomRef.child(KEY_TIMESTAMP).setValue(Long.valueOf(System.currentTimeMillis()));
  }

  /**
   * Registers a new listener for the given room code. The listener is invoked whenever the data for
   * the room code is changed.
   */
  void registerNewListenerForRoom(Long roomCode, CloudAnchorIdListener listener) {
    Preconditions.checkNotNull(app, "Firebase App was null");
    clearRoomListener();
    currentRoomRef = hotspotListRef.child(String.valueOf(roomCode));
    currentRoomListener =
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot dataSnapshot) {
            Object valObj = dataSnapshot.child(KEY_ANCHOR_ID).getValue();
            if (valObj != null) {
              String anchorId = String.valueOf(valObj);
              if (!anchorId.isEmpty()) {
                listener.onNewCloudAnchorId(anchorId);
              }
            }
          }

          @Override
          public void onCancelled(DatabaseError databaseError) {
            Log.w(TAG, "The Firebase operation was cancelled.", databaseError.toException());
          }
        };
    currentRoomRef.addValueEventListener(currentRoomListener);
  }

  /**
   * Resets the current room listener registered using {@link #registerNewListenerForRoom(Long,
   * CloudAnchorIdListener)}.
   */
  void clearRoomListener() {
    if (currentRoomListener != null && currentRoomRef != null) {
      currentRoomRef.removeEventListener(currentRoomListener);
      currentRoomListener = null;
      currentRoomRef = null;
    }
  }
}
