package com.pdf.peace_space;

import android.graphics.Color;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 35)
public class OverlayAppearanceTest {
    @Test public void colorsPersistAndForegroundAdaptsToLightAndDark() {
        OverlayAppearance original = new OverlayAppearance(RuntimeEnvironment.getApplication());
        original.save(OverlayAppearance.parse("203040"), OverlayAppearance.parse("#00B894"));
        OverlayAppearance reopened = new OverlayAppearance(RuntimeEnvironment.getApplication());
        assertEquals(Color.rgb(32,48,64), reopened.surface());
        assertEquals(Color.rgb(0,184,148), reopened.buttons());
        assertEquals(Color.WHITE, OverlayAppearance.foreground(Color.BLACK));
        assertNotEquals(Color.WHITE, OverlayAppearance.foreground(Color.WHITE));
        reopened.reset();
    }
    @Test(expected = IllegalArgumentException.class) public void invalidColorIsRejected() { OverlayAppearance.parse("purple!"); }
}
