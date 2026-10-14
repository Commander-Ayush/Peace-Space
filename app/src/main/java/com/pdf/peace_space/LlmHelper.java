package com.pdf.peace_space;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.mediapipe.tasks.genai.llminference.LlmInference;
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LlmHelper {

    private static final String TAG = "LlmHelper";
    private static LlmHelper instance;

    private volatile LlmInference llmInference;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean isInitializing = false;

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

    public interface Generation { void cancel(); }

    private static final class Request implements Generation {
        volatile boolean cancelled;
        LlmInferenceSession session;
        @Override public synchronized void cancel() {
            if (cancelled) return;
            cancelled = true;
            if (session != null) {
                try { session.cancelGenerateResponseAsync(); }
                catch (RuntimeException error) { Log.w(TAG, "Unable to cancel generation", error); }
            }
        }
    }

    public Generation explainText(String selectedText, LlmCallback callback) {
        Request request = new Request();
        executorService.execute(() -> {
            if (request.cancelled) return;
            try {
                if (llmInference == null) throw new IllegalStateException(
                        "The model is not ready yet. Wait for it to load and try again.");
                String prompt = "You are a helpful dictionary and language assistant. Explain the meaning of the following text, phrase, or idiom concisely in clear, simple English. Provide a definition and an example sentence:\n\n\"" + selectedText.trim() + "\"";
                com.google.common.util.concurrent.ListenableFuture<String> response;
                synchronized (request) {
                    if (request.cancelled) return;
                    request.session = LlmInferenceSession.createFromOptions(llmInference,
                            LlmInferenceSession.LlmInferenceSessionOptions.builder().setTopK(40).build());
                    request.session.addQueryChunk(prompt);
                    response = request.session.generateResponseAsync((partialResult, done) -> {
                        mainHandler.post(() -> {
                            if (!request.cancelled && callback != null) callback.onTokenGenerated(partialResult, done);
                        });
                    });
                }
                // Native cancellation finishes through the completion callback. Wait for that
                // before closing this session or starting another on the same inference engine.
                response.get();
            } catch (Exception error) {
                if (!request.cancelled) {
                    Log.e(TAG, "Error generating response", error);
                    mainHandler.post(() -> {
                        if (!request.cancelled && callback != null) callback.onError(error);
                    });
                }
            } finally {
                synchronized (request) {
                    if (request.session != null) {
                        try { request.session.close(); }
                        catch (RuntimeException error) { Log.w(TAG, "Unable to close generation session", error); }
                        request.session = null;
                    }
                }
            }
        });
        return request;
    }

    public void close() {
        executorService.execute(() -> {
            if (llmInference != null) {
                try { llmInference.close(); }
                catch (Exception error) { Log.e(TAG, "Error closing inference", error); }
                finally { llmInference = null; }
            }
        });
    }
}
