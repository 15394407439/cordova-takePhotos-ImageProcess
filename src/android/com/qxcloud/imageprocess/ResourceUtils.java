package com.qxcloud.imageprocess;

import android.content.Context;

/**
 * CREATED BY:         heaton
 * CREATED DATE:       2017/9/18
 * CREATED TIME:       下午5:52
 * CREATED DESCRIPTION: 动态获取资源ID工具
 */

public class ResourceUtils {
    public static final String TYPE_ID="id";
    public static final String TYPE_COLOR="color";
    public static final String TYPE_DRAWABLE="drawable";
    public static final String TYPE_MIPMAP="mipmap";
    public static final String TYPE_STRING="string";
    public static final String TYPE_ANIM="anim";
    public static final String TYPE_ATTR="attr";
    public static final String TYPE_STYLEABLE="styleable";
    public static final String TYPE_LAYOUT="layout";
    public static int getIdByName(Context context, String className, String name) {
        String packageName = context.getPackageName();
        Class r = null;
        int id = 0;

        try {
            r = Class.forName(packageName + ".R");
            Class[] classes = r.getClasses();
            Class desireClass = null;

            for (int i = 0; i < classes.length; ++i) {
                if (classes[i].getName().split("\\$")[1].equals(className)) {
                    desireClass = classes[i];
                    break;
                }
            }

            if (desireClass != null) {
                id = desireClass.getField(name).getInt(desireClass);
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }

        return id;
    }

    public static int[] getIdsByName(Context context, String className, String name) {
        String packageName = context.getPackageName();
        Class r = null;
        int[] ids = null;

        try {
            r = Class.forName(packageName + ".R");

            Class[] classes = r.getClasses();
            Class desireClass = null;

            for (int i = 0; i < classes.length; ++i) {
                if (classes[i].getName().split("\\$")[1].equals(className)) {
                    desireClass = classes[i];
                    break;
                }
            }

            if ((desireClass != null) && (desireClass.getField(name).get(desireClass)) != null && (desireClass.getField(name).get(desireClass).getClass().isArray())) {
                ids = (int[]) desireClass.getField(name).get(desireClass);
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }

        return ids;
    }
}
