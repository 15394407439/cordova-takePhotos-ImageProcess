package com.qxcloud.imageprocess.operate;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.qxcloud.imageprocess.utils.CameraUtils;


public class ReferenceLine extends View {

    private Paint mLinePaint;

    public ReferenceLine(Context context) {
        super(context);
        init();
    }

    public ReferenceLine(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ReferenceLine(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mLinePaint = new Paint();
        mLinePaint.setAntiAlias(true);
        mLinePaint.setColor(Color.parseColor("#45e0e0e0"));
        mLinePaint.setStrokeWidth(3);
    }


    @Override
    protected void onDraw(Canvas canvas) {
        int screenWidth = CameraUtils.getScreenWH(getContext()).widthPixels;
        int screenHeight = CameraUtils.getScreenWH(getContext()).heightPixels;

        int width = screenWidth / 8;
        int height = screenHeight / 5;

        canvas.drawLine(width, 0, width, screenHeight, mLinePaint);//左
        canvas.drawLine(screenWidth - (width*2), 0, screenWidth - (width*2), screenHeight, mLinePaint);//右
        canvas.drawLine(0, height, screenWidth, height, mLinePaint);//上
        canvas.drawLine(0, screenHeight - height , screenWidth, screenHeight - height , mLinePaint);
    }


}
