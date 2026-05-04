package com.example.kidslearningapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    Button animalsButton, numbersButton, alphabetButton, shapesButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.activity_main);

        alphabetButton = findViewById(R.id.alphabetButton);
        numbersButton = findViewById(R.id.numbersButton);
        animalsButton = findViewById(R.id.animalsButton);
        shapesButton = findViewById(R.id.shapesButton);

        alphabetButton.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, AlphabetActivity.class)));

        numbersButton.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, NumbersActivity.class)));

        animalsButton.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, AnimalsActivity.class)));

        shapesButton.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, ShapesActivity.class)));
    }
}