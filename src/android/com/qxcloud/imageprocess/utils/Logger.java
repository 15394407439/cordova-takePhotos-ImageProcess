package com.qxcloud.imageprocess.utils;

import android.util.Log;

/**
 * Created by cfh on 2017-09-07.
 * TODO
 */

public class Logger {
    public static  boolean LOG_ON = true;
    public static  void e(String log){
        if(LOG_ON)
        Log.e("EDIT_IMG",log);
    }
}
