package com.example.kidslearningapp;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.BounceInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class NumbersActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private static final int[] CUBE_COLORS = {
            0xFFEF5350,
            0xFFFF9800,
            0xFFFFC107,
            0xFF66BB6A,
            0xFF42A5F5,
            0xFFAB47BC,
            0xFFEC407A,
            0xFF26C6DA,
            0xFF8D6E63,
            0xFF78909C
    };
    private int currentStart = 1;
    private static final int TOTAL = 10;
    private static final int DROP_COUNT = 5;

    private TextToSpeech tts;
    private boolean ttsReady = false;

    private FrameLayout dragCanvas;

    private View[] slotViews = new View[TOTAL];
    private boolean[] slotFilled = new boolean[TOTAL];

    private final List<Integer> missingIndices = new ArrayList<>();
    private int placedCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.activity_numbers);

        dragCanvas = findViewById(R.id.dragCanvas);
        tts = new TextToSpeech(this, this);

        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        dragCanvas.post(this::startRound);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.ENGLISH);
            tts.setSpeechRate(0.9f);
            tts.setPitch(1.1f);
            ttsReady = true;
        }
    }

    private void startRound() {
        dragCanvas.removeAllViews();

        slotViews = new View[TOTAL];
        slotFilled = new boolean[TOTAL];
        missingIndices.clear();
        placedCount = 0;

        TextView rangeText = findViewById(R.id.rangeText);
        rangeText.setText(currentStart + " - " + (currentStart + 9));

        List<Integer> pool = new ArrayList<>();
        for (int i = 0; i < TOTAL; i++) {
            pool.add(i);
        }

        Collections.shuffle(pool);

        for (int i = 0; i < DROP_COUNT; i++) {
            missingIndices.add(pool.get(i));
        }

        buildSlotRows();
        buildCubeTray();
    }

    private void buildSlotRows() {
        int canvasW = dragCanvas.getWidth();

        int padding = dpToPx(8);
        int gap = dpToPx(6);
        int slotSize = (canvasW - padding * 2 - gap * 4) / 5;

        int row1Top = dpToPx(24);
        int row2Top = row1Top + slotSize + dpToPx(18);

        for (int i = 0; i < TOTAL; i++) {
            int col = i % 5;
            int row = i / 5;
            int number = currentStart  + i;

            boolean isMissing = missingIndices.contains(i);

            int left = padding + col * (slotSize + gap);
            int top = row == 0 ? row1Top : row2Top;

            View cube = makeCubeView(number, CUBE_COLORS[i], slotSize, true, isMissing);

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(slotSize, slotSize);
            lp.leftMargin = left;
            lp.topMargin = top;

            dragCanvas.addView(cube, lp);

            slotViews[i] = cube;
            slotFilled[i] = !isMissing;

            if (isMissing) {
                TextView label = cube.findViewWithTag("label");
                if (label != null) {
                    label.setText("");
                }
                cube.setAlpha(0.85f);
            }
        }
    }

    private void buildCubeTray() {
        int canvasW = dragCanvas.getWidth();
        int canvasH = dragCanvas.getHeight();

        int padding = dpToPx(8);
        int gap = dpToPx(8);
        int cubeSize = (canvasW - padding * 2 - gap * (DROP_COUNT - 1)) / DROP_COUNT;

        int trayTop = canvasH - cubeSize - dpToPx(30);

        List<Integer> shuffled = new ArrayList<>(missingIndices);
        Collections.shuffle(shuffled);

        for (int t = 0; t < shuffled.size(); t++) {
            int idx = shuffled.get(t);
            int number = currentStart + idx;

            View cube = makeCubeView(number, CUBE_COLORS[idx], cubeSize, true, false);
            cube.setTag(R.id.tag_number, number);

            int left = padding + t * (cubeSize + gap);
            int top = trayTop;

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(cubeSize, cubeSize);
            lp.leftMargin = left;
            lp.topMargin = top;

            dragCanvas.addView(cube, lp);

            cube.setTag(R.id.tag_origin_x, left);
            cube.setTag(R.id.tag_origin_y, top);

            attachDragListener(cube);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void attachDragListener(View cube) {
        cube.setOnTouchListener(new View.OnTouchListener() {
            float dX;
            float dY;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        dX = v.getX() - e.getRawX();
                        dY = v.getY() - e.getRawY();

                        v.bringToFront();
                        v.animate()
                                .scaleX(1.12f)
                                .scaleY(1.12f)
                                .setDuration(120)
                                .start();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        v.setX(e.getRawX() + dX);
                        v.setY(e.getRawY() + dY);
                        return true;

                    case MotionEvent.ACTION_UP:
                        v.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(100)
                                .start();

                        checkDrop(
                                v,
                                e.getRawX() + dX + v.getWidth() / 2f,
                                e.getRawY() + dY + v.getHeight() / 2f
                        );
                        return true;
                }

                return false;
            }
        });
    }

    private void checkDrop(View cube, float cubeCenterX, float cubeCenterY) {
        int number = (int) cube.getTag(R.id.tag_number);
        int targetSlotIndex = number - currentStart;;

        View slot = slotViews[targetSlotIndex];

        if (slot == null || slotFilled[targetSlotIndex]) {
            shakeAndBounceBack(cube);
            return;
        }

        int[] slotPos = new int[2];
        int[] canvasPos = new int[2];

        slot.getLocationOnScreen(slotPos);
        dragCanvas.getLocationOnScreen(canvasPos);

        float slotCenterX = slotPos[0] - canvasPos[0] + slot.getWidth() / 2f;
        float slotCenterY = slotPos[1] - canvasPos[1] + slot.getHeight() / 2f;

        float hitRadius = slot.getWidth() * 0.75f;
        double distance = Math.hypot(cubeCenterX - slotCenterX, cubeCenterY - slotCenterY);

        if (distance < hitRadius) {
            snapToSlot(cube, slot, targetSlotIndex, number);
        } else {
            shakeAndBounceBack(cube);
        }
    }

    private void snapToSlot(View cube, View slot, int slotIndex, int number) {
        slotFilled[slotIndex] = true;

        int[] slotPos = new int[2];
        int[] canvasPos = new int[2];

        slot.getLocationOnScreen(slotPos);
        dragCanvas.getLocationOnScreen(canvasPos);

        float targetX = slotPos[0] - canvasPos[0];
        float targetY = slotPos[1] - canvasPos[1];

        cube.animate()
                .x(targetX)
                .y(targetY)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(250)
                .setInterpolator(new OvershootInterpolator(1.5f))
                .withEndAction(() -> {
                    slot.setAlpha(1f);

                    TextView slotLabel = slot.findViewWithTag("label");
                    if (slotLabel != null) {
                        slotLabel.setText(String.valueOf(number));
                    }

                    cube.setVisibility(View.GONE);
                    cube.setOnTouchListener(null);

                    celebrateSlot(slot);
                    speak(String.valueOf(number));

                    placedCount++;
                    checkComplete();
                })
                .start();
    }

    private void bounceBack(View cube) {
        float originX = (float) (int) cube.getTag(R.id.tag_origin_x);
        float originY = (float) (int) cube.getTag(R.id.tag_origin_y);

        cube.animate()
                .x(originX)
                .y(originY)
                .setDuration(400)
                .setInterpolator(new BounceInterpolator())
                .start();
    }

    private void shakeAndBounceBack(View cube) {
        ObjectAnimator shake = ObjectAnimator.ofFloat(
                cube,
                "translationX",
                0,
                -18,
                18,
                -14,
                14,
                -8,
                8,
                0
        );

        shake.setDuration(400);
        shake.start();

        new Handler().postDelayed(() -> bounceBack(cube), 420);
    }

    private void celebrateSlot(View slot) {
        AnimatorSet set = new AnimatorSet();

        set.playTogether(
                ObjectAnimator.ofFloat(slot, "scaleX", 1f, 1.25f, 1f),
                ObjectAnimator.ofFloat(slot, "scaleY", 1f, 1.25f, 1f)
        );

        set.setDuration(350);
        set.setInterpolator(new OvershootInterpolator());
        set.start();
    }

    private void checkComplete() {
        if (placedCount < DROP_COUNT) return;

        new Handler().postDelayed(() -> {
            speak("Great! Next level!");

            currentStart += 10;

            if (currentStart > 100) {
                speak("You finished all levels!");
                new Handler().postDelayed(this::finish, 2500);
            } else {
                new Handler().postDelayed(this::startRound, 2000);
            }

        }, 500);
    }

    private View makeCubeView(int number, int color, int size, boolean filled, boolean emptySlot) {
        FrameLayout frame = new FrameLayout(this);

        int depth = Math.max(dpToPx(6), size / 8);
        int faceSize = size - depth;

        int darkColor = darken(color, 0.55f);

        View bottomShadow = new View(this);
        bottomShadow.setBackgroundColor(darkColor);

        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(size, depth);
        bottomLp.topMargin = size - depth;
        frame.addView(bottomShadow, bottomLp);

        View rightShadow = new View(this);
        rightShadow.setBackgroundColor(darkColor);

        FrameLayout.LayoutParams rightLp = new FrameLayout.LayoutParams(depth, size);
        rightLp.leftMargin = size - depth;
        frame.addView(rightShadow, rightLp);

        android.graphics.drawable.GradientDrawable face =
                new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                        new int[]{
                                lighten(color, 1.25f),
                                color,
                                darken(color, 0.85f)
                        }
                );

        face.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        face.setCornerRadius(dpToPx(12));

        if (emptySlot) {
            face.setStroke(dpToPx(4), 0xFFFFFFFF);
            face.setAlpha(190);
        } else {
            face.setStroke(dpToPx(2), 0xFFFFFFFF);
        }

        View faceView = new View(this);
        faceView.setBackground(face);

        FrameLayout.LayoutParams faceLp = new FrameLayout.LayoutParams(faceSize, faceSize);
        frame.addView(faceView, faceLp);

        View shine = new View(this);

        android.graphics.drawable.GradientDrawable shineDrawable =
                new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                        new int[]{
                                0x77FFFFFF,
                                0x00FFFFFF
                        }
                );

        shineDrawable.setCornerRadius(dpToPx(12));
        shine.setBackground(shineDrawable);

        FrameLayout.LayoutParams shineLp = new FrameLayout.LayoutParams(faceSize, faceSize / 2);
        frame.addView(shine, shineLp);

        TextView label = new TextView(this);
        label.setTag("label");
        label.setText(filled ? String.valueOf(number) : "");
        label.setTextColor(0xFFFFFFFF);
        label.setGravity(android.view.Gravity.CENTER);
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        label.setShadowLayer(dpToPx(3), 1, 3, 0x88000000);

        label.setTextSize(
                android.util.TypedValue.COMPLEX_UNIT_PX,
                faceSize * 0.42f
        );

        FrameLayout.LayoutParams labelLp = new FrameLayout.LayoutParams(faceSize, faceSize);
        frame.addView(label, labelLp);

        return frame;
    }

    private int darken(int color, float factor) {
        int r = (int) (((color >> 16) & 0xFF) * factor);
        int g = (int) (((color >> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);

        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private int lighten(int color, float factor) {
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) * factor));
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) * factor));
        int b = Math.min(255, (int) ((color & 0xFF) * factor));

        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void speak(String text) {
        if (ttsReady) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "number");
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }

        super.onDestroy();
    }
}