package com.google.ar.core.examples.java.cloudanchor;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

public class ScaleGestures implements View.OnTouchListener,ScaleGestureDetector.OnScaleGestureListener {
    private ScaleGestureDetector scaleGestureDetector;
    private float scale = 1;
    private Logger mLogger;
    private CreateTreasureActivity.TreasureType treasureType;

    public ScaleGestures(Context context, CreateTreasureActivity.TreasureType treasureType){
        scaleGestureDetector = new ScaleGestureDetector(context, this);
        mLogger = new Logger("ScaleGestures");
        this.treasureType = treasureType;

    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {

        float newScale = -1;
        if (treasureType == CreateTreasureActivity.TreasureType.LETTER) {
            scale = GlobalVariables.OBJECT_SCALE - (1.0f-detector.getScaleFactor());
            scale = ((float) ((int) (scale * 100))) / 100;
            scale = Math.max(0.05f, Math.min(scale, 0.5f));
            GlobalVariables.OBJECT_SCALE = scale;
            mLogger.logInfo("Previous scale:"+Float.toString(GlobalVariables.OBJECT_SCALE)+", detector:"+Float.toString(detector.getScaleFactor()) + ", scale:"+scale);

        } else if (treasureType == CreateTreasureActivity.TreasureType.TREASURE_CHEST) {
            scale *= detector.getScaleFactor();

            scale = (scale < 1 ? 1 : scale);
            scale = ((float) ((int) (scale * 100))) / 100;
            newScale =  Math.max(0.1f, Math.min(scale, 5.0f));


            scale = newScale;
            GlobalVariables.OBJECT_SCALE = newScale;
        }
        return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {

    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        //mLogger.logInfo("onTouch");

        scaleGestureDetector.onTouchEvent(event);
        return false;
    }
}
