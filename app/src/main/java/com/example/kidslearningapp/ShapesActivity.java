package com.example.kidslearningapp;

import android.content.SharedPreferences;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * ShapesActivity — Shape Sorter
 *
 * One round = all 6 shapes in shuffled order, NO repeats.
 * Three pieces per question (1 correct + 2 wrong shapes).
 * Background: set shapes_bg drawable (placed in res/drawable).
 */
public class ShapesActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    // ── Shape data ────────────────────────────────────────────────────────

    private static final ShapeSorterView.Shape[] ALL_SHAPES = {
            ShapeSorterView.Shape.CIRCLE,
            ShapeSorterView.Shape.SQUARE,
            ShapeSorterView.Shape.TRIANGLE,
            ShapeSorterView.Shape.STAR,
            ShapeSorterView.Shape.HEART,
            ShapeSorterView.Shape.PENTAGON,
    };

    private static final int ROUND_SIZE = ALL_SHAPES.length; // exactly 6 — no repeats

    private static final int[] SHAPE_COLORS = {
            0xFFE53935, // CIRCLE    — red
            0xFF1E88E5, // SQUARE    — blue
            0xFF43A047, // TRIANGLE  — green
            0xFFFBC02D, // STAR      — yellow (deeper, visible on any bg)
            0xFFE91E63, // HEART     — pink
            0xFF8E24AA, // PENTAGON  — purple
    };

    private static final String[] SHAPE_NAMES = {
            "Circle", "Square", "Triangle", "Star", "Heart", "Pentagon"
    };

    private static final String[] SHAPE_SPEECH = {
            "Circle! Round like the sun!",
            "Square! Four equal sides!",
            "Triangle! Three pointy corners!",
            "Star! Twinkle twinkle!",
            "Heart! Full of love!",
            "Pentagon! Five sides!",
    };

    // Two wrong shape-colours per correct shape (clearly different hues)
    private static final int[][] WRONG_COLORS = {
            { 0xFF1E88E5, 0xFF43A047 }, // circle wrongs
            { 0xFFE53935, 0xFFE91E63 }, // square wrongs
            { 0xFF8E24AA, 0xFFFBC02D }, // triangle wrongs
            { 0xFFE91E63, 0xFF8E24AA }, // star wrongs
            { 0xFF43A047, 0xFF1E88E5 }, // heart wrongs
            { 0xFFE53935, 0xFFFBC02D }, // pentagon wrongs
    };

    // ── State ─────────────────────────────────────────────────────────────

    private TextToSpeech tts;
    private boolean ttsReady = false;

    /** Shuffled indices 0-5, one pass per round — guarantees no repeats */
    private int[] questionOrder = new int[ROUND_SIZE];
    private int   questionIndex = 0;
    private boolean answered    = false;

    // ── Views ─────────────────────────────────────────────────────────────

    private FrameLayout      canvas;
    private ShapeSorterView  blockView;
    private DraggableShapeView[] pieces = new DraggableShapeView[3];
    private int correctPieceSlot; // 0, 1 or 2
    private View celebOverlay;

    // ── Lifecycle ─────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_shapes);

        canvas       = findViewById(R.id.dragCanvas);
        celebOverlay = findViewById(R.id.celebOverlay);

        celebOverlay.setVisibility(View.GONE);
        celebOverlay.findViewById(R.id.playAgainButton).setOnClickListener(v -> {
            celebOverlay.animate().alpha(0f).setDuration(280)
                    .withEndAction(() -> { celebOverlay.setVisibility(View.GONE); startRound(); })
                    .start();
        });
        celebOverlay.findViewById(R.id.menuButton).setOnClickListener(v -> finish());

        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        tts = new TextToSpeech(this, this);

        canvas.post(this::startRound);
    }

    @Override public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.ENGLISH);
            tts.setSpeechRate(0.85f);
            tts.setPitch(1.1f);
            ttsReady = true;
        }
    }

    /**
     * Adds points to the cumulative score in SharedPreferences.
     * MainActivity reads this in onResume() to display the running total.
     */
    private void saveScoreToPrefs(int points) {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
        int previous = prefs.getInt(MainActivity.KEY_TOTAL, 0);
        prefs.edit()
                .putInt(MainActivity.KEY_TOTAL, previous + points)
                .apply();
    }

    @Override protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }

    // ── Round / question setup ────────────────────────────────────────────

    private void startRound() {
        // Shuffle indices 0–5 into questionOrder — each shape appears exactly once
        List<Integer> pool = new ArrayList<>();
        for (int i = 0; i < ROUND_SIZE; i++) pool.add(i);
        Collections.shuffle(pool);
        for (int i = 0; i < ROUND_SIZE; i++) questionOrder[i] = pool.get(i);
        questionIndex = 0;
        loadQuestion(true);
    }

    private void loadQuestion(boolean firstLoad) {
        answered = false;
        canvas.removeAllViews();

        int   si     = questionOrder[questionIndex];   // shape index
        int   canW   = canvas.getWidth();
        int   canH   = canvas.getHeight();

        // ── Block ─────────────────────────────────────────────────────
        // Make the block large — 70% of the narrower canvas dimension
        int blockSize = (int)(Math.min(canW, canH) * 0.70f);
        int blockL    = (canW - blockSize) / 2;
        int blockT    = dpToPx(56); // below label

        blockView = new ShapeSorterView(this);
        blockView.setShape(ALL_SHAPES[si]);

        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(blockSize, blockSize);
        blp.leftMargin = blockL;
        blp.topMargin  = blockT;
        canvas.addView(blockView, blp);

        if (!firstLoad) {
            blockView.setScaleX(0f); blockView.setScaleY(0f);
            blockView.animate().scaleX(1f).scaleY(1f).setDuration(420)
                    .setInterpolator(new OvershootInterpolator(1.8f)).start();
        }

        // ── "Find the X" label ────────────────────────────────────────
        TextView lbl = new TextView(this);
        lbl.setText("Find the " + SHAPE_NAMES[si]);
        lbl.setTextSize(21f);
        lbl.setTypeface(null, android.graphics.Typeface.BOLD);
        lbl.setTextColor(0xFF3E2723);
        lbl.setGravity(android.view.Gravity.CENTER);
        lbl.setShadowLayer(dpToPx(2), 0, 1, 0x33000000);
        lbl.setBackground(makeLabelBg());
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(canW - dpToPx(32), dpToPx(44));
        llp.leftMargin = dpToPx(16);
        llp.topMargin  = dpToPx(8);
        canvas.addView(lbl, llp);

        // ── Stars row ─────────────────────────────────────────────────
        int starsTop = blockT + blockSize + dpToPx(6);
        addStarsRow(canW, starsTop);

        // ── Three shape pieces ────────────────────────────────────────
        // Piece size: 26% of canvas width — big and draggable
        int pieceSize  = (int)(canW * 0.26f);
        int totalW     = pieceSize * 3 + dpToPx(14) * 2;
        int startX     = (canW - totalW) / 2;
        int pieceTop   = canH - pieceSize - dpToPx(22);

        // Pick 2 wrong shapes (different from correct)
        List<Integer> wrongPool = new ArrayList<>();
        for (int i = 0; i < ALL_SHAPES.length; i++) if (i != si) wrongPool.add(i);
        Collections.shuffle(wrongPool);
        int wrong1 = wrongPool.get(0);
        int wrong2 = wrongPool.get(1);

        correctPieceSlot = (int)(Math.random() * 3);

        for (int t = 0; t < 3; t++) {
            boolean isCorrect = (t == correctPieceSlot);
            int shapeIdx, pieceColor;

            if (isCorrect) {
                shapeIdx   = si;
                pieceColor = SHAPE_COLORS[si];
            } else {
                // slot 0,1,2 — map wrong pieces to wrong1/wrong2
                int wrongIdx = (t < correctPieceSlot) ? t : t - 1;
                shapeIdx   = (wrongIdx == 0) ? wrong1 : wrong2;
                pieceColor = WRONG_COLORS[si][wrongIdx % 2];
            }

            DraggableShapeView piece = new DraggableShapeView(this);
            piece.setShape(ALL_SHAPES[shapeIdx]);
            piece.setShapeColor(pieceColor);
            piece.setTag(R.id.tag_number, isCorrect ? 1 : 0);

            int pieceLeft = startX + t * (pieceSize + dpToPx(14));
            FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(pieceSize, pieceSize);
            plp.leftMargin = pieceLeft;
            plp.topMargin  = pieceTop;
            canvas.addView(piece, plp);
            pieces[t] = piece;

            piece.setTag(R.id.tag_origin_x, pieceLeft);
            piece.setTag(R.id.tag_origin_y, pieceTop);

            // Bounce-in from below with stagger
            piece.setTranslationY(dpToPx(140));
            piece.setAlpha(0f);
            piece.animate().translationY(0f).alpha(1f)
                    .setDuration(420).setStartDelay(t * 90L)
                    .setInterpolator(new OvershootInterpolator(1.4f)).start();

            attachDrag(piece);
        }
    }

    // ── Stars row ─────────────────────────────────────────────────────────

    private void addStarsRow(int canW, int topMargin) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER);
        for (int i = 0; i < ROUND_SIZE; i++) {
            TextView s = new TextView(this);
            s.setText(i < questionIndex ? "⭐" : (i == questionIndex ? "✨" : "☆"));
            s.setTextSize(i == questionIndex ? 20 : 15);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(4, 0, 4, 0);
            row.addView(s, lp);
        }
        FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(canW, dpToPx(36));
        rlp.topMargin = topMargin;
        canvas.addView(row, rlp);
    }

    // ── Drag ──────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private void attachDrag(DraggableShapeView piece) {
        piece.setOnTouchListener(new View.OnTouchListener() {
            float dX, dY;
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (answered) return false;
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        dX = v.getX() - e.getRawX();
                        dY = v.getY() - e.getRawY();
                        v.bringToFront();
                        v.animate().scaleX(1.18f).scaleY(1.18f).setDuration(100).start();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        v.setX(e.getRawX() + dX);
                        v.setY(e.getRawY() + dY);
                        return true;
                    case MotionEvent.ACTION_UP:
                        v.animate().scaleX(1f).scaleY(1f).setDuration(80).start();
                        checkDrop((DraggableShapeView)v,
                                e.getRawX() + dX + v.getWidth() / 2f,
                                e.getRawY() + dY + v.getHeight() / 2f);
                        return true;
                }
                return false;
            }
        });
    }

    private void checkDrop(DraggableShapeView piece, float pCX, float pCY) {
        if (answered) return;

        // Map hole rect to canvas coordinates
        int[] bp = new int[2], cp = new int[2];
        blockView.getLocationOnScreen(bp);
        canvas.getLocationOnScreen(cp);

        RectF hole    = blockView.getHoleRect();
        float holeCX  = bp[0] - cp[0] + hole.centerX();
        float holeCY  = bp[1] - cp[1] + hole.centerY();
        float hitR    = hole.width() * 0.60f;

        boolean isCorrect = (int) piece.getTag(R.id.tag_number) == 1;

        if (Math.hypot(pCX - holeCX, pCY - holeCY) < hitR) {
            if (isCorrect) onCorrect(piece, holeCX, holeCY);
            else           onWrong(piece);
        } else {
            bounceBack(piece);
        }
    }

    // ── Correct ───────────────────────────────────────────────────────────

    private void onCorrect(DraggableShapeView piece, float holeCX, float holeCY) {
        answered = true;
        int si = questionOrder[questionIndex];

        float tx = holeCX - piece.getWidth()  / 2f;
        float ty = holeCY - piece.getHeight() / 2f;

        piece.animate().x(tx).y(ty)
                .setDuration(180).setInterpolator(new DecelerateInterpolator())
                .withEndAction(() ->
                        piece.animate().scaleX(0.15f).scaleY(0.15f).alpha(0f)
                                .setDuration(280).setInterpolator(new DecelerateInterpolator())
                                .withEndAction(() -> {
                                    piece.setVisibility(View.INVISIBLE);
                                    blockJiggle();
                                    burstStars(holeCX, holeCY);
                                    speak(SHAPE_SPEECH[si]);
                                    new Handler().postDelayed(() -> {
                                        questionIndex++;
                                        if (questionIndex >= ROUND_SIZE) showCelebration();
                                        else                             loadQuestion(false);
                                    }, 1300);
                                }).start()
                ).start();
    }

    // ── Wrong ────────────────────────────────────────────────────────────

    private void onWrong(DraggableShapeView piece) {
        ObjectAnimator.ofFloat(blockView, "translationX", 0,-16,16,-11,11,-6,6,0)
                .setDuration(400).start();
        ObjectAnimator.ofFloat(piece, "rotation", 0,-22,22,-15,15,0)
                .setDuration(360).start();
        new Handler().postDelayed(() -> bounceBack(piece), 380);
        speak("Oops! Try again!");
    }

    private void bounceBack(View v) {
        float ox = (float)(int) v.getTag(R.id.tag_origin_x);
        float oy = (float)(int) v.getTag(R.id.tag_origin_y);
        v.animate().x(ox).y(oy).rotation(0f)
                .setDuration(440).setInterpolator(new BounceInterpolator()).start();
    }

    // ── Block jiggle ─────────────────────────────────────────────────────

    private void blockJiggle() {
        blockView.animate().rotationBy(7f).setDuration(70).withEndAction(() ->
                blockView.animate().rotationBy(-14f).setDuration(70).withEndAction(() ->
                        blockView.animate().rotationBy(7f).setDuration(70).start()
                ).start()).start();
    }

    // ── Star burst ───────────────────────────────────────────────────────

    private void burstStars(float cx, float cy) {
        String[] em = {"⭐","🌟","✨","💫","🎉","🎊"};
        for (int i = 0; i < 6; i++) {
            TextView t = new TextView(this);
            t.setText(em[i]);
            t.setTextSize(24f);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dpToPx(44), dpToPx(44));
            lp.leftMargin = (int)cx - dpToPx(22);
            lp.topMargin  = (int)cy - dpToPx(22);
            canvas.addView(t, lp);
            double angle = i * 60 * Math.PI / 180;
            float dx = (float)(Math.cos(angle) * dpToPx(90));
            float dy = (float)(Math.sin(angle) * dpToPx(90));
            t.animate().translationX(dx).translationY(dy).alpha(0f).scaleX(1.6f).scaleY(1.6f)
                    .setDuration(650).setStartDelay(i * 35L)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> canvas.removeView(t)).start();
        }
    }

    // ── Celebration ───────────────────────────────────────────────────────

    private void showCelebration() {
        speak("Amazing! You sorted all the shapes! You are a star!");
        saveScoreToPrefs(6); // award 6 stars (one per shape sorted) for completing a round
        celebOverlay.setVisibility(View.VISIBLE);
        celebOverlay.setAlpha(0f);
        celebOverlay.animate().alpha(1f).setDuration(480).start();
        TextView ce = celebOverlay.findViewById(R.id.celebEmoji);
        if (ce != null) {
            ce.setScaleX(0f); ce.setScaleY(0f);
            ce.animate().scaleX(1f).scaleY(1f).setDuration(580).setStartDelay(200)
                    .setInterpolator(new BounceInterpolator()).start();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Semi-transparent white pill for the label */
    private android.graphics.drawable.GradientDrawable makeLabelBg() {
        android.graphics.drawable.GradientDrawable gd =
                new android.graphics.drawable.GradientDrawable();
        gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        gd.setCornerRadius(dpToPx(22));
        gd.setColor(0xCCFFFFFF);
        return gd;
    }

    private void speak(String t) {
        if (ttsReady) tts.speak(t, TextToSpeech.QUEUE_FLUSH, null, "shape");
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}