package com.pdf.peace_space;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.mediapipe.tasks.genai.llminference.LlmInference;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LlmHelper {

    private static final String TAG = "LlmHelper";
    private static LlmHelper instance;

    private LlmInference llmInference;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isInitializing = false;

    public interface LlmCallback {
        void onTokenGenerated(String partialResult, boolean isComplete);
        void onError(Throwable error);
    }

    private LlmHelper() {}

    public static synchronized LlmHelper getInstance() {
        if (instance == null) {
            instance = new LlmHelper();
        }
        return instance;
    }

    public boolean isModelLoaded() {
        return llmInference != null;
    }

    public void initializeModel(Context context, String modelPath, LlmCallback initCallback) {
        if (llmInference != null) {
            if (initCallback != null) {
                initCallback.onTokenGenerated("Model already initialized", true);
            }
            return;
        }

        if (isInitializing) {
            return;
        }

        isInitializing = true;

        executorService.execute(() -> {
            try {
                File modelFile = new File(modelPath);
                if (!modelFile.exists()) {
                    throw new IllegalArgumentException("Model file does not exist at: " + modelPath);
                }

                LlmInference.LlmInferenceOptions options = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelPath)
                        .setMaxTokens(512)
                        .setMaxTopK(40)
                        .build();

                llmInference = LlmInference.createFromOptions(context.getApplicationContext(), options);
                isInitializing = false;

                mainHandler.post(() -> {
                    if (initCallback != null) {
                        initCallback.onTokenGenerated("Model successfully loaded", true);
                    }
                });
            } catch (Exception e) {
                isInitializing = false;
                Log.e(TAG, "Error initializing LLM model", e);
                mainHandler.post(() -> {
                    if (initCallback != null) {
                        initCallback.onError(e);
                    }
                });
            }
        });
    }

    public void explainText(String selectedText, LlmCallback callback) {
        if (llmInference == null) {
            if (callback != null) {
                callback.onError(new IllegalStateException("LLM Model is not initialized. Please load Gemma model first."));
            }
            return;
        }

        String prompt = "You are a helpful dictionary and language assistant. Explain the meaning of the following text, phrase, or idiom concisely in clear, simple English. Provide a definition and an example sentence:\n\n\"" + selectedText.trim() + "\"";

        try {
            llmInference.generateResponseAsync(prompt, (partialResult, done) -> {
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onTokenGenerated(partialResult, done);
                    }
                });
            });
        } catch (Exception e) {
            Log.e(TAG, "Error generating response", e);
            if (callback != null) {
                callback.onError(e);
            }
        }
    }

    public void close() {
        if (llmInference != null) {
            try {
                llmInference.close();
                llmInference = null;
            } catch (Exception e) {
                Log.e(TAG, "Error closing LLM inference engine", e);
            }
        }
    }
}
