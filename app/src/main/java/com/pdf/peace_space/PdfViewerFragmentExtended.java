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
    }

    private OnPdfEventListener pdfEventListener;

    public void setOnPdfEventListener(OnPdfEventListener listener) {
        this.pdfEventListener = listener;
    }

    @OptIn(markerClass = ExperimentalPdfApi.class)
    @Override
    public void onPdfViewCreated(@NonNull PdfView pdfView) {
        super.onPdfViewCreated(pdfView);
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

    @Override
    public void onLoadDocumentSuccess(@NonNull PdfDocument document) {
        super.onLoadDocumentSuccess(document);
        Log.d(TAG, "Document loaded successfully: " + document.getUri());
        setToolboxVisible(false);
        if (pdfEventListener != null) {
            pdfEventListener.onDocumentLoaded();
        }
    }

    @Override
    public void onLoadDocumentError(@NonNull Throwable error) {
        super.onLoadDocumentError(error);
        Log.e(TAG, "Error loading document", error);
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
