package com.qxcloud.imageprocess.activity;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import com.qxcloud.imageprocess.ImageProcess;
import com.qxcloud.imageprocess.ResourceUtils;
import com.qxcloud.imageprocess.editAPI.EditImageAPI;
import com.qxcloud.imageprocess.editAPI.EditImageMessage;
import com.qxcloud.imageprocess.operate.CameraView;
import com.qxcloud.imageprocess.operate.FocusView;
import com.qxcloud.imageprocess.utils.Logger;
import com.qxcloud.imageprocess.utils.PermissionUtils;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;


/**
 * @Class: TakePhoteActivity
 * @Description: 拍照界面
 */
public class TakePhotoActivity extends FragmentActivity implements CameraBridgeViewBase.CvCameraViewListener2,
        CameraView.OnSavedListener {

    private static final String[] NEED_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    private String savedPath;
    CameraView mOpenCvCameraView;
    private CheckBox photograph;//闪关灯
    private Handler handler = new Handler();
    private String mAction;
    private FocusView focusView;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Logger.e("OpenCV loaded successfully");
                    if(mOpenCvCameraView != null){
                        mOpenCvCameraView.enableView();
                    }
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 设置横屏
//        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        // 设置全屏
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        checkPermission();
    }

    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= 23 &&
                !PermissionUtils.hasPermissions(
                        this, NEED_PERMISSIONS)) {
            PermissionUtils.requestPermissions(this,102,NEED_PERMISSIONS);
        } else {
            initView();
        }
    }

    private void initView() {
        setContentView(ResourceUtils.getIdByName(this, ResourceUtils.TYPE_LAYOUT, "activity_take_photo"));

        mAction = getIntent().getStringExtra(ImageProcess.EXTRA_DEFAULT_METHOD_ACTION);
        savedPath = getIntent().getStringExtra(ImageProcess.EXTRA_DEFAULT_SAVE_PATH);

        Logger.e("mAction --- "+mAction);

        mOpenCvCameraView = (CameraView) findViewById(ResourceUtils.getIdByName(this, ResourceUtils.TYPE_ID, "cameraPreview"));
        focusView = (FocusView) findViewById(ResourceUtils.getIdByName(this, ResourceUtils.TYPE_ID, "view_focus"));

        mOpenCvCameraView.setFocusListener(focusView);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);

        mOpenCvCameraView.setCvCameraViewListener(this);

        mOpenCvCameraView.setOnSavedListener(this);

        photograph = (CheckBox) findViewById(ResourceUtils.getIdByName(this, ResourceUtils.TYPE_ID, "photographs"));
        photograph.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Logger.e("启动闪光灯");
                mOpenCvCameraView.triggerFlash(isChecked);
            }
        });
    }


    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Logger.e("Internal OpenCV library not found. Using OpenCV Manager for initialization");
        } else {
            Logger.e("OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }


    public void close(View view) {
        EditImageAPI.getInstance().post(2, new EditImageMessage(1));
        finish();
    }

    public void takePhoto(View view) {
        if (mOpenCvCameraView != null) {
            mOpenCvCameraView.takePicture();
        }

    }

    @Override
    public void onCameraViewStarted(int width, int height) {
    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        return inputFrame.rgba();
    }

    @Override
    public void onSaved(byte[] data) {
        Logger.e("mAction --- "+mAction);
        Intent intent = new Intent(this, CropImgActivity.class);
        intent.putExtra(ImageProcess.EXTRA_DEFAULT_SAVE_PATH, savedPath);
        intent.putExtra(ImageProcess.EXTRA_DEFAULT_METHOD_ACTION, mAction);
        BitmapTransfer.transferBitmapData = data;
        startActivity(intent);
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults.length > 0) {
            boolean isGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    isGranted = false;
                    break;
                }
            }
            Logger.e("isGranted === " + isGranted + " --- " + grantResults.length);
            if (isGranted) {
                initView();
            } else {
                new AlertDialog.Builder(this)
                        .setMessage("此功能需要相机及存储权限，请前往设置打开")
                        .setNegativeButton("确认", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                handler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        EditImageAPI.getInstance().post(1, new EditImageMessage(1));
                                        finish();
                                    }
                                }, 1000);
                            }
                        })
                        .show();
            }
        }
    }

    @Override
    public void onBackPressed() {
        EditImageAPI.getInstance().post(2, new EditImageMessage(1));
        super.onBackPressed();
    }
}
