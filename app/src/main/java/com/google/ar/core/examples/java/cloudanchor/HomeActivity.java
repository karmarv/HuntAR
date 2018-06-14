package com.google.ar.core.examples.java.cloudanchor;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.GuardedBy;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.ar.core.Anchor;
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
import com.google.ar.core.examples.java.common.rendering.BackgroundRenderer;
import com.google.ar.core.examples.java.common.rendering.ObjectRenderer;
import com.google.ar.core.examples.java.common.rendering.ObjectRenderer.BlendMode;
import com.google.ar.core.examples.java.common.rendering.PlaneRenderer;
import com.google.ar.core.examples.java.common.rendering.PointCloudRenderer;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


public class HomeActivity extends AppCompatActivity implements GLSurfaceView.Renderer {
    private static final String TAG = CreateTreasureActivity.class.getSimpleName();
    private Button startHuntButton;
    private Button createTreasureButton;
    private Button openCollectionButton;
    private enum HostResolveMode {
        NONE,
        HOSTING,
        RESOLVING,
    }
    private enum CreateTreasureMode{
        NONE,
        NO_TREASURE_NO_PLANE,
        NO_TREASURE_BUT_PLANE,
        TREASURE_PLACED,
        TREASURE_UPLOADING,
        TREASURE_UPLOADED,
        LOOKING_FOR_SURFACE,        //Show snackbar with info on how to find surfaces
        PLACING_TREASURE_ON_SURFACE, //Show snackbar with info on how to place treasure.
        // Draw planes
        // Place treasure where camera is pointing
        UPLOADING_TREASURE,         //Don't draw planes
        // Draw treasure
        // Show UI with progress thingie "Creating treasure"
        UPLOADED_TREASURE, //Show snackbar "Do a spin and try finding your treasure again!"
        // Reset anchor knowledge on button click
        // Draw plan
    }


    // Rendering. The Renderers are created here, and initialized when the GL surface is created.
    private GLSurfaceView surfaceView;
    private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();
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

    @GuardedBy("singleTapLock")
    private MotionEvent queuedSingleTap;

    private Session session;

    @GuardedBy("anchorLock")
    private Anchor anchor;

    // Cloud Anchor Components.
    private FirebaseManager firebaseManager;
    private final CloudAnchorManager cloudManager = new CloudAnchorManager();
    private HostResolveMode currentMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        startHuntButton = findViewById(R.id.startHuntingButton);
        startHuntButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openTreasureHuntDialog();
                //Toast.makeText(getApplicationContext(), "Run start hunting", Toast.LENGTH_LONG).show();
            }
        });
        createTreasureButton = findViewById(R.id.createTreasureButton);
        createTreasureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openTreasureTypeDialog();
            }

        });
        openCollectionButton = findViewById(R.id.openCollectionButton);
        openCollectionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });
        surfaceView = findViewById(R.id.surfaceview);
        displayRotationHelper = new DisplayRotationHelper(this);

        // Set up tap listener.
        gestureDetector =
                new GestureDetector(
                        this,
                        new GestureDetector.SimpleOnGestureListener() {
                            @Override
                            public boolean onSingleTapUp(MotionEvent e) {
                                synchronized (singleTapLock) {
                                    if (currentMode == HostResolveMode.HOSTING) {
                                        queuedSingleTap = e;
                                    }
                                }
                                return true;
                            }

                            @Override
                            public boolean onDown(MotionEvent e) {
                                return true;
                            }
                        });
        surfaceView.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));

        // Set up renderer.
        surfaceView.setPreserveEGLContextOnPause(true);
        surfaceView.setEGLContextClientVersion(2);
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
        surfaceView.setRenderer(this);
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        installRequested = false;

        // Initialize Cloud Anchor variables.
        firebaseManager = new FirebaseManager(this);
        currentMode = HostResolveMode.NONE;
    }

    private void openTreasureHuntDialog(){
        final Dialog dialog = new Dialog(HomeActivity.this);
        //setting custom layout to dialog
        dialog.setContentView(R.layout.hunt_type_picture_dialog);

        // Load image on the hint view
        ImageView img = (ImageView) dialog.findViewById(R.id.treasureHintImageView);
        img.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { // set the image
                Log.i(TAG, "Click on the image view");
                File imgFile = new File(Environment.getExternalStorageDirectory().getPath()+"/Download/pippo.png");
                if(imgFile.exists()){
                    Bitmap myBitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
                    ImageView myImage = (ImageView) dialog.findViewById(R.id.treasureHintImageView);
                    myImage.setImageBitmap(myBitmap);
                }
            }
        });

        ImageButton huntDialogButton = dialog.findViewById(R.id.startHuntDialogImgButton);
        huntDialogButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(HomeActivity.this, HuntTreasureActivity.class);
                intent.putExtra("type", "treasure");
                startActivity(intent);
                dialog.dismiss();
            }
        });

        ImageButton navigateMapsButton = dialog.findViewById(R.id.startMapNavButton);
        navigateMapsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Intent intent = new Intent(HomeActivity.this, CreateTreasureActivity.class);
                //intent.putExtra("type", "letter");
                Intent intent = new Intent(android.content.Intent.ACTION_VIEW,
                        Uri.parse("http://maps.google.com/maps?saddr=20.344,34.34&daddr=20.5666,45.345"));
                startActivity(intent);
                dialog.dismiss();

            }
        });
        dialog.show();

    }


    private void openTreasureTypeDialog() {
        final Dialog dialog = new Dialog(HomeActivity.this);
        //setting custom layout to dialog
        dialog.setContentView(R.layout.treasure_type_picture_dialog);
        ImageButton createChestButton = dialog.findViewById(R.id.treasureChestButton);
        createChestButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(HomeActivity.this, CreateTreasureActivity.class);
                intent.putExtra("type", "treasure");
                startActivity(intent);
                dialog.dismiss();



            }
        });
        ImageButton createLetterButton = dialog.findViewById(R.id.letterButton);
        createLetterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(HomeActivity.this, CreateTreasureActivity.class);
                intent.putExtra("type", "letter");
                startActivity(intent);
                dialog.dismiss();

            }
        });
        dialog.show();
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
                if (!CameraPermissionHelper.hasCameraPermission(this)) {
                    CameraPermissionHelper.requestCameraPermission(this);
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

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
    }

    /**
     * Handles the most recent user tap.
     *
     * <p>We only ever handle one tap at a time, since this app only allows for a single anchor.
     *
     * @param frame the current AR frame
     * @param cameraTrackingState the current camera tracking state
     */
    private void handleTap(Frame frame, TrackingState cameraTrackingState) {
        // Handle taps. Handling only one tap per frame, as taps are usually low frequency
        // compared to frame rate.
    }

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
        } catch (IOException ex) {
            Log.e(TAG, "Failed to read an asset file", ex);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        displayRotationHelper.onSurfaceChanged(width, height);
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // Clear screen to notify driver it should not load any pixels from previous frame.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        if (session == null) {
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
            handleTap(frame, cameraTrackingState);

            // Draw background.
            backgroundRenderer.draw(frame);

            // If not tracking, don't draw 3d objects.
            if (cameraTrackingState == TrackingState.PAUSED) {
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
            planeRenderer.drawPlanes(
                    session.getAllTrackables(Plane.class), camera.getDisplayOrientedPose(), projectionMatrix);
        } catch (Throwable t) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e(TAG, "Exception on the OpenGL thread", t);
        }
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



    /** Resets the mode of the app to its initial state and removes the anchors. */
    private void resetMode() {
        currentMode = HostResolveMode.NONE;
        firebaseManager.clearRoomListener();
        setNewAnchor(null);
        snackbarHelper.hide(this);
        cloudManager.clearListeners();
    }

}
