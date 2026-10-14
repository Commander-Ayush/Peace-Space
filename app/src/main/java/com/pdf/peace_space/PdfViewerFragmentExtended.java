package com.pdf.peace_space;

import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.pdf.ExperimentalPdfApi;
import androidx.annotation.RequiresExtension;
import androidx.pdf.PdfDocument;
import androidx.pdf.selection.model.TextSelection;
import androidx.pdf.view.PdfView;
import androidx.pdf.viewer.fragment.PdfViewerFragment;

@RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
public class PdfViewerFragmentExtended extends PdfViewerFragment {
    private static final String TAG = "PdfViewerExtended";

    public interface OnPdfEventListener {
        void onDocumentLoaded();
        void onImmersiveModeRequested(boolean enterImmersive);
        void onDocumentError(Throwable error);
        default void onRendererError(Throwable error) { onDocumentError(error); }
    }

    private OnPdfEventListener pdfEventListener;
    private PdfView documentView;

    public void setOnPdfEventListener(OnPdfEventListener listener) {
        this.pdfEventListener = listener;
    }

    @OptIn(markerClass = ExperimentalPdfApi.class)
    @Override
    public void onPdfViewCreated(@NonNull PdfView pdfView) {
        super.onPdfViewCreated(pdfView);
        documentView = pdfView;
        observeRendererFailures(pdfView);
        pdfView.addSelectionMenuItemPreparer(components -> {
            if (!(pdfView.getCurrentSelection() instanceof TextSelection)) {
                return;
            }
            components.add(0, MeaningSelectionAction.create(pdfView::getCurrentSelection, text -> {
                if (isAdded() && !getParentFragmentManager().isStateSaved()
                        && getParentFragmentManager().findFragmentByTag(
                                "explanation_bottom_sheet") == null) {
                    ExplanationBottomSheet.newInstance(text,
                            getActivity() instanceof MainActivity ? ((MainActivity) getActivity()).getCurrentDocumentUri() : null,
                            null).show(
                            getParentFragmentManager(), "explanation_bottom_sheet");
                }
            }));
        });
    }

    // beta01 exposes render failures only through this restricted diagnostics hook.
    // Keep the compatibility boundary here; it does not change the sandbox or its permissions.
    @android.annotation.SuppressLint("RestrictedApi")
    private void observeRendererFailures(PdfView view) {
        view.setRequestFailedListener(event -> {
            if (event instanceof androidx.pdf.event.RequestFailureEvent) {
                Throwable error = ((androidx.pdf.event.RequestFailureEvent) event).getException();
                if (isRendererConnectionError(error) && pdfEventListener != null) pdfEventListener.onRendererError(error);
            }
        });
    }

    static boolean isRendererConnectionError(Throwable error) {
        for (int depth = 0; error != null && depth < 8; depth++, error = error.getCause()) {
            String message = String.valueOf(error.getMessage());
            if (error instanceof android.os.RemoteException || error instanceof java.util.concurrent.TimeoutException
                    || message.contains("rendererAdapter") || message.contains("Unknown exception code")
                    || message.contains("Document has been closed")) return true;
        }
        return false;
    }

    int readingPage() { return documentView == null ? 0 : Math.max(0, documentView.getFirstVisiblePage()); }
    float readingZoom() { return documentView == null ? 1f : documentView.getZoom(); }

    void restoreReadingPosition(int page, float zoom) {
        if (documentView == null || page == 0 && zoom == 1f) return;
        PdfView target = documentView;
        target.addOnFirstContentLoadListener(new PdfView.OnFirstContentLoadListener() {
            @Override public void onFirstContentLoad() {
                target.removeOnFirstContentLoadListener(this);
                if (documentView != target) return;
                if (Float.isFinite(zoom) && zoom > 0) target.setZoom(zoom);
                if (target.getPdfDocument() != null)
                    target.scrollToPage(Math.max(0, Math.min(page, target.getPdfDocument().getPageCount() - 1)));
            }
        });
    }

    @android.annotation.SuppressLint("RestrictedApi")
    @Override public void onDestroyView() {
        if (documentView != null) documentView.setRequestFailedListener(null);
        documentView = null;
        super.onDestroyView();
    }

    @Override
    public void onLoadDocumentSuccess(@NonNull PdfDocument document) {
        super.onLoadDocumentSuccess(document);
        Log.d(TAG, "Document loaded successfully: " + document.getUri());
        setToolboxVisible(false);
        notifyDocumentLoaded();
    }

    void notifyDocumentLoaded() {
        if (pdfEventListener != null) pdfEventListener.onDocumentLoaded();
    }

    @Override
    public void onLoadDocumentError(@NonNull Throwable error) {
        super.onLoadDocumentError(error);
        Log.e(TAG, "Error loading document", error);
        if (pdfEventListener != null) pdfEventListener.onDocumentError(error);
    }

    @Override
    public void onRequestImmersiveMode(boolean enterImmersive) {
        super.onRequestImmersiveMode(enterImmersive);
        setToolboxVisible(false);
        if (pdfEventListener != null) {
            pdfEventListener.onImmersiveModeRequested(enterImmersive);
        }
    }
}
