package com.qxcloud.imageprocess.utils;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;

/**
 * CREATED BY:         heaton
 * CREATED DATE:       2017/9/27
 * CREATED TIME:       下午2:08
 * CREATED DESCRIPTION:
 */

public class PermissionUtils {
    public static void requestPermissions(Activity activity, int requestCode, String[] permissions) {
        if (activity == null) {
            return;
        }
        ActivityCompat.requestPermissions(activity, permissions, requestCode);
    }

    public static boolean hasPermission(Context context, String permission) {
        if(context == null){
            return false;
        }
        return ActivityCompat.checkSelfPermission(context,permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasPermissions(Context context, String[] permissions) {
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(context,permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
}
