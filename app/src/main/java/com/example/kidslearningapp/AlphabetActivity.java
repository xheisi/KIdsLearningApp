package com.example.kidslearningapp;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.SharedPreferences;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * AlphabetActivity
 * ─────────────────
 * Tracing validation:
 *   When the child presses Next, we measure what fraction of their touch
 *   points landed inside the letter's bounding box.
 *     ≥ 25 %  → accept, animate to next letter
 *     < 25 %  → shake the card + show "Trace the letter!" hint, don't advance
 *
 *   Threshold is intentionally lenient (25%) so kids aren't frustrated,
 *   but scribbling in the corner or tapping once without drawing is rejected.
 */
public class AlphabetActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    // ── Letter data: { LETTER, phonetic, example word, emoji } ───────────
    private static final String[][] LETTER_DATA = {
            {"A","ay",  "Apple",    "🍎"}, {"B","buh","Bear",     "🐻"},
            {"C","kuh", "Cat",      "🐱"}, {"D","duh","Dog",      "🐶"},
            {"E","eh",  "Elephant", "🐘"}, {"F","fuh","Frog",     "🐸"},
            {"G","guh", "Giraffe",  "🦒"}, {"H","huh","Horse",    "🐴"},
            {"I","ih",  "Ice cream","🍦"}, {"J","juh","Jellyfish","🪼"},
            {"K","kuh", "Kite",     "🪁"}, {"L","luh","Lion",     "🦁"},
            {"M","muh", "Monkey",   "🐵"}, {"N","nuh","Nest",     "🪺"},
            {"O","oh",  "Octopus",  "🐙"}, {"P","puh","Pig",      "🐷"},
            {"Q","kwuh","Queen",    "👑"}, {"R","ruh","Rabbit",   "🐰"},
            {"S","suh", "Snake",    "🐍"}, {"T","tuh","Tiger",    "🐯"},
            {"U","uh",  "Umbrella", "☂️"}, {"V","vuh","Volcano",  "🌋"},
            {"W","wuh", "Whale",    "🐋"}, {"X","ks", "X-ray",    "🩻"},
            {"Y","yuh", "Yak",      "🦬"}, {"Z","zuh","Zebra",    "🦓"},
    };

    private static final int[] BG_TOP = {
            0xFFFF6B6B,0xFFFF9F43,0xFFFFD93D,0xFF6BCB77,0xFF4D96FF,
            0xFFB983FF,0xFFFF6BD6,0xFF00D2D3,0xFFFF6B6B,0xFF54A0FF,
    };
    private static final int[] BG_BOT = {
            0xFFFF9F43,0xFFFFD93D,0xFFF9CA24,0xFF00D2D3,0xFFB983FF,
            0xFFFF6BD6,0xFF4D96FF,0xFF6BCB77,0xFFFF9F43,0xFF48DBFB,
    };

    private static final int ROUND_SIZE       = 6;
    private static final int COVERAGE_THRESHOLD = 25; // % of strokes inside letter zone

    // ── State ─────────────────────────────────────────────────────────────
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private int[] questionOrder;
    private int   questionIndex = 0;

    // ── Views ─────────────────────────────────────────────────────────────
    private View           bgView;
    private TextView       letterBig, letterSmall, emojiText, wordHintText, progressText;
    private TextView       traceHintText;   // "Try again!" feedback
    private Button         hearButton, nextButton, clearButton;
    private LetterTraceView traceView;
    private LinearLayout   starsRow;
    private FrameLayout    letterCard;      // card containing the letter — used for shake
    private View           celebOverlay;
    private Button         playAgainButton, menuButton;

    // ── Lifecycle ─────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_alphabet);

        bindViews();
        tts = new TextToSpeech(this, this);

        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        hearButton.setOnClickListener(v -> {
            hearButton.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80)
                    .withEndAction(() ->
                            hearButton.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    ).start();
            speakCurrent();
        });

        clearButton.setOnClickListener(v -> {
            traceView.clearTrace();
            traceHintText.setVisibility(View.GONE);
            clearButton.animate().rotationBy(360f).setDuration(400).start();
        });

        nextButton.setOnClickListener(v -> attemptAdvance());

        buildRound();
        loadLetter(true);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.ENGLISH);
            tts.setSpeechRate(0.82f);
            tts.setPitch(1.15f);
            ttsReady = true;
            new Handler().postDelayed(this::speakCurrent, 900);
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

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }

    // ── Round / letter loading ────────────────────────────────────────────
    private void buildRound() {
        List<Integer> pool = new ArrayList<>();
        for (int i = 0; i < LETTER_DATA.length; i++) pool.add(i);
        Collections.shuffle(pool);
        questionOrder = new int[ROUND_SIZE];
        for (int i = 0; i < ROUND_SIZE; i++) questionOrder[i] = pool.get(i);
        questionIndex = 0;
    }

    private void loadLetter(boolean firstLoad) {
        int idx = questionOrder[questionIndex];
        String[] d = LETTER_DATA[idx];

        letterBig.setText(d[0]);
        letterSmall.setText(d[0].toLowerCase());
        emojiText.setText(d[3]);
        wordHintText.setText("like " + d[2] + "!");
        progressText.setText((questionIndex + 1) + " / " + ROUND_SIZE);
        traceHintText.setVisibility(View.GONE);

        int top = BG_TOP[questionIndex % BG_TOP.length];
        int bot = BG_BOT[questionIndex % BG_BOT.length];
        ObjectAnimator.ofArgb(bgView, "backgroundColor", top, bot)
                .setDuration(600).start();

        updateStars();
        traceView.clearTrace();
        if (!firstLoad) animateLetterIn();
    }

    private void animateLetterIn() {
        letterBig.setScaleX(0f); letterBig.setScaleY(0f); letterBig.setRotation(-12f);
        letterBig.animate().scaleX(1f).scaleY(1f).rotation(0f)
                .setDuration(480).setInterpolator(new OvershootInterpolator(2.2f)).start();

        letterSmall.setTranslationX(100f); letterSmall.setAlpha(0f);
        letterSmall.animate().translationX(0f).alpha(1f)
                .setDuration(400).setStartDelay(140).setInterpolator(new DecelerateInterpolator()).start();

        emojiText.setScaleX(0f); emojiText.setScaleY(0f);
        emojiText.animate().scaleX(1f).scaleY(1f)
                .setDuration(380).setStartDelay(200).setInterpolator(new BounceInterpolator()).start();
    }

    // ── Validation + advance ──────────────────────────────────────────────

    /**
     * Called when the child taps "Next".
     * Measures coverage of the child's tracing against the letter's bounding box.
     * If coverage is too low → reject with a shake + hint.
     * If the child hasn't drawn at all → also reject.
     */
    private void attemptAdvance() {
        if (!traceView.hasDrawn()) {
            showTraceHint("✏️ Trace the letter first!");
            shakeCard();
            return;
        }

        // Get the letter view's bounding box in screen coordinates,
        // then convert to coordinates relative to the traceView.
        int[] letterPos  = new int[2];
        int[] tracePos   = new int[2];
        letterBig.getLocationOnScreen(letterPos);
        traceView.getLocationOnScreen(tracePos);

        float left   = letterPos[0] - tracePos[0];
        float top    = letterPos[1] - tracePos[1];
        float right  = left + letterBig.getWidth();
        float bottom = top  + letterBig.getHeight();
        RectF letterRect = new RectF(left, top, right, bottom);

        int coverage = traceView.checkCoverage(letterRect);

        if (coverage < COVERAGE_THRESHOLD) {
            showTraceHint("🎯 Trace over the letter!");
            shakeCard();
            // Pulse the guide letter briefly so kid looks at it
            letterBig.animate().alpha(0.6f).setDuration(150)
                    .withEndAction(() -> letterBig.animate().alpha(0.22f).setDuration(300).start())
                    .start();
        } else {
            // Good trace — advance
            doAdvance();
        }
    }

    private void doAdvance() {
        traceHintText.setVisibility(View.GONE);
        letterBig.animate()
                .translationX(-getResources().getDisplayMetrics().widthPixels * 0.6f).alpha(0f)
                .setDuration(240).setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    letterBig.setTranslationX(0f); letterBig.setAlpha(1f);
                    questionIndex++;
                    if (questionIndex >= ROUND_SIZE) {
                        showCelebration();
                    } else {
                        loadLetter(false);
                        new Handler().postDelayed(this::speakCurrent, 700);
                    }
                }).start();
    }

    // ── Shake animation ───────────────────────────────────────────────────
    private void shakeCard() {
        ObjectAnimator shake = ObjectAnimator.ofFloat(
                letterCard, "translationX",
                0, -22f, 22f, -18f, 18f, -10f, 10f, -5f, 5f, 0);
        shake.setDuration(500);
        shake.start();
    }

    private void showTraceHint(String msg) {
        traceHintText.setText(msg);
        traceHintText.setVisibility(View.VISIBLE);
        traceHintText.setAlpha(0f);
        traceHintText.animate().alpha(1f).setDuration(250).start();
        // Auto-hide after 2 seconds
        new Handler().postDelayed(() ->
                        traceHintText.animate().alpha(0f).setDuration(300)
                                .withEndAction(() -> traceHintText.setVisibility(View.GONE)).start(),
                2000);
    }

    // ── TTS ───────────────────────────────────────────────────────────────
    private void speakCurrent() {
        if (!ttsReady) return;
        String[] d = LETTER_DATA[questionOrder[questionIndex]];
        tts.speak(d[0] + "! . " + d[1] + "! . Like " + d[2] + "!",
                TextToSpeech.QUEUE_FLUSH, null, "alpha");
    }

    // ── Stars ─────────────────────────────────────────────────────────────
    private void updateStars() {
        starsRow.removeAllViews();
        for (int i = 0; i < ROUND_SIZE; i++) {
            TextView s = new TextView(this);
            s.setText(i < questionIndex ? "⭐" : (i == questionIndex ? "✨" : "☆"));
            s.setTextSize(i == questionIndex ? 20 : 15);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(4, 0, 4, 0);
            starsRow.addView(s, lp);
        }
    }

    private void showCelebration() {
        if (ttsReady) {
            tts.speak(
                    "Great job! Do you want to continue practicing?",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "done"
            );
        }

        saveScoreToPrefs(10); // award 10 stars for completing a round
        celebOverlay.setVisibility(View.VISIBLE);
        celebOverlay.setAlpha(0f);
        celebOverlay.animate()
                .alpha(1f)
                .setDuration(350)
                .start();

        TextView ce = celebOverlay.findViewById(R.id.celebEmoji);
        ce.setText("🌟");
        ce.setScaleX(0f);
        ce.setScaleY(0f);

        ce.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(500)
                .setStartDelay(150)
                .setInterpolator(new BounceInterpolator())
                .start();

        playAgainButton.setText("Continue");
        menuButton.setText("Back to Menu");

        playAgainButton.setOnClickListener(v ->
                celebOverlay.animate()
                        .alpha(0f)
                        .setDuration(250)
                        .withEndAction(() -> {
                            celebOverlay.setVisibility(View.GONE);
                            buildRound();
                            loadLetter(true);
                        })
                        .start()
        );

        menuButton.setOnClickListener(v -> finish());
    }
    // ── Bind views ────────────────────────────────────────────────────────
    private void bindViews() {
        bgView         = findViewById(R.id.bgView);
        letterBig      = findViewById(R.id.letterBig);
        letterSmall    = findViewById(R.id.letterSmall);
        emojiText      = findViewById(R.id.emojiText);
        wordHintText   = findViewById(R.id.wordHintText);
        progressText   = findViewById(R.id.progressText);
        traceHintText  = findViewById(R.id.traceHintText);
        hearButton     = findViewById(R.id.hearButton);
        nextButton     = findViewById(R.id.nextButton);
        clearButton    = findViewById(R.id.clearButton);
        traceView      = findViewById(R.id.traceView);
        starsRow       = findViewById(R.id.starsRow);
        letterCard     = findViewById(R.id.letterCard);
        celebOverlay   = findViewById(R.id.celebOverlay);
        playAgainButton = celebOverlay.findViewById(R.id.playAgainButton);
        menuButton      = celebOverlay.findViewById(R.id.menuButton);
    }
}