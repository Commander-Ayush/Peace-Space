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
    @Test public void reconnectingLocationPreservesVocabularyIdentity() {
        String identity = "content://book/old";
        store.recordDocumentNow(identity, "Book");
        store.recordWordNow(identity, "phrase", "Saved explanation");
        store.reconnectDocumentNow(identity, "content://book/new", "Book");
        assertEquals(identity, store.identityForAccessUriNow("content://book/new"));
        store.close(); store = new ReadingStore(RuntimeEnvironment.getApplication(), "test-reading.db");
        assertEquals("content://book/new", store.accessUriNow(identity));
        assertEquals("Saved explanation", store.wordsNow(identity).get(0).explanation);
    }
    @Test public void schemaUpgradeKeepsExistingDocumentsAndWords() {
        android.content.Context context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("migration.db");
        android.database.sqlite.SQLiteDatabase old = context.openOrCreateDatabase("migration.db", 0, null);
        old.execSQL("CREATE TABLE documents(uri TEXT PRIMARY KEY,title TEXT NOT NULL,opened INTEGER NOT NULL)");
        old.execSQL("CREATE TABLE words(document_uri TEXT NOT NULL,term_key TEXT NOT NULL,term TEXT NOT NULL,explanation TEXT,searched INTEGER NOT NULL,PRIMARY KEY(document_uri,term_key))");
        old.execSQL("INSERT INTO documents VALUES('old-uri','Book',1)");
        old.execSQL("INSERT INTO words VALUES('old-uri','word','Word','Meaning',1)");
        old.setVersion(1); old.close();
        try (ReadingStore migrated = new ReadingStore(context, "migration.db")) {
            assertEquals("old-uri", migrated.accessUriNow("old-uri"));
            assertEquals(1, migrated.documentsNow().size());
            assertEquals("Meaning", migrated.wordsNow("old-uri").get(0).explanation);
        }
    }
}
