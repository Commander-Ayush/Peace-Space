package com.pdf.peace_space;

import android.content.Context;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ReadingStoreTest {
    private ReadingStore store;
    @Before public void setup() {
        Context context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("test-reading.db");
        store = new ReadingStore(context, "test-reading.db");
        store.setHistoryEnabled(true);
    }
    @After public void teardown() { store.close(); }

    @Test public void reopeningPreservesWordsAndSavedExplanation() {
        store.recordDocumentNow("content://book/1", "Book");
        store.recordWordNow("content://book/1", "break the ice", "Start a conversation.");
        store.recordDocumentNow("content://book/1", "Book renamed");
        store.recordWordNow("content://book/1", "Break the ice", null);
        assertEquals(1, store.wordsNow("content://book/1").size());
        assertEquals("Start a conversation.", store.wordsNow("content://book/1").get(0).explanation);
        assertEquals("Book renamed", store.documentsNow().get(0).title);
    }
    @Test public void documentWordsRemainSeparateAndPersistAfterReopen() {
        store.recordDocumentNow("content://book/1", "One");
        store.recordDocumentNow("content://book/2", "Two");
        store.recordWordNow("content://book/1", "apple", "Fruit");
        store.recordWordNow("content://book/2", "apple", "Company");
        store.close(); store = new ReadingStore(RuntimeEnvironment.getApplication(), "test-reading.db");
        assertEquals("Fruit", store.wordsNow("content://book/1").get(0).explanation);
        assertEquals("Company", store.wordsNow("content://book/2").get(0).explanation);
    }
    @Test public void capKeeps99RecentDocumentsWithoutErasingWordHistory() {
        store.recordDocumentNow("content://book/old", "Old");
        store.recordWordNow("content://book/old", "old term", "Old meaning");
        for (int i = 0; i < 99; i++) store.recordDocumentNow("content://book/" + i, "Book " + i);
        assertEquals(99, store.documentsNow().size());
        assertEquals("content://book/98", store.documentsNow().get(0).uri);
        assertEquals(1, store.wordsNow("content://book/old").size());
        store.recordWordNow("content://book/old", "late result", "History stays independent of recents");
        assertEquals(99, store.documentsNow().size());
        assertEquals(2, store.wordsNow("content://book/old").size());
        store.recordDocumentNow("content://book/0", "Book 0");
        assertEquals("content://book/0", store.documentsNow().get(0).uri);
    }
    @Test public void disablingRecordingKeepsExistingHistoryAndBlocksNewWrites() {
        store.recordDocumentNow("content://book/1", "Book");
        store.recordWordNow("content://book/1", "first", "Saved");
        store.setHistoryEnabled(false);
        store.recordWordNow("content://book/1", "second", "Not saved");
        assertEquals(1, store.wordsNow("content://book/1").size());
        store.setHistoryEnabled(true);
        store.recordWordNow("content://book/1", "second", "Saved now");
        assertEquals(2, store.wordsNow("content://book/1").size());
    }
}
