package com.qi.desktoppets;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import java.util.Random;

/** 一只桌宠：画自己、走自己、戳它会有反应。 */
public class PetView extends View {

    public interface Listener { void onLongPress(PetView v); }

    private final Bitmap bmp;
    private final String[] lines;
    private final Listener listener;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bubble = new RectF();
    private final Random rnd = new Random();

    private final int petSize;
    private final int bubbleH;
    public float wx, wy;              // 窗口在屏幕上的位置（由 Service 写回 LayoutParams）
    private float vx;
    private int frame;
    public float dragDx, dragDy;      // 拖动产生的增量，由 Service 取走

    private String saying = "";
    private long hideAt;
    private boolean showHeart;
    private long downAt;
    private float downX, downY;
    private boolean moved;

    public PetView(Context c, Bitmap bmp, String[] lines, int startX, int startY, Listener l) {
        super(c);
        this.bmp = bmp;
        this.lines = lines;
        this.listener = l;
        float d = c.getResources().getDisplayMetrics().density;
        this.petSize = (int) (110 * d);
        this.bubbleH = (int) (40 * d);
        this.wx = startX;
        this.wy = startY;
        this.vx = (rnd.nextBoolean() ? 1 : -1) * (1.2f + rnd.nextFloat()) * d;
        text.setColor(Color.parseColor("#39405A"));
        text.setTextSize(13 * d);
        text.setTextAlign(Paint.Align.CENTER);
        say("!");
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(petSize, petSize + bubbleH);
    }

    public void say(String s) {
        saying = s;
        hideAt = System.currentTimeMillis() + 3000;
        invalidate();
    }

    public void sayRandom() {
        if (lines != null && lines.length > 0) say(lines[rnd.nextInt(lines.length)]);
    }

    /** 由 Service 每 33ms 调一次。 */
    public void tick(int screenW) {
        frame++;
        wx += vx;
        if (wx < 0) { wx = 0; vx = Math.abs(vx); }
        if (wx > screenW - petSize) { wx = screenW - petSize; vx = -Math.abs(vx); }
        if (frame % 180 == 0) sayRandom();
        if (saying != null && !saying.isEmpty() && System.currentTimeMillis() > hideAt) {
            saying = "";
        }
        invalidate();
    }

    public int bob() {
        return (int) (Math.sin(frame / 12.0) * 3);
    }

    public int viewWidth() { return petSize; }

    public float baseY() { return wy; }

    @Override
    protected void onDraw(Canvas c) {
        float d = getResources().getDisplayMetrics().density;
        if (bmp != null) {
            RectF dst = new RectF(0, bubbleH, petSize, bubbleH + petSize);
            c.drawBitmap(bmp, null, dst, p);
        }
        if (showHeart) {
            p.setColor(Color.parseColor("#FF5A8A"));
            text.setTextSize(20 * d);
            c.drawText("\u2665", petSize - 14 * d, bubbleH + 20 * d, text);
            text.setTextSize(13 * d);
        }
        if (saying != null && !saying.isEmpty()) {
            p.setColor(Color.WHITE);
            bubble.set(2, 2, petSize - 2, bubbleH - 2);
            c.drawRoundRect(bubble, 10 * d, 10 * d, p);
            p.setColor(Color.parseColor("#DFE3EE"));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.2f * d);
            c.drawRoundRect(bubble, 10 * d, 10 * d, p);
            p.setStyle(Paint.Style.FILL);
            c.drawText(saying, petSize / 2f, bubbleH / 2f + 4 * d, text);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downAt = System.currentTimeMillis();
                downX = e.getRawX();
                downY = e.getRawY();
                moved = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = e.getRawX() - downX;
                float dy = e.getRawY() - downY;
                if (Math.abs(dx) > 6 || Math.abs(dy) > 6) moved = true;
                if (moved) {
                    dragDx += dx;
                    dragDy += dy;
                    downX = e.getRawX();
                    downY = e.getRawY();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!moved && System.currentTimeMillis() - downAt < 350) {
                    showHeart = true;
                    sayRandom();
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        showHeart = false;
                        invalidate();
                    }, 1000);
                } else if (!moved) {
                    if (listener != null) listener.onLongPress(this);
                }
                return true;
        }
        return super.onTouchEvent(e);
    }
}