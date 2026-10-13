package com.pdf.peace_space;

import androidx.pdf.selection.Selection;
import androidx.pdf.selection.SelectionMenuComponent;
import androidx.pdf.selection.model.TextSelection;

import java.util.function.Consumer;
import java.util.function.Supplier;

import kotlin.Unit;

final class MeaningSelectionAction {
    private MeaningSelectionAction() {}

    static SelectionMenuComponent create(Supplier<Selection> currentSelection,
                                         Consumer<String> explain) {
        return new SelectionMenuComponent("peace_space_meaning", "Meaning",
                "Explain selected text", session -> {
            // Read at click time: the user may have moved the selection handles.
            Selection selection = currentSelection.get();
            if (selection instanceof TextSelection) {
                String text = ((TextSelection) selection).getText().toString().trim();
                if (!text.isEmpty()) {
                    // Closing the menu may clear selection, so capture the text first.
                    session.close();
                    explain.accept(text);
                }
            }
            return Unit.INSTANCE;
        });
    }
}
