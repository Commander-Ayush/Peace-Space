package com.pdf.peace_space;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class OverlayLayoutTest {
    @Test public void portraitCardAndLandscapePanelKeepScrollAreaAndButtonsVisible() throws Exception {
        renderExplanation(360, 450, "portrait");
        renderExplanation(420, 300, "landscape");
    }
    @Test public void homeControlsFitAtBottomRight() throws Exception {
        try (org.robolectric.android.controller.ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class)) {
            Activity activity = controller.get(); activity.setTheme(R.style.Theme_PeaceSpace); controller.create();
            View home = LayoutInflater.from(activity).inflate(R.layout.activity_main, null);
            home.findViewById(R.id.home_button).setVisibility(View.GONE);
            float density = activity.getResources().getDisplayMetrics().density;
            int width = Math.round(390 * density), height = Math.round(780 * density);
            home.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            home.layout(0, 0, width, height);
            for (int id : new int[]{R.id.launch_button, R.id.search_button, R.id.history_button}) {
                androidx.appcompat.widget.AppCompatImageButton button = home.findViewById(id);
                assertNotNull("Control icon must be loaded", button.getDrawable());
                assertTrue("Icon needs visible bounds", button.getDrawable().getBounds().width() > 0);
            }
            View controls = home.findViewById(R.id.reading_controls);
            assertTrue(controls.getLeft() > width / 3);
            assertTrue(controls.getRight() <= width);
            assertTrue(controls.getBottom() <= height);
            Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            home.draw(new Canvas(image));
            File file = new File("build/previews/home.png"); file.getParentFile().mkdirs();
            try (FileOutputStream out = new FileOutputStream(file)) { image.compress(Bitmap.CompressFormat.PNG, 100, out); }
            image.recycle();
        }
    }

    @Test @Config(sdk = 35, qualifiers = "land")
    public void landscapeDialogUsesRightSideAndSlideAnimation() {
        checkDialog(android.view.Gravity.RIGHT | android.view.Gravity.CENTER_VERTICAL, R.style.ExplanationPanelAnimation);
    }
    @Test public void portraitDialogUsesCenteredBox() {
        checkDialog(android.view.Gravity.CENTER, android.R.style.Animation_Dialog);
    }
    private void checkDialog(int gravity, int animation) {
        try (org.robolectric.android.controller.ActivityController<androidx.fragment.app.FragmentActivity> controller = Robolectric.buildActivity(androidx.fragment.app.FragmentActivity.class)) {
            androidx.fragment.app.FragmentActivity activity = controller.get();
            activity.setTheme(R.style.Theme_PeaceSpace); controller.setup();
            ExplanationBottomSheet panel = ExplanationBottomSheet.newInstance("break the ice", null, "Start a conversation.");
            panel.showNow(activity.getSupportFragmentManager(), "test-panel");
            android.view.WindowManager.LayoutParams params = panel.requireDialog().getWindow().getAttributes();
            assertEquals(gravity, params.gravity);
            assertEquals(animation, params.windowAnimations);
            assertTrue(params.width > 0);
            assertTrue(params.height > 0);
            assertEquals("Start a conversation.", ((TextView) panel.requireView().findViewById(R.id.tv_explanation_result)).getText().toString());
            panel.dismissNow();
        }
    }

    private void renderExplanation(int widthDp, int heightDp, String name) throws Exception {
        try (org.robolectric.android.controller.ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class)) {
            Activity activity = controller.get(); activity.setTheme(R.style.Theme_PeaceSpace); controller.create();
            View panel = LayoutInflater.from(activity).inflate(R.layout.bottom_sheet_explanation, null);
            ((TextView) panel.findViewById(R.id.tv_selected_text)).setText("“break the ice”");
            ((TextView) panel.findViewById(R.id.tv_explanation_result)).setText(
                    "To make people feel more comfortable in a new or awkward situation.\n\nExample\nShe told a funny story to break the ice before the meeting.\n\nThis idiom describes starting a friendly conversation.");
            panel.findViewById(R.id.progress_indicator).setVisibility(View.GONE);
            float density = activity.getResources().getDisplayMetrics().density;
            int width = Math.round(widthDp * density), height = Math.round(heightDp * density);
            panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            panel.layout(0, 0, width, height);
            assertTrue(panel.findViewById(R.id.nested_scroll_view).getHeight() > 0);
            View buttons = (View) panel.findViewById(R.id.btn_close).getParent();
            assertTrue(buttons.getBottom() <= height);
            assertTrue(panel.findViewById(R.id.btn_close).getHeight() >= 48 * density);
            Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            panel.draw(new Canvas(image));
            File file = new File("build/previews/" + name + ".png"); file.getParentFile().mkdirs();
            try (FileOutputStream out = new FileOutputStream(file)) { image.compress(Bitmap.CompressFormat.PNG, 100, out); }
            image.recycle();
        }
    }
}
