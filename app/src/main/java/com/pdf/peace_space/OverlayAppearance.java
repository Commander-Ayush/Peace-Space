package com.pdf.peace_space;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;

final class OverlayAppearance {
    private final SharedPreferences preferences;
    OverlayAppearance(Context context) { preferences = context.getSharedPreferences("overlay_appearance", Context.MODE_PRIVATE); }
    int surface() { return preferences.getInt("surface", Color.BLACK); }
    int buttons() { return preferences.getInt("buttons", Color.rgb(48, 48, 48)); }
    void save(int surface, int buttons) { preferences.edit().putInt("surface", surface).putInt("buttons", buttons).apply(); }
    void reset() { preferences.edit().clear().apply(); }
    static int foreground(int background) {
        return androidx.core.graphics.ColorUtils.calculateContrast(Color.BLACK, background)
                >= androidx.core.graphics.ColorUtils.calculateContrast(Color.WHITE, background)
                ? Color.BLACK : Color.WHITE;
    }
    static int parse(String hex) {
        String value = hex.trim();
        if (!value.matches("#?[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Use six hexadecimal digits, for example #203040.");
        return Color.parseColor(value.startsWith("#") ? value : "#" + value);
    }
    void apply(View view) {
        int surface = surface(), accent = buttons(), foreground = foreground(surface);
        GradientDrawable background = new GradientDrawable();
        background.setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(surface, 230));
        background.setCornerRadius(20 * view.getResources().getDisplayMetrics().density);
        view.setBackground(background);
        for (int id : new int[]{R.id.tv_selected_text_title, R.id.tv_selected_text, R.id.tv_explanation_result}) {
            ((TextView) view.findViewById(id)).setTextColor(foreground);
        }
        for (int id : new int[]{R.id.btn_copy, R.id.btn_close}) {
            MaterialButton button = view.findViewById(id);
            button.setBackgroundTintList(ColorStateList.valueOf(accent));
            button.setTextColor(foreground(accent));
            button.setStrokeColor(ColorStateList.valueOf(accent));
            button.setIconTint(ColorStateList.valueOf(foreground(accent)));
        }
    }
}
