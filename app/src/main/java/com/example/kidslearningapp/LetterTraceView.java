package com.example.kidslearningapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * LetterTraceView
 * ───────────────
 * Transparent canvas overlaid on the letter display.
 *
 * Stroke:
 *   • Sharp trail  → 7 dp  (thin enough for lowercase letters)
 *   • Glow layer   → 16 dp (soft aura, very light alpha)
 *   • Rainbow hue shift as the child draws
 *   • Sparkle dots at each touch-down
 *
 * Validation (checkCoverage):
 *   Samples the child's collected touch points against a target RectF
 *   (the letter's bounding box on screen). Returns 0–100 % of how
 *   well the tracing covers that region.
 *   AlphabetActivity calls this when the child presses Next.
 */
public class LetterTraceView extends View {

    // ── Configurable sizes (dp) ──────────────────────────────────────────
    private static final int TRAIL_WIDTH_DP = 7;   // sharp line width
    private static final int GLOW_WIDTH_DP  = 16;  // soft aura width

    // ── Paints ───────────────────────────────────────────────────────────
    private final Paint glowPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sparkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ── Path / segment storage ───────────────────────────────────────────
    private final Path currentPath = new Path();
    private float lastX, lastY;
    private float hue = 0f;

    private static final class Segment {
        final Path  path;
        final float hue;
        Segment(Path p, float h) { this.path = p; this.hue = h; }
    }
    private final List<Segment> segments = new ArrayList<>();

    // ── Sparkle storage ──────────────────────────────────────────────────
    private static final class Dot {
        final float x, y, r;
        final int   color;
        Dot(float x, float y, float r, int c) { this.x=x; this.y=y; this.r=r; this.color=c; }
    }
    private final List<Dot> dots = new ArrayList<>();

    // ── All touch points (for coverage check) ────────────────────────────
    private final List<float[]> touchPoints = new ArrayList<>(); // each = {x, y}

    private boolean hasDrawn = false;

    // ── Constructor ───────────────────────────────────────────────────────
    public LetterTraceView(Context c)                          { super(c);          init(); }
    public LetterTraceView(Context c, AttributeSet a)         { super(c,a);        init(); }
    public LetterTraceView(Context c, AttributeSet a, int d)  { super(c,a,d);      init(); }

    private void init() {
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        float density = getResources() == null ? 3f
                : getResources().getDisplayMetrics().density;

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(GLOW_WIDTH_DP * density);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setStrokeJoin(Paint.Join.ROUND);
        glowPaint.setPathEffect(new CornerPathEffect(12 * density));
        glowPaint.setAlpha(55); // very soft

        trailPaint.setStyle(Paint.Style.STROKE);
        trailPaint.setStrokeWidth(TRAIL_WIDTH_DP * density);
        trailPaint.setStrokeCap(Paint.Cap.ROUND);
        trailPaint.setStrokeJoin(Paint.Join.ROUND);
        trailPaint.setPathEffect(new CornerPathEffect(8 * density));

        sparkPaint.setStyle(Paint.Style.FILL);
    }

    // ── Touch ─────────────────────────────────────────────────────────────
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                currentPath.moveTo(x, y);
                lastX = x; lastY = y;
                hue = (hue + 35f) % 360f;

                // Tiny sparkle cluster at touch-down
                float sp = dp(4) + (float)(Math.random() * dp(4));
                dots.add(new Dot(x, y, sp, hsv(hue)));
                dots.add(new Dot(x + dp(8), y - dp(5), sp * 0.5f, hsv((hue+40)%360)));
                dots.add(new Dot(x - dp(7), y + dp(6), sp * 0.4f, hsv((hue+80)%360)));

                hasDrawn = true;
                break;

            case MotionEvent.ACTION_MOVE:
                float mx = (lastX + x) / 2f;
                float my = (lastY + y) / 2f;
                currentPath.quadTo(lastX, lastY, mx, my);
                lastX = x; lastY = y;
                hue = (hue + 0.6f) % 360f;
                touchPoints.add(new float[]{x, y});
                break;

            case MotionEvent.ACTION_UP:
                currentPath.lineTo(x, y);
                segments.add(new Segment(new Path(currentPath), hue));
                currentPath.reset();
                touchPoints.add(new float[]{x, y});
                break;
        }

        invalidate();
        return true;
    }

    // ── Draw ──────────────────────────────────────────────────────────────
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        for (Segment s : segments) drawSeg(canvas, s.path, s.hue);
        if (!currentPath.isEmpty())  drawSeg(canvas, currentPath, hue);

        for (Dot d : dots) {
            sparkPaint.setColor(d.color);
            sparkPaint.setAlpha(180);
            canvas.drawCircle(d.x, d.y, d.r, sparkPaint);
        }
    }

    private void drawSeg(Canvas canvas, Path path, float h) {
        glowPaint.setColor(hsv(h));
        canvas.drawPath(path, glowPaint);
        trailPaint.setColor(hsv(h));
        canvas.drawPath(path, trailPaint);
    }

    // ── Public API ────────────────────────────────────────────────────────

    /** Wipe all traces. */
    public void clearTrace() {
        segments.clear();
        dots.clear();
        touchPoints.clear();
        currentPath.reset();
        hue = 0f;
        hasDrawn = false;
        invalidate();
    }

    public boolean hasDrawn() { return hasDrawn; }

    /**
     * Coverage check — call this when the child presses Next.
     *
     * @param letterRect  The bounding box of the guide letter ON SCREEN
     *                    (get via getLocationOnScreen + letter width/height).
     *                    Pass null to skip validation (always returns 100).
     * @return            0–100 coverage score. Threshold for "good enough" is ~25.
     *                    Low threshold intentional — we reward effort, not perfection.
     */
    public int checkCoverage(RectF letterRect) {
        if (letterRect == null || touchPoints.isEmpty()) {
            return hasDrawn ? 40 : 0;
        }

        // How many of our touch points fall inside the letter rect (expanded a bit)?
        float expand = dp(20); // generous padding so kids aren't penalised for slight overshoot
        RectF expanded = new RectF(
                letterRect.left   - expand,
                letterRect.top    - expand,
                letterRect.right  + expand,
                letterRect.bottom + expand);

        int inside = 0;
        for (float[] pt : touchPoints) {
            if (expanded.contains(pt[0], pt[1])) inside++;
        }

        // What fraction of touch points were inside the letter zone?
        float ratio = (float) inside / touchPoints.size();

        // Also check the child has drawn at least a minimum stroke length
        // (so just tapping doesn't count)
        boolean hasLength = touchPoints.size() > 8;

        if (!hasLength) return 5;

        return (int)(ratio * 100f);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private int hsv(float h) {
        return Color.HSVToColor(new float[]{h, 0.85f, 1f});
    }

    private float dp(int dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}
