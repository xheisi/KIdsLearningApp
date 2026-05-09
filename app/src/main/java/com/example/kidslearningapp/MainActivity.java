package com.example.kidslearningapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * MainActivity — Home screen.
 *
 * NEW (requirements fix):
 *  1. Reads cumulative score from SharedPreferences in onResume() → data persistence requirement.
 *  2. "Share My Score" button fires an implicit ACTION_SEND Intent → implicit Intent requirement.
 */
public class MainActivity extends AppCompatActivity {

    /** SharedPreferences file name — shared across all quiz activities */
    public static final String PREFS_NAME = "KidsAppPrefs";
    /** Key for cumulative star total */
    public static final String KEY_TOTAL  = "total_score";

    private Button alphabetButton, numbersButton, animalsButton, shapesButton, shareButton;
    private TextView totalScoreText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        setContentView(R.layout.activity_main);

        alphabetButton = findViewById(R.id.alphabetButton);
        numbersButton  = findViewById(R.id.numbersButton);
        animalsButton  = findViewById(R.id.animalsButton);
        shapesButton   = findViewById(R.id.shapesButton);
        shareButton    = findViewById(R.id.shareButton);
        totalScoreText = findViewById(R.id.totalScoreText);

        // Explicit intents — unchanged
        alphabetButton.setOnClickListener(v ->
                startActivity(new Intent(this, AlphabetActivity.class)));
        numbersButton.setOnClickListener(v ->
                startActivity(new Intent(this, NumbersActivity.class)));
        animalsButton.setOnClickListener(v ->
                startActivity(new Intent(this, AnimalsActivity.class)));
        shapesButton.setOnClickListener(v ->
                startActivity(new Intent(this, ShapesActivity.class)));

        // Implicit Intent — share score via any installed app (WhatsApp, Email, etc.)
        shareButton.setOnClickListener(v -> shareScore());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh score every time we return here (after finishing a quiz)
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int total = prefs.getInt(KEY_TOTAL, 0);
        totalScoreText.setText("⭐ Total Stars: " + total);
    }

    /**
     * Fires an implicit ACTION_SEND Intent so the user can share their score
     * via any app they prefer — this fulfils the implicit Intent requirement.
     */
    private void shareScore() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int total = prefs.getInt(KEY_TOTAL, 0);

        String message = "I scored " + total + " ⭐ stars on Kids Learning App!\n"
                + "Can you beat my score? 🎉";

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, message);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "My Kids Learning App Score");

        startActivity(Intent.createChooser(shareIntent, "Share your score via…"));
    }
}