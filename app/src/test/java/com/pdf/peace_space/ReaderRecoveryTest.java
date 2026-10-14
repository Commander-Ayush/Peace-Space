package com.pdf.peace_space;

import android.net.Uri;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@androidx.annotation.RequiresExtension(extension = android.os.Build.VERSION_CODES.S, version = 13)
public class ReaderRecoveryTest {
    public static class TestActivity extends MainActivity {
        final List<PdfViewerFragmentExtended> viewers = new ArrayList<>();
        @Override protected void checkAndInitializeLlmModel() { }
        @Override protected void retainPdfSandbox() { }
        @Override protected boolean isSdkExtensionSupported() { return true; }
        @Override protected void resolvePickedDocumentIdentity(Uri uri, java.util.function.Consumer<String> callback) {
            callback.accept(uri.toString());
        }
        @Override protected void installViewer(PdfViewerFragmentExtended viewer, Uri uri) {
            viewers.add(viewer);
            if (uri.toString().contains("stalled")) return;
            if (uri.toString().contains("broken")) viewer.onLoadDocumentError(new IllegalArgumentException("Invalid PDF"));
            else viewer.notifyDocumentLoaded();
        }
    }
    private void tap(TestActivity activity, long time) {
        MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 20, 20, 0);
        MotionEvent up = MotionEvent.obtain(time, time + 100, MotionEvent.ACTION_UP, 20, 20, 0);
        activity.dispatchTouchEvent(down); activity.dispatchTouchEvent(up); down.recycle(); up.recycle();
    }
    private Uri file(String name) throws Exception {
        File file = new File(RuntimeEnvironment.getApplication().getCacheDir(), name);
        try (java.io.FileWriter out = new java.io.FileWriter(file)) { out.write("Test loader fixture"); }
        return Uri.fromFile(file);
    }
    @Test public void failedLoadReturnsHomeAndNextOpenUsesFreshViewer() throws Exception {
        try (org.robolectric.android.controller.ActivityController<TestActivity> controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            TestActivity activity = controller.get();
            Uri broken = file("broken.pdf");
            activity.openDocument(broken, broken.toString()); ShadowLooper.idleMainLooper();
            assertEquals(View.VISIBLE, activity.findViewById(R.id.home_content).getVisibility());
            assertEquals(View.GONE, activity.findViewById(R.id.fragment_container_view).getVisibility());
            android.app.Dialog dialog = ShadowDialog.getLatestDialog(); if (dialog != null) dialog.dismiss();
            Uri valid = file("valid.pdf"); activity.openDocument(valid, valid.toString());
            assertEquals(View.VISIBLE, activity.findViewById(R.id.fragment_container_view).getVisibility());
            assertEquals(2, activity.viewers.size());
            assertNotSame(activity.viewers.get(0), activity.viewers.get(1));
            Uri second = file("second.pdf"); activity.openDocument(second, second.toString());
            assertEquals(3, activity.viewers.size());
            assertNotSame(activity.viewers.get(1), activity.viewers.get(2));
        }
    }
    @Test public void homeHasOnlyOpenAndReaderActionsRequireTapThenDots() throws Exception {
        try (org.robolectric.android.controller.ActivityController<TestActivity> controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            TestActivity activity = controller.get();
            assertEquals(View.VISIBLE, activity.findViewById(R.id.launch_button).getVisibility());
            assertEquals(View.GONE, activity.findViewById(R.id.search_button).getVisibility());
            assertEquals(View.GONE, activity.findViewById(R.id.history_button).getVisibility());
            Uri valid = file("valid.pdf"); activity.openDocument(valid, valid.toString());
            assertEquals(View.GONE, activity.findViewById(R.id.reading_controls).getVisibility());
            assertEquals(View.GONE, activity.findViewById(R.id.more_button).getVisibility());
            MotionEvent touch = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 20, 20, 0);
            activity.dispatchTouchEvent(touch); touch.recycle();
            MotionEvent up = MotionEvent.obtain(0, 100, MotionEvent.ACTION_UP, 20, 20, 0);
            activity.dispatchTouchEvent(up); up.recycle();
            assertEquals(View.VISIBLE, activity.findViewById(R.id.more_button).getVisibility());
            assertEquals(View.GONE, activity.findViewById(R.id.reading_controls).getVisibility());
            ShadowLooper.idleMainLooper(4001, java.util.concurrent.TimeUnit.MILLISECONDS);
            assertEquals(View.VISIBLE, activity.findViewById(R.id.more_button).getVisibility());
            tap(activity, 4500);
            assertEquals(View.GONE, activity.findViewById(R.id.more_button).getVisibility());
            MotionEvent again = MotionEvent.obtain(5000, 5000, MotionEvent.ACTION_DOWN, 20, 20, 0);
            activity.dispatchTouchEvent(again); again.recycle();
            MotionEvent againUp = MotionEvent.obtain(5000, 5100, MotionEvent.ACTION_UP, 20, 20, 0);
            activity.dispatchTouchEvent(againUp); againUp.recycle();
            activity.findViewById(R.id.more_button).performClick();
            assertEquals(View.VISIBLE, activity.findViewById(R.id.reading_controls).getVisibility());
            assertEquals(View.VISIBLE, activity.findViewById(R.id.history_button).getVisibility());
        }
    }
    @Test public void pendingOpenSurvivesSavedStateAndLatestSelectionWins() throws Exception {
        try (org.robolectric.android.controller.ActivityController<TestActivity> controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            TestActivity activity = controller.get();
            Uri first = file("first.pdf"), second = file("second.pdf"), third = file("third.pdf");
            activity.openDocument(first, first.toString());
            controller.pause().saveInstanceState(new android.os.Bundle()).stop();
            activity.openDocument(second, second.toString());
            activity.openDocument(third, third.toString());
            assertEquals(1, activity.viewers.size());
            controller.restart().start().resume();
            assertEquals(2, activity.viewers.size());
            assertEquals(third.toString(), activity.getCurrentDocumentUri());
            assertEquals(View.VISIBLE, activity.findViewById(R.id.fragment_container_view).getVisibility());
        }
    }

    @Test public void screenLockReopensCurrentPdfAndBackgroundFailureDoesNotPoisonNextOpen() throws Exception {
        try (org.robolectric.android.controller.ActivityController<TestActivity> controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            TestActivity activity = controller.get();
            Uri first = file("first.pdf"), second = file("second.pdf");
            activity.openDocument(first, first.toString());
            PdfViewerFragmentExtended stopped = activity.viewers.get(0);
            controller.pause().saveInstanceState(new android.os.Bundle()).stop();
            stopped.onLoadDocumentError(new android.os.DeadObjectException());
            ShadowLooper.idleMainLooper();
            assertEquals(View.GONE, activity.findViewById(R.id.home_content).getVisibility());
            controller.restart().start().resume();
            assertEquals(2, activity.viewers.size());
            assertNotSame(stopped, activity.viewers.get(1));
            assertEquals(first.toString(), activity.getCurrentDocumentUri());
            activity.openDocument(second, second.toString());
            assertEquals(3, activity.viewers.size());
            assertEquals(second.toString(), activity.getCurrentDocumentUri());
        }
    }

    @Test public void pickerResultBeforeResumeIsQueuedInsteadOfDiscarded() throws Exception {
        try (org.robolectric.android.controller.ActivityController<TestActivity> controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            TestActivity activity = controller.get();
            Uri first = file("first.pdf"), second = file("second.pdf");
            activity.openDocument(first, first.toString());
            activity.findViewById(R.id.launch_button).performClick();
            org.robolectric.shadows.ShadowActivity shadow = org.robolectric.shadow.api.Shadow.extract(activity);
            org.robolectric.shadows.ShadowActivity.IntentForResult request = shadow.getNextStartedActivityForResult();
            assertNotNull(request);
            controller.pause();
            activity.getActivityResultRegistry().dispatchResult(request.requestCode, android.app.Activity.RESULT_OK,
                    new android.content.Intent().setData(second));
            assertEquals(1, activity.viewers.size());
            controller.resume();
            assertEquals(2, activity.viewers.size());
            assertEquals(second.toString(), activity.getCurrentDocumentUri());
        }
    }
    @Test public void renderConnectionFailuresAreDistinguishedFromInvalidFileErrors() {
        assertTrue(PdfViewerFragmentExtended.isRendererConnectionError(
                new IllegalStateException("lateinit property rendererAdapter has not been initialized")));
        assertTrue(PdfViewerFragmentExtended.isRendererConnectionError(new android.os.DeadObjectException()));
        assertFalse(PdfViewerFragmentExtended.isRendererConnectionError(new java.io.FileNotFoundException("Missing document")));
    }
    @Test public void stalledLoadRetriesOnceThenReturnsHomeInsteadOfSpinningForever() throws Exception {
        try (org.robolectric.android.controller.ActivityController<TestActivity> controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            TestActivity activity = controller.get();
            Uri stalled = file("stalled.pdf"); activity.openDocument(stalled, stalled.toString());
            ShadowLooper.idleMainLooper(20001, java.util.concurrent.TimeUnit.MILLISECONDS);
            assertEquals(2, activity.viewers.size());
            ShadowLooper.idleMainLooper(20001, java.util.concurrent.TimeUnit.MILLISECONDS);
            assertEquals(2, activity.viewers.size());
            assertEquals(View.VISIBLE, activity.findViewById(R.id.home_content).getVisibility());
            android.app.Dialog dialog = ShadowDialog.getLatestDialog(); if (dialog != null) dialog.dismiss();
            Uri valid = file("valid-after-timeout.pdf"); activity.openDocument(valid, valid.toString());
            assertEquals(3, activity.viewers.size());
            assertEquals(View.VISIBLE, activity.findViewById(R.id.fragment_container_view).getVisibility());
        }
    }
}
