package com.qxcloud.imageprocess.operate;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;

import com.qxcloud.imageprocess.utils.Logger;

import java.util.ArrayList;
import java.util.List;


public class CameraView extends JavaCameraView implements PictureCallback,Camera.AutoFocusCallback {

    private OnSavedListener onSavedListener;
    private FocusListener focusListener;

    public void setOnSavedListener(OnSavedListener onSavedListener) {
        this.onSavedListener = onSavedListener;
    }
    public void setFocusListener(FocusListener focusListener) {
        this.focusListener = focusListener;
    }

    public CameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    public void takePicture() {
        // Postview and jpeg are sent in the same buffers if the queue is not empty when performing a capture.
        // Clear up buffers to avoid mCamera.takePicture to be stuck because of a memory issue
        mCamera.setPreviewCallback(null);

        // PictureCallback is implemented by the current class
        mCamera.takePicture(null, null, this);
    }

    public void triggerFlash(boolean on){
        Camera.Parameters parameters = mCamera.getParameters();
        if(on){
            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
        }else{
            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        }

        mCamera.setParameters(parameters);
    }


    @Override
    public void onPictureTaken(byte[] data, Camera camera) {
        Logger.e("Saving a bitmap to file");
        // The camera preview was automatically stopped. Start it again.
//        mCamera.startPreview();
//        mCamera.setPreviewCallback(this);
        Bitmap bmp = BitmapFactory.decodeByteArray(data,0,data.length);
        Logger.e("bmp === "+data.length/1024+" w = "+bmp.getWidth()+" h = "+bmp.getHeight());
        // Write the image in a file (in jpeg format)

        if(onSavedListener != null){
            onSavedListener.onSaved(data);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()){
            case MotionEvent.ACTION_DOWN:
                focusOnTouch(event);
                float x = event.getX();
                float y = event.getY();
                if(focusListener != null && !focusListener.isFocusing()){
                    focusListener.beginFocus(x,y);
                }
                break;
        }
        return false;
    }

    public void focusOnTouch(MotionEvent event) {
        Rect focusRect = calculateTapArea(event.getRawX(), event.getRawY(), 1f);
        Rect meteringRect = calculateTapArea(event.getRawX(), event.getRawY(), 1.5f);

        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);

        if (parameters.getMaxNumFocusAreas() > 0) {
            List<Camera.Area> focusAreas = new ArrayList<Camera.Area>();
            focusAreas.add(new Camera.Area(focusRect, 1000));

            parameters.setFocusAreas(focusAreas);
        }

        if (parameters.getMaxNumMeteringAreas() > 0) {
            List<Camera.Area> meteringAreas = new ArrayList<Camera.Area>();
            meteringAreas.add(new Camera.Area(meteringRect, 1000));

            parameters.setMeteringAreas(meteringAreas);
        }

        mCamera.setParameters(parameters);
        mCamera.autoFocus(this);
    }

    public Camera.Size getResolution() {
        Camera.Parameters params = mCamera.getParameters();
        Camera.Size s = params.getPreviewSize();
        return s;
    }

    /**
     * Convert touch position x:y to {@link Camera.Area} position -1000:-1000 to 1000:1000.
     */
    private Rect calculateTapArea(float x, float y, float coefficient) {
        float focusAreaSize = 300;
        int areaSize = Float.valueOf(focusAreaSize * coefficient).intValue();

        int centerX = (int) (x / getResolution().width * 2000 - 1000);
        int centerY = (int) (y / getResolution().height * 2000 - 1000);

        int left = clamp(centerX - areaSize / 2, -1000, 1000);
        int right = clamp(left + areaSize, -1000, 1000);
        int top = clamp(centerY - areaSize / 2, -1000, 1000);
        int bottom = clamp(top + areaSize, -1000, 1000);

        return new Rect(left, top, right, bottom);
    }

    private int clamp(int x, int min, int max) {
        if (x > max) {
            return max;
        }
        if (x < min) {
            return min;
        }
        return x;
    }

    @Override
    public void onAutoFocus(boolean success, Camera camera) {
        if (success) {
            camera.cancelAutoFocus();// 只有加上了这一句，才会自动对焦
            doAutoFocus();
        }
    }
    private void doAutoFocus() {
        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        mCamera.setParameters(parameters);
        mCamera.autoFocus(new Camera.AutoFocusCallback() {
            @Override
            public void onAutoFocus(boolean success, Camera camera) {
                if (success) {
                    camera.cancelAutoFocus();// 只有加上了这一句，才会自动对焦。
                    if (!Build.MODEL.equals("KORIDY H30")) {
                        Camera.Parameters parameters = camera.getParameters();
                        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);// 1连续对焦
                        camera.setParameters(parameters);
                    }else{
                        Camera.Parameters parameters = camera.getParameters();
                        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                        camera.setParameters(parameters);
                    }
                }
            }
        });
    }

    public static interface OnSavedListener{
        void onSaved(byte[] data);
    }
    public static interface FocusListener{
        void beginFocus(float x,float y);
        boolean isFocusing();
    }
}
