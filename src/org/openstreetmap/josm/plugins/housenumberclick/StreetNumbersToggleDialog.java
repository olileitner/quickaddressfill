package org.openstreetmap.josm.plugins.housenumberclick;

import java.awt.BorderLayout;

import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.tools.I18n;

/**
 * Dockable JOSM sidebar dialog showing house numbers for the currently selected street.
 */
final class StreetNumbersToggleDialog extends ToggleDialog {

    private final StreetNumbersPanel panel;

    StreetNumbersToggleDialog(StreetNumbersPanel panel) {
        super(
                I18n.tr("Street Numbers"),
                "housenumberclick",
                I18n.tr("Show street numbers"),
                null,
                260
        );
        this.panel = panel;
        setLayout(new BorderLayout());
        add(panel, BorderLayout.CENTER);
    }

    StreetNumbersPanel getPanel() {
        return panel;
    }
}

