package com.qxcloud.imageprocess;

import android.content.Context;
import android.widget.Toast;

/**
 * CREATED BY:         heaton
 * CREATED DATE:       2017/9/18
 * CREATED TIME:       下午6:08
 * CREATED DESCRIPTION:
 */

public class ToastUtils {
    public static void toastMessage(Context context,String message){
        Toast.makeText(context,message,Toast.LENGTH_SHORT).show();
    }
}
