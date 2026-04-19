package org.openstreetmap.josm.plugins.housenumberclick;

import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;

import javax.swing.JDialog;

/**
 * Restores and persists dialog bounds with on-screen validation and default fallback behavior.
 */
final class DialogWindowBoundsManager {

    private static final int MIN_VISIBLE_WIDTH = 120;
    private static final int MIN_VISIBLE_HEIGHT = 80;

    private DialogWindowBoundsManager() {
        // Utility class
    }

    static void applyStoredBoundsOrDefaults(JDialog dialog, String dialogId, Dimension minimumSize,
            Dimension defaultSize, Runnable defaultPositioner) {
        if (dialog == null) {
            return;
        }
        if (minimumSize != null) {
            dialog.setMinimumSize(minimumSize);
        }
        if (defaultSize != null) {
            dialog.setSize(defaultSize);
        }

        Rectangle storedBounds = HouseNumberClickPreferences.getDialogBounds(dialogId);
        if (!isValidStoredBounds(storedBounds) || !isVisibleOnAnyScreen(storedBounds)) {
            if (defaultPositioner != null) {
                defaultPositioner.run();
            }
            return;
        }

        int minWidth = minimumSize == null ? 1 : minimumSize.width;
        int minHeight = minimumSize == null ? 1 : minimumSize.height;
        dialog.setBounds(new Rectangle(
                storedBounds.x,
                storedBounds.y,
                Math.max(minWidth, storedBounds.width),
                Math.max(minHeight, storedBounds.height)
        ));
    }

    static void saveDialogBounds(JDialog dialog, String dialogId) {
        if (dialog == null || !dialog.isDisplayable()) {
            return;
        }
        HouseNumberClickPreferences.putDialogBounds(dialogId, dialog.getBounds());
    }

    private static boolean isValidStoredBounds(Rectangle bounds) {
        return bounds != null && bounds.width > 0 && bounds.height > 0;
    }

    private static boolean isVisibleOnAnyScreen(Rectangle bounds) {
        if (!isValidStoredBounds(bounds)) {
            return false;
        }
        GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
        if (environment == null) {
            return false;
        }
        for (GraphicsDevice device : environment.getScreenDevices()) {
            if (device == null) {
                continue;
            }
            GraphicsConfiguration configuration = device.getDefaultConfiguration();
            if (configuration == null) {
                continue;
            }
            Rectangle usableBounds = resolveUsableBounds(configuration);
            Rectangle intersection = usableBounds.intersection(bounds);
            if (intersection.width >= MIN_VISIBLE_WIDTH && intersection.height >= MIN_VISIBLE_HEIGHT) {
                return true;
            }
        }
        return false;
    }

    private static Rectangle resolveUsableBounds(GraphicsConfiguration configuration) {
        Rectangle screenBounds = configuration.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        return new Rectangle(
                screenBounds.x + insets.left,
                screenBounds.y + insets.top,
                Math.max(1, screenBounds.width - insets.left - insets.right),
                Math.max(1, screenBounds.height - insets.top - insets.bottom)
        );
    }
}

