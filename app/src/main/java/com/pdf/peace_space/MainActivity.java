package com.pdf.peace_space;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ext.SdkExtensions;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresExtension;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.pdf.viewer.fragment.PdfViewerFragment;

import com.google.android.material.button.MaterialButton;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String MIME_TYPE_PDF = "application/pdf";
    private static final String PDF_VIEWER_FRAGMENT_TAG = "pdf_viewer_fragment_tag";

    private PdfViewerFragment pdfViewerFragment;
    private MaterialButton getContentButton;
    private MaterialButton searchButton;

    private final ActivityResultLauncher<String[]> filePicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    Log.d(TAG, "File selected via picker: " + uri);
                    try {
                        getContentResolver().takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        );
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to take persistable URI permission", e);
                    }
                    if (isSdkExtensionSupported()) {
                        initializePdfViewerFragment();
                        if (pdfViewerFragment != null) {
                            pdfViewerFragment.setDocumentUri(uri);
                        }
                    } else {
                        Toast.makeText(this, "Device does not meet SDK extension requirements for PDF Viewer.", Toast.LENGTH_LONG).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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

        handleIncomingIntent(getIntent());
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
                try {
                    getContentResolver().takePersistableUriPermission(
                            pdfUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );
                } catch (Exception e) {
                    Log.w(TAG, "Failed to take persistable URI permission for intent data", e);
                }
                initializePdfViewerFragment();
                if (pdfViewerFragment != null) {
                    pdfViewerFragment.setDocumentUri(pdfUri);
                }
            }
        }
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
                    setBottomControlsVisible(false);
                }

                @Override
                public void onImmersiveModeRequested(boolean enterImmersive) {
                    setBottomControlsVisible(!enterImmersive);
                }
            });
        }
    }

    private void setBottomControlsVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        if (getContentButton != null) {
            getContentButton.setVisibility(visibility);
        }
        if (searchButton != null) {
            searchButton.setVisibility(visibility);
        }
    }
}
