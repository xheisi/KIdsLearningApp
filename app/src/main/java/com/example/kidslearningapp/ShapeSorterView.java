package com.example.kidslearningapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * ShapeSorterView — 3D toy block with a true punched-through hole.
 *
 * Technique: build the block face as a Path, then use Path.Op.DIFFERENCE
 * to subtract the hole shape. This guarantees a real transparent cutout
 * without any saveLayer / PorterDuff complexity.
 *
 * Visual design:
 *   • Vivid sky-blue top face (kids-toy plastic feel, not wood)
 *   • Darker blue right + bottom sides for 3D thickness
 *   • White bevel highlight top-left
 *   • Gold screw dots in corners
 *   • NO drop shadow (removed per feedback)
 *   • Hole has a dark inner-ring to look deep
 */
public class ShapeSorterView extends View {

    public enum Shape { CIRCLE, SQUARE, TRIANGLE, STAR, HEART, PENTAGON }

    private Shape shape = Shape.CIRCLE;

    // ── Block colour palette — bright toy plastic ─────────────────────────
    // Top face: vibrant gradient per shape so the block feels themed
    private static final int[][] FACE_GRADIENTS = {
            { 0xFFFF6B9D, 0xFFFF3D7F },   // CIRCLE    — hot pink
            { 0xFF4FC3F7, 0xFF0288D1 },   // SQUARE    — sky blue
            { 0xFF81C784, 0xFF388E3C },   // TRIANGLE  — fresh green
            { 0xFFFFD54F, 0xFFFFA000 },   // STAR      — golden yellow
            { 0xFFCE93D8, 0xFF7B1FA2 },   // HEART     — purple
            { 0xFFFF8A65, 0xFFE64A19 },   // PENTAGON  — deep orange
    };
    private static final int[] SIDE_COLORS = {
            0xFFB02060,   // CIRCLE
            0xFF01579B,   // SQUARE
            0xFF1B5E20,   // TRIANGLE
            0xFFE65100,   // STAR (using orange for visibility)
            0xFF4A148C,   // HEART
            0xFFBF360C,   // PENTAGON
    };

    private final Paint facePaint      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sidePaint      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bevelPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint holeRingPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint       = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotShinePaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint      = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ShapeSorterView(Context c)                         { super(c); init(); }
    public ShapeSorterView(Context c, AttributeSet a)        { super(c, a); init(); }
    public ShapeSorterView(Context c, AttributeSet a, int d) { super(c, a, d); init(); }

    private void init() {
        // Path.Op works in software or hardware — no special layer needed
        sidePaint.setStyle(Paint.Style.FILL);

        bevelPaint.setColor(0x88FFFFFF);
        bevelPaint.setStyle(Paint.Style.STROKE);
        bevelPaint.setStrokeWidth(dp(5));
        bevelPaint.setStrokeCap(Paint.Cap.ROUND);

        holeRingPaint.setStyle(Paint.Style.STROKE);
        holeRingPaint.setStrokeWidth(dp(7));
        holeRingPaint.setColor(0xCC000000);

        dotPaint.setStyle(Paint.Style.FILL);
        dotShinePaint.setColor(0x88FFFFFF);
        dotShinePaint.setStyle(Paint.Style.FILL);

        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(dp(3));
    }

    public void setShape(Shape s) { shape = s; invalidate(); }
    public Shape getShape()        { return shape; }

    /** Hole rect in VIEW coordinates for drag hit-testing */
    public RectF getHoleRect() {
        float d  = depth();
        float fl = dp(14), ft = dp(14);
        float fr = getWidth()  - d - dp(8);
        float fb = getHeight() - d - dp(8);
        float cx = (fl + fr) / 2f, cy = (ft + fb) / 2f;
        float hs = holeHalf();
        return new RectF(cx - hs, cy - hs, cx + hs, cy + hs);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float W = getWidth(), H = getHeight();
        float d = depth();
        float r = dp(20);

        // Face rectangle
        float fl = dp(14), ft = dp(14);
        float fr = W - d - dp(8);
        float fb = H - d - dp(8);
        RectF face = new RectF(fl, ft, fr, fb);

        int si = shape.ordinal();

        // ── 1. Right side ─────────────────────────────────────────────
        sidePaint.setColor(SIDE_COLORS[si]);
        Path rightSide = new Path();
        rightSide.moveTo(fr, ft + r * 0.5f);
        rightSide.lineTo(fr + d, ft + r * 0.5f + d * 0.7f);
        rightSide.lineTo(fr + d, fb + d);
        rightSide.lineTo(fr,    fb);
        rightSide.close();
        canvas.drawPath(rightSide, sidePaint);

        // ── 2. Bottom side ────────────────────────────────────────────
        Path bottomSide = new Path();
        bottomSide.moveTo(fl + r * 0.5f, fb);
        bottomSide.lineTo(fl + r * 0.5f + d * 0.7f, fb + d);
        bottomSide.lineTo(fr + d, fb + d);
        bottomSide.lineTo(fr,     fb);
        bottomSide.close();
        canvas.drawPath(bottomSide, sidePaint);

        // ── 3. Top face with hole cut out using Path.Op ───────────────
        // Build the block face path
        Path facePath = new Path();
        facePath.addRoundRect(face, r, r, Path.Direction.CW);

        // Build the hole path
        Path holePath = buildHolePath(face);

        // Subtract hole from face — true transparent cutout
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            facePath.op(holePath, Path.Op.DIFFERENCE);
        }

        // Apply gradient shader to the paint
        int[] colors = FACE_GRADIENTS[si];
        facePaint.setShader(new LinearGradient(
                fl, ft, fr, fb,
                new int[]{ colors[0], colors[1] },
                null, Shader.TileMode.CLAMP));
        facePaint.setStyle(Paint.Style.FILL);
        canvas.drawPath(facePath, facePaint);

        // ── 4. Face border ────────────────────────────────────────────
        edgePaint.setColor(SIDE_COLORS[si]);
        canvas.drawRoundRect(face, r, r, edgePaint);

        // ── 5. Hole inner ring (depth effect) ─────────────────────────
        drawHoleOutline(canvas, face, holeRingPaint);

        // ── 6. Bevel highlight ────────────────────────────────────────
        RectF bev = new RectF(fl + dp(3), ft + dp(3), fr - dp(3), fb - dp(3));
        canvas.drawRoundRect(bev, r - dp(3), r - dp(3), bevelPaint);

        // ── 7. Screw dots ─────────────────────────────────────────────
        dotPaint.setColor(SIDE_COLORS[si]);
        float sr = dp(7), so = dp(22);
        float[] dotX = { face.left + so, face.right - so, face.left + so, face.right - so };
        float[] dotY = { face.top  + so, face.top   + so, face.bottom - so, face.bottom - so };
        for (int i = 0; i < 4; i++) {
            canvas.drawCircle(dotX[i], dotY[i], sr, dotPaint);
            canvas.drawCircle(dotX[i] - dp(2), dotY[i] - dp(2), sr * 0.4f, dotShinePaint);
        }
    }

    // ── Path helpers ──────────────────────────────────────────────────────

    private Path buildHolePath(RectF face) {
        float cx = face.centerX(), cy = face.centerY(), hs = holeHalf();
        RectF r = new RectF(cx - hs, cy - hs, cx + hs, cy + hs);
        Path p = new Path();
        switch (shape) {
            case CIRCLE:
                p.addOval(r, Path.Direction.CW);
                break;
            case SQUARE:
                float cr = dp(12);
                p.addRoundRect(r, cr, cr, Path.Direction.CW);
                break;
            case TRIANGLE:
                p.moveTo(cx,             cy - hs * 1.12f);
                p.lineTo(cx + hs * 1.18f, cy + hs * 0.98f);
                p.lineTo(cx - hs * 1.18f, cy + hs * 0.98f);
                p.close();
                break;
            case STAR:
                addStarToPath(p, cx, cy, hs * 1.12f, hs * 0.44f, 5);
                break;
            case HEART:
                addHeartToPath(p, cx, cy, hs * 1.08f);
                break;
            case PENTAGON:
                addPolyToPath(p, cx, cy, hs * 1.12f, 5, -90);
                break;
        }
        return p;
    }

    /** Draw just the hole outline (for the depth ring) */
    private void drawHoleOutline(Canvas canvas, RectF face, Paint p) {
        float cx = face.centerX(), cy = face.centerY(), hs = holeHalf();
        RectF r = new RectF(cx - hs, cy - hs, cx + hs, cy + hs);
        switch (shape) {
            case CIRCLE:
                canvas.drawOval(r, p); break;
            case SQUARE:
                canvas.drawRoundRect(r, dp(12), dp(12), p); break;
            default:
                canvas.drawPath(buildHolePath(face), p); break;
        }
    }

    private void addStarToPath(Path p, float cx, float cy, float outer, float inner, int pts) {
        double step = Math.PI / pts;
        for (int i = 0; i < pts * 2; i++) {
            double a = i * step - Math.PI / 2;
            float rv = (i % 2 == 0) ? outer : inner;
            float x = cx + (float)(rv * Math.cos(a));
            float y = cy + (float)(rv * Math.sin(a));
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.close();
    }

    private void addHeartToPath(Path p, float cx, float cy, float s) {
        p.moveTo(cx, cy + s * 0.72f);
        p.cubicTo(cx - s*1.4f, cy + s*0.1f, cx - s*1.4f, cy - s*0.8f, cx, cy - s*0.28f);
        p.cubicTo(cx + s*1.4f, cy - s*0.8f, cx + s*1.4f, cy + s*0.1f, cx, cy + s*0.72f);
        p.close();
    }

    private void addPolyToPath(Path p, float cx, float cy, float r, int sides, float startDeg) {
        for (int i = 0; i < sides; i++) {
            double a = Math.toRadians(startDeg + i * 360.0 / sides);
            float x = cx + (float)(r * Math.cos(a));
            float y = cy + (float)(r * Math.sin(a));
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.close();
    }

    // ── Measurements ─────────────────────────────────────────────────────

    private float depth() { return dp(24); }

    private float holeHalf() {
        float d = depth();
        float faceW = getWidth()  - d - dp(22);
        float faceH = getHeight() - d - dp(22);
        return Math.min(faceW, faceH) * 0.285f;
    }

    private float dp(int v) {
        return v * getResources().getDisplayMetrics().density;
    }
}