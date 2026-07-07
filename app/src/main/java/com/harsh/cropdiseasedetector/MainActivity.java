package com.***.*******;

import android.Manifest;
import android.content.Intent; // Ise add kiya
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "CropDetector";
    private static final int MODEL_INPUT_SIZE = 224;

    private PreviewView previewView;
    private ImageView imagePreview;
    // btnMenu ko yahan top par define kiya taaki error na aaye
    private MaterialButton btnCapture, btnDetect, btnGallery, btnMenu;
    private FloatingActionButton btnToggleFlash;
    private TextView tvResult;
    private MaterialCardView resultCard;

    private Interpreter tflite;
    private ImageCapture imageCapture;
    private Camera camera;
    private TextToSpeech tts;
    private boolean isFlashOn = false;

    private List<String> labels = new ArrayList<>();
    private JSONObject remediesJson;
    private Bitmap lastCapturedBitmap = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // UI Binding
        previewView = findViewById(R.id.previewView);
        imagePreview = findViewById(R.id.imagePreview);
        btnCapture = findViewById(R.id.btnCapture);
        btnDetect = findViewById(R.id.btnDetect);
        btnGallery = findViewById(R.id.btnGallery);
        btnMenu = findViewById(R.id.btnMenu); // XML mein ID btnHistory hi rakhi hai na?
        btnToggleFlash = findViewById(R.id.btnToggleFlash);
        tvResult = findViewById(R.id.tvResult);
        resultCard = findViewById(R.id.resultCard);

        // TTS Init
        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.ERROR) tts.setLanguage(new Locale("hi", "IN"));
        });

        if (!loadModelAndAssets()) {
            tvResult.setText("Error loading assets!");
        }

        setupClickListeners();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 100);
        }
    }

    private void setupClickListeners() {
        btnCapture.setOnClickListener(v -> {
            resultCard.setVisibility(View.GONE);
            btnDetect.setVisibility(View.GONE);

            if (lastCapturedBitmap != null) {
                lastCapturedBitmap = null;
                startCamera();
            } else {
                takePhoto();
            }
        });

        btnDetect.setOnClickListener(v -> {
            if (lastCapturedBitmap != null) {
                btnDetect.setVisibility(View.GONE);
                resultCard.setVisibility(View.VISIBLE);
                tvResult.setText("जाँच की जा रही है... कृपया प्रतीक्षा करें");
                tts.speak("जाँच की जा रही है, कृपया प्रतीक्षा करें", TextToSpeech.QUEUE_FLUSH, null, null);

                new Handler().postDelayed(() -> {
                    runInference(lastCapturedBitmap);
                }, 2000);
            }
        });

        btnGallery.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(intent, 101);
        });

        // Ab btnMenu yahan accessible hai
        btnMenu.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ExpertModeActivity.class);
            startActivity(intent);
            });

        btnToggleFlash.setOnClickListener(v -> {
            if (camera != null) {
                isFlashOn = !isFlashOn;
                camera.getCameraControl().enableTorch(isFlashOn);
            }
        });
    }

    private void runInference(Bitmap bitmap) {
        if (isImageTooDark(bitmap)) {
            tvResult.setText("⚠️ कैमरा ढका हुआ है या बहुत अंधेरा है।\nकृपया रोशनी में फोटो लें।");
            tts.speak("कैमरा ढका हुआ है या बहुत अंधेरा है", TextToSpeech.QUEUE_FLUSH, null, null);
            return;
        }

        ByteBuffer inputBuffer = convertBitmapToByteBuffer(bitmap);
        int modelOutputClasses = tflite.getOutputTensor(0).shape()[1];
        float[][] output = new float[1][modelOutputClasses];

        tflite.run(inputBuffer, output);

        float[] probabilities = output[0];
        int maxIndex = -1;
        float maxConfidence = 0.0f;

        int limit = Math.min(probabilities.length, labels.size());
        for (int i = 0; i < limit; i++) {
            if (probabilities[i] > maxConfidence) {
                maxConfidence = probabilities[i];
                maxIndex = i;
            }
        }

        if (maxIndex != -1 && maxIndex < labels.size()) {
            String label = labels.get(maxIndex);
            // Accuracy Fix: Ensuring 100% cap
            int accuracyPercent = (int) (maxConfidence * 100);
            if (accuracyPercent > 100) accuracyPercent = 100;

            if (maxConfidence >= 0.40f) {
                showFinalResult(label, (float)accuracyPercent / 100.0f);
            } else {
                tvResult.setText("⚠️ पहचान पक्की नहीं है (" + accuracyPercent + "%)\nकृपया दोबारा फोटो लें।");
                tts.speak("पहचान पक्की नहीं है", TextToSpeech.QUEUE_FLUSH, null, null);
            }
        } else {
            tvResult.setText("⚠️ फसल की जांच नहीं हो पाई।");
            tts.speak("फसल की जांच नहीं हो पाई।", TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    private void showFinalResult(String label, float confidence) {
        String key = label.toLowerCase().trim().replace(" ", "_").replace("___", "___");
        JSONObject remedy = remediesJson != null ? remediesJson.optJSONObject(key) : null;

        try {
            if (remedy != null) {
                String name = remedy.getString("name_hi");
                String treat = remedy.getString("treatment");
                tvResult.setText("✅ " + name + " (" + (int)(confidence * 100) + "%)\n\nइलाज: " + treat);
                tts.speak("पहचान की गई: " + name, TextToSpeech.QUEUE_FLUSH, null, null);
            } else {
                tvResult.setText("पहचान: " + label + "\n(इलाज नहीं मिला)");
            }
        } catch (Exception e) {
            tvResult.setText("Error: " + e.getMessage());
        }
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, true);
        ByteBuffer buffer = ByteBuffer.allocateDirect(4 * MODEL_INPUT_SIZE * MODEL_INPUT_SIZE * 3);
        buffer.order(ByteOrder.nativeOrder());
        int[] pixels = new int[MODEL_INPUT_SIZE * MODEL_INPUT_SIZE];
        scaled.getPixels(pixels, 0, scaled.getWidth(), 0, 0, scaled.getWidth(), scaled.getHeight());

        for (int p : pixels) {
            buffer.putFloat(((p >> 16) & 0xFF) / 255.0f);
            buffer.putFloat(((p >> 8) & 0xFF) / 255.0f);
            buffer.putFloat((p & 0xFF) / 255.0f);
        }
        return buffer;
    }

    private boolean isImageTooDark(Bitmap bitmap) {
        long sum = 0;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int p : pixels) sum += (((p >> 16) & 0xFF) + ((p >> 8) & 0xFF) + (p & 0xFF)) / 3;
        return (sum / (width * height)) < 30;
    }

    private boolean loadModelAndAssets() {
        try {
            tflite = new Interpreter(loadModelFile("model.tflite"));
            loadLabels("labels.txt");
            InputStream is = getAssets().open("remedies.json");
            byte[] b = new byte[is.available()];
            is.read(b);
            is.close();
            remediesJson = new JSONObject(new String(b, "UTF-8"));
            return true;
        } catch (Exception e) { return false; }
    }

    private MappedByteBuffer loadModelFile(String name) throws IOException {
        FileInputStream fis = new FileInputStream(getAssets().openFd(name).getFileDescriptor());
        FileChannel fc = fis.getChannel();
        return fc.map(FileChannel.MapMode.READ_ONLY, getAssets().openFd(name).getStartOffset(), getAssets().openFd(name).getDeclaredLength());
    }

    private void loadLabels(String name) throws IOException {
        labels.clear();
        java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(getAssets().open(name)));
        String line;
        while ((line = r.readLine()) != null) if (!line.trim().isEmpty()) labels.add(line.trim());
        r.close();
    }

    private void startCamera() {
        try {
            ProcessCameraProvider p = ProcessCameraProvider.getInstance(this).get();
            Preview pr = new Preview.Builder().build();
            pr.setSurfaceProvider(previewView.getSurfaceProvider());
            imageCapture = new ImageCapture.Builder().setTargetResolution(new Size(1080, 1080)).build();
            p.unbindAll();
            camera = p.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, pr, imageCapture);
            previewView.setVisibility(View.VISIBLE);
            imagePreview.setVisibility(View.GONE);
            btnCapture.setText("Capture");
        } catch (Exception e) {}
    }

    private void takePhoto() {
        if (imageCapture == null) return;
        imageCapture.takePicture(ContextCompat.getMainExecutor(this), new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull androidx.camera.core.ImageProxy image) {
                ByteBuffer b = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[b.remaining()];
                b.get(bytes);
                lastCapturedBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                image.close();
                runOnUiThread(() -> {
                    previewView.setVisibility(View.GONE);
                    imagePreview.setVisibility(View.VISIBLE);
                    imagePreview.setImageBitmap(lastCapturedBitmap);
                    btnCapture.setText("Recapture");
                    btnDetect.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 101 && resultCode == RESULT_OK && data != null) {
            try {
                InputStream is = getContentResolver().openInputStream(data.getData());
                lastCapturedBitmap = BitmapFactory.decodeStream(is);
                previewView.setVisibility(View.GONE);
                imagePreview.setVisibility(View.VISIBLE);
                imagePreview.setImageBitmap(lastCapturedBitmap);
                btnDetect.setVisibility(View.VISIBLE);
                resultCard.setVisibility(View.GONE);
            } catch (Exception e) {}
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}
