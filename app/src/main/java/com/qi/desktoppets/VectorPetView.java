package com.qi.desktoppets;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * 矢量桌宠（样机：Grok / 小格）
 * ---------------------------------------------------------------
 * 和电脑版完全同一套画法：坐标系沿用 Windows 版的 240x240 画布，
 * 环的半径、斜杠的四个点、盘子的位置都取自 b_orbit.txt 里的原始参数。
 * 区别只是每秒 30 次重画 + 用 Matrix 做连续变换，而不是换图片。
 *
 * 动画（全部逐帧连续变化，不是翻页）：
 *   · 呼吸：整体缩放 ±2%
 *   · 浮动：上下 sin 曲线
 *   · 眨眼：眼睛高度在 ~120ms 内压到 10% 再弹回（真正的插值）
 *   · 走动倾斜：按速度方向轻微旋转
 *   · 蹦跳：挤压拉伸 squash & stretch + 位移
 *   · 转圈：整只真旋转
 *   · 被戳：抖一下 + 爱心
 *   · 粒子：几颗小星绕着自己飘
 */
public class VectorPetView extends PetView {

    private static final float CANVAS = 240f;      // 与 Windows 版一致的逻辑画布

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eye = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path ring = new Path();
    private final Path slash = new Path();
    private final RectF oval = new RectF();
    private final Matrix m = new Matrix();
    private final float d;
    private final float scale;
    private final java.util.Random rnd = new java.util.Random();

    private long t = 0;
    private float blink = 0f;          // 1 = 眼睛完全闭上
    private long nextBlink;
    private float squash = 1f;
    private float squashVel = 0f;
    private float tilt = 0f;
    private float spin = 0f;
    private float spinTarget = 0f;
    private float[] stars = new float[12];   // x,y,相位 各 3 个 * 4 颗

    public VectorPetView(Context c, String[] lines, int startX, int startY, Listener l) {
        super(c, new Bitmap[12], lines, startX, startY, l);   // 帧全 null：基类不画图，只保留气泡/爱心/移动
        this.d = c.getResources().getDisplayMetrics().density;
        this.scale = viewWidth() / CANVAS;
        eye.setColor(Color.parseColor("#101014"));
        for (int i = 0; i < 4; i++) {
            stars[i * 3] = 20 + rnd.nextInt(200);
            stars[i * 3 + 1] = 20 + rnd.nextInt(200);
            stars[i * 3 + 2] = rnd.nextFloat() * 6.28f;
        }
        nextBlink = System.currentTimeMillis() + 1500 + rnd.nextInt(2500);
    }

    @Override
    public void tick(int screenW) {
        super.tick(screenW);                 // 走动、气泡计时、随机台词
        t++;

        // 眨眼：真正的插值（0 -> 1 -> 0），不是换图片
        long now = System.currentTimeMillis();
        if (now > nextBlink && blink == 0f) {
            blink = 0.001f;
            nextBlink = now + 2000 + rnd.nextInt(3000);
        }
        if (blink > 0f) {
            blink += 0.22f;
            if (blink >= 2f) blink = 0f;
            else if (blink > 1f) blink = 2f - blink;      // 回来
        }

        // 挤压拉伸：带阻尼的弹簧（蹦跳用）
        squashVel += (1f - squash) * 0.18f;
        squashVel *= 0.82f;
        squash += squashVel;

        // 走动倾斜 + 转圈
        float vx = getX();
        tilt = (float) Math.sin(t / 9.0) * 2.2f;
        if (spinTarget > 0f) {
            spin += 9f;
            spinTarget -= 9f;
            if (spinTarget <= 0f) { spinTarget = 0f; spin = 0f; }
        }
        invalidate();
    }

    public void bounce() {
        squashVel = 0.16f;
    }

    public void spinOnce() {
        spinTarget = 360f;
    }

    private void buildShapes() {
        // 环：外圆 R=90 / 内圆 R=58，中心 (120,122) —— 与 Windows 版 b_orbit.txt 一致
        ring.reset();
        ring.setFillType(Path.FillType.EVEN_ODD);
        ring.addCircle(120f, 122f, 90f, Path.Direction.CW);
        ring.addCircle(120f, 122f, 58f, Path.Direction.CCW);
        // 斜杠：四个点 (176,44) (196,60) (64,200) (44,184) —— 同一组坐标
        slash.reset();
        slash.moveTo(176f, 44f);
        slash.lineTo(196f, 60f);
        slash.lineTo(64f, 200f);
        slash.lineTo(44f, 184f);
        slash.close();
    }

    @Override
    protected void onDraw(Canvas c) {
        buildShapes();

        int w = viewWidth();
        int bh = (int) (40 * d);
        float cx = 120f, cy = 122f;

        // 渐变：和 Windows 版同一组色（深灰到近黑）
        fill.setShader(new LinearGradient(44f, 44f, 200f, 200f,
                Color.parseColor("#2A2A33"), Color.parseColor("#101014"), Shader.TileMode.CLAMP));

        c.save();
        // 平移到底部宠物区，缩放，再做整体变换
        c.translate(0, bh);
        c.scale(scale, scale);
        m.reset();
        m.postTranslate(-cx, -cy);
        m.postScale(1f + (squash - 1f) * 0.5f, squash);   // 挤压拉伸
        m.postRotate(tilt + spin);
        m.postTranslate(cx, cy);
        c.concat(m);

        // 1) 斜杠（画在脸的后面，只露出盘外的两段）
        c.drawPath(slash, fill);
        // 2) 白脸盘 R=64
        fill.setShader(null);
        fill.setColor(Color.parseColor("#FBFBFD"));
        c.drawCircle(cx, cy, 64f, fill);
        // 3) 黑环（在最前）
        fill.setShader(new LinearGradient(44f, 44f, 200f, 200f,
                Color.parseColor("#2A2A33"), Color.parseColor("#101014"), Shader.TileMode.CLAMP));
        c.drawPath(ring, fill);
        // 4) 眼睛（白底 + 黑珠，随眨眼压扁）
        float eyeH = 1f - Math.min(1f, blink);
        fill.setColor(Color.parseColor("#FBFBFD"));
        oval.set(88f, 100f - 12f * eyeH, 114f, 100f + 12f * eyeH);
        c.drawOval(oval, fill);
        oval.set(126f, 100f - 12f * eyeH, 152f, 100f + 12f * eyeH);
        c.drawOval(oval, fill);
        if (eyeH > 0.18f) {
            eye.setAlpha((int) (255 * Math.min(1f, eyeH * 1.4f)));
            oval.set(97f, 106f - 5f * eyeH, 106f, 106f + 5f * eyeH);
            c.drawOval(oval, eye);
            oval.set(135f, 106f - 5f * eyeH, 144f, 106f + 5f * eyeH);
            c.drawOval(oval, eye);
        }
        // 5) 嘴（弧线）
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(3.4f);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setColor(Color.parseColor("#101014"));
        oval.set(105f, 122f, 135f, 148f);
        c.drawArc(oval, 25f, 130f, false, line);

        // 6) 粒子（几颗小星在绕圈飘）
        fill.setShader(null);
        for (int i = 0; i < 4; i++) {
            float a = stars[i * 3 + 2] + t / 26f;
            float rad = 78f + (float) Math.sin(t / 17f + i) * 12f;
            float px = cx + (float) Math.cos(a) * rad;
            float py = cy + (float) Math.sin(a) * rad;
            float alpha = 120 + (int) (100 * Math.abs(Math.sin(t / 20f + i)));
            fill.setColor(Color.argb(alpha, 150, 150, 170));
            c.drawCircle(px, py, 2.6f, fill);
        }
        c.restore();

        super.onDraw(c);   // 基类画气泡和爱心
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent e) {
        boolean r = super.onTouchEvent(e);
        if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP) {
            bounce();
            if (rnd.nextInt(3) == 0) spinOnce();
        }
        return r;
    }
}
