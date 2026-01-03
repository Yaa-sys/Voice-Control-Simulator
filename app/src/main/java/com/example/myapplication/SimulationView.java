package com.example.myapplication;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class SimulationView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Bitmap ballBitmap;

    // ✅ 兩張背景
    private Bitmap bgFlatBitmap;     // 平面背景
    private Bitmap bgSlopeBitmap;    // 斜面/會員背景

    // ✅ 斜坡 overlay（去背）
    private Bitmap slopeBitmap;

    // 縮放後
    private Bitmap bgScaled;
    private Bitmap slopeScaled;

    // 背景捲動
    private float bgX = 0f;

    // 速度（private，用 getter/setter）
    private float currentSpeed = 0f;

    // 顯示控制
    private boolean useSlopeBackground = false; // false=平面背景, true=斜面背景
    private boolean showSlopeOverlay = false;   // 平面 false，斜面/會員 true

    // 球視覺旋轉
    private float ballRotation = 0f;

    // ===== 斜坡 overlay 參數（你原本那套保留）=====
    private float slopeHeightRatio = 0.45f;          // 斜坡高度佔螢幕比例
    private final float SLOPE_WIDTH_SCALE = 1.2f;    // 左右放大避免縫
    private final float SLOPE_VERTICAL_OFFSET = 80f; // 往下推貼底（可調）

    public SimulationView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        Bitmap b = BitmapFactory.decodeResource(getResources(), R.drawable.ball);
        ballBitmap = Bitmap.createScaledBitmap(b, 150, 150, true);

        bgFlatBitmap  = BitmapFactory.decodeResource(getResources(), R.drawable.background_flat);
        bgSlopeBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.background_slope);

        slopeBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.road_overlay);
    }

    // ===== 給 Activity 控制 =====
    public void setSpeed(float speed) {
        currentSpeed = Math.max(0f, speed);
    }

    public float getSpeed() {
        return currentSpeed;
    }

    /** 斜坡視覺（球抬頭） */
    public void setSlopeMode(boolean isSlope) {
        ballRotation = isSlope ? -15f : 0f;
        invalidate();
    }

    /** 平面/斜面背景切換 */
    public void setBackgroundMode(boolean useSlopeBg) {
        this.useSlopeBackground = useSlopeBg;
        if (getWidth() > 0 && getHeight() > 0) {
            rebuildBackgroundScaled(getWidth(), getHeight());
            invalidate();
        }
    }

    /** 是否顯示斜坡 overlay */
    public void setShowSlopeOverlay(boolean show) {
        this.showSlopeOverlay = show;
        invalidate();
    }

    // ===== 重新縮放背景（保持比例）=====
    private void rebuildBackgroundScaled(int w, int h) {
        Bitmap src = useSlopeBackground ? bgSlopeBitmap : bgFlatBitmap;
        if (src == null) return;

        float screenRatio = (float) w / (float) h;
        float bgRatio = (float) src.getWidth() / (float) src.getHeight();

        int finalBgW, finalBgH;
        if (screenRatio > bgRatio) {
            finalBgW = w;
            finalBgH = (int) (w / bgRatio);
        } else {
            finalBgH = h;
            finalBgW = (int) (h * bgRatio);
        }
        bgScaled = Bitmap.createScaledBitmap(src, finalBgW, finalBgH, true);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;

        // ✅ 背景依模式縮放（平面/斜面）
        rebuildBackgroundScaled(w, h);

        // ✅ 斜坡 overlay 縮放
        int finalSlopeW = (int) (w * SLOPE_WIDTH_SCALE);
        int finalSlopeH = (int) (h * slopeHeightRatio);
        slopeScaled = Bitmap.createScaledBitmap(slopeBitmap, finalSlopeW, finalSlopeH, true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bgScaled == null || ballBitmap == null) return;

        int viewW = getWidth();
        int viewH = getHeight();

        // ===== 1) 畫背景（水平循環捲動）=====
        bgX -= currentSpeed;

        int bgWidth = bgScaled.getWidth();
        while (bgX <= -bgWidth) bgX += bgWidth;
        while (bgX > 0) bgX -= bgWidth;

        float x = bgX;
        while (x < viewW) {
            canvas.drawBitmap(bgScaled, x, 0, paint);
            x += bgWidth;
        }

        // ===== 2) 畫斜坡 overlay（只在斜面/會員顯示）=====
        float slopeY = 0f;
        if (showSlopeOverlay && slopeScaled != null) {
            float slopeX = (viewW - slopeScaled.getWidth()) / 2f;
            slopeY = (viewH - slopeScaled.getHeight()) + SLOPE_VERTICAL_OFFSET;
            canvas.drawBitmap(slopeScaled, slopeX, slopeY, paint);
        }

        // ===== 3) 畫球 =====
        float ballX = (viewW / 2f) - (ballBitmap.getWidth() / 2f);

        float ballY;
        if (showSlopeOverlay && slopeScaled != null) {
            // 斜面/會員：球放在斜坡圖上方某個位置（依你的素材調）
            float offset = slopeScaled.getHeight() * 0.35f;
            ballY = slopeY + offset;
        } else {
            // 平面：固定位置
            ballY = viewH - 250;
        }

        canvas.save();
        canvas.rotate(ballRotation,
                ballX + ballBitmap.getWidth() / 2f,
                ballY + ballBitmap.getHeight() / 2f);
        canvas.drawBitmap(ballBitmap, ballX, ballY, paint);
        canvas.restore();

        invalidate();
    }
}
