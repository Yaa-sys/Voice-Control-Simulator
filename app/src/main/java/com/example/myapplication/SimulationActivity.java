package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

public class SimulationActivity extends AppCompatActivity {

    private SimulationView simulationView;
    private TextView tvStatus;
    private SpeechRecognizer speechRecognizer;
    private Intent speechIntent;

    // 模式：0平面, 1斜面, 2會員(斜面+摩擦+自訂參數)
    private int mode = 0;

    // ===== 會員可調參數（預設值）=====
    // 一般斜面固定 45 度；會員模式用 seekbar 改
    private float massKg = 5f;          // m（一般模式預設）
    private float angleDeg = 45f;       // θ（一般斜面固定 45）
    private float mu = 0f;              // 摩擦係數（一般模式 0）

    // ===== 語音推力（用「力」表示，會除以質量變成加速度）=====
    private float pendingForce = 0f;      // 本 frame 要施加的力（喊一次給一次）
    private final float SHOUT_FORCE = 260f; // 喊一次「加速」給的力大小（可調大/小）

    // ===== 物理常數（用角度計算）=====
    private final float G = 9.81f;       // 真實重力
    private final float PIXEL_SCALE = 55f; // 把 m/s^2 轉成「畫面速度」的倍率（調這個最有感）
    private final float DT = 0.03f;      // 每次 updatePhysics 的時間（30ms）

    // UI（會員模式動態面板）
    private LinearLayout memberPanel;
    private TextView tvMass, tvAngle, tvMu;

    // 計時器 (Game Loop)
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable physicsRunnable = new Runnable() {
        @Override
        public void run() {
            updatePhysics();
            handler.postDelayed(this, 30);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simulation);

        simulationView = findViewById(R.id.simulationView);
        tvStatus = findViewById(R.id.tvStatus);

        mode = getIntent().getIntExtra("MODE", 0);

        // 模式初始化
        if (mode == 0) {
            // ===== 平面模式 =====
            angleDeg = 0f;
            mu = 0f;
            massKg = 5f;

            simulationView.setSlopeMode(false);          // 球不抬頭
            simulationView.setShowSlopeOverlay(false);   // ❌ 不畫斜坡
            simulationView.setBackgroundMode(false);     // ✅ 用「平面背景」

            tvStatus.setText("模式：平面 (無重力 / 無摩擦)");

        } else if (mode == 1) {
            // ===== 一般斜面 =====
            angleDeg = 45f;   // 固定 45 度
            mu = 0f;          // 無摩擦
            massKg = 5f;      // 預設質量

            simulationView.setSlopeMode(true);           // 球抬頭
            simulationView.setShowSlopeOverlay(true);    // ✅ 畫斜坡
            simulationView.setBackgroundMode(true);      // ✅ 用「斜面背景」

            tvStatus.setText("模式：斜面 (45°，有重力)");

        } else {
            // ===== 會員模式 =====
            angleDeg = 30f;   // 可調
            mu = 0.20f;       // 可調
            massKg = 5f;      // 可調

            simulationView.setSlopeMode(true);           // 球抬頭
            simulationView.setShowSlopeOverlay(true);    // ✅ 畫斜坡
            simulationView.setBackgroundMode(true);      // ✅ 用「斜面背景」

            tvStatus.setText("會員專屬：自訂 m / θ / μ");
            setupMemberPanel(); // 加入三條 SeekBar
        }


        initVoiceRecognition();
        handler.post(physicsRunnable);
    }

    // =============================
    // 會員面板：三條 SeekBar
    // =============================
    private void setupMemberPanel() {
        // 把面板加到畫面最下面（不改 XML）
        ViewGroup root = findViewById(android.R.id.content);
        memberPanel = new LinearLayout(this);
        memberPanel.setOrientation(LinearLayout.VERTICAL);
        memberPanel.setPadding(dp(16), dp(8), dp(16), dp(16));
        memberPanel.setBackgroundColor(0x33FFFFFF);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.gravity = Gravity.BOTTOM;
        memberPanel.setLayoutParams(lp);

        // --- 質量 m ---
        tvMass = new TextView(this);
        tvMass.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        memberPanel.addView(tvMass);

        SeekBar sbMass = new SeekBar(this);
        sbMass.setMax(190); // 0~190 => 1~20kg
        sbMass.setProgress((int)((massKg - 1f) * 10f)); // 0.1kg 精度
        memberPanel.addView(sbMass);

        // --- 角度 θ ---
        tvAngle = new TextView(this);
        tvAngle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        memberPanel.addView(tvAngle);

        SeekBar sbAngle = new SeekBar(this);
        sbAngle.setMax(600); // 0~600 => 0~60度（0.1度精度）
        sbAngle.setProgress((int)(angleDeg * 10f));
        memberPanel.addView(sbAngle);

        // --- 摩擦係數 μ ---
        tvMu = new TextView(this);
        tvMu.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        memberPanel.addView(tvMu);

        SeekBar sbMu = new SeekBar(this);
        sbMu.setMax(100); // 0~1.00
        sbMu.setProgress((int)(mu * 100f));
        memberPanel.addView(sbMu);

        // 先更新文字
        refreshMemberText();

        sbMass.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                massKg = 1f + (progress / 10f); // 1.0 ~ 20.0
                refreshMemberText();
            }
        });

        sbAngle.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                angleDeg = progress / 10f; // 0.0 ~ 60.0
                refreshMemberText();
            }
        });

        sbMu.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mu = progress / 100f; // 0.00 ~ 1.00
                refreshMemberText();
            }
        });

        root.addView(memberPanel);
    }

    private void refreshMemberText() {
        if (tvMass != null) tvMass.setText("質量 m = " + massKg + " kg");
        if (tvAngle != null) tvAngle.setText("坡度 θ = " + angleDeg + "°");
        if (tvMu != null) tvMu.setText("摩擦係數 μ = " + mu);
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()
        );
    }

    private static abstract class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    }

    // =============================
    // 核心物理（角度計算重力與摩擦）
    // =============================
    private void updatePhysics() {
        float speed = simulationView.getSpeed();

        // 角度/摩擦/質量：依模式決定
        float theta = 0f;
        float localMu = 0f;
        float m = 5f;

        if (mode == 0) {
            theta = 0f;
            localMu = 0f;
            m = 5f;
        } else if (mode == 1) {
            theta = 45f; // ✅ 一般斜面固定 45°
            localMu = 0f; // ✅ 一般斜面不加摩擦
            m = 5f;       // ✅ 一般斜面預設質量
        } else { // mode == 2
            theta = angleDeg;
            localMu = mu;
            m = Math.max(0.1f, massKg);
        }

        // 1) 語音喊一次：施加一瞬間「力」 → a = F/m → Δv = a*dt
        if (pendingForce > 0f) {
            float aPush = (pendingForce / m);
            speed += (aPush * DT) * PIXEL_SCALE;
            pendingForce = 0f;
        }

        // 2) 斜面重力分量（沿斜面向下）與摩擦（反向阻擋運動）
        if (speed > 0f && (mode == 1 || mode == 2)) {
            double rad = Math.toRadians(theta);

            // 重力沿斜面分量：g*sinθ
            float aG = (float) (G * Math.sin(rad));

            // 正向力：g*cosθ → 摩擦：μ*g*cosθ
            float aFric = (float) (localMu * G * Math.cos(rad));

            // 合成減速度（都會讓 speed 變小）
            float aDecel = aG + aFric;

            speed -= (aDecel * DT) * PIXEL_SCALE;
        }

        // 3) 不允許負值
        if (speed < 0f) speed = 0f;

        simulationView.setSpeed(speed);
    }

    // =============================
    // 語音識別（喊「加速」→ 給一次力）
    // =============================
    private void initVoiceRecognition() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-TW");
        speechIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null) {
                    for (String word : matches) {
                        if (word.contains("加速") || word.contains("跑") || word.contains("快")) {
                            // ✅ 喊一次 = 給一次力（會除以 m 變成加速度）
                            pendingForce = SHOUT_FORCE;
                            tvStatus.setText("偵測到指令：加速！（F=" + SHOUT_FORCE + "）");
                            break;
                        }
                    }
                }
                speechRecognizer.startListening(speechIntent);
            }

            @Override
            public void onError(int error) {
                speechRecognizer.startListening(speechIntent);
            }

            @Override public void onReadyForSpeech(Bundle params) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        speechRecognizer.startListening(speechIntent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) speechRecognizer.destroy();
        handler.removeCallbacks(physicsRunnable);
    }
}
