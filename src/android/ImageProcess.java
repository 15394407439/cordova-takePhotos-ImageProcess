package com.qxcloud.imageprocess;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.qxcloud.imageprocess.editAPI.EditImageAPI;
import com.qxcloud.imageprocess.editAPI.EditImageMessage;
import com.qxcloud.imageprocess.editAPI.EditImgInterface;
import com.qxcloud.imageprocess.utils.FileUtils;
import com.qxcloud.imageprocess.utils.Logger;
import com.qxcloud.imageprocess.utils.URIUtils;

import org.apache.cordova.BuildHelper;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.LOG;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.PluginResult;
import org.apache.cordova.camera.CordovaUri;
import org.apache.cordova.camera.ExifHelper;
import org.apache.cordova.camera.FileHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * CREATED BY:         heaton
 * CREATED DATE:       2017/9/18
 * CREATED TIME:       下午5:52
 * CREATED DESCRIPTION:
 */

public class ImageProcess extends CordovaPlugin implements EditImgInterface, MediaScannerConnection.MediaScannerConnectionClient {
    private static final String ACTION_CAMERA_NEW = "com.qxcloud.imageprocess.activity.newCamera.NewTackPhotoActivity";
    public static final String ACTION_CAMERA = "com.qxcloud.imageprocess.activity.TakePhotoActivity";
    public static final String ACTION_CROP = "com.qxcloud.imageprocess.activity.CropImgActivity";
    public static final String ACTION_ALBUM = Intent.ACTION_PICK;

    public static final int REQUEST_ALBUM = 10;
    public static final int HANDLER_WHAT_CROP = 11;

    public static final String EXTRA_DEFAULT_SAVE_PATH = "default_save_path";
    public static final String EXTRA_DEFAULT_SELECT_PATH = "default_select_path";
    public static final String RATIO = "ratio";

    public static final String METHOD_OPEN_CAMERA = "takePhotoAndCropImag";
    public static final String METHOD_OPEN_ALBUM = "selectAndCropImage";
    public static final String METHOD_OPEN_CROP = "openCrop";

    public static final String EXTRA_DEFAULT_METHOD_ACTION = "method_action";
    public static final String  TAG = "ImageProcess";

    private static final int DATA_URL = 0;              // Return base64 encoded string
    private static final int FILE_URI = 1;              // Return file uri (content://media/external/images/media/2 for Android)
    private static final int NATIVE_URI = 2;                    // On Android, this is the same as FILE_URI

    private static final int PHOTOLIBRARY = 0;          // Choose image from picture library (same as SAVEDPHOTOALBUM for Android)
    private static final int CAMERA = 1;                // Take picture from camera
    private static final int SAVEDPHOTOALBUM = 2;       // Choose image from picture library (same as PHOTOLIBRARY for Android)

    private static final int PICTURE = 0;               // allow selection of still pictures only. DEFAULT. Will return format specified via DestinationType
    private static final int VIDEO = 1;                 // allow selection of video only, ONLY RETURNS URL
    private static final int ALLMEDIA = 2;              // allow selection from all media types

    private static final int JPEG = 0;                  // Take a picture of type JPEG
    private static final int PNG = 1;                   // Take a picture of type PNG
    private static final String GET_PICTURE = "Get Picture";
    private static final String GET_VIDEO = "Get Video";
    private static final String GET_All = "Get All";

    public static final int PERMISSION_DENIED_ERROR = 20;
    public static final int TAKE_PIC_SEC = 0;
    public static final int SAVE_TO_ALBUM_SEC = 1;

    //Where did this come from?
    private static final int CROP_CAMERA = 100;

    private int mQuality;                   // Compression quality hint (0-100: 0=low quality & high compression, 100=compress of max quality)
    private int targetWidth;                // desired width of the image
    private int targetHeight;               // desired height of the image
    private CordovaUri imageUri;            // Uri of captured image
    private int encodingType;               // Type of encoding to use
    private int mediaType;                  // What type of media to retrieve
    private int destType;                   // Source type (needs to be saved for the permission handling)
    private int srcType;                    // Destination type (needs to be saved for permission handling)
    private boolean saveToPhotoAlbum;       // Should the picture be saved to the device's photo album
    private boolean correctOrientation;     // Should the pictures orientation be corrected
    private boolean orientationCorrected;   // Has the picture's orientation been corrected
    private boolean allowEdit;              // Should we allow the user to crop the image.

    protected final static String[] permissions = { Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE };
    private int numPics;
    private MediaScannerConnection conn;    // Used to update gallery app with newly-written files
    private Uri scanMe;                     // Uri of image to be added to content store
    private Uri croppedUri;
    private ExifHelper exifData;            // Exif data from source
    private String applicationId;

    private String mSavedFilePath = null;
    private CallbackContext callbackContext;
    private String mAction;
    private String mSelectFilePath = null;
    public static float mRatio;


    private Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what){
                case HANDLER_WHAT_CROP:
                    openCrop((Uri) msg.obj);
                    break;
            }
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        EditImageAPI.getInstance().unRegisterEditImg(this);
    }

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        EditImageAPI.getInstance().registerEditImg(this);
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        this.callbackContext = callbackContext;
        this.mAction = action;
        JSONObject jsonObject = args.getJSONObject(0);
        String ratio = jsonObject.getString("ratio");
        mRatio = Float.parseFloat(ratio);
        Log.e(TAG, "mRatio === : "+ mRatio );
        exeMethod(args);
        return true;
    }

    private void exeMethod(JSONArray args){
      /*  if(TextUtils.isEmpty(mSavedFilePath)){
            mSavedFilePath = FileUtils.createFile(this.cordova.getActivity());
        }*/
        mSavedFilePath = FileUtils.createFile(this.cordova.getActivity());
        if(mAction.equals(METHOD_OPEN_CAMERA)){
            openCamera();
        }else if(mAction.equals(METHOD_OPEN_ALBUM)){
            openAlbum();
        }else if(mAction.equals(METHOD_OPEN_CROP)){
            if(!TextUtils.isEmpty(mSelectFilePath)){
                openCrop(Uri.parse(mSelectFilePath));
            }
        }
    }

    //执行拍照
      public void openCamera(){
          this.applicationId = (String) BuildHelper.getBuildConfigValue(cordova.getActivity(), "APPLICATION_ID");
          this.applicationId = preferences.getString("applicationId", this.applicationId);

          this.srcType = CAMERA;
          this.destType = FILE_URI;
          this.saveToPhotoAlbum = false;
          this.targetHeight = 600;
          this.targetWidth = 800;
          this.encodingType = JPEG;
          this.mediaType = PICTURE;
          this.mQuality = 50;
          this.allowEdit = true;

          try {
              if (this.srcType == CAMERA) {
                  this.callTakePicture(destType, encodingType);
              } else if ((this.srcType == PHOTOLIBRARY) || (this.srcType == SAVEDPHOTOALBUM)) {
                  // FIXME: Stop always requesting the permission
                  if (!PermissionHelper.hasPermission(this, permissions[0])) {
                      PermissionHelper.requestPermission(this, SAVE_TO_ALBUM_SEC, Manifest.permission.READ_EXTERNAL_STORAGE);
                  } else {
                      this.getImage(this.srcType, destType, encodingType);
                  }
              }
          } catch (IllegalArgumentException e) {
              callbackContext.error("Illegal Argument Exception");
              PluginResult r = new PluginResult(PluginResult.Status.ERROR);
              callbackContext.sendPluginResult(r);
          }

          PluginResult r = new PluginResult(PluginResult.Status.NO_RESULT);
          r.setKeepCallback(true);
          callbackContext.sendPluginResult(r);

    }

    public void openAlbum(){
        Intent intent = new Intent(ACTION_ALBUM);
        intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
        intent.putExtra(EXTRA_DEFAULT_METHOD_ACTION, mAction);
        this.cordova.startActivityForResult(this,intent,REQUEST_ALBUM);
    }

    public void openCrop(Uri selectedImageUri){
        if(selectedImageUri.getScheme().equals("file")) {//判断uri地址是以什么开头的
            selectedImageUri = URIUtils.getFileUri(this.cordova.getActivity(),selectedImageUri);
        }
        Cursor cursor = this.cordova.getActivity().getContentResolver().query(selectedImageUri, null, null, null, null);
        if(cursor !=null && cursor.moveToFirst()){
            String imagePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA));
            Logger.e("imagePath === "+imagePath);
            Intent intent = new Intent(ACTION_CROP);
            intent.putExtra(EXTRA_DEFAULT_SAVE_PATH, mSavedFilePath);
            intent.putExtra(EXTRA_DEFAULT_SELECT_PATH,imagePath);
            intent.putExtra(EXTRA_DEFAULT_METHOD_ACTION, mAction);
            this.cordova.startActivityForResult(this,intent,103);
        }
    }

    public void openCrop(String selectFilePath){
        Intent intent = new Intent(ACTION_CROP);
        intent.putExtra(EXTRA_DEFAULT_SAVE_PATH, mSavedFilePath);
        intent.putExtra(EXTRA_DEFAULT_SELECT_PATH,selectFilePath);
        intent.putExtra(EXTRA_DEFAULT_METHOD_ACTION, mAction);
        this.cordova.startActivityForResult(this,intent,104);
    }


    @Override
    public void onEditImgResult(int code, EditImageMessage editImageMessage) {
        if(code == 0 && editImageMessage.getWhat() == 0){
            String cropImageResult = Uri.fromFile(new File(mSavedFilePath)).toString();
            Log.e(TAG, "onEditImgResult: 获取裁剪后图片的路径 == " + cropImageResult);
            callbackContext.success(cropImageResult);
        }
        if(code == 1 && editImageMessage.getWhat() == 1){
            callbackContext.error("请前往设置打开相机及内部存储权限");
        }else if(code == 2 && editImageMessage.getWhat() == 1){
            callbackContext.error("用户手动关闭页面");
        }
    }

    private String getTempDirectoryPath() {
        File cache = null;

        // SD Card Mounted
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            cache = cordova.getActivity().getExternalCacheDir();
        }
        // Use internal storage
        else {
            cache = cordova.getActivity().getCacheDir();
        }

        // Create the cache directory if it doesn't exist
        cache.mkdirs();
        return cache.getAbsolutePath();
    }

    /**
     *
     * @param returnType        Set the type of image to return.
     * @param encodingType           Compression quality hint (0-100: 0=low quality & high compression, 100=compress of max quality)
     */
    public void callTakePicture(int returnType, int encodingType) {
        boolean saveAlbumPermission = PermissionHelper.hasPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
        boolean takePicturePermission = PermissionHelper.hasPermission(this, Manifest.permission.CAMERA);

        if (!takePicturePermission) {
            takePicturePermission = true;
            try {
                PackageManager packageManager = this.cordova.getActivity().getPackageManager();
                String[] permissionsInPackage = packageManager.getPackageInfo(this.cordova.getActivity().getPackageName(), PackageManager.GET_PERMISSIONS).requestedPermissions;
                if (permissionsInPackage != null) {
                    for (String permission : permissionsInPackage) {
                        if (permission.equals(Manifest.permission.CAMERA)) {
                            takePicturePermission = false;
                            break;
                        }
                    }
                }
            } catch (PackageManager.NameNotFoundException e) {

            }
        }

        if (takePicturePermission && saveAlbumPermission) {
            takePicture(returnType, encodingType);
        } else if (saveAlbumPermission && !takePicturePermission) {
            PermissionHelper.requestPermission(this, TAKE_PIC_SEC, Manifest.permission.CAMERA);
        } else if (!saveAlbumPermission && takePicturePermission) {
            PermissionHelper.requestPermission(this, TAKE_PIC_SEC, Manifest.permission.READ_EXTERNAL_STORAGE);
        } else {
            PermissionHelper.requestPermissions(this, TAKE_PIC_SEC, permissions);
        }
    }

    public void takePicture(int returnType, int encodingType)
    {
        // Save the number of images currently on disk for later
        this.numPics = queryImgDB(whichContentStore()).getCount();

        // Let's use the intent and see what happens
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        // Specify file so that large image is captured and returned
        File photo = createCaptureFile(encodingType);
        this.imageUri = new CordovaUri(FileProvider.getUriForFile(cordova.getActivity(),
                applicationId + ".provider",
                photo));
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri.getCorrectUri());
        //We can write to this URI, this will hopefully allow us to write files to get to the next step
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        if (this.cordova != null) {
            // Let's check to make sure the camera is actually installed. (Legacy Nexus 7 code)
            PackageManager mPm = this.cordova.getActivity().getPackageManager();
            if(intent.resolveActivity(mPm) != null)
            {

                this.cordova.startActivityForResult((CordovaPlugin) this, intent, (CAMERA + 1) * 16 + returnType + 1);
            }
            else
            {
                LOG.d(TAG, "Error: You don't have a default camera.  Your device may not be CTS complaint.");
            }
        }
    }

    private File createCaptureFile(int encodingType) {
        return createCaptureFile(encodingType, "");
    }

    /**
     * Create a file in the applications temporary directory based upon the supplied encoding.
     *
     * @param encodingType of the image to be taken
     * @param fileName or resultant File object.
     * @return a File object pointing to the temporary picture
     */
    private File createCaptureFile(int encodingType, String fileName) {
        if (fileName.isEmpty()) {
            fileName = ".Pic";
        }

        if (encodingType == JPEG) {
            fileName = fileName + ".jpg";
        } else if (encodingType == PNG) {
            fileName = fileName + ".png";
        } else {
            throw new IllegalArgumentException("Invalid Encoding Type: " + encodingType);
        }

        return new File(getTempDirectoryPath(), fileName);
    }

    /**
     * 从照片库获取图片
     *
     * @param srcType           The album to get image from.
     * @param returnType        Set the type of image to return.
     * @param encodingType
     */
    // TODO: Images selected from SDCARD don't display correctly, but from CAMERA ALBUM do!
    // TODO: Images from kitkat filechooser not going into crop function
    public void getImage(int srcType, int returnType, int encodingType) {
        Intent intent = new Intent();
        String title = GET_PICTURE;
        croppedUri = null;
        if (this.mediaType == PICTURE) {
            intent.setType("image/*");
            if (this.allowEdit) {
                intent.setAction(Intent.ACTION_PICK);
                intent.putExtra("crop", "true");
                if (targetWidth > 0) {
                    intent.putExtra("outputX", targetWidth);
                }
                if (targetHeight > 0) {
                    intent.putExtra("outputY", targetHeight);
                }
                if (targetHeight > 0 && targetWidth > 0 && targetWidth == targetHeight) {
                    intent.putExtra("aspectX", 1);
                    intent.putExtra("aspectY", 1);
                }
                File photo = createCaptureFile(JPEG);
                croppedUri = Uri.fromFile(photo);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, croppedUri);
            } else {
                intent.setAction(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
            }
        } else if (this.mediaType == VIDEO) {
            intent.setType("video/*");
            title = GET_VIDEO;
            intent.setAction(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
        } else if (this.mediaType == ALLMEDIA) {
            // I wanted to make the type 'image/*, video/*' but this does not work on all versions
            // of android so I had to go with the wildcard search.
            intent.setType("*/*");
            title = GET_All;
            intent.setAction(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
        }
        if (this.cordova != null) {
            this.cordova.startActivityForResult((CordovaPlugin) this, Intent.createChooser(intent,
                    new String(title)), (srcType + 1) * 16 + returnType + 1);
        }
    }

    /**
     *这个方法是执行裁剪的方法
     * @param picUri
     */
    private void performCrop(Uri picUri, int destType, Intent cameraIntent) {
        try {
            Intent cropIntent = new Intent("com.android.camera.action.CROP");
            cropIntent.setDataAndType(picUri, "image/*");
            cropIntent.putExtra("crop", "true");
            cropIntent.putExtra("outputX", targetWidth);
            cropIntent.putExtra("outputY", targetHeight);
            if(mRatio == 1.0){
                cropIntent.putExtra("aspectX", 1);
                cropIntent.putExtra("aspectY", 1);
            }else if (mRatio == 0.75){
                cropIntent.putExtra("aspectX", 4);
                cropIntent.putExtra("aspectY", 3);
            }

            croppedUri = Uri.fromFile(createCaptureFile(this.encodingType, System.currentTimeMillis() + ""));
            cropIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            cropIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            cropIntent.putExtra("output", croppedUri);

            if (this.cordova != null) {
                this.cordova.startActivityForResult((CordovaPlugin) this, cropIntent, CROP_CAMERA + destType);
            }
        } catch (ActivityNotFoundException anfe) {
            LOG.e(TAG, "Crop operation not supported on this device");
            try {
                processResultFromCamera(destType, cameraIntent);
            } catch (IOException e) {
                e.printStackTrace();
                LOG.e(TAG, "Unable to write to file");
            }
        }
    }

    /**
     * Applies all needed transformation to the image received from the camera.
     *
     * @param destType          In which form should we return the image
     * @param intent            An Intent, which can return result data to the caller (various data can be attached to Intent "extras").
     */
    private void processResultFromCamera(int destType, Intent intent) throws IOException {
        int rotate = 0;
        // Create an ExifHelper to save the exif data that is lost during compression
        ExifHelper exif = new ExifHelper();
        String sourcePath = (this.allowEdit && this.croppedUri != null) ?
                FileHelper.stripFileProtocol(this.croppedUri.toString()) : this.imageUri.getFilePath();

        if (this.encodingType == JPEG) {
            try {
                //We don't support PNG, so let's not pretend we do
                exif.createInFile(sourcePath);
                exif.readExifData();
                rotate = exif.getOrientation();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Bitmap bitmap = null;
        Uri galleryUri = null;

        if (this.saveToPhotoAlbum) {
            galleryUri = Uri.fromFile(new File(getPicturesPath()));

            if (this.allowEdit && this.croppedUri != null) {
                writeUncompressedImage(croppedUri, galleryUri);
            } else {
                Uri imageUri = this.imageUri.getFileUri();
                writeUncompressedImage(imageUri, galleryUri);
            }

            refreshGallery(galleryUri);
        }

        // If sending base64 image back
        if (destType == DATA_URL) {
            bitmap = getScaledAndRotatedBitmap(sourcePath);

            if (bitmap == null) {
                // Try to get the bitmap from intent.
                bitmap = (Bitmap) intent.getExtras().get("data");
            }

            // Double-check the bitmap.
            if (bitmap == null) {
                LOG.d(TAG, "I either have a null image path or bitmap");
                this.failPicture("Unable to create bitmap!");
                return;
            }
            this.processPicture(bitmap, this.encodingType);

            if (!this.saveToPhotoAlbum) {
                checkForDuplicateImage(DATA_URL);
            }
        }

        // If sending filename back
        else if (destType == FILE_URI || destType == NATIVE_URI) {
            // If all this is true we shouldn't compress the image.
            if (this.targetHeight == -1 && this.targetWidth == -1 && this.mQuality == 100 &&
                    !this.correctOrientation) {

                // If we saved the uncompressed photo to the album, we can just
                // return the URI we already created
                if (this.saveToPhotoAlbum) {
                    this.callbackContext.success(galleryUri.toString());
                } else {
                    Uri uri = Uri.fromFile(createCaptureFile(this.encodingType, System.currentTimeMillis() + ""));

                    if (this.allowEdit && this.croppedUri != null) {
                        Uri croppedUri = Uri.fromFile(new File(getFileNameFromUri(this.croppedUri)));
                        writeUncompressedImage(croppedUri, uri);
                    } else {
                        Uri imageUri = this.imageUri.getFileUri();
                        writeUncompressedImage(imageUri, uri);
                    }

                    this.callbackContext.success(uri.toString());
                }
            } else {
                Uri uri = Uri.fromFile(createCaptureFile(this.encodingType, System.currentTimeMillis() + ""));
                bitmap = getScaledAndRotatedBitmap(sourcePath);

                // Double-check the bitmap.
                if (bitmap == null) {
                    LOG.d(TAG, "I either have a null image path or bitmap");
                    this.failPicture("Unable to create bitmap!");
                    return;
                }

                // Add compressed version of captured image to returned media store Uri
                OutputStream os = this.cordova.getActivity().getContentResolver().openOutputStream(uri);
                Bitmap.CompressFormat compressFormat = encodingType == JPEG ?
                        Bitmap.CompressFormat.JPEG :
                        Bitmap.CompressFormat.PNG;

                bitmap.compress(compressFormat, this.mQuality, os);
                os.close();

                // Restore exif data to file
                if (this.encodingType == JPEG) {
                    String exifPath;
                    exifPath = uri.getPath();
                    exif.createOutFile(exifPath);
                    exif.writeExifData();
                }

                // Send Uri back to JavaScript for viewing image
                this.callbackContext.success(uri.toString());

            }
        } else {
            throw new IllegalStateException();
        }

        this.cleanup(FILE_URI, this.imageUri.getFileUri(), galleryUri, bitmap);
        bitmap = null;
    }

    private String getPicturesPath() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "IMG_" + timeStamp + (this.encodingType == JPEG ? ".jpg" : ".png");
        File storageDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES);
        String galleryPath = storageDir.getAbsolutePath() + "/" + imageFileName;
        return galleryPath;
    }

    private void refreshGallery(Uri contentUri) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(contentUri);
        this.cordova.getActivity().sendBroadcast(mediaScanIntent);
    }

    /**
     * Converts output image format int value to string value of mime type.
     * @param outputFormat int Output format of camera API.
     *                     Must be value of either JPEG or PNG constant
     * @return String String value of mime type or empty string if mime type is not supported
     */
    private String getMimetypeForFormat(int outputFormat) {
        if (outputFormat == PNG) return "image/png";
        if (outputFormat == JPEG) return "image/jpeg";
        return "";
    }

    private String outputModifiedBitmap(Bitmap bitmap, Uri uri) throws IOException {
        // Some content: URIs do not map to file paths (e.g. picasa).
        String realPath = FileHelper.getRealPath(uri, this.cordova);

        // Get filename from uri
        String fileName = realPath != null ?
                realPath.substring(realPath.lastIndexOf('/') + 1) :
                "modified." + (this.encodingType == JPEG ? "jpg" : "png");

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        //String fileName = "IMG_" + timeStamp + (this.encodingType == JPEG ? ".jpg" : ".png");
        String modifiedPath = getTempDirectoryPath() + "/" + fileName;

        OutputStream os = new FileOutputStream(modifiedPath);
        Bitmap.CompressFormat compressFormat = this.encodingType == JPEG ?
                Bitmap.CompressFormat.JPEG :
                Bitmap.CompressFormat.PNG;

        bitmap.compress(compressFormat, this.mQuality, os);
        os.close();

        if (exifData != null && this.encodingType == JPEG) {
            try {
                if (this.correctOrientation && this.orientationCorrected) {
                    exifData.resetOrientation();
                }
                exifData.createOutFile(modifiedPath);
                exifData.writeExifData();
                exifData = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return modifiedPath;
    }


    /**
     * Applies all needed transformation to the image received from the gallery.
     *
     * @param destType In which form should we return the image
     * @param intent   An Intent, which can return result data to the caller (various data can be attached to Intent "extras").
     */
    private void processResultFromGallery(int destType, Intent intent) {
        Uri uri = intent.getData();
        if (uri == null) {
            if (croppedUri != null) {
                uri = croppedUri;
            } else {
                this.failPicture("null data from photo library");
                return;
            }
        }
        int rotate = 0;

        String fileLocation = FileHelper.getRealPath(uri, this.cordova);
        LOG.d(TAG, "File locaton is: " + fileLocation);

        // If you ask for video or all media type you will automatically get back a file URI
        // and there will be no attempt to resize any returned data
        if (this.mediaType != PICTURE) {
            this.callbackContext.success(fileLocation);
        }
        else {
            String uriString = uri.toString();
            // Get the path to the image. Makes loading so much easier.
            String mimeType = FileHelper.getMimeType(uriString, this.cordova);

            // This is a special case to just return the path as no scaling,
            // rotating, nor compressing needs to be done
            if (this.targetHeight == -1 && this.targetWidth == -1 &&
                    (destType == FILE_URI || destType == NATIVE_URI) && !this.correctOrientation &&
                    mimeType.equalsIgnoreCase(getMimetypeForFormat(encodingType)))
            {
                this.callbackContext.success(uriString);
            } else {
                // If we don't have a valid image so quit.
                if (!("image/jpeg".equalsIgnoreCase(mimeType) || "image/png".equalsIgnoreCase(mimeType))) {
                    LOG.d(TAG, "I either have a null image path or bitmap");
                    this.failPicture("Unable to retrieve path to picture!");
                    return;
                }
                Bitmap bitmap = null;
                try {
                    bitmap = getScaledAndRotatedBitmap(uriString);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (bitmap == null) {
                    LOG.d(TAG, "I either have a null image path or bitmap");
                    this.failPicture("Unable to create bitmap!");
                    return;
                }

                // If sending base64 image back
                if (destType == DATA_URL) {
                    this.processPicture(bitmap, this.encodingType);
                }

                // If sending filename back
                else if (destType == FILE_URI || destType == NATIVE_URI) {
                    // Did we modify the image?
                    if ( (this.targetHeight > 0 && this.targetWidth > 0) ||
                            (this.correctOrientation && this.orientationCorrected) ||
                            !mimeType.equalsIgnoreCase(getMimetypeForFormat(encodingType)))
                    {
                        try {
                            String modifiedPath = this.outputModifiedBitmap(bitmap, uri);
                            // The modified image is cached by the app in order to get around this and not have to delete you
                            // application cache I'm adding the current system time to the end of the file url.
                            this.callbackContext.success("file://" + modifiedPath + "?" + System.currentTimeMillis());

                        } catch (Exception e) {
                            e.printStackTrace();
                            this.failPicture("Error retrieving image.");
                        }
                    } else {
                        this.callbackContext.success(fileLocation);
                    }
                }
                if (bitmap != null) {
                    bitmap.recycle();
                    bitmap = null;
                }
                System.gc();
            }
        }
    }

    /**
     * Called when the camera view exits.
     *
     * @param requestCode The request code originally supplied to startActivityForResult(),
     *                    allowing you to identify who this result came from.
     * @param resultCode  The integer result code returned by the child activity through its setResult().
     * @param intent      An Intent, which can return result data to the caller (various data can be attached to Intent "extras").
     */
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        //super.onActivityResult(requestCode, resultCode, intent);
        if(requestCode == REQUEST_ALBUM ){
            if (intent != null && resultCode == Activity.RESULT_OK) {
                Message message = new Message();
                message.obj = intent.getData();
                message.what = HANDLER_WHAT_CROP;
                handler.sendMessage(message);
            }else{
                callbackContext.error("用户取消选择图片或图片选择失败");
            }
        }

        // Get src and dest types from request code for a Camera Activity
        int srcType = (requestCode / 16) - 1;
        int destType = (requestCode % 16) - 1;

        // If Camera Crop
        if (requestCode >= CROP_CAMERA) {
            if (resultCode == Activity.RESULT_OK) {

                // Because of the inability to pass through multiple intents, this hack will allow us
                // to pass arcane codes back.
                destType = requestCode - CROP_CAMERA;
                try {
                    processResultFromCamera(destType, intent);
                } catch (IOException e) {
                    e.printStackTrace();
                    LOG.e(TAG, "Unable to write to file");
                }

            }// If cancelled
            else if (resultCode == Activity.RESULT_CANCELED) {
                this.failPicture("Camera cancelled.");
            }

            // If something else
            else {
                this.failPicture("Did not complete!");
            }
        }
        // If CAMERA
        else if (srcType == CAMERA) {
            // If image available
            if (resultCode == Activity.RESULT_OK) {
                try {
                    if (this.allowEdit) {
                        Uri tmpFile = FileProvider.getUriForFile(cordova.getActivity(),
                                applicationId + ".provider",
                                createCaptureFile(this.encodingType));
                        performCrop(tmpFile, destType, intent);
                    } else {
                        this.processResultFromCamera(destType, intent);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    this.failPicture("Error capturing image.");
                }
            }

            // If cancelled
            else if (resultCode == Activity.RESULT_CANCELED) {
                this.failPicture("Camera cancelled.");
            }

            // If something else
            else {
                this.failPicture("Did not complete!");
            }
        }
        // If retrieving photo from library
        else if ((srcType == PHOTOLIBRARY) || (srcType == SAVEDPHOTOALBUM)) {
            if (resultCode == Activity.RESULT_OK && intent != null) {
                final Intent i = intent;
                final int finalDestType = destType;
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        processResultFromGallery(finalDestType, i);
                    }
                });
            } else if (resultCode == Activity.RESULT_CANCELED) {
                this.failPicture("Selection cancelled.");
            } else {
                this.failPicture("Selection did not complete!");
            }
        }
    }

    private int exifToDegrees(int exifOrientation) {
        if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_90) {
            return 90;
        } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_180) {
            return 180;
        } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) {
            return 270;
        } else {
            return 0;
        }
    }

    /**
     * Write an inputstream to local disk
     *
     * @param fis - The InputStream to write
     * @param dest - Destination on disk to write to
     * @throws FileNotFoundException
     * @throws IOException
     */
    private void writeUncompressedImage(InputStream fis, Uri dest) throws FileNotFoundException,
            IOException {
        OutputStream os = null;
        try {
            os = this.cordova.getActivity().getContentResolver().openOutputStream(dest);
            byte[] buffer = new byte[4096];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }
            os.flush();
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    LOG.d(TAG, "Exception while closing output stream.");
                }
            }
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    LOG.d(TAG, "Exception while closing file input stream.");
                }
            }
        }
    }
    /**
     * In the special case where the default width, height and quality are unchanged
     * we just write the file out to disk saving the expensive Bitmap.compress function.
     *
     * @param src
     * @throws FileNotFoundException
     * @throws IOException
     */
    private void writeUncompressedImage(Uri src, Uri dest) throws FileNotFoundException,
            IOException {

        FileInputStream fis = new FileInputStream(FileHelper.stripFileProtocol(src.toString()));
        writeUncompressedImage(fis, dest);

    }

    /**
     * Create entry in media store for image
     *
     * @return uri
     */
    private Uri getUriFromMediaStore() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        Uri uri;
        try {
            uri = this.cordova.getActivity().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        } catch (RuntimeException e) {
            LOG.d(TAG, "Can't write to external media storage.");
            try {
                uri = this.cordova.getActivity().getContentResolver().insert(MediaStore.Images.Media.INTERNAL_CONTENT_URI, values);
            } catch (RuntimeException ex) {
                LOG.d(TAG, "Can't write to internal media storage.");
                return null;
            }
        }
        return uri;
    }

    /**
     * Return a scaled and rotated bitmap based on the target width and height
     *
     * @param imageUrl
     * @return
     * @throws IOException
     */
    private Bitmap getScaledAndRotatedBitmap(String imageUrl) throws IOException {
        // If no new width or height were specified, and orientation is not needed return the original bitmap
        if (this.targetWidth <= 0 && this.targetHeight <= 0 && !(this.correctOrientation)) {
            InputStream fileStream = null;
            Bitmap image = null;
            try {
                fileStream = FileHelper.getInputStreamFromUriString(imageUrl, cordova);
                image = BitmapFactory.decodeStream(fileStream);
            } finally {
                if (fileStream != null) {
                    try {
                        fileStream.close();
                    } catch (IOException e) {
                        LOG.d(TAG, "Exception while closing file input stream.");
                    }
                }
            }
            return image;
        }

        File localFile = null;
        Uri galleryUri = null;
        int rotate = 0;
        try {
            InputStream fileStream = FileHelper.getInputStreamFromUriString(imageUrl, cordova);
            if (fileStream != null) {
                // Generate a temporary file
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                String fileName = "IMG_" + timeStamp + (this.encodingType == JPEG ? ".jpg" : ".png");
                localFile = new File(getTempDirectoryPath() + fileName);
                galleryUri = Uri.fromFile(localFile);
                writeUncompressedImage(fileStream, galleryUri);
                try {
                    String mimeType = FileHelper.getMimeType(imageUrl.toString(), cordova);
                    if ("image/jpeg".equalsIgnoreCase(mimeType)) {
                        //  ExifInterface doesn't like the file:// prefix
                        String filePath = galleryUri.toString().replace("file://", "");
                        // read exifData of source
                        exifData = new ExifHelper();
                        exifData.createInFile(filePath);
                        // Use ExifInterface to pull rotation information
                        if (this.correctOrientation) {
                            ExifInterface exif = new ExifInterface(filePath);
                            rotate = exifToDegrees(exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED));
                        }
                    }
                } catch (Exception oe) {
                    LOG.w(TAG,"Unable to read Exif data: "+ oe.toString());
                    rotate = 0;
                }
            }
        }
        catch (Exception e)
        {
            LOG.e(TAG,"Exception while getting input stream: "+ e.toString());
            return null;
        }



        try {
            // figure out the original width and height of the image
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            InputStream fileStream = null;
            try {
                fileStream = FileHelper.getInputStreamFromUriString(galleryUri.toString(), cordova);
                BitmapFactory.decodeStream(fileStream, null, options);
            } finally {
                if (fileStream != null) {
                    try {
                        fileStream.close();
                    } catch (IOException e) {
                        LOG.d(TAG, "Exception while closing file input stream.");
                    }
                }
            }


            //CB-2292: WTF? Why is the width null?
            if (options.outWidth == 0 || options.outHeight == 0) {
                return null;
            }

            // User didn't specify output dimensions, but they need orientation
            if (this.targetWidth <= 0 && this.targetHeight <= 0) {
                this.targetWidth = options.outWidth;
                this.targetHeight = options.outHeight;
            }

            // Setup target width/height based on orientation
            int rotatedWidth, rotatedHeight;
            boolean rotated= false;
            if (rotate == 90 || rotate == 270) {
                rotatedWidth = options.outHeight;
                rotatedHeight = options.outWidth;
                rotated = true;
            } else {
                rotatedWidth = options.outWidth;
                rotatedHeight = options.outHeight;
            }

            // determine the correct aspect ratio
            int[] widthHeight = calculateAspectRatio(rotatedWidth, rotatedHeight);


            // Load in the smallest bitmap possible that is closest to the size we want
            options.inJustDecodeBounds = false;
            options.inSampleSize = calculateSampleSize(rotatedWidth, rotatedHeight,  widthHeight[0], widthHeight[1]);
            Bitmap unscaledBitmap = null;
            try {
                fileStream = FileHelper.getInputStreamFromUriString(galleryUri.toString(), cordova);
                unscaledBitmap = BitmapFactory.decodeStream(fileStream, null, options);
            } finally {
                if (fileStream != null) {
                    try {
                        fileStream.close();
                    } catch (IOException e) {
                        LOG.d(TAG, "Exception while closing file input stream.");
                    }
                }
            }
            if (unscaledBitmap == null) {
                return null;
            }

            int scaledWidth = (!rotated) ? widthHeight[0] : widthHeight[1];
            int scaledHeight = (!rotated) ? widthHeight[1] : widthHeight[0];

            Bitmap scaledBitmap = Bitmap.createScaledBitmap(unscaledBitmap, scaledWidth, scaledHeight, true);
            if (scaledBitmap != unscaledBitmap) {
                unscaledBitmap.recycle();
                unscaledBitmap = null;
            }
            if (this.correctOrientation && (rotate != 0)) {
                Matrix matrix = new Matrix();
                matrix.setRotate(rotate);
                try {
                    scaledBitmap = Bitmap.createBitmap(scaledBitmap, 0, 0, scaledBitmap.getWidth(), scaledBitmap.getHeight(), matrix, true);
                    this.orientationCorrected = true;
                } catch (OutOfMemoryError oom) {
                    this.orientationCorrected = false;
                }
            }
            return scaledBitmap;
        }
        finally {
            // delete the temporary copy
            if (localFile != null) {
                localFile.delete();
            }
        }

    }

    /**
     * Maintain the aspect ratio so the resulting image does not look smooshed
     *
     * @param origWidth
     * @param origHeight
     * @return
     */
    public int[] calculateAspectRatio(int origWidth, int origHeight) {
        int newWidth = this.targetWidth;
        int newHeight = this.targetHeight;

        // If no new width or height were specified return the original bitmap
        if (newWidth <= 0 && newHeight <= 0) {
            newWidth = origWidth;
            newHeight = origHeight;
        }
        // Only the width was specified
        else if (newWidth > 0 && newHeight <= 0) {
            newHeight = (int)((double)(newWidth / (double)origWidth) * origHeight);
        }
        // only the height was specified
        else if (newWidth <= 0 && newHeight > 0) {
            newWidth = (int)((double)(newHeight / (double)origHeight) * origWidth);
        }
        // If the user specified both a positive width and height
        // (potentially different aspect ratio) then the width or height is
        // scaled so that the image fits while maintaining aspect ratio.
        // Alternatively, the specified width and height could have been
        // kept and Bitmap.SCALE_TO_FIT specified when scaling, but this
        // would result in whitespace in the new image.
        else {
            double newRatio = newWidth / (double) newHeight;
            double origRatio = origWidth / (double) origHeight;

            if (origRatio > newRatio) {
                newHeight = (newWidth * origHeight) / origWidth;
            } else if (origRatio < newRatio) {
                newWidth = (newHeight * origWidth) / origHeight;
            }
        }

        int[] retval = new int[2];
        retval[0] = newWidth;
        retval[1] = newHeight;
        return retval;
    }

    /**
     * Figure out what ratio we can load our image into memory at while still being bigger than
     * our desired width and height
     *
     * @param srcWidth
     * @param srcHeight
     * @param dstWidth
     * @param dstHeight
     * @return
     */
    public static int calculateSampleSize(int srcWidth, int srcHeight, int dstWidth, int dstHeight) {
        final float srcAspect = (float) srcWidth / (float) srcHeight;
        final float dstAspect = (float) dstWidth / (float) dstHeight;

        if (srcAspect > dstAspect) {
            return srcWidth / dstWidth;
        } else {
            return srcHeight / dstHeight;
        }
    }

    /**
     * Creates a cursor that can be used to determine how many images we have.
     *
     * @return a cursor
     */
    private Cursor queryImgDB(Uri contentStore) {
        return this.cordova.getActivity().getContentResolver().query(
                contentStore,
                new String[]{MediaStore.Images.Media._ID},
                null,
                null,
                null);
    }

    /**
     * Cleans up after picture taking. Checking for duplicates and that kind of stuff.
     *
     * @param newImage
     */
    private void cleanup(int imageType, Uri oldImage, Uri newImage, Bitmap bitmap) {
        if (bitmap != null) {
            bitmap.recycle();
        }

        // Clean up initial camera-written image file.
        (new File(FileHelper.stripFileProtocol(oldImage.toString()))).delete();

        checkForDuplicateImage(imageType);
        // Scan for the gallery to update pic refs in gallery
        if (this.saveToPhotoAlbum && newImage != null) {
            this.scanForGallery(newImage);
        }

        System.gc();
    }

    /**
     * @param type FILE_URI or DATA_URL
     */
    private void checkForDuplicateImage(int type) {
        int diff = 1;
        Uri contentStore = whichContentStore();
        Cursor cursor = queryImgDB(contentStore);
        int currentNumOfImages = cursor.getCount();

        if (type == FILE_URI && this.saveToPhotoAlbum) {
            diff = 2;
        }

        // delete the duplicate file if the difference is 2 for file URI or 1 for Data URL
        if ((currentNumOfImages - numPics) == diff) {
            cursor.moveToLast();
            int id = Integer.valueOf(cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media._ID)));
            if (diff == 2) {
                id--;
            }
            Uri uri = Uri.parse(contentStore + "/" + id);
            this.cordova.getActivity().getContentResolver().delete(uri, null, null);
            cursor.close();
        }
    }

    /**
     * Determine if we are storing the images in internal or external storage
     *
     * @return Uri
     */
    private Uri whichContentStore() {
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            return MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        } else {
            return MediaStore.Images.Media.INTERNAL_CONTENT_URI;
        }
    }

    /**
     * Compress bitmap using jpeg, convert to Base64 encoded string, and return to JavaScript.
     *
     * @param bitmap
     */
    public void processPicture(Bitmap bitmap, int encodingType) {
        ByteArrayOutputStream jpeg_data = new ByteArrayOutputStream();
        Bitmap.CompressFormat compressFormat = encodingType == JPEG ?
                Bitmap.CompressFormat.JPEG :
                Bitmap.CompressFormat.PNG;

        try {
            if (bitmap.compress(compressFormat, mQuality, jpeg_data)) {
                byte[] code = jpeg_data.toByteArray();
                byte[] output = Base64.encode(code, Base64.NO_WRAP);
                String js_out = new String(output);
                this.callbackContext.success(js_out);
                js_out = null;
                output = null;
                code = null;
            }
        } catch (Exception e) {
            this.failPicture("Error compressing image.");
        }
        jpeg_data = null;
    }

    /**
     * Send error message to JavaScript.
     *
     * @param err
     */
    public void failPicture(String err) {
        this.callbackContext.error(err);
    }

    private void scanForGallery(Uri newImage) {
        this.scanMe = newImage;
        if (this.conn != null) {
            this.conn.disconnect();
        }
        this.conn = new MediaScannerConnection(this.cordova.getActivity().getApplicationContext(), this);
        conn.connect();
    }

    public void onMediaScannerConnected() {
        try {
            this.conn.scanFile(this.scanMe.toString(), "image/*");
        } catch (IllegalStateException e) {
            LOG.e(TAG, "Can't scan file in MediaScanner after taking picture");
        }

    }

    public void onScanCompleted(String path, Uri uri) {
        this.conn.disconnect();
    }


    public void onRequestPermissionResult(int requestCode, String[] permissions,
                                          int[] grantResults) throws JSONException {
        for (int r : grantResults) {
            if (r == PackageManager.PERMISSION_DENIED) {
                this.callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, PERMISSION_DENIED_ERROR));
                return;
            }
        }
        switch (requestCode) {
            case TAKE_PIC_SEC:
                takePicture(this.destType, this.encodingType);
                break;
            case SAVE_TO_ALBUM_SEC:
                this.getImage(this.srcType, this.destType, this.encodingType);
                break;
        }
    }

    public Bundle onSaveInstanceState() {
        Bundle state = new Bundle();
        state.putInt("destType", this.destType);
        state.putInt("srcType", this.srcType);
        state.putInt("mQuality", this.mQuality);
        state.putInt("targetWidth", this.targetWidth);
        state.putInt("targetHeight", this.targetHeight);
        state.putInt("encodingType", this.encodingType);
        state.putInt("mediaType", this.mediaType);
        state.putInt("numPics", this.numPics);
        state.putBoolean("allowEdit", this.allowEdit);
        state.putBoolean("correctOrientation", this.correctOrientation);
        state.putBoolean("saveToPhotoAlbum", this.saveToPhotoAlbum);

        if (this.croppedUri != null) {
            state.putString("croppedUri", this.croppedUri.toString());
        }

        if (this.imageUri != null) {
            state.putString("imageUri", this.imageUri.getFileUri().toString());
        }

        return state;
    }

    public void onRestoreStateForActivityResult(Bundle state, CallbackContext callbackContext) {
        this.destType = state.getInt("destType");
        this.srcType = state.getInt("srcType");
        this.mQuality = state.getInt("mQuality");
        this.targetWidth = state.getInt("targetWidth");
        this.targetHeight = state.getInt("targetHeight");
        this.encodingType = state.getInt("encodingType");
        this.mediaType = state.getInt("mediaType");
        this.numPics = state.getInt("numPics");
        this.allowEdit = state.getBoolean("allowEdit");
        this.correctOrientation = state.getBoolean("correctOrientation");
        this.saveToPhotoAlbum = state.getBoolean("saveToPhotoAlbum");

        if (state.containsKey("croppedUri")) {
            this.croppedUri = Uri.parse(state.getString("croppedUri"));
        }

        if (state.containsKey("imageUri")) {
            //I have no idea what type of URI is being passed in
            this.imageUri = new CordovaUri(Uri.parse(state.getString("imageUri")));
        }

        this.callbackContext = callbackContext;
    }

    private String getFileNameFromUri(Uri uri) {
        String fullUri = uri.toString();
        String partial_path = fullUri.split("external_files")[1];
        File external_storage = Environment.getExternalStorageDirectory();
        String path = external_storage.getAbsolutePath() + partial_path;
        return path;

    }

}