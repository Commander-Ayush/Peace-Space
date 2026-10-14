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
import android.view.MotionEvent;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Rect;
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
    private androidx.pdf.PdfSandboxHandle pdfSandbox;
    private androidx.fragment.app.Fragment retiringViewer;
    private AppCompatImageButton getContentButton;
    private AppCompatImageButton searchButton;
    private ReadingStore readingStore;
    private String currentDocumentUri;
    private String currentAccessUri;
    private boolean showingDocument;
    private int homeLoadId;
    private int openRequestId;
    private boolean documentLoaded;
    private boolean actionsExpanded;
    private boolean readerTap;
    private float tapX, tapY;
    private long tapTime;
    private String pendingReconnectUri;
    private boolean transactionsReady;
    private boolean pickerInFlight;
    private boolean resolvingPicker;
    private boolean restoreOnResume;
    private Uri deferredOpenUri;
    private String deferredOpenIdentity;
    private int restorePage;
    private float restoreZoom = 1f;
    private int rendererRetries;
    private Runnable loadWatchdog;
    private final Handler controlsHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideControls = () -> {
        if (showingDocument) {
            actionsExpanded = false;
            findViewById(R.id.reading_controls).setVisibility(View.GONE);
            findViewById(R.id.more_button).setVisibility(View.GONE);
        }
    };

    private final ActivityResultLauncher<String[]> filePicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                pickerInFlight = false;
                if (uri != null) {
                    String reconnect = pendingReconnectUri;
                    pendingReconnectUri = null;
                    if (reconnect != null) openDocument(uri, reconnect);
                    else {
                        final int request = ++openRequestId;
                        resolvingPicker = true;
                        resolvePickedDocumentIdentity(uri, identity -> {
                            if (!isDestroyed() && !isFinishing() && request == openRequestId) {
                                resolvingPicker = false;
                                openDocument(uri, identity);
                            }
                        });
                    }
                } else {
                    pendingReconnectUri = null;
                    resumePendingDocument();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        readingStore = ReadingStore.get(this);
        if (savedInstanceState != null) {
            currentDocumentUri = savedInstanceState.getString("current_document_uri");
            currentAccessUri = savedInstanceState.getString("current_access_uri", currentDocumentUri);
            showingDocument = savedInstanceState.getBoolean("showing_document");
            pendingReconnectUri = savedInstanceState.getString("pending_reconnect_uri");
            pickerInFlight = savedInstanceState.getBoolean("picker_in_flight");
            restorePage = savedInstanceState.getInt("reading_page");
            restoreZoom = savedInstanceState.getFloat("reading_zoom", 1f);
            String deferred = savedInstanceState.getString("deferred_open_uri");
            deferredOpenUri = deferred == null ? null : Uri.parse(deferred);
            deferredOpenIdentity = savedInstanceState.getString("deferred_open_identity");
            restoreOnResume = showingDocument;
        }

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.pdf_container_view), (v, windowInsets) -> {
            Insets insets = showingDocument ? windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout()) : windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        if (isSdkExtensionSupported()) {
            retainPdfSandbox();
            pdfViewerFragment = (PdfViewerFragment) getSupportFragmentManager()
                    .findFragmentByTag(PDF_VIEWER_FRAGMENT_TAG);
            attachPdfEventListener();
        }

        getContentButton = findViewById(R.id.launch_button);
        searchButton = findViewById(R.id.search_button);

        getContentButton.setOnClickListener(v -> {
            pendingReconnectUri = null;
            hideControls.run();
            launchPdfPicker();
        });

        searchButton.setOnClickListener(v -> {
            if (pdfViewerFragment != null) {
                boolean isSearchActive = pdfViewerFragment.isTextSearchActive();
                pdfViewerFragment.setTextSearchActive(!isSearchActive);
                hideControls.run();
            }
        });

        findViewById(R.id.history_button).setOnClickListener(v -> { hideControls.run(); showWordHistory(); });
        findViewById(R.id.appearance_settings).setOnClickListener(v -> AppearanceDialog.show(this));
        findViewById(R.id.appearance_button).setOnClickListener(v -> { hideControls.run(); AppearanceDialog.show(this); });
        findViewById(R.id.more_button).setOnClickListener(v -> {
            controlsHandler.removeCallbacks(hideControls);
            actionsExpanded = true;
            updatePageVisibility();
        });
        findViewById(R.id.home_button).setOnClickListener(v -> showHome());
        for (int id : new int[]{R.id.home_button, R.id.launch_button, R.id.search_button, R.id.history_button, R.id.appearance_button, R.id.more_button}) {
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

    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
    protected void retainPdfSandbox() {
        // A stable warm binding prevents isolated-service teardown between documents.
        if (pdfSandbox == null) pdfSandbox = androidx.pdf.SandboxedPdfLoader.startInitialization(this);
    }

    protected void resolvePickedDocumentIdentity(Uri uri, java.util.function.Consumer<String> callback) {
        readingStore.identityForAccessUri(uri.toString(), callback);
    }

    private void launchPdfPicker() {
        pickerInFlight = true;
        filePicker.launch(new String[]{MIME_TYPE_PDF});
    }

    @Override protected void onPause() {
        transactionsReady = false;
        super.onPause();
    }

    @Override protected void onStop() {
        if (showingDocument && currentAccessUri != null) {
            captureReadingPosition();
            restoreOnResume = true;
            rendererRetries = 0;
        }
        if (loadWatchdog != null) controlsHandler.removeCallbacks(loadWatchdog);
        super.onStop();
    }

    @Override protected void onResumeFragments() {
        super.onResumeFragments();
        transactionsReady = true;
        resumePendingDocument();
    }

    private void resumePendingDocument() {
        if (!transactionsReady || getSupportFragmentManager().isStateSaved() || isFinishing() || isDestroyed()) return;
        if (deferredOpenUri != null) {
            Uri uri = deferredOpenUri; String identity = deferredOpenIdentity;
            deferredOpenUri = null; deferredOpenIdentity = null;
            restoreOnResume = false;
            openDocumentAttempt(uri, identity);
        } else if (restoreOnResume && showingDocument && currentAccessUri != null
                && !pickerInFlight && !resolvingPicker) {
            restoreOnResume = false;
            openDocumentAttempt(Uri.parse(currentAccessUri), currentDocumentUri);
        }
    }

    private void captureReadingPosition() {
        if (documentLoaded && pdfViewerFragment instanceof PdfViewerFragmentExtended && isSdkExtensionSupported()) {
            PdfViewerFragmentExtended viewer = (PdfViewerFragmentExtended) pdfViewerFragment;
            restorePage = viewer.readingPage();
            restoreZoom = viewer.readingZoom();
        }
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

    protected void checkAndInitializeLlmModel() {
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

    protected boolean isSdkExtensionSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13;
    }

    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
    protected PdfViewerFragmentExtended createPdfViewerFragment() { return new PdfViewerFragmentExtended(); }

    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
    protected void installViewer(PdfViewerFragmentExtended viewer, Uri uri) {
        androidx.fragment.app.FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        if (retiringViewer != null && retiringViewer.isAdded()) transaction.hide(retiringViewer);
        transaction.add(R.id.fragment_container_view, viewer, PDF_VIEWER_FRAGMENT_TAG).commitNow();
        viewer.setDocumentUri(uri);
    }

    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
    private void attachPdfEventListener() {
        if (!(pdfViewerFragment instanceof PdfViewerFragmentExtended)) return;
        final PdfViewerFragmentExtended source = (PdfViewerFragmentExtended) pdfViewerFragment;
        source.setOnPdfEventListener(new PdfViewerFragmentExtended.OnPdfEventListener() {
            @Override public void onDocumentLoaded() {
                if (source != pdfViewerFragment || !showingDocument) return;
                documentLoaded = true;
                releaseRetiringViewer();
                if (loadWatchdog != null) controlsHandler.removeCallbacks(loadWatchdog);
                source.restoreReadingPosition(restorePage, restoreZoom);
                updatePageVisibility();
                hideControls.run();
                Uri access = currentAccessUri == null ? null : Uri.parse(currentAccessUri);
                String identity = currentDocumentUri;
                if (identity != null && access != null) readingStore.reconnectDocument(identity, access.toString(), documentTitle(access));
            }
            @Override public void onDocumentError(Throwable error) {
                controlsHandler.post(() -> {
                    if (source != pdfViewerFragment || !showingDocument || isFinishing() || isDestroyed()) return;
                    if (!transactionsReady || getSupportFragmentManager().isStateSaved()) {
                        restoreOnResume = true;
                        return;
                    }
                    String identity = currentDocumentUri;
                    resetViewer();
                    showHome();
                    showOpenError(identity, error);
                });
            }
            @Override public void onRendererError(Throwable error) { recoverRenderer(source, error); }
            @Override public void onImmersiveModeRequested(boolean enterImmersive) { }
        });
    }

    private void recoverRenderer(PdfViewerFragmentExtended source, Throwable error) {
        controlsHandler.post(() -> {
            if (source != pdfViewerFragment || !showingDocument || isFinishing() || isDestroyed()) return;
            if (!transactionsReady || getSupportFragmentManager().isStateSaved() || pickerInFlight || resolvingPicker) {
                restoreOnResume = true;
                return;
            }
            captureReadingPosition();
            if (rendererRetries++ == 0 && currentAccessUri != null) {
                Log.w(TAG, "Reopening disconnected PDF renderer", error);
                openDocumentAttempt(Uri.parse(currentAccessUri), currentDocumentUri);
            } else {
                String identity = currentDocumentUri;
                if (isSdkExtensionSupported()) resetViewer();
                showHome();
                showOpenError(identity, error);
            }
        });
    }

    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
    private void resetViewer() {
        if (loadWatchdog != null) controlsHandler.removeCallbacks(loadWatchdog);
        // Never forget a fragment merely because a transaction cannot run yet.
        if (getSupportFragmentManager().isStateSaved()) return;
        androidx.fragment.app.Fragment active = pdfViewerFragment != null ? pdfViewerFragment
                : getSupportFragmentManager().findFragmentByTag(PDF_VIEWER_FRAGMENT_TAG);
        if (active instanceof PdfViewerFragmentExtended) {
            ((PdfViewerFragmentExtended) active).setOnPdfEventListener(null);
        }
        if (active != null && active.isAdded()) {
            try {
                getSupportFragmentManager().beginTransaction().remove(active).commitNow();
            } catch (RuntimeException error) {
                // A native/service close failure must not recursively poison the next open.
                Log.w(TAG, "Previous PDF cleanup failed", error);
            }
        }
        pdfViewerFragment = null;
        documentLoaded = false;
    }

    private void releaseRetiringViewer() {
        if (retiringViewer != null && !getSupportFragmentManager().isStateSaved()) {
            if (retiringViewer.isAdded()) getSupportFragmentManager().beginTransaction().remove(retiringViewer).commitNow();
            retiringViewer = null;
        }
    }

    public String getCurrentDocumentUri() { return currentDocumentUri; }

    @Override protected void onSaveInstanceState(@NonNull Bundle state) {
        captureReadingPosition();
        state.putInt("reading_page", restorePage);
        state.putFloat("reading_zoom", restoreZoom);
        state.putBoolean("picker_in_flight", pickerInFlight);
        state.putString("deferred_open_uri", deferredOpenUri == null ? null : deferredOpenUri.toString());
        state.putString("deferred_open_identity", deferredOpenIdentity);
        state.putString("current_document_uri", currentDocumentUri);
        state.putString("current_access_uri", currentAccessUri);
        state.putBoolean("showing_document", showingDocument);
        state.putString("pending_reconnect_uri", pendingReconnectUri);
        super.onSaveInstanceState(state);
    }

    private void openDocument(Uri uri) { openDocument(uri, uri.toString()); }

    void openDocument(Uri uri, String identity) {
        rendererRetries = 0;
        openDocumentAttempt(uri, identity);
    }

    private void openDocumentAttempt(Uri uri, String identity) {
        openRequestId++;
        if (!isSdkExtensionSupported()) {
            Toast.makeText(this, "This device does not support the PDF viewer.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!transactionsReady || getSupportFragmentManager().isStateSaved()) {
            deferredOpenUri = uri;
            deferredOpenIdentity = identity;
            return;
        }
        deferredOpenUri = null;
        deferredOpenIdentity = null;
        restoreOnResume = false;
        try {
            try (android.content.res.AssetFileDescriptor file = getContentResolver().openAssetFileDescriptor(uri, "r")) {
                if (file == null) throw new java.io.IOException("Document unavailable");
            }
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch (SecurityException ignored) { /* Recover expired grants through Locate PDF. */ }
            // Never reuse a failed/previous renderer or its native document state.
            if (identity == null || !identity.equals(currentDocumentUri)) { restorePage = 0; restoreZoom = 1f; }
            // Bind the next document before releasing the previous document's service.
            // Some devices terminate the isolated renderer between consecutive unbind/bind calls.
            releaseRetiringViewer();
            if (pdfViewerFragment != null && pdfViewerFragment.isAdded()) {
                retiringViewer = pdfViewerFragment;
                if (retiringViewer instanceof PdfViewerFragmentExtended)
                    ((PdfViewerFragmentExtended) retiringViewer).setOnPdfEventListener(null);
                pdfViewerFragment = null;
            } else resetViewer();
            currentDocumentUri = identity;
            currentAccessUri = uri.toString();
            showingDocument = true;
            documentLoaded = false;
            actionsExpanded = false;
            controlsHandler.removeCallbacks(hideControls);
            pdfViewerFragment = createPdfViewerFragment();
            attachPdfEventListener();
            updatePageVisibility();
            final PdfViewerFragmentExtended opening = (PdfViewerFragmentExtended) pdfViewerFragment;
            loadWatchdog = () -> {
                if (opening == pdfViewerFragment && !documentLoaded && showingDocument)
                    recoverRenderer(opening, new java.util.concurrent.TimeoutException("PDF loading did not complete"));
            };
            controlsHandler.postDelayed(loadWatchdog, 20000);
            installViewer(opening, uri);
        } catch (Exception error) {
            resetViewer();
            showHome();
            showOpenError(identity, error);
        }
    }

    private String documentTitle(Uri uri) {
        String title = uri.getLastPathSegment();
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) title = cursor.getString(0);
        } catch (RuntimeException ignored) { }
        return title == null ? "PDF document" : title;
    }

    private void showOpenError(String identity, Throwable error) {
        Log.e(TAG, "PDF open failed", error);
        if (isFinishing() || isDestroyed()) return;
        new MaterialAlertDialogBuilder(this).setTitle("Couldn’t open this PDF")
                .setMessage("Its saved location may no longer be accessible, or the file may not be a valid PDF. Locate the same PDF to reconnect this entry and keep its word history.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Locate PDF", (dialog, which) -> {
                    pendingReconnectUri = identity;
                    launchPdfPicker();
                }).show();
    }

    private void showHome() {
        openRequestId++;
        deferredOpenUri = null; deferredOpenIdentity = null;
        restoreOnResume = false;
        resolvingPicker = false;
        controlsHandler.removeCallbacks(hideControls);
        showingDocument = false;
        actionsExpanded = false;
        updatePageVisibility();
        refreshRecentDocuments();
    }

    private void updatePageVisibility() {
        findViewById(R.id.home_content).setVisibility(showingDocument ? View.GONE : View.VISIBLE);
        findViewById(R.id.fragment_container_view).setVisibility(showingDocument ? View.VISIBLE : View.GONE);
        updateReaderWindow();
        boolean actions = showingDocument && documentLoaded && actionsExpanded;
        for (int id : new int[]{R.id.home_button, R.id.search_button, R.id.history_button, R.id.appearance_button}) {
            findViewById(id).setVisibility(actions ? View.VISIBLE : View.GONE);
        }
        findViewById(R.id.reading_controls).setVisibility(!showingDocument || actions ? View.VISIBLE : View.GONE);
        findViewById(R.id.more_button).setVisibility(View.GONE);
        if (searchButton != null) searchButton.setEnabled(documentLoaded);
    }

    private void updateReaderWindow() {
        androidx.core.view.WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.setAppearanceLightStatusBars(false);
        bars.setAppearanceLightNavigationBars(false);
        bars.setSystemBarsBehavior(androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        if (showingDocument) bars.hide(WindowInsetsCompat.Type.systemBars());
        else bars.show(WindowInsetsCompat.Type.systemBars());
        ViewCompat.requestApplyInsets(findViewById(R.id.pdf_container_view));
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && findViewById(R.id.pdf_container_view) != null) updateReaderWindow();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            readerTap = showingDocument && documentLoaded
                    && !isInside(findViewById(R.id.reading_controls), event)
                    && !isInside(findViewById(R.id.more_button), event);
            tapX = event.getX(); tapY = event.getY(); tapTime = event.getEventTime();
        } else if (action == MotionEvent.ACTION_MOVE) {
            int slop = android.view.ViewConfiguration.get(this).getScaledTouchSlop();
            if (Math.abs(event.getX() - tapX) > slop || Math.abs(event.getY() - tapY) > slop) readerTap = false;
        } else if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_CANCEL) {
            readerTap = false;
        } else if (action == MotionEvent.ACTION_UP) {
            if (readerTap && showingDocument && documentLoaded
                    && event.getEventTime() - tapTime < android.view.ViewConfiguration.getLongPressTimeout()) {
                boolean visible = actionsExpanded || findViewById(R.id.more_button).getVisibility() == View.VISIBLE;
                hideControls.run();
                if (!visible) findViewById(R.id.more_button).setVisibility(View.VISIBLE);
            }
            readerTap = false;
        }
        return super.dispatchTouchEvent(event);
    }
    private boolean isInside(View view, MotionEvent event) {
        Rect rect = new Rect();
        return view.getVisibility() == View.VISIBLE && view.getGlobalVisibleRect(rect)
                && rect.contains((int) event.getRawX(), (int) event.getRawY());
    }
    @Override protected void onDestroy() {
        controlsHandler.removeCallbacksAndMessages(null);
        if (pdfSandbox != null) { pdfSandbox.close(); pdfSandbox = null; }
        super.onDestroy();
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
                MaterialCardView card = listCard(document.title, null);
                card.setOnClickListener(v -> {
                    final int openRequest = ++openRequestId;
                    readingStore.accessUri(document.uri, access -> {
                        if (!isDestroyed() && !isFinishing() && openRequest == openRequestId)
                            openDocument(Uri.parse(access), document.uri);
                    });
                }); rows.addView(card);
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
