package com.pdf.peace_space;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

/** Keeps a running explanation across rotation without restarting inference. */
public class ExplanationViewModel extends ViewModel {
    final MutableLiveData<String> result = new MutableLiveData<>("");
    final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    private boolean started;
    private final StringBuilder text = new StringBuilder();

    void start(String selected, String documentUri, String saved, ReadingStore store) {
        if (started) return;
        started = true;
        if (saved != null && !saved.isEmpty()) { result.setValue(saved); return; }
        if (selected == null || selected.trim().isEmpty()) { result.setValue("No text selected."); return; }
        store.recordWord(documentUri, selected, null);
        busy.setValue(true);
        result.setValue("Generating explanation…");
        LlmHelper.getInstance().explainText(selected, new LlmHelper.LlmCallback() {
            @Override public void onTokenGenerated(String partial, boolean complete) {
                if (partial != null) text.append(partial);
                result.setValue(text.toString());
                if (complete) {
                    busy.setValue(false);
                    store.recordWord(documentUri, selected, text.toString());
                }
            }
            @Override public void onError(Throwable error) {
                busy.setValue(false);
                result.setValue("Unable to explain this text. " + error.getLocalizedMessage());
            }
        });
    }
}
