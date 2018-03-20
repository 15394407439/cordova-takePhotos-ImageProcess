package com.qxcloud.imageprocess.editAPI;


import com.qxcloud.imageprocess.utils.Logger;

import java.util.ArrayList;

/**
 * Created by cfh on 2017-09-11.
 * TODO
 */

public class EditImageAPI {

    /**
     * GeneralUtil 实例
     */
    private static EditImageAPI singletonGeneralUtil = null;
   private static ArrayList<EditImgInterface> arrayList = new ArrayList<EditImgInterface>();

    /**
     * 单例模式
     */
    public static EditImageAPI getInstance() {
        if (singletonGeneralUtil == null) {
            synchronized (EditImageAPI.class) {
                if (singletonGeneralUtil == null) {
                    singletonGeneralUtil = new EditImageAPI();
                }
            }
        }
        return singletonGeneralUtil;
    }

    /**
     * 注册
     * @param editImgInterface
     */
    public void  registerEditImg(EditImgInterface editImgInterface){
        if(null!=arrayList ){
            if(!arrayList.contains(editImgInterface)){
                arrayList.add(editImgInterface);
           }
        }
    }

    /**
     * 反注册
     * @param editImgInterface
     */
    public void  unRegisterEditImg(EditImgInterface editImgInterface){
        if(arrayList.contains(editImgInterface)){
            arrayList.remove(editImgInterface);
        }
    }

    /**
     * 发送通知
     * @param code
     * @param editImageMessage
     */
    public void post(int code,EditImageMessage editImageMessage){
        Logger.e("++++++++++++++arrayList.size():"+arrayList.size());
        for (int i=0;i<arrayList.size();i++){
            arrayList.get(i).onEditImgResult(code,editImageMessage);
        }
    }


}
