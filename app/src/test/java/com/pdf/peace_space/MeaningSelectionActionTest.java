package com.pdf.peace_space;

import androidx.pdf.selection.Selection;
import androidx.pdf.selection.SelectionMenuComponent;
import androidx.pdf.selection.model.TextSelection;

import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class MeaningSelectionActionTest {
    @Test
    public void explainsLatestWholePhraseBeforeMenuClearsSelection() {
        AtomicReference<Selection> selection = new AtomicReference<>(
                new TextSelection("old word", Collections.emptyList()));
        AtomicReference<String> explained = new AtomicReference<>();
        SelectionMenuComponent item = MeaningSelectionAction.create(selection::get, explained::set);
        selection.set(new TextSelection("  break the ice  ", Collections.emptyList()));
        item.getOnClick().invoke(() -> selection.set(null));
        assertEquals("break the ice", explained.get());
        assertNull(selection.get());
    }

    @Test
    public void missingSelectionDoesNotExplainStaleText() {
        AtomicReference<String> explained = new AtomicReference<>();
        SelectionMenuComponent item = MeaningSelectionAction.create(() -> null, explained::set);
        item.getOnClick().invoke(() -> fail("Missing selection must not close menu"));
        assertNull(explained.get());
    }

    @Test
    public void emptySelectionDoesNotOpenExplanation() {
        AtomicReference<String> explained = new AtomicReference<>();
        SelectionMenuComponent item = MeaningSelectionAction.create(
                () -> new TextSelection("   ", Collections.emptyList()), explained::set);
        item.getOnClick().invoke(() -> fail("Empty selection must not close menu"));
        assertNull(explained.get());
    }
}
