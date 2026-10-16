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
 * Vector desktop pet - batch 1: grok / qwen / glm / copilot
 * -----------------------------------------------------------------
 * Every number is copied from each repo's build blocks, nothing guessed:
 *   body  : New-SquircleData -CX -CY -Size -R -Rot
 *           fill = radial gradient centered (120,92) radius 152, true stops
 *           plus a soft white highlight (relative origin 0.34,0.20, radius 0.72)
 *   order : blossom layer -> body -> orbit layer -> face
 *           (face last, so Copilot's front tile never covers the eyes)
 *
 *   qwen    body Squircle 156 R44 @(120,122)
 *           three white blocks Size54 R17 radius56 angles 270/30/150 rot -18/22/-6
 *           plus the same three behind: Size42 R13 radius62
 *   glm     body Squircle 108 R54 @(122,124)
 *           ellipse rings @(97,92) rot20 and @(97,164) rot-20, Ro64 Ri50 squash0.95
 *   copilot body Squircle 104 R32 @(150,152) rot-10
 *           front tile 104 R32 @(114,116) rot-10, rainbow linear gradient
 *   grok    light grey body, black ring outer90 inner58, slash drawn behind body
 *
 *   face    shared by all ten: dark eyes 24x27 @(92,100.5)/(124,100.5),
 *           white glints 9x9 and 4x4, smile arc @(120,138), blush @(73,120)/(143,120)
 */
public class VectorPetView extends PetView {

    private static final float CANVAS = 240f;

    private final String key;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path tmp = new Path();
    private final RectF rect = new RectF();
    private final Matrix mat = new Matrix();
    private final float d;
    private final float scale;
    private final java.util.Random rnd = new java.util.Random();

    private long t = 0;
    private float blink = 0f;
    private int blinkDir = 0;
    private long nextBlink;
    private float squash = 1f, squashVel = 0f;
    private float tilt = 0f, spin = 0f, spinTarget = 0f;
    private boolean happy = false;
    private long happyUntil = 0;
    private float blushAlpha = 0f;
    private final float[] stars = new float[12];

    public VectorPetView(Context c, String petKey, String[] lines, int startX, int startY, Listener l) {
        super(c, new Bitmap[12], lines, startX, startY, l);
        this.key = petKey;
        this.d = c.getResources().getDisplayMetrics().density;
        this.scale = viewWidth() / CANVAS;
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setColor(Color.parseColor("#FF101014"));
        for (int i = 0; i < 4; i++) {
            stars[i * 3] = rnd.nextFloat() * 6.28f;
            stars[i * 3 + 1] = 78f + rnd.nextInt(14);
            stars[i * 3 + 2] = rnd.nextFloat() * 4f;
        }
        nextBlink = System.currentTimeMillis() + 1200 + rnd.nextInt(2500);
    }

    private Path squircle(float cx, float cy, float size, float r, float rotDeg) {
        float half = size / 2f;
        tmp.reset();
        rect.set(-half, -half, half, half);
        tmp.addRoundRect(rect, r, r, Path.Direction.CW);
        mat.reset();
        mat.postRotate(rotDeg);
        mat.postTranslate(cx, cy);
        tmp.transform(mat);
        return tmp;
    }

    private Path ellipseRing(float cx, float cy, float ro, float ri, float squash) {
        path.reset();
        path.setFillType(Path.FillType.EVEN_ODD);
        rect.set(cx - ro, cy - ro * squash, cx + ro, cy + ro * squash);
        path.addOval(rect, Path.Direction.CW);
        rect.set(cx - ri, cy - ri * squash, cx + ri, cy + ri * squash);
        path.addOval(rect, Path.Direction.CCW);
        return path;
    }

    private Path circleRing(float cx, float cy, float ro, float ri) {
        path.reset();
        path.setFillType(Path.FillType.EVEN_ODD);
        path.addCircle(cx, cy, ro, Path.Direction.CW);
        path.addCircle(cx, cy, ri, Path.Direction.CCW);
        return path;
    }

    private Shader bodyShader() {
        int c1, c2, c3;
        if ("qwen".equals(key)) { c1 = 0xFFBFD0FF; c2 = 0xFF6D8BFF; c3 = 0xFF2B4BE0; }
        else if ("glm".equals(key)) { c1 = 0xFFFFFFFF; c2 = 0xFFE7EDFF; c3 = 0xFFD6DEFF; }
        else if ("copilot".equals(key)) { c1 = 0xFF0F6CBD; c2 = 0xFF7FBA00; c3 = 0xFFFFD400; }
        else { c1 = 0xFFF5F5F8; c2 = 0xFFECECF1; c3 = 0xFFDCDCE4; }
        return new RadialGradient(120f, 92f, 152f, new int[] { c1, c2, c3 },
                new float[] { 0f, 0.55f, 1f }, Shader.TileMode.CLAMP);
    }

    private void drawQwenBlocks(Canvas c, float size, float r, float radius, boolean front) {
        float[] angles = { 270f, 30f, 150f };
        float[] rotd = { -18f, 22f, -6f };
        for (int i = 0; i < 3; i++) {
            double a = angles[i] * Math.PI / 180.0;
            float px = 120f + (float) Math.cos(a) * radius;
            float py = 122f + (float) Math.sin(a) * radius;
            int c2 = front ? 0xFFE9EFFF : 0xFFEDF2FF;
            fill.setShader(new LinearGradient(px - size / 2f, py - size / 2f, px + size / 2f, py + size / 2f,
                    0xFFFFFFFF, c2, Shader.TileMode.CLAMP));
            c.drawPath(squircle(px, py, size, r, rotd[i]), fill);
        }
        fill.setShader(null);
    }

    private void drawBlossom(Canvas c) {
        if ("qwen".equals(key)) {
            drawQwenBlocks(c, 42f, 13f, 62f, false);
        } else if ("grok".equals(key)) {
            Path sp = new Path();
            sp.moveTo(176f, 44f);
            sp.lineTo(196f, 60f);
            sp.lineTo(64f, 200f);
            sp.lineTo(44f, 184f);
            sp.close();
            fill.setShader(new LinearGradient(44f, 44f, 200f, 200f,
                    0xFF2A2A33, 0xFF101014, Shader.TileMode.CLAMP));
            c.drawPath(sp, fill);
            fill.setShader(null);
        }
    }

    private void drawBody(Canvas c) {
        Path body;
        if ("qwen".equals(key)) body = squircle(120f, 122f, 156f, 44f, 0f);
        else if ("glm".equals(key)) body = squircle(122f, 124f, 108f, 54f, 0f);
        else if ("copilot".equals(key)) body = squircle(150f, 152f, 104f, 32f, -10f);
        else body = squircle(122f, 124f, 128f, 64f, 0f);

        fill.setShader(null);
        fill.setShadowLayer(9f * scale, 0f, 3f * scale, Color.argb(80, 20, 20, 40));
        fill.setShader(bodyShader());
        c.drawPath(body, fill);
        fill.clearShadowLayer();

        fill.setShader(new RadialGradient(81.6f, 48f, 170f,
                new int[] { 0x8CFFFFFF, 0x00FFFFFF }, new float[] { 0f, 1f }, Shader.TileMode.CLAMP));
        c.drawPath(body, fill);
        fill.setShader(null);
    }

    private void drawOrbit(Canvas c) {
        if ("qwen".equals(key)) {
            drawQwenBlocks(c, 54f, 17f, 56f, true);
        } else if ("glm".equals(key)) {
            fill.setShader(new LinearGradient(33f, 31f, 161f, 153f,
                    0xFF2B4BF2, 0xFF1F3AD6, Shader.TileMode.CLAMP));
            Path r1 = ellipseRing(97f, 92f, 64f, 50f, 0.95f);
            mat.reset();
            mat.postRotate(20f, 97f, 92f);
            r1.transform(mat);
            c.drawPath(r1, fill);

            fill.setShader(new LinearGradient(33f, 103f, 161f, 225f,
                    0xB33B5BFE, 0xB34B6BFF, Shader.TileMode.CLAMP));
            Path r2 = ellipseRing(97f, 164f, 64f, 50f, 0.95f);
            mat.reset();
            mat.postRotate(-20f, 97f, 164f);
            r2.transform(mat);
            c.drawPath(r2, fill);
            fill.setShader(null);
        } else if ("copilot".equals(key)) {
            fill.setShader(new LinearGradient(70f, 64f, 158f, 168f,
                    new int[] { 0xFF0F6CBD, 0xFF00B7C3, 0xFF7FBA00, 0xFFFFD400 },
                    new float[] { 0f, 0.35f, 0.7f, 1f }, Shader.TileMode.CLAMP));
            c.drawPath(squircle(114f, 116f, 104f, 32f, -10f), fill);
            fill.setShader(null);
        } else if ("grok".equals(key)) {
            fill.setShader(new LinearGradient(44f, 44f, 200f, 200f,
                    0xFF2A2A33, 0xFF101014, Shader.TileMode.CLAMP));
            c.drawPath(circleRing(120f, 122f, 90f, 58f), fill);
            fill.setShader(null);
        }
    }

    private void drawFace(Canvas c) {
        if (blushAlpha > 0.02f) {
            for (float bx : new float[] { 73f, 143f }) {
                rect.set(bx, 120f, bx + 24f, 132f);
                fill.setShader(new RadialGradient(bx + 12f, 126f, 12f,
                        Color.argb((int) (120 * blushAlpha), 255, 130, 145), 0x00FF8291, Shader.TileMode.CLAMP));
                c.drawOval(rect, fill);
            }
            fill.setShader(null);
        }
        float eyeScaleY = 1f - Math.min(1f, blink);
        if (happy) {
            for (float x : new float[] { 104f, 136f }) {
                Path p = new Path();
                p.moveTo(x - 11f, 118f);
                p.quadTo(x, 106f, x + 11f, 118f);
                stroke.setStrokeWidth(4f);
                c.drawPath(p, stroke);
            }
        } else {
            for (float x : new float[] { 92f, 124f }) {
                float midY = 114f;
                float halfH = 13.5f * Math.max(0.08f, eyeScaleY);
                rect.set(x, midY - halfH, x + 24f, midY + halfH);
                fill.setShader(new LinearGradient(x, midY - halfH, x + 24f, midY + halfH,
                        0xFF1C1C22, 0xFF101014, Shader.TileMode.CLAMP));
                c.drawOval(rect, fill);
                fill.setShader(null);
                if (eyeScaleY > 0.25f) {
                    rect.set(x + 4f, 104f, x + 13f, 113f);
                    fill.setColor(0xF2FFFFFF);
                    c.drawOval(rect, fill);
                    rect.set(x + 18f, 117f, x + 22f, 121f);
                    fill.setColor(0x99FFFFFF);
                    c.drawOval(rect, fill);
                }
            }
        }
        Path mouth = new Path();
        mouth.moveTo(111f, 138f);
        mouth.quadTo(120f, 147f, 129f, 138f);
        stroke.setStrokeWidth(3.2f);
        c.drawPath(mouth, stroke);
    }

    private void drawStars(Canvas c) {
        fill.setShader(null);
        for (int i = 0; i < 4; i++) {
            float a = stars[i * 3] + t / 26f;
            float rad = stars[i * 3 + 1] + (float) Math.sin(t / 17f + i) * 10f;
            float px = 120f + (float) Math.cos(a) * rad;
            float py = 122f + (float) Math.sin(a) * rad;
            int alpha = 110 + (int) (90 * Math.abs(Math.sin(t / 20f + i)));
            fill.setColor(Color.argb(alpha, 150, 150, 170));
            c.drawCircle(px, py, 2.6f, fill);
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        int bh = (int) (40 * d);
        c.save();
        c.translate(0, bh);
        c.scale(scale, scale);
        mat.reset();
        mat.postTranslate(-120f, -122f);
        mat.postScale(1f + (squash - 1f) * 0.5f, squash);
        mat.postRotate(tilt + spin);
        mat.postTranslate(120f, 122f);
        c.concat(mat);

        drawBlossom(c);
        drawBody(c);
        drawOrbit(c);
        drawStars(c);
        drawFace(c);

        c.restore();
        super.onDraw(c);
    }

    @Override
    public void tick(int screenW) {
        super.tick(screenW);
        t++;
        long now = System.currentTimeMillis();
        if (now > nextBlink && blinkDir == 0) {
            blinkDir = 1;
            blink = 0.001f;
            nextBlink = now + 1800 + rnd.nextInt(3200);
        }
        if (blinkDir > 0 && blink > 0f) {
            blink += 0.22f;
            if (blink >= 1f) { blink = 1f; blinkDir = -1; }
        } else if (blinkDir < 0) {
            blink -= 0.16f;
            if (blink <= 0f) { blink = 0f; blinkDir = 0; }
        }
        squashVel += (1f - squash) * 0.18f;
        squashVel *= 0.82f;
        squash += squashVel;
        tilt = (float) Math.sin(t / 9.0) * 2.2f;
        if (spinTarget > 0f) {
            float step = Math.min(12f, spinTarget);
            spin += step;
            spinTarget -= step;
            if (spinTarget <= 0f) { spinTarget = 0f; spin = 0f; }
        }
        if (now > happyUntil) happy = false;
        float target = happy ? 1f : 0f;
        blushAlpha += (target - blushAlpha) * 0.12f;
        invalidate();
    }

    public void bounce() { squashVel = 0.16f; }

    public void cheer() { happy = true; happyUntil = System.currentTimeMillis() + 900; }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent e) {
        boolean r = super.onTouchEvent(e);
        if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP) {
            bounce();
            cheer();
            if (rnd.nextInt(3) == 0) spinTarget = 360f;
        }
        return r;
    }
}