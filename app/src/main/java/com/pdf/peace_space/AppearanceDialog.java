package com.pdf.peace_space;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;
import android.text.Editable;
import android.text.TextWatcher;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import com.google.android.material.button.MaterialButton;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.util.Locale;

final class AppearanceDialog {
    private AppearanceDialog() {}
    static void show(Context context) {
        OverlayAppearance appearance = new OverlayAppearance(context);
        LinearLayout content = new LinearLayout(context); content.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * context.getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding / 2, padding, 0);
        TextView note = new TextView(context);
        note.setText("Choose any color using its six-digit hex code. The panel stays translucent, and text adjusts for contrast.");
        content.addView(note);
        TextInputLayout surface = field(context, content, "Panel color", appearance.surface());
        TextInputLayout buttons = field(context, content, "Button color", appearance.buttons());
        LinearLayout preview = new LinearLayout(context); preview.setOrientation(LinearLayout.VERTICAL);
        preview.setPadding(padding, padding / 2, padding, padding / 2);
        TextView sample = new TextView(context); sample.setText("Explanation preview"); preview.addView(sample);
        MaterialButton sampleButton = new MaterialButton(context); sampleButton.setText("Button preview"); preview.addView(sampleButton);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = padding;
        content.addView(preview, params);
        Runnable refresh = () -> {
            try {
                int panel = OverlayAppearance.parse(surface.getEditText().getText().toString());
                int accent = OverlayAppearance.parse(buttons.getEditText().getText().toString());
                preview.setBackgroundColor(panel); sample.setTextColor(OverlayAppearance.foreground(panel));
                sampleButton.setBackgroundTintList(ColorStateList.valueOf(accent));
                sampleButton.setTextColor(OverlayAppearance.foreground(accent));
            } catch (IllegalArgumentException ignored) { }
        };
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) { refresh.run(); }
            @Override public void afterTextChanged(Editable text) { }
        };
        surface.getEditText().addTextChangedListener(watcher); buttons.getEditText().addTextChangedListener(watcher); refresh.run();
        ScrollView scroll = new ScrollView(context); scroll.addView(content);
        AlertDialog dialog = new MaterialAlertDialogBuilder(context).setTitle("Explanation appearance").setView(scroll)
                .setNegativeButton("Cancel", null).setNeutralButton("Reset", (d, which) -> appearance.reset())
                .setPositiveButton("Save", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            Integer panelColor = parse(surface), buttonColor = parse(buttons);
            if (panelColor != null && buttonColor != null) { appearance.save(panelColor, buttonColor); dialog.dismiss(); }
        }));
        dialog.show();
    }
    private static TextInputLayout field(Context context, LinearLayout parent, String label, int color) {
        TextInputLayout layout = new TextInputLayout(context); layout.setHint(label);
        TextInputEditText input = new TextInputEditText(context); input.setSingleLine(true);
        input.setText(String.format(Locale.ROOT, "#%06X", color & 0xffffff));
        layout.addView(input); parent.addView(layout);
        LinearLayout swatches = new LinearLayout(context);
        int[] colors = {0xffdbeafe, 0xffd1fae5, 0xfffde68a, 0xfffbcfe8, 0xfff5f8fc, 0xff203040};
        String[] names = {"Sky", "Mint", "Warm", "Rose", "Light", "Dark"};
        int size = Math.round(48 * context.getResources().getDisplayMetrics().density);
        for (int i = 0; i < colors.length; i++) {
            final int selected = colors[i];
            View swatch = new View(context); swatch.setContentDescription(label + ": " + names[i]);
            GradientDrawable shape = new GradientDrawable(); shape.setColor(selected);
            shape.setCornerRadius(size / 5f); shape.setStroke(1, 0xff999999); swatch.setBackground(shape);
            LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, size); cell.weight = 1; cell.setMargins(3, 6, 3, 6);
            swatches.addView(swatch, cell); swatch.setFocusable(true);
            swatch.setOnClickListener(v -> input.setText(String.format(Locale.ROOT, "#%06X", selected & 0xffffff)));
        }
        parent.addView(swatches);
        return layout;
    }
    private static Integer parse(TextInputLayout field) {
        try {
            int color = OverlayAppearance.parse(field.getEditText().getText().toString());
            field.setError(null); return color;
        } catch (IllegalArgumentException error) { field.setError(error.getMessage()); return null; }
    }
}
