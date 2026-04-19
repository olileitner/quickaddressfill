package org.openstreetmap.josm.plugins.housenumberclick;

import java.awt.BorderLayout;

import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.tools.I18n;

/**
 * Dockable JOSM sidebar dialog showing street-level house-number counts.
 */
final class StreetCountsToggleDialog extends ToggleDialog {

    private final StreetCountsPanel panel;

    StreetCountsToggleDialog(StreetCountsPanel panel) {
        super(
                I18n.tr("Street Counts"),
                "housenumberclick",
                I18n.tr("Show street counts"),
                null,
                200
        );
        this.panel = panel;
        setLayout(new BorderLayout());
        add(panel, BorderLayout.CENTER);
    }

    StreetCountsPanel getPanel() {
        return panel;
    }
}

