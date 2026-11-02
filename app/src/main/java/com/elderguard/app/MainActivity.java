package com.elderguard.app;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class MainActivity extends AppCompatActivity {

    private EditText etMessageInput;
    private Button btnAnalyze, btnLockTransfer;
    private CardView cardResult;
    private LinearLayout layoutCardBackground;
    private TextView tvRiskLevel, tvFraudScore, tvDetectedIndicators, tvAdvice;
    private ElderGuardClassifier classifier;
    private boolean isModelReady = false;

    // Phishing and spoofing detection heuristics
    private static final String[] SHORTENERS = {"bit.ly", "tinyurl.com", "t.co", "is.gd", "rb.gy", "shorturl.at"};
    private static final String[] SUSPICIOUS_TLDS = {".xyz", ".top", ".club", ".click", ".buzz", ".work", ".link", ".icu"};
    private static final String[] GENUINE_DOMAINS = {"onlinesbi.sbi", "sbi.co.in", "hdfcbank.com", "icicibank.com", "paytm.com", "rbi.org.in"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etMessageInput = findViewById(R.id.etMessageInput);
        btnAnalyze = findViewById(R.id.btnAnalyze);
        btnLockTransfer = findViewById(R.id.btnLockTransfer);
        cardResult = findViewById(R.id.cardResult);
        layoutCardBackground = findViewById(R.id.layoutCardBackground);
        tvRiskLevel = findViewById(R.id.tvRiskLevel);
        tvFraudScore = findViewById(R.id.tvFraudScore);
        tvDetectedIndicators = findViewById(R.id.tvDetectedIndicators);
        tvAdvice = findViewById(R.id.tvAdvice);

        btnAnalyze.setEnabled(false);
        btnAnalyze.setText("⏳ INITIALIZING AI SHIELD...");

        // Load the on-device AI model in the background
        new Thread(() -> {
            try {
                classifier = new ElderGuardClassifier(MainActivity.this);
                isModelReady = true;
                runOnUiThread(() -> {
                    btnAnalyze.setEnabled(true);
                    btnAnalyze.setText("🔍 SCAN MESSAGE");
                    Toast.makeText(MainActivity.this, "🛡️ ElderGuard Shield Active", Toast.LENGTH_SHORT).show();
                });
            } catch (Throwable t) {
                runOnUiThread(() -> {
                    btnAnalyze.setText("INITIALIZATION FAILED");
                    Toast.makeText(MainActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();

        btnAnalyze.setOnClickListener(v -> analyzeMessage());
        btnLockTransfer.setOnClickListener(v -> startCoolOffTimer());
    }

    private void analyzeMessage() {
        if (!isModelReady || classifier == null) {
            Toast.makeText(this, "AI model is still preparing, please wait...", Toast.LENGTH_SHORT).show();
            return;
        }

        String rawMessage = etMessageInput.getText().toString().trim();
        if (rawMessage.isEmpty()) {
            Toast.makeText(this, "Please enter a message to scan", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            float fraudProb = classifier.predictFraudProbability(rawMessage) * 100;
            String lower = rawMessage.toLowerCase();

            StringBuilder indicators = new StringBuilder();
            int indicatorBonus = 0;

            // 1. Digital Arrest & Authority Impersonation
            if (lower.contains("digital arrest") || lower.contains("under arrest")) {
                indicators.append("Digital Arrest Threat, ");
                indicatorBonus += 30;
            }
            if (lower.contains("cbi") || lower.contains("police") || lower.contains("customs") || lower.contains("rbi")) {
                indicators.append("Authority Impersonation, ");
                indicatorBonus += 25;
            }

            // 2. Remote Access Traps
            if (lower.contains("anydesk") || lower.contains("teamviewer") || lower.contains("remote")) {
                indicators.append("Remote Access App, ");
                indicatorBonus += 30;
            }

            // 3. Credential Harvesting
            if (lower.contains("otp") || lower.contains("pin") || lower.contains("password")) {
                indicators.append("Credential Theft Trap, ");
                indicatorBonus += 30;
            }

            // 4. Award, Lottery, Cashback & Prize Baits
            boolean hasRewardTerms = lower.contains("won") || lower.contains("award") ||
                    lower.contains("cashback") || lower.contains("redeem") ||
                    lower.contains("prize") || lower.contains("lottery") ||
                    lower.contains("claim") || lower.contains("voucher");
            if (hasRewardTerms) {
                indicators.append("Fake Reward / Cashback Bait, ");
                indicatorBonus += 35;
            }

            // 5. URL & Phishing Link Detection
            boolean hasLink = lower.contains("http://") || lower.contains("https://") ||
                    lower.contains("www.") || lower.contains(".xyz") || lower.contains("bit.ly");

            for (String shortener : SHORTENERS) {
                if (lower.contains(shortener)) {
                    indicators.append("Masked Short URL (" + shortener + "), ");
                    indicatorBonus += 40;
                    break;
                }
            }

            for (String tld : SUSPICIOUS_TLDS) {
                if (lower.contains(tld)) {
                    indicators.append("Suspicious Phishing Domain (" + tld + "), ");
                    indicatorBonus += 40;
                    break;
                }
            }

            boolean mentionsBrand = lower.contains("sbi") || lower.contains("paytm") ||
                    lower.contains("hdfc") || lower.contains("icici");
            if (mentionsBrand && hasLink) {
                boolean isOfficial = false;
                for (String official : GENUINE_DOMAINS) {
                    if (lower.contains(official)) {
                        isOfficial = true;
                        break;
                    }
                }
                if (!isOfficial) {
                    indicators.append("Fake / Spoofed Brand Link, ");
                    indicatorBonus += 40;
                }
            }

            // 6. Urgency Pressure
            if (lower.contains("immediate") || lower.contains("urgent") ||
                    lower.contains("blocked") || lower.contains("freeze")) {
                indicators.append("Urgency Pressure, ");
                indicatorBonus += 15;
            }

            // Final Composite Risk Score (0-100)
            float finalScore = Math.min(100f, (fraudProb * 0.4f) + indicatorBonus);

            cardResult.setVisibility(View.VISIBLE);
            tvFraudScore.setText(String.format("AI Probability: %.1f%%  |  ElderGuard Risk: %.0f/100", fraudProb, finalScore));

            if (finalScore >= 70) {
                layoutCardBackground.setBackgroundColor(Color.parseColor("#FFEBEE"));
                tvRiskLevel.setText("⚠️ HIGH RISK FRAUD DETECTED");
                tvRiskLevel.setTextColor(Color.parseColor("#C62828"));

                if (hasLink) {
                    tvAdvice.setText("🛑 DO NOT TAP THE LINK!\n" +
                            "• Banks never ask for a PIN to receive cashback.\n" +
                            "• Do not enter bank details or scan any QR code.");
                } else {
                    tvAdvice.setText("🛑 DO NOT TRANSFER ANY MONEY!\n" +
                            "• Do not share OTP or install remote access apps.\n" +
                            "• Contact your family or visit your bank branch.");
                }
                btnLockTransfer.setVisibility(View.VISIBLE);

            } else if (finalScore >= 40) {
                layoutCardBackground.setBackgroundColor(Color.parseColor("#FFF3E0"));
                tvRiskLevel.setText("⚠️ MEDIUM RISK — CAUTION");
                tvRiskLevel.setTextColor(Color.parseColor("#E65100"));
                tvAdvice.setText("Exercise caution. Verify directly with family before proceeding.");
                btnLockTransfer.setVisibility(View.GONE);

            } else {
                layoutCardBackground.setBackgroundColor(Color.parseColor("#E8F5E9"));
                tvRiskLevel.setText("✅ SAFE MESSAGE");
                tvRiskLevel.setTextColor(Color.parseColor("#2E7D32"));
                tvAdvice.setText("No immediate scam indicators detected.");
                btnLockTransfer.setVisibility(View.GONE);
            }

            if (indicators.length() > 0) {
                tvDetectedIndicators.setText("Red Flags: " + indicators.substring(0, indicators.length() - 2));
            } else {
                tvDetectedIndicators.setText("Red Flags: None detected");
            }

        } catch (Exception e) {
            Toast.makeText(this, "Analysis error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void startCoolOffTimer() {
        btnLockTransfer.setEnabled(false);

        // Build and display the formal emergency time-lock alert dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🛡️ ElderGuard Security Time-Lock");
        builder.setMessage("A 15-minute cooling-off delay has been activated on outgoing transactions.\n\n" +
                "Time Remaining: 15:00\n\n" +
                "Please call your family or cyber helpline to verify before sending any money.");
        builder.setCancelable(false);

        // Emergency call button
        builder.setPositiveButton("📞 Call Cyber Helpline (1930)", (dialog, which) -> {
            Intent callIntent = new Intent(Intent.ACTION_DIAL);
            callIntent.setData(Uri.parse("tel:1930"));
            startActivity(callIntent);
        });

        // Cancel transfer button
        builder.setNegativeButton("Cancel Transfer", (dialog, which) -> {
            dialog.dismiss();
            Toast.makeText(MainActivity.this, "Transfer cancelled safely.", Toast.LENGTH_SHORT).show();
        });

        AlertDialog dialog = builder.create();
        dialog.show();

        // 15-minute countdown timer
        new CountDownTimer(15 * 60 * 1000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long minutes = millisUntilFinished / 1000 / 60;
                long seconds = (millisUntilFinished / 1000) % 60;

                btnLockTransfer.setText(String.format("🔒 LOCKED: %02d:%02d REMAINING", minutes, seconds));

                if (dialog.isShowing()) {
                    dialog.setMessage(String.format("A 15-minute cooling-off delay has been activated on outgoing transactions.\n\n" +
                            "Time Remaining: %02d:%02d\n\n" +
                            "Please call your family or cyber helpline to verify before sending any money.", minutes, seconds));
                }
            }

            @Override
            public void onFinish() {
                btnLockTransfer.setText("LOCK EXPIRED — PROCEED WITH CAUTION");
                btnLockTransfer.setEnabled(true);
                if (dialog.isShowing()) {
                    dialog.dismiss();
                }
            }
        }.start();
    }
}