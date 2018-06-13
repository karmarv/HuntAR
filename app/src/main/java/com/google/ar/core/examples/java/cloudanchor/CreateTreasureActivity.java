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

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.GuardedBy;
import android.support.design.widget.TextInputEditText;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.ar.core.Anchor;
import com.google.ar.core.Anchor.CloudAnchorState;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Config.CloudAnchorMode;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.Point.OrientationMode;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.examples.java.common.helpers.CameraPermissionHelper;
import com.google.ar.core.examples.java.common.helpers.DisplayRotationHelper;
import com.google.ar.core.examples.java.common.helpers.FullScreenHelper;
import com.google.ar.core.examples.java.common.helpers.SnackbarHelper;
import com.google.ar.core.examples.java.common.messaging.HuntNotification;
import com.google.ar.core.examples.java.common.rendering.BackgroundRenderer;
import com.google.ar.core.examples.java.common.rendering.ObjectRenderer;
import com.google.ar.core.examples.java.common.rendering.ObjectRenderer.BlendMode;
import com.google.ar.core.examples.java.common.rendering.PlaneRenderer;
import com.google.ar.core.examples.java.common.rendering.PointCloudRenderer;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.common.base.Preconditions;
import com.google.firebase.database.DatabaseError;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.IntBuffer;
import java.util.Collection;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Main Activity for the Cloud Anchor Example
 *
 * <p>This is a simple example that shows how to host and resolve anchors using ARCore Cloud Anchors
 * API calls. This app only has at most one anchor at a time, to focus more on the cloud aspect of
 * anchors.
 */
public class CreateTreasureActivity extends AppCompatActivity implements GLSurfaceView.Renderer, SnackbarHelper.SnackbarListener {
  private static final String TAG = CreateTreasureActivity.class.getSimpleName();

  private enum HostResolveMode {
    NONE,
    HOSTING,
    RESOLVING,
  }
  private enum CreationState {
      NONE,
      NO_TREASURE_NO_PLANE,
      NO_TREASURE_BUT_PLANE,
      TREASURE_PLACED,
      TREASURE_MISSING,
      TREASURE_UPLOADING,
      TREASURE_UPLOADED,
  }
  public enum TreasureType{
      TREASURE_CHEST,
      LETTER,
  }


  // Rendering. The Renderers are created here, and initialized when the GL surface is created.
  private GLSurfaceView surfaceView;
  private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();
  private final ObjectRenderer virtualObject = new ObjectRenderer();
  private final ObjectRenderer virtualObjectShadow = new ObjectRenderer();
  private final PlaneRenderer planeRenderer = new PlaneRenderer();
  private final PointCloudRenderer pointCloudRenderer = new PointCloudRenderer();

  private boolean installRequested;

  // Temporary matrices allocated here to reduce number of allocations for each frame.
  private final float[] anchorMatrix = new float[16];
  private final float[] viewMatrix = new float[16];
  private final float[] projectionMatrix = new float[16];

  // Locks needed for synchronization
  private final Object singleTapLock = new Object();
  private final Object anchorLock = new Object();

  // Tap handling and UI.
  private GestureDetector gestureDetector;
  private final SnackbarHelper snackbarHelper = new SnackbarHelper();
  private DisplayRotationHelper displayRotationHelper;
  private RelativeLayout mainLayout;
  private LinearLayout uploadProgressLayout;

  //Rotation and scaling
  private RelativeLayout rotateLayout;
  private ImageButton rotateLeftButton;
  private long leftDown = 0;
  private long rightDown = 0;
  private ImageButton rotateRightButton;
  private ScaleGestures scaleGestureDetector;

  //keep some variables to take pictures and display them in dialogs
  private int mWidth;
  private int mHeight;
  private boolean takePicture =false;
  private Bitmap treasureBitmap;
  private boolean isTreasureEditDialogOpen = false;
  private ImageView treasureImageView;
  private ProgressBar treasureImageProgressBar;
    // Logging
  private Logger mLogger;


  @GuardedBy("singleTapLock")
  private MotionEvent queuedSingleTap;

  private Session session;

  @GuardedBy("anchorLock")
  private Anchor anchor;

  // Cloud Anchor Components.
  private FirebaseManager firebaseManager;
  private final CloudAnchorManager cloudManager = new CloudAnchorManager();
  private HostResolveMode hostResolveMode;
  private CreationState creationState;

  private TreasureType treasureType;
  private RoomCodeAndCloudAnchorIdListener hostListener;

  private HuntNotification mHuntNotification;
  private Bitmap mHintImage;


  //region Activity lifecycle
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_create_treasure);
    mLogger = new Logger("CreateTreasure");
    mHuntNotification = new HuntNotification(0L,"");
    mHintImage = null;
    Intent intent = getIntent();
    String treasureTypeString = intent.getExtras().getString("type","treasure");
      if (treasureTypeString.equals("treasure")) {
          treasureType = TreasureType.TREASURE_CHEST;
          GlobalVariables.OBJECT_ROTATION = 245.88f;
      }
      else{
          treasureType = TreasureType.LETTER;
          GlobalVariables.OBJECT_SCALE = 0.1f;
      }

    uploadProgressLayout = findViewById(R.id.uploadProgressLayout);

    rotateLayout = findViewById(R.id.rotateLayout);
    rotateLeftButton = findViewById(R.id.rotateLeft);
    rotateLeftButton.setOnTouchListener(new View.OnTouchListener() {
      @Override
      public boolean onTouch(View v, MotionEvent event) {
        if(event.getAction() == MotionEvent.ACTION_MOVE || event.getAction() == MotionEvent.ACTION_DOWN) {
          if (leftDown == 0) {
            leftDown = System.currentTimeMillis();
          }
          else{
            long now = System.currentTimeMillis();
            long diff = now -leftDown;
            leftDown = now;

            updateObjectRotation(true, diff);
          }
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
          leftDown = 0;
        }

        return true;
      }
    });
    rotateRightButton = findViewById(R.id.rotateRight);
    rotateRightButton.setOnTouchListener(new View.OnTouchListener() {
      @Override
      public boolean onTouch(View v, MotionEvent event) {
          //mLogger.logInfo("Rotate right:"+event.toString());
        if( event.getAction() == MotionEvent.ACTION_MOVE || event.getAction() == MotionEvent.ACTION_DOWN) {
            //mLogger.logInfo("Down");
          if (rightDown == 0) {
            rightDown = System.currentTimeMillis();
          }
          else{
            long now = System.currentTimeMillis();
            long diff = now -rightDown;
            rightDown = now;
            updateObjectRotation(false, diff);
          }
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
          rightDown = 0;
        }

        return true;
      }
    });

    surfaceView = findViewById(R.id.surfaceview);
    displayRotationHelper = new DisplayRotationHelper(this);

    // Set up gesture detectors
    scaleGestureDetector = new ScaleGestures(this, treasureType);

    gestureDetector =
            new GestureDetector(
                    this,
                    new GestureDetector.SimpleOnGestureListener() {
                      @Override
                      public boolean onSingleTapUp(MotionEvent e) {
                        synchronized (singleTapLock) {
                          queuedSingleTap = e;
                        }
                        return true;
                      }
                    });
    surfaceView.setOnTouchListener(new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (!gestureDetector.onTouchEvent(event)) {

                scaleGestureDetector.onTouch(v,event);
            }
            return true;
        }
    });

    // Set up renderer.
    surfaceView.setPreserveEGLContextOnPause(true);
    surfaceView.setEGLContextClientVersion(2);
    surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
    surfaceView.setRenderer(this);
    surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    installRequested = false;

    // Initialize Cloud Anchor variables.
    firebaseManager = new FirebaseManager(this);
    hostResolveMode = HostResolveMode.NONE;
    creationState = CreationState.NO_TREASURE_NO_PLANE;


    // Initialize the messaging
    firebaseManager.subscribeNotifications(getApplicationContext());
  }
  @Override
  protected void onResume() {
    super.onResume();

    if (session == null) {
      Exception exception = null;
      int messageId = -1;
      try {
        switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
          case INSTALL_REQUESTED:
            installRequested = true;
            return;
          case INSTALLED:
            break;
        }

        // ARCore requires camera permissions to operate. If we did not yet obtain runtime
        // permission on Android M and above, now is a good time to ask the user for it.
        if (!CameraPermissionHelper.hasPermissions(this)) {
          CameraPermissionHelper.requestPermissions(this);
          return;
        }
        session = new Session(this);
      } catch (UnavailableArcoreNotInstalledException e) {
        messageId = R.string.snackbar_arcore_unavailable;
        exception = e;
      } catch (UnavailableApkTooOldException e) {
        messageId = R.string.snackbar_arcore_too_old;
        exception = e;
      } catch (UnavailableSdkTooOldException e) {
        messageId = R.string.snackbar_arcore_sdk_too_old;
        exception = e;
      } catch (Exception e) {
        messageId = R.string.snackbar_arcore_exception;
        exception = e;
      }

      if (exception != null) {
        snackbarHelper.showError(this, getString(messageId));
        Log.e(TAG, "Exception creating session", exception);
        return;
      }

      // Create default config and check if supported.
      Config config = new Config(session);
      config.setCloudAnchorMode(CloudAnchorMode.ENABLED);
      session.configure(config);

      // Setting the session in the HostManager.
      cloudManager.setSession(session);
      // Show the inital message only in the first resume.
      snackbarHelper.showMessageWithDismiss(this, getString(R.string.treasure_planes_looking));
    }

    // Note that order matters - see the note in onPause(), the reverse applies here.
    try {
      session.resume();
    } catch (CameraNotAvailableException e) {
      // In some cases (such as another camera app launching) the camera may be given to
      // a different app instead. Handle this properly by showing a message and recreate the
      // session at the next iteration.
      snackbarHelper.showError(this, getString(R.string.snackbar_camera_unavailable));
      session = null;
      return;
    }
    surfaceView.onResume();
    displayRotationHelper.onResume();
  }

  @Override
  public void onPause() {
    super.onPause();
    if (session != null) {
      // Note that the order matters - GLSurfaceView is paused first so that it does not try
      // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
      // still call session.update() and get a SessionPausedException.
      displayRotationHelper.onPause();
      surfaceView.onPause();
      session.pause();
    }
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
  }

  //endregion

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
    if (!CameraPermissionHelper.hasPermissions(this)) {
      Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
          .show();
      if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
        // Permission denied with checking "Do not ask again".
        CameraPermissionHelper.launchPermissionSettings(this);
      }
      finish();
    }
  }



  //region ARCore
  /** Returns {@code true} if and only if the hit can be used to create an Anchor reliably. */
  private static boolean shouldCreateAnchorWithHit(HitResult hit) {
    Trackable trackable = hit.getTrackable();
    if (trackable instanceof Plane) {
      // Check if the hit was within the plane's polygon.
      return ((Plane) trackable).isPoseInPolygon(hit.getHitPose());
    } else if (trackable instanceof Point) {
      // Check if the hit was against an oriented point.
      return ((Point) trackable).getOrientationMode() == OrientationMode.ESTIMATED_SURFACE_NORMAL;
    }
    return false;
  }

  @Override
  public void onSurfaceCreated(GL10 gl, EGLConfig config) {
    GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

    // Prepare the rendering objects. This involves reading shaders, so may throw an IOException.
    try {
      // Create the texture and pass it to ARCore session to be filled during update().
      backgroundRenderer.createOnGlThread(this);
      planeRenderer.createOnGlThread(this, "models/trigrid.png");
      pointCloudRenderer.createOnGlThread(this);

        if (treasureType == TreasureType.LETTER) {
            mLogger.logInfo("Treasuretype was letter");
            virtualObject.createOnGlThread(this, "models/letter.obj", "models/letter.png");

        } else if (treasureType == TreasureType.TREASURE_CHEST) {
            mLogger.logInfo("Treasuretype was treasure");

            virtualObject.createOnGlThread(this, "models/treasure.obj", "models/t5.png");
        }
      virtualObject.setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f);

      virtualObjectShadow.createOnGlThread(
          this, "models/andy_shadow.obj", "models/andy_shadow.png");
      virtualObjectShadow.setBlendMode(BlendMode.Shadow);
      virtualObjectShadow.setMaterialProperties(1.0f, 0.0f, 0.0f, 1.0f);
    } catch (IOException ex) {
      Log.e(TAG, "Failed to read an asset file", ex);
    }
  }

  @Override
  public void onSurfaceChanged(GL10 gl, int width, int height) {
    displayRotationHelper.onSurfaceChanged(width, height);
    GLES20.glViewport(0, 0, width, height);
    mWidth = width;
    mHeight = height;
  }

  @Override
  public void onDrawFrame(GL10 gl) {
    // Clear screen to notify driver it should not load any pixels from previous frame.
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
      String summaryString = "rot:" + Float.toString(GlobalVariables.OBJECT_ROTATION);

    if (session == null) {
      //mLogger.logInfo("Session was null");
      return;
    }
    // Notify ARCore session that the view size changed so that the perspective matrix and
    // the video background can be properly adjusted.
    displayRotationHelper.updateSessionIfNeeded(session);

    try {
      session.setCameraTextureName(backgroundRenderer.getTextureId());

      // Obtain the current frame from ARSession. When the configuration is set to
      // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
      // camera framerate.
      Frame frame = session.update();
      Camera camera = frame.getCamera();
      Collection<Anchor> updatedAnchors = frame.getUpdatedAnchors();
      TrackingState cameraTrackingState = camera.getTrackingState();

      // Notify the cloudManager of all the updates.
      cloudManager.onUpdate(updatedAnchors);

      // Handle user input.
        synchronized (singleTapLock) {
            summaryString += ",noTapLock";
            synchronized (anchorLock) {
                summaryString += ",noALock";

                // Only handle a tap if the anchor is currently null, the queued tap is non-null and the
                // camera is currently tracking.
                if (queuedSingleTap != null && cameraTrackingState == TrackingState.TRACKING) {
                    for (HitResult hit : frame.hitTest(queuedSingleTap)) {
                        if (shouldCreateAnchorWithHit(hit)) {
                          if (anchor == null) { // We are creating the treasure
                            summaryString += ",CREATED NEW ANCHOR";
                            snackbarHelper.showMessageWithAction(CreateTreasureActivity.this, getString(R.string.treasure_placed),this);
                          }
                          else{
                            summaryString += ",REPOSITIONED ANCHOR";
                            snackbarHelper.showMessageWithAction(CreateTreasureActivity.this,getString(R.string.treasure_relocated),this);
                          }
                          creationState = CreationState.TREASURE_PLACED;
                          Anchor newAnchor = hit.createAnchor();
                          setNewAnchor(newAnchor);
                          queuedSingleTap = null;
                          takePicture = true;

                          break;

                        }
                    }
                    summaryString += ",iterated hits";

                }
            }
            queuedSingleTap = null;
        }
      // Draw background.
      backgroundRenderer.draw(frame);

      // If not tracking, don't draw 3d objects.
      if (cameraTrackingState == TrackingState.PAUSED) {
          summaryString+="TRACKING_PAUSED_NO_DRAW";
          mLogger.logInfo(summaryString);
          return;
      }

      // Get camera and projection matrices.
      camera.getViewMatrix(viewMatrix, 0);
      camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f);

      // Visualize tracked points.
      PointCloud pointCloud = frame.acquirePointCloud();
      pointCloudRenderer.update(pointCloud);
      pointCloudRenderer.draw(viewMatrix, projectionMatrix);

      // Application is responsible for releasing the point cloud resources after using it.
      pointCloud.release();

      // Visualize planes.
      Collection<Plane> myPlanes = session.getAllTrackables(Plane.class);
      if (!takePicture) {
          planeRenderer.drawPlanes(myPlanes, camera.getDisplayOrientedPose(), projectionMatrix);
      }
      if (creationState == CreationState.NO_TREASURE_NO_PLANE &&  myPlanes.size() > 0) {
        creationState = CreationState.NO_TREASURE_BUT_PLANE;
        snackbarHelper.showMessageWithDismiss(CreateTreasureActivity.this,getString(R.string.treasure_planes_found));
      } else if (creationState == CreationState.NO_TREASURE_BUT_PLANE && myPlanes.size() == 0) {
        creationState = CreationState.NO_TREASURE_NO_PLANE;
        snackbarHelper.showMessageWithDismiss(CreateTreasureActivity.this,getString(R.string.treasure_planes_looking));
      }

      // Check if the anchor can be visualized or not, and get its pose if it can be.
      boolean shouldDrawAnchor = false;
      synchronized (anchorLock) {
        if (anchor != null && anchor.getTrackingState() == TrackingState.TRACKING) {
          // Get the current pose of an Anchor in world space. The Anchor pose is updated
          // during calls to session.update() as ARCore refines its estimate of the world.

          anchor.getPose().toMatrix(anchorMatrix, 0);
          shouldDrawAnchor = true;


        }
      }
        summaryString += ",drawing anchor: "+Boolean.toString(shouldDrawAnchor);
      // Visualize anchor.
      if (shouldDrawAnchor) {
          if (creationState == CreationState.TREASURE_PLACED ) {
              creationState = CreationState.TREASURE_MISSING;

              snackbarHelper.showMessageWithAction(CreateTreasureActivity.this, getString(R.string.treasure_relocated),this);

              //TBD: To Be removed after the share dialog is stable.
            /*this.runOnUiThread(new Runnable() {
              public void run() {
                onShareTreasure();
                //uploadTreasure();
              }
            });*/

          }
        float[] colorCorrectionRgba = new float[4];
        frame.getLightEstimate().getColorCorrection(colorCorrectionRgba, 0);

        // Update and draw the model and its shadow.
        virtualObject.updateModelMatrix(anchorMatrix, GlobalVariables.OBJECT_SCALE);
        virtualObjectShadow.updateModelMatrix(anchorMatrix, GlobalVariables.OBJECT_SCALE);
        virtualObject.draw(viewMatrix, projectionMatrix, colorCorrectionRgba);
        virtualObjectShadow.draw(viewMatrix, projectionMatrix, colorCorrectionRgba);
      } else if (CreationState.TREASURE_PLACED == creationState) {
          creationState = CreationState.TREASURE_MISSING;
          snackbarHelper.showMessageWithAction(CreateTreasureActivity.this, getString(R.string.treasure_trying_to_locate), this);
      }

        if (takePicture) {
            takePicture = false;
            treasureBitmap = getCurrentPicture();
            if (isTreasureEditDialogOpen) {
              this.runOnUiThread(new Runnable() {
                public void run() {
                  treasureImageView.setImageBitmap(treasureBitmap);
                  treasureImageProgressBar.setVisibility(View.GONE);
                  mLogger.logInfo("Dialog was open");
                }
              });
            }
            else{
                mLogger.logInfo("");
            }
        }
    } catch (Throwable t) {
      // Avoid crashing the application due to unhandled exceptions.
      Log.e(TAG, "Exception on the OpenGL thread", t);
    }

    summaryString+= ", Creation State: "+creationState.name();
    //mLogger.logInfo("S:"+summaryString); // TODO: comment out in production
  }

  /** Sets the new value of the current anchor. Detaches the old anchor, if it was non-null. */
  private void setNewAnchor(Anchor newAnchor) {
    synchronized (anchorLock) {
      if (anchor != null) {
        anchor.detach();
      }
      anchor = newAnchor;
    }
  }

  private void updateObjectRotation(boolean wasRotatedLeft, long duration) {
      mLogger.logInfo("Was rotated left:"+Boolean.toString(wasRotatedLeft)+" for ms:"+Long.toString(duration));

      float toAdd = 0.090f * ((int) duration % 4000L);
      if (wasRotatedLeft) {
          GlobalVariables.OBJECT_ROTATION += toAdd;
      }
      else{
          GlobalVariables.OBJECT_ROTATION -= toAdd;
      }

  }

  private Bitmap getCurrentPicture() throws IOException {
      int pixelData[] = new int[mWidth * mHeight];

      // Read the pixels from the current GL frame.
      IntBuffer buf = IntBuffer.wrap(pixelData);
      buf.position(0);
      GLES20.glReadPixels(0, 0, mWidth, mHeight,
              GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf);

      // Convert the pixel data from RGBA to what Android wants, ARGB.
      int bitmapData[] = new int[pixelData.length];
      for (int i = 0; i < mHeight; i++) {
          for (int j = 0; j < mWidth; j++) {
              int p = pixelData[i * mWidth + j];
              int b = (p & 0x00ff0000) >> 16;
              int r = (p & 0x000000ff) << 16;
              int ga = p & 0xff00ff00;
              bitmapData[(mHeight - i - 1) * mWidth + j] = ga | r | b;
          }
      }
      // Create a bitmap.
      Bitmap bmp = Bitmap.createBitmap(bitmapData,mWidth, mHeight, Bitmap.Config.ARGB_8888);
      return bmp;
  }


//endregion


  @Override
  public void onShareTreasure() {
    //todo add picture functionality, retrieve hint and letter content
    mLogger.logInfo("Treasure was shared");
    openTreasureDetailsDialog();
  }
  private void uploadTreasure() {
    hostListener = new RoomCodeAndCloudAnchorIdListener();
    firebaseManager.getNewRoomCode(hostListener);
    Preconditions.checkNotNull(hostListener, "The host listener cannot be null.");
    cloudManager.hostCloudAnchor(anchor, hostListener);

  }


  //region Dialogs
  private void openTreasureDetailsDialog() {
    final Dialog dialog = new Dialog(CreateTreasureActivity.this);

      isTreasureEditDialogOpen = true;
    //setting custom layout to dialog
    dialog.setContentView(R.layout.treasure_details_dialog);

    TextInputLayout letterInput = dialog.findViewById(R.id.letterLayout);
    if (treasureType == TreasureType.LETTER) {
      letterInput.setVisibility(View.VISIBLE);
    }
    treasureImageView = dialog.findViewById(R.id.treasureImageView);
    treasureImageProgressBar = dialog.findViewById(R.id.newPictureProgress);
    treasureImageView.setImageBitmap(treasureBitmap);

    Button takeNewPictureButton = dialog.findViewById(R.id.takeNewPictureButton);
    takeNewPictureButton.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (takePicture) {
                //keep waiting for picture to be set
            }
            else{
                takePicture = true;
                treasureImageProgressBar.setVisibility(View.VISIBLE);

            }
        }
    });
    Button createTreasure = dialog.findViewById(R.id.createTreasureButton);
    createTreasure.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
          String letter = "";
        mHuntNotification.setType("treasure");
          if (treasureType == TreasureType.LETTER) {
              letter = ((TextInputEditText) dialog.findViewById(R.id.letterEditText)).getText().toString();
              mHuntNotification.setNotificationMessage(letter);
              mHuntNotification.setType("letter");
          }
          String hint = ((TextInputEditText) dialog.findViewById(R.id.hintEditText)).getText().toString();
          mHuntNotification.setIdentifyHint(hint);
          mHuntNotification.setRotation(GlobalVariables.OBJECT_ROTATION);
          mHuntNotification.setScale(GlobalVariables.OBJECT_SCALE);
          BitmapDrawable drawable = (BitmapDrawable)((ImageView)dialog.findViewById(R.id.treasureImageView)).getDrawable();
          mHintImage = drawable.getBitmap();

          dialog.dismiss();
          uploadProgressLayout.setVisibility(View.VISIBLE);

          //todo actually upload anchor
          final Handler timerHandler = new Handler();
          Runnable timerRunnable = new Runnable() {
            @Override
            public void run() {
              uploadProgressLayout.setVisibility(View.GONE);
              uploadTreasure();
              openTreasureUploadedDialog();
            }
          };
          timerHandler.postDelayed(timerRunnable, 5000);
      }
    });
    dialog.show();
    dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
        @Override
        public void onDismiss(DialogInterface dialog) {
            isTreasureEditDialogOpen = false;
        }
    });
  }

  private void openTreasureUploadedDialog() {
    final Dialog dialog = new Dialog(CreateTreasureActivity.this);

    //setting custom layout to dialog
    dialog.setContentView(R.layout.treasure_uploaded_dialog);
    Button mainMenu = dialog.findViewById(R.id.mainMenuButton);
    mainMenu.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        finish();
      }
    });
    Button admireWork = dialog.findViewById(R.id.admireButton);
    admireWork.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        dialog.dismiss();
        creationState = CreationState.TREASURE_UPLOADED;
      }
    });
    dialog.show();

  }
  //endregion
  //region ARCOre sample methods
  /** Callback function invoked when the Host Button is pressed. */


  /** Callback function invoked when the Resolve Button is pressed. */


  /** Resets the mode of the app to its initial state and removes the anchors. */
  private void resetMode() {
    hostResolveMode = HostResolveMode.NONE;
    creationState = CreationState.NO_TREASURE_NO_PLANE;
    firebaseManager.clearRoomListener();
    hostListener = null;
    setNewAnchor(null);
    snackbarHelper.hide(this);
    cloudManager.clearListeners();
  }
  /** Callback function invoked when the user presses the OK button in the Resolve Dialog. */
  private void onRoomCodeEntered(Long roomCode) {
    hostResolveMode = HostResolveMode.RESOLVING;
    snackbarHelper.showMessageWithDismiss(this, getString(R.string.snackbar_on_resolve));

    // Register a new listener for the given room.
    firebaseManager.registerNewListenerForRoom(
        roomCode,
        (cloudAnchorId) -> {
          // When the cloud anchor ID is available from Firebase.
          cloudManager.resolveCloudAnchor(
              cloudAnchorId,
              (anchor) -> {
                // When the anchor has been resolved, or had a final error state.
                CloudAnchorState cloudState = anchor.getCloudAnchorState();
                if (cloudState.isError()) {
                  Log.w(
                      TAG,
                      "The anchor in room "
                          + roomCode
                          + " could not be resolved. The error state was "
                          + cloudState);
                  snackbarHelper.showMessageWithDismiss(
                      CreateTreasureActivity.this,
                      getString(R.string.snackbar_resolve_error, cloudState));
                  return;
                }
                snackbarHelper.showMessageWithDismiss(
                    CreateTreasureActivity.this, getString(R.string.snackbar_resolve_success));
                setNewAnchor(anchor);
              });
        });
  }

  /**
   * Listens for both a new room code and an anchor ID, and shares the anchor ID in Firebase with
   * the room code when both are available.
   */
  private final class RoomCodeAndCloudAnchorIdListener implements CloudAnchorManager.CloudAnchorListener, FirebaseManager.RoomCodeListener, FirebaseManager.StorageListener {

    private Long roomCode;
    private String cloudAnchorId;

    @Override
    public void onNewRoomCode(Long newRoomCode) {
      Preconditions.checkState(roomCode == null, "The room code cannot have been set before.");
      roomCode = newRoomCode;
      snackbarHelper.showMessageWithDismiss(
          CreateTreasureActivity.this, getString(R.string.snackbar_room_code_available));
      checkAndMaybeShare();
      synchronized (singleTapLock) {
        // Change hostResolveMode to HOSTING after receiving the room code (not when the 'Host' button
        // is tapped), to prevent an anchor being placed before we know the room code and able to
        // share the anchor ID.
        hostResolveMode = HostResolveMode.HOSTING;
      }
    }

    @Override
    public void onError(DatabaseError error) {
      Log.w(TAG, "A Firebase database error happened.", error.toException());
      snackbarHelper.showError(
          CreateTreasureActivity.this, getString(R.string.snackbar_firebase_error));
    }

    @Override
    public void onFetchNotificationsData() {
      Log.i(TAG, "Fetch notification from database.");
    }


    @Override
    public void onCloudTaskComplete(Anchor anchor) {
      CloudAnchorState cloudState = anchor.getCloudAnchorState();
      if (cloudState.isError()) {
        Log.e(TAG, "Error hosting a cloud anchor, state " + cloudState);
        snackbarHelper.showMessageWithDismiss(
            CreateTreasureActivity.this, getString(R.string.snackbar_host_error, cloudState));
        return;
      }
      Preconditions.checkState(
          cloudAnchorId == null, "The cloud anchor ID cannot have been set before.");
      cloudAnchorId = anchor.getCloudAnchorId();
      setNewAnchor(anchor);

      // Send out notification. Upload image to firebase storage and set the URL
      String imageFireUploadedPath = firebaseManager.uploadImageToStorage
              (roomCode+"_hint.jpg", mHintImage, hostListener);
      mHuntNotification.setNotificationImageurl(imageFireUploadedPath);
      Log.i(TAG, "Download initiated for this image");
      mHuntNotification.setRoomId(roomCode);
      mHuntNotification.setHostedAnchorId(cloudAnchorId);
      firebaseManager.sendUpstreamMessage(getApplicationContext(), mHuntNotification);
      Log.i(TAG, "Broadcast Cloud Anchor Notification: "+mHuntNotification);
      checkAndMaybeShare();

    }

    private void checkAndMaybeShare() {
      if (roomCode == null || cloudAnchorId == null) {
        return;
      }
      mLogger.logInfo(">>> RoomCode: "+roomCode+", CloudAnchorId:"+cloudAnchorId);
      mHuntNotification.setRoomId(roomCode);
      mHuntNotification.setHostedAnchorId(cloudAnchorId);
      firebaseManager.storeAnchorIdInRoom(mHuntNotification);
      mLogger.logInfo(">>> Store");
      snackbarHelper.showMessageWithDismiss(
          CreateTreasureActivity.this, getString(R.string.snackbar_cloud_id_shared, new Object[]{roomCode}));

       // Toast.makeText(getApplicationContext(),
       //         "RoomCode: "+roomCode+", CloudAnchorId:"+cloudAnchorId, Toast.LENGTH_LONG).show();

    }

    @Override
    public void onDownloadCompleteUrl(String imageUrl) {
      Log.i(TAG, "URL Download complete, "+imageUrl);
    }

    @Override
    public void onDownloadCompleteBitmap(Bitmap bitmap) {
      Log.i(TAG, "Bmp Download complete, "+bitmap.getHeight()+"x"+bitmap.getWidth());
      String filename = "pippo.png";
      File folder = new File(Environment.getExternalStorageDirectory().getPath()+"/Download/");
      //Bitmap bitmap = (Bitmap)data.getExtras().get("data");
      try {
        File dest = new File(folder, filename);
        FileOutputStream out = new FileOutputStream(dest);
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, out);
        out.flush();
        out.close();
      } catch (Exception e) {
        Log.e(TAG, e.getMessage());
      }
    }

    @Override
    public void onUploadCompleteUrl(String imageUrl) {
      // Download and test the image
      // firebaseManager.downloadImageFromStorage(roomCode+"_hint.jpg", hostListener);
    }

    @Override
    public void onError(String error) {
      Log.e(TAG, "Exception, "+error);
    }

  }

  //endregion
}
