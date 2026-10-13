package com.pdf.peace_space;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

/** Retains the original class name for compatibility; now a card / side panel. */
public class ExplanationBottomSheet extends DialogFragment {
    public static ExplanationBottomSheet newInstance(String text) { return newInstance(text, null, null); }
    public static ExplanationBottomSheet newInstance(String text, String documentUri, String saved) {
        ExplanationBottomSheet fragment = new ExplanationBottomSheet();
        Bundle args = new Bundle();
        args.putString("selected_text", text); args.putString("document_uri", documentUri);
        args.putString("saved_explanation", saved); fragment.setArguments(args);
        return fragment;
    }
    @Override public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setStyle(STYLE_NO_TITLE, R.style.ExplanationDialog);
    }
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
                                               @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.bottom_sheet_explanation, container, false);
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        Bundle args = requireArguments();
        String selected = args.getString("selected_text", "");
        ((TextView) view.findViewById(R.id.tv_selected_text)).setText("“" + selected + "”");
        ExplanationViewModel model = new ViewModelProvider(this).get(ExplanationViewModel.class);
        TextView result = view.findViewById(R.id.tv_explanation_result);
        View progress = view.findViewById(R.id.progress_indicator);
        View copy = view.findViewById(R.id.btn_copy);
        model.result.observe(getViewLifecycleOwner(), result::setText);
        model.busy.observe(getViewLifecycleOwner(), busy -> {
            progress.setVisibility(busy ? View.VISIBLE : View.GONE);
            copy.setEnabled(!busy);
        });
        view.findViewById(R.id.btn_close).setOnClickListener(v -> dismiss());
        copy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && result.length() > 0) {
                clipboard.setPrimaryClip(ClipData.newPlainText("Explanation", result.getText()));
                Toast.makeText(requireContext(), "Copied", Toast.LENGTH_SHORT).show();
            }
        });
        model.start(selected, args.getString("document_uri"), args.getString("saved_explanation"), ReadingStore.get(requireContext()));
    }
    @Override public void onStart() {
        super.onStart();
        Window window = requireDialog().getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams params = window.getAttributes();
        params.dimAmount = 0.15f;
        window.setAttributes(params);
        android.graphics.Rect bounds = requireActivity().getWindowManager().getCurrentWindowMetrics().getBounds();
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        int margin = (int) (16 * getResources().getDisplayMetrics().density);
        int maxWidth = (int) ((landscape ? 440 : 540) * getResources().getDisplayMetrics().density);
        window.setLayout(Math.min(maxWidth, (int) (bounds.width() * (landscape ? 0.48 : 0.92))),
                landscape ? bounds.height() - 2 * margin : (int) (bounds.height() * 0.62));
        window.setGravity(landscape ? Gravity.RIGHT | Gravity.CENTER_VERTICAL : Gravity.CENTER);
        params = window.getAttributes();
        params.x = landscape ? margin : 0;
        params.windowAnimations = landscape ? R.style.ExplanationPanelAnimation : android.R.style.Animation_Dialog;
        window.setAttributes(params);
    }
}
