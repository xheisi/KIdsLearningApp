package com.example.kidslearningapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DURATION_MS = 2000;
    private static final int TICK_MS = 20; // update every 20ms → smooth animation

    private final Handler handler = new Handler();
    private int progress = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        setContentView(R.layout.activity_splash);

        ProgressBar progressBar = findViewById(R.id.splashProgressBar);

        // Animate progress bar from 0 → 100 over SPLASH_DURATION_MS
        int totalTicks = SPLASH_DURATION_MS / TICK_MS;       // 100 ticks
        int incrementPerTick = 100 / totalTicks;              // 1 per tick

        Runnable ticker = new Runnable() {
            @Override
            public void run() {
                progress += incrementPerTick;
                progressBar.setProgress(Math.min(progress, 100));

                if (progress < 100) {
                    handler.postDelayed(this, TICK_MS);
                } else {
                    // Progress complete → launch MainActivity
                    startActivity(new Intent(SplashActivity.this, MainActivity.class));
                    finish();
                }
            }
        };

        handler.postDelayed(ticker, TICK_MS);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null); // avoid leaks
        super.onDestroy();
    }
}