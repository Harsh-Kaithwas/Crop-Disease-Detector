package com.harsh.cropdiseasedetector;

import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.card.MaterialCardView;

import java.util.Locale;

public class ExpertModeActivity extends AppCompatActivity {

    private TextToSpeech tts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_expert_mode);

            // TTS ko tabhi shuru karo jab layout load ho jaye
            new android.os.Handler().postDelayed(() -> {
                tts = new TextToSpeech(ExpertModeActivity.this, status -> {
                    if (status != TextToSpeech.ERROR) {
                        tts.setLanguage(new Locale("hi", "IN"));
                    }
                });
            }, 500);

            setupClickListeners();
        } catch (Exception e) {
            // Agar layout phatega toh ye error dikha dega crash hone ke bajaye
            android.util.Log.e("LayoutInflateError", e.getMessage());
        }
    }

    private void setupClickListeners() {
        // 1. Scan History
        findViewById(R.id.cardHistory).setOnClickListener(v -> {
            speakAndNavigate("पिछली जांच का इतिहास खुल रहा है", null);
        });

        // 2. Weather Forecast (Online)
        findViewById(R.id.cardWeather).setOnClickListener(v -> {
            speakAndNavigate("आज का मौसम और बारिश की जानकारी", null);
        });

        // 3. Mandi Rates (Online)
        findViewById(R.id.cardMandi).setOnClickListener(v -> {
            speakAndNavigate("ताजा मंडी भाव की जानकारी", null);
        });

        // 4. Crop Details
        findViewById(R.id.cardCrops).setOnClickListener(v -> {
            speakAndNavigate("फसलों की जानकारी और देखभाल के तरीके", null);
        });

        // 5. Government Schemes
        findViewById(R.id.cardSchemes).setOnClickListener(v -> {
            speakAndNavigate("सरकारी योजनाएं और सब्सिडी की जानकारी", null);
        });

        // 6. Earning Tracker
        findViewById(R.id.cardEarning).setOnClickListener(v -> {
            speakAndNavigate("कमाई और खर्चे का हिसाब", null);
        });
    }

    private void speakAndNavigate(String message, Class<?> destination) {
        tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, null);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

        if (destination != null) {
            Intent intent = new Intent(ExpertModeActivity.this, destination);
            startActivity(intent);
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