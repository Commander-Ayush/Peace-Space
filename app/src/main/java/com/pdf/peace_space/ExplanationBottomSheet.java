package com.pdf.peace_space;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

public class ExplanationBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_SELECTED_TEXT = "selected_text";

    private String selectedText;
    private TextView tvSelectedText;
    private TextView tvExplanationResult;
    private LinearProgressIndicator progressIndicator;
    private final StringBuilder accumulatedExplanation = new StringBuilder();

    public static ExplanationBottomSheet newInstance(String selectedText) {
        ExplanationBottomSheet fragment = new ExplanationBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_SELECTED_TEXT, selectedText);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            selectedText = getArguments().getString(ARG_SELECTED_TEXT);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_explanation, container, false);

        tvSelectedText = view.findViewById(R.id.tv_selected_text);
        tvExplanationResult = view.findViewById(R.id.tv_explanation_result);
        progressIndicator = view.findViewById(R.id.progress_indicator);
        MaterialButton btnCopy = view.findViewById(R.id.btn_copy);
        MaterialButton btnClose = view.findViewById(R.id.btn_close);

        if (selectedText != null) {
            tvSelectedText.setText("\"" + selectedText + "\"");
        }

        btnClose.setOnClickListener(v -> dismiss());

        btnCopy.setOnClickListener(v -> {
            CharSequence explanation = tvExplanationResult.getText();
            if (explanation != null && explanation.length() > 0) {
                ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("PDF LLM Explanation", explanation);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(requireContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show();
                }
            }
        });

        generateExplanation();

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null) {
            View bottomSheet = getDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);

                int orientation = getResources().getConfiguration().orientation;
                int screenHeight = getResources().getDisplayMetrics().heightPixels;

                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    bottomSheet.getLayoutParams().height = (int) (screenHeight * 0.65);
                    behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                } else {
                    behavior.setPeekHeight((int) (screenHeight * 0.50));
                    behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                }
            }
        }
    }

    private void generateExplanation() {
        if (selectedText == null || selectedText.trim().isEmpty()) {
            tvExplanationResult.setText("No text selected to explain.");
            progressIndicator.setVisibility(View.GONE);
            return;
        }

        progressIndicator.setVisibility(View.VISIBLE);
        accumulatedExplanation.setLength(0);

        LlmHelper.getInstance().explainText(selectedText, new LlmHelper.LlmCallback() {
            @Override
            public void onTokenGenerated(String partialResult, boolean isComplete) {
                if (isAdded()) {
                    if (partialResult != null) {
                        accumulatedExplanation.append(partialResult);
                        tvExplanationResult.setText(accumulatedExplanation.toString());
                    }
                    if (isComplete) {
                        progressIndicator.setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void onError(Throwable error) {
                if (isAdded()) {
                    progressIndicator.setVisibility(View.GONE);
                    tvExplanationResult.setText("Error generating explanation:\n" + error.getLocalizedMessage());
                }
            }
        });
    }
}
