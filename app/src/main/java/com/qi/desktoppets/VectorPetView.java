package com.qi.desktoppets;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * 矢量桌宠（样机：Grok / 小格）—— 五官逐值照搬 Windows 版 XAML
 * -----------------------------------------------------------------
 * 环: 外圆 R=90 / 内圆 R=58 @ (120,122)      来自 b_orbit.txt
 * 斜杠: (176,44)(196,60)(64,200)(44,184)      来自 b_orbit.txt
 * 身体: 浅色圆盘 R=64 @ (120,122)             来自 b_geometry.txt
 * 眼睛(睁): 深色椭圆 24x27 @ (92,100.5) (124,100.5)
 *          白色高光 9x9 @ (96,104) (128,104)；小白点 4x4 @ (110,117) (142,117)
 * 眼睛(笑): 弧 M -11,3 Q 0,-9 11,3  @ (104,115) (136,115)，粗 4
 * 嘴(笑):   弧 M -9,0 Q 0,9 9,0     @ (120,138)，粗 3.2
 * 嘴(张):   椭圆 12x14 @ (114,131)
 * 腮红:     椭圆 24x12 @ (73,120) (143,120)
 */
public class VectorPetView extends PetView {

    private static final float CANVAS = 240f;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path ring = new Path();
    private final Path slash = new Path();
    private final RectF r = new RectF();
    private final Matrix m = new Matrix();
    private final float d;
    private final float scale;
    private final java.util.Random rnd = new java.util.Random();

    private long t = 0;
    private float blink = 0f;              // 0=睁 1=闭
    private long nextBlink;
    private float squash = 1f, squashVel = 0f;
    private float tilt = 0f, spin = 0f, spinTarget = 0f;
    private boolean happy = false;
    private float happyUntil = 0f;
    private float blushAlpha = 0f;
    private final float[] stars = new float[12];

    public VectorPetView(Context c, String[] lines, int startX, int startY, Listener l) {
        super(c, new Bitmap[12], lines, startX, startY, l);
        this.d = c.getResources().getDisplayMetrics().density;
        this.scale = viewWidth() / CANVAS;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setColor(Color.parseColor("#FF101014"));
        for (int i = 0; i < 4; i++) {
            stars[i * 3] = rnd.nextFloat() * 6.28f;
            stars[i * 3 + 1] = 74f + rnd.nextInt(16);
            stars[i * 3 + 2] = rnd.nextFloat() * 4f;
        }
        nextBlink = System.currentTimeMillis() + 1200 + rnd.nextInt(2500);
    }

    @Override
    public void tick(int screenW) {
        super.tick(screenW);
        t++;
        long now = System.currentTimeMillis();

        // 眨眼：和电脑版一样驱动 EyeScale.ScaleY（这里是插值）
        if (now > nextBlink && blink == 0f) {
            blink = 0.001f;
            nextBlink = now + 1800 + rnd.nextInt(3200);
        }
        if (blink > 0f) {
            blink += 0.24f;
            if (blink >= 2f) blink = 0f;
            else if (blink > 1f) blink = 2f - blink;
        }
        // 弹簧挤压拉伸
        squashVel += (1f - squash) * 0.18f;
        squashVel *= 0.82f;
        squash += squashVel;
        // 走动倾斜 / 真旋转
        tilt = (float) Math.sin(t / 9.0) * 2.2f;
        if (spinTarget > 0f) {
            float step = Math.min(12f, spinTarget);
            spin += step; spinTarget -= step;
            if (spinTarget <= 0f) { spinTarget = 0f; spin = 0f; }
        }
        // 笑眼与腮红淡入淡出
        if (now > happyUntil) happy = false;
        float target = happy ? 1f : 0f;
        blushAlpha += (target - blushAlpha) * 0.12f;
        invalidate();
    }

    public void bounce() { squashVel = 0.16f; }

    public void cheer() { happy = true; happyUntil = System.currentTimeMillis() + 900; }

    private void shapes() {
        ring.reset();
        ring.setFillType(Path.FillType.EVEN_ODD);
        ring.addCircle(120f, 122f, 90f, Path.Direction.CW);
        ring.addCircle(120f, 122f, 58f, Path.Direction.CCW);
        slash.reset();
        slash.moveTo(176f, 44f);
        slash.lineTo(196f, 60f);
        slash.lineTo(64f, 200f);
        slash.lineTo(44f, 184f);
        slash.close();
    }

    @Override
    protected void onDraw(Canvas c) {
        shapes();
        int w = viewWidth();
        int bh = (int) (40 * d);
        float cx = 120f, cy = 122f;

        c.save();
        c.translate(0, bh);
        c.scale(scale, scale);
        m.reset();
        m.postTranslate(-cx, -cy);
        m.postScale(1f + (squash - 1f) * 0.5f, squash);
        m.postRotate(tilt + spin);
        m.postTranslate(cx, cy);
        c.concat(m);

        // 1) 斜杠（在身体后面）
        fill.setShader(new LinearGradient(44f, 44f, 200f, 200f,
                Color.parseColor("#FF2A2A33"), Color.parseColor("#FF101014"), Shader.TileMode.CLAMP));
        c.drawPath(slash, fill);

        // 2) 浅色身体圆盘 R=64
        fill.setShader(null);
        fill.setColor(Color.parseColor("#FFFBFBFD"));
        c.drawCircle(cx, cy, 64f, fill);

        // 3) 黑环（最前）
        fill.setShader(new LinearGradient(44f, 44f, 200f, 200f,
                Color.parseColor("#FF2A2A33"), Color.parseColor("#FF101014"), Shader.TileMode.CLAMP));
        c.drawPath(ring, fill);
        fill.setShader(null);

        // 4) 腮红（点它的时候淡入）
        if (blushAlpha > 0.02f) {
            for (float bx : new float[] { 73f, 143f }) {
                r.set(bx, 120f, bx + 24f, 132f);
                fill.setShader(new RadialGradient(bx + 12f, 126f, 12f,
                        Color.argb((int) (110 * blushAlpha), 255, 140, 150),
                        Color.argb(0, 255, 140, 150), Shader.TileMode.CLAMP));
                c.drawOval(r, fill);
            }
            fill.setShader(null);
        }

        float eyeScaleY = 1f - Math.min(1f, blink);
        float[] ex = { 92f, 124f };
        if (happy) {
            // 笑眼：两条弯月弧线
            for (float x : new float[] { 104f, 136f }) {
                Path p = new Path();
                p.moveTo(x - 11f, 115f + 3f);
                p.quadTo(x, 115f - 9f, x + 11f, 115f + 3f);
                stroke.setStrokeWidth(4f);
                c.drawPath(p, stroke);
            }
        } else {
            for (float x : ex) {
                float midY = 114f;                       // 眼睛中心 y = 100.5 + 13.5
                float halfH = 13.5f * Math.max(0.08f, eyeScaleY);
                // 深色眼
                r.set(x, midY - halfH, x + 24f, midY + halfH);
                fill.setShader(new LinearGradient(x + 4.8f, midY - halfH, x + 19.2f, midY + halfH,
                        Color.parseColor("#FF1C1C22"), Color.parseColor("#FF101014"), Shader.TileMode.CLAMP));
                c.drawOval(r, fill);
                fill.setShader(null);
                if (eyeScaleY > 0.25f) {
                    // 白色高光 9x9
                    r.set(x + 4f, 104f - 4.5f * eyeScaleY + 4.5f, x + 13f, 113f);
                    fill.setColor(Color.argb(242, 255, 255, 255));
                    c.drawOval(r, fill);
                    // 小白点 4x4
                    fill.setColor(Color.argb(153, 255, 255, 255));
                    r.set(x + 18f, 117f, x + 22f, 121f);
                    c.drawOval(r, fill);
                }
            }
        }

        // 5) 嘴：微笑弧（说话时改成张小口）
        boolean talking = false;
        try { talking = false; } catch (Exception ignored) { }
        if (talking) {
            fill.setColor(Color.parseColor("#FF101014"));
            r.set(114f, 131f, 126f, 145f);
            c.drawOval(r, fill);
        } else {
            Path mouth = new Path();
            mouth.moveTo(111f, 138f);
            mouth.quadTo(120f, 147f, 129f, 138f);
            stroke.setStrokeWidth(3.2f);
            c.drawPath(mouth, stroke);
        }

        // 6) 粒子
        fill.setShader(null);
        for (int i = 0; i < 4; i++) {
            float a = stars[i * 3] + t / 26f;
            float rad = stars[i * 3 + 1] + (float) Math.sin(t / 17f + i) * 10f;
            float px = cx + (float) Math.cos(a) * rad;
            float py = cy + (float) Math.sin(a) * rad;
            int alpha = 110 + (int) (90 * Math.abs(Math.sin(t / 20f + i)));
            fill.setColor(Color.argb(alpha, 150, 150, 170));
            c.drawCircle(px, py, 2.6f, fill);
        }
        c.restore();

        super.onDraw(c);   // 气泡 + 爱心
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent e) {
        boolean r2 = super.onTouchEvent(e);
        if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP) {
            bounce();
            cheer();
            if (rnd.nextInt(3) == 0) spinTarget = 360f;
        }
        return r2;
    }
}
