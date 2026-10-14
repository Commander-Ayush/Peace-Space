package com.pdf.peace_space;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** App-private SQLite storage. All operations run in order off the UI thread. */
public final class ReadingStore extends SQLiteOpenHelper {
    private static ReadingStore instance;
    private final Context appContext;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public static synchronized ReadingStore get(Context context) {
        if (instance == null) instance = new ReadingStore(context.getApplicationContext());
        return instance;
    }

    ReadingStore(Context context, String name) {
        super(context, name, null, 2);
        appContext = context.getApplicationContext();
    }
    private ReadingStore(Context context) { this(context, "reading.db"); }

    @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE documents (uri TEXT PRIMARY KEY, title TEXT NOT NULL, opened INTEGER NOT NULL, access_uri TEXT)");
        db.execSQL("CREATE TABLE words (document_uri TEXT NOT NULL, "
                + "term_key TEXT NOT NULL, term TEXT NOT NULL, explanation TEXT, searched INTEGER NOT NULL, "
                + "PRIMARY KEY(document_uri, term_key))");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE documents ADD COLUMN access_uri TEXT");
    }

    public boolean historyEnabled() {
        return appContext.getSharedPreferences("reading", Context.MODE_PRIVATE).getBoolean("history", true);
    }
    public void setHistoryEnabled(boolean enabled) {
        appContext.getSharedPreferences("reading", Context.MODE_PRIVATE).edit().putBoolean("history", enabled).apply();
    }

    void recordDocumentNow(String uri, String title) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            // UPDATE keeps each document unique and promotes it even within the same millisecond.
            ContentValues values = new ContentValues();
            values.put("title", title);
            long opened = System.currentTimeMillis();
            try (Cursor latest = db.rawQuery("SELECT MAX(opened) FROM documents", null)) {
                if (latest.moveToFirst() && !latest.isNull(0)) opened = Math.max(opened, latest.getLong(0) + 1);
            }
            values.put("opened", opened);
            if (db.update("documents", values, "uri=?", new String[]{uri}) == 0) {
                values.put("uri", uri); db.insertOrThrow("documents", null, values);
            }
            db.execSQL("DELETE FROM documents WHERE uri NOT IN "
                    + "(SELECT uri FROM documents ORDER BY opened DESC, rowid DESC LIMIT 99)");
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public void recordDocument(String uri, String title) { write(() -> recordDocumentNow(uri, title)); }

    // A relocated/reselected PDF keeps its original vocabulary identity.
    public void reconnectDocument(String identity, String accessUri, String title) {
        write(() -> reconnectDocumentNow(identity, accessUri, title));
    }
    void reconnectDocumentNow(String identity, String accessUri, String title) {
        recordDocumentNow(identity, title);
        ContentValues values = new ContentValues(); values.put("access_uri", accessUri);
        getWritableDatabase().update("documents", values, "uri=?", new String[]{identity});
    }
    String identityForAccessUriNow(String accessUri) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT uri FROM documents WHERE uri=? OR access_uri=? ORDER BY opened DESC LIMIT 1",
                new String[]{accessUri, accessUri})) {
            return c.moveToFirst() ? c.getString(0) : accessUri;
        }
    }
    public void identityForAccessUri(String accessUri, Consumer<String> callback) {
        worker.execute(() -> {
            String identity;
            try { identity = identityForAccessUriNow(accessUri); } catch (RuntimeException error) { identity = accessUri; }
            String result = identity; main.post(() -> callback.accept(result));
        });
    }
    String accessUriNow(String identity) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT access_uri FROM documents WHERE uri=?", new String[]{identity})) {
            return c.moveToFirst() && !c.isNull(0) ? c.getString(0) : identity;
        }
    }
    public void accessUri(String identity, Consumer<String> callback) {
        worker.execute(() -> {
            String uri;
            try { uri = accessUriNow(identity); } catch (RuntimeException error) { uri = identity; }
            String result = uri; main.post(() -> callback.accept(result));
        });
    }

    void recordWordNow(String uri, String term, String explanation) {
        if (!historyEnabled() || uri == null || term.trim().isEmpty()) return;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("term", term.trim()); values.put("searched", System.currentTimeMillis());
        if (explanation != null) values.put("explanation", explanation);
        String key = term.trim().toLowerCase(Locale.ROOT);
        if (db.update("words", values, "document_uri=? AND term_key=?", new String[]{uri, key}) == 0) {
            values.put("document_uri", uri); values.put("term_key", key);
            db.insertOrThrow("words", null, values);
        }
    }
    public void recordWord(String uri, String term, String explanation) {
        if (historyEnabled()) write(() -> recordWordNow(uri, term, explanation));
    }
    private void write(Runnable operation) {
        worker.execute(() -> {
            try { operation.run(); }
            catch (RuntimeException error) { Log.e("ReadingStore", "Unable to save reading history", error); }
        });
    }

    public static final class Document {
        public final String uri, title, accessUri;
        public Document(String uri, String title) { this(uri, title, uri); }
        public Document(String uri, String title, String accessUri) { this.uri = uri; this.title = title; this.accessUri = accessUri; }
    }
    public static final class Word {
        public final String term, explanation;
        public Word(String term, String explanation) { this.term = term; this.explanation = explanation; }
    }
    List<Document> documentsNow() {
        List<Document> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT uri,title,access_uri FROM documents ORDER BY opened DESC,rowid DESC LIMIT 99", null)) {
            while (c.moveToNext()) result.add(new Document(c.getString(0), c.getString(1), c.isNull(2) ? c.getString(0) : c.getString(2)));
        }
        return result;
    }
    List<Word> wordsNow(String uri) {
        List<Word> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT term,explanation FROM words WHERE document_uri=? ORDER BY searched DESC,rowid DESC", new String[]{uri})) {
            while (c.moveToNext()) result.add(new Word(c.getString(0), c.getString(1)));
        }
        return result;
    }
    public void documents(Consumer<List<Document>> callback) {
        worker.execute(() -> {
            List<Document> result;
            try { result = documentsNow(); } catch (RuntimeException e) { Log.e("ReadingStore", "Read failed", e); result = new ArrayList<>(); }
            List<Document> items = result; main.post(() -> callback.accept(items));
        });
    }
    public void words(String uri, Consumer<List<Word>> callback) {
        worker.execute(() -> {
            List<Word> result;
            try { result = wordsNow(uri); } catch (RuntimeException e) { Log.e("ReadingStore", "Read failed", e); result = new ArrayList<>(); }
            List<Word> items = result; main.post(() -> callback.accept(items));
        });
    }
}
