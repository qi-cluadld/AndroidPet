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

    private static final String DS_BODY = "M 48,132 C 48,104 66,90 94,90 C 128,90 160,98 176,116 C 186,128 186,146 178,152 C 168,174 142,186 114,186 C 84,186 52,176 48,152 C 46,144 46,138 48,132 Z";
    private static final String DS_BELLY = "M 56,148 C 72,172 100,186 122,186 C 148,186 168,170 178,148 C 168,160 146,170 120,170 C 94,170 70,158 56,148 Z";
    private static final String DS_TAIL = "M 172,124 C 182,108 198,96 212,96 C 221,104 213,124 195,136 C 213,148 221,168 212,176 C 198,176 182,164 172,148 Z";
    private static final String DS_FIN = "M 87,153 C 76,159 66,173 70,185 C 82,187 96,179 102,167 C 100,159 93,153 87,153 Z";

    private final Bitmap appleBmp;
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
        Bitmap ab = null;
        if ("apple".equals(petKey)) {
            try (java.io.InputStream in = c.getResources().openRawResource(
                    c.getResources().getIdentifier("apple_body", "raw", c.getPackageName()))) {
                ab = android.graphics.BitmapFactory.decodeStream(in);
            } catch (Exception ignored) { }
        }
        this.appleBmp = ab;
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

    /** Port of New-RoundPolyData -R -N -Round -Phase (N-sided polygon with rounded corners). */
    private Path roundPoly(float R, int N, float round, float phase, float cx, float cy) {
        float[] vx = new float[N], vy = new float[N];
        for (int i = 0; i < N; i++) {
            double a = 2 * Math.PI * i / N + phase;
            vx[i] = cx + (float) Math.cos(a) * R;
            vy[i] = cy + (float) Math.sin(a) * R;
        }
        float k = round;
        float[] enx = new float[N], eny = new float[N], exx = new float[N], exy = new float[N];
        for (int i = 0; i < N; i++) {
            int p = (i - 1 + N) % N, n = (i + 1) % N;
            enx[i] = vx[i] + (vx[p] - vx[i]) * k;
            eny[i] = vy[i] + (vy[p] - vy[i]) * k;
            exx[i] = vx[i] + (vx[n] - vx[i]) * k;
            exy[i] = vy[i] + (vy[n] - vy[i]) * k;
        }
        path.reset();
        path.moveTo(exx[0], exy[0]);
        for (int i = 1; i < N; i++) {
            path.lineTo(enx[i], eny[i]);
            path.quadTo(vx[i], vy[i], exx[i], exy[i]);
        }
        path.lineTo(enx[0], eny[0]);
        path.quadTo(vx[0], vy[0], exx[0], exy[0]);
        path.close();
        return path;
    }

    /** Port of New-SparkleData: a four-pointed star with cubic waists. */
    private Path sparkle(float R, float waist, float cx, float cy) {
        float[][] pts = { { 0, -R }, { R, 0 }, { 0, R }, { -R, 0 } };
        path.reset();
        path.moveTo(cx + pts[0][0], cy + pts[0][1]);
        for (int i = 0; i < 4; i++) {
            float[] p = pts[i];
            float[] q = pts[(i + 1) % 4];
            path.cubicTo(cx + p[0] * waist, cy + p[1] * waist,
                    cx + q[0] * waist, cy + q[1] * waist,
                    cx + q[0], cy + q[1]);
        }
        path.close();
        return path;
    }

    /** Minimal SVG path parser: M, L, C, Z (enough for the DeepSeek whale). */
    private Path svgPath(String d) {
        Path p = new Path();
        String[] tok = d.replace(",", " ").trim().split("\\s+");
        int i = 0;
        float x = 0, y = 0, sx = 0, sy = 0;
        while (i < tok.length) {
            String cmd = tok[i];
            if (cmd.equalsIgnoreCase("M") || cmd.equalsIgnoreCase("L")) {
                i++;
                x = Float.parseFloat(tok[i++]);
                y = Float.parseFloat(tok[i++]);
                if (cmd.equalsIgnoreCase("M")) { p.moveTo(x, y); sx = x; sy = y; }
                else p.lineTo(x, y);
            } else if (cmd.equalsIgnoreCase("C")) {
                i++;
                float x1 = Float.parseFloat(tok[i++]), y1 = Float.parseFloat(tok[i++]);
                float x2 = Float.parseFloat(tok[i++]), y2 = Float.parseFloat(tok[i++]);
                float x3 = Float.parseFloat(tok[i++]), y3 = Float.parseFloat(tok[i++]);
                p.cubicTo(x1, y1, x2, y2, x3, y3);
                x = x3; y = y3;
            } else if (cmd.equalsIgnoreCase("Z")) {
                p.close();
                i++;
                x = sx; y = sy;
            } else {
                i++;   // unsupported token, skip
            }
        }
        return p;
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
        if ("gemini".equals(key)) {
            return new LinearGradient(44f, 44f, 200f, 200f,
                    new int[] { 0xFF4A88F8, 0xFF8B78E2, 0xFF9B72CB, 0xFFE2688C },
                    new float[] { 0f, 0.35f, 0.7f, 1f }, Shader.TileMode.CLAMP);
        }
        if ("qwen".equals(key)) { c1 = 0xFFBFD0FF; c2 = 0xFF6D8BFF; c3 = 0xFF2B4BE0; }
        else if ("glm".equals(key)) { c1 = 0xFFFFFFFF; c2 = 0xFFE7EDFF; c3 = 0xFFD6DEFF; }
        else if ("copilot".equals(key)) { c1 = 0xFF0F6CBD; c2 = 0xFF7FBA00; c3 = 0xFFFFD400; }
        else if ("claude".equals(key)) { c1 = 0xFFF0A98A; c2 = 0xFFC96A45; c3 = 0xFFBD5D3A; }
        else if ("chatgpt".equals(key)) { c1 = 0xFF62E3B8; c2 = 0xFF17A882; c3 = 0xFF0A7A5E; }
        else if ("doubao".equals(key)) { c1 = 0xFFCFE0FA; c2 = 0xFF9CBBEA; c3 = 0xFF7FA6DD; }
        else if ("deepseek".equals(key)) { c1 = 0xFF82A3FF; c2 = 0xFF4D6BFE; c3 = 0xFF3A55DC; }
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
        if ("deepseek".equals(key)) {
            fill.setShader(new LinearGradient(60f, 90f, 200f, 190f, 0xFF9DBAFF, 0xFF3A55DC, Shader.TileMode.CLAMP));
            c.drawPath(svgPath(DS_TAIL), fill);
            fill.setShader(null);
        } else if ("chatgpt".equals(key)) {
            fill.setShader(new LinearGradient(40f, 30f, 200f, 190f,
                    new int[] { 0xFF62E3B8, 0xFF17A882, 0xFF0A7A5E },
                    new float[] { 0f, 0.5f, 1f }, Shader.TileMode.CLAMP));
            for (int i = 0; i < 6; i++) {
                double ang = 2 * Math.PI * i / 6;
                float cx = 120f + (float) Math.cos(ang) * 51.5f;
                float cy = 122f + (float) Math.sin(ang) * 51.5f;
                Path arm = new Path();
                rect.set(-25f, -12f, 25f, 12f);
                arm.addRoundRect(rect, 12f, 12f, Path.Direction.CW);
                mat.reset();
                mat.postRotate(90f + i * 60f + 12f);
                mat.postTranslate(cx, cy);
                arm.transform(mat);
                c.drawPath(arm, fill);
            }
            fill.setShader(null);
        } else if ("claude".equals(key)) {
            fill.setShader(new LinearGradient(40f, 30f, 200f, 190f,
                    new int[] { 0xFFF3C0A6, 0xFFC96A45, 0xFFBD5D3A },
                    new float[] { 0f, 0.5f, 1f }, Shader.TileMode.CLAMP));
            for (int i = 0; i < 12; i++) {
                Path ray = new Path();
                rect.set(-9.5f, -84.5f, 9.5f, -26.5f);
                ray.addRoundRect(rect, 9.5f, 9.5f, Path.Direction.CW);
                mat.reset();
                mat.postRotate(i * 30f);
                mat.postTranslate(120f, 122f);
                ray.transform(mat);
                c.drawPath(ray, fill);
            }
            fill.setShader(null);
        } else if ("qwen".equals(key)) {
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
        if ("gemini".equals(key)) {
            body = sparkle(78f, 0.78f, 120f, 122f);
        } else if ("apple".equals(key)) {
            body = squircle(120f, 122f, 176f, 88f, 0f);
        } else if ("doubao".equals(key)) {
            Path bp = new Path();
            rect.set(-76f, -60f, 76f, 60f);
            bp.addRoundRect(rect, 56f, 56f, Path.Direction.CW);
            mat.reset();
            mat.postRotate(-8f);
            mat.postTranslate(122f, 124f);
            bp.transform(mat);
            body = bp;
        } else if ("deepseek".equals(key)) {
            body = svgPath(DS_BODY);
        } else if ("claude".equals(key)) body = roundPoly(44f, 12, 0.55f, (float) (Math.PI / 2), 120f, 122f);
        else if ("chatgpt".equals(key)) body = roundPoly(50f, 6, 0.42f, (float) (Math.PI / 2), 120f, 122f);
        else if ("qwen".equals(key)) body = squircle(120f, 122f, 156f, 44f, 0f);
        else if ("glm".equals(key)) body = squircle(122f, 124f, 108f, 54f, 0f);
        else if ("copilot".equals(key)) body = squircle(150f, 152f, 104f, 32f, -10f);
        else body = squircle(122f, 124f, 128f, 64f, 0f);

        if ("apple".equals(key) && appleBmp != null) {
            rect.set(32f, 34f, 208f, 210f);
            c.drawBitmap(appleBmp, null, rect, fill);
            return;
        }
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
        if ("deepseek".equals(key)) {
            fill.setShader(new LinearGradient(60f, 140f, 180f, 190f, 0xFFEAF1FF, 0xFFB9CBFF, Shader.TileMode.CLAMP));
            c.drawPath(svgPath(DS_BELLY), fill);
            fill.setShader(new LinearGradient(66f, 150f, 104f, 190f, 0xFFEAF1FF, 0xFF9DB4F0, Shader.TileMode.CLAMP));
            c.drawPath(svgPath(DS_FIN), fill);
            fill.setShader(null);
        } else if ("qwen".equals(key)) {
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
            fill.setShader(new RadialGradient(114f, 116f, 74f,
                    new int[] { 0xFFF8D22A, 0xFFE3008C, 0xFF8661C5 },
                    new float[] { 0f, 0.55f, 1f }, Shader.TileMode.CLAMP));
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