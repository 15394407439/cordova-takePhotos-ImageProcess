package com.qxcloud.imageprocess.utils;

import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.Parameters;


/**
 * Created by cfh on 2017-05-10.
 * 拍照工具类
 */

public class CameraHelper {

    private static CameraHelper helper;
    private Camera camera;

    //	闪光灯模式(default：自动)
    private String flashlightStatus = Parameters.FLASH_MODE_OFF;


    private CameraHelper() {
    }

    public static synchronized CameraHelper getInstance() {
        if (helper == null) {
            helper = new CameraHelper();
        }
        return helper;
    }


    /**
     * @param mIsOpenFlashMode
     * @Description: 设置开启闪光灯(重新预览)
     * @Since:2015-8-12
     * @Version:1.1.0
     */
    public void setIsOpenFlashMode(boolean mIsOpenFlashMode) {
        try {
            if (null == this.camera) {
                return;
            }
            if (!mIsOpenFlashMode) {
                //要求关闭
                if (null == this.camera.getParameters().getFlashMode() || this.camera.getParameters().getFlashMode().equals(Parameters.FLASH_MODE_OFF)) {
                    //不做处理
                    return;
                } else {
                    this.flashlightStatus = Parameters.FLASH_MODE_OFF;
                }
            } else {
                //要求打开
                this.flashlightStatus = Parameters.FLASH_MODE_TORCH;
            }
            Parameters p = this.camera.getParameters();
            p.setFlashMode(this.flashlightStatus);
            this.camera.setParameters(p);
            this.startPreview();
        } catch (Exception e) {

        }
    }

    private boolean isPreviewing;

    /**
     * 开始预览
     */
    public void startPreview() {
        try {
            if (this.camera != null) {
                this.camera.startPreview();
                autoFocus();
                this.isPreviewing = true;
            }
        } catch (Exception e) {

        }
    }


    /**
     * 相机聚焦
     */
    public void autoFocus() {
        try {
            if (null != this.camera) {
                this.camera.autoFocus(new AutoFocusCallback() {
                    @Override
                    public void onAutoFocus(boolean success, Camera camera) {
                        if (success) {
                            //聚焦成功
//                            Logger.e("聚焦成功");
                        } else {
                            //聚焦失败
//                            Logger.e("聚焦失败");
                        }
                    }
                });
            }
        } catch (Exception e) {

        }
    }
}

