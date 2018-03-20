package com.qxcloud.imageprocess.utils;

import android.graphics.Bitmap;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

/**
 * CREATED BY:         heaton
 * CREATED DATE:       2017/9/11
 * CREATED TIME:       下午3:54
 * CREATED DESCRIPTION:
 */

public class OpenCVUtils {
    static {
        if(!OpenCVLoader.initDebug()){
            Logger.e("OpenCV 加载失败");
        }
    }

    public static Bitmap threshold(Bitmap bmp, int blockSize, double C){
        return threshold(bmp,blockSize,C,false,0);
    }
    public static Bitmap threshold(Bitmap bmp, int blockSize, double C,boolean isMediaBlur){
        return threshold(bmp,blockSize,C,isMediaBlur,3);
    }

    public static Bitmap threshold(Bitmap bmp, int blockSize, double C,boolean isMediaBlur,int blurBlockSize){
        Logger.e("threshold bmp size = "+bmp.getByteCount()/8/1024+" width = "+bmp.getWidth()+" height = "+bmp.getHeight());
        double max = 255D;
        Mat srcMat = new Mat();
        Mat grayMat = new Mat();
        Mat thresholdMat= new Mat();
        Mat dstMat = new Mat();
        Utils.bitmapToMat(bmp,srcMat);
        Imgproc.cvtColor(srcMat,grayMat,Imgproc.COLOR_RGBA2GRAY);
//        srcMat.release();
        Imgproc.adaptiveThreshold(
                grayMat,
                thresholdMat,
                max,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                blockSize,
                C);
//        grayMat.release();
        if(isMediaBlur){
            Imgproc.medianBlur(thresholdMat,dstMat,blurBlockSize);
        }else{
            dstMat = thresholdMat;
        }
//        thresholdMat.release();

        Bitmap dstBmp = Bitmap.createBitmap(bmp.getWidth(),bmp.getHeight(), Bitmap.Config.RGB_565);
        Utils.matToBitmap(dstMat,dstBmp);
//        dstMat.release();
        Logger.e("dstBmp size = "+dstBmp.getByteCount()/8/1024+" width = "+dstBmp.getWidth()+" height = "+dstBmp.getHeight());
        return dstBmp;
    }

}
