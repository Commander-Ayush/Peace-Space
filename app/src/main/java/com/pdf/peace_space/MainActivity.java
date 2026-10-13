package com.pdf.peace_space;

import android.content.Intent;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import androidx.activity.OnBackPressedCallback;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ext.SdkExtensions;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresExtension;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.pdf.viewer.fragment.PdfViewerFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.appcompat.widget.AppCompatImageButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.File;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String MIME_TYPE_PDF = "application/pdf";
    private static final String PDF_VIEWER_FRAGMENT_TAG = "pdf_viewer_fragment_tag";
    public static final String DEFAULT_MODEL_URL = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task";

    private PdfViewerFragment pdfViewerFragment;
    private AppCompatImageButton getContentButton;
    private AppCompatImageButton searchButton;
    private ReadingStore readingStore;
    private String currentDocumentUri;
    private boolean showingDocument;
    private int homeLoadId;

    private final ActivityResultLauncher<String[]> filePicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    openDocument(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        readingStore = ReadingStore.get(this);
        if (savedInstanceState != null) {
            currentDocumentUri = savedInstanceState.getString("current_document_uri");
            showingDocument = savedInstanceState.getBoolean("showing_document");
        }

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.pdf_container_view), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        if (isSdkExtensionSupported()) {
            pdfViewerFragment = (PdfViewerFragment) getSupportFragmentManager()
                    .findFragmentByTag(PDF_VIEWER_FRAGMENT_TAG);
            attachPdfEventListener();
        }

        getContentButton = findViewById(R.id.launch_button);
        searchButton = findViewById(R.id.search_button);

        getContentButton.setOnClickListener(v -> filePicker.launch(new String[]{MIME_TYPE_PDF}));

        searchButton.setOnClickListener(v -> {
            if (pdfViewerFragment != null) {
                boolean isSearchActive = pdfViewerFragment.isTextSearchActive();
                pdfViewerFragment.setTextSearchActive(!isSearchActive);
            }
        });

        findViewById(R.id.history_button).setOnClickListener(v -> showWordHistory());
        findViewById(R.id.home_button).setOnClickListener(v -> showHome());
        for (int id : new int[]{R.id.home_button, R.id.launch_button, R.id.search_button, R.id.history_button}) {
            View control = findViewById(id);
            androidx.appcompat.widget.TooltipCompat.setTooltipText(control, control.getContentDescription());
        }
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (showingDocument) showHome();
                else { setEnabled(false); getOnBackPressedDispatcher().onBackPressed(); }
            }
        });
        updatePageVisibility();
        refreshRecentDocuments();
        if (savedInstanceState == null) handleIncomingIntent(getIntent());
        checkAndInitializeLlmModel();
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent != null && (Intent.ACTION_VIEW.equals(intent.getAction()) || Intent.ACTION_SEND.equals(intent.getAction()))) {
            Uri pdfUri = intent.getData();
            if (pdfUri == null && intent.hasExtra(Intent.EXTRA_STREAM)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pdfUri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
                } else {
                    pdfUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                }
            }
            Log.d(TAG, "Incoming intent URI: " + pdfUri);
            if (pdfUri != null && isSdkExtensionSupported()) {
                openDocument(pdfUri);
            }
        }
    }

    private void checkAndInitializeLlmModel() {
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File internalDir = getFilesDir();
        File externalDir = getExternalFilesDir(null);
        File adbTmpDir = new File("/data/local/tmp/llm/");

        File[] candidateLocations = new File[]{
                ModelDownloader.getTargetModelFile(this),
                new File(adbTmpDir, "model_version.task"),
                new File(adbTmpDir, "gemma3-1b-it-int4.task"),
                new File(adbTmpDir, "gemma-3-1b-it.task"),
                new File(downloadsDir, "gemma3-1b-it-int4.task"),
                new File(downloadsDir, "gemma-3-1b-it-gpu-int4.bin"),
                new File(downloadsDir, "gemma-2b-it-cpu-int4.bin"),
                new File(downloadsDir, "gemma.bin"),
                new File(internalDir, "gemma3-1b-it-int4.task"),
                new File(internalDir, "gemma-3-1b-it-gpu-int4.bin"),
                new File(externalDir, "gemma3-1b-it-int4.task")
        };

        File foundModel = null;
        for (File candidate : candidateLocations) {
            if (candidate.exists()) {
                foundModel = candidate;
                break;
            }
        }

        if (foundModel != null) {
            Log.d(TAG, "Found LLM model at: " + foundModel.getAbsolutePath());
            LlmHelper.getInstance().initializeModel(this, foundModel.getAbsolutePath(), new LlmHelper.LlmCallback() {
                @Override
                public void onTokenGenerated(String partialResult, boolean isComplete) {
                    Log.d(TAG, "LLM Model initialized: " + partialResult);
                }

                @Override
                public void onError(Throwable error) {
                    Log.e(TAG, "Failed to initialize LLM Model", error);
                }
            });
        } else {
            Log.w(TAG, "No Gemma model file found. Launching auto-downloader...");
            startModelDownload(DEFAULT_MODEL_URL);
        }
    }

    public void startModelDownload(String modelUrl) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_model_download, null);
        LinearProgressIndicator progressBar = dialogView.findViewById(R.id.download_progress_bar);
        TextView statusText = dialogView.findViewById(R.id.tv_download_status);

        MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setCancelable(false);

        AlertDialog dialog = dialogBuilder.create();
        dialog.show();

        ModelDownloader downloader = new ModelDownloader(this);
        downloader.downloadModel(modelUrl, new ModelDownloader.DownloadCallback() {
            @Override
            public void onProgress(int progressPercent, long downloadedBytes, long totalBytes) {
                progressBar.setIndeterminate(false);
                progressBar.setProgress(progressPercent);
                long downloadedMb = downloadedBytes / (1024 * 1024);
                long totalMb = totalBytes / (1024 * 1024);
                statusText.setText(String.format(Locale.getDefault(), "Downloaded %d MB / %d MB (%d%%)", downloadedMb, totalMb, progressPercent));
            }

            @Override
            public void onCompleted(File modelFile) {
                dialog.dismiss();
                Toast.makeText(MainActivity.this, "Model download complete!", Toast.LENGTH_LONG).show();
                checkAndInitializeLlmModel();
            }

            @Override
            public void onError(Exception e) {
                dialog.dismiss();
                Toast.makeText(MainActivity.this, "Model download failed: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private boolean isSdkExtensionSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13;
    }

    private void initializePdfViewerFragment() {
        if (pdfViewerFragment == null && isSdkExtensionSupported()) {
            FragmentManager fragmentManager = getSupportFragmentManager();

            PdfViewerFragmentExtended extendedFragment = new PdfViewerFragmentExtended();
            pdfViewerFragment = extendedFragment;
            attachPdfEventListener();

            FragmentTransaction transaction = fragmentManager.beginTransaction();

            transaction.replace(
                    R.id.fragment_container_view,
                    pdfViewerFragment,
                    PDF_VIEWER_FRAGMENT_TAG
            );
            transaction.commitAllowingStateLoss();
            fragmentManager.executePendingTransactions();
        }
    }

    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
    private void attachPdfEventListener() {
        if (pdfViewerFragment instanceof PdfViewerFragmentExtended) {
            ((PdfViewerFragmentExtended) pdfViewerFragment).setOnPdfEventListener(new PdfViewerFragmentExtended.OnPdfEventListener() {
                @Override
                public void onDocumentLoaded() {
                    updatePageVisibility();
                }

                @Override
                public void onImmersiveModeRequested(boolean enterImmersive) {
                    // Corner tools stay reachable while reading.
                }
            });
        }
    }

    public String getCurrentDocumentUri() { return currentDocumentUri; }

    @Override protected void onSaveInstanceState(@NonNull Bundle state) {
        state.putString("current_document_uri", currentDocumentUri);
        state.putBoolean("showing_document", showingDocument);
        super.onSaveInstanceState(state);
    }

    private void openDocument(Uri uri) {
        if (!isSdkExtensionSupported()) {
            Toast.makeText(this, "This device does not support the PDF viewer.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            // Confirm access before hiding the home screen. Some share URIs expire.
            try (android.content.res.AssetFileDescriptor file = getContentResolver().openAssetFileDescriptor(uri, "r")) {
                if (file == null) throw new java.io.IOException("Document unavailable");
            }
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch (SecurityException ignored) { /* Temporary external grants may not persist. */ }
            initializePdfViewerFragment();
            currentDocumentUri = uri.toString();
            showingDocument = true;
            pdfViewerFragment.setDocumentUri(uri);
            String title = uri.getLastPathSegment();
            try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) title = cursor.getString(0);
            } catch (RuntimeException ignored) { }
            readingStore.recordDocument(currentDocumentUri, title == null ? "PDF document" : title);
            updatePageVisibility();
        } catch (Exception error) {
            Toast.makeText(this, "Cannot reopen this PDF. Use Open to select it again.", Toast.LENGTH_LONG).show();
        }
    }

    private void showHome() {
        showingDocument = false;
        updatePageVisibility();
        refreshRecentDocuments();
    }
    private void updatePageVisibility() {
        findViewById(R.id.home_content).setVisibility(showingDocument ? View.GONE : View.VISIBLE);
        findViewById(R.id.fragment_container_view).setVisibility(showingDocument ? View.VISIBLE : View.GONE);
        if (searchButton != null) { searchButton.setEnabled(showingDocument); searchButton.setAlpha(showingDocument ? 1f : 0.4f); }
        findViewById(R.id.home_button).setVisibility(showingDocument ? View.VISIBLE : View.GONE);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private MaterialCardView listCard(String title, String subtitle) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(18));
        card.setCardElevation(0);
        card.setStrokeWidth(dp(1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(12); card.setLayoutParams(params);
        LinearLayout contents = new LinearLayout(this);
        contents.setOrientation(LinearLayout.VERTICAL); contents.setPadding(dp(18), dp(16), dp(18), dp(16));
        TextView heading = new TextView(this); heading.setText(title);
        heading.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
        contents.addView(heading);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView detail = new TextView(this); detail.setText(subtitle); detail.setMaxLines(2);
            detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
            detail.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
            detail.setPadding(0, dp(6), 0, 0); contents.addView(detail);
        }
        card.addView(contents); card.setClickable(true); card.setFocusable(true);
        return card;
    }
    private void refreshRecentDocuments() {
        final int request = ++homeLoadId;
        readingStore.documents(documents -> {
            if (isFinishing() || isDestroyed() || request != homeLoadId) return;
            LinearLayout rows = findViewById(R.id.recent_documents); rows.removeAllViews();
            findViewById(R.id.recent_empty).setVisibility(documents.isEmpty() ? View.VISIBLE : View.GONE);
            for (ReadingStore.Document document : documents) {
                MaterialCardView card = listCard(document.title, document.uri);
                card.setOnClickListener(v -> openDocument(Uri.parse(document.uri))); rows.addView(card);
            }
        });
    }
    private void showWordHistory() {
        final String documentUri = showingDocument ? currentDocumentUri : null;
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(12), dp(20), 0);
        MaterialSwitch recording = new MaterialSwitch(this);
        recording.setText("Save word history"); recording.setChecked(readingStore.historyEnabled());
        recording.setOnCheckedChangeListener((button, checked) -> readingStore.setHistoryEnabled(checked));
        content.addView(recording);
        TextView description = new TextView(this);
        description.setText(documentUri == null ? "Open a PDF to see its saved words and idioms. Turning this off stops new entries; saved words stay available."
                : "Words and idioms from this PDF. Turning recording off keeps existing entries.");
        description.setPadding(0, dp(8), 0, dp(16)); content.addView(description);
        ScrollView scroll = new ScrollView(this); LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL); scroll.addView(rows);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, dp(280)));
        AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle("Word history").setView(content)
                .setPositiveButton("Close", null).create(); dialog.show();
        if (documentUri != null) readingStore.words(documentUri, words -> {
            if (!dialog.isShowing() || isDestroyed()) return;
            if (words.isEmpty()) { TextView empty = new TextView(this); empty.setText("No saved words yet. Select text in this PDF and tap Meaning."); rows.addView(empty); }
            for (ReadingStore.Word word : words) {
                MaterialCardView card = listCard(word.term, word.explanation);
                card.setOnClickListener(v -> {
                    dialog.dismiss();
                    if (!getSupportFragmentManager().isStateSaved()) ExplanationBottomSheet.newInstance(
                            word.term, documentUri, word.explanation).show(getSupportFragmentManager(), "explanation_bottom_sheet");
                }); rows.addView(card);
            }
        });
    }
}
