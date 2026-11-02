package com.elderguard.app;

import android.content.Context;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

import java.io.*;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ElderGuardClassifier {

    private final OrtEnvironment env;
    private final OrtSession session;
    private final Map<String, Integer> vocab = new HashMap<>();
    private static final int MAX_LENGTH = 128;

    public ElderGuardClassifier(Context context) throws Exception {
        env = OrtEnvironment.getEnvironment();

        // 1. Read vocab.txt line-by-line (only ~200 KB)
        InputStream vocabStream = context.getAssets().open("vocab.txt");
        BufferedReader reader = new BufferedReader(new InputStreamReader(vocabStream, "UTF-8"));
        String line;
        int index = 0;
        while ((line = reader.readLine()) != null) {
            vocab.put(line.trim(), index++);
        }
        reader.close();

        // 2. Stream model from assets to internal storage without using Java RAM
        File modelFile = new File(context.getFilesDir(), "elderguard_model.onnx");
        if (!modelFile.exists() || modelFile.length() < 1000000) {
            try (InputStream in = context.getAssets().open("elderguard_model.onnx");
                 OutputStream out = new FileOutputStream(modelFile)) {
                byte[] buffer = new byte[8192]; // Small 8KB chunks
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
            }
        }

        // 3. Memory-map the model directly from disk (uses ~0 MB Java Heap)
        session = env.createSession(modelFile.getAbsolutePath(), new OrtSession.SessionOptions());
    }

    public float predictFraudProbability(String text) throws Exception {
        List<Long> inputIdsList = new ArrayList<>();
        List<Long> attentionMaskList = new ArrayList<>();

        int clsId = vocab.getOrDefault("[CLS]", 101);
        int sepId = vocab.getOrDefault("[SEP]", 102);
        int padId = vocab.getOrDefault("[PAD]", 0);
        int unkId = vocab.getOrDefault("[UNK]", 100);

        inputIdsList.add((long) clsId);
        attentionMaskList.add(1L);

        String[] words = text.toLowerCase().split("\\s+");
        for (String word : words) {
            if (inputIdsList.size() >= MAX_LENGTH - 1) break;
            int tokenId = vocab.getOrDefault(word, unkId);
            inputIdsList.add((long) tokenId);
            attentionMaskList.add(1L);
        }

        inputIdsList.add((long) sepId);
        attentionMaskList.add(1L);

        while (inputIdsList.size() < MAX_LENGTH) {
            inputIdsList.add((long) padId);
            attentionMaskList.add(0L);
        }

        long[] inputIds = new long[MAX_LENGTH];
        long[] attentionMask = new long[MAX_LENGTH];
        for (int i = 0; i < MAX_LENGTH; i++) {
            inputIds[i] = inputIdsList.get(i);
            attentionMask[i] = attentionMaskList.get(i);
        }

        long[] shape = new long[]{1, MAX_LENGTH};
        OnnxTensor tensorInputIds = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), shape);
        OnnxTensor tensorAttention = OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMask), shape);

        Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put("input_ids", tensorInputIds);
        inputs.put("attention_mask", tensorAttention);

        try (OrtSession.Result result = session.run(inputs)) {
            float[][] logits = (float[][]) result.get(0).getValue();
            float safeLogit = logits[0][0];
            float fraudLogit = logits[0][1];

            float maxLogit = Math.max(safeLogit, fraudLogit);
            float expSafe = (float) Math.exp(safeLogit - maxLogit);
            float expFraud = (float) Math.exp(fraudLogit - maxLogit);
            return expFraud / (expSafe + expFraud);
        }
    }
}