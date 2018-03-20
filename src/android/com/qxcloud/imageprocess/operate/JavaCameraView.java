package com.qxcloud.imageprocess.operate;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.os.Build;
import android.util.AttributeSet;
import android.view.ViewGroup;

import com.qxcloud.imageprocess.utils.Logger;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.List;

/**
 * This class is an implementation of the Bridge View between OpenCV and Java Camera.
 * This class relays on the functionality available in base class and only implements
 * required functions:
 * connectCamera - opens Java camera and sets the PreviewCallback to be delivered.
 * disconnectCamera - closes the camera and stops preview.
 * When frame is delivered via callback from Camera - it processed via OpenCV to be
 * converted to RGBA32 and then passed to the external callback for modifications if required.
 */
public class JavaCameraView extends CameraBridgeViewBase implements PreviewCallback {

    private static final int MAGIC_TEXTURE_ID = 10;
    private static final String TAG = "JavaCameraView";

    private byte mBuffer[];
    private Mat[] mFrameChain;
    private int mChainIdx = 0;
    private Thread mThread;
    private boolean mStopThread;

    protected Camera mCamera;
    protected JavaCameraFrame[] mCameraFrame;
    private SurfaceTexture mSurfaceTexture;
    private int mPreviewFormat = ImageFormat.NV21;

    public static class JavaCameraSizeAccessor implements ListItemAccessor {

        @Override
        public int getWidth(Object obj) {
            Camera.Size size = (Camera.Size) obj;
            return size.width;
        }

        @Override
        public int getHeight(Object obj) {
            Camera.Size size = (Camera.Size) obj;
            return size.height;
        }
    }

    public JavaCameraView(Context context, int cameraId) {
        super(context, cameraId);
    }

    public JavaCameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    protected boolean initializeCamera(int width, int height) {
        Logger.e("Initialize java camera");
        boolean result = true;
        synchronized (this) {
            mCamera = null;

            if (mCameraIndex == CAMERA_ID_ANY) {
                Logger.e("Trying to open camera with old open()");
                try {
                    mCamera = Camera.open();
                } catch (Exception e) {
                    Logger.e("Camera is not available (in use or does not exist): " + e.getLocalizedMessage());
                }


                if (mCamera == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
                    boolean connected = false;
                    for (int camIdx = 0; camIdx < Camera.getNumberOfCameras(); ++camIdx) {
                        Logger.e("Trying to open camera with new open(" + Integer.valueOf(camIdx) + ")");
                        try {
                            mCamera = Camera.open(camIdx);
                            connected = true;
                        } catch (RuntimeException e) {
                            Logger.e("Camera #" + camIdx + "failed to open: " + e.getLocalizedMessage());
                        }
                        if (connected) break;
                    }
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
                    int localCameraIndex = mCameraIndex;
                    if (mCameraIndex == CAMERA_ID_BACK) {
                        Logger.e("Trying to open back camera");
                        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
                        for (int camIdx = 0; camIdx < Camera.getNumberOfCameras(); ++camIdx) {
                            Camera.getCameraInfo(camIdx, cameraInfo);
                            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                                localCameraIndex = camIdx;
                                break;
                            }
                        }
                    } else if (mCameraIndex == CAMERA_ID_FRONT) {
                        Logger.e("Trying to open front camera");
                        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
                        for (int camIdx = 0; camIdx < Camera.getNumberOfCameras(); ++camIdx) {
                            Camera.getCameraInfo(camIdx, cameraInfo);
                            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                                localCameraIndex = camIdx;
                                break;
                            }
                        }
                    }
                    if (localCameraIndex == CAMERA_ID_BACK) {
                        Logger.e("Back camera not found!");
                    } else if (localCameraIndex == CAMERA_ID_FRONT) {
                        Logger.e("Front camera not found!");
                    } else {
                        Logger.e("Trying to open camera with new open(" + Integer.valueOf(localCameraIndex) + ")");
                        try {
                            mCamera = Camera.open(localCameraIndex);
                        } catch (RuntimeException e) {
                            Logger.e("Camera #" + localCameraIndex + "failed to open: " + e.getLocalizedMessage());
                        }
                    }
                }
            }

            if (mCamera == null)
                return false;

            /* Now set camera parameters */
            try {
                Camera.Parameters params = mCamera.getParameters();
                Logger.e("getSupportedPreviewSizes()");
                List<Camera.Size> previewSizes = params.getSupportedPreviewSizes();
                List<Camera.Size> pictureSizes = params.getSupportedPictureSizes();

                if (previewSizes != null && pictureSizes != null) {
                    /* Select the size that fits surface considering maximum size allowed */
                    Size frameSize = calculateCameraFrameSize(previewSizes, new JavaCameraSizeAccessor());
                    Size pictureSize = calculateCameraFrameSize(pictureSizes, new JavaCameraSizeAccessor());
                    /* Image format NV21 causes issues in the Android emulators */
                    if (Build.FINGERPRINT.startsWith("generic")
                            || Build.FINGERPRINT.startsWith("unknown")
                            || Build.MODEL.contains("google_sdk")
                            || Build.MODEL.contains("Emulator")
                            || Build.MODEL.contains("Android SDK built for x86")
                            || Build.MANUFACTURER.contains("Genymotion")
                            || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                            || "google_sdk".equals(Build.PRODUCT))
                        params.setPreviewFormat(ImageFormat.YV12);  // "generic" or "android" = android emulator
                    else
                        params.setPreviewFormat(ImageFormat.NV21);

                    mPreviewFormat = params.getPreviewFormat();

                    if(frameSize!=null){
                        Logger.e("Set preview size to " + Integer.valueOf((int) frameSize.width) + "x" + Integer.valueOf((int) frameSize.height));
                        params.setPreviewSize((int) frameSize.width, (int) frameSize.height);
                    }

                    if(pictureSize != null){
                        Logger.e("Set picture size to " + Integer.valueOf((int) pictureSize.width) + "x" + Integer.valueOf((int) pictureSize.height));
                        params.setPictureSize((int) pictureSize.width, (int) pictureSize.height);
                    }


                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH && !Build.MODEL.equals("GT-I9100"))
                        params.setRecordingHint(true);

                    List<String> FocusModes = params.getSupportedFocusModes();
                    if (FocusModes != null && FocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                        params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                    }

                    mCamera.setParameters(params);
                    params = mCamera.getParameters();

                    mFrameWidth = params.getPreviewSize().width;
                    mFrameHeight = params.getPreviewSize().height;

                    if ((getLayoutParams().width == ViewGroup.LayoutParams.MATCH_PARENT)
                            && (getLayoutParams().height == ViewGroup.LayoutParams.MATCH_PARENT))
                        mScale = Math.min(((float) height) / mFrameHeight, ((float) width) / mFrameWidth);
                    else
                        mScale = 0;

                    Logger.e("mScale === "+mScale);

                    if (mFpsMeter != null) {
                        mFpsMeter.setResolution(mFrameWidth, mFrameHeight);
                    }

                    int size = mFrameWidth * mFrameHeight;
                    size = size * ImageFormat.getBitsPerPixel(params.getPreviewFormat()) / 8;
                    mBuffer = new byte[size];

                    mCamera.addCallbackBuffer(mBuffer);
                    mCamera.setPreviewCallbackWithBuffer(this);

                    mFrameChain = new Mat[2];
                    mFrameChain[0] = new Mat(mFrameHeight + (mFrameHeight / 2), mFrameWidth, CvType.CV_8UC1);
                    mFrameChain[1] = new Mat(mFrameHeight + (mFrameHeight / 2), mFrameWidth, CvType.CV_8UC1);

                    AllocateCache();

                    mCameraFrame = new JavaCameraFrame[2];
                    mCameraFrame[0] = new JavaCameraFrame(mFrameChain[0], mFrameWidth, mFrameHeight);
                    mCameraFrame[1] = new JavaCameraFrame(mFrameChain[1], mFrameWidth, mFrameHeight);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                        mSurfaceTexture = new SurfaceTexture(MAGIC_TEXTURE_ID);
                        mCamera.setPreviewTexture(mSurfaceTexture);
                    } else
                        mCamera.setPreviewDisplay(null);

                    /* Finally we are ready to start the preview */
                    Logger.e("startPreview");
                    mCamera.startPreview();
                } else
                    result = false;
            } catch (Exception e) {
                result = false;
                e.printStackTrace();
            }
        }

        return result;
    }

    protected void releaseCamera() {
        synchronized (this) {
            if (mCamera != null) {
                try {
                    mCamera.stopPreview();
                    mCamera.setPreviewCallback(null);

                    mCamera.release();
                }catch (Exception e){
                    Logger.e("camera release exception "+e.getMessage());
                }
            }
            mCamera = null;
            if (mFrameChain != null) {
                mFrameChain[0].release();
                mFrameChain[1].release();
            }
            if (mCameraFrame != null) {
                mCameraFrame[0].release();
                mCameraFrame[1].release();
            }
        }
    }

    private boolean mCameraFrameReady = false;

    @Override
    protected boolean connectCamera(int width, int height) {

        /* 1. We need to instantiate camera
         * 2. We need to start thread which will be getting frames
         */
        /* First step - initialize camera connection */
        Logger.e("Connecting to camera");
        if (!initializeCamera(width, height))
            return false;

        mCameraFrameReady = false;

        /* now we can start update thread */
        Logger.e("Starting processing thread");
        mStopThread = false;
        mThread = new Thread(new CameraWorker());
        mThread.start();

        return true;
    }

    @Override
    protected void disconnectCamera() {
        /* 1. We need to stop thread which updating the frames
         * 2. Stop camera and release it
         */
        Logger.e("Disconnecting from camera");
        try {
            mStopThread = true;
            Logger.e("Notify thread");
            synchronized (this) {
                this.notify();
            }
            Logger.e("Waiting for thread");
            if (mThread != null)
                mThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            mThread = null;
        }

        /* Now release camera */
        releaseCamera();

        mCameraFrameReady = false;
    }

    @Override
    public void onPreviewFrame(byte[] frame, Camera arg1) {
//        if (BuildConfig.DEBUG)
//            Logger.e("Preview Frame received. Frame size: " + frame.length);
        synchronized (this) {
            mFrameChain[mChainIdx].put(0, 0, frame);
            mCameraFrameReady = true;
            this.notify();
        }
        if (mCamera != null)
            mCamera.addCallbackBuffer(mBuffer);
    }

    private class JavaCameraFrame implements CvCameraViewFrame {
        @Override
        public Mat gray() {
            return mYuvFrameData.submat(0, mHeight, 0, mWidth);
        }

        @Override
        public Mat rgba() {
            if (mPreviewFormat == ImageFormat.NV21)
                Imgproc.cvtColor(mYuvFrameData, mRgba, Imgproc.COLOR_YUV2RGBA_NV21, 4);
            else if (mPreviewFormat == ImageFormat.YV12)
                Imgproc.cvtColor(mYuvFrameData, mRgba, Imgproc.COLOR_YUV2RGB_I420, 4);  // COLOR_YUV2RGBA_YV12 produces inverted colors
            else
                throw new IllegalArgumentException("Preview Format can be NV21 or YV12");

            return mRgba;
        }

        public JavaCameraFrame(Mat Yuv420sp, int width, int height) {
            super();
            mWidth = width;
            mHeight = height;
            mYuvFrameData = Yuv420sp;
            mRgba = new Mat();
        }

        public void release() {
            mRgba.release();
        }

        private Mat mYuvFrameData;
        private Mat mRgba;
        private int mWidth;
        private int mHeight;
    }

    ;

    private class CameraWorker implements Runnable {

        @Override
        public void run() {
            do {
                boolean hasFrame = false;
                synchronized (JavaCameraView.this) {
                    try {
                        while (!mCameraFrameReady && !mStopThread) {
                            JavaCameraView.this.wait();
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (mCameraFrameReady) {
                        mChainIdx = 1 - mChainIdx;
                        mCameraFrameReady = false;
                        hasFrame = true;
                    }
                }

                if (!mStopThread && hasFrame) {
                    if (!mFrameChain[1 - mChainIdx].empty())
                        deliverAndDrawFrame(mCameraFrame[1 - mChainIdx]);
                }
            } while (!mStopThread);
            Logger.e("Finish processing thread");
        }
    }

//    public Size calculateCameraPictureSize(List<?> supportedSizes, ListItemAccessor accessor){
//        int calcWidth = 0;
//        int calcHeight = 0;
//
//        int maxAllowedWidth = 1920;
//        int maxAllowedHeight = 1080;
//        float aspectRatio = (float)(Math.round(((float)maxAllowedWidth/(float)maxAllowedHeight)*100))/100;
//
//        for (Object size : supportedSizes) {
//            int width = accessor.getWidth(size);
//            int height = accessor.getHeight(size);
//            float sizeAspectRatio = (float)(Math.round(((float)width/(float)height)*100))/100;
//            Logger.e("width -- "+width+" height = "+height+" sizeAspectRatio "+sizeAspectRatio +" aspectRatio = "+aspectRatio);
//            if (width <= maxAllowedWidth && height <= maxAllowedHeight && sizeAspectRatio == aspectRatio) {
//                if (width >= calcWidth && height >= calcHeight) {
//                    calcWidth = (int) width;
//                    calcHeight = (int) height;
//                }
//            }
//        }
//
//        Logger.e("calcWidth = "+calcWidth+" calcHeight = "+calcHeight);
//
//        return new Size(calcWidth,calcHeight);
//    }

    protected Size calculateCameraFrameSize(List<?> supportedSizes, ListItemAccessor accessor) {
        int calcWidth = 0;
        int calcHeight = 0;

        int maxAllowedWidth = 1920;
        int maxAllowedHeight = 1080;
        float aspectRatio = (float)(Math.round(((float)maxAllowedWidth/(float)maxAllowedHeight)*100))/100;

        for (Object size : supportedSizes) {
            int width = accessor.getWidth(size);
            int height = accessor.getHeight(size);
            float sizeAspectRatio = (float)(Math.round(((float) width/(float) height)*100))/100;
            Logger.e("width -- "+width+" height = "+height+" sizeAspectRatio "+sizeAspectRatio +" aspectRatio = "+aspectRatio);
            if (width <= maxAllowedWidth && height <= maxAllowedHeight && sizeAspectRatio == aspectRatio) {
                if (width >= calcWidth && height >= calcHeight) {
                    calcWidth = (int) width;
                    calcHeight = (int) height;
                }
            }
        }

        Logger.e("calcWidth = "+calcWidth+" calcHeight = "+calcHeight);
        if(calcWidth > 0 && calcHeight > 0){
            return new Size(calcWidth,calcHeight);
        }
        return null;
    }
}
