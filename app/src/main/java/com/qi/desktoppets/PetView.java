package com.qi.desktoppets;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.Random;

/**
 * 一只桌宠：12 帧姿势动画 + 走动 + 气泡 + 戳它/拖动/长按。
 * 帧顺序（缺帧时自动回退到 idle）：
 *   0 idle 1 blink 2 think 3 ping 4 bounce 5 spin
 *   6 wobble 7 pat 8 sleep 9 stretch 10 look 11 slide
 */
public class PetView extends View {

    public interface Listener { void onLongPress(PetView v); }

    private static final int F_IDLE = 0, F_BLINK = 1, F_THINK = 2, F_PING = 3, F_BOUNCE = 4,
            F_SPIN = 5, F_WOBBLE = 6, F_PAT = 7, F_SLEEP = 8, F_STRETCH = 9, F_LOOK = 10, F_SLIDE = 11;

    private final Bitmap[] frames;
    private final String[] lines;
    private final Listener listener;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bubble = new RectF();
    private final Random rnd = new Random();

    private final int petSize;
    private final int bubbleH;
    private final float density;

    public float wx, wy;
    private float vx;
    private int frame;
    private long lastPress = System.currentTimeMillis();
    public float dragDx, dragDy;

    private int state = F_IDLE;
    private long stateUntil;
    private int stateFrame;
    private int sleepFrameCounter;

    private String saying = "";
    private long hideAt;
    private boolean showHeart;
    private boolean heartClearScheduled;
    private long downAt;
    private float downX, downY;
    private boolean moved;
    private boolean sleeping;

    public PetView(Context c, Bitmap[] frames, String[] lines, int startX, int startY, Listener l) {
        super(c);
        this.frames = frames;
        this.lines = lines;
        this.listener = l;
        this.density = c.getResources().getDisplayMetrics().density;
        this.petSize = (int) (110 * density);
        this.bubbleH = (int) (40 * density);
        this.wx = startX;
        this.wy = startY;
        this.vx = (rnd.nextBoolean() ? 1 : -1) * (1.1f + rnd.nextFloat()) * density;
        text.setColor(Color.parseColor("#39405A"));
        text.setTextSize(13 * density);
        text.setTextAlign(Paint.Align.CENTER);
        say("!");
    }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(petSize, petSize + bubbleH);
    }

    public int viewWidth() { return petSize; }

    public void say(String s) { saying = s; hideAt = System.currentTimeMillis() + 3000; invalidate(); }

    public void sayRandom() {
        if (lines != null && lines.length > 0) say(lines[rnd.nextInt(lines.length)]);
    }

    private void enter(int newState, long ms) {
        if (!hasFrame(newState) && newState != F_IDLE) newState = F_IDLE;
        state = newState;
        stateUntil = System.currentTimeMillis() + ms;
        stateFrame = 0;
    }

    private boolean hasFrame(int idx) {
        return frames != null && idx >= 0 && idx < frames.length && frames[idx] != null;
    }

    private Bitmap currentBitmap() {
        if (hasFrame(state)) return frames[state];
        if (hasFrame(F_IDLE)) return frames[F_IDLE];
        for (Bitmap b : frames) if (b != null) return b;
        return null;
    }

    /** 由 Service 每 33ms 调一次。 */
    public void tick(int screenW) {
        frame++;
        long now = System.currentTimeMillis();

        if (state == F_IDLE) {
            if (sleeping) {
                // 睡着时不动、不说话
                if (frame % 120 == 0) sleepFrameCounter++;
            } else {
                wx += vx;
                if (wx < 0) { wx = 0; vx = Math.abs(vx); }
                if (wx > screenW - petSize) { wx = screenW - petSize; vx = -Math.abs(vx); }
                if (now - lastPress > 60000) {
                    sleeping = true;
                    enter(F_SLEEP, 1000000);
                } else if (now - lastPress > 4000 && rnd.nextInt(45) == 0) {
                    pickRandomAction();
                }
                if (frame % 200 == 0) sayRandom();
            }
        } else {
            // 正在做一个动作：轻微前移，动作结束回到待机
            wx += vx * 0.35f;
            if (wx < 0) { wx = 0; vx = Math.abs(vx); }
            if (wx > screenW - petSize) { wx = screenW - petSize; vx = -Math.abs(vx); }
            if (now > stateUntil) {
                state = F_IDLE;
                stateFrame = 0;
            }
        }

        if (saying != null && !saying.isEmpty() && now > hideAt) saying = "";
        invalidate();
    }

    private void pickRandomAction() {
        lastPress = System.currentTimeMillis();
        int r = rnd.nextInt(100);
        if (r < 34) enter(F_BLINK, 420);
        else if (r < 52) enter(F_LOOK, 1400);
        else if (r < 66) enter(F_BOUNCE, 900);
        else if (r < 76) enter(F_WOBBLE, 1100);
        else if (r < 84) enter(F_STRETCH, 1300);
        else if (r < 92) enter(F_SPIN, 1200);
        else if (r < 97) enter(F_THINK, 1600);
        else enter(F_SLIDE, 1000);
    }

    public int bob() {
        if (state == F_BOUNCE) return (int) (-Math.abs(Math.sin(frame / 5.0)) * 10 * density);
        if (sleeping) return 0;
        return (int) (Math.sin(frame / 12.0) * 3 * density / 2);
    }

    @Override protected void onDraw(Canvas c) {
        Bitmap bmp = currentBitmap();
        if (bmp != null) {
            RectF dst = new RectF(0, bubbleH, petSize, bubbleH + petSize);
            c.drawBitmap(bmp, null, dst, p);
        }
        if (showHeart) {
            p.setColor(Color.parseColor("#FF5A8A"));
            text.setTextSize(20 * density);
            c.drawText("\u2665", petSize - 14 * density, bubbleH + 20 * density, text);
            text.setTextSize(13 * density);
        }
        if (saying != null && !saying.isEmpty()) {
            p.setColor(Color.WHITE);
            bubble.set(2, 2, petSize - 2, bubbleH - 2);
            c.drawRoundRect(bubble, 10 * density, 10 * density, p);
            p.setColor(Color.parseColor("#DFE3EE"));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.2f * density);
            c.drawRoundRect(bubble, 10 * density, 10 * density, p);
            p.setStyle(Paint.Style.FILL);
            c.drawText(saying, petSize / 2f, bubbleH / 2f + 4 * density, text);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downAt = System.currentTimeMillis();
                downX = e.getRawX();
                downY = e.getRawY();
                moved = false;
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = e.getRawX() - downX;
                float dy = e.getRawY() - downY;
                if (Math.abs(dx) > 6 || Math.abs(dy) > 6) moved = true;
                if (moved) { dragDx += dx; dragDy += dy; downX = e.getRawX(); downY = e.getRawY(); }
                return true;
            }
            case MotionEvent.ACTION_UP:
                if (!moved) {
                    long held = System.currentTimeMillis() - downAt;
                    if (held < 350) {
                        wake();
                        lastPress = System.currentTimeMillis();
                        enter(F_PAT, 900);
                        showHeart = true;
                        sayRandom();
                        if (!heartClearScheduled) {
                            heartClearScheduled = true;
                            postDelayed(() -> { showHeart = false; heartClearScheduled = false; invalidate(); }, 1000);
                        }
                    } else if (listener != null) {
                        listener.onLongPress(this);
                    }
                }
                return true;
        }
        return super.onTouchEvent(e);
    }

    private void wake() {
        sleeping = false;
        state = F_IDLE;
        stateUntil = 0;
    }

    public void greet() {
        if (sleeping) return;
        sayRandom();
        lastPress = System.currentTimeMillis();
    }

    public float getX() { return wx; }
    public float getBaseY() { return wy; }
}