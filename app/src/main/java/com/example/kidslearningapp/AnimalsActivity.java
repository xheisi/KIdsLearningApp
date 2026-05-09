package com.example.kidslearningapp;

import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class AnimalsActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private static final int TOTAL_QUESTIONS = 10;

    private static final Animal[] ANIMALS = {
            new Animal("Dog", R.drawable.dog, R.raw.dog),
            new Animal("Cat", R.drawable.cat, R.raw.cat),
            new Animal("Cow", R.drawable.cow, R.raw.cow),
            new Animal("Duck", R.drawable.duck, R.raw.duck),
            new Animal("Lion", R.drawable.lion, R.raw.lion),
            new Animal("Elephant", R.drawable.elephant, R.raw.elephant),
            new Animal("Frog", R.drawable.frog, R.raw.frog),
            new Animal("Pig", R.drawable.pig, R.raw.pig),
            new Animal("Horse", R.drawable.horse, R.raw.horse),
            new Animal("Sheep", R.drawable.sheep, R.raw.sheep),
            new Animal("Monkey", R.drawable.monkey, R.raw.monkey),
            new Animal("Bear", R.drawable.bear, R.raw.bear),
            new Animal("Chicken", R.drawable.chicken, R.raw.chicken),
            new Animal("Donkey", R.drawable.donkey, R.raw.donkey),
            new Animal("Wolf", R.drawable.wolf, R.raw.wolf)
    };

    private TextView animalNameText, scoreText, progressText, feedbackText;
    private ProgressBar progressBar;
    private Button nextButton, ttsButton, backButton;

    private final LinearLayout[] optionLayouts = new LinearLayout[4];
    private final ImageView[] optionImages = new ImageView[4];
    private final TextView[] optionLabels = new TextView[4];

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private MediaPlayer mediaPlayer;

    private int questionIndex = 0;
    private int score = 0;

    private int[] questionOrder;
    private int correctOptionIndex;
    private int currentAnimalIndex;

    private final int[] optionAnimalIndices = new int[4];
    private int selectedOptionIndex = -1;
    private boolean resultShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.activity_animals);

        animalNameText = findViewById(R.id.animalNameText);
        scoreText = findViewById(R.id.scoreText);
        progressText = findViewById(R.id.progressText);
        feedbackText = findViewById(R.id.feedbackText);
        progressBar = findViewById(R.id.progressBar);
        nextButton = findViewById(R.id.nextButton);
        ttsButton = findViewById(R.id.ttsButton);
        backButton = findViewById(R.id.backButton);

        optionLayouts[0] = findViewById(R.id.option1);
        optionLayouts[1] = findViewById(R.id.option2);
        optionLayouts[2] = findViewById(R.id.option3);
        optionLayouts[3] = findViewById(R.id.option4);

        optionImages[0] = findViewById(R.id.option1Image);
        optionImages[1] = findViewById(R.id.option2Image);
        optionImages[2] = findViewById(R.id.option3Image);
        optionImages[3] = findViewById(R.id.option4Image);

        optionLabels[0] = findViewById(R.id.option1Label);
        optionLabels[1] = findViewById(R.id.option2Label);
        optionLabels[2] = findViewById(R.id.option3Label);
        optionLabels[3] = findViewById(R.id.option4Label);

        tts = new TextToSpeech(this, this);

        backButton.setOnClickListener(v -> finish());
        ttsButton.setOnClickListener(v -> speakAnimalName());

        nextButton.setOnClickListener(v -> {
            if (!resultShown) {
                checkAnswer();
            } else {
                questionIndex++;

                if (questionIndex >= TOTAL_QUESTIONS) {
                    showResult();
                } else {
                    loadQuestion();
                }
            }
        });

        for (int i = 0; i < 4; i++) {
            final int index = i;
            optionLayouts[i].setOnClickListener(v -> handleOptionTap(index));
        }

        buildQuestionOrder();
        loadQuestion();
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

    private void buildQuestionOrder() {
        Integer[] pool = new Integer[ANIMALS.length];

        for (int i = 0; i < ANIMALS.length; i++) {
            pool[i] = i;
        }

        List<Integer> poolList = Arrays.asList(pool);
        Collections.shuffle(poolList);

        questionOrder = new int[TOTAL_QUESTIONS];

        for (int i = 0; i < TOTAL_QUESTIONS; i++) {
            questionOrder[i] = poolList.get(i % ANIMALS.length);
        }
    }

    private void loadQuestion() {
        selectedOptionIndex = -1;
        resultShown = false;

        feedbackText.setText("");
        nextButton.setText("Check");
        nextButton.setVisibility(View.GONE);

        for (LinearLayout layout : optionLayouts) {
            layout.setBackground(getDrawable(R.drawable.quiz_option_normal));
            layout.setEnabled(true);
        }

        currentAnimalIndex = questionOrder[questionIndex];
        Animal correctAnimal = ANIMALS[currentAnimalIndex];

        animalNameText.setText(correctAnimal.name.toUpperCase());
        progressText.setText((questionIndex + 1) + "/" + TOTAL_QUESTIONS);
        progressBar.setMax(TOTAL_QUESTIONS);
        progressBar.setProgress(questionIndex + 1);
        scoreText.setText("⭐ " + score);

        int[] wrongIndices = pickWrongOptions(currentAnimalIndex, 3);
        correctOptionIndex = new Random().nextInt(4);

        int wrongIndex = 0;

        for (int i = 0; i < 4; i++) {
            int animalIndex;

            if (i == correctOptionIndex) {
                animalIndex = currentAnimalIndex;
            } else {
                animalIndex = wrongIndices[wrongIndex];
                wrongIndex++;
            }

            optionAnimalIndices[i] = animalIndex;

            Animal optionAnimal = ANIMALS[animalIndex];
            optionImages[i].setImageResource(optionAnimal.imageResId);
            optionLabels[i].setText(optionAnimal.name);
        }
    }

    private int[] pickWrongOptions(int correctIndex, int count) {
        int[] result = new int[count];
        List<Integer> pool = new java.util.ArrayList<>();

        for (int i = 0; i < ANIMALS.length; i++) {
            if (i != correctIndex) {
                pool.add(i);
            }
        }

        Collections.shuffle(pool);

        for (int i = 0; i < count; i++) {
            result[i] = pool.get(i);
        }

        return result;
    }

    private void handleOptionTap(int tappedIndex) {
        if (resultShown) return;

        selectedOptionIndex = tappedIndex;

        for (LinearLayout layout : optionLayouts) {
            layout.setBackground(getDrawable(R.drawable.quiz_option_normal));
        }

        optionLayouts[tappedIndex].setBackground(getDrawable(R.drawable.quiz_option_selected));

        playOptionAnimalSound(tappedIndex);

        feedbackText.setText("Press next when ready!");
        feedbackText.setTextColor(0xFF5D4037);

        nextButton.setVisibility(View.VISIBLE);
    }

    private void checkAnswer() {
        if (selectedOptionIndex == -1) {
            Toast.makeText(this, "Pick an animal first!", Toast.LENGTH_SHORT).show();
            return;
        }

        resultShown = true;

        for (LinearLayout layout : optionLayouts) {
            layout.setEnabled(false);
        }

        if (selectedOptionIndex == correctOptionIndex) {
            optionLayouts[selectedOptionIndex].setBackground(getDrawable(R.drawable.quiz_option_correct));
            feedbackText.setText("✅ Great job!");
            feedbackText.setTextColor(0xFF388E3C);
            score++;
            scoreText.setText("⭐ " + score);
        } else {
            optionLayouts[selectedOptionIndex].setBackground(getDrawable(R.drawable.quiz_option_wrong));
            optionLayouts[correctOptionIndex].setBackground(getDrawable(R.drawable.quiz_option_correct));
            feedbackText.setText("❌ Good try! This is " + ANIMALS[currentAnimalIndex].name);
            feedbackText.setTextColor(0xFFC62828);
        }

        playAnimalSound();

        nextButton.setText("Next");
        nextButton.setVisibility(View.VISIBLE);
    }

    private void speakAnimalName() {
        if (!ttsReady) return;

        String name = ANIMALS[currentAnimalIndex].name;
        tts.speak(name, TextToSpeech.QUEUE_FLUSH, null, "animal_name");
    }

    private void playOptionAnimalSound(int optionIndex) {
        stopAnimalSound();

        int animalIndex = optionAnimalIndices[optionIndex];
        int soundId = ANIMALS[animalIndex].soundResId;

        mediaPlayer = MediaPlayer.create(this, soundId);

        if (mediaPlayer != null) {
            mediaPlayer.setOnCompletionListener(mp -> {
                mp.release();
                mediaPlayer = null;
            });

            mediaPlayer.start();
        }
    }

    private void playAnimalSound() {
        stopAnimalSound();

        int soundId = ANIMALS[currentAnimalIndex].soundResId;

        mediaPlayer = MediaPlayer.create(this, soundId);

        if (mediaPlayer != null) {
            mediaPlayer.setOnCompletionListener(mp -> {
                mp.release();
                mediaPlayer = null;
            });

            mediaPlayer.start();
        }
    }

    private void stopAnimalSound() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }

            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private void showResult() {
        String msg;

        if (score >= 9) {
            msg = "Amazing! " + score + "/" + TOTAL_QUESTIONS;
        } else if (score >= 7) {
            msg = "Great job! " + score + "/" + TOTAL_QUESTIONS;
        } else if (score >= 5) {
            msg = "Good try! " + score + "/" + TOTAL_QUESTIONS;
        } else {
            msg = "Keep practising! " + score + "/" + TOTAL_QUESTIONS;
        }

        if (ttsReady) {
            tts.speak("Quiz finished. Your score is " + score + " out of " + TOTAL_QUESTIONS,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "result");
        }

        saveScoreToPrefs(score);
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();

        new Handler().postDelayed(this::finish, 2500);
    }

    /**
     * Adds this session's score to the cumulative total in SharedPreferences.
     * MainActivity reads this in onResume() to display the running total.
     */
    private void saveScoreToPrefs(int sessionScore) {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
        int previous = prefs.getInt(MainActivity.KEY_TOTAL, 0);
        prefs.edit()
                .putInt(MainActivity.KEY_TOTAL, previous + sessionScore)
                .apply();
    }

    @Override
    protected void onDestroy() {
        stopAnimalSound();

        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }

        super.onDestroy();
    }

    private static class Animal {
        String name;
        int imageResId;
        int soundResId;

        Animal(String name, int imageResId, int soundResId) {
            this.name = name;
            this.imageResId = imageResId;
            this.soundResId = soundResId;
        }
    }
}