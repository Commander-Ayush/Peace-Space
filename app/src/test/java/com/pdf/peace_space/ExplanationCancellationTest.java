package com.pdf.peace_space;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ExplanationCancellationTest {
    static class Model extends ExplanationViewModel {
        LlmHelper.LlmCallback callback;
        boolean stopped;
        int requests;
        @Override LlmHelper.Generation generate(String selected, LlmHelper.LlmCallback callback) {
            this.callback = callback; requests++;
            return () -> stopped = true;
        }
    }
    @Test public void cancelledExplanationIgnoresLateResultsAndNextSelectionCompletes() {
        ReadingStore store = ReadingStore.get(RuntimeEnvironment.getApplication());
        Model first = new Model();
        first.start("first", null, null, store);
        first.cancel();
        assertTrue(first.stopped);
        first.callback.onTokenGenerated("stale answer", true);
        first.callback.onError(new IllegalStateException("late error"));
        assertEquals("Generating explanation…", first.result.getValue());
        assertFalse(first.busy.getValue());
        Model next = new Model();
        next.start("second", null, null, store);
        next.callback.onTokenGenerated("New definition", true);
        assertEquals("New definition", next.result.getValue());
        assertFalse(next.busy.getValue());
    }
    @Test public void retainedModelDoesNotRestartButRemovalCancelsGeneration() {
        Model model = new Model();
        ReadingStore store = ReadingStore.get(RuntimeEnvironment.getApplication());
        model.start("word", null, null, store);
        model.start("word", null, null, store);
        assertEquals(1, model.requests);
        assertFalse(model.stopped);
        model.onCleared();
        assertTrue(model.stopped);
    }
}
