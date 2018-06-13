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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.ar.core.examples.java.common.messaging.AppController;
import com.google.ar.core.examples.java.common.messaging.HuntNotification;
import com.google.common.base.Preconditions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A helper class to manage all communications with Firebase. */
public class FirebaseManager {
  private static final String TAG =
      HuntTreasureActivity.class.getSimpleName() + "." + FirebaseManager.class.getSimpleName();

  /** Listener for a new room code. */
  interface RoomCodeListener {
    /** Invoked when a new room code is available from Firebase. */
    void onNewRoomCode(Long newRoomCode);
    /** Invoked if a Firebase Database Error happened while fetching the room code. */
    void onError(DatabaseError error);

    void onFetchNotificationsData();
  }

  /** Listener for a new cloud anchor ID. */
  interface CloudAnchorIdListener {
    /** Invoked when a new cloud anchor ID is available. */
    void onNewCloudAnchorId(String cloudAnchorId);
  }

  interface StorageListener {
    /** Invoked when an image download is available from Firebase. */
    void onDownloadCompleteUrl(String imageUrl);
    void onUploadCompleteUrl(String imageUrl);
    void onDownloadCompleteBitmap(Bitmap bitmap);
    void onError(String error);


  }

  // Names of the nodes used in the Firebase Database
  private static final String ROOT_FIREBASE_HOTSPOTS = "hotspot_list";
  private static final String ROOT_LAST_ROOM_CODE = "last_room_code";

  // Some common keys and values used when writing to the Firebase Database.
  private static final String KEY_ROOM_ID = "room_id";
  private static final String KEY_TYPE = "type";
  private static final String KEY_DISPLAY_NAME = "display_name";
  private static final String KEY_ANCHOR_ID = "hosted_anchor_id";
  private static final String KEY_IDENTIFY_STATUS = "identify_status";
  private static final String KEY_IDENTIFY_HINT="identify_hint";
  private static final String KEY_LATITUDE = "latitude";
  private static final String KEY_LONGITUDE = "longitude";
  private static final String KEY_NOTIFICATION_TITLE = "notification_title";
  private static final String KEY_NOTIFICATION_MESSAGE = "notification_message";
  private static final String KEY_NOTIFICATION_IMAGEURL = "notification_imageurl";
  private static final String KEY_NOTIFICATION_STATUS = "notification_status";
  private static final String KEY_TIMESTAMP = "updated_at";
  // Values
  private static final String DISPLAY_NAME_VALUE = "Hunt AR App";

  private static String SERVER_API_KEY= "AIzaSyAEZkjyxfBtXdvgFo5toXaoxhS79K4gEVo";

  private final FirebaseApp app;
  private final DatabaseReference hotspotListRef;
  private final DatabaseReference roomCodeRef;
  private DatabaseReference currentRoomRef = null;
  private ValueEventListener currentRoomListener = null;
  private StorageReference storageRef = null;

  private static Map<String, Treasure> notificationStoreMap = new HashMap<>();

  /**
   * Default constructor for the FirebaseManager.
   *
   * @param context The application context.
   */
  FirebaseManager(Context context) {
    app = FirebaseApp.initializeApp(context);
    if (app != null) {
      // Initialize the Database reference
      DatabaseReference rootRef = FirebaseDatabase.getInstance(app).getReference();
      hotspotListRef = rootRef.child(ROOT_FIREBASE_HOTSPOTS);
      hotspotListRef.addValueEventListener(new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot dataSnapshot) {
              for (DataSnapshot childDataSnapshot : dataSnapshot.getChildren()) {
                  String  key = childDataSnapshot.getKey();
                  Log.i(TAG, "\n\nKey  : " + key); //displays the key for the node
                  Log.i(TAG, "Value: " + childDataSnapshot.getValue());   //gives the value for given keyname
                  try {
                      if(childDataSnapshot.child(KEY_IDENTIFY_STATUS).getValue() != null &&
                              !childDataSnapshot.child(KEY_IDENTIFY_STATUS).getValue().toString().equalsIgnoreCase("created")){
                          continue; // Skip the ones marked found
                      }

                      Treasure t = new Treasure();
                      t.setExpiration("Expires in: 8 mins");
                      t.setRoomId(Integer.parseInt(key));
                      if(childDataSnapshot.child(KEY_IDENTIFY_HINT).getValue() != null)
                            t.setHint(childDataSnapshot.child(KEY_IDENTIFY_HINT).getValue().toString());
                      if( childDataSnapshot.child(KEY_TYPE).getValue() != null) {
                          if(childDataSnapshot.child(KEY_TYPE).getValue().toString().equalsIgnoreCase("treasure")) {
                              t.setTreasureType(CreateTreasureActivity.TreasureType.TREASURE_CHEST);
                          }else {
                              t.setTreasureType(CreateTreasureActivity.TreasureType.LETTER);
                          }
                      }
                      if(childDataSnapshot.child(KEY_NOTIFICATION_IMAGEURL).getValue() != null)
                          t.setHintPictureUrl(childDataSnapshot.child(KEY_NOTIFICATION_IMAGEURL).getValue().toString());
                      if(childDataSnapshot.child(KEY_LATITUDE).getValue() != null)
                            t.setLatitude(Double.parseDouble(childDataSnapshot.child(KEY_LATITUDE).getValue().toString()));
                      if(childDataSnapshot.child(KEY_LONGITUDE).getValue() != null)
                          t.setLongitude(Double.parseDouble(childDataSnapshot.child(KEY_LONGITUDE).getValue().toString()));
                      notificationStoreMap.put(key, t);
                      downloadCacheImageFromStorage(key, (String) childDataSnapshot.child(KEY_NOTIFICATION_IMAGEURL).getValue());
                      Log.i(TAG, t.toString());
                  }catch(Exception e){
                      Log.e(TAG, e.getMessage());
                  }
              }
          }

          @Override
          public void onCancelled(DatabaseError databaseError) {
              // Getting Post failed, log a message
              Log.w(TAG, "loadPost:onCancelled", databaseError.toException());
          }
      });

      roomCodeRef = rootRef.child(ROOT_LAST_ROOM_CODE);

      DatabaseReference.goOnline();
      Log.d(TAG, "Successfully connected to Firebase Database!");

      // Initialize the storage bucket reference
      FirebaseStorage storage = FirebaseStorage.getInstance(app, "gs://huntar-88a42.appspot.com");
      // Create a storage reference from our app
      storageRef = storage.getReference();
    } else {
      Log.d(TAG, "Could not connect to Firebase Database!");
      hotspotListRef = null;
      roomCodeRef = null;
    }
  }

  public List<Treasure> getAllTreasures(){
      List<Treasure> sorted = new ArrayList<>(notificationStoreMap.values());
      Collections.sort(sorted, new Comparator<Treasure>(){
          public int compare(Treasure o1, Treasure o2){
              if(o1.getRoomId() == o2.getRoomId())
                  return 0;
              return o1.getRoomId() > o2.getRoomId() ? -1 : 1;
          }
      });
      return sorted;
  }
    /**
     * Update the image bitmap in the local list
     * @param key
     * @param pathFireImg
     */
    public void downloadCacheImageFromStorage(String key, String pathFireImg){
        // Create a reference with an initial file path and name
        StorageReference pathReference = storageRef.child(pathFireImg);
        Log.i(TAG,"\n\n Downloading " + pathFireImg + " from firebase");
        // Create a reference to a file from a Google Cloud Storage URI
        storageRef.child(pathFireImg).getBytes(Long.MAX_VALUE).addOnSuccessListener(new OnSuccessListener<byte[]>() {
            @Override
            public void onSuccess(byte[] bytes) {
                // Data for "images/island.jpg" is returns, use this as needed
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes , 0, bytes.length);
                Log.i(TAG, "Image "+pathFireImg+" is of size "+bitmap.getWidth()+"x"+bitmap.getHeight());
                notificationStoreMap.get(key).setHintPicture(bitmap);
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception exception) {
                Log.e(TAG, "Error downloading from Firebase Database!");
            }
        });

    }

  /**
   * Upload image to firebase storage and return the file path
   *
   * @param filename
   * @param bitmap
   * @return
   */
  public String uploadImageToStorage(String filename, Bitmap bitmap, StorageListener listener){
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
    byte[] data = baos.toByteArray();

    String pathFireImg = "images/"+filename;
    // Create the file metadata
    // Create file metadata including the content type
    StorageMetadata metadata = new StorageMetadata.Builder()
            .setContentType("image/jpg")
            .build();
    // Create a child reference imagesRef now points to "images"
    StorageReference imagesRef = storageRef.child(pathFireImg); // Firebase path: images/file.jpg
    // Upload task
    UploadTask uploadTask = imagesRef.putBytes(data, metadata);
    uploadTask.addOnFailureListener(new OnFailureListener() {
      @Override
      public void onFailure(@NonNull Exception exception) {
        Log.e(TAG, "Error in upload: "+exception.getMessage());
      }
    }).addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
      @Override
      public void onProgress(UploadTask.TaskSnapshot taskSnapshot) {
        double progress = (100.0 * taskSnapshot.getBytesTransferred()) / taskSnapshot.getTotalByteCount();
        Log.i(TAG,"\n\n Upload is " + progress + "% done");
      }
    }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
      @Override
      public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
        // taskSnapshot.getMetadata() contains file metadata such as size, content-type, etc.
        String url = taskSnapshot.getDownloadUrl().toString();
        Log.i(TAG, "Upload success");
        listener.onUploadCompleteUrl(url);
      }
    });
    return pathFireImg;
  }

  public void downloadImageFromStorage(String pathFireImg, StorageListener listener){
    // Create a reference with an initial file path and name
    StorageReference pathReference = storageRef.child(pathFireImg);
    Log.i(TAG,"\n\n Downloading " + pathFireImg + " from firebase");
    // Create a reference to a file from a Google Cloud Storage URI
    storageRef.child(pathFireImg).getBytes(Long.MAX_VALUE).addOnSuccessListener(new OnSuccessListener<byte[]>() {
      @Override
      public void onSuccess(byte[] bytes) {
        // Data for "images/island.jpg" is returns, use this as needed
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes , 0, bytes.length);
        listener.onDownloadCompleteBitmap(bitmap);
      }
    }).addOnFailureListener(new OnFailureListener() {
      @Override
      public void onFailure(@NonNull Exception exception) {
        listener.onError(exception.getMessage());
      }
    });

  }

  public void downloadImageUrlFromStorage(String pathFireImg, StorageListener listener){
    // Create a reference with an initial file path and name
    StorageReference pathReference = storageRef.child(pathFireImg);
    Log.i(TAG,"\n\n Downloading " + pathFireImg + " from firebase");
    storageRef.child(pathFireImg).getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
      @Override
      public void onSuccess(Uri uri) {
        // Uri for "images/island.jpg" is returns, use this as needed
        listener.onDownloadCompleteUrl(uri.toString());
      }
    }).addOnFailureListener(new OnFailureListener() {
      @Override
      public void onFailure(@NonNull Exception exception) {
        listener.onError(exception.getMessage());
      }
    });
  }


    public void downloadNotifications(HuntNotification huntNotification, RoomCodeListener listener){


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
  void storeAnchorIdInRoom(HuntNotification huntNotification) { // Long roomCode, String cloudAnchorId
      Preconditions.checkNotNull(app, "Firebase App was null");
      DatabaseReference roomRef = hotspotListRef.child(String.valueOf(huntNotification.getRoomId()));
      roomRef.child(KEY_ROOM_ID).setValue(huntNotification.getRoomId());
      roomRef.child(KEY_TYPE).setValue(huntNotification.getType());
      roomRef.child(KEY_DISPLAY_NAME).setValue(DISPLAY_NAME_VALUE);
      roomRef.child(KEY_ANCHOR_ID).setValue(huntNotification.getHostedAnchorId());
      roomRef.child(KEY_IDENTIFY_STATUS).setValue(huntNotification.getIdentifyStatus());
      roomRef.child(KEY_IDENTIFY_HINT).setValue(huntNotification.getIdentifyHint());
      roomRef.child(KEY_NOTIFICATION_TITLE).setValue(huntNotification.getNotificationTitle());
      roomRef.child(KEY_NOTIFICATION_MESSAGE).setValue(huntNotification.getNotificationMessage());
      roomRef.child(KEY_NOTIFICATION_IMAGEURL).setValue(huntNotification.getNotificationImageurl());
      roomRef.child(KEY_NOTIFICATION_STATUS).setValue(huntNotification.getNotificationStatus());
      roomRef.child(KEY_LATITUDE).setValue(huntNotification.getLatitude());
      roomRef.child(KEY_LONGITUDE).setValue(huntNotification.getLongitude());
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
