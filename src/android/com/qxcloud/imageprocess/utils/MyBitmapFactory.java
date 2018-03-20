package com.qxcloud.imageprocess.utils;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Base64;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import static android.graphics.BitmapFactory.decodeByteArray;

/**
 * @Title:MyBitmapFactory
 * @Description: 自定义位图类
 * @Author: chenfuhao
 * @Since:2013-6-22 下午2:23:12
 */
public class MyBitmapFactory {


    /**
     * 根据路径获取图片 InputStream
     * @param path
     * @return
     */
    public static InputStream getBitmapForManager(String path) {
        InputStream f = null;
        try {
            //图片压缩
            ByteArrayOutputStream baos = getByteArrayOutputStreamByPath(path);
            f = new ByteArrayInputStream(baos.toByteArray());//把压缩后的数据baos存放到ByteArrayInputStream
        } catch (Exception e) {
            e.printStackTrace();
        }catch (Error error){
            error.printStackTrace();
        }
        return f;
    }

    /**
     * 根据路径 获取图片Base64字符串
     * @param path
     * @return
     */
    public static String getBitmapStr(String path) {
        String str = null;
        try {
            //图片压缩
            ByteArrayOutputStream baos = getByteArrayOutputStreamByPath(path);
            str = Base64.encodeToString(baos.toByteArray(),Base64.DEFAULT);
            Logger.e("str length"+str.length()/1024+"KB");
        } catch (Exception e) {
            e.printStackTrace();
        }catch (Error error){
            error.printStackTrace();
        }
        return str;
    }

    /**
     * 图片压缩  根据路径获取图片压缩后的流
     *
     * @param path 路径
     * @return InputStream
     */
    public static Bitmap getBitmapByPath(String path) {
        Bitmap result = null;
        try {
            //图片压缩

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.RGB_565;

            ByteArrayOutputStream baos = getByteArrayOutputStreamByPath(path);
            result = decodeByteArray(baos.toByteArray(), 0, baos.toByteArray().length,options);
            Logger.e("compress result size = "+result.getByteCount()/8/1024+"KB");
        } catch (Exception e) {
            e.printStackTrace();
        } catch (Error error) {
            error.printStackTrace();
        }
        return result;
    }

    /**
     * 图片压缩  根据路径获取图片压缩后的Base64 字符串
     *
     * @param path
     * @return getByteArrayOutputStreamByPath
     */
    public static ByteArrayOutputStream getByteArrayOutputStreamByPath(String path) {
        ByteArrayOutputStream baos = null;
        try {
            //第一步：图片的压缩
            Bitmap bm = getSmallBitmap(path);
            //第二步图片修正
            int degree = readPictureDegree(path);
            if (degree > 0) {
                //将图片更具长宽进行压缩和生成
                bm = rotateBitmap(bm, degree);
            }
            //第二步：图片质量压缩
            baos = getByteArrayOutputStream(bm);
        } catch (Exception e) {
            e.printStackTrace();
        } catch (Error error) {
            error.printStackTrace();
        }
        return baos;
    }

//************************************************************************************************************************

    /**
     * 更具路径返回图片
     *
     * @param filePath
     * @return
     */
    public static Bitmap getSmallBitmap(String filePath) {
        try {
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(filePath, options);
            //计算压缩比例
            options.inSampleSize = getBitMapInSampleSize(options);
            options.inJustDecodeBounds = false;
            return BitmapFactory.decodeFile(filePath, options);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } catch (Error error) {
            error.printStackTrace();
            return null;
        }
    }
    /**
     * 获取图片呗旋转角度
     *
     * @param path 图片路径
     * @return 返回旋转值
     */
    public static int readPictureDegree(String path) {
        int degree = 0;
        try {
            ExifInterface exifInterface = new ExifInterface(path);
            int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    degree = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    degree = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    degree = 270;
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return degree;
    }
    /**
     * 旋转照片
     *
     * @param bitmap
     * @param degress
     * @return
     */
    public static Bitmap rotateBitmap(Bitmap bitmap, int degress) {
        try {
            if (bitmap != null) {
                Matrix m = new Matrix();
                m.postRotate(degress);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                        bitmap.getHeight(), m, true);
                return bitmap;
            } else {
                return bitmap;
            }
        } catch (Error error) {
            Logger.e("旋转异常：" + error.toString());
        }
        return bitmap;
    }
    /**
     * 图片质量的压缩，符合大小
     *
     * @param bm
     * @return
     */
    private static ByteArrayOutputStream getByteArrayOutputStream(Bitmap bm) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        //根据质量进行图片压缩
        bm.compress(Bitmap.CompressFormat.JPEG, 100, baos);

        Logger.e("compress init bmp size = "+baos.toByteArray().length/1024+"KB");

        int options = 100;
        // if (baos.toByteArray().length / 1024 > 1024) {//图片大于1M时进行压缩
        //获取最长的图片的边的长度
        int m = bm.getWidth() > bm.getHeight() ? bm.getWidth() : bm.getHeight();
        //如果大于1280的，1.5倍意思，压缩质量小于150K,2倍以上5倍以下，压缩质量小于400K,5倍以上压缩到600K，10倍以上1M
        int maxBitmapSize = 0;
        if (m <= 1280) {
            maxBitmapSize = 100;//100K
        } else if (m > 1280 && m <= 1280 * 1.5f) {
            maxBitmapSize = 120;//150K
        } else if (m > 1280 * 1.5f && m <= 1280 * 2f) {
            maxBitmapSize = 150;//150K
        } else if (m > 1280 * 2 && m <= 1280 * 5) {
            maxBitmapSize = 300;//300K
        } else if (m > 1280 * 5 && m <= 1280 * 10f) {
            maxBitmapSize = 600;//600K
        } else if (m > 1280 * 10) {
            maxBitmapSize = 1024;//1024K
        }
        while (baos.toByteArray().length / 1024 > maxBitmapSize) {
            //循环判断如果压缩后图片是否大于指定的大小,大于继续压缩
            baos.reset();//重置baos即清空baos
            bm.compress(Bitmap.CompressFormat.JPEG, options, baos);//这里压缩到options%，把压缩后的数据存放到baos中
            Logger.e("compress progress bmp size = "+baos.toByteArray().length/1024+"KB");
            options -= 5;//每次都减少5
            if (options <= 0)
                break;
        }
        //}
        Logger.e("compress result bmp size = "+baos.toByteArray().length/1024+"KB");
        return baos;
    }

    /**
     * 获取图片缩放比例
     * @param options
     * @return
     */
    public static int getBitMapInSampleSize(BitmapFactory.Options options) {
        // 原始图片的宽高
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        if (width < height) {//宽度小于高度
            if (width <= 800) {//图片宽度小于800
                if (height > 1280) {
                    //如果大于1280--这种图片展示使用BitmapRegionDecoder切图显示
                    int m = options.outWidth > options.outHeight ? options.outWidth : options.outHeight;
                    if (m > 1280 && m <= 1280 * 2f) {
                        inSampleSize = 2;//缩小2倍
                    } else if (m > 1280 * 2 && m <= 1280 * 5) {
                        inSampleSize = 3;//缩小3倍
                    } else if (m > 1280 * 5 && m <= 1280 * 10f) {
                        inSampleSize = 5;//缩小5倍
                    } else if (m > 1280 * 10) {
                        inSampleSize = 10;//缩小10倍
                    }
                } else {//高度小于或者等于1280的时候不处理
                    inSampleSize = 1;
                }
            } else {//按比例指定图片大小
                /*默认图片大小720*1280
                * 3：4图片960*1280
                * 9：16图片720*1280
                * 3：5图片760*1200|480*800
                * */
                double s = options.outWidth * 1.0d / (options.outHeight * 1.0d);
                float scaleWidth = 0.0f, scaleHeight = 0.0f;
                float rWidth = 720f;
                float rHeight = 1280f;

                if (s == 0.75d) {// 3：4图片960*1280
                    rWidth = 960f;
                    rHeight = 1280f;
                } else if (s == 0.5625d) {// 9：16图片720*1280
                    rWidth = 720f;
                    rHeight = 1280f;
                } else if (s == 0.6d) {// 3：5图片760*1200
                    // 缩放
                    rWidth = 760f;
                    rHeight = 1200f;
                } else {//默认图片大小720*1280
                    // 缩放
                    rWidth = 720f;
                    rHeight = 1280f;
                }
                // 缩放
                scaleWidth = ((float) width) / rWidth;
                scaleHeight = ((float) height) / rHeight;
                float scale = Math.max(scaleWidth, scaleHeight);
                inSampleSize = (int) scale;
            }
        } else {//宽度大于高度
            if (height <= 800) {//图片宽度小于800
                if (width > 1280) {
                    //如果大于1280--这种图片展示使用BitmapRegionDecoder切图显示
                    int m = options.outWidth > options.outHeight ? options.outWidth : options.outHeight;
                    if (m > 1280 && m <= 1280 * 1.5f) {
                        inSampleSize = 2;//缩小2倍
                    } else if (m > 1280 * 2 && m <= 1280 * 5) {
                        inSampleSize = 3;//缩小3倍
                    } else if (m > 1280 * 5 && m <= 1280 * 10f) {
                        inSampleSize = 5;//缩小5倍
                    } else if (m > 1280 * 10) {
                        inSampleSize = 10;//缩小10倍
                    }
                } else {//高度小于或者等于1280的时候不处理
                    inSampleSize = 1;
                }
            } else {//按比例指定图片大小
                /*默认图片大小720*1280
                * 3：4图片960*1280
                * 9：16图片720*1280
                * 3：5图片760*1200|480*800
                * */
                double s = options.outHeight * 1.0d / (options.outWidth * 1.0d);
                float scaleWidth = 0.0f, scaleHeight = 0.0f;
                float rWidth = 1280f;
                float rHeight = 720f;

                if (s == 0.75d) {// 3：4图片960*1280
                    rHeight = 960f;
                    rWidth = 1280f;
                } else if (s == 0.5625d) {// 9：16图片720*1280
                    rHeight = 720f;
                    rWidth = 1280f;
                } else if (s == 0.6d) {// 3：5图片760*1200
                    // 缩放
                    rHeight = 760f;
                    rWidth = 1200f;
                } else {//默认图片大小720*1280
                    // 缩放
                    rHeight = 720f;
                    rWidth = 1280f;
                }
                // 缩放
                scaleWidth = ((float) width) / rWidth;
                scaleHeight = ((float) height) / rHeight;
                float scale = Math.max(scaleWidth, scaleHeight);
                inSampleSize = (int) scale;
            }
        }
        return inSampleSize;
    }


//*******************************************************************************根据路径处理图片结束****************************************************************************************



    /**
     * 压缩图片到小于300k
     *
     * @param imagepath 图片路径
     * @return Bitmap
     */
    public static Bitmap compressImage(String imagepath, int size, int w, int h) {
        Bitmap image = getBitMapwithWith(imagepath, w, h);
        return compressImage(image, size);
    }

    public static Bitmap compressImage(String imagepath, int size) {
        Bitmap image = getBitMapwithWith(imagepath, 300, 300);
        return compressImage(image, size);
    }

    /**
     * BitmapFactory.Options 按图片长宽放缩(压缩到forwidth*forwidth)
     *
     * @param path
     * @param fwidth
     * @param fheight
     * @return
     */
    public static Bitmap getBitMapwithWith(String path, int fwidth, int fheight) {
        Logger.e(fwidth + "++++++++++App++++++++" + fheight);
        BitmapFactory.Options opts = null;
        FileInputStream f = null;
        Bitmap bm = null;
        if (opts == null) {
            opts = new BitmapFactory.Options();
            int w = fwidth;
            int h = fheight;
            // 设置为ture只获取图片大小
            opts.inJustDecodeBounds = false;
            opts.inPurgeable = true;
            opts.inInputShareable = true;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            BitmapFactory.decodeFile(path, opts);
            // 返回为空
            int width = opts.outWidth;
            int height = opts.outHeight;
            Logger.e(width + "++++++++++++++++" + height);
            if (width > height) {
                width = opts.outHeight;
                height = opts.outWidth;
            }
            Logger.e(width + "++++++********++++" + height);
            float scaleWidth = 0.0f, scaleHeight = 0.0f;
            if (width > w || height > h) {
                // 缩放
                scaleWidth = ((float) width) / w;
                scaleHeight = ((float) height) / h;
            }
            opts.inJustDecodeBounds = false;
            float scale = Math.max(scaleWidth, scaleHeight);
            Logger.e("++++++++scale+++++++：" + scale);
            scale = (scale > 2) ? 2 : scale;
            opts.inSampleSize = (int) scale;
        }
        try {
            f = new FileInputStream(path);
            bm = BitmapFactory.decodeStream(f, null, opts);
            f.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bm;
    }


    /**
     * 压缩图片到小于300k
     *
     * @param image Bitmap位图 size kb
     * @return Bitmap
     */
    public static Bitmap compressImage(Bitmap image, int size) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        // 质量压缩方法，这里100表示不压缩，把压缩后的数据存放到baos中
        int options = 100;
        if (baos.toByteArray().length > 1024 * size) {
            options = 50;
        }
        while (baos.toByteArray().length > 1024 * size) {
            baos.reset();// 重置baos即清空baos
            image.compress(Bitmap.CompressFormat.JPEG, options, baos);
            // 这里压缩options%，把压缩后的数据存放到baos中
            options -= 10;// 每次都减少10%
            if (options <= 0) {
                break;
            }
        }
        ByteArrayInputStream isBm = new ByteArrayInputStream(baos.toByteArray());
        // 把压缩后的数据baos存放到ByteArrayInputStream中
        image.recycle();
        Bitmap bitmap = BitmapFactory.decodeStream(isBm, null, null);
        // 把ByteArrayInputStream数据生成图片
        return bitmap;
    }




//-------------------------------------------根据 Uri 获取图片----------------------------------------------------------------------------------------------
     private Bitmap getBitmapFromUri(Activity activity, Uri uri) {
        try {
            // 读取uri所在的图片
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(activity.getContentResolver(), uri);
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }
    /**
     * 读取一个缩放后的图片，限定图片大小，避免OOM
     * @param uri       图片uri，支持“file://”、“content://”
     * @param maxWidth  最大允许宽度
     * @param maxHeight 最大允许高度
     * @return  返回一个缩放后的Bitmap，失败则返回null
     */
    public static Bitmap decodeUri(Context context, Uri uri, int maxWidth, int maxHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true; //只读取图片尺寸
        //计算实际缩放比例
        int scale = 1;
        for (int i = 0; i < Integer.MAX_VALUE; i++) {
            if ((options.outWidth / scale > maxWidth &&
                    options.outWidth / scale > maxWidth * 1.2) ||
                    (options.outHeight / scale > maxHeight &&
                            options.outHeight / scale > maxHeight * 1.2)) {
                scale++;
            } else {
                break;
            }
        }
        options.inSampleSize = scale;
        options.inJustDecodeBounds = false;//读取图片内容
        options.inPreferredConfig = Bitmap.Config.RGB_565; //根据情况进行修改
        Bitmap bm = null;
        try {
            InputStream stream = context.getContentResolver().openInputStream(uri);
            bm = BitmapFactory.decodeStream(stream, null, options);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        //第二步：图片质量压缩
        ByteArrayOutputStream baos = getByteArrayOutputStream(bm);
        Bitmap bitmap= decodeByteArray(baos.toByteArray(), 0, baos.toByteArray().length);
        return bitmap;
    }

    public static Bitmap compressBitmap(byte[] data){
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true; //只读取图片尺寸
        BitmapFactory.decodeByteArray(data,0,data.length,options);

        options.inSampleSize = getBitMapInSampleSize(options);
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeByteArray(data,0,data.length,options);

        //第二步：图片质量压缩
        ByteArrayOutputStream baos = getByteArrayOutputStream(bitmap);
        Bitmap result = decodeByteArray(baos.toByteArray(), 0, baos.toByteArray().length);
        Logger.e("compressBitmap data "+baos.toByteArray().length/1024+" w = "+result.getWidth()+" h = "+result.getHeight());
        return result;
    }

    public static Bitmap compressBitmap(Bitmap bitmap){
        //第二步：图片质量压缩
        ByteArrayOutputStream baos = getByteArrayOutputStream(bitmap);
        Bitmap result = decodeByteArray(baos.toByteArray(), 0, baos.toByteArray().length);
        Logger.e("compressBitmap data "+baos.toByteArray().length/1024+" w = "+result.getWidth()+" h = "+result.getHeight());
        return result;
    }

    public static byte[] compressBitmap(String path){
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true; //只读取图片尺寸
        BitmapFactory.decodeFile(path,options);

        options.inSampleSize = getBitMapInSampleSize(options);
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeFile(path,options);

        //第二步：图片质量压缩
        ByteArrayOutputStream baos = getByteArrayOutputStream(bitmap);
        return baos.toByteArray();
    }



//----------------------------------------根据Uri 处理图片结束------------------------------------------------------------------












    /**
     * 将图片重新设置正常
     *
     * @param angle  设置角度
     * @param bitmap 图片
     * @return
     */
    private static Bitmap rotaingImageView(int angle, Bitmap bitmap, float w, float h, int ww, int hh) {
        try {
            System.out.println("angle2=" + angle);
            //旋转图片 动作
            Matrix matrix = new Matrix();
            float scaleWidth = ((float) ww) / w;
            float scaleHeight = ((float) hh) / h;
            matrix.setScale(scaleWidth, scaleHeight);
            matrix.postRotate(90);  //旋转图片 动作
            //创建新的图片
            Bitmap resizedBitmap = Bitmap.createBitmap(bitmap, 0, 0,
                    bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            return resizedBitmap;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 自定义输入流
     *
     * @author BruceLee
     */
    public class MyInputStream extends FilterInputStream {

        protected MyInputStream(InputStream in) {
            super(in);
        }

        @Override
        public int read(byte[] buffer, int offset, int count)
                throws IOException {
            int ret = super.read(buffer, offset, count);
            int len = buffer.length;
            for (int i = 6; i < len - 4; i++) {
                if (buffer[i] == 0x2c) {
                    if (buffer[i + 2] == 0 && buffer[i + 1] > 0
                            && buffer[i + 1] <= 48) {
                        buffer[i + 1] = 0;
                    }
                    if (buffer[i + 4] == 0 && buffer[i + 3] > 0
                            && buffer[i + 3] <= 48) {
                        buffer[i + 3] = 0;
                    }
                }
            }
            return ret;
        }
    }


    /**
     * BitmapFactory.Options 按图片长宽放缩(压缩到500*500)
     *
     * @param path 路徑
     * @return BitmapFactory.Options.inSampleSize 压缩比例
     */
    public static int GetBitMapOptInt(String path) {
        BitmapFactory.Options opts = null;
        if (opts == null) {
            opts = new BitmapFactory.Options();
            int w = 500;
            int h = 500;
            // 设置为ture只获取图片大小
            opts.inJustDecodeBounds = false;
            opts.inPurgeable = true;
            opts.inInputShareable = true;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            BitmapFactory.decodeFile(path, opts);
            // 返回为空
            int width = opts.outWidth;
            int height = opts.outHeight;
            float scaleWidth = 0.f, scaleHeight = 0.f;
            if (width > w || height > h) {
                // 缩放
                scaleWidth = ((float) width) / w;
                scaleHeight = ((float) height) / h;
            }
            opts.inJustDecodeBounds = false;
            float scale = Math.max(scaleWidth, scaleHeight);
            opts.inSampleSize = (int) scale;
        }
        return opts.inSampleSize;
    }

    /**
     * 压缩图片，处理某些手机拍照角度旋转的问题
     *
     * @param context
     * @param filePath 图片的路径
     * @param fileName 文件保存的名称
     * @param q        图片质量，默认100；
     * @return
     * @throws FileNotFoundException
     */
    public static String compressImage(Context context, String filePath, String fileName, int q) throws FileNotFoundException {

        Bitmap bm = null;
        try {
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(filePath, options);
            options.inSampleSize = getBitMapInSampleSize(options);
            options.inJustDecodeBounds = false;
            bm = BitmapFactory.decodeFile(filePath, options);
        } catch (Exception e) {
            e.printStackTrace();
            return filePath;
        }

        int degree = readPictureDegree(filePath);

        if (degree != 0) {//旋转照片角度
            bm = rotateBitmap(bm, degree);
        }
        String imgDir = filePath.substring(0, filePath.lastIndexOf("/"));
        File imageDir = new File(filePath);
        if (!imageDir.exists()) {
            imageDir.mkdirs();
        }
        File outputFile = new File(imageDir, fileName);
        if (!outputFile.exists()) {
            try {
                outputFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        FileOutputStream out = new FileOutputStream(outputFile);
        bm.compress(Bitmap.CompressFormat.JPEG, q, out);
        return outputFile.getPath();
    }

    public static boolean saveBitmap(Bitmap bmp,String path){
        try {
            File file = new File(path);
            if(!file.exists()){
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
            ByteArrayOutputStream baos = getByteArrayOutputStream(bmp);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(baos.toByteArray());
            fos.close();
            Logger.e("saveBitmap size = "+file.length()/1024+"KB");
            return true;
        }catch (Exception e){
            Logger.e("saveBitmap e "+e.getMessage());
        }
        return false;
    }
}
