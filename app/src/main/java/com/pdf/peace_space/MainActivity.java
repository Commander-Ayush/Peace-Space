package com.pdf.peace_space;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ext.SdkExtensions;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.pdf.viewer.fragment.PdfViewerFragment;

import com.google.android.material.button.MaterialButton;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String MIME_TYPE_PDF = "application/pdf";
    private static final String PDF_VIEWER_FRAGMENT_TAG = "pdf_viewer_fragment_tag";

    private PdfViewerFragment pdfViewerFragment;

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

        if (isSdkExtensionSupported()) {
            pdfViewerFragment = (PdfViewerFragment) getSupportFragmentManager()
                    .findFragmentByTag(PDF_VIEWER_FRAGMENT_TAG);
        }

        MaterialButton getContentButton = findViewById(R.id.launch_button);
        MaterialButton searchButton = findViewById(R.id.search_button);

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

            pdfViewerFragment = new PdfViewerFragmentExtended();
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
}
