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
 * DraggableShapeView — Colourful 3D toy shape piece.
 *
 * Sized to fill most of the view (38% of half-min → pushed to 44%)
 * so pieces look big and juicy on screen.
 */
public class DraggableShapeView extends View {

    private ShapeSorterView.Shape shape = ShapeSorterView.Shape.CIRCLE;
    private int baseColor = 0xFFE53935;

    private final Paint fillPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shinePaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public DraggableShapeView(Context c)                         { super(c); init(); }
    public DraggableShapeView(Context c, AttributeSet a)        { super(c,a); init(); }
    public DraggableShapeView(Context c, AttributeSet a, int d) { super(c,a,d); init(); }

    private void init() {
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(4));
        borderPaint.setStrokeCap(Paint.Cap.ROUND);
        borderPaint.setStrokeJoin(Paint.Join.ROUND);

        shinePaint.setStyle(Paint.Style.STROKE);
        shinePaint.setStrokeWidth(dp(6));
        shinePaint.setStrokeCap(Paint.Cap.ROUND);
        shinePaint.setColor(0x99FFFFFF);

        shadowPaint.setColor(0x55000000);
        shadowPaint.setStyle(Paint.Style.FILL);
    }

    public void setShape(ShapeSorterView.Shape s) { shape = s; invalidate(); }
    public ShapeSorterView.Shape getShape()        { return shape; }
    public void setShapeColor(int c) { baseColor = c; invalidate(); }

    @Override
    protected void onDraw(Canvas canvas) {
        float w  = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        // Larger: 44% of the smaller dimension as half-size
        float hs = Math.min(w, h) * 0.44f;

        int light = blend(baseColor, Color.WHITE, 0.32f);
        int dark  = blend(baseColor, Color.BLACK, 0.28f);

        fillPaint.setShader(new LinearGradient(
                cx - hs, cy - hs, cx + hs, cy + hs,
                new int[]{ light, baseColor, dark },
                new float[]{ 0f, 0.45f, 1f },
                Shader.TileMode.CLAMP));
        fillPaint.setStyle(Paint.Style.FILL);
        borderPaint.setColor(blend(baseColor, Color.BLACK, 0.38f));

        // Shadow
        canvas.save();
        canvas.translate(dp(5), dp(6));
        drawShape(canvas, cx, cy, hs, shadowPaint);
        canvas.restore();

        // Shape
        drawShape(canvas, cx, cy, hs, fillPaint);
        drawShape(canvas, cx, cy, hs, borderPaint);

        // Shine arc on top-left
        RectF sr = new RectF(cx - hs * 0.72f, cy - hs * 0.72f,
                cx + hs * 0.05f, cy + hs * 0.05f);
        canvas.drawArc(sr, 200f, 100f, false, shinePaint);
    }

    private void drawShape(Canvas canvas, float cx, float cy, float hs, Paint p) {
        RectF r = new RectF(cx - hs, cy - hs, cx + hs, cy + hs);
        switch (shape) {
            case CIRCLE:
                canvas.drawOval(r, p); break;
            case SQUARE:
                canvas.drawRoundRect(r, dp(14), dp(14), p); break;
            case TRIANGLE:
                Path tri = new Path();
                tri.moveTo(cx,            cy - hs * 1.1f);
                tri.lineTo(cx + hs * 1.15f, cy + hs * 0.95f);
                tri.lineTo(cx - hs * 1.15f, cy + hs * 0.95f);
                tri.close();
                canvas.drawPath(tri, p); break;
            case STAR:
                canvas.drawPath(starPath(cx, cy, hs * 1.1f, hs * 0.44f, 5), p); break;
            case HEART:
                canvas.drawPath(heartPath(cx, cy, hs * 1.05f), p); break;
            case PENTAGON:
                canvas.drawPath(polyPath(cx, cy, hs * 1.1f, 5, -90), p); break;
        }
    }

    private Path starPath(float cx, float cy, float outer, float inner, int pts) {
        Path p = new Path();
        double step = Math.PI / pts;
        for (int i = 0; i < pts * 2; i++) {
            double a = i * step - Math.PI / 2;
            float rv = (i % 2 == 0) ? outer : inner;
            float x = cx + (float)(rv * Math.cos(a));
            float y = cy + (float)(rv * Math.sin(a));
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.close(); return p;
    }

    private Path heartPath(float cx, float cy, float s) {
        Path p = new Path();
        p.moveTo(cx, cy + s * 0.72f);
        p.cubicTo(cx - s*1.4f, cy + s*0.1f, cx - s*1.4f, cy - s*0.8f, cx, cy - s*0.28f);
        p.cubicTo(cx + s*1.4f, cy - s*0.8f, cx + s*1.4f, cy + s*0.1f, cx, cy + s*0.72f);
        p.close(); return p;
    }

    private Path polyPath(float cx, float cy, float r, int sides, float startDeg) {
        Path p = new Path();
        for (int i = 0; i < sides; i++) {
            double a = Math.toRadians(startDeg + i * 360.0 / sides);
            float x = cx + (float)(r * Math.cos(a));
            float y = cy + (float)(r * Math.sin(a));
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.close(); return p;
    }

    private int blend(int color, int target, float f) {
        int r = (int)(((color>>16)&0xFF)*(1-f) + ((target>>16)&0xFF)*f);
        int g = (int)(((color>> 8)&0xFF)*(1-f) + ((target>> 8)&0xFF)*f);
        int b = (int)( (color     &0xFF)*(1-f) + ( target     &0xFF)*f);
        return 0xFF000000|(r<<16)|(g<<8)|b;
    }

    private float dp(int v) { return v * getResources().getDisplayMetrics().density; }
}
